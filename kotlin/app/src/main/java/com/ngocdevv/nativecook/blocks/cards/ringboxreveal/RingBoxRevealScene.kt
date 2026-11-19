package com.ngocdevv.nativecook.blocks.cards.ringboxreveal

import android.content.Context
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import android.view.Surface
import com.google.android.filament.Camera
import com.google.android.filament.Colors
import com.google.android.filament.ColorGrading
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
import kotlin.math.cbrt
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The block's folder in the app's assets. */
internal const val RING_ASSET_DIR = "ring-box-reveal"

internal class RingStageAssets(val model: ByteBuffer, val environment: ByteBuffer)

/** Reads ring.glb and the studio light off the main thread. Throws an IOException when a file is missing. */
internal suspend fun loadRingStageAssets(context: Context): RingStageAssets = withContext(Dispatchers.IO) {
    fun buffer(name: String): ByteBuffer {
        val bytes = context.assets.open("$RING_ASSET_DIR/$name").use { it.readBytes() }
        // Filament reads from native memory: the buffer has to be a direct one.
        return ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).apply { rewind() }
    }
    RingStageAssets(buffer("ring.glb"), buffer("ring-studio-ibl.ktx"))
}

/**
 * The Filament scene of RingStage3D.tsx: the velvet box, the solitaire and the box's reflection under the floor
 * (baked in ring.glb as BaseM / LidM), lit by the studio IBL (36,000), a warm key (80,000 lx, 5600 K) and a cool fill
 * (16,000 lx, 7000 K), ACES (legacy) at exposure 0, Filament's default camera exposure, FXAA and a faint bloom, on a
 * transparent frame so the Canvas floor behind shows through. Called on the main thread only.
 */
internal class RingBoxRevealScene(assets: RingStageAssets, refreshRate: Float) {
    private val engine: Engine = Engine.create()
    private val renderer: Renderer = engine.createRenderer()
    private val scene = engine.createScene()
    private val view = engine.createView()
    private val cameraEntity = EntityManager.get().create()
    private val camera: Camera = engine.createCamera(cameraEntity)

    private val materialProvider = UbershaderProvider(engine)
    private val assetLoader = AssetLoader(engine, materialProvider, EntityManager.get())
    private val resourceLoader = ResourceLoader(engine, true)
    private val asset = checkNotNull(assetLoader.createAsset(assets.model)) { "ring.glb did not load" }

    private val environment: Texture
    private val indirectLight: IndirectLight
    private val colorGrading: ColorGrading
    private val keyLight: Int
    private val fillLight: Int

    private val base: Int
    private val baseM: Int
    private val lid: Int
    private val lidM: Int
    private val ring: Int
    private val setting: IntArray
    private val metal: List<MaterialInstance>
    private val stone: List<MaterialInstance>

    /** True once every material of the model has its programs compiled, so the box is in the next frame. */
    var ready = false
        private set

    private var swapChain: SwapChain? = null
    private var aspect = 0.0
    private val cam = DoubleArray(6)
    private val m = FloatArray(16)
    private val a = FloatArray(16)
    private val b = FloatArray(16)
    private var lastMat = ""
    private var lastCarat = -1.0

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
            strength = 0.08f
            levels = 5
            threshold = true
            highlight = 1000f
            quality = View.QualityLevel.MEDIUM
        }
        colorGrading = ColorGrading.Builder()
            .toneMapper(ToneMapper.ACESLegacy())
            .exposure(RingMotion.EXPOSURE)
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

        base = asset.getFirstEntityByName("Base")
        baseM = asset.getFirstEntityByName("BaseM")
        lid = asset.getFirstEntityByName("Lid")
        lidM = asset.getFirstEntityByName("LidM")
        ring = asset.getFirstEntityByName("Ring")
        fun material(name: String): MaterialInstance? {
            val e = asset.getFirstEntityByName(name)
            if (e == 0) return null
            return renderables.getMaterialInstanceAt(renderables.getInstance(e), 0)
        }
        setting = arrayOf("Stone", "Head", "Gallery").map { asset.getFirstEntityByName(it) }.filter { it != 0 }.toIntArray()
        stone = listOfNotNull(material("Stone"))
        metal = listOfNotNull(material("Head"), material("Gallery"), material("Band"))

        environment = KTX1Loader.createTexture(engine, assets.environment)
        indirectLight = IndirectLight.Builder()
            .reflections(environment)
            .irradiance(3, checkNotNull(KTX1Loader.getSphericalHarmonics(assets.environment)) { "ring-studio-ibl.ktx has no harmonics" })
            .intensity(RingMotion.IBL_INTENSITY)
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

        keyLight = addLight(RingMotion.KEY_DIRECTION, RingMotion.KEY_LUX, 5600f)
        fillLight = addLight(RingMotion.FILL_DIRECTION, RingMotion.FILL_LUX, 7000f)
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

    /** Poses the scene: the camera, the box's turn, the lid on its hinge, the carat and the blended materials. */
    fun apply(p: RingPose) {
        RingMotion.cameraAt(p.open, p.shot, p.t, cam)
        camera.lookAt(cam[0], cam[1], cam[2], cam[3], cam[4], cam[5], 0.0, 1.0, 0.0)

        val yawDeg = Math.toDegrees(p.yaw).toFloat()
        Matrix.setRotateM(m, 0, yawDeg, 0f, 1f, 0f)
        setTransform(base, m)
        setTransform(baseM, m)
        // the lid turns about its hinge, then with the box; the mirrored lid the other way about the mirrored hinge
        val lidDeg = Math.toDegrees(RingMotion.LID_OPEN * p.open).toFloat()
        val h = RingMotion.LID_HINGE
        lidMatrix(yawDeg, h.x.toFloat(), h.y.toFloat(), h.z.toFloat(), -lidDeg)
        setTransform(lid, m)
        lidMatrix(yawDeg, h.x.toFloat(), -h.y.toFloat(), h.z.toFloat(), lidDeg)
        setTransform(lidM, m)
        Matrix.setRotateM(a, 0, yawDeg, 0f, 1f, 0f)
        Matrix.setIdentityM(b, 0)
        Matrix.translateM(b, 0, 0f, RingMotion.RING_Y.toFloat(), 0f)
        Matrix.multiplyMM(m, 0, a, 0, b, 0)
        setTransform(ring, m)

        // carat: the stone and its prongs scale about the girdle's centre (cube root of the weight)
        if (abs(p.carat - lastCarat) > 0.0005) {
            lastCarat = p.carat
            val k = cbrt(p.carat / RingMotion.CARAT_MODEL).toFloat()
            Matrix.setIdentityM(m, 0)
            Matrix.translateM(m, 0, 0f, (RingMotion.STONE_LOCAL_Y * (1 - k)).toFloat(), 0f)
            Matrix.scaleM(m, 0, k, k, k)
            for (e in setting) setTransform(e, m)
        }

        // metal and stone colours, blended (written only when they change)
        val mk = RingMotion.easeInOutSine(RingMotion.clamp01(p.mProg))
        val sk = RingMotion.easeInOutSine(RingMotion.clamp01(p.sProg))
        val key = "${p.mFrom},${p.mTo},${(mk * 300).roundToInt()},${p.sFrom},${p.sTo},${(sk * 300).roundToInt()}"
        if (key != lastMat) {
            lastMat = key
            val ma = RingMotion.METALS[p.mFrom]
            val mb = RingMotion.METALS[p.mTo]
            val ca = RingMotion.linear(ma.color)
            val cb = RingMotion.linear(mb.color)
            val rough = RingMotion.lerp(ma.rough, mb.rough, mk).toFloat()
            for (mi in metal) {
                mi.setParameter(
                    "baseColorFactor",
                    RingMotion.lerp(ca[0], cb[0], mk).toFloat(),
                    RingMotion.lerp(ca[1], cb[1], mk).toFloat(),
                    RingMotion.lerp(ca[2], cb[2], mk).toFloat(),
                    1f,
                )
                mi.setParameter("roughnessFactor", rough)
            }
            val sa = RingMotion.STONES[p.sFrom]
            val sb = RingMotion.STONES[p.sTo]
            val da = RingMotion.linear(sa.color)
            val db = RingMotion.linear(sb.color)
            val glow = RingMotion.lerp(sa.glow, sb.glow, sk)
            val r = RingMotion.lerp(da[0], db[0], sk)
            val g = RingMotion.lerp(da[1], db[1], sk)
            val bl = RingMotion.lerp(da[2], db[2], sk)
            for (mi in stone) {
                // the base colour tints the stone's mirror reflections; the emissive lights its baked facet pattern
                mi.setParameter("baseColorFactor", r.toFloat(), g.toFloat(), bl.toFloat(), 1f)
                mi.setParameter("emissiveFactor", (r * glow).toFloat(), (g * glow).toFloat(), (bl * glow).toFloat())
            }
        }
    }

    /** m = Ry(yaw) * T(hinge) * Rx(lid). */
    private fun lidMatrix(yawDeg: Float, hx: Float, hy: Float, hz: Float, lidDeg: Float) {
        Matrix.setRotateM(a, 0, yawDeg, 0f, 1f, 0f)
        Matrix.translateM(a, 0, hx, hy, hz)
        Matrix.setRotateM(b, 0, lidDeg, 1f, 0f, 0f)
        Matrix.multiplyMM(m, 0, a, 0, b, 0)
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
            camera.setProjection(RingMotion.HFOV_DEG, asp, RingMotion.CAMERA_NEAR, RingMotion.CAMERA_FAR, Camera.Fov.HORIZONTAL)
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

        init {
            // Loads Filament's native libraries (filament, gltfio and utils) once per process.
            Utils.init()
        }
    }
}
