package com.ngocdevv.nativecook.blocks.inputs.paperplanesend

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

/**
 * The 3D layer: Filament renders the sheet of paper into a see-through TextureView over the whole chat. Every display
 * frame asks the controller for the film's time at Choreographer's frame time (the same clock the Compose layers
 * read), so the flat sheet lands on the 2D paper at the hand-off. The view's own alpha is the layer's fade; while the
 * layer is transparent nothing is rendered.
 */
@Composable
internal fun PaperPlaneSendStage(controller: PaperPlaneSendController, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val stage = remember(controller) { PlaneStageHost(controller) }

    LaunchedEffect(stage) {
        val assets = try {
            loadPlaneStageAssets(context.applicationContext)
        } catch (error: IOException) {
            Log.e("PaperPlaneSend", "The 3D sheet is not shown: assets/$PLANE_ASSET_DIR could not be read.", error)
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
    // keyed by the stage, so a new controller (autoPlay or reduced motion changed) gets a new view and surface
    key(stage) {
        AndroidView(
            factory = { viewContext ->
                TextureView(viewContext).also {
                    it.isOpaque = false
                    it.alpha = 0f
                    stage.attach(it)
                }
            },
            modifier = modifier,
        )
    }
}

private fun displayRefreshRate(context: Context): Float {
    val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
    return display?.refreshRate ?: 60f
}

private class PlaneStageHost(private val controller: PaperPlaneSendController) : Choreographer.FrameCallback, UiHelper.RendererCallback {
    var visible = false
        set(value) {
            if (field == value) return
            field = value
            updateFrameLoop()
        }

    private var scene: PaperPlaneSendScene? = null
    private var textureView: TextureView? = null
    private var surface: Surface? = null
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).apply { isOpaque = false }
    private val choreographer = Choreographer.getInstance()
    private var looping = false
    private var destroyed = false
    private var drawnReady = 0
    private var shown = false

    fun attach(view: TextureView) {
        textureView = view
        uiHelper.renderCallback = this
        uiHelper.attachTo(view)
    }

    fun start(assets: PlaneStageAssets, refreshRate: Float) {
        if (destroyed || scene != null) return
        val s = PaperPlaneSendScene(assets, refreshRate)
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
        if (surface == null || surfaceWidth == 0 || !controller.hasGeometry) return
        // the film's clock starts once the sheet has been through two frames (parked, at no size)
        if (s.ready && drawnReady >= 2) controller.ready = true
        controller.frame(frameTimeNanos / 1e6)
        val t = controller.t
        val alpha = PlaneMotion.stageAlpha(t).toFloat()
        textureView?.let { if (it.alpha != alpha) it.alpha = alpha }
        // outside the 3D part of the film the layer is transparent: one last frame parks the plane, then nothing is drawn
        val shows = s.shows(t)
        if (!shows && !shown && drawnReady >= 2) return
        shown = shows
        s.apply(t, controller.geo)
        if (s.render(frameTimeNanos) && s.ready && drawnReady < 2) drawnReady++
    }

    override fun onNativeWindowChanged(surface: Surface) {
        this.surface = surface
        scene?.setSurface(surface, uiHelper.swapChainFlags)
        // a new surface starts empty: draw the parked frame again
        shown = true
    }

    override fun onDetachedFromSurface() {
        surface = null
        scene?.releaseSurface()
    }

    override fun onResized(width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        scene?.setViewport(width, height)
        shown = true
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        updateFrameLoop()
        uiHelper.detach()
        scene?.destroy()
        scene = null
        textureView = null
    }
}
