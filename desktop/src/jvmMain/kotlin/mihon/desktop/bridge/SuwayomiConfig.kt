package mihon.desktop.bridge

/**
 * Connection settings for a Suwayomi-Server instance and helpers to build the REST image URLs
 * (covers and reader pages are served over plain REST even though everything else is GraphQL).
 */
data class SuwayomiConfig(
    val baseUrl: String = DEFAULT_BASE_URL,
    val authHeader: String? = null,
) {
    val graphqlEndpoint: String get() = "$baseUrl/api/graphql"

    /** Absolute URL for a manga cover. [thumbnailPath] is the relative path from `MangaType.thumbnailUrl`. */
    fun coverUrl(thumbnailPath: String?): String? =
        thumbnailPath?.let { if (it.startsWith("http")) it else "$baseUrl$it" }

    /** Absolute URL for a reader page. [pagePath] is a relative path from `fetchChapterPages`. */
    fun pageUrl(pagePath: String): String =
        if (pagePath.startsWith("http")) pagePath else "$baseUrl$pagePath"

    companion object {
        const val DEFAULT_BASE_URL = "http://localhost:4567"
    }
}
