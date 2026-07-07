package mihon.desktop.ui.browse

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import mihon.desktop.bridge.SourceDto
import mihon.desktop.bridge.Suwayomi
import mihon.desktop.settings.DesktopSettings
import mihon.desktop.ui.common.ErrorBox
import mihon.desktop.ui.common.LoadState
import mihon.desktop.ui.common.LoadingBox

/**
 * Lists the installed sources, grouped by language with pinned / last-used sections at the top —
 * mirroring Mihon's Sources tab. Long-press a source to pin/unpin it.
 */
object SourcesScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        var state by remember { mutableStateOf<LoadState<List<SourceDto>>>(LoadState.Loading) }
        var query by remember { mutableStateOf("") }
        var reloadKey by remember { mutableStateOf(0) }
        var optionsFor by remember { mutableStateOf<SourceDto?>(null) }

        LaunchedEffect(reloadKey) {
            state = runCatching { Suwayomi.client.getSources() }
                .fold({ LoadState.Success(it) }, { LoadState.Error(it.message ?: it.toString()) })
        }

        Scaffold(
            topBar = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Buscar fuentes…") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            },
        ) { padding ->
            when (val s = state) {
                is LoadState.Loading -> LoadingBox()
                is LoadState.Error -> ErrorBox(s.message)
                is LoadState.Success -> {
                    val models = groupSources(s.value, query, DesktopSettings.showNsfwSources)
                    if (models.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                            Text(
                                if (query.isNotBlank()) "Sin fuentes que coincidan." else "Sin fuentes. Instala extensiones en Más → Extensiones.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                            items(models, key = {
                                when (it) {
                                    is SourceUiModel.Header -> "header-${it.key}"
                                    is SourceUiModel.Item -> "source-${it.source.id}"
                                }
                            }) { model ->
                                when (model) {
                                    is SourceUiModel.Header -> SourceHeader(model.key)
                                    is SourceUiModel.Item -> SourceRow(
                                        source = model.source,
                                        onClick = {
                                            DesktopSettings.markLastUsedSource(model.source.id)
                                            navigator.push(BrowseSourceScreen(model.source.id, model.source.name, model.source.lang))
                                        },
                                        onLongClick = { optionsFor = model.source },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        optionsFor?.let { source ->
            SourceOptionsDialog(
                source = source,
                onPin = {
                    DesktopSettings.toggleSourcePin(source.id)
                    optionsFor = null
                    reloadKey++
                },
                onOpen = {
                    optionsFor = null
                    DesktopSettings.markLastUsedSource(source.id)
                    navigator.push(GlobalSearchScreen(initialExtensionFilter = source.id))
                },
                onDismiss = { optionsFor = null },
            )
        }
    }

    @Composable
    private fun SourceHeader(key: String) {
        Text(
            text = sourceGroupLabel(key),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun SourceRow(source: SourceDto, onClick: () -> Unit, onLongClick: () -> Unit) {
        ListItem(
            modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
            leadingContent = {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))) {
                    AsyncImage(
                        model = Suwayomi.config.coverUrl(source.iconUrl),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            },
            headlineContent = { Text(source.name, fontWeight = FontWeight.Medium) },
            supportingContent = {
                Text(
                    source.lang.uppercase() + (if (source.isNsfw) " · 18+" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    if (source.supportsLatest) {
                        TextButton(onClick = onClick) { Text("Recientes", color = MaterialTheme.colorScheme.primary) }
                    }
                    IconButton(onClick = { DesktopSettings.toggleSourcePin(source.id) }) {
                        Icon(
                            if (source.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (source.isPinned) "Desfijar" else "Fijar",
                            tint = if (source.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
        )
        HorizontalDivider()
    }
}

@Composable
private fun SourceOptionsDialog(
    source: SourceDto,
    onPin: () -> Unit,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
        title = { Text(source.name) },
        text = {
            androidx.compose.foundation.layout.Column {
                Text(
                    if (source.isPinned) "Quitar de fijadas" else "Fijar fuente",
                    modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onPin).padding(vertical = 12.dp),
                )
                Text(
                    "Buscar en esta fuente…",
                    modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onOpen).padding(vertical = 12.dp),
                )
            }
        },
    )
}
