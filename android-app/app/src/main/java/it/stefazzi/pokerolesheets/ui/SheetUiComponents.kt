package it.stefazzi.pokerolesheets.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.stefazzi.pokerolesheets.data.*
import coil3.compose.AsyncImage

internal val LocalSheetReferences = staticCompositionLocalOf { SheetReferences() }

@Composable
internal fun PortraitUrls(urls: List<String>, description: String, modifier: Modifier = Modifier) {
    var index by remember(urls) { mutableIntStateOf(0) }
    val url = urls.getOrNull(index)
    if (url == null) Box(modifier, contentAlignment = Alignment.Center) {
        Text("Immagine non disponibile", style = MaterialTheme.typography.labelSmall)
    } else AsyncImage(model = url, contentDescription = description, contentScale = ContentScale.Fit,
        onError = { if (urls.getOrNull(index) == url) index += 1 }, modifier = modifier)
}

@Composable
internal fun ReferenceSprite(entry: CorebookSpecies, modifier: Modifier = Modifier) {
    PortraitUrls(LocalSheetReferences.current.spritePaths(entry.number, entry.name, entry.form, false), entry.name, modifier)
}

@Composable
internal fun CompactSearch(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(value = value, onValueChange = onChange, singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
        label = { Text(label, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingIcon = { if (value.isNotEmpty()) TextButton(onClick = { onChange("") }) { Text("×", fontSize = 18.sp) } },
        modifier = modifier.fillMaxWidth())
}

@Composable
internal fun CollapsibleCard(title: String, initiallyExpanded: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                Text("${if (expanded) "▾" else "▸"} $title", modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.SemiBold)
            }
            if (expanded) content()
        }
    }
}

@Composable
internal fun ReferencePicker(title: String, entries: List<NamedReference>, suggested: List<String> = emptyList(),
    onDismiss: () -> Unit, onSelect: (NamedReference) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val rows = remember(entries, query, suggested) {
        entries.filter { referenceKey(it.name).contains(referenceKey(query)) }
            .sortedWith(compareBy<NamedReference> { it.name !in suggested }.thenBy { it.name })
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactSearch("Cerca per nome", query, { query = it })
            if (suggested.isNotEmpty()) Text("Prima le abilità della specie. Le altre richiedono l'accordo del DM.", style = MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.height(320.dp)) {
                if (rows.isEmpty()) item { Text("Nessun risultato") }
                items(rows, key = { it.name }) { entry ->
                    TextButton(onClick = { onSelect(entry) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(entry.name + if (entry.name in suggested) " · specie" else "", fontWeight = FontWeight.Bold)
                            Text(entry.effect.ifBlank { entry.description }, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Annulla") } })
}
