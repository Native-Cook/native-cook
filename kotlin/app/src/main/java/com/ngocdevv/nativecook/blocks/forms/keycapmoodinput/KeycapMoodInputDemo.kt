package com.ngocdevv.nativecook.blocks.forms.keycapmoodinput

import android.app.Activity
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ngocdevv.nativecook.shared.rememberReducedMotion

private val labels = arrayOf("Subject", "Verb", "Intensity", "Mood")

/** Four sculpted keys. The frame clock and input both step the same native simulation. */
@Composable
fun KeycapMoodInput(autoPlay: Boolean = false, onTouch: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    val context = LocalContext.current
    val density = LocalDensity.current
    val latestOnTouch by rememberUpdatedState(onTouch)
    var nativeView by remember { mutableStateOf<KeycapView?>(null) }
    var accessible by remember { mutableStateOf(IntArray(4)) }
    var fanOpen by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val activity = context as? Activity
        val decor = activity?.window?.decorView
        val previous = decor?.systemUiVisibility ?: 0
        decor?.systemUiVisibility = previous or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        onDispose { decor?.systemUiVisibility = previous }
    }

    LaunchedEffect(nativeView) {
        val view = nativeView ?: return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            view.advance((now - last) / 1_000_000.0)
            last = now
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = {
                KeycapView(it, reduced, autoPlay, { latestOnTouch?.invoke() }) { state ->
                    val next = state.choices.copyOf()
                    if (!accessible.contentEquals(next)) accessible = next
                    if (fanOpen != state.fanOpen) fanOpen = state.fanOpen
                }.also { nativeView = it }
            },
            modifier = Modifier.fillMaxSize(),
        )
        val width = with(density) { maxWidth.toPx().toDouble() }
        val height = with(density) { maxHeight.toPx().toDouble() }
        val scale = density.density
        val layout = remember(width, height) { KeycapLayout(width / scale, height / scale) }
        for (index in 0..3) {
            val hit = layout.hits[index]
            val current = if (index == 3) keycapMoodNames[accessible[3]] else keycapWords[index][accessible[index]]
            Box(
                Modifier
                    .offset(hit.x.dp, hit.y.dp)
                    .size(hit.width.dp, hit.height.dp)
                    .semantics {
                        contentDescription = labels[index]
                        stateDescription = current
                        progressBarRangeInfo = ProgressBarRangeInfo(accessible[index].toFloat(),
                            0f..(if (index == 3) 5f else (keycapWords[index].size - 1).toFloat()))
                        onClick(label = "Next $current") { nativeView?.accessibilityAction("next", index); true }
                        customActions = listOf(
                            CustomAccessibilityAction("Increment") { nativeView?.accessibilityAction("next", index); true },
                            CustomAccessibilityAction("Decrement") { nativeView?.accessibilityAction("previous", index); true },
                        ) + if (index == 3) listOf(CustomAccessibilityAction("Show moods") {
                            nativeView?.accessibilityAction("toggleFan"); true
                        }) else emptyList()
                    },
            )
        }
        if (fanOpen) for (index in 0..5) {
            val point = layout.minis[index]
            val mood = keycapFanOrder[index]
            Box(
                Modifier
                    .offset((point.x - layout.mini / 2).dp, (point.y - layout.mini / 2).dp)
                    .size(layout.mini.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = keycapMoodNames[mood].replaceFirstChar { it.uppercase() }
                        selected = mood == accessible[3]
                        onClick { nativeView?.accessibilityAction("selectMood", target = mood); true }
                    },
            )
        }
    }
}

@Composable
fun KeycapMoodInputDemo() {
    KeycapMoodInput(autoPlay = true)
}
