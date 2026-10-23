package com.ngocdevv.nativecook.blocks.gestures.pullcordtheme

import androidx.compose.ui.graphics.Color
import com.ngocdevv.nativecook.shared.rgba

/**
 * Every colour and size of the block, copied from pull-cord-theme/theme.ts.
 * Sizes are dp on a 402 dp wide screen and are scaled by width / 402.
 */
internal object PullCordThemeTheme {
    val DARK = PullCordThemePalette(
        // Measured from the source: wall #0C0C0C-#0E1215, blue spill #19498F on the panel edges.
        wallTop = Color(0xFF0B0C0E),
        wallBottom = Color(0xFF0D1116),
        panelShadow = Color(0xFF030405),
        panelLight = Color(0xFF3C7EF2),
        spill = rgba(36, 104, 230, 0.34f),
        spillFar = rgba(36, 104, 230, 0f),
        halo = rgba(52, 132, 255, 0.55f),
        glassTint = rgba(40, 112, 255, 0.22f),
        glassEdge = rgba(120, 186, 255, 0.55f),
        glassEdgeLit = Color(0xFFBFE0FF),
        glassSpecular = rgba(235, 246, 255, 0.85f),
        filamentCore = Color(0xFFE4F3FF),
        filamentGlow = Color(0xFF2F86FF),
        support = rgba(170, 210, 255, 0.35f),
        wire = Color(0xFF30343A),
        metal = listOf(
            Color(0xFF2A2D31), Color(0xFF7D838C), Color(0xFFE9EDF2),
            Color(0xFF9AA0A9), Color(0xFF3A3E44), Color(0xFF1C1E21),
        ),
        metalStops = floatArrayOf(0f, 0.22f, 0.42f, 0.62f, 0.84f, 1f),
        thread = rgba(0, 0, 0, 0.55f),
        cord = Color(0xFF3A414B),
        cordLit = rgba(110, 170, 255, 0.55f),
        brass = listOf(
            Color(0xFF2E2414), Color(0xFF7A5E2E), Color(0xFFD6B676), Color(0xFF8E6C34), Color(0xFF2A2012),
        ),
        brassStops = floatArrayOf(0f, 0.25f, 0.45f, 0.7f, 1f),
        ink = Color(0xFFEEF3FA),
        mute = rgba(238, 243, 250, 0.56f),
    )

    val LIGHT = PullCordThemePalette(
        wallTop = Color(0xFFF3EEE6),
        wallBottom = Color(0xFFE7DFD2),
        panelShadow = Color(0xFFCBBFAE),
        panelLight = Color(0xFFFFFFFF),
        spill = rgba(255, 196, 110, 0.5f),
        spillFar = rgba(255, 196, 110, 0f),
        halo = rgba(255, 184, 92, 0.6f),
        glassTint = rgba(255, 228, 176, 0.3f),
        glassEdge = rgba(120, 104, 82, 0.5f),
        glassEdgeLit = Color(0xFFFFFFFF),
        glassSpecular = rgba(255, 255, 255, 0.95f),
        filamentCore = Color(0xFFFFF8E6),
        filamentGlow = Color(0xFFFFA733),
        support = rgba(120, 96, 60, 0.35f),
        wire = Color(0xFF2F2A25),
        metal = listOf(
            Color(0xFF4A4D52), Color(0xFF9DA2AA), Color(0xFFF6F7F9),
            Color(0xFFB4B9C0), Color(0xFF5A5E64), Color(0xFF3A3C40),
        ),
        metalStops = floatArrayOf(0f, 0.22f, 0.42f, 0.62f, 0.84f, 1f),
        thread = rgba(40, 32, 20, 0.45f),
        cord = Color(0xFF3A332B),
        cordLit = rgba(255, 255, 255, 0.5f),
        brass = listOf(
            Color(0xFF5E461E), Color(0xFFB48A42), Color(0xFFF6DFA4), Color(0xFFC29A52), Color(0xFF5A4320),
        ),
        brassStops = floatArrayOf(0f, 0.25f, 0.45f, 0.7f, 1f),
        ink = Color(0xFF1D1A16),
        mute = rgba(29, 26, 22, 0.58f),
    )

    /** Warm light that rises inside the glass like a liquid (the alt source). */
    internal object Liquid {
        val top = rgba(255, 249, 232, 0.96f)
        val bottom = rgba(255, 186, 92, 0.92f)
        val meniscus = Color(0xFFFFFFFF)

        /** In the light room the warm sprite already glows; the liquid only tints it. */
        const val lightAlpha = 0.5f
        val glow = Color(0xFFFFA733)
        val filament = Color(0xFFFFC261)
        val core = Color(0xFFFFF8E6)
        val spill = rgba(255, 190, 100, 0.42f)
        val spillFar = rgba(255, 190, 100, 0f)
        val halo = rgba(255, 184, 92, 0.6f)
        val haloFar = rgba(255, 184, 92, 0f)
    }

    const val STAGE_WIDTH = 402.0

    /** Bulb, measured from the source frame f012 at 0.558 pt per px. Local frame: origin at the socket's top centre. */
    internal object Bulb {
        /** Of the screen height. */
        const val socketTopRatio = 0.265

        /** Pivot above the socket top. */
        const val wire = 700.0
        const val socketWidth = 56.0
        const val socketHeight = 50.0
        const val capWidth = 40.0
        const val capHeight = 6.0
        const val threads = 5
        const val neckTop = 46.0
        const val neckHalf = 25.0
        const val radius = 68.5
        const val centerY = 156.5
        const val bottom = 225.0
        const val stemHalf = 8.0
        const val stemEnd = 108.0
        const val armHalf = 30.0
        const val foot = 146.0
        const val wireWidth = 2.6
        const val halo = 170.0
    }

    internal object Cord {
        /** Of the width: between the bulb's widest swing and the right stile. */
        const val xRatio = 0.925
        const val top = -14.0

        /** Bead top at rest, of the height. */
        const val restYRatio = 0.56
        const val points = 18
        const val width = 2.6
        const val beadWidth = 15.0
        const val beadHeight = 36.0
        const val beadRadius = 7.5
        const val crimpWidth = 7.0
        const val crimpHeight = 6.0
        const val hit = 56.0
    }

    /** Neon glow of the filament: wide soft bloom, tight bloom, core. */
    internal object Glow {
        const val wide = 12.0
        const val wideBlur = 10.0
        const val wideAlpha = 0.6
        const val tight = 5.0
        const val tightBlur = 3.0
        const val core = 2.2

        /** Blue light filling the dark scene's glass. */
        const val innerAlpha = 0.5
    }

    internal object Panel {
        /** The source's panel edges at 12.5 % and 87.5 %. */
        const val insetRatio = 0.125
        const val bottomFromEnd = 36.0
        const val bevel = 3.0
        const val line = 1.5

        /** The lit lip of each stile. */
        const val lip = 4.0
        const val lipBlur = 3.0
    }

    internal object Type {
        const val margin = 32.0
        const val bottom = 48.0
        const val eyebrowSize = 12.0
        const val eyebrowTracking = 1.8
        const val titleSize = 48.0
        const val titleLine = 54.0
        const val hintSize = 15.0
        const val hintLine = 20.0
        const val gap = 8.0
    }

    internal object Copy {
        const val eyebrow = "APPEARANCE"
        const val dark = "Dark"
        const val light = "Light"
        const val hint = "Pull the cord to switch"
        const val a11yLabel = "Light cord"
        const val a11yHint = "Switches the app between dark and light theme"
    }

    /** Replica step (owner's fidelity gate), shipped value: off, the full upgrade shows. */
    const val REPLICA = false

    /** Source px to dp, and the crop's x offset in dp. */
    const val FIT_K = 2.048 / 3
    const val FIT_CROP_X = -134.0 / 3

    internal object ReplicaArt {
        // assets/wall.jpg was generated with its stiles at 148 / 1056 px; the source's are at 58 / 1148 px.
        const val wallX = (58 - 148 * 1.2) / 3
        const val wallW = (1206 * 1.2) / 3

        // assets/bulb-*.png: 480 x 720 px, socket top at (240, 30) px, glass 408 px wide.
        /** Template-matched against the three refs (1.25-1.27 sprite px per screen px). */
        const val spriteScale = 1.26 / 3
        const val spriteW = 480.0
        const val spriteH = 720.0
        const val anchorX = 240.0
        const val anchorY = 30.0

        /** Source px: socket top at 759 screen px in the refs. */
        const val socketTopSrc = 370.6

        /** Below the socket top, source px. */
        const val glassCenterSrc = 205.0

        /** Plus or minus source px at the glass: 169.5 screen px at the socket in the refs. */
        const val swingSrc = 82.0

        /** Swing centre at 619.5 screen px. */
        const val centreSrc = 367.9

        /** A tight soft glow close to the bulb, as in the source. */
        const val halo = 170.0
        val haloColor = rgba(40, 120, 240, 0.12f)
        val spill = rgba(20, 90, 255, 0.24f)

        /** Of the width. */
        const val spillR = 0.8

        /** Slower falloff, as in the refs. */
        val spillFall = listOf(rgba(30, 95, 235, 0.2f), rgba(30, 95, 235, 0.1f), rgba(30, 95, 235, 0f))
        val spillStops = floatArrayOf(0f, 0.55f, 1f)

        /** The lit room's faint blue reaching the far wall. */
        val ambient = listOf(rgba(40, 90, 210, 0.05f), rgba(40, 90, 210, 0.025f))

        /** Of the width. */
        const val ambientR = 1.6

        /** A touch this close to the glass centre swings the bulb. */
        const val grabRadius = 110.0

        /** The wire hangs from far above the frame, as in the source. */
        const val wire = 2000.0
        val wireColor = Color(0xFF2E3136)
        const val wireWidth = 6.0

        // The sprite's bright pixels already match the source (BGR 247,184,63 vs 243,179,62).
        // The glass interior must stay a clear dark blue. Skia matrices: 4 x 5, row-major, offsets in 0..1.
        val interior = floatArrayOf(
            1.12f, 0f, 0f, 0f, -0.05f,
            0f, 1.12f, 0f, 0f, -0.05f,
            0f, 0f, 1.12f, 0f, -0.05f,
            0f, 0f, 0f, 1f, 0f,
        )

        /** Tinted toward #1F7DFF so the bloom outside the glass is blue, not a white haze. */
        val brightPass = floatArrayOf(
            0.25f, 0f, 0f, 0f, -0.12f,
            0f, 1f, 0f, 0f, -0.5f,
            0f, 0f, 2f, 0f, -1f,
            0f, 0f, 0f, 1f, 0f,
        )
        const val bloom = 10.0
        const val bloomOpacity = 0.55

        /** QA: 0 while baking the light maps, 1 in use. */
        const val lightMap = 1

        /** The refs' unlit wall is about 10/10/10. */
        val wallTint = floatArrayOf(
            0.48f, 0f, 0f, 0f, 0f,
            0f, 0.6f, 0f, 0f, 0f,
            0f, 0f, 0.6f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )

        // Door stiles lit by the bulb, measured in the fitted reference (px).
        /** The door's outer edge and its stile lip, both lit. */
        val stileLeft = doubleArrayOf(28.0, 78.0)
        val stileRight = doubleArrayOf(1128.0, 1182.0)
        val stileGlow = doubleArrayOf(850.0, 1300.0, 1800.0)
        val stileColor = rgba(36, 104, 210, 0.85f)
        val railY = doubleArrayOf(2498.0, 2524.0)
        val railColor = rgba(18, 52, 120, 0.3f)
    }
}
