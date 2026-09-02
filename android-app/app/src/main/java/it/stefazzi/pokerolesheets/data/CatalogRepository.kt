package it.stefazzi.pokerolesheets.data

import android.content.Context
import it.stefazzi.pokerolesheets.data.model.PokemonSpecies
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale

class CatalogRepository(
    private val context: Context,
    private val json: Json,
) {
    fun loadPokemon(): List<PokemonSpecies> = context.assets
        .open("legacy/json/poke_list.json")
        .bufferedReader()
        .use { json.decodeFromString<List<PokemonSpecies>>(it.readText()) }

    fun loadMoveTypes(): Map<String, String> = context.assets
        .list(MOVES_PATH)
        .orEmpty()
        .asSequence()
        .filter { it.endsWith(".json", ignoreCase = true) }
        .mapNotNull { fileName ->
            runCatching {
                val move = context.assets.open("$MOVES_PATH/$fileName").bufferedReader().use {
                    json.parseToJsonElement(it.readText()).jsonObject
                }
                val name = move["Name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val type = move["Type"]?.jsonPrimitive?.contentOrNull.orEmpty()
                normalizeMoveName(name).takeIf(String::isNotBlank)?.let { it to type }
            }.getOrNull()
        }
        .toMap()

    private companion object {
        const val MOVES_PATH = "legacy/json/Moves"
    }
}

fun normalizeMoveName(value: String): String = value
    .lowercase(Locale.ROOT)
    .filter(Char::isLetterOrDigit)
