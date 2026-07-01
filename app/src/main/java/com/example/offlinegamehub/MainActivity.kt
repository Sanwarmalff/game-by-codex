package com.example.offlinegamehub

import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

private const val CATALOG_URL = "https://raw.githubusercontent.com/YOUR_USERNAME/YOUR_REPO/main/games.json"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppTheme { HubApp() } }
    }
}

@Composable
fun HubApp() {
    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(1500); showSplash = false }
    if (showSplash) SplashScreen() else HomeScreen()
}

@Composable
private fun SplashScreen() {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = .6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "alpha"
    )
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF12162A), Color(0xFF080A12)))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("◈", color = Color(0xFF8B5CF6), style = MaterialTheme.typography.displayLarge, modifier = Modifier.alpha(pulse))
            Text("Offline Game Hub", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Native • Offline • Play Anywhere", color = Color(0xFFB7C0FF))
        }
    }
}

@Composable
private fun HomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { DownloadManager(context) }
    var catalog by remember { mutableStateOf<List<GameModel>>(emptyList()) }
    var installed by remember { mutableStateOf(manager.installedGames()) }
    var error by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(0) }
    val progress = remember { mutableStateMapOf<String, Float>() }

    LaunchedEffect(Unit) {
        val local = manager.installedGames()
        installed = local
        if (context.isOnline()) {
            val api = Retrofit.Builder()
                .baseUrl("https://raw.githubusercontent.com/")
                .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().add(KotlinJsonAdapterFactory()).build()))
                .build()
                .create(CatalogApi::class.java)
            runCatching { api.fetchCatalog(CATALOG_URL).games }
                .onSuccess { catalog = it }
                .onFailure { error = "Catalog unavailable. Showing installed games." }
        }
        if (catalog.isEmpty()) {
            catalog = local.map { GameModel(it.id, it.name, "Installed offline game", "", it.version, "") }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color(0xCC111827)) {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = {}, label = { Text("Catalog") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = {}, label = { Text("My Games") })
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF101426), Color(0xFF05070D)))).padding(padding)) {
            Column(Modifier.padding(16.dp)) {
                Text(if (tab == 0) "Game Catalog" else "My Games", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                AnimatedVisibility(error != null) { Text(error.orEmpty(), color = Color(0xFFFFD166)) }
                Spacer(Modifier.height(12.dp))
                if (tab == 0) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(catalog, key = { it.id }) { game ->
                            val local = installed.firstOrNull { it.id == game.id }
                            GameCard(GameUiState(game, local, progress[game.id]), onAction = {
                                if (local != null && game.version <= local.version) {
                                    context.startActivity(Intent(context, GamePlayActivity::class.java).putExtra(GamePlayActivity.EXTRA_GAME_ID, game.id))
                                } else {
                                    scope.launch {
                                        manager.installOrUpdate(game).collect { progress[game.id] = it }
                                        progress.remove(game.id)
                                        installed = manager.installedGames()
                                    }
                                }
                            })
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(installed, key = { it.id }) { game ->
                            InstalledCard(game, onPlay = {
                                context.startActivity(Intent(context, GamePlayActivity::class.java).putExtra(GamePlayActivity.EXTRA_GAME_ID, game.id))
                            }, onDelete = {
                                manager.deleteGame(game.id)
                                installed = manager.installedGames()
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameCard(state: GameUiState, onAction: () -> Unit) {
    GlassCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = state.game.logoUrl, contentDescription = null, modifier = Modifier.size(72.dp).background(Color(0x332C365E), RoundedCornerShape(18.dp)))
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(state.game.name, color = Color.White, fontWeight = FontWeight.Bold)
                Text(state.game.description, color = Color(0xFFC7D2FE), style = MaterialTheme.typography.bodySmall)
            }
            if (state.progress != null) CircularProgressIndicator(progress = { state.progress }, modifier = Modifier.size(44.dp)) else Button(onClick = onAction) {
                Text(if (!state.isInstalled) "Install" else if (state.needsUpdate) "Update" else "Play")
            }
        }
    }
}

@Composable
private fun InstalledCard(game: InstalledGame, onPlay: () -> Unit, onDelete: () -> Unit) {
    GlassCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(game.name, color = Color.White, fontWeight = FontWeight.Bold)
                Text("${game.folderSizeBytes.toReadableSize()} • v${game.version}", color = Color(0xFFC7D2FE))
            }
            Button(onClick = onPlay) { Text("Play") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF6B6B)) }
        }
    }
}

@Composable
private fun GlassCard(content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Surface(color = Color(0x6622304A), modifier = Modifier.fillMaxWidth().padding(16.dp), content = content)
    }
}

@Composable
private fun AppTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF8B5CF6)), content = content)

private fun android.content.Context.isOnline(): Boolean {
    val cm = getSystemService(ConnectivityManager::class.java)
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
