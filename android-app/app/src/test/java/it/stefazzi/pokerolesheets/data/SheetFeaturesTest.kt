package it.stefazzi.pokerolesheets.data

import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SheetFeaturesTest {
    private val references = SheetReferences.parse(
        File("src/main/assets/pokerole/abilities-v3.json").readText(),
        File("src/main/assets/pokerole/natures-v3.json").readText(),
        File("src/main/assets/pokerole/sprites.json").readText())
    private val species = CorebookCatalog.parse(File("src/main/assets/pokerole/species-v3.json").readText()).first()
    private fun sample(pokemon: Boolean = true) = EditableSheet.blank(pokemon).copy(dotStats = mapOf(
        "attributes.vitality" to List(2) { true }, "attributes.insight" to List(4) { true },
        "attributes.dexterity" to List(3) { true }, "skills.survival.alert" to List(1) { true },
        "skills.fight.evasion" to List(2) { true },
    ), hpActual = "1", willActual = "0").withRuleValue("rank", "Starter")

    @Test fun campaignFormulasUseIndependentDefensesAndNoInitiativeDie() {
        val sheet = sample().withRuleValue("base_hp", "3").recalculateMaximums(null)
        assertEquals(listOf("5", "7", "2", "4", "4", "5"),
            listOf(sheet.hpTotal, sheet.willTotal, sheet.defenseActual, sheet.defenseTotal, sheet.initiative, sheet.evasion))
        assertEquals("1", sheet.hpActual); assertEquals("0", sheet.willActual)
    }
    @Test fun alphaAddsTwoOnceAndShinyDoesNotChangeStats() {
        val normal = sample().recalculateMaximums(species)
        val alpha = normal.withRuleValue("alpha", "true").recalculateMaximums(species)
        assertEquals(normal.hpTotal.toInt() + 2, alpha.hpTotal.toInt())
        assertEquals(alpha, alpha.recalculateMaximums(species))
        assertEquals(normal, alpha.withRuleValue("alpha", "false").recalculateMaximums(species).copy(raw = normal.raw))
        val shiny = alpha.withRuleValue("shiny", "true").recalculateMaximums(species)
        assertEquals(alpha.hpTotal, shiny.hpTotal); assertTrue(shiny.isShiny)
    }
    @Test fun trainerUsesBaseFourAndIgnoresPokemonFlags() {
        val trainer = sample(false).withRuleValue("alpha", "true").withRuleValue("shiny", "true").recalculateMaximums(null)
        assertEquals("6", trainer.hpTotal); assertFalse(trainer.isAlpha); assertFalse(trainer.isShiny)
    }
    @Test fun masterAndChampionAddTraitAndEvasionBonusesOnlyOnce() {
        for (rank in listOf("Master", "Champion")) {
            val sheet = sample().withRuleValue("rank", rank).recalculateMaximums(species)
            assertEquals(listOf("8", "10", "5", "7", "7", "7"),
                listOf(sheet.hpTotal, sheet.willTotal, sheet.defenseActual, sheet.defenseTotal, sheet.initiative, sheet.evasion))
            assertEquals(sheet, sheet.recalculateMaximums(species))
        }
    }
    @Test fun flagsSurvivePrefillEvolutionAndJsonRoundtrip() {
        val filled = species.prefill(sample().withRuleValue("alpha", "true").withRuleValue("shiny", "true"), SheetRank.Starter)
        assertTrue(filled.isAlpha); assertTrue(filled.isShiny); assertEquals("7", filled.hpTotal)
        val loaded = EditableSheet.from("pokemon", filled.updatedJson(), Json)
        assertTrue(loaded.isAlpha); assertTrue(loaded.isShiny)
        val evolved = species.copy(evolutions = listOf(EvolutionReference("Test evolution", ""))).evolve(
            filled.copy(recordId = "saved"), species.copy(name = "Test evolution"), species.abilities.first())
        assertTrue(evolved.isAlpha); assertTrue(evolved.isShiny)
    }
    @Test fun referencesHaveCompleteNamesAndReplaceOldSelection() {
        assertEquals(305, references.abilities.size); assertEquals(25, references.natures.size)
        val ability = requireNotNull(references.ability("anticipation"))
        val nature = requireNotNull(references.nature("TIMID"))
        val sheet = sample().copy(abilityEffect = "old", natureDescription = "old").selectAbility(ability).selectNature(nature)
        assertEquals(ability.effect, sheet.abilityEffect); assertEquals(nature.description, sheet.natureDescription)
        val loaded = EditableSheet.from("test", sheet.updatedJson(), Json)
        assertEquals(sheet.abilityDescription, loaded.abilityDescription)
        assertEquals(sheet.natureConfiguration, loaded.natureConfiguration)
        assertNull(references.ability("Anticipation & Healer"))
    }
    @Test fun regionalMegaAndShinySpritesDoNotUseNationalBase() {
        val alola = references.spritePaths("26", "Raichu", "Alolan Form", false)
        assertTrue(alola.isNotEmpty()); assertTrue(alola.first().endsWith("/10100.png"))
        val mega = references.spritePaths("6", "Charizard (Mega X Form)", "Mega X Form", true)
        assertTrue(mega.isNotEmpty()); assertTrue(mega.all { it.contains("/shiny/") }); assertTrue(mega.first().endsWith("/10034.png"))
        assertTrue(references.spritePaths("128", "Tauros (Paldean Form) (Aqua Form)", "Paldean Form) (Aqua Form", false).first().endsWith("/10252.png"))
    }
    @Test fun unknownFormsAndMalformedDexNeverInventSprite() {
        assertTrue(references.spritePaths("26", "Raichu", "Custom Form", false).isEmpty())
        assertTrue(references.spritePaths("26/27", "Raichu", "Standard", false).isEmpty())
        assertTrue(references.spritePaths("# 856", "Hatenna", "Standard", true).isNotEmpty())
    }
    @Test fun releaseRequiresSavedPokemonAndParentIds() {
        val sheet = sample().copy(recordId = "pokemon-id", trainerId = "trainer-id")
        assertEquals("pokemon-id" to "trainer-id", sheet.releaseIdentifiers())
        assertThrows(IllegalArgumentException::class.java) { sheet.copy(recordId = "").releaseIdentifiers() }
        assertThrows(IllegalArgumentException::class.java) { sheet.copy(trainerId = null).releaseIdentifiers() }
        assertThrows(IllegalArgumentException::class.java) { sheet.copy(isPokemon = false).releaseIdentifiers() }
    }
}
