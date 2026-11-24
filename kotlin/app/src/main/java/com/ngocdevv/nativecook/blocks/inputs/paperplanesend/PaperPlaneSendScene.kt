package com.ngocdevv.nativecook.blocks.inputs.paperplanesend

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
import kotlin.math.sqrt
import kotlin.math.tan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The block's folder in the app's assets. */
internal const val PLANE_ASSET_DIR = "paperplanesend"

internal class PlaneStageAssets(val model: ByteBuffer, val environment: ByteBuffer)

/** Reads plane.glb and the studio light off the main thread. Throws an IOException when one is missing. */
internal suspend fun loadPlaneStageAssets(context: Context): PlaneStageAssets = withContext(Dispatchers.IO) {
    fun buffer(name: String): ByteBuffer {
        val bytes = context.assets.open("$PLANE_ASSET_DIR/$name").use { it.readBytes() }
        // Filament reads from native memory: the buffer has to be a direct one.
        return ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).apply { rewind() }
    }
    PlaneStageAssets(buffer("plane.glb"), buffer("plane-ibl.ktx"))
}

/**
 * The Filament scene of PlaneStage3D.tsx: the sheet of paper from plane.glb (sixteen rigid panels hinged on the real
 * creases of a dart), lit by the studio IBL (15,000), a key (70,000 lx, 5600 K) and a fill (9,000 lx, 7500 K), ACES
 * (legacy) at exposure 0, FXAA, no bloom, on a transparent frame over the whole chat. The camera looks straight at
 * the plane z = 0 from the distance where the screen's height is its height in dp, so a screen point (x, y) is the
 * world point ((x - w/2) m, (h/2 - y) m, 0) and the flat sheet matches the 2D paper. The fold sets every panel from
 * PlaneMotion.HINGES (the "hinges" driver: T(pivot) · R(axis, angle · ease)). Called on the main thread only.
 */
internal class PaperPlaneSendScene(assets: PlaneStageAssets, refreshRate: Float) {
    private val engine: Engine = Engine.create()
    private val renderer: Renderer = engine.createRenderer()
    private val scene = engine.createScene()
    private val view = engine.createView()
    private val cameraEntity = EntityManager.get().create()
    private val camera: Camera = engine.createCamera(cameraEntity)

    private val materialProvider = UbershaderProvider(engine)
    private val assetLoader = AssetLoader(engine, materialProvider, EntityManager.get())
    private val resourceLoader = ResourceLoader(engine, true)
    private val asset = checkNotNull(assetLoader.createAsset(assets.model)) { "plane.glb did not load" }

    private val environment: Texture
    private val indirectLight: IndirectLight
    private val colorGrading: ColorGrading
    private val keyLight: Int
    private val fillLight: Int
    private val root: Int
    private val panels: IntArray

    /** True once every material of the model has its programs compiled, so the sheet is in the next frame. */
    var ready = false
        private set

    private var swapChain: SwapChain? = null
    private var aspect = 0.0
    private var cameraStamp = 0.0
    private val m = FloatArray(16)
    private val pose = DoubleArray(6)
    private val rot = DoubleArray(4)
    private var lastFold = -1.0
    private var parked = false

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
        view.bloomOptions = View.BloomOptions().apply { enabled = false }
        colorGrading = ColorGrading.Builder()
            .toneMapper(ToneMapper.ACESLegacy())
            .exposure(PlaneMotion.EXPOSURE)
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
        root = asset.getFirstEntityByName("Plane")
        panels = IntArray(PlaneMotion.HINGES.size) { asset.getFirstEntityByName(PlaneMotion.HINGES[it].node) }

        environment = KTX1Loader.createTexture(engine, assets.environment)
        indirectLight = IndirectLight.Builder()
            .reflections(environment)
            .irradiance(3, checkNotNull(KTX1Loader.getSphericalHarmonics(assets.environment)) { "plane-ibl.ktx has no harmonics" })
            .intensity(PlaneMotion.IBL_INTENSITY)
            .build(engine)
        scene.indirectLight = indirectLight

        // Compile the model's programs up front: until they are, Filament leaves the objects out of the frame.
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

        keyLight = addLight(PlaneMotion.KEY_DIRECTION, PlaneMotion.KEY_LUX, 5600f)
        fillLight = addLight(PlaneMotion.FILL_DIRECTION, PlaneMotion.FILL_LUX, 7500f)
        park()
    }

    private fun addLight(direction: FloatArray, lux: Float, kelvin: Float): Int {
        val entity = EntityManager.get().create()
        val c = Colors.cct(kelvin)
        val l = sqrt(direction[0] * direction[0] + direction[1] * direction[1] + direction[2] * direction[2])
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(c[0], c[1], c[2])
            .intensity(lux)
            .direction(direction[0] / l, direction[1] / l, direction[2] / l)
            .castShadows(false)
            .build(engine, entity)
        scene.addEntity(entity)
        return entity
    }

    private fun setTransform(entity: Int, matrix: FloatArray) {
        if (entity == 0) return
        val tm = engine.transformManager
        tm.setTransform(tm.getInstance(entity), matrix)
    }

    /** Outside the 3D part of the film the plane is parked at no size (the layer is transparent then). */
    private fun park() {
        if (parked) return
        parked = true
        Matrix.setIdentityM(m, 0)
        Matrix.scaleM(m, 0, 0.0001f, 0.0001f, 0.0001f)
        setTransform(root, m)
    }

    /** True while the film at `t` shows the 3D sheet (a little either side of the layer's fades). */
    fun shows(t: Double): Boolean = t >= PlaneMotion.LIFT_END - 0.1 && t <= PlaneMotion.FLIGHT_END + 0.1

    /** Poses the scene for film time `t` with the screen's geometry `g` (PlaneMotion.G_*). */
    fun apply(t: Double, g: DoubleArray) {
        // metres per dp: the sheet is g[G_PT] wide on screen
        val mpp = PlaneMotion.SHEET_L / g[PlaneMotion.G_PT]
        val stamp = aspect * 1000 + g[PlaneMotion.G_H] + g[PlaneMotion.G_PT] * 0.001
        if (stamp != cameraStamp) {
            // straight at the plane z = 0 from the distance where the screen's height is g[G_H] dp
            cameraStamp = stamp
            camera.lookAt(0.0, 0.0, g[PlaneMotion.G_H] * mpp / 2 / TAN_HALF, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0)
        }
        if (root == 0) return
        if (!shows(t)) {
            park()
            return
        }
        parked = false

        // the fold: written only when its time changes
        val ft = PlaneMotion.foldTime(t)
        if (ft != lastFold) {
            lastFold = ft
            for (i in panels.indices) {
                val e = panels[i]
                if (e == 0) continue
                val h = PlaneMotion.HINGES[i]
                val k = PlaneMotion.easeInOutCubic(PlaneMotion.seg(ft, h.step[0], h.step[1]))
                Matrix.setIdentityM(m, 0)
                Matrix.translateM(m, 0, h.pivot[0], h.pivot[1], h.pivot[2])
                Matrix.rotateM(m, 0, (h.deg * k).toFloat(), h.axis[0], h.axis[1], h.axis[2])
                setTransform(e, m)
            }
        }

        // the pose: T(place on the plane z = 0, screen dp to metres) · R · S(scale)
        PlaneMotion.poseAt(t, g, pose)
        PlaneMotion.poseRotation(pose[2], pose[3], pose[4], rot)
        val s = pose[5].toFloat()
        Matrix.setIdentityM(m, 0)
        Matrix.translateM(m, 0, ((pose[0] - g[PlaneMotion.G_W] / 2) * mpp).toFloat(), ((g[PlaneMotion.G_H] / 2 - pose[1]) * mpp).toFloat(), 0f)
        Matrix.rotateM(m, 0, Math.toDegrees(rot[3]).toFloat(), rot[0].toFloat(), rot[1].toFloat(), rot[2].toFloat())
        Matrix.scaleM(m, 0, s, s, s)
        setTransform(root, m)
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
            camera.setProjection(PlaneMotion.VFOV_DEG, asp, PlaneMotion.CAMERA_NEAR, PlaneMotion.CAMERA_FAR, Camera.Fov.VERTICAL)
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
        for (light in intArrayOf(keyLight, fillLight)) {
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
        val TAN_HALF = tan(Math.toRadians(PlaneMotion.VFOV_DEG / 2))

        init {
            // Loads Filament's native libraries (filament, gltfio and utils) once per process.
            Utils.init()
        }
    }
}
