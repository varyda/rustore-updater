package ru.app.rustoreupdater.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Offline checks for the RuStore archive handling. Fixtures are built on the fly:
 * a minimal "APK" is just a ZIP with `AndroidManifest.xml`, which is exactly the
 * heuristic [ApkArchive.isApk] uses.
 */
class ApkArchiveTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun fakeApk(name: String = "app.apk", payloadSize: Int = 64 * 1024): File {
        val file = File(temp.root, name)
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write(ByteArray(512) { it.toByte() })
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("classes.dex"))
            zip.write(ByteArray(payloadSize) { (it % 251).toByte() })
            zip.closeEntry()
        }
        return file
    }

    private fun bundle(name: String, apkEntry: String?, apkBytes: ByteArray?): File {
        val file = File(temp.root, name)
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("assets/dexopt/baseline.prof"))
            zip.write(ByteArray(1024))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("assets/dexopt/baseline.profm"))
            zip.write(ByteArray(128))
            zip.closeEntry()
            if (apkEntry != null && apkBytes != null) {
                zip.putNextEntry(ZipEntry(apkEntry))
                zip.write(apkBytes)
                zip.closeEntry()
            }
        }
        return file
    }

    @Test
    fun isApkRecognisesApkAndRejectsBundle() {
        assertTrue(ApkArchive.isApk(fakeApk()))
        val plain = File(temp.root, "notes.txt").apply { writeText("PK- not really") }
        assertFalse(ApkArchive.isApk(plain))
        assertFalse(ApkArchive.isApk(File(temp.root, "missing.apk")))
    }

    @Test
    fun resolveApkUnpacksUniversalApkFromRuStoreBundle() {
        val apk = fakeApk()
        val archive = bundle("ru.sberbankmobile.zip", "UNIVERSAL_APK_247102c2.apk", apk.readBytes())

        assertFalse("bundle must not be mistaken for an APK", ApkArchive.isApk(archive))

        val resolved = ApkArchive.resolveApk(archive)
        assertTrue("resolveApk returned null", resolved != null)
        assertEquals("ru.sberbankmobile-universal.apk", resolved!!.name)
        assertTrue(ApkArchive.isApk(resolved))
        assertTrue(resolved.readBytes().contentEquals(apk.readBytes()))
    }

    @Test
    fun resolveApkPassesBareApkThrough() {
        val apk = fakeApk()
        assertSame(apk, ApkArchive.resolveApk(apk))
    }

    @Test
    fun resolveApkRejectsBundleWithoutApk() {
        val archive = bundle("empty.zip", apkEntry = null, apkBytes = null)
        assertNull(ApkArchive.resolveApk(archive))
        val thrown = runCatching { ApkArchive.extractUniversalApk(archive) }.exceptionOrNull()
        assertTrue("expected IOException, got $thrown", thrown is IOException)
    }

    @Test
    fun resolveApkRejectsGarbageDownload() {
        val garbage = File(temp.root, "error.html").apply {
            writeText("<html><body>404 Not Found</body></html>")
        }
        assertNull(ApkArchive.resolveApk(garbage))
        assertNull(ApkArchive.resolveApk(File(temp.root, "does-not-exist.zip")))
    }

    @Test
    fun downloaderNamesArchiveDownloadsByUrl() {
        assertTrue(ApkDownloader.isArchiveUrl("https://static.rustore.ru/a/b/c.zip"))
        assertTrue(ApkDownloader.isArchiveUrl("https://static.rustore.ru/c.zip?token=abc"))
        assertFalse(ApkDownloader.isArchiveUrl("https://github.com/o/r/releases/download/latest/updater.apk"))
    }
}
