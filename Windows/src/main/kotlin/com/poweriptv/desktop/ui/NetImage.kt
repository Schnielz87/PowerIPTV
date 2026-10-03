package com.poweriptv.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.poweriptv.desktop.data.AppDirs
import com.poweriptv.desktop.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jetbrains.skia.Image as SkiaImage
import java.io.File
import java.security.MessageDigest
import java.util.Collections

/** Kleiner Bild-Lader: Speicher-Cache (LRU) + Platten-Cache, max. 6 parallele Downloads. */
object ImageLoader {
    private val memory: MutableMap<String, ImageBitmap> = Collections.synchronizedMap(
        object : LinkedHashMap<String, ImageBitmap>(256, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 400
        },
    )
    private val failed = Collections.synchronizedSet(HashSet<String>())
    private val gate = Semaphore(6)
    private val dir: File by lazy { File(AppDirs.cache, "img").apply { mkdirs() } }

    fun cached(url: String): ImageBitmap? = memory[url]

    suspend fun load(url: String): ImageBitmap? {
        memory[url]?.let { return it }
        if (url in failed) return null
        return withContext(Dispatchers.IO) {
            val file = File(dir, md5(url))
            val bytes = if (file.exists() && file.length() > 0) runCatching { file.readBytes() }.getOrNull() else null
                ?: gate.withPermit { download(url)?.also { b -> runCatching { file.writeBytes(b) } } }
            val bmp = bytes?.let { b -> runCatching { SkiaImage.makeFromEncoded(b).toComposeImageBitmap() }.getOrNull() }
            if (bmp != null) memory[url] = bmp else failed += url
            bmp
        }
    }

    private fun download(url: String): ByteArray? = runCatching {
        if (url.startsWith("file:")) return@runCatching File(java.net.URI(url)).readBytes()
        Http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (r.isSuccessful) r.body?.bytes() else null
        }
    }.getOrNull()

    private fun md5(s: String) = MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    fun clearDisk() {
        memory.clear(); failed.clear()
        dir.listFiles()?.forEach { it.delete() }
    }
}

/** Bild aus dem Netz; probiert die URLs der Reihe nach, bis eins laedt. */
@Composable
fun NetImage(
    urls: List<String?>,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    placeholder: @Composable () -> Unit = {},
) {
    val list = urls.filterNotNull().filter { it.isNotBlank() && (it.startsWith("http") || it.startsWith("file:")) }.distinct()
    val bitmap by produceState(list.firstNotNullOfOrNull { ImageLoader.cached(it) }, list) {
        if (value == null) value = list.firstNotNullOfOrNull { ImageLoader.load(it) }
    }
    val b = bitmap
    if (b != null) Image(b, contentDescription = null, modifier = modifier, contentScale = contentScale, alignment = alignment)
    else Box(modifier, contentAlignment = Alignment.Center) { placeholder() }
}

@Composable
fun NetImage(url: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop, placeholder: @Composable () -> Unit = {}) =
    NetImage(listOf(url), modifier, contentScale, Alignment.Center, placeholder)
