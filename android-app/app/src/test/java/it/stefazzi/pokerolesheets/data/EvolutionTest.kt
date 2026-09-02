package it.stefazzi.pokerolesheets.data

import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class EvolutionTest {
    private val base = CorebookCatalog.parse(File("src/main/assets/pokerole/species-v3.json").readText())
        .first { it.name == "Poliwhirl" }
        .copy(evolutions = listOf(EvolutionReference("Politoed", "Trade · King's Rock"), EvolutionReference("Poliwrath", "Water Stone")))
    private val target = base.copy(name = "Politoed", number = "186", catalogId = "politoed", baseHp = 5,
        abilities = listOf("Water Absorb", "Damp"), height = "1.1 m", weight = "33.9 kg")
    private fun sheet(): EditableSheet = base.prefill(EditableSheet.blank(true), SheetRank.Starter).copy(
        storageKey = "original", recordId = "pokemon-1", trainerId = "trainer-1", ownerId = "owner-1", teamSlot = 2,
        pokemonName = "POLIWURL", trainerName = "Zeno", moves = listOf("Water Gun", "Hypnosis", "Custom move"),
        natureName = "Brave", background = "History", heldItem = "King's Rock", profilePicture = "custom.png",
        hpActual = "1", willActual = "2", abilityName = "Water Absorb", abilityDescription = "keep", abilityEffect = "effect",
    ).withRuleValue("custom", "keep")

    @Test fun evolvesTheSameIndividualWithoutResettingResourcesOrProgress() {
        val old = sheet()
        val evolved = base.evolve(old, target, "Water Absorb")
        assertEquals("Politoed", evolved.speciesName)
        assertEquals("186", evolved.pokedexNumber)
        assertEquals("7", evolved.hpTotal)
        assertEquals("1", evolved.hpActual)
        assertEquals("2", evolved.willActual)
        assertEquals(old.dotStats, evolved.dotStats)
        assertEquals(old.moves, evolved.moves)
        assertEquals(old.sheetRank, evolved.sheetRank)
        assertEquals(old.recordId, evolved.recordId)
        assertEquals(old.storageKey, evolved.storageKey)
        assertEquals(old.trainerId, evolved.trainerId)
        assertEquals(old.ownerId, evolved.ownerId)
        assertEquals(old.teamSlot, evolved.teamSlot)
        assertEquals(old.pokemonName, evolved.pokemonName)
        assertEquals(old.natureName, evolved.natureName)
        assertEquals(old.background, evolved.background)
        assertEquals(old.heldItem, evolved.heldItem)
        assertEquals(old.profilePicture, evolved.profilePicture)
        assertEquals(old.happiness, evolved.happiness)
        assertEquals(old.loyalty, evolved.loyalty)
        assertEquals("keep", evolved.abilityDescription)
        assertEquals(target, CorebookCatalog.find(listOf(base, target), evolved))
        val roundTrip = EditableSheet.from(old.storageKey, evolved.updatedJson(), Json)
        assertEquals("Politoed", roundTrip.speciesName)
        assertEquals(old.moves, roundTrip.moves)
        assertTrue(roundTrip.raw.toString().contains("keep"))
        assertEquals("Poliwhirl", old.speciesName) // Draft transformations never mutate their input.
    }

    @Test fun newAbilityDoesNotRetainTheOldAbilityDescription() {
        val evolved = base.evolve(sheet(), target, "Damp")
        assertEquals("Damp", evolved.abilityName)
        assertEquals("", evolved.abilityDescription)
        assertEquals("", evolved.abilityEffect)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsUnrelatedTarget() {
        base.evolve(sheet(), target.copy(name = "Mew"), "Damp")
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsUnsavedCapture() {
        base.evolve(sheet().copy(recordId = ""), target, "Damp")
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsTrainer() {
        base.evolve(sheet().copy(isPokemon = false), target, "Damp")
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsStaleSource() {
        base.evolve(sheet().copy(speciesName = "Shinx"), target, "Damp")
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsInvalidAbility() {
        base.evolve(sheet(), target, "Unknown")
    }
}
