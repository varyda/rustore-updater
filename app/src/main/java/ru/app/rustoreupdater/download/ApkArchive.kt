package ru.app.rustoreupdater.download

import android.util.Log
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/**
 * Turns RuStore download artifacts into a file the system installer accepts.
 *
 * `applicationData/v2/download-link` does **not** return an APK. It returns a ZIP
 * archive (`.zip`) with a single `UNIVERSAL_APK_<uuid>.apk` member plus baseline
 * profiles, e.g.:
 *
 * ```
 * UNIVERSAL_APK_247102c2-….apk   176 MB   <- the real APK
 * assets/dexopt/baseline.prof     66 KB
 * assets/dexopt/baseline.profm     9 KB
 * ```
 *
 * Handing that archive to `ACTION_VIEW` fails with "Не удалось проанализировать
 * пакет", so the archive has to be unpacked first. GitHub self-updates still deliver
 * a plain APK; [resolveApk] simply passes those through untouched.
 */
object ApkArchive {

    private const val TAG = "ApkArchive"
    private const val MANIFEST_ENTRY = "AndroidManifest.xml"
    private const val APK_SUFFIX = ".apk"
    private const val BUFFER_SIZE = 1 shl 16

    /** True when [file] is a real APK (a ZIP that contains an AndroidManifest.xml). */
    fun isApk(file: File): Boolean {
        if (!file.isFile || file.length() == 0L) return false
        return try {
            ZipFile(file).use { it.getEntry(MANIFEST_ENTRY) != null }
        } catch (e: IOException) {
            // Not a ZIP at all (e.g. an HTML error page saved by DownloadManager).
            Log.w(TAG, "${file.name} is not a zip archive: ${e.message}")
            false
        }
    }

    /**
     * Returns an installable APK for a finished download, or `null` when [file] is
     * neither an APK nor a RuStore archive that contains one.
     *
     * When [file] is an archive, the APK is extracted next to it as
     * `<name>-universal.apk`; the caller owns both files and may delete the archive.
     */
    fun resolveApk(file: File): File? {
        if (!file.isFile) {
            Log.e(TAG, "resolveApk: ${file.absolutePath} does not exist")
            return null
        }
        if (isApk(file)) return file
        return try {
            extractUniversalApk(file)
        } catch (e: Exception) {
            Log.e(TAG, "cannot unpack ${file.absolutePath}", e)
            null
        }
    }

    /**
     * Extracts the largest top-level `*.apk` member of [archive].
     *
     * @throws IOException when the archive has no APK member or the copy fails.
     */
    fun extractUniversalApk(archive: File): File {
        val target = File(archive.parentFile, archive.nameWithoutExtension + "-universal.apk")
        target.delete()

        ZipFile(archive).use { zip ->
            val entry = zip.entries().asSequence()
                // Top-level only: a split bundle would also carry `splits/*.apk`
                // members, and installing one of those would fail.
                .filter { !it.name.contains('/') && it.name.endsWith(APK_SUFFIX, ignoreCase = true) }
                .sortedByDescending { it.size }
                .firstOrNull()
                ?: throw IOException("no .apk member in ${archive.name}")

            Log.d(TAG, "extracting ${entry.name} (${entry.size} bytes) from ${archive.name}")
            zip.getInputStream(entry).use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var copied = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        copied += read
                    }
                    output.flush()
                    Log.d(TAG, "extracted $copied bytes -> ${target.absolutePath}")
                }
            }
        }

        if (!isApk(target)) {
            target.delete()
            throw IOException("extracted file is not an APK: ${archive.name}")
        }
        return target
    }
}
