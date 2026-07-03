package ru.app.rustoreupdater.data.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface RuStoreApi {

    /** Search the RuStore catalog. */
    @GET("applicationData/apps")
    suspend fun search(
        @Query("query") query: String,
        @Query("pageNumber") pageNumber: Int = 0,
        @Query("pageSize") pageSize: Int = 20,
    ): SearchResultDto

    /** Full app info (version, description, screenshots, etc.). */
    @GET("applicationData/overallInfo/{appId}")
    suspend fun getOverallInfo(
        @Path("appId") appId: String,
    ): OverallInfoDto

    /** Get the direct APK download link. */
    @POST("applicationData/v2/download-link")
    suspend fun getDownloadLink(
        @Body body: DownloadLinkRequest,
    ): DownloadLinkDto

    companion object {
        const val BASE_URL = "https://backapi.rustore.ru/"
        const val WEB_CATALOG_URL = "https://www.rustore.ru/catalog/app/"
    }
}
