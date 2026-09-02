package it.stefazzi.pokerolesheets.data

import kotlinx.serialization.json.*
import java.text.Normalizer
import java.util.Locale

data class NamedReference(val name: String, val description: String, val effect: String = "", val keywords: String = "", val configuration: String = "")

data class SheetReferences(
    val abilities: List<NamedReference> = emptyList(),
    val natures: List<NamedReference> = emptyList(),
    val sprites: JsonObject = JsonObject(emptyMap()),
) {
    fun ability(name: String) = abilities.firstOrNull { referenceKey(it.name) == referenceKey(name) }
    fun nature(name: String) = natures.firstOrNull { referenceKey(it.name) == referenceKey(name) }
    fun spritePaths(number: String, name: String, form: String, shiny: Boolean): List<String> {
        val digits = number.trim().removePrefix("#").trim()
        val dex = digits.toIntOrNull()?.takeIf { it > 0 } ?: return emptyList()
        val fullName = if ('(' in name || form.isBlank() || form.equals("Standard", true)) name else "$name ($form)"
        val isForm = '(' in fullName || (!form.equals("Standard", true) && form.isNotBlank())
        val entry = if (isForm) sprites[referenceKey(fullName)] else sprites["dex:$dex"]
        val paths = (entry as? JsonObject)?.get(if (shiny) "shiny" else "normal") as? JsonArray
        return paths.orEmpty().map { "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/${it.jsonPrimitive.content}" }
    }

    companion object {
        fun parse(abilities: String, natures: String, sprites: String): SheetReferences {
            fun entries(text: String) = Json.parseToJsonElement(text).jsonArray.map { value ->
                val row = value.jsonObject
                fun string(key: String) = row[key]?.jsonPrimitive?.contentOrNull.orEmpty()
                NamedReference(string("Name"), string("Description"), string("Effect"), string("Keywords"), string("Nature"))
            }
            return SheetReferences(entries(abilities), entries(natures), Json.parseToJsonElement(sprites).jsonObject.getValue("entries").jsonObject)
        }
    }
}

fun referenceKey(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")

fun EditableSheet.selectAbility(reference: NamedReference) = copy(abilityName = reference.name,
    abilitySearch = reference.name, abilityEffect = reference.effect, abilityDescription = reference.description)
fun EditableSheet.selectNature(reference: NamedReference) = copy(natureName = reference.name,
    natureSearch = reference.name, natureConfiguration = reference.configuration,
    natureKeywords = reference.keywords, natureDescription = reference.description)

fun EditableSheet.releaseIdentifiers(): Pair<String, String> {
    require(isPokemon && recordId.isNotBlank() && !trainerId.isNullOrBlank()) { "Seleziona un Pokémon già salvato e associato a un allenatore" }
    return recordId to requireNotNull(trainerId)
}
