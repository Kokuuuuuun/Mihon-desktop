package mihon.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.isTraySupported
import androidx.compose.ui.window.rememberWindowState
import cafe.adriel.voyager.navigator.Navigator
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import eu.kanade.presentation.theme.TachiyomiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.desktop.bridge.Suwayomi
import mihon.desktop.bridge.SuwayomiConfig
import mihon.desktop.bridge.SuwayomiServerManager
import mihon.desktop.settings.DesktopSettings
import mihon.desktop.ui.home.HomeScreen

private enum class Boot { STARTING, READY, FAILED }

fun main() {
    // Configure Coil once: load Suwayomi cover/page images over OkHttp.
    SingletonImageLoader.setSafe { context ->
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .crossfade(true)
            .build()
    }

    DesktopSettings.load()
    Suwayomi.configure(SuwayomiConfig(baseUrl = DesktopSettings.baseUrl))

    application {
        var boot by remember { mutableStateOf(Boot.STARTING) }
        val log = remember { mutableStateListOf<String>() }
        var windowVisible by remember { mutableStateOf(true) }
        val windowState = rememberWindowState()

        // Start (or reuse) the bundled Suwayomi-Server, then reveal the app.
        LaunchedEffect(Unit) {
            val ok = withContext(Dispatchers.IO) {
                SuwayomiServerManager.ensureRunning(baseUrl = DesktopSettings.baseUrl) { line ->
                    synchronized(log) { log.add(line); if (log.size > 200) log.removeAt(0) }
                }
            }
            boot = if (ok) Boot.READY else Boot.FAILED
        }

        // System tray: keep the app resident and reopenable while background sync runs.
        // Tray support is platform-dependent (e.g. unavailable on headless Linux / CI). Only
        // register one when the runtime reports it as supported, otherwise the Tray() composable
        // throws "Tray is not supported on the current platform".
        val trayIcon = rememberVectorPainter(Icons.AutoMirrored.Filled.MenuBook)
        if (isTraySupported) {
            Tray(
                icon = trayIcon,
                tooltip = "Mihon Desktop",
                onAction = { windowVisible = true },
                menu = {
                    Item("Abrir Mihon", onClick = { windowVisible = true })
                    Item("Salir", onClick = {
                        SuwayomiServerManager.stop()
                        exitApplication()
                    })
                },
            )
        }

        Window(
            onCloseRequest = {
                if (DesktopSettings.minimizeToTray) {
                    windowVisible = false
                } else {
                    SuwayomiServerManager.stop()
                    exitApplication()
                }
            },
            visible = windowVisible,
            state = windowState,
            title = "Mihon Desktop",
        ) {
            TachiyomiTheme(appTheme = DesktopSettings.appTheme, isAmoled = DesktopSettings.isAmoled) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when (boot) {
                        Boot.STARTING -> StartupSplash(log)
                        Boot.FAILED -> StartupFailed(log, onRetry = { boot = Boot.STARTING })
                        Boot.READY -> Navigator(screen = HomeScreen)
                    }
                }
            }
        }
    }
}

@Composable
private fun StartupSplash(log: List<String>) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(
            "Iniciando Suwayomi-Server…",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        LogTail(log)
    }
}

@Composable
private fun StartupFailed(log: List<String>, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "No se pudo iniciar Suwayomi-Server.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) { Text("Reintentar") }
        LogTail(log)
    }
}

@Composable
private fun LogTail(log: List<String>) {
    Column(
        modifier = Modifier
            .padding(top = 16.dp)
            .width(560.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        log.takeLast(12).forEach {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
