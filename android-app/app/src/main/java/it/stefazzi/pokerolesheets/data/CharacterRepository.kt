package it.stefazzi.pokerolesheets.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import it.stefazzi.pokerolesheets.data.model.ClaimTrainerParams
import it.stefazzi.pokerolesheets.data.model.ClaimableTrainer
import it.stefazzi.pokerolesheets.data.model.PokemonRow
import it.stefazzi.pokerolesheets.data.model.PokemonWrite
import it.stefazzi.pokerolesheets.data.model.SetTrainerClaimCodeParams
import it.stefazzi.pokerolesheets.data.model.TrainerRow
import it.stefazzi.pokerolesheets.data.model.TrainerWrite
import it.stefazzi.pokerolesheets.data.model.UserProfileRow
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Duration.Companion.hours

class CharacterRepository(
    private val client: SupabaseClient,
    private val json: Json,
) {
    val sessionStatus: StateFlow<SessionStatus>
        get() = client.auth.sessionStatus

    val currentUserEmail: String?
        get() = client.auth.currentUserOrNull()?.email

    suspend fun awaitAuthInitialization() = client.auth.awaitInitialization()

    suspend fun signInWithGoogle() {
        client.auth.signInWith(Google)
    }

    suspend fun signOut() {
        client.auth.signOut()
    }

    suspend fun loadProfile(): UserProfileRow {
        val userId = requireNotNull(client.auth.currentUserOrNull()?.id) {
            "Sessione non disponibile"
        }
        return client.from(PROFILES_TABLE)
            .select {
                filter { eq("user_id", userId) }
                limit(1)
            }
            .decodeSingle<UserProfileRow>()
    }

    suspend fun loadSheets(): List<EditableSheet> = coroutineScope {
        val trainersRequest = async {
            client.from(TRAINERS_TABLE).select().decodeList<TrainerRow>()
        }
        val pokemonRequest = async {
            client.from(POKEMON_TABLE).select().decodeList<PokemonRow>()
        }
        val trainers = trainersRequest.await()
        val trainerNames = trainers.associate { it.id to it.trainerName }
        val pokemon = pokemonRequest.await()

        val sheets = trainers.map { it.toEditableSheet() } +
            pokemon.map { it.toEditableSheet(trainerNames[it.trainerId].orEmpty()) }
        sheets.map { sheet -> async { sheet.withResolvedPortrait() } }
            .map { it.await() }
            .sortedWith(compareBy<EditableSheet> { it.isPokemon }.thenBy { it.displayName.lowercase() })
    }

    suspend fun loadClaimableTrainers(): List<ClaimableTrainer> = client.postgrest
        .rpc("list_claimable_trainers")
        .decodeList()

    suspend fun claimTrainer(trainerId: String, claimCode: String) {
        require(claimCode.isNotBlank()) { "Inserisci il codice fornito dal DM" }
        client.postgrest.rpc(
            function = "claim_trainer",
            parameters = ClaimTrainerParams(trainerId, claimCode.trim()),
        )
    }

    suspend fun setTrainerClaimCode(trainerId: String, claimCode: String) {
        require(claimCode.trim().length >= 6) { "Il codice deve contenere almeno 6 caratteri" }
        client.postgrest.rpc(
            function = "set_trainer_claim_code",
            parameters = SetTrainerClaimCodeParams(trainerId, claimCode.trim()),
        )
    }

    suspend fun saveSheet(sheet: EditableSheet): EditableSheet {
        require(sheet.trainerName.isNotBlank()) { "Inserisci il nome dell'allenatore" }
        return if (sheet.isPokemon) savePokemon(sheet) else saveTrainer(sheet)
    }

    suspend fun uploadTrainerPortrait(sheet: EditableSheet, imageData: ByteArray): EditableSheet {
        require(sheet.recordId.isNotBlank()) { "Salva la scheda prima di aggiungere l'immagine" }
        require(imageData.isNotEmpty()) { "Immagine non valida" }
        require(imageData.size <= MAX_PORTRAIT_BYTES) { "L'immagine supera il limite di 2 MB" }

        val objectPath = "${if (sheet.isPokemon) "pokemon" else "trainers"}/${sheet.recordId}/portrait.webp"
        client.storage.from(MEDIA_BUCKET).upload(objectPath, imageData) {
            upsert = true
            contentType = ContentType("image", "webp")
        }
        val reference = "$STORAGE_PREFIX$objectPath"
        val signedUrl = client.storage.from(MEDIA_BUCKET)
            .createSignedUrl(objectPath, SIGNED_URL_DURATION)
        return saveSheet(sheet.copy(profilePicture = reference))
            .copy(resolvedProfilePicture = signedUrl)
    }

    suspend fun releasePokemon(sheet: EditableSheet) {
        val (pokemonId, trainerId) = sheet.releaseIdentifiers()
        // One database transaction deletes exactly this row and updates its trainer team.
        client.postgrest.rpc("release_pokemon", buildJsonObject {
            put("p_pokemon_id", pokemonId)
            put("p_trainer_id", trainerId)
        })
    }

    private suspend fun saveTrainer(sheet: EditableSheet): EditableSheet {
        val storageKey = sheet.storageKey.ifBlank { sheet.trainerName.trim() }
        val normalized = sheet.copy(storageKey = storageKey)
        val payload = normalized.toTrainerWrite()
        val row = if (sheet.recordId.isBlank()) {
            client.from(TRAINERS_TABLE).insert(payload) { select() }.decodeSingle<TrainerRow>()
        } else {
            client.from(TRAINERS_TABLE).update(payload) {
                filter { eq("id", sheet.recordId) }
                select()
            }.decodeSingle<TrainerRow>()
        }
        return row.toEditableSheet()
    }

    private suspend fun savePokemon(sheet: EditableSheet): EditableSheet {
        require(sheet.pokemonName.isNotBlank() || sheet.speciesName.isNotBlank()) {
            "Inserisci il soprannome o la specie del Pokémon"
        }
        val trainer = resolveTrainer(sheet)
        val storageKey = sheet.storageKey.ifBlank {
            listOf(trainer.trainerName, sheet.pokemonName.ifBlank { sheet.speciesName })
                .map(String::trim)
                .filter(String::isNotBlank)
                .joinToString("_")
        }
        val slot = sheet.teamSlot ?: firstAvailableTeamSlot(trainer.id)
        val normalized = sheet.copy(
            storageKey = storageKey,
            trainerId = trainer.id,
            trainerName = trainer.trainerName,
            teamSlot = slot,
        )
        val payload = normalized.toPokemonWrite(trainer.id)
        val row = if (sheet.recordId.isBlank()) {
            client.from(POKEMON_TABLE).insert(payload) { select() }.decodeSingle<PokemonRow>()
        } else {
            client.from(POKEMON_TABLE).update(payload) {
                filter { eq("id", sheet.recordId) }
                select()
            }.decodeSingle<PokemonRow>()
        }
        synchronizeTrainerTeam(trainer.id)
        return row.toEditableSheet(trainer.trainerName)
    }

    private suspend fun resolveTrainer(sheet: EditableSheet): TrainerRow {
        sheet.trainerId?.takeIf(String::isNotBlank)?.let { trainerId ->
            return client.from(TRAINERS_TABLE).select {
                filter { eq("id", trainerId) }
                limit(1)
            }.decodeSingleOrNull<TrainerRow>()
                ?: error("Allenatore selezionato non trovato o non accessibile. Aggiorna l'elenco e riprova.")
        }

        return client.from(TRAINERS_TABLE).select {
            filter { eq("trainer_name", sheet.trainerName.trim()) }
            limit(1)
        }.decodeSingleOrNull<TrainerRow>()
            ?: error("Allenatore non trovato o non accessibile")
    }

    private suspend fun firstAvailableTeamSlot(trainerId: String): Int? {
        val occupied = client.from(POKEMON_TABLE).select {
            filter { eq("trainer_id", trainerId) }
        }.decodeList<PokemonRow>().mapNotNull(PokemonRow::teamSlot).toSet()
        return (1..MAX_TEAM_SIZE).firstOrNull { it !in occupied }
    }

    private suspend fun synchronizeTrainerTeam(trainerId: String) {
        val trainer = client.from(TRAINERS_TABLE).select {
            filter { eq("id", trainerId) }
            limit(1)
        }.decodeSingle<TrainerRow>()
        val pokemon = client.from(POKEMON_TABLE).select {
            filter { eq("trainer_id", trainerId) }
        }.decodeList<PokemonRow>()
        val namesBySlot = pokemon.mapNotNull { row ->
            row.teamSlot?.let { it to row.nickname.orEmpty().ifBlank { row.species.orEmpty() } }
        }.toMap()
        val updatedTrainer = trainer.toEditableSheet().copy(
            pokemonTeam = (1..MAX_TEAM_SIZE).map { namesBySlot[it].orEmpty() },
        )
        client.from(TRAINERS_TABLE).update(updatedTrainer.toTrainerWrite()) {
            filter { eq("id", trainerId) }
        }
    }

    private fun TrainerRow.toEditableSheet(): EditableSheet = EditableSheet
        .from(legacyName ?: id, sheetData, json)
        .copy(
            isPokemon = false,
            recordId = id,
            trainerId = id,
            ownerId = ownerId,
            trainerName = trainerName,
            team = team.orEmpty(),
            age = age.orEmpty(),
            money = money.orEmpty(),
            reputation = reputation.orEmpty(),
        )

    private fun PokemonRow.toEditableSheet(parentTrainerName: String): EditableSheet = EditableSheet
        .from(legacyName ?: id, sheetData, json)
        .copy(
            isPokemon = true,
            recordId = id,
            trainerId = trainerId,
            teamSlot = teamSlot,
            trainerName = parentTrainerName,
            pokemonName = nickname.orEmpty(),
            speciesName = species.orEmpty(),
            pokedexNumber = pokedexNumber.orEmpty(),
            primaryType = primaryType.orEmpty(),
            secondaryType = secondaryType.orEmpty(),
        )

    private fun EditableSheet.toTrainerWrite() = TrainerWrite(
        legacyName = storageKey,
        trainerName = trainerName.trim(),
        team = team.trim().ifBlank { null },
        age = age.trim().ifBlank { null },
        money = money.trim().ifBlank { null },
        reputation = reputation.trim().ifBlank { null },
        sheetData = updatedJson(),
    )

    private fun EditableSheet.toPokemonWrite(parentTrainerId: String) = PokemonWrite(
        trainerId = parentTrainerId,
        legacyName = storageKey,
        nickname = pokemonName.trim().ifBlank { null },
        species = speciesName.trim(),
        pokedexNumber = pokedexNumber.trim().ifBlank { null },
        primaryType = primaryType.trim().ifBlank { null },
        secondaryType = secondaryType.trim().ifBlank { null },
        teamSlot = teamSlot,
        sheetData = updatedJson(),
    )

    private suspend fun EditableSheet.withResolvedPortrait(): EditableSheet {
        val objectPath = profilePicture
            .takeIf { it.startsWith(STORAGE_PREFIX) }
            ?.removePrefix(STORAGE_PREFIX)
            ?: return this
        return runCatching {
            val signedUrl = client.storage.from(MEDIA_BUCKET)
                .createSignedUrl(objectPath, SIGNED_URL_DURATION)
            copy(resolvedProfilePicture = signedUrl)
        }.getOrDefault(this)
    }

    private companion object {
        const val PROFILES_TABLE = "user_profiles"
        const val TRAINERS_TABLE = "trainers"
        const val POKEMON_TABLE = "pokemon"
        const val MAX_TEAM_SIZE = 6
        const val MEDIA_BUCKET = "pokerole-media"
        const val STORAGE_PREFIX = "storage://pokerole-media/"
        const val MAX_PORTRAIT_BYTES = 2 * 1024 * 1024
        val SIGNED_URL_DURATION = 12.hours
    }
}
