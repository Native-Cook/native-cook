package com.ngocdevv.nativecook.blocks.pickers.deskfanspeed

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
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.floor
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val FAN_ASSET_DIR = "deskfanspeed"

internal class FanStageAssets(val model: ByteBuffer, val environment: ByteBuffer)

/** Reads the model and its image-based light off the main thread. Throws an IOException when one is missing. */
internal suspend fun loadFanStageAssets(context: Context): FanStageAssets = withContext(Dispatchers.IO) {
    fun buffer(name: String): ByteBuffer {
        val bytes = context.assets.open("$FAN_ASSET_DIR/${FAN_ASSET_DIR}_$name").use { it.readBytes() }
        // Filament reads from native memory: the buffer has to be a direct one.
        return ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).apply { rewind() }
    }
    FanStageAssets(buffer("fan.glb"), buffer("fan-ibl.ktx"))
}

/**
 * The Filament scene of FanStage3D.tsx: the fan and studio sweep from fan.glb, IBL 30,000, key and fill directional
 * lights, no shadows (the contact shadow is baked), ACES (legacy) at exposure 0, FXAA, on a transparent frame. The file
 * has no animations and none are applied: the head, the blades, the four keys and the blur disc's alpha are set from
 * the loop's values every frame. Main thread only.
 */
internal class DeskFanSpeedScene(assets: FanStageAssets, refreshRate: Float) {
    private val engine: Engine = Engine.create()
    private val renderer: Renderer = engine.createRenderer()
    private val scene = engine.createScene()
    private val view = engine.createView()
    private val cameraEntity = EntityManager.get().create()
    private val camera: Camera = engine.createCamera(cameraEntity)

    private val materialProvider = UbershaderProvider(engine)
    private val assetLoader = AssetLoader(engine, materialProvider, EntityManager.get())
    private val resourceLoader = ResourceLoader(engine, true)
    private val asset = checkNotNull(assetLoader.createAsset(assets.model)) { "fan.glb did not load" }

    private val environment: Texture
    private val indirectLight: IndirectLight
    private val colorGrading: ColorGrading
    private val key: Int
    private val fill: Int
    private val head: Int
    private val blades: Int
    private val keyNodes: IntArray
    private val blur: MaterialInstance?
    private var blurShown = 0.0

    /** True once every material of the model has its programs compiled. */
    var ready = false
        private set

    private var swapChain: SwapChain? = null
    private var aspect = 0.0
    private val cam = DoubleArray(6)
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
        colorGrading = ColorGrading.Builder()
            .toneMapper(ToneMapper.ACESLegacy())
            .exposure(FanMotion.EXPOSURE)
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

        head = asset.getFirstEntityByName("Head")
        blades = asset.getFirstEntityByName("Blades")
        keyNodes = IntArray(FanMotion.KEY_POSITIONS.size) { asset.getFirstEntityByName("Key$it") }
        // the blur disc's own material instance: its alpha follows the blade speed
        val blurEntity = asset.getFirstEntityByName("BladeBlur")
        val blurInstance = if (blurEntity == 0) 0 else renderables.getInstance(blurEntity)
        blur = if (blurInstance == 0) null else renderables.getMaterialInstanceAt(blurInstance, 0)
        val c = FanMotion.BLUR_RGB
        blur?.setParameter("baseColorFactor", c[0], c[1], c[2], 0f)

        environment = KTX1Loader.createTexture(engine, assets.environment)
        indirectLight = IndirectLight.Builder()
            .reflections(environment)
            .irradiance(3, checkNotNull(KTX1Loader.getSphericalHarmonics(assets.environment)) { "fan-ibl.ktx has no harmonics" })
            .intensity(FanMotion.IBL_INTENSITY)
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

        key = directional(FanMotion.KEY_DIRECTION, FanMotion.KEY_LUX, FanMotion.KEY_KELVIN)
        fill = directional(FanMotion.FILL_DIRECTION, FanMotion.FILL_LUX, FanMotion.FILL_KELVIN)
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

    /** A node turned (deg) about an axis through its pivot. */
    private fun turn(entity: Int, pivot: FloatArray, degrees: Double, ax: Float, ay: Float, az: Float) {
        Matrix.setIdentityM(m, 0)
        Matrix.translateM(m, 0, pivot[0], pivot[1], pivot[2])
        Matrix.rotateM(m, 0, degrees.toFloat(), ax, ay, az)
        write(entity)
    }

    /** Poses the fan: blade angle (turns), blade speed (turns a second), head angle (deg), how far each key is down. */
    fun apply(turns: Double, speed: Double, yaw: Double, keys: DoubleArray) {
        FanMotion.cameraAt(speed, cam)
        camera.lookAt(cam[0], cam[1], cam[2], cam[3], cam[4], cam[5], 0.0, 1.0, 0.0)

        turn(head, FanMotion.HEAD_PIVOT, yaw, 0f, 1f, 0f)
        // clockwise for the viewer is a negative turn about z; only the fraction matters
        val frac = turns - floor(turns)
        turn(blades, FanMotion.BLADES_PIVOT, -frac * 360, 0f, 0f, 1f)
        val b = blur
        if (b != null) {
            val a = FanMotion.blurAt(speed)
            if (abs(a - blurShown) > BLUR_STEP || (a == 0.0 && blurShown != 0.0)) {
                val c = FanMotion.BLUR_RGB
                b.setParameter("baseColorFactor", c[0], c[1], c[2], a.toFloat())
                blurShown = a
            }
        }
        for (i in keyNodes.indices) {
            val p = FanMotion.KEY_POSITIONS[i]
            Matrix.setIdentityM(m, 0)
            Matrix.translateM(m, 0, p[0], p[1] - keys[i].toFloat() * FanMotion.KEY_TRAVEL, p[2])
            write(keyNodes[i])
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
            val fovY = Math.toDegrees(2 * atan(FanMotion.tanHalfFovY(asp)))
            camera.setProjection(fovY, asp, FanMotion.CAMERA_NEAR, FanMotion.CAMERA_FAR, Camera.Fov.VERTICAL)
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
        for (light in intArrayOf(key, fill)) {
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
        const val VARIANTS = 0x1 or 0x2

        /** The blur's alpha is only sent when it moved this much. */
        const val BLUR_STEP = 0.004

        init {
            Utils.init()
        }
    }
}
