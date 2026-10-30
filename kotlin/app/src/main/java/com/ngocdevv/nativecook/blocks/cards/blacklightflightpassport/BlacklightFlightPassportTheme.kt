package com.ngocdevv.nativecook.blocks.cards.blacklightflightpassport

/** The reference is 402 × 874. Width scales uniformly; excess height is centred. */
internal object BlacklightFlightPassportTheme {
    const val REF_W = 402.0
    const val REF_H = 874.0
    data class Palette(
        val bg: Int, val cover: Int, val page: Int, val page2: Int, val map: Int,
        val arc: Int, val airport: Int, val ink: Int, val inkSoft: Int, val text: Int,
        val grey: Int, val button: Int, val buttonInk: Int, val chrome: Int,
        val chromeInk: Int, val header: Int, val headerSub: Int,
    )
    val LIGHT = Palette(
        bg = 0xf3f2ee,
        cover = 0x13245e,
        page = 0xeef2ec,
        page2 = 0xe6eeea,
        map = 0xa6d2ba,
        arc = 0xb4642e,
        airport = 0xd8433a,
        ink = 0x1c2c6e,
        inkSoft = 0x2f43a0,
        text = 0x202226,
        grey = 0x6b6f7a,
        button = 0xffffff,
        buttonInk = 0x16171a,
        chrome = 0xffffff,
        chromeInk = 0x16171a,
        header = 0x16171a,
        headerSub = 0x7a7d86,
    )
    val UV = Palette(
        bg = 0x090a2a,
        cover = 0x1a1460,
        page = 0xb9b9f6,
        page2 = 0xe6c8f6,
        map = 0xf27fe6,
        arc = 0x3f2aa3,
        airport = 0x7a3cf0,
        ink = 0x1d2470,
        inkSoft = 0x3a3fb0,
        text = 0x20214a,
        grey = 0x4e4f8a,
        button = 0x3b1f98,
        buttonInk = 0xffffff,
        chrome = 0xffffff,
        chromeInk = 0xffffff,
        header = 0xffffff,
        headerSub = 0xb9b8e8,
    )
    const val GLOW_LINE = 0x5ff3ff
    const val STAMP = 0xff7ad9
    const val BG_GLOW = 0x3a23b8
    object UI {
        const val CARD_X = 31.0
        const val CARD_Y = 188.0
        const val CARD_W = 341.0
        const val CARD_H = 452.0
        const val CARD_R = 14.0
        const val BAND = 10.0
        const val BAND_BOTTOM = 12.0
        const val BUTTON_X = 149.0
        const val BUTTON_Y = 666.0
        const val BUTTON_W = 102.0
        const val BUTTON_H = 31.0
        const val CLOSE_X = 40.0
        const val LAYOUT_X = 362.0
        const val CHROME_Y = 89.0
        const val CHROME_R = 23.0
        const val TITLE_Y = 81.0
        const val SUB_Y = 99.0
        val SHARE_X = doubleArrayOf(86.0, 201.0, 315.0)
        const val SHARE_Y = 796.0
        const val SHARE_LABEL_Y = 820.0
        const val SHARE_SIZE = 26.0
    }
    object Page {
        const val PLANES_Y = 26.0
        const val MAP_Y = 34.0
        const val MAP_H = 176.0
        const val FLAGS_Y = 214.0
        const val RULE_Y = 228.0
        const val BRAND_X = 13.0
        const val BRAND_Y = 248.0
        const val KINDS_X = 26.0
        const val KINDS_Y = 263.0
        const val LOGO_X = 316.0
        const val LOGO_Y = 248.0
        const val LOGO_SIZE = 22.0
        const val BIG_X = 13.0
        const val BIG_Y = 299.0
        const val BIG_SIZE = 34.0
        const val WORD_X = 13.0
        const val WORD_Y = 333.0
        const val WORD_SIZE = 27.0
        const val ROW_X = 124.0
        const val ROW_SIZE = 10.5
        val ROW_Y = doubleArrayOf(291.0, 308.0, 325.0, 342.0)
        val STAT_X = doubleArrayOf(13.0, 140.0, 225.0, 300.0)
        const val STAT_LABEL_Y = 367.0
        const val STAT_VALUE_Y = 384.0
        const val MRZ_X = 13.0
        const val MRZ_SIZE = 10.3
        val MRZ_Y = doubleArrayOf(414.0, 426.0)
    }
    object Copy {
        const val TITLE = "Your All-Time Passport"
        const val SUB = "View and Share"
        const val BRAND = "MY CONTRAIL PASSPORT"
        const val KINDS = "PASSPORT • PASS • PASAPORTE"
        const val FLIGHTS = "flights"
        const val BUTTON = "Blacklight"
        const val STAMP = "VERIFIED TRAVELLER · 2026"
        val ROWS = listOf("Authority" to "Contrail", "Place of issue" to "SFO", "Date of issue" to "28 JUN 26", "Member since" to "26 JUN 26")
        val STATS = listOf("Distance", "Flight Time", "Airports", "Airlines")
        val MRZ = listOf("ALLTIME<<SAM<<RIVERA<<MEMBER26JUN26<<@CONTRAIL<<<<", "ISSUED28JUN26SFO<<<<<<<<<<<<<<<<<<<<<<CONTRAIL.APP")
        val SHARE = listOf("Messages", "Photos", "More")
    }
    val AIRPORTS = mapOf(
        "SFO" to (-122.4 to 37.6),
        "LAX" to (-118.4 to 33.9),
        "SEA" to (-122.3 to 47.4),
        "JFK" to (-73.8 to 40.6),
        "MEX" to (-99.1 to 19.4),
        "GRU" to (-46.5 to -23.4),
        "HNL" to (-157.9 to 21.3),
        "LHR" to (-0.45 to 51.5),
        "CDG" to (2.5 to 49.0),
        "FCO" to (12.2 to 41.8),
        "IST" to (28.8 to 41.3),
        "DXB" to (55.4 to 25.2),
        "DEL" to (77.1 to 28.6),
        "BKK" to (100.7 to 13.7),
        "SIN" to (103.9 to 1.3),
        "HKG" to (113.9 to 22.3),
        "ICN" to (126.4 to 37.5),
        "NRT" to (140.4 to 35.8),
        "SYD" to (151.2 to -33.9),
        "JNB" to (28.2 to -26.1),
        "KEF" to (-22.6 to 64.0),
    )
    val ROUTES = listOf(
        Triple("SFO", "NRT", 0.22),
        Triple("SFO", "ICN", 0.2),
        Triple("SFO", "JFK", 0.18),
        Triple("JFK", "LHR", 0.22),
        Triple("LHR", "CDG", 0.4),
        Triple("CDG", "FCO", 0.3),
        Triple("FCO", "IST", 0.3),
        Triple("IST", "DXB", 0.25),
        Triple("DXB", "DEL", 0.25),
        Triple("DEL", "BKK", 0.25),
        Triple("BKK", "SIN", 0.3),
        Triple("SIN", "SYD", 0.22),
        Triple("HKG", "NRT", 0.3),
        Triple("SFO", "HNL", 0.2),
        Triple("LAX", "MEX", 0.25),
        Triple("MEX", "GRU", 0.22),
        Triple("JFK", "KEF", 0.25),
        Triple("DXB", "JNB", 0.18),
    )
    val FLAGS = listOf(
        'h' to intArrayOf(0xb22234, 0xffffff, 0xb22234, 0x3c3b6e),
        'v' to intArrayOf(0x009246, 0xffffff, 0xce2b37),
        'd' to intArrayOf(0xffffff, 0xbc002d),
        'd' to intArrayOf(0xffffff, 0xc60c30),
        'h' to intArrayOf(0x000000, 0xdd0000, 0xffce00),
        'v' to intArrayOf(0x002395, 0xffffff, 0xed2939),
        'h' to intArrayOf(0x00732f, 0xffffff, 0x000000),
        'v' to intArrayOf(0x006847, 0xffffff, 0xce1126),
        'd' to intArrayOf(0x009c3b, 0xffdf00),
        'h' to intArrayOf(0xed1c24, 0xffffff, 0x241d4f, 0xffffff, 0xed1c24),
        'd' to intArrayOf(0xe30a17, 0xffffff),
        'h' to intArrayOf(0x012169, 0xc8102e, 0x012169),
        'd' to intArrayOf(0x00247d, 0xffffff),
    )
}
