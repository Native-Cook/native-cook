package com.ngocdevv.nativecook.blocks.pickers.twinbellalarm

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

internal const val ALARM_ASSET_DIR = "twinbellalarm"

internal class AlarmStageAssets(val model: ByteBuffer, val environment: ByteBuffer)

/** Reads the model and its image-based light off the main thread. Throws an IOException when one is missing. */
internal suspend fun loadAlarmStageAssets(context: Context): AlarmStageAssets = withContext(Dispatchers.IO) {
    fun buffer(name: String): ByteBuffer {
        val bytes = context.assets.open("$ALARM_ASSET_DIR/${ALARM_ASSET_DIR}_$name").use { it.readBytes() }
        // Filament reads from native memory: the buffer has to be a direct one.
        return ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).apply { rewind() }
    }
    AlarmStageAssets(buffer("alarm.glb"), buffer("alarm-ibl.ktx"))
}

/**
 * The Filament scene of AlarmStage3D.tsx: the clock and studio sweep from alarm.glb, IBL 30,000, key and fill
 * directional lights, no shadows, ACES (legacy) at exposure 0, FXAA, on a transparent frame. The file has no
 * animations and none are applied: every moving node is posed from the loop's values. Main thread only.
 */
internal class TwinBellAlarmScene(assets: AlarmStageAssets, refreshRate: Float) {
    private val engine: Engine = Engine.create()
    private val renderer: Renderer = engine.createRenderer()
    private val scene = engine.createScene()
    private val view = engine.createView()
    private val cameraEntity = EntityManager.get().create()
    private val camera: Camera = engine.createCamera(cameraEntity)

    private val materialProvider = UbershaderProvider(engine)
    private val assetLoader = AssetLoader(engine, materialProvider, EntityManager.get())
    private val resourceLoader = ResourceLoader(engine, true)
    private val asset = checkNotNull(assetLoader.createAsset(assets.model)) { "alarm.glb did not load" }

    private val environment: Texture
    private val indirectLight: IndirectLight
    private val colorGrading: ColorGrading
    private val key: Int
    private val fill: Int
    private val root: Int
    private val hourHand: Int
    private val minuteHand: Int
    private val hammer: Int
    private val bellL: Int
    private val bellR: Int

    /** True once every material of the model has its programs compiled. */
    var ready = false
        private set

    private var swapChain: SwapChain? = null
    private var aspect = 0.0
    private val cam = DoubleArray(6)
    private val r = DoubleArray(6)
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
            .exposure(AlarmMotion.EXPOSURE)
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

        root = asset.getFirstEntityByName("Clock")
        hourHand = asset.getFirstEntityByName("HourHand")
        minuteHand = asset.getFirstEntityByName("MinuteHand")
        hammer = asset.getFirstEntityByName("Hammer")
        bellL = asset.getFirstEntityByName("BellL")
        bellR = asset.getFirstEntityByName("BellR")

        environment = KTX1Loader.createTexture(engine, assets.environment)
        indirectLight = IndirectLight.Builder()
            .reflections(environment)
            .irradiance(3, checkNotNull(KTX1Loader.getSphericalHarmonics(assets.environment)) { "alarm-ibl.ktx has no harmonics" })
            .intensity(AlarmMotion.IBL_INTENSITY)
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

        key = directional(AlarmMotion.KEY_DIRECTION, AlarmMotion.KEY_LUX, 5400f)
        fill = directional(AlarmMotion.FILL_DIRECTION, AlarmMotion.FILL_LUX, 7000f)
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

    /** A node turned about z (deg) through its pivot. */
    private fun turn(entity: Int, pivot: FloatArray, degrees: Double) {
        Matrix.setIdentityM(m, 0)
        Matrix.translateM(m, 0, pivot[0], pivot[1], pivot[2])
        Matrix.rotateM(m, 0, degrees.toFloat(), 0f, 0f, 1f)
        write(entity)
    }

    /** Poses the clock: the hands (deg clockwise from 12), `ringT` seconds into a ring, the camera's push and orbit. */
    fun apply(hourDeg: Double, minuteDeg: Double, ringT: Double, push: Double, orbit: Double) {
        AlarmMotion.cameraAt(push, orbit, cam)
        camera.lookAt(cam[0], cam[1], cam[2], cam[3], cam[4], cam[5], 0.0, 1.0, 0.0)

        // clockwise for the viewer is a negative turn about z
        turn(hourHand, AlarmMotion.HOUR_PIVOT, -hourDeg)
        turn(minuteHand, AlarmMotion.MINUTE_PIVOT, -minuteDeg)

        // the ring: hammer, bells, and the whole clock rocking on its legs, shuffling and hopping
        AlarmMotion.ringAt(ringT, r)
        turn(hammer, AlarmMotion.HAMMER_PIVOT, r[0])
        turn(bellL, AlarmMotion.BELL_L_PIVOT, r[1])
        turn(bellR, AlarmMotion.BELL_R_PIVOT, r[2])
        Matrix.setIdentityM(m, 0)
        Matrix.translateM(m, 0, r[4].toFloat(), r[5].toFloat(), 0f)
        Matrix.rotateM(m, 0, r[3].toFloat(), 0f, 0f, 1f)
        write(root)
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
            val fovY = Math.toDegrees(2 * atan(AlarmMotion.tanHalfFovY(asp)))
            camera.setProjection(fovY, asp, AlarmMotion.CAMERA_NEAR, AlarmMotion.CAMERA_FAR, Camera.Fov.VERTICAL)
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

        init {
            Utils.init()
        }
    }
}
