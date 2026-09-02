package it.stefazzi.pokerolesheets.data

import android.content.Context
import android.util.AtomicFile
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import java.io.File
import it.stefazzi.pokerolesheets.BuildConfig
import it.stefazzi.pokerolesheets.data.model.PokemonSpecies
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale

class CatalogRepository(
    private val context: Context,
    private val json: Json,
    private val client: SupabaseClient? = null,
) {
    // No user sheets/tokens in this cache; isolate it by configured Supabase project.
    private val cache = AtomicFile(File(context.filesDir, "catalog-3-${BuildConfig.SUPABASE_URL.hashCode()}.json"))

    fun loadCachedCatalog(): FullCatalog? = runCatching {
        cache.openRead().bufferedReader().use { RemoteCatalog.parse(json.parseToJsonElement(it.readText()).jsonObject) }
    }.getOrNull()

    suspend fun downloadCatalog(onProgress: (String) -> Unit): FullCatalog = withContext(Dispatchers.IO) {
        val db = checkNotNull(client) { "Supabase non configurato" }
        val source = db.from("catalog_sources").select {
            filter { eq("rules_version", RemoteCatalog.VERSION) }
        }.decodeList<JsonObject>().single()
        val pokemonCount = source.integer("pokemon_count").also { require(it in 1..10000) }
        val movesCount = source.integer("move_count").also { require(it in 1..5000) }
        val linksCount = source.integer("learnset_count").also { require(it in 1..200000) }
        suspend fun pages(table: String, count: Int, orderColumns: List<String>): JsonArray {
            val result = mutableListOf<JsonObject>()
            while (result.size < count) {
                coroutineContext.ensureActive()
                val offset = result.size.toLong()
                val page = db.from(table).select {
                    filter { eq("rules_version", RemoteCatalog.VERSION) }
                    orderColumns.forEach { order(it, Order.ASCENDING) }
                    range(offset..minOf(offset + 999L, count.toLong() - 1))
                }.decodeList<JsonObject>()
                require(page.isNotEmpty()) { "Importazione incompleta: $table (${result.size}/$count)" }
                result += page
                onProgress("$table: ${result.size} / $count")
            }
            require(result.size == count)
            return JsonArray(result)
        }
        val root = JsonObject(mapOf(
            "source" to source,
            "pokemon" to pages("catalog_pokemon", pokemonCount, listOf("id")),
            "moves" to pages("catalog_moves", movesCount, listOf("id")),
            "learnsets" to pages("catalog_learnsets", linksCount, listOf("pokemon_id", "entry_index")),
        ))
        val parsed = RemoteCatalog.parse(root)
        coroutineContext.ensureActive()
        val output = cache.startWrite()
        try {
            output.write(root.toString().toByteArray(Charsets.UTF_8))
            cache.finishWrite(output)
        } catch (error: Throwable) {
            cache.failWrite(output)
            throw error
        }
        parsed
    }

    fun loadCorebookSpecies(): List<CorebookSpecies> = context.assets
        .open("pokerole/species-v3.json").bufferedReader().use { CorebookCatalog.parse(it.readText()) }

    fun loadSheetReferences(): SheetReferences {
        fun asset(name: String) = context.assets.open("pokerole/$name").bufferedReader().use { it.readText() }
        return SheetReferences.parse(asset("abilities-v3.json"), asset("natures-v3.json"), asset("sprites.json"))
    }

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
