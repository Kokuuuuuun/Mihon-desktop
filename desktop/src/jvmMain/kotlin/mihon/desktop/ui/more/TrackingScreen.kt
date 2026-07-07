package mihon.desktop.ui.more

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import mihon.desktop.bridge.Suwayomi
import mihon.desktop.bridge.TrackerDto
import mihon.desktop.ui.common.ErrorBox
import mihon.desktop.ui.common.LoadState
import mihon.desktop.ui.common.LoadingBox
import java.awt.Desktop
import java.net.URI

/**
 * Basic tracker management: lists the supported trackers (MyAnimeList / AniList / …) and their
 * login state. OAuth trackers open the auth URL in the system browser; the callback is completed
 * from the web UI / a redirect the server handles.
 */
object TrackingScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        var state by remember { mutableStateOf<LoadState<List<TrackerDto>>>(LoadState.Loading) }
        var reloadKey by remember { mutableStateOf(0) }

        LaunchedEffect(reloadKey) {
            state = runCatching { Suwayomi.client.getTrackers() }
                .fold({ LoadState.Success(it) }, { LoadState.Error(it.message ?: it.toString()) })
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Seguimiento") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                        }
                    },
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (val s = state) {
                    is LoadState.Loading -> LoadingBox()
                    is LoadState.Error -> ErrorBox(s.message)
                    is LoadState.Success -> LazyColumn(Modifier.fillMaxSize()) {
                        items(s.value, key = { it.id }) { tracker ->
                            ListItem(
                                leadingContent = {
                                    AsyncImage(
                                        model = Suwayomi.config.coverUrl(tracker.icon),
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp),
                                    )
                                },
                                headlineContent = { Text(tracker.name, fontWeight = FontWeight.Medium) },
                                supportingContent = {
                                    Text(
                                        when {
                                            tracker.isLoggedIn && tracker.isTokenExpired -> "Sesión expirada"
                                            tracker.isLoggedIn -> "Conectado"
                                            else -> "No conectado"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                trailingContent = {
                                    if (tracker.isLoggedIn) {
                                        OutlinedButton(onClick = {
                                            scope.launch { runCatching { Suwayomi.client.logoutTracker(tracker.id) }; reloadKey++ }
                                        }) { Text("Cerrar sesión") }
                                    } else {
                                        OutlinedButton(
                                            enabled = tracker.authUrl != null,
                                            onClick = { tracker.authUrl?.let(::openInBrowser) },
                                        ) { Text("Conectar") }
                                    }
                                },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    private fun openInBrowser(url: String) {
        runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(url))
            }
        }
    }
}
