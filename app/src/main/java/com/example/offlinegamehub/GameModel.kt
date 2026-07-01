package com.example.offlinegamehub

import com.squareup.moshi.Json
import retrofit2.http.GET
import retrofit2.http.Url

data class GamesCatalog(@Json(name = "games") val games: List<GameModel> = emptyList())

data class GameModel(
    val id: String,
    val name: String,
    val description: String = "Offline HTML game",
    @Json(name = "logoUrl") val logoUrl: String = "",
    val version: Int = 1,
    @Json(name = "downloadUrl") val downloadUrl: String = "",
    @Json(name = "sizeBytes") val sizeBytes: Long = 0L
)

data class InstalledGame(
    val id: String,
    val name: String,
    val version: Int,
    val folderSizeBytes: Long
)

data class GameUiState(
    val game: GameModel,
    val installed: InstalledGame? = null,
    val progress: Float? = null,
    val error: String? = null
) {
    val isInstalled: Boolean get() = installed != null
    val needsUpdate: Boolean get() = installed != null && game.version > installed.version
}

interface CatalogApi {
    @GET
    suspend fun fetchCatalog(@Url rawJsonUrl: String): GamesCatalog
}

sealed class HubResult<out T> {
    data class Success<T>(val value: T) : HubResult<T>()
    data class Failure(val userMessage: String, val cause: Throwable? = null) : HubResult<Nothing>()
}

sealed class DownloadState {
    data class Progress(val percent: Float) : DownloadState()
    data class Failed(val userMessage: String) : DownloadState()
    data object Complete : DownloadState()
}
