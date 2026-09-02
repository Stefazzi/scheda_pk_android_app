package it.stefazzi.pokerolesheets.data

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class CorebookCatalogTest {
    private val catalog = CorebookCatalog.parse(File("src/main/assets/pokerole/species-v3.json").readText())

    @Test fun referencesMatchTheFourInspectedManualEntries() {
        assertEquals(4, catalog.size)
        val expected = mapOf(
            "Hatenna" to listOf(1 to 3, 1 to 3, 2 to 4, 2 to 4, 2 to 4),
            "Shinx" to listOf(2 to 4, 2 to 4, 1 to 3, 1 to 3, 1 to 3),
            "Wimpod" to listOf(1 to 3, 2 to 5, 1 to 3, 1 to 3, 1 to 3),
            "Poliwhirl" to listOf(2 to 4, 2 to 5, 2 to 4, 2 to 4, 2 to 4),
        )
        catalog.forEach { species ->
            assertEquals(expected[species.name], SheetStats.attributes.keys.map {
                species.attributes.getValue(it.substringAfter('.')).let { ref -> ref.base to ref.limit }
            })
            assertEquals(species.moves.size, species.moves.map { it.name }.distinct().size)
            assertTrue(species.abilities.isNotEmpty())
        }
    }

    @Test fun prefillKeepsOwnershipAndUsesBaseNotRankAllocatedAttributes() {
        val draft = EditableSheet.blank(true).copy(trainerId = "trainer-1", trainerName = "Elia", pokemonName = "Nickname")
        val hatenna = catalog.first { it.name == "Hatenna" }
        val filled = hatenna.prefill(draft, SheetRank.Rookie)
        assertEquals("trainer-1", filled.trainerId)
        assertEquals("Nickname", filled.pokemonName)
        assertEquals("856", filled.pokedexNumber)
        assertEquals("Psychic", filled.primaryType)
        assertEquals("5", filled.hpTotal)
        assertEquals("5", filled.willTotal)
        assertEquals("5", filled.hpActual)
        assertEquals(1, filled.dotStats.getValue("attributes.strength").count { it })
        assertTrue(filled.moves.isEmpty())
        assertEquals(SheetRank.Rookie, filled.sheetRank)
        assertEquals(3, filled.baseHpValue)
        assertEquals("Standard", filled.speciesForm)
        assertEquals(2, filled.happiness.count { it })
        assertEquals(2, filled.loyalty.count { it })
        SheetStats.socialAttributes.keys.forEach { assertEquals(1, filled.dotStats.getValue(it).count { it }) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun prefillCannotResetAnExistingPokemon() {
        catalog.first().prefill(EditableSheet.blank(true).copy(recordId = "saved"), SheetRank.Starter)
    }

    @Test(expected = IllegalArgumentException::class)
    fun prefillCannotConvertATrainerIntoAPokemon() {
        catalog.first().prefill(EditableSheet.blank(false), SheetRank.Starter)
    }

    @Test fun rangesDistinguishSpeciesTrainerSocialsAndSkills() {
        val trainer = EditableSheet.blank(false).withRuleValue("rank", "Rookie")
        assertFalse(trainer.attributeFields().containsKey("attributes.special"))
        assertEquals(1..5, trainer.statRange("attributes.strength", null))
        assertEquals(1..5, trainer.statRange("social_attributes.cool", null))
        assertEquals(0..2, trainer.statRange("skills.fight.brawl", null))
        val hatenna = catalog.first { it.name == "Hatenna" }
        val pokemon = hatenna.prefill(EditableSheet.blank(true), SheetRank.Standard)
        assertEquals(1..3, pokemon.statRange("attributes.dexterity", hatenna))
        assertEquals(1..12, pokemon.statRange("attributes.dexterity", null))
        assertTrue(pokemon.attributeFields().containsKey("attributes.special"))
        assertEquals(1..7, trainer.withRuleValue("rank", "Champion").statRange("attributes.strength", null))
        assertEquals(1..5, pokemon.withRuleValue("rank", "Champion").statRange("attributes.dexterity", hatenna))
        assertEquals(0..5, pokemon.withRuleValue("rank", "Master").statRange("skills.fight.brawl", hatenna))
    }

    @Test fun identificationDoesNotConfuseNicknameDifferentSpeciesOrForms() {
        val pokemon = EditableSheet.blank(true).copy(speciesName = "Poliwhirl", pokedexNumber = "061", pokemonName = "POLIWURL")
        assertEquals("Poliwhirl", CorebookCatalog.find(catalog, pokemon)?.name)
        assertNull(CorebookCatalog.find(catalog, pokemon.copy(speciesName = "Shinx")))
        assertNull(CorebookCatalog.find(catalog, pokemon.withRuleValue("form", "Custom")))
        assertNull(CorebookCatalog.find(catalog, EditableSheet.blank(true)))
        assertNull(CorebookCatalog.find(catalog, pokemon.copy(isPokemon = false)))
    }

    @Test fun metadataAndTwelveDotsRoundTripWithoutRemovingLegacyData() {
        val source = Json.parseToJsonElement("""{"header":{"pokename":"Shinx"},"extra":{"keep":42},"pokerole":{"custom":"kept"}}""")
        val sheet = EditableSheet.from("old", source, Json).withRuleValue("rank", "Advanced")
            .withRuleValue("base_hp", "3").withRuleValue("form", "Standard")
        val edited = sheet.copy(dotStats = sheet.dotStats + ("attributes.strength" to List(12) { true }))
        val loaded = EditableSheet.from("old", edited.updatedJson(), Json)
        assertEquals(12, loaded.dotStats.getValue("attributes.strength").count { it })
        assertEquals(SheetRank.Advanced, loaded.sheetRank)
        assertEquals(3, loaded.baseHpValue)
        assertEquals(source.jsonObject["extra"], loaded.raw["extra"])
        assertTrue(loaded.raw.toString().contains("kept"))
        // Merely displaying a reference/range must not clamp legacy values.
        loaded.statRange("attributes.strength", catalog.first { it.name == "Shinx" })
        assertEquals(12, EditableSheet.from("old", loaded.updatedJson(), Json).dotStats.getValue("attributes.strength").count { it })
    }

    @Test fun legacyWebPokedexFormattingIsRecognizedWithoutChangingTheSheet() {
        listOf("856", "0856", "#856", "# 856", "  # 0856  ", "#\u00a00856").forEach { number ->
            val sheet = EditableSheet.blank(true).copy(speciesName = "hatenna", pokedexNumber = number)
            assertEquals("Hatenna for $number", "Hatenna", CorebookCatalog.find(catalog, sheet)?.name)
            assertEquals(number, sheet.pokedexNumber)
        }
        val poliwhirl = EditableSheet.blank(true).copy(speciesName = "poliwhirl", pokedexNumber = "# 061")
        assertEquals("Poliwhirl", CorebookCatalog.find(catalog, poliwhirl)?.name)
    }

    @Test fun legacyFormattingDoesNotHideConflictingOrMalformedIdentity() {
        val sheet = EditableSheet.blank(true).copy(speciesName = "Hatenna")
        listOf("# 061", "856/857", "# nope", "#", "0", "999999999999").forEach { number ->
            assertNull(number, CorebookCatalog.find(catalog, sheet.copy(pokedexNumber = number)))
        }
        assertNull(CorebookCatalog.find(catalog, sheet.copy(pokedexNumber = "# 856").withRuleValue("form", "Custom")))
    }

    @Test fun maximumCalculationDoesNotHealOrChangeDefense() {
        val reference = catalog.first { it.name == "Hatenna" }
        val draft = reference.prefill(EditableSheet.blank(true), SheetRank.Master)
            .copy(hpActual = "1", willActual = "0", defenseActual = "2", defenseTotal = "7")
        val updated = draft.recalculateMaximums(reference)
        assertEquals("8", updated.hpTotal)
        assertEquals("8", updated.willTotal)
        assertEquals("1", updated.hpActual)
        assertEquals("0", updated.willActual)
        assertEquals("2", updated.defenseActual)
        assertEquals("7", updated.defenseTotal)
        assertEquals("99", EditableSheet.blank(true).copy(hpTotal = "99").recalculateMaximums(null).hpTotal)
    }

    @Test fun moveSuggestionsRespectRankAndDoNotPickMovesForThePlayer() {
        val wimpod = catalog.first { it.name == "Wimpod" }
        assertEquals(listOf("Struggle Bug", "Sand Attack"), wimpod.availableMoves(SheetRank.Starter).map { it.name })
        assertFalse(wimpod.availableMoves(SheetRank.Expert).any { it.name == "Aqua Jet" })
        assertTrue(wimpod.availableMoves(SheetRank.Ace).any { it.name == "Aqua Jet" })
    }

    @Test fun newTrainersStartAtOneAndAttributesUseEnglishLabels() {
        val trainer = EditableSheet.blank(false)
        trainer.attributeFields().keys.forEach { assertEquals(1, trainer.dotStats.getValue(it).count { it }) }
        assertEquals(listOf("Strength", "Dexterity", "Vitality", "Special", "Insight"), SheetStats.attributes.values.toList())
        assertEquals(listOf("Tough", "Cool", "Beauty", "Cute", "Clever"), SheetStats.socialAttributes.values.toList())
    }

    @Test fun invalidBaseHpIsVisibleAndDoesNotSilentlyUseSpeciesDefault() {
        val reference = catalog.first()
        val sheet = reference.prefill(EditableSheet.blank(true), SheetRank.Starter)
            .withRuleValue("base_hp", "").copy(hpTotal = "99")
        assertEquals("", sheet.baseHpText)
        assertNull(sheet.baseHpValue)
        assertEquals("99", sheet.recalculateMaximums(reference).hpTotal)
    }

    @Test fun readingAnOldZeroAttributeDoesNotAutomaticallyRaiseIt() {
        val source = Json.parseToJsonElement("""{"attributes":{"strength":[false,false,false,false,false]}}""")
        val loaded = EditableSheet.from("old", source, Json)
        assertEquals(0, loaded.dotStats.getValue("attributes.strength").count { it })
        assertEquals(source.jsonObject["attributes"]!!.jsonObject["strength"], loaded.updatedJson()["attributes"]!!.jsonObject["strength"])
    }
}
