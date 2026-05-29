package io.torvox.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun TerminalScreen(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            AndroidView(
                factory = { context ->
                    TerminalSurface(context).apply {
                        setDimensions(24, 80)
                        setMaxScrollback(50000)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
