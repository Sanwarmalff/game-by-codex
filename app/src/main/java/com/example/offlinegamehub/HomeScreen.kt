package com.example.offlinegamehub

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

enum class HubTab { Catalog, Favorites, Library }

@Composable
fun OfflineGameHubApp() {
    var playingGameId by remember { mutableStateOf<String?>(null) }
    if (playingGameId != null) {
        GamePlayScreen(gameId = playingGameId.orEmpty(), onExit = { playingGameId = null })
    } else {
        HomeScreen(onPlay = { playingGameId = it })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onPlay: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { GameRepository() }
    val downloadManager = remember { DownloadManager(context) }
    val prefs = remember { context.getSharedPreferences("favorites", Context.MODE_PRIVATE) }

    var selectedTab by remember { mutableStateOf(HubTab.Catalog) }
    var search by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var catalog by remember { mutableStateOf<List<GameModel>>(emptyList()) }
    var installed by remember { mutableStateOf(downloadManager.installedGames()) }
    var favorites by remember { mutableStateOf(prefs.getStringSet("ids", emptySet()).orEmpty()) }
    var screenError by remember { mutableStateOf<String?>(null) }
    val progress = remember { mutableStateMapOf<String, Float>() }
    val cardErrors = remember { mutableStateMapOf<String, String>() }

    fun refreshInstalled() { installed = downloadManager.installedGames() }
    fun toggleFavorite(id: String) {
        favorites = if (id in favorites) favorites - id else favorites + id
        prefs.edit().putStringSet("ids", favorites).apply()
    }

    LaunchedEffect(Unit) {
        loading = true
        when (val result = repository.fetchCatalog()) {
            is HubResult.Success -> {
                catalog = result.value
                screenError = if (result.value.isEmpty()) "Catalog is empty. Add game folders under /games and run the GitHub Action." else null
            }
            is HubResult.Failure -> screenError = result.userMessage
        }
        refreshInstalled()
        loading = false
    }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(containerColor = Color(0xEE0B1020)) {
                NavigationBarItem(selected = selectedTab == HubTab.Catalog, onClick = { selectedTab = HubTab.Catalog }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Catalog") })
                NavigationBarItem(selected = selectedTab == HubTab.Favorites, onClick = { selectedTab = HubTab.Favorites }, icon = { Icon(Icons.Default.Favorite, null) }, label = { Text("Favorites") })
                NavigationBarItem(selected = selectedTab == HubTab.Library, onClick = { selectedTab = HubTab.Library }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Library") })
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Offline Game Hub", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
            Text("Native Kotlin • HTML games packaged by GitHub Actions", color = Color(0xFFB7C0FF))
            Spacer(Modifier.height(14.dp))
            if (selectedTab != HubTab.Library) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search games") },
                    shape = RoundedCornerShape(22.dp)
                )
                Spacer(Modifier.height(12.dp))
            }
            AnimatedVisibility(screenError != null) { ErrorBanner(screenError.orEmpty()) }

            when {
                loading -> SkeletonGrid()
                selectedTab == HubTab.Library -> LibraryScreen(installed, downloadManager.totalStorageBytes(), onDelete = { id ->
                    when (val result = downloadManager.deleteGame(id)) {
                        is HubResult.Success -> refreshInstalled()
                        is HubResult.Failure -> screenError = result.userMessage
                    }
                })
                else -> {
                    val base = if (selectedTab == HubTab.Favorites) catalog.filter { it.id in favorites } else catalog
                    val games = base.filter { it.name.contains(search, true) || it.description.contains(search, true) || it.id.contains(search, true) }
                    if (games.isEmpty()) EmptyMessage(if (selectedTab == HubTab.Favorites) "No favorites yet. Tap the heart on any game." else "No games match your search.")
                    LazyVerticalGrid(modifier = Modifier.weight(1f), columns = GridCells.Adaptive(180.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(games, key = { it.id }) { game ->
                            GameCard(
                                state = GameUiState(game, installed.firstOrNull { it.id == game.id }, progress[game.id], cardErrors[game.id]),
                                favorite = game.id in favorites,
                                onFavorite = { toggleFavorite(game.id) },
                                onAction = {
                                    val local = installed.firstOrNull { it.id == game.id }
                                    if (local != null && game.version <= local.version) onPlay(game.id) else scope.launch {
                                        cardErrors.remove(game.id)
                                        downloadManager.installOrUpdate(game).collect { state ->
                                            when (state) {
                                                is DownloadState.Progress -> progress[game.id] = state.percent
                                                is DownloadState.Failed -> { progress.remove(game.id); cardErrors[game.id] = state.userMessage }
                                                DownloadState.Complete -> { progress.remove(game.id); refreshInstalled() }
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GameCard(state: GameUiState, favorite: Boolean, onFavorite: () -> Unit, onAction: () -> Unit) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color(0xCC151B2E)), shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.padding(14.dp)) {
            Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFF202944))) {
                AsyncImage(model = state.game.logoUrl, contentDescription = state.game.name, modifier = Modifier.fillMaxSize())
                IconButton(onClick = onFavorite, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "Favorite", tint = Color(0xFFFF5C8A))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(state.game.name, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(state.game.description, color = Color(0xFFC7D2FE), style = MaterialTheme.typography.bodySmall, minLines = 2, maxLines = 2)
            AnimatedVisibility(state.error != null) { Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(10.dp))
            if (state.progress != null) {
                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                Text("Downloading ${(state.progress * 100).toInt()}%", color = Color(0xFFB7C0FF), style = MaterialTheme.typography.labelMedium)
            } else {
                Button(onClick = onAction, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Text(if (!state.isInstalled) "Install" else if (state.needsUpdate) "Update" else "Play")
                }
            }
        }
    }
}

@Composable
fun LibraryScreen(installed: List<InstalledGame>, totalBytes: Long, onDelete: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color(0xCC151B2E)), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Text("Storage Manager", color = Color.White, fontWeight = FontWeight.Bold)
                Text("${installed.size} games installed • ${totalBytes.toReadableSize()} used", color = Color(0xFFC7D2FE))
            }
        }
        installed.forEach { game ->
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color(0xAA151B2E)), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(game.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("v${game.version} • ${game.folderSizeBytes.toReadableSize()}", color = Color(0xFFC7D2FE))
                    }
                    IconButton(onClick = { onDelete(game.id) }) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF6B6B)) }
                }
            }
        }
        if (installed.isEmpty()) EmptyMessage("No installed games yet.")
    }
}

@Composable fun ErrorBanner(message: String) { Text(message, color = Color(0xFFFFD166), modifier = Modifier.fillMaxWidth().background(Color(0x332C1D00), RoundedCornerShape(16.dp)).padding(12.dp)) }
@Composable fun EmptyMessage(message: String) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(message, color = Color(0xFFB7C0FF)) } }
@Composable fun SkeletonGrid() { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { repeat(4) { Box(Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(24.dp)).background(Color(0x55202A44))) } } }
