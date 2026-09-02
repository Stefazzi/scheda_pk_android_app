package it.stefazzi.pokerolesheets.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.auth.status.SessionStatus
import it.stefazzi.pokerolesheets.data.CatalogRepository
import it.stefazzi.pokerolesheets.data.CorebookSpecies
import it.stefazzi.pokerolesheets.data.CatalogMove
import it.stefazzi.pokerolesheets.data.SheetReferences
import it.stefazzi.pokerolesheets.data.CatalogItem
import it.stefazzi.pokerolesheets.data.InventorySlot
import it.stefazzi.pokerolesheets.data.ItemEdits
import it.stefazzi.pokerolesheets.data.NewItem
import it.stefazzi.pokerolesheets.data.ItemRepository
import it.stefazzi.pokerolesheets.data.FullCatalog
import it.stefazzi.pokerolesheets.data.normalizeMoveName
import it.stefazzi.pokerolesheets.data.CharacterRepository
import it.stefazzi.pokerolesheets.data.EditableSheet
import it.stefazzi.pokerolesheets.data.SupabaseProvider
import it.stefazzi.pokerolesheets.data.model.ClaimableTrainer
import it.stefazzi.pokerolesheets.data.model.PokemonSpecies
import it.stefazzi.pokerolesheets.data.model.UserProfileRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LoginPortal { DM, PLAYER }

data class AppUiState(
    val configured: Boolean = SupabaseProvider.isConfigured,
    val authLoading: Boolean = SupabaseProvider.isConfigured,
    val authenticated: Boolean = false,
    val email: String? = null,
    val portal: LoginPortal? = null,
    val profile: UserProfileRow? = null,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val uploadingPortrait: Boolean = false,
    val claiming: Boolean = false,
    val sheets: List<EditableSheet> = emptyList(),
    val claimableTrainers: List<ClaimableTrainer> = emptyList(),
    val catalog: List<PokemonSpecies> = emptyList(),
    val corebookSpecies: List<CorebookSpecies> = emptyList(),
    val catalogMoves: Map<String, CatalogMove> = emptyMap(),
    val catalogLoading: Boolean = false,
    val catalogStatus: String = "Catalogo locale di emergenza",
    val moveTypes: Map<String, String> = emptyMap(),
    val sheetReferences: SheetReferences = SheetReferences(),
    val items: List<CatalogItem> = emptyList(),
    val inventory: List<InventorySlot> = emptyList(),
    val itemsLoading: Boolean = false,
    val itemsReady: Boolean = false,
    val itemSaving: Boolean = false,
    val itemsStatus: String = "Catalogo oggetti non caricato",
    val selectedSheet: EditableSheet? = null,
    val message: String? = null,
) {
    val isDm: Boolean get() = portal == LoginPortal.DM && profile?.role == "dm"
    val needsTrainerClaim: Boolean
        get() = portal == LoginPortal.PLAYER && sheets.none { !it.isPokemon }
}

class AppViewModel(
    private val characterRepository: CharacterRepository?,
    private val catalogRepository: CatalogRepository,
    private val itemRepository: ItemRepository? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()
    private var pendingPortal: LoginPortal? = null
    private val localCatalogReady = CompletableDeferred<Unit>()
    private var catalogJob: Job? = null
    private var itemsJob: Job? = null

    init {
        loadCatalog()
        observeAuthentication()
    }

    fun login(portal: LoginPortal) {
        val repository = characterRepository ?: return
        pendingPortal = portal
        if (_uiState.value.authenticated) {
            activatePortal(portal)
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(authLoading = true, message = null) }
            runCatching { repository.signInWithGoogle() }
                .onSuccess { _uiState.update { it.copy(authLoading = false) } }
                .onFailure { error ->
                    pendingPortal = null
                    _uiState.update {
                        it.copy(authLoading = false, message = error.userMessage("Accesso non riuscito"))
                    }
                }
        }
    }

    fun logout() {
        val repository = characterRepository ?: return
        viewModelScope.launch {
            runCatching { repository.signOut() }
                .onFailure { error ->
                    _uiState.update { it.copy(message = error.userMessage("Disconnessione non riuscita")) }
                }
        }
    }

    fun leavePortal() {
        itemsJob?.cancel()
        pendingPortal = null
        _uiState.update {
            it.copy(portal = null, profile = null, sheets = emptyList(), selectedSheet = null,
                inventory = emptyList(), items = emptyList(), itemsReady = false, itemsLoading = false, itemSaving = false)
        }
    }

    fun refresh() {
        if (_uiState.value.portal == null) return
        loadAuthorizedData()
    }

    fun claimTrainer(trainerId: String, claimCode: String) {
        val repository = characterRepository ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(claiming = true, message = null) }
            runCatching { repository.claimTrainer(trainerId, claimCode) }
                .onSuccess {
                    _uiState.update { it.copy(claiming = false, message = "Scheda associata correttamente") }
                    loadAuthorizedData()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(claiming = false, message = error.userMessage("Associazione non riuscita"))
                    }
                }
        }
    }

    fun setTrainerClaimCode(trainerId: String, claimCode: String) {
        val repository = characterRepository ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, message = null) }
            runCatching { repository.setTrainerClaimCode(trainerId, claimCode) }
                .onSuccess {
                    _uiState.update { it.copy(saving = false, message = "Codice associazione aggiornato") }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(saving = false, message = error.userMessage("Creazione codice non riuscita"))
                    }
                }
        }
    }

    fun selectSheet(sheet: EditableSheet?) {
        _uiState.update { it.copy(selectedSheet = sheet, message = null) }
    }

    fun beginNewSheet(isPokemon: Boolean) {
        val state = _uiState.value
        if (!isPokemon && !state.isDm) {
            _uiState.update { it.copy(message = "Solo il DM può creare un nuovo allenatore") }
            return
        }
        val playerTrainer = state.sheets.firstOrNull { !it.isPokemon }
        val blank = EditableSheet.blank(isPokemon).let { sheet ->
            if (isPokemon && !state.isDm && playerTrainer != null) {
                sheet.copy(
                    trainerName = playerTrainer.trainerName,
                    trainerId = playerTrainer.recordId,
                )
            } else sheet
        }
        _uiState.update { it.copy(selectedSheet = blank, message = null) }
    }

    fun saveSheet(sheet: EditableSheet) {
        val repository = characterRepository ?: return
        val state = _uiState.value
        if (state.isDm && sheet.isPokemon && sheet.recordId.isBlank() &&
            state.sheets.none { !it.isPokemon && it.recordId.isNotBlank() && it.recordId == sheet.trainerId }) {
            _uiState.update { it.copy(message = "Seleziona un allenatore dall'elenco prima di salvare la cattura") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, message = null) }
            runCatching { repository.saveSheet(sheet) }
                .onSuccess { saved ->
                    _uiState.update { state ->
                        val updatedSheets = state.sheets
                            .filterNot { existing ->
                                existing.recordId == saved.recordId ||
                                    (sheet.recordId.isNotBlank() && existing.recordId == sheet.recordId)
                            }
                            .plus(saved)
                            .sortedWith(
                                compareBy<EditableSheet> { it.isPokemon }
                                    .thenBy { it.displayName.lowercase() },
                            )
                        state.copy(
                            saving = false,
                            selectedSheet = saved,
                            sheets = updatedSheets,
                            message = "Scheda salvata",
                        )
                    }
                    refresh()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(saving = false, message = error.userMessage("Salvataggio non riuscito"))
                    }
                }
        }
    }

    fun releasePokemon(sheet: EditableSheet) {
        val repository = characterRepository ?: return
        if (_uiState.value.saving || _uiState.value.uploadingPortrait) return
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, message = null) }
            runCatching { repository.releasePokemon(sheet) }
                .onSuccess {
                    _uiState.update { state -> state.copy(saving = false, selectedSheet = null,
                        sheets = state.sheets.filterNot { it.isPokemon && it.recordId == sheet.recordId },
                        message = "${sheet.displayName} liberato. La riga Pokémon è stata eliminata; lo storico resta soggetto ai limiti configurati.") }
                    refresh()
                }.onFailure { error ->
                    _uiState.update { it.copy(saving = false, message = error.userMessage("Impossibile liberare il Pokémon. Verifica anche la migrazione 05 su Supabase.")) }
                }
        }
    }

    fun uploadTrainerPortrait(sheet: EditableSheet, imageData: ByteArray) {
        val repository = characterRepository ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(uploadingPortrait = true, message = null) }
            runCatching { repository.uploadTrainerPortrait(sheet, imageData) }
                .onSuccess { saved ->
                    _uiState.update { state ->
                        state.copy(
                            uploadingPortrait = false,
                            selectedSheet = saved,
                            sheets = state.sheets
                                .filterNot { it.recordId == saved.recordId }
                                .plus(saved)
                                .sortedWith(
                                    compareBy<EditableSheet> { it.isPokemon }
                                        .thenBy { it.displayName.lowercase() },
                                ),
                            message = "Immagine allenatore aggiornata",
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            uploadingPortrait = false,
                            message = error.userMessage("Caricamento immagine non riuscito"),
                        )
                    }
                }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun observeAuthentication() {
        val repository = characterRepository ?: return
        viewModelScope.launch {
            repository.awaitAuthInitialization()
            repository.sessionStatus.collectLatest { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        _uiState.update {
                            it.copy(
                                authLoading = false,
                                authenticated = true,
                                email = repository.currentUserEmail,
                            )
                        }
                        pendingPortal?.let(::activatePortal)
                        if (_uiState.value.catalogMoves.isEmpty()) startCatalogRefresh(force = false)
                    }
                    is SessionStatus.NotAuthenticated -> {
                        catalogJob?.cancel()
                        itemsJob?.cancel()
                        pendingPortal = null
                        _uiState.update {
                            AppUiState(
                                configured = it.configured,
                                catalog = it.catalog,
                                corebookSpecies = it.corebookSpecies,
                                catalogMoves = it.catalogMoves,
                                catalogStatus = it.catalogStatus,
                                moveTypes = it.moveTypes,
                                sheetReferences = it.sheetReferences,
                                authLoading = false,
                            )
                        }
                    }
                    SessionStatus.Initializing -> _uiState.update { it.copy(authLoading = true) }
                    is SessionStatus.RefreshFailure -> _uiState.update {
                        it.copy(
                            authLoading = false,
                            authenticated = false,
                            portal = null,
                            inventory = emptyList(), items = emptyList(), itemsReady = false,
                            itemsLoading = false, itemSaving = false,
                            profile = null, sheets = emptyList(), selectedSheet = null,
                            message = "Sessione scaduta: accedi nuovamente",
                        )
                    }
                }
            }
        }
    }

    private fun activatePortal(portal: LoginPortal) {
        val repository = characterRepository ?: return
        pendingPortal = null
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, portal = null, message = null) }
            runCatching { repository.loadProfile() }
                .onSuccess { profile ->
                    val expectedRole = if (portal == LoginPortal.DM) "dm" else "player"
                    if (profile.role != expectedRole) {
                        _uiState.update {
                            it.copy(
                                loading = false,
                                profile = profile,
                                message = if (portal == LoginPortal.DM) {
                                    "Questo account non è autorizzato come DM"
                                } else {
                                    "Questo account è configurato come DM: usa Login DM"
                                },
                            )
                        }
                    } else {
                        _uiState.update { it.copy(portal = portal, profile = profile) }
                        loadAuthorizedData()
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(loading = false, message = error.userMessage("Profilo non disponibile"))
                    }
                }
        }
    }

    private fun loadAuthorizedData() {
        val repository = characterRepository ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, message = null) }
            runCatching { repository.loadSheets() }
                .onSuccess { sheets ->
                    val claimables = if (
                        _uiState.value.portal == LoginPortal.PLAYER && sheets.none { !it.isPokemon }
                    ) {
                        runCatching { repository.loadClaimableTrainers() }.getOrDefault(emptyList())
                    } else emptyList()
                    _uiState.update {
                        it.copy(loading = false, sheets = sheets, claimableTrainers = claimables)
                    }
                    refreshItems()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(loading = false, message = error.userMessage("Caricamento non riuscito"))
                    }
                }
        }
    }

    private fun loadCatalog() {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { catalogRepository.loadSheetReferences() } }
                .onSuccess { references -> _uiState.update { it.copy(sheetReferences = references) } }
                .onFailure { _uiState.update { it.copy(message = "Riferimenti Ability/Nature e sprite non disponibili") } }
            runCatching {
                withContext(Dispatchers.IO) {
                    Triple(catalogRepository.loadPokemon(), catalogRepository.loadMoveTypes(), catalogRepository.loadCorebookSpecies())
                }
            }.onSuccess { (pokemon, moveTypes, species) ->
                val verifiedTypes = species.flatMap { it.moves }.associate { normalizeMoveName(it.name) to it.type }
                _uiState.update { it.copy(catalog = pokemon, moveTypes = moveTypes + verifiedTypes, corebookSpecies = species) }
            }.onFailure {
                _uiState.update { it.copy(message = "Catalogo locale non disponibile: puoi compilare le schede manualmente") }
            }
            val cached = withContext(Dispatchers.IO) { catalogRepository.loadCachedCatalog() }
            if (cached != null) publishCatalog(cached, "Catalogo 3.0 salvato sul dispositivo")
            localCatalogReady.complete(Unit)
        }
    }

    fun refreshCatalog() = startCatalogRefresh(force = true)

    fun refreshItems() {
        val repository = itemRepository ?: return
        val profile = _uiState.value.profile?.userId ?: return
        if (_uiState.value.portal == null || _uiState.value.itemSaving) return
        itemsJob?.cancel()
        itemsJob = viewModelScope.launch {
            _uiState.update { it.copy(itemsLoading = true, itemsReady = false) }
            try {
                val items = repository.loadCatalog()
                val inventory = repository.loadInventory()
                if (_uiState.value.profile?.userId == profile && _uiState.value.portal != null) _uiState.update {
                    it.copy(items = items, inventory = inventory, itemsLoading = false, itemsReady = true,
                        itemsStatus = "${items.size} oggetti da Supabase. Aggiorna per ricevere le modifiche del DM.")
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (_uiState.value.profile?.userId == profile && _uiState.value.portal != null) _uiState.update {
                    it.copy(itemsLoading = false, itemsReady = false,
                        itemsStatus = "Oggetti non aggiornati: verifica la connessione e gli script 06 e 07. ${error.message.orEmpty()}")
                }
            }
        }
    }

    fun saveInventory(slot: InventorySlot, onSuccess: () -> Unit) {
        val repository = itemRepository ?: return
        val state = _uiState.value
        val profile = state.profile?.userId ?: return
        if (!state.itemsReady || state.itemSaving) return
        _uiState.update { it.copy(itemSaving = true) }
        viewModelScope.launch {
            try {
                val saved = repository.saveSlot(slot,state.isDm)
                if (_uiState.value.profile?.userId == profile && _uiState.value.portal != null) {
                    _uiState.update { it.copy(itemSaving = false, inventory = it.inventory.filterNot { old ->
                        old.trainerId == saved.trainerId && old.slotKey == saved.slotKey } + saved,
                        message = "Slot della borsa salvato") }
                    onSuccess()
                }
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                if (_uiState.value.profile?.userId == profile && _uiState.value.portal != null) _uiState.update {
                    it.copy(itemSaving = false, message = error.userMessage("Salvataggio borsa non riuscito"))
                }
            }
        }
    }

    fun createCatalogItem(item: NewItem, onSuccess: () -> Unit) {
        val repository = itemRepository ?: return
        val state = _uiState.value
        val profile = state.profile?.userId ?: return
        if (!state.isDm || !state.itemsReady || state.itemSaving) return
        _uiState.update { it.copy(itemSaving = true) }
        viewModelScope.launch {
            try {
                val saved = repository.createItem(item)
                if (_uiState.value.profile?.userId == profile && _uiState.value.portal != null) {
                    _uiState.update { it.copy(itemSaving = false, items = it.items.filterNot { row -> row.id == saved.id } + saved,
                        message = "Oggetto custom creato: disponibile nel catalogo e nelle borse") }
                    onSuccess()
                }
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                if (_uiState.value.profile?.userId == profile && _uiState.value.portal != null) _uiState.update {
                    it.copy(itemSaving = false, message = error.userMessage("Creazione oggetto non riuscita"))
                }
            }
        }
    }

    fun changeItemImage(item:CatalogItem, data:ByteArray?, sourceId:String?, onSuccess:()->Unit) {
        val repository=itemRepository ?: return
        val state=_uiState.value
        val profile=state.profile?.userId ?: return
        if(!state.isDm || !state.itemsReady || state.itemSaving) return
        _uiState.update { it.copy(itemSaving=true) }
        viewModelScope.launch {
            try {
                val saved=if(data!=null) repository.uploadImage(item,data)
                    else repository.setImage(item,if(sourceId!=null) "sprite" else "reset",sourceId=sourceId)
                if(_uiState.value.profile?.userId==profile && _uiState.value.portal!=null) {
                    _uiState.update { it.copy(itemSaving=false,items=it.items.map { row->if(row.id==saved.id)saved else row },
                        message="Immagine aggiornata. Gli altri dispositivi la riceveranno con Aggiorna oggetti e borsa.") }
                    onSuccess()
                }
            } catch(error:CancellationException) { throw error }
            catch(error:Exception) {
                if(_uiState.value.profile?.userId==profile && _uiState.value.portal!=null) _uiState.update {
                    it.copy(itemSaving=false,message=error.userMessage("Modifica immagine non riuscita: verifica anche lo script 10"))
                }
            }
        }
    }

    fun saveCatalogItem(item: CatalogItem, edits: ItemEdits, restore: Boolean, onSuccess: () -> Unit) {
        val repository = itemRepository ?: return
        val state = _uiState.value
        val profile = state.profile?.userId ?: return
        if (!state.isDm || !state.itemsReady || state.itemSaving) return
        _uiState.update { it.copy(itemSaving = true) }
        viewModelScope.launch {
            try {
                val saved = repository.saveItem(item,edits,restore)
                if (_uiState.value.profile?.userId == profile && _uiState.value.portal != null) {
                    _uiState.update { it.copy(itemSaving = false, items = it.items.map { row -> if(row.id == saved.id) saved else row },
                        message = "Catalogo aggiornato: modifica condivisa con tutte le borse collegate") }
                    onSuccess()
                }
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                if (_uiState.value.profile?.userId == profile && _uiState.value.portal != null) _uiState.update {
                    it.copy(itemSaving = false, message = error.userMessage("Modifica oggetto non riuscita"))
                }
            }
        }
    }

    private fun startCatalogRefresh(force: Boolean) {
        if (!_uiState.value.authenticated || catalogJob?.isActive == true) return
        catalogJob = viewModelScope.launch {
            localCatalogReady.await()
            if (!force && _uiState.value.catalogMoves.isNotEmpty()) return@launch
            _uiState.update { it.copy(catalogLoading = true, catalogStatus = "Download catalogo 3.0…") }
            try {
                val catalog = catalogRepository.downloadCatalog { progress ->
                    _uiState.update { it.copy(catalogStatus = progress) }
                }
                publishCatalog(catalog, "Catalogo 3.0 aggiornato da Supabase")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(catalogStatus = "Download non riuscito. Uso i dati locali (${it.corebookSpecies.size} specie/forme). Riprova con Aggiorna catalogo.")
                }
            } finally {
                _uiState.update { it.copy(catalogLoading = false) }
            }
        }
    }

    private fun publishCatalog(catalog: FullCatalog, status: String) {
        _uiState.update {
            it.copy(corebookSpecies = catalog.species, catalogMoves = catalog.moves,
                catalog = catalog.species.map { species ->
                    PokemonSpecies(species.name, species.number, species.types.first(), species.types.getOrElse(1) { "" })
                },
                moveTypes = it.moveTypes + catalog.moves.mapValues { (_, move) -> move.type },
                catalogStatus = "$status · ${catalog.species.size} specie/forme · ${catalog.moves.size} mosse")
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repository = if (SupabaseProvider.isConfigured) SupabaseProvider.repository() else null
                return AppViewModel(
                    characterRepository = repository,
                    catalogRepository = CatalogRepository(context.applicationContext, SupabaseProvider.json,
                        if (SupabaseProvider.isConfigured) SupabaseProvider.client else null),
                    itemRepository = if (SupabaseProvider.isConfigured) ItemRepository(SupabaseProvider.client) else null,
                ) as T
            }
        }
    }
}

private fun Throwable.userMessage(fallback: String): String =
    message?.takeIf(String::isNotBlank) ?: fallback
