package mihon.desktop.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import mihon.desktop.bridge.SuwayomiServerManager
import mihon.desktop.ui.browse.ExtensionsScreen
import mihon.desktop.ui.downloads.DownloadsScreen

/** The "Más" tab: entry points to extensions, downloads, tracking, settings and app info. */
object MoreScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        Scaffold(topBar = { TopAppBar(title = { Text("Más") }) }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                MoreItem(Icons.Filled.Extension, "Extensiones", "Instalar y actualizar fuentes") {
                    navigator.push(ExtensionsScreen)
                }
                HorizontalDivider()
                MoreItem(Icons.Filled.Download, "Descargas", "Cola y capítulos descargados") {
                    navigator.push(DownloadsScreen)
                }
                HorizontalDivider()
                MoreItem(Icons.Filled.Sync, "Seguimiento", "MyAnimeList, AniList y más") {
                    navigator.push(TrackingScreen)
                }
                HorizontalDivider()
                MoreItem(Icons.Filled.Settings, "Ajustes", "Tema, lector y servidor") {
                    navigator.push(SettingsScreen)
                }
                HorizontalDivider()
                MoreItem(
                    Icons.Filled.Info,
                    "Acerca de",
                    "Cliente de Suwayomi-Server · datos en ${SuwayomiServerManager.dataDir.absolutePath}",
                ) {}
            }
        }
    }

    @Composable
    private fun MoreItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
        ListItem(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
            leadingContent = { Icon(icon, contentDescription = null) },
            headlineContent = { Text(title) },
            supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        )
    }
}
