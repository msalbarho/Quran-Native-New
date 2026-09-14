package com.quransunah.app.data.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class OnDeviceSpeechTranscriberTest {
    @Test
    fun selectsTheFirstNonBlankRecognitionAlternative() {
        assertEquals(
            "الحمد لله رب العالمين",
            OnDeviceSpeechTranscriber.firstUsableResult(
                listOf("", "  الحمد لله رب العالمين  ", "الحمد لله"),
            ),
        )
    }

    @Test
    fun returnsAnEmptyTranscriptWhenNoAlternativeIsUsable() {
        assertEquals("", OnDeviceSpeechTranscriber.firstUsableResult(listOf("", "  ")))
        assertEquals("", OnDeviceSpeechTranscriber.firstUsableResult(null))
    }
}
