package com.alanmaster.xread.ui

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Some decrypted episode files contain zero padding inside MP4 sample-entry
 * boxes. MediaPlayer tolerates it, but Media3's strict MP4 parser rejects
 * `childAtomSize must be positive`. Trim the trailing zero children into a
 * cache copy before handing the file to ExoPlayer.
 */
internal object Mp4Sanitizer {
    private const val TAG = "DramaPlayer"

    fun sanitize(context: Context, path: String): String {
        val source = File(path)
        if (!source.isFile) return path
        val cacheDir = File(context.cacheDir, "drama-play").apply { mkdirs() }
        val cache = File(cacheDir, "${source.name}-${source.length()}-${source.lastModified()}.mp4")
        if (cache.isFile && cache.length() > 0) return cache.absolutePath

        return runCatching {
            val bytes = source.readBytes()
            val patched = patch(bytes)
            if (patched == 0) {
                source.absolutePath
            } else {
                FileOutputStream(cache).use { it.write(bytes) }
                Log.i(TAG, "sanitized mp4 boxes=$patched source=${source.name} size=${bytes.size}")
                cache.absolutePath
            }
        }.getOrElse {
            Log.w(TAG, "mp4 sanitize failed path=$path", it)
            source.absolutePath
        }
    }

    private fun patch(bytes: ByteArray): Int {
        var patches = 0
        fun u32(pos: Int): Long =
            ((bytes[pos].toLong() and 0xff) shl 24) or
                ((bytes[pos + 1].toLong() and 0xff) shl 16) or
                ((bytes[pos + 2].toLong() and 0xff) shl 8) or
                (bytes[pos + 3].toLong() and 0xff)

        fun writeU32(pos: Int, value: Int) {
            bytes[pos] = (value ushr 24).toByte()
            bytes[pos + 1] = (value ushr 16).toByte()
            bytes[pos + 2] = (value ushr 8).toByte()
            bytes[pos + 3] = value.toByte()
        }

        fun typeAt(pos: Int): String = String(bytes, pos, 4, Charsets.ISO_8859_1)

        fun walk(start: Int, end: Int) {
            var pos = start
            while (pos + 8 <= end) {
                val size32 = u32(pos)
                if (size32 < 8 || size32 > Int.MAX_VALUE) return
                val size = size32.toInt()
                if (pos + size > end) return
                when (typeAt(pos + 4)) {
                    "moov", "trak", "mdia", "minf", "stbl" -> walk(pos + 8, pos + size)
                    "stsd" -> {
                        if (pos + 16 > pos + size) return
                        val count = u32(pos + 12).toInt()
                        var entry = pos + 16
                        repeat(count) {
                            if (entry + 8 > pos + size) return@repeat
                            val entrySize = u32(entry).toInt()
                            if (entrySize < 8 || entry + entrySize > pos + size) return@repeat
                            val type = typeAt(entry + 4)
                            val header = when (type) {
                                "avc1", "hvc1", "hev1", "encv" -> 86
                                "mp4a", "enca" -> 36
                                else -> 0
                            }
                            if (header > 0 && entry + header <= entry + entrySize) {
                                var child = entry + header
                                var lastValid = child
                                while (child + 8 <= entry + entrySize) {
                                    val childSize = u32(child).toInt()
                                    if (childSize < 8 || child + childSize > entry + entrySize) break
                                    lastValid = child + childSize
                                    child += childSize
                                }
                                if (lastValid < entry + entrySize) {
                                    writeU32(entry, lastValid - entry)
                                    patches++
                                }
                            }
                            entry += entrySize
                        }
                    }
                }
                pos += size
            }
        }

        walk(0, bytes.size)
        return patches
    }
}
