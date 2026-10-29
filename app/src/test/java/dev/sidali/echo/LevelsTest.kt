package dev.sidali.echo

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class LevelsTest {
    private fun tone(amplitude: Double, seconds: Int = 1) =
        ShortArray(Audio.RATE * seconds) { (amplitude * 32767 * sin(2 * PI * 440 * it / Audio.RATE)).toInt().toShort() }

    @Test fun silenceIsSilent() {
        val s = ShortArray(Audio.RATE)
        assertEquals(Level.SILENT, Levels.of(Levels.peakDb(s), Levels.loudDb(s)))
    }

    @Test fun normalSpeechLevelIsGood() {
        val s = tone(0.1) // about -23 dBFS
        assertEquals(Level.GOOD, Levels.of(Levels.peakDb(s), Levels.loudDb(s)))
    }

    @Test fun faintVoiceIsQuiet() {
        val s = tone(0.01) // about -43 dBFS
        assertEquals(Level.QUIET, Levels.of(Levels.peakDb(s), Levels.loudDb(s)))
    }

    @Test fun fullScaleClips() {
        val s = tone(1.0)
        assertEquals(Level.CLIPPING, Levels.of(Levels.peakDb(s), Levels.loudDb(s)))
    }

    @Test fun pausesDoNotLowerTheSpeechLevel() {
        val s = ShortArray(Audio.RATE * 2).also { tone(0.1).copyInto(it) } // one second of voice, one of silence
        assertEquals(Levels.loudDb(tone(0.1)), Levels.loudDb(s), 0.5f)
    }

    @Test fun fallbackOpusHeadIsMonoAt48k() {
        val h = Opus.opusHead(null)
        assertEquals(19, h.size)
        assertEquals("OpusHead", String(h.copyOf(8)))
        assertEquals(1, h[9].toInt())
        assertEquals(312, Opus.preSkip(h))
    }
}
