package dev.sidali.echo

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaRecorder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/*
 * The audio path of a video call, without the call.
 *
 * Meet, Teams, Signal and WhatsApp all open the microphone the same way on Android: the phone goes into
 * "communication" mode, the app records from the VOICE_COMMUNICATION source (which turns on the phone's
 * own echo cancellation and noise suppression) and the voice is compressed with Opus before it is sent.
 * Echo does exactly that, keeps the take in memory, decodes it again and plays it back, so you hear what
 * the other side receives before their own app touches it. Nothing is written to disk.
 */
object Audio {
    const val RATE = 48_000
    const val SECONDS = 5
    /** A typical bitrate for one voice in a Meet or Teams call. */
    const val CALL_BITRATE = 32_000
    /** One level reading every 20 ms, the length of an Opus frame. */
    const val FRAME = RATE / 50
}

enum class MicKind { USB, WIRED, BLUETOOTH, PHONE }

/** The microphone a call would use right now. */
data class Mic(val name: String, val kind: MicKind)

/** One recording, in memory only. */
class Take(val raw: ShortArray, val call: ShortArray, val mic: Mic, val peakDb: Float, val loudDb: Float) {
    val level: Level get() = Levels.of(peakDb, loudDb)
}

enum class Level { SILENT, QUIET, GOOD, CLIPPING }

object Levels {
    /** Thresholds in dBFS on the loud part of the take (speech, not the pauses). */
    const val SILENT_DB = -55f
    const val QUIET_DB = -32f
    const val CLIP_DB = -0.5f

    fun of(peakDb: Float, loudDb: Float): Level = when {
        loudDb < SILENT_DB -> Level.SILENT
        peakDb >= CLIP_DB -> Level.CLIPPING
        loudDb < QUIET_DB -> Level.QUIET
        else -> Level.GOOD
    }

    /** dBFS of a block of 16-bit samples. */
    fun db(samples: ShortArray, from: Int, to: Int): Float {
        if (to <= from) return -96f
        var sum = 0.0
        for (i in from until to) { val s = samples[i] / 32768.0; sum += s * s }
        val rms = sqrt(sum / (to - from))
        return if (rms <= 0.0) -96f else max(-96f, (20 * log10(rms)).toFloat())
    }

    fun peakDb(samples: ShortArray): Float {
        var p = 0
        for (s in samples) p = max(p, kotlin.math.abs(s.toInt()))
        return if (p == 0) -96f else (20 * log10(p / 32768.0)).toFloat()
    }

    /** Loudness of the speech: the average of the loudest 30 % of 20 ms frames, so pauses don't count. */
    fun loudDb(samples: ShortArray): Float {
        val frames = (0 until samples.size / Audio.FRAME).map { db(samples, it * Audio.FRAME, (it + 1) * Audio.FRAME) }
        if (frames.isEmpty()) return -96f
        val top = frames.sortedDescending().take(max(1, frames.size * 3 / 10))
        return top.average().toFloat()
    }

    /** 0..1 for a meter, from -60 dBFS to 0. */
    fun meter(db: Float) = ((db + 60f) / 60f).coerceIn(0f, 1f)
}

object Route {
    /** What Android picks for a call, in its own order: USB headset, wired headset, Bluetooth, then the phone. */
    private val order = listOf(
        AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    )

    fun kindOf(type: Int) = when (type) {
        AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> MicKind.USB
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> MicKind.WIRED
        AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> MicKind.BLUETOOTH
        else -> MicKind.PHONE
    }

    /** The device a call would talk through: a headset if one is plugged in, otherwise the loudspeaker. */
    fun callDevice(am: AudioManager): AudioDeviceInfo? {
        val all = am.availableCommunicationDevices
        for (t in order) all.firstOrNull { it.type == t }?.let { return it }
        return all.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
    }

    fun micOf(d: AudioDeviceInfo?): Mic {
        val kind = d?.let { kindOf(it.type) } ?: MicKind.PHONE
        val name = d?.productName?.toString()?.trim().orEmpty()
        return Mic(if (kind == MicKind.PHONE || name.isEmpty()) "" else name, kind)
    }

    fun current(ctx: Context): Mic = micOf(callDevice(ctx.getSystemService(AudioManager::class.java)))
}

/**
 * Holds the phone in communication mode, on the device a call would use, for one record and its
 * playbacks. Always [close] it: it puts the phone back the way it was.
 */
class CallSession(ctx: Context) : AutoCloseable {
    private val am = ctx.getSystemService(AudioManager::class.java)
    private val oldMode = am.mode
    val device: AudioDeviceInfo? = Route.callDevice(am)

    init {
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        device?.let { am.setCommunicationDevice(it) }
    }

    /** Records [Audio.SECONDS] seconds the way a call app does. [onLevel] gets 0..1 every 20 ms. */
    @SuppressLint("MissingPermission") // asked by the screen before it calls this
    fun record(onLevel: (Float, Float) -> Unit, stop: () -> Boolean): Take? {
        val min = AudioRecord.getMinBufferSize(Audio.RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION, Audio.RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(min, Audio.FRAME * 2 * 4),
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return null }
        val total = Audio.RATE * Audio.SECONDS
        val pcm = ShortArray(total)
        var n = 0
        var routed: AudioDeviceInfo? = null
        try {
            rec.startRecording()
            while (n < total && !stop()) {
                val r = rec.read(pcm, n, minOf(Audio.FRAME, total - n))
                if (r <= 0) break
                if (routed == null) routed = rec.routedDevice
                onLevel(Levels.meter(Levels.db(pcm, n, n + r)), (n + r).toFloat() / total)
                n += r
            }
        } finally {
            runCatching { rec.stop() }
            rec.release()
        }
        if (n < Audio.RATE) return null
        val raw = pcm.copyOf(n)
        val mic = Route.micOf(routed?.takeIf { it.type != AudioDeviceInfo.TYPE_BUILTIN_MIC } ?: device)
        return Take(raw, Opus.roundTrip(raw) ?: raw, mic, Levels.peakDb(raw), Levels.loudDb(raw))
    }

    /** Plays [pcm] through the call device, the way a call app plays the other side. Blocks until done. */
    fun play(pcm: ShortArray, onProgress: (Float) -> Unit, stop: () -> Boolean) {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
            )
            .setAudioFormat(
                AudioFormat.Builder().setSampleRate(Audio.RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(Audio.FRAME * 2 * 8)
            .build()
        device?.let { track.setPreferredDevice(it) }
        try {
            track.play()
            var i = 0
            while (i < pcm.size && !stop()) {
                val w = track.write(pcm, i, minOf(Audio.FRAME, pcm.size - i))
                if (w <= 0) break
                i += w
                onProgress(i.toFloat() / pcm.size)
            }
            if (!stop()) {
                // Let the last buffer play out.
                val end = System.nanoTime() + 400_000_000L
                while (track.playbackHeadPosition < i && System.nanoTime() < end && !stop()) Thread.sleep(10)
            }
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    override fun close() {
        am.clearCommunicationDevice()
        am.mode = oldMode
    }
}

/**
 * Opus at a call bitrate and back, with the phone's own codecs (MediaCodec). Returns null if the phone
 * has no Opus encoder; the screen then plays the take as recorded.
 */
object Opus {
    private const val MIME = MediaFormat.MIMETYPE_AUDIO_OPUS
    private const val TIMEOUT_US = 10_000L
    private const val PRE_ROLL_NS = 80_000_000L

    fun roundTrip(pcm: ShortArray): ShortArray? = runCatching {
        val (head, packets) = encode(pcm)
        decode(head, packets, pcm.size)
    }.getOrNull()

    private fun bytesOf(pcm: ShortArray, from: Int, count: Int): ByteArray {
        val b = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in from until from + count) b.putShort(pcm[i])
        return b.array()
    }

    private class Packet(val data: ByteArray, val timeUs: Long)

    /** Returns the OpusHead and the encoded packets. */
    private fun encode(pcm: ShortArray): Pair<ByteArray, List<Packet>> {
        val f = MediaFormat.createAudioFormat(MIME, Audio.RATE, 1).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, Audio.CALL_BITRATE)
        }
        val enc = MediaCodec.createEncoderByType(MIME)
        enc.configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        enc.start()
        var head: ByteArray? = null
        val out = mutableListOf<Packet>()
        val info = MediaCodec.BufferInfo()
        var fed = 0
        var inputDone = false
        try {
            while (true) {
                if (!inputDone) {
                    val ii = enc.dequeueInputBuffer(TIMEOUT_US)
                    if (ii >= 0) {
                        val buf = enc.getInputBuffer(ii)!!
                        val count = minOf(buf.capacity() / 2, Audio.FRAME, pcm.size - fed)
                        val t = fed * 1_000_000L / Audio.RATE
                        if (count <= 0) {
                            enc.queueInputBuffer(ii, 0, 0, t, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            buf.clear(); buf.put(bytesOf(pcm, fed, count))
                            enc.queueInputBuffer(ii, 0, count * 2, t, 0)
                            fed += count
                        }
                    }
                }
                val oi = enc.dequeueOutputBuffer(info, TIMEOUT_US)
                if (oi >= 0) {
                    val b = enc.getOutputBuffer(oi)!!
                    val data = ByteArray(info.size).also { b.position(info.offset); b.get(it) }
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        if (head == null) head = data
                    } else if (data.isNotEmpty()) out += Packet(data, info.presentationTimeUs)
                    enc.releaseOutputBuffer(oi, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                } else if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED && head == null) {
                    enc.outputFormat.getByteBuffer("csd-0")?.let { bb -> head = ByteArray(bb.remaining()).also { bb.get(it) } }
                }
            }
        } finally {
            enc.stop(); enc.release()
        }
        return opusHead(head) to out
    }

    /** The 19-byte OpusHead: the encoder's own when it gives one, otherwise a standard mono header. */
    internal fun opusHead(fromEncoder: ByteArray?): ByteArray {
        val magic = "OpusHead".toByteArray()
        if (fromEncoder != null && fromEncoder.size >= 19 && fromEncoder.copyOf(8).contentEquals(magic)) return fromEncoder.copyOf(19)
        return ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(magic); put(1); put(1); putShort(312); putInt(Audio.RATE); putShort(0); put(0)
        }.array()
    }

    internal fun preSkip(head: ByteArray) = (head[10].toInt() and 0xFF) or ((head[11].toInt() and 0xFF) shl 8)

    private fun nanos(v: Long) = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).putLong(v).array()

    private fun decode(head: ByteArray, packets: List<Packet>, length: Int): ShortArray {
        val f = MediaFormat.createAudioFormat(MIME, Audio.RATE, 1).apply {
            setByteBuffer("csd-0", ByteBuffer.wrap(head))
            setByteBuffer("csd-1", ByteBuffer.wrap(nanos(preSkip(head) * 1_000_000_000L / Audio.RATE)))
            setByteBuffer("csd-2", ByteBuffer.wrap(nanos(PRE_ROLL_NS)))
        }
        val dec = MediaCodec.createDecoderByType(MIME)
        dec.configure(f, null, null, 0)
        dec.start()
        val out = ShortArray(length)
        var written = 0
        var next = 0
        var inputDone = false
        val info = MediaCodec.BufferInfo()
        try {
            while (true) {
                if (!inputDone) {
                    val ii = dec.dequeueInputBuffer(TIMEOUT_US)
                    if (ii >= 0) {
                        if (next >= packets.size) {
                            dec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val p = packets[next++]
                            dec.getInputBuffer(ii)!!.apply { clear(); put(p.data) }
                            dec.queueInputBuffer(ii, 0, p.data.size, p.timeUs, 0)
                        }
                    }
                }
                val oi = dec.dequeueOutputBuffer(info, TIMEOUT_US)
                if (oi >= 0) {
                    val b = dec.getOutputBuffer(oi)!!.order(ByteOrder.LITTLE_ENDIAN)
                    b.position(info.offset); b.limit(info.offset + info.size)
                    val s = b.asShortBuffer()
                    val count = minOf(s.remaining(), length - written)
                    if (count > 0) { s.get(out, written, count); written += count }
                    dec.releaseOutputBuffer(oi, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        } finally {
            dec.stop(); dec.release()
        }
        if (written < length / 2) error("Opus decoder returned $written of $length samples")
        return out
    }
}
