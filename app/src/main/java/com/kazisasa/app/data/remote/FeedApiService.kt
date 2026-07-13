package com.kazisasa.app.data.remote

import com.kazisasa.app.data.remote.dto.FeedResponseDto
import retrofit2.Response
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Url
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Feed source (spec §24.1): a single JSON file on GitHub raw, regenerated on a
 * schedule by GitHub Actions in the companion `feed/` repo - the same pattern as
 * the election-intelligence engine. [feedUrl] is a full URL rather than a
 * @Path/@Query pair because it may point at any branch/tag during development.
 */
interface FeedApiService {
    @GET
    suspend fun fetchFeed(@Url feedUrl: String): Response<FeedResponseDto>

    companion object {
        /**
         * Point this at your own `feed/` repo's raw feed.json once it exists -
         * see feed/README.md in this project for the GitHub Actions setup.
         */
        const val DEFAULT_FEED_URL =
            "https://raw.githubusercontent.com/dansamuka/kazi-sasa-feed/main/feed.json"

        fun create(): FeedApiService {
            val json = Json { ignoreUnknownKeys = true; isLenient = true }
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            // baseUrl is required by Retrofit but unused since every call supplies a full @Url.
            val retrofit = Retrofit.Builder()
                .baseUrl("https://raw.githubusercontent.com/")
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
            return retrofit.create(FeedApiService::class.java)
        }
    }
}
