package ru.app.rustoreupdater.data.network

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.util.Locale
import java.util.UUID

/**
 * Fingerprint of the *RuStore client* that this app pretends to be.
 *
 * RuStore's backapi is not a stable public API: since 2025 it rejects requests that
 * do not look like they came from the official client. In particular
 * `applicationData/overallInfo/{packageName}` and `applicationData/v2/download-link`
 * fail when the numeric `ruStoreVerCode` header (and the rest of the device
 * fingerprint) is missing or out of the accepted range — HTTP 400 with an empty body
 * when headers are absent, HTTP 417 ("update RuStore") for an outdated client code
 * and HTTP 419 for a code from the future. `applicationData/apps` (search) still
 * works without any headers.
 *
 * The header values are mirrored from working open-source clients (Obtainium,
 * WyStore). They contain no user data: `deviceId` is a locally generated random
 * UUID, everything else is public device metadata.
 */
data class RuStoreDevice(
    val deviceId: String,
    val manufacturer: String,
    val model: String,
    val hardware: String,
    val firmwareVer: String,
    val sdkInt: Int,
    val abis: List<String>,
    val screenDensity: Int,
    val locale: String,
    val locales: List<String>,
    val deviceType: String = "mobile",
) {

    /** Primary ABI as the official client reports it in the User-Agent. */
    val primaryAbi: String get() = abis.firstOrNull() ?: "arm64-v8a"

    fun userAgent(): String =
        "RuStore/$CLIENT_VERSION_NAME (Android $firmwareVer; SDK $sdkInt; $primaryAbi; " +
            "$manufacturer $model; $locale)"

    /**
     * Full set of headers the RuStore client sends. [ruStoreVerCode] is the APK
     * manifest versionCode of the *client* (1111030 for client 1.111.0.3) — not the
     * short marketing code 111103, which the backend treats as a 2021-era client and
     * rejects with HTTP 417.
     */
    fun headers(ruStoreVerCode: Long): Map<String, String> = mapOf(
        "User-Agent" to userAgent(),
        "Accept" to "application/json",
        "deviceId" to deviceId,
        "firmwareVer" to firmwareVer,
        "androidSdkVer" to sdkInt.toString(),
        "deviceManufacturerName" to manufacturer,
        "deviceModelName" to model,
        "deviceModel" to hardware,
        "firmwareLang" to locale,
        "ruStoreVerCode" to ruStoreVerCode.toString(),
        "deviceType" to deviceType,
    )

    companion object {
        /**
         * Marketing version name of the emulated RuStore client, used only inside the
         * User-Agent. Its APK manifest versionCode is [RuStoreHeadersInterceptor]
         * `FALLBACK_VERSION_CODES[0]`. The backend does not cross-check the two: the
         * UA may advertise any version as long as `ruStoreVerCode` is accepted.
         */
        private const val CLIENT_VERSION_NAME = "1.111.0.3"

        /** SharedPreferences file that keeps the generated device id stable. */
        private const val PREFS = "rustore_device"
        private const val KEY_DEVICE_ID = "device_id"

        /** Build the fingerprint from the real device this app runs on. */
        fun fromContext(context: Context): RuStoreDevice {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val deviceId = prefs.getString(KEY_DEVICE_ID, null)
                ?: ("android-" + UUID.randomUUID().toString().replace("-", "").take(16))
                    .also { prefs.edit().putString(KEY_DEVICE_ID, it).apply() }

            val abis = Build.SUPPORTED_ABIS?.toList()?.filter { it.isNotBlank() }
                ?.takeIf { it.isNotEmpty() }
                ?: listOf("arm64-v8a")
            val config = context.resources.configuration
            val systemLocales = config.locales
            val languages = if (systemLocales.isEmpty) {
                listOf(Locale.getDefault())
            } else {
                (0 until systemLocales.size()).map { systemLocales[it] }
            }
            val locales = languages.map { localeTag(it) }
            val density = context.resources.displayMetrics.densityDpi
                .takeIf { it > 0 } ?: 420
            val isTv = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

            return RuStoreDevice(
                deviceId = deviceId,
                manufacturer = Build.MANUFACTURER.ifBlank { "Google" },
                model = Build.MODEL.ifBlank { "Android" },
                hardware = Build.DEVICE.ifBlank { "generic" },
                firmwareVer = Build.VERSION.RELEASE.orEmpty().ifBlank { Build.VERSION.SDK_INT.toString() },
                sdkInt = Build.VERSION.SDK_INT,
                abis = abis,
                screenDensity = density,
                locale = languages.firstOrNull()?.language ?: "ru",
                locales = locales.ifEmpty { listOf("ru_RU") },
                deviceType = if (isTv) "TV" else "mobile",
            )
        }

        /** Java `Locale.toString()`-style tag, e.g. `ru_RU`, `en_US`. */
        private fun localeTag(locale: Locale): String = when {
            locale.country.isNotEmpty() -> "${locale.language}_${locale.country}"
            else -> locale.language
        }
    }
}
