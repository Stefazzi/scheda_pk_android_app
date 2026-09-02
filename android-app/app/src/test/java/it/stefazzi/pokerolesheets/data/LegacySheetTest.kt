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
    fun resetsCurrentStatsToTheirMaximumValues() {
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
        assertEquals("4", reset.defenseActual)
        assertEquals("5", reset.willActual)
    }
}
