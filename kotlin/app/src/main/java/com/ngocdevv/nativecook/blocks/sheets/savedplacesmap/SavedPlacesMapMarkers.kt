package com.ngocdevv.nativecook.blocks.sheets.savedplacesmap

import kotlin.math.hypot

/** The 38 reference-pixel map glyphs and their deterministic nearest-café flight paths. */
internal object SavedPlacesMapMarkers {
    enum class Kind(val width: Float, val height: Float) {
        BOOKMARK(28f, 32f),
        THUMB(34f, 38f),
        STAR(32f, 31f),
    }

    data class Glyph(val x: Float, val y: Float, val kind: Kind)

    data class GlyphPath(
        val x: Float,
        val y: Float,
        val kind: Kind,
        val targetX: Float,
        val targetY: Float,
        val delay: Double,
        val cluster: Int,
    )

    data class Cluster(
        val key: String,
        val centerX: Float,
        val centerY: Float,
        val bubbleX: Float,
        val bubbleY: Float,
        val count: Int,
        val delays: List<Double>,
    )

    val glyphs = listOf(
        Glyph(453f, 646f, Kind.BOOKMARK),
        Glyph(424f, 649f, Kind.BOOKMARK),
        Glyph(460f, 745f, Kind.BOOKMARK),
        Glyph(462f, 759f, Kind.BOOKMARK),
        Glyph(647f, 810f, Kind.BOOKMARK),
        Glyph(357f, 835f, Kind.BOOKMARK),
        Glyph(549f, 850f, Kind.BOOKMARK),
        Glyph(402f, 872f, Kind.BOOKMARK),
        Glyph(626f, 883f, Kind.BOOKMARK),
        Glyph(710f, 934f, Kind.BOOKMARK),
        Glyph(406f, 1044f, Kind.BOOKMARK),
        Glyph(608f, 1052f, Kind.BOOKMARK),
        Glyph(667f, 1090f, Kind.BOOKMARK),
        Glyph(633f, 1151f, Kind.BOOKMARK),
        Glyph(616f, 1173f, Kind.BOOKMARK),
        Glyph(399f, 785f, Kind.THUMB),
        Glyph(419f, 786f, Kind.THUMB),
        Glyph(608f, 809f, Kind.THUMB),
        Glyph(372f, 813f, Kind.THUMB),
        Glyph(437f, 814f, Kind.THUMB),
        Glyph(414f, 815f, Kind.THUMB),
        Glyph(396f, 830f, Kind.THUMB),
        Glyph(588f, 841f, Kind.THUMB),
        Glyph(418f, 853f, Kind.THUMB),
        Glyph(571f, 861f, Kind.THUMB),
        Glyph(647f, 865f, Kind.THUMB),
        Glyph(671f, 913f, Kind.THUMB),
        Glyph(775f, 1005f, Kind.THUMB),
        Glyph(251f, 1129f, Kind.THUMB),
        Glyph(206f, 1135f, Kind.THUMB),
        Glyph(171f, 1186f, Kind.THUMB),
        Glyph(113f, 1192f, Kind.THUMB),
        Glyph(223f, 1195f, Kind.THUMB),
        Glyph(580f, 404f, Kind.STAR),
        Glyph(730f, 563f, Kind.STAR),
        Glyph(722f, 565f, Kind.STAR),
        Glyph(457f, 815f, Kind.STAR),
        Glyph(859f, 941f, Kind.STAR),
    )

    val paths: List<GlyphPath>
    val clusters: List<Cluster>

    init {
        val discs = SavedPlacesMapTheme.markers.map { marker ->
            Triple(
                marker.key,
                marker.x - SavedPlacesMapTheme.MarkerToken.discDx,
                marker.baseline - SavedPlacesMapTheme.MarkerToken.discDy,
            )
        }
        val nearest = glyphs.map { glyph ->
            var best = 0
            var bestDistance = Double.POSITIVE_INFINITY
            discs.forEachIndexed { index, disc ->
                val distance = hypot((glyph.x - disc.second).toDouble(), (glyph.y - disc.third).toDouble())
                if (distance < bestDistance) {
                    best = index
                    bestDistance = distance
                }
            }
            best to bestDistance
        }
        val rank = IntArray(glyphs.size)
        nearest.mapIndexed { index, value -> index to value.second }
            .sortedBy { it.second }
            .forEachIndexed { order, value -> rank[value.first] = order }
        val last = (glyphs.size - 1).coerceAtLeast(1)
        paths = glyphs.mapIndexed { index, glyph ->
            val clusterIndex = nearest[index].first
            val disc = discs[clusterIndex]
            GlyphPath(
                x = glyph.x,
                y = glyph.y,
                kind = glyph.kind,
                targetX = disc.second + SavedPlacesMapTheme.Bubble.dx,
                targetY = disc.third + SavedPlacesMapTheme.Bubble.dy,
                delay = SavedPlacesMapMotion.Cluster.span * rank[index] / last,
                cluster = clusterIndex,
            )
        }
        clusters = discs.mapIndexed { index, disc ->
            val mine = paths.filter { path -> path.cluster == index }
            Cluster(
                key = disc.first,
                centerX = disc.second,
                centerY = disc.third,
                bubbleX = disc.second + SavedPlacesMapTheme.Bubble.dx,
                bubbleY = disc.third + SavedPlacesMapTheme.Bubble.dy,
                count = mine.size,
                delays = mine.map { path -> path.delay },
            )
        }
    }
}
