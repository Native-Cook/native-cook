package com.ngocdevv.nativecook.blocks.onboarding.visorbootonboarding

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.filament.android.UiHelper
import java.io.IOException

/**
 * The Filament stage in a transparent TextureView. Each display frame it asks the controller for the state at the
 * frame's time and draws it, so it cannot drift from the Compose layers, which read the same controller at the same
 * Choreographer time. The film's clock waits (controller.ready) until the helmet is loaded.
 */
@Composable
internal fun VisorBootStage3D(controller: VisorBootController, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val stage = remember(controller) { VisorBootStageHost(controller) }

    LaunchedEffect(stage) {
        val assets = try {
            loadVisorBootStageAssets(context.applicationContext)
        } catch (error: IOException) {
            Log.e("VisorBootOnboarding", "assets/$VISOR_BOOT_ASSET_DIR could not be read; the helmet is not shown", error)
            controller.ready = true
            return@LaunchedEffect
        }
        stage.start(assets, displayRefreshRate(context))
    }

    DisposableEffect(stage) {
        onDispose { stage.destroy() }
    }

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

    AndroidView(
        factory = { viewContext -> TextureView(viewContext).also { it.isOpaque = false; stage.attach(it) } },
        modifier = modifier,
    )
}

private fun displayRefreshRate(context: Context): Float {
    val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
    return display?.refreshRate ?: 60f
}

private class VisorBootStageHost(private val controller: VisorBootController) :
    Choreographer.FrameCallback, UiHelper.RendererCallback {

    var visible = false
        set(value) {
            if (field == value) return
            field = value
            updateFrameLoop()
        }

    private var scene: VisorBootStageScene? = null
    private var surface: Surface? = null
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).apply { isOpaque = false }
    private val choreographer = Choreographer.getInstance()
    private var looping = false
    private var destroyed = false
    private var framesDrawn = 0

    fun attach(view: TextureView) {
        uiHelper.renderCallback = this
        uiHelper.attachTo(view)
    }

    fun start(assets: VisorBootStageAssets, refreshRate: Float) {
        if (destroyed || scene != null) return
        val scene = VisorBootStageScene(assets, refreshRate)
        this.scene = scene
        surface?.let { scene.setSurface(it, uiHelper.swapChainFlags) }
        if (surfaceWidth > 0 && surfaceHeight > 0) scene.setViewport(surfaceWidth, surfaceHeight)
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
        val scene = scene ?: return
        if (surface == null || surfaceWidth == 0) return
        scene.pose(controller.state(frameTimeNanos / 1e9), surfaceWidth, surfaceHeight)
        if (scene.render(frameTimeNanos)) {
            // the clock starts once a few frames are on screen (shader programs compile on the first ones)
            if (++framesDrawn >= 3) controller.ready = true
        }
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
        surface = null
    }
}
