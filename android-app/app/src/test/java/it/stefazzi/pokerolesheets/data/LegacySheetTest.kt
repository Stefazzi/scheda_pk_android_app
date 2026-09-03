package it.stefazzi.pokerolesheets.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class LegacySheetTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun pokemonShowsOnlyTheTwelveRequestedSkillsInOrder() {
        val groups = SheetStats.skillGroupsFor(isPokemon = true)
        assertEquals(listOf("Fight", "Survival", "Social"), groups.keys.toList())
        assertEquals(listOf("Brawl", "Channel", "Clash", "Evasion"), groups.getValue("Fight").values.toList())
        assertEquals(listOf("Alert", "Athletic", "Nature", "Stealth"), groups.getValue("Survival").values.toList())
        assertEquals(listOf("Charm", "Etiquette", "Intimidate", "Perform"), groups.getValue("Social").values.toList())
        assertEquals(12, groups.values.sumOf { it.size })
    }

    @Test
    fun trainerShowsOnlyTheSixteenRequestedSkillsInOrder() {
        val groups = SheetStats.skillGroupsFor(isPokemon = false)
        assertEquals(listOf("Fight", "Survival", "Social", "Knowledge"), groups.keys.toList())
        assertEquals(listOf("Brawl", "Throw", "Evasion", "Weapon"), groups.getValue("Fight").values.toList())
        assertEquals(listOf("Alert", "Athletic", "Nature", "Stealth"), groups.getValue("Survival").values.toList())
        assertEquals(listOf("Empathy", "Etiquette", "Intimidate", "Perform"), groups.getValue("Social").values.toList())
        assertEquals(listOf("Crafts", "Lore", "Medicine", "Science"), groups.getValue("Knowledge").values.toList())
        assertEquals(16, groups.values.sumOf { it.size })
    }

    @Test
    fun editingTrainerPreservesHiddenSkillsAndOtherStats() {
        val original = EditableSheet.blank(false).copy(
            hpActual = "3", hpTotal = "7", money = "200",
            dotStats = SheetStats.allDotKeys.associateWith { List(5) { index -> index < 3 } },
        )
        val loaded = EditableSheet.from("Test_Trainer", original.updatedJson(), json)
        val edited = loaded.copy(dotStats = loaded.dotStats + ("skills.fight.throw" to List(5) { it < 2 }))
        val reloaded = EditableSheet.from("Test_Trainer", edited.updatedJson(), json)
        for (key in SheetStats.allDotKeys.filter { it != "skills.fight.throw" }) {
            assertEquals(key, original.dotStats[key], reloaded.dotStats[key])
        }
        assertEquals(2, reloaded.dotStats.getValue("skills.fight.throw").count { it })
        assertEquals(original.hpActual, reloaded.hpActual)
        assertEquals(original.hpTotal, reloaded.hpTotal)
        assertEquals(original.money, reloaded.money)
    }

    @Test
    fun weaponUsesExistingWeaponsStorageKey() {
        val key = SheetStats.skillGroupsFor(false).getValue("Fight").entries.single { it.value == "Weapon" }.key
        assertEquals("skills.fight.weapons", key)
        val original = EditableSheet.blank(false).copy(dotStats = mapOf(key to List(5) { it < 4 }))
        val saved = original.updatedJson()
        assertEquals(false, saved["skills"]!!.jsonObject["fight"]!!.jsonObject.containsKey("weapon"))
        val reloaded = EditableSheet.from("Trainer", saved, json)
        assertEquals(4, reloaded.dotStats.getValue(key).count { it })
    }

    @Test
    fun editingPokemonPreservesHiddenLegacySkillValues() {
        val visible = SheetStats.skillGroupsFor(true).values.flatMap { it.keys }.toSet()
        val hidden = SheetStats.skillGroups.values.flatMap { it.keys }.filter { it !in visible }
        val original = EditableSheet.blank(true).copy(dotStats = SheetStats.allDotKeys.associateWith { List(5) { index -> index < 3 } })
        val loaded = EditableSheet.from("Test_Pokemon", original.updatedJson(), json)
        val edited = loaded.copy(dotStats = loaded.dotStats + ("skills.fight.brawl" to List(5) { it < 2 }))
        val reloaded = EditableSheet.from("Test_Pokemon", edited.updatedJson(), json)
        hidden.forEach { key -> assertEquals(key, original.dotStats[key], reloaded.dotStats[key]) }
        assertEquals(2, reloaded.dotStats.getValue("skills.fight.brawl").count { it })
    }

    @Test
    fun updatesKnownFieldsWithoutRemovingLegacyData() {
        val source = json.parseToJsonElement(
            """{
                "header":{"trainer_name":"Alice","pokemon_name":"","team":"A"},
                "quick_references":{"hp":{"actual":"2","total":"5"}},
                "unknown_section":{"keep_me":true}
            }""",
        )

        val edited = EditableSheet.from("Alice", source, json).copy(team = "B", money = "120")
        val result = edited.updatedJson()

        assertEquals("B", result["header"]!!.jsonObject["team"]!!.jsonPrimitive.content)
        assertEquals("120", result["header"]!!.jsonObject["money"]!!.jsonPrimitive.content)
        assertEquals("true", result["unknown_section"]!!.jsonObject["keep_me"]!!.jsonPrimitive.content)
    }

    @Test
    fun readsAndWritesTheCompleteLegacyStatistics() {
        val source = json.parseToJsonElement(
            """{
                "header":{
                    "trainer_name":"Alice",
                    "pokemon_name":"Sparky",
                    "pokename":"Pikachu",
                    "pokenr":"25",
                    "type1":{"text":"Electric","class":"type-electric"},
                    "happiness":[true,true,false,false,false]
                },
                "quick_references":{
                    "hp":{"actual":"4","total":"6"},
                    "def_spdef":{"actual":"2","total":"3"},
                    "will":{"actual":"1","total":"4"},
                    "initiative":"7",
                    "fatigue":[true,false,false,false,false]
                },
                "attributes":{"strength":[true,true,true,false,false]},
                "skills":{"fight":{"brawl":[true,true,false,false,false]}},
                "bag":{"items_left":["Corda"],"items_right":["Mappa"]},
                "moves":[{"id":"move1","value":"Thunder Shock"}]
            }""",
        )

        val sheet = EditableSheet.from("Alice_Sparky", source, json)
        assertEquals("Electric", sheet.primaryType)
        assertEquals(3, sheet.dotStats["attributes.strength"]!!.count { it })
        assertEquals("Thunder Shock", sheet.moves.first())
        assertEquals("Corda", sheet.bagItemsLeft.first())

        val result = sheet.copy(
            defenseActual = "3",
            moves = listOf("Thunderbolt", "Quick Attack"),
            dotStats = sheet.dotStats + ("skills.fight.brawl" to listOf(true, true, true, false, false)),
        ).updatedJson()

        assertEquals(
            "3",
            result["quick_references"]!!.jsonObject["def_spdef"]!!.jsonObject["actual"]!!.jsonPrimitive.content,
        )
        assertEquals(2, result["moves"]!!.jsonArray.size)
        assertEquals(
            3,
            result["skills"]!!.jsonObject["fight"]!!.jsonObject["brawl"]!!.jsonArray
                .count { it.jsonPrimitive.content == "true" },
        )
        assertEquals(
            "type-electric",
            result["header"]!!.jsonObject["type1"]!!.jsonObject["class"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun trainerResetRestoresResourcesButPreservesBothDefenses() {
        val sheet = EditableSheet.blank(isPokemon = false).copy(
            hpActual = "1",
            hpTotal = "7",
            defenseActual = "0",
            defenseTotal = "4",
            willActual = "2",
            willTotal = "5",
        )

        val reset = sheet.resetCurrentStats()

        assertEquals("7", reset.hpActual)
        assertEquals("0", reset.defenseActual)
        assertEquals("4", reset.defenseTotal)
        assertEquals("5", reset.willActual)
    }

    @Test
    fun pokemonResetRestoresResourcesButPreservesBothDefenses() {
        val sheet = EditableSheet.blank(isPokemon = true).copy(
            defenseActual = "1", defenseTotal = "4", hpActual = "0", hpTotal = "7",
            willActual = "1", willTotal = "5",
        )
        val reset = sheet.resetCurrentStats()
        assertEquals("1", reset.defenseActual)
        assertEquals("4", reset.defenseTotal)
        assertEquals("7", reset.hpActual)
        assertEquals("5", reset.willActual)
    }
}
