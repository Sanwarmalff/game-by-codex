package com.example.offlinegamehub

import com.squareup.moshi.Json
import retrofit2.http.GET
import retrofit2.http.Url

// Point CATALOG_URL in MainActivity to the raw GitHub URL of a file with this shape.
data class GamesCatalog(@Json(name = "games") val games: List<GameModel>)

data class GameModel(
    val id: String,
    val name: String,
    val description: String,
    @Json(name = "logoUrl") val logoUrl: String,
    val version: Int,
    @Json(name = "downloadUrl") val downloadUrl: String
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
    val progress: Float? = null
) {
    val isInstalled: Boolean get() = installed != null
    val needsUpdate: Boolean get() = installed != null && game.version > installed.version
}

interface CatalogApi {
    @GET
    suspend fun fetchCatalog(@Url rawJsonUrl: String): GamesCatalog
}
