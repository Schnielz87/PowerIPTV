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

    private val frameMs = IntroFingerprinter.FRAME_MS
    private val frames = (WINDOW_MS / frameMs).toInt()

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
            val idx = (pos / frameMs).toInt()
            if (idx in 0 until frames) {
                if (fp[idx] < 0) recorded++
                fp[idx] = hash
            }
        }
        detectLive(playerPosMs)
    }

    /** Live-Vergleich der letzten 3 Sekunden mit der Vorlage. */
    private fun detectLive(posMs: Long) {
        val tpl = template ?: return
        if (detected != null) return
        val end = (posMs / frameMs).toInt() - 2
        val w = LIVE_WINDOW
        if (end < w || end >= frames) return
        val start = end - w + 1
        var best = Int.MAX_VALUE; var bestJ = -1
        for (j in 0..tpl.size - w) {
            var bits = 0; var valid = 0
            for (k in 0 until w) {
                val a = fp[start + k]; val b = tpl[j + k]
                if (a < 0 || b < 0) continue
                valid++
                bits += Integer.bitCount(a xor b)
                if (bits > best) break
            }
            if (valid >= w * 3 / 4 && bits < best) {
                // auf volle Fensterlaenge hochrechnen
                val scaled = bits * w / valid
                if (scaled < best) { best = scaled; bestJ = j }
            }
        }
        if (bestJ < 0) return
        val ber = best.toDouble() / (w * 16)
        if (ber > LIVE_BER) { lastMatchStart = Long.MIN_VALUE; return }
        val introStart = (start - bestJ).toLong() * frameMs
        // Zweimal hintereinander gleiches Ergebnis -> sicher
        if (kotlin.math.abs(introStart - lastMatchStart) <= 1500) {
            detected = Intro(introStart.coerceAtLeast(0), introStart + tpl.size * frameMs)
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

    /** Laengster gemeinsamer Abschnitt (Start, Ende als Index in [a]) zwischen zwei Folgen. */
    private fun longestCommon(a: IntArray, b: IntArray): Pair<Int, Int>? {
        val maxShift = (MAX_SHIFT_MS / frameMs).toInt()
        var bestLen = 0; var bestStart = 0
        for (d in -maxShift..maxShift) {
            var runStart = -1; var misses = 0; var lastHit = -1
            val from = maxOf(0, -d); val to = minOf(a.size, b.size - d)
            for (i in from until to) {
                val x = a[i]; val y = b[i + d]
                val hit = x >= 0 && y >= 0 && Integer.bitCount(x xor y) <= 3
                if (hit) {
                    if (runStart < 0) runStart = i
                    lastHit = i; misses = 0
                } else if (runStart >= 0) {
                    if (++misses > MAX_GAP) {
                        val len = lastHit - runStart + 1
                        if (len > bestLen) { bestLen = len; bestStart = runStart }
                        runStart = -1; misses = 0
                    }
                }
            }
            if (runStart >= 0) {
                val len = lastHit - runStart + 1
                if (len > bestLen) { bestLen = len; bestStart = runStart }
            }
        }
        return if (bestLen >= MIN_FRAMES && bestLen <= MAX_FRAMES) bestStart to (bestStart + bestLen) else null
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
        /** Vorspann darf in zwei Folgen bis zu 5 Minuten verschoben liegen. */
        private const val MAX_SHIFT_MS = 300_000L
        private const val MAX_GAP = 6
        /** Mindestens 2 Minuten Ton aufgezeichnet, bevor gelernt wird. */
        private const val MIN_RECORDED = 120 * IntroFingerprinter.FRAMES_PER_SECOND
        /** Live-Erkennung: 3 s Fenster, Bitfehlerrate hoechstens 25 %. */
        private const val LIVE_WINDOW = 3 * IntroFingerprinter.FRAMES_PER_SECOND
        private const val LIVE_BER = 0.25
    }
}
