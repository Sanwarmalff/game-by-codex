package com.example.offlinegamehub

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.EOFException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

private const val RAW_GITHUB_BASE_URL = "https://raw.githubusercontent.com/"
const val CATALOG_URL = "https://raw.githubusercontent.com/YOUR_USERNAME/YOUR_REPO/gh-pages/games.json"

class GameRepository(
    private val catalogUrl: String = CATALOG_URL,
    okHttpClient: OkHttpClient = defaultClient()
) {
    private val api: CatalogApi = Retrofit.Builder()
        .baseUrl(RAW_GITHUB_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(
            MoshiConverterFactory.create(
                Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            )
        )
        .build()
        .create(CatalogApi::class.java)

    suspend fun fetchCatalog(): HubResult<List<GameModel>> = withContext(Dispatchers.IO) {
        try {
            val games = api.fetchCatalog(catalogUrl).games
                .filter { it.id.isNotBlank() && it.name.isNotBlank() && it.downloadUrl.isNotBlank() }
            HubResult.Success(games)
        } catch (error: Throwable) {
            HubResult.Failure(error.toCatalogMessage(), error)
        }
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .build()
    }
}

private fun Throwable.toCatalogMessage(): String = when (this) {
    is UnknownHostException -> "No internet connection. Connect to the internet to refresh the catalog."
    is SocketTimeoutException -> "Network timeout. Please try again on a faster connection."
    is HttpException -> "Catalog server error: HTTP ${code()}."
    is JsonDataException, is EOFException -> "JSON parsing failed. The catalog format is invalid."
    else -> localizedMessage?.takeIf { it.isNotBlank() }?.let { "Catalog fetch failed: $it" }
        ?: "Catalog fetch failed due to an unknown error."
}
