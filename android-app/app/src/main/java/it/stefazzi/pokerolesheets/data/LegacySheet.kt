package it.stefazzi.pokerolesheets.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

object SheetStats {
    val attributes = linkedMapOf(
        "attributes.strength" to "Strength",
        "attributes.dexterity" to "Dexterity",
        "attributes.vitality" to "Vitality",
        "attributes.special" to "Special",
        "attributes.insight" to "Insight",
    )

    val socialAttributes = linkedMapOf(
        "social_attributes.tough" to "Tough",
        "social_attributes.cool" to "Cool",
        "social_attributes.beauty" to "Beauty",
        "social_attributes.cute" to "Cute",
        "social_attributes.clever" to "Clever",
    )

    val skillGroups = linkedMapOf(
        "Fight" to linkedMapOf(
            "skills.fight.brawl" to "Brawl",
            "skills.fight.channel" to "Channel",
            "skills.fight.clash" to "Clash",
            "skills.fight.evasion" to "Evasion",
            "skills.fight.throw" to "Throw",
            "skills.fight.weapons" to "Weapons",
        ),
        "Survival" to linkedMapOf(
            "skills.survival.alert" to "Alert",
            "skills.survival.athletic" to "Athletic",
            "skills.survival.nature" to "Nature",
            "skills.survival.stealth" to "Stealth",
        ),
        "Social" to linkedMapOf(
            "skills.social.charm" to "Charm",
            "skills.social.empathy" to "Empathy",
            "skills.social.etiquette" to "Etiquette",
            "skills.social.intimidate" to "Intimidate",
            "skills.social.perform" to "Perform",
        ),
        "Knowledge" to linkedMapOf(
            "skills.knowledge.crafts" to "Crafts",
            "skills.knowledge.lore" to "Lore",
            "skills.knowledge.medicine" to "Medicine",
            "skills.knowledge.science" to "Science",
        ),
    )

    val allDotKeys: List<String> = (attributes.keys + socialAttributes.keys +
        skillGroups.values.flatMap { it.keys }).toList()

}

data class EditableSheet(
    val storageKey: String,
    val isPokemon: Boolean,
    val trainerName: String,
    val pokemonName: String,
    val speciesName: String,
    val pokedexNumber: String,
    val primaryType: String,
    val secondaryType: String,
    val rankImage: String,
    val happiness: List<Boolean>,
    val loyalty: List<Boolean>,
    val size: String,
    val weight: String,
    val profilePicture: String,
    val team: String,
    val age: String,
    val money: String,
    val reputation: String,
    val hpActual: String,
    val hpTotal: String,
    val defenseActual: String,
    val defenseTotal: String,
    val willActual: String,
    val willTotal: String,
    val heldItem: String,
    val statusEffect: String,
    val initiative: String,
    val evasion: String,
    val fatigue: List<Boolean>,
    val actionUsed: List<Boolean>,
    val abilitySearch: String,
    val abilityName: String,
    val abilityEffect: String,
    val abilityDescription: String,
    val natureSearch: String,
    val natureName: String,
    val natureConfiguration: String,
    val natureKeywords: String,
    val natureDescription: String,
    val pokemonTeam: List<String>,
    val dotStats: Map<String, List<Boolean>>,
    val potion: String,
    val superPotion: String,
    val hyperPotion: String,
    val bagItemsLeft: List<String>,
    val bagItemsRight: List<String>,
    val moves: List<String>,
    val background: String,
    val personalKnowledge: String,
    val raw: JsonObject,
    val recordId: String = "",
    val trainerId: String? = null,
    val teamSlot: Int? = null,
    val ownerId: String? = null,
    val resolvedProfilePicture: String = "",
) {
    val displayName: String
        get() = if (isPokemon) pokemonName.ifBlank { speciesName.ifBlank { storageKey } }
        else trainerName.ifBlank { storageKey }

    fun updatedJson(): JsonObject = raw.patch {
        val header = objectAt("header").patch {
            putString("trainer_name", trainerName)
            putString("pokemon_name", pokemonName)
            putString("pokename", speciesName)
            putString("pokenr", pokedexNumber)
            put("type1", objectAt("type1").patch { putString("text", primaryType) })
            put("type2", objectAt("type2").patch { putString("text", secondaryType) })
            putString("rank_img", rankImage)
            put("happiness", happiness.toJsonArray())
            put("loyalty", loyalty.toJsonArray())
            putString("size", this@EditableSheet.size)
            putString("weight", weight)
            putString("profile_picture", profilePicture)
            putString("team", team)
            putString("age", age)
            putString("money", money)
            putString("reputation", reputation)
        }
        put("header", header)

        put("quick_references", objectAt("quick_references").patch {
            put("hp", objectAt("hp").patch {
                putString("actual", hpActual)
                putString("total", hpTotal)
            })
            put("def_spdef", objectAt("def_spdef").patch {
                putString("actual", defenseActual)
                putString("total", defenseTotal)
            })
            put("will", objectAt("will").patch {
                putString("actual", willActual)
                putString("total", willTotal)
            })
            putString("held_item", heldItem)
            putString("status_effect", statusEffect)
            putString("initiative", initiative)
            putString("evasion", evasion)
            put("fatigue", fatigue.toJsonArray())
            put("action_used", actionUsed.toJsonArray())
        })

        put("ability", objectAt("ability").patch {
            putString("search_input", abilitySearch)
            put("details", objectAt("details").patch {
                putString("name", abilityName)
                putString("effect", abilityEffect)
                putString("text", abilityDescription)
            })
        })
        put("nature", objectAt("nature").patch {
            putString("search_input", natureSearch)
            put("details", objectAt("details").patch {
                putString("name", natureName)
                putString("configuration", natureConfiguration)
                putString("key", natureKeywords)
                putString("text", natureDescription)
            })
        })

        put("pokemon_team", JsonArray(pokemonTeam.mapIndexed { index, value ->
            JsonObject(mapOf(
                "slot" to JsonPrimitive(index + 1),
                "id" to JsonPrimitive("pk${index + 1}"),
                "value" to JsonPrimitive(value),
            ))
        }))

        putDotSection("attributes", SheetStats.attributes.keys, dotStats)
        putDotSection("social_attributes", SheetStats.socialAttributes.keys, dotStats)
        put("skills", objectAt("skills").patch {
            SheetStats.skillGroups.values.forEach { fields ->
                val groupKey = fields.keys.first().split('.')[1]
                put(groupKey, objectAt(groupKey).patch {
                    fields.keys.forEach { path ->
                        put(path.substringAfterLast('.'), dotStats[path].orEmpty().toJsonArray())
                    }
                })
            }
        })

        put("bag", objectAt("bag").patch {
            putString("potion", potion)
            putString("super_potion", superPotion)
            putString("hyper_potion", hyperPotion)
            put("items_left", bagItemsLeft.toStringJsonArray())
            put("items_right", bagItemsRight.toStringJsonArray())
        })
        put("moves", JsonArray(moves.mapIndexed { index, value ->
            JsonObject(mapOf(
                "id" to JsonPrimitive("move${index + 1}"),
                "value" to JsonPrimitive(value),
            ))
        }))
        putString("background", background)
        putString("conoscenze_personali", personalKnowledge)
    }

    fun resetCurrentStats(): EditableSheet = copy(
        hpActual = hpTotal.ifBlank { hpActual },
        // Both legacy defense fields are independent stats, not current/maximum.
        // Leave them unchanged for trainers and Pokemon alike.
        willActual = willTotal.ifBlank { willActual },
    )

    companion object {
        fun blank(isPokemon: Boolean): EditableSheet = from(
            storageKey = "",
            value = JsonObject(emptyMap()),
            json = Json,
            forcedPokemon = isPokemon,
        ).let { sheet ->
            sheet.copy(
                dotStats = sheet.dotStats + sheet.attributeFields().keys.associateWith {
                    List(if (isPokemon) 12 else 5) { index -> index == 0 }
                } + SheetStats.socialAttributes.keys.associateWith { List(5) { index -> index == 0 } },
                hpActual = if (isPokemon) "" else "5", hpTotal = if (isPokemon) "" else "5",
                willActual = "4", willTotal = "4",
            )
        }

        fun from(storageKey: String, value: JsonElement, json: Json): EditableSheet =
            from(storageKey, value, json, forcedPokemon = null)

        private fun from(
            storageKey: String,
            value: JsonElement,
            json: Json,
            forcedPokemon: Boolean?,
        ): EditableSheet {
            val raw = when (value) {
                is JsonObject -> value
                is JsonPrimitive -> value.contentOrNull
                    ?.takeIf(String::isNotBlank)
                    ?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
                    ?: JsonObject(emptyMap())
                else -> JsonObject(emptyMap())
            }
            val header = raw.objectAt("header")
            val quickReferences = raw.objectAt("quick_references")
            val hp = quickReferences.objectAt("hp")
            val defense = quickReferences.objectAt("def_spdef")
            val will = quickReferences.objectAt("will")
            val ability = raw.objectAt("ability")
            val abilityDetails = ability.objectAt("details")
            val nature = raw.objectAt("nature")
            val natureDetails = nature.objectAt("details")
            val bag = raw.objectAt("bag")
            val pokemonName = header.stringAt("pokemon_name")
            val speciesName = header.stringAt("pokename")

            val dotStats = buildMap {
                addDotSection(raw, "attributes", SheetStats.attributes.keys)
                addDotSection(raw, "social_attributes", SheetStats.socialAttributes.keys)
                SheetStats.skillGroups.values.forEach { fields ->
                    fields.keys.forEach { path -> put(path, raw.booleanListAtPath(path)) }
                }
            }

            return EditableSheet(
                storageKey = storageKey,
                isPokemon = forcedPokemon ?: (pokemonName.isNotBlank() || speciesName.isNotBlank()),
                trainerName = header.stringAt("trainer_name"),
                pokemonName = pokemonName,
                speciesName = speciesName,
                pokedexNumber = header.stringAt("pokenr"),
                primaryType = header.objectAt("type1").stringAt("text"),
                secondaryType = header.objectAt("type2").stringAt("text"),
                rankImage = header.stringAt("rank_img"),
                happiness = header.booleanListAt("happiness"),
                loyalty = header.booleanListAt("loyalty"),
                size = header.stringAt("size"),
                weight = header.stringAt("weight"),
                profilePicture = header.stringAt("profile_picture"),
                team = header.stringAt("team"),
                age = header.stringAt("age"),
                money = header.stringAt("money"),
                reputation = header.stringAt("reputation"),
                hpActual = hp.stringAt("actual"),
                hpTotal = hp.stringAt("total"),
                defenseActual = defense.stringAt("actual"),
                defenseTotal = defense.stringAt("total"),
                willActual = will.stringAt("actual"),
                willTotal = will.stringAt("total"),
                heldItem = quickReferences.stringAt("held_item"),
                statusEffect = quickReferences.stringAt("status_effect"),
                initiative = quickReferences.stringAt("initiative"),
                evasion = quickReferences.stringAt("evasion"),
                fatigue = quickReferences.booleanListAt("fatigue"),
                actionUsed = quickReferences.booleanListAt("action_used"),
                abilitySearch = ability.stringAt("search_input"),
                abilityName = abilityDetails.stringAt("name"),
                abilityEffect = abilityDetails.stringAt("effect"),
                abilityDescription = abilityDetails.stringAt("text"),
                natureSearch = nature.stringAt("search_input"),
                natureName = natureDetails.stringAt("name"),
                natureConfiguration = natureDetails.stringAt("configuration"),
                natureKeywords = natureDetails.stringAt("key"),
                natureDescription = natureDetails.stringAt("text"),
                pokemonTeam = raw.objectListValues("pokemon_team", "value", minimumSize = 3),
                dotStats = dotStats,
                potion = bag.stringAt("potion"),
                superPotion = bag.stringAt("super_potion"),
                hyperPotion = bag.stringAt("hyper_potion"),
                bagItemsLeft = bag.stringListAt("items_left", minimumSize = 15),
                bagItemsRight = bag.stringListAt("items_right", minimumSize = 15),
                moves = raw.objectListValues("moves", "value", minimumSize = if (forcedPokemon == true) 1 else 0),
                background = raw.stringAt("background"),
                personalKnowledge = raw.stringAt("conoscenze_personali"),
                raw = raw,
            )
        }
    }
}

private fun JsonObject.stringAt(key: String): String =
    (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()

private fun JsonObject.objectAt(key: String): JsonObject = get(key) as? JsonObject ?: JsonObject(emptyMap())

private fun JsonObject.booleanListAt(key: String): List<Boolean> {
    val values = (get(key) as? JsonArray)?.map { (it as? JsonPrimitive)?.content == "true" }.orEmpty()
    return values.padTo(5, false)
}

private fun JsonObject.stringListAt(key: String, minimumSize: Int): List<String> {
    val values = (get(key) as? JsonArray)?.map { (it as? JsonPrimitive)?.contentOrNull.orEmpty() }.orEmpty()
    return values.padTo(minimumSize, "")
}

private fun JsonObject.objectListValues(key: String, valueKey: String, minimumSize: Int): List<String> {
    val values = (get(key) as? JsonArray)?.map { (it as? JsonObject)?.stringAt(valueKey).orEmpty() }.orEmpty()
    return values.padTo(minimumSize, "")
}

private fun JsonObject.booleanListAtPath(path: String): List<Boolean> {
    val parts = path.split('.')
    var current = this
    parts.dropLast(1).forEach { current = current.objectAt(it) }
    return current.booleanListAt(parts.last())
}

private fun <T> List<T>.padTo(size: Int, value: T): List<T> =
    if (this.size >= size) this else this + List(size - this.size) { value }

private inline fun JsonObject.patch(block: MutableMap<String, JsonElement>.() -> Unit): JsonObject =
    JsonObject(toMutableMap().apply(block))

private fun MutableMap<String, JsonElement>.objectAt(key: String): JsonObject =
    get(key) as? JsonObject ?: JsonObject(emptyMap())

private fun MutableMap<String, JsonElement>.putString(key: String, value: String) {
    this[key] = JsonPrimitive(value)
}

private fun List<Boolean>.toJsonArray(): JsonArray = JsonArray(map(::JsonPrimitive))

private fun List<String>.toStringJsonArray(): JsonArray = JsonArray(map(::JsonPrimitive))

private fun MutableMap<String, JsonElement>.putDotSection(
    sectionKey: String,
    paths: Collection<String>,
    values: Map<String, List<Boolean>>,
) {
    put(sectionKey, objectAt(sectionKey).patch {
        paths.forEach { path -> put(path.substringAfterLast('.'), values[path].orEmpty().toJsonArray()) }
    })
}

private fun MutableMap<String, List<Boolean>>.addDotSection(
    raw: JsonObject,
    sectionKey: String,
    paths: Collection<String>,
) {
    val section = raw.objectAt(sectionKey)
    paths.forEach { path -> put(path, section.booleanListAt(path.substringAfterLast('.'))) }
}
