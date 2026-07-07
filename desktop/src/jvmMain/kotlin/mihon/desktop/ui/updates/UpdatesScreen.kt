package mihon.desktop.ui.updates

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import mihon.desktop.bridge.ChapterDto
import mihon.desktop.bridge.Suwayomi
import mihon.desktop.ui.common.ChapterFeedList
import mihon.desktop.ui.common.ErrorBox
import mihon.desktop.ui.common.LoadState
import mihon.desktop.ui.common.LoadingBox
import mihon.desktop.ui.reader.ReaderScreen

/** The Updates ("Novedades") tab: recently fetched chapters of library manga. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesScreen() {
    val navigator = LocalNavigator.currentOrThrow
    var state by remember { mutableStateOf<LoadState<List<ChapterDto>>>(LoadState.Loading) }

    LaunchedEffect(Unit) {
        state = runCatching { Suwayomi.client.getRecentUpdates() }
            .fold({ LoadState.Success(it) }, { LoadState.Error(it.message ?: it.toString()) })
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Novedades") }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is LoadState.Loading -> LoadingBox()
                is LoadState.Error -> ErrorBox(s.message)
                is LoadState.Success -> if (s.value.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Sin novedades. Actualiza tu biblioteca.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    ChapterFeedList(s.value) { ch ->
                        navigator.push(ReaderScreen(ch.mangaId, ch.id, ch.manga?.title ?: "", ch.name))
                    }
                }
            }
        }
    }
}
