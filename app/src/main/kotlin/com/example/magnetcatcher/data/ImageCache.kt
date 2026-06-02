package com.example.magnetcatcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest

class ImageCache(context: Context, memoryCacheSizeKb: Int = defaultMemoryCacheKb()) {
    private val memoryCache = object : LruCache<String, Bitmap>(memoryCacheSizeKb.coerceAtLeast(1)) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }
    private val diskDir = File(context.cacheDir, "images")

    init {
        ensureDiskDir()
    }

    fun getFromMemory(key: String?, targetWidth: Int, targetHeight: Int): Bitmap? {
        if (key == null) return null
        return memoryCache.get(memoryKey(key, targetWidth, targetHeight))
    }

    fun putToMemory(key: String?, targetWidth: Int, targetHeight: Int, bitmap: Bitmap?) {
        if (key == null || bitmap == null || bitmap.isRecycled) return
        memoryCache.put(memoryKey(key, targetWidth, targetHeight), bitmap)
    }

    fun getDiskFile(key: String): File = File(diskDir, "${cacheKey(key)}.img")

    fun getFromDisk(key: String, targetWidth: Int, targetHeight: Int): Bitmap? {
        val file = getDiskFile(key)
        if (!file.isFile) return null
        val bitmap = decodeSampledBitmap(file, targetWidth, targetHeight)
        if (bitmap != null) putToMemory(key, targetWidth, targetHeight, bitmap)
        return bitmap
    }

    fun get(key: String, targetWidth: Int, targetHeight: Int): Bitmap? {
        return getFromMemory(key, targetWidth, targetHeight) ?: getFromDisk(key, targetWidth, targetHeight)
    }

    fun putBytesAndDecode(key: String, data: ByteArray?, targetWidth: Int, targetHeight: Int): Bitmap? {
        if (data == null) return null
        putToDisk(key, data)
        val bitmap = decodeSampledBitmap(data, targetWidth, targetHeight)
        if (bitmap != null) putToMemory(key, targetWidth, targetHeight, bitmap)
        return bitmap
    }

    fun putStreamAndDecode(key: String, input: InputStream?, targetWidth: Int, targetHeight: Int): Bitmap? {
        if (input == null || !putToDisk(key, input)) return null
        return getFromDisk(key, targetWidth, targetHeight)
    }

    fun clearMemory() {
        memoryCache.evictAll()
    }

    fun clearDisk() {
        diskDir.listFiles()?.filter { it.isFile }?.forEach { it.delete() }
    }

    private fun putToDisk(key: String, data: ByteArray): Boolean {
        val target = getDiskFile(key)
        val temp = tempFileFor(target)
        var output: FileOutputStream? = null
        return try {
            ensureDiskDir()
            output = FileOutputStream(temp)
            output.write(data)
            closeQuietly(output)
            output = null
            replaceFile(temp, target)
        } catch (_: Exception) {
            false
        } finally {
            closeQuietly(output)
            deleteQuietly(temp)
        }
    }

    private fun putToDisk(key: String, input: InputStream): Boolean {
        val target = getDiskFile(key)
        val temp = tempFileFor(target)
        var output: FileOutputStream? = null
        return try {
            ensureDiskDir()
            output = FileOutputStream(temp)
            val buffer = ByteArray(IO_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                output.write(buffer, 0, read)
            }
            closeQuietly(output)
            output = null
            replaceFile(temp, target)
        } catch (_: Exception) {
            false
        } finally {
            closeQuietly(output)
            deleteQuietly(temp)
        }
    }

    private fun ensureDiskDir() {
        if (!diskDir.exists()) diskDir.mkdirs()
    }

    private fun tempFileFor(target: File): File = File("${target.absolutePath}.tmp")

    private fun replaceFile(temp: File, target: File): Boolean {
        if (!temp.isFile) return false
        val backup = File("${target.absolutePath}.bak")
        deleteQuietly(backup)
        val hadTarget = target.exists()
        if (hadTarget && !target.renameTo(backup)) return false
        if (temp.renameTo(target)) {
            deleteQuietly(backup)
            return true
        }
        if (hadTarget) backup.renameTo(target)
        return false
    }

    companion object {
        private const val IO_BUFFER_SIZE = 16 * 1024

        fun defaultMemoryCacheKb(): Int {
            return maxOf(4 * 1024, (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt())
        }

        fun decodeSampledBitmap(data: ByteArray?, targetWidth: Int, targetHeight: Int): Bitmap? {
            if (data == null) return null
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, boundsOptions)
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(boundsOptions, targetWidth, targetHeight)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            return BitmapFactory.decodeByteArray(data, 0, data.size, decodeOptions)
        }

        fun decodeSampledBitmap(file: File?, targetWidth: Int, targetHeight: Int): Bitmap? {
            if (file == null || !file.isFile) return null
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, boundsOptions)
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(boundsOptions, targetWidth, targetHeight)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            return BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
        }

        fun calculateInSampleSize(options: BitmapFactory.Options?, targetWidth: Int, targetHeight: Int): Int {
            if (options == null || targetWidth <= 0 || targetHeight <= 0) return 1
            val height = options.outHeight
            val width = options.outWidth
            var inSampleSize = 1
            if (height > targetHeight || width > targetWidth) {
                val halfHeight = height / 2
                val halfWidth = width / 2
                while ((halfHeight / inSampleSize) >= targetHeight && (halfWidth / inSampleSize) >= targetWidth) {
                    inSampleSize *= 2
                }
            }
            return inSampleSize.coerceAtLeast(1)
        }

        fun cacheKey(rawKey: String?): String {
            val key = rawKey ?: ""
            return try {
                val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
                digest.joinToString("") { "%02x".format(it) }
            } catch (_: Exception) {
                key.hashCode().toString()
            }
        }

        fun buildKey(url: String?, referer: String?): String = "${url ?: ""}|${referer ?: ""}"

        private fun memoryKey(key: String, targetWidth: Int, targetHeight: Int): String {
            return "${cacheKey(key)}:${targetWidth.coerceAtLeast(0)}x${targetHeight.coerceAtLeast(0)}"
        }

        private fun closeQuietly(closeable: Closeable?) {
            try {
                closeable?.close()
            } catch (_: Exception) {
            }
        }

        private fun deleteQuietly(file: File?) {
            if (file != null && file.exists()) file.delete()
        }
    }
}
