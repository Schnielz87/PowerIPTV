package com.poweriptv.app.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.opengl.GLES20
import androidx.annotation.OptIn
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.poweriptv.app.AppContainer
import com.poweriptv.app.PlayEntry

/**
 * Wiedergabe-Feinabstimmung (Standard-Player und VLC):
 *  - Mobile-Daten-Modus: erkennt selbst das Mobilfunknetz; im WLAN bleibt die volle Qualitaet.
 *  - Stabil-Modus: groesserer Puffer gegen Stocken (automatisch bei Mobilfunk oder erkanntem Stocken).
 *  - Bildschaerfe: dezenter Schaerfe-Filter (v.a. fuer SD-Sender).
 */
object PlaybackTuning {

    /** Laeuft das Geraet gerade ueber Mobilfunk (nicht WLAN/LAN)? */
    fun isCellular(context: Context): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
            !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }.getOrDefault(false)

    fun dataSaverActive(container: AppContainer, context: Context): Boolean = when (container.settings.dataSaver.value) {
        "ON" -> true
        "OFF" -> false
        else -> isCellular(context)
    }

    fun stableActive(container: AppContainer, context: Context): Boolean = when (container.settings.stableMode.value) {
        "ON" -> true
        "OFF" -> false
        else -> isCellular(context) || container.settings.stutterRecently()
    }

    /** Schaerfe-Staerke fuer den Filter (0 = aus). */
    fun sharpenAmount(container: AppContainer): Float = when (container.settings.sharpen.value) {
        "LIGHT" -> 0.35f
        "STRONG" -> 0.7f
        else -> 0f
    }

    private val qualityTag = Regex("""(?i)(\b|_)(f?hd|uhd|4k|8k|hevc|h\.?265|1080[pi]?|720p|2160p|50fps|ᴴᴰ|ᶠᴴᴰ|ᵁᴴᴰ)(\b|_)|[\s*+]*\b(raw|backup)\b""")
    private fun base(name: String) = qualityTag.replace(name, " ").replace(Regex("""[^\p{L}\p{Nd}]+"""), " ").trim().lowercase()
    private fun isHigh(name: String) = Regex("""(?i)\b(f?hd|uhd|4k|8k|hevc|h\.?265|1080|2160)\b|ᴴᴰ|ᶠᴴᴰ|ᵁᴴᴰ""").containsMatchIn(name)

    /**
     * Mobile Daten: SD-Version desselben Senders aus der Senderliste (z.B. "RTL" statt "RTL FHD").
     * Liefert null, wenn es keine gibt – dann laeuft der Sender wie gewohnt.
     */
    fun sdVariant(entry: PlayEntry, queue: List<PlayEntry>): PlayEntry? {
        if (!entry.live || !isHigh(entry.title)) return null
        val key = base(entry.title).ifEmpty { return null }
        return queue.firstOrNull { it !== entry && it.live && it.url != entry.url && !isHigh(it.title) && base(it.title) == key }
    }

    /** VLC-Optionen fuer Schaerfe (leer = aus). */
    fun vlcSharpenOptions(container: AppContainer): List<String> = when (container.settings.sharpen.value) {
        "LIGHT" -> listOf(":video-filter=sharpen", ":sharpen-sigma=0.08")
        "STRONG" -> listOf(":video-filter=sharpen", ":sharpen-sigma=0.18")
        else -> emptyList()
    }
}

/** Schaerfe-Filter fuer den Standard-Player (GPU, Unscharf-Maskierung mit den 4 Nachbarpunkten). */
@OptIn(UnstableApi::class)
class SharpenEffect(private val amount: Float) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = SharpenShaderProgram(useHdr, amount)
    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = amount <= 0f
}

@OptIn(UnstableApi::class)
private class SharpenShaderProgram(useHdr: Boolean, amount: Float) : BaseGlShaderProgram(useHdr, 1) {
    private val program: GlProgram = try {
        GlProgram(VERTEX, FRAGMENT)
    } catch (e: GlUtil.GlException) {
        throw VideoFrameProcessingException(e)
    }

    init {
        program.setFloatsUniform("uAmount", floatArrayOf(amount))
        program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        program.setFloatsUniform("uTexel", floatArrayOf(1f / inputWidth.coerceAtLeast(1), 1f / inputHeight.coerceAtLeast(1)))
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    override fun release() {
        super.release()
        try { program.delete() } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    }

    private companion object {
        const val VERTEX = """
            attribute vec4 aFramePosition;
            varying vec2 vTexSamplingCoord;
            void main() {
              gl_Position = aFramePosition;
              vTexSamplingCoord = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5);
            }
        """
        const val FRAGMENT = """
            precision mediump float;
            uniform sampler2D uTexSampler;
            uniform vec2 uTexel;
            uniform float uAmount;
            varying vec2 vTexSamplingCoord;
            void main() {
              vec4 c = texture2D(uTexSampler, vTexSamplingCoord);
              vec3 n = texture2D(uTexSampler, vTexSamplingCoord + vec2(uTexel.x, 0.0)).rgb
                     + texture2D(uTexSampler, vTexSamplingCoord - vec2(uTexel.x, 0.0)).rgb
                     + texture2D(uTexSampler, vTexSamplingCoord + vec2(0.0, uTexel.y)).rgb
                     + texture2D(uTexSampler, vTexSamplingCoord - vec2(0.0, uTexel.y)).rgb;
              vec3 sharp = c.rgb + uAmount * (4.0 * c.rgb - n);
              gl_FragColor = vec4(clamp(sharp, 0.0, 1.0), c.a);
            }
        """
    }
}
