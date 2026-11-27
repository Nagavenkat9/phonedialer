package app.memories.gallery.ui.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) {
    System("System default"),
    Light("Light"),
    Dark("Dark"),
}

/**
 * App-level settings backed by their own SharedPreferences file ("app_settings").
 * Obtain via [SettingsStore.get]; every value is exposed as a [StateFlow] so UI updates live.
 */
class SettingsStore private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(readThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC_COLOR, true))
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _storyDurationSeconds = MutableStateFlow(readDuration())
    /** Seconds each photo is shown in a memory story: one of [STORY_DURATIONS]. */
    val storyDurationSeconds: StateFlow<Int> = _storyDurationSeconds.asStateFlow()

    private val _autoPlayVideosMuted = MutableStateFlow(prefs.getBoolean(KEY_VIDEOS_MUTED, false))
    /** Whether videos inside memories should auto-play muted. */
    val autoPlayVideosMuted: StateFlow<Boolean> = _autoPlayVideosMuted.asStateFlow()

    private val _keepScreenOn = MutableStateFlow(prefs.getBoolean(KEY_KEEP_SCREEN_ON, true))
    /** Whether the screen should stay on while a memory is playing. */
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
        _dynamicColor.value = enabled
    }

    fun setStoryDurationSeconds(seconds: Int) {
        val value = if (seconds in STORY_DURATIONS) seconds else DEFAULT_DURATION
        prefs.edit().putInt(KEY_STORY_DURATION, value).apply()
        _storyDurationSeconds.value = value
    }

    fun setAutoPlayVideosMuted(muted: Boolean) {
        prefs.edit().putBoolean(KEY_VIDEOS_MUTED, muted).apply()
        _autoPlayVideosMuted.value = muted
    }

    fun setKeepScreenOn(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, enabled).apply()
        _keepScreenOn.value = enabled
    }

    private fun readThemeMode(): ThemeMode {
        val name = prefs.getString(KEY_THEME_MODE, null) ?: return ThemeMode.System
        return ThemeMode.entries.firstOrNull { it.name == name } ?: ThemeMode.System
    }

    private fun readDuration(): Int {
        val v = prefs.getInt(KEY_STORY_DURATION, DEFAULT_DURATION)
        return if (v in STORY_DURATIONS) v else DEFAULT_DURATION
    }

    companion object {
        private const val FILE = "app_settings"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_STORY_DURATION = "story_duration_seconds"
        private const val KEY_VIDEOS_MUTED = "autoplay_videos_muted"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"

        const val DEFAULT_DURATION = 5
        val STORY_DURATIONS: List<Int> = listOf(3, 5, 8)

        @Volatile
        private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context.applicationContext).also { instance = it }
            }
    }
}
