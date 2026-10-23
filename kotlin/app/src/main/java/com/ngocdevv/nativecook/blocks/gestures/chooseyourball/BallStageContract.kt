package com.ngocdevv.nativecook.blocks.gestures.chooseyourball

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// Every number of the 3D scene (BallStage3D.contract.ts, ballFilamentSurface.ts,
// ballStageMotion.ts). The React Native block renders with the same engine, so these are its
// values unchanged: world units are ball radii, and the model is used as exported.

/** Names of the model's eight entities (ball-filament.glb). */
internal object BallEntity {
    const val SHELL_UPPER = "shell-upper"
    const val SHELL_LOWER = "shell-lower"
    const val BAND = "band"
    const val BUTTON = "button"
}

/** The camera: a 26° vertical field of view from 5.9 radii away, on a square viewport. */
internal object BallCamera {
    const val FOV_DEGREES = 26.0
    const val ASPECT = 1.0
    const val NEAR = 0.1
    const val FAR = 100.0
    const val DISTANCE = 5.9
    /** The orbit at the end of the free look, either way. */
    const val TILT_DEGREES = 25.0
    // react-native-filament's camera: f/16, 1/125 s, ISO 100.
    const val APERTURE = 16f
    const val SHUTTER_SPEED = 1f / 125f
    const val SENSITIVITY = 100f

    /** The eye for a tilt of -1…1: an orbit over the ball in the YZ plane, looking at its centre. */
    fun orbitPosition(tilt: Float): DoubleArray {
        val angle = tilt.coerceIn(-1f, 1f) * TILT_DEGREES * (PI / 180.0)
        return doubleArrayOf(0.0, sin(angle) * DISTANCE, cos(angle) * DISTANCE)
    }
}

/** ACES (legacy) tone mapping with the exposure in stops: log2(1.15). */
internal val BALL_EXPOSURE_STOPS: Float = (ln(1.15) / ln(2.0)).toFloat()

/** Intensity of the image-based light (warehouse-ibl.ktx). */
internal const val BALL_ENVIRONMENT_INTENSITY = 30_000f

/** The shell turns exactly once per slot, so the button is home whenever the carousel rests. */
internal const val TURN_PER_SLOT = 2.0 * PI

private fun srgbChannelToLinear(channel: Int): Float {
    val value = channel / 255.0
    return (if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)).toFloat()
}

/** sRGB 0xRRGGBB as the linear RGB Filament's lights and material factors take. */
internal fun hexToLinear(rgb: Int): FloatArray = floatArrayOf(
    srgbChannelToLinear((rgb shr 16) and 255),
    srgbChannelToLinear((rgb shr 8) and 255),
    srgbChannelToLinear(rgb and 255),
)

/** A glTF material's factors; `baseColor` is linear RGB. */
internal class BallMaterial(val baseColor: FloatArray, val metallic: Float, val roughness: Float)

// The white hardware is not 0xE7E7E7 converted: these factors are the reference's measured
// compensation, which keeps it neutral under the warehouse light and the ACES curve.
private val NEUTRAL_HARDWARE = floatArrayOf(1.16f, 1.32f, 1.65f)

internal object BallMaterials {
    /** The upper shell's colour comes from its texture, one per ball. */
    val upper = BallMaterial(floatArrayOf(1f, 1f, 1f), metallic = 0.1f, roughness = 0f)
    val lower = BallMaterial(NEUTRAL_HARDWARE, metallic = 0f, roughness = 0f)
    val band = BallMaterial(hexToLinear(0x000000), metallic = 0f, roughness = 0.14f)
    val button = BallMaterial(NEUTRAL_HARDWARE, metallic = 0f, roughness = 0f)
}

/**
 * Markings that are meshes of their own, laid just over the shell. Three balls use them; on every
 * other ball they are scaled to nothing. `lift` is the scale that raises one off the shell.
 */
internal enum class BallMeshMark(val entity: String, val paint: Int, val lift: Float, val metallic: Float) {
    GREAT("mark-great", 0x850007, 1.006f, 0.07f),
    ULTRA("mark-ultra", 0xAAA000, 1.006f, 0.07f),
    MASTER_PATCH("mark-master-patch", 0xEB0997, 1.006f, 0.07f),
    MASTER_EMBLEM("mark-master-emblem", 0xFFFFFF, 1.02f, 0f);

    val material: BallMaterial get() = BallMaterial(hexToLinear(paint), metallic, roughness = 0.1f)
}

internal fun meshMarksOf(ball: BallId): List<BallMeshMark> = when (ball) {
    BallId.GREAT -> listOf(BallMeshMark.GREAT)
    BallId.ULTRA -> listOf(BallMeshMark.ULTRA)
    BallId.MASTER -> listOf(BallMeshMark.MASTER_PATCH, BallMeshMark.MASTER_EMBLEM)
    else -> emptyList()
}

/** A direct light. `intensity` is lux for a directional light and lumens for a point light. */
internal class BallLight(
    val directional: Boolean,
    val position: FloatArray,
    val intensity: Float,
    val falloff: Float = 0f,
)

// The scene was first lit in three.js, whose lights have no unit: one three unit is 10 000 lux
// for a directional light and 1 000 lumens for a point light.
private const val DIRECTIONAL_LUX = 10_000f
private const val POINT_LUMENS = 1_000f

internal object BallLights {
    /** White key from the upper left, in front. */
    val key = BallLight(true, floatArrayOf(-2.4f, 3.2f, 3.4f), 2.2f * DIRECTIONAL_LUX)
    /** Cool fill (0x8FA6C4) from the lower right. */
    val fill = BallLight(true, floatArrayOf(2.6f, -1.4f, 1.8f), 0.35f * DIRECTIONAL_LUX)
    const val FILL_COLOR = 0x8FA6C4
    /** Two point lights behind the ball, in the colour of the selected ball's aura. */
    val rim = BallLight(false, floatArrayOf(2.1f, -0.9f, -1.7f), 9f * POINT_LUMENS, falloff = 14f)
    val rimBack = BallLight(false, floatArrayOf(-2.1f, 0.7f, -1.8f), 5.5f * POINT_LUMENS, falloff = 14f)

    /** A directional light shines from its position towards the ball's centre. */
    fun directionToOrigin(position: FloatArray): FloatArray {
        val length = sqrt(position[0] * position[0] + position[1] * position[1] + position[2] * position[2])
        return floatArrayOf(-position[0] / length, -position[1] / length, -position[2] / length)
    }
}
