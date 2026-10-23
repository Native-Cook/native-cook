package com.ngocdevv.nativecook.blocks.gestures.boosterpackopening

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Xfermode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private val BODY_TIMES = DoubleArray(BODY_KEYFRAMES.size) { BODY_KEYFRAMES[it].ms }
private val BODY_SEAM_Y = DoubleArray(BODY_KEYFRAMES.size) { BODY_KEYFRAMES[it].seamY }
private val BODY_SCALE = DoubleArray(BODY_KEYFRAMES.size) { BODY_KEYFRAMES[it].scale }

private val CARD_TIMES = DoubleArray(CARD_KEYFRAMES.size) { CARD_KEYFRAMES[it].ms }
private val CARD_OPACITY = DoubleArray(CARD_KEYFRAMES.size) { CARD_KEYFRAMES[it].opacity }

private const val TRACE_THICKNESS_SCALE = 0.85
private val DIM_PROGRESS = doubleArrayOf(0.0, 0.1175, 0.44, 0.52, 0.72, 0.915, 1.0)
private val DIM_OPACITY = doubleArrayOf(0.0, 0.21, 0.374, 0.456, 0.62, 0.7, 0.7)
private const val BACKDROP_REVEAL_LEAD_MS = 160.0
private const val BACKDROP_FADE_DURATION_MS = 320.0
private const val BACKDROP_ZOOM_DURATION_MS = 2_600.0

private val MOUTH_DEPTH_TIMES =
    doubleArrayOf(0.0, 66.667, 100.0, 150.0, 300.0, 433.333, 633.333, 766.667, 833.333, 900.0, 1000.0)
private val MOUTH_DEPTH = doubleArrayOf(0.0065, 0.009, 0.015, 0.025, 0.042, 0.049, 0.052, 0.059, 0.061, 0.063, 0.067)
private val MOUTH_EXPONENT_TIMES = doubleArrayOf(0.0, 150.0, 300.0, 433.333, 550.0, 633.333, 766.667, 1000.0)
private val MOUTH_EXPONENT = doubleArrayOf(1.3, 1.3, 1.7, 2.0, 1.55, 1.3, 1.3, 1.3)
private val MOUTH_OFFSET_TIMES = doubleArrayOf(0.0, 150.0, 300.0, 433.333, 633.333, 766.667, 833.333, 900.0, 1000.0)
private val MOUTH_ENTRY_OFFSET = doubleArrayOf(0.0, 0.0, 0.002, 0.01, 0.0073, -0.006, 0.002, 0.016, 0.020)
private val MOUTH_EXIT_OFFSET = doubleArrayOf(0.0, 0.035, 0.037, 0.061, 0.061, 0.051, 0.061, 0.084, 0.090)
private val MOUTH_RELAXATION_TIMES = doubleArrayOf(0.0, 300.0, 433.333, 633.333, 766.667, 833.333, 1000.0)
private val MOUTH_RELAXATION = doubleArrayOf(1.4, 1.65, 1.7, 1.5, 1.3, 1.2, 1.2)
private val MOUTH_LIFT_TIMES = doubleArrayOf(0.0, 300.0, 433.333, 633.333, 766.667, 833.333, 900.0, 1000.0)
private val MOUTH_LIFT = doubleArrayOf(0.0, 0.0, -0.008, -0.008, -0.014, -0.010, -0.016, -0.012)
private val CAVITY_TIMES = doubleArrayOf(0.0, 66.667, 100.0, 150.0)
private val CAVITY_STRENGTH = doubleArrayOf(0.12, 0.38, 0.82, 1.0)
private val CURL_TIMES = doubleArrayOf(0.0, 433.0, 633.0, 767.0, 833.0, 900.0, 1000.0)
private val CURL = doubleArrayOf(0.0, 0.0, 0.005, 0.015, 0.02, 0.032, 0.035)
private val BODY_OFFSET_TIMES = doubleArrayOf(0.0, 150.0, 433.333, 633.333, 766.667, 850.0, 900.0, 1000.0)
private val BODY_OFFSET = doubleArrayOf(0.0, 0.0041, 0.005, 0.0049, 0.0113, 0.0113, 0.003, 0.0)
private val LIP_TIMES = doubleArrayOf(0.0, 150.0, 433.0, 767.0, 900.0, 1000.0)
private val LIP_THICKNESS = doubleArrayOf(0.011, 0.014, 0.019, 0.021, 0.021, 0.020)
private val CORE_EXTENSION_PROGRESS = doubleArrayOf(0.0, 0.72, 1.0)
private val CORE_EXTENSION = doubleArrayOf(0.0, 0.014, 0.06)

private val SKY_COLORS = intArrayOf(0xFFAAC2E5.toInt(), 0xFFCADBF8.toInt(), 0xFFE7FFFF.toInt(), 0xFFE3F9FD.toInt())
private val OPEN_SKY_COLORS = intArrayOf(0xFFC9D7F9.toInt(), 0xFFC5D6F7.toInt(), 0xFF9DB6DD.toInt(), 0xFF7EA1CE.toInt())
private val SKY_POSITIONS = floatArrayOf(0.08f, 0.33f, 0.69f, 0.93f)
private val DIM_COLORS = intArrayOf(0xFF050607.toInt(), 0xFF07090D.toInt(), 0xFF181827.toInt())
private val DIM_POSITIONS = floatArrayOf(0f, 0.46f, 0.78f)
private val SHEEN_COLORS =
    intArrayOf(
        rgba(255, 255, 255, 0.0),
        rgba(255, 255, 255, 0.1),
        rgba(255, 255, 255, 0.3),
        rgba(255, 255, 255, 0.62),
        rgba(214, 250, 255, 0.28),
        rgba(190, 246, 255, 0.1),
        rgba(255, 255, 255, 0.0),
    )
private val SHEEN_POSITIONS = floatArrayOf(0f, 0.3f, 0.45f, 0.5f, 0.55f, 0.7f, 1f)
private val WEDGE_COLORS = intArrayOf(0xFFB96FE7.toInt(), 0xFFB52CE8.toInt(), 0xFFD052EE.toInt())
private val WEDGE_POSITIONS = floatArrayOf(0f, 0.42f, 1f)
private val MOUTH_GLOW_COLORS = intArrayOf(rgba(150, 255, 255, 0.8), rgba(20, 242, 255, 0.48), rgba(0, 190, 255, 0.0))
private val MOUTH_GLOW_POSITIONS = floatArrayOf(0f, 0.25f, 1f)
private val SEAM_HINT_COLORS = intArrayOf(rgba(120, 240, 255, 0.0), rgba(150, 246, 255, 0.5), rgba(255, 255, 255, 0.95))
private val SEAM_HINT_POSITIONS = floatArrayOf(0f, 0.65f, 1f)
private val TRACE_BODY_COLORS =
    intArrayOf(
        0xFFCF49F1.toInt(),
        0xFFCF49F1.toInt(),
        0xFFD34AF2.toInt(),
        0xFFD94EF2.toInt(),
        0xFFDB4EF2.toInt(),
        0xFFD54AF1.toInt(),
    )
private val TRACE_FILL_COLORS =
    intArrayOf(
        0xFFD04AF1.toInt(),
        0xFFD04AF1.toInt(),
        0xFFE66EF6.toInt(),
        0xFFFBC5FD.toInt(),
        0xFFFAC1FD.toInt(),
        0xFFEE7AF6.toInt(),
    )
private val TRACE_POSITIONS = floatArrayOf(0f, 0.62f, 0.7f, 0.82f, 0.96f, 1f)
private val TRACE_CORE_COLORS =
    intArrayOf(
        rgba(255, 255, 255, 1.0),
        rgba(255, 255, 255, 1.0),
        rgba(255, 252, 255, 0.98),
        rgba(255, 248, 255, 0.82),
        rgba(255, 245, 255, 0.58),
        rgba(255, 240, 255, 0.32),
        rgba(255, 238, 255, 0.05),
        rgba(255, 232, 255, 0.0),
    )
private val TRACE_CORE_POSITIONS = floatArrayOf(0f, 0.52f, 0.58f, 0.64f, 0.69f, 0.735f, 0.82f, 1f)

/** Moments of the opening that between them use every layer: the flash, the flight, the mouth, the card. */
private val PREWARM_TIMES = doubleArrayOf(0.0, 20.0, 120.0, 700.0, 950.0)

/** The artwork the scene draws: the sealed pack, the five card fronts in pack order, and the back. */
internal class PackImages(
    @JvmField val pack: Bitmap,
    @JvmField val cards: List<Bitmap>,
    @JvmField val back: Bitmap,
)

/** What the scene asks of the device at its discrete moments. */
internal interface PackFeedback {
    fun cutHaptic()

    fun cardHaptic()

    fun rareFaceHaptic()
}

/**
 * The whole effect for one viewport size: the tear the finger draws, the opening clock that takes
 * over when the cut reaches the far edge, and the five-card reveal. Everything is computed in dp
 * from one clock and one cut, and drawn in one pass; the gesture entry points are the same for a
 * finger and for the scripted loop.
 */
internal class PackScene(
    val width: Double,
    val height: Double,
    private val images: PackImages,
    private val audio: PackAudio?,
    private val feedback: PackFeedback,
) : CardRevealEvents {
    val clock = AnimationClock()

    /** Reduce Motion: fades instead of travel, and none of the added sparkle. */
    var reducedMotion = false
        set(value) {
            field = value
            clock.reducedMotion = value
            cards.reducedMotion = value
        }

    /** False while the app is in the background: the idle and backdrop loops rest. */
    var focused = true
        set(value) {
            field = value
            cards.enabled = value
        }

    /** True while the scripted loop drives the scene: it plays without haptics. */
    var scripted = false

    val pack: Rect
    private val settledBodyTexture: Rect
    val seamY = height * ReferenceLayout.SEAM_Y
    private val traceSize = width * TRACE_THICKNESS_SCALE
    private val glowWidth = traceSize * 0.055
    private val traceBodyWidth = traceSize * 0.072
    private val traceFillWidth = traceSize * 0.056
    private val traceCoreWidth = traceSize * 0.046
    private val cardWidth = width * ReferenceLayout.Card.WIDTH
    private val cardHeight = height * ReferenceLayout.Card.HEIGHT
    private val cardX = width * ReferenceLayout.Card.CENTER_X - cardWidth / 2

    private var points: List<Point> = emptyList()
    private var previewBottomMask = makeEmptyPath()
    private val collapsedMesh: Mesh
    private val collapsedPreviewMesh: Mesh
    private var previewMeshBase: List<Point>
    private var previewMeshTextures: List<Point>
    private var peelVisible = 0.0
    private var meshBase: List<Point>
    private var meshTextures: List<Point>
    var split = 0.0
        private set
    var committed = false
        private set
    var active = false
        private set
    private var direction = 1
    private val cutProgress = Animated(clock, 0.0)
    private val traceOpacity = Animated(clock, 0.0)
    private val dimOpacity = Animated(clock, 0.0)
    val postCutMs = Animated(clock, 0.0)
    private val audioMovement = Animated(clock, 0.0)
    private val backdropOpacity = Animated(clock, 0.0)
    private val backdropZoom = Animated(clock, 0.0)
    private var idlePhase = 0.0
    private val idleMix = Animated(clock, 0.0)
    private var idleRunning = false
    private var idleLeadMs = 0.0
    private var backdropVisible = false
    private var tearMoving = false
    private var rareFaceLanded: Boolean? = null
    private var lastFrameMs = Double.NaN
    private var prewarmed = false

    val cards = CardReveal(clock, width, height, postCutMs, this)

    private val brush = Brush()
    private val meshes = MeshRenderer()
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private val foldMaterial: FoldMaterial
    private val foilMaterial: FlyingFoilMaterial
    private val bodyMaterial = BodyMaterial(images.pack)
    private val wallMaterial = MouthMaterial(rim = false)
    private val rimMaterial = MouthMaterial(rim = true)
    private val cutFlash = CutFlash(width, height, seamY)
    private val cutBurst: CutBurst
    private val riseLight = CardRiseLight(width, height, cardHeight)
    private val sparks = TearSparks(width)
    private val backdrop = CollectionCardBackdrop(width, height)
    private val cardScene = CardRevealScene(cards, width, height, images.cards, images.back)
    private val skyGradient = linearGradient(0.0, 0.0, 0.0, height, SKY_COLORS, SKY_POSITIONS)
    private val openSkyGradient = linearGradient(0.0, 0.0, 0.0, height, OPEN_SKY_COLORS, SKY_POSITIONS)
    private val dimGradient = linearGradient(0.0, 0.0, 0.0, height, DIM_COLORS, DIM_POSITIONS)
    private val wedgeGradient =
        linearGradient(0.0, seamY + width * 0.004, 0.0, seamY + width * 0.1, WEDGE_COLORS, WEDGE_POSITIONS)
    private val emissiveSeamPath = Path()
    private val seamHintPath = Path()
    private val foilEdgePath = Path()

    // The folded preview and the open mouth are rebuilt only when what they depend on changes.
    private var previewFoilVertices: List<Point> = emptyList()
    private var previewKeyBase: List<Point>? = null
    private var previewKeyProgress = Double.NaN
    private var previewKeyDirection = 0
    private var mouth: MouthGeometry? = null
    private var mouthKeyMs = Double.NaN
    private var mouthKeyPoints: List<Point>? = null

    init {
        val packWidth = width * ReferenceLayout.Pack.WIDTH
        // The reference video applies a small non-uniform viewport scale to the pack.
        // Preserve that measured silhouette instead of stretching the calibrated crop.
        val packHeight = packWidth * ReferenceLayout.Pack.SOURCE_ASPECT * ReferenceLayout.Pack.VIEWPORT_HEIGHT_SCALE
        pack =
            Rect(
                width * ReferenceLayout.Pack.CENTER_X - packWidth / 2,
                height * ReferenceLayout.Pack.TOP,
                packWidth,
                packHeight,
            )
        // The moving body in the reference settles back toward the package's native
        // aspect ratio after the first separation beat.
        val settledWidth = width * ReferenceLayout.Pack.SETTLED_BODY_WIDTH
        settledBodyTexture =
            Rect(
                width * ReferenceLayout.Pack.CENTER_X - settledWidth / 2,
                height * ReferenceLayout.Pack.TOP,
                settledWidth,
                settledWidth * ReferenceLayout.Pack.SOURCE_ASPECT,
            )
        collapsedMesh = makeCollapsedFoilMesh(pack)
        collapsedPreviewMesh = makeCollapsedPreviewFoilMesh(pack)
        previewMeshBase = collapsedPreviewMesh.vertices
        previewMeshTextures = collapsedPreviewMesh.textures
        meshBase = collapsedMesh.vertices
        meshTextures = collapsedMesh.textures
        foldMaterial = FoldMaterial(images.pack, pack)
        foilMaterial = FlyingFoilMaterial(images.pack, pack, width, height)
        cutBurst = CutBurst(pack, seamY - height * 0.003, width, height)
        val seamLightY = (seamY - height * 0.0045).toFloat()
        emissiveSeamPath.moveTo(pack.x.toFloat(), seamLightY)
        emissiveSeamPath.lineTo((pack.x + pack.width).toFloat(), seamLightY)
    }

    // Added sparkle is live-only and is left out under Reduce Motion.
    private val liveEffects: Boolean get() = !reducedMotion

    private val backdropRevealTime: Double
        get() =
            if (reducedMotion) {
                POST_CUT_DURATION_MS
            } else {
                POST_CUT_DURATION_MS + CARD_CENTERING_DURATION_MS - BACKDROP_REVEAL_LEAD_MS
            }

    /** True while the card stack takes input: its buttons are enabled with it. */
    val interactive: Boolean get() = cards.ready && !cards.busy

    val isIdle: Boolean get() = !active && !committed && peelVisible == 0.0

    /** Starts the idle loop as if the pack had been sealed for [elapsedMs] already. */
    fun primeIdle(elapsedMs: Double) {
        idleRunning = true
        idlePhase = (elapsedMs / IDLE_CYCLE_MS) % 1
        idleMix.set(1.0)
    }

    /**
     * Seals the pack as [reset] does, [lateMs] after the moment it was due: the idle loop starts
     * that far in, so a scripted loop keeps its period whatever the frame times are.
     */
    fun reset(lateMs: Double) {
        reset()
        idleLeadMs = lateMs
    }

    // region Frame

    /** Advances every clock to [nowMs] and runs what reacts to them. [script] runs in between. */
    fun frame(nowMs: Double, script: (() -> Unit)? = null) {
        val elapsed = if (lastFrameMs.isNaN()) 0.0 else max(nowMs - lastFrameMs, 0.0)
        lastFrameMs = nowMs
        clock.inFrame = true
        clock.advance(nowMs)
        if (idleRunning) idlePhase = (idlePhase + elapsed / IDLE_CYCLE_MS) % 1
        script?.invoke()
        react()
        cards.react()
        reactToRareFace()
        if (liveEffects && focused && (active || !sparks.isEmpty)) {
            val head = if (active && points.size > 1) points[points.size - 1] else null
            sparks.step(if (elapsed > 0) elapsed else 16.667, head, direction)
        } else if (!sparks.isEmpty) {
            sparks.clear()
        }
        backdrop.step(elapsed, focused && !reducedMotion && backdropOpacity.value > 0)
        clock.inFrame = false
    }

    private fun react() {
        val visible = committed && postCutMs.value >= backdropRevealTime
        if (visible != backdropVisible) {
            backdropVisible = visible
            if (!visible) {
                backdropOpacity.set(0.0)
                backdropZoom.set(0.0)
            } else {
                backdropOpacity.timing(1.0, if (reducedMotion) 180.0 else BACKDROP_FADE_DURATION_MS, Easing.EaseOut)
                if (reducedMotion) {
                    backdropZoom.set(1.0)
                } else {
                    backdropZoom.timing(1.0, BACKDROP_ZOOM_DURATION_MS, Easing.EaseOut)
                }
            }
        }

        // Only movement START/STOP reaches the player. The 100 ms inactivity
        // detector is the audioMovement value itself, including during a long hold.
        val moving = focused && active && audioMovement.value > 0
        if (moving != tearMoving) {
            tearMoving = moving
            audio?.setTearing(moving)
        }

        // The idle loop runs only while an untouched pack is on screen.
        val running = liveEffects && focused && isIdle
        if (running != idleRunning) {
            idleRunning = running
            if (!running) {
                // Hold the phase while fading, so a sheen never jumps as a tear begins.
                idleMix.timing(0.0, 160.0, Easing.OutQuad)
            } else {
                idlePhase = (idleLeadMs / IDLE_CYCLE_MS) % 1
                idleLeadMs = 0.0
                idleMix.timing(1.0, 420.0, Easing.OutQuad)
            }
        }
    }

    // The rare face lands with its foil flash.
    private fun reactToRareFace() {
        val revealMs = cards.revealMs.value
        val landed = cards.ready && isRareCard(cards.index) && revealMs >= 1250 && revealMs < RARE_REVEAL_MS
        if (landed && rareFaceLanded == false && !scripted) feedback.rareFaceHaptic()
        rareFaceLanded = landed
    }

    // endregion

    // region Gesture

    private fun updatePreviewGeometry(sourcePoints: List<Point>, swipeDirection: Int) {
        val completed = completePreviewCutPoints(sourcePoints, pack, swipeDirection, seamY)
        val mesh = makePreviewFoilMesh(completed, pack)
        previewBottomMask = makeBottomMask(completed, pack)
        previewMeshBase = mesh.vertices
        previewMeshTextures = mesh.textures
    }

    /** "Open another pack": back to the sealed pack. The ambience keeps playing. */
    fun reset() {
        cards.reset()
        backdropVisible = false
        backdropOpacity.set(0.0)
        backdropZoom.set(0.0)
        audioMovement.set(0.0)
        audio?.reset()
        seal()
    }

    private fun seal() {
        points = emptyList()
        previewBottomMask = makeEmptyPath()
        previewMeshBase = collapsedPreviewMesh.vertices
        previewMeshTextures = collapsedPreviewMesh.textures
        peelVisible = 0.0
        meshBase = collapsedMesh.vertices
        meshTextures = collapsedMesh.textures
        split = 0.0
        committed = false
        active = false
        direction = 1
        cutProgress.set(0.0)
        traceOpacity.set(0.0)
        dimOpacity.set(0.0)
        postCutMs.set(0.0)
    }

    private fun commitCut(sourcePoints: List<Point>, swipeDirection: Int) {
        audioMovement.set(0.0)
        val completed = completeCutPoints(sourcePoints, pack, swipeDirection)
        val mesh = makeFoilMesh(completed, pack)
        val previewMesh = makePreviewFoilMesh(completed, pack)

        points = completed
        previewBottomMask = makeBottomMask(completed, pack)
        previewMeshBase = previewMesh.vertices
        previewMeshTextures = previewMesh.textures
        meshBase = mesh.vertices
        meshTextures = mesh.textures
        active = false
        cutProgress.set(1.0)
        committed = true
        // The release is a single pose boundary hidden by the optical burst.
        split = 1.0
        peelVisible = 0.0
        // Keep the last folded pose under the first flash frame. Flattening the
        // cap at this boundary exposes a rectangular corner absent in the video.
        dimOpacity.set(0.0)
        postCutMs.set(0.0)
        // This clock also gates replay. Motion is reduced by each visual
        // layer, without skipping the session's elapsed time.
        postCutMs.timing(OPENING_DURATION_MS, OPENING_DURATION_MS, always = true)
        traceOpacity.timing(0.0, 180.0, Easing.EaseOut, delay = 100.0)
        if (!scripted) feedback.cutHaptic()
        audio?.commitCut()
    }

    /** The finger moved to [position] while tearing. */
    fun updateCut(position: Point) {
        if (!active) return

        val next = advanceTearPoints(points, position, pack, direction) ?: return

        val head = next[next.size - 1]
        val progress =
            clamp(
                if (direction == 1) (head.x - pack.x) / pack.width else (pack.x + pack.width - head.x) / pack.width,
                0.0,
                1.0,
            )

        // Finger position drives the whole cut, including the last few millimetres.
        // No timing animation or velocity prediction may advance an unfinished tear.
        val advanced = progress > cutProgress.value
        points = next
        cutProgress.set(progress)
        val reachedEdge = if (direction == 1) head.x >= pack.x + pack.width else head.x <= pack.x
        if (reachedEdge) {
            commitCut(next, direction)
            return
        }

        if (advanced) {
            audioMovement.set(1.0)
            audioMovement.timing(0.0, 0.0, delay = 100.0, always = true)
        }

        updatePreviewGeometry(next, direction)
        dimOpacity.set(interpolateKeyframes(progress, DIM_PROGRESS, DIM_OPACITY))
    }

    /** Shared entry point for a real finger and the scripted loop: a touch came down at [event]. */
    fun beginCut(event: Point) {
        val corridorTop = pack.y + pack.height * 0.08
        val corridorBottom = pack.y + pack.height * 0.27
        val edgeSlop = pack.width * 0.13
        val nearLeft = event.x >= pack.x - edgeSlop && event.x <= pack.x + edgeSlop
        val nearRight = event.x >= pack.x + pack.width - edgeSlop && event.x <= pack.x + pack.width + edgeSlop
        val insideCorridor = event.y >= corridorTop && event.y <= corridorBottom

        if ((!nearLeft && !nearRight) || !insideCorridor) {
            active = false
            return
        }

        val nextDirection = if (nearLeft) 1 else -1
        val y = clamp(event.y, corridorTop, corridorBottom)
        val boundaryX = if (nextDirection == 1) pack.x else pack.x + pack.width
        val currentX = clamp(event.x, pack.x, pack.x + pack.width)
        val initialPoints = listOf(Point(boundaryX, y), Point(currentX, y))

        audioMovement.set(0.0)
        audio?.beginTear()
        // A second stroke may begin before the cancelled stroke has faded.
        // Its cleanup callbacks are dropped before they can erase the new cut path.
        direction = nextDirection
        active = true
        peelVisible = 1.0
        points = initialPoints
        updatePreviewGeometry(initialPoints, nextDirection)
        val initialProgress = abs(currentX - boundaryX) / pack.width
        cutProgress.set(initialProgress)
        traceOpacity.set(1.0)
        dimOpacity.set(interpolateKeyframes(initialProgress, DIM_PROGRESS, DIM_OPACITY))
    }

    /** The touch ended or was taken away before the cut reached the far edge: the foil springs back. */
    private fun finalizeCut(velocityX: Double) {
        cards.cancel()
        if (!active) return
        active = false
        audioMovement.set(0.0)
        audio?.reset()
        val returnVelocity = -abs(velocityX / pack.width)
        cutProgress.spring(0.0, duration = 400.0, dampingRatio = 0.8, velocity = returnVelocity) {
            peelVisible = 0.0
            previewBottomMask = makeEmptyPath()
            previewMeshBase = collapsedPreviewMesh.vertices
            previewMeshTextures = collapsedPreviewMesh.textures
        }
        dimOpacity.timing(0.0, 180.0, Easing.EaseOut)
        traceOpacity.timing(0.0, 150.0) { points = emptyList() }
    }

    /** A touch came down at ([x], [y]). After the cut it belongs to the card stack. */
    fun panBegin(x: Double, y: Double) {
        if (split > 0.5) {
            cards.begin(x, y)
            return
        }
        beginCut(Point(x, y))
    }

    fun panUpdate(x: Double, y: Double, translationX: Double, translationY: Double) {
        if (split > 0.5) {
            cards.update(translationX, translationY)
            return
        }
        updateCut(Point(x, y))
    }

    /** The touch lifted after moving. [success] is false when the gesture was taken away. */
    fun panEnd(x: Double, y: Double, translationX: Double, translationY: Double, velocityX: Double, success: Boolean) {
        if (cards.dragging) {
            cards.end(translationX, translationY, velocityX, success)
        } else if (success) {
            // A fast swipe can deliver the edge crossing with its final event.
            // Cancellation must never turn that final coordinate into a cut.
            updateCut(Point(x, y))
        }
        finalizeCut(velocityX)
    }

    /** The touch lifted without moving: over the top card it reveals the next one. */
    fun tap(x: Double, y: Double) {
        finalizeCut(0.0)
        val halfWidth = width * ReferenceLayout.Card.WIDTH / 2
        val halfHeight = height * ReferenceLayout.Card.HEIGHT / 2
        if (split > 0.5 && abs(x - width / 2) <= halfWidth && abs(y - height / 2) <= halfHeight) {
            cards.advance()
        }
    }

    /** Ends a tear that is under way without committing it, as an early release does. */
    fun cancelTear() = finalizeCut(0.0)

    // endregion

    // region Events

    override fun nextCard(fromIndex: Int) {
        audio?.beginCardAdvance(fromIndex)
    }

    override fun revealCard(index: Int, rarity: CardRarity) {
        audio?.revealCard(index, rarity)
    }

    override fun cardHaptic() {
        if (!scripted) feedback.cardHaptic()
    }

    // endregion

    // region Drawing

    private fun drawImage(
        canvas: Canvas,
        image: Bitmap,
        x: Double,
        y: Double,
        w: Double,
        h: Double,
        opacity: Double,
        blend: Xfermode? = null,
    ) {
        val alpha = alpha255(opacity)
        if (alpha == 0) return
        rect.set(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat())
        imagePaint.alpha = alpha
        imagePaint.xfermode = blend
        canvas.drawBitmap(image, null, rect, imagePaint)
    }

    private fun roundRect(canvas: Canvas, x: Double, y: Double, w: Double, h: Double, radius: Double, paint: Paint) {
        rect.set(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat())
        canvas.drawRoundRect(rect, radius.toFloat(), radius.toFloat(), paint)
    }

    private fun fullRect(canvas: Canvas, paint: Paint) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    private fun applyBodyTransform(canvas: Canvas, pose: PackLightingPose) {
        canvas.translate((pose.originX + pose.translateX).toFloat(), (pose.originY + pose.translateY).toFloat())
        canvas.rotate(Math.toDegrees(pose.rotation).toFloat())
        canvas.scale(pose.scale.toFloat(), pose.scale.toFloat())
        canvas.translate((-pose.originX).toFloat(), (-pose.originY).toFloat())
    }

    private fun foldedPreview(): List<Point> {
        val progress = cutProgress.value
        if (previewKeyBase !== previewMeshBase || previewKeyProgress != progress || previewKeyDirection != direction) {
            previewFoilVertices = deformPreviewFoil(previewMeshBase, pack, progress, direction)
            previewKeyBase = previewMeshBase
            previewKeyProgress = progress
            previewKeyDirection = direction
            foldMaterial.update(previewFoilVertices, previewMeshTextures, direction, reducedMotion)
        }
        return previewFoilVertices
    }

    private fun bodyPose(t: Double): PackLightingPose {
        if (reducedMotion) {
            return PackLightingPose(width * 0.5, seamY, 0.0, height * 0.015 * clamp(t / 220, 0.0, 1.0), 1.0, 0.0)
        }
        val desiredSeam = interpolateKeyframes(t, BODY_TIMES, BODY_SEAM_Y) * height
        val scale = interpolateKeyframes(t, BODY_TIMES, BODY_SCALE)
        return PackLightingPose(width * 0.5, seamY, 0.0, desiredSeam - seamY, scale, 0.0)
    }

    private fun openMouth(t: Double): MouthGeometry {
        val cached = mouth
        if (cached != null && mouthKeyMs == t && mouthKeyPoints === points) return cached
        val pose = bodyPose(t)
        val mouthDepth = width * interpolateKeyframes(t, MOUTH_DEPTH_TIMES, MOUTH_DEPTH)
        val mouthDepthExponent = interpolateKeyframes(t, MOUTH_EXPONENT_TIMES, MOUTH_EXPONENT)
        val mouthEntryOffset = width * interpolateKeyframes(t, MOUTH_OFFSET_TIMES, MOUTH_ENTRY_OFFSET)
        val mouthExitOffset = width * interpolateKeyframes(t, MOUTH_OFFSET_TIMES, MOUTH_EXIT_OFFSET)
        val mouthRelaxationExponent = interpolateKeyframes(t, MOUTH_RELAXATION_TIMES, MOUTH_RELAXATION)
        val mouthCenterLift = width * interpolateKeyframes(t, MOUTH_LIFT_TIMES, MOUTH_LIFT)
        val mouthRestPoints =
            relaxMouthPoints(
                points, pack, mouthEntryOffset, mouthExitOffset, mouthRelaxationExponent, mouthCenterLift, direction,
            )
        val curl = width * interpolateKeyframes(t, CURL_TIMES, CURL)
        val relaxedMouthPoints = curlMouthPoints(mouthRestPoints, pack, curl, direction)
        // Registered print landmarks sit 2–4 px lower at f150/f162/f178. Adjust
        // the substrate UVs without moving the already aligned torn silhouette.
        val offset = height * interpolateKeyframes(t, BODY_OFFSET_TIMES, BODY_OFFSET)
        val bodySurface =
            makeBodyMaterialMesh(
                relaxedMouthPoints, pack, seamY + width * 0.014, clamp((t - 16.667) / 100, 0.0, 1.0), offset,
            )
        val lipThickness = width * interpolateKeyframes(t, LIP_TIMES, LIP_THICKNESS)
        val mouthSurface =
            makeMouthWallMesh(relaxedMouthPoints, mouthRestPoints, pack, mouthDepth, direction, mouthDepthExponent)
        val mouthLighting =
            deriveMouthLighting(
                MouthLightingInput(
                    vertices = mouthSurface.vertices,
                    textures = mouthSurface.textures,
                    pose = pose,
                    opening = mouthDepth,
                    width = width,
                    height = height,
                    reducedMotion = reducedMotion,
                ),
            )
        val rimSurface =
            makeMouthMaterialMesh(relaxedMouthPoints, pack, lipThickness, direction, 1.0, true, width * 0.0015)

        val u = mouthLighting.liningCenter
        val glowX = pack.x + pack.width * (if (direction == 1) u else 1 - u)
        var glowY = seamY
        for (i in 1 until relaxedMouthPoints.size) {
            if (glowX <= relaxedMouthPoints[i].x) {
                val a = relaxedMouthPoints[i - 1]
                val b = relaxedMouthPoints[i]
                val f = clamp((glowX - a.x) / max(b.x - a.x, 0.001), 0.0, 1.0)
                glowY = a.y + (b.y - a.y) * f
                break
            }
        }

        val geometry =
            MouthGeometry(
                pose = pose,
                bodySurface = bodySurface,
                postCutBottomMask = makeCavityBottomMask(relaxedMouthPoints, pack),
                entryBodyWedgePath = makeEntryBodyWedge(relaxedMouthPoints, pack, direction),
                mouthFrontEdgePath = makeCavityFrontEdge(relaxedMouthPoints),
                mouthLighting = mouthLighting,
                mouthSurfaceVertices = expandMouthMesh(mouthSurface.vertices, MOUTH_WALL_ROWS, width * 0.0015),
                mouthSurfaceTextures = mouthSurface.textures,
                mouthSurfaceOutline = makeMouthMeshOutline(mouthSurface.vertices, MOUTH_WALL_ROWS),
                rimSurfaceVertices = expandMouthMesh(rimSurface.vertices, 2, width * 0.003),
                rimSurfaceTextures = rimSurface.textures,
                rimSurfaceOutline = makeMouthMeshOutline(rimSurface.vertices, 2, width * 0.0005),
                glowShader = radialGradient(glowX, glowY, pack.width * 0.30, MOUTH_GLOW_COLORS, MOUTH_GLOW_POSITIONS),
            )
        mouth = geometry
        mouthKeyMs = t
        mouthKeyPoints = points
        return geometry
    }

    /** Draws the scene in dp; [density] is the scale already applied to [canvas]. */
    fun draw(canvas: Canvas, density: Float) {
        meshes.beginFrame()
        if (!prewarmed) {
            prewarmed = true
            // Where meshes go through bitmaps, the pass would only allocate them all at once.
            if (isIdle && meshes.hardware) prewarm(canvas, density)
        }
        val backdropAlpha = alpha255(backdropOpacity.value)
        // Once the collection backdrop is opaque it covers the pack scene completely.
        if (backdropAlpha < 255) {
            val punch = if (liveEffects && committed) commitPunchScale(postCutMs.value) else 1.0
            canvas.save()
            if (punch != 1.0) canvas.scale(punch.toFloat(), punch.toFloat(), (width / 2).toFloat(), (height / 2).toFloat())
            drawPack(canvas, density, postCutMs.value)
            canvas.restore()
        }

        val cardTop = cardTopAtTime(postCutMs.value, reducedMotion) * height
        backdrop.draw(canvas, brush, backdropOpacity.value, backdropZoom.value, reducedMotion)
        cardScene.draw(canvas, brush, cardTop, if (backdropOpacity.value > 0.001) 1.0 else 0.0, reducedMotion)
    }

    /**
     * The first frame draws every layer of the tear, the opening and the card stack once, into a
     * layer composited at 1/255, as the source keeps its backdrop rendered almost transparent
     * before it is revealed: the GPU programs are built before the pack is touched, not at the cut.
     */
    private fun prewarm(canvas: Canvas, density: Float) {
        canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), 1)
        val tear = referenceTracePoints(0.6, width, height)
        points = tear
        peelVisible = 1.0
        updatePreviewGeometry(tear, 1)
        cutProgress.set(0.6)
        traceOpacity.set(1.0)
        drawPack(canvas, density, 0.0)

        val cut = completeCutPoints(referenceTracePoints(1.0, width, height), pack, 1)
        val mesh = makeFoilMesh(cut, pack)
        val previewMesh = makePreviewFoilMesh(cut, pack)
        points = cut
        previewMeshBase = previewMesh.vertices
        previewMeshTextures = previewMesh.textures
        meshBase = mesh.vertices
        meshTextures = mesh.textures
        cutProgress.set(1.0)
        committed = true
        split = 1.0
        peelVisible = 0.0
        for (t in PREWARM_TIMES) drawPack(canvas, density, t)
        backdrop.draw(canvas, brush, 1.0, 1.0, reducedMotion)
        cardScene.prewarm(canvas, brush)
        canvas.restore()
        seal()
    }

    private fun drawPack(canvas: Canvas, density: Float, t: Double) {
        val backgroundMorph = clamp(t / 133.333, 0.0, 1.0)
        val progress = cutProgress.value
        val previewMix = peelVisible * clamp((progress - 0.14) / 0.08, 0.0, 1.0)
        val previewOpacity = (1 - split) * previewMix
        val intactOpacity = (1 - split) * (1 - previewMix)
        val flatFoilOpacity = split * (1 - clamp(t / 33.333, 0.0, 1.0))
        val visibleTraceOpacity = if (committed) 0.0 else traceOpacity.value
        val packAuraOpacity =
            0.36 * (1 + 0.3 * idleMix.value * idleAuraBreath(idlePhase)) * (1 - split * clamp(t / 350, 0.0, 1.0))
        val sheen = idleSheen(idlePhase, pack)
        val sheenOpacity = idleMix.value * sheen.opacity
        val seamHint = idleSeamHint(idlePhase, pack)
        val seamHintOpacity = idleMix.value * seamHint.opacity
        val seamHintY = seamY - height * 0.0045

        fullRect(canvas, brush.fill(shader = skyGradient))
        if (backgroundMorph > 0) fullRect(canvas, brush.fill(opacity = backgroundMorph, shader = openSkyGradient))
        // Dim the background while tearing, keeping the pack and card clear.
        if (dimOpacity.value > 0.001) fullRect(canvas, brush.fill(opacity = dimOpacity.value, shader = dimGradient))

        if (packAuraOpacity > 0.002) {
            roundRect(
                canvas,
                pack.x - width * 0.018,
                pack.y - width * 0.012,
                pack.width + width * 0.036,
                pack.height + width * 0.02,
                width * 0.035,
                brush.fill(0xFF63F8FF.toInt(), packAuraOpacity, blur = width * 0.055),
            )
        }

        val folded = previewOpacity > 0 || flatFoilOpacity > 0
        val previewVertices = if (folded) foldedPreview() else previewFoilVertices
        if (split < 1) {
            if (previewOpacity <= 0 && sheenOpacity <= 0.001) {
                drawImage(canvas, images.pack, pack.x, pack.y, pack.width, pack.height, intactOpacity)
            } else {
                // Sum the two weighted poses on an isolated layer. Source-over
                // crossfading would expose 25% of the background at the midpoint.
                val margin = width * 0.03
                canvas.saveLayer(
                    (pack.x - margin).toFloat(),
                    (pack.y - margin).toFloat(),
                    (pack.x + pack.width + margin).toFloat(),
                    (pack.y + pack.height + margin).toFloat(),
                    null,
                )
                drawImage(canvas, images.pack, pack.x, pack.y, pack.width, pack.height, intactOpacity)
                if (previewOpacity > 0) {
                    canvas.save()
                    canvas.clipPath(previewBottomMask)
                    drawImage(canvas, images.pack, pack.x, pack.y, pack.width, pack.height, previewOpacity, BLEND_PLUS)
                    canvas.restore()
                    foldMaterial.draw(canvas, meshes, previewVertices, previewMeshTextures, previewOpacity, BLEND_PLUS, density)
                }
                // Inside the pack layer, so the sheen keeps the artwork's alpha.
                if (sheenOpacity > 0.001) {
                    canvas.drawRect(
                        pack.x.toFloat(),
                        pack.y.toFloat(),
                        (pack.x + pack.width).toFloat(),
                        (pack.y + pack.height).toFloat(),
                        brush.fill(
                            opacity = sheenOpacity,
                            shader =
                                linearGradient(
                                    sheen.start.x, sheen.start.y, sheen.end.x, sheen.end.y, SHEEN_COLORS, SHEEN_POSITIONS,
                                ),
                            blend = BLEND_SRC_ATOP,
                        ),
                    )
                }
                canvas.restore()
            }
        }

        if (committed) drawOpening(canvas, density, t)

        if (flatFoilOpacity > 0) {
            foldMaterial.draw(canvas, meshes, previewVertices, previewMeshTextures, flatFoilOpacity, null, density)
        }

        if (committed && t < 220) {
            val topRow = ArrayList<Point>(PREVIEW_MESH_COLUMNS)
            var index = 0
            while (index < previewVertices.size) {
                topRow.add(previewVertices[index])
                index += PREVIEW_MESH_ROWS
            }
            cutFlash.draw(canvas, brush, t, 1.0, makeBottomMask(topRow, pack), reducedMotion)
        }
        if (liveEffects && committed) cutBurst.draw(canvas, brush, t)

        canvas.save()
        canvas.clipRect(pack.x.toFloat(), pack.y.toFloat(), (pack.x + pack.width).toFloat(), (pack.y + pack.height).toFloat())
        if (seamHintOpacity > 0.001) {
            seamHintPath.rewind()
            seamHintPath.moveTo(seamHint.tail.toFloat(), seamHintY.toFloat())
            seamHintPath.lineTo(seamHint.head.toFloat(), seamHintY.toFloat())
            canvas.drawPath(
                seamHintPath,
                brush.stroke(
                    width * 0.026,
                    opacity = seamHintOpacity,
                    blur = width * 0.012,
                    shader =
                        linearGradient(
                            seamHint.tail, seamHintY, seamHint.head, seamHintY, SEAM_HINT_COLORS, SEAM_HINT_POSITIONS,
                        ),
                    cap = Paint.Cap.ROUND,
                ),
            )
            canvas.drawCircle(
                seamHint.head.toFloat(), seamHintY.toFloat(), (width * 0.034).toFloat(),
                brush.fill(0xFF9CF8FF.toInt(), seamHintOpacity, blur = width * 0.03),
            )
            canvas.drawCircle(
                seamHint.head.toFloat(), seamHintY.toFloat(), (width * 0.017).toFloat(),
                brush.fill(0xFFFFFFFF.toInt(), seamHintOpacity, blur = width * 0.008),
            )
        }
        if (visibleTraceOpacity > 0.001) drawTrace(canvas, visibleTraceOpacity, progress)
        canvas.restore()
        if (liveEffects) sparks.draw(canvas, brush)
    }

    /** The layers of the open pack, [t] milliseconds after the cut: strip, mouth, card, body, rim. */
    private fun drawOpening(canvas: Canvas, density: Float, t: Double) {
        // Pack geometry reaches its final pose at 1000 ms. The clamp stops
        // mesh rebuilding while the card and session clock continue to 2000.
        val geometryMs = min(t, POST_CUT_DURATION_MS)
        val meshOpacity = split * clamp(t / 33.333, 0.0, 1.0) * (1 - clamp((t - 920) / 80, 0.0, 1.0))
        val bodyOpacity =
            if (reducedMotion) {
                split * (1 - clamp((t - 650) / 200, 0.0, 1.0))
            } else {
                // The video ends with a little of the package lip still at the bottom.
                // Clear that remnant while the card moves into its final presentation.
                split * (1 - clamp((t - POST_CUT_DURATION_MS) / 180, 0.0, 1.0))
            }
        val bodyDeglareStrength = split * clamp((t - 16.667) / 100, 0.0, 1.0)
        val edgeOpacity = split * (1 - clamp((t - 900) / 200, 0.0, 1.0))
        val cavityStrength = interpolateKeyframes(geometryMs, CAVITY_TIMES, CAVITY_STRENGTH)
        val cavityOpacity = bodyOpacity * cavityStrength
        val cardTop = cardTopAtTime(t, reducedMotion) * height
        val cardOpacity =
            if (reducedMotion) clamp((t - 700) / 200, 0.0, 1.0) else interpolateKeyframes(t, CARD_TIMES, CARD_OPACITY)
        val cardContactShadowOpacity = bodyOpacity * cardOpacity * cavityStrength * 0.36
        val cardShadowOpacity = cardOpacity * 0.46

        if (meshOpacity > 0) {
            val foilVertices = if (reducedMotion) meshBase else deformFlyingFoil(meshBase, pack, width, height, geometryMs)
            foilMaterial.update(foilVertices, meshTextures, geometryMs, reducedMotion)
            foilMaterial.draw(canvas, meshes, foilVertices, meshTextures, meshOpacity, density)
            if (foilVertices.size >= 4) {
                foilEdgePath.rewind()
                foilEdgePath.moveTo(foilVertices[1].x.toFloat(), foilVertices[1].y.toFloat())
                var index = 3
                while (index < foilVertices.size) {
                    foilEdgePath.lineTo(foilVertices[index].x.toFloat(), foilVertices[index].y.toFloat())
                    index += 2
                }
                canvas.drawPath(
                    foilEdgePath,
                    brush.stroke(width * 0.006, 0xFF48F3F6.toInt(), meshOpacity, blur = width * 0.009, cap = Paint.Cap.ROUND),
                )
            }
        }

        // The renderer and reflections consume the same pose. Changing motion or
        // pausing it cannot leave an independent lighting animation running.
        val open = if (bodyOpacity > 0) openMouth(geometryMs) else null

        if (open != null && cavityOpacity > 0) {
            canvas.save()
            applyBodyTransform(canvas, open.pose)
            canvas.clipPath(open.mouthSurfaceOutline)
            wallMaterial.draw(
                canvas, meshes, open.mouthSurfaceVertices, open.mouthSurfaceTextures, MOUTH_WALL_INDICES,
                open.mouthLighting, cavityOpacity * cavityStrength, density,
            )
            canvas.restore()
        }

        if (liveEffects) riseLight.draw(canvas, t, cardTop)

        if (cardShadowOpacity > 0.002) {
            roundRect(
                canvas,
                cardX - width * 0.004,
                cardTop,
                cardWidth + width * 0.008,
                cardHeight + width * 0.008,
                width * 0.02,
                brush.fill(0xFF4B5474.toInt(), cardShadowOpacity, blur = width * 0.01),
            )
        }
        drawImage(canvas, images.cards[0], cardX, cardTop, cardWidth, cardHeight, cardOpacity)

        if (open == null) return

        if (cardContactShadowOpacity > 0.002) {
            canvas.save()
            applyBodyTransform(canvas, open.pose)
            canvas.clipRect(
                (pack.x - width * 0.02).toFloat(),
                (pack.y - width * 0.01).toFloat(),
                (pack.x + pack.width + width * 0.02).toFloat(),
                (pack.y + pack.height + width * 0.01).toFloat(),
            )
            canvas.drawPath(
                open.mouthFrontEdgePath,
                brush.stroke(
                    width * 0.012, 0xFF05040B.toInt(), cardContactShadowOpacity,
                    blur = width * 0.006, cap = Paint.Cap.ROUND, join = Paint.Join.ROUND,
                ),
            )
            canvas.restore()
        }

        canvas.save()
        applyBodyTransform(canvas, open.pose)
        canvas.clipPath(open.postCutBottomMask)
        canvas.drawPath(open.entryBodyWedgePath, brush.fill(opacity = bodyOpacity * cavityStrength, shader = wedgeGradient))
        val mix = clamp((geometryMs - 66.667) / 83.333, 0.0, 1.0)
        // Interpolate geometry of one opaque image. Crossfading two source-over
        // images would let 25% of the background through at the halfway point.
        val bodyTextureRect =
            Rect(
                pack.x + (settledBodyTexture.x - pack.x) * mix,
                pack.y,
                pack.width + (settledBodyTexture.width - pack.width) * mix,
                pack.height + (settledBodyTexture.height - pack.height) * mix,
            )
        bodyMaterial.draw(canvas, meshes, open.bodySurface, bodyTextureRect, bodyDeglareStrength, bodyOpacity, density)
        canvas.restore()

        canvas.save()
        applyBodyTransform(canvas, open.pose)
        if (edgeOpacity * bodyOpacity > 0) {
            canvas.save()
            canvas.clipPath(open.rimSurfaceOutline)
            rimMaterial.draw(
                canvas, meshes, open.rimSurfaceVertices, open.rimSurfaceTextures, MOUTH_MESH_INDICES,
                open.mouthLighting, edgeOpacity * bodyOpacity, density,
            )
            canvas.restore()
        }
        val cavitySpecularOpacity = open.mouthLighting.liningLight * 0.94
        if (cavitySpecularOpacity * bodyOpacity > 0.002) {
            canvas.drawPath(
                open.mouthFrontEdgePath,
                brush.stroke(
                    width * 0.023,
                    opacity = cavitySpecularOpacity * bodyOpacity,
                    blur = width * 0.013,
                    shader = open.glowShader,
                    blend = BLEND_SCREEN,
                    cap = Paint.Cap.ROUND,
                ),
            )
        }
        canvas.restore()
    }

    private fun drawTrace(canvas: Canvas, opacity: Double, progress: Double) {
        val startX = if (direction == 1) pack.x else pack.x + pack.width
        // Keep the material transition fixed to the pack: a gesture covers its whole width.
        val endX = if (direction == 1) pack.x + pack.width else pack.x
        val extension = pack.width * interpolateKeyframes(progress, CORE_EXTENSION_PROGRESS, CORE_EXTENSION)
        val coreEndX = if (direction == 1) endX + extension else endX - extension
        val traceBodyPath = makeSmoothPath(trimTraceEnd(points, traceBodyWidth * 0.5 - traceSize * 0.003))
        val traceCorePath = makeSmoothPath(trimTraceEnd(points, traceCoreWidth * 0.5))

        canvas.drawPath(
            emissiveSeamPath,
            brush.stroke(width * 0.016, rgba(214, 38, 238, 0.55), opacity, blur = width * 0.003),
        )
        canvas.drawPath(emissiveSeamPath, brush.stroke(width * 0.008, 0xFFF006FA.toInt(), opacity))
        canvas.drawPath(
            traceBodyPath,
            brush.stroke(
                glowWidth, 0xFFE049F1.toInt(), opacity,
                blur = traceSize * 0.032, cap = Paint.Cap.ROUND, join = Paint.Join.ROUND,
            ),
        )
        canvas.drawPath(
            traceBodyPath,
            brush.stroke(
                traceBodyWidth,
                opacity = opacity,
                blur = traceSize * 0.0045,
                shader = linearGradient(startX, seamY, endX, seamY, TRACE_BODY_COLORS, TRACE_POSITIONS),
                cap = Paint.Cap.ROUND,
                join = Paint.Join.ROUND,
            ),
        )
        canvas.drawPath(
            traceBodyPath,
            brush.stroke(
                traceFillWidth,
                opacity = opacity,
                blur = traceSize * 0.002,
                shader = linearGradient(startX, seamY, endX, seamY, TRACE_FILL_COLORS, TRACE_POSITIONS),
                cap = Paint.Cap.ROUND,
                join = Paint.Join.ROUND,
            ),
        )
        canvas.drawPath(
            traceCorePath,
            brush.stroke(
                traceCoreWidth,
                opacity = opacity,
                blur = traceSize * 0.004,
                shader = linearGradient(startX, seamY, coreEndX, seamY, TRACE_CORE_COLORS, TRACE_CORE_POSITIONS),
                cap = Paint.Cap.ROUND,
                join = Paint.Join.ROUND,
            ),
        )
    }

    // endregion
}

/** Everything the open pack is drawn from at one moment of the opening clock. */
private class MouthGeometry(
    val pose: PackLightingPose,
    val bodySurface: Mesh,
    val postCutBottomMask: Path,
    val entryBodyWedgePath: Path,
    val mouthFrontEdgePath: Path,
    val mouthLighting: MouthLighting,
    val mouthSurfaceVertices: List<Point>,
    val mouthSurfaceTextures: List<Point>,
    val mouthSurfaceOutline: Path,
    val rimSurfaceVertices: List<Point>,
    val rimSurfaceTextures: List<Point>,
    val rimSurfaceOutline: Path,
    val glowShader: Shader,
)
