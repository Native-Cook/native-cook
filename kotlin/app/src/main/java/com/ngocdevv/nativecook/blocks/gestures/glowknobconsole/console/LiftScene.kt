// The block is built for Android 12 (API 31) and newer; the demo checks before showing it.
@file:androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.S)

package com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.console

import android.graphics.Bitmap
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.platform.Perf
import android.graphics.ColorSpace
import android.graphics.PixelFormat
import android.hardware.HardwareBuffer
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.android.filament.Camera
import com.google.android.filament.ColorGrading
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndexBuffer
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SurfaceOrientation
import com.google.android.filament.SwapChain
import com.google.android.filament.SwapChainFlags
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import com.google.android.filament.ToneMapper
import com.google.android.filament.VertexBuffer
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.filamat.MaterialBuilder
import com.google.android.filament.utils.IBLPrefilterContext
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.anim.AnimationEngine
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.anim.FrameCallback
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.gpu.MeshData
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.gpu.SoftBox
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.gpu.StudioEnvironment
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.gpu.StudioOptions
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.gpu.cylinder
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.gpu.extrudeCapsule
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.gpu.faceUV
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.gpu.makeStudio
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.gpu.rotateXTranslate
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.gpu.translate
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

data class Box(val x: Double, val y: Double, val width: Double, val height: Double)

/** The console's light as a scene light: its leading edge (drawing units),
 * linear RGB color and strength (0…1). */
data class Glow(val tip: Double, val color: DoubleArray, val power: Double)

/** Link between the lift choreography and the scene (lift-scene.ts LiftLink). */
class LiftLink {
    /** While false the scene neither reads the pose nor renders. */
    var live = false
        private set
    /** A rendered frame exists (the renderer is up). */
    var ready = false
        private set
    /** A freshly recorded face to paint before the next render. */
    private var face: android.graphics.Picture? = null
    /** The console's own light at the moment it was picked up. */
    var light: Glow? = null
        private set
    private var onFace: () -> Unit = {}
    private var onRender: () -> Unit = {}
    // [lift, yaw, pitch, tiltX, tiltY], pushed every frame it changes.
    private var latest = doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0)
    internal var wake: () -> Unit = {}

    fun setPose(pose: DoubleArray) {
        latest = pose
        wake()
    }

    fun pose() = latest

    /** Shows `picture` on the object's face and starts following the pose. */
    fun start(picture: android.graphics.Picture, light: Glow?) {
        face = picture
        this.light = light
        live = true
        wake()
    }

    fun stop() {
        live = false
    }

    fun takeFace(): android.graphics.Picture? = face.also { face = null }

    fun on(face: () -> Unit, render: () -> Unit) {
        onFace = face
        onRender = render
    }

    /** After a render reached the screen; `revealed` when it is the first with a new face. */
    fun rendered(revealed: Boolean) {
        ready = true
        onRender()
        if (revealed) onFace()
    }
}

// Drawing units, as DeviceArt (housing 1000 × 284).
private const val ART_W = 1000.0
private const val ART_H = 284.0
// Body thickness, edge chamfer and knob height, in drawing units.
private const val DEPTH_U = 62.0
private const val BEVEL_U = 7.0
private const val KNOB_U = 22.0
private const val FOV = 30.0
// How far the console comes toward the viewer, as a fraction of the camera
// distance (about +12% apparent size), and its presenting tilt: the top edge
// turns toward the viewer.
private const val LIFT_Z = 0.11
private const val LIFT_Y = 14.0
private const val REST_PITCH = 0.14
// Peak intensity of the track's light inside the scene (candela, dp²).
private const val LAMP = 3.2e4
// Filament works in world units of 100 dp so that mediump shading math stays
// in range; lengths scale by WORLD and candela by WORLD² (I/d² is invariant).
private const val WORLD = 0.01

// A dark studio over a white table: the floor bounce below gives the lower
// chamfer its rim, a soft box above and two strips draw the moving
// highlights across the glass and the metal.
val STUDIO = StudioOptions(
    floor = doubleArrayOf(0.36, 0.355, 0.35),
    sky = doubleArrayOf(0.03, 0.03, 0.035),
    boxes = listOf(
        SoftBox(doubleArrayOf(16.0, 5.0), doubleArrayOf(0.0, 12.0, 10.0), 5.0),
        SoftBox(doubleArrayOf(2.4, 13.0), doubleArrayOf(-14.0, 3.0, 9.0), 3.2),
        SoftBox(doubleArrayOf(2.4, 15.0), doubleArrayOf(13.0, 2.0, -7.0), 2.4),
    ),
)

/** Runtime-compiled Filament materials (filamat), the MeshPhysicalMaterial values of lift-scene.ts. */
object LiftMaterials {
    @Volatile private var packages: Map<String, ByteBuffer>? = null

    private fun build(name: String, shader: String, configure: MaterialBuilder.() -> Unit): ByteBuffer {
        val b = MaterialBuilder()
            .name(name)
            .shading(MaterialBuilder.Shading.LIT)
            .require(MaterialBuilder.VertexAttribute.UV0)
            .platform(MaterialBuilder.Platform.MOBILE)
            .targetApi(MaterialBuilder.TargetApi.OPENGL)
            .optimization(MaterialBuilder.Optimization.PERFORMANCE)
            .culling(MaterialBuilder.CullingMode.BACK)
            // three.js samples uv as given (v = 0 is the first uploaded row);
            // filamat flips V by default (glTF convention).
            .flipUV(false)
        b.configure()
        val pkg = b.material(shader).build()
        check(pkg.isValid) { "material $name failed to compile" }
        val src = pkg.buffer
        return ByteBuffer.allocateDirect(src.remaining()).order(ByteOrder.nativeOrder()).put(src).also { it.flip() }
    }

    /** CPU-only shader compilation; safe off the main thread. */
    fun compile(): Map<String, ByteBuffer> {
        packages?.let { return it }
        MaterialBuilder.init()
        val sampler: MaterialBuilder.(String) -> Unit = {
            samplerParameter(MaterialBuilder.SamplerType.SAMPLER_2D, MaterialBuilder.SamplerFormat.FLOAT, MaterialBuilder.ParameterPrecision.DEFAULT, it)
        }
        val result = mapOf(
            // Glass-coated face: the artwork is emitted as is (it carries its own
            // painted light), and a clear coat adds the studio's real reflections.
            // gloss = min(1, lift·1.4): resting on the stage the face is the 2D
            // drawing pixel for pixel, so the handoff is invisible.
            // three: specularIntensity = gloss scales F0 = 0.04 → reflectance
            // 0.5·√gloss (F0 = 0.16·r²); its clear coat attenuates the base layer
            // (emissive included) by 1 − cc·Fcc, which Filament does not do for
            // emissive, so it is applied here.
            "face" to build("face", """
                void material(inout MaterialInputs material) {
                    prepareMaterial(material);
                    float g = materialParams.gloss;
                    material.baseColor = vec4(0.0, 0.0, 0.0, 1.0);
                    material.metallic = 0.0;
                    material.roughness = 0.45;
                    material.reflectance = 0.5 * sqrt(g);
                    material.clearCoat = 0.8 * g;
                    material.clearCoatRoughness = 0.07;
                    vec3 e = texture(materialParams_faceTex, getUV0()).rgb;
                    float NoV = clamp(dot(shading_normal, shading_view), 0.0, 1.0);
                    float Fcc = 0.04 + 0.96 * pow(1.0 - NoV, 5.0);
                    material.emissive = vec4(e * (1.0 - material.clearCoat * Fcc), materialParams.emissiveEv);
                }
            """.trimIndent()) {
                sampler("faceTex")
                uniformParameter(MaterialBuilder.UniformType.FLOAT, "gloss")
                uniformParameter(MaterialBuilder.UniformType.FLOAT, "emissiveEv")
            },
            // Anodized aluminium chamfer and walls: #55555A (sRGB → linear).
            "body" to build("body", """
                void material(inout MaterialInputs material) {
                    prepareMaterial(material);
                    material.baseColor = vec4(${srgbToLinear(0x55)}, ${srgbToLinear(0x55)}, ${srgbToLinear(0x5A)}, 1.0);
                    material.metallic = 1.0;
                    material.roughness = 0.3;
                }
            """.trimIndent()) {},
            // The back is seen after a half turn about the vertical axis: mirror u.
            "back" to build("back", """
                void material(inout MaterialInputs material) {
                    prepareMaterial(material);
                    vec2 uv = getUV0();
                    material.baseColor = vec4(texture(materialParams_backTex, vec2(1.0 - uv.x, uv.y)).rgb, 1.0);
                    material.metallic = 0.35;
                    material.roughness = 0.4;
                }
            """.trimIndent()) { sampler("backTex") },
            // Brushed rather than polished: the light's warmth spreads across the
            // flank instead of glinting off a few ridges.
            "knurl" to build("knurl", """
                void material(inout MaterialInputs material) {
                    prepareMaterial(material);
                    material.baseColor = vec4(texture(materialParams_knurlTex, getUV0() * vec2(3.0, 1.0)).rgb, 1.0);
                    material.metallic = 0.55;
                    material.roughness = 0.7;
                }
            """.trimIndent()) { sampler("knurlTex") },
        )
        packages = result
        return result
    }

    private fun srgbToLinear(v: Int): String {
        val c = v / 255.0
        val l = if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        return "%.6f".format(java.util.Locale.US, l)
    }
}

/** Studio environment, built once per process off the main thread. */
object LiftStudio {
    @Volatile private var env: StudioEnvironment? = null
    fun get(): StudioEnvironment = env ?: makeStudio(STUDIO).also { env = it }
}

private fun floats(a: FloatArray): ByteBuffer =
    ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder()).also { it.asFloatBuffer().put(a) }

private fun ints(a: IntArray): ByteBuffer =
    ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder()).also { it.asIntBuffer().put(a) }

/**
 * The 3D object (lift-scene.ts makeLiftScene) rendered by Filament into an
 * ImageReader whose HardwareBuffers are wrapped as hardware Bitmaps (no copy)
 * and composited by Compose three times: mirror, silhouette shadow, object.
 */
class LiftScene(
    private val canvas: Box,
    private val housing: Box,
    private val pixelRatio: Float,
    private val link: LiftLink,
    materials: Map<String, ByteBuffer>,
    studio: StudioEnvironment,
) {
    var image by mutableStateOf<ImageBitmap?>(null)
        private set

    private val widthPx = (canvas.width * pixelRatio).toInt().coerceAtLeast(1)
    private val heightPx = (canvas.height * pixelRatio).toInt().coerceAtLeast(1)
    private val engine: Engine = Engine.create(Engine.Backend.OPENGL)
    private val renderer: Renderer = engine.createRenderer()
    private val scene: Scene = engine.createScene()
    private val view: View = engine.createView()
    private val cameraEntity = EntityManager.get().create()
    private val camera: Camera = engine.createCamera(cameraEntity)
    private val reader = ImageReader.newInstance(
        widthPx, heightPx, PixelFormat.RGBA_8888, 4,
        HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
    )
    private val swapChain: SwapChain = engine.createSwapChain(reader.surface, SwapChainFlags.CONFIG_TRANSPARENT)
    private val handler = Handler(Looper.getMainLooper())

    private val distance = canvas.height / 2 / tan(Math.toRadians(FOV / 2))
    private val s = housing.width / ART_W
    private val w = housing.width
    private val h = ART_H * s
    private val depth = DEPTH_U * s
    private val bevel = BEVEL_U * s
    private val front = depth / 2 + bevel
    private val home = doubleArrayOf(housing.x + w / 2 - canvas.width / 2, canvas.height / 2 - (housing.y + h / 2), -front)

    // Face textures sized to their largest on-screen footprint (lifted) so
    // they are sampled about 1:1 (no mipmaps, as RN's external textures).
    private val faceW = min(2048, (w * pixelRatio * 1.2).roundToInt())
    private val faceH = (faceW * ART_H / ART_W).roundToInt()
    private val faceTex = rgbaTexture(faceW, faceH)
    private val backTex = rgbaTexture(faceW, faceH)
    private val knurlTex = rgbaTexture(256, 64)

    private val faceMat: Material
    private val bodyMat: Material
    private val backMat: Material
    private val knurlMat: Material
    private val faceMI: MaterialInstance
    private val bodyMI: MaterialInstance
    private val backMI: MaterialInstance
    private val knurlMI: MaterialInstance
    private val consoleEntity = EntityManager.get().create()
    private val bodyEntity = EntityManager.get().create()
    private val knobEntity = EntityManager.get().create()
    private val lampEntity = EntityManager.get().create()
    private val buffers = ArrayList<Pair<VertexBuffer, IndexBuffer>>()
    private val ibl: IndirectLight
    private val iblTextures = ArrayList<Texture>()
    private val colorGrading: ColorGrading

    // The prefilter's GPU work is asynchronous: its context lives as long as the scene.
    private var iblKeep: Triple<IBLPrefilterContext.SpecularFilter, IBLPrefilterContext.EquirectangularToCubemap, IBLPrefilterContext>? = null
    private var dirty = true
    private var reveal = false
    private var last = ""
    private var destroyed = false
    private val pending = ArrayDeque<Boolean>() // reveal flag per submitted frame
    private val held = ArrayDeque<Pair<Image, HardwareBuffer>>()
    private val frame = FrameCallback { t -> Perf.section("Lift:update") { update(t) } }
    private val wake: () -> Unit = { requestRender() }
    /** The face rasterized off the UI thread, waiting to be uploaded. */
    private var facePainted: Bitmap? = null

    init {
        view.scene = scene
        view.camera = camera
        view.viewport = Viewport(0, 0, widthPx, heightPx)
        view.blendMode = View.BlendMode.TRANSLUCENT
        view.dithering = View.Dithering.NONE
        view.antiAliasing = View.AntiAliasing.NONE
        view.multiSampleAntiAliasingOptions = View.MultiSampleAntiAliasingOptions().apply { enabled = true; sampleCount = 4 }
        // three: no tone mapping, sRGB output.
        colorGrading = ColorGrading.Builder().toneMapper(ToneMapper.Linear()).build(engine)
        view.colorGrading = colorGrading
        renderer.clearOptions = renderer.clearOptions.apply { clear = true; clearColor = doubleArrayOf(0.0, 0.0, 0.0, 0.0) }
        // World units are 100 dp; the z = 0 plane maps 1:1 to the canvas.
        camera.setProjection(FOV, canvas.width / canvas.height, distance * 0.2 * WORLD, distance * 3 * WORLD, Camera.Fov.VERTICAL)
        camera.lookAt(0.0, 0.0, distance * WORLD, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0)
        // Exposure 1 (aperture 1, shutter 1.2, ISO 100): light units as three's.
        camera.setExposure(1f, 1.2f, 100f)

        fun mat(name: String): Material {
            val buf = materials.getValue(name)
            return Material.Builder().payload(buf, buf.remaining()).build(engine)
        }
        faceMat = mat("face"); bodyMat = mat("body"); backMat = mat("back"); knurlMat = mat("knurl")
        val linear = TextureSampler(TextureSampler.MinFilter.LINEAR, TextureSampler.MagFilter.LINEAR, TextureSampler.WrapMode.CLAMP_TO_EDGE)
        faceMI = faceMat.createInstance().apply {
            setParameter("faceTex", faceTex, linear)
            setParameter("gloss", 0f)
            // Filament adds emissive × 2^(ev100 + w − 3) × exposure (or
            // mix(1, exposure, w) in newer shaders): this w is 1:1 either way.
            val ev100 = log2((1.0 * 1.0 / 1.2) * 100.0 / 100.0)
            setParameter("emissiveEv", (3.0 - ev100).toFloat())
        }
        bodyMI = bodyMat.createInstance()
        backMI = backMat.createInstance().apply { setParameter("backTex", backTex, linear) }
        knurlMI = knurlMat.createInstance().apply {
            setParameter(
                "knurlTex", knurlTex,
                TextureSampler(TextureSampler.MinFilter.LINEAR, TextureSampler.MagFilter.LINEAR, TextureSampler.WrapMode.REPEAT, TextureSampler.WrapMode.CLAMP_TO_EDGE, TextureSampler.WrapMode.CLAMP_TO_EDGE),
            )
        }

        paint(backTex, faceW, faceH) { c ->
            c.scale((faceW / ART_W).toFloat(), (faceH / ART_H).toFloat())
            paintConsoleBack(c)
        }
        paint(knurlTex, 256, 64) { c -> paintKnurl(c, 256, 64) }

        // Body: ExtrudeGeometry(capsule(w − 2·bevel, h − 2·bevel)), depth 62u,
        // bevel 7u (6 segments), 64 curve segments, centered on z.
        val body = extrudeCapsule(w - 2 * bevel, h - 2 * bevel, depth, bevel, bevel, 6, 64)
        body.translate(0.0, 0.0, -depth / 2)
        body.faceUV(w, h)
        // ExtrudeGeometry puts both lids in group 0 (back lid first, then front)
        // and the walls in group 1. Split the lids: 0 face, 1 walls, 2 back.
        val (lidStart, lidCount, _) = body.groups[0]
        val walls = body.groups[1]
        val lid = lidCount / 2
        val bodyGroups = listOf(
            Triple(lidStart, lid, 2),
            Triple(lidStart + lid, lid, 0),
            Triple(walls.first, walls.second, 1),
        )
        // The knob stands proud of the face; its top shows the knob artwork.
        val knobR = 100 * s
        val knobH = KNOB_U * s
        val knob = cylinder(knobR, knobH, 96)
        knob.rotateXTranslate(PI / 2, (G.Dial.x - ART_W / 2) * s, (ART_H / 2 - G.Dial.y) * s, front + knobH / 2)
        knob.faceUV(w, h, onlyGroups = listOf(1, 2))

        val tm = engine.transformManager
        tm.create(consoleEntity)
        val consoleInstance = tm.getInstance(consoleEntity)
        renderable(bodyEntity, body, bodyGroups, mapOf(0 to faceMI, 1 to bodyMI, 2 to backMI))
        renderable(knobEntity, knob, knob.groups, mapOf(0 to knurlMI, 1 to faceMI, 2 to bodyMI))
        tm.setParent(tm.getInstance(bodyEntity), consoleInstance)
        tm.setParent(tm.getInstance(knobEntity), consoleInstance)

        // The light in the track, as a light: it warms the knob's knurled flank
        // and the chamfer beside it. It sits just under the face so the glass
        // itself takes no hot spot (the artwork already paints the light).
        // Short reach: it warms what is beside the light, never the far edges.
        LightManager.Builder(LightManager.Type.POINT)
            .intensityCandela(0f)
            .falloff((w * 0.5 * WORLD).toFloat())
            .castShadows(false)
            .build(engine, lampEntity)
        tm.create(lampEntity)
        tm.setParent(tm.getInstance(lampEntity), consoleInstance)
        scene.addEntity(consoleEntity)
        scene.addEntity(bodyEntity)
        scene.addEntity(knobEntity)
        scene.addEntity(lampEntity)
        setPose(0.0, 0.0, 0.0, 0.0, 0.0)

        // Environment: equirect HDR → cubemap → GGX prefiltered reflections, SH irradiance.
        // Full mip chain, as Filament's HDRLoader allocates it (the prefilter requires it).
        val equirect = Texture.Builder().width(studio.width).height(studio.height).levels(0xff)
            .usage(Texture.Usage.DEFAULT or Texture.Usage.GEN_MIPMAPPABLE)
            .format(Texture.InternalFormat.R11F_G11F_B10F).sampler(Texture.Sampler.SAMPLER_2D).build(engine)
        equirect.setImage(engine, 0, Texture.PixelBufferDescriptor(floats(studio.rgb), Texture.Format.RGB, Texture.Type.FLOAT))
        equirect.generateMipmaps(engine)
        val ctx = IBLPrefilterContext(engine)
        val toCube = IBLPrefilterContext.EquirectangularToCubemap(ctx)
        val cube = toCube.run(equirect)
        val specular = IBLPrefilterContext.SpecularFilter(ctx)
        val reflections = specular.run(cube)
        iblKeep = Triple(specular, toCube, ctx)
        iblTextures.add(equirect)
        iblTextures.add(cube)
        iblTextures.add(reflections)
        ibl = IndirectLight.Builder().reflections(reflections).irradiance(3, studio.sh).intensity(1f).build(engine)
        scene.indirectLight = ibl

        reader.setOnImageAvailableListener({ onImages() }, handler)
        link.wake = wake
        requestRender()
    }

    private fun rgbaTexture(width: Int, height: Int): Texture =
        Texture.Builder().width(width).height(height).levels(1)
            .format(Texture.InternalFormat.SRGB8_A8).sampler(Texture.Sampler.SAMPLER_2D).build(engine)

    private fun paint(texture: Texture, width: Int, height: Int, draw: (android.graphics.Canvas) -> Unit) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bitmap)
        draw(c)
        upload(texture, bitmap)
        bitmap.recycle()
    }

    private fun upload(texture: Texture, bitmap: Bitmap) {
        val buffer = ByteBuffer.allocateDirect(bitmap.byteCount).order(ByteOrder.nativeOrder())
        bitmap.copyPixelsToBuffer(buffer)
        buffer.flip()
        texture.setImage(engine, 0, Texture.PixelBufferDescriptor(buffer, Texture.Format.RGBA, Texture.Type.UBYTE))
    }

    private fun renderable(entity: Int, mesh: MeshData, groups: List<Triple<Int, Int, Int>>, materials: Map<Int, MaterialInstance>) {
        val n = mesh.vertexCount
        val positions = FloatArray(mesh.positions.size) { (mesh.positions[it] * WORLD).toFloat() }
        val normals = floats(mesh.normals)
        val quats = ByteBuffer.allocateDirect(n * 16).order(ByteOrder.nativeOrder())
        val orientation = SurfaceOrientation.Builder().vertexCount(n).normals(normals.asFloatBuffer()).build()
        orientation.getQuatsAsFloat(quats.asFloatBuffer())
        orientation.destroy()
        val vb = VertexBuffer.Builder().bufferCount(3).vertexCount(n)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 12)
            .attribute(VertexBuffer.VertexAttribute.TANGENTS, 1, VertexBuffer.AttributeType.FLOAT4, 0, 16)
            .attribute(VertexBuffer.VertexAttribute.UV0, 2, VertexBuffer.AttributeType.FLOAT2, 0, 8)
            .build(engine)
        vb.setBufferAt(engine, 0, floats(positions))
        vb.setBufferAt(engine, 1, quats)
        vb.setBufferAt(engine, 2, floats(mesh.uvs))
        val ib = IndexBuffer.Builder().indexCount(mesh.indices.size).bufferType(IndexBuffer.Builder.IndexType.UINT).build(engine)
        ib.setBuffer(engine, ints(mesh.indices))
        buffers.add(vb to ib)
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        for (i in 0 until n) {
            minX = min(minX, positions[i * 3]); maxX = maxOf(maxX, positions[i * 3])
            minY = min(minY, positions[i * 3 + 1]); maxY = maxOf(maxY, positions[i * 3 + 1])
            minZ = min(minZ, positions[i * 3 + 2]); maxZ = maxOf(maxZ, positions[i * 3 + 2])
        }
        val builder = RenderableManager.Builder(groups.size)
            .boundingBox(com.google.android.filament.Box((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2, (maxX - minX) / 2, (maxY - minY) / 2, (maxZ - minZ) / 2))
            .castShadows(false)
            .receiveShadows(false)
            .culling(false)
        groups.forEachIndexed { i, (start, count, material) ->
            builder.geometry(i, RenderableManager.PrimitiveType.TRIANGLES, vb, ib, start, count)
            builder.material(i, materials.getValue(material))
        }
        builder.build(engine, entity)
        engine.transformManager.create(entity)
    }

    private fun setPose(lift: Double, yaw: Double, pitch: Double, tx: Double, ty: Double) {
        faceMI.setParameter("gloss", min(1.0, lift * 1.4).toFloat())
        val px = home[0]
        val py = home[1] + LIFT_Y * lift
        val pz = home[2] + distance * LIFT_Z * lift
        val ax = pitch + (REST_PITCH + ty * 0.12) * lift
        val ay = yaw + tx * 0.16 * lift
        // three Euler 'XYZ': R = Rx · Ry. Column-major T · R.
        val cx = cos(ax); val sx = sin(ax); val cy = cos(ay); val sy = sin(ay)
        val m = doubleArrayOf(
            cy, sx * sy, -cx * sy, 0.0,
            0.0, cx, sx, 0.0,
            sy, -sx * cy, cx * cy, 0.0,
            px * WORLD, py * WORLD, pz * WORLD, 1.0,
        )
        engine.transformManager.setTransform(engine.transformManager.getInstance(consoleEntity), m)
    }

    private fun requestRender() {
        if (destroyed) return
        AnimationEngine.addFrameCallback(frame)
    }

    private fun update(frameTimeMs: Double) {
        if (destroyed) return
        val picture = link.takeFace()
        if (picture != null) {
            // Rasterizing the recorded face (blurred shadows at texture size)
            // takes tens of ms on the CPU: off the UI thread, uploaded when done.
            Thread {
                val bitmap = Perf.section("Lift:paintFace") {
                    Bitmap.createBitmap(faceW, faceH, Bitmap.Config.ARGB_8888).also { b ->
                        val c = android.graphics.Canvas(b)
                        c.scale((faceW / ART_W).toFloat(), (faceH / ART_H).toFloat())
                        c.drawPicture(picture)
                    }
                }
                handler.post {
                    if (destroyed) bitmap.recycle() else {
                        facePainted = bitmap
                        requestRender()
                    }
                }
            }.start()
        }
        val painted = facePainted
        if (painted != null) {
            facePainted = null
            Perf.section("Lift:uploadFace") { upload(faceTex, painted) }
            painted.recycle()
            val glow = link.light
            val lm = engine.lightManager
            val li = lm.getInstance(lampEntity)
            lm.setIntensityCandela(li, if (glow != null) (glow.power * LAMP * WORLD * WORLD).toFloat() else 0f)
            if (glow != null) {
                lm.setColor(li, glow.color[0].toFloat(), glow.color[1].toFloat(), glow.color[2].toFloat())
                lm.setPosition(
                    li,
                    ((glow.tip - 42 - ART_W / 2) * s * WORLD).toFloat(),
                    ((ART_H / 2 - (G.Track.y + G.Track.height / 2)) * s * WORLD).toFloat(),
                    ((front - bevel * 0.5) * WORLD).toFloat(),
                )
            }
            reveal = true
            dirty = true
        }
        if (link.live) {
            val (lift, yaw, pitch, tx, ty) = link.pose().let { listOf(it[0], it[1], it[2], it[3], it[4]) }
            val key = "%.4f %.4f %.4f %.3f %.3f".format(java.util.Locale.US, lift, yaw, pitch, tx, ty)
            if (key != last) {
                last = key
                setPose(lift, yaw, pitch, tx, ty)
                dirty = true
            }
        }
        if (!dirty) {
            AnimationEngine.removeFrameCallback(frame)
            return
        }
        val began = Perf.section("Lift:beginFrame") { renderer.beginFrame(swapChain, (frameTimeMs * 1e6).toLong()) }
        if (!began) Perf.counter("Lift:skippedFrames", ++skipped)
        if (began) {
            Perf.section("Lift:render") {
                renderer.render(view)
                renderer.endFrame()
            }
            pending.addLast(reveal)
            reveal = false
            dirty = false
        }
    }

    private var skipped = 0L

    private fun onImages() = Perf.section("Lift:onImages") { onImagesTraced() }

    private fun onImagesTraced() {
        if (destroyed) return
        while (true) {
            val img = runCatching { reader.acquireNextImage() }.getOrNull() ?: break
            val revealed = pending.removeFirstOrNull() ?: false
            val buffer = img.hardwareBuffer
            if (buffer == null) {
                img.close()
                continue
            }
            val bitmap = Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB))
            held.addLast(img to buffer)
            // Keep the two most recent frames alive: the render thread may still
            // be sampling the previous one.
            while (held.size > 2) {
                val (old, oldBuffer) = held.removeFirst()
                oldBuffer.close(); old.close()
            }
            if (bitmap != null) image = bitmap.asImageBitmap()
            link.rendered(revealed)
        }
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        AnimationEngine.removeFrameCallback(frame)
        // A scene mounted after this one (back from the background) owns the link now.
        if (link.wake === wake) link.wake = {}
        reader.setOnImageAvailableListener(null, null)
        image = null
        engine.flushAndWait()
        for (e in listOf(bodyEntity, knobEntity, lampEntity)) {
            engine.renderableManager.destroy(e)
            engine.lightManager.destroy(e)
            engine.transformManager.destroy(e)
            engine.destroyEntity(e)
        }
        engine.transformManager.destroy(consoleEntity)
        for ((vb, ib) in buffers) { engine.destroyVertexBuffer(vb); engine.destroyIndexBuffer(ib) }
        for (mi in listOf(faceMI, bodyMI, backMI, knurlMI)) engine.destroyMaterialInstance(mi)
        for (m in listOf(faceMat, bodyMat, backMat, knurlMat)) engine.destroyMaterial(m)
        for (t in listOf(faceTex, backTex, knurlTex) + iblTextures) engine.destroyTexture(t)
        iblKeep?.let { (specular, toCube, ctx) -> specular.destroy(); toCube.destroy(); ctx.destroy() }
        engine.destroyIndirectLight(ibl)
        engine.destroyColorGrading(colorGrading)
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(cameraEntity)
        engine.destroySwapChain(swapChain)
        engine.flushAndWait()
        engine.destroy()
        // Let the UI release the last frame before its buffer goes.
        handler.postDelayed({
            for ((img, buf) in held) { buf.close(); img.close() }
            held.clear()
            reader.close()
        }, 250)
    }
}
