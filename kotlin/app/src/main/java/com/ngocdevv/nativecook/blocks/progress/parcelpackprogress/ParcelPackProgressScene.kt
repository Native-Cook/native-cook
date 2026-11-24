package com.ngocdevv.nativecook.blocks.progress.parcelpackprogress

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
import kotlin.math.max
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val PARCEL_ASSET_DIR = "parcelpackprogress"

internal class ParcelStageAssets(val model: ByteBuffer, val environment: ByteBuffer)

/** Reads the model and its image-based light off the main thread. Throws an IOException when one is missing. */
internal suspend fun loadParcelStageAssets(context: Context): ParcelStageAssets = withContext(Dispatchers.IO) {
    fun buffer(name: String): ByteBuffer {
        val bytes = context.assets.open("$PARCEL_ASSET_DIR/${PARCEL_ASSET_DIR}_$name").use { it.readBytes() }
        // Filament reads from native memory: the buffer has to be a direct one.
        return ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).apply { rewind() }
    }
    ParcelStageAssets(buffer("parcel.glb"), buffer("parcel-ibl.ktx"))
}

/**
 * The Filament scene of ParcelStage3D.tsx: the carton and studio floor from parcel.glb, IBL 30,000, key and fill
 * directional lights, no shadows, ACES (legacy) at exposure 0, FXAA, on a transparent frame. The file has no
 * animations and none are applied: every moving node is posed from the film's time. Main thread only.
 */
internal class ParcelPackProgressScene(assets: ParcelStageAssets, refreshRate: Float) {
    private val engine: Engine = Engine.create()
    private val renderer: Renderer = engine.createRenderer()
    private val scene = engine.createScene()
    private val view = engine.createView()
    private val cameraEntity = EntityManager.get().create()
    private val camera: Camera = engine.createCamera(cameraEntity)

    private val materialProvider = UbershaderProvider(engine)
    private val assetLoader = AssetLoader(engine, materialProvider, EntityManager.get())
    private val resourceLoader = ResourceLoader(engine, true)
    private val asset = checkNotNull(assetLoader.createAsset(assets.model)) { "parcel.glb did not load" }

    private val environment: Texture
    private val indirectLight: IndirectLight
    private val colorGrading: ColorGrading
    private val key: Int
    private val fill: Int
    private val root: Int
    private val flaps: IntArray
    private val tapeA: Int
    private val tapeTop: Int
    private val tapeB: Int
    private val label: Int

    /** True once every material of the model has its programs compiled. */
    var ready = false
        private set

    private var swapChain: SwapChain? = null
    private var aspect = 0.0
    private val cam = DoubleArray(6)
    private val tape = DoubleArray(3)
    private val lb = DoubleArray(4)
    private val m = FloatArray(16)
    private var lastT = Double.NaN
    private var lastOrbit = Double.NaN

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
            .exposure(ParcelMotion.EXPOSURE)
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

        root = asset.getFirstEntityByName("Box")
        flaps = IntArray(ParcelMotion.FLAPS.size) { asset.getFirstEntityByName(ParcelMotion.FLAPS[it].node) }
        tapeA = asset.getFirstEntityByName("TapeA")
        tapeTop = asset.getFirstEntityByName("TapeTop")
        tapeB = asset.getFirstEntityByName("TapeB")
        label = asset.getFirstEntityByName("Label")

        environment = KTX1Loader.createTexture(engine, assets.environment)
        indirectLight = IndirectLight.Builder()
            .reflections(environment)
            .irradiance(3, checkNotNull(KTX1Loader.getSphericalHarmonics(assets.environment)) { "parcel-ibl.ktx has no harmonics" })
            .intensity(ParcelMotion.IBL_INTENSITY)
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

        key = directional(ParcelMotion.KEY_DIRECTION, ParcelMotion.KEY_LUX, 5400f)
        fill = directional(ParcelMotion.FILL_DIRECTION, ParcelMotion.FILL_LUX, 7000f)
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

    private fun place(x: Double, y: Double, z: Double) {
        Matrix.setIdentityM(m, 0)
        Matrix.translateM(m, 0, x.toFloat(), y.toFloat(), z.toFloat())
    }

    /** Poses every moving node for film time `t` with a finger's `orbit` (deg). A still scene writes nothing. */
    fun apply(t: Double, orbit: Double) {
        if (t == lastT && orbit == lastOrbit) return
        lastT = t
        lastOrbit = orbit

        ParcelMotion.cameraAt(t, orbit, cam)
        camera.lookAt(cam[0], cam[1], cam[2], cam[3], cam[4], cam[5], 0.0, 1.0, 0.0)

        // the carton: squash, then its turn on the floor, then its place along the slide
        place(ParcelMotion.slideX(t), 0.0, 0.0)
        Matrix.rotateM(m, 0, ParcelMotion.yawAt(t).toFloat(), 0f, 1f, 0f)
        Matrix.scaleM(m, 0, 1f, ParcelMotion.boxSquash(t).toFloat(), 1f)
        write(root)

        // the flaps: each turns about its hinge; a short one's hinge sinks as it closes
        for (i in flaps.indices) {
            val f = ParcelMotion.FLAPS[i]
            val open = ParcelMotion.flapOpen(i, t)
            place(f.pivot[0], f.pivot[1] - f.sink * (1 - open), f.pivot[2])
            Matrix.rotateM(m, 0, (f.open * open).toFloat(), f.axis[0], f.axis[1], f.axis[2])
            write(flaps[i])
        }

        // the tape: three pieces, each scaled along its length from where it starts
        ParcelMotion.tapeAt(t, tape)
        place(ParcelMotion.TAPE_A[0], ParcelMotion.TAPE_A[1], ParcelMotion.TAPE_A[2])
        Matrix.scaleM(m, 0, 1f, visible(tape[0]), 1f)
        write(tapeA)
        place(ParcelMotion.TAPE_TOP[0], ParcelMotion.TAPE_TOP[1], ParcelMotion.TAPE_TOP[2])
        Matrix.scaleM(m, 0, visible(tape[1]), 1f, 1f)
        write(tapeTop)
        place(ParcelMotion.TAPE_B[0], ParcelMotion.TAPE_B[1], ParcelMotion.TAPE_B[2])
        Matrix.scaleM(m, 0, 1f, visible(tape[2]), 1f)
        write(tapeB)

        // the label: size and squash, its turn on the lid, its tilt on the way down, its place above the lid
        ParcelMotion.labelAt(t, lb)
        val size = visible(lb[1])
        val wide = size * (1 + lb[3]).toFloat()
        val a = ParcelMotion.TILT_AXIS
        place(ParcelMotion.LABEL[0], ParcelMotion.LABEL[1] + lb[0], ParcelMotion.LABEL[2])
        Matrix.rotateM(m, 0, lb[2].toFloat(), a[0], a[1], a[2])
        Matrix.rotateM(m, 0, ParcelMotion.LABEL_YAW, 0f, 1f, 0f)
        Matrix.scaleM(m, 0, wide, size, wide)
        write(label)
    }

    /** A piece that is "not there" is this small: a scale of 0 makes a matrix Filament cannot invert. */
    private fun visible(v: Double): Float = max(NONE, v).toFloat()

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
            val fovY = Math.toDegrees(2 * atan(ParcelMotion.tanHalfFovY(asp)))
            camera.setProjection(fovY, asp, ParcelMotion.CAMERA_NEAR, ParcelMotion.CAMERA_FAR, Camera.Fov.VERTICAL)
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
        const val NONE = 0.0001

        init {
            Utils.init()
        }
    }
}
