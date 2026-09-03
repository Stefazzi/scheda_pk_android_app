package it.stefazzi.pokerolesheets.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.stefazzi.pokerolesheets.data.*

internal val LocalEquipmentInfluences = staticCompositionLocalOf<Map<String, List<String>>> { emptyMap() }
internal val EquipmentAccent = Color(0xFF80DEEA)

@Composable
internal fun EquipmentHint(key: String) {
    LocalEquipmentInfluences.current[key]?.takeIf { it.isNotEmpty() }?.let {
        Text("Influenzato da: ${it.joinToString()} · modifica il valore manualmente",
            color = EquipmentAccent, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun ParameterPicker(selected: List<String>, onChange: (List<String>) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { expanded = true }) { Text("Parametri influenzati (${selected.size})") }
    Text(selected.mapNotNull { EquipmentParameters.labels[it] }.joinToString().ifBlank { "Nessuno" },
        style = MaterialTheme.typography.bodySmall)
    if (expanded) AlertDialog(onDismissRequest = { expanded = false }, title = { Text("Parametri influenzati") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text("Selezione multipla. Evidenzia i parametri quando lo strumento o accessorio è equipaggiato; non applica bonus numerici o condizioni automaticamente.")
                EquipmentParameters.groups.forEach { (group, fields) ->
                    Text(group, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                    fields.forEach { (key, label) ->
                        Row {
                            Checkbox(checked = key in selected, onCheckedChange = { checked ->
                                onChange(if (checked) (selected + key).distinct() else selected - key)
                            })
                            Text(label, modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { expanded = false }) { Text("Fatto") } },
        dismissButton = { TextButton(onClick = { onChange(emptyList()) }) { Text("Nessun parametro") } })
}

@Composable
internal fun PainHint(sheet: EditableSheet) {
    sheet.painWarning()?.let {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Column(Modifier.padding(12.dp)) {
                Text(it, fontWeight = FontWeight.Bold)
                if (sheet.hpActual.trim().toIntOrNull() != 0)
                    Text("Sottrai successi, non dadi. Le prove con Vitality e Will sono esenti (Corebook p. 62).", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
internal fun MoveRollDialog(move: CatalogMove?, name: String, sheet: EditableSheet, onDismiss: () -> Unit) {
    var defense by remember(name) { mutableStateOf("") }
    var ignoreDefense by remember(name) { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(name.ifBlank { "Mossa" }) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (move == null) {
                    Text("Mossa non trovata nel catalogo: controlla il nome o aggiorna il catalogo. Accuracy, Damage e Clash restano manuali.")
                } else {
                    Text("Dadi calcolati sui valori attuali della scheda. Se sono indicate alternative, scegli quella prevista dalla mossa.", style = MaterialTheme.typography.bodySmall)
                    DiceSection("Accuracy", sheet.dicePools(move.accuracy), move.accuracy)
                    val pools = move.damagePools(sheet)
                    when {
                        move.category.equals("Support", true) -> Text("Damage: nessun tiro di danno ordinario (Support). Consulta l'effetto.")
                        move.damage.contains("SetDamage", true) -> Text("Damage speciale: non usa attributo + Power. Consulta l'effetto per i dadi da tirare o il danno fisso.")
                        else -> {
                            DiceSection("Damage · prima della difesa", pools, "${move.damage} + Power ${move.power}")
                            if (pools.isNotEmpty()) {
                                Row {
                                    Checkbox(ignoreDefense, onCheckedChange = { ignoreDefense = it })
                                    Text("Questo tiro ignora le difese", Modifier.padding(top = 12.dp))
                                }
                                if (ignoreDefense) Text("Difesa non sottratta: usa il pool sopra. Attiva questa opzione solo quando l'effetto lo prevede per questo tiro.")
                                else {
                                    OutlinedTextField(value = defense, onValueChange = { if (it.length <= 3 && it.all(Char::isDigit)) defense = it },
                                        label = { Text(if (move.category.equals("Physical", true)) "Physical Defense del bersaglio" else "Special Defense del bersaglio") },
                                        singleLine = true, modifier = Modifier.fillMaxWidth())
                                    val enemyDefense = defense.toIntOrNull()
                                    if (enemyDefense != null) pools.forEach { pool ->
                                        Text("Damage: ${damageAfterDefense(pool.dice, enemyDefense)}d6 (${pool.dice} − $enemyDefense, minimo 1)", fontWeight = FontWeight.Bold)
                                    }
                                    else Text("Inserisci la difesa del bersaglio per ottenere i dadi dopo la difesa.", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    if (move.clashPools(sheet).isEmpty()) Text("Clash: non disponibile con questa categoria di mossa.")
                    else {
                        DiceSection("Clash", move.clashPools(sheet), "Strength/Special + Clash")
                        Text("Pool per usare questa mossa in un Clash. Verifica che l'attacco avversario possa essere contrastato: niente Support, Social Accuracy, danni fissi, attacchi che ignorano difese o bersagliano il campo; contro Area serve Area. Corebook p. 70.", style = MaterialTheme.typography.bodySmall)
                    }
                    if (move.healingWillReminder) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Text("Cura: ricorda di spendere 1 Will per attivare l'effetto curativo, quando si verificano le condizioni della mossa. Will non viene scalato automaticamente.", Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
                    }
                    PainHint(sheet)
                    Text("I pool non includono bonus condizionali, Critical Hit, strumenti, Ability o modificatori temporanei non già inseriti in scheda. Low Accuracy e ferite riducono i successi, non questi dadi. Resistenze/debolezze vanno risolte separatamente.", style = MaterialTheme.typography.bodySmall)
                    if (move.effect.isNotBlank()) Text(move.effect)
                    if (move.description.isNotBlank()) Text(move.description, style = MaterialTheme.typography.bodySmall)
                }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } })
}

@Composable
private fun DiceSection(title: String, pools: List<DicePool>, formula: String) {
    Text(title, fontWeight = FontWeight.Bold)
    if (pools.isEmpty()) Text("Calcolo manuale: ${formula.ifBlank { "formula speciale/non disponibile" }}. Consulta l'effetto della mossa.")
    pools.forEach {
        Text("${it.dice}d6 · ${it.formula}")
    }
}
