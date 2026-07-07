package mihon.desktop.ui.tabs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import mihon.desktop.bridge.SuwayomiServerManager
import mihon.desktop.ui.browse.BrowseSourceScreen
import mihon.desktop.ui.browse.ExtensionsScreen
import mihon.desktop.ui.browse.SourcesScreen
import mihon.desktop.ui.downloads.DownloadsScreen
import mihon.desktop.ui.history.HistoryScreen
import mihon.desktop.ui.library.LibraryScreen
import mihon.desktop.ui.manga.MangaScreen
import mihon.desktop.ui.more.MoreScreen
import mihon.desktop.ui.more.SettingsScreen
import mihon.desktop.ui.more.TrackingScreen
import mihon.desktop.ui.reader.ReaderScreen
import mihon.desktop.ui.updates.UpdatesScreen

/** Reads an optional `debug-more` file in the data dir to deep-link a More sub-screen for verification. */
private fun moreInitialStack(): List<Screen> = when (
    runCatching { java.io.File(SuwayomiServerManager.dataDir, "debug-more").takeIf { it.isFile }?.readText()?.trim() }.getOrNull()
) {
    "extensions" -> listOf(MoreScreen, ExtensionsScreen)
    "downloads" -> listOf(MoreScreen, DownloadsScreen)
    "settings" -> listOf(MoreScreen, SettingsScreen)
    "tracking" -> listOf(MoreScreen, TrackingScreen)
    else -> listOf(MoreScreen)
}

/**
 * Desktop tabs mirroring Mihon's HomeScreen tab set (Library / Updates / History / Browse / More).
 * Each tab hosts its own Voyager [Navigator] so detail screens (manga, reader, extensions, …) push
 * on top of the tab's own back stack, exactly like the Android app.
 */

// Root screens wrapping the tab composables so they can live inside a Navigator.
private object LibraryRootScreen : Screen {
    @Composable override fun Content() = LibraryScreen()
}
private object UpdatesRootScreen : Screen {
    @Composable override fun Content() = UpdatesScreen()
}
private object HistoryRootScreen : Screen {
    @Composable override fun Content() = HistoryScreen()
}

object LibraryTab : Tab {
    override val options: TabOptions
        @Composable get() {
            val icon = rememberVectorPainter(Icons.AutoMirrored.Outlined.LibraryBooks)
            return remember(icon) { TabOptions(index = 0u, title = "Biblioteca", icon = icon) }
        }

    @Composable
    override fun Content() = Navigator(LibraryRootScreen)
}

object UpdatesTab : Tab {
    override val options: TabOptions
        @Composable get() {
            val icon = rememberVectorPainter(Icons.Outlined.NewReleases)
            return remember(icon) { TabOptions(index = 1u, title = "Novedades", icon = icon) }
        }

    @Composable
    override fun Content() = Navigator(UpdatesRootScreen)
}

object HistoryTab : Tab {
    override val options: TabOptions
        @Composable get() {
            val icon = rememberVectorPainter(Icons.Outlined.History)
            return remember(icon) { TabOptions(index = 2u, title = "Historial", icon = icon) }
        }

    @Composable
    override fun Content() = Navigator(HistoryRootScreen)
}

object BrowseTab : Tab {
    override val options: TabOptions
        @Composable get() {
            val icon = rememberVectorPainter(Icons.Outlined.Explore)
            return remember(icon) { TabOptions(index = 3u, title = "Explorar", icon = icon) }
        }

    @Composable
    override fun Content() = Navigator(debugInitialStack())
}

// Debug deep-link: set MIHON_DEBUG=grid|manga|reader to preseed the Browse back stack for
// visual verification without a pointer. Defaults to the normal sources list.
private fun debugInitialStack(): List<Screen> {
    val enMangaDex = "2499283573021220255"
    return when (System.getenv("MIHON_DEBUG")) {
        "grid" -> listOf(SourcesScreen, BrowseSourceScreen(enMangaDex, "MangaDex", "en"))
        "manga" -> listOf(SourcesScreen, MangaScreen(3L, enMangaDex))
        "reader" -> listOf(SourcesScreen, ReaderScreen(3L, 1L, "The Eminence in Shadow", "Chapter 1"))
        else -> listOf(SourcesScreen)
    }
}

object MoreTab : Tab {
    override val options: TabOptions
        @Composable get() {
            val icon = rememberVectorPainter(Icons.Outlined.MoreHoriz)
            return remember(icon) { TabOptions(index = 4u, title = "Más", icon = icon) }
        }

    @Composable
    override fun Content() = Navigator(moreInitialStack())
}
