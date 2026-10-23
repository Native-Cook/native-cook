package com.ngocdevv.nativecook.blocks.lists.donationfancarousel

import androidx.compose.ui.graphics.Color

/**
 * Colours, measurements and fan values of the React Native block: `theme.ts`, plus the values its
 * `StyleSheet`s and `ReactionMarks.tsx` write inline. Lengths are dp of a 402 dp wide screen and
 * are multiplied by the stage scale where the React Native code multiplies by `scale`.
 */
internal object DonationFanCarouselTheme {
    /** `colors` in `theme.ts`. */
    object Colors {
        val Background = Color(0xFFFFFFFF)
        val Ink = Color(0xFF070707)
        val DetailAmount = Color(0xFF303030)
        val Secondary = Color(0xFF8F8F8F)
        val Accent = Color(0xFFC8F77D)
        val AccentDark = Color(0xFF99CE4A)
        val Heart = Color(0xFFF43D43)
        val HeartDark = Color(0xFFB81421)

        /** `styles.card.backgroundColor`. */
        val CardFill = Color(0xFFE8E8E8)

        /** `styles.card.shadowColor` and `shadowOpacity`. */
        val CardShadow = Color(0xFF171717).copy(alpha = 0.13f)

        /** `styles.heart.shadowColor` and `shadowOpacity`. */
        val HeartShadow = Color(0xFF8B0F18).copy(alpha = 0.22f)

        /** `styles.coin.shadowColor` and `shadowOpacity`. */
        val CoinShadow = Color(0xFF4A691E).copy(alpha = 0.22f)

        /** Gradient stop at offset 0 of the heart (`#FF8B8E`). */
        val HeartLight = Color(0xFFFF8B8E)

        /** Stroke of the heart's highlight (`#FFD2D2`). */
        val HeartHighlight = Color(0xFFFFD2D2)

        /** `styles.coinInner.borderColor` (`rgba(255,255,255,0.72)`). */
        val CoinInnerBorder = Color(1f, 1f, 1f, 0.72f)

        /** `styles.seed.backgroundColor` (`rgba(255,255,255,0.75)`). */
        val CoinSeed = Color(1f, 1f, 1f, 0.75f)
    }

    /** `layout` in `theme.ts`, measured from the 748 x 462 source frame. */
    object Layout {
        const val SourceWidth = 748f

        /** The 402 dp screen the numbers are normalised to (`scale = stageWidth / 402`). */
        const val ReferenceWidth = 402f

        /** Stage = window width - 16, with the source frame's 462 / 748 aspect. */
        const val StageInset = 16f
        const val StageAspectHeight = 462f

        object Header {
            const val Top = 22.3f
            const val Side = 59f
            const val FontSize = 15f
            const val CounterSize = 12.5f
            const val LineHeight = 19f

            /** `styles.counter.top`, a relative offset that is not scaled. */
            const val CounterTop = -1.3f
            const val HeadingTracking = -0.35f
            const val CounterTracking = -0.25f
        }

        object Deck {
            const val CenterX = 199.3f
            const val CenterY = 121f
            const val Card = 134.7f
            const val Radius = 9f
            const val Border = 1.2f

            /** `styles.card.shadowRadius` and `shadowOffset.height`. */
            const val ShadowRadius = 4f
            const val ShadowOffsetY = 2f

            /** `styles.activeBorder.zIndex`. */
            const val ActiveBorderZ = 130f
        }

        object Metadata {
            const val Top = 203f
            const val NameSize = 9.3f
            const val DetailSize = 9f
            const val NameLine = 11.7f
            const val DetailLine = 12f
            const val NameTracking = -0.1f
            const val DetailTracking = -0.08f
        }
    }

    /** `fan` in `theme.ts`: one value per distance 0, 1, 2 and 3 from the active portrait. */
    object Fan {
        val X = doubleArrayOf(0.0, 64.0, 87.0, 94.0)
        val Y = doubleArrayOf(0.0, 1.0, 3.0, 5.0)
        val Scale = doubleArrayOf(1.0, 0.76, 0.63, 0.52)
        val Rotate = doubleArrayOf(0.0, 7.0, 9.0, 11.0)
    }

    /** The three hearts of `Heart` in `ReactionMarks.tsx`. */
    object Hearts {
        class Placement(
            val x: Float,
            val y: Float,
            val rotate: Float,
            val size: Float,
            val rise: Float,
        )

        val Placements = listOf(
            Placement(x = 45f, y = -59f, rotate = 13f, size = 24f, rise = 16f),
            Placement(x = -54f, y = -15f, rotate = -10f, size = 25f, rise = 9f),
            Placement(x = 16f, y = 34f, rotate = 16f, size = 28f, rise = 13f),
        )

        /** `amount * 1.35 - index * 0.08`. */
        const val StaggerGain = 1.35
        const val StaggerStep = 0.08

        /** `interpolate(delayed, [0, 1], [0.72, 1])`. */
        const val ScaleFrom = 0.72
        const val ShadowRadius = 3f
        const val ShadowOffsetY = 2f

        /** `viewBox="0 0 32 30"`. */
        const val ViewBoxWidth = 32f
        const val ViewBoxHeight = 30f

        /** `strokeWidth="1.7"` of the highlight. */
        const val HighlightWidth = 1.7f

        /** Room around each heart so its shadow is not cut off by the layer it fades in. */
        const val Bleed = 12f
    }

    /** The coin of `ReactionMarks`. */
    object Coin {
        const val Size = 22f
        const val Border = 1f

        /** `interpolate(amount, [0, 1], [0.74, 1])`. */
        const val ScaleFrom = 0.74
        const val ShadowRadius = 4f
        const val ShadowOffsetY = 2f

        /** `coinInner`: 72 % of the coin's content box, a 1 dp border, radius 8 and 3 dp of padding. */
        const val InnerFraction = 0.72f
        const val InnerBorder = 1f
        const val InnerRadius = 8f
        const val InnerPaddingBottom = 3f
        const val InnerGap = 2f

        /** The three seeds: width 2, heights 4, 6 and 3.5, `borderRadius: 2` (not scaled). */
        const val SeedWidth = 2f
        val SeedHeights = floatArrayOf(4f, 6f, 3.5f)
        const val SeedRadius = 2f

        /** `t < 2500 ? 0 : 3` in `coinStyle`. */
        const val SecondHalfStartMs = 2500.0

        /** Room around the coin for its shadow. */
        const val Bleed = 12f
    }
}
