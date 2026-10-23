package com.ngocdevv.nativecook.blocks.animations.depthfieldgallery

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class DepthGlyph { Search, Expand, Figure, Basket }

/** How a photo is printed: in black and white, or black on the accent. */
enum class DepthPrint { Mono, Lime }

/** What a card shows: a photo printed in black and white or in lime, or a piece of the identity. */
sealed interface DepthCard {
    val title: String
    val note: String

    /** A card with a photo on it: [uri] is held for the field, [full] fetched once the camera stops there. */
    sealed interface Pictured : DepthCard {
        val uri: String
        val full: String?
    }

    data class Photo(
        override val title: String,
        override val note: String,
        override val uri: String,
        override val full: String?,
        val print: DepthPrint,
    ) : Pictured

    data class Poster(
        override val title: String,
        override val note: String,
        override val uri: String,
        override val full: String?,
        val headline: Pair<String, String>,
    ) : Pictured

    data class Dots(override val title: String, override val note: String, val pattern: Int) : DepthCard

    data class Glyph(override val title: String, override val note: String, val glyph: DepthGlyph) : DepthCard

    data class Chip(override val title: String, override val note: String, val first: String, val second: String) : DepthCard

    data class Mark(override val title: String, override val note: String, val word: String) : DepthCard
}

private fun unsplash(id: String, w: Int) =
    "https://images.unsplash.com/$id?auto=format&fit=crop&w=$w&h=${Math.round(w * 1.25)}&q=82"

// Every photo is held at a size fit for the field; the card the camera stops at is fetched again
// at full size, so it is sharp across most of the screen without holding fifty large photos.
private fun depthPhoto(title: String, note: String, id: String, print: DepthPrint) =
    DepthCard.Photo(title, note, unsplash(id, 480), unsplash(id, 1000), print)

private fun depthPoster(title: String, note: String, id: String, first: String, second: String) =
    DepthCard.Poster(title, note, unsplash(id, 480), unsplash(id, 1000), first to second)

internal val DepthCards: List<DepthCard> = listOf(
    DepthCard.Dots("Signal", "Pattern, lime", 0),
    depthPhoto("Grey Room", "Portrait, black and white", "photo-1684598273432-8f8e882cd16f", DepthPrint.Mono),
    depthPhoto("Outline", "Profile, lime print", "photo-1676195470090-7c90bf539b3b", DepthPrint.Lime),
    depthPhoto("Ninety", "Portrait, black and white", "photo-1685041552051-13de9e56228c", DepthPrint.Mono),
    depthPhoto("Antique", "Sculpture, black and white", "photo-1729008712499-5e6b58420789", DepthPrint.Mono),
    depthPhoto("Atelier", "Fashion, black and white", "photo-1717765698239-88731a968d20", DepthPrint.Mono),
    DepthCard.Glyph("Find", "Icon, dotted", DepthGlyph.Search),
    depthPhoto("Low Key", "Portrait, lime print", "photo-1587987746776-302404b98970", DepthPrint.Lime),
    depthPoster("Edge", "Poster, framed", "photo-1508153460964-48ffffcb0829", "Light finds", "its level"),
    DepthCard.Mark("Aloft", "Wordmark", "aloft"),
    depthPhoto("Relic", "Sculpture, black and white", "photo-1565170556450-bdfc81864ca1", DepthPrint.Mono),
    depthPhoto("Noir", "Fashion, lime print", "photo-1592833578500-1082e18665a3", DepthPrint.Lime),
    depthPhoto("Plaster", "Sculpture, lime print", "photo-1604854574894-1be73ca43cb8", DepthPrint.Lime),
    depthPhoto("Gaze", "Portrait, black and white", "photo-1506863530036-1efeddceb993", DepthPrint.Mono),
    depthPhoto("Contour", "Profile, black and white", "photo-1592479950461-2c8ef29f2a14", DepthPrint.Mono),
    DepthCard.Chip("Controls", "Interface", "Lift off", "Glide"),
    depthPhoto("Bust", "Sculpture, lime print", "photo-1612753666134-2792aa809104", DepthPrint.Lime),
    depthPhoto("Contrast", "Fashion, black and white", "photo-1754315052701-0b98f6ba786b", DepthPrint.Mono),
    DepthCard.Glyph("Expand", "Icon, dotted", DepthGlyph.Expand),
    depthPhoto("Linen", "Portrait, black and white", "photo-1606143412458-acc5f86de897", DepthPrint.Mono),
    depthPhoto("Shadow", "Profile, lime print", "photo-1611034540516-665df2bbdfd9", DepthPrint.Lime),
    depthPoster("Focus", "Poster, framed", "photo-1629511565591-a1d494ad6c58", "Where depth", "meets focus"),
    depthPhoto("Classic", "Sculpture, black and white", "photo-1673707180677-6e3605339daa", DepthPrint.Mono),
    depthPhoto("Grain", "Fashion, black and white", "photo-1565754121773-b185234efb37", DepthPrint.Mono),
    DepthCard.Dots("Swarm", "Pattern, lime", 1),
    depthPhoto("Dusk", "Portrait, lime print", "photo-1690376647869-45b2e9987018", DepthPrint.Lime),
    depthPhoto("Silhouette", "Profile, black and white", "photo-1540304801084-0944beb20de7", DepthPrint.Mono),
    depthPhoto("Profile", "Portrait, black and white", "photo-1657412235453-8adb25a45cd0", DepthPrint.Mono),
    depthPhoto("Cast", "Sculpture, black and white", "photo-1781707870248-e2607e82bac0", DepthPrint.Mono),
    depthPoster("Cut Out", "Poster, framed", "photo-1574015974293-817f0ebebb74", "Still air,", "clear sight"),
    DepthCard.Glyph("Pilot", "Icon, dotted", DepthGlyph.Figure),
    depthPhoto("Hush", "Portrait, black and white", "photo-1563170446-9c3c0622d8a9", DepthPrint.Mono),
    depthPhoto("Turn", "Profile, black and white", "photo-1584462746497-276f4aeb9fca", DepthPrint.Mono),
    depthPhoto("Stem", "Still life, lime print", "photo-1471086569966-db3eebc25a59", DepthPrint.Lime),
    depthPhoto("Chalk", "Sculpture, lime print", "photo-1637246344338-2afcfb02e766", DepthPrint.Lime),
    depthPhoto("Swing", "Fashion, black and white", "photo-1596993100471-c3905dafa78e", DepthPrint.Mono),
    DepthCard.Chip("Status", "Interface", "Ascend", "Hold"),
    depthPhoto("Glance", "Portrait, black and white", "photo-1612928414075-bc722ade44f1", DepthPrint.Mono),
    depthPhoto("Side", "Profile, lime print", "photo-1663232176328-5430c668593e", DepthPrint.Lime),
    depthPhoto("Mask", "Sculpture, black and white", "photo-1561910377-c2a014ffd1cd", DepthPrint.Mono),
    depthPhoto("Gesso", "Sculpture, black and white", "photo-1682422911441-a4afa4ddf659", DepthPrint.Mono),
    depthPhoto("Halo", "Portrait, black and white", "photo-1535713875002-d1d0cf377fde", DepthPrint.Mono),
    DepthCard.Glyph("Carry", "Icon, dotted", DepthGlyph.Basket),
    depthPhoto("Line", "Profile, lime print", "photo-1704202632056-de2c85caf4cf", DepthPrint.Lime),
    depthPhoto("Matte", "Portrait, black and white", "photo-1768247695735-e10baa73e917", DepthPrint.Mono),
    DepthCard.Dots("Pulse", "Pattern, lime", 2),
    depthPhoto("Rim", "Profile, black and white", "photo-1627626487034-0a3dc7278c18", DepthPrint.Mono),
    depthPhoto("Studio", "Portrait, lime print", "photo-1631002724711-d1ae3cf651b0", DepthPrint.Lime),
    depthPhoto("Soft Box", "Portrait, black and white", "photo-1649058047388-b49c028df9ac", DepthPrint.Mono),
)

/** A dot in card widths about the card's middle. */
internal class Dot(val x: Float, val y: Float, val r: Float)

// Three clusters of dots, bigger towards the top right, in card widths about the card's middle.
internal val DotPatterns: List<List<Dot>> = listOf(
    listOf(
        Dot(-0.3f, -0.16f, 0.035f), Dot(-0.12f, -0.17f, 0.075f), Dot(0.14f, -0.19f, 0.115f),
        Dot(-0.24f, 0f, 0.05f), Dot(-0.02f, 0.01f, 0.075f), Dot(0.21f, 0.04f, 0.075f),
        Dot(-0.17f, 0.18f, 0.035f), Dot(0.04f, 0.21f, 0.05f), Dot(0.21f, 0.24f, 0.035f),
    ),
    listOf(
        Dot(-0.26f, -0.2f, 0.03f), Dot(-0.05f, -0.2f, 0.055f), Dot(0.17f, -0.16f, 0.09f),
        Dot(-0.18f, -0.03f, 0.07f), Dot(0.06f, 0.02f, 0.1f), Dot(-0.27f, 0.14f, 0.035f),
        Dot(-0.06f, 0.19f, 0.05f), Dot(0.18f, 0.2f, 0.04f),
    ),
    listOf(
        Dot(-0.2f, -0.22f, 0.05f), Dot(0.04f, -0.2f, 0.035f), Dot(0.2f, -0.12f, 0.06f),
        Dot(-0.25f, -0.04f, 0.035f), Dot(-0.05f, -0.02f, 0.11f), Dot(0.2f, 0.08f, 0.075f),
        Dot(-0.2f, 0.17f, 0.06f), Dot(0.02f, 0.22f, 0.035f),
    ),
)

/** An icon drawn in evenly spaced dots, in card widths about the card's middle. */
internal fun dotted(glyph: DepthGlyph): List<Dot> {
    val dots = ArrayList<Dot>()
    val size = 0.017f
    fun line(x0: Float, y0: Float, x1: Float, y1: Float, n: Int) {
        for (i in 0 until n) {
            val t = if (n == 1) 0f else i / (n - 1f)
            dots.add(Dot(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, size))
        }
    }
    fun ring(cx: Float, cy: Float, radius: Float, n: Int) {
        for (i in 0 until n) {
            val a = i.toFloat() / n * PI.toFloat() * 2f
            dots.add(Dot(cx + cos(a) * radius, cy + sin(a) * radius, size))
        }
    }
    when (glyph) {
        DepthGlyph.Search -> {
            ring(0.03f, -0.04f, 0.2f, 18)
            line(-0.15f, 0.14f, -0.25f, 0.24f, 3)
        }
        DepthGlyph.Expand -> {
            line(-0.07f, -0.07f, 0.07f, -0.07f, 4)
            line(-0.07f, 0.07f, 0.07f, 0.07f, 4)
            line(-0.07f, -0.023f, -0.07f, 0.023f, 2)
            line(0.07f, -0.023f, 0.07f, 0.023f, 2)
            for ((sx, sy) in listOf(1f to 1f, 1f to -1f, -1f to 1f, -1f to -1f)) {
                line(sx * 0.12f, sy * 0.12f, sx * 0.28f, sy * 0.28f, 4)
                dots.add(Dot(sx * 0.2f, sy * 0.28f, size))
                dots.add(Dot(sx * 0.28f, sy * 0.2f, size))
            }
        }
        DepthGlyph.Figure -> {
            ring(0f, -0.13f, 0.1f, 12)
            line(0f, 0.03f, 0f, 0.27f, 5)
            dots.add(Dot(-0.06f, 0.21f, size))
            dots.add(Dot(0.06f, 0.21f, size))
        }
        DepthGlyph.Basket -> {
            line(-0.27f, -0.02f, 0.27f, -0.02f, 9)
            line(-0.24f, 0.05f, -0.18f, 0.22f, 3)
            line(0.24f, 0.05f, 0.18f, 0.22f, 3)
            line(-0.11f, 0.22f, 0.11f, 0.22f, 3)
            line(-0.1f, -0.09f, -0.04f, -0.21f, 3)
            line(0.1f, -0.09f, 0.04f, -0.21f, 3)
        }
    }
    return dots
}
