package com.ngocdevv.nativecook.blocks.sheets.savedplacesmap

import android.graphics.Color

/** Value-for-value rendering tokens from the accepted React Native theme.ts. */
internal object SavedPlacesMapTheme {
    const val REF_W = 1206f
    const val REF_PT = 402f

    object Colors {
        const val ink = 0xFF000000.toInt()
        const val meta = 0xFF6E7479.toInt()
        const val tileLabel = 0xFF474C4F.toInt()
        const val slash = 0xFF707479.toInt()
        const val tile0 = 0xFFEAF0F7.toInt()
        const val tile0Border = 0xFFCDD4DF.toInt()
        const val tile = 0xFFFDFFFF.toInt()
        const val tileShade = 0xFFEDEDF5.toInt()
        const val card = 0xFFFFFFFF.toInt()
        const val cardSub = 0xFF8E8E93.toInt()
        const val chip = 0xFFFDFCFE.toInt()
        const val chipShade = 0xFFEBE8EC.toInt()
        const val grabber = 0xFFC5C7C9.toInt()
        const val highlight = 0xFFF7FFF7.toInt()
        const val city = 0xFF101010.toInt()
        const val borough = 0xFF474858.toInt()
        const val water = 0xFF6EA1C1.toInt()
        const val faded = 0xFFA5A7A8.toInt()
        const val markerInk = 0xFF000000.toInt()
        const val markerSub = 0xFF2B2B2B.toInt()
        const val bookmark = 0xFF0E77EA.toInt()
        const val thumb = 0xFF8FCAFC.toInt()
        const val star = 0xFFFFBD20.toInt()
        const val locate = 0xFF0263FF.toInt()
        const val shieldBlue = 0xFF3C62AC.toInt()
        const val shieldRed = 0xFFC8514F.toInt()
        const val badge = 0xFF707274.toInt()
        const val white = 0xFFFFFFFF.toInt()
        const val checkbox = 0xFF8A8A8D.toInt()
        const val shadow = 0xFF4B7391.toInt()
        const val dim = 0xFF0E2233.toInt()
        const val grabberTaken = 0xFF9FA3A7.toInt()
        const val sheetTail = 0xFFDDEEF8.toInt()
    }

    object Space {
        const val margin = 16f
        const val cardMargin = 26f / 3f
        const val tileGap = 8f
        const val cardPad = 38f / 3f
    }

    object Radius {
        const val sheet = 84f
        const val card = 66f
        const val tile = 50f
        const val photoTop = 24f
        const val photoList = 36f
        const val badge = 14f
    }

    object Copy {
        const val title = "NYC cafes for chatting"
        const val places = "59 places"
        const val saves = "11 saves"
        const val views = "252 views"
        const val handle = "@ines.vale"
        const val visitedNum = "0"
        const val visitedNext = "1"
        const val visitedDen = "/59"
        const val visited = "you visited"
        const val save = "save list"
        const val share = "share list"
        const val similar = "similar lists"
        const val listTitle = "Mabel's Corner Café"
        const val listSub = "piano singalong"
        const val allPlaces = "all places"
        const val eat = "eat"
        const val cafe = "café"
        const val bar = "bar"
        // Screen reader.
        const val a11yBy = "by"
        const val a11ySheetHint = "Swipe up or down to expand or collapse the list."
        const val a11yExpanded = "expanded"
        const val a11yCollapsed = "collapsed"
        const val a11yOf = "of"
        const val a11yVisitHint = "Double-tap to mark Mabel's Corner Café visited."
        const val a11yVisitedBox = "Visited"
        const val a11yVisited = "visited"
        const val a11yMarkerHint = "Double-tap to highlight it on the map."
    }

    object Type {
        const val title = 66.7f
        const val meta = 42f
        const val handle = 36.8f
        const val visitedNum = 57f
        const val visitedDen = 57f
        const val visited = 39.5f
        const val tile = 40.5f
        const val listTitle = 54.1f
        const val listSub = 45.3f
        const val chip = 48.5f
    }

    object Sheet {
        const val top = 1548f
        const val grabberX = 526f
        const val grabberY = 1572f
        const val grabberW = 153f
        const val grabberH = 15f
    }

    object Tiles {
        const val y = 2068f
        const val h = 200f
        const val w = 259.5f
        val xs = floatArrayOf(48f, 331.5f, 615f, 898.5f)
        val baseline = floatArrayOf(2222f, 2219f, 2216f, 2217f)
        val centre = floatArrayOf(177.5f, 461.5f, 744f, 1027.5f)
    }

    data class Chip(
        val x: Float,
        val w: Float,
        val text: String,
        val textX: Float,
        val emoji: String?,
        val emojiX: Float,
        val globe: Boolean = false,
    )

    object Chips {
        const val y = 1440f
        const val h = 82f
        const val baseline = 1498f
        val items = listOf(
            Chip(41f, 365f, Copy.allPlaces, 120f, null, 62f, globe = true),
            Chip(446f, 184f, Copy.eat, 528f, "🍽️", 491f),
            Chip(666f, 211f, Copy.cafe, 749f, "☕", 712f),
            Chip(917f, 188f, Copy.bar, 999f, "🍸", 964f),
            Chip(1143f, 120f, "", 0f, "👜", 1180f),
        )
    }

    data class MapLabel(
        val text: String,
        val x: Float,
        val baseline: Float,
        val size: Float,
        val color: Int,
        val letterSpacing: Float = 0f,
        val alpha: Float = 1f,
        val centered: Boolean = false,
        val italic: Boolean = false,
    )

    val mapLabels = listOf(
        MapLabel("North Bergen", 195f, 262f, 41f, Colors.faded, alpha = 0.8f),
        MapLabel("Union City", 148f, 428f, 47.3f, Colors.city),
        MapLabel("Jersey City", 18f, 828f, 45.7f, Colors.city),
        MapLabel("MANHATTAN", 336f, 573f, 31f, Colors.borough, 5.2f),
        MapLabel("BROOKLYN", 537f, 1296f, 31f, Colors.borough, 5.2f),
        MapLabel("EAST NEW", 984.5f, 1180f, 31f, Colors.borough, 5.2f, centered = true),
        MapLabel("YORK", 984.5f, 1218f, 31f, Colors.borough, 5.2f, centered = true),
        MapLabel("Upper New", 0f, 1148f, 44f, Colors.water, 6.2f, italic = true),
        MapLabel("York Bay", 26f, 1208f, 44f, Colors.water, 6.2f, italic = true),
    )

    data class Marker(
        val key: String,
        val x: Float,
        val baseline: Float,
        val name: String,
        val sub: String,
        val tag: String,
        val emoji: String,
        val emojiSize: Float,
    )

    val markers = listOf(
        Marker("lumen", 845f, 444f, "Lumen Coffee", "coffee & pastries", "saved", "☕", 15.6f),
        Marker("mabel", 419f, 742.5f, "Mabel's Corner Café", "piano singalong", "saved", "🎭", 17f),
        Marker("linden", 500f, 946f, "Linden Wick", "pourover coffee", "saved", "☕", 15.6f),
        Marker("fern", 847f, 926f, "Pale Fern", "nordic roastery", "liked", "☕", 15.6f),
        Marker("aurelle", 564f, 1141f, "Cafe Aurelle", "parisian cafe", "saved", "☕", 15.6f),
    )

    object MarkerToken {
        const val disc = 100f
        const val discDx = 63f
        const val discDy = 12f
        const val iconDx = 36f
        const val bookmarkDy = 58f
        const val savedDy = 39f
        const val subDy = 31f
        const val nameSize = 29.9f
        const val subSize = 25.8f
        const val hitW = 240f
    }

    object Back {
        const val chevronX = 46f
        const val chevronY = 221f
        const val chevronW = 31f
        const val chevronH = 56f
        const val badgeX = 44f
        const val badgeY = 283f
        const val badgeW = 54f
        const val badgeH = 55f
    }

    object Locate {
        const val centerX = 1094f
        const val centerY = 1338f
        const val diameter = 152f
        const val arrowCenterX = 1092f
        const val arrowCenterY = 1335f
        const val arrowSize = 50f
    }

    object Shield {
        const val x = 918f
        const val y = 738f
        const val w = 74f
        const val h = 57f
        const val text = "495"
        const val size = 25f
    }

    object FadedChip {
        const val x = 1105f
        const val y = 190f
        const val w = 67f
        const val h = 42f
    }

    object Bubble {
        const val dx = -36f
        const val dy = -36f
        const val h = 45f
        const val digit = 17f
        const val padX = 12f
        const val ring = 2f
        const val size = 30f
    }

    object VisitBadge {
        const val dx = 32f
        const val dy = 32f
        const val d = 34f
        const val ring = 2f
    }

    object Tick {
        const val length = 22f
        const val stroke = 3.4f
    }

    object ListToken {
        const val cardH = 328f
        const val gap = 26f
    }

    data class MoreCard(
        val title: String,
        val sub: String,
        val photo: String,
        val zoom: Float,
        val ox: Float,
        val oy: Float,
    )

    val moreCards = listOf(
        MoreCard("Lumen Coffee", "coffee & pastries", "cafe", 1.9f, -0.38f, -0.42f),
        MoreCard("Linden Wick", "pourover coffee", "bar", 1.7f, -0.1f, -0.35f),
    )

    object Layout {
        const val mapH = 1650f
        const val photoX = 48f
        const val photoY = 1646f
        const val photoW = 306f
        const val photoH = 390f
        const val photoR = 24f
        const val titleX = 385f
        const val titleBase = 1760f
        const val metaBase = 1840f
        const val metaPinX = 386f
        const val metaPinY = 1808f
        const val metaPinW = 31f
        const val metaPinH = 39f
        const val metaPlacesX = 435f
        const val metaBookmarkX = 650f
        const val metaBookmarkY = 1808f
        const val metaBookmarkW = 29f
        const val metaBookmarkH = 39f
        const val metaSavesX = 699f
        const val metaBarsX = 895f
        const val metaBarsY = 1808f
        const val metaBarsW = 36f
        const val metaBarsH = 39f
        const val metaViewsX = 946f
        const val pillX = 386f
        const val pillY = 1893f
        const val pillW = 319f
        const val pillH = 91f
        const val avatarX = 397f
        const val avatarY = 1904f
        const val avatarD = 71f
        const val handleX = 485f
        const val handleBase = 1952f
        const val tile0NumX = 101f
        const val tile0DenX = 144f
        const val tile0NumBase = 2155f
        const val tile0LabelBase = 2222f
        val tileIcons = listOf(
            floatArrayOf(439f, 2112f, 45f, 51f),
            floatArrayOf(720f, 2112f, 51f, 51f),
            floatArrayOf(1002f, 2113f, 53f, 52f),
        )
        const val cardX = 26f
        const val cardY = 2343f
        const val cardW = 1156f
        const val listPhotoX = 64f
        const val listPhotoY = 2381f
        const val listPhotoD = 252f
        const val listPhotoR = 36f
        const val listTitleX = 357f
        const val listTitleBase = 2500f
        const val listSubX = 358f
        const val listSubBase = 2563f
        const val checkboxX = 360f
        const val checkboxY = 2598f
        const val checkboxD = 29f
        const val listAvatarX = 407f
        const val listAvatarY = 2598f
        const val listAvatarD = 48f
    }

    object Roll {
        const val x = 95f
        const val y = 2104f
        const val w = 52f
        const val h = 60f
        const val row = 60f
        const val centre = 125f
    }

    /** The pin's progressive blur (theme.ts topBlur): sigma is the measured Gaussian, tint what the BlurView's light tint adds. */
    object TopBlur {
        const val h = 330f
        const val solid = 120f
        const val sigma = 11f
        const val tint = 0.22f
        const val fadeH = 420f
        val fadeAlphas = floatArrayOf(0.42f, 0.24f, 0.1f, 0f)
        val fadeStops = floatArrayOf(0f, 280f / 420f, 370f / 420f, 1f)
    }

    /** The 3D state's look (theme.ts depth). */
    object Depth {
        const val skyTop = 0xFFE7EEF3.toInt()
        const val skyBottom = 0xFFEEF2F5.toInt()
        const val fogH = 560f
        val fogAlphas = floatArrayOf(1f, 0.85f, 0f)
        val fogStops = floatArrayOf(0f, 0.55f, 1f)
        const val fogR = 238
        const val fogG = 243
        const val fogB = 246
        const val stalkW = 9f
        const val stalkTop = 0xFFFFFFFF.toInt()
        const val stalkBottom = 0xFFD3DBE2.toInt()
        const val footD = 16f
        const val foot = 0xFF3E5568.toInt()
        val beamSaved = intArrayOf(14, 119, 234)
        val beamLiked = intArrayOf(96, 170, 240)
        const val shadow = 0xFF2E4153.toInt()
        const val rippleRing = 3f
    }

    val depthRank = mapOf("lumen" to 0, "mabel" to 1, "fern" to 2, "linden" to 3, "aurelle" to 4)

    const val VISITED_KEY = "mabel"
}
