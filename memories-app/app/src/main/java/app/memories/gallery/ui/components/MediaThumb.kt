package app.memories.gallery.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.memories.gallery.data.GalleryItem
import app.memories.gallery.util.Fmt
import coil.compose.AsyncImage

private val badgeStyle = TextStyle(
    color = Color.White,
    fontSize = 12.sp,
    fontWeight = FontWeight.Medium,
    shadow = Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 4f),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaThumb(
    item: GalleryItem,
    isFavorite: Boolean,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    overlay: @Composable (() -> Unit)? = null,
    /** null = not in selection mode; true/false = selection state shown with a check circle. */
    selected: Boolean? = null,
    onClick: () -> Unit,
) {
    val inSelection = selected != null
    val isSelected = selected == true
    val scale by animateFloatAsState(if (isSelected) 0.85f else 1f, label = "thumbScale")
    val corner by animateFloatAsState(if (isSelected) 12f else 0f, label = "thumbCorner")
    Box(
        modifier
            .aspectRatio(1f)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(corner.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            ThumbContent(item, isFavorite)
        }
        if (inSelection) {
            if (isSelected) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Selected",
                        tint = Color(0xFF1A73E8),
                        modifier = Modifier.size(24.dp),
                    )
                }
            } else {
                Icon(
                    Icons.Outlined.Circle,
                    contentDescription = "Not selected",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(22.dp),
                )
            }
        }
        overlay?.invoke()
    }
}

@Composable
private fun ThumbContent(item: GalleryItem, isFavorite: Boolean) {
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (item.isVideo || isFavorite) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.25f),
                            0.3f to Color.Transparent,
                            0.7f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.25f),
                        ),
                    ),
            )
        }
        if (item.isVideo) {
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(Fmt.duration(item.durationMs), style = badgeStyle)
                Spacer(Modifier.width(2.dp))
                Icon(Icons.Filled.PlayCircle, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        if (isFavorite) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = "Favorite",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .size(16.dp),
            )
        }
    }
}
