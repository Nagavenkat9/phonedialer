package app.memories.gallery.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.memories.gallery.memories.Memory
import app.memories.gallery.util.Fmt
import coil.compose.AsyncImage
import java.time.LocalDate

/**
 * Shown when a memory is long-pressed: the day it is about, how long ago it was,
 * what's inside, and quick actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaySheet(
    memory: Memory,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onHide: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val today = LocalDate.now()
    val days = memory.days
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text(
                memory.title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            val day = memory.day ?: days.singleOrNull()
            if (day != null) {
                Text(day.format(Fmt.weekday), style = MaterialTheme.typography.titleMedium)
                Text(
                    day.format(Fmt.dayMonthYearLong),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    Fmt.relative(day, today),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (days.isNotEmpty()) {
                Text(
                    "${days.first().format(Fmt.short)} – ${days.last().format(Fmt.short)}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Across ${days.size} days · ${memory.subtitle}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (memory.photoCount > 0) {
                    InfoChip(Icons.Outlined.Photo, if (memory.photoCount == 1) "1 photo" else "${memory.photoCount} photos")
                }
                if (memory.videoCount > 0) {
                    InfoChip(Icons.Outlined.Videocam, if (memory.videoCount == 1) "1 video" else "${memory.videoCount} videos")
                }
                if (memory.favoriteCount > 0) {
                    InfoChip(Icons.Filled.Favorite, "${memory.favoriteCount}")
                }
            }

            Spacer(Modifier.height(12.dp))
            val preview = memory.items.take(8)
            preview.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { item ->
                        AsyncImage(
                            model = item.uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            if (days.size > 1) {
                Spacer(Modifier.height(8.dp))
                Text("Days in this memory", style = MaterialTheme.typography.titleSmall)
                val countByDay = memory.items.groupingBy { it.date }.eachCount()
                days.sortedDescending().take(8).forEach { d ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenDay(d) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.format(Fmt.full), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${Fmt.relative(d, today)} · ${countByDay[d] ?: 0} in memory",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(onClick = onPlay, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("Play memory")
            }
            if (day != null) {
                OutlinedButton(onClick = { onOpenDay(day) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.CalendarMonth, null)
                    Spacer(Modifier.width(8.dp))
                    Text("See everything from this day")
                }
            }
            TextButton(onClick = onHide, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.VisibilityOff, null)
                Spacer(Modifier.width(8.dp))
                Text("Don't show this memory")
            }
            Box(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun InfoChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    AssistChip(
        onClick = {},
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
    )
}
