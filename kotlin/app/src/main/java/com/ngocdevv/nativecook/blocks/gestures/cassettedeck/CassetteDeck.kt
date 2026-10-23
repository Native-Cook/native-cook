package com.ngocdevv.nativecook.blocks.gestures.cassettedeck

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive

/** A full-screen mechanical cassette deck faithfully ported from the React Native reference. */
@Composable
fun CassetteDeck(
    autoPlay: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val activity = LocalActivity.current
    val reducedMotion = rememberReducedMotion()
    val safeDrawing = WindowInsets.safeDrawing
    val topInset = safeDrawing.getTop(density) / density.density.toDouble()
    val bottomInset = safeDrawing.getBottom(density) / density.density.toDouble()
    val assets = remember(context) { CassetteDeckAssets(context) }
    val session = remember(autoPlay, reducedMotion) {
        CassetteDeckSession(reducedMotion = reducedMotion, autoPlay = autoPlay && !reducedMotion)
    }
    val wake = remember(session) { Channel<Unit>(Channel.CONFLATED) }
    var frameInvalidation by remember { mutableLongStateOf(0L) }
    var accessibilityVersion by remember { mutableIntStateOf(session.model.version) }

    DisposableEffect(activity, view) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        if (controller == null) {
            onDispose { }
        } else {
            val previousStatus = controller.isAppearanceLightStatusBars
            val previousNavigation = controller.isAppearanceLightNavigationBars
            controller.isAppearanceLightStatusBars = true
            controller.isAppearanceLightNavigationBars = true
            onDispose {
                controller.isAppearanceLightStatusBars = previousStatus
                controller.isAppearanceLightNavigationBars = previousNavigation
            }
        }
    }

    LaunchedEffect(session, wake) {
        var previousNanos = 0L
        while (isActive) {
            if (!session.needsFrames()) {
                wake.receive()
                previousNanos = 0L
            }
            val nowNanos = withFrameNanos { it }
            val dt = if (previousNanos == 0L) 0.016 else (nowNanos - previousNanos) / 1_000_000_000.0
            previousNanos = nowNanos
            if (session.advance(dt)) frameInvalidation = nowNanos
            session.model.events.forEach(view::performCassetteDeckHaptic)
            session.model.events.clear()
            if (accessibilityVersion != session.model.version) accessibilityVersion = session.model.version
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(CassetteDeckTheme.plate)),
    ) {
        val width = maxWidth.value.toDouble()
        val height = maxHeight.value.toDouble()
        val layout = remember(width, height, topInset, bottomInset) {
            CassetteDeckLayout(width, height, topInset, bottomInset)
        }
        val renderer = remember(layout, assets, density.density) {
            CassetteDeckRenderer(layout, assets, density.density)
        }
        DisposableEffect(renderer) {
            onDispose { renderer.recycle() }
        }

        Box(
            Modifier
                .fillMaxSize()
                .cassetteDeckTouch(session, layout, wake),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                frameInvalidation
                drawIntoCanvas { composeCanvas ->
                    val nativeCanvas = composeCanvas.nativeCanvas
                    val saved = nativeCanvas.save()
                    nativeCanvas.scale(density.density, density.density)
                    renderer.draw(nativeCanvas, session.model)
                    nativeCanvas.restoreToCount(saved)
                }
            }

            @Suppress("UNUSED_VARIABLE")
            val accessibilityState = accessibilityVersion
            val well = CassetteDeckTheme.well
            Box(
                Modifier
                    .offset(
                        (layout.originX + well.x * layout.scale).dp,
                        (layout.originY + well.y * layout.scale).dp,
                    )
                    .size((well.width * layout.scale).dp, (well.height * layout.scale).dp)
                    .semantics {
                        role = Role.Image
                        contentDescription = "${CassetteDeckTheme.title}, side ${CassetteDeckTheme.side}. ${session.model.mode.accessibilityLabel}."
                    },
            )
            CassetteDeckTheme.keyNames.forEachIndexed { index, label ->
                Box(
                    Modifier
                        .offset(
                            (layout.originX + CassetteDeckTheme.keyX(index) * layout.scale).dp,
                            (layout.originY + CassetteDeckTheme.KEYS_Y * layout.scale).dp,
                        )
                        .size(
                            (CassetteDeckTheme.KEY_WIDTH * layout.scale).dp,
                            (CassetteDeckTheme.KEYS_HEIGHT * layout.scale).dp,
                        )
                        .semantics {
                            role = Role.Button
                            contentDescription = label
                            onClick(label) {
                                session.accessibilityPress(index)
                                wake.trySend(Unit)
                                true
                            }
                        },
                )
            }
        }
    }
}

private val CassetteDeckMode.accessibilityLabel: String
    get() = when (this) {
        CassetteDeckMode.Stop -> "Stopped"
        CassetteDeckMode.Play -> "Playing"
        CassetteDeckMode.FastForward -> "Fast forwarding"
        CassetteDeckMode.Rewind -> "Rewinding"
        CassetteDeckMode.Pause -> "Paused"
    }
