package com.ngocdevv.nativecook.blocks.media.boomboxnowplaying

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
import kotlin.math.atan

/** The block's folder in the app's assets. */
private const val ASSET_DIR = "boombox-now-playing"

private class BnpFiles(val model: ByteBuffer, val environment: ByteBuffer)

private suspend fun loadFiles(context: Context): BnpFiles = withContext(Dispatchers.IO) {
    fun buffer(name: String): ByteBuffer {
        val bytes = context.assets.open("$ASSET_DIR/$name").use { it.readBytes() }
        // Filament reads from native memory: the buffer has to be a direct one.
        return ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).apply { rewind() }
    }
    BnpFiles(buffer("vinyl.glb"), buffer("studio-ibl.ktx"))
}

/** The film may start once the record has been on screen for this many Filament frames. */
private const val READY_FRAMES = 3

/**
 * The Filament scene of VinylStage3D.tsx: the record and its sleeve from vinyl.glb, lit by the studio IBL (32,000), a
 * warm key (60,000 lx, 5200 K) and a cool rim (28,000 lx, 7500 K), no shadows (the soft contact shadows are a Compose
 * canvas under the see-through view); ACES (legacy) tone mapping at +0.25 stops and react-native-filament's camera
 * (f/16, 1/125 s, ISO 100) with the stage's horizontal field; bloom 0.04 over 5 levels, FXAA, no TAA, no ambient
 * occlusion. The groove highlight's discs glow at 0.9 (their emissive factor). Everything runs on the main thread.
 */
private class BnpScene3D(files: BnpFiles, refreshRate: Float) {
    private val engine: Engine = Engine.create()
    private val renderer: Renderer = engine.createRenderer()
    private val scene = engine.createScene()
    private val view = engine.createView()
    private val cameraEntity = EntityManager.get().create()
    private val camera: Camera = engine.createCamera(cameraEntity)
    private val materialProvider = UbershaderProvider(engine)
    private val assetLoader = AssetLoader(engine, materialProvider, EntityManager.get())
    private val resourceLoader = ResourceLoader(engine, true)
    private val asset: FilamentAsset = checkNotNull(assetLoader.createAsset(files.model)) { "vinyl.glb did not load" }
    private val environment: Texture
    private val indirectLight: IndirectLight
    private val colorGrading: ColorGrading
    private val key: Int
    private val rim: Int
    private val record: Int
    private val spin: Int
    private val sheen: Int
    private val sleeve: Int
    private var swapChain: SwapChain? = null
    private var stageW = 0
    private var stageH = 0
    private val m = FloatArray(16)
    var drawn = 0
        private set

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
            strength = 0.04f
            levels = 5
            threshold = true
            highlight = 1000f
            quality = View.QualityLevel.MEDIUM
        }
        camera.setExposure(16f, 1f / 125f, 100f)
        colorGrading = ColorGrading.Builder().toneMapper(ToneMapper.ACESLegacy()).exposure(BnpScene.EXPOSURE_STOPS).build(engine)
        view.colorGrading = colorGrading

        resourceLoader.loadResources(asset)
        asset.releaseSourceData()
        scene.addEntities(asset.entities)
        val rm = engine.renderableManager
        for (e in asset.renderableEntities) {
            val i = rm.getInstance(e)
            rm.setCastShadows(i, false)
            rm.setReceiveShadows(i, false)
            for (p in 0 until rm.getPrimitiveCount(i)) {
                val mi = rm.getMaterialInstanceAt(i, p)
                if (mi.name == "Sheen") mi.setParameter("emissiveFactor", BnpScene.SHEEN_GLOW, BnpScene.SHEEN_GLOW, BnpScene.SHEEN_GLOW)
            }
        }
        record = asset.getFirstEntityByName("Record")
        spin = asset.getFirstEntityByName("Spin")
        sheen = asset.getFirstEntityByName("SheenSpin")
        sleeve = asset.getFirstEntityByName("Sleeve")

        environment = KTX1Loader.createTexture(engine, files.environment)
        indirectLight = IndirectLight.Builder()
            .reflections(environment)
            .irradiance(3, checkNotNull(KTX1Loader.getSphericalHarmonics(files.environment)) { "the IBL has no harmonics" })
            .intensity(BnpScene.IBL_INTENSITY)
            .build(engine)
        scene.indirectLight = indirectLight
        key = addLight(BnpScene.KEY_DIRECTION, BnpScene.KEY_LUX, 5200f)
        rim = addLight(BnpScene.RIM_DIRECTION, BnpScene.RIM_LUX, 7500f)
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
        if (w != stageW || h != stageH) {
            stageW = w
            stageH = h
            val hfov = 2 * atan(w / 2.0 / BnpMotion.focalFor(w.toDouble(), h.toDouble())) * 180 / PI
            camera.setProjection(hfov, w.toDouble() / h, BnpScene.CAMERA_NEAR, BnpScene.CAMERA_FAR, Camera.Fov.HORIZONTAL)
        }
    }

    private fun setLocal(entity: Int, mat: FloatArray) {
        if (entity == 0) return
        val tm = engine.transformManager
        tm.setTransform(tm.getInstance(entity), mat)
    }

    /** Poses the scene for one frame (VinylStage3D.tsx's render callback). */
    fun apply(f: BnpFrame) {
        val sc = f.scene
        camera.lookAt(sc[8], sc[9], sc[10], sc[11], sc[12], sc[13], 0.0, 1.0, 0.0)
        // the record's whole body: its yaw about the upright axis, then its place
        Matrix.setRotateM(m, 0, Math.toDegrees(sc[3]).toFloat(), 0f, 1f, 0f)
        m[12] = sc[0].toFloat(); m[13] = sc[1].toFloat(); m[14] = sc[2].toFloat()
        setLocal(record, m)
        // the spin on its own axis, clockwise as the viewer sees side A: a negative angle about +z
        Matrix.setRotateM(m, 0, Math.toDegrees(-f.platter).toFloat(), 0f, 0f, 1f)
        setLocal(spin, m)
        // the groove highlight stays with the light and the eye, not with the record's spin
        Matrix.setRotateM(m, 0, Math.toDegrees(BnpMotion.sheenAngle(sc)).toFloat(), 0f, 0f, 1f)
        setLocal(sheen, m)
        Matrix.setRotateM(m, 0, Math.toDegrees(sc[7]).toFloat(), 0f, 1f, 0f)
        m[12] = sc[4].toFloat(); m[13] = sc[5].toFloat(); m[14] = sc[6].toFloat()
        setLocal(sleeve, m)
    }

    fun render(frameTimeNanos: Long): Boolean {
        val target = swapChain ?: return false
        if (!renderer.beginFrame(target, frameTimeNanos)) return false
        renderer.render(view)
        renderer.endFrame()
        drawn++
        return true
    }

    fun destroy() {
        releaseSurface()
        val em = EntityManager.get()
        for (l in intArrayOf(key, rim)) {
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
private class BnpStageHost(private val controller: BnpController) : Choreographer.FrameCallback, UiHelper.RendererCallback {
    private var scene: BnpScene3D? = null
    private var surface: Surface? = null
    private var width = 0
    private var height = 0
    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).apply { isOpaque = false }
    private val choreographer = Choreographer.getInstance()
    private var looping = false
    private var destroyed = false

    fun attach(view: TextureView) {
        uiHelper.renderCallback = this
        uiHelper.attachTo(view)
    }

    fun start(files: BnpFiles, refreshRate: Float) {
        if (destroyed || scene != null) return
        val s = BnpScene3D(files, refreshRate)
        scene = s
        surface?.let { s.setSurface(it, uiHelper.swapChainFlags) }
        if (width > 0 && height > 0) s.setViewport(width, height)
        if (!looping) {
            looping = true
            choreographer.postFrameCallback(this)
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!looping) return
        choreographer.postFrameCallback(this)
        val s = scene ?: return
        if (surface == null || width == 0) return
        s.apply(controller.frame(frameTimeNanos / 1e9))
        s.render(frameTimeNanos)
        // the film's clock starts once the record has really been on screen for a few frames (its textures are up)
        if (s.drawn >= READY_FRAMES && !controller.ready) controller.ready = true
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
internal fun VinylStage3D(controller: BnpController, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val host = remember(controller) { BnpStageHost(controller) }
    LaunchedEffect(host) {
        val files = try {
            loadFiles(context.applicationContext)
        } catch (e: IOException) {
            Log.e("BoomboxNowPlaying", "assets/$ASSET_DIR (vinyl.glb, studio-ibl.ktx) could not be read", e)
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
