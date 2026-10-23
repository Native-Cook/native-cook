package com.ngocdevv.nativecook.blocks.sheets.savedplacesmap

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.RadialGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Cached Android Canvas renderer. Every coordinate below is in the RN block's 1206 px space. */
internal class SavedPlacesMapPainter(private val assets: SavedPlacesMapAssets) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val path = Path()
    private val scratchPath = Path()
    private val rect = RectF()
    private val sourceRect = Rect()
    private val tickPath = Path().apply {
        moveTo(7.5f, 15f)
        lineTo(12.5f, 20f)
        lineTo(21.5f, 9.5f)
    }
    private val tickMeasure = PathMeasure(tickPath, false)
    private val visitedIndex = SavedPlacesMapTheme.markers.indexOfFirst { marker -> marker.key == SavedPlacesMapTheme.VISITED_KEY }

    /** Sky, the pitched map plane, haze, standing pins, loose glyphs and bubbles: everything under the blur. */
    fun drawScene(canvas: Canvas, width: Float, frame: SavedPlacesMapMotion.Frame, reducedMotion: Boolean) {
        val unit = width / SavedPlacesMapTheme.REF_W
        canvas.save()
        canvas.scale(unit, unit)
        val tilt = if (reducedMotion) 0.0 else SavedPlacesMapMotion.tiltOf(frame.sheet)
        drawSky(canvas)
        canvas.save()
        if (tilt > 0.0) {
            canvas.concat(groundMatrix(tilt))
        }
        drawGround(canvas)
        if (tilt > 0.0) {
            drawGroundMarks(canvas, tilt)
        }
        canvas.restore()
        drawFog(canvas, tilt, reducedMotion)
        drawBillboards(canvas, frame, tilt, reducedMotion)
        drawFadedChip(canvas)
        canvas.restore()
    }

    /** Back chevron, badge, floating controls and the sheet: everything above the blur. */
    fun drawFront(canvas: Canvas, width: Float, height: Float, frame: SavedPlacesMapMotion.Frame, reducedMotion: Boolean) {
        val unit = width / SavedPlacesMapTheme.REF_W
        canvas.save()
        canvas.scale(unit, unit)
        drawBackControls(canvas)
        drawFloatingControls(canvas, frame)
        drawSheet(canvas, height / unit, frame, reducedMotion)
        canvas.restore()
    }

    // ---- Ground ----

    private val groundMatrix = Matrix()
    private val groundSrc = floatArrayOf(0f, 0f, 1206f, 0f, 1206f, 1650f, 0f, 1650f)
    private val groundDst = FloatArray(8)

    /**
     * The map plane at pitch progress p. [SavedPlacesMapMotion.project] is the same maths RN applies to the plane
     * (translateY · perspective · rotateX · scale about the origin), and a plane under it is a homography, so the
     * four projected corners fix the matrix exactly.
     */
    private fun groundMatrix(tilt: Double): Matrix {
        val corners = intArrayOf(0, 0, 1206, 0, 1206, 1650, 0, 1650)
        for (i in 0 until 4) {
            val p = SavedPlacesMapMotion.project(corners[i * 2].toDouble(), corners[i * 2 + 1].toDouble(), tilt)
            groundDst[i * 2] = p.x.toFloat()
            groundDst[i * 2 + 1] = p.y.toFloat()
        }
        groundMatrix.setPolyToPoly(groundSrc, 0, groundDst, 0, 4)
        return groundMatrix
    }

    private fun drawSky(canvas: Canvas) {
        if (skyShader == null) {
            skyShader = LinearGradient(
                0f, 0f, 0f, SavedPlacesMapTheme.Layout.mapH,
                SavedPlacesMapTheme.Depth.skyTop, SavedPlacesMapTheme.Depth.skyBottom, Shader.TileMode.CLAMP,
            )
        }
        paint.resetFill()
        paint.shader = skyShader
        canvas.drawRect(0f, 0f, SavedPlacesMapTheme.REF_W, SavedPlacesMapTheme.Layout.mapH, paint)
        paint.shader = null
    }

    private var skyShader: Shader? = null

    /** Map image, place labels and the highway shield (flat map coordinates). */
    private fun drawGround(canvas: Canvas) {
        drawBitmap(canvas, assets.map, RectF(0f, 0f, SavedPlacesMapTheme.REF_W, SavedPlacesMapTheme.Layout.mapH))
        SavedPlacesMapTheme.mapLabels.forEach { label ->
            drawText(
                canvas = canvas,
                text = label.text,
                x = label.x,
                baseline = label.baseline,
                size = label.size,
                typeface = if (label.italic) assets.barlowItalic else assets.barlow,
                color = label.color,
                letterSpacing = label.letterSpacing,
                alpha = label.alpha,
                centered = label.centered,
            )
        }
        drawShield(
            canvas,
            SavedPlacesMapTheme.Shield.x,
            SavedPlacesMapTheme.Shield.y,
            SavedPlacesMapTheme.Shield.w,
            SavedPlacesMapTheme.Shield.h,
        )
    }

    private var shadowShader: Shader? = null

    /** Long contact shadows and landing ripples, painted on the ground plane (so they pitch with it). */
    private fun drawGroundMarks(canvas: Canvas, tilt: Double) {
        val token = SavedPlacesMapTheme.MarkerToken
        SavedPlacesMapTheme.markers.forEach { marker ->
            val cx = marker.x - token.discDx
            val cy = marker.baseline - token.discDy
            val rank = SavedPlacesMapTheme.depthRank[marker.key] ?: 0
            val st = SavedPlacesMapMotion.standOf(tilt, rank)
            val shadowAlpha = SavedPlacesMapMotion.Shadow.opacity * min(1.0, st)
            if (shadowAlpha > 0.001) {
                val len = SavedPlacesMapMotion.Shadow.len
                val w = SavedPlacesMapMotion.Shadow.w
                if (shadowShader == null) {
                    // Gradient in the box's own unit space: centre (0.1, 0.5), radii (0.9, 0.5) of the box.
                    val c = SavedPlacesMapTheme.Depth.shadow
                    shadowShader = RadialGradient(
                        0f, 0f, 1f,
                        intArrayOf(c, withAlpha(c, 0.45f), withAlpha(c, 0f)),
                        floatArrayOf(0f, 0.45f, 1f),
                        Shader.TileMode.CLAMP,
                    )
                }
                canvas.save()
                canvas.translate(cx, cy)
                canvas.rotate(SavedPlacesMapMotion.Shadow.angle)
                canvas.scale(max(0.001, st).toFloat(), 1f)
                canvas.translate(0.1f * len, 0f)
                canvas.scale(0.9f * len, 0.5f * w)
                paint.resetFill()
                paint.shader = shadowShader
                paint.alpha = (255 * shadowAlpha).toInt().coerceIn(0, 255)
                // The box's ellipse in that space: centre (0.4/0.9, 0), radii (0.5/0.9, 1).
                rect.set(-0.1f / 0.9f, -1f, -0.1f / 0.9f + 1f / 0.9f, 1f)
                canvas.drawOval(rect, paint)
                paint.shader = null
                canvas.restore()
            }
            val q = SavedPlacesMapMotion.standLinear(tilt, rank)
            val e = SavedPlacesMapMotion.seg(q, SavedPlacesMapMotion.Ripple.at, 1.0)
            val rippleAlpha = 0.8 * SavedPlacesMapMotion.seg(q, SavedPlacesMapMotion.Ripple.at, SavedPlacesMapMotion.Ripple.at + 0.05) * (1.0 - e)
            if (rippleAlpha > 0.001) {
                val k = SavedPlacesMapMotion.lerp(1.0, SavedPlacesMapMotion.Ripple.grow, SavedPlacesMapMotion.easeOut(e)).toFloat()
                val ring = SavedPlacesMapTheme.Depth.rippleRing
                paint.resetStroke(SavedPlacesMapTheme.Colors.bookmark, ring)
                paint.alpha = (255 * rippleAlpha).toInt().coerceIn(0, 255)
                canvas.save()
                canvas.scale(k, k, cx, cy)
                canvas.drawCircle(cx, cy, SavedPlacesMapMotion.Ripple.d / 2f - ring / 2f, paint)
                canvas.restore()
            }
        }
    }

    private fun drawFog(canvas: Canvas, tilt: Double, reducedMotion: Boolean) {
        val opacity = if (reducedMotion) {
            0.0
        } else {
            SavedPlacesMapMotion.easeOut(SavedPlacesMapMotion.seg(tilt, SavedPlacesMapMotion.Tilt.fog[0], SavedPlacesMapMotion.Tilt.fog[1]))
        }
        if (opacity <= 0.001) {
            return
        }
        if (fogShader == null) {
            val d = SavedPlacesMapTheme.Depth
            fogShader = LinearGradient(
                0f, 0f, 0f, d.fogH,
                IntArray(3) { i -> Color.argb((255 * d.fogAlphas[i]).toInt(), d.fogR, d.fogG, d.fogB) },
                d.fogStops,
                Shader.TileMode.CLAMP,
            )
        }
        paint.resetFill()
        paint.shader = fogShader
        paint.alpha = (255 * opacity).toInt().coerceIn(0, 255)
        canvas.drawRect(0f, 0f, SavedPlacesMapTheme.REF_W, SavedPlacesMapTheme.Depth.fogH, paint)
        paint.shader = null
    }

    private var fogShader: Shader? = null

    // ---- Billboards ----

    private fun drawBillboards(canvas: Canvas, frame: SavedPlacesMapMotion.Frame, tilt: Double, reducedMotion: Boolean) {
        if (tilt > 0.0) {
            SavedPlacesMapTheme.markers.forEachIndexed { index, marker -> drawRig(canvas, index, marker, tilt, frame) }
        }
        SavedPlacesMapMarkers.paths.forEach { glyph -> drawMovingGlyph(canvas, glyph, frame.cluster, tilt, reducedMotion) }
        SavedPlacesMapTheme.markers.forEachIndexed { index, marker ->
            val token = SavedPlacesMapTheme.MarkerToken
            val cx = marker.x - token.discDx
            val cy = marker.baseline - token.discDy
            canvas.save()
            if (!reducedMotion) {
                val rank = SavedPlacesMapTheme.depthRank[marker.key] ?: 0
                val disc = SavedPlacesMapMotion.discAt(cx.toDouble(), cy.toDouble(), tilt, rank, token.disc / 2.0)
                canvas.translate((disc.x - cx).toFloat(), (disc.y - cy).toFloat())
                canvas.scale(disc.m.toFloat(), disc.m.toFloat(), cx, cy)
            }
            drawMarker(canvas, index, marker, frame, reducedMotion)
            drawBubble(canvas, SavedPlacesMapMarkers.clusters[index], frame.cluster, reducedMotion)
            canvas.restore()
        }
    }

    private val beamShaders = arrayOfNulls<Shader>(SavedPlacesMapTheme.markers.size * 3)
    private val stalkShaders = arrayOfNulls<Shader>(SavedPlacesMapTheme.markers.size)

    /** Stalk, foot and light beam of a standing pin, riding the pin's ground spot. */
    private fun drawRig(canvas: Canvas, index: Int, marker: SavedPlacesMapTheme.Marker, tilt: Double, frame: SavedPlacesMapMotion.Frame) {
        val token = SavedPlacesMapTheme.MarkerToken
        val cx = marker.x - token.discDx
        val cy = marker.baseline - token.discDy
        val rank = SavedPlacesMapTheme.depthRank[marker.key] ?: 0
        val g = SavedPlacesMapMotion.project(cx.toDouble(), cy.toDouble(), tilt)
        val st = SavedPlacesMapMotion.standOf(tilt, rank)
        val depth = SavedPlacesMapTheme.Depth
        canvas.save()
        canvas.translate((g.x - cx).toFloat(), (g.y - cy).toFloat())
        canvas.scale(g.m.toFloat(), g.m.toFloat(), cx, cy)

        val on = SavedPlacesMapMotion.seg(st, 0.4, 1.0)
        val flare = index == visitedIndex
        val boost = if (flare) 1.0 + SavedPlacesMapMotion.Beam.flare * max(0.0, frame.visitPop - 1.0) else 1.0
        val beamAlpha = min(1.0, SavedPlacesMapMotion.Beam.opacity * on * boost)
        if (beamAlpha > 0.001) {
            val tone = if (marker.tag == "saved") depth.beamSaved else depth.beamLiked
            val w = SavedPlacesMapMotion.Beam.w
            val h = SavedPlacesMapMotion.Beam.h
            val left = cx - w * 1.5f
            val top = cy - h
            canvas.save()
            canvas.scale(boost.toFloat(), SavedPlacesMapMotion.lerp(0.3, 1.0, SavedPlacesMapMotion.easeOut(on)).toFloat(), cx, cy)
            val alpha = (255 * beamAlpha).toInt().coerceIn(0, 255)
            fun strip(slot: Int, x: Float, width: Float, from: Int, to: Int) {
                var shader = beamShaders[index * 3 + slot]
                if (shader == null) {
                    shader = LinearGradient(0f, top, 0f, cy, from, to, Shader.TileMode.CLAMP)
                    beamShaders[index * 3 + slot] = shader
                }
                paint.resetFill()
                paint.shader = shader
                paint.alpha = alpha
                canvas.drawRect(x, top, x + width, cy, paint)
                paint.shader = null
            }
            strip(0, left, w * 3f, Color.argb(0, tone[0], tone[1], tone[2]), Color.argb(41, tone[0], tone[1], tone[2]))
            strip(1, left + w, w, Color.argb(0, tone[0], tone[1], tone[2]), Color.argb(87, tone[0], tone[1], tone[2]))
            strip(2, left + w * 1.5f - SavedPlacesMapMotion.Beam.core / 2f, SavedPlacesMapMotion.Beam.core, Color.argb(0, 255, 255, 255), Color.argb(204, 255, 255, 255))
            canvas.restore()
        }
        val footAlpha = 0.45 * SavedPlacesMapMotion.seg(st, 0.0, 0.5)
        if (footAlpha > 0.001) {
            paint.resetFill(withAlpha(depth.foot, footAlpha.toFloat()))
            rect.set(cx - depth.footD / 2f, cy - depth.footD / 4f, cx + depth.footD / 2f, cy + depth.footD / 4f)
            canvas.drawOval(rect, paint)
        }
        val stalkAlpha = SavedPlacesMapMotion.seg(st, 0.0, 0.2)
        if (stalkAlpha > 0.001) {
            val stalkLength = (SavedPlacesMapMotion.Stand.stalk + token.disc / 2.0).toFloat()
            val height = stalkLength * max(0.001, st).toFloat()
            var shader = stalkShaders[index]
            if (shader == null) {
                shader = LinearGradient(cx - depth.stalkW / 2f, 0f, cx + depth.stalkW / 2f, 0f, depth.stalkTop, depth.stalkBottom, Shader.TileMode.CLAMP)
                stalkShaders[index] = shader
            }
            paint.resetFill()
            paint.shader = shader
            paint.alpha = (255 * stalkAlpha).toInt().coerceIn(0, 255)
            val radius = min(depth.stalkW / 2f, height / 2f)
            canvas.drawRoundRect(cx - depth.stalkW / 2f, cy - height, cx + depth.stalkW / 2f, cy, radius, radius, paint)
            paint.shader = null
        }
        canvas.restore()
    }

    /** A loose map glyph: on its ground spot, projected while the map pitches, gliding on an arc into its café's bubble. */
    private fun drawMovingGlyph(
        canvas: Canvas,
        glyph: SavedPlacesMapMarkers.GlyphPath,
        cluster: Double,
        tilt: Double,
        reducedMotion: Boolean,
    ) {
        val progress = SavedPlacesMapMotion.glyphProgress(cluster, glyph.delay)
        val alpha = if (reducedMotion) 1.0 - progress else 1.0 - SavedPlacesMapMotion.seg(progress, SavedPlacesMapMotion.Cluster.fadeFrom, 1.0)
        if (alpha <= 0.0) {
            return
        }
        var centerX = glyph.x.toDouble()
        var centerY = glyph.y.toDouble()
        var scale = 1.0
        if (!reducedMotion) {
            val from = SavedPlacesMapMotion.project(glyph.x.toDouble(), glyph.y.toDouble(), tilt)
            val marker = SavedPlacesMapTheme.markers[glyph.cluster]
            val token = SavedPlacesMapTheme.MarkerToken
            val rank = SavedPlacesMapTheme.depthRank[marker.key] ?: 0
            val disc = SavedPlacesMapMotion.discAt(
                (marker.x - token.discDx).toDouble(),
                (marker.baseline - token.discDy).toDouble(),
                tilt,
                rank,
                token.disc / 2.0,
            )
            val targetX = disc.x + SavedPlacesMapTheme.Bubble.dx * disc.m
            val targetY = disc.y + SavedPlacesMapTheme.Bubble.dy * disc.m
            val deltaX = targetX - from.x
            val deltaY = targetY - from.y
            val bulge = sin(PI * progress) * SavedPlacesMapMotion.Cluster.arc
            centerX = from.x + deltaX * progress - deltaY * bulge
            centerY = from.y + deltaY * progress + deltaX * bulge
            scale = SavedPlacesMapMotion.lerp(from.m, SavedPlacesMapMotion.Cluster.endScale * disc.m, progress)
        }
        canvas.save()
        canvas.translate(centerX.toFloat(), centerY.toFloat())
        canvas.scale(scale.toFloat(), scale.toFloat())
        drawMapGlyph(canvas, glyph.kind, 0f, 0f, alpha.toFloat())
        canvas.restore()
    }

    private fun drawMarker(
        canvas: Canvas,
        index: Int,
        marker: SavedPlacesMapTheme.Marker,
        frame: SavedPlacesMapMotion.Frame,
        reducedMotion: Boolean,
    ) {
        val token = SavedPlacesMapTheme.MarkerToken
        val centerX = marker.x - token.discDx
        val centerY = marker.baseline - token.discDy
        val visitScale = if (index == visitedIndex) frame.visitPop else 1.0
        val tapScale = if (frame.tapIndex == index) frame.tapPop else 1.0
        val scale = if (reducedMotion) 1f else (visitScale * tapScale).toFloat()
        canvas.save()
        canvas.scale(scale, scale, centerX, centerY)
        drawShadowCircle(canvas, centerX, centerY, token.disc / 2f, Color.rgb(62, 85, 104), 0.28f, 3f, 10f)
        paint.resetFill(Color.rgb(251, 251, 250))
        canvas.drawCircle(centerX, centerY, token.disc / 2f, paint)
        drawEmoji(canvas, marker.emoji, centerX, centerY, marker.emojiSize * 3f)
        if (index == visitedIndex && frame.badge > 0.0) {
            drawCheckBadge(canvas, centerX, centerY, frame.badge.toFloat(), reducedMotion)
        }
        canvas.restore()

        val glyphKind = if (marker.tag == "saved") SavedPlacesMapMarkers.Kind.BOOKMARK else SavedPlacesMapMarkers.Kind.THUMB
        val glyphCenterX = marker.x - token.iconDx - 3f + glyphKind.width / 2f
        val glyphCenterY = marker.baseline - token.bookmarkDy - 3f + glyphKind.height / 2f
        drawMapGlyph(canvas, glyphKind, glyphCenterX, glyphCenterY, 1f)
        drawText(canvas, marker.tag, marker.x, marker.baseline - token.savedDy, token.subSize, assets.barlowItalic, SavedPlacesMapTheme.Colors.markerSub)
        drawText(canvas, marker.name, marker.x, marker.baseline, token.nameSize, assets.barlowSemiBold, SavedPlacesMapTheme.Colors.markerInk)
        drawText(canvas, marker.sub, marker.x, marker.baseline + token.subDy, token.subSize, assets.barlowItalic, SavedPlacesMapTheme.Colors.markerSub)
    }

    private fun drawBubble(
        canvas: Canvas,
        cluster: SavedPlacesMapMarkers.Cluster,
        clusterProgress: Double,
        reducedMotion: Boolean,
    ) {
        var landed = 0.0
        cluster.delays.forEach { delay ->
            landed += SavedPlacesMapMotion.seg(
                SavedPlacesMapMotion.glyphProgress(clusterProgress, delay),
                SavedPlacesMapMotion.Cluster.landFrom,
                1.0,
            )
        }
        val show = if (reducedMotion) {
            SavedPlacesMapMotion.seg(clusterProgress, 0.0, 1.0)
        } else {
            SavedPlacesMapMotion.seg(landed, 0.0, 0.5)
        }
        if (show <= 0.0) {
            return
        }
        val gulp = if (!reducedMotion && landed > 0.0 && landed < cluster.count) {
            SavedPlacesMapMotion.Cluster.gulp * sin(PI * (landed % 1.0))
        } else {
            0.0
        }
        val scale = if (reducedMotion) 1.0 else SavedPlacesMapMotion.lerp(SavedPlacesMapMotion.Cluster.bubbleFrom, 1.0, show) + gulp
        val width = max(
            SavedPlacesMapTheme.Bubble.h,
            cluster.count.toString().length * SavedPlacesMapTheme.Bubble.digit + SavedPlacesMapTheme.Bubble.padX * 2f,
        )
        val height = SavedPlacesMapTheme.Bubble.h
        val ring = SavedPlacesMapTheme.Bubble.ring
        val row = height - ring * 2f
        val left = cluster.bubbleX - width / 2f
        val top = cluster.bubbleY - height / 2f
        val alpha = (255 * show).toInt().coerceIn(0, 255)
        canvas.save()
        canvas.scale(scale.toFloat(), scale.toFloat(), cluster.bubbleX, cluster.bubbleY)
        // Border box: the white ring, then the blue body inside it.
        paint.resetFill(SavedPlacesMapTheme.Colors.white)
        paint.alpha = alpha
        canvas.drawRoundRect(left, top, left + width, top + height, height / 2f, height / 2f, paint)
        paint.resetFill(SavedPlacesMapTheme.Colors.bookmark)
        paint.alpha = alpha
        canvas.drawRoundRect(left + ring, top + ring, left + width - ring, top + height - ring, height / 2f - ring, height / 2f - ring, paint)
        clipRoundRect(canvas, RectF(left + ring, top + ring, left + width - ring, top + height - ring), height / 2f - ring)
        val displayed = if (reducedMotion) cluster.count.toDouble() else landed.coerceIn(1.0, cluster.count.toDouble())
        val stripOffset = -(displayed - 1.0) * row
        // Only the digits that show in the window are drawn.
        val first = floor(displayed).toInt().coerceIn(1, cluster.count)
        val last = min(cluster.count, first + 1)
        for (value in first..last) {
            val rowTop = top + ring + (value - 1) * row + stripOffset.toFloat()
            if (rowTop > top + height || rowTop + row < top) {
                continue
            }
            drawCenteredTextInRow(
                canvas,
                value.toString(),
                cluster.bubbleX,
                rowTop + row / 2f,
                SavedPlacesMapTheme.Bubble.size,
                assets.barlowSemiBold,
                SavedPlacesMapTheme.Colors.white,
                show.toFloat(),
            )
        }
        canvas.restore()
    }

    private fun drawFadedChip(canvas: Canvas) {
        paint.resetFill(Color.argb(115, 255, 255, 255))
        canvas.drawRoundRect(
            SavedPlacesMapTheme.FadedChip.x,
            SavedPlacesMapTheme.FadedChip.y,
            SavedPlacesMapTheme.FadedChip.x + SavedPlacesMapTheme.FadedChip.w,
            SavedPlacesMapTheme.FadedChip.y + SavedPlacesMapTheme.FadedChip.h,
            14f,
            14f,
            paint,
        )
    }

    private fun drawBackControls(canvas: Canvas) {
        drawChevronLeft(
            canvas,
            SavedPlacesMapTheme.Back.chevronX,
            SavedPlacesMapTheme.Back.chevronY,
            SavedPlacesMapTheme.Back.chevronW,
            SavedPlacesMapTheme.Back.chevronH,
        )
        paint.resetFill(SavedPlacesMapTheme.Colors.badge)
        canvas.drawRoundRect(
            SavedPlacesMapTheme.Back.badgeX,
            SavedPlacesMapTheme.Back.badgeY,
            SavedPlacesMapTheme.Back.badgeX + SavedPlacesMapTheme.Back.badgeW,
            SavedPlacesMapTheme.Back.badgeY + SavedPlacesMapTheme.Back.badgeH,
            14f,
            14f,
            paint,
        )
        paint.resetFill(Color.rgb(244, 244, 244))
        canvas.drawCircle(71f, 310.5f, 19f, paint)
        drawCenteredTextInRow(canvas, "3", 71f, 310.5f, 26f, assets.barlowSemiBold, Color.rgb(34, 34, 34))
    }

    private fun drawFloatingControls(canvas: Canvas, frame: SavedPlacesMapMotion.Frame) {
        val opacity = 1.0 - SavedPlacesMapMotion.easeOut(
            SavedPlacesMapMotion.seg(frame.sheet, SavedPlacesMapMotion.CONTROLS_FADE[0], SavedPlacesMapMotion.CONTROLS_FADE[1]),
        )
        if (opacity <= 0.0) {
            return
        }
        canvas.saveLayerAlpha(null, (255 * opacity).toInt())
        canvas.translate(0f, (-SavedPlacesMapMotion.RISE * 3f * frame.sheet).toFloat())
        drawShadowCircle(canvas, SavedPlacesMapTheme.Locate.centerX, SavedPlacesMapTheme.Locate.centerY, SavedPlacesMapTheme.Locate.diameter / 2f, Color.rgb(62, 85, 104), 0.28f, 5f, 14f)
        paint.resetFill(Color.WHITE)
        canvas.drawCircle(SavedPlacesMapTheme.Locate.centerX, SavedPlacesMapTheme.Locate.centerY, SavedPlacesMapTheme.Locate.diameter / 2f, paint)
        drawLocateArrow(
            canvas,
            SavedPlacesMapTheme.Locate.arrowCenterX - SavedPlacesMapTheme.Locate.arrowSize / 2f,
            SavedPlacesMapTheme.Locate.arrowCenterY - SavedPlacesMapTheme.Locate.arrowSize / 2f,
            SavedPlacesMapTheme.Locate.arrowSize,
        )

        SavedPlacesMapTheme.Chips.items.forEach { chip ->
            drawShadowRoundRect(canvas, chip.x, SavedPlacesMapTheme.Chips.y, chip.w, SavedPlacesMapTheme.Chips.h, 41f, Color.rgb(75, 85, 96), 0.28f, 5f, 10f)
            paint.resetFill(SavedPlacesMapTheme.Colors.chip)
            canvas.drawRoundRect(chip.x, SavedPlacesMapTheme.Chips.y, chip.x + chip.w, SavedPlacesMapTheme.Chips.y + SavedPlacesMapTheme.Chips.h, 41f, 41f, paint)
            paint.resetFill()
            paint.shader = LinearGradient(
                0f,
                SavedPlacesMapTheme.Chips.y,
                0f,
                SavedPlacesMapTheme.Chips.y + SavedPlacesMapTheme.Chips.h,
                intArrayOf(Color.TRANSPARENT, Color.argb(230, 235, 232, 236)),
                floatArrayOf(0.75f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawRoundRect(chip.x, SavedPlacesMapTheme.Chips.y, chip.x + chip.w, SavedPlacesMapTheme.Chips.y + SavedPlacesMapTheme.Chips.h, 41f, 41f, paint)
            paint.shader = null
            if (chip.globe) {
                drawGlobe(canvas, 62f, 1463f, 35f, 35f)
            } else if (chip.emoji != null) {
                drawEmoji(canvas, chip.emoji, chip.emojiX, 1481f, 37.8f)
            }
            if (chip.text.isNotEmpty()) {
                drawText(canvas, chip.text, chip.textX, SavedPlacesMapTheme.Chips.baseline, SavedPlacesMapTheme.Type.chip, assets.archivoSemiBold, SavedPlacesMapTheme.Colors.ink)
            }
        }
        drawChevronDown(canvas, 354f, 1474f, 28f, 14f)
        canvas.restore()
    }

    private fun drawSheet(
        canvas: Canvas,
        referenceHeight: Float,
        frame: SavedPlacesMapMotion.Frame,
        reducedMotion: Boolean,
    ) {
        canvas.save()
        canvas.translate(0f, (-SavedPlacesMapMotion.RISE * 3f * frame.sheet).toFloat())
        path.reset()
        path.addRoundRect(
            RectF(0f, SavedPlacesMapTheme.Sheet.top, SavedPlacesMapTheme.REF_W, referenceHeight + SavedPlacesMapMotion.RISE * 6f),
            SavedPlacesMapTheme.Radius.sheet,
            SavedPlacesMapTheme.Radius.sheet,
            Path.Direction.CW,
        )
        paint.resetFill()
        paint.shader = LinearGradient(
            0f,
            SavedPlacesMapTheme.Sheet.top,
            0f,
            referenceHeight,
            intArrayOf(0xFFECF2F4.toInt(), 0xFFE8F0F5.toInt(), 0xFFE2EEF7.toInt(), 0xFFDDEEF8.toInt()),
            floatArrayOf(0f, 0.25f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(path, paint)
        paint.shader = null
        drawSheetHighlight(canvas)

        val grabScale = SavedPlacesMapMotion.lerp(1.0, SavedPlacesMapMotion.Grab.scale, frame.grab).toFloat()
        canvas.save()
        canvas.scale(grabScale, 1f, SavedPlacesMapTheme.Sheet.grabberX + SavedPlacesMapTheme.Sheet.grabberW / 2f, SavedPlacesMapTheme.Sheet.grabberY + SavedPlacesMapTheme.Sheet.grabberH / 2f)
        val grabColor = blend(SavedPlacesMapTheme.Colors.grabber, SavedPlacesMapTheme.Colors.grabberTaken, frame.grab.toFloat())
        paint.resetFill(grabColor)
        canvas.drawRoundRect(
            SavedPlacesMapTheme.Sheet.grabberX,
            SavedPlacesMapTheme.Sheet.grabberY,
            SavedPlacesMapTheme.Sheet.grabberX + SavedPlacesMapTheme.Sheet.grabberW,
            SavedPlacesMapTheme.Sheet.grabberY + SavedPlacesMapTheme.Sheet.grabberH,
            8f,
            8f,
            paint,
        )
        canvas.restore()

        drawRoundedBitmap(canvas, assets.cafe, RectF(48f, 1646f, 354f, 2036f), 24f)
        drawText(canvas, SavedPlacesMapTheme.Copy.title, 385f, 1760f, SavedPlacesMapTheme.Type.title, assets.archivoExtraBold, SavedPlacesMapTheme.Colors.ink)
        drawPin(canvas, SavedPlacesMapTheme.Layout.metaPinX, SavedPlacesMapTheme.Layout.metaPinY, SavedPlacesMapTheme.Layout.metaPinW, 36f)
        drawText(canvas, SavedPlacesMapTheme.Copy.places, SavedPlacesMapTheme.Layout.metaPlacesX, SavedPlacesMapTheme.Layout.metaBase, SavedPlacesMapTheme.Type.meta, assets.archivo, SavedPlacesMapTheme.Colors.meta)
        drawBookmarkOutline(canvas, SavedPlacesMapTheme.Layout.metaBookmarkX, SavedPlacesMapTheme.Layout.metaBookmarkY, SavedPlacesMapTheme.Layout.metaBookmarkW, 35f, SavedPlacesMapTheme.Colors.meta, 1.5f)
        drawText(canvas, SavedPlacesMapTheme.Copy.saves, SavedPlacesMapTheme.Layout.metaSavesX, SavedPlacesMapTheme.Layout.metaBase, SavedPlacesMapTheme.Type.meta, assets.archivo, SavedPlacesMapTheme.Colors.meta)
        drawBars(canvas, 894f, 1809f, 37f, 34f)
        drawText(canvas, SavedPlacesMapTheme.Copy.views, SavedPlacesMapTheme.Layout.metaViewsX, SavedPlacesMapTheme.Layout.metaBase, SavedPlacesMapTheme.Type.meta, assets.archivo, SavedPlacesMapTheme.Colors.meta)

        drawShadowRoundRect(canvas, 386f, 1893f, 319f, 91f, 45.5f, SavedPlacesMapTheme.Colors.shadow, 0.2f, 6f, 16f)
        paint.resetFill(Color.WHITE)
        canvas.drawRoundRect(386f, 1893f, 705f, 1984f, 45.5f, 45.5f, paint)
        drawRoundedBitmap(canvas, assets.avatar, RectF(397f, 1904f, 468f, 1975f), 35.5f)
        drawText(canvas, SavedPlacesMapTheme.Copy.handle, 485f, 1952f, SavedPlacesMapTheme.Type.handle, assets.archivoMedium, SavedPlacesMapTheme.Colors.ink)

        drawActionTiles(canvas, frame, reducedMotion)
        drawListCards(canvas, frame, reducedMotion)
        canvas.restore()
    }

    /** RN's border-top: a band that follows the corner arcs and tapers to nothing at the sides. */
    private fun drawSheetHighlight(canvas: Canvas) {
        val top = SavedPlacesMapTheme.Sheet.top
        val r = SavedPlacesMapTheme.Radius.sheet
        val width = SavedPlacesMapTheme.REF_W
        val bw = 3f
        val ry = r - bw
        val k = 0.5522847f
        path.reset()
        path.moveTo(0f, top + r)
        path.cubicTo(0f, top + r - r * k, r - r * k, top, r, top)
        path.lineTo(width - r, top)
        path.cubicTo(width - r + r * k, top, width, top + r - r * k, width, top + r)
        path.cubicTo(width, top + bw + ry - ry * k, width - r + r * k, top + bw, width - r, top + bw)
        path.lineTo(r, top + bw)
        path.cubicTo(r - r * k, top + bw, 0f, top + bw + ry - ry * k, 0f, top + r)
        path.close()
        paint.resetFill(SavedPlacesMapTheme.Colors.highlight)
        canvas.drawPath(path, paint)
    }

    private fun drawActionTiles(canvas: Canvas, frame: SavedPlacesMapMotion.Frame, reducedMotion: Boolean) {
        repeat(4) { index ->
            val pressed = if (index == 0 && frame.pressTarget == SavedPlacesMapController.HIT_TILE) frame.press else 0.0
            val scale = if (index == 0) SavedPlacesMapMotion.lerp(1.0, SavedPlacesMapMotion.Press.tile, pressed).toFloat() else 1f
            canvas.save()
            canvas.scale(scale, scale, SavedPlacesMapTheme.Tiles.centre[index], SavedPlacesMapTheme.Tiles.y + SavedPlacesMapTheme.Tiles.h / 2f)
            if (index == 0) {
                paint.resetFill(SavedPlacesMapTheme.Colors.tile0)
                canvas.drawRoundRect(
                    SavedPlacesMapTheme.Tiles.xs[index],
                    SavedPlacesMapTheme.Tiles.y,
                    SavedPlacesMapTheme.Tiles.xs[index] + SavedPlacesMapTheme.Tiles.w,
                    SavedPlacesMapTheme.Tiles.y + SavedPlacesMapTheme.Tiles.h,
                    SavedPlacesMapTheme.Radius.tile,
                    SavedPlacesMapTheme.Radius.tile,
                    paint,
                )
                paint.resetStroke(SavedPlacesMapTheme.Colors.tile0Border, 2f)
                canvas.drawRoundRect(
                    SavedPlacesMapTheme.Tiles.xs[index],
                    SavedPlacesMapTheme.Tiles.y,
                    SavedPlacesMapTheme.Tiles.xs[index] + SavedPlacesMapTheme.Tiles.w,
                    SavedPlacesMapTheme.Tiles.y + SavedPlacesMapTheme.Tiles.h,
                    SavedPlacesMapTheme.Radius.tile,
                    SavedPlacesMapTheme.Radius.tile,
                    paint,
                )
            } else {
                drawShadowRoundRect(canvas, SavedPlacesMapTheme.Tiles.xs[index], SavedPlacesMapTheme.Tiles.y, SavedPlacesMapTheme.Tiles.w, SavedPlacesMapTheme.Tiles.h, SavedPlacesMapTheme.Radius.tile, SavedPlacesMapTheme.Colors.shadow, 0.2f, 6f, 20f)
                paint.resetFill(SavedPlacesMapTheme.Colors.tile)
                canvas.drawRoundRect(
                    SavedPlacesMapTheme.Tiles.xs[index],
                    SavedPlacesMapTheme.Tiles.y,
                    SavedPlacesMapTheme.Tiles.xs[index] + SavedPlacesMapTheme.Tiles.w,
                    SavedPlacesMapTheme.Tiles.y + SavedPlacesMapTheme.Tiles.h,
                    SavedPlacesMapTheme.Radius.tile,
                    SavedPlacesMapTheme.Radius.tile,
                    paint,
                )
                paint.resetFill()
                paint.shader = LinearGradient(
                    0f,
                    SavedPlacesMapTheme.Tiles.y,
                    0f,
                    SavedPlacesMapTheme.Tiles.y + SavedPlacesMapTheme.Tiles.h,
                    intArrayOf(Color.TRANSPARENT, Color.argb(242, 237, 237, 245)),
                    floatArrayOf(0.82f, 1f),
                    Shader.TileMode.CLAMP,
                )
                canvas.drawRoundRect(
                    SavedPlacesMapTheme.Tiles.xs[index],
                    SavedPlacesMapTheme.Tiles.y,
                    SavedPlacesMapTheme.Tiles.xs[index] + SavedPlacesMapTheme.Tiles.w,
                    SavedPlacesMapTheme.Tiles.y + SavedPlacesMapTheme.Tiles.h,
                    SavedPlacesMapTheme.Radius.tile,
                    SavedPlacesMapTheme.Radius.tile,
                    paint,
                )
                paint.shader = null
            }
            canvas.restore()
        }

        canvas.save()
        val tilePressed = if (frame.pressTarget == SavedPlacesMapController.HIT_TILE) frame.press else 0.0
        val tileScale = SavedPlacesMapMotion.lerp(1.0, SavedPlacesMapMotion.Press.tile, tilePressed).toFloat()
        canvas.scale(tileScale, tileScale, SavedPlacesMapTheme.Tiles.centre[0], SavedPlacesMapTheme.Tiles.y + SavedPlacesMapTheme.Tiles.h / 2f)
        canvas.save()
        canvas.clipRect(
            SavedPlacesMapTheme.Roll.x,
            SavedPlacesMapTheme.Roll.y,
            SavedPlacesMapTheme.Roll.x + SavedPlacesMapTheme.Roll.w,
            SavedPlacesMapTheme.Roll.y + SavedPlacesMapTheme.Roll.h,
        )
        val rollOffset = if (reducedMotion) 0f else (-frame.roll * SavedPlacesMapTheme.Roll.row).toFloat()
        val zeroAlpha = if (reducedMotion) (1.0 - frame.roll).toFloat() else 1f
        val oneAlpha = if (reducedMotion) frame.roll.toFloat() else 1f
        drawText(canvas, "0", SavedPlacesMapTheme.Layout.tile0NumX, SavedPlacesMapTheme.Layout.tile0NumBase + rollOffset, SavedPlacesMapTheme.Type.visitedNum, assets.archivoExtraBold, SavedPlacesMapTheme.Colors.ink, alpha = zeroAlpha)
        drawText(canvas, "1", SavedPlacesMapTheme.Roll.centre, SavedPlacesMapTheme.Layout.tile0NumBase + rollOffset + if (reducedMotion) 0f else SavedPlacesMapTheme.Roll.row, SavedPlacesMapTheme.Type.visitedNum, assets.archivoExtraBold, SavedPlacesMapTheme.Colors.ink, alpha = oneAlpha, centered = true)
        canvas.restore()
        drawText(canvas, SavedPlacesMapTheme.Copy.visitedDen, SavedPlacesMapTheme.Layout.tile0DenX, SavedPlacesMapTheme.Layout.tile0NumBase, SavedPlacesMapTheme.Type.visitedDen, assets.archivoSemiBold, SavedPlacesMapTheme.Colors.slash)
        drawText(canvas, SavedPlacesMapTheme.Copy.visited, SavedPlacesMapTheme.Tiles.centre[0], SavedPlacesMapTheme.Layout.tile0LabelBase, SavedPlacesMapTheme.Type.visited, assets.archivo, SavedPlacesMapTheme.Colors.tileLabel, centered = true)
        canvas.restore()

        val labels = listOf(SavedPlacesMapTheme.Copy.save, SavedPlacesMapTheme.Copy.share, SavedPlacesMapTheme.Copy.similar)
        labels.forEachIndexed { index, label ->
            drawText(canvas, label, SavedPlacesMapTheme.Tiles.centre[index + 1], SavedPlacesMapTheme.Tiles.baseline[index + 1], SavedPlacesMapTheme.Type.tile, assets.archivo, SavedPlacesMapTheme.Colors.tileLabel, centered = true)
        }
        val bookmarkIcon = SavedPlacesMapTheme.Layout.tileIcons[0]
        val shareIcon = SavedPlacesMapTheme.Layout.tileIcons[1]
        val gridIcon = SavedPlacesMapTheme.Layout.tileIcons[2]
        drawBookmarkOutline(canvas, bookmarkIcon[0], bookmarkIcon[1], bookmarkIcon[2], bookmarkIcon[3], Color.BLACK, 1.65f)
        drawShare(canvas, shareIcon[0], shareIcon[1], shareIcon[2], shareIcon[3])
        drawGridPlus(canvas, gridIcon[0], gridIcon[1], gridIcon[2], gridIcon[3])
    }

    private fun drawListCards(canvas: Canvas, frame: SavedPlacesMapMotion.Frame, reducedMotion: Boolean) {
        drawCard(canvas, SavedPlacesMapTheme.Layout.cardY, SavedPlacesMapTheme.Copy.listTitle, SavedPlacesMapTheme.Copy.listSub, assets.bar, 1f, 0f, 0f)
        val checkboxPressed = if (frame.pressTarget == SavedPlacesMapController.HIT_CHECKBOX) frame.press else 0.0
        val checkboxScale = SavedPlacesMapMotion.lerp(1.0, SavedPlacesMapMotion.Press.checkbox, checkboxPressed).toFloat()
        val centerX = SavedPlacesMapTheme.Layout.checkboxX + SavedPlacesMapTheme.Layout.checkboxD / 2f
        val centerY = SavedPlacesMapTheme.Layout.checkboxY + SavedPlacesMapTheme.Layout.checkboxD / 2f
        canvas.save()
        canvas.scale(checkboxScale, checkboxScale, centerX, centerY)
        paint.resetStroke(SavedPlacesMapTheme.Colors.checkbox, 3f)
        canvas.drawRoundRect(
            SavedPlacesMapTheme.Layout.checkboxX,
            SavedPlacesMapTheme.Layout.checkboxY,
            SavedPlacesMapTheme.Layout.checkboxX + SavedPlacesMapTheme.Layout.checkboxD,
            SavedPlacesMapTheme.Layout.checkboxY + SavedPlacesMapTheme.Layout.checkboxD,
            7f,
            7f,
            paint,
        )
        if (frame.fill > 0.0) {
            val fillScale = if (reducedMotion) 1f else SavedPlacesMapMotion.lerp(0.9, 1.0, frame.fill).toFloat()
            canvas.save()
            canvas.scale(fillScale, fillScale, centerX, centerY)
            paint.resetFill(Color.BLACK)
            paint.alpha = (255 * frame.fill).toInt()
            canvas.drawRoundRect(
                SavedPlacesMapTheme.Layout.checkboxX,
                SavedPlacesMapTheme.Layout.checkboxY,
                SavedPlacesMapTheme.Layout.checkboxX + SavedPlacesMapTheme.Layout.checkboxD,
                SavedPlacesMapTheme.Layout.checkboxY + SavedPlacesMapTheme.Layout.checkboxD,
                7f,
                7f,
                paint,
            )
            // RN dashes a 22 long pen over a path of about 20.9
            drawTick(canvas, SavedPlacesMapTheme.Layout.checkboxX, SavedPlacesMapTheme.Layout.checkboxY, (frame.draw * 22.0 / 20.9).coerceAtMost(1.0).toFloat(), 1f)
            canvas.restore()
        }
        canvas.restore()

        SavedPlacesMapTheme.moreCards.forEachIndexed { index, card ->
            val y = SavedPlacesMapTheme.Layout.cardY + (SavedPlacesMapTheme.ListToken.cardH + SavedPlacesMapTheme.ListToken.gap) * (index + 1)
            val bitmap = if (card.photo == "cafe") assets.cafe else assets.bar
            drawCard(canvas, y, card.title, card.sub, bitmap, card.zoom, card.ox, card.oy)
            paint.resetStroke(SavedPlacesMapTheme.Colors.checkbox, 3f)
            canvas.drawRoundRect(
                SavedPlacesMapTheme.Layout.checkboxX,
                y + SavedPlacesMapTheme.Layout.checkboxY - SavedPlacesMapTheme.Layout.cardY,
                SavedPlacesMapTheme.Layout.checkboxX + SavedPlacesMapTheme.Layout.checkboxD,
                y + SavedPlacesMapTheme.Layout.checkboxY - SavedPlacesMapTheme.Layout.cardY + SavedPlacesMapTheme.Layout.checkboxD,
                7f,
                7f,
                paint,
            )
        }
    }

    private fun drawCard(
        canvas: Canvas,
        y: Float,
        title: String,
        subtitle: String,
        bitmap: Bitmap,
        zoom: Float,
        offsetX: Float,
        offsetY: Float,
    ) {
        val deltaY = y - SavedPlacesMapTheme.Layout.cardY
        drawShadowRoundRect(canvas, SavedPlacesMapTheme.Layout.cardX, y, SavedPlacesMapTheme.Layout.cardW, SavedPlacesMapTheme.ListToken.cardH, SavedPlacesMapTheme.Radius.card, SavedPlacesMapTheme.Colors.shadow, 0.3f, 4f, 18f)
        paint.resetFill(Color.WHITE)
        canvas.drawRoundRect(
            SavedPlacesMapTheme.Layout.cardX,
            y,
            SavedPlacesMapTheme.Layout.cardX + SavedPlacesMapTheme.Layout.cardW,
            y + SavedPlacesMapTheme.ListToken.cardH,
            SavedPlacesMapTheme.Radius.card,
            SavedPlacesMapTheme.Radius.card,
            paint,
        )
        val photoTop = y + SavedPlacesMapTheme.Layout.listPhotoY - SavedPlacesMapTheme.Layout.cardY
        val destination = RectF(
            SavedPlacesMapTheme.Layout.listPhotoX,
            photoTop,
            SavedPlacesMapTheme.Layout.listPhotoX + SavedPlacesMapTheme.Layout.listPhotoD,
            photoTop + SavedPlacesMapTheme.Layout.listPhotoD,
        )
        if (zoom == 1f) {
            drawRoundedBitmap(canvas, bitmap, destination, SavedPlacesMapTheme.Layout.listPhotoR)
        } else {
            canvas.save()
            clipRoundRect(canvas, destination, SavedPlacesMapTheme.Layout.listPhotoR)
            val size = SavedPlacesMapTheme.Layout.listPhotoD
            val left = destination.left + offsetX * size * zoom
            val top = destination.top + offsetY * size * zoom
            drawBitmap(canvas, bitmap, RectF(left, top, left + size * zoom, top + size * zoom))
            canvas.restore()
        }
        drawText(canvas, title, SavedPlacesMapTheme.Layout.listTitleX, SavedPlacesMapTheme.Layout.listTitleBase + deltaY, SavedPlacesMapTheme.Type.listTitle, assets.archivoExtraBold, SavedPlacesMapTheme.Colors.ink)
        drawText(canvas, subtitle, SavedPlacesMapTheme.Layout.listSubX, SavedPlacesMapTheme.Layout.listSubBase + deltaY, SavedPlacesMapTheme.Type.listSub, assets.archivo, SavedPlacesMapTheme.Colors.cardSub)
        drawRoundedBitmap(
            canvas,
            assets.avatar,
            RectF(
                SavedPlacesMapTheme.Layout.listAvatarX,
                SavedPlacesMapTheme.Layout.listAvatarY + deltaY,
                SavedPlacesMapTheme.Layout.listAvatarX + SavedPlacesMapTheme.Layout.listAvatarD,
                SavedPlacesMapTheme.Layout.listAvatarY + deltaY + SavedPlacesMapTheme.Layout.listAvatarD,
            ),
            SavedPlacesMapTheme.Layout.listAvatarD / 2f,
        )
    }

    private fun drawCheckBadge(canvas: Canvas, centerX: Float, centerY: Float, progress: Float, reducedMotion: Boolean) {
        val badge = SavedPlacesMapTheme.VisitBadge
        val x = centerX + badge.dx
        val y = centerY + badge.dy
        val scale = if (reducedMotion) 1f else SavedPlacesMapMotion.lerp(0.9, 1.0, progress.toDouble()).toFloat()
        canvas.save()
        canvas.scale(scale, scale, x, y)
        paint.resetFill(Color.BLACK)
        paint.alpha = (255 * progress).toInt()
        canvas.drawCircle(x, y, badge.d / 2f, paint)
        paint.resetStroke(Color.WHITE, badge.ring)
        paint.alpha = (255 * progress).toInt()
        canvas.drawCircle(x, y, badge.d / 2f, paint)
        canvas.save()
        canvas.translate(x - 14.5f, y - 14.5f)
        drawTick(canvas, 0f, 0f, 1f, 1.3f, progress)
        canvas.restore()
        canvas.restore()
    }

    private fun drawMapGlyph(canvas: Canvas, kind: SavedPlacesMapMarkers.Kind, centerX: Float, centerY: Float, alpha: Float) {
        canvas.save()
        canvas.translate(centerX - kind.width / 2f, centerY - kind.height / 2f)
        when (kind) {
            SavedPlacesMapMarkers.Kind.BOOKMARK -> {
                path.reset()
                path.moveTo(3f, 3f)
                path.lineTo(25f, 3f)
                path.lineTo(25f, 29f)
                path.lineTo(14f, 22f)
                path.lineTo(3f, 29f)
                path.close()
                paint.resetFill(SavedPlacesMapTheme.Colors.bookmark)
                paint.alpha = (255 * alpha).toInt()
                canvas.drawPath(path, paint)
                paint.resetStroke(Color.WHITE, 2.4f)
                paint.strokeJoin = Paint.Join.ROUND
                paint.alpha = (255 * alpha).toInt()
                canvas.drawPath(path, paint)
            }
            SavedPlacesMapMarkers.Kind.THUMB -> {
                // viewBox 16 x 18 fitted (meet) into the box
                val fit = min(kind.width / 16f, kind.height / 18f)
                canvas.translate((kind.width - 16f * fit) / 2f, (kind.height - 18f * fit) / 2f)
                canvas.scale(fit, fit)
                path.reset()
                path.moveTo(1f, 7.4f)
                path.lineTo(4f, 7.4f)
                path.lineTo(4f, 16.6f)
                path.lineTo(1f, 16.6f)
                path.close()
                path.moveTo(5.2f, 7.6f)
                path.lineTo(8.2f, 2.2f)
                path.quadTo(9f, 0.6f, 10.4f, 1.6f)
                path.quadTo(11.4f, 2.4f, 10.9f, 4f)
                path.lineTo(10.2f, 6.2f)
                path.lineTo(14.2f, 6.2f)
                path.quadTo(15.8f, 6.4f, 15.4f, 8.2f)
                path.lineTo(14.4f, 14f)
                path.quadTo(14.1f, 15.8f, 12.4f, 16f)
                path.lineTo(5.6f, 16.6f)
                path.close()
                paint.resetFill(SavedPlacesMapTheme.Colors.thumb)
                paint.alpha = (255 * alpha).toInt()
                canvas.drawPath(path, paint)
                paint.resetStroke(Color.WHITE, 0.9f)
                paint.strokeJoin = Paint.Join.ROUND
                paint.alpha = (255 * alpha).toInt()
                canvas.drawPath(path, paint)
            }
            SavedPlacesMapMarkers.Kind.STAR -> {
                // viewBox 24 x 23 fitted (meet) into the box
                val fit = min(kind.width / 24f, kind.height / 23f)
                canvas.translate((kind.width - 24f * fit) / 2f, (kind.height - 23f * fit) / 2f)
                canvas.scale(fit, fit)
                val points = floatArrayOf(12f, 1.2f, 15.2f, 8f, 22.6f, 8.9f, 17.1f, 14f, 18.6f, 21.3f, 12f, 17.7f, 5.4f, 21.3f, 6.9f, 14f, 1.4f, 8.9f, 8.8f, 8f)
                path.reset()
                path.moveTo(points[0], points[1])
                var index = 2
                while (index < points.size) {
                    path.lineTo(points[index], points[index + 1])
                    index += 2
                }
                path.close()
                paint.resetFill(SavedPlacesMapTheme.Colors.star)
                paint.alpha = (255 * alpha).toInt()
                canvas.drawPath(path, paint)
                paint.resetStroke(Color.WHITE, 1.2f)
                paint.strokeJoin = Paint.Join.ROUND
                paint.alpha = (255 * alpha).toInt()
                canvas.drawPath(path, paint)
            }
        }
        canvas.restore()
    }

    private fun drawText(
        canvas: Canvas,
        text: String,
        x: Float,
        baseline: Float,
        size: Float,
        typeface: Typeface,
        color: Int,
        letterSpacing: Float = 0f,
        alpha: Float = 1f,
        centered: Boolean = false,
    ) {
        paint.resetFill(color)
        paint.typeface = typeface
        paint.textSize = size
        paint.letterSpacing = if (size == 0f) 0f else letterSpacing / size
        paint.alpha = (255 * alpha).toInt().coerceIn(0, 255)
        val drawX = if (centered) x - paint.measureText(text) / 2f else x
        canvas.drawText(text, drawX, baseline, paint)
        paint.letterSpacing = 0f
    }

    private fun drawCenteredTextInRow(
        canvas: Canvas,
        text: String,
        centerX: Float,
        centerY: Float,
        size: Float,
        typeface: Typeface,
        color: Int,
        alpha: Float = 1f,
    ) {
        paint.resetFill(color)
        paint.typeface = typeface
        paint.textSize = size
        paint.alpha = (255 * alpha).toInt().coerceIn(0, 255)
        val metrics = paint.fontMetrics
        val baseline = centerY - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(text, centerX - paint.measureText(text) / 2f, baseline, paint)
    }

    private fun drawEmoji(canvas: Canvas, emoji: String, centerX: Float, centerY: Float, size: Float) {
        paint.resetFill(Color.BLACK)
        paint.typeface = Typeface.DEFAULT
        paint.textSize = size
        val metrics = paint.fontMetrics
        val baseline = centerY - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(emoji, centerX - paint.measureText(emoji) / 2f, baseline, paint)
    }

    private fun drawBitmap(canvas: Canvas, bitmap: Bitmap, destination: RectF) {
        sourceRect.set(0, 0, bitmap.width, bitmap.height)
        paint.resetFill()
        paint.isFilterBitmap = true
        canvas.drawBitmap(bitmap, sourceRect, destination, paint)
    }

    private fun drawRoundedBitmap(canvas: Canvas, bitmap: Bitmap, destination: RectF, radius: Float) {
        canvas.save()
        clipRoundRect(canvas, destination, radius)
        drawBitmap(canvas, bitmap, destination)
        canvas.restore()
    }

    private fun drawShadowCircle(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int, alpha: Float, offsetY: Float, blur: Float) {
        paint.resetFill(withAlpha(color, alpha))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            paint.setShadowLayer(blur, 0f, offsetY, withAlpha(color, alpha))
            canvas.drawCircle(cx, cy, radius, paint)
            paint.clearShadowLayer()
        } else {
            // API 26-27 do not reliably rasterize blurred shape shadows on a hardware canvas.
            canvas.drawCircle(cx, cy + offsetY, radius + blur * 0.3f, paint)
        }
    }

    private fun clipRoundRect(canvas: Canvas, bounds: RectF, radius: Float) {
        scratchPath.reset()
        scratchPath.addRoundRect(bounds, radius, radius, Path.Direction.CW)
        canvas.clipPath(scratchPath)
    }

    private fun drawShadowRoundRect(canvas: Canvas, x: Float, y: Float, width: Float, height: Float, radius: Float, color: Int, alpha: Float, offsetY: Float, blur: Float) {
        paint.resetFill(withAlpha(color, alpha))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            paint.setShadowLayer(blur, 0f, offsetY, withAlpha(color, alpha))
            canvas.drawRoundRect(x, y, x + width, y + height, radius, radius, paint)
            paint.clearShadowLayer()
        } else {
            val spread = blur * 0.3f
            canvas.drawRoundRect(
                x - spread,
                y + offsetY - spread,
                x + width + spread,
                y + height + offsetY + spread,
                radius + spread,
                radius + spread,
                paint,
            )
        }
    }

    private fun drawTick(canvas: Canvas, x: Float, y: Float, progress: Float, strokeScale: Float, alpha: Float = 1f) {
        scratchPath.reset()
        tickMeasure.getSegment(0f, tickMeasure.length * progress.coerceIn(0f, 1f), scratchPath, true)
        canvas.save()
        canvas.translate(x, y)
        paint.resetStroke(Color.WHITE, SavedPlacesMapTheme.Tick.stroke * strokeScale)
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.alpha = (255 * alpha).toInt()
        canvas.drawPath(scratchPath, paint)
        canvas.restore()
    }

    private fun drawChevronLeft(canvas: Canvas, x: Float, y: Float, width: Float, height: Float) {
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(width / 11.6f, height / 20.6f)
        canvas.translate(-5.7f, -1.7f)
        path.reset()
        path.moveTo(16f, 3f)
        path.lineTo(7f, 12f)
        path.lineTo(16f, 21f)
        paint.resetStroke(Color.BLACK, 2.4f)
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    private fun drawChevronDown(canvas: Canvas, x: Float, y: Float, width: Float, height: Float) {
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(width / 18.2f, height / 10.2f)
        canvas.translate(-2.9f, -6.9f)
        path.reset()
        path.moveTo(4f, 8f)
        path.lineTo(12f, 16f)
        path.lineTo(20f, 8f)
        paint.resetStroke(Color.BLACK, 2.4f)
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    private fun drawGlobe(canvas: Canvas, x: Float, y: Float, width: Float, height: Float) {
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(width / 20.3f, height / 20.3f)
        canvas.translate(-1.85f, -1.85f)
        paint.resetStroke(Color.BLACK, 1.5f)
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawCircle(12f, 12f, 9.3f, paint)
        canvas.drawOval(7.9f, 2.7f, 16.1f, 21.3f, paint)
        canvas.drawLine(2.7f, 12f, 21.3f, 12f, paint)
        canvas.drawLine(4.2f, 7.2f, 19.8f, 7.2f, paint)
        canvas.drawLine(4.2f, 16.8f, 19.8f, 16.8f, paint)
        canvas.restore()
    }

    private fun drawPin(canvas: Canvas, x: Float, y: Float, width: Float, height: Float) {
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(width / 15.5f, height / 20f)
        canvas.translate(-4.25f, -2.45f)
        path.reset()
        path.moveTo(12f, 22f)
        path.cubicTo(8f, 17.6f, 5.2f, 14.6f, 5.2f, 10.2f)
        path.cubicTo(5.2f, 1.13f, 18.8f, 1.13f, 18.8f, 10.2f)
        path.cubicTo(18.8f, 14.6f, 16f, 17.6f, 12f, 22f)
        path.close()
        paint.resetStroke(SavedPlacesMapTheme.Colors.meta, 1.6f)
        paint.strokeJoin = Paint.Join.ROUND
        canvas.drawPath(path, paint)
        canvas.drawCircle(12f, 10.2f, 2.6f, paint)
        canvas.restore()
    }

    private fun drawBookmarkOutline(canvas: Canvas, x: Float, y: Float, width: Float, height: Float, color: Int, stroke: Float) {
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(width / 13.5f, height / 19.9f)
        canvas.translate(-5.25f, -2.05f)
        path.reset()
        path.moveTo(6.2f, 3f)
        path.lineTo(17.8f, 3f)
        path.lineTo(17.8f, 21f)
        path.lineTo(12f, 16.6f)
        path.lineTo(6.2f, 21f)
        path.close()
        paint.resetStroke(color, stroke)
        paint.strokeJoin = Paint.Join.ROUND
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    private fun drawBars(canvas: Canvas, x: Float, y: Float, width: Float, height: Float) {
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(width / 18.9f, height / 18.4f)
        canvas.translate(-3.3f, -2.8f)
        paint.resetStroke(SavedPlacesMapTheme.Colors.meta, 2f)
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(4.5f, 10f, 4.5f, 20f, paint)
        canvas.drawLine(10f, 4f, 10f, 20f, paint)
        canvas.drawLine(15.5f, 12f, 15.5f, 20f, paint)
        canvas.drawLine(21f, 15f, 21f, 20f, paint)
        canvas.restore()
    }

    private fun drawShare(canvas: Canvas, x: Float, y: Float, width: Float, height: Float) {
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(width / 18.2f, height / 19.2f)
        canvas.translate(-2.9f, -2.1f)
        paint.resetStroke(Color.BLACK, 1.8f)
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        path.reset()
        path.moveTo(12f, 15f)
        path.lineTo(12f, 3.5f)
        path.moveTo(7.6f, 7.6f)
        path.lineTo(12f, 3.2f)
        path.lineTo(16.4f, 7.6f)
        path.moveTo(8f, 10.5f)
        path.lineTo(6.2f, 10.5f)
        path.cubicTo(4.73f, 10.5f, 4f, 11.23f, 4f, 12.7f)
        path.lineTo(4f, 18f)
        path.cubicTo(4f, 19.47f, 4.73f, 20.2f, 6.2f, 20.2f)
        path.lineTo(17.8f, 20.2f)
        path.cubicTo(19.27f, 20.2f, 20f, 19.47f, 20f, 18f)
        path.lineTo(20f, 12.7f)
        path.cubicTo(20f, 11.23f, 19.27f, 10.5f, 17.8f, 10.5f)
        path.lineTo(16f, 10.5f)
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    private fun drawGridPlus(canvas: Canvas, x: Float, y: Float, width: Float, height: Float) {
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(width / 20.4f, height / 20.4f)
        canvas.translate(-1.8f, -1.8f)
        paint.resetStroke(Color.BLACK, 2.1f)
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        canvas.drawRoundRect(3f, 3f, 10.6f, 10.6f, 1.6f, 1.6f, paint)
        canvas.drawRoundRect(3f, 13.4f, 10.6f, 21f, 1.6f, 1.6f, paint)
        canvas.drawRoundRect(13.4f, 13.4f, 21f, 21f, 1.6f, 1.6f, paint)
        canvas.drawLine(17.2f, 3f, 17.2f, 10.6f, paint)
        canvas.drawLine(13.4f, 6.8f, 21f, 6.8f, paint)
        canvas.restore()
    }

    private fun drawLocateArrow(canvas: Canvas, x: Float, y: Float, size: Float) {
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(size / 24f, size / 24f)
        path.reset()
        path.moveTo(21.2f, 2.8f)
        path.lineTo(3.4f, 10.6f)
        path.quadTo(2.6f, 11f, 3.5f, 11.5f)
        path.lineTo(10f, 13.9f)
        path.lineTo(12.5f, 20.5f)
        path.quadTo(12.9f, 21.4f, 13.4f, 20.6f)
        path.close()
        paint.resetFill(SavedPlacesMapTheme.Colors.locate)
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    private fun drawShield(canvas: Canvas, x: Float, y: Float, width: Float, height: Float) {
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(width / 74f, height / 57f)
        path.reset()
        path.moveTo(37f, 1.5f)
        path.cubicTo(22f, 1.5f, 11f, 6.5f, 2f, 11f)
        path.lineTo(2f, 22f)
        path.lineTo(72f, 22f)
        path.lineTo(72f, 11f)
        path.cubicTo(63f, 6.5f, 52f, 1.5f, 37f, 1.5f)
        path.close()
        paint.resetFill(SavedPlacesMapTheme.Colors.shieldRed)
        canvas.drawPath(path, paint)
        paint.resetStroke(Color.WHITE, 3f)
        canvas.drawPath(path, paint)
        path.reset()
        path.moveTo(2f, 22f)
        path.lineTo(72f, 22f)
        path.lineTo(72f, 38f)
        path.cubicTo(72f, 48f, 56f, 54f, 37f, 55.5f)
        path.cubicTo(18f, 54f, 2f, 48f, 2f, 38f)
        path.close()
        paint.resetFill(SavedPlacesMapTheme.Colors.shieldBlue)
        canvas.drawPath(path, paint)
        paint.resetStroke(Color.WHITE, 3f)
        canvas.drawPath(path, paint)
        drawCenteredTextInRow(canvas, SavedPlacesMapTheme.Shield.text, 37f, 36f, SavedPlacesMapTheme.Shield.size, assets.barlowSemiBold, Color.WHITE)
        canvas.restore()
    }

    private fun Paint.resetFill(color: Int = Color.WHITE) {
        reset()
        isAntiAlias = true
        isSubpixelText = true
        style = Paint.Style.FILL
        this.color = color
        alpha = 255
        shader = null
    }

    private fun Paint.resetStroke(color: Int, width: Float) {
        reset()
        isAntiAlias = true
        isSubpixelText = true
        style = Paint.Style.STROKE
        this.color = color
        strokeWidth = width
        alpha = 255
        shader = null
    }

    private fun blend(from: Int, to: Int, progress: Float): Int {
        val p = progress.coerceIn(0f, 1f)
        return Color.argb(
            (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * p).toInt(),
            (Color.red(from) + (Color.red(to) - Color.red(from)) * p).toInt(),
            (Color.green(from) + (Color.green(to) - Color.green(from)) * p).toInt(),
            (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * p).toInt(),
        )
    }

    private fun withAlpha(color: Int, alpha: Float): Int {
        return Color.argb((255 * alpha).toInt().coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
    }
}
