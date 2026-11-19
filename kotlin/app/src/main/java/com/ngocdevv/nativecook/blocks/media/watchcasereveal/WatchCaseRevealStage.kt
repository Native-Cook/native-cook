package com.ngocdevv.nativecook.blocks.media.watchcasereveal

import android.content.Context
import android.hardware.display.DisplayManager
import android.opengl.Matrix
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
import com.google.android.filament.Camera
import com.google.android.filament.ColorGrading
import com.google.android.filament.Colors
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Renderer
import com.google.android.filament.SwapChain
import com.google.android.filament.Texture
import com.google.android.filament.ToneMapper
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.google.android.filament.utils.KTX1Loader
import com.google.android.filament.utils.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.pow

/** The block's folder in the app's assets. */
private const val ASSET_DIR = "watch-case-reveal"

private class WcrFiles(val model: ByteBuffer, val environment: ByteBuffer)

private suspend fun loadFiles(context: Context): WcrFiles = withContext(Dispatchers.IO) {
    fun buffer(name: String): ByteBuffer {
        val bytes = context.assets.open("$ASSET_DIR/$name").use { it.readBytes() }
        // Filament reads from native memory: the buffer has to be a direct one.
        return ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).apply { rewind() }
    }
    WcrFiles(buffer("watch.glb"), buffer("watch-studio-ibl.ktx"))
}

private fun srgbToLinear(c: Int): FloatArray = FloatArray(3) { i ->
    val v = ((c shr (16 - 8 * i)) and 255) / 255.0
    (if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)).toFloat()
}

/**
 * The Filament scene of WatchStage3D.tsx: the walnut case, its pillow and the watch from watch.glb, lit by the studio
 * IBL (30,000), a warm key (72,000 lx, 5400 K) and a cool fill (15,000 lx, 7000 K), no shadows; ACES (legacy) tone
 * mapping at exposure 0 and react-native-filament's camera (f/16, 1/125 s, ISO 100, 30 deg horizontal field); bloom
 * 0.05 over 5 levels, FXAA, no TAA, no ambient occlusion; a see-through frame over the Compose backdrop.
 * Everything is called on the main thread.
 */
private class WcrScene3D(files: WcrFiles, refreshRate: Float) {
    private val engine: Engine = Engine.create()
    private val renderer: Renderer = engine.createRenderer()
    private val scene = engine.createScene()
    private val view = engine.createView()
    private val cameraEntity = EntityManager.get().create()
    private val camera: Camera = engine.createCamera(cameraEntity)
    private val materialProvider = UbershaderProvider(engine)
    private val assetLoader = AssetLoader(engine, materialProvider, EntityManager.get())
    private val resourceLoader = ResourceLoader(engine, true)
    private val asset: FilamentAsset = checkNotNull(assetLoader.createAsset(files.model)) { "watch.glb did not load" }
    private val environment: Texture
    private val indirectLight: IndirectLight
    private val colorGrading: ColorGrading
    private val key: Int
    private val fill: Int
    private val lid: Int
    private val cushion: Int
    private val second: Int
    private val dial: MaterialInstance?
    private var swapChain: SwapChain? = null
    private var aspect = 0.0
    private val m = FloatArray(16)
    private val a = FloatArray(16)
    private val b = FloatArray(16)
    private val cam = DoubleArray(6)
    private var lastDial = ""
    private val dialTable = WcrScene.DIALS.map { srgbToLinear(it.color) }

    init {
        renderer.clearOptions = Renderer.ClearOptions().apply {
            clear = true
            clearColor = doubleArrayOf(0.0, 0.0, 0.0, 0.0)
        }
        renderer.setDisplayInfo(Renderer.DisplayInfo().apply { this.refreshRate = refreshRate })
        view.scene = scene
        view.camera = camera
        view.blendMode = View.BlendMode.TRANSLUCENT
        view.antiAliasing = View.AntiAliasing.FXAA
        view.temporalAntiAliasingOptions = View.TemporalAntiAliasingOptions().apply { enabled = false }
        view.ambientOcclusionOptions = View.AmbientOcclusionOptions().apply { enabled = false }
        view.bloomOptions = View.BloomOptions().apply {
            enabled = true
            strength = 0.05f
            levels = 5
            threshold = true
            highlight = 1000f
            quality = View.QualityLevel.MEDIUM
        }
        camera.setExposure(16f, 1f / 125f, 100f)
        colorGrading = ColorGrading.Builder().toneMapper(ToneMapper.ACESLegacy()).exposure(WcrScene.EXPOSURE).build(engine)
        view.colorGrading = colorGrading

        resourceLoader.loadResources(asset)
        asset.releaseSourceData()
        scene.addEntities(asset.entities)
        val rm = engine.renderableManager
        for (e in asset.renderableEntities) {
            val i = rm.getInstance(e)
            rm.setCastShadows(i, false)
            rm.setReceiveShadows(i, false)
        }
        lid = asset.getFirstEntityByName("Lid")
        cushion = asset.getFirstEntityByName("Cushion")
        second = asset.getFirstEntityByName("HandSecond")
        val d = asset.getFirstEntityByName("Dial")
        dial = if (d != 0 && rm.hasComponent(d)) rm.getMaterialInstanceAt(rm.getInstance(d), 0) else null

        environment = KTX1Loader.createTexture(engine, files.environment)
        indirectLight = IndirectLight.Builder()
            .reflections(environment)
            .irradiance(3, checkNotNull(KTX1Loader.getSphericalHarmonics(files.environment)) { "the IBL has no harmonics" })
            .intensity(WcrScene.IBL_INTENSITY)
            .build(engine)
        scene.indirectLight = indirectLight
        key = addLight(WcrScene.KEY_DIRECTION, WcrScene.KEY_LUX, 5400f)
        fill = addLight(WcrScene.FILL_DIRECTION, WcrScene.FILL_LUX, 7000f)
    }

    private fun addLight(dir: FloatArray, lux: Float, kelvin: Float): Int {
        val e = EntityManager.get().create()
        val c = Colors.cct(kelvin)
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(c[0], c[1], c[2])
            .intensity(lux)
            .direction(dir[0], dir[1], dir[2])
            .castShadows(false)
            .build(engine, e)
        scene.addEntity(e)
        return e
    }

    fun setSurface(surface: Surface, flags: Long) {
        releaseSurface()
        swapChain = engine.createSwapChain(surface, flags)
    }

    fun releaseSurface() {
        val s = swapChain ?: return
        swapChain = null
        engine.destroySwapChain(s)
        engine.flushAndWait()
    }

    fun setViewport(w: Int, h: Int) {
        view.viewport = Viewport(0, 0, w, h)
        val asp = w.toDouble() / h
        if (asp != aspect) {
            aspect = asp
            camera.setProjection(WcrScene.HFOV_DEG, asp, WcrScene.CAMERA_NEAR, WcrScene.CAMERA_FAR, Camera.Fov.HORIZONTAL)
        }
    }

    private fun setLocal(entity: Int, mat: FloatArray) {
        if (entity == 0) return
        val tm = engine.transformManager
        tm.setTransform(tm.getInstance(entity), mat)
    }

    /** Poses the scene for one frame (WatchStage3D.tsx's render callback). */
    fun apply(p: WcrPose) {
        WcrMotion.cameraAt(p.open, p.macro, p.t, cam)
        camera.lookAt(cam[0], cam[1], cam[2], cam[3], cam[4], cam[5], 0.0, 1.0, 0.0)

        // the lid: a turn about x, then back to its hinge
        Matrix.setRotateM(m, 0, Math.toDegrees(-WcrScene.LID_OPEN * WcrMotion.lidOf(p.open)).toFloat(), 1f, 0f, 0f)
        m[12] = WcrScene.LID_HINGE[0]; m[13] = WcrScene.LID_HINGE[1]; m[14] = WcrScene.LID_HINGE[2]
        setLocal(lid, m)

        // the pillow: tipped about x, turned about y, lifted
        Matrix.setRotateM(a, 0, Math.toDegrees(WcrScene.TILT * WcrMotion.tipOf(p.open)).toFloat(), 1f, 0f, 0f)
        Matrix.setRotateM(b, 0, Math.toDegrees(p.yaw).toFloat(), 0f, 1f, 0f)
        Matrix.multiplyMM(m, 0, b, 0, a, 0)
        m[13] = (WcrScene.CUSHION_Y + WcrScene.LIFT * WcrMotion.liftOf(p.open)).toFloat()
        setLocal(cushion, m)

        // the second hand ticks once a second (a 80 ms ease-out step)
        val whole = floor(p.secs)
        val frac = WcrMotion.clamp01((p.secs - whole) / 0.08)
        val ang = (whole + (1 - (1 - frac).pow(3))) * PI / 30
        Matrix.setRotateM(m, 0, Math.toDegrees(-ang).toFloat(), 0f, 1f, 0f)
        setLocal(second, m)

        // the dial's colour, blended between two dials (written only when it changes)
        val mi = dial ?: return
        val da = p.dialFrom
        val db = p.dialTo
        val k = WcrMotion.easeInOutSine(WcrMotion.clamp01(p.dialK))
        val keyStr = "$da,$db,${Math.round(k * 400)}"
        if (keyStr != lastDial) {
            lastDial = keyStr
            val ca = dialTable[da]
            val cb = dialTable[db]
            val kf = k.toFloat()
            mi.setParameter("baseColorFactor", ca[0] + (cb[0] - ca[0]) * kf, ca[1] + (cb[1] - ca[1]) * kf, ca[2] + (cb[2] - ca[2]) * kf, 1f)
            val dA = WcrScene.DIALS[da]
            val dB = WcrScene.DIALS[db]
            mi.setParameter("metallicFactor", dA.metal + (dB.metal - dA.metal) * kf)
            mi.setParameter("roughnessFactor", dA.rough + (dB.rough - dA.rough) * kf)
        }
    }

    fun render(frameTimeNanos: Long): Boolean {
        val target = swapChain ?: return false
        if (!renderer.beginFrame(target, frameTimeNanos)) return false
        renderer.render(view)
        renderer.endFrame()
        return true
    }

    fun destroy() {
        releaseSurface()
        val em = EntityManager.get()
        for (l in intArrayOf(key, fill)) {
            scene.removeEntity(l)
            engine.destroyEntity(l)
            em.destroy(l)
        }
        scene.removeEntities(asset.entities)
        assetLoader.destroyAsset(asset)
        resourceLoader.destroy()
        assetLoader.destroy()
        materialProvider.destroyMaterials()
        materialProvider.destroy()
        engine.destroyIndirectLight(indirectLight)
        engine.destroyTexture(environment)
        engine.destroyColorGrading(colorGrading)
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(cameraEntity)
        em.destroy(cameraEntity)
        engine.destroy()
    }

    companion object {
        init {
            Utils.init()
        }
    }
}

/** Owns the scene, its surface and the frame loop (Choreographer); poses the scene from the controller each vsync. */
private class WcrStageHost(private val controller: WcrController) : Choreographer.FrameCallback, UiHelper.RendererCallback {
    private var scene: WcrScene3D? = null
    private var surface: Surface? = null
    private var width = 0
    private var height = 0
    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).apply { isOpaque = false }
    private val choreographer = Choreographer.getInstance()
    private var looping = false
    private var destroyed = false
    private var drawn = 0
    var onReady: () -> Unit = {}

    fun attach(view: TextureView) {
        uiHelper.renderCallback = this
        uiHelper.attachTo(view)
    }

    fun start(files: WcrFiles, refreshRate: Float) {
        if (destroyed || scene != null) return
        val s = WcrScene3D(files, refreshRate)
        scene = s
        surface?.let { s.setSurface(it, uiHelper.swapChainFlags) }
        if (width > 0 && height > 0) s.setViewport(width, height)
        if (!looping) {
            looping = true
            choreographer.postFrameCallback(this)
        }
        onReady()
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!looping) return
        choreographer.postFrameCallback(this)
        val s = scene ?: return
        if (surface == null || width == 0) return
        s.apply(controller.frame(frameTimeNanos / 1e9))
        if (s.render(frameTimeNanos) && ++drawn >= 3) controller.ready = true
    }

    override fun onNativeWindowChanged(surface: Surface) {
        this.surface = surface
        scene?.setSurface(surface, uiHelper.swapChainFlags)
    }

    override fun onDetachedFromSurface() {
        scene?.releaseSurface()
        surface = null
    }

    override fun onResized(width: Int, height: Int) {
        this.width = width
        this.height = height
        scene?.setViewport(width, height)
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        looping = false
        choreographer.removeFrameCallback(this)
        uiHelper.detach()
        scene?.destroy()
        scene = null
    }
}

/** The see-through Filament stage; it never runs its own clock, it shows what the controller says for each vsync. */
@Composable
internal fun WatchStage3D(controller: WcrController, onReady: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val host = remember(controller) { WcrStageHost(controller) }
    host.onReady = onReady
    LaunchedEffect(host) {
        val files = try {
            loadFiles(context.applicationContext)
        } catch (e: IOException) {
            Log.e("WatchCaseReveal", "assets/$ASSET_DIR (watch.glb, watch-studio-ibl.ktx) could not be read", e)
            return@LaunchedEffect
        }
        val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
        host.start(files, display?.refreshRate ?: 60f)
    }
    DisposableEffect(host) { onDispose { host.destroy() } }
    AndroidView(
        factory = { c -> TextureView(c).also { it.isOpaque = false; host.attach(it) } },
        modifier = modifier,
    )
}
