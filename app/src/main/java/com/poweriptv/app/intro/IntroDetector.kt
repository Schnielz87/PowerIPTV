package com.poweriptv.app.intro

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest

/**
 * Lernt den Vorspann einer Serie selbst:
 * 1. Waehrend jeder Folge wird fuer die ersten 10 Minuten ein Ton-Fingerabdruck aufgezeichnet.
 * 2. Liegen zwei Folgen derselben Serie vor, wird der laengste gemeinsame Abschnitt gesucht
 *    (= Vorspann-Musik) und als Vorlage gespeichert.
 * 3. In jeder weiteren Folge wird der Ton live mit der Vorlage verglichen: Sobald der Vorspann
 *    laeuft, sind Beginn und Ende dieser Folge bekannt – egal, wo er in der Folge liegt.
 */
class IntroDetector(private val baseDir: File) {
    data class Intro(val startMs: Long, val endMs: Long)

    private val fps = IntroFingerprinter.FRAMES_PER_SECOND
    private val frames = (WINDOW_MS * fps / 1000).toInt()
    private fun idxOf(ms: Long) = (ms * fps / 1000).toInt()
    private fun msOf(idx: Int) = idx.toLong() * 1000 / fps

    private var seriesDir: File? = null
    private var episodeFile: File? = null
    private var fp = IntArray(frames) { -1 }
    private var recorded = 0
    private var template: IntArray? = null
    private var lastMatchStart = Long.MIN_VALUE
    private var lastEpoch = -1L

    /** Fuer diese Folge erkannter Vorspann (null = noch nicht erkannt). */
    @Volatile var detected: Intro? = null
        private set

    /** Neue Folge beginnt. */
    fun startEpisode(seriesKey: String, episodeUrl: String) {
        val dir = File(baseDir, md5(seriesKey)).apply { mkdirs() }
        seriesDir = dir
        episodeFile = File(dir, md5(episodeUrl) + ".fp")
        fp = episodeFile?.takeIf { it.exists() }?.let { readInts(it) }?.takeIf { it.size == frames } ?: IntArray(frames) { -1 }
        recorded = fp.count { it >= 0 }
        template = File(dir, "intro.tpl").takeIf { it.exists() }?.let { readInts(it) }?.takeIf { it.size >= MIN_FRAMES }
        detected = null
        lastMatchStart = Long.MIN_VALUE
    }

    fun stop() {
        seriesDir = null; episodeFile = null; detected = null
    }

    val active get() = seriesDir != null
    val hasTemplate get() = template != null

    /**
     * Neue Fingerabdruecke uebernehmen. [playerPosMs] = aktuelle Wiedergabeposition,
     * daraus wird die Zeit jedes Abdrucks berechnet.
     */
    fun drain(fpr: IntroFingerprinter, playerPosMs: Long) {
        if (seriesDir == null) { fpr.output.clear(); return }
        val epoch = fpr.epoch
        val processed = fpr.processedSamples
        val rate = fpr.sampleRate.coerceAtLeast(8000)
        if (epoch != lastEpoch) { lastEpoch = epoch }
        while (true) {
            val v = fpr.output.poll() ?: break
            if ((v ushr 48) != epoch) continue
            val sample = (v ushr 16) and 0xFFFFFFFFL
            val hash = (v and 0xFFFF).toInt()
            val pos = playerPosMs - (processed - sample) * 1000 / rate
            val idx = idxOf(pos)
            if (idx in 0 until frames) {
                if (fp[idx] < 0) recorded++
                fp[idx] = hash
            }
        }
        detectLive(playerPosMs)
    }

    /** Live-Vergleich der letzten 3 Sekunden mit der Vorlage (Bitfehlerrate). */
    private fun detectLive(posMs: Long) {
        val tpl = template ?: return
        if (detected != null) return
        val w = LIVE_WINDOW
        val end = idxOf(posMs) - fps / 2 // Ton wird etwas vor der Wiedergabe verarbeitet
        if (end < w || end >= frames || tpl.size < w) return
        val start = end - w
        var bestBits = Int.MAX_VALUE; var bestJ = -1
        for (j in 0..tpl.size - w) {
            var bits = 0; var valid = 0
            for (k in 0 until w) {
                val a = fp[start + k]; val b = tpl[j + k]
                if (a < 0 || b < 0) continue
                valid++
                bits += Integer.bitCount(a xor b)
            }
            if (valid < w * 3 / 4) continue
            val scaled = bits * w / valid
            if (scaled < bestBits) { bestBits = scaled; bestJ = j }
        }
        if (bestJ < 0 || bestBits.toDouble() / (w * 16) > LIVE_BER) { lastMatchStart = Long.MIN_VALUE; return }
        val introStart = msOf(start - bestJ)
        // Zweimal hintereinander gleiches Ergebnis -> sicher
        if (kotlin.math.abs(introStart - lastMatchStart) <= 1_000) {
            detected = Intro(introStart.coerceAtLeast(0), introStart + msOf(tpl.size))
        }
        lastMatchStart = introStart
    }

    /**
     * Fingerabdruck dieser Folge speichern und – falls noch keine Vorlage existiert –
     * mit frueheren Folgen vergleichen. Laeuft im Hintergrund.
     */
    fun finishEpisode(): (() -> Unit)? {
        val dir = seriesDir ?: return null
        val file = episodeFile ?: return null
        if (recorded < MIN_RECORDED) return null
        val data = fp.copyOf()
        // Wurde der Vorspann in dieser Folge nicht erkannt, Vorlage neu lernen (selbstkorrigierend)
        val relearn = template == null || detected == null
        return {
            writeInts(file, data)
            // aelteste Abdruecke loeschen (max. 4 pro Serie)
            dir.listFiles { f -> f.name.endsWith(".fp") }.orEmpty().sortedByDescending { it.lastModified() }.drop(4).forEach { it.delete() }
            if (relearn) {
                val others = dir.listFiles { f -> f.name.endsWith(".fp") && f != file }.orEmpty().sortedByDescending { it.lastModified() }
                for (o in others) {
                    val other = readInts(o)?.takeIf { it.size == frames } ?: continue
                    val seg = longestCommon(data, other) ?: continue
                    writeInts(File(dir, "intro.tpl"), data.copyOfRange(seg.first, seg.second))
                    break
                }
            }
        }
    }

    /**
     * Laengster gemeinsamer Abschnitt (Start, Ende als Index in [a]) zweier Folgen:
     * fuer jede Verschiebung die Bitfehlerrate entlang der Diagonale (jede 4. Probe, gleitend ueber 2 s).
     */
    private fun longestCommon(a: IntArray, b: IntArray): Pair<Int, Int>? {
        val maxShift = idxOf(MAX_SHIFT_MS)
        val step = 4; val k = 16; val gap = 4
        val limit = (LEARN_BER * 16 * k).toInt()
        var bestLen = 0; var bestStart = 0
        val h = IntArray(a.size / step + 1)
        for (d in -maxShift..maxShift) {
            val lo = maxOf(0, -d); val hi = minOf(a.size, b.size - d)
            if (hi - lo < 64) continue
            var n = 0
            var i = lo
            while (i < hi) {
                val x = a[i]; val y = b[i + d]
                h[n++] = if (x >= 0 && y >= 0) Integer.bitCount(x xor y) else 8
                i += step
            }
            if (n <= k) continue
            var sum = 0
            for (q in 0 until k) sum += h[q]
            var rs = -1; var last = -1; var miss = 0
            for (p in 0..n - k) {
                if (p > 0) sum += h[p + k - 1] - h[p - 1]
                if (sum <= limit) {
                    if (rs < 0) rs = p
                    last = p; miss = 0
                } else if (rs >= 0 && ++miss > gap) {
                    if (last - rs > bestLen) { bestLen = last - rs; bestStart = lo + rs * step }
                    rs = -1
                }
            }
            if (rs >= 0 && last - rs > bestLen) { bestLen = last - rs; bestStart = lo + rs * step }
        }
        val len = (bestLen + k) * step - fps // Ende etwas vorsichtiger (1 s)
        return if (len in MIN_FRAMES..MAX_FRAMES) bestStart to (bestStart + len) else null
    }

    private fun readInts(f: File): IntArray? = runCatching {
        DataInputStream(f.inputStream().buffered()).use { s -> IntArray(s.readInt()) { s.readInt() } }
    }.getOrNull()

    private fun writeInts(f: File, a: IntArray) {
        DataOutputStream(f.outputStream().buffered()).use { s -> s.writeInt(a.size); a.forEach { s.writeInt(it) } }
    }

    private fun md5(s: String) = MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        /** Aufzeichnung der ersten 10 Minuten jeder Folge. */
        const val WINDOW_MS = 600_000L
        /** Vorspann: mindestens 15 s, hoechstens 3 Minuten. */
        private const val MIN_FRAMES = 15 * IntroFingerprinter.FRAMES_PER_SECOND
        private const val MAX_FRAMES = 180 * IntroFingerprinter.FRAMES_PER_SECOND
        /** Vorspann darf in zwei Folgen bis zu 4 Minuten verschoben liegen. */
        private const val MAX_SHIFT_MS = 240_000L
        private const val LEARN_BER = 0.38
        /** Mindestens 2 Minuten Ton aufgezeichnet, bevor gelernt wird. */
        private const val MIN_RECORDED = 120 * IntroFingerprinter.FRAMES_PER_SECOND
        /** Live-Erkennung: 3 s Fenster, Bitfehlerrate hoechstens 36 % (fremder Ton ~50 %). */
        private const val LIVE_WINDOW = 3 * IntroFingerprinter.FRAMES_PER_SECOND
        private const val LIVE_BER = 0.36
    }
}
