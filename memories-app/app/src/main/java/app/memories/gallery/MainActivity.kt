package app.memories.gallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.memories.gallery.ui.AppRoot
import app.memories.gallery.ui.theme.MemoriesAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MemoriesAppTheme {
                AppRoot()
            }
        }
    }
}
