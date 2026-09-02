package it.stefazzi.pokerolesheets.data

import kotlinx.serialization.json.*

enum class SheetRank(val attributePoints: Int, val skillPoints: Int, val skillLimit: Int) {
    Starter(0, 5, 1), Rookie(2, 10, 2), Standard(4, 14, 3), Advanced(6, 17, 4),
    Expert(8, 19, 5), Ace(10, 20, 5), Master(10, 22, 5), Champion(14, 25, 5);

    val traitBonus: Int get() = if (ordinal >= Master.ordinal) 3 else 0

    companion object {
        fun parse(value: String): SheetRank? = entries.firstOrNull { it.name.equals(value, true) }
    }
}

data class AttributeReference(val base: Int, val limit: Int)
data class MoveReference(val name: String, val rank: SheetRank, val type: String)
data class EvolutionReference(val targetName: String, val condition: String)

data class CorebookSpecies(
    val name: String,
    val number: String,
    val form: String,
    val types: List<String>,
    val height: String,
    val weight: String,
    val baseHp: Int,
    val suggestedRank: SheetRank,
    val attributes: Map<String, AttributeReference>,
    val abilities: List<String>,
    val moves: List<MoveReference>,
    val sourcePage: Int,
    val catalogId: String = "",
    val evolutions: List<EvolutionReference> = emptyList(),
    val anyMoveRank: SheetRank? = null,
    val dexCategory: String = "",
    val dexDescription: String = "",
) {
    fun availableMoves(rank: SheetRank) = moves.filter { it.rank.ordinal <= rank.ordinal }
        .sortedBy { it.rank.ordinal }.distinctBy { normalizeMoveName(it.name) }

    val sourceLabel: String get() = if (sourcePage > 0) "Corebook 3.0, p. $sourcePage" else "Catalogo community 3.0"

    // Only a new draft may be initialized. Existing records must never be reset by a catalog lookup.
    fun prefill(sheet: EditableSheet, rank: SheetRank): EditableSheet {
        require(sheet.isPokemon && sheet.recordId.isBlank()) { "La precompilazione è riservata ai nuovi Pokémon" }
        val stats = sheet.dotStats + attributes.map { (key, ref) ->
            "attributes.$key" to List(12) { it < ref.base }
        } + SheetStats.socialAttributes.keys.associateWith { List(5) { false } }
        val hp = baseHp + attributes.getValue("vitality").base + rank.traitBonus + if (sheet.isAlpha) 2 else 0
        val will = 3 + attributes.getValue("insight").base + rank.traitBonus
        return sheet.copy(
            speciesName = name, pokedexNumber = number,
            primaryType = types.first(), secondaryType = types.getOrElse(1) { "" },
            size = height, weight = weight, dotStats = stats,
            hpActual = hp.toString(), hpTotal = hp.toString(),
            willActual = will.toString(), willTotal = will.toString(),
            abilityName = abilities.singleOrNull().orEmpty(), abilitySearch = abilities.singleOrNull().orEmpty(),
            abilityEffect = "", abilityDescription = "", moves = emptyList(),
            happiness = List(5) { it < 2 }, loyalty = List(5) { it < 2 },
        ).withRuleValue("rank", rank.name).withRuleValue("form", form)
            .withRuleValue("base_hp", baseHp.toString())
            .withRuleValue("catalog_id", catalogId).recalculateMaximums(this)
    }
}

// Only explicit confirmation changes a draft. Do not use capture prefill for evolution:
// it would reset the individual's learned moves, stats and personal data.
fun CorebookSpecies.evolve(sheet: EditableSheet, target: CorebookSpecies, ability: String): EditableSheet {
    require(sheet.isPokemon && sheet.recordId.isNotBlank()) { "Salva il Pokémon prima di evolverlo" }
    require(CorebookCatalog.find(listOf(this), sheet) == this) { "La specie della scheda è cambiata" }
    require(evolutions.any { it.targetName.equals(target.name, true) }) { "Evoluzione non prevista dal catalogo" }
    require(target.abilities.isEmpty() || ability in target.abilities) { "Scegli un'abilità della nuova specie" }
    val sameAbility = ability.equals(sheet.abilityName, true)
    return sheet.copy(
        speciesName = target.name, pokedexNumber = target.number,
        primaryType = target.types.first(), secondaryType = target.types.getOrElse(1) { "" },
        size = target.height, weight = target.weight,
        abilityName = ability, abilitySearch = ability,
        abilityDescription = if (sameAbility) sheet.abilityDescription else "",
        abilityEffect = if (sameAbility) sheet.abilityEffect else "",
    ).withRuleValue("form", target.form).withRuleValue("catalog_id", target.catalogId)
        .withRuleValue("base_hp", target.baseHp.toString()).recalculateMaximums(target)
}

object CorebookCatalog {
    fun parse(text: String): List<CorebookSpecies> = Json.parseToJsonElement(text).jsonArray.map { item ->
        val row = item.jsonObject
        fun string(key: String) = row.getValue(key).jsonPrimitive.content
        val attributes = row.getValue("attributes").jsonObject.mapValues { (_, value) ->
            val pair = value.jsonArray
            AttributeReference(pair[0].jsonPrimitive.int, pair[1].jsonPrimitive.int).also {
                require(it.base in 1..it.limit && it.limit <= 12)
            }
        }
        require(attributes.keys == SheetStats.attributes.keys.map { it.substringAfter('.') }.toSet())
        CorebookSpecies(
            name = string("name"), number = string("number"), form = string("form"),
            types = row.getValue("types").jsonArray.map { it.jsonPrimitive.content },
            height = string("height"), weight = string("weight"), baseHp = string("base_hp").toInt(),
            suggestedRank = SheetRank.valueOf(string("suggested_rank")), attributes = attributes,
            abilities = row.getValue("abilities").jsonArray.map { it.jsonPrimitive.content },
            moves = row.getValue("moves").jsonArray.map {
                val move = it.jsonArray
                MoveReference(move[0].jsonPrimitive.content, SheetRank.valueOf(move[1].jsonPrimitive.content), move[2].jsonPrimitive.content)
            }, sourcePage = string("source_page").toInt(),
        )
    }.also { species -> require(species.map { it.number to it.form }.distinct().size == species.size) }

    fun find(species: List<CorebookSpecies>, sheet: EditableSheet): CorebookSpecies? {
        if (!sheet.isPokemon) return null
        // Legacy web sheets store '# 061', not just '061'. Normalize only the
        // comparison value, preserving the original sheet and rejecting malformed IDs.
        val rawNumber = sheet.pokedexNumber.trim()
        val number = if (rawNumber.isBlank()) "" else {
            val digits = rawNumber.removePrefix("#").trim()
            if (digits.isEmpty() || digits.any { it !in '0'..'9' }) return null
            digits.toIntOrNull()?.takeIf { it > 0 }?.toString() ?: return null
        }
        return species.firstOrNull {
            it.form.equals(sheet.speciesForm, true) &&
                (sheet.speciesName.isBlank() || it.name.equals(sheet.speciesName.trim(), true)) &&
                (number.isBlank() || it.number == number) &&
                (number.isNotBlank() || sheet.speciesName.isNotBlank())
        }
    }
}

private fun EditableSheet.ruleValue(key: String): String =
    ((raw["pokerole"] as? JsonObject)?.get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()

fun EditableSheet.withRuleValue(key: String, value: String): EditableSheet {
    val rules = (raw["pokerole"] as? JsonObject).orEmpty() + (key to JsonPrimitive(value))
    return copy(raw = JsonObject(raw + ("pokerole" to JsonObject(rules))))
}

val EditableSheet.speciesForm: String get() = ruleValue("form").ifBlank { "Standard" }
val EditableSheet.sheetRank: SheetRank? get() = SheetRank.parse(ruleValue("rank"))
    ?: SheetRank.parse(rankImage.substringAfterLast('/').substringBeforeLast('.'))
val EditableSheet.baseHpText: String? get() =
    ((raw["pokerole"] as? JsonObject)?.get("base_hp") as? JsonPrimitive)?.contentOrNull
val EditableSheet.baseHpValue: Int? get() = baseHpText?.toIntOrNull()?.takeIf { it in 1..999 }
val EditableSheet.isShiny: Boolean get() = isPokemon && ruleValue("shiny").equals("true", true)
val EditableSheet.isAlpha: Boolean get() = isPokemon && ruleValue("alpha").equals("true", true)

fun EditableSheet.attributeFields(): Map<String, String> = SheetStats.attributes.filterKeys {
    isPokemon || it != "attributes.special"
}

fun EditableSheet.statRange(key: String, reference: CorebookSpecies?): IntRange = when {
    key in SheetStats.attributes -> {
        val limit = if (isPokemon) reference?.attributes?.get(key.substringAfter('.'))?.limit ?: 12 else 5
        0..(limit + if (sheetRank == SheetRank.Champion) 2 else 0).coerceAtMost(12)
    }
    key in SheetStats.socialAttributes -> 0..5
    else -> 0..(sheetRank?.skillLimit ?: 5)
}

// Campaign option 2 (Corebook pp. 26-28, 31, 56, 70). Recalculate baseline
// traits; never heal HP/Will. Temporary combat/Ability modifiers stay manual.
fun EditableSheet.recalculateMaximums(reference: CorebookSpecies?): EditableSheet {
    val baseHp = if (baseHpText != null) baseHpValue else reference?.baseHp ?: if (!isPokemon) 4 else null
    val vitality = dotStats["attributes.vitality"].orEmpty().count { it }
    val insight = dotStats["attributes.insight"].orEmpty().count { it }
    val dexterity = dotStats["attributes.dexterity"].orEmpty().count { it }
    val alert = dotStats["skills.survival.alert"].orEmpty().count { it }
    val evade = dotStats["skills.fight.evasion"].orEmpty().count { it }
    val bonus = sheetRank?.traitBonus ?: 0
    return copy(
        hpTotal = baseHp?.let { (it + vitality + bonus + if (isAlpha) 2 else 0).toString() } ?: hpTotal,
        willTotal = (3 + insight + bonus).toString(),
        defenseActual = (vitality + bonus).toString(),
        defenseTotal = (insight + bonus).toString(),
        initiative = (dexterity + alert + bonus).toString(),
        evasion = (dexterity + evade + if (bonus > 0) 2 else 0).toString(),
    )
}
