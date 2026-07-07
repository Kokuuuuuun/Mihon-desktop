package eu.kanade.domain.ui.model

/**
 * Desktop port of Mihon's AppTheme.
 *
 * The Android original carries a moko-resources [StringResource] label; on desktop we use a plain
 * display name and drop the Material You [MONET] dynamic theme (no wallpaper-based theming on Linux).
 */
enum class AppTheme(val label: String) {
    DEFAULT("Default"),
    CATPPUCCIN("Catppuccin"),
    GREEN_APPLE("Green Apple"),
    LAVENDER("Lavender"),
    MIDNIGHT_DUSK("Midnight Dusk"),
    NORD("Nord"),
    STRAWBERRY_DAIQUIRI("Strawberry Daiquiri"),
    TAKO("Tako"),
    TEALTURQUOISE("Teal & Turquoise"),
    TIDAL_WAVE("Tidal Wave"),
    YINYANG("Yin & Yang"),
    YOTSUBA("Yotsuba"),
    MONOCHROME("Monochrome"),
}
