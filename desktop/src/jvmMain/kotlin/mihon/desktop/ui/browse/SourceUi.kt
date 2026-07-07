package mihon.desktop.ui.browse

import mihon.desktop.bridge.SourceDto
import mihon.desktop.settings.DesktopSettings
import java.util.Locale

/**
 * Grouping model for the sources list, mirroring Mihon's [SourceUiModel]: each language gets a
 * [Header], individual sources become [Item]. Pinned sources and the last-used one get their own
 * special header keys so they sort to the top.
 */
sealed interface SourceUiModel {
    data class Header(val key: String) : SourceUiModel
    data class Item(val source: SourceDto) : SourceUiModel
}

/** Keys reserved for non-language group headers (must not collide with real language codes). */
internal const val PINNED_KEY = "pinned"
internal const val LAST_USED_KEY = "last_used"

/** True when the user pinned this source via [DesktopSettings.toggleSourcePin]. */
val SourceDto.isPinned: Boolean get() = id in DesktopSettings.pinnedSources

/** True when this source was the most recently opened catalogue. */
val SourceDto.isUsedLast: Boolean get() = DesktopSettings.lastUsedSource == id

/**
 * Builds the grouped, ordered source list mirroring Mihon's `collectLatestSources`:
 * last-used -> pinned -> by language (alphabetical) -> no-language last. NSFW sources are hidden
 * when the user opted to not show them.
 */
fun groupSources(
    sources: List<SourceDto>,
    query: String,
    showNsfw: Boolean,
): List<SourceUiModel> {
    val filtered = sources.asSequence()
        .filter { it.id != "0" } // skip Local source
        .filter { showNsfw || !it.isNsfw }
        .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) || it.lang.contains(query, ignoreCase = true) }
        .toList()

    // Group preserving the special ordering of keys.
    val byKey = linkedMapOf<String, MutableList<SourceDto>>()
    for (source in filtered) {
        val key = when {
            source.isUsedLast -> LAST_USED_KEY
            source.isPinned -> PINNED_KEY
            else -> source.lang.ifEmpty { "other" }
        }
        byKey.getOrPut(key) { mutableListOf() }.add(source)
    }

    val ordered = byKey.entries.sortedWith(
        compareBy<Map.Entry<String, MutableList<SourceDto>>> { keyPriority(it.key) }
            .thenBy { it.key },
    )

    return ordered.flatMap { (key, list) ->
        listOf(SourceUiModel.Header(key)) + list.sortedBy { it.name.lowercase() }.map { SourceUiModel.Item(it) }
    }
}

private fun keyPriority(key: String): Int = when (key) {
    LAST_USED_KEY -> 0
    PINNED_KEY -> 1
    "other" -> 3
    else -> 2
}

/** Human-readable label for a source group/language key, like Mihon's `LocaleHelper.getSourceDisplayName`. */
fun sourceGroupLabel(key: String): String = when (key) {
    LAST_USED_KEY -> "Última usada"
    PINNED_KEY -> "Fijadas"
    "other" -> "Otras"
    else -> Locale.forLanguageTag(key).getDisplayName(Locale.getDefault()).replaceFirstChar { it.uppercase() }.ifEmpty { key }
}
