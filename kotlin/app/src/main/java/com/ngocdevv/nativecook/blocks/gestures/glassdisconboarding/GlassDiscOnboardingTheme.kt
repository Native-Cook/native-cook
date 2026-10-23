package com.ngocdevv.nativecook.blocks.gestures.glassdisconboarding

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

/** Compose-ready aliases backed entirely by Tokens.kt. */
internal object GlassDiscOnboardingTheme {
    object COLOR {
        val bgTL = colorToken(GlassDiscOnboardingTokens.COLOR.bgTL)
        val bgTR = colorToken(GlassDiscOnboardingTokens.COLOR.bgTR)
        val bgBL = colorToken(GlassDiscOnboardingTokens.COLOR.bgBL)
        val bgBR = colorToken(GlassDiscOnboardingTokens.COLOR.bgBR)
        val ink = colorToken(GlassDiscOnboardingTokens.COLOR.ink)
        val inkSoft = colorToken(GlassDiscOnboardingTokens.COLOR.inkSoft)
        val hint = colorToken(GlassDiscOnboardingTokens.COLOR.hint)
        val lensInk = colorToken(GlassDiscOnboardingTokens.COLOR.lensInk)
        val shadow = colorToken(GlassDiscOnboardingTokens.COLOR.shadow)
        val buttonLight = colorToken(GlassDiscOnboardingTokens.COLOR.buttonLight)
        val buttonLightEdge = colorToken(GlassDiscOnboardingTokens.COLOR.buttonLightEdge)
        val buttonLightShadow = colorToken(GlassDiscOnboardingTokens.COLOR.buttonLightShadow)
        val buttonLightInk = colorToken(GlassDiscOnboardingTokens.COLOR.buttonLightInk)
        val buttonDark = colorToken(GlassDiscOnboardingTokens.COLOR.buttonDark)
        val buttonDarkInk = colorToken(GlassDiscOnboardingTokens.COLOR.buttonDarkInk)
        val finger = colorToken(GlassDiscOnboardingTokens.COLOR.finger)
        val fingerRing = colorToken(GlassDiscOnboardingTokens.COLOR.fingerRing)
        val ringInner = colorToken(GlassDiscOnboardingTokens.COLOR.ringInner)
        val ringCore = colorToken(GlassDiscOnboardingTokens.COLOR.ringCore)
        val ringOuter = colorToken(GlassDiscOnboardingTokens.COLOR.ringOuter)
        val ringBlush = colorToken(GlassDiscOnboardingTokens.COLOR.ringBlush)
        val pulse = colorToken(GlassDiscOnboardingTokens.COLOR.pulse)
        val fieldA = colorToken(GlassDiscOnboardingTokens.COLOR.fieldA)
        val fieldB = colorToken(GlassDiscOnboardingTokens.COLOR.fieldB)
    }

    const val FONT = GlassDiscOnboardingTokens.FONT

    object TYPE {
        object intro {
            const val fontSize = GlassDiscOnboardingTokens.TYPE.introFontSize
            const val lineHeight = GlassDiscOnboardingTokens.TYPE.introLineHeight
            const val fontWeight = GlassDiscOnboardingTokens.TYPE.introFontWeight
            const val letterSpacing = GlassDiscOnboardingTokens.TYPE.introLetterSpacing
            val color = COLOR.ink
            const val textAlign = GlassDiscOnboardingTokens.TYPE.introTextAlign
        }

        object hint {
            const val fontSize = GlassDiscOnboardingTokens.TYPE.hintFontSize
            const val fontWeight = GlassDiscOnboardingTokens.TYPE.hintFontWeight
            const val letterSpacing = GlassDiscOnboardingTokens.TYPE.hintLetterSpacing
            val color = COLOR.hint
            const val textAlign = GlassDiscOnboardingTokens.TYPE.hintTextAlign
        }

        const val headline = GlassDiscOnboardingTokens.TYPE.headline
        const val headlineLine = GlassDiscOnboardingTokens.TYPE.headlineLine
        const val headlineTracking = GlassDiscOnboardingTokens.TYPE.headlineTracking

        object button {
            const val fontSize = GlassDiscOnboardingTokens.TYPE.buttonFontSize
            const val letterSpacing = GlassDiscOnboardingTokens.TYPE.buttonLetterSpacing
        }

        const val lens = GlassDiscOnboardingTokens.TYPE.lens
    }

    object SPACE {
        const val xs = GlassDiscOnboardingTokens.SPACE.xs
        const val sm = GlassDiscOnboardingTokens.SPACE.sm
        const val md = GlassDiscOnboardingTokens.SPACE.md
        const val lg = GlassDiscOnboardingTokens.SPACE.lg
        const val xl = GlassDiscOnboardingTokens.SPACE.xl
        const val xxl = GlassDiscOnboardingTokens.SPACE.xxl
    }

    object LAYOUT {
        const val domeRadius = GlassDiscOnboardingTokens.LAYOUT.domeRadius
        const val domeTop = GlassDiscOnboardingTokens.LAYOUT.domeTop
        val pullT = GlassDiscOnboardingTokens.LAYOUT.pullT
        val pullR = GlassDiscOnboardingTokens.LAYOUT.pullR
        val pullY = GlassDiscOnboardingTokens.LAYOUT.pullY
        val pullX = GlassDiscOnboardingTokens.LAYOUT.pullX

        object lens {
            const val fold = GlassDiscOnboardingTokens.LAYOUT.lens.fold
            const val foldBack = GlassDiscOnboardingTokens.LAYOUT.lens.foldBack
            const val foldShape = GlassDiscOnboardingTokens.LAYOUT.lens.foldShape
            const val kY = GlassDiscOnboardingTokens.LAYOUT.lens.kY
            const val kX = GlassDiscOnboardingTokens.LAYOUT.lens.kX
            const val squeezeX = GlassDiscOnboardingTokens.LAYOUT.lens.squeezeX
            const val tx = GlassDiscOnboardingTokens.LAYOUT.lens.tx
            const val ty = GlassDiscOnboardingTokens.LAYOUT.lens.ty
            const val spread = GlassDiscOnboardingTokens.LAYOUT.lens.spread
            const val fringeFrom = GlassDiscOnboardingTokens.LAYOUT.lens.fringeFrom
            const val fade = GlassDiscOnboardingTokens.LAYOUT.lens.fade
            const val mirrorFloor = GlassDiscOnboardingTokens.LAYOUT.lens.mirrorFloor
        }

        const val rimLight = GlassDiscOnboardingTokens.LAYOUT.rimLight
        const val rimWidth = GlassDiscOnboardingTokens.LAYOUT.rimWidth
        const val shadowAt = GlassDiscOnboardingTokens.LAYOUT.shadowAt
        const val shadowWidth = GlassDiscOnboardingTokens.LAYOUT.shadowWidth
        const val shadowAlpha = GlassDiscOnboardingTokens.LAYOUT.shadowAlpha

        object bead {
            const val from = GlassDiscOnboardingTokens.LAYOUT.bead.from
            const val span = GlassDiscOnboardingTokens.LAYOUT.bead.span
            const val frost = GlassDiscOnboardingTokens.LAYOUT.bead.frost
        }

        const val domeFrost = GlassDiscOnboardingTokens.LAYOUT.domeFrost
        const val ringFrom = GlassDiscOnboardingTokens.LAYOUT.ringFrom
        const val ringTo = GlassDiscOnboardingTokens.LAYOUT.ringTo
        const val hintY = GlassDiscOnboardingTokens.LAYOUT.hintY
        const val introBaseline = GlassDiscOnboardingTokens.LAYOUT.introBaseline
        const val headlineBaseline = GlassDiscOnboardingTokens.LAYOUT.headlineBaseline
        const val beadY = GlassDiscOnboardingTokens.LAYOUT.beadY
        const val beadRadius = GlassDiscOnboardingTokens.LAYOUT.beadRadius
        const val beadRestY = GlassDiscOnboardingTokens.LAYOUT.beadRestY
        const val beadRestRadius = GlassDiscOnboardingTokens.LAYOUT.beadRestRadius
        const val backRadius = GlassDiscOnboardingTokens.LAYOUT.backRadius
        const val buttonsBottom = GlassDiscOnboardingTokens.LAYOUT.buttonsBottom
        const val buttonHeight = GlassDiscOnboardingTokens.LAYOUT.buttonHeight
        const val buttonHeightDark = GlassDiscOnboardingTokens.LAYOUT.buttonHeightDark
        const val buttonGap = GlassDiscOnboardingTokens.LAYOUT.buttonGap
        const val gutter = GlassDiscOnboardingTokens.LAYOUT.gutter
        const val buttonIcon = GlassDiscOnboardingTokens.LAYOUT.buttonIcon
        const val buttonIconInset = GlassDiscOnboardingTokens.LAYOUT.buttonIconInset
        const val fingerRadius = GlassDiscOnboardingTokens.LAYOUT.fingerRadius
    }

    object CLOUD_FRAME {
        const val width = GlassDiscOnboardingTokens.CLOUD_FRAME.width
        const val height = GlassDiscOnboardingTokens.CLOUD_FRAME.height
    }

    val CLOUD = GlassDiscOnboardingTokens.CLOUD.toList()

    object ATLAS {
        const val cell = GlassDiscOnboardingTokens.ATLAS.cell
        const val columns = GlassDiscOnboardingTokens.ATLAS.columns
        const val rows = GlassDiscOnboardingTokens.ATLAS.rows
    }

    object COPY {
        val intro = GlassDiscOnboardingTokens.COPY.intro
        const val hint = GlassDiscOnboardingTokens.COPY.hint
        const val lens = GlassDiscOnboardingTokens.COPY.lens
        const val headline = GlassDiscOnboardingTokens.COPY.headline
        val sub = GlassDiscOnboardingTokens.COPY.sub
        const val email = GlassDiscOnboardingTokens.COPY.email
        const val phone = GlassDiscOnboardingTokens.COPY.phone
        const val back = GlassDiscOnboardingTokens.COPY.back
        const val enter = GlassDiscOnboardingTokens.COPY.enter
    }
}

private fun colorToken(value: String): Color {
    if (value.startsWith("#")) {
        return Color(android.graphics.Color.parseColor(value))
    }
    val components = value.removePrefix("rgba(").removeSuffix(")").split(',')
    require(components.size == 4) { "Unsupported color token: $value" }
    return Color(
        red = components[0].toInt(),
        green = components[1].toInt(),
        blue = components[2].toInt(),
        alpha = (components[3].toFloat() * 255f).roundToInt(),
    )
}
