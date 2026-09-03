package it.stefazzi.pokerolesheets.data

/** UI hints only: never change the sheet, roll dice, or spend resources. */
data class DicePool(val formula: String, val dice: Int)

object EquipmentParameters {
    val groups: Map<String, Map<String, String>> = linkedMapOf(
        "Attributes" to SheetStats.attributes,
        "Social Attributes" to SheetStats.socialAttributes,
        *SheetStats.skillGroups.map { (group, fields) -> "Skills · $group" to fields }.toTypedArray(),
        "Riferimenti rapidi" to linkedMapOf(
            "quick.hp" to "HP maximum", "quick.will" to "Will maximum",
            "quick.physical_defense" to "Physical Defense", "quick.special_defense" to "Special Defense",
            "quick.initiative" to "Initiative", "quick.evasion" to "Evasion",
        ),
    )
    val labels = groups.values.flatMap { it.entries }.associate { it.key to it.value }
}

fun EditableSheet.equipmentInfluences(catalog: List<CatalogItem>): Map<String, List<String>> {
    if (!isPokemon) return emptyMap()
    val ids = (accessories.map { it.catalogId } + heldItemCatalogId).filter(String::isNotBlank).toSet()
    return catalog.filter { it.id in ids }.flatMap { item ->
        item.displayAffectedParameters.distinct().filter { it in EquipmentParameters.labels }.map { it to item.displayName }
    }.groupBy({ it.first }, { it.second })
}

fun EditableSheet.painWarning(): String? {
    val current = hpActual.trim().toIntOrNull() ?: return null
    val maximum = hpTotal.trim().toIntOrNull()?.takeIf { it > 0 } ?: return null
    return when {
        current < 0 -> null
        current == 0 -> "0 HP: Pokémon / allenatore fuori combattimento (Fainted)."
        current >= maximum -> null
        current == 1 -> "1 HP: rimuovi due successi dalle tue prove."
        current <= maximum / 2 -> "Metà vita o meno: rimuovi un successo dalle tue prove."
        else -> null
    }
}

private val rollFields = (SheetStats.attributes + SheetStats.socialAttributes +
    SheetStats.skillGroups.values.fold(emptyMap<String, String>()) { all, group -> all + group })
    .entries.associate { it.value.lowercase() to it.key }

/** Enumerate slash alternatives rather than silently choosing the highest attribute. Unknown tokens stay manual. */
fun EditableSheet.dicePools(expression: String, skillBonus: Boolean = true): List<DicePool> {
    if (expression.isBlank()) return emptyList()
    data class Term(val label: String, val value: Int, val skill: Boolean)
    var combinations = listOf(emptyList<Term>())
    for (part in expression.split('+')) {
        val alternatives = part.split('/').map { token ->
            val label = token.trim()
            val path = rollFields[label.lowercase()]
            val value = when {
                path != null -> dotStats[path]?.count { it } ?: return emptyList()
                label.equals("Will", true) -> willTotal.trim().toIntOrNull()?.takeIf { it >= 0 } ?: return emptyList()
                else -> label.toIntOrNull()?.takeIf { it >= 0 } ?: return emptyList()
            }
            Term(label, value, path?.startsWith("skills.") == true)
        }
        combinations = combinations.flatMap { prior -> alternatives.map { prior + it } }
        if (combinations.size > 16) return emptyList()
    }
    return combinations.map { terms ->
        val bonus = if (skillBonus && terms.any { it.skill } &&
            (sheetRank == SheetRank.Master || sheetRank == SheetRank.Champion)) 2 else 0
        DicePool(terms.joinToString(" + ") { "${it.label} (${it.value})" } + if (bonus > 0) " + Rank (2)" else "",
            terms.sumOf { it.value } + bonus)
    }.distinct()
}

fun CatalogMove.damagePools(sheet: EditableSheet): List<DicePool> {
    if (category.equals("Support", true) || damage.isBlank()) return emptyList()
    val basePower = power.toIntOrNull()?.takeIf { it >= 0 } ?: return emptyList()
    val stab = if (type.isNotBlank() && !type.equals("None", true) &&
        listOf(sheet.primaryType, sheet.secondaryType).any { it.equals(type, true) }) 1 else 0
    return sheet.dicePools(damage, skillBonus = false).map {
        DicePool("${it.formula} + Power ($basePower)" + if (stab > 0) " + STAB (1)" else "", it.dice + basePower + stab)
    }
}

fun CatalogMove.clashPools(sheet: EditableSheet): List<DicePool> = when {
    category.equals("Physical", true) -> sheet.dicePools("Strength + Clash")
    category.equals("Special", true) -> sheet.dicePools("Special + Clash")
    else -> emptyList()
}

val CatalogMove.healingWillReminder: Boolean get() =
    Regex("\\b(?:basic|minor|complete) heal\\b", RegexOption.IGNORE_CASE).containsMatchIn(effect) ||
        (Regex("\\bspend\\s+1\\s+will\\s+point\\b", RegexOption.IGNORE_CASE).containsMatchIn(effect) &&
            Regex("\\b(?:heal\\w*|restore\\w*|recover\\w*)\\b", RegexOption.IGNORE_CASE).containsMatchIn(effect))

fun damageAfterDefense(pool: Int, defense: Int): Int = (pool - defense.coerceAtLeast(0)).coerceAtLeast(1)
