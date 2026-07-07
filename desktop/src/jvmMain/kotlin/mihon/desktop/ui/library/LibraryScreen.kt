package mihon.desktop.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.launch
import mihon.desktop.bridge.CategoryDto
import mihon.desktop.bridge.MangaDto
import mihon.desktop.bridge.Suwayomi
import mihon.desktop.settings.DesktopSettings
import mihon.desktop.settings.LibraryDisplayMode
import mihon.desktop.settings.LibrarySort
import mihon.desktop.ui.common.ErrorBox
import mihon.desktop.ui.common.LoadState
import mihon.desktop.ui.common.LoadingBox
import mihon.desktop.ui.common.MangaCompactList
import mihon.desktop.ui.common.MangaCoverGrid
import mihon.desktop.ui.manga.MangaScreen

/**
 * The Library ("Biblioteca") tab content: favourites grouped by category, mirroring Mihon's
 * category tab bar + cover grid with unread/download badges. Adds a local search box, sort menu
 * (title / last read / date added), a grid/compact display toggle, per-category item counts, and a
 * synthetic "Todas" tab that lists every library manga.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen() {
    val navigator = LocalNavigator.currentOrThrow
    val scope = rememberCoroutineScope()
    var categories by remember { mutableStateOf<List<CategoryDto>>(emptyList()) }
    // selectedTab 0 == the synthetic "Todas" aggregate; category tabs follow at index 1..n.
    var selectedTab by remember { mutableStateOf(0) }
    var state by remember { mutableStateOf<LoadState<List<MangaDto>>>(LoadState.Loading) }
    var reloadKey by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var sortMenuOpen by remember { mutableStateOf(false) }

    // Per-category manga caches for counts + the "Todas" aggregate.
    var perCategory by remember { mutableStateOf<Map<Int, List<MangaDto>>>(emptyMap()) }

    LaunchedEffect(reloadKey) {
        runCatching { Suwayomi.client.getCategories() }
            .onSuccess { cats -> categories = cats.sortedBy { it.order } }
    }

    // Load the manga for the selected tab.
    LaunchedEffect(selectedTab, categories, reloadKey) {
        state = LoadState.Loading
        // Index 0 -> "Todas" (fetch everything by passing null); else the chosen category id.
        val catId = if (selectedTab == 0) null else categories.getOrNull(selectedTab - 1)?.id
        state = runCatching { Suwayomi.client.getLibraryManga(catId) }
            .fold({ LoadState.Success(it) }, { LoadState.Error(it.message ?: it.toString()) })
    }

    // Refresh per-category caches on each reload so counts stay accurate.
    LaunchedEffect(categories, reloadKey) {
        if (categories.isNotEmpty()) {
            val result = categories.associate { cat -> cat.id to (runCatching { Suwayomi.client.getLibraryManga(cat.id) }.getOrDefault(emptyList())) }
            perCategory = result
        }
    }

    /** Apply the sort + client-side query to the loaded list. */
    fun sortedAndFiltered(list: List<MangaDto>): List<MangaDto> {
        val sorted = when (DesktopSettings.librarySort) {
            LibrarySort.TITLE -> list.sortedBy { it.title.lowercase() }
            LibrarySort.LAST_READ -> list.sortedByDescending { it.lastReadAt ?: "0" }
            LibrarySort.DATE_ADDED -> list.sortedByDescending { it.inLibraryAt ?: "0" }
        }
        val q = query.trim()
        return if (q.isEmpty()) sorted else sorted.filter { it.title.contains(q, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Biblioteca") },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            runCatching { Suwayomi.client.updateLibrary() }
                            reloadKey++
                        }
                    }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Actualizar biblioteca")
                    }
                    IconButton(onClick = {
                        DesktopSettings.updateLibraryDisplay(
                            if (DesktopSettings.libraryDisplay == LibraryDisplayMode.GRID) LibraryDisplayMode.COMPACT else LibraryDisplayMode.GRID,
                        )
                    }) {
                        Icon(
                            if (DesktopSettings.libraryDisplay == LibraryDisplayMode.GRID) Icons.Outlined.GridView else Icons.Outlined.ViewList,
                            contentDescription = "Alternar vista (cuadrícula / compacto)",
                        )
                    }
                    // Sort menu.
                    Box {
                        IconButton(onClick = { sortMenuOpen = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Ordenar biblioteca")
                        }
                        DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                            LibrarySort.entries.forEach { s ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            when (s) {
                                                LibrarySort.TITLE -> "Por título"
                                                LibrarySort.LAST_READ -> "Último leído"
                                                LibrarySort.DATE_ADDED -> "Fecha de añadir"
                                            },
                                        )
                                    },
                                    onClick = {
                                        DesktopSettings.updateLibrarySort(s)
                                        sortMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Local search box (applies to the currently selected tab list).
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Buscar en la biblioteca…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            )

            // Tab row: leading "Todas" + one tab per category (with item count).
            val tabCount = categories.size + 1
            if (tabCount > 1) {
                ScrollableTabRow(selectedTabIndex = selectedTab.coerceIn(0, tabCount - 1), edgePadding = 8.dp) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Todas") })
                    categories.forEachIndexed { index, cat ->
                        val count = perCategory[cat.id]?.size ?: 0
                        Tab(
                            selected = selectedTab == index + 1,
                            onClick = { selectedTab = index + 1 },
                            text = { Text(if (count > 0) "${cat.name} ($count)" else cat.name) },
                        )
                    }
                }
            }

            when (val s = state) {
                is LoadState.Loading -> LoadingBox()
                is LoadState.Error -> ErrorBox(s.message)
                is LoadState.Success -> {
                    val items = sortedAndFiltered(s.value)
                    if (items.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                if (query.isNotBlank()) "Sin resultados para \"$query\"."
                                else "Tu biblioteca está vacía.\nAñade manga desde Explorar.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        val onClick: (MangaDto) -> Unit = { m -> navigator.push(MangaScreen(m.id, m.sourceId ?: "")) }
                        if (DesktopSettings.libraryDisplay == LibraryDisplayMode.GRID) {
                            MangaCoverGrid(manga = items, showBadges = true, onClick = onClick)
                        } else {
                            MangaCompactList(manga = items, onClick = onClick)
                        }
                    }
                }
            }
        }
    }
}
