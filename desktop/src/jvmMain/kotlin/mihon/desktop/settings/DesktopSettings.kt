package mihon.desktop.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.kanade.domain.ui.model.AppTheme
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mihon.desktop.bridge.SuwayomiConfig
import mihon.desktop.bridge.SuwayomiServerManager
import java.io.File

/** Persisted form of the desktop preferences. */
@Serializable
private data class SettingsSnapshot(
    val appTheme: String = AppTheme.DEFAULT.name,
    val isAmoled: Boolean = false,
    val baseUrl: String = SuwayomiConfig.DEFAULT_BASE_URL,
    val defaultWebtoon: Boolean = false,
    val minimizeToTray: Boolean = true,
    val pinnedSources: Set<String> = emptySet(),
    val lastUsedSource: String? = null,
    val sourceDisplayMode: String = SourceDisplayMode.GRID.name,
    val hideInLibraryInBrowse: Boolean = false,
    val showNsfwSources: Boolean = true,
    val defaultZoom: Float = 1f,
    val webtoonPadding: Int = 0,
    val keepScreenAwake: Boolean = true,
    val librarySort: String = LibrarySort.TITLE.name,
    val libraryDisplay: String = LibraryDisplayMode.GRID.name,
    val chaptersSortAsc: Boolean = false,
)

/** How the per-source catalogue grid is rendered. */
enum class SourceDisplayMode { GRID, COMPACT }

/** Library ordering options. */
enum class LibrarySort { TITLE, LAST_READ, DATE_ADDED }

/** Library display modes — cover grid vs. compact list. */
enum class LibraryDisplayMode { GRID, COMPACT }

/**
 * Process-wide desktop preferences, backed by a JSON file in the app data dir. Exposes Compose
 * state so the UI (theme, reader defaults) recomposes when a setting changes. A thin stand-in for
 * Mihon's Preferences/DataStore on Android.
 */
object DesktopSettings {

    private val file: File by lazy { File(SuwayomiServerManager.dataDir, "mihon-desktop.json") }
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    var appTheme: AppTheme by mutableStateOf(AppTheme.DEFAULT)
        private set
    var isAmoled: Boolean by mutableStateOf(false)
        private set
    var baseUrl: String by mutableStateOf(SuwayomiConfig.DEFAULT_BASE_URL)
        private set
    var defaultWebtoon: Boolean by mutableStateOf(false)
        private set
    var minimizeToTray: Boolean by mutableStateOf(true)
        private set
    var pinnedSources: Set<String> by mutableStateOf(emptySet())
        private set
    var lastUsedSource: String? by mutableStateOf(null)
        private set
    var sourceDisplayMode: SourceDisplayMode by mutableStateOf(SourceDisplayMode.GRID)
        private set
    var hideInLibraryInBrowse: Boolean by mutableStateOf(false)
        private set
    var showNsfwSources: Boolean by mutableStateOf(true)
        private set
    var defaultZoom: Float by mutableStateOf(1f)
        private set
    var webtoonPadding: Int by mutableStateOf(0)
        private set
    var keepScreenAwake: Boolean by mutableStateOf(true)
        private set
    var librarySort: LibrarySort by mutableStateOf(LibrarySort.TITLE)
        private set
    var libraryDisplay: LibraryDisplayMode by mutableStateOf(LibraryDisplayMode.GRID)
        private set
    var chaptersSortAsc: Boolean by mutableStateOf(false)
        private set

    fun load() {
        val snap = runCatching {
            if (file.isFile) json.decodeFromString(SettingsSnapshot.serializer(), file.readText()) else null
        }.getOrNull() ?: SettingsSnapshot()
        appTheme = runCatching { AppTheme.valueOf(snap.appTheme) }.getOrDefault(AppTheme.DEFAULT)
        isAmoled = snap.isAmoled
        baseUrl = snap.baseUrl
        defaultWebtoon = snap.defaultWebtoon
        minimizeToTray = snap.minimizeToTray
        pinnedSources = snap.pinnedSources
        lastUsedSource = snap.lastUsedSource
        sourceDisplayMode = runCatching { SourceDisplayMode.valueOf(snap.sourceDisplayMode) }.getOrDefault(SourceDisplayMode.GRID)
        hideInLibraryInBrowse = snap.hideInLibraryInBrowse
        showNsfwSources = snap.showNsfwSources
        defaultZoom = snap.defaultZoom.coerceIn(1f, 5f)
        webtoonPadding = snap.webtoonPadding.coerceIn(0, 48)
        keepScreenAwake = snap.keepScreenAwake
        librarySort = runCatching { LibrarySort.valueOf(snap.librarySort) }.getOrDefault(LibrarySort.TITLE)
        libraryDisplay = runCatching { LibraryDisplayMode.valueOf(snap.libraryDisplay) }.getOrDefault(LibraryDisplayMode.GRID)
        chaptersSortAsc = snap.chaptersSortAsc
    }

    fun setTheme(theme: AppTheme, amoled: Boolean = isAmoled) {
        appTheme = theme
        isAmoled = amoled
        persist()
    }

    fun updateBaseUrl(url: String) { baseUrl = url.trimEnd('/'); persist() }
    fun updateDefaultWebtoon(value: Boolean) { defaultWebtoon = value; persist() }
    fun updateMinimizeToTray(value: Boolean) { minimizeToTray = value; persist() }

    fun toggleSourcePin(sourceId: String) {
        pinnedSources = if (sourceId in pinnedSources) pinnedSources - sourceId else pinnedSources + sourceId
        persist()
    }

    fun markLastUsedSource(sourceId: String) {
        lastUsedSource = sourceId
        persist()
    }

    fun updateSourceDisplayMode(mode: SourceDisplayMode) { sourceDisplayMode = mode; persist() }
    fun updateHideInLibraryInBrowse(value: Boolean) { hideInLibraryInBrowse = value; persist() }
    fun updateShowNsfwSources(value: Boolean) { showNsfwSources = value; persist() }
    fun updateDefaultZoom(value: Float) { defaultZoom = value.coerceIn(1f, 5f); persist() }
    fun updateWebtoonPadding(value: Int) { webtoonPadding = value.coerceIn(0, 48); persist() }
    fun updateKeepScreenAwake(value: Boolean) { keepScreenAwake = value; persist() }
    fun updateLibrarySort(value: LibrarySort) { librarySort = value; persist() }
    fun updateLibraryDisplay(value: LibraryDisplayMode) { libraryDisplay = value; persist() }
    fun updateChaptersSortAsc(value: Boolean) { chaptersSortAsc = value; persist() }

    private fun persist() {
        runCatching {
            file.writeText(
                json.encodeToString(
                    SettingsSnapshot.serializer(),
                    SettingsSnapshot(
                        appTheme = appTheme.name,
                        isAmoled = isAmoled,
                        baseUrl = baseUrl,
                        defaultWebtoon = defaultWebtoon,
                        minimizeToTray = minimizeToTray,
                        pinnedSources = pinnedSources,
                        lastUsedSource = lastUsedSource,
                        sourceDisplayMode = sourceDisplayMode.name,
                        hideInLibraryInBrowse = hideInLibraryInBrowse,
                        showNsfwSources = showNsfwSources,
                        defaultZoom = defaultZoom,
                        webtoonPadding = webtoonPadding,
                        keepScreenAwake = keepScreenAwake,
                        librarySort = librarySort.name,
                        libraryDisplay = libraryDisplay.name,
                        chaptersSortAsc = chaptersSortAsc,
                    ),
                ),
            )
        }
    }
}
