package com.ngocdevv.nativecook.blocks.pickers.mixingdeskfader

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
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val MIXER_ASSET_DIR = "mixingdeskfader"

internal class MixerStageAssets(val model: ByteBuffer, val environment: ByteBuffer)

/** Reads the model and its image-based light off the main thread. Throws an IOException when one is missing. */
internal suspend fun loadMixerStageAssets(context: Context): MixerStageAssets = withContext(Dispatchers.IO) {
    fun buffer(name: String): ByteBuffer {
        val bytes = context.assets.open("$MIXER_ASSET_DIR/${MIXER_ASSET_DIR}_$name").use { it.readBytes() }
        // Filament reads from native memory: the buffer has to be a direct one.
        return ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).apply { rewind() }
    }
    MixerStageAssets(buffer("mixer.glb"), buffer("mixer-ibl.ktx"))
}

/**
 * The Filament scene of MixerStage3D.tsx: the desk and the studio sweep from mixer.glb, IBL 24,000, key and fill
 * directional lights, no shadows (the contact shadow is baked), ACES (legacy) at exposure 0, FXAA, bloom 0.10, on a
 * transparent frame. The file has no animations and none are applied: the faders, knobs and mute buttons (children of
 * the tilted Deck node, posed in its frame) and the emissive of every LED and mute lamp are set from the pose and the
 * meters every frame. Main thread only.
 */
internal class MixingDeskFaderScene(assets: MixerStageAssets, refreshRate: Float) {
    private val engine: Engine = Engine.create()
    private val renderer: Renderer = engine.createRenderer()
    private val scene = engine.createScene()
    private val view = engine.createView()
    private val cameraEntity = EntityManager.get().create()
    private val camera: Camera = engine.createCamera(cameraEntity)

    private val materialProvider = UbershaderProvider(engine)
    private val assetLoader = AssetLoader(engine, materialProvider, EntityManager.get())
    private val resourceLoader = ResourceLoader(engine, true)
    private val asset = checkNotNull(assetLoader.createAsset(assets.model)) { "mixer.glb did not load" }

    private val environment: Texture
    private val indirectLight: IndirectLight
    private val colorGrading: ColorGrading
    private val key: Int
    private val fill: Int
    private val faders: IntArray
    private val knobs: IntArray
    private val mutes: IntArray
    private val lamps: Array<MaterialInstance?>

    /** CHANNELS x LEDS, channel-major. */
    private val leds: Array<MaterialInstance?>

    /** What each LED and lamp shows now (in GLOW_STEPS; LEDs first, then the lamps), so a frame writes only changes. */
    private val glow = IntArray(MixerMotion.CHANNELS * (MixerMotion.LEDS + 1)) { -1 }

    /** True once every material of the model has its programs compiled. */
    var ready = false
        private set

    private var swapChain: SwapChain? = null
    private var aspect = 0.0
    private val eye = DoubleArray(3)
    private val m = FloatArray(16)

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
        view.temporalAntiAliasingOptions = view.temporalAntiAliasingOptions.apply { enabled = false }
        view.ambientOcclusionOptions = view.ambientOcclusionOptions.apply { enabled = false }
        view.bloomOptions = view.bloomOptions.apply {
            enabled = true
            strength = MixerMotion.BLOOM_STRENGTH
            levels = 5
            threshold = true
            highlight = 1000f
            quality = View.QualityLevel.MEDIUM
        }
        colorGrading = ColorGrading.Builder()
            .toneMapper(ToneMapper.ACESLegacy())
            .exposure(MixerMotion.EXPOSURE)
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

        fun materialOf(name: String): MaterialInstance? {
            val e = asset.getFirstEntityByName(name)
            val ri = if (e == 0) 0 else renderables.getInstance(e)
            return if (ri == 0) null else renderables.getMaterialInstanceAt(ri, 0)
        }
        val n = MixerMotion.CHANNELS
        faders = IntArray(n) { asset.getFirstEntityByName("Fader$it") }
        knobs = IntArray(n) { asset.getFirstEntityByName("Knob$it") }
        mutes = IntArray(n) { asset.getFirstEntityByName("Mute$it") }
        lamps = Array(n) { materialOf("MuteLens$it") }
        leds = Array(n * MixerMotion.LEDS) { materialOf("Led${it / MixerMotion.LEDS}_${it % MixerMotion.LEDS}") }

        environment = KTX1Loader.createTexture(engine, assets.environment)
        indirectLight = IndirectLight.Builder()
            .reflections(environment)
            .irradiance(3, checkNotNull(KTX1Loader.getSphericalHarmonics(assets.environment)) { "mixer-ibl.ktx has no harmonics" })
            .intensity(MixerMotion.IBL_INTENSITY)
            .build(engine)
        scene.indirectLight = indirectLight

        // Compile up front: until they are, Filament leaves the objects out of the frame.
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

        key = directional(MixerMotion.KEY_DIRECTION, MixerMotion.KEY_LUX, MixerMotion.KEY_KELVIN)
        fill = directional(MixerMotion.FILL_DIRECTION, MixerMotion.FILL_LUX, MixerMotion.FILL_KELVIN)
    }

    private fun directional(d: FloatArray, lux: Float, kelvin: Float): Int {
        val entity = EntityManager.get().create()
        val c = Colors.cct(kelvin)
        val l = sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2])
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(c[0], c[1], c[2])
            .intensity(lux)
            .direction(d[0] / l, d[1] / l, d[2] / l)
            .castShadows(false)
            .build(engine, entity)
        scene.addEntity(entity)
        return entity
    }

    private fun write(entity: Int) {
        if (entity == 0) return
        val tm = engine.transformManager
        tm.setTransform(tm.getInstance(entity), m)
    }

    /**
     * Poses the desk: p is the 12-number pose (faders, knobs, mutes), lean the camera's lean (0..1) and s the meters'
     * signal time (s).
     */
    fun apply(p: DoubleArray, lean: Double, s: Double) {
        MixerMotion.cameraAt(lean, eye)
        val tg = MixerMotion.TARGET
        camera.lookAt(eye[0], eye[1], eye[2], tg[0], tg[1], tg[2], 0.0, 1.0, 0.0)

        val stepsF = GLOW_STEPS.toFloat()
        for (c in 0 until MixerMotion.CHANNELS) {
            val b = MixerMotion.FADER_BASE[c]
            Matrix.setIdentityM(m, 0)
            Matrix.translateM(m, 0, b[0], b[1], b[2] - p[c].toFloat() * MixerMotion.TRAVEL)
            write(faders[c])

            // T(position) · R(y)
            val kp = MixerMotion.KNOB_POS[c]
            Matrix.setIdentityM(m, 0)
            Matrix.translateM(m, 0, kp[0], kp[1], kp[2])
            Matrix.rotateM(m, 0, MixerMotion.knobDeg(p[4 + c]).toFloat(), 0f, 1f, 0f)
            write(knobs[c])

            val mute = p[8 + c]
            val mp = MixerMotion.MUTE_POS[c]
            Matrix.setIdentityM(m, 0)
            Matrix.translateM(m, 0, mp[0], mp[1] - mute.toFloat() * MixerMotion.MUTE_TRAVEL, mp[2])
            write(mutes[c])

            val lampLevel = MixerMotion.jsRound((1 - mute) * GLOW_STEPS).toInt()
            val li = MixerMotion.CHANNELS * MixerMotion.LEDS + c
            val lamp = lamps[c]
            if (lamp != null && glow[li] != lampLevel) {
                glow[li] = lampLevel
                val e = MixerMotion.LAMP_ON * lampLevel / stepsF
                val rgb = MixerMotion.LAMP_RGB
                lamp.setParameter("emissiveFactor", rgb[0] * e, rgb[1] * e, rgb[2] * e)
            }
            val level = MixerMotion.meterAt(c, s, p[c], p[4 + c], mute)
            for (i in 0 until MixerMotion.LEDS) {
                val idx = c * MixerMotion.LEDS + i
                val mi = leds[idx] ?: continue
                val q = MixerMotion.jsRound(MixerMotion.ledAt(level, i) * GLOW_STEPS).toInt()
                if (glow[idx] == q) continue
                glow[idx] = q
                val col = when (i) {
                    MixerMotion.LEDS - 1 -> MixerMotion.LED_RED
                    MixerMotion.LEDS - 2 -> MixerMotion.LED_AMBER
                    else -> MixerMotion.LED_GREEN
                }
                val e = MixerMotion.LED_ON * q / stepsF
                mi.setParameter("emissiveFactor", col[0] * e, col[1] * e, col[2] * e)
            }
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
        val asp = width.toDouble() / height
        if (asp != aspect) {
            aspect = asp
            val fovY = Math.toDegrees(2 * atan(MixerMotion.tanHalfFovY(asp)))
            camera.setProjection(fovY, asp, MixerMotion.CAMERA_NEAR, MixerMotion.CAMERA_FAR, Camera.Fov.VERTICAL)
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
        for (l in intArrayOf(key, fill)) {
            scene.removeEntity(l)
            engine.destroyEntity(l)
            entities.destroy(l)
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
        const val VARIANTS = 0x1 or 0x2

        /** LEDs and lamps are written only when their level moved by a step of this size. */
        const val GLOW_STEPS = 48

        init {
            Utils.init()
        }
    }
}
