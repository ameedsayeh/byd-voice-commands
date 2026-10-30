package com.offline.bydvoice.nlu

import com.offline.bydvoice.voice.WakeWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WakeWordTest {
    @Test fun wakePhrasesAndOneBreathCommands() {
        assertEquals("", WakeWord.remainder("hi byd"))
        assertEquals("", WakeWord.remainder("hello bid"))
        assertEquals("", WakeWord.remainder("hey b y d"))
        assertEquals("open the windows", WakeWord.remainder("hi b y d open the windows"))
        assertEquals("turn on the ac", WakeWord.remainder("hello bite turn on the ac"))
        assertEquals("fan speed three", WakeWord.remainder("okay buy d fan speed three"))
        // command-word recognizer hears "hi" as "high"; Moonshine writes it properly
        assertEquals("opened the trunk", WakeWord.remainder("high bee y dee opened the trunk"))
        assertEquals("open the trunk", WakeWord.remainder("Hi BYD, open the trunk."))
    }

    @Test fun ordinarySpeechDoesNotWake() {
        assertNull(WakeWord.remainder("turn on the ac"))
        assertNull(WakeWord.remainder("hello how are you"))
        assertNull(WakeWord.remainder("it is a bit cold"))
        assertNull(WakeWord.remainder("driver seat heating high"))
    }
}
