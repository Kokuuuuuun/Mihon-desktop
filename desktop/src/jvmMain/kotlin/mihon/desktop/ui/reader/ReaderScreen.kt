package mihon.desktop.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.PlatformContext
import coil3.compose.AsyncImagePainter.State
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.setParameter
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import mihon.desktop.bridge.ChapterDto
import mihon.desktop.bridge.Suwayomi
import mihon.desktop.settings.DesktopSettings
import mihon.desktop.ui.common.ErrorBox
import mihon.desktop.ui.common.LoadState
import mihon.desktop.ui.common.LoadingBox
import kotlin.math.abs

private enum class ReaderMode { PAGED, WEBTOON }

/**
 * Compose reader (full rewrite of Mihon's View-based viewer). Paged (HorizontalPager + pinch/scroll
 * zoom) and continuous webtoon modes, driven by keyboard arrows, Page Up/Down, Space, Home/End and
 * the mouse wheel. A bottom bar offers prev/next page, a page slider, mode toggle and prev/next
 * chapter. Progress is synced to the server; the chapter is auto-marked read at the last page.
 *
 * Each page tracks a discrete [PageState] (Loading / Success / Error) so a stuck spinner or a
 * silently-failed page is impossible: errors surface a "Reintentar" affordance, and the spinner
 * only shows while a request is genuinely in flight.
 */
data class ReaderScreen(
    val mangaId: Long,
    val chapterId: Long,
    val mangaTitle: String,
    val chapterName: String,
    /** Optional list of chapters for prev/next navigation, ordered new-to-old as Mihon shows them. */
    val chapters: List<ChapterDto> = emptyList(),
) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        var pagesState by remember { mutableStateOf<LoadState<List<String>>>(LoadState.Loading) }
        var mode by remember { mutableStateOf(if (DesktopSettings.defaultWebtoon) ReaderMode.WEBTOON else ReaderMode.PAGED) }
        var controlsVisible by remember { mutableStateOf(true) }
        var currentPage by remember { mutableStateOf(0) }

        LaunchedEffect(chapterId) {
            pagesState = LoadState.Loading
            pagesState = runCatching {
                Suwayomi.client.fetchChapterPages(chapterId).map { Suwayomi.config.pageUrl(it) }
            }.fold({ LoadState.Success(it) }, { LoadState.Error(it.message ?: it.toString()) })
        }

        // Best-effort "keep screen awake": extension point for per-OS display-sleep suppression.
        ReaderKeepAwake(DesktopSettings.keepScreenAwake)

        val onPageChanged: (Int, Int) -> Unit = remember(chapterId) {
            { page, total ->
                scope.launch {
                    runCatching {
                        val read = total > 0 && page >= total - 1
                        Suwayomi.client.updateChapter(chapterId, lastPageRead = page, isRead = if (read) true else null)
                    }
                }
            }
        }
        // Seek channel: the bottom-bar slider writes a target page index; the pager consumes it.
        val seekTarget = remember { mutableStateOf<Int?>(null) }
        fun seek(page: Int) { seekTarget.value = page }

        // Find prev/next chapters within the manga for the bottom bar navigation.
        val sortedChapters = remember(chapters) { chapters.sortedByDescending { it.sourceOrder } }
        val currentIndex = remember(chapters, chapterId) { sortedChapters.indexOfFirst { it.id == chapterId } }
        val prevChapter = sortedChapters.getOrNull(currentIndex - 1)
        val nextChapter = sortedChapters.getOrNull(currentIndex + 1)

        fun goToChapter(chapter: ChapterDto?) {
            if (chapter != null) navigator.replace(
                ReaderScreen(mangaId, chapter.id, mangaTitle, chapter.name, chapters),
            )
        }

        Scaffold(
            containerColor = Color.Black,
            contentColor = Color.White,
            topBar = {
                if (controlsVisible) {
                    TopAppBar(
                        title = {
                            Column {
                                Text(mangaTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    chapterName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { navigator.pop() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Salir del lector")
                            }
                        },
                        actions = {
                            (pagesState as? LoadState.Success)?.let { s ->
                                val total = s.value.size.coerceAtLeast(1)
                                val pct = if (total <= 1) 100 else ((currentPage + 1) * 100 / total).coerceIn(0, 100)
                                AssistChip(
                                    onClick = {},
                                    enabled = false,
                                    label = { Text("$pct%") },
                                    colors = AssistChipDefaults.assistChipColors(),
                                    modifier = Modifier.padding(end = 4.dp),
                                )
                            }
                            IconButton(onClick = { mode = if (mode == ReaderMode.PAGED) ReaderMode.WEBTOON else ReaderMode.PAGED }) {
                                Icon(
                                    if (mode == ReaderMode.PAGED) Icons.Filled.ViewDay else Icons.Filled.ViewColumn,
                                    contentDescription = "Cambiar modo (paginado / webtoon)",
                                )
                            }
                        },
                    )
                }
            },
            bottomBar = {
                if (controlsVisible) {
                    (pagesState as? LoadState.Success)?.let { s ->
                        ReaderBottomBar(
                            totalPages = s.value.size,
                            currentPage = currentPage,
                            hasPrevChapter = prevChapter != null,
                            hasNextChapter = nextChapter != null,
                            onPageChange = { page -> seek(page) },
                            onPrevChapter = { goToChapter(prevChapter) },
                            onNextChapter = { goToChapter(nextChapter) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(padding)
                    .pointerInput(Unit) { detectTapGestures(onTap = { controlsVisible = !controlsVisible }) },
            ) {
                when (val s = pagesState) {
                    is LoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                    is LoadState.Error -> ErrorBox(s.message)
                    is LoadState.Success -> when (mode) {
                        ReaderMode.PAGED -> PagedReader(s.value, onPageChanged, seekTarget, onPageIndex = { currentPage = it })
                        ReaderMode.WEBTOON -> WebtoonReader(
                            pages = s.value,
                            onPageChanged = onPageChanged,
                            seekTarget = seekTarget,
                            onPageIndex = { currentPage = it },
                            gapPadding = DesktopSettings.webtoonPadding,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Fixed bottom control bar with prev/next page buttons, a page slider and chapter navigation,
 * mirroring Mihon's reader overlay. Grouped for clarity: [chapter nav] · [page nav · slider · count] ·
 * [chapter nav].
 */
@Composable
private fun ReaderBottomBar(
    totalPages: Int,
    currentPage: Int,
    hasPrevChapter: Boolean,
    hasNextChapter: Boolean,
    onPageChange: (Int) -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), tonalElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevChapter, enabled = hasPrevChapter) {
                Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "Capítulo anterior")
            }
            IconButton(onClick = { onPageChange((currentPage - 1).coerceAtLeast(0)) }, enabled = currentPage > 0) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Página anterior")
            }
            Slider(
                value = if (totalPages > 1) currentPage.toFloat() / (totalPages - 1) else 0f,
                onValueChange = { onPageChange(if (totalPages > 1) (it * (totalPages - 1)).toInt().coerceIn(0, totalPages - 1) else 0) },
                enabled = totalPages > 1,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            IconButton(onClick = { onPageChange((currentPage + 1).coerceAtMost(totalPages - 1)) }, enabled = currentPage < totalPages - 1) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Página siguiente")
            }
            Text(
                "${currentPage + 1}/$totalPages",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            IconButton(onClick = onNextChapter, enabled = hasNextChapter) {
                Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "Capítulo siguiente")
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PagedReader(
    pages: List<String>,
    onPageChanged: (Int, Int) -> Unit,
    seekTarget: androidx.compose.runtime.MutableState<Int?>,
    onPageIndex: (Int) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                onPageIndex(page)
                if (pages.isNotEmpty()) onPageChanged(page, pages.size)
            }
    }

    fun go(delta: Int) {
        val target = (pagerState.currentPage + delta).coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        scope.launch { pagerState.animateScrollToPage(target) }
    }
    fun goTo(target: Int) {
        scope.launch { pagerState.animateScrollToPage(target.coerceIn(0, (pages.size - 1).coerceAtLeast(0))) }
    }

    // Honour bottom-bar slider seeks.
    LaunchedEffect(seekTarget) {
        snapshotFlow { seekTarget.value }
            .collect { target -> if (target != null) { seekTarget.value = null; goTo(target) } }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val dy = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                if (dy > 0f) go(1) else if (dy < 0f) go(-1)
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionRight, Key.PageDown, Key.Spacebar -> { go(1); true }
                    Key.DirectionLeft, Key.PageUp -> { go(-1); true }
                    Key.MoveHome -> { goTo(0); true }
                    Key.MoveEnd -> { goTo(pages.size - 1); true }
                    else -> false
                }
            },
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { index ->
            ZoomablePage(url = pages[index], defaultZoom = DesktopSettings.defaultZoom)
        }
        PageBadge(
            "${pagerState.currentPage + 1} / ${pages.size}",
            Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
        )
    }
}

/**
 * Pinch/double-tap zoom with **bounded panning**: the image never drifts off-screen, offsets are
 * clamped to how far the scaled image overflows the viewport on each axis. A discrete [PageState]
 * (Loading/Success/Error) drives the overlay so the spinner clears exactly when the bitmap is ready
 * and a failed fetch shows a tappable "Reintentar" instead of spinning forever.
 */
@Composable
private fun ZoomablePage(url: String, defaultZoom: Float = 1f) {
    var scale by remember(url) { mutableStateOf(defaultZoom) }
    var offsetX by remember(url) { mutableStateOf(0f) }
    var offsetY by remember(url) { mutableStateOf(0f) }
    var viewport by remember(url) { mutableStateOf(IntSize.Zero) }
    var state by remember(url) { mutableStateOf<PageState>(PageState.Loading) }
    // Bump on retry so [rememberAsyncImagePainter] is rebuilt and Coil re-fetches the page.
    var retryKey by remember(url) { mutableStateOf(0) }

    val maxOffsetX: () -> Float = {
        if (viewport.width == 0) 0f else ((scale - 1f) * viewport.width / 2f).coerceAtLeast(0f)
    }
    val maxOffsetY: () -> Float = {
        if (viewport.height == 0) 0f else ((scale - 1f) * viewport.height / 2f).coerceAtLeast(0f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { viewport = it }
            .pointerInput(url) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                    // Only accumulate pan when zoomed in; reset when returning to fit.
                    if (newScale > 1f) {
                        offsetX = (offsetX + pan.x).coerceIn(-maxOffsetX(), maxOffsetX())
                        offsetY = (offsetY + pan.y).coerceIn(-maxOffsetY(), maxOffsetY())
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                    scale = newScale
                }
            }
            .pointerInput(url) {
                detectTapGestures(onDoubleTap = {
                    val newScale = if (scale > 1f) 1f else 2f
                    scale = newScale
                    if (newScale <= 1f) { offsetX = 0f; offsetY = 0f }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        // Use a remembered painter so the load state is authoritative; AsyncImage composable reuses it.
        // The model is an ImageRequest carrying a per-retry "retry" parameter, so bumping [retryKey]
        // changes the request identity and forces Coil to re-fetch the page (bypassing its cache hit).
        val painter = rememberAsyncImagePainter(
            model = coil3.request.ImageRequest.Builder(PlatformContext.INSTANCE)
                .data(url)
                .setParameter("retry", retryKey.toString())
                .build(),
            onState = { st ->
                state = when (st) {
                    is State.Empty, is State.Loading -> PageState.Loading
                    is State.Success -> PageState.Success
                    is State.Error -> PageState.Error
                }
            },
        )
        androidx.compose.foundation.Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY,
                ),
        )

        when (val s = state) {
            is PageState.Loading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
            )
            is PageState.Error -> Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No se pudo cargar la página", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { state = PageState.Loading; retryKey++ }) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Text("Reintentar", color = Color.White)
                }
            }
            PageState.Success -> Unit
        }
    }
}

private sealed interface PageState {
    data object Loading : PageState
    data object Success : PageState
    data object Error : PageState
}

@Composable
private fun PageBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = Color.Black.copy(alpha = 0.55f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    ) {
        Text(
            text,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun WebtoonReader(
    pages: List<String>,
    onPageChanged: (Int, Int) -> Unit,
    seekTarget: androidx.compose.runtime.MutableState<Int?>,
    onPageIndex: (Int) -> Unit,
    gapPadding: Int = 0,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last ->
                onPageIndex(last)
                if (pages.isNotEmpty()) onPageChanged(last, pages.size)
            }
    }
    // Honour bottom-bar slider seeks.
    LaunchedEffect(seekTarget) {
        snapshotFlow { seekTarget.value }
            .collect { target ->
                if (target != null) {
                    seekTarget.value = null
                    scope.launch { listState.scrollToItem(target.coerceIn(0, pages.lastIndex.coerceAtLeast(0))) }
                }
            }
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().background(Color.Black)) {
        items(pages) { url ->
            WebtoonPage(url = url, gapPadding = gapPadding)
        }
    }
}

/** A single webtoon strip: full-width image with per-page loading + error affordance. */
@Composable
private fun WebtoonPage(url: String, gapPadding: Int) {
    var state by remember(url) { mutableStateOf<PageState>(PageState.Loading) }
    var retryKey by remember(url) { mutableStateOf(0) }
    val painter = rememberAsyncImagePainter(
        model = coil3.request.ImageRequest.Builder(PlatformContext.INSTANCE)
            .data(url)
            .setParameter("retry", retryKey.toString())
            .build(),
        onState = { st ->
            state = when (st) {
                is State.Empty, is State.Loading -> PageState.Loading
                is State.Success -> PageState.Success
                is State.Error -> PageState.Error
            }
        },
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth(),
        )
        when (val s = state) {
            is PageState.Loading -> CircularProgressIndicator(
                modifier = Modifier.padding(vertical = 24.dp),
                color = Color.White,
            )
            is PageState.Error -> Column(
                modifier = Modifier.padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No se pudo cargar la página", color = Color.White, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { state = PageState.Loading; retryKey++ }) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Text("Reintentar", color = Color.White)
                }
            }
            PageState.Success -> Unit
        }
        if (gapPadding > 0) Spacer(Modifier.fillMaxWidth().height(gapPadding.dp))
    }
}

/**
 * Best-effort display-sleep suppression while the reader is foregrounded. On desktop the OS idle
 * timer is hard to reach portably, so this is an extension point that currently no-ops; the setting
 * is honoured structurally so a future platform hook can plug in here without UI changes.
 */
@Composable
private fun ReaderKeepAwake(enabled: Boolean) {
    DisposableEffect(enabled) {
        onDispose { }
    }
}
