package com.ngocdevv.nativecook.blocks.cards.cityeventhero

import androidx.compose.ui.graphics.Color

internal object CityEventHeroTheme {
    const val REF_W = 1206f
    const val REF_H = 2622f
    const val REF_INSET = 186f
    const val REF_PT = 402f
    const val TOP_GROUP_MAX = 1000f
    const val ASCENT_SF = .953f
    const val ASCENT_HEADLINE = .93f

    object colors {
        val page = Color(0xFF000000)
        val white = Color(0xFFFFFFFF)
        val glassTop = Color(0x7C8A8A8A)
        val glassBottom = Color(0xBF636363)
        val wordOutline = Color(0xFF8A9FA7)
        val wordFill = Color(0xFF274450)
        val ink = Color(0xFFFFFFFF)
        val inkSoft = Color(0x99FFFFFF)
        val inkDate = Color(0x9EFFFFFF)
        val inkLabel = Color(0xABFFFFFF)
        val chevCard = Color(0x61FFFFFF)
        val chevView = Color(0xA3FFFFFF)
        val scrimTop = Color(0x9E04090B)
        val scrimTopClear = Color(0x0004090B)
        val ring = Color(0xFFFFFFFF)
        val heartFill = Color(0xFFFFFFFF)
        val burst = Color(0xFFFFFFFF)
        val sweep = Color(0x4DFFFFFF)
        val sweepSoft = Color(0x14FFFFFF)
        val sweepClear = Color(0x00FFFFFF)
        val mist = floatArrayOf(.8f, .9f, .93f)
    }

    object fonts {
        const val headline = "BricolageHeadline"
        const val wordmark = "ArchivoWordmark"
    }

    data class TextStyleSpec(
        val size: Float,
        val weight: Int,
        val color: Color,
        val ls: Float,
        val font: String? = null,
    )

    object type {
        val mute = TextStyleSpec(16f, 400, colors.inkSoft, .2f)
        val headline = TextStyleSpec(34.33f, 400, colors.ink, 0f, fonts.headline)
        val date = TextStyleSpec(15.85f, 400, colors.inkDate, .24f)
        val label = TextStyleSpec(13.85f, 400, colors.inkLabel, .2f)
        val title = TextStyleSpec(15.85f, 500, colors.ink, .2f)
        val pill = TextStyleSpec(14f, 500, colors.ink, .15f)
        val pillSmall = TextStyleSpec(13.85f, 500, colors.ink, .2f)
    }

    data class Box(val x: Float, val y: Float, val w: Float, val h: Float)

    object space {
        const val edge = 49f
        const val pill = 99f
        const val ring = 6f
    }

    object radius {
        const val card = 75f
        const val glassCard = 48f
        const val thumb = 36f
    }

    data class Wordmark(val x: Float, val base: Float, val cap: Float, val outline: Float, val size: Float)
    data class Baseline(val x: Float, val base: Float)
    data class CenterBaseline(val cx: Float, val base: Float)
    data class Guest(val cx: Float, val cy: Float, val d: Float, val img: Int)
    data class RowItem(val x: Float, val w: Float)
    data class Band(val y0: Float, val y1: Float)
    data class Tab(val y: Float)
    data class SkyBand(val y0: Float)
    data class Crop(val x: Float, val y: Float, val size: Float)

    object layout {
        val card = Box(0f, 181f, 1206f, 2195f)
        val wordmark = Wordmark(63f, 306f, 52f, 11.5f, 75.8f)

        object topPills {
            val gift = Box(598f, 231f, 98f, 98f)
            val byDate = Box(720f, 230f, 314f, 100f)
            val search = Box(1059f, 231f, 98f, 98f)
        }

        val byDate = Baseline(835f, 295f)
        val guests = listOf(
            Guest(291f, 1539f, 106f, 1),
            Guest(915f, 1539f, 106f, 6),
            Guest(381f, 1533f, 131f, 0),
            Guest(826f, 1533f, 131f, 5),
            Guest(488f, 1526f, 155f, 2),
            Guest(718f, 1526f, 155f, 4),
            Guest(603f, 1520f, 183f, 3),
        )
        val mute = CenterBaseline(609f, 1692f)
        val headline = CenterBaseline(605f, 1803f)
        val date = CenterBaseline(609f, 1895f)
        val presented = Box(49f, 1957f, 1108f, 222f)
        val thumb = Box(86f, 1994f, 148f, 148f)
        val presentedBy = Baseline(274f, 2053f)
        val presentedTitle = Baseline(275f, 2114f)
        val chevCard = Baseline(1070f, 2050f)
        val row = Box(0f, 2228f, 1206f, 99f)
        val like = RowItem(49f, 180f)
        val share = RowItem(254f, 98f)
        val more = RowItem(376f, 99f)
        val view = RowItem(787f, 370f)
        val likeCount = Baseline(163f, 2292f)
        val viewText = Baseline(838f, 2292f)
        val tab = Tab(2461f)

        object bearing {
            const val headline = 5f
            const val mute = 2f
            const val date = 3f
            const val label = 4f
            const val title = 4f
            const val pill = 3f
        }

        val skyBand = SkyBand(345f)
        val guestBand = Band(1400f, 1720f)
        const val burstHalf = 70f
        const val countInset = 18f
        const val photoShift = .57f
        val thumbCrop = Crop(180f, 300f, 500f)
    }

    data class Knot(val y: Float, val color: Color)
    data class Glow(val cx: Float, val cy: Float, val sx: Float, val sy: Float, val color: Color)

    object backdrop {
        const val fadeFrom = 1450f
        const val solidFrom = 1760f
        val knots = listOf(
            Knot(1760f, Color(0xFF1A282A)),
            Knot(1860f, Color(0xFF151F20)),
            Knot(1960f, Color(0xFF111718)),
            Knot(2060f, Color(0xFF030F0F)),
            Knot(2160f, Color(0xFF00090C)),
            Knot(2260f, Color(0xFF000607)),
            Knot(2330f, Color(0xFF030705)),
            Knot(2372f, Color(0xFF060808)),
        )
        val glows = listOf(
            Glow(407f, 2031f, 239f, 205f, Color(0xFF381101)),
            Glow(1104f, 2084f, 269f, 600f, Color(0xFF200900)),
        )
        const val topFadeTo = 880f
    }

    object copy {
        const val wordmark = "TOWNIE"
        const val byDate = "By date"
        const val mute = "Amira and 17 more"
        const val headline = "Dubai Jan 2026"
        const val date = "Thu, 9:00 AM – 17 Jan, 11:45 PM  ·  Dubai"
        const val presentedBy = "Presented by"
        const val presentedTitle = "Dubai in Jan 2026"
        const val likes = "1"
        const val likesNext = "2"
        val names = listOf("Amira", "Kwame", "Mei", "Karim", "Priya", "Tomás", "Ingrid")
        const val moreSuffix = " and 17 more"
        const val view = "View event"
    }

    object night {
        val zenith = floatArrayOf(.025f, .045f, .11f)
        val horizon = floatArrayOf(.1f, .11f, .22f)
        val cityGlow = floatArrayOf(.34f, .17f, .09f)
        val afterglow = floatArrayOf(.22f, .08f, .12f)
        val buildings = floatArrayOf(.13f, .16f, .25f)
        val warm = floatArrayOf(1f, .74f, .42f)
        val cool = floatArrayOf(.8f, .88f, 1f)
        const val warmShare = .8f
        val bloom = floatArrayOf(1f, .62f, .32f)
        val flash = floatArrayOf(1f, .78f, .5f)
        val flare = floatArrayOf(1f, .9f, .72f)
        const val horizonY = 930f
        const val zenithY = 250f
        const val skyFrom = 940f
        const val lightGain = 1.5f
        const val edgeGain = 1.4f
        const val bloomGain = .55f
        const val flareCore = 9f
        const val flareHalo = 60f
        val cell = floatArrayOf(5f, 6f)
        val rim = Color(0xFFFFC977)
        val headlineGlow = Color(0xF2FFBE6E)
        const val headlineGlowRadius = 18f
    }

    object scene {
        val sunHigh = floatArrayOf(1f, .93f, .8f)
        val sunLow = floatArrayOf(1f, .52f, .24f)
        const val warm = .55f
        const val lift = .22f
        const val cloudLit = .8f
        const val horizonGlow = .55f
        const val glint = 1.5f
        const val glintPower = 60f
        const val rim = .4f
        const val shadow = .62f
        val shadowTint = floatArrayOf(.86f, .92f, 1f)
        const val ledOff = .28f
        const val ledGain = 1.35f
        const val ledField = .3f
        val fieldA = floatArrayOf(.02f, .62f, .72f)
        val fieldB = floatArrayOf(.22f, .14f, .62f)
        val ledText = floatArrayOf(1f, .84f, .52f)
        val ledHot = floatArrayOf(1f, .98f, .94f)
        val ledScan = floatArrayOf(.82f, .96f, 1f)
        val ledRing = floatArrayOf(1f, .78f, .36f)
        val spill = floatArrayOf(.5f, .72f, .85f)
        val spillText = floatArrayOf(1f, .78f, .45f)
        const val halo = .55f
        val smoke = floatArrayOf(.3f, .31f, .37f)
        const val smokeAlpha = .42f
        val title = Color(0xE6FFFFFF)
        val titleSoft = Color(0x9EFFFFFF)
    }
}

internal const val CityEventHeroRefWidth = CityEventHeroTheme.REF_W
internal const val CityEventHeroRefHeight = CityEventHeroTheme.REF_H
internal const val CityEventHeroRefInset = CityEventHeroTheme.REF_INSET
internal const val CityEventHeroTopGroupMax = CityEventHeroTheme.TOP_GROUP_MAX
internal const val LOOP = 10f
internal const val RESUME_AFTER = 2000L
internal const val RETURN_MS = 800L

internal val CityEventHeroPage = CityEventHeroTheme.colors.page
internal val CityEventHeroWhite = CityEventHeroTheme.colors.white
internal val CityEventHeroGlassTop = CityEventHeroTheme.colors.glassTop
internal val CityEventHeroGlassBottom = CityEventHeroTheme.colors.glassBottom
internal val CityEventHeroOutline = CityEventHeroTheme.colors.wordOutline
internal val CityEventHeroWordmark = CityEventHeroTheme.colors.wordFill
internal val CityEventHeroSoftInk = CityEventHeroTheme.colors.inkSoft
internal val CityEventHeroDateInk = CityEventHeroTheme.colors.inkDate
internal val CityEventHeroLabelInk = CityEventHeroTheme.colors.inkLabel
internal val CityEventHeroChev = CityEventHeroTheme.colors.chevCard
internal val CityEventHeroViewChev = CityEventHeroTheme.colors.chevView
internal val CityEventHeroHeartFill = CityEventHeroTheme.colors.heartFill
internal val CityEventHeroBurstInk = CityEventHeroTheme.colors.burst
internal val CityEventHeroSweep = CityEventHeroTheme.colors.sweep
internal val CityEventHeroSweepSoft = CityEventHeroTheme.colors.sweepSoft
internal val CityEventHeroNightRim = CityEventHeroTheme.night.rim

internal typealias CityEventHeroBox = CityEventHeroTheme.Box
internal typealias CityEventHeroGuest = CityEventHeroTheme.Guest

internal val CityEventHeroCard = CityEventHeroTheme.layout.card
internal val CityEventHeroGift = CityEventHeroTheme.layout.topPills.gift
internal val CityEventHeroDatePill = CityEventHeroTheme.layout.topPills.byDate
internal val CityEventHeroSearch = CityEventHeroTheme.layout.topPills.search
internal val CityEventHeroPresented = CityEventHeroTheme.layout.presented
internal val CityEventHeroThumb = CityEventHeroTheme.layout.thumb
internal val CityEventHeroLike = CityEventHeroBox(49f, 2228f, 180f, 99f)
internal val CityEventHeroShare = CityEventHeroBox(254f, 2228f, 98f, 99f)
internal val CityEventHeroMore = CityEventHeroBox(376f, 2228f, 99f, 99f)
internal val CityEventHeroView = CityEventHeroBox(787f, 2228f, 370f, 99f)
internal val CityEventHeroGuests = CityEventHeroTheme.layout.guests
internal val CityEventHeroNames = CityEventHeroTheme.copy.names
