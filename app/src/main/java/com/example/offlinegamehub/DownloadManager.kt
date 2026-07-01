package com.example.offlinegamehub

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class DownloadManager(private val context: Context) {
    private val client = OkHttpClient.Builder().build()
    private val gamesRoot: File get() = File(context.filesDir, "games").apply { mkdirs() }

    fun installedGames(): List<InstalledGame> = gamesRoot.listFiles().orEmpty()
        .filter { it.isDirectory }
        .mapNotNull { folder ->
            val manifest = File(folder, LOCAL_VERSION_FILE)
            val title = File(folder, LOCAL_NAME_FILE).takeIf { it.exists() }?.readText().orEmpty()
            val version = manifest.takeIf { it.exists() }?.readText()?.toIntOrNull() ?: return@mapNotNull null
            InstalledGame(folder.name, title.ifBlank { folder.name }, version, folder.folderSize())
        }

    fun gameDirectory(gameId: String): File = File(gamesRoot, gameId)
    fun indexFile(gameId: String): File = File(gameDirectory(gameId), "index.html")

    fun installOrUpdate(game: GameModel): Flow<Float> = flow {
        val tempZip = File(context.cacheDir, "${game.id}.zip")
        val request = Request.Builder().url(game.downloadUrl).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Download failed: HTTP ${response.code}")
            val body = response.body ?: error("Empty response body")
            val total = body.contentLength().coerceAtLeast(1L)
            tempZip.outputStream().use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var read: Int
                    var downloaded = 0L
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        emit((downloaded.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }

        val destination = gameDirectory(game.id)
        if (destination.exists()) destination.deleteRecursively()
        destination.mkdirs()
        unzipSecurely(tempZip, destination)
        File(destination, LOCAL_VERSION_FILE).writeText(game.version.toString())
        File(destination, LOCAL_NAME_FILE).writeText(game.name)
        tempZip.delete()
        emit(1f)
    }.flowOn(Dispatchers.IO)

    fun deleteGame(gameId: String): Boolean = gameDirectory(gameId).deleteRecursively()

    private fun unzipSecurely(zipFile: File, destination: File) {
        val canonicalDestination = destination.canonicalFile
        ZipInputStream(FileInputStream(zipFile)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val outFile = File(destination, entry.name).canonicalFile
                require(outFile.path.startsWith(canonicalDestination.path)) { "Unsafe zip path: ${entry.name}" }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { output -> zip.copyTo(output) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private fun File.folderSize(): Long = walkTopDown().filter { it.isFile }.sumOf { it.length() }

    companion object {
        private const val LOCAL_VERSION_FILE = ".game-version"
        private const val LOCAL_NAME_FILE = ".game-name"
    }
}

fun Long.toReadableSize(): String {
    val mb = this / (1024.0 * 1024.0)
    return if (mb >= 1) "%.1f MB".format(mb) else "%.0f KB".format(this / 1024.0)
}
