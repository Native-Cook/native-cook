package com.ngocdevv.nativecook.blocks.onboarding.visorbootonboarding

import android.content.Context
import android.view.Surface
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
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.google.android.filament.utils.KTX1Loader
import com.google.android.filament.utils.Utils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The block's folder in the app's assets. */
internal const val VISOR_BOOT_ASSET_DIR = "visorbootonboarding"

internal class VisorBootStageAssets(val model: ByteBuffer, val environment: ByteBuffer)

/** Reads visor.glb and the studio IBL off the main thread. Throws an IOException when a file is missing. */
internal suspend fun loadVisorBootStageAssets(context: Context): VisorBootStageAssets =
    withContext(Dispatchers.IO) {
        fun buffer(name: String): ByteBuffer {
            val bytes = context.assets.open("$VISOR_BOOT_ASSET_DIR/${VISOR_BOOT_ASSET_DIR}_$name").use { it.readBytes() }
            // Filament reads from native memory: the buffer has to be a direct one.
            return ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).apply { rewind() }
        }
        VisorBootStageAssets(buffer("visor.glb"), buffer("visor-studio-ibl.ktx"))
    }

/**
 * The helmet on its plinth (VisorStage3D.tsx): the React Native block's own visor.glb and studio IBL, the same three
 * directional lights (warm key, cool rim, the Get Started sweep), Filament's default camera exposure, ACES legacy
 * grading at 0 stops, bloom (strength 0.14, threshold) and FXAA without TAA, all as react-native-filament sets them.
 * The view is see-through: the studio backdrop and the plinth's floor shadow are drawn by Compose underneath it.
 *
 * It never runs its own clock: `pose(state)` sets everything from a VisorBootState. Called on the main thread only.
 */
internal class VisorBootStageScene(assets: VisorBootStageAssets, refreshRate: Float) {
    private val engine: Engine = Engine.create()
    private val renderer: Renderer = engine.createRenderer()
    private val scene = engine.createScene()
    private val view = engine.createView()
    private val cameraEntity = EntityManager.get().create()
    private val camera: Camera = engine.createCamera(cameraEntity)

    private val materialProvider = UbershaderProvider(engine)
    private val assetLoader = AssetLoader(engine, materialProvider, EntityManager.get())
    private val resourceLoader = ResourceLoader(engine, true)
    private val asset = checkNotNull(assetLoader.createAsset(assets.model)) { "visor.glb did not load" }

    private val environment: Texture
    private val indirectLight: IndirectLight
    private val colorGrading: ColorGrading
    private val keyLight: Int
    private val rimLight: Int
    private val sweepLight: Int
    private val rig: Int
    private val shell = mutableListOf<MaterialInstance>()

    private var swapChain: SwapChain? = null
    private var aspect = 0.0
    private var lastLamp = -1
    private val rigTransform = FloatArray(16)

    init {
        renderer.clearOptions = Renderer.ClearOptions().apply { clear = true }
        renderer.setDisplayInfo(Renderer.DisplayInfo().apply { this.refreshRate = refreshRate })
        view.scene = scene
        view.camera = camera
        view.blendMode = View.BlendMode.TRANSLUCENT
        view.antiAliasing = View.AntiAliasing.FXAA
        view.temporalAntiAliasingOptions = View.TemporalAntiAliasingOptions().apply { enabled = false }
        view.ambientOcclusionOptions = View.AmbientOcclusionOptions().apply { enabled = false }
        view.bloomOptions = View.BloomOptions().apply {
            enabled = true
            strength = 0.14f
            levels = 5
            threshold = true
            highlight = 1000f
            quality = View.QualityLevel.MEDIUM
        }

        colorGrading = ColorGrading.Builder().toneMapper(ToneMapper.ACESLegacy()).exposure(0f).build(engine)
        view.colorGrading = colorGrading

        resourceLoader.loadResources(asset)
        asset.releaseSourceData()
        scene.addEntities(asset.entities)

        val renderables = engine.renderableManager
        for (e in asset.renderableEntities) {
            val ri = renderables.getInstance(e)
            if (ri == 0) continue
            renderables.setCastShadows(ri, false)
            renderables.setReceiveShadows(ri, false)
            for (k in 0 until renderables.getPrimitiveCount(ri)) {
                val mi = renderables.getMaterialInstanceAt(ri, k)
                if (mi.name == "HelmetShell") shell += mi
            }
        }
        rig = asset.getFirstEntityByName("HelmetRig")

        environment = KTX1Loader.createTexture(engine, assets.environment)
        indirectLight = IndirectLight.Builder()
            .reflections(environment)
            .irradiance(3, checkNotNull(KTX1Loader.getSphericalHarmonics(assets.environment)) { "IBL has no harmonics" })
            .intensity(VisorBootMotion.IBL_INTENSITY)
            .build(engine)
        scene.indirectLight = indirectLight

        keyLight = addLight(VisorBootMotion.keyDirection, VisorBootMotion.KEY_LUX, 4300f)
        rimLight = addLight(VisorBootMotion.rimDirection, VisorBootMotion.RIM_LUX, 9000f)
        sweepLight = addLight(floatArrayOf(0f, -0.35f, -1f), 0f, 6500f)
    }

    private fun addLight(direction: FloatArray, lux: Float, kelvin: Float): Int {
        val entity = EntityManager.get().create()
        val c = Colors.cct(kelvin)
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(c[0], c[1], c[2])
            .intensity(lux)
            .direction(direction[0], direction[1], direction[2])
            .castShadows(false)
            .build(engine, entity)
        scene.addEntity(entity)
        return entity
    }

    /** Poses the camera, the helmet, the sweep light and the panel lamps for one frame. */
    fun pose(s: VisorBootState, width: Int, height: Int) {
        val a = width.toDouble() / height
        if (a != aspect) {
            camera.setProjection(VisorBootMotion.HFOV_DEG, a, VisorBootMotion.NEAR, VisorBootMotion.FAR, Camera.Fov.HORIZONTAL)
            aspect = a
        }
        val c = VisorBootMotion.cameraAt(s)
        camera.lookAt(c.eye.x, c.eye.y, c.eye.z, c.target.x, c.target.y, c.target.z, 0.0, 1.0, 0.0)

        // the light sweep of the Get Started beat swings across the front; only the film has it
        val level = VisorBootMotion.filmSweepLevel(s.t) * (1 - VisorBootMotion.clamp01(s.m))
        val az = VisorBootMotion.lerp(VisorBootMotion.SWEEP_FROM_DEG, VisorBootMotion.SWEEP_TO_DEG, VisorBootMotion.filmSweepAzimuth(s.t)) * PI / 180
        val lights = engine.lightManager
        val li = lights.getInstance(sweepLight)
        lights.setDirection(li, (-sin(az)).toFloat(), -0.35f, (-cos(az)).toFloat())
        lights.setIntensity(li, (VisorBootMotion.SWEEP_LUX * level).toFloat())

        if (rig != 0) {
            rigTransform.fill(0f)
            rigTransform[0] = 1f; rigTransform[5] = 1f; rigTransform[10] = 1f; rigTransform[15] = 1f
            rigTransform[13] = (VisorBootMotion.HELMET_Y + s.lift).toFloat()
            val tm = engine.transformManager
            tm.setTransform(tm.getInstance(rig), rigTransform)
        }

        // panel lamps: only write when the level moved
        val lamp = VisorBootMotion.blend(VisorBootMotion.filmLamps(s.t), VisorBootMotion.pageLamps(s.page), s.m)
        val q = (lamp * 400).roundToInt()
        if (q != lastLamp) {
            lastLamp = q
            val k = (VisorBootMotion.LAMP_STRENGTH * lamp).toFloat()
            for (mi in shell) mi.setParameter("emissiveFactor", k, k, k)
        }
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
        for (light in intArrayOf(keyLight, rimLight, sweepLight)) {
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
        init {
            Utils.init()
        }
    }
}
