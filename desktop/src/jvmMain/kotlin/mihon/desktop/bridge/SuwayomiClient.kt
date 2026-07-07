package mihon.desktop.bridge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.serializer
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Thin GraphQL client for Suwayomi-Server built on the JDK's [HttpClient] (no extra HTTP dependency).
 *
 * Covers the operations the desktop client needs: list sources, browse a source, manga details +
 * chapters, and reader page URLs. Image bytes themselves are fetched separately over REST via
 * [SuwayomiConfig.coverUrl] / [SuwayomiConfig.pageUrl] (fed to Coil).
 */
class SuwayomiClient(
    val config: SuwayomiConfig = SuwayomiConfig(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build()

    private suspend inline fun <reified T> execute(
        query: String,
        variables: JsonObject = JsonObject(emptyMap()),
    ): T = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("query", query)
            put("variables", variables)
        }
        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(config.graphqlEndpoint))
            .timeout(Duration.ofSeconds(120))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(JsonObject.serializer(), body)))
        config.authHeader?.let { requestBuilder.header("Authorization", it) }

        val response = http.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            error("Suwayomi HTTP ${response.statusCode()}: ${response.body().take(200)}")
        }
        val parsed = json.decodeFromString(GraphQLResponse.serializer(serializer<T>()), response.body())
        parsed.errors?.takeIf { it.isNotEmpty() }?.let {
            error("Suwayomi GraphQL error: ${it.joinToString { e -> e.message }}")
        }
        parsed.data ?: error("Suwayomi returned no data for query")
    }

    suspend fun getSources(): List<SourceDto> =
        execute<SourcesData>(
            "{ sources { nodes { id name lang iconUrl isNsfw supportsLatest } } }",
        ).sources.nodes

    suspend fun fetchSourceManga(
        sourceId: String,
        type: MangaFetchType,
        page: Int,
        query: String? = null,
    ): SourceMangaPage {
        val variables = buildJsonObject {
            put("source", sourceId)
            put("type", type.name)
            put("page", page)
            if (query != null) put("query", query)
        }
        val gql = """
            mutation Fetch(${'$'}source: LongString!, ${'$'}type: FetchSourceMangaType!, ${'$'}page: Int!, ${'$'}query: String) {
              fetchSourceManga(input: { source: ${'$'}source, type: ${'$'}type, page: ${'$'}page, query: ${'$'}query }) {
                hasNextPage
                mangas { id title thumbnailUrl inLibrary }
              }
            }
        """.trimIndent()
        return execute<FetchSourceMangaData>(gql, variables).fetchSourceManga
    }

    /** Search inside a single source by free-text query (type = SEARCH). */
    suspend fun searchSourceManga(sourceId: String, query: String, page: Int = 1): SourceMangaPage =
        fetchSourceManga(sourceId, MangaFetchType.SEARCH, page, query)

    /**
     * Searches a query across many sources in parallel and returns the per-source first page.
     * Each entry's value is empty when that source errored or returned nothing.
     */
    suspend fun globalSearch(sources: List<SourceDto>, query: String): List<Pair<SourceDto, List<MangaDto>>> =
        coroutineScope {
            sources.map { source ->
                async {
                    runCatching { searchSourceManga(source.id, query, 1).mangas }
                        .getOrNull()
                        .let { source to (it ?: emptyList()) }
                }
            }.awaitAll()
        }

    /** Full manga details, refreshed from the source. */
    suspend fun fetchManga(id: Long): MangaDto {
        val variables = buildJsonObject { put("id", id) }
        val gql = """
            mutation FetchManga(${'$'}id: Int!) {
              fetchManga(input: { id: ${'$'}id }) {
                manga { id title thumbnailUrl author artist description genre status inLibrary }
              }
            }
        """.trimIndent()
        return execute<FetchMangaData>(gql, variables).fetchManga.manga
    }

    suspend fun fetchChapters(mangaId: Long): List<ChapterDto> {
        val variables = buildJsonObject { put("mangaId", mangaId) }
        val gql = """
            mutation FetchChapters(${'$'}mangaId: Int!) {
              fetchChapters(input: { mangaId: ${'$'}mangaId }) {
                chapters { id name chapterNumber scanlator pageCount isRead isDownloaded uploadDate sourceOrder }
              }
            }
        """.trimIndent()
        return execute<FetchChaptersData>(gql, variables).fetchChapters.chapters
    }

    /** Ordered relative page paths for a chapter. Wrap with [SuwayomiConfig.pageUrl] before loading. */
    suspend fun fetchChapterPages(chapterId: Long): List<String> {
        val variables = buildJsonObject { put("chapterId", chapterId) }
        val gql = """
            mutation FetchPages(${'$'}chapterId: Int!) {
              fetchChapterPages(input: { chapterId: ${'$'}chapterId }) { pages }
            }
        """.trimIndent()
        return execute<FetchChapterPagesData>(gql, variables).fetchChapterPages.pages
    }

    // ---------------------------------------------------------------------------------------------
    // Library
    // ---------------------------------------------------------------------------------------------

    private val libraryMangaFields =
        "id title thumbnailUrl author status inLibrary unreadCount downloadCount sourceId inLibraryAt"

    /** Manga in the user's library, optionally restricted to a category. */
    suspend fun getLibraryManga(categoryId: Int? = null): List<MangaDto> {
        return if (categoryId == null) {
            execute<MangasData>(
                "{ mangas(condition: { inLibrary: true }, order: { by: TITLE }) { nodes { $libraryMangaFields } } }",
            ).mangas.nodes
        } else {
            val variables = buildJsonObject { put("id", categoryId) }
            execute<CategoryMangaData>(
                "query Cat(${'$'}id: Int!) { category(id: ${'$'}id) { mangas { nodes { $libraryMangaFields } } } }",
                variables,
            ).category.mangas.nodes
        }
    }

    /** Add or remove a manga from the library. */
    suspend fun setInLibrary(mangaId: Long, inLibrary: Boolean) {
        val variables = buildJsonObject {
            put("id", mangaId)
            put("inLibrary", inLibrary)
        }
        execute<UpdateMangaData>(
            """
            mutation SetLib(${'$'}id: Int!, ${'$'}inLibrary: Boolean!) {
              updateManga(input: { id: ${'$'}id, patch: { inLibrary: ${'$'}inLibrary } }) {
                manga { id inLibrary }
              }
            }
            """.trimIndent(),
            variables,
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Categories
    // ---------------------------------------------------------------------------------------------

    suspend fun getCategories(): List<CategoryDto> =
        execute<CategoriesData>(
            "{ categories(order: { by: ORDER }) { nodes { id name order default } } }",
        ).categories.nodes

    suspend fun createCategory(name: String) {
        val variables = buildJsonObject { put("name", name) }
        execute<CreateCategoryData>(
            """
            mutation CreateCat(${'$'}name: String!) {
              createCategory(input: { name: ${'$'}name }) { category { id name } }
            }
            """.trimIndent(),
            variables,
        )
    }

    suspend fun deleteCategory(categoryId: Int) {
        val variables = buildJsonObject { put("id", categoryId) }
        execute<JsonObject>(
            "mutation DelCat(${'$'}id: Int!) { deleteCategory(input: { categoryId: ${'$'}id }) { clientMutationId } }",
            variables,
        )
    }

    /** Move a manga's category membership. */
    suspend fun updateMangaCategories(mangaId: Long, addTo: List<Int>, removeFrom: List<Int>) {
        val variables = buildJsonObject {
            put("id", mangaId)
            putJsonArray("add") { addTo.forEach { add(it) } }
            putJsonArray("remove") { removeFrom.forEach { add(it) } }
        }
        execute<JsonObject>(
            """
            mutation MangaCats(${'$'}id: Int!, ${'$'}add: [Int!], ${'$'}remove: [Int!]) {
              updateMangaCategories(input: { id: ${'$'}id, patch: { addToCategories: ${'$'}add, removeFromCategories: ${'$'}remove } }) {
                manga { id }
              }
            }
            """.trimIndent(),
            variables,
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Chapters (read state, bookmark, progress)
    // ---------------------------------------------------------------------------------------------

    suspend fun updateChapter(
        chapterId: Long,
        isRead: Boolean? = null,
        isBookmarked: Boolean? = null,
        lastPageRead: Int? = null,
    ) {
        val variables = buildJsonObject {
            put("id", chapterId)
            if (isRead != null) put("isRead", isRead)
            if (isBookmarked != null) put("isBookmarked", isBookmarked)
            if (lastPageRead != null) put("lastPageRead", lastPageRead)
        }
        val patch = buildString {
            append("{ ")
            if (isRead != null) append("isRead: ${'$'}isRead ")
            if (isBookmarked != null) append("isBookmarked: ${'$'}isBookmarked ")
            if (lastPageRead != null) append("lastPageRead: ${'$'}lastPageRead ")
            append("}")
        }
        val decls = buildList {
            add("${'$'}id: Int!")
            if (isRead != null) add("${'$'}isRead: Boolean")
            if (isBookmarked != null) add("${'$'}isBookmarked: Boolean")
            if (lastPageRead != null) add("${'$'}lastPageRead: Int")
        }.joinToString(", ")
        execute<JsonObject>(
            "mutation UpdCh($decls) { updateChapter(input: { id: ${'$'}id, patch: $patch }) { chapter { id } } }",
            variables,
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Updates & History
    // ---------------------------------------------------------------------------------------------

    private val feedChapterFields =
        "id name chapterNumber scanlator isRead isBookmarked isDownloaded lastPageRead " +
            "fetchedAt lastReadAt sourceOrder mangaId manga { id title thumbnailUrl sourceId }"

    /** Recently fetched chapters of library manga (the "Updates"/"Novedades" feed). */
    suspend fun getRecentUpdates(limit: Int = 300): List<ChapterDto> {
        val variables = buildJsonObject { put("first", limit) }
        return execute<ChaptersQueryData>(
            """
            query Updates(${'$'}first: Int!) {
              chapters(
                filter: { inLibrary: { equalTo: true } }
                order: { by: FETCHED_AT, byType: DESC }
                first: ${'$'}first
              ) { nodes { $feedChapterFields } }
            }
            """.trimIndent(),
            variables,
        ).chapters.nodes
    }

    /** Chapters with a read timestamp, newest first (the "History"/"Historial" feed). */
    suspend fun getHistory(limit: Int = 300): List<ChapterDto> {
        val variables = buildJsonObject { put("first", limit) }
        return execute<ChaptersQueryData>(
            """
            query History(${'$'}first: Int!) {
              chapters(
                filter: { lastReadAt: { greaterThan: "0" } }
                order: { by: LAST_READ_AT, byType: DESC }
                first: ${'$'}first
              ) { nodes { $feedChapterFields } }
            }
            """.trimIndent(),
            variables,
        ).chapters.nodes
    }

    /** Kick off a global library update on the server. */
    suspend fun updateLibrary() {
        execute<JsonObject>("mutation { updateLibrary(input: {}) { clientMutationId } }")
    }

    // ---------------------------------------------------------------------------------------------
    // Downloads
    // ---------------------------------------------------------------------------------------------

    suspend fun getDownloadStatus(): DownloadStatusDto =
        execute<DownloadStatusData>(
            """
            { downloadStatus {
                state
                queue {
                  position progress state tries
                  chapter { id name mangaId }
                  manga { id title thumbnailUrl }
                }
            } }
            """.trimIndent(),
        ).downloadStatus

    suspend fun enqueueDownloads(chapterIds: List<Long>) {
        val variables = buildJsonObject { putJsonArray("ids") { chapterIds.forEach { add(it) } } }
        execute<JsonObject>(
            "mutation Enq(${'$'}ids: [Int!]!) { enqueueChapterDownloads(input: { ids: ${'$'}ids }) { clientMutationId } }",
            variables,
        )
    }

    suspend fun dequeueDownloads(chapterIds: List<Long>) {
        val variables = buildJsonObject { putJsonArray("ids") { chapterIds.forEach { add(it) } } }
        execute<JsonObject>(
            "mutation Deq(${'$'}ids: [Int!]!) { dequeueChapterDownloads(input: { ids: ${'$'}ids }) { clientMutationId } }",
            variables,
        )
    }

    suspend fun startDownloader() =
        execute<JsonObject>("mutation { startDownloader(input: {}) { clientMutationId } }").let { }

    suspend fun stopDownloader() =
        execute<JsonObject>("mutation { stopDownloader(input: {}) { clientMutationId } }").let { }

    suspend fun deleteDownloadedChapters(chapterIds: List<Long>) {
        val variables = buildJsonObject { putJsonArray("ids") { chapterIds.forEach { add(it) } } }
        execute<JsonObject>(
            "mutation Del(${'$'}ids: [Int!]!) { deleteDownloadedChapters(input: { ids: ${'$'}ids }) { clientMutationId } }",
            variables,
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Extensions & repositories
    // ---------------------------------------------------------------------------------------------

    suspend fun getExtensions(): List<ExtensionDto> =
        execute<ExtensionsData>(
            "{ extensions { nodes { pkgName name lang versionName iconUrl isInstalled hasUpdate isObsolete } } }",
        ).extensions.nodes

    suspend fun getExtensionStores(): List<ExtensionStoreDto> =
        execute<ExtensionStoresData>(
            "{ extensionStores { nodes { name indexUrl } } }",
        ).extensionStores.nodes

    suspend fun addExtensionStore(indexUrl: String) {
        val variables = buildJsonObject { put("indexUrl", indexUrl) }
        execute<JsonObject>(
            "mutation AddStore(${'$'}indexUrl: String!) { addExtensionStore(input: { indexUrl: ${'$'}indexUrl }) { extensionStore { name } } }",
            variables,
        )
    }

    suspend fun removeExtensionStore(indexUrl: String) {
        val variables = buildJsonObject { put("indexUrl", indexUrl) }
        execute<JsonObject>(
            "mutation RmStore(${'$'}indexUrl: String!) { removeExtensionStore(input: { indexUrl: ${'$'}indexUrl }) { clientMutationId } }",
            variables,
        )
    }

    /** Refresh the extension index from all configured repositories. */
    suspend fun fetchExtensions(): List<ExtensionDto> =
        execute<FetchExtensionsData>(
            "mutation { fetchExtensions(input: {}) { extensions { pkgName name lang versionName iconUrl isInstalled hasUpdate isObsolete } } }",
        ).fetchExtensions.extensions

    private suspend fun patchExtension(pkgName: String, field: String) {
        val variables = buildJsonObject { put("id", pkgName) }
        execute<JsonObject>(
            "mutation ExtPatch(${'$'}id: String!) { updateExtension(input: { id: ${'$'}id, patch: { $field: true } }) { extension { pkgName isInstalled } } }",
            variables,
        )
    }

    suspend fun installExtension(pkgName: String) = patchExtension(pkgName, "install")
    suspend fun uninstallExtension(pkgName: String) = patchExtension(pkgName, "uninstall")
    suspend fun updateExtension(pkgName: String) = patchExtension(pkgName, "update")

    // ---------------------------------------------------------------------------------------------
    // Trackers
    // ---------------------------------------------------------------------------------------------

    suspend fun getTrackers(): List<TrackerDto> =
        execute<TrackersData>(
            "{ trackers { nodes { id name icon isLoggedIn isTokenExpired authUrl supportsPrivateTracking } } }",
        ).trackers.nodes

    suspend fun loginTrackerOAuth(trackerId: Int, callbackUrl: String) {
        val variables = buildJsonObject {
            put("trackerId", trackerId)
            put("callbackUrl", callbackUrl)
        }
        execute<JsonObject>(
            "mutation TrLogin(${'$'}trackerId: Int!, ${'$'}callbackUrl: String!) { loginTrackerOAuth(input: { trackerId: ${'$'}trackerId, callbackUrl: ${'$'}callbackUrl }) { isLoggedIn } }",
            variables,
        )
    }

    suspend fun logoutTracker(trackerId: Int) {
        val variables = buildJsonObject { put("trackerId", trackerId) }
        execute<JsonObject>(
            "mutation TrLogout(${'$'}trackerId: Int!) { logoutTracker(input: { trackerId: ${'$'}trackerId }) { isLoggedIn } }",
            variables,
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Bulk chapter operations (mark all read / download all)
    // ---------------------------------------------------------------------------------------------

    /**
     * Mark every chapter of a manga read or unread in one server round-trip, with a per-chapter
     * fallback when the server does not expose a bulk mutation.
     */
    suspend fun setChaptersRead(chapters: List<ChapterDto>, read: Boolean) {
        if (chapters.isEmpty()) return
        runCatching {
            execute<JsonObject>(
                """
                mutation MarkAll(${'$'}ids: [Int!]!) {
                  updateChapters(input: { ids: ${'$'}ids, patch: { isRead: $read } }) { clientMutationId }
                }
                """.trimIndent(),
                buildJsonObject { putJsonArray("ids") { chapters.forEach { add(it.id) } } },
            )
        }.getOrElse {
            chapters.forEach { updateChapter(it.id, isRead = read) }
        }
    }

    /** Enqueue every chapter of a manga for download and start the downloader worker. */
    suspend fun downloadAllChapters(chapters: List<ChapterDto>) {
        if (chapters.isEmpty()) return
        enqueueDownloads(chapters.map { it.id })
        startDownloader()
    }

    // ---------------------------------------------------------------------------------------------
    // Server info
    // ---------------------------------------------------------------------------------------------

    /** Basic server metadata for the "Ajustes > Servidor" panel. */
    suspend fun getServerInfo(): ServerInfoDto = execute<ServerInfoData>(
        "{ aboutServer { version buildType serverBuildTime } }",
    ).aboutServer
}
