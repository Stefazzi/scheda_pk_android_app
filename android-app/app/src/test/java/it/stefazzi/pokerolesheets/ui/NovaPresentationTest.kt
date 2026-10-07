package it.stefazzi.pokerolesheets.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class NovaPresentationTest {
    @Test fun voice_ends_every_answer_with_super_once() {
        assertEquals("Pronti! Super!!!", novaVoice("Pronti!"))
        assertEquals("Pronti! Super!!!", novaVoice("Pronti! Super!!!"))
    }

    @Test fun expression_tracks_loading_and_answer_tone() {
        assertEquals(NovaExpression.NEUTRAL, novaExpression(emptyList(), false))
        assertEquals(NovaExpression.THINKING, novaExpression(emptyList(), true))
        assertEquals(
            NovaExpression.CONFUSED,
            novaExpression(listOf(NovaChatMessage("Specifica quale Pokémon", false)), false),
        )
        assertEquals(
            NovaExpression.HAPPY,
            novaExpression(listOf(NovaChatMessage("Trovato! Super!!!", false)), false),
        )
    }
}
