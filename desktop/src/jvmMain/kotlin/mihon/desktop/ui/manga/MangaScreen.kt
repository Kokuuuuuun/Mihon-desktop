package mihon.desktop.ui.manga

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import mihon.desktop.bridge.ChapterDto
import mihon.desktop.bridge.MangaDto
import mihon.desktop.bridge.Suwayomi
import mihon.desktop.settings.DesktopSettings
import mihon.desktop.ui.common.ErrorBox
import mihon.desktop.ui.common.LoadState
import mihon.desktop.ui.common.LoadingBox
import mihon.desktop.ui.reader.ReaderScreen

/** Manga details + chapter list. Refreshes details and chapters from the source on open. */
data class MangaScreen(val mangaId: Long, val sourceId: String) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        var state by remember { mutableStateOf<LoadState<Pair<MangaDto, List<ChapterDto>>>>(LoadState.Loading) }
        var inLibrary by remember { mutableStateOf(false) }
        var reloadKey by remember { mutableStateOf(0) }
        // Chapter list controls: sort order, free-text search, bulk-action spinners.
        var chaptersAsc by remember { mutableStateOf(DesktopSettings.chaptersSortAsc) }
        var chapterQuery by remember { mutableStateOf("") }
        var bulkRunning by remember { mutableStateOf(false) }

        LaunchedEffect(mangaId, reloadKey) {
            state = runCatching {
                val details = Suwayomi.client.fetchManga(mangaId)
                val chapters = Suwayomi.client.fetchChapters(mangaId)
                inLibrary = details.inLibrary
                details to chapters
            }.fold({ LoadState.Success(it) }, { LoadState.Error(it.message ?: it.toString()) })
        }

        /** Re-fetch only the chapter list (used after reader / mark-all / download-all). */
        fun refreshChapters() {
            scope.launch {
                runCatching { Suwayomi.client.fetchChapters(mangaId) }.onSuccess { chapters ->
                    (state as? LoadState.Success)?.let { s -> state = LoadState.Success(s.value.first to chapters) }
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text((state as? LoadState.Success)?.value?.first?.title ?: "Detalles") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val target = !inLibrary
                            inLibrary = target
                            scope.launch { runCatching { Suwayomi.client.setInLibrary(mangaId, target) } }
                        }) {
                            Icon(
                                if (inLibrary) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = if (inLibrary) "En biblioteca" else "Añadir a biblioteca",
                                tint = if (inLibrary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    },
                )
            },
        ) { padding ->
            when (val s = state) {
                is LoadState.Loading -> LoadingBox()
                is LoadState.Error -> ErrorBox(s.message)
                is LoadState.Success -> {
                    val (manga, chapters) = s.value
                    // Sort: server already returns sourceOrder; asc = oldest first, desc = newest first.
                    val ordered = remember(chapters, chaptersAsc) {
                        if (chaptersAsc) chapters.sortedBy { it.sourceOrder } else chapters.sortedByDescending { it.sourceOrder }
                    }
                    val filtered = remember(ordered, chapterQuery) {
                        val q = chapterQuery.trim()
                        if (q.isEmpty()) ordered else ordered.filter { it.name.contains(q, ignoreCase = true) }
                    }
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                        item { MangaHeader(manga) }
                        if (manga.genre.isNotEmpty()) {
                            item {
                                GenreChips(manga.genre)
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                        item {
                            InfoTable(
                                author = manga.author,
                                artist = null,
                                status = manga.status,
                                source = manga.sourceId ?: sourceId,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        item {
                            ChapterListHeader(
                                total = chapters.size,
                                shown = filtered.size,
                                asc = chaptersAsc,
                                query = chapterQuery,
                                onQueryChange = { chapterQuery = it },
                                onToggleOrder = {
                                    chaptersAsc = !chaptersAsc
                                    DesktopSettings.updateChaptersSortAsc(chaptersAsc)
                                },
                                onMarkAllRead = {
                                    if (!bulkRunning) {
                                        bulkRunning = true
                                        scope.launch {
                                            runCatching { Suwayomi.client.setChaptersRead(chapters, read = true) }
                                            refreshChapters()
                                            bulkRunning = false
                                        }
                                    }
                                },
                                onMarkAllUnread = {
                                    if (!bulkRunning) {
                                        bulkRunning = true
                                        scope.launch {
                                            runCatching { Suwayomi.client.setChaptersRead(chapters, read = false) }
                                            refreshChapters()
                                            bulkRunning = false
                                        }
                                    }
                                },
                                onDownloadAll = {
                                    if (!bulkRunning) {
                                        bulkRunning = true
                                        scope.launch {
                                            runCatching { Suwayomi.client.downloadAllChapters(chapters) }
                                            bulkRunning = false
                                        }
                                    }
                                },
                                bulkRunning = bulkRunning,
                            )
                            HorizontalDivider()
                        }
                        items(filtered, key = { it.id }) { chapter ->
                            ChapterRow(
                                chapter = chapter,
                                onOpen = { navigator.push(ReaderScreen(mangaId, chapter.id, manga.title, chapter.name, chapters)) },
                                onToggleRead = {
                                    scope.launch {
                                        runCatching { Suwayomi.client.updateChapter(chapter.id, isRead = !chapter.isRead) }
                                        refreshChapters()
                                    }
                                },
                                onDownload = {
                                    scope.launch {
                                        runCatching {
                                            Suwayomi.client.enqueueDownloads(listOf(chapter.id))
                                            Suwayomi.client.startDownloader()
                                        }
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
}

@Composable
private fun MangaHeader(manga: MangaDto) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row {
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                Suwayomi.config.coverUrl(manga.thumbnailUrl)?.let { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = manga.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(manga.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                manga.author?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    manga.status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        manga.description?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** A horizontal-wrap row of tappable genre chips. Clicking a chip is a structural hook for the
 *  browse-by-genre flow (currently a no-op visual affordance since the source API doesn't expose
 *  genre search globally). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenreChips(genres: List<String>) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        genres.forEach { genre ->
            AssistChip(
                onClick = { /* hook: browse-by-genre */ },
                label = { Text(genre) },
                colors = AssistChipDefaults.assistChipColors(),
            )
        }
    }
}

/** Two-column key/value info card mirroring Mihon's manga "Informa­ción" panel. */
@Composable
private fun InfoTable(author: String?, artist: String?, status: String, source: String) {
    Surface(
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            InfoRow("Autor", author)
            InfoRow("Artista", artist)
            InfoRow("Estado", status.replaceFirstChar { it.uppercase() })
            InfoRow("Fuente", source)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String?) {
    val v = value?.takeIf { it.isNotBlank() } ?: "—"
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Text(v, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterListHeader(
    total: Int,
    shown: Int,
    asc: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onToggleOrder: () -> Unit,
    onMarkAllRead: () -> Unit,
    onMarkAllUnread: () -> Unit,
    onDownloadAll: () -> Unit,
    bulkRunning: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                "Capítulos ($shown/${total})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onToggleOrder) {
                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = if (asc) "Más antiguos primero" else "Más recientes primero")
            }
            IconButton(onClick = onMarkAllRead, enabled = !bulkRunning) {
                Icon(Icons.Filled.DoneAll, contentDescription = "Marcar todo leído")
            }
            IconButton(onClick = onMarkAllUnread, enabled = !bulkRunning) {
                Icon(Icons.Filled.RadioButtonUnchecked, contentDescription = "Marcar todo no leído")
            }
            IconButton(onClick = onDownloadAll, enabled = !bulkRunning) {
                Icon(Icons.Filled.Download, contentDescription = "Descargar todo")
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            placeholder = { Text("Buscar capítulos…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ChapterRow(
    chapter: ChapterDto,
    onOpen: () -> Unit,
    onToggleRead: () -> Unit,
    onDownload: () -> Unit,
) {
    ListItem(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        headlineContent = {
            Text(
                chapter.name,
                fontWeight = if (chapter.isRead) FontWeight.Normal else FontWeight.Medium,
                color = if (chapter.isRead) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = chapter.scanlator?.takeIf { it.isNotBlank() }?.let {
            { Text(it, style = MaterialTheme.typography.bodySmall) }
        },
        trailingContent = {
            Row {
                IconButton(onClick = onToggleRead) {
                    Icon(
                        if (chapter.isRead) Icons.Filled.Check else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = if (chapter.isRead) "Marcar como no leído" else "Marcar como leído",
                        tint = if (chapter.isRead) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDownload, enabled = !chapter.isDownloaded) {
                    Icon(
                        Icons.Filled.Download,
                        contentDescription = "Descargar",
                        tint = if (chapter.isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}
