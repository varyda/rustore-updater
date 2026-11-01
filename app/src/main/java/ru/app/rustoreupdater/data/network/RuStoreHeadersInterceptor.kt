package ru.app.rustoreupdater.data.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds the RuStore client fingerprint to every backapi request and copes with the
 * server-side version window.
 *
 * Background: the backend validates `ruStoreVerCode` against the range of client
 * builds it currently accepts. Values below that range are answered with HTTP 417
 * ("Обновите RuStore"), values above it with HTTP 419 — both with an empty body or
 * a plain-text stub. The window moves as RuStore ships updates, so a single
 * hardcoded value eventually stops working; [FALLBACK_VERSION_CODES] gives the
 * request a few cheap retries before the failure is surfaced as a normal API error.
 */
class RuStoreHeadersInterceptor(
    private val device: RuStoreDevice,
    private val versionCodes: List<Long> = FALLBACK_VERSION_CODES,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val base = chain.request()
        var index = 0
        while (true) {
            val code = versionCodes[index]
            val request = base.newBuilder()
                .apply { device.headers(code).forEach { (name, value) -> header(name, value) } }
                .build()

            val response = chain.proceed(request)
            val retryable = response.code == 417 || response.code == 419
            if (!retryable || index == versionCodes.lastIndex) {
                if (retryable) {
                    Log.w(
                        TAG,
                        "RuStore rejected ruStoreVerCode=$code with HTTP ${response.code} " +
                            "on ${base.url.encodedPath}; out of fallbacks"
                    )
                }
                return response
            }
            Log.d(
                TAG,
                "RuStore answered HTTP ${response.code} for ruStoreVerCode=$code on " +
                    "${base.url.encodedPath}; retrying with ${versionCodes[index + 1]}"
            )
            response.close()
            index++
        }
    }

    companion object {
        private const val TAG = "RuStoreHeaders"

        /**
         * `ruStoreVerCode` candidates, most likely first.
         *
         * The header carries the **APK manifest versionCode** of the RuStore client
         * (`1.111.0.3` -> `1111030`), not the 6-digit marketing code `111103`: the
         * backend classifies 6-digit values as ancient clients and answers them with
         * HTTP 417. Measured live (November 2026): `1105001`..`10000000` are accepted,
         * `1100000` and lower return 417, above `10000000` returns 419.
         */
        val FALLBACK_VERSION_CODES: List<Long> =
            listOf(1111030L, 1108002L, 1105002L, 1105001L)
    }
}
