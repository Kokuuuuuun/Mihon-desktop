package mihon.desktop.ui.downloads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mihon.desktop.bridge.DownloadStatusDto
import mihon.desktop.bridge.Suwayomi

/** Download queue with live progress polling and start/stop/clear controls. */
object DownloadsScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        var status by remember { mutableStateOf(DownloadStatusDto()) }

        // Poll the queue while this screen is visible.
        LaunchedEffect(Unit) {
            while (true) {
                runCatching { Suwayomi.client.getDownloadStatus() }.onSuccess { status = it }
                delay(1000)
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Descargas") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                        }
                    },
                    actions = {
                        val running = status.state == "STARTED"
                        IconButton(onClick = {
                            scope.launch {
                                runCatching {
                                    if (running) Suwayomi.client.stopDownloader() else Suwayomi.client.startDownloader()
                                }
                            }
                        }) {
                            Icon(
                                if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                contentDescription = if (running) "Pausar" else "Reanudar",
                            )
                        }
                    },
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                if (status.queue.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("La cola de descargas está vacía.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(status.queue, key = { it.chapter?.id ?: it.position }) { dl ->
                            ListItem(
                                headlineContent = {
                                    Text(
                                        dl.manga?.title ?: "—",
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                supportingContent = {
                                    Column(Modifier.fillMaxWidth()) {
                                        Text(
                                            "${dl.chapter?.name ?: ""} · ${dl.state}",
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        LinearProgressIndicator(
                                            progress = { dl.progress.coerceIn(0f, 1f) },
                                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        )
                                    }
                                },
                                trailingContent = {
                                    IconButton(onClick = {
                                        val chId = dl.chapter?.id
                                        if (chId != null) scope.launch { runCatching { Suwayomi.client.dequeueDownloads(listOf(chId)) } }
                                    }) { Icon(Icons.Filled.Delete, contentDescription = "Quitar de la cola") }
                                },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
