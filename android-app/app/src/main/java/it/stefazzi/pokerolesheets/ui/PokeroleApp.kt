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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import it.stefazzi.pokerolesheets.data.CorebookCatalog
import it.stefazzi.pokerolesheets.data.CorebookSpecies
import it.stefazzi.pokerolesheets.data.CatalogMove
import it.stefazzi.pokerolesheets.data.evolve
import it.stefazzi.pokerolesheets.data.readPortraitInput
import it.stefazzi.pokerolesheets.data.matchesSearch
import it.stefazzi.pokerolesheets.data.sortPokemonSheets
import it.stefazzi.pokerolesheets.data.withCaptureTrainer
import it.stefazzi.pokerolesheets.data.SheetRank
import it.stefazzi.pokerolesheets.data.attributeFields
import it.stefazzi.pokerolesheets.data.statRange
import it.stefazzi.pokerolesheets.data.sheetRank
import it.stefazzi.pokerolesheets.data.speciesForm
import it.stefazzi.pokerolesheets.data.baseHpValue
import it.stefazzi.pokerolesheets.data.baseHpText
import it.stefazzi.pokerolesheets.data.withRuleValue
import it.stefazzi.pokerolesheets.data.recalculateMaximums
import it.stefazzi.pokerolesheets.data.SheetStats
import it.stefazzi.pokerolesheets.data.normalizeMoveName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

private enum class HomeTab(val label: String) {
    TRAINERS("Allenatori"),
    POKEMON("Pokémon"),
    CATALOG("Pokédex"),
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
                    isDm = state.isDm,
                    trainers = state.sheets.filterNot { it.isPokemon },
                    saving = state.saving,
                    uploadingPortrait = state.uploadingPortrait,
                    moveTypes = state.moveTypes,
                    corebookSpecies = state.corebookSpecies,
                    catalogMoves = state.catalogMoves,
                    catalogStatus = state.catalogStatus,
                    catalogLoading = state.catalogLoading,
                    onRefreshCatalog = viewModel::refreshCatalog,
                    onUploadPortrait = viewModel::uploadTrainerPortrait,
                    onSave = viewModel::saveSheet,
                )
                else -> HomeContent(
                    state = state,
                    onSelect = viewModel::selectSheet,
                    onCreate = viewModel::beginNewSheet,
                    onSetClaimCode = { claimCodeTrainer = it },
                    onRefreshCatalog = viewModel::refreshCatalog,
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
    onRefreshCatalog: () -> Unit,
) {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var trainerSearch by rememberSaveable { mutableStateOf("") }
    var pokemonSearch by rememberSaveable { mutableStateOf("") }
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
                searchQuery = if (state.isDm) trainerSearch else "",
                onSearchChange = if (state.isDm) ({ trainerSearch = it }) else null,
                searchLabel = "Cerca allenatore o squadra",
            )
            HomeTab.POKEMON -> SheetList(
                sheets = sortPokemonSheets(state.sheets.filter { it.isPokemon }),
                createLabel = "Cattura Pokémon",
                onCreate = { onCreate(true) },
                onSelect = onSelect,
                onSetClaimCode = null,
                searchQuery = if (state.isDm) pokemonSearch else "",
                onSearchChange = if (state.isDm) ({ pokemonSearch = it }) else null,
                searchLabel = "Cerca Pokémon, specie o allenatore",
            )
            HomeTab.CATALOG -> PokedexScreen(
                species = state.corebookSpecies, moves = state.catalogMoves,
                status = state.catalogStatus, loading = state.catalogLoading, onRefresh = onRefreshCatalog,
            )
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
    searchQuery: String = "",
    onSearchChange: ((String) -> Unit)? = null,
    searchLabel: String = "Cerca scheda",
) {
    val filtered = remember(sheets, searchQuery) { sheets.filter { it.matchesSearch(searchQuery) } }
    Column(Modifier.fillMaxSize()) {
        if (onSearchChange != null) {
            OutlinedTextField(value = searchQuery, onValueChange = onSearchChange,
                label = { Text(searchLabel) }, singleLine = true,
                trailingIcon = { if (searchQuery.isNotEmpty()) TextButton(onClick = { onSearchChange("") }) { Text("Pulisci") } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
            Text("${filtered.size} / ${sheets.size} schede", modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall)
        }
    LazyColumn(
        modifier = Modifier.weight(1f),
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
        if (filtered.isEmpty()) {
            item {
                Text(
                    if (searchQuery.isBlank()) "Nessuna scheda trovata" else "Nessuna scheda corrisponde alla ricerca",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        items(filtered, key = { it.recordId.ifBlank { it.storageKey } }) { sheet ->
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
}

@Composable
private fun SheetEditor(
    initial: EditableSheet,
    isDm: Boolean,
    trainers: List<EditableSheet>,
    saving: Boolean,
    uploadingPortrait: Boolean,
    moveTypes: Map<String, String>,
    corebookSpecies: List<CorebookSpecies>,
    catalogMoves: Map<String, CatalogMove>,
    catalogStatus: String,
    catalogLoading: Boolean,
    onRefreshCatalog: () -> Unit,
    onUploadPortrait: (EditableSheet, ByteArray) -> Unit,
    onSave: (EditableSheet) -> Unit,
) {
    var draft by remember(initial.storageKey, initial.raw) { mutableStateOf(initial) }
    val dmCapture = isDm && draft.isPokemon && draft.recordId.isBlank()
    val captureTrainerSelected = trainers.any { it.recordId == draft.trainerId && it.recordId.isNotBlank() }
    var chooseTrainer by remember { mutableStateOf(false) }
    if (chooseTrainer && dmCapture) CaptureTrainerPicker(trainers, onDismiss = { chooseTrainer = false }) {
        draft = draft.withCaptureTrainer(it)
        chooseTrainer = false
    }
    val reference = CorebookCatalog.find(corebookSpecies, draft)
    var captureSpecies by remember { mutableStateOf<CorebookSpecies?>(null) }
    var captureRank by remember { mutableStateOf(SheetRank.Starter) }
    var confirmPrefill by remember { mutableStateOf(false) }
    var chooseSpecies by remember { mutableStateOf(false) }
    var showEvolution by remember(draft.recordId) { mutableStateOf(false) }
    if (chooseSpecies) SpeciesPicker(corebookSpecies, onDismiss = { chooseSpecies = false }) {
        captureSpecies = it
        // The player's chosen rank is independent of the suggested species rank.
        chooseSpecies = false
    }
    if (showEvolution && reference != null) EvolutionDialog(
        sheet = draft, source = reference, catalog = corebookSpecies,
        onDismiss = { showEvolution = false },
        onEvolve = { draft = it; showEvolution = false },
    )
    if (confirmPrefill && captureSpecies != null) {
        AlertDialog(
            onDismissRequest = { confirmPrefill = false },
            title = { Text("Precompilare ${captureSpecies!!.name}?") },
            text = { Text("Sostituisce specie, tipi, dimensioni, Attributes, Social Attributes, HP, Will, Abilities, Happiness e Loyalty della nuova bozza. Svuota le mosse per consentirti di sceglierle. Conserva allenatore, soprannome e Skills. Nessuna scheda già salvata viene modificata.") },
            confirmButton = { TextButton(onClick = {
                draft = captureSpecies!!.prefill(draft, captureRank)
                confirmPrefill = false
            }) { Text("Precompila") } },
            dismissButton = { TextButton(onClick = { confirmPrefill = false }) { Text("Annulla") } },
        )
    }
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

        if (draft.isPokemon) {
            item {
                SheetSectionCard("Catalogo Pokérole 3.0") {
                    Text(catalogStatus, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = onRefreshCatalog, enabled = !catalogLoading && !saving) {
                        Text(if (catalogLoading) "Download in corso…" else "Aggiorna catalogo")
                    }
                }
            }
        }

        if (draft.isPokemon && draft.recordId.isBlank()) {
            item {
                SheetSectionCard("Cattura · Precompilazione") {
                    if (dmCapture) {
                        Text("Allenatore destinatario", fontWeight = FontWeight.SemiBold)
                        OutlinedButton(onClick = { chooseTrainer = true }, enabled = trainers.isNotEmpty() && !saving) {
                            Text(if (captureTrainerSelected) draft.trainerName else "Scegli allenatore da Supabase")
                        }
                        if (trainers.isEmpty()) Text("Nessun allenatore disponibile. Torna alla lista e aggiorna le schede, oppure crea prima un allenatore.")
                        else if (!captureTrainerSelected) Text("Seleziona l'allenatore prima di salvare la cattura.")
                    }
                    Text("Cerca specie o forma nel catalogo. Il Rank resta una tua scelta; le mosse vengono scelte separatamente.")
                    OutlinedButton(onClick = { chooseSpecies = true }) { Text(captureSpecies?.name ?: "Scegli specie / forma") }
                    ChoiceField("Rank", captureRank.name, SheetRank.entries.map { it.name }) { captureRank = SheetRank.valueOf(it) }
                    captureSpecies?.let { species ->
                        Text("${species.types.joinToString(" / ")} · Base HP ${species.baseHp} · ${species.height} · ${species.weight}")
                        Text("${species.abilities.joinToString(" & ")} · ${species.sourceLabel}")
                        Text("I valori base sono di Starter Rank. Avrai ${captureRank.attributePoints} punti da distribuire in Attributes, altrettanti in Social Attributes e ${captureRank.skillPoints} in Skills.")
                        Button(onClick = { confirmPrefill = true }, enabled = !saving) { Text("Precompila scheda") }
                    }
                }
            }
        }

        item {
            SheetSectionCard("Identità") {
                SheetPortrait(
                    sheet = draft,
                    modifier = Modifier.size(144.dp).align(Alignment.CenterHorizontally),
                )
                if (dmCapture) {
                    OutlinedTextField(value = draft.trainerName, onValueChange = {}, readOnly = true,
                        label = { Text("Allenatore selezionato") }, modifier = Modifier.fillMaxWidth())
                } else {
                    SheetField("Allenatore", draft.trainerName) { draft = draft.copy(trainerName = it) }
                }
                if (draft.isPokemon) {
                    SheetField("Soprannome", draft.pokemonName) { draft = draft.copy(pokemonName = it) }
                    SheetField("Specie", draft.speciesName) { draft = draft.copy(speciesName = it) }
                    OutlinedButton(onClick = { showEvolution = true },
                        enabled = !saving && draft.recordId.isNotBlank() && reference != null && reference.evolutions.isNotEmpty()) {
                        Text("Evolvi")
                    }
                    Text(when {
                        draft.recordId.isBlank() -> "Salva il Pokémon prima di evolverlo."
                        reference == null -> "Evoluzione non disponibile: aggiorna il catalogo e controlla specie, numero e forma."
                        reference.evolutions.isEmpty() -> "Nessuna evoluzione successiva indicata nel catalogo."
                        else -> "Scegli l'evoluzione e conferma con il DM. La modifica verrà registrata solo con Salva."
                    }, style = MaterialTheme.typography.bodySmall)
                    SheetField("Form", draft.speciesForm) { draft = draft.withRuleValue("form", it) }
                    FieldPair(
                        "Numero Pokédex", draft.pokedexNumber, { draft = draft.copy(pokedexNumber = it) },
                        "Tipo primario", draft.primaryType, { draft = draft.copy(primaryType = it) },
                    )
                    FieldPair(
                        "Tipo secondario", draft.secondaryType, { draft = draft.copy(secondaryType = it) },
                        "Taglia", draft.size, { draft = draft.copy(size = it) },
                    )
                    SheetField("Peso", draft.weight) { draft = draft.copy(weight = it) }
                    DotRatingField("Happiness", draft.happiness) { draft = draft.copy(happiness = it) }
                    DotRatingField("Loyalty", draft.loyalty) { draft = draft.copy(loyalty = it) }
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
            SheetSectionCard("Rank e riferimenti del manuale") {
                ChoiceField("Rank", draft.sheetRank?.name ?: "Non impostato", SheetRank.entries.map { it.name }) {
                    draft = draft.withRuleValue("rank", it)
                }
                draft.sheetRank?.let { rank ->
                    Text("Attributes: +${rank.attributePoints} · Social Attributes: +${rank.attributePoints} · Skills: ${rank.skillPoints} punti, limite ${rank.skillLimit}.")
                    if (!draft.isPokemon) Text("Per gli allenatori aggiungi anche i punti dell'età (manuale p. 41).")
                    if (rank == SheetRank.Master || rank == SheetRank.Champion) {
                        Text("Bonus Master: +3 HP/Will/Defense/Sp. Defense/Base Initiative e +2 dadi ai tiri con Skills. I bonus non sono punti Skill.")
                    }
                } ?: Text("Seleziona il Rank per applicare i limiti delle Skills; il vecchio riferimento non viene eliminato.")
                if (draft.isPokemon) {
                    if (reference == null) Text("Specie o forma senza dati verificati: Attributes in modalità manuale 1–12; consulta il Pokédex.")
                    else Text("${reference.name} · ${reference.form} · ${reference.sourceLabel}. Base e limiti sono riferimenti, non modificano i valori salvati.")
                }
            }
        }

        item {
            SheetSectionCard("Riferimenti rapidi") {
                SheetField("Base HP", draft.baseHpText ?: reference?.baseHp?.toString() ?: if (!draft.isPokemon) "4" else "") {
                    draft = draft.withRuleValue("base_hp", it)
                }
                if (draft.baseHpText != null && draft.baseHpValue == null) {
                    Text("Base HP non valido: inserisci un intero positivo (1–999). HP maximum non verrà ricalcolato.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                FieldPair(
                    "HP current", draft.hpActual, { draft = draft.copy(hpActual = it) },
                    "HP maximum", draft.hpTotal, { draft = draft.copy(hpTotal = it) },
                )
                FieldPair(
                    "Physical Defense",
                    draft.defenseActual, { draft = draft.copy(defenseActual = it) },
                    "Special Defense",
                    draft.defenseTotal, { draft = draft.copy(defenseTotal = it) },
                )
                FieldPair(
                    "Will current", draft.willActual, { draft = draft.copy(willActual = it) },
                    "Will maximum", draft.willTotal, { draft = draft.copy(willTotal = it) },
                )
                FieldPair(
                    "Initiative", draft.initiative, { draft = draft.copy(initiative = it) },
                    "Evasion", draft.evasion, { draft = draft.copy(evasion = it) },
                )
                SheetField("Strumento equipaggiato", draft.heldItem) { draft = draft.copy(heldItem = it) }
                SheetField("Effetto di stato", draft.statusEffect) { draft = draft.copy(statusEffect = it) }
                DotRatingField("Fatigue", draft.fatigue) { draft = draft.copy(fatigue = it) }
                DotRatingField("Actions used", draft.actionUsed) { draft = draft.copy(actionUsed = it) }
                OutlinedButton(onClick = { draft = draft.recalculateMaximums(reference) }) { Text("Calcola massimi HP / Will") }
                Text("HP = Base HP + Vitality; Will = 3 + Insight, più eventuali bonus Rank. Cambia solo i massimi, senza curare. Le varianti HP/Defense del manuale p. 27 restano da gestire con il DM.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(
                    onClick = { draft = draft.resetCurrentStats() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Reset statistiche")
                }
                Text(
                    "Riporta HP e Will ai rispettivi valori massimi, senza modificare Physical Defense e Special Defense.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (draft.isPokemon) {
            item {
                SheetSectionCard("Abilities") {
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
                SheetSectionCard("Nature") {
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
            DotStatsCard(
                "Attributes", draft.attributeFields(), draft, reference, updateDots,
                helpText = if (draft.isPokemon) "Valore attuale / limite; i valori base sono indicati separatamente."
                    else "Gli allenatori non hanno Special. Limite 5, con l'eccezione del Rank Champion (+2).",
            )
        }
        item {
            DotStatsCard("Social Attributes", SheetStats.socialAttributes, draft, reference, updateDots)
        }
        SheetStats.skillGroups.forEach { (group, fields) ->
            item {
                DotStatsCard("Skills · $group", fields, draft, reference, updateDots)
            }
        }

        if (draft.isPokemon) {
            if (reference != null && draft.sheetRank != null) {
                item {
                    SheetSectionCard("Moves · Suggerimenti per Rank") {
                        val known = draft.moves.filter(String::isNotBlank)
                        val moveLimit = draft.dotStats["attributes.insight"].orEmpty().count { it } + 3
                        Text("${known.size} / $moveLimit Moves · Insight + 3")
                        Text("Scegli le mosse; non vengono assegnate automaticamente. TM, tutor e deroghe del DM rimangono inseribili manualmente.", style = MaterialTheme.typography.bodySmall)
                        if (reference.anyMoveRank?.let { draft.sheetRank!!.ordinal >= it.ordinal } == true) {
                            Text("Any Move: questa specie può scegliere altre mosse con il DM. Inseriscile nella sezione Mosse; il catalogo ne mostrerà i dettagli.")
                        }
                        reference.availableMoves(draft.sheetRank!!).forEach { move ->
                            OutlinedButton(
                                onClick = { draft = draft.copy(moves = known + move.name) },
                                enabled = known.size < moveLimit && known.none { normalizeMoveName(it) == normalizeMoveName(move.name) },
                            ) { Text("${move.name} · ${move.type} · Rank ${move.rank.ordinal + 1} (${move.rank.name})") }
                        }
                    }
                }
            }
            item {
                SheetSectionCard("Mosse") {
                    val visibleMoves = draft.moves.ifEmpty { listOf("") }
                    visibleMoves.forEachIndexed { index, value ->
                        MoveFieldCard(
                            index = index,
                            value = value,
                            type = moveTypes[normalizeMoveName(value)].orEmpty(),
                            details = catalogMoves[normalizeMoveName(value)],
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
                enabled = !saving && !uploadingPortrait && (!dmCapture || captureTrainerSelected),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (saving) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("Salva su Supabase")
            }
        }
    }
}

@Composable
private fun CaptureTrainerPicker(
    trainers: List<EditableSheet>, onDismiss: () -> Unit, onSelect: (EditableSheet) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val rows = remember(trainers, query) {
        trainers.filter { !it.isPokemon && it.recordId.isNotBlank() && it.matchesSearch(query) }
            .sortedBy { it.trainerName.lowercase() }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Allenatori da Supabase") },
        text = {
            Column {
                SheetField("Cerca allenatore", query) { query = it }
                LazyColumn(Modifier.height(300.dp)) {
                    if (rows.isEmpty()) item { Text("Nessun allenatore corrisponde alla ricerca.") }
                    items(rows, key = { it.recordId }) { trainer ->
                        TextButton(onClick = { onSelect(trainer) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(trainer.trainerName, fontWeight = FontWeight.SemiBold)
                                Text("${trainer.team.ifBlank { "Senza squadra" }} · ID ${trainer.recordId.take(8)}",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("Annulla") } })
}

@Composable
private fun SpeciesPicker(
    species: List<CorebookSpecies>, onDismiss: () -> Unit, onSelect: (CorebookSpecies) -> Unit,
) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(search, species) {
        species.filter { it.name.contains(search.trim(), true) || it.number == search.trim().trimStart('#', '0') }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Species / Form") },
        text = {
            Column {
                SheetField("Nome o numero Pokédex", search) { search = it }
                Text("${filtered.size} risultati", style = MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.height(320.dp)) {
                    items(filtered, key = { it.name }) { species ->
                        TextButton(onClick = { onSelect(species) }) {
                            Text("#${species.number} ${species.name} · ${species.types.joinToString(" / ")}")
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } })
}

@Composable
private fun EvolutionDialog(
    sheet: EditableSheet, source: CorebookSpecies, catalog: List<CorebookSpecies>,
    onDismiss: () -> Unit, onEvolve: (EditableSheet) -> Unit,
) {
    var selectedName by remember(source.name) { mutableStateOf("") }
    var selectedAbility by remember(selectedName) { mutableStateOf("") }
    val route = source.evolutions.firstOrNull { it.targetName == selectedName }
    val target = catalog.firstOrNull { it.name.equals(selectedName, true) }
    val ability = selectedAbility.ifBlank {
        target?.abilities?.firstOrNull { it.equals(sheet.abilityName, true) }
            ?: target?.abilities?.singleOrNull().orEmpty()
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Evolvi ${sheet.displayName}") },
        text = {
            LazyColumn(Modifier.height(420.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    ChoiceField("Evoluzione", selectedName.ifBlank { "Scegli evoluzione" },
                        source.evolutions.map { it.targetName }.distinct()) { selectedName = it }
                    route?.let { Text(it.condition.ifBlank { "Condizioni da confermare con il DM" }) }
                    if (selectedName.isNotBlank() && target == null) Text("Questa destinazione non è disponibile nel catalogo. Nessuna modifica verrà eseguita.")
                }
                if (target != null) {
                    item {
                        Text("${source.name} → ${target.name} · ${target.types.joinToString(" / ")}")
                        if (target.abilities.isNotEmpty()) {
                            ChoiceField("Ability", ability.ifBlank { "Scegli abilità" }, target.abilities) { selectedAbility = it }
                        }
                        Text("Aggiorna specie, forma, tipi, dimensioni, Ability e Base HP. Ricalcola i massimi HP/Will senza curare; conserva Attributes attuali, Skills, Rank, mosse, soprannome, Nature, Happiness, Loyalty, note e immagine personalizzata.")
                        Text("Controlla con il DM condizioni, punti e valori della nuova forma: non vengono assegnati punti automaticamente. Premi poi Salva nella scheda per registrare l'evoluzione.")
                    }
                    item {
                        Text("Attributes attuali → base / massimo nuova specie", fontWeight = FontWeight.SemiBold)
                        target.attributes.forEach { (key, reference) ->
                            val current = sheet.dotStats["attributes.$key"].orEmpty().count { it }
                            Text("${SheetStats.attributes["attributes.$key"]}: $current → ${reference.base} / ${reference.limit}")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = target != null && (target.abilities.isEmpty() || ability in target.abilities), onClick = {
                target?.let { onEvolve(source.evolve(sheet, it, ability)) }
            }) { Text("Applica alla bozza") }
        }, dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } })
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

internal fun pokemonSpriteUrl(pokedexNumber: String): String? {
    val number = pokedexNumber.filter(Char::isDigit).toIntOrNull() ?: return null
    return "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/$number.png"
}

@Composable
private fun MoveFieldCard(
    index: Int,
    value: String,
    type: String,
    details: CatalogMove? = null,
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
            details?.let { move ->
                Text("${move.category} · Power ${move.power} · Target ${move.target}", style = MaterialTheme.typography.bodySmall)
                Text("Accuracy: ${move.accuracy} · Damage: ${move.damage.ifBlank { "—" }}", style = MaterialTheme.typography.bodySmall)
                if (move.effect.isNotBlank()) Text(move.effect, style = MaterialTheme.typography.bodySmall)
                if (move.description.isNotBlank()) Text(move.description, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

internal fun moveTypeColor(type: String): Color = when (type.lowercase()) {
    "normal" -> Color(0xFF6D6D4E)
    "fire" -> Color(0xFFC85216)
    "water" -> Color(0xFF3265C7)
    "electric" -> Color(0xFF9B7700)
    "grass" -> Color(0xFF3D7E27)
    "ice" -> Color(0xFF3C8585)
    "fighting", "fight" -> Color(0xFF9B2520)
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
    sheet: EditableSheet,
    reference: CorebookSpecies?,
    onChange: (String, List<Boolean>) -> Unit,
    helpText: String? = null,
) {
    SheetSectionCard(title) {
        if (helpText != null) {
            Text(helpText, style = MaterialTheme.typography.bodySmall)
        }
        fields.forEach { (key, label) ->
            val base = if (key in SheetStats.attributes) reference?.attributes?.get(key.substringAfter('.'))?.base else null
            DotRatingField(if (base != null) "$label · Base $base" else label, sheet.dotStats[key].orEmpty(), sheet.statRange(key, reference)) { onChange(key, it) }
        }
    }
}

@Composable
private fun DotRatingField(
    label: String,
    values: List<Boolean>,
    range: IntRange = 0..5,
    onValueChange: (List<Boolean>) -> Unit,
) {
    val rating = values.count { it }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text("$rating / ${range.last}", fontWeight = FontWeight.SemiBold)
        }
        if (rating !in range) Text("Valore salvato fuori intervallo (${range.first}–${range.last}): mantenuto finché non lo modifichi.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        Slider(
            value = rating.coerceIn(range).toFloat(),
            onValueChange = { raw ->
                val selected = raw.roundToInt().coerceIn(range)
                onValueChange(List(range.last) { it < selected })
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1,
        )
    }
}

@Composable
internal fun ChoiceField(label: String, value: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text("$label: $value") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false })
            }
        }
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
    // Bounds-only decoding intentionally returns null, even for a valid image.
    // Check the stream separately, then validate the populated dimensions below.
    readPortraitInput({ context.contentResolver.openInputStream(uri) }) {
        BitmapFactory.decodeStream(it, null, bounds)
    }
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Formato immagine non supportato" }

    var sampleSize = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > PORTRAIT_MAX_SIDE * 2) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val decoded = readPortraitInput({ context.contentResolver.openInputStream(uri) }) {
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
