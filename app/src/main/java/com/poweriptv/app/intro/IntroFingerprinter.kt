package com.poweriptv.app.intro

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin

/**
 * Hoert beim Abspielen mit (ohne den Ton zu veraendern) und erzeugt 8x pro Sekunde
 * einen 16-Bit-Ton-Fingerabdruck (Bandenergie-Verlauf nach Haitsma/Kalker).
 * Damit erkennt die App Vorspann-Musik, die in jeder Folge einer Serie gleich ist.
 */
@OptIn(UnstableApi::class)
class IntroFingerprinter : BaseAudioProcessor() {
    /** Nur bei Serien in den ersten Minuten aktiv (spart Rechenzeit). */
    @Volatile var enabled = false

    /** Fertige Fingerabdruecke: (epoch shl 48) or (sampleIndex shl 16) or hash. */
    val output = ConcurrentLinkedQueue<Long>()
    /** Zaehler, der sich bei jedem Spulen/Neustart erhoeht (alte Werte verwerfen). */
    @Volatile var epoch = 0L
        private set
    /** Verarbeitete Samples seit dem letzten Spulen (pro Kanal). */
    @Volatile var processedSamples = 0L
        private set
    @Volatile var sampleRate = 48_000
        private set

    private var channels = 2
    private val ring = FloatArray(N)
    private var ringPos = 0
    private var filled = 0
    private var sinceHop = 0
    private var prevBands: FloatArray? = null
    private var edges = IntArray(0)

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) return AudioFormat.NOT_SET
        channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        sampleRate = inputAudioFormat.sampleRate
        edges = bandEdges(sampleRate)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        if (enabled) analyze(inputBuffer.duplicate().order(ByteOrder.nativeOrder()))
        else processedSamples += remaining / (2 * channels)
        val out = replaceOutputBuffer(remaining)
        out.put(inputBuffer)
        out.flip()
    }

    override fun onFlush() {
        epoch++
        processedSamples = 0
        filled = 0; ringPos = 0; sinceHop = 0
        prevBands = null
    }

    override fun onReset() = onFlush()

    private fun analyze(buf: ByteBuffer) {
        val hop = sampleRate / FRAMES_PER_SECOND
        val frames = buf.remaining() / (2 * channels)
        for (f in 0 until frames) {
            var sum = 0f
            for (c in 0 until channels) sum += buf.short.toFloat()
            ring[ringPos] = sum / channels
            ringPos = (ringPos + 1) and (N - 1)
            if (filled < N) filled++
            processedSamples++
            if (++sinceHop >= hop && filled == N) {
                sinceHop = 0
                val h = hashFrame()
                if (h >= 0) output.add((epoch shl 48) or (processedSamples shl 16) or h.toLong())
                while (output.size > 20_000) output.poll()
            }
        }
    }

    private val re = FloatArray(N)
    private val im = FloatArray(N)

    /** 16-Bit-Hash des aktuellen Fensters; -1 bei Stille. */
    private fun hashFrame(): Int {
        for (i in 0 until N) {
            re[i] = ring[(ringPos + i) and (N - 1)] * WINDOW[i]
            im[i] = 0f
        }
        fft(re, im)
        val bands = FloatArray(BANDS + 1)
        var total = 0f
        for (b in 0..BANDS) {
            var e = 0f
            for (k in edges[b] until edges[b + 1]) e += re[k] * re[k] + im[k] * im[k]
            bands[b] = ln(1f + e)
            total += e
        }
        val prev = prevBands
        prevBands = bands
        if (prev == null || total < SILENCE) return -1
        var h = 0
        for (m in 0 until BANDS) {
            val d = (bands[m] - bands[m + 1]) - (prev[m] - prev[m + 1])
            if (d > 0) h = h or (1 shl m)
        }
        return h
    }

    companion object {
        const val FRAMES_PER_SECOND = 8
        const val FRAME_MS = 1000L / FRAMES_PER_SECOND
        private const val N = 2048
        private const val BANDS = 16
        private const val SILENCE = 1e9f
        private val WINDOW = FloatArray(N) { (0.5 - 0.5 * cos(2 * PI * it / (N - 1))).toFloat() }

        /** 17 logarithmisch verteilte Baender zwischen 300 und 3000 Hz (FFT-Bins). */
        private fun bandEdges(rate: Int): IntArray = IntArray(BANDS + 2) { i ->
            val f = 300.0 * Math.pow(3000.0 / 300.0, i.toDouble() / (BANDS + 1))
            (f * N / rate).toInt().coerceIn(1, N / 2 - 1)
        }.also { e -> for (i in 1 until e.size) if (e[i] <= e[i - 1]) e[i] = e[i - 1] + 1 }

        /** In-place Radix-2-FFT. */
        private fun fft(re: FloatArray, im: FloatArray) {
            val n = re.size
            var j = 0
            for (i in 1 until n) {
                var bit = n shr 1
                while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
                j = j xor bit
                if (i < j) { var t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t }
            }
            var len = 2
            while (len <= n) {
                val ang = -2 * PI / len
                val wr = cos(ang).toFloat(); val wi = sin(ang).toFloat()
                var i = 0
                while (i < n) {
                    var cr = 1f; var ci = 0f
                    for (k in 0 until len / 2) {
                        val ur = re[i + k]; val ui = im[i + k]
                        val vr = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                        val vi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                        re[i + k] = ur + vr; im[i + k] = ui + vi
                        re[i + k + len / 2] = ur - vr; im[i + k + len / 2] = ui - vi
                        val nr = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = nr
                    }
                    i += len
                }
                len = len shl 1
            }
        }
    }
}
