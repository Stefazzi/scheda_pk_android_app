package it.stefazzi.pokerolesheets.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PokemonSpecies(
    val pokemon: String,
    val nr: String,
    val tipo1: String,
    val tipo2: String = "",
)
