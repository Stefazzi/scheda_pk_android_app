package it.stefazzi.pokerolesheets.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class UserProfileRow(
    @SerialName("user_id") val userId: String,
    val role: String,
    @SerialName("display_name") val displayName: String? = null,
)

@Serializable
data class TrainerRow(
    val id: String,
    @SerialName("owner_id") val ownerId: String? = null,
    @SerialName("legacy_name") val legacyName: String? = null,
    @SerialName("trainer_name") val trainerName: String,
    val team: String? = null,
    val age: String? = null,
    val money: String? = null,
    val reputation: String? = null,
    @SerialName("sheet_data") val sheetData: JsonElement,
)

@Serializable
data class PokemonRow(
    val id: String,
    @SerialName("trainer_id") val trainerId: String,
    @SerialName("legacy_name") val legacyName: String? = null,
    val nickname: String? = null,
    val species: String? = null,
    @SerialName("pokedex_number") val pokedexNumber: String? = null,
    @SerialName("primary_type") val primaryType: String? = null,
    @SerialName("secondary_type") val secondaryType: String? = null,
    @SerialName("team_slot") val teamSlot: Int? = null,
    @SerialName("sheet_data") val sheetData: JsonElement,
)

@Serializable
data class TrainerWrite(
    @SerialName("legacy_name") val legacyName: String,
    @SerialName("trainer_name") val trainerName: String,
    val team: String? = null,
    val age: String? = null,
    val money: String? = null,
    val reputation: String? = null,
    @SerialName("sheet_data") val sheetData: JsonElement,
)

@Serializable
data class PokemonWrite(
    @SerialName("trainer_id") val trainerId: String,
    @SerialName("legacy_name") val legacyName: String,
    val nickname: String? = null,
    val species: String,
    @SerialName("pokedex_number") val pokedexNumber: String? = null,
    @SerialName("primary_type") val primaryType: String? = null,
    @SerialName("secondary_type") val secondaryType: String? = null,
    @SerialName("team_slot") val teamSlot: Int? = null,
    @SerialName("sheet_data") val sheetData: JsonElement,
)

@Serializable
data class ClaimableTrainer(
    val id: String,
    @SerialName("trainer_name") val trainerName: String,
)

@Serializable
data class ClaimTrainerParams(
    @SerialName("p_trainer_id") val trainerId: String,
    @SerialName("p_claim_code") val claimCode: String,
)

@Serializable
data class SetTrainerClaimCodeParams(
    @SerialName("p_trainer_id") val trainerId: String,
    @SerialName("p_claim_code") val claimCode: String,
)
