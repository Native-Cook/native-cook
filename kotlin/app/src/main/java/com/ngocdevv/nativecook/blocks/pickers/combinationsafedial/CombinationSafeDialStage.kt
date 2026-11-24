package com.ngocdevv.nativecook.blocks.pickers.combinationsafedial

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Choreographer
import android.view.Display
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.filament.android.UiHelper
import java.io.IOException

/** The 3D stage: Filament in a TextureView, asking the controller for its values at Choreographer's frame time. */
@Composable
internal fun CombinationSafeDialStage(controller: CombinationSafeDialController, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val stage = remember(controller) { SafeStageHost(controller) }

    LaunchedEffect(stage) {
        val assets = try {
            loadSafeStageAssets(context.applicationContext)
        } catch (error: IOException) {
            Log.e("CombinationSafeDial", "The 3D safe is not shown: assets/$SAFE_ASSET_DIR could not be read.", error)
            return@LaunchedEffect
        }
        stage.start(assets, displayRefreshRate(context))
    }
    DisposableEffect(stage) { onDispose { stage.destroy() } }
    DisposableEffect(stage, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> stage.visible = true
                Lifecycle.Event.ON_STOP -> stage.visible = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        stage.visible = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    key(stage) {
        AndroidView(
            factory = { viewContext -> TextureView(viewContext).also { it.isOpaque = false; stage.attach(it) } },
            modifier = modifier,
        )
    }
}

private fun displayRefreshRate(context: Context): Float {
    val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
    return display?.refreshRate ?: 60f
}

private class SafeStageHost(private val controller: CombinationSafeDialController) : Choreographer.FrameCallback, UiHelper.RendererCallback {
    var visible = false
        set(value) {
            if (field == value) return
            field = value
            updateFrameLoop()
        }

    private var scene: CombinationSafeDialScene? = null
    private var surface: Surface? = null
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).apply { isOpaque = false }
    private val choreographer = Choreographer.getInstance()
    private var looping = false
    private var destroyed = false
    private var drawnReady = 0

    fun attach(view: TextureView) {
        uiHelper.renderCallback = this
        uiHelper.attachTo(view)
    }

    fun start(assets: SafeStageAssets, refreshRate: Float) {
        if (destroyed || scene != null) return
        val s = CombinationSafeDialScene(assets, refreshRate)
        scene = s
        surface?.let { s.setSurface(it, uiHelper.swapChainFlags) }
        if (surfaceWidth > 0 && surfaceHeight > 0) s.setViewport(surfaceWidth, surfaceHeight)
        updateFrameLoop()
    }

    private fun updateFrameLoop() {
        val shouldLoop = visible && scene != null && !destroyed
        if (shouldLoop == looping) return
        looping = shouldLoop
        if (shouldLoop) choreographer.postFrameCallback(this) else choreographer.removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!looping) return
        choreographer.postFrameCallback(this)
        val s = scene ?: return
        if (surface == null || surfaceWidth == 0) return
        controller.frame(frameTimeNanos / 1e6)
        // the film's clock starts with the first frames that show the safe
        if (s.ready && drawnReady >= 2) controller.markReady()
        s.apply(controller.dialNow, controller.unlockNow, controller.shakeNow)
        if (s.render(frameTimeNanos) && s.ready) drawnReady++
    }

    override fun onNativeWindowChanged(surface: Surface) {
        this.surface = surface
        scene?.setSurface(surface, uiHelper.swapChainFlags)
    }

    override fun onDetachedFromSurface() {
        surface = null
        scene?.releaseSurface()
    }

    override fun onResized(width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        scene?.setViewport(width, height)
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        updateFrameLoop()
        uiHelper.detach()
        scene?.destroy()
        scene = null
    }
}
