package mihon.desktop.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import mihon.desktop.bridge.ExtensionDto
import mihon.desktop.bridge.Suwayomi
import mihon.desktop.ui.common.ErrorBox
import mihon.desktop.ui.common.LoadState
import mihon.desktop.ui.common.LoadingBox

/** Extension management: browse the repos, install / update / uninstall, and manage repositories. */
object ExtensionsScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        var state by remember { mutableStateOf<LoadState<List<ExtensionDto>>>(LoadState.Loading) }
        var query by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf<String?>(null) }
        var reloadKey by remember { mutableStateOf(0) }
        var showRepos by remember { mutableStateOf(false) }

        suspend fun reload() {
            state = runCatching { Suwayomi.client.getExtensions() }
                .fold({ LoadState.Success(it) }, { LoadState.Error(it.message ?: it.toString()) })
        }

        LaunchedEffect(reloadKey) { reload() }

        fun act(pkg: String, block: suspend () -> Unit) {
            scope.launch {
                busy = pkg
                runCatching { block() }
                runCatching { reload() }
                busy = null
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Extensiones") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                        }
                    },
                    actions = {
                        TextButton(onClick = { showRepos = true }) { Text("Repos") }
                        TextButton(onClick = {
                            scope.launch {
                                state = LoadState.Loading
                                runCatching { Suwayomi.client.fetchExtensions() }
                                reload()
                            }
                        }) { Text("Refrescar") }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Buscar extensión") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                )
                when (val s = state) {
                    is LoadState.Loading -> LoadingBox()
                    is LoadState.Error -> ErrorBox(s.message)
                    is LoadState.Success -> {
                        val filtered = s.value
                            .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
                            .sortedWith(compareByDescending<ExtensionDto> { it.hasUpdate }
                                .thenByDescending { it.isInstalled }
                                .thenBy { it.name.lowercase() })
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(filtered, key = { it.pkgName }) { ext ->
                                ExtensionRow(
                                    ext = ext,
                                    busy = busy == ext.pkgName,
                                    onInstall = { act(ext.pkgName) { Suwayomi.client.installExtension(ext.pkgName) } },
                                    onUpdate = { act(ext.pkgName) { Suwayomi.client.updateExtension(ext.pkgName) } },
                                    onUninstall = { act(ext.pkgName) { Suwayomi.client.uninstallExtension(ext.pkgName) } },
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }

        if (showRepos) {
            RepoDialog(onDismiss = { showRepos = false; reloadKey++ })
        }
    }
}

@Composable
private fun ExtensionRow(
    ext: ExtensionDto,
    busy: Boolean,
    onInstall: () -> Unit,
    onUpdate: () -> Unit,
    onUninstall: () -> Unit,
) {
    ListItem(
        leadingContent = {
            Box(Modifier.size(40.dp)) {
                AsyncImage(
                    model = Suwayomi.config.coverUrl(ext.iconUrl),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        },
        headlineContent = { Text(ext.name, fontWeight = FontWeight.Medium) },
        supportingContent = {
            Text(
                "${ext.lang.uppercase()} · v${ext.versionName}" + if (ext.isObsolete) " · obsoleta" else "",
                style = MaterialTheme.typography.bodySmall,
            )
        },
        trailingContent = {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    when {
                        ext.hasUpdate -> Button(onClick = onUpdate) { Text("Actualizar") }
                        !ext.isInstalled -> Button(onClick = onInstall) { Text("Instalar") }
                    }
                    if (ext.isInstalled) {
                        IconButton(onClick = onUninstall) {
                            Icon(Icons.Filled.Delete, contentDescription = "Desinstalar")
                        }
                    }
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepoDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var repos by remember { mutableStateOf<List<String>>(emptyList()) }
    var newUrl by remember { mutableStateOf("") }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey) {
        runCatching { Suwayomi.client.getExtensionStores() }
            .onSuccess { repos = it.map { s -> s.indexUrl } }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
        title = { Text("Repositorios de extensiones") },
        text = {
            Column {
                repos.forEach { url ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(url, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 2)
                        IconButton(onClick = {
                            scope.launch { runCatching { Suwayomi.client.removeExtensionStore(url) }; reloadKey++ }
                        }) { Icon(Icons.Filled.Delete, contentDescription = "Quitar") }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newUrl,
                    onValueChange = { newUrl = it },
                    label = { Text("URL del índice (index.min.json)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        val url = newUrl.trim()
                        if (url.isNotEmpty()) {
                            scope.launch {
                                runCatching { Suwayomi.client.addExtensionStore(url) }
                                runCatching { Suwayomi.client.fetchExtensions() }
                                newUrl = ""
                                reloadKey++
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Añadir repositorio") }
            }
        },
    )
}
