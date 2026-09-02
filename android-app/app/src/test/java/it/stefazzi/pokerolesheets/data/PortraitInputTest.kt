package it.stefazzi.pokerolesheets.data

import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class PortraitInputTest {
    private class TrackedInput : ByteArrayInputStream(byteArrayOf(1, 2, 3)) {
        var closed = false
        override fun close() { closed = true; super.close() }
    }

    @Test fun boundsOnlyNullResultIsNotAReadFailure() {
        val input = TrackedInput()
        var width = -1
        var height = -1
        val result = readPortraitInput({ input }) {
            // Same contract as BitmapFactory with inJustDecodeBounds=true:
            // fill dimensions, return no bitmap.
            width = 259
            height = 238
            null
        }
        assertNull(result)
        assertEquals(259, width)
        assertEquals(238, height)
        assertTrue(input.closed)
    }

    @Test fun aMissingStreamDoesNotInvokeTheDecoder() {
        var called = false
        try {
            readPortraitInput({ null }) { called = true }
            fail("Missing stream must fail")
        } catch (error: IllegalStateException) {
            assertTrue(error.message!!.contains("Impossibile aprire"))
        }
        assertFalse(called)
    }

    @Test fun inputIsClosedOnDecodeError() {
        val input = TrackedInput()
        val failure = IOException("decode failure")
        try {
            readPortraitInput({ input }) { throw failure }
            fail("Expected decoder error")
        } catch (error: IOException) { assertSame(failure, error) }
        assertTrue(input.closed)
    }

    @Test fun eachPassOpensAndClosesItsOwnStream() {
        val inputs = mutableListOf<TrackedInput>()
        val open = { TrackedInput().also { inputs += it } }
        readPortraitInput(open) { it.read(); null }
        assertEquals(1, readPortraitInput(open) { it.read() })
        assertEquals(2, inputs.size)
        assertTrue(inputs.all { it.closed })
    }

    @Test fun contentAccessFailureIsNotReplacedWithAFormatError() {
        val failure = SecurityException("access denied")
        try {
            readPortraitInput({ throw failure }) { it.read() }
            fail("Expected access error")
        } catch (error: SecurityException) { assertSame(failure, error) }
    }
}
