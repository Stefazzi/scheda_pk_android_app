package it.stefazzi.pokerolesheets.data

import kotlinx.serialization.json.*
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class RemoteCatalogTest {
    private fun fixture(): JsonObject = Json.parseToJsonElement("""{
      "source":{"rules_version":"3.0","pokemon_count":1,"move_count":1,"learnset_count":2},
      "pokemon":[{"rules_version":"3.0","id":"hatenna","name":"Hatenna","dex_number":856,"entry_kind":"pokemon",
       "primary_type":"Psychic","secondary_type":null,"base_hp":3,"recommended_rank":2,
       "strength_base":1,"strength_max":3,"dexterity_base":1,"dexterity_max":3,"vitality_base":2,"vitality_max":4,
       "special_base":2,"special_max":4,"insight_base":2,"insight_max":4,"ability_1":"Healer","ability_2":"Anticipation",
       "extra_data":{"DexCategory":"Calm Pokémon","DexDescription":"Catalog description.","Height":{"Meters":0.4},"Weight":{"Kilograms":3},"Evolutions":[{"From":"None"},{"To":"Hattrem","Kind":"Level","Speed":"Medium"}]}}],
      "moves":[{"rules_version":"3.0","id":"confusion","name":"Confusion","type":"Psychic","category":"Special",
       "power":"X","accuracy_1":"Insight","accuracy_2":"Channel","damage_1":"Special","damage_2":null,
       "target":"Foe","effect":"Effect","description":"Description"}],
      "learnsets":[{"rules_version":"3.0","pokemon_id":"hatenna","entry_index":1,"rank_id":1,"move_id":"confusion","special_rule":null},
       {"rules_version":"3.0","pokemon_id":"hatenna","entry_index":2,"rank_id":3,"move_id":"confusion","special_rule":null}]
    }""").jsonObject

    @Test fun parsesNumericRanksDynamicPowerAndForwardEvolutionOnly() {
        val full = RemoteCatalog.parse(fixture())
        val species = full.species.single()
        assertEquals(SheetRank.Rookie, species.suggestedRank)
        assertEquals("0.4 m", species.height)
        assertEquals("Calm Pokémon", species.dexCategory)
        assertEquals("Catalog description.", species.dexDescription)
        assertEquals("X", full.moves.getValue("confusion").power)
        assertEquals("Insight + Channel", full.moves.getValue("confusion").accuracy)
        assertEquals(listOf("Hattrem"), species.evolutions.map { it.targetName })
        assertEquals(1, species.availableMoves(SheetRank.Standard).size)
        assertEquals(2, species.moves.size) // Raw repetitions retained.
        assertEquals(SheetRank.Starter, species.availableMoves(SheetRank.Standard).single().rank)
    }
    @Test(expected = IllegalArgumentException::class) fun refusesIncompleteCache() {
        RemoteCatalog.parse(JsonObject(fixture() + ("learnsets" to JsonArray(emptyList()))))
    }
    @Test(expected = IllegalArgumentException::class) fun refusesBrokenMoveReference() {
        RemoteCatalog.parse(Json.parseToJsonElement(fixture().toString().replace("\"move_id\":\"confusion\"", "\"move_id\":\"missing\"")).jsonObject)
    }
    @Test(expected = IllegalArgumentException::class) fun refusesRankOutsideOneToEight() {
        RemoteCatalog.parse(Json.parseToJsonElement(fixture().toString().replace("\"rank_id\":1", "\"rank_id\":0")).jsonObject)
    }
    @Test fun specialRuleIsNotAnInventedMove() {
        val input = fixture().toString().replace("\"move_id\":\"confusion\",\"special_rule\":null", "\"move_id\":null,\"special_rule\":\"any_move\"")
        val species = RemoteCatalog.parse(Json.parseToJsonElement(input).jsonObject).species.single()
        assertEquals(SheetRank.Starter, species.anyMoveRank)
        assertTrue(species.moves.isEmpty())
    }

    // Optional integration fixture exported from a local PostgreSQL run of the delivered SQL.
    // Not committed: complete community dataset is downloaded by the app after authentication.
    @Test fun completeImportedSnapshotParsesAndResolvesCampaignEvolutions() {
        val path = System.getProperty("catalog.fixture")
        assumeTrue(path != null)
        val full = RemoteCatalog.parse(Json.parseToJsonElement(File(path!!).readText()).jsonObject)
        assertEquals(1199, full.species.size)
        assertEquals(894, full.moves.size)
        assertEquals(1025, full.species.map { it.number }.distinct().size)
        assertTrue(full.species.first { it.name == "Hatenna" }.dexDescription.isNotBlank())
        assertTrue(full.species.last().number.toInt() > 200)
        val expected = mapOf("Hatenna" to "Hattrem", "Shinx" to "Luxio", "Wimpod" to "Golisopod", "Poliwhirl" to "Politoed")
        expected.forEach { (from, to) ->
            val source = full.species.first { it.name == from }
            val target = full.species.first { it.name == to }
            assertTrue(source.evolutions.any { it.targetName == to })
            val sheet = source.prefill(EditableSheet.blank(true), SheetRank.Starter).copy(
                recordId = "existing", hpActual = "1", pokedexNumber = "# ${source.number.padStart(3, '0')}")
            val evolved = source.evolve(sheet, target, target.abilities.first())
            assertEquals(target.name, evolved.speciesName)
            assertEquals("1", evolved.hpActual)
        }
        assertEquals(SheetRank.Ace, full.species.first { it.name == "Mew" }.anyMoveRank)
    }
}
