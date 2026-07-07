package mihon.desktop.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import mihon.desktop.bridge.ChapterDto
import mihon.desktop.bridge.Suwayomi

/**
 * Shared list used by the Updates and History tabs: each row shows the manga cover, its title and
 * the chapter, and opens the reader on click.
 */
@Composable
fun ChapterFeedList(
    chapters: List<ChapterDto>,
    modifier: Modifier = Modifier,
    onClick: (ChapterDto) -> Unit,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(chapters, key = { it.id }) { chapter ->
            ChapterFeedRow(chapter) { onClick(chapter) }
            HorizontalDivider()
        }
    }
}

@Composable
private fun ChapterFeedRow(chapter: ChapterDto, onClick: () -> Unit) {
    val manga = chapter.manga
    ListItem(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        leadingContent = {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(4.dp)),
            ) {
                AsyncImage(
                    model = Suwayomi.config.coverUrl(manga?.thumbnailUrl),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        },
        headlineContent = {
            Text(
                manga?.title ?: "—",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
            )
        },
        supportingContent = {
            Text(
                chapter.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = if (chapter.isRead) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        },
    )
    Spacer(Modifier.width(0.dp))
}
