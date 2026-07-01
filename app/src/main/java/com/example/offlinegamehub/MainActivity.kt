package com.example.offlinegamehub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {
    
    // 👑 UPDATE THIS: Apne GitHub ka raw games.json URL yahan daliye
    companion object {
        const val CATALOG_URL = "https://raw.githubusercontent.com/sanwarmalff/game-by-codex/main/games.json"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppTheme { OfflineGameHubApp() } }
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF8B5CF6),
            secondary = Color(0xFF22D3EE),
            background = Color(0xFF05070D),
            surface = Color(0xFF111827),
            error = Color(0xFFFF6B6B)
        ),
        content = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFF101426), Color(0xFF05070D))))
            ) { content() }
        }
    )
}
