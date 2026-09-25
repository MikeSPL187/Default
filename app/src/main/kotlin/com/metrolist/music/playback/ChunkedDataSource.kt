package com.metrolist.music.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * Reads a span of known length as consecutive bounded ranges of at most [chunkSize] bytes.
 * YouTube serves bounded ranges at full speed but slows open-ended requests to about the pace of
 * playback, which makes downloads crawl. A span of unknown length is passed through untouched.
 */
internal class ChunkedDataSource(
    private val upstream: DataSource,
    private val chunkSize: Long = DEFAULT_CHUNK_SIZE,
) : DataSource {
    private var spec: DataSpec? = null
    private var passThrough = false
    private var upstreamOpen = false

    /** Where the next chunk starts, and how much of the span and of the open chunk is left. */
    private var nextPosition = 0L
    private var remaining = 0L
    private var chunkLeft = 0L

    override fun addTransferListener(transferListener: TransferListener) = upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        spec = dataSpec
        passThrough = dataSpec.length == C.LENGTH_UNSET.toLong() || dataSpec.length <= chunkSize
        if (passThrough) {
            upstreamOpen = true
            return upstream.open(dataSpec)
        }
        nextPosition = dataSpec.position
        remaining = dataSpec.length
        openChunk()
        return dataSpec.length
    }

    private fun openChunk() {
        val whole = checkNotNull(spec)
        val length = minOf(chunkSize, remaining)
        upstreamOpen = true
        upstream.open(whole.subrange(nextPosition - whole.position, length))
        chunkLeft = length
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        if (passThrough) return upstream.read(buffer, offset, length)
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        if (chunkLeft == 0L) {
            upstream.close()
            upstreamOpen = false
            openChunk()
        }
        val read = upstream.read(buffer, offset, minOf(length.toLong(), chunkLeft).toInt())
        if (read == C.RESULT_END_OF_INPUT) return read
        chunkLeft -= read
        remaining -= read
        nextPosition += read
        return read
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        spec = null
        if (upstreamOpen) {
            upstreamOpen = false
            upstream.close()
        }
    }

    class Factory(
        private val upstream: DataSource.Factory,
        private val chunkSize: Long = DEFAULT_CHUNK_SIZE,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = ChunkedDataSource(upstream.createDataSource(), chunkSize)
    }

    companion object {
        /** The largest range YouTube still serves unthrottled, as download tools settle on. */
        const val DEFAULT_CHUNK_SIZE = 10L * 1024 * 1024
    }
}
