package com.ngocdevv.nativecook.blocks.cards.aurawalletwidget

// Fixed uniform storage owned by a shell; the pure builder fills it in place.
// Arrays are handed directly to RuntimeShader through the Java bridge.
internal class AuraUniforms {
    val opaque = FloatArray(1)
    val neckJoin = FloatArray(1)
    val pressValues = FloatArray(4)
    val actionRects = FloatArray(16)
    val lipPoses = FloatArray(20)
    val liquidState = FloatArray(4)
    val dropPose = FloatArray(4)
    val dropShape = FloatArray(4)
    val neckPose = FloatArray(4)
    val sendFx = FloatArray(4)
    val liquidOptics = FloatArray(4)
    val liquidBounds = FloatArray(4)
    val dropCast = FloatArray(3)
    val castBounds = FloatArray(4)
    val poolWave = FloatArray(2)
    val landing = FloatArray(4)
    val names = arrayOf("opaque", "neckJoin", "pressValues", "actionRects", "lipPoses",
        "liquidState", "dropPose", "dropShape", "neckPose", "sendFx", "liquidOptics",
        "liquidBounds", "dropCast", "castBounds", "poolWave", "landing")
    val arrays = arrayOf(opaque, neckJoin, pressValues, actionRects, lipPoses,
        liquidState, dropPose, dropShape, neckPose, sendFx, liquidOptics,
        liquidBounds, dropCast, castBounds, poolWave, landing)
}
