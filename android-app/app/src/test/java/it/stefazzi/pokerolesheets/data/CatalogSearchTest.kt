package it.stefazzi.pokerolesheets.data

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class CatalogSearchTest {
    private fun pokemon(id: String, trainer: String, nickname: String, species: String = "Hatenna") =
        EditableSheet.blank(true).copy(recordId = id, trainerName = trainer, pokemonName = nickname,
            speciesName = species, pokedexNumber = "# 856")

    @Test fun ordersByTrainerThenNicknameOrSpeciesWithoutMutatingInput() {
        val original = listOf(pokemon("1", "Zeno", "A"), pokemon("2", "Elia", "Zeta"),
            pokemon("3", "elia", ""), pokemon("4", "Elia", "Alfa"))
        val sorted = sortPokemonSheets(original)
        assertEquals(listOf("4", "3", "2", "1"), sorted.map { it.recordId })
        assertEquals(listOf("1", "2", "3", "4"), original.map { it.recordId })
    }
    @Test fun accentsAndCaseDoNotChangeAlphabeticGrouping() {
        val rows = listOf(pokemon("1", "Élia", "zeta"), pokemon("2", "elia", "Alfa"), pokemon("3", "Zeno", "aaa"))
        assertEquals(listOf("2", "1", "3"), sortPokemonSheets(rows).map { it.recordId })
    }
    @Test fun searchesNicknameSpeciesTrainerAndLegacyDexNumber() {
        val row = pokemon("1", "Élia Aster", "Fiore")
        listOf("fiore", "HATENNA", "aster", "elia", "0856", "# 856", "elia hatenna").forEach {
            assertTrue(it, row.matchesSearch(it))
        }
        assertFalse(row.matchesSearch("Zeno"))
        assertFalse(row.matchesSearch("8560"))
        assertFalse(row.matchesSearch("elia shinx"))
    }
    @Test fun blankSearchShowsAllAndFilteringPreservesTrainerOrder() {
        val rows = listOf(pokemon("1", "Zeno", ""), pokemon("2", "Elia", ""))
        assertTrue(rows.all { it.matchesSearch("  ") })
        assertEquals(listOf("2", "1"), sortPokemonSheets(rows).filter { it.matchesSearch("hatenna") }.map { it.recordId })
        // Only the supplied, already authorized list is searched; no extra records are fetched.
        assertEquals(1, sortPokemonSheets(rows.take(1)).filter { it.matchesSearch("hatenna") }.size)
    }
    @Test fun trainerSearchIncludesTeam() {
        val row = EditableSheet.blank(false).copy(trainerName = "Macharius Von Valancius", team = "Squadra Blu")
        assertTrue(row.matchesSearch("valanc blu"))
        assertFalse(row.matchesSearch("rosso"))
    }
    @Test fun duplicateNamesHaveDeterministicOrder() {
        val rows = listOf(pokemon("b", "Elia", "Hatenna"), pokemon("a", "Elia", "Hatenna"))
        assertEquals(listOf("a", "b"), sortPokemonSheets(rows).map { it.recordId })
    }
    @Test fun pokedexSearchIncludesNumberFormAndType() {
        val reference = CorebookCatalog.parse(File("src/main/assets/pokerole/species-v3.json").readText()).first()
        listOf("hatenna", "#0856", "psychic", "standard", "hat psychic").forEach {
            assertTrue(it, reference.matchesSearch(it))
        }
        assertFalse(reference.matchesSearch("water"))
        assertFalse(reference.matchesSearch("85"))
    }

    @Test fun captureUsesSelectedTrainerIdEvenWhenNamesAreIdentical() {
        val first = EditableSheet.blank(false).copy(recordId = "trainer-a", trainerName = "Elia")
        val second = first.copy(recordId = "trainer-b")
        val draft = EditableSheet.blank(true)
        assertEquals("trainer-a", draft.withCaptureTrainer(first).trainerId)
        assertEquals("trainer-b", draft.withCaptureTrainer(second).trainerId)
    }

    @Test fun changingCaptureTrainerPreservesIndividualDataAndClearsOldSlot() {
        val trainer = EditableSheet.blank(false).copy(recordId = "trainer-b", trainerName = "Zeno")
        val draft = EditableSheet.blank(true).copy(
            trainerId = "trainer-a", trainerName = "Elia", teamSlot = 2,
            speciesName = "Hatenna", pokemonName = "Fiore", moves = listOf("Confusion"),
            dotStats = mapOf("attributes.strength" to listOf(true, true, false)),
        ).withRuleValue("rank", "Rookie")
        val selected = draft.withCaptureTrainer(trainer)
        assertEquals(draft.copy(trainerId = "trainer-b", trainerName = "Zeno", teamSlot = null), selected)
    }

    @Test fun prefillPreservesSelectedCaptureTrainer() {
        val species = CorebookCatalog.parse(File("src/main/assets/pokerole/species-v3.json").readText()).first()
        val trainer = EditableSheet.blank(false).copy(recordId = "trainer-a", trainerName = "Elia")
        val result = species.prefill(EditableSheet.blank(true).withCaptureTrainer(trainer), SheetRank.Starter)
        assertEquals(trainer.recordId, result.trainerId)
        assertEquals(trainer.trainerName, result.trainerName)
    }

    @Test fun captureSelectorCannotTransferSavedPokemonOrEditTrainerSheets() {
        val trainer = EditableSheet.blank(false).copy(recordId = "trainer-a", trainerName = "Elia")
        assertThrows(IllegalArgumentException::class.java) {
            EditableSheet.blank(true).copy(recordId = "saved-pokemon").withCaptureTrainer(trainer)
        }
        assertThrows(IllegalArgumentException::class.java) {
            EditableSheet.blank(false).withCaptureTrainer(trainer)
        }
    }

    @Test fun captureRequiresSavedTrainerNotAnUnsavedSheetOrPokemon() {
        val draft = EditableSheet.blank(true)
        assertThrows(IllegalArgumentException::class.java) {
            draft.withCaptureTrainer(EditableSheet.blank(false))
        }
        assertThrows(IllegalArgumentException::class.java) {
            draft.withCaptureTrainer(pokemon("pokemon-id", "Elia", "Fiore"))
        }
    }
}
