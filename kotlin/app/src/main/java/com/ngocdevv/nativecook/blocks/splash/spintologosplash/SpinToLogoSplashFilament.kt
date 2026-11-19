package com.ngocdevv.nativecook.blocks.splash.spintologosplash

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
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
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.google.android.filament.utils.KTX1Loader
import com.google.android.filament.utils.Utils
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// A small Filament + gltfio stage, written for this block (each 3D block keeps its own copy). It mirrors what
// react-native-filament does for the React Native reference: one engine, the block's own .glb and cmgen KTX, the
// image-based light with three bands of harmonics, Filament's default camera exposure (f/16, 1/125 s, ISO 100), ACES
// (legacy) tone mapping with the reference's exposure stops, and a transparent view over the Compose layers.
// Everything runs on the main thread; the block's own clock poses and renders it once per frame, so the 3D never lags
// the 2D layers drawn from the same time.

/** The model and the light, read into direct buffers off the main thread. */
internal class F3Assets(val model: ByteBuffer, val ibl: ByteBuffer)

internal suspend fun loadF3Assets(context: Context, dir: String, model: String, ibl: String): F3Assets =
    withContext(Dispatchers.IO) {
        fun buffer(name: String): ByteBuffer {
            val bytes = context.assets.open("$dir/$name").use { it.readBytes() }
            return ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).apply { rewind() }
        }
        F3Assets(buffer(model), buffer(ibl))
    }

/** react-native-filament's view options that the reference sets. */
internal class F3Options(
    val iblIntensity: Float,
    /** ACES legacy tone mapping with these exposure stops (the reference's setACESLegacyColorGrading). */
    val acesExposure: Float?,
    val bloomStrength: Float = 0f,
    val bloomLevels: Int = 6,
    val bloomThreshold: Boolean = true,
    val bloomHighlight: Float = 1000f,
    val fxaa: Boolean = true,
    val ambientOcclusion: Boolean = false,
)

/** Column-major 4×4 matrices as Filament's TransformManager takes them. */
internal object M4 {
    fun identity(out: FloatArray = FloatArray(16)): FloatArray {
        out.fill(0f); out[0] = 1f; out[5] = 1f; out[10] = 1f; out[15] = 1f; return out
    }

    fun translation(x: Float, y: Float, z: Float, out: FloatArray = FloatArray(16)): FloatArray {
        identity(out); out[12] = x; out[13] = y; out[14] = z; return out
    }

    fun scaling(x: Float, y: Float, z: Float, out: FloatArray = FloatArray(16)): FloatArray {
        identity(out); out[0] = x; out[5] = y; out[10] = z; return out
    }

    /** math::mat4::rotation(angle, axis): the axis is normalised. */
    fun rotation(angle: Double, ax: Float, ay: Float, az: Float, out: FloatArray = FloatArray(16)): FloatArray {
        val l = sqrt((ax * ax + ay * ay + az * az).toDouble()).toFloat()
        val x = ax / l
        val y = ay / l
        val z = az / l
        val c = cos(angle).toFloat()
        val s = sin(angle).toFloat()
        val ic = 1f - c
        identity(out)
        out[0] = x * x * ic + c; out[1] = y * x * ic + z * s; out[2] = z * x * ic - y * s
        out[4] = x * y * ic - z * s; out[5] = y * y * ic + c; out[6] = z * y * ic + x * s
        out[8] = x * z * ic + y * s; out[9] = y * z * ic - x * s; out[10] = z * z * ic + c
        return out
    }

    /** out = a · b (out may not alias a or b). */
    fun mul(a: FloatArray, b: FloatArray, out: FloatArray = FloatArray(16)): FloatArray {
        for (c in 0 until 4) for (r in 0 until 4) {
            var s = 0f
            for (k in 0 until 4) s += a[k * 4 + r] * b[c * 4 + k]
            out[c * 4 + r] = s
        }
        return out
    }
}

/**
 * The engine, the scene and the loaded model. Subclasses add lights and pose the model in [pose]; [render] draws one
 * frame. Created on the main thread once the files are read.
 */
internal open class F3World(assets: F3Assets, options: F3Options, refreshRate: Float) {
    val engine: Engine = Engine.create()
    val renderer: Renderer = engine.createRenderer()
    val scene = engine.createScene()
    val view: View = engine.createView()
    private val cameraEntity = EntityManager.get().create()
    val camera: Camera = engine.createCamera(cameraEntity)
    private val materialProvider = UbershaderProvider(engine)
    private val assetLoader = AssetLoader(engine, materialProvider, EntityManager.get())
    private val resourceLoader = ResourceLoader(engine, true)
    val asset = checkNotNull(assetLoader.createAsset(assets.model)) { "The model did not load" }
    private val environment: Texture
    private val indirectLight: IndirectLight
    private val colorGrading: ColorGrading?
    private val lights = ArrayList<Int>()
    private var swapChain: SwapChain? = null
    val transforms get() = engine.transformManager
    val renderables get() = engine.renderableManager
    private val scratch = FloatArray(16)
    private var pendingCompiles = 0
    private var framesDrawn = 0

    /** True once every material's programs are compiled and a few frames have been drawn with them, so the first
     *  frame the block shows has the whole model in it (Filament leaves out objects whose shaders are still compiling). */
    val ready: Boolean get() = pendingCompiles == 0 && framesDrawn >= 3
    var width = 1
        private set
    var height = 1
        private set

    init {
        renderer.clearOptions = Renderer.ClearOptions().apply { clear = true }
        renderer.setDisplayInfo(Renderer.DisplayInfo().apply { this.refreshRate = refreshRate })
        view.scene = scene
        view.camera = camera
        view.blendMode = View.BlendMode.TRANSLUCENT
        camera.setExposure(16f, 1f / 125f, 100f)
        view.antiAliasing = if (options.fxaa) View.AntiAliasing.FXAA else View.AntiAliasing.NONE
        view.ambientOcclusionOptions = view.ambientOcclusionOptions.apply { enabled = options.ambientOcclusion }
        view.temporalAntiAliasingOptions = view.temporalAntiAliasingOptions.apply { enabled = false }
        if (options.bloomStrength > 0f) {
            view.bloomOptions = view.bloomOptions.apply {
                enabled = true
                strength = options.bloomStrength
                levels = options.bloomLevels
                threshold = options.bloomThreshold
                highlight = options.bloomHighlight
                quality = View.QualityLevel.MEDIUM
            }
        }
        colorGrading = options.acesExposure?.let {
            ColorGrading.Builder().toneMapper(ToneMapper.ACESLegacy()).exposure(it).build(engine)
        }
        colorGrading?.let { view.colorGrading = it }

        resourceLoader.loadResources(asset)
        asset.releaseSourceData()
        scene.addEntities(asset.entities)
        for (e in asset.renderableEntities) {
            val i = renderables.getInstance(e)
            renderables.setCastShadows(i, false)
            renderables.setReceiveShadows(i, false)
        }

        // the materials the model actually uses (the provider's own list holds slots it never filled)
        val materials = LinkedHashMap<Long, Material>()
        for (e in asset.renderableEntities) {
            val ri = renderables.getInstance(e)
            for (k in 0 until renderables.getPrimitiveCount(ri)) {
                val mat = renderables.getMaterialInstanceAt(ri, k).material
                materials[mat.nativeObject] = mat
            }
        }
        pendingCompiles = materials.size
        val main = Handler(Looper.getMainLooper())
        for (material in materials.values) {
            material.compile(
                Material.CompilerPriorityQueue.HIGH,
                Material.UserVariantFilterBit.DIRECTIONAL_LIGHTING or Material.UserVariantFilterBit.DYNAMIC_LIGHTING or
                    Material.UserVariantFilterBit.SKINNING,
                main,
            ) { pendingCompiles-- }
        }

        environment = KTX1Loader.createTexture(engine, assets.ibl)
        indirectLight = IndirectLight.Builder()
            .reflections(environment)
            .irradiance(3, checkNotNull(KTX1Loader.getSphericalHarmonics(assets.ibl)) { "The light has no harmonics" })
            .intensity(options.iblIntensity)
            .build(engine)
        scene.indirectLight = indirectLight
    }

    /** The node called `name`, or 0 when the model has none. */
    fun entity(name: String): Int = asset.getFirstEntityByName(name)

    /** Replaces the node's local transform (react-native-filament's set…(…, false)). */
    fun setLocal(entity: Int, m: FloatArray) {
        if (entity == 0) return
        transforms.setTransform(transforms.getInstance(entity), m)
    }

    fun getLocal(entity: Int, out: FloatArray): FloatArray {
        transforms.getTransform(transforms.getInstance(entity), out)
        return out
    }

    /** react-native-filament's set…(…, true): `m · current`. */
    fun preMultiply(entity: Int, m: FloatArray) {
        if (entity == 0) return
        val i = transforms.getInstance(entity)
        val cur = FloatArray(16)
        transforms.getTransform(i, cur)
        transforms.setTransform(i, M4.mul(m, cur, scratch))
    }

    /** A directional light as useLightEntity makes it: direction is where the light travels, colour from Kelvin. */
    fun addDirectional(dx: Float, dy: Float, dz: Float, lux: Float, kelvin: Float, castShadows: Boolean = false): Int {
        val e = EntityManager.get().create()
        val c = Colors.cct(kelvin)
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(c[0], c[1], c[2])
            .intensity(lux)
            .direction(dx, dy, dz)
            .castShadows(castShadows)
            .build(engine, e)
        scene.addEntity(e)
        lights.add(e)
        return e
    }

    fun addPoint(x: Float, y: Float, z: Float, lumens: Float, kelvin: Float, falloff: Float): Int {
        val e = EntityManager.get().create()
        val c = Colors.cct(kelvin)
        LightManager.Builder(LightManager.Type.POINT)
            .color(c[0], c[1], c[2])
            .intensity(lumens)
            .position(x, y, z)
            .falloff(falloff)
            .castShadows(false)
            .build(engine, e)
        scene.addEntity(e)
        lights.add(e)
        return e
    }

    /** The material of the renderable's primitive `i`. */
    fun materialAt(entity: Int, i: Int = 0): MaterialInstance =
        renderables.getMaterialInstanceAt(renderables.getInstance(entity), i)

    fun primitiveCount(entity: Int): Int = renderables.getPrimitiveCount(renderables.getInstance(entity))

    fun setShadows(entity: Int, cast: Boolean, receive: Boolean) {
        if (entity == 0) return
        val i = renderables.getInstance(entity)
        if (i == 0) return
        renderables.setCastShadows(i, cast)
        renderables.setReceiveShadows(i, receive)
    }

    fun lookAt(ex: Float, ey: Float, ez: Float, tx: Float, ty: Float, tz: Float) {
        camera.lookAt(ex.toDouble(), ey.toDouble(), ez.toDouble(), tx.toDouble(), ty.toDouble(), tz.toDouble(), 0.0, 1.0, 0.0)
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

    open fun setViewport(width: Int, height: Int) {
        this.width = width
        this.height = height
        view.viewport = Viewport(0, 0, width, height)
    }

    fun render(frameTimeNanos: Long): Boolean {
        val target = swapChain ?: return false
        if (!renderer.beginFrame(target, frameTimeNanos)) return false
        renderer.render(view)
        renderer.endFrame()
        if (pendingCompiles == 0) framesDrawn++
        return true
    }

    open fun destroy() {
        releaseSurface()
        val entities = EntityManager.get()
        for (light in lights) {
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
        colorGrading?.let(engine::destroyColorGrading)
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(cameraEntity)
        entities.destroy(cameraEntity)
        engine.destroy()
    }

    companion object {
        init {
            Utils.init()
        }
    }
}

/** Owns the world, its TextureView and surface. The block's clock calls [frame] once per display frame. */
internal class F3Host<W : F3World> : UiHelper.RendererCallback {
    var world: W? = null
        private set
    var view: TextureView? = null
        private set
    private var surface: Surface? = null
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).apply { isOpaque = false }
    private var destroyed = false

    val ready: Boolean get() = world?.ready == true

    fun attach(textureView: TextureView) {
        view = textureView
        uiHelper.renderCallback = this
        uiHelper.attachTo(textureView)
    }

    fun start(world: W) {
        if (destroyed) {
            world.destroy(); return
        }
        this.world = world
        surface?.let { world.setSurface(it, uiHelper.swapChainFlags) }
        if (surfaceWidth > 0 && surfaceHeight > 0) world.setViewport(surfaceWidth, surfaceHeight)
    }

    /** Poses the world and draws it; nothing happens before the files are loaded or without a surface. */
    inline fun frame(frameTimeNanos: Long, pose: (W) -> Unit) {
        val w = world ?: return
        if (!hasSurface()) return
        pose(w)
        w.render(frameTimeNanos)
    }

    fun hasSurface(): Boolean = surface != null && surfaceWidth > 0

    override fun onNativeWindowChanged(surface: Surface) {
        this.surface = surface
        world?.setSurface(surface, uiHelper.swapChainFlags)
    }

    override fun onDetachedFromSurface() {
        surface = null
        world?.releaseSurface()
    }

    override fun onResized(width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        world?.setViewport(width, height)
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        uiHelper.detach()
        world?.destroy()
        world = null
        surface = null
    }
}

internal fun displayRefreshRate(context: Context): Float {
    val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
    return display?.refreshRate ?: 60f
}

/**
 * The transparent TextureView the host draws into. `create` builds the world from the loaded files on the main thread;
 * a block copied without its asset folder logs and shows no 3D, and the screen keeps working.
 */
@Composable
internal fun <W : F3World> F3Stage(
    host: F3Host<W>,
    load: suspend (Context) -> F3Assets,
    create: (F3Assets, Float) -> W,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    LaunchedEffect(host) {
        val assets = try {
            load(context.applicationContext)
        } catch (error: IOException) {
            Log.e("NativeCook3D", "The 3D stage is not shown: its asset folder could not be read.", error)
            return@LaunchedEffect
        }
        host.start(create(assets, displayRefreshRate(context)))
    }
    DisposableEffect(host) { onDispose { host.destroy() } }
    AndroidView(
        factory = { c -> TextureView(c).also { it.isOpaque = false; host.attach(it) } },
        modifier = modifier,
    )
}
