package com.poweriptv.app.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import java.io.File
import java.io.InterruptedIOException
import java.io.RandomAccessFile

/**
 * Liest eine Datei, die gleichzeitig noch geschrieben wird (Timeshift-Puffer).
 * Am aktuellen Dateiende wird auf neue Daten gewartet, solange der Mitschnitt laeuft.
 */
@OptIn(UnstableApi::class)
class GrowingFileDataSource(
    private val file: File,
    private val isGrowing: () -> Boolean,
) : BaseDataSource(false) {
    private var raf: RandomAccessFile? = null
    private var uri: Uri? = null
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val r = RandomAccessFile(file, "r")
        r.seek(dataSpec.position)
        raf = r
        opened = true
        transferStarted(dataSpec)
        return C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val r = raf ?: return C.RESULT_END_OF_INPUT
        var waited = 0
        while (true) {
            val n = r.read(buffer, offset, length)
            if (n > 0) {
                bytesTransferred(n)
                return n
            }
            if (!isGrowing() || waited > 15_000) return C.RESULT_END_OF_INPUT
            try {
                Thread.sleep(40)
            } catch (e: InterruptedException) {
                throw InterruptedIOException()
            }
            waited += 40
        }
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        raf?.close()
        raf = null
        uri = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }
}
