package com.example.offlinegamehub

import android.content.Context
import android.system.ErrnoException
import android.system.OsConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

class DownloadManager(private val context: Context) {
    private val client = GameRepository.defaultClient()
    private val gamesRoot: File get() = File(context.filesDir, "installed_games").apply { mkdirs() }

    fun installedGames(): List<InstalledGame> = runCatching {
        gamesRoot.listFiles().orEmpty().filter { it.isDirectory }.mapNotNull { folder ->
            val version = File(folder, LOCAL_VERSION_FILE).takeIf { it.exists() }?.readText()?.toIntOrNull() ?: 1
            val name = File(folder, LOCAL_NAME_FILE).takeIf { it.exists() }?.readText().orEmpty().ifBlank { folder.name }
            InstalledGame(folder.name, name, version, folder.folderSize())
        }.sortedBy { it.name.lowercase() }
    }.getOrDefault(emptyList())

    fun totalStorageBytes(): Long = gamesRoot.folderSize()
    fun gameDirectory(gameId: String): File = File(gamesRoot, gameId.safeId())
    fun indexFile(gameId: String): File = File(gameDirectory(gameId), "index.html")

    fun installOrUpdate(game: GameModel): Flow<DownloadState> = flow {
        require(game.id.isNotBlank()) { "Invalid game ID." }
        require(game.downloadUrl.isNotBlank()) { "Missing game download URL." }

        val tempZip = File(context.cacheDir, "${game.id.safeId()}.zip")
        tempZip.delete()
        downloadZip(game.downloadUrl, tempZip) { emit(DownloadState.Progress(it * 0.85f)) }

        val destination = gameDirectory(game.id)
        val staging = File(gamesRoot, ".${game.id.safeId()}_staging").apply { deleteRecursively(); mkdirs() }
        try {
            unzipSecurely(tempZip, staging)
            if (!File(staging, "index.html").exists()) throw IllegalStateException("Extraction failed: index.html was not found in the zip.")
            File(staging, LOCAL_VERSION_FILE).writeText(game.version.toString())
            File(staging, LOCAL_NAME_FILE).writeText(game.name)
            if (destination.exists() && !destination.deleteRecursively()) throw IOException("Could not replace the old game files.")
            if (!staging.renameTo(destination)) throw IOException("Could not move extracted files into the game library.")
            emit(DownloadState.Progress(1f))
            emit(DownloadState.Complete)
        } finally {
            tempZip.delete()
            staging.deleteRecursively()
        }
    }.catch { emit(DownloadState.Failed(it.toDownloadMessage())) }.flowOn(Dispatchers.IO)

    private suspend fun downloadZip(url: String, outputFile: File, onProgress: suspend (Float) -> Unit) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Download failed: HTTP ${response.code}.")
            val body = response.body ?: throw IOException("Download failed: server returned an empty file.")
            val total = body.contentLength().coerceAtLeast(1L)
            outputFile.outputStream().use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress((downloaded.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
    }

    fun deleteGame(gameId: String): HubResult<Unit> = try {
        val dir = gameDirectory(gameId)
        if (!dir.exists() || dir.deleteRecursively()) HubResult.Success(Unit) else HubResult.Failure("Could not delete ${gameId}. Please try again.")
    } catch (error: Throwable) {
        HubResult.Failure(error.toDownloadMessage(), error)
    }

    private fun unzipSecurely(zipFile: File, destination: File) {
        val canonicalDestination = destination.canonicalFile
        ZipInputStream(FileInputStream(zipFile)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val outFile = File(destination, entry.name).canonicalFile
                if (!outFile.path.startsWith(canonicalDestination.path + File.separator)) throw SecurityException("Extraction failed: unsafe zip path ${entry.name}.")
                if (entry.isDirectory) {
                    if (!outFile.mkdirs() && !outFile.isDirectory) throw IOException("Could not create folder ${entry.name}.")
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { zip.copyTo(it) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private fun File.folderSize(): Long = runCatching { walkTopDown().filter { it.isFile }.sumOf { it.length() } }.getOrDefault(0L)

    companion object {
        private const val LOCAL_VERSION_FILE = ".game-version"
        private const val LOCAL_NAME_FILE = ".game-name"
    }
}

private fun String.safeId(): String = lowercase().replace(Regex("[^a-z0-9_-]"), "-").trim('-')

private fun Throwable.toDownloadMessage(): String = when (this) {
    is UnknownHostException -> "Network error: no internet connection."
    is SocketTimeoutException -> "Network timeout while downloading."
    is ZipException -> "Extraction failed: the downloaded zip is corrupted."
    is SecurityException -> message ?: "Extraction failed: unsafe zip content blocked."
    is IOException -> if (message?.contains("No space", true) == true || (cause is ErrnoException && (cause as ErrnoException).errno == OsConstants.ENOSPC)) "Not enough storage to install this game." else message ?: "File error while installing the game."
    is IllegalArgumentException, is IllegalStateException -> message ?: "Install failed because the game package is invalid."
    else -> localizedMessage ?: "Install failed due to an unknown error."
}

fun Long.toReadableSize(): String {
    val mb = this / (1024.0 * 1024.0)
    return if (mb >= 1) "%.1f MB".format(mb) else "%.0f KB".format(this / 1024.0)
}
