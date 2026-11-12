package app.memories.gallery

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import app.memories.gallery.notify.openMemoryIdExtra
import app.memories.gallery.ui.AppRoot
import app.memories.gallery.ui.theme.MemoriesAppTheme

class MainActivity : ComponentActivity() {
    private val pendingMemoryId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) pendingMemoryId.value = intent.openMemoryIdExtra()
        enableEdgeToEdge()
        setContent {
            MemoriesAppTheme {
                AppRoot(
                    openMemoryId = pendingMemoryId.value,
                    onMemoryOpened = { pendingMemoryId.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.openMemoryIdExtra()?.let { pendingMemoryId.value = it }
    }
}
