package com.ngocdevv.nativecook.blocks.progress.espressobrewprogress

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
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val ESPRESSO_ASSET_DIR = "espressobrewprogress"

internal class EspressoStageAssets(val model: ByteBuffer, val environment: ByteBuffer)

/** Reads the model and its image-based light off the main thread. Throws an IOException when one is missing. */
internal suspend fun loadEspressoStageAssets(context: Context): EspressoStageAssets = withContext(Dispatchers.IO) {
    fun buffer(name: String): ByteBuffer {
        val bytes = context.assets.open("$ESPRESSO_ASSET_DIR/${ESPRESSO_ASSET_DIR}_$name").use { it.readBytes() }
        // Filament reads from native memory: the buffer has to be a direct one.
        return ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).apply { rewind() }
    }
    EspressoStageAssets(buffer("espresso.glb"), buffer("espresso-ibl.ktx"))
}

/**
 * The Filament scene of EspressoStage3D.tsx: the machine, the cup and the studio sweep from espresso.glb, IBL 30,000,
 * key and fill directional lights, no shadows (the contact shadow is baked), ACES (legacy) at exposure 0, FXAA, bloom
 * 0.06, on a transparent frame. The file has no animations and none are applied: the needle, the group light, the cup,
 * the coffee, the crema and the two streams are set from the loop's values every frame, composed as RNF does
 * (each `multiplyCurrent` call pre-multiplies the node's local matrix). Main thread only.
 */
internal class EspressoBrewProgressScene(assets: EspressoStageAssets, refreshRate: Float) {
    private val engine: Engine = Engine.create()
    private val renderer: Renderer = engine.createRenderer()
    private val scene = engine.createScene()
    private val view = engine.createView()
    private val cameraEntity = EntityManager.get().create()
    private val camera: Camera = engine.createCamera(cameraEntity)

    private val materialProvider = UbershaderProvider(engine)
    private val assetLoader = AssetLoader(engine, materialProvider, EntityManager.get())
    private val resourceLoader = ResourceLoader(engine, true)
    private val asset = checkNotNull(assetLoader.createAsset(assets.model)) { "espresso.glb did not load" }

    private val environment: Texture
    private val indirectLight: IndirectLight
    private val colorGrading: ColorGrading
    private val key: Int
    private val fill: Int
    private val needle: Int
    private val cup: Int
    private val coffee: Int
    private val crema: Int
    private val streams: IntArray
    private val light: MaterialInstance?
    private var lightShown = -1.0

    /** True once every material of the model has its programs compiled. */
    var ready = false
        private set

    private var swapChain: SwapChain? = null
    private var aspect = 0.0
    private val cam = DoubleArray(6)
    private val slide = DoubleArray(2)
    private val st = DoubleArray(3)
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
            strength = EspressoMotion.BLOOM_STRENGTH
            levels = 5
            threshold = true
            highlight = 1000f
            quality = View.QualityLevel.MEDIUM
        }
        colorGrading = ColorGrading.Builder()
            .toneMapper(ToneMapper.ACESLegacy())
            .exposure(EspressoMotion.EXPOSURE)
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

        needle = asset.getFirstEntityByName("Needle")
        cup = asset.getFirstEntityByName("Cup")
        coffee = asset.getFirstEntityByName("Coffee")
        crema = asset.getFirstEntityByName("Crema")
        streams = IntArray(EspressoMotion.STREAM_TIPS.size) { asset.getFirstEntityByName("Stream$it") }
        // the group head's light band has its own material: its emissive follows the light
        val lightEntity = asset.getFirstEntityByName("GroupLight")
        val lightInstance = if (lightEntity == 0) 0 else renderables.getInstance(lightEntity)
        light = if (lightInstance == 0) null else renderables.getMaterialInstanceAt(lightInstance, 0)

        environment = KTX1Loader.createTexture(engine, assets.environment)
        indirectLight = IndirectLight.Builder()
            .reflections(environment)
            .irradiance(3, checkNotNull(KTX1Loader.getSphericalHarmonics(assets.environment)) { "espresso-ibl.ktx has no harmonics" })
            .intensity(EspressoMotion.IBL_INTENSITY)
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

        key = directional(EspressoMotion.KEY_DIRECTION, EspressoMotion.KEY_LUX, EspressoMotion.KEY_KELVIN)
        fill = directional(EspressoMotion.FILL_DIRECTION, EspressoMotion.FILL_LUX, EspressoMotion.FILL_KELVIN)
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

    /** RNF's setEntityScale(HIDDEN, false): the node shrunk to nothing at its parent's origin. */
    private fun hide(entity: Int) {
        Matrix.setIdentityM(m, 0)
        Matrix.scaleM(m, 0, HIDDEN, HIDDEN, HIDDEN)
        write(entity)
    }

    /** Poses the machine: the film's time (s), the liquid's dose (0 single .. 1 double) and a finger's orbit (deg). */
    fun apply(t: Double, dose: Double, orbit: Double) {
        EspressoMotion.cameraAt(EspressoMotion.camAt(t), orbit, cam)
        camera.lookAt(cam[0], cam[1], cam[2], cam[3], cam[4], cam[5], 0.0, 1.0, 0.0)

        // the needle turns about z through its pivot: T(pivot) · R(z)
        if (needle != 0) {
            val p = EspressoMotion.NEEDLE_PIVOT
            Matrix.setIdentityM(m, 0)
            Matrix.translateM(m, 0, p[0], p[1], p[2])
            Matrix.rotateM(m, 0, EspressoMotion.needleDeg(EspressoMotion.barAt(t)).toFloat(), 0f, 0f, 1f)
            write(needle)
        }
        val li = light
        if (li != null) {
            val k = EspressoMotion.lightAt(t)
            if (abs(k - lightShown) > LIGHT_STEP || (k == 0.0 && lightShown != 0.0)) {
                val e = EspressoMotion.GROUP_LIGHT_ON * k.toFloat()
                val c = EspressoMotion.GROUP_LIGHT_RGB
                li.setParameter("emissiveFactor", c[0] * e, c[1] * e, c[2] * e)
                lightShown = k
            }
        }

        // the cup and what is in it
        EspressoMotion.cupAt(t, slide)
        val rest = EspressoMotion.CUP_REST
        if (cup != 0) {
            Matrix.setIdentityM(m, 0)
            Matrix.translateM(m, 0, rest[0] + slide[0].toFloat(), rest[1] + slide[1].toFloat(), rest[2])
            write(cup)
        }
        val total = EspressoMotion.lerp(EspressoMotion.DOSE_H[0], EspressoMotion.DOSE_H[1], dose) * EspressoMotion.fillAt(t)
        val cremaH = min(total, EspressoMotion.lerp(EspressoMotion.CREMA_H[0], EspressoMotion.CREMA_H[1], dose) * EspressoMotion.cremaAt(t))
        val body = total - cremaH
        if (coffee != 0) {
            if (body < MIN_H) {
                hide(coffee)
            } else {
                // T(base) · S(1, body / ref, 1)
                Matrix.setIdentityM(m, 0)
                Matrix.translateM(m, 0, 0f, EspressoMotion.COFFEE_BASE.toFloat(), 0f)
                Matrix.scaleM(m, 0, 1f, (body / EspressoMotion.COFFEE_REF).toFloat(), 1f)
                write(coffee)
            }
        }
        if (crema != 0) {
            if (cremaH < MIN_H) {
                hide(crema)
            } else {
                // T(on the coffee) · S(1, crema / ref, 1) · R(y, swirl)
                Matrix.setIdentityM(m, 0)
                Matrix.translateM(m, 0, 0f, (EspressoMotion.COFFEE_BASE + body).toFloat(), 0f)
                Matrix.scaleM(m, 0, 1f, (cremaH / EspressoMotion.CREMA_REF).toFloat(), 1f)
                Matrix.rotateM(m, 0, Math.toDegrees(EspressoMotion.swirlAt(t)).toFloat(), 0f, 1f, 0f)
                write(crema)
            }
        }

        // the streams run from the spout tips to the surface; only while the cup stands under them
        val surface = rest[1] + slide[1] + EspressoMotion.COFFEE_BASE + total
        val gap = EspressoMotion.SPOUT_TIP_Y - surface
        for (i in streams.indices) {
            val e = streams[i]
            if (e == 0) continue
            EspressoMotion.streamAt(t, i, st)
            val top = EspressoMotion.SPOUT_TIP_Y - st[1] * gap
            val len = (st[0] - st[1]) * gap
            if (len < MIN_H || st[2] <= 0 || slide[0] > 0.001) {
                hide(e)
                continue
            }
            val tip = EspressoMotion.STREAM_TIPS[i]
            // T(tip, top) · S(width, len / ref, width)
            Matrix.setIdentityM(m, 0)
            Matrix.translateM(m, 0, tip[0], top.toFloat(), tip[2])
            Matrix.scaleM(m, 0, st[2].toFloat(), (len / EspressoMotion.STREAM_REF).toFloat(), st[2].toFloat())
            write(e)
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
            val fovY = Math.toDegrees(2 * atan(EspressoMotion.tanHalfFovY(asp)))
            camera.setProjection(fovY, asp, EspressoMotion.CAMERA_NEAR, EspressoMotion.CAMERA_FAR, Camera.Fov.VERTICAL)
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

        /** Below this a layer or a stream is not drawn (m). */
        const val MIN_H = 0.00015

        /** A hidden node's scale. */
        const val HIDDEN = 1e-4f

        /** The light's emissive is only sent when it moved this much. */
        const val LIGHT_STEP = 0.004

        init {
            Utils.init()
        }
    }
}
