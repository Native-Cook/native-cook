package com.ngocdevv.nativecook.blocks.gestures.chooseyourball

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
import androidx.compose.runtime.SideEffect
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
 * The ball, rendered by Filament into a transparent TextureView that composites with the Compose
 * content around it like any other view.
 *
 * `offset` (the carousel's position in slots) and `tilt` (-1…1) are read once per display frame,
 * outside composition: a swipe turns the shell and a drag orbits the camera without recomposing
 * anything. `variant` changes the paint, and comes from composition because it changes once per
 * ball. With `reducedMotion` the camera stays level.
 */
@Composable
internal fun BallStage3D(
    offset: () -> Float,
    tilt: () -> Float,
    reducedMotion: Boolean,
    variant: BallId,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val stage = remember { BallStageHost() }

    SideEffect {
        stage.offset = offset
        stage.tilt = tilt
        stage.reducedMotion = reducedMotion
        stage.variant = variant
    }

    // The engine is created once, when the files are in memory, and lives as long as the stage.
    LaunchedEffect(stage) {
        val assets = try {
            loadBallStageAssets(context.applicationContext)
        } catch (error: IOException) {
            // The block was copied without its files: the stage stays empty, the screen works.
            Log.e(
                "ChooseYourBall",
                "The 3D ball is not shown: assets/$BALL_ASSET_DIR could not be read. Copy that folder " +
                    "(ball-filament.glb, warehouse-ibl.ktx, shells/*.png) into the app's assets.",
                error,
            )
            return@LaunchedEffect
        }
        stage.start(assets, displayRefreshRate(context))
    }

    // The engine goes when the composable leaves the composition, and only then.
    DisposableEffect(stage) {
        onDispose { stage.destroy() }
    }

    // Frames are drawn only while the screen is visible. The owner of the lifecycle can change
    // under a mounted stage; that swaps the observer and nothing else. The surface itself goes
    // away in the background and comes back later, which `BallStageHost` follows through UiHelper.
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

/**
 * After a change, every frame is drawn for this many more. One frame is not enough after a start:
 * the first one came out empty on the test device (Filament may leave an object out of a frame
 * while its shader program is still being compiled).
 */
private const val SETTLE_FRAMES = 90

/** At rest, one display frame in this many is drawn, in case anything was still missing. */
private const val KEEP_ALIVE_INTERVAL = 30

/**
 * Owns the scene, its surface and its frame loop.
 *
 * Every frame is drawn while the pose, the paint or the surface is changing and for a moment
 * after; a ball at rest is redrawn only now and then.
 */
private class BallStageHost : Choreographer.FrameCallback, UiHelper.RendererCallback {
    var offset: () -> Float = { 0f }
    var tilt: () -> Float = { 0f }
    var reducedMotion = false
    var variant = BallId.POKE

    var visible = false
        set(value) {
            if (field == value) return
            field = value
            // Back on screen: draw at once, whatever happened to the surface meanwhile.
            if (value) surfaceChanged = true
            updateFrameLoop()
        }

    private var scene: BallStageScene? = null
    private var surface: Surface? = null
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).apply {
        // Not opaque: the TextureView blends over the backdrop, and the swap chain gets alpha.
        isOpaque = false
    }
    private val choreographer = Choreographer.getInstance()
    private var looping = false
    private var destroyed = false

    // What the frame on screen shows; a difference from the live values asks for a new frame.
    private var drawnOffset = Float.NaN
    private var drawnTilt = Float.NaN
    private var drawnVariant: BallId? = null
    private var surfaceChanged = false
    private var settleFrames = 0
    private var idleFrames = 0

    /** The view's surface comes and goes on its own schedule; UiHelper reports it from here on. */
    fun attach(view: TextureView) {
        uiHelper.renderCallback = this
        uiHelper.attachTo(view)
    }

    fun start(assets: BallStageAssets, refreshRate: Float) {
        if (destroyed || scene != null) return
        val scene = BallStageScene(assets, refreshRate)
        this.scene = scene
        // The surface is usually there before the files are read.
        surface?.let { scene.setSurface(it, uiHelper.swapChainFlags) }
        if (surfaceWidth > 0 && surfaceHeight > 0) scene.setViewport(surfaceWidth, surfaceHeight)
        surfaceChanged = true
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

        val offset = offset()
        val tilt = if (reducedMotion) 0f else tilt()
        val variant = variant
        val changed = surfaceChanged || offset != drawnOffset || tilt != drawnTilt || variant != drawnVariant
        when {
            changed -> settleFrames = SETTLE_FRAMES
            settleFrames > 0 -> settleFrames--
            // At rest the last frame stays on screen between the occasional redraws.
            ++idleFrames < KEEP_ALIVE_INTERVAL -> return
        }
        idleFrames = 0

        if (variant != drawnVariant) {
            scene.setVariant(variant)
            drawnVariant = variant
        }
        scene.setPose(offset, tilt)
        // When Filament skips the frame the pose is not recorded as drawn, so the next vsync retries.
        if (scene.render(frameTimeNanos)) {
            drawnOffset = offset
            drawnTilt = tilt
            surfaceChanged = false
        }
    }

    override fun onNativeWindowChanged(surface: Surface) {
        this.surface = surface
        scene?.setSurface(surface, uiHelper.swapChainFlags)
        surfaceChanged = true
    }

    override fun onDetachedFromSurface() {
        surface = null
        scene?.releaseSurface()
    }

    override fun onResized(width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        scene?.setViewport(width, height)
        surfaceChanged = true
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
