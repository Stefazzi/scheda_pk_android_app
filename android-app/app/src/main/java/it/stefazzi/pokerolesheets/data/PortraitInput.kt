package it.stefazzi.pokerolesheets.data

import java.io.InputStream

/** A null decoder result is valid for BitmapFactory's bounds-only pass.
 * Only a missing input stream means that the selected file could not be opened.
 */
internal fun <T> readPortraitInput(open: () -> InputStream?, decode: (InputStream) -> T): T {
    val stream = open() ?: error("Impossibile aprire l'immagine selezionata. Prova a scaricarla sul dispositivo e selezionarla nuovamente.")
    return stream.use(decode)
}
