package com.example.offlinegamehub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class GamePlayActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val gameId = intent.getStringExtra(EXTRA_GAME_ID).orEmpty()
        setContent { AppTheme { GamePlayScreen(gameId = gameId, onExit = { finish() }) } }
    }

    companion object { const val EXTRA_GAME_ID = "game_id" }
}
