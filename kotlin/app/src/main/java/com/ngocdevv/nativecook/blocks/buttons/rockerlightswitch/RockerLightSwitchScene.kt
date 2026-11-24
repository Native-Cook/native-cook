package com.ngocdevv.nativecook.blocks.buttons.rockerlightswitch

import android.content.Context
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import android.view.Surface
import com.google.android.filament.Camera
import com.google.android.filament.ColorGrading
import com.google.android.filament.Colors
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Renderer
import com.google.android.filament.SwapChain
import com.google.android.filament.Texture
import com.google.android.filament.ToneMapper
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.google.android.filament.utils.KTX1Loader
import com.google.android.filament.utils.Utils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.atan
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The block's folder in the app's assets. */
internal const val SWITCH_ASSET_DIR = "rockerlightswitch"

internal class SwitchStageAssets(val model: ByteBuffer, val environment: ByteBuffer)

/** Reads switch.glb and the dusk's image-based light off the main thread. Throws an IOException when one is missing. */
internal suspend fun loadSwitchStageAssets(context: Context): SwitchStageAssets = withContext(Dispatchers.IO) {
    fun buffer(name: String): ByteBuffer {
        val bytes = context.assets.open("$SWITCH_ASSET_DIR/$name").use { it.readBytes() }
        // Filament reads from native memory: the buffer has to be a direct one.
        return ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).apply { rewind() }
    }
    SwitchStageAssets(buffer("switch.glb"), buffer("switch-ibl.ktx"))
}

/**
 * The Filament scene of SwitchStage3D.tsx: the painted wall, the plate with its rocker and the brass sconce from
 * switch.glb, lit by the dusk IBL (9,000), a point light inside the globe (42,000 lm x level, 2.5 m falloff) and a
 * warm directional bounce (16,000 lx x level, 3000 K), ACES (legacy) at exposure 0, FXAA and a 0.14 bloom, on a
 * transparent frame. The rocker turns about x through its pivot; the globe's emissive and the plate shadow's alpha
 * follow the light. Called on the main thread only.
 */
internal class RockerLightSwitchScene(assets: SwitchStageAssets, refreshRate: Float) {
    private val engine: Engine = Engine.create()
    private val renderer: Renderer = engine.createRenderer()
    private val scene = engine.createScene()
    private val view = engine.createView()
    private val cameraEntity = EntityManager.get().create()
    private val camera: Camera = engine.createCamera(cameraEntity)

    private val materialProvider = UbershaderProvider(engine)
    private val assetLoader = AssetLoader(engine, materialProvider, EntityManager.get())
    private val resourceLoader = ResourceLoader(engine, true)
    private val asset = checkNotNull(assetLoader.createAsset(assets.model)) { "switch.glb did not load" }

    private val environment: Texture
    private val indirectLight: IndirectLight
    private val colorGrading: ColorGrading
    private val bulb: Int
    private val bounce: Int
    private val rocker: Int
    private val globe: MaterialInstance?
    private val shadow: MaterialInstance?

    /** True once every material of the model has its programs compiled, so the wall is in the next frame. */
    var ready = false
        private set

    private var swapChain: SwapChain? = null
    private var aspect = 0.0
    private val cam = DoubleArray(6)
    private val col = DoubleArray(3)
    private val m = FloatArray(16)
    private var lastLight = -1

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
        view.bloomOptions = View.BloomOptions().apply {
            enabled = true
            strength = 0.14f
            levels = 5
            threshold = true
            highlight = 1000f
            quality = View.QualityLevel.MEDIUM
        }
        colorGrading = ColorGrading.Builder()
            .toneMapper(ToneMapper.ACESLegacy())
            .exposure(SwitchMotion.EXPOSURE)
            .build(engine)
        view.colorGrading = colorGrading

        resourceLoader.loadResources(asset)
        asset.releaseSourceData()
        val renderables = engine.renderableManager
        for (e in asset.renderableEntities) {
            val ri = renderables.getInstance(e)
            renderables.setCastShadows(ri, false)
            renderables.setReceiveShadows(ri, false)
        }
        scene.addEntities(asset.entities)

        fun material(name: String): MaterialInstance? {
            val e = asset.getFirstEntityByName(name)
            if (e == 0) return null
            val ri = renderables.getInstance(e)
            return if (ri == 0) null else renderables.getMaterialInstanceAt(ri, 0)
        }
        rocker = asset.getFirstEntityByName("Rocker")
        globe = material("Globe")
        shadow = material("PlateShadow")

        environment = KTX1Loader.createTexture(engine, assets.environment)
        indirectLight = IndirectLight.Builder()
            .reflections(environment)
            .irradiance(3, checkNotNull(KTX1Loader.getSphericalHarmonics(assets.environment)) { "switch-ibl.ktx has no harmonics" })
            .intensity(SwitchMotion.IBL_INTENSITY)
            .build(engine)
        scene.indirectLight = indirectLight

        // Compile the model's programs up front: until they are, Filament leaves the objects out of the frame, and the
        // film would start on an empty stage.
        val materials = asset.instance.materialInstances.map { it.material }.distinct()
        var pending = materials.size
        val handler = Handler(Looper.getMainLooper())
        for (mat in materials) {
            mat.compile(Material.CompilerPriorityQueue.HIGH, VARIANTS, handler) {
                pending--
                if (pending == 0) ready = true
            }
        }
        if (materials.isEmpty()) ready = true

        // the bulb: a point light inside the globe; the bounce: the room answering it. Both start dark.
        val entities = EntityManager.get()
        bulb = entities.create()
        val warm = Colors.cct(2700f)
        val g = SwitchMotion.GLOBE
        LightManager.Builder(LightManager.Type.POINT)
            .color(warm[0], warm[1], warm[2])
            .intensity(0f)
            .position(g[0].toFloat(), g[1].toFloat(), g[2].toFloat())
            .falloff(SwitchMotion.BULB_FALLOFF)
            .castShadows(false)
            .build(engine, bulb)
        scene.addEntity(bulb)
        bounce = entities.create()
        val soft = Colors.cct(3000f)
        val d = SwitchMotion.BOUNCE_DIRECTION
        val l = sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2])
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(soft[0], soft[1], soft[2])
            .intensity(0f)
            .direction(d[0] / l, d[1] / l, d[2] / l)
            .castShadows(false)
            .build(engine, bounce)
        scene.addEntity(bounce)
    }

    /** Poses the scene: the camera at `orbit` and film time `t`, the rocker at `press`, the light at output `k`. */
    fun apply(press: Double, k: Double, orbit: Double, t: Double) {
        SwitchMotion.cameraAt(orbit, t, cam)
        camera.lookAt(cam[0], cam[1], cam[2], cam[3], cam[4], cam[5], 0.0, 1.0, 0.0)

        if (rocker != 0) {
            // off = +ROCK (the bottom half in), on = -ROCK (the top half in), about x through the pivot
            val p = SwitchMotion.ROCKER_PIVOT
            Matrix.setIdentityM(m, 0)
            Matrix.translateM(m, 0, p[0].toFloat(), p[1].toFloat(), p[2].toFloat())
            Matrix.rotateM(m, 0, SwitchMotion.lerp(SwitchMotion.ROCK_DEG, -SwitchMotion.ROCK_DEG, press).toFloat(), 1f, 0f, 0f)
            val tm = engine.transformManager
            tm.setTransform(tm.getInstance(rocker), m)
        }

        // the light: written only when it changes
        val q = (k * 1000).roundToInt()
        if (q == lastLight) return
        lastLight = q
        SwitchMotion.warmColor(k, col)
        val lights = engine.lightManager
        val bi = lights.getInstance(bulb)
        lights.setIntensity(bi, (SwitchMotion.BULB_LUMENS * k).toFloat())
        lights.setColor(bi, col[0].toFloat(), col[1].toFloat(), col[2].toFloat())
        lights.setIntensity(lights.getInstance(bounce), (SwitchMotion.BOUNCE_LUX * k).toFloat())
        val e = SwitchMotion.GLOBE_EMISSIVE * k
        globe?.setParameter("emissiveFactor", (col[0] * e).toFloat(), (col[1] * e).toFloat(), (col[2] * e).toFloat())
        val s = SwitchMotion.SHADOW_RGB
        shadow?.setParameter("baseColorFactor", s[0], s[1], s[2], k.toFloat())
    }

    fun setSurface(surface: Surface, flags: Long) {
        releaseSurface()
        swapChain = engine.createSwapChain(surface, flags)
    }

    fun releaseSurface() {
        val current = swapChain ?: return
        swapChain = null
        engine.destroySwapChain(current)
        engine.flushAndWait()
    }

    fun setViewport(width: Int, height: Int) {
        view.viewport = Viewport(0, 0, width, height)
        val asp = width.toDouble() / height
        if (asp != aspect) {
            aspect = asp
            // the vertical field of view is fitted so FRAME_W x FRAME_H of wall is always in frame
            val fovY = Math.toDegrees(2 * atan(SwitchMotion.tanHalfFovY(asp)))
            camera.setProjection(fovY, asp, SwitchMotion.CAMERA_NEAR, SwitchMotion.CAMERA_FAR, Camera.Fov.VERTICAL)
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
        val entities = EntityManager.get()
        for (light in intArrayOf(bulb, bounce)) {
            scene.removeEntity(light)
            engine.destroyEntity(light)
            entities.destroy(light)
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
        entities.destroy(cameraEntity)
        engine.destroy()
    }

    private companion object {
        /** Directional and dynamic lighting: the variants this scene draws with. */
        const val VARIANTS = 0x1 or 0x2

        init {
            // Loads Filament's native libraries (filament, gltfio and utils) once per process.
            Utils.init()
        }
    }
}
