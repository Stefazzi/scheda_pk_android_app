package it.stefazzi.pokerolesheets.data

import java.text.Normalizer
import java.util.Locale

private fun searchable(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
    .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

private fun matchesQuery(query: String, values: List<String>): Boolean {
    val words = values.flatMap { searchable(it).split(' ') }.filter(String::isNotBlank)
    return searchable(query).split(' ').filter(String::isNotBlank).all { token ->
        val number = token.toIntOrNull()
        if (number != null) words.any { it.toIntOrNull() == number }
        else words.any { it.contains(token) }
    }
}

fun EditableSheet.matchesSearch(query: String): Boolean = matchesQuery(query, listOf(
    displayName, trainerName, pokemonName, speciesName, pokedexNumber, team,
))

fun CorebookSpecies.matchesSearch(query: String): Boolean = matchesQuery(query,
    listOf(name, number, form) + types)

/** Non-mutating ordering: trainer first, then nickname (species if unnamed). */
fun sortPokemonSheets(sheets: List<EditableSheet>): List<EditableSheet> = sheets.sortedWith(
    compareBy<EditableSheet> { searchable(it.trainerName) }
        .thenBy { searchable(it.displayName) }
        .thenBy { it.recordId }
        .thenBy { it.storageKey },
)

/** Selecting a parent is only permitted for a new capture, never a transfer of a saved Pokemon. */
fun EditableSheet.withCaptureTrainer(trainer: EditableSheet): EditableSheet {
    require(isPokemon && recordId.isBlank()) { "La scelta allenatore è riservata alle nuove catture" }
    require(!trainer.isPokemon && trainer.recordId.isNotBlank()) { "Seleziona una scheda allenatore salvata" }
    return copy(trainerId = trainer.recordId, trainerName = trainer.trainerName, teamSlot = null)
}
