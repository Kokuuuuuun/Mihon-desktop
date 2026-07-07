package mihon.desktop.ui.browse

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import mihon.desktop.bridge.MangaDto
import mihon.desktop.bridge.SourceDto
import mihon.desktop.bridge.Suwayomi
import mihon.desktop.ui.manga.MangaScreen

/**
 * Global search across every installed source, mirroring Mihon's GlobalSearchScreen: results are
 * shown grouped by source as they arrive, with a "see all" entry into that source's catalogue.
 */
data class GlobalSearchScreen(val initialQuery: String = "", val initialExtensionFilter: String? = null) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        var query by remember { mutableStateOf(initialQuery) }
        var sources by remember { mutableStateOf<List<SourceDto>>(emptyList()) }
        var results by remember { mutableStateOf<List<Pair<SourceDto, List<MangaDto>>>>(emptyList()) }
        var searching by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            runCatching { Suwayomi.client.getSources() }
                .onSuccess { all ->
                    sources = all.filter { it.id != "0" }.let { srcs ->
                        initialExtensionFilter?.let { f -> srcs.filter { it.id == f } }.takeIf { !it.isNullOrEmpty() } ?: srcs
                    }
                }
        }

        // Debounced search: re-run when the query settles for 600ms.
        var searchJob by remember { mutableStateOf<Job?>(null) }
        LaunchedEffect(query, sources) {
            searchJob?.cancel()
            if (query.isBlank() || sources.isEmpty()) {
                results = emptyList()
                return@LaunchedEffect
            }
            searchJob = scope.launch {
                searching = true
                results = runCatching { Suwayomi.client.globalSearch(sources, query) }.getOrDefault(emptyList())
                searching = false
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Búsqueda global") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                        }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Buscar en todas las fuentes…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { /* debounced */ }),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                )

                if (query.isBlank()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Escribe un término para buscar en todas las fuentes.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    return@Scaffold
                }

                if (searching && results.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else if (results.all { it.second.isEmpty() }) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Sin resultados en ninguna fuente.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            if (searching) {
                                Text(
                                    "Buscando… (resultados parciales)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                        }
                        results.forEach { (source, mangas) ->
                            if (mangas.isEmpty()) return@forEach
                            item(key = "header-${source.id}") {
                                SearchHeader(source.name, onSeeAll = {
                                    navigator.push(BrowseSourceScreen(source.id, source.name, source.lang, initialQuery = query))
                                })
                            }
                            items(mangas, key = { "${source.id}-${it.id}" }) { manga ->
                                SearchRow(manga) { navigator.push(MangaScreen(manga.id, source.id)) }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SearchHeader(sourceName: String, onSeeAll: () -> Unit) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(sourceName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onSeeAll) {
                Text("Ver todo")
                Icon(Icons.Filled.ChevronRight, contentDescription = null)
            }
        }
    }

    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    @Composable
    private fun SearchRow(manga: MangaDto, onClick: () -> Unit) {
        ListItem(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).combinedClickable(onClick = onClick),
            leadingContent = {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(6.dp))) {
                    AsyncImage(
                        model = Suwayomi.config.coverUrl(manga.thumbnailUrl),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            },
            headlineContent = { Text(manga.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        )
    }
}
