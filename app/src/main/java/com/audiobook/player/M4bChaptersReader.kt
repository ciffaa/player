package com.audiobook.player

import android.content.Context
import android.net.Uri
import java.io.InputStream
import java.nio.ByteBuffer

data class M4bChapter(
    val index: Int,
    val title: String,
    val startMs: Long
)

class M4bChaptersReader(private val context: Context) {

    fun readChapters(uri: Uri): List<M4bChapter> {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val chapters = scanStreamForChpl(stream)
                if (chapters.size > 1) chapters else emptyList()
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun scanStreamForChpl(input: InputStream): List<M4bChapter> {
        val buffer = ByteArray(64 * 1024)
        var carry = ByteArray(0)
        var totalRead = 0
        val maxScanBytes = 32 * 1024 * 1024

        while (totalRead < maxScanBytes) {
            val read = input.read(buffer)
            if (read <= 0) break
            totalRead += read

            val combined = ByteArray(carry.size + read)
            System.arraycopy(carry, 0, combined, 0, carry.size)
            System.arraycopy(buffer, 0, combined, carry.size, read)

            val chplPos = findAsciiTag(combined, "chpl")
            if (chplPos >= 4) {
                val atomStart = chplPos - 4
                val atomSize = readUint32BE(combined, atomStart).toInt()
                if (atomSize in 16..(2 * 1024 * 1024) && atomStart + atomSize <= combined.size) {
                    val parsed = parseChplPayload(combined, atomStart + 8, atomSize - 8)
                    if (parsed.size > 1) return parsed
                }
            }

            val keep = minOf(combined.size, 1024 * 1024)
            carry = combined.copyOfRange(combined.size - keep, combined.size)
        }
        return emptyList()
    }

    private fun parseChplPayload(data: ByteArray, offset: Int, length: Int): List<M4bChapter> {
        if (length < 10 || offset + length > data.size) return emptyList()
        return try {
            var pos = offset
            val version = data[pos].toInt() and 0xFF
            pos += 4 // version (1 byte) + flags (3 bytes)
            if (version == 1) {
                pos += 4
            }
            if (pos >= offset + length) return emptyList()
            val count = data[pos].toInt() and 0xFF
            pos += 1

            val chapters = mutableListOf<M4bChapter>()
            val endLimit = offset + length
            for (i in 0 until count) {
                if (pos + 9 > endLimit) break
                val start100Ns = readInt64BE(data, pos)
                pos += 8
                val titleLen = data[pos].toInt() and 0xFF
                pos += 1
                if (pos + titleLen > endLimit) break
                val rawTitle = String(data, pos, titleLen, Charsets.UTF_8).trim()
                pos += titleLen

                val startMs = (start100Ns / 10_000L).coerceAtLeast(0L)
                val title = rawTitle.ifEmpty { "Глава ${i + 1}" }
                chapters.add(M4bChapter(i, title, startMs))
            }
            chapters.sortedBy { it.startMs }.mapIndexed { i, ch -> ch.copy(index = i) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun findAsciiTag(buf: ByteArray, tag: String): Int {
        val b0 = tag[0].code.toByte()
        val b1 = tag[1].code.toByte()
        val b2 = tag[2].code.toByte()
        val b3 = tag[3].code.toByte()
        for (i in 0..(buf.size - 4)) {
            if (buf[i] == b0 && buf[i + 1] == b1 && buf[i + 2] == b2 && buf[i + 3] == b3) {
                return i
            }
        }
        return -1
    }

    private fun readUint32BE(buf: ByteArray, off: Int): Long {
        return ((buf[off].toLong() and 0xFFL) shl 24) or
            ((buf[off + 1].toLong() and 0xFFL) shl 16) or
            ((buf[off + 2].toLong() and 0xFFL) shl 8) or
            (buf[off + 3].toLong() and 0xFFL)
    }

    private fun readInt64BE(buf: ByteArray, off: Int): Long {
        return ByteBuffer.wrap(buf, off, 8).long
    }
}
