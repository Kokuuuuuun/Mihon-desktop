package mihon.desktop.ui.more

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import eu.kanade.domain.ui.model.AppTheme
import kotlinx.coroutines.launch
import mihon.desktop.bridge.CategoryDto
import mihon.desktop.bridge.ServerInfoDto
import mihon.desktop.bridge.Suwayomi
import mihon.desktop.bridge.SuwayomiConfig
import mihon.desktop.settings.DesktopSettings
import mihon.desktop.settings.LibraryDisplayMode
import mihon.desktop.settings.LibrarySort

/** Desktop preferences grouped into collapsible, card-separated sections (Mihon's settings layout). */
object SettingsScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        var urlDraft by remember { mutableStateOf(DesktopSettings.baseUrl) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Ajustes") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // General
                SettingsSection("General", defaultOpen = false) {
                    SwitchRow("Minimizar a la bandeja al cerrar", DesktopSettings.minimizeToTray) {
                        DesktopSettings.updateMinimizeToTray(it)
                    }
                    DividerThin()
                    SwitchRow("Mostrar fuentes NSFW", DesktopSettings.showNsfwSources) {
                        DesktopSettings.updateShowNsfwSources(it)
                    }
                    DividerThin()
                    SwitchRow("Ocultar manga ya en biblioteca al explorar", DesktopSettings.hideInLibraryInBrowse) {
                        DesktopSettings.updateHideInLibraryInBrowse(it)
                    }
                }

                // Apariencia
                SettingsSection("Apariencia", defaultOpen = true) {
                    SettingsLabel("Tema")
                    AppTheme.entries.forEach { theme ->
                        RadioRow(
                            label = theme.label,
                            selected = DesktopSettings.appTheme == theme,
                            onSelect = { DesktopSettings.setTheme(theme) },
                        )
                    }
                    DividerThin()
                    SwitchRow("Modo AMOLED (negros puros)", DesktopSettings.isAmoled) {
                        DesktopSettings.setTheme(DesktopSettings.appTheme, it)
                    }
                    DividerThin()
                    SettingsLabel("Biblioteca — orden por defecto")
                    LibrarySort.entries.forEach { s ->
                        RadioRow(
                            label = when (s) {
                                LibrarySort.TITLE -> "Por título"
                                LibrarySort.LAST_READ -> "Último leído"
                                LibrarySort.DATE_ADDED -> "Fecha de añadir"
                            },
                            selected = DesktopSettings.librarySort == s,
                            onSelect = { DesktopSettings.updateLibrarySort(s) },
                        )
                    }
                    DividerThin()
                    SettingsLabel("Biblioteca — vista por defecto")
                    LibraryDisplayMode.entries.forEach { m ->
                        RadioRow(
                            label = when (m) {
                                LibraryDisplayMode.GRID -> "Cuadrículas"
                                LibraryDisplayMode.COMPACT -> "Compacto"
                            },
                            selected = DesktopSettings.libraryDisplay == m,
                            onSelect = { DesktopSettings.updateLibraryDisplay(m) },
                        )
                    }
                }

                // Lector
                SettingsSection("Lector", defaultOpen = true) {
                    SwitchRow("Modo webtoon por defecto", DesktopSettings.defaultWebtoon) {
                        DesktopSettings.updateDefaultWebtoon(it)
                    }
                    DividerThin()
                    SwitchRow("Mantener pantalla encendida", DesktopSettings.keepScreenAwake) {
                        DesktopSettings.updateKeepScreenAwake(it)
                    }
                    DividerThin()
                    SettingsLabel("Zoom inicial por defecto")
                    Slider(
                        value = DesktopSettings.defaultZoom,
                        onValueChange = { DesktopSettings.updateDefaultZoom(it) },
                        valueRange = 1f..5f,
                        steps = 7,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DividerThin()
                    SettingsLabel("Espaciado entre paneles (webtoon): ${DesktopSettings.webtoonPadding} dp")
                    Slider(
                        value = DesktopSettings.webtoonPadding.toFloat(),
                        onValueChange = { DesktopSettings.updateWebtoonPadding(it.toInt()) },
                        valueRange = 0f..48f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Servidor
                SettingsSection("Servidor", defaultOpen = true) {
                    ServerInfoPanel()
                    DividerThin()
                    SettingsLabel("Conexión")
                    OutlinedTextField(
                        value = urlDraft,
                        onValueChange = { urlDraft = it },
                        label = { Text("URL del servidor") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            DesktopSettings.updateBaseUrl(urlDraft)
                            Suwayomi.configure(SuwayomiConfig(baseUrl = urlDraft.trimEnd('/')))
                        }) { Text("Guardar y reconectar") }
                        OutlinedButton(onClick = {
                            urlDraft = SuwayomiConfig.DEFAULT_BASE_URL
                            DesktopSettings.updateBaseUrl(urlDraft)
                            Suwayomi.configure(SuwayomiConfig())
                        }) { Text("Restablecer") }
                    }
                }

                // Datos
                SettingsSection("Datos", defaultOpen = false) {
                    CategoryManagementPanel()
                }
            }
        }
    }
}

/**
 * A collapsible settings section rendered as a Material [Card]: a clickable header row (title +
 * expand/contract icon) followed by the section body when open. The card gives each group a clear
 * visual boundary and consistent vertical spacing so controls don't run together.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSection(title: String, defaultOpen: Boolean = false, content: @Composable () -> Unit) {
    var open by remember(title) { mutableStateOf(defaultOpen) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (open) "Contraer" else "Expandir",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = open) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DividerThin() {
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Fetches and displays the Suwayomi-Server version/build info. */
@Composable
private fun ServerInfoPanel() {
    var info by remember { mutableStateOf<ServerInfoDto?>(null) }
    LaunchedEffect(Unit) {
        runCatching { Suwayomi.client.getServerInfo() }.onSuccess { info = it }
    }
    Column {
        SettingsLabel("Servidor Suwayomi")
        val i = info
        if (i != null) {
            InfoLine("Versión", i.version)
            InfoLine("Build", i.buildType)
            InfoLine("Compilado", i.serverBuildTime)
        } else {
            Text("Recuperando información del servidor…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    val v = value.takeIf { it.isNotBlank() } ?: "—"
    Text(
        "$label: $v",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(vertical = 1.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Create / delete categories. Mirrors Mihon's "Categorías" management page in a compact inline form.
 */
@Composable
private fun CategoryManagementPanel() {
    val scope = rememberCoroutineScope()
    var categories by remember { mutableStateOf<List<CategoryDto>>(emptyList()) }
    var newName by remember { mutableStateOf("") }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey) {
        runCatching { Suwayomi.client.getCategories() }.onSuccess { categories = it.sortedBy { c -> c.order } }
    }

    Column {
        SettingsLabel("Categorías")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                singleLine = true,
                label = { Text("Nueva categoría") },
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                val n = newName.trim()
                if (n.isNotEmpty()) {
                    scope.launch {
                        runCatching { Suwayomi.client.createCategory(n) }
                        newName = ""
                        reloadKey++
                    }
                }
            }) { Icon(Icons.Filled.Add, contentDescription = "Crear categoría") }
        }
        if (categories.isEmpty()) {
            Text(
                "Sin categorías. Crea una para organizar tu biblioteca.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            Spacer(Modifier.height(8.dp))
            categories.forEach { cat ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(cat.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = {
                        scope.launch {
                            runCatching { Suwayomi.client.deleteCategory(cat.id) }
                            reloadKey++
                        }
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Eliminar categoría")
                    }
                }
            }
        }
    }
}
