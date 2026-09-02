package it.stefazzi.pokerolesheets.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import it.stefazzi.pokerolesheets.data.*

/** Read-only encyclopedia. Uses the same validated Supabase snapshot as capture/evolution. */
@Composable
internal fun PokedexScreen(
    species: List<CorebookSpecies>, moves: Map<String, CatalogMove>,
    status: String, loading: Boolean, onRefresh: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("Tutti") }
    var history by rememberSaveable { mutableStateOf(listOf<String>()) }
    val selected = species.firstOrNull { it.name == history.lastOrNull() }
    val back = { history = history.dropLast(1) }
    BackHandler(enabled = history.isNotEmpty(), onBack = back)
    if (selected != null) {
        PokedexEntry(selected, species, moves, back) { next -> history = history + next.name }
        return
    }
    val types = remember(species) { listOf("Tutti") + species.flatMap { it.types }.distinct().sorted() }
    val filtered = remember(species, query, type) {
        species.filter { it.matchesSearch(query) && (type == "Tutti" || type in it.types) }
            .sortedWith(compareBy<CorebookSpecies> { it.number.toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it.name })
    }
    Column(Modifier.fillMaxSize()) {
        Text(status, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onRefresh, enabled = !loading, modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(if (loading) "Download in corso…" else "Aggiorna catalogo")
        }
        CompactSearch("Cerca nome, numero, forma o tipo", query, { query = it },
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            ChoiceField("Type", type, types) { type = it }
            Spacer(Modifier.width(12.dp))
            Text("${filtered.size} / ${species.size}", style = MaterialTheme.typography.bodySmall)
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (filtered.isEmpty()) item { Text("Nessuna specie corrisponde alla ricerca.") }
            // No 200-row cutoff: lazy rendering makes all downloaded forms accessible.
            items(filtered, key = { it.name }) { entry ->
                Card(onClick = { history = listOf(entry.name) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ReferenceSprite(entry, Modifier.size(64.dp))
                        Column(Modifier.weight(1f)) {
                            Text("#${entry.number.padStart(3, '0')} ${entry.name}", fontWeight = FontWeight.SemiBold)
                            Text(entry.types.joinToString(" / "), style = MaterialTheme.typography.bodySmall)
                            if (entry.dexCategory.isNotBlank()) Text(entry.dexCategory, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PokedexEntry(
    entry: CorebookSpecies, allSpecies: List<CorebookSpecies>, moves: Map<String, CatalogMove>,
    onBack: () -> Unit, onOpen: (CorebookSpecies) -> Unit,
) {
    var rankFilter by rememberSaveable(entry.name) { mutableStateOf("Tutti i rank") }
    val rankOptions = listOf("Tutti i rank") + SheetRank.entries.map { "${it.ordinal + 1} · ${it.name}" }
    val rank = SheetRank.entries.getOrNull(rankOptions.indexOf(rankFilter) - 1)
    val learnset = remember(entry, rank) { entry.availableMoves(rank ?: SheetRank.Champion) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TextButton(onClick = onBack) { Text("← Indietro") }
            Text("#${entry.number.padStart(3, '0')} ${entry.name}", style = MaterialTheme.typography.headlineSmall)
            Text(entry.sourceLabel, style = MaterialTheme.typography.bodySmall)
            Text("Consultazione Pokédex: non modifica le schede della campagna.", style = MaterialTheme.typography.bodySmall)
        }
        item {
            DexCard("Species") {
                ReferenceSprite(entry, Modifier.size(144.dp).align(Alignment.CenterHorizontally))
                Text("Type: ${entry.types.joinToString(" / ")}")
                Text("Form: ${entry.form}")
                if (entry.dexCategory.isNotBlank()) Text("Category: ${entry.dexCategory}")
                Text("Height: ${entry.height.ifBlank { "—" }} · Weight: ${entry.weight.ifBlank { "—" }}")
                if (entry.dexDescription.isNotBlank()) Text(entry.dexDescription)
                else Text("Descrizione non disponibile in questa copia del catalogo.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            DexCard("Attributes") {
                Text("Base HP: ${entry.baseHp}")
                Text("Valori della specie: base / massimo. Non includono i punti individuali e i bonus del Rank.", style = MaterialTheme.typography.bodySmall)
                entry.attributes.forEach { (key, attribute) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(SheetStats.attributes["attributes.$key"].orEmpty())
                        Text("${attribute.base} / ${attribute.limit}", fontWeight = FontWeight.SemiBold)
                    }
                }
                Text("Suggested Rank: ${entry.suggestedRank.ordinal + 1} · ${entry.suggestedRank.name}")
                Text("Il Rank del Pokémon catturato resta una scelta manuale.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            DexCard("Abilities") {
                Text(entry.abilities.joinToString(" / ").ifBlank { "Nessuna abilità indicata" })
            }
        }
        item {
            DexCard("Evolutions") {
                if (entry.evolutions.isEmpty()) Text("Nessuna evoluzione successiva indicata in questa copia del catalogo.")
                entry.evolutions.forEach { evolution ->
                    val next = allSpecies.firstOrNull { it.name.equals(evolution.targetName, true) }
                    if (next == null) Text(evolution.targetName)
                    else OutlinedButton(onClick = { onOpen(next) }) { Text(evolution.targetName) }
                    if (evolution.condition.isNotBlank()) Text(evolution.condition, style = MaterialTheme.typography.bodySmall)
                }
                Text("Condizioni da verificare con il DM. Qui apri solo la voce Pokédex, senza evolvere alcuna scheda.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Text("Moves", style = MaterialTheme.typography.titleLarge)
            ChoiceField("Apprendimento", rankFilter, rankOptions) { rankFilter = it }
            Text(if (rank == null) "Tutte le mosse della specie, raggruppate per primo rank di apprendimento."
                else "Mosse disponibili fino al rank ${rank.ordinal + 1} · ${rank.name} incluso.", style = MaterialTheme.typography.bodySmall)
            if (entry.anyMoveRank?.let { rank == null || rank.ordinal >= it.ordinal } == true) {
                Text("Any Move · Rank ${entry.anyMoveRank.ordinal + 1}: scelte aggiuntive da concordare con il DM.")
            }
            Text("${learnset.size} mosse")
        }
        items(learnset, key = { normalizeMoveName(it.name) }) { reference ->
            val details = moves[normalizeMoveName(reference.name)]
            val accent = moveTypeColor(reference.type)
            Card(colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.18f)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(reference.name, fontWeight = FontWeight.SemiBold)
                    Text("${reference.type} · Rank ${reference.rank.ordinal + 1} · ${reference.rank.name}")
                    if (details != null) {
                        Text("${details.category} · Power ${details.power} · Target ${details.target}")
                        Text("Accuracy: ${details.accuracy} · Damage: ${details.damage.ifBlank { "—" }}")
                        if (details.effect.isNotBlank()) Text(details.effect)
                        if (details.description.isNotBlank()) Text(details.description, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun DexCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
