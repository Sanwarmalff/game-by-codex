package com.example.offlinegamehub

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

class GamePlayActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val gameId = intent.getStringExtra(EXTRA_GAME_ID).orEmpty()
        val indexFile = DownloadManager(this).indexFile(gameId)
        setContent { GameWebView(indexFile.toURI().toString(), onExit = { finish() }) }
    }

    companion object {
        const val EXTRA_GAME_ID = "game_id"
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun GameWebView(indexUrl: String, onExit: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    settings.allowFileAccessFromFileURLs = true
                    settings.allowUniversalAccessFromFileURLs = false
                    loadUrl(indexUrl)
                }
            }
        )
        IconButton(
            onClick = onExit,
            modifier = Modifier.align(Alignment.TopEnd).padding(18.dp).background(Color(0x99000000), CircleShape)
        ) { Text("X", color = Color.White) }
    }
}
