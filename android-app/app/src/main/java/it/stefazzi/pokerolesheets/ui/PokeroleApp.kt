package it.stefazzi.pokerolesheets.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import it.stefazzi.pokerolesheets.data.EditableSheet
import it.stefazzi.pokerolesheets.data.SheetStats
import it.stefazzi.pokerolesheets.data.normalizeMoveName
import it.stefazzi.pokerolesheets.data.model.PokemonSpecies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

private enum class HomeTab(val label: String) {
    TRAINERS("Allenatori"),
    POKEMON("Pokémon"),
    CATALOG("Catalogo"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PokeroleApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel: AppViewModel = viewModel(factory = AppViewModel.factory(context))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var claimCodeTrainer by remember { mutableStateOf<EditableSheet?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    claimCodeTrainer?.let { trainer ->
        ClaimCodeDialog(
            trainer = trainer,
            saving = state.saving,
            onDismiss = { claimCodeTrainer = null },
            onSave = { code ->
                viewModel.setTrainerClaimCode(trainer.recordId, code)
                claimCodeTrainer = null
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.selectedSheet?.displayName ?: "Pokerole Sheets",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (state.selectedSheet != null) {
                        IconButton(onClick = { viewModel.selectSheet(null) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                        }
                    }
                },
                actions = {
                    if (state.selectedSheet == null && state.portal != null) {
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Default.Refresh, contentDescription = "Aggiorna")
                        }
                    }
                    if (state.authenticated && state.portal != null) {
                        IconButton(onClick = viewModel::logout) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Esci")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !state.configured -> ConfigurationMissing()
                state.authLoading -> LoadingScreen()
                state.portal == null -> LoginScreen(
                    authenticated = state.authenticated,
                    email = state.email,
                    loading = state.loading,
                    onLoginDm = { viewModel.login(LoginPortal.DM) },
                    onLoginPlayer = { viewModel.login(LoginPortal.PLAYER) },
                    onLogout = viewModel::logout,
                )
                state.loading -> LoadingScreen()
                state.needsTrainerClaim -> ClaimTrainerScreen(
                    trainers = state.claimableTrainers,
                    claiming = state.claiming,
                    onClaim = viewModel::claimTrainer,
                    onRefresh = viewModel::refresh,
                    onLogout = viewModel::logout,
                )
                state.selectedSheet != null -> SheetEditor(
                    initial = state.selectedSheet!!,
                    saving = state.saving,
                    uploadingPortrait = state.uploadingPortrait,
                    moveTypes = state.moveTypes,
                    onUploadPortrait = viewModel::uploadTrainerPortrait,
                    onSave = viewModel::saveSheet,
                )
                else -> HomeContent(
                    state = state,
                    onSelect = viewModel::selectSheet,
                    onCreate = viewModel::beginNewSheet,
                    onSetClaimCode = { claimCodeTrainer = it },
                )
            }
        }
    }
}

@Composable
private fun ClaimCodeDialog(
    trainer: EditableSheet,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var code by rememberSaveable(trainer.recordId) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Codice per ${trainer.trainerName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Comunica questo codice soltanto al giocatore che deve associare la scheda.")
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Codice (minimo 6 caratteri)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(code) }, enabled = code.trim().length >= 6 && !saving) {
                Text("Salva codice")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun LoginScreen(
    authenticated: Boolean,
    email: String?,
    loading: Boolean,
    onLoginDm: () -> Unit,
    onLoginPlayer: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Pokerole Sheets", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            if (authenticated) "Account Google: ${email.orEmpty()}" else "Accedi con il tuo account Google",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onLoginDm, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
            Text("Login DM")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onLoginPlayer, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
            Text("Login Player")
        }
        if (authenticated) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onLogout) { Text("Usa un altro account") }
        }
    }
}

@Composable
private fun ClaimTrainerScreen(
    trainers: List<it.stefazzi.pokerolesheets.data.model.ClaimableTrainer>,
    claiming: Boolean,
    onClaim: (String, String) -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var code by rememberSaveable { mutableStateOf("") }
    LazyColumn(
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Associa il tuo allenatore", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text("Scegli la scheda e inserisci il codice ricevuto dal DM.")
        }
        if (trainers.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        "Nessuna scheda disponibile. Chiedi al DM di generare un codice di associazione.",
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
        items(trainers, key = { it.id }) { trainer ->
            OutlinedButton(
                onClick = { selectedId = trainer.id },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (selectedId == trainer.id) "✓ ${trainer.trainerName}" else trainer.trainerName)
            }
        }
        item {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text("Codice associazione") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Button(
                onClick = { selectedId?.let { onClaim(it, code) } },
                enabled = selectedId != null && code.isNotBlank() && !claiming,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (claiming) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("Associa scheda")
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onRefresh) { Text("Aggiorna elenco") }
                TextButton(onClick = onLogout) { Text("Esci") }
            }
        }
    }
}

@Composable
private fun ConfigurationMissing() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Configurazione Supabase mancante", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(
            "Copia local.properties.example in local.properties e inserisci URL e chiave " +
                "pubblica del progetto Supabase. Il file locale non viene salvato su GitHub.",
        )
    }
}

@Composable
private fun HomeContent(
    state: AppUiState,
    onSelect: (EditableSheet) -> Unit,
    onCreate: (Boolean) -> Unit,
    onSetClaimCode: (EditableSheet) -> Unit,
) {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tabIndex) {
            HomeTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = tabIndex == index,
                    onClick = { tabIndex = index },
                    text = { Text(tab.label) },
                )
            }
        }

        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }

        when (HomeTab.entries[tabIndex]) {
            HomeTab.TRAINERS -> SheetList(
                sheets = state.sheets.filterNot { it.isPokemon },
                createLabel = if (state.isDm) "Nuovo allenatore" else null,
                onCreate = { onCreate(false) },
                onSelect = onSelect,
                onSetClaimCode = if (state.isDm) onSetClaimCode else null,
            )
            HomeTab.POKEMON -> SheetList(
                sheets = state.sheets.filter { it.isPokemon },
                createLabel = "Cattura Pokémon",
                onCreate = { onCreate(true) },
                onSelect = onSelect,
                onSetClaimCode = null,
            )
            HomeTab.CATALOG -> PokemonCatalog(state.catalog)
        }
    }
}

@Composable
private fun SheetList(
    sheets: List<EditableSheet>,
    createLabel: String?,
    onCreate: () -> Unit,
    onSelect: (EditableSheet) -> Unit,
    onSetClaimCode: ((EditableSheet) -> Unit)?,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (createLabel != null) {
            item {
                Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                    Text(createLabel)
                }
            }
        }
        if (sheets.isEmpty()) {
            item {
                Text(
                    "Nessuna scheda trovata",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        items(sheets, key = { it.storageKey }) { sheet ->
            Card(onClick = { onSelect(sheet) }, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SheetPortrait(sheet, Modifier.size(72.dp))
                    Column(Modifier.weight(1f)) {
                        Text(sheet.displayName, fontWeight = FontWeight.SemiBold)
                        if (sheet.isPokemon) {
                            Text(
                                listOf(sheet.speciesName, "Allenatore: ${sheet.trainerName}")
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else {
                            Text(
                                "Squadra ${sheet.team.ifBlank { "—" }}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (onSetClaimCode != null && sheet.ownerId == null) {
                                TextButton(onClick = { onSetClaimCode(sheet) }) {
                                    Text("Imposta codice associazione")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PokemonCatalog(pokemon: List<PokemonSpecies>) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(query, pokemon) {
        pokemon.filter { it.pokemon.contains(query.trim(), ignoreCase = true) }.take(200)
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Cerca Pokémon") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            items(filtered, key = { "${it.nr}-${it.pokemon}" }) { species ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = pokemonSpriteUrl(species.nr),
                        contentDescription = species.pokemon,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(52.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text("#${species.nr.padStart(3, '0')}  ${species.pokemon.replaceFirstChar { it.uppercase() }}")
                        Text(
                            listOf(species.tipo1, species.tipo2).filter(String::isNotBlank).joinToString(" / "),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SheetEditor(
    initial: EditableSheet,
    saving: Boolean,
    uploadingPortrait: Boolean,
    moveTypes: Map<String, String>,
    onUploadPortrait: (EditableSheet, ByteArray) -> Unit,
    onSave: (EditableSheet) -> Unit,
) {
    var draft by remember(initial.storageKey, initial.raw) { mutableStateOf(initial) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var preparingPortrait by remember { mutableStateOf(false) }
    var portraitError by remember { mutableStateOf<String?>(null) }
    val portraitPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        portraitError = null
        preparingPortrait = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { compressPortrait(context, uri) } }
                .onSuccess { onUploadPortrait(draft, it) }
                .onFailure { portraitError = it.message ?: "Immagine non valida" }
            preparingPortrait = false
        }
    }
    val updateDots: (String, List<Boolean>) -> Unit = { key, values ->
        draft = draft.copy(dotStats = draft.dotStats + (key to values))
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                if (draft.isPokemon) "Scheda Pokémon" else "Scheda allenatore",
                style = MaterialTheme.typography.titleLarge,
            )
        }

        item {
            SheetSectionCard("Identità") {
                SheetPortrait(
                    sheet = draft,
                    modifier = Modifier.size(144.dp).align(Alignment.CenterHorizontally),
                )
                SheetField("Allenatore", draft.trainerName) { draft = draft.copy(trainerName = it) }
                if (draft.isPokemon) {
                    SheetField("Soprannome", draft.pokemonName) { draft = draft.copy(pokemonName = it) }
                    SheetField("Specie", draft.speciesName) { draft = draft.copy(speciesName = it) }
                    FieldPair(
                        "Numero Pokédex", draft.pokedexNumber, { draft = draft.copy(pokedexNumber = it) },
                        "Tipo primario", draft.primaryType, { draft = draft.copy(primaryType = it) },
                    )
                    FieldPair(
                        "Tipo secondario", draft.secondaryType, { draft = draft.copy(secondaryType = it) },
                        "Taglia", draft.size, { draft = draft.copy(size = it) },
                    )
                    SheetField("Peso", draft.weight) { draft = draft.copy(weight = it) }
                    DotRatingField("Felicità", draft.happiness) { draft = draft.copy(happiness = it) }
                    DotRatingField("Lealtà", draft.loyalty) { draft = draft.copy(loyalty = it) }
                    SheetField("Immagine rango", draft.rankImage) { draft = draft.copy(rankImage = it) }
                } else {
                    SheetField("Team", draft.team) { draft = draft.copy(team = it) }
                    FieldPair(
                        "Età", draft.age, { draft = draft.copy(age = it) },
                        "Denaro", draft.money, { draft = draft.copy(money = it) },
                    )
                    SheetField("Reputazione", draft.reputation) { draft = draft.copy(reputation = it) }
                }
                if (draft.isPokemon) {
                    SheetField("URL immagine alternativa", draft.profilePicture) {
                        draft = draft.copy(profilePicture = it)
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            portraitPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        enabled = draft.recordId.isNotBlank() && !preparingPortrait && !uploadingPortrait,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (preparingPortrait || uploadingPortrait) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                if (draft.profilePicture.isBlank()) "Scegli immagine dalla galleria"
                                else "Cambia immagine allenatore",
                            )
                        }
                    }
                    if (draft.recordId.isBlank()) {
                        Text(
                            "Salva prima la nuova scheda, poi potrai aggiungere l'immagine.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    portraitError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            SheetSectionCard("Riferimenti rapidi") {
                FieldPair(
                    "PS attuali", draft.hpActual, { draft = draft.copy(hpActual = it) },
                    "PS totali", draft.hpTotal, { draft = draft.copy(hpTotal = it) },
                )
                FieldPair(
                    "Difesa attuale", draft.defenseActual, { draft = draft.copy(defenseActual = it) },
                    "Difesa totale", draft.defenseTotal, { draft = draft.copy(defenseTotal = it) },
                )
                FieldPair(
                    "Volontà attuale", draft.willActual, { draft = draft.copy(willActual = it) },
                    "Volontà totale", draft.willTotal, { draft = draft.copy(willTotal = it) },
                )
                FieldPair(
                    "Iniziativa", draft.initiative, { draft = draft.copy(initiative = it) },
                    "Evasione", draft.evasion, { draft = draft.copy(evasion = it) },
                )
                SheetField("Strumento equipaggiato", draft.heldItem) { draft = draft.copy(heldItem = it) }
                SheetField("Effetto di stato", draft.statusEffect) { draft = draft.copy(statusEffect = it) }
                DotRatingField("Fatica", draft.fatigue) { draft = draft.copy(fatigue = it) }
                DotRatingField("Azioni utilizzate", draft.actionUsed) { draft = draft.copy(actionUsed = it) }
                OutlinedButton(
                    onClick = { draft = draft.resetCurrentStats() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Reset statistiche")
                }
                Text(
                    "Riporta PS, Difesa e Volontà ai rispettivi valori massimi.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (draft.isPokemon) {
            item {
                SheetSectionCard("Abilità") {
                    SheetField("Ricerca abilità", draft.abilitySearch) { draft = draft.copy(abilitySearch = it) }
                    SheetField("Nome", draft.abilityName) { draft = draft.copy(abilityName = it) }
                    SheetField("Effetto", draft.abilityEffect, minLines = 2) {
                        draft = draft.copy(abilityEffect = it)
                    }
                    SheetField("Descrizione", draft.abilityDescription, minLines = 3) {
                        draft = draft.copy(abilityDescription = it)
                    }
                }
            }
            item {
                SheetSectionCard("Natura") {
                    SheetField("Ricerca natura", draft.natureSearch) { draft = draft.copy(natureSearch = it) }
                    SheetField("Nome", draft.natureName) { draft = draft.copy(natureName = it) }
                    SheetField("Configurazione", draft.natureConfiguration) {
                        draft = draft.copy(natureConfiguration = it)
                    }
                    SheetField("Parole chiave", draft.natureKeywords) { draft = draft.copy(natureKeywords = it) }
                    SheetField("Descrizione", draft.natureDescription, minLines = 3) {
                        draft = draft.copy(natureDescription = it)
                    }
                }
            }
        } else {
            item {
                SheetSectionCard("Squadra Pokémon") {
                    draft.pokemonTeam.forEachIndexed { index, value ->
                        SheetField("Slot ${index + 1}", value) {
                            draft = draft.copy(pokemonTeam = draft.pokemonTeam.updated(index, it, 3))
                        }
                    }
                }
            }
        }

        item {
            DotStatsCard("Attributi", SheetStats.attributes, draft.dotStats, updateDots)
        }
        item {
            DotStatsCard("Attributi sociali", SheetStats.socialAttributes, draft.dotStats, updateDots)
        }
        SheetStats.skillGroups.forEach { (group, fields) ->
            item {
                DotStatsCard("Abilità · $group", fields, draft.dotStats, updateDots)
            }
        }

        if (draft.isPokemon) {
            item {
                SheetSectionCard("Mosse") {
                    val visibleMoves = draft.moves.ifEmpty { listOf("") }
                    visibleMoves.forEachIndexed { index, value ->
                        MoveFieldCard(
                            index = index,
                            value = value,
                            type = moveTypes[normalizeMoveName(value)].orEmpty(),
                        ) {
                            draft = draft.copy(moves = draft.moves.updated(index, it, 1))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { draft = draft.copy(moves = visibleMoves + "") },
                            modifier = Modifier.weight(1f),
                        ) { Text("Aggiungi") }
                        TextButton(
                            onClick = { draft = draft.copy(moves = visibleMoves.dropLast(1)) },
                            enabled = visibleMoves.size > 1,
                            modifier = Modifier.weight(1f),
                        ) { Text("Rimuovi ultima") }
                    }
                }
            }
        } else {
            item {
                SheetSectionCard("Borsa") {
                    FieldPair(
                        "Pozioni", draft.potion, { draft = draft.copy(potion = it) },
                        "Super Pozioni", draft.superPotion, { draft = draft.copy(superPotion = it) },
                    )
                    SheetField("Iper Pozioni", draft.hyperPotion) { draft = draft.copy(hyperPotion = it) }
                    repeat(15) { index ->
                        FieldPair(
                            "Oggetto ${index + 1} A", draft.bagItemsLeft.getOrElse(index) { "" },
                            { draft = draft.copy(bagItemsLeft = draft.bagItemsLeft.updated(index, it, 15)) },
                            "Oggetto ${index + 1} B", draft.bagItemsRight.getOrElse(index) { "" },
                            { draft = draft.copy(bagItemsRight = draft.bagItemsRight.updated(index, it, 15)) },
                        )
                    }
                }
            }
        }

        item {
            SheetSectionCard("Note") {
                SheetField("Background", draft.background, minLines = 4) {
                    draft = draft.copy(background = it)
                }
                SheetField("Conoscenze personali", draft.personalKnowledge, minLines = 4) {
                    draft = draft.copy(personalKnowledge = it)
                }
            }
        }

        item {
            Button(
                onClick = { onSave(draft) },
                enabled = !saving && !uploadingPortrait,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (saving) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("Salva su Supabase")
            }
        }
    }
}

@Composable
private fun SheetPortrait(sheet: EditableSheet, modifier: Modifier = Modifier) {
    val imageUrl = sheet.resolvedProfilePicture
        .trim()
        .takeIf { it.startsWith("https://") || it.startsWith("http://") }
        ?: sheet.profilePicture
        .trim()
        .takeIf { it.startsWith("https://") || it.startsWith("http://") }
        ?: sheet.takeIf { it.isPokemon }?.let { pokemonSpriteUrl(it.pokedexNumber) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "Immagine di ${sheet.displayName}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(4.dp),
            )
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        sheet.displayName.firstOrNull()?.uppercase().orEmpty().ifBlank { "?" },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

private fun pokemonSpriteUrl(pokedexNumber: String): String? {
    val number = pokedexNumber.filter(Char::isDigit).toIntOrNull() ?: return null
    return "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/$number.png"
}

@Composable
private fun MoveFieldCard(
    index: Int,
    value: String,
    type: String,
    onValueChange: (String) -> Unit,
) {
    val accent = moveTypeColor(type)
    Card(
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.18f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Mossa ${index + 1}", fontWeight = FontWeight.SemiBold)
                if (type.isNotBlank()) {
                    Text(
                        type.uppercase(),
                        color = accent,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text("Nome della mossa") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun moveTypeColor(type: String): Color = when (type.lowercase()) {
    "normal" -> Color(0xFF6D6D4E)
    "fire" -> Color(0xFFC85216)
    "water" -> Color(0xFF3265C7)
    "electric" -> Color(0xFF9B7700)
    "grass" -> Color(0xFF3D7E27)
    "ice" -> Color(0xFF3C8585)
    "fighting" -> Color(0xFF9B2520)
    "poison" -> Color(0xFF7A327A)
    "ground" -> Color(0xFF846A25)
    "flying" -> Color(0xFF6548A8)
    "psychic" -> Color(0xFFC52D62)
    "bug" -> Color(0xFF738014)
    "rock" -> Color(0xFF786B24)
    "ghost" -> Color(0xFF513E70)
    "dragon" -> Color(0xFF5120B8)
    "dark" -> Color(0xFF4E3B30)
    "steel" -> Color(0xFF65658A)
    "fairy" -> Color(0xFFB55F78)
    else -> Color(0xFF59636F)
}

@Composable
private fun SheetSectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun DotStatsCard(
    title: String,
    fields: Map<String, String>,
    values: Map<String, List<Boolean>>,
    onChange: (String, List<Boolean>) -> Unit,
) {
    SheetSectionCard(title) {
        fields.forEach { (key, label) ->
            DotRatingField(label, values[key].orEmpty()) { onChange(key, it) }
        }
    }
}

@Composable
private fun DotRatingField(label: String, values: List<Boolean>, onValueChange: (List<Boolean>) -> Unit) {
    val rating = values.count { it }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text("$rating / 5", fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = rating.toFloat(),
            onValueChange = { raw ->
                val selected = raw.roundToInt().coerceIn(0, 5)
                onValueChange(List(5) { it < selected })
            },
            valueRange = 0f..5f,
            steps = 4,
        )
    }
}

@Composable
private fun FieldPair(
    firstLabel: String,
    firstValue: String,
    firstChange: (String) -> Unit,
    secondLabel: String,
    secondValue: String,
    secondChange: (String) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SheetField(firstLabel, firstValue, Modifier.weight(1f), onValueChange = firstChange)
        SheetField(secondLabel, secondValue, Modifier.weight(1f), onValueChange = secondChange)
    }
}

@Composable
private fun SheetField(
    label: String,
    value: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    minLines: Int = 1,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        minLines = minLines,
        modifier = modifier,
    )
}

private fun compressPortrait(context: Context, uri: Uri): ByteArray {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        ?: error("Impossibile leggere l'immagine")
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Formato immagine non supportato" }

    var sampleSize = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > PORTRAIT_MAX_SIDE * 2) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val decoded = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    } ?: error("Impossibile decodificare l'immagine")

    val longestSide = maxOf(decoded.width, decoded.height)
    val scaled = if (longestSide > PORTRAIT_MAX_SIDE) {
        val ratio = PORTRAIT_MAX_SIDE.toFloat() / longestSide
        Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * ratio).toInt().coerceAtLeast(1),
            (decoded.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    } else decoded

    try {
        @Suppress("DEPRECATION")
        val webpFormat = Bitmap.CompressFormat.WEBP
        listOf(86, 72, 58).forEach { quality ->
            val bytes = ByteArrayOutputStream().use { output ->
                check(scaled.compress(webpFormat, quality, output)) {
                    "Compressione immagine non riuscita"
                }
                output.toByteArray()
            }
            if (bytes.size <= PORTRAIT_MAX_BYTES) return bytes
        }
        error("L'immagine compressa supera il limite di 2 MB")
    } finally {
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
    }
}

private fun List<String>.updated(index: Int, value: String, minimumSize: Int): List<String> {
    val size = maxOf(this.size, minimumSize, index + 1)
    return MutableList(size) { getOrElse(it) { "" } }.also { it[index] = value }
}

private const val PORTRAIT_MAX_SIDE = 1024
private const val PORTRAIT_MAX_BYTES = 2 * 1024 * 1024
