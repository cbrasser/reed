package app.reed.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.reed.ui.theme.Margin
import coil3.compose.AsyncImage
import java.io.File

private val CoverShape = androidx.compose.foundation.shape.RoundedCornerShape(3.dp)

@Composable
fun BookCover(
    title: String,
    author: String?,
    coverPath: String?,
    modifier: Modifier = Modifier,
    isPrivate: Boolean = false,
    elevation: Dp = 2.dp,
) {
    Box(
        modifier
            .aspectRatio(2f / 3f)
            .shadow(elevation, CoverShape, clip = false)
            .clip(CoverShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, Margin.colors.rule.copy(alpha = 0.6f), CoverShape),
    ) {
        if (coverPath != null) {
            AsyncImage(
                model = File(coverPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            TypeCover(title, author)
        }
        if (isPrivate) {
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp),
            ) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = "Private",
                    modifier = Modifier.padding(5.dp),
                )
            }
        }
    }
}

/** A cover for books that ship without one: the title, set plainly. */
@Composable
private fun TypeCover(title: String, author: String?) {
    Box(Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 10.dp)) {
        Column(Modifier.fillMaxSize()) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, lineHeight = 17.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            if (author != null) {
                Text(
                    author,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
