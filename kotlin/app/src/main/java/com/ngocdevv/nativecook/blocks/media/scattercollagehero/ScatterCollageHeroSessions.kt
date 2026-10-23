package com.ngocdevv.nativecook.blocks.media.scattercollagehero

enum class CollageShape { Portrait, Landscape, Square }

/**
 * One practice in the library: a photo in the collage, and the hero it opens into.
 *
 * @param photo Unsplash photo id.
 * @param shape Orientation of the photo, which sets the tile's shape in the collage.
 * @param title The hero's title, on two lines; the second is set in italic.
 * @param date Day the session was published, shown as "25 Apr".
 * @param category One of [CollageCategories] after "All".
 */
data class CollageSession(
    val photo: String,
    val shape: CollageShape,
    val title: String,
    val titleItalic: String,
    val guide: String,
    val minutes: Int,
    val date: String,
    val category: String,
)

/** The chips under the collage; "All" shows every tile, the others dim the rest. */
val CollageCategories = listOf("All", "Meditation", "Yoga", "Breath", "Sleep")

/** One line under the title, by category. */
internal val CollageBlurbs = mapOf(
    "Meditation" to "Settle on a single point and let everything else drift past, a little slower each time.",
    "Yoga" to "A slow sequence that lengthens the spine and steadies the breath between each hold.",
    "Breath" to "Four counts in, six counts out, until the shoulders drop and the body follows.",
    "Sleep" to "Lower the lights, soften the jaw and let the day unhook itself, one thought at a time.",
)

/** Photo URL at a width in pixels, cropped to the photo's own aspect. */
internal fun collagePhotoUrl(id: String, width: Int) = "https://images.unsplash.com/$id?auto=format&fit=crop&w=$width&q=80"

/**
 * Twenty-four sessions. Their order is the order they are born in the collage, so neighbours
 * here are kept unlike each other (the two sunset photos sit far apart).
 */
val CollageSessions = listOf(
    CollageSession("photo-1506126613408-eca07ce68773", CollageShape.Portrait, "Golden", "Hour", "Mira Okafor", 20, "25 Apr", "Meditation"),
    CollageSession("photo-1791188012967-d34fdec9219d", CollageShape.Portrait, "Salt", "Balance", "Theo Marlow", 35, "24 Apr", "Yoga"),
    CollageSession("photo-1546458887-b4d5d7e7a00b", CollageShape.Portrait, "Stacked", "Calm", "Noa Lindqvist", 12, "24 Apr", "Sleep"),
    CollageSession("photo-1552196563-55cd4e45efb3", CollageShape.Landscape, "Grounded", "Breath", "Ines Albrecht", 15, "23 Apr", "Breath"),
    CollageSession("photo-1562088287-bde35a1ea917", CollageShape.Portrait, "Eagle", "Twist", "June Okada", 40, "22 Apr", "Yoga"),
    CollageSession("photo-1765490526583-4bf7f007096f", CollageShape.Landscape, "Blue", "Hour", "Sol Ramírez", 25, "22 Apr", "Sleep"),
    CollageSession("photo-1591228127791-8e2eaef098d3", CollageShape.Landscape, "Tidal", "Mudra", "Mira Okafor", 10, "21 Apr", "Meditation"),
    CollageSession("photo-1560233026-ad254fa8da38", CollageShape.Portrait, "Lilac", "Arch", "Theo Marlow", 30, "20 Apr", "Yoga"),
    CollageSession("photo-1602192509154-0b900ee1f851", CollageShape.Landscape, "Open", "Field", "Ines Albrecht", 18, "19 Apr", "Breath"),
    CollageSession("photo-1616074735081-0a3a0a79600d", CollageShape.Portrait, "Ritual", "Flame", "Noa Lindqvist", 22, "18 Apr", "Sleep"),
    CollageSession("photo-1447452001602-7090c7ab2db3", CollageShape.Landscape, "Tree", "Pose", "June Okada", 28, "18 Apr", "Yoga"),
    CollageSession("photo-1722094250550-4993fa28a51b", CollageShape.Landscape, "Lake", "Stillness", "Sol Ramírez", 16, "17 Apr", "Meditation"),
    CollageSession("photo-1554244933-d876deb6b2ff", CollageShape.Square, "Heart", "Centre", "Mira Okafor", 24, "16 Apr", "Yoga"),
    CollageSession("photo-1778159242575-3f2c0b02212d", CollageShape.Portrait, "Coastal", "Pause", "Ines Albrecht", 8, "15 Apr", "Breath"),
    CollageSession("photo-1600618528240-fb9fc964b853", CollageShape.Landscape, "Palm", "Shade", "Theo Marlow", 20, "15 Apr", "Meditation"),
    CollageSession("photo-1611800065908-233b597db552", CollageShape.Landscape, "Evening", "Ritual", "Noa Lindqvist", 32, "14 Apr", "Sleep"),
    CollageSession("photo-1556816723-1ce827b9cfbb", CollageShape.Landscape, "Dusk", "Dancer", "June Okada", 26, "13 Apr", "Yoga"),
    CollageSession("photo-1532798442725-41036acc7489", CollageShape.Landscape, "Quiet", "Hands", "Sol Ramírez", 14, "12 Apr", "Meditation"),
    CollageSession("photo-1764054655220-99548fc12fe3", CollageShape.Portrait, "Shore", "Cairn", "Ines Albrecht", 11, "11 Apr", "Breath"),
    CollageSession("photo-1695795910772-6336b0beba36", CollageShape.Landscape, "Misty", "Summit", "Mira Okafor", 45, "10 Apr", "Breath"),
    CollageSession("photo-1577344718665-3e7c0c1ecf6b", CollageShape.Landscape, "Inner", "Light", "Noa Lindqvist", 19, "9 Apr", "Sleep"),
    CollageSession("photo-1541588007165-da26f41a1996", CollageShape.Portrait, "First", "Light", "Theo Marlow", 21, "8 Apr", "Meditation"),
    CollageSession("photo-1782231722172-762f60de15a9", CollageShape.Landscape, "River", "Stones", "Sol Ramírez", 17, "7 Apr", "Sleep"),
    CollageSession("photo-1566501206188-5dd0cf160a0e", CollageShape.Portrait, "Soft", "Morning", "June Okada", 30, "6 Apr", "Meditation"),
)
