package com.ngocdevv.nativecook

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ngocdevv.nativecook.gallery.GalleryApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The gallery is dark, so the system bars use light icons. A block draws under both bars.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // `--es block <slug>` on the launch intent opens that block at once, without a tap.
        val initialSlug = intent.getStringExtra("block")
        setContent {
            GalleryApp(initialSlug = initialSlug)
        }
    }
}
