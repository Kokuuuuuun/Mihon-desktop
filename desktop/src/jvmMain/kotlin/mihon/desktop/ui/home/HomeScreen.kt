package mihon.desktop.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.util.fastForEach
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import mihon.desktop.ui.tabs.BrowseTab
import mihon.desktop.ui.tabs.HistoryTab
import mihon.desktop.ui.tabs.LibraryTab
import mihon.desktop.ui.tabs.MoreTab
import mihon.desktop.ui.tabs.UpdatesTab

/**
 * Desktop root screen. Mirrors Mihon's HomeScreen: a Voyager [TabNavigator] over the five tabs,
 * with a left [NavigationRail] (the desktop-appropriate equivalent of Mihon's tablet layout).
 */
object HomeScreen : Screen {

    private val TABS: List<Tab> = listOf(
        LibraryTab,
        UpdatesTab,
        HistoryTab,
        BrowseTab,
        MoreTab,
    )

    // Debug: preselect a tab for verification via MIHON_TAB env var or a `debug-tab` file in the
    // data dir (the file wins; env var is a fallback). Values: library|updates|history|browse|more.
    private val initialTab: Tab = run {
        val fromFile = runCatching {
            java.io.File(mihon.desktop.bridge.SuwayomiServerManager.dataDir, "debug-tab")
                .takeIf { it.isFile }?.readText()?.trim()
        }.getOrNull()
        when (fromFile ?: System.getenv("MIHON_TAB")) {
            "library" -> LibraryTab
            "updates" -> UpdatesTab
            "history" -> HistoryTab
            "more" -> MoreTab
            else -> BrowseTab
        }
    }

    @Composable
    override fun Content() {
        TabNavigator(tab = initialTab, key = "HomeTabs") { tabNavigator ->
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(modifier = Modifier.fillMaxHeight()) {
                    TABS.fastForEach { tab -> RailItem(tab) }
                }
                AnimatedContent(
                    targetState = tabNavigator.current,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
                    },
                    modifier = Modifier.weight(1f),
                    label = "tabContent",
                ) { tab ->
                    tabNavigator.saveableState(key = "currentTab", tab) {
                        tab.Content()
                    }
                }
            }
        }
    }
}

@Composable
private fun RailItem(tab: Tab) {
    val tabNavigator = LocalTabNavigator.current
    val selected = tabNavigator.current::class == tab::class
    val options = tab.options
    NavigationRailItem(
        selected = selected,
        onClick = { tabNavigator.current = tab },
        icon = {
            val painter = options.icon
            if (painter != null) Icon(painter = painter, contentDescription = options.title)
        },
        label = { Text(options.title) },
        alwaysShowLabel = true,
    )
}
