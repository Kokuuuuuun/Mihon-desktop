package mihon.desktop.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import mihon.desktop.bridge.MangaDto
import mihon.desktop.bridge.Suwayomi

/**
 * A reusable adaptive cover grid used by Library and Browse. Each cell shows the cover, the title,
 * and optional unread/download badges (Mihon shows these on library items).
 */
@Composable
fun MangaCoverGrid(
    manga: List<MangaDto>,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    showBadges: Boolean = false,
    showInLibraryMark: Boolean = false,
    onLongClick: (MangaDto) -> Unit = {},
    onClick: (MangaDto) -> Unit,
) {
    LazyVerticalGrid(
        state = state,
        columns = GridCells.Adaptive(minSize = 130.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
    ) {
        items(manga, key = { it.id }) { m ->
            MangaCoverCard(
                manga = m,
                showBadges = showBadges,
                showInLibraryMark = showInLibraryMark,
                onLongClick = { onLongClick(m) },
                onClick = { onClick(m) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MangaCoverCard(
    manga: MangaDto,
    showBadges: Boolean = false,
    showInLibraryMark: Boolean = false,
    onLongClick: () -> Unit = {},
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.padding(4.dp).combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val url = Suwayomi.config.coverUrl(manga.thumbnailUrl)
            AsyncImage(
                model = url,
                contentDescription = manga.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (showBadges && (manga.unreadCount > 0 || manga.downloadCount > 0)) {
                Row(modifier = Modifier.align(Alignment.TopStart)) {
                    if (manga.unreadCount > 0) {
                        Badge(manga.unreadCount.toString(), MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                    }
                    if (manga.downloadCount > 0) {
                        Badge(manga.downloadCount.toString(), MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary)
                    }
                }
            }
            if (showInLibraryMark && manga.inLibrary) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                ) {
                    Text(
                        "En biblioteca",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Text(
            text = manga.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun Badge(text: String, bg: Color, fg: Color) {
    Box(
        modifier = Modifier
            .padding(2.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text(text, color = fg, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

/**
 * Compact list variant of the library: a single row per manga with a small cover thumbnail, the
 * title, the unread/download badge and the date added. Used when the user selects "Compacto" in
 * Ajustes (mirrors Mihon's compact library layout).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MangaCompactList(
    manga: List<MangaDto>,
    modifier: Modifier = Modifier,
    onLongClick: (MangaDto) -> Unit = {},
    onClick: (MangaDto) -> Unit,
) {
    androidx.compose.foundation.lazy.LazyColumn(modifier = modifier.fillMaxSize()) {
        lazyItems(manga, key = { it.id }) { m ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .combinedClickable(onClick = { onClick(m) }, onLongClick = { onLongClick(m) }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(72.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(6.dp)),
                ) {
                    AsyncImage(
                        model = Suwayomi.config.coverUrl(m.thumbnailUrl),
                        contentDescription = m.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Column(modifier = Modifier.padding(start = 8.dp).weight(1f)) {
                    Text(m.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(m.status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (m.unreadCount > 0) {
                    Badge(m.unreadCount.toString(), MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}
