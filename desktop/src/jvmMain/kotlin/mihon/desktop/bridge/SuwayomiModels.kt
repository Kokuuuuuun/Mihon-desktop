package mihon.desktop.bridge

import kotlinx.serialization.Serializable

/** GraphQL response envelope. */
@Serializable
data class GraphQLResponse<T>(
    val data: T? = null,
    val errors: List<GraphQLError>? = null,
)

@Serializable
data class GraphQLError(val message: String)

// --- Domain-ish DTOs mirroring Suwayomi's GraphQL types (only the fields we use) ---

@Serializable
data class SourceDto(
    val id: String,
    val name: String,
    val lang: String,
    val iconUrl: String? = null,
    val isNsfw: Boolean = false,
    val supportsLatest: Boolean = false,
)

@Serializable
data class MangaDto(
    val id: Long,
    val title: String,
    val thumbnailUrl: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val genre: List<String> = emptyList(),
    val status: String = "UNKNOWN",
    val inLibrary: Boolean = false,
    val sourceId: String? = null,
    val unreadCount: Int = 0,
    val downloadCount: Int = 0,
    val lastReadAt: String? = null,
    val inLibraryAt: String? = null,
)

@Serializable
data class ChapterDto(
    val id: Long,
    val name: String,
    val chapterNumber: Float = 0f,
    val scanlator: String? = null,
    val pageCount: Int = -1,
    val isRead: Boolean = false,
    val isBookmarked: Boolean = false,
    val isDownloaded: Boolean = false,
    val lastPageRead: Int = 0,
    val uploadDate: String = "0",
    val fetchedAt: String = "0",
    val lastReadAt: String = "0",
    val sourceOrder: Int = 0,
    val mangaId: Long = 0,
    val manga: MangaDto? = null,
)

@Serializable
data class CategoryDto(
    val id: Int,
    val name: String,
    val order: Int = 0,
    val default: Boolean = false,
)

@Serializable
data class ExtensionDto(
    val pkgName: String,
    val name: String,
    val lang: String,
    val versionName: String = "",
    val iconUrl: String? = null,
    val isInstalled: Boolean = false,
    val hasUpdate: Boolean = false,
    val isObsolete: Boolean = false,
)

@Serializable
data class ExtensionStoreDto(
    val name: String,
    val indexUrl: String,
)

@Serializable
data class DownloadDto(
    val position: Int = 0,
    val progress: Float = 0f,
    val state: String = "QUEUED",
    val tries: Int = 0,
    val chapter: ChapterDto? = null,
    val manga: MangaDto? = null,
)

@Serializable
data class DownloadStatusDto(
    val state: String = "STOPPED",
    val queue: List<DownloadDto> = emptyList(),
)

@Serializable
data class TrackerDto(
    val id: Int,
    val name: String,
    val icon: String? = null,
    val isLoggedIn: Boolean = false,
    val isTokenExpired: Boolean = false,
    val authUrl: String? = null,
    val supportsPrivateTracking: Boolean = false,
)

// --- Query/mutation payload wrappers ---

@Serializable
data class NodeList<T>(val nodes: List<T> = emptyList(), val totalCount: Int = 0)

@Serializable
data class SourcesData(val sources: NodeList<SourceDto>)

@Serializable
data class MangasData(val mangas: NodeList<MangaDto>)

@Serializable
data class CategoriesData(val categories: NodeList<CategoryDto>)

@Serializable
data class ChaptersQueryData(val chapters: NodeList<ChapterDto>)

@Serializable
data class ExtensionsData(val extensions: NodeList<ExtensionDto>)

@Serializable
data class ExtensionStoresData(val extensionStores: NodeList<ExtensionStoreDto>)

@Serializable
data class TrackersData(val trackers: NodeList<TrackerDto>)

@Serializable
data class DownloadStatusData(val downloadStatus: DownloadStatusDto)

@Serializable
data class CategoryMangaData(val category: CategoryMangaWrapper)

@Serializable
data class CategoryMangaWrapper(val mangas: NodeList<MangaDto>)

@Serializable
data class UpdateMangaData(val updateManga: MangaWrapper)

@Serializable
data class CreateCategoryData(val createCategory: CategoryWrapper)

@Serializable
data class CategoryWrapper(val category: CategoryDto)

@Serializable
data class FetchExtensionsData(val fetchExtensions: FetchExtensionsWrapper)

@Serializable
data class FetchExtensionsWrapper(val extensions: List<ExtensionDto>)

@Serializable
data class FetchSourceMangaData(val fetchSourceManga: SourceMangaPage)

@Serializable
data class SourceMangaPage(val hasNextPage: Boolean, val mangas: List<MangaDto>)

@Serializable
data class FetchMangaData(val fetchManga: MangaWrapper)

@Serializable
data class MangaWrapper(val manga: MangaDto)

@Serializable
data class FetchChaptersData(val fetchChapters: ChaptersWrapper)

@Serializable
data class ChaptersWrapper(val chapters: List<ChapterDto>)

@Serializable
data class FetchChapterPagesData(val fetchChapterPages: PagesWrapper)

@Serializable
data class PagesWrapper(val pages: List<String>)

// Generic mutation acknowledgements we don't need to read deeply.
@Serializable
data class MutationAck(val clientMutationId: String? = null)

/** Browse type for [SuwayomiClient.fetchSourceManga]. */
enum class MangaFetchType { POPULAR, LATEST, SEARCH }

@Serializable
data class ServerInfoDto(
    val version: String = "",
    val buildType: String = "",
    val serverBuildTime: String = "",
)

@Serializable
data class ServerInfoData(val aboutServer: ServerInfoDto)
