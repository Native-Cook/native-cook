package com.ngocdevv.nativecook.blocks.gestures.chooseyourball

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.Surface
import com.google.android.filament.Camera
import com.google.android.filament.ColorGrading
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Renderer
import com.google.android.filament.SwapChain
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import com.google.android.filament.ToneMapper
import com.google.android.filament.Viewport
import com.google.android.filament.android.TextureHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.google.android.filament.utils.KTX1Loader
import com.google.android.filament.utils.Utils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** The block's folder in the app's assets. */
internal const val BALL_ASSET_DIR = "chooseyourball"

/** The files of the scene, read and decoded off the main thread before the engine is built. */
internal class BallStageAssets(
    val model: ByteBuffer,
    val environment: ByteBuffer,
    /** The upper shell's paint for each ball, 512 × 256 sRGB. */
    val shells: Map<BallId, Bitmap>,
)

/**
 * Reads the model, the image-based light and all 26 shells, so no swipe waits for a texture.
 * Throws an IOException when a file is missing.
 */
internal suspend fun loadBallStageAssets(context: Context): BallStageAssets =
    withContext(Dispatchers.IO) {
        val assets = context.assets
        fun buffer(name: String): ByteBuffer {
            val bytes = assets.open("$BALL_ASSET_DIR/$name").use { it.readBytes() }
            // Filament reads from native memory: the buffer has to be a direct one.
            return ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).apply { rewind() }
        }
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        }
        val shells = BallId.entries.associateWith { ball ->
            ensureActive()
            assets.open("$BALL_ASSET_DIR/shells/${ball.assetName}.png").use { stream ->
                checkNotNull(BitmapFactory.decodeStream(stream, null, options)) { "Shell of ${ball.assetName} did not decode" }
            }
        }
        BallStageAssets(buffer("ball-filament.glb"), buffer("warehouse-ibl.ktx"), shells)
    }

/**
 * The Filament scene of the ball (BallStage3D.tsx): one engine, the model, its 26 shell
 * textures, the image-based light, four direct lights, the camera and the colour grading. The
 * view's settings are Filament's defaults, as in the reference: post-processing with FXAA and
 * temporal dithering, no bloom, no shadows cast.
 *
 * Everything here is called on the thread that created it (the main thread).
 */
internal class BallStageScene(assets: BallStageAssets, refreshRate: Float) {
    private val engine: Engine = Engine.create()
    private val renderer: Renderer = engine.createRenderer()
    private val scene = engine.createScene()
    private val view = engine.createView()
    private val cameraEntity = EntityManager.get().create()
    private val camera: Camera = engine.createCamera(cameraEntity)

    private val materialProvider = UbershaderProvider(engine)
    private val assetLoader = AssetLoader(engine, materialProvider, EntityManager.get())
    private val resourceLoader = ResourceLoader(engine, true)
    private val asset = checkNotNull(assetLoader.createAsset(assets.model)) { "ball-filament.glb did not load" }

    private val shellTextures: Map<BallId, Texture>
    // The reference's sampler for a replaced texture: linear both ways, repeating.
    private val shellSampler = TextureSampler(
        TextureSampler.MinFilter.LINEAR,
        TextureSampler.MagFilter.LINEAR,
        TextureSampler.WrapMode.REPEAT,
    )
    private val upperMaterial: MaterialInstance
    private val markEntities: Map<BallMeshMark, Int>

    private val environment: Texture
    private val indirectLight: IndirectLight
    private val colorGrading: ColorGrading
    private val keyLight: Int
    private val fillLight: Int
    private val rimLight: Int
    private val rimBackLight: Int

    private var swapChain: SwapChain? = null
    private val rootTransform = FloatArray(16)
    private val markTransform = FloatArray(16)

    init {
        // A transparent frame: the Compose backdrop shows around the ball.
        renderer.clearOptions = Renderer.ClearOptions().apply { clear = true }
        renderer.setDisplayInfo(Renderer.DisplayInfo().apply { this.refreshRate = refreshRate })
        view.scene = scene
        view.camera = camera
        camera.setExposure(BallCamera.APERTURE, BallCamera.SHUTTER_SPEED, BallCamera.SENSITIVITY)
        camera.setProjection(
            BallCamera.FOV_DEGREES, BallCamera.ASPECT, BallCamera.NEAR, BallCamera.FAR, Camera.Fov.VERTICAL,
        )

        colorGrading = ColorGrading.Builder()
            .toneMapper(ToneMapper.ACESLegacy())
            .exposure(BALL_EXPOSURE_STOPS)
            .build(engine)
        view.colorGrading = colorGrading

        // The model: used as exported, a ball of radius 1 at the origin with its button on +Z.
        resourceLoader.loadResources(asset)
        asset.releaseSourceData()
        scene.addEntities(asset.entities)

        upperMaterial = applyMaterial(BallEntity.SHELL_UPPER, BallMaterials.upper)
        applyMaterial(BallEntity.SHELL_LOWER, BallMaterials.lower)
        applyMaterial(BallEntity.BAND, BallMaterials.band)
        applyMaterial(BallEntity.BUTTON, BallMaterials.button)
        markEntities = BallMeshMark.entries.associateWith { mark ->
            applyMaterial(mark.entity, mark.material)
            entity(mark.entity)
        }

        shellTextures = assets.shells.mapValues { (_, bitmap) ->
            Texture.Builder()
                .width(bitmap.width)
                .height(bitmap.height)
                .levels(1)
                .sampler(Texture.Sampler.SAMPLER_2D)
                .format(Texture.InternalFormat.SRGB8_A8)
                .build(engine)
                .also { TextureHelper.setBitmap(engine, it, 0, bitmap) }
        }

        // The light of the room: the cube map as baked by cmgen, with the three bands of
        // spherical harmonics stored in the same file.
        environment = KTX1Loader.createTexture(engine, assets.environment)
        indirectLight = IndirectLight.Builder()
            .reflections(environment)
            .irradiance(3, checkNotNull(KTX1Loader.getSphericalHarmonics(assets.environment)) { "warehouse-ibl.ktx has no harmonics" })
            .intensity(BALL_ENVIRONMENT_INTENSITY)
            .build(engine)
        scene.indirectLight = indirectLight

        keyLight = addLight(BallLights.key, hexToLinear(0xFFFFFF))
        fillLight = addLight(BallLights.fill, hexToLinear(BallLights.FILL_COLOR))
        rimLight = addLight(BallLights.rim, hexToLinear(0xFFFFFF))
        rimBackLight = addLight(BallLights.rimBack, hexToLinear(0xFFFFFF))
    }

    private fun entity(name: String): Int =
        asset.getFirstEntityByName(name).also { check(it != 0) { "The model has no entity \"$name\"" } }

    private fun applyMaterial(entityName: String, material: BallMaterial): MaterialInstance {
        val renderables = engine.renderableManager
        val instance = renderables.getMaterialInstanceAt(renderables.getInstance(entity(entityName)), 0)
        val (r, g, b) = material.baseColor
        instance.setParameter("baseColorFactor", r, g, b, 1f)
        instance.setParameter("metallicFactor", material.metallic)
        instance.setParameter("roughnessFactor", material.roughness)
        return instance
    }

    private fun addLight(light: BallLight, linearColor: FloatArray): Int {
        val entity = EntityManager.get().create()
        val (x, y, z) = light.position
        val builder = LightManager.Builder(
            if (light.directional) LightManager.Type.DIRECTIONAL else LightManager.Type.POINT,
        )
            .color(linearColor[0], linearColor[1], linearColor[2])
            .intensity(light.intensity)
            .position(x, y, z)
        if (light.directional) {
            val (dx, dy, dz) = BallLights.directionToOrigin(light.position)
            builder.direction(dx, dy, dz)
        } else {
            builder.falloff(light.falloff)
        }
        builder.build(engine, entity)
        scene.addEntity(entity)
        return entity
    }

    /**
     * Paints the ball as `ball`: the texture of the upper shell, the markings that are meshes, and
     * the colour of the two rim lights. Nothing is loaded here; it is three parameter changes.
     */
    fun setVariant(ball: BallId) {
        upperMaterial.setParameter("baseColorMap", shellTextures.getValue(ball), shellSampler)

        val transforms = engine.transformManager
        val visible = meshMarksOf(ball)
        for ((mark, entity) in markEntities) {
            val scale = if (mark in visible) mark.lift else 0f
            markTransform.fill(0f)
            markTransform[0] = scale
            markTransform[5] = scale
            markTransform[10] = scale
            markTransform[15] = 1f
            transforms.setTransform(transforms.getInstance(entity), markTransform)
        }

        val (r, g, b) = hexToLinear(ball.fx.aura)
        val lights = engine.lightManager
        lights.setColor(lights.getInstance(rimLight), r, g, b)
        lights.setColor(lights.getInstance(rimBackLight), r, g, b)
    }

    /**
     * Turns the shell for the carousel's position and orbits the camera for the free look.
     * `offset` is in slots and unbounded; only its fraction matters, since a slot is a full turn.
     */
    fun setPose(offset: Float, tilt: Float) {
        val turn = offset.toDouble()
        val angle = -(turn - floor(turn)) * TURN_PER_SLOT
        val c = cos(angle).toFloat()
        val s = sin(angle).toFloat()
        // A rotation about Y, column-major.
        rootTransform.fill(0f)
        rootTransform[0] = c
        rootTransform[2] = -s
        rootTransform[5] = 1f
        rootTransform[8] = s
        rootTransform[10] = c
        rootTransform[15] = 1f
        val transforms = engine.transformManager
        transforms.setTransform(transforms.getInstance(asset.root), rootTransform)

        val (x, y, z) = BallCamera.orbitPosition(tilt)
        camera.lookAt(x, y, z, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0)
    }

    /** A new surface: the previous swap chain, if any, is replaced. */
    fun setSurface(surface: Surface, flags: Long) {
        releaseSurface()
        swapChain = engine.createSwapChain(surface, flags)
    }

    /** The surface is going away. Returns once Filament has stopped using it. */
    fun releaseSurface() {
        val current = swapChain ?: return
        swapChain = null
        engine.destroySwapChain(current)
        engine.flushAndWait()
    }

    fun setViewport(width: Int, height: Int) {
        view.viewport = Viewport(0, 0, width, height)
    }

    /** Draws one frame. False when there is no surface or Filament asks to skip this vsync. */
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
        for (light in intArrayOf(keyLight, fillLight, rimLight, rimBackLight)) {
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
        shellTextures.values.forEach(engine::destroyTexture)
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
            // Loads Filament's native libraries (filament, gltfio and utils) once per process.
            Utils.init()
        }
    }
}
