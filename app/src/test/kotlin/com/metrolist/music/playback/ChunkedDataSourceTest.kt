package com.metrolist.music.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ChunkedDataSourceTest {
    /** Serves [data], each open limited to the requested range, and remembers the ranges asked for. */
    private class RangeSource(
        private val data: ByteArray,
    ) : DataSource {
        val opened = mutableListOf<Pair<Long, Long>>()
        private var position = 0
        private var end = 0

        override fun addTransferListener(transferListener: TransferListener) = Unit

        override fun open(dataSpec: DataSpec): Long {
            opened += dataSpec.position to dataSpec.length
            position = dataSpec.position.toInt()
            end = if (dataSpec.length == C.LENGTH_UNSET.toLong()) data.size else (dataSpec.position + dataSpec.length).toInt()
            return (end - position).toLong()
        }

        override fun read(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int {
            if (position >= end) return C.RESULT_END_OF_INPUT
            val count = minOf(length, end - position)
            System.arraycopy(data, position, buffer, offset, count)
            position += count
            return count
        }

        override fun getUri(): Uri? = null

        override fun close() = Unit
    }

    private val data = ByteArray(25) { it.toByte() }

    private fun readAll(
        source: DataSource,
        spec: DataSpec,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        source.open(spec)
        val buffer = ByteArray(4)
        while (true) {
            val read = source.read(buffer, 0, buffer.size)
            if (read == C.RESULT_END_OF_INPUT) break
            out.write(buffer, 0, read)
        }
        source.close()
        return out.toByteArray()
    }

    private val uri = Uri.parse("https://example.com/audio")

    @Test
    fun `a long span is read in bounded ranges and comes out whole`() {
        val upstream = RangeSource(data)
        val source = ChunkedDataSource(upstream, chunkSize = 10)
        val bytes = readAll(source, DataSpec.Builder().setUri(uri).setPosition(0).setLength(25).build())
        assertArrayEquals(data, bytes)
        assertEquals(listOf(0L to 10L, 10L to 10L, 20L to 5L), upstream.opened)
    }

    @Test
    fun `a span that starts midway keeps its offset`() {
        val upstream = RangeSource(data)
        val source = ChunkedDataSource(upstream, chunkSize = 10)
        val bytes = readAll(source, DataSpec.Builder().setUri(uri).setPosition(7).setLength(18).build())
        assertArrayEquals(data.copyOfRange(7, 25), bytes)
        assertEquals(listOf(7L to 10L, 17L to 8L), upstream.opened)
    }

    @Test
    fun `short and open-ended spans pass straight through`() {
        val upstream = RangeSource(data)
        val source = ChunkedDataSource(upstream, chunkSize = 10)
        assertArrayEquals(data.copyOfRange(0, 8), readAll(source, DataSpec.Builder().setUri(uri).setLength(8).build()))
        assertArrayEquals(data, readAll(source, DataSpec.Builder().setUri(uri).build()))
        assertEquals(listOf(0L to 8L, 0L to C.LENGTH_UNSET.toLong()), upstream.opened)
    }

    @Test
    fun `a download asks for the whole song, never a single playback chunk`() {
        val stream = CachedStreamUrl(url = "https://example.com/s", requestHeaders = emptyMap(), clientName = "c", useRangeChunks = true, rangeChunkSizeBytes = 5)
        val open = DataSpec.Builder().setUri(uri).build()
        assertEquals(25L, open.forDownload(stream, contentLength = 25).length)
        assertEquals(C.LENGTH_UNSET.toLong(), open.forDownload(stream, contentLength = null).length)
        val resumed = DataSpec.Builder().setUri(uri).setPosition(10).build()
        assertEquals(15L, resumed.forDownload(stream, contentLength = 25).length)
    }
}
