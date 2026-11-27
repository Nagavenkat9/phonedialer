package app.memories.gallery.ui.settings

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenHidden: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember(context) { SettingsStore.get(context) }
    val themeMode by store.themeMode.collectAsState()
    val dynamicColor by store.dynamicColor.collectAsState()
    val duration by store.storyDurationSeconds.collectAsState()
    val videosMuted by store.autoPlayVideosMuted.collectAsState()
    val keepScreenOn by store.keepScreenOn.collectAsState()
    val (appName, version) = remember(context) { appInfo(context) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(Modifier.width(4.dp))
                Text("Settings", style = MaterialTheme.typography.titleLarge)
            }

            LazyColumn(
                Modifier.fillMaxSize().navigationBarsPadding(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                // Appearance
                item(key = "h-appearance") { SectionHeader("Appearance") }
                item(key = "theme") {
                    Column(Modifier.selectableGroup()) {
                        ThemeMode.entries.forEach { mode ->
                            ListItem(
                                headlineContent = { Text(mode.label) },
                                leadingContent = { RadioButton(selected = themeMode == mode, onClick = null) },
                                modifier = Modifier.selectable(
                                    selected = themeMode == mode,
                                    role = Role.RadioButton,
                                    onClick = { store.setThemeMode(mode) },
                                ),
                            )
                        }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    item(key = "dynamic") {
                        SwitchRow(
                            title = "Dynamic color",
                            subtitle = "Use colors from your wallpaper",
                            checked = dynamicColor,
                            onCheckedChange = store::setDynamicColor,
                        )
                    }
                }
                item(key = "d1") { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }

                // Memories
                item(key = "h-memories") { SectionHeader("Memories") }
                item(key = "duration") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("Photo duration in stories", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "How long each photo is shown",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.padding(top = 8.dp))
                        val options = SettingsStore.STORY_DURATIONS
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            options.forEachIndexed { index, seconds ->
                                SegmentedButton(
                                    selected = duration == seconds,
                                    onClick = { store.setStoryDurationSeconds(seconds) },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                                ) {
                                    Text("${seconds}s")
                                }
                            }
                        }
                    }
                }
                item(key = "muted") {
                    SwitchRow(
                        title = "Auto-play videos muted",
                        subtitle = "Videos in memories start without sound",
                        checked = videosMuted,
                        onCheckedChange = store::setAutoPlayVideosMuted,
                    )
                }
                item(key = "screen") {
                    SwitchRow(
                        title = "Keep screen on",
                        subtitle = "Prevent the screen from turning off while a memory plays",
                        checked = keepScreenOn,
                        onCheckedChange = store::setKeepScreenOn,
                    )
                }
                item(key = "hidden") {
                    ListItem(
                        headlineContent = { Text("Hidden from memories") },
                        supportingContent = { Text("Manage photos and memories you've hidden") },
                        leadingContent = { Icon(Icons.Outlined.VisibilityOff, contentDescription = null) },
                        trailingContent = {
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
                        },
                        modifier = Modifier.clickable(onClick = onOpenHidden),
                    )
                }
                item(key = "d2") { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }

                // About
                item(key = "h-about") { SectionHeader("About") }
                item(key = "about") {
                    ListItem(
                        headlineContent = { Text(appName) },
                        supportingContent = { Text("Version $version") },
                        leadingContent = { Icon(Icons.Outlined.Info, contentDescription = null) },
                    )
                }
                item(key = "about-note") {
                    Text(
                        "Everything stays on your phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(
            value = checked,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
    )
}

private fun appInfo(context: Context): Pair<String, String> {
    val pm = context.packageManager
    val name = runCatching { context.applicationInfo.loadLabel(pm).toString() }.getOrDefault("Memories")
    val version = runCatching {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0)
        }
        info.versionName ?: "?"
    }.getOrDefault("?")
    return name to version
}
