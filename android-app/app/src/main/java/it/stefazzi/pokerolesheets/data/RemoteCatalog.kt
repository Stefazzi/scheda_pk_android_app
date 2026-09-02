package it.stefazzi.pokerolesheets.data

import kotlinx.serialization.json.*

data class CatalogMove(
    val id: String, val name: String, val type: String, val category: String,
    val power: String, val accuracy: String, val damage: String, val target: String,
    val effect: String, val description: String,
)

data class FullCatalog(val species: List<CorebookSpecies>, val moves: Map<String, CatalogMove>)

/** Pure conversion: also validates a complete downloaded/cache snapshot before publishing it. */
object RemoteCatalog {
    const val VERSION = "3.0"
    fun parse(root: JsonObject): FullCatalog {
        val source = root.getValue("source").jsonObject
        require(source.text("rules_version") == VERSION)
        val pokemon = root.getValue("pokemon").jsonArray.map { it.jsonObject }
        val rows = root.getValue("moves").jsonArray.map { it.jsonObject }
        val links = root.getValue("learnsets").jsonArray.map { it.jsonObject }
        require(pokemon.size == source.integer("pokemon_count")) { "Catalogo specie incompleto" }
        require(rows.size == source.integer("move_count")) { "Catalogo mosse incompleto" }
        require(links.size == source.integer("learnset_count")) { "Catalogo apprendimento incompleto" }
        require((pokemon + rows + links).all { it.text("rules_version") == VERSION })
        val movesById = rows.associate { row ->
            row.text("id") to CatalogMove(
                id = row.text("id"), name = row.text("name"), type = row.text("type"),
                category = row.text("category"), power = row.text("power"),
                accuracy = listOf(row.text("accuracy_1"), row.text("accuracy_2")).filter(String::isNotBlank).joinToString(" + "),
                damage = listOf(row.text("damage_1"), row.text("damage_2")).filter(String::isNotBlank).joinToString(" + "),
                target = row.text("target"), effect = row.text("effect"), description = row.text("description"),
            )
        }
        require(movesById.size == rows.size)
        val speciesIds = pokemon.map { it.text("id") }.toSet()
        require(speciesIds.size == pokemon.size)
        require(links.map { it.text("pokemon_id") to it.integer("entry_index") }.distinct().size == links.size)
        links.forEach {
            require(it.text("pokemon_id") in speciesIds)
            rank(it.integer("rank_id"))
            require((it.text("move_id") in movesById && it.text("special_rule").isBlank()) ||
                (it.text("move_id").isBlank() && it.text("special_rule") == "any_move"))
        }
        val grouped = links.groupBy { it.text("pokemon_id") }
        val species = pokemon.filter { it.text("entry_kind") != "egg" }.map { row ->
            val extra = row.getValue("extra_data").jsonObject
            val name = row.text("name")
            val learnset = grouped[row.text("id")].orEmpty().sortedBy { it.integer("entry_index") }
            CorebookSpecies(
                name = name, number = row.integer("dex_number").toString(),
                form = if ('(' in name) name.substringAfter('(').removeSuffix(")") else "Standard",
                types = listOf(row.text("primary_type"), row.text("secondary_type")).filter(String::isNotBlank),
                height = extra.measure("Height", "Meters", "m"), weight = extra.measure("Weight", "Kilograms", "kg"),
                baseHp = row.integer("base_hp"), suggestedRank = rank(row.integer("recommended_rank")),
                attributes = SheetStats.attributes.keys.associate { path ->
                    val key = path.substringAfter('.')
                    key to AttributeReference(row.integer("${key}_base"), row.integer("${key}_max")).also {
                        require(it.base in 1..it.limit && it.limit <= 12)
                    }
                },
                abilities = listOf(row.text("ability_1"), row.text("ability_2")).filter(String::isNotBlank).distinct(),
                moves = learnset.filter { it.text("move_id").isNotBlank() }.map {
                    val move = movesById.getValue(it.text("move_id"))
                    MoveReference(move.name, rank(it.integer("rank_id")), move.type)
                },
                sourcePage = 0, catalogId = row.text("id"),
                evolutions = (extra["Evolutions"] as? JsonArray).orEmpty().mapNotNull { element ->
                    val evolution = element.jsonObject
                    evolution.text("To").takeIf(String::isNotBlank)?.let { target ->
                        EvolutionReference(target, evolution.entries.filter { it.key != "To" && it.key != "From" }
                            .joinToString(" · ") { "${it.key}: ${(it.value as? JsonPrimitive)?.contentOrNull ?: it.value}" })
                    }
                }.distinct(),
                anyMoveRank = learnset.filter { it.text("special_rule") == "any_move" }
                    .minOfOrNull { it.integer("rank_id") }?.let(::rank),
                dexCategory = extra.text("DexCategory"),
                dexDescription = extra.text("DexDescription"),
            )
        }.sortedWith(compareBy<CorebookSpecies> { it.number.toInt() }.thenBy { it.name })
        val moves = movesById.values.associateBy { normalizeMoveName(it.name) }
        require(moves.size == rows.size) { "Nomi mosse ambigui" }
        return FullCatalog(species, moves)
    }

    private fun rank(number: Int): SheetRank {
        require(number in 1..8) { "Rank non valido: $number" }
        return SheetRank.entries[number - 1]
    }
}

internal fun JsonObject.text(key: String): String = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
internal fun JsonObject.integer(key: String): Int = getValue(key).jsonPrimitive.int
private fun JsonObject.measure(key: String, unit: String, suffix: String): String =
    (get(key) as? JsonObject)?.text(unit)?.takeIf(String::isNotBlank)?.let { "$it $suffix" }.orEmpty()
