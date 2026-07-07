package mihon.desktop.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import mihon.desktop.bridge.CategoryDto
import mihon.desktop.bridge.MangaDto
import mihon.desktop.bridge.MangaFetchType
import mihon.desktop.bridge.Suwayomi
import mihon.desktop.settings.DesktopSettings
import mihon.desktop.settings.SourceDisplayMode
import mihon.desktop.ui.common.ErrorBox
import mihon.desktop.ui.common.MangaCoverGrid
import mihon.desktop.ui.manga.MangaScreen

/**
 * Catalogue for a single source: Popular / Latest browse, free-text search with infinite scroll,
 * a generic filter sheet (hide-in-library, display mode), long-press a cover to add/remove from the
 * library and pick categories. Mirrors Mihon's BrowseSourceScreen within what Suwayomi exposes.
 */
data class BrowseSourceScreen(
    val sourceId: String,
    val sourceName: String,
    val sourceLang: String,
    val initialQuery: String? = null,
) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val manga = remember { mutableStateListOf<MangaDto>() }
        // Search query is the source of truth: empty = Popular (or Latest if the source supports it).
        var query by remember { mutableStateOf(initialQuery ?: "") }
        var queryDraft by remember { mutableStateOf(initialQuery ?: "") }
        var type by remember { mutableStateOf(if (initialQuery.isNullOrBlank()) MangaFetchType.POPULAR else MangaFetchType.SEARCH) }
        var page by remember { mutableStateOf(1) }
        var hasNext by remember { mutableStateOf(true) }
        var loading by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var showFilters by remember { mutableStateOf(false) }
        var manageManga by remember { mutableStateOf<ManageManga?>(null) }
        val gridState = rememberLazyGridState()
        val performSearch: () -> Unit = {
            query = queryDraft.trim()
            type = if (query.isBlank()) MangaFetchType.POPULAR else MangaFetchType.SEARCH
        }

        val visibleManga: List<MangaDto> =
            if (DesktopSettings.hideInLibraryInBrowse) manga.filterNot { it.inLibrary } else manga.toList()

        suspend fun loadPage(p: Int, reset: Boolean) {
            loading = true
            runCatching {
                if (type == MangaFetchType.SEARCH && query.isBlank()) {
                    // empty search ⇒ fall back to popular listing
                    Suwayomi.client.fetchSourceManga(sourceId, MangaFetchType.POPULAR, p, null)
                } else {
                    Suwayomi.client.fetchSourceManga(sourceId, type, p, query.ifBlank { null })
                }
            }.onSuccess { result ->
                if (reset) manga.clear()
                manga.addAll(result.mangas)
                hasNext = result.hasNextPage
                page = p
            }.onFailure { errorMessage = it.message }
            loading = false
        }

        // (Re)load from scratch whenever the browse type or query changes.
        LaunchedEffect(type, query) {
            errorMessage = null
            manga.clear()
            page = 1
            hasNext = true
            loadPage(1, reset = false)
        }

        // Endless scroll: load the next page when near the end.
        LaunchedEffect(gridState) {
            snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                .distinctUntilChanged()
                .collect { lastVisible ->
                    if (!loading && hasNext && lastVisible >= visibleManga.size - 6 && visibleManga.isNotEmpty()) {
                        loadPage(page + 1, reset = false)
                    }
                }
        }

        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = { Text("$sourceName · ${sourceLang.uppercase()}") },
                        navigationIcon = {
                            IconButton(onClick = { navigator.pop() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                            }
                        },
                        actions = {
                            IconButton(onClick = {
                                DesktopSettings.updateSourceDisplayMode(
                                    if (DesktopSettings.sourceDisplayMode == SourceDisplayMode.GRID) SourceDisplayMode.COMPACT else SourceDisplayMode.GRID,
                                )
                            }) {
                                Icon(
                                    if (DesktopSettings.sourceDisplayMode == SourceDisplayMode.GRID) Icons.Filled.ViewList else Icons.Filled.GridView,
                                    contentDescription = "Modo de visualización",
                                )
                            }
                            IconButton(onClick = { showFilters = true }) {
                                Icon(Icons.Filled.Tune, contentDescription = "Filtros")
                            }
                        },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = queryDraft,
                            onValueChange = { queryDraft = it },
                            placeholder = { Text("Buscar…") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            trailingIcon = { IconButton(onClick = { performSearch() }) { Icon(Icons.Filled.Search, contentDescription = "Buscar") } },
                            singleLine = true,
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { performSearch() }),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                            modifier = Modifier.weight(1f),
                        )
                        FilterChip(
                            selected = type == MangaFetchType.POPULAR && query.isBlank(),
                            onClick = { query = ""; queryDraft = ""; type = MangaFetchType.POPULAR },
                            label = { Text("Populares") },
                        )
                        FilterChip(
                            selected = type == MangaFetchType.LATEST && query.isBlank(),
                            onClick = { query = ""; queryDraft = ""; type = MangaFetchType.LATEST },
                            label = { Text("Recientes") },
                        )
                    }
                }
            },
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                errorMessage?.let {
                    Text(
                        "Error: $it",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                if (visibleManga.isEmpty() && !loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (type == MangaFetchType.SEARCH && query.isNotBlank()) "Sin resultados."
                            else "Sin manga para mostrar.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (DesktopSettings.sourceDisplayMode == SourceDisplayMode.GRID) {
                    MangaCoverGrid(
                        manga = visibleManga,
                        state = gridState,
                        showInLibraryMark = true,
                        onLongClick = { m -> manageManga = ManageManga(m) },
                        onClick = { m -> navigator.push(MangaScreen(m.id, sourceId)) },
                    )
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(visibleManga, key = { it.id }) { m ->
                            ListItem(
                                modifier = Modifier.fillMaxWidth(),
                                leadingContent = {
                                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(6.dp))) {
                                        AsyncImage(
                                            model = Suwayomi.config.coverUrl(m.thumbnailUrl),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                },
                                headlineContent = { Text(m.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = {
                                    Text(
                                        if (m.inLibrary) "En biblioteca" else sourceName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                            )
                            HorizontalDivider()
                        }
                    }
                }

                if (loading && visibleManga.isNotEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
        }

        if (showFilters) {
            FiltersDialog(onDismiss = { showFilters = false })
        }

        manageManga?.let { mm ->
            ManageMangaDialog(
                manga = mm.manga,
                onDismiss = { manageManga = null },
                onToggled = { inLibrary ->
                    scope.launch { runCatching { Suwayomi.client.setInLibrary(mm.manga.id, inLibrary) } }
                    manageManga = null
                },
            )
        }
    }

    private data class ManageManga(val manga: MangaDto)
}

@Composable
private fun FiltersDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Hecho") } },
        title = { Text("Filtros") },
        text = {
            Column {
                FilterSwitchRow("Ocultar manga ya en biblioteca", DesktopSettings.hideInLibraryInBrowse) {
                    DesktopSettings.updateHideInLibraryInBrowse(it)
                }
                FilterSwitchRow("Mostrar fuentes NSFW", DesktopSettings.showNsfwSources) {
                    DesktopSettings.updateShowNsfwSources(it)
                }
            }
        },
    )
}

@Composable
private fun FilterSwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * Long-press dialog: when the manga is already in the library it offers removal, otherwise it adds
 * and (if categories exist) lets the user pick them — mirroring Mihon's add-favorite flow.
 */
@Composable
private fun ManageMangaDialog(
    manga: MangaDto,
    onDismiss: () -> Unit,
    onToggled: (Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val inLibrary = manga.inLibrary

    if (inLibrary) {
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = { onToggled(false) }) { Text("Quitar de la biblioteca") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
            title = { Text(manga.title) },
            text = { Text("¿Quitar \"${manga.title}\" de tu biblioteca?") },
        )
        return
    }

    var categories by remember { mutableStateOf<List<CategoryDto>>(emptyList()) }
    val selected = remember { mutableStateListOf<Int>() }
    LaunchedEffect(Unit) {
        runCatching { Suwayomi.client.getCategories() }.onSuccess { categories = it.filter { c -> !c.default } }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    Suwayomi.client.setInLibrary(manga.id, true)
                    if (selected.isNotEmpty()) {
                        Suwayomi.client.updateMangaCategories(manga.id, addTo = selected.toList(), removeFrom = emptyList())
                    }
                }
                onToggled(true)
            }) { Text("Añadir") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        title = { Text(manga.title) },
        text = {
            Column {
                Text("Añadir a la biblioteca.", style = MaterialTheme.typography.bodyMedium)
                if (categories.isNotEmpty()) {
                    Text("Categorías", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    categories.forEach { cat ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = cat.id in selected,
                                onCheckedChange = {
                                    if (it) selected.add(cat.id) else selected.remove(cat.id)
                                },
                            )
                            Text(cat.name)
                        }
                    }
                } else {
                    Text(
                        "Sin categorías; se añadirá a la categoría por defecto.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
    )
}
