package ru.app.rustoreupdater.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* ---- Search response: GET /applicationData/apps ---- */

@Serializable
data class SearchResultDto(
    @SerialName("code") val code: String? = null,
    @SerialName("body") val body: SearchResultBodyDto? = null,
)

@Serializable
data class SearchResultBodyDto(
    @SerialName("content") val content: List<SearchItemDto> = emptyList(),
    @SerialName("totalPages") val totalPages: Int = 0,
)

@Serializable
data class SearchItemDto(
    @SerialName("appId") val appId: Long = 0,
    @SerialName("packageName") val packageName: String? = null,
    @SerialName("appName") val appName: String? = null,
    @SerialName("iconUrl") val iconUrl: String? = null,
    @SerialName("companyName") val companyName: String? = null,
    @SerialName("versionName") val versionName: String? = null,
    @SerialName("versionCode") val versionCode: Long = 0,
    @SerialName("fileSize") val fileSize: Long = 0,
    @SerialName("averageUserRating") val averageUserRating: Double = 0.0,
    @SerialName("totalRatings") val totalRatings: Long = 0,
    @SerialName("shortDescription") val shortDescription: String? = null,
    @SerialName("appVerUpdatedAt") val appVerUpdatedAt: String? = null,
    @SerialName("firstPublishedAt") val firstPublishedAt: String? = null,
) {
    val appIdString: String get() = appId.toString()
}

/* ---- Overall info: GET /applicationData/overallInfo/{appId} ---- */

@Serializable
data class OverallInfoDto(
    @SerialName("code") val code: String? = null,
    @SerialName("body") val body: OverallInfoBodyDto? = null,
)

@Serializable
data class OverallInfoBodyDto(
    @SerialName("appId") val appId: Long = 0,
    @SerialName("appName") val appName: String? = null,
    @SerialName("packageName") val packageName: String? = null,
    @SerialName("companyName") val companyName: String? = null,
    @SerialName("iconUrl") val iconUrl: String? = null,
    // RuStore returns categories as a plain array of strings, e.g. ["transport"].
    @SerialName("categories") val categories: List<String> = emptyList(),
    @SerialName("versionName") val versionName: String? = null,
    @SerialName("versionCode") val versionCode: Long = 0,
    @SerialName("fileSize") val fileSize: Long = 0,
    @SerialName("whatsNew") val whatsNew: String? = null,
    @SerialName("fullDescription") val fullDescription: String? = null,
    @SerialName("shortDescription") val shortDescription: String? = null,
    @SerialName("appVerUpdatedAt") val appVerUpdatedAt: String? = null,
    @SerialName("firstPublishedAt") val firstPublishedAt: String? = null,
    @SerialName("fileUrls") val fileUrls: List<FileUrlDto> = emptyList(),
    @SerialName("ageRestriction") val ageRestriction: AgeRestrictionDto? = null,
) {
    val appIdString: String get() = appId.toString()
    val category: String? get() = categories.firstOrNull()
}

@Serializable
data class AgeRestrictionDto(
    @SerialName("category") val category: String? = null,
    @SerialName("name") val name: String? = null,
)

@Serializable
data class FileUrlDto(
    @SerialName("fileUrl") val fileUrl: String? = null,
    @SerialName("ordinal") val ordinal: Int = 0,
)

/* ---- Download link: POST /applicationData/v2/download-link ---- */

@Serializable
data class DownloadLinkRequest(
    @SerialName("appId") val appId: String,
    @SerialName("firstInstall") val firstInstall: Boolean = true,
    @SerialName("mobileServices") val mobileServices: List<String> = emptyList(),
    @SerialName("supportedAbis") val supportedAbis: List<String> = listOf(
        "arm64-v8a", "armeabi-v7a", "x86_64", "x86"
    ),
    @SerialName("screenDensity") val screenDensity: Int = 0,
    @SerialName("supportedLocales") val supportedLocales: List<String> = listOf("ru_RU"),
    @SerialName("sdkVersion") val sdkVersion: Int = 33,
    @SerialName("withoutSplits") val withoutSplits: Boolean = true,
    @SerialName("signatureFingerprint") val signatureFingerprint: String? = null,
)

@Serializable
data class DownloadLinkDto(
    @SerialName("code") val code: String? = null,
    @SerialName("body") val body: DownloadLinkBodyDto? = null,
)

@Serializable
data class DownloadLinkBodyDto(
    @SerialName("appId") val appId: Long = 0,
    @SerialName("versionCode") val versionCode: Long = 0,
    @SerialName("versionId") val versionId: Long = 0,
    @SerialName("downloadUrls") val downloadUrls: List<DownloadUrlDto> = emptyList(),
    @SerialName("preApproveInfo") val preApproveInfo: PreApproveInfoDto? = null,
    @SerialName("signature") val signature: String? = null,
)

@Serializable
data class DownloadUrlDto(
    @SerialName("url") val url: String? = null,
    @SerialName("size") val size: Long = 0,
    @SerialName("hash") val hash: String? = null,
)

@Serializable
data class PreApproveInfoDto(
    @SerialName("label") val label: String? = null,
    @SerialName("locale") val locale: String? = null,
)
