package com.ngocdevv.nativecook.blocks.gestures.pullcordtheme

import androidx.compose.ui.graphics.Color

/** Literal design and motion tokens mirrored from the approved React Native source. */
internal object PullCordThemeTokens {
    const val STAGE_WIDTH = 402f
    const val STEP = 1f / 120f
    const val MAX_STEPS = 30
    const val LOOP_MS = 12_000L
    const val RESUME_MS = 3_500L
    const val LIGHT_REST_AT = 5f
    const val REPLICA = false
    const val REPLICA_LOOP_MS = 6_000L
    const val REPLICA_HOLD = -1f
    const val QA_SPEED = 1f
    const val REPLICA_SWING_PHASE = -Math.PI.toFloat() / 2f
    const val REPLICA_PERIOD = 5.8f

    data class Palette(
        val wallTop: Color, val wallBottom: Color, val panelShadow: Color, val panelLight: Color,
        val spill: Color, val spillFar: Color, val halo: Color, val glassTint: Color,
        val glassEdge: Color, val glassEdgeLit: Color, val glassSpecular: Color,
        val filamentCore: Color, val filamentGlow: Color, val support: Color, val wire: Color,
        val metal: List<Color>, val metalStops: FloatArray, val thread: Color,
        val cord: Color, val cordLit: Color, val brass: List<Color>, val brassStops: FloatArray,
        val ink: Color, val mute: Color,
    )

    private fun rgba(hex: Long, alpha: Float = 1f) = Color((hex or 0xFF000000L).toInt()).copy(alpha = alpha)
    private fun rgb(hex: Long) = rgba(hex)

    val DARK = Palette(rgb(0x0B0C0E), rgb(0x0D1116), rgb(0x030405), rgb(0x3C7EF2), rgba(0x2468E6, .34f), rgba(0x2468E6, 0f), rgba(0x3484FF, .55f), rgba(0x2870FF, .22f), rgba(0x78BAFF, .55f), rgb(0xBFE0FF), rgba(0xEBF6FF, .85f), rgb(0xE4F3FF), rgb(0x2F86FF), rgba(0xAAD2FF, .35f), rgb(0x30343A), listOf(0x2A2D31, 0x7D838C, 0xE9EDF2, 0x9AA0A9, 0x3A3E44, 0x1C1E21).map { rgb(it.toLong()) }, floatArrayOf(0f,.22f,.42f,.62f,.84f,1f), rgba(0, .55f), rgb(0x3A414B), rgba(0x6EAAFF, .55f), listOf(0x2E2414,0x7A5E2E,0xD6B676,0x8E6C34,0x2A2012).map { rgb(it.toLong()) }, floatArrayOf(0f,.25f,.45f,.7f,1f), rgb(0xEEF3FA), rgba(0xEEF3FA, .56f))
    val LIGHT = Palette(rgb(0xF3EEE6), rgb(0xE7DFD2), rgb(0xCBBFAE), rgb(0xFFFFFF), rgba(0xFFC46E, .5f), rgba(0xFFC46E, 0f), rgba(0xFFB85C, .6f), rgba(0xFFE4B0, .3f), rgba(0x786852, .5f), rgb(0xFFFFFF), rgba(0xFFFFFF, .95f), rgb(0xFFF8E6), rgb(0xFFA733), rgba(0x78603C, .35f), rgb(0x2F2A25), listOf(0x4A4D52,0x9DA2AA,0xF6F7F9,0xB4B9C0,0x5A5E64,0x3A3C40).map { rgb(it.toLong()) }, floatArrayOf(0f,.22f,.42f,.62f,.84f,1f), rgba(0x282014, .45f), rgb(0x3A332B), rgba(0xFFFFFF, .5f), listOf(0x5E461E,0xB48A42,0xF6DFA4,0xC29A52,0x5A4320).map { rgb(it.toLong()) }, floatArrayOf(0f,.25f,.45f,.7f,1f), rgb(0x1D1A16), rgba(0x1D1A16, .58f))

    val LIQUID = mapOf("top" to rgba(0xFFF9E8,.96f), "bottom" to rgba(0xFFBA5C,.92f), "meniscus" to rgb(0xFFFFFF), "lightAlpha" to .5f, "glow" to rgb(0xFFA733), "filament" to rgb(0xFFC261), "core" to rgb(0xFFF8E6), "spill" to rgba(0xFFBE64,.42f), "spillFar" to rgba(0xFFBE64,0f), "halo" to rgba(0xFFB85C,.6f), "haloFar" to rgba(0xFFB85C,0f))
    val SPACE = floatArrayOf(4f,8f,12f,16f,24f,32f)
    val BULB = floatArrayOf(.265f,700f,56f,50f,40f,6f,5f,46f,25f,68.5f,156.5f,225f,8f,108f,30f,146f,2.6f,170f)
    val CORD = floatArrayOf(1f - 32f/402f,-14f,.56f,18f,2.6f,15f,36f,7.5f,7f,6f,56f)
    val GLOW = floatArrayOf(12f,10f,.6f,5f,3f,2.2f,.5f)
    val PANEL = floatArrayOf(.125f,36f,3f,1.5f,4f,3f)
    val TYPE = floatArrayOf(32f,48f,12f,1.8f,48f,54f,15f,20f,8f)
    val COPY = listOf("APPEARANCE", "Dark", "Light", "Pull the cord to switch", "Light cord", "Switches the app between dark and light theme")
    val FIT = floatArrayOf(2.048f / 3f, -134f / 3f)
    val REPLICA_ART = mapOf(
        "wallX" to ((58f - 148f * 1.2f) / 3f), "wallW" to (1206f * 1.2f / 3f),
        "spriteScale" to (1.26f / 3f), "spriteW" to 480f, "spriteH" to 720f,
        "anchorX" to 240f, "anchorY" to 30f, "socketTopSrc" to 370.6f,
        "glassCenterSrc" to 205f, "swingSrc" to 82f, "centreSrc" to 367.9f,
        "halo" to 170f, "spillR" to .8f, "ambientR" to 1.6f, "grabRadius" to 110f,
        "wire" to 2000f, "wireWidth" to 6f, "bloom" to 10f, "bloomOpacity" to .55f,
        "lightMap" to 1f,
    )
    val ROPE = floatArrayOf(2600f,.988f,10f,.25f,72f,90f,1800f,1.5f,600f)
    val SWITCH = floatArrayOf(36f,66f,44f,20f,300f,26f,46f)
    val BULB_MOTION = floatArrayOf(6f,14f,0f,.3f,.02f,.025f)
    val REVEAL = floatArrayOf(.72f,.62f,.2f,40f,70f)
    val LEVEL = floatArrayOf(16f,10f)
    val IGNITE_TIMES = floatArrayOf(0f,.05f,.11f,.19f,.26f,.36f,.45f,.6f)
    val IGNITE_VALUES = floatArrayOf(.1f,.8f,.15f,.9f,.3f,1f,.6f,1f)
    val HAPTIC = intArrayOf(1,2,3,4)
    val FEEL = floatArrayOf(12f,900f,24f,.005f,.2f)
    val HERO = floatArrayOf(.9f,.12f,.22f,.34f,.42f,.55f,.28f,.32f,34f,.05f,26f,26f,34f,.2f,10f,26f,.42f)
    val SCRIPT = arrayOf(floatArrayOf(1.2f,2f,0f,0f,-6f,30f,1f,0f), floatArrayOf(2f,2.35f,-6f,30f,-4f,18f,0f,0f), floatArrayOf(2.35f,2.8f,-4f,18f,4f,64f,0f,0f), floatArrayOf(2.8f,2.95f,4f,64f,4f,64f,0f,1f), floatArrayOf(7.2f,7.75f,0f,0f,8f,60f,1f,0f), floatArrayOf(7.75f,7.9f,8f,60f,8f,60f,0f,1f))
    val TAP_PULL = arrayOf(floatArrayOf(0f,.3f,0f,0f,2f,60f,1f,0f), floatArrayOf(.3f,.4f,2f,60f,2f,60f,0f,1f))
    val TAP = floatArrayOf(8f,.25f)
    val REPLICA_IGNITE_TIMES = floatArrayOf(0f,.25f,.5f,.75f)
    val REPLICA_IGNITE_VALUES = floatArrayOf(.08f,.5f,.5f,1f)
}
