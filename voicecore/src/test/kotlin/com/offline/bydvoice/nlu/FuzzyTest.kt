package com.offline.bydvoice.nlu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyTest {

    @Test fun metaphoneCollapsesSoundAlikes() {
        assertEquals(Fuzzy.metaphone("seat"), Fuzzy.metaphone("seed"))     // ST
        assertEquals(Fuzzy.metaphone("night"), Fuzzy.metaphone("knight"))  // NT (silent k, gh)
        assertEquals(Fuzzy.metaphone("fone"), Fuzzy.metaphone("phone"))    // FN (ph -> f)
        assertEquals(Fuzzy.metaphone("roof"), Fuzzy.metaphone("ruff"))     // RF
    }

    @Test fun metaphoneSeparatesDifferentSounds() {
        assertTrue(Fuzzy.metaphone("open") != Fuzzy.metaphone("close"))
        assertTrue(Fuzzy.metaphone("left") != Fuzzy.metaphone("right"))
    }

    @Test fun matchLiftsPhoneticMishear() {
        // A d/t initial confusion ("dill" heard for "till") is a classic recognizer mishear. Spelling
        // similarity is 0.75 (below the 0.78 floor) and Soundex can't help (different first letter), so
        // only the Metaphone lift (both -> "TL") carries it over - exactly the near-miss we want.
        assertTrue(Fuzzy.match("till", "dill") >= 0.78)
    }

    @Test fun matchStillRejectsUnrelated() {
        assertEquals(0.0, Fuzzy.match("open", "close"), 1e-9)
    }
}
