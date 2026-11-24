package com.ngocdevv.nativecook.blocks.pickers.combinationsafedial

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

internal const val SAFE_ASSET_DIR = "combinationsafedial"

internal class SafeStageAssets(val model: ByteBuffer, val environment: ByteBuffer)

/** Reads the model and its image-based light off the main thread. Throws an IOException when one is missing. */
internal suspend fun loadSafeStageAssets(context: Context): SafeStageAssets = withContext(Dispatchers.IO) {
    fun buffer(name: String): ByteBuffer {
        val bytes = context.assets.open("$SAFE_ASSET_DIR/${SAFE_ASSET_DIR}_$name").use { it.readBytes() }
        // Filament reads from native memory: the buffer has to be a direct one.
        return ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).apply { rewind() }
    }
    SafeStageAssets(buffer("safe.glb"), buffer("safe-ibl.ktx"))
}

/**
 * The Filament scene of SafeStage3D.tsx: the safe and studio sweep from safe.glb, IBL 30,000, key and fill directional
 * lights, no shadows (the contact shadow is baked), ACES (legacy) at exposure 0, FXAA, on a transparent frame. The file
 * has no animations and none are applied: the door, dial, handle, bolts and the root's shake are posed from the loop's
 * values every frame. Main thread only.
 */
internal class CombinationSafeDialScene(assets: SafeStageAssets, refreshRate: Float) {
    private val engine: Engine = Engine.create()
    private val renderer: Renderer = engine.createRenderer()
    private val scene = engine.createScene()
    private val view = engine.createView()
    private val cameraEntity = EntityManager.get().create()
    private val camera: Camera = engine.createCamera(cameraEntity)

    private val materialProvider = UbershaderProvider(engine)
    private val assetLoader = AssetLoader(engine, materialProvider, EntityManager.get())
    private val resourceLoader = ResourceLoader(engine, true)
    private val asset = checkNotNull(assetLoader.createAsset(assets.model)) { "safe.glb did not load" }

    private val environment: Texture
    private val indirectLight: IndirectLight
    private val colorGrading: ColorGrading
    private val key: Int
    private val fill: Int
    private val root: Int
    private val door: Int
    private val dial: Int
    private val handle: Int
    private val bolts: IntArray

    /** True once every material of the model has its programs compiled. */
    var ready = false
        private set

    private var swapChain: SwapChain? = null
    private var aspect = 0.0
    private val cam = DoubleArray(6)
    private val dir = DoubleArray(2)
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
            .exposure(SafeMotion.EXPOSURE)
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

        root = asset.getFirstEntityByName("Safe")
        door = asset.getFirstEntityByName("Door")
        dial = asset.getFirstEntityByName("Dial")
        handle = asset.getFirstEntityByName("Handle")
        bolts = IntArray(SafeMotion.BOLT_COUNT) { asset.getFirstEntityByName("Bolt$it") }

        environment = KTX1Loader.createTexture(engine, assets.environment)
        indirectLight = IndirectLight.Builder()
            .reflections(environment)
            .irradiance(3, checkNotNull(KTX1Loader.getSphericalHarmonics(assets.environment)) { "safe-ibl.ktx has no harmonics" })
            .intensity(SafeMotion.IBL_INTENSITY)
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

        key = directional(SafeMotion.KEY_DIRECTION, SafeMotion.KEY_LUX, SafeMotion.KEY_KELVIN)
        fill = directional(SafeMotion.FILL_DIRECTION, SafeMotion.FILL_LUX, SafeMotion.FILL_KELVIN)
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

    /** Poses the safe: the dial (unwrapped numbers), the unlock timeline u (s) and the wrong code's shake (m along x). */
    fun apply(dialAt: Double, u: Double, shake: Double) {
        val open = SafeMotion.doorAt(u)
        SafeMotion.cameraAt(open, cam)
        camera.lookAt(cam[0], cam[1], cam[2], cam[3], cam[4], cam[5], 0.0, 1.0, 0.0)

        Matrix.setIdentityM(m, 0)
        Matrix.translateM(m, 0, shake.toFloat(), 0f, 0f)
        write(root)
        // opening towards the viewer is a negative turn about y
        turn(door, SafeMotion.DOOR_HINGE, -open * SafeMotion.DOOR_DEG, 0f, 1f, 0f)
        turn(dial, SafeMotion.DIAL_PIVOT, dialAt * SafeMotion.DEG_PER_NUMBER, 0f, 0f, 1f)
        turn(handle, SafeMotion.HANDLE_PIVOT, -SafeMotion.handleAt(u) * SafeMotion.HANDLE_DEG, 0f, 0f, 1f)
        val c = SafeMotion.DOOR_CENTRE
        for (i in 0 until SafeMotion.BOLT_COUNT) {
            SafeMotion.boltDir(i, dir)
            val pull = SafeMotion.boltAt(u, i) * SafeMotion.BOLT_TRAVEL
            Matrix.setIdentityM(m, 0)
            Matrix.translateM(m, 0, (c[0] - dir[0] * pull).toFloat(), (c[1] - dir[1] * pull).toFloat(), c[2])
            write(bolts[i])
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
            val fovY = Math.toDegrees(2 * atan(SafeMotion.tanHalfFovY(asp)))
            camera.setProjection(fovY, asp, SafeMotion.CAMERA_NEAR, SafeMotion.CAMERA_FAR, Camera.Fov.VERTICAL)
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
