package ru.app.rustoreupdater.data.network

import kotlinx.coroutines.runBlocking
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.app.rustoreupdater.data.repo.AppRepository

/**
 * End-to-end check of the RuStore backapi contract that the app depends on.
 *
 * These tests hit the **real** (unofficial) RuStore endpoints, because the whole
 * point is to catch server-side changes such as the HTTP 417 "update RuStore"
 * answer that a stale `ruStoreVerCode` header produces. They are intentionally not
 * part of the release build: run them manually with
 *
 * ```
 * ./gradlew :app:testDebugUnitTest --tests '*RuStoreApiTest*'
 * ```
 */
class RuStoreApiTest {

    private val device = RuStoreDevice(
        deviceId = "android-testdevice0000",
        manufacturer = "Google",
        model = "Pixel 8 Pro",
        hardware = "husky",
        firmwareVer = "16",
        sdkInt = 36,
        abis = listOf("arm64-v8a", "armeabi-v7a"),
        screenDensity = 420,
        locale = "ru",
        locales = listOf("ru_RU"),
    )

    private val client = RuStoreApiFactory.client(device, HttpLoggingInterceptor.Level.NONE)

    private val api = RuStoreApiFactory.create(device = device, client = client)

    /** Search works without any special headers and returns usable app entries. */
    @Test
    fun searchReturnsApps() = runBlocking {
        val res = api.search(query = "сбербанк", pageNumber = 0, pageSize = 5)
        assertEquals("OK", res.code)
        val content = res.body?.content
        assertNotNull("search body is null", content)
        assertTrue("search returned no apps", (content?.size ?: 0) > 0)
        val first = content!!.first()
        assertTrue(first.appId > 0)
        assertTrue(!first.packageName.isNullOrBlank())
    }

    /**
     * The exact three-step flow the app uses when the user adds an app:
     * search -> overallInfo (by package name!) -> download-link (by numeric appId).
     *
     * The payload must be reachable over a **publicly trusted** TLS chain, because
     * DownloadManager uses the system trust store: `static-m.rustore.ru` is signed by
     * the Ministry's "Russian Trusted Sub CA" and fails there, while
     * `static.rustore.ru` serves the same bytes under a HARICA certificate. That is
     * why the repository rewrites the host before downloading
     * ([AppRepository.portableDownloadUrl]).
     */
    @Test
    fun addAppFlowReturnsDownloadableArchive() = runBlocking {
        val search = api.search(query = "сбербанк", pageNumber = 0, pageSize = 5)
        val item = search.body!!.content.first()

        val info = api.getOverallInfo(item.packageName!!)
        assertEquals("overallInfo must be queried by package name", "OK", info.code)
        val body = info.body
        assertNotNull("overallInfo body is null", body)
        assertTrue("overallInfo.versionCode missing", (body!!.versionCode ?: 0) > 0)

        val link = api.getDownloadLink(
            DownloadLinkRequest(
                appId = item.appId.toString(),
                supportedAbis = device.abis,
                screenDensity = device.screenDensity,
                supportedLocales = device.locales,
                sdkVersion = device.sdkInt,
            )
        )
        assertEquals("download-link failed", "OK", link.code)
        val urls = link.body?.downloadUrls
        assertNotNull("downloadUrls is null", urls)
        assertTrue("no download urls", (urls?.size ?: 0) > 0)
        val rawUrl = urls!!.first().url
        assertNotNull("download url is null", rawUrl)

        val url = AppRepository.portableDownloadUrl(rawUrl!!)
        assertTrue("unexpected host: $url", url.startsWith("https://static.rustore.ru/"))
        assertTrue(
            "RuStore now serves an unexpected artifact: $url",
            url.endsWith(".zip") || url.endsWith(".apk"),
        )

        // Fetch only the first four bytes: proves the host is trusted by the platform
        // trust store and tells the APK/ZIP format apart (PK\x03\x04).
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-3")
            .get()
            .build()
        client.newCall(request).execute().use { resp ->
            assertTrue("download host answered HTTP ${resp.code}", resp.code in 200..299)
            val magic = resp.body?.bytes()
            assertNotNull("empty body from download host", magic)
            assertEquals("PK\u0003\u0004", String(magic!!, Charsets.ISO_8859_1))
        }
    }

    /** The mirror used by the official app is not trusted by stock Android. */
    @Test
    fun portableUrlRewritesUntrustedMirror() {
        assertEquals(
            "https://static.rustore.ru/2026/10/23/b5/abc.zip",
            AppRepository.portableDownloadUrl(
                "https://static-m.rustore.ru/2026/10/23/b5/abc.zip"
            ),
        )
        assertEquals(
            "https://static.rustore.ru/x.apk",
            AppRepository.portableDownloadUrl("https://static.rustore.ru/x.apk"),
        )
        assertEquals(
            "https://example.com/x.zip",
            AppRepository.portableDownloadUrl("https://example.com/x.zip"),
        )
    }
}
