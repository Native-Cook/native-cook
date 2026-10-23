package com.ngocdevv.nativecook.blocks.lists.raceresultstandings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.graphics.PathParser
import com.ngocdevv.nativecook.blocks.lists.raceresultstandings.RaceResultStandingsTheme as Theme
import com.ngocdevv.nativecook.blocks.lists.raceresultstandings.RaceResultStandingsMotion as Motion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Android-canvas port of the RN/Skia composition.  It deliberately owns no animation state: the
 * controller supplies one immutable [RaceResultStandingsFrame] for each frame.
 */
internal class RaceResultStandingsRenderer(private val context: Context) {
    private companion object {
        const val LAPS_DRAWN: Int = 4
        const val HEADER_DIM: Double = 0.95
        const val REPLAY_CANVAS_BOTTOM: Double = 372.0
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        isSubpixelText = true
    }
    private val path = Path()
    private val typefaces = hashMapOf<String, Typeface>()
    private var track: Bitmap? = null
    private var trackLarge: Bitmap? = null
    private var trackLoaded = false
    private var cachedWidth = -1f
    private var lineX = FloatArray(0)
    private var lineY = FloatArray(0)
    private var cumulative = FloatArray(0)
    private var totalLength = 1f
    private var backdropBitmap: Bitmap? = null
    private var backdropOrigin = Float.NaN
    private var cometBitmap: Bitmap? = null
    private val glowBitmaps = hashMapOf<String, Bitmap>()
    private data class LiveryGeometry(val streak: Path?, val bars: Path, val streakBounds: RectF)
    private val liveryGeometry = hashMapOf<Theme.MarkKind, LiveryGeometry>()
    private val liveryShaders = hashMapOf<String, Shader>()
    fun draw(
        canvas: Canvas,
        width: Float,
        height: Float,
        safeTop: Float,
        frame: RaceResultStandingsFrame,
        backPress: Double,
        trackPress: Double,
        stretch: Double,
        fade: Double,
    ) {
        val s = width / Theme.REF_W.toFloat()
        val y0 = safeTop - Theme.REF_TOP.toFloat() * s
        if (cachedWidth != width) {
            cachedWidth = width
            glowBitmaps.values.forEach(Bitmap::recycle)
            glowBitmaps.clear()
            buildLine(s)
        }
        drawBackdrop(canvas, width, height, s, y0)
        if (fade < .999) canvas.saveLayerAlpha(0f, 0f, width, height, (fade.coerceIn(0.0, 1.0) * 255).roundToInt())
        drawHeader(canvas, s, y0, frame, backPress)
        val headerAlpha = (1.0 - HEADER_DIM * Motion.headerDim(frame.rp)).toFloat()
        val headerLayer = if (headerAlpha < .999f) {
            canvas.saveLayerAlpha(0f, 0f, width, height, (headerAlpha * 255).roundToInt())
        } else {
            canvas.save()
        }
        drawHeaderCopy(canvas, s, y0)
        drawPodium(canvas, s, y0, frame)
        drawStats(canvas, s, y0, frame)
        canvas.restoreToCount(headerLayer)
        drawRulesAndSlots(canvas, s, y0, frame)
        drawRows(canvas, s, y0, frame, stretch.toFloat() * context.resources.displayMetrics.density)
        if (fade < .999) canvas.restore()
        drawTrackAndRace(canvas, s, y0, frame, trackPress)
    }

    private fun x(v: Double, s: Float) = v.toFloat() * s

    private fun y(v: Double, s: Float, y0: Float) = y0 + x(v, s)

    private fun font(face: String, size: Double, s: Float, color: Int, alpha: Float = 1f): Paint = paint.apply {
        reset()
        isAntiAlias = true
        isFilterBitmap = true
        isSubpixelText = true
        typeface = typeface(face)
        textSize = x(size, s)
        this.color = withAlpha(color, alpha)
    }

    private fun typeface(face: String): Typeface = typefaces.getOrPut(face) {
        val file = when (face) {
            Theme.FONT.medium -> "raceresultstandings_Montserrat-Medium.ttf"
            Theme.FONT.semibold -> "raceresultstandings_Montserrat-SemiBold.ttf"
            Theme.FONT.bold -> "raceresultstandings_Montserrat-Bold.ttf"
            else -> "raceresultstandings_Montserrat-ExtraBold.ttf"
        }
        Typeface.createFromAsset(context.assets, "raceresultstandings/$file")
    }

    private fun label(canvas: Canvas, text: String, px: Float, baseline: Float, size: Double, face: String, color: Int, s: Float, alpha: Float = 1f, align: Paint.Align = Paint.Align.LEFT) {
        if (alpha <= .001f) return
        font(face, size, s, color, alpha).textAlign = align
        canvas.drawText(text, px, baseline, paint)
    }

    private fun drawBackdrop(canvas: Canvas, width: Float, height: Float, s: Float, y0: Float) {
        val bitmapWidth = width.roundToInt().coerceAtLeast(1)
        val bitmapHeight = height.roundToInt().coerceAtLeast(1)
        var bitmap = backdropBitmap
        if (bitmap == null || bitmap.width != bitmapWidth || bitmap.height != bitmapHeight || backdropOrigin != y0) {
            bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
            backdropBitmap = bitmap
            backdropOrigin = y0
            val raster = Canvas(bitmap)
            val stops = Theme.backdrop.centre
            val colors = IntArray(stops.size + 1) {
                index ->
                if (index == stops.size) Theme.colors.base
                else Color.rgb(stops[index].grey, stops[index].grey, stops[index].grey)
            }
            val positions = FloatArray(stops.size + 1) {
                index ->
                if (index == stops.size) 1f else (y(stops[index].y, s, y0) / height).coerceIn(0f, 1f)
            }
            paint.reset()
            paint.isAntiAlias = true
            paint.shader = LinearGradient(0f, 0f, 0f, height, colors, positions, Shader.TileMode.CLAMP)
            raster.drawRect(0f, 0f, width, height, paint)
            val edge = Theme.backdrop.edge
            val edgeColors = IntArray(edge.size + 1) {
                index ->
                val opacity = if (index == edge.size) 0.0 else kotlin.math.round(edge[index].grey * 1.2 / 255 * 10000) / 10000
                Color.argb((opacity * 255).roundToInt(), 255, 255, 255)
            }
            val edgePositions = FloatArray(edge.size + 1) {
                index ->
                if (index == edge.size) 1f else (y(edge[index].y, s, y0) / height).coerceIn(0f, 1f)
            }
            val profile = Theme.backdrop.edgeProfile
            val profileColors = IntArray(profile.size) {
                index -> Color.argb((profile[index] * 255).roundToInt(), 255, 255, 255)
            }
            val vertical = LinearGradient(0f, 0f, 0f, height, edgeColors, edgePositions, Shader.TileMode.CLAMP)
            val horizontal = LinearGradient(0f, 0f, width, 0f, profileColors, null, Shader.TileMode.CLAMP)
            // Mask only the white edge lift, then composite that over the intact centre ramp.
            paint.reset()
            paint.shader = ComposeShader(horizontal, vertical, PorterDuff.Mode.DST_IN)
            raster.drawRect(0f, 0f, width, height, paint)
        }
        paint.reset()
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
    }

    private data class MapState(val matrix: Matrix, val scale: Float, val open: Double)

    private fun drawTrackAndRace(
        canvas: Canvas,
        s: Float,
        y0: Float,
        frame: RaceResultStandingsFrame,
        trackPress: Double,
    ) {
        if (lineX.isEmpty()) {
            return
        }
        ensureTrack()
        val state = mapState(s, y0, frame.rp, trackPress)
        val trackLayout = Theme.layout.track
        val width = x(trackLayout.w, s)
        val height = x(trackLayout.h, s)
        canvas.save()
        canvas.clipRect(0f, 0f, x(Theme.REF_W, s), y(REPLAY_CANVAS_BOTTOM, s, y0))
        canvas.save()
        canvas.concat(state.matrix)
        paint.reset()
        paint.isFilterBitmap = true
        val smallAlpha = 1.0 - Motion.clamp01(((Motion.MAP.scale - 1.0) * state.open) / Motion.MAP.hiResFrom)
        if (state.open > 0.0001) {
            paint.alpha = 255
            trackLarge?.let { bitmap ->
                canvas.drawBitmap(bitmap, null, RectF(0f, 0f, width, height), paint)
            }
        }
        if (smallAlpha > 0.001) {
            paint.alpha = (smallAlpha * 255).roundToInt()
            track?.let { bitmap ->
                canvas.drawBitmap(bitmap, null, RectF(0f, 0f, width, height), paint)
            }
        }
        drawComet(canvas, s, frame)
        drawRace(canvas, s, frame.rp, state.scale)
        canvas.restore()
        drawTags(canvas, s, frame.rp, state)
        drawLights(canvas, s, y0, frame.rp)
        canvas.restore()
    }

    private fun mapState(s: Float, y0: Float, replay: Double, trackPress: Double): MapState {
        val trackLayout = Theme.layout.track
        val open = Motion.mapOpen(replay)
        val pressScale = 1.0 - (1.0 - Motion.MAP.press) * trackPress
        val scale = ((1.0 + (Motion.MAP.scale - 1.0) * open) * pressScale).toFloat()
        val centreX = x(Motion.lerp(Motion.MAP.from.x, Motion.MAP.to.x, open), s)
        val centreY = y(Motion.lerp(Motion.MAP.from.y, Motion.MAP.to.y, open), s, y0)
        val halfWidth = x(trackLayout.w, s) / 2f
        val halfHeight = x(trackLayout.h, s) / 2f
        val tilt = Math.toRadians(sin(Math.PI * Motion.clamp01(open)) * Motion.MAP.tilt)
        // RN keeps perspective at 700 logical pixels while the circuit geometry follows width scale.
        val perspective = (Motion.MAP.perspective * context.resources.displayMetrics.density).toFloat()
        val source = floatArrayOf(
            0f,
            0f,
            halfWidth * 2f,
            0f,
            halfWidth * 2f,
            halfHeight * 2f,
            0f,
            halfHeight * 2f,
        )
        val destination = FloatArray(8)
        val corners = arrayOf(
            Pair(-halfWidth, -halfHeight),
            Pair(halfWidth, -halfHeight),
            Pair(halfWidth, halfHeight),
            Pair(-halfWidth, halfHeight),
        )
        for ((index, corner) in corners.withIndex()) {
            val depth = sin(tilt).toFloat() * corner.second * scale
            val perspectiveScale = perspective / (perspective - depth)
            destination[index * 2] = centreX + corner.first * scale * perspectiveScale
            destination[index * 2 + 1] = centreY + cos(tilt).toFloat() * corner.second * scale * perspectiveScale
        }
        val matrix = Matrix()
        check(matrix.setPolyToPoly(source, 0, destination, 0, 4))
        return MapState(matrix = matrix, scale = scale, open = open)
    }

    private fun drawComet(canvas: Canvas, s: Float, frame: RaceResultStandingsFrame) {
        val alpha = Motion.cometAlpha(frame.lap).toFloat()
        if (alpha > 0f) {
            val trackLayout = Theme.layout.track
            val pad = 32f * s
            val bitmapWidth = (x(trackLayout.w, s) + 2f * pad).roundToInt().coerceAtLeast(1)
            val bitmapHeight = (x(trackLayout.h, s) + 2f * pad).roundToInt().coerceAtLeast(1)
            var bitmap = cometBitmap
            if (bitmap == null || bitmap.width != bitmapWidth || bitmap.height != bitmapHeight) {
                bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
                cometBitmap = bitmap
            }
            bitmap.eraseColor(Color.TRANSPARENT)
            val raster = Canvas(bitmap)
            raster.translate(pad, pad)
            val end = trim(Motion.lapPlace(frame.lap))
            val glowStart = trim(Motion.lapPlace(frame.lap - Motion.GLOW_LAG))
            strokeTrim(raster, glowStart, end, 5f * s, Theme.colors.cometGlow, alpha * .4f, blur = 3f * s)
            for (index in Motion.TAIL.indices.reversed()) {
                val tail = Motion.TAIL[index]
                val start = trim(Motion.lapPlace(frame.lap - tail.lag))
                strokeTrim(raster, start, end, x(tail.width, s), Theme.colors.comet, alpha * tail.alpha.toFloat())
            }
            val head = pointAt(Motion.lapPlace(frame.lap))
            paint.reset()
            paint.isAntiAlias = true
            paint.color = withAlpha(Theme.colors.cometGlow, alpha * .65f)
            paint.maskFilter = BlurMaskFilter(sigmaToRadius(5f * s), BlurMaskFilter.Blur.NORMAL)
            raster.drawCircle(head.first, head.second, 8f * s, paint)
            paint.maskFilter = null
            paint.color = withAlpha(Theme.colors.comet, alpha)
            raster.drawCircle(head.first, head.second, 3f * s, paint)
            paint.reset()
            canvas.drawBitmap(bitmap, -pad, -pad, paint)
        }
        val flash = Motion.lineFlash(frame.lap)
        if (flash <= 0.0) {
            return
        }
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = x(Motion.FLASH.stroke, s)
        paint.color = withAlpha(Theme.colors.cometGlow, (1.0 - flash).toFloat())
        val radius = Motion.lerp(Motion.FLASH.from, Motion.FLASH.to, Motion.easeOut(flash))
        canvas.drawCircle(lineX[0], lineY[0], x(radius, s), paint)
    }

    private fun drawRace(canvas: Canvas, s: Float, replay: Double, mapScale: Float) {
        val alpha = Motion.carAlpha(replay).toFloat()
        if (alpha > 0.001f) {
            for (index in Theme.drivers.indices) {
                val endLaps = Motion.carLaps(index, replay)
                val end = lapTrim(endLaps)
                val shade = Theme.carShade[index]
                for (trail in Motion.TRAIL) {
                    val start = lapTrim(endLaps - Motion.RACE.trailLag * trail.part * Motion.leaderPace(replay))
                    strokeLapTrim(
                        canvas,
                        start,
                        end,
                        x(trail.width, s) / mapScale,
                        shade.fill,
                        alpha * trail.alpha.toFloat(),
                    )
                }
            }
            for (index in Theme.drivers.indices) {
                drawCar(canvas, index, replay, s, mapScale, alpha)
            }
        }
        val finish = Motion.finishFlash(replay)
        if (finish <= 0.0) {
            return
        }
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = x(Motion.FINISH_FLASH.stroke, s) / mapScale
        paint.color = withAlpha(Theme.colors.cometGlow, (1.0 - finish).toFloat())
        val radius = Motion.lerp(Motion.FINISH_FLASH.from, Motion.FINISH_FLASH.to, Motion.easeOut(finish))
        canvas.drawCircle(lineX[0], lineY[0], x(radius, s) / mapScale, paint)
    }

    private fun drawCar(canvas: Canvas, index: Int, replay: Double, s: Float, mapScale: Float, alpha: Float) {
        val shade = Theme.carShade[index]
        val point = carPoint(Motion.carLaps(index, replay), Motion.carLateral(index, replay) * s)
        val glow = glowBitmap(
            color = shade.fill,
            radius = x(Motion.CAR.glow, s),
            blur = x(Motion.CAR.glowBlur, s),
            alpha = Motion.CAR.glowAlpha.toFloat(),
        )
        val halfWidth = glow.width / (2f * mapScale)
        val halfHeight = glow.height / (2f * mapScale)
        paint.reset()
        paint.alpha = (alpha * 255).roundToInt()
        canvas.drawBitmap(
            glow,
            null,
            RectF(point.first - halfWidth, point.second - halfHeight, point.first + halfWidth, point.second + halfHeight),
            paint,
        )
        paint.reset()
        paint.isAntiAlias = true
        paint.color = withAlpha(shade.fill, alpha)
        canvas.drawCircle(point.first, point.second, x(Motion.CAR.core, s) / mapScale, paint)
        paint.color = withAlpha(Theme.colors.ink, alpha)
        canvas.drawCircle(point.first, point.second, x(Motion.CAR.centre, s) / mapScale, paint)
    }

    private fun drawTags(canvas: Canvas, s: Float, replay: Double, state: MapState) {
        for (index in Theme.drivers.indices) {
            val alpha = (Motion.tagAlpha(index, replay) * Motion.carAlpha(replay)).toFloat()
            if (alpha <= 0.001f) {
                continue
            }
            val driver = Theme.drivers[index]
            val shade = Theme.carShade[index]
            val local = carPoint(Motion.carLaps(index, replay), Motion.carLateral(index, replay) * s)
            val screen = floatArrayOf(local.first, local.second)
            state.matrix.mapPoints(screen)
            font(Theme.FONT.extrabold, Motion.TAG.size, s, Theme.colors.ink)
            val width = paint.measureText(driver.code) + 2f * x(Motion.TAG.padX, s)
            val height = x(Motion.TAG.h, s)
            val lift = x(Motion.TAG.above + Motion.TAG.rise * (1.0 - alpha), s)
            val left = screen[0] - width / 2f
            val top = screen[1] - lift - height
            paint.reset()
            paint.isAntiAlias = true
            paint.color = withAlpha(shade.fill, alpha)
            canvas.drawRoundRect(left, top, left + width, top + height, x(Motion.TAG.radius, s), x(Motion.TAG.radius, s), paint)
            label(
                canvas,
                driver.code,
                left + x(Motion.TAG.padX, s),
                screen[1] - lift - height / 2f + x(Motion.TAG.size * 0.36, s),
                Motion.TAG.size,
                Theme.FONT.extrabold,
                shade.ink,
                s,
                alpha,
            )
        }
    }

    private fun drawLights(canvas: Canvas, s: Float, y0: Float, replay: Double) {
        val alpha = Motion.lightsHousing(replay).toFloat()
        if (alpha <= 0.001f) {
            return
        }
        val lights = Motion.LIGHTS
        val left = x(lights.cx - lights.pill.w / 2.0, s)
        val top = y(lights.cy - lights.pill.h / 2.0, s, y0)
        val right = left + x(lights.pill.w, s)
        val bottom = top + x(lights.pill.h, s)
        paint.reset()
        paint.isAntiAlias = true
        paint.color = withAlpha(Theme.colors.plate, alpha)
        canvas.drawRoundRect(left, top, right, bottom, x(lights.pill.h / 2.0, s), x(lights.pill.h / 2.0, s), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = withAlpha(Theme.colors.plateEdge, alpha)
        canvas.drawRoundRect(left + .5f, top + .5f, right - .5f, bottom - .5f, x(lights.pill.h / 2.0, s), x(lights.pill.h / 2.0, s), paint)
        val glow = glowBitmap(
            color = Theme.colors.award,
            radius = x(lights.r * lights.glow.radius, s),
            blur = x(lights.glow.blur, s),
            alpha = lights.glow.alpha.toFloat(),
        )
        for (index in 0 until lights.count) {
            val on = Motion.lightOn(index, replay).toFloat()
            val cx = x(lights.cx + (index - (lights.count - 1) / 2.0) * lights.pitch, s)
            val cy = y(lights.cy, s, y0)
            paint.reset()
            paint.isAntiAlias = true
            paint.color = withAlpha(Theme.colors.lightOff, alpha)
            canvas.drawCircle(cx, cy, x(lights.r, s), paint)
            if (on > 0.001f) {
                paint.reset()
                paint.alpha = (alpha * on * 255).roundToInt()
                canvas.drawBitmap(glow, cx - glow.width / 2f, cy - glow.height / 2f, paint)
                paint.reset()
                paint.isAntiAlias = true
                paint.color = withAlpha(Theme.colors.award, alpha * on)
                canvas.drawCircle(cx, cy, x(lights.r, s), paint)
            }
        }
    }

    private fun drawHeader(canvas: Canvas, s: Float, y0: Float, frame: RaceResultStandingsFrame, backPress: Double) {
        val t = Theme.layout
        val scale = 1f - .03f * backPress.toFloat().coerceIn(0f, 1f)
        canvas.save()
        canvas.scale(scale, scale, x(t.back.cx, s), y(t.back.cy, s, y0))
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Theme.colors.backFill
        canvas.drawCircle(x(t.back.cx, s), y(t.back.cy, s, y0), x(t.back.d / 2.0, s), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = x(t.back.ring, s)
        paint.color = Theme.colors.backRing
        canvas.drawCircle(x(t.back.cx, s), y(t.back.cy, s, y0), x(t.back.d / 2.0 - t.back.ring / 2.0, s), paint)
        paint.color = Theme.colors.ink
        paint.strokeWidth = 2f * s
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        val c = t.back.d / 2.0
        canvas.drawLine(x(t.back.cx + c + 8.33 - t.back.d / 2.0, s), y(t.back.cy, s, y0), x(t.back.cx + c - 7.97 - t.back.d / 2.0, s), y(t.back.cy, s, y0), paint)
        polyline(canvas, floatArrayOf(x(t.back.cx - .87, s), y(t.back.cy - 6.7, s, y0), x(t.back.cx - 7.97, s), y(t.back.cy, s, y0), x(t.back.cx - .87, s), y(t.back.cy + 6.7, s, y0)), paint)
        canvas.restore()
        paint.style = Paint.Style.FILL
        val wordQ = Motion.towards(frame.from, Motion.wordProgress(frame.u))
        val raceWidth = wordWidth("Race", t.title.size, Theme.FONT.extrabold, s)
        val sprintWidth = wordWidth("Sprint", t.title.size, Theme.FONT.extrabold, s)
        val delta = sprintWidth - raceWidth
        val pressScale = 1f - .03f * frame.press.toFloat().coerceIn(0f, 1f)
        val titleLeft = t.title.x - 12.0
        val titleRight = t.sort.x + t.sort.w + 12.0
        val titleTop = t.title.baseline - Theme.ASCENT * t.title.size - 8.0
        val titleHeight = max(Theme.space.hit, 1.219 * t.title.size + 16.0)
        canvas.save()
        canvas.scale(pressScale, pressScale, x((titleLeft + titleRight) / 2.0, s), y(titleTop + titleHeight / 2.0, s, y0))
        val shift = -delta * .5f * wordQ.toFloat()
        canvas.save()
        canvas.clipRect(x(t.title.x, s) + shift, y(titleTop, s, y0), x(t.title.x, s) + shift + max(raceWidth, sprintWidth) + 4f * s, y(titleTop + 1.219 * t.title.size + 16.0, s, y0))
        label(canvas, "Race", x(t.title.x, s) + shift, y(t.title.baseline - Motion.ROW.roll * t.title.size * wordQ, s, y0), t.title.size, Theme.FONT.extrabold, Theme.colors.ink, s, 1f - wordQ.toFloat())
        label(canvas, "Sprint", x(t.title.x, s) + shift, y(t.title.baseline + Motion.ROW.roll * t.title.size * (1.0 - wordQ), s, y0), t.title.size, Theme.FONT.extrabold, Theme.colors.ink, s, wordQ.toFloat())
        canvas.restore()
        val restShift = delta * .5f * wordQ.toFloat()
        label(canvas, Theme.copy.results, x(t.title.x, s) + raceWidth + restShift, y(t.title.baseline, s, y0), t.title.size, Theme.FONT.extrabold, Theme.colors.ink, s)
        drawChevron(canvas, x(t.sort.x, s) + restShift, y(t.sort.y, s, y0) - x(Motion.PRESS.chevronOpen * frame.press, s), true, x(t.sort.stroke, s), s)
        drawChevron(canvas, x(t.sort.x, s) + restShift, y(t.sort.y + t.sort.h - 4.95, s, y0) + x(Motion.PRESS.chevronOpen * frame.press, s), false, x(t.sort.stroke, s), s)
        canvas.restore()
    }

    private fun drawHeaderCopy(canvas: Canvas, s: Float, y0: Float) {
        val layout = Theme.layout
        label(
            canvas,
            Theme.copy.round,
            x(layout.round.x, s),
            y(layout.round.baseline, s, y0),
            layout.round.size,
            Theme.FONT.semibold,
            Theme.colors.muted,
            s,
        )
        label(
            canvas,
            Theme.copy.event,
            x(layout.event.x, s),
            y(layout.event.baseline, s, y0),
            layout.event.size,
            Theme.FONT.extrabold,
            Theme.colors.ink,
            s,
        )
    }

    private fun drawPodium(canvas: Canvas, s: Float, y0: Float, frame: RaceResultStandingsFrame) {
        val pod = Theme.layout.podium
        for (slot in 0..2) for (session in 0..1) {
            val q = Motion.towards(frame.from, Motion.podiumProgress(frame.u, slot))
            val left = x(pod.markRight[slot] - pod.markW, s)
            val unit = x(pod.codeX[slot] - (pod.markRight[slot] - pod.markW) + 30.0, s)
            val shift = if (session == 0) q.toFloat() * unit else -(1f - q.toFloat()) * unit
            val top = y(pod.markY - 4.0, s, y0)
            canvas.save()
            canvas.clipRect(left + shift, top, left + shift + unit, top + x(pod.markH + 8.0, s))
            val driver = Theme.drivers[Theme.sessions[session].order[slot]]
            drawLivery(canvas, left, y(pod.markY, s, y0), s, driver.mark, Theme.teams.getValue(driver.team).mark)
            label(canvas, driver.code, x(pod.codeX[slot], s), y(pod.baseline, s, y0), pod.size, Theme.FONT.semibold, Theme.colors.ink, s)
            canvas.restore()
        }
    }

    private fun drawStats(canvas: Canvas, s: Float, y0: Float, frame: RaceResultStandingsFrame) {
        val t = Theme.layout
        val stats = t.stats
        drawStopwatch(canvas, x(20.67, s), y(245.07, s, y0), s)
        val q = Motion.towards(frame.flFrom, Motion.easeOut(frame.flU))
        canvas.save()
        canvas.clipRect(x(stats.lap.x, s), y(stats.lap.baseline - Theme.ASCENT * stats.valueSize, s, y0), x(Theme.REF_W - Theme.space.rowInset, s), y(stats.lap.baseline - Theme.ASCENT * stats.valueSize + 1.219 * stats.valueSize, s, y0))
        label(canvas, Theme.sessions[0].fastestLap, x(stats.lap.x, s), y(stats.lap.baseline - Motion.ROW.roll * stats.valueSize * q, s, y0), stats.valueSize, Theme.FONT.semibold, Theme.colors.ink, s, 1f - q.toFloat())
        label(canvas, Theme.sessions[1].fastestLap, x(stats.lap.x, s), y(stats.lap.baseline + Motion.ROW.roll * stats.valueSize * (1.0 - q), s, y0), stats.valueSize, Theme.FONT.semibold, Theme.colors.ink, s, q.toFloat())
        canvas.restore()
        label(canvas, Theme.copy.lapLabel, x(stats.lap.labelX, s), y(stats.lap.baseline + Theme.space.labelGap, s, y0), stats.labelSize, Theme.FONT.medium, Theme.colors.ink, s)
        drawAward(canvas, x(20.67, s), y(292.7, s, y0), s)
        label(canvas, Theme.copy.dayValue, x(stats.day.x, s), y(stats.day.baseline, s, y0), stats.valueSize, Theme.FONT.semibold, Theme.colors.ink, s)
        label(canvas, Theme.copy.dayLabel, x(stats.day.labelX, s), y(stats.day.baseline + Theme.space.labelGap, s, y0), stats.labelSize, Theme.FONT.medium, Theme.colors.ink, s)
        drawWheel(canvas, x(193.83, s), y(293.67, s, y0), s)
        label(canvas, Theme.copy.pitValue, x(stats.pit.x, s), y(stats.pit.baseline, s, y0), stats.valueSize, Theme.FONT.semibold, Theme.colors.ink, s)
        label(canvas, Theme.copy.pitLabel, x(stats.pit.labelX, s), y(stats.pit.baseline + Theme.space.labelGap, s, y0), stats.labelSize, Theme.FONT.medium, Theme.colors.ink, s)
    }

    private fun drawRulesAndSlots(canvas: Canvas, s: Float, y0: Float, frame: RaceResultStandingsFrame) {
        val rows = Theme.layout.rows
        val space = Theme.space
        paint.reset()
        paint.color = Theme.colors.hairline
        canvas.drawRect(x(space.headerRuleInset, s), y(Theme.layout.headerRule.y, s, y0) - .5f, x(Theme.REF_W - space.headerRuleInset, s), y(Theme.layout.headerRule.y, s, y0) + .5f, paint)
        for (slot in 0..5) {
            val base = slot * space.rowPitch
            label(canvas, "0${slot + 1}", x(rows.numberX, s), y(rows.numberBaseline + base, s, y0), rows.numberSize, Theme.FONT.bold, Theme.colors.ink, s)
            val replay = Motion.replayPoints(slot, frame.rp)
            val replayLayer = if (replay.alpha < 0.999) {
                canvas.saveLayerAlpha(
                    0f,
                    y(rows.numberBaseline + base - rows.pointsSize, s, y0),
                    x(Theme.REF_W, s),
                    y(rows.firstRule + base, s, y0),
                    (replay.alpha * 255).roundToInt(),
                )
            } else {
                canvas.save()
            }
            canvas.translate(0f, x(Motion.ROW.roll * rows.pointsSize * replay.rise, s))
            val q = Motion.towards(frame.from, Motion.pointsProgress(frame.u, slot)).toFloat()
            val dir = if (Theme.sessions[1].points[slot] < Theme.sessions[0].points[slot]) 1f else -1f
            val dy = x(Motion.ROW.roll * rows.pointsSize, s)
            canvas.save()
            canvas.clipRect(x(rows.pointsRight - 120.0, s), y(rows.pointsBaseline + base - Theme.ASCENT * rows.pointsSize, s, y0), x(rows.pointsRight, s), y(rows.pointsBaseline + base - Theme.ASCENT * rows.pointsSize + 1.219 * rows.pointsSize, s, y0))
            label(canvas, Theme.sessions[0].points[slot].toString(), x(rows.pointsRight, s), y(rows.pointsBaseline + base, s, y0) + dir * dy * q, rows.pointsSize, Theme.FONT.extrabold, Theme.colors.ink, s, 1f - q, Paint.Align.RIGHT)
            label(canvas, Theme.sessions[1].points[slot].toString(), x(rows.pointsRight, s), y(rows.pointsBaseline + base, s, y0) - dir * dy * (1f - q), rows.pointsSize, Theme.FONT.extrabold, Theme.colors.ink, s, q, Paint.Align.RIGHT)
            canvas.restore()
            label(canvas, Theme.copy.pts, x(rows.ptsRight, s), y(rows.ptsBaseline + base, s, y0), rows.ptsSize, Theme.FONT.semibold, Theme.colors.ink, s, align = Paint.Align.RIGHT)
            canvas.restoreToCount(replayLayer)
            paint.color = Theme.colors.hairline
            canvas.drawRect(x(space.rowInset, s), y(rows.firstRule + base, s, y0) - .5f, x(Theme.REF_W - space.rowInset, s), y(rows.firstRule + base, s, y0) + .5f, paint)
        }
    }

    private fun drawRows(canvas: Canvas, s: Float, y0: Float, frame: RaceResultStandingsFrame, stretchPx: Float) {
        val rows = Theme.layout.rows
        val space = Theme.space
        data class Pose(
            val index: Int,
            val z: Int,
            val y: Float,
            val arc: Float,
            val lean: Float,
            val scale: Float,
            val alpha: Float,
            val bump: Double,
            val badgeProgress: Double,
            val rising: Boolean,
            val speed: Double,
        )
        val poses = Theme.drivers.indices.map {
            index ->
            val row: Double
            val speed: Double
            val bump: Double
            val rising: Boolean
            val moving: Boolean
            val badgeProgress: Double
            if (frame.rp > 0.0) {
                val replay = Motion.replayRow(index, frame.rp)
                row = replay.y
                speed = replay.speed * space.rowPitch
                bump = replay.bump
                rising = replay.direction < 0
                moving = replay.direction != 0
                badgeProgress = 0.0
            } else {
                val fromSlot = Theme.SLOT_OF[frame.from][index]
                val toSlot = Theme.SLOT_OF[1 - frame.from][index]
                val progress = Motion.rowProgress(frame.u, toSlot)
                row = fromSlot + (toSlot - fromSlot) * progress
                speed = Motion.rowSpeed(frame.u, toSlot) * (toSlot - fromSlot) * space.rowPitch
                moving = fromSlot != toSlot
                rising = toSlot < fromSlot
                bump = if (moving) sin(PI * Motion.clamp01(progress)) else 0.0
                badgeProgress = progress
            }
            val lane = if (rising) Motion.ROW.laneRise else Motion.ROW.laneFall
            Pose(
                index = index,
                z = if (rising) 3 else if (moving) 1 else 2,
                y = (row * space.rowPitch).toFloat(),
                arc = (if (moving) bump * lane else 0.0).toFloat(),
                lean = (speed / Motion.ROW.leanSpeed).coerceIn(-1.0, 1.0).toFloat() * Motion.ROW.leanMax.toFloat(),
                scale = (if (rising) 1.0 + Motion.ROW.liftRise * bump else 1.0 - Motion.ROW.sinkFall * bump).toFloat(),
                alpha = (if (!rising) 1.0 - Motion.ROW.dimFall * bump else 1.0).toFloat(),
                bump = bump,
                badgeProgress = badgeProgress,
                rising = rising,
                speed = speed,
            )
        }
        .sortedBy {
            it.z
        }
        poses.forEach {
            pose ->
            val driver = Theme.drivers[pose.index]
            val team = Theme.teams.getValue(driver.team)
            val anchorX = x(rows.nameX, s)
            val anchorY = y((rows.nameBaseline + rows.teamBaseline) / 2 - 6, s, y0)
            canvas.save()
            canvas.translate(stretchPx + pose.arc * s, y(rows.firstRule - space.rowPitch, s, y0) + pose.y * s)
            canvas.rotate(pose.lean, anchorX, anchorY - y(rows.firstRule - space.rowPitch, s, y0))
            canvas.scale(pose.scale, pose.scale, anchorX, anchorY - y(rows.firstRule - space.rowPitch, s, y0))
            val localMid = x(((rows.nameBaseline + rows.teamBaseline) / 2 - 6) - (rows.firstRule - space.rowPitch), s)
            val rowLayer = if (pose.alpha < 1f) {
                canvas.saveLayerAlpha(0f, -x(rows.trailMax, s), widthForLayer(s), x(space.rowPitch + rows.trailMax, s), (pose.alpha * 255f).roundToInt())
            } else canvas.save()
            if (pose.rising) {
                paint.reset()
                paint.isAntiAlias = true
                paint.color = Theme.colors.base
                canvas.drawRoundRect(x(rows.plate.x, s), x(rows.plate.inset, s), x(rows.plate.x + rows.plate.w, s), x(space.rowPitch - rows.plate.inset, s), x(rows.plate.radius, s), x(rows.plate.radius, s), paint)
                val lit = pose.bump.toFloat()
                paint.color = withAlpha(Theme.colors.plate, lit)
                canvas.drawRoundRect(x(rows.plate.x, s), x(rows.plate.inset, s), x(rows.plate.x + rows.plate.w, s), x(space.rowPitch - rows.plate.inset, s), x(rows.plate.radius, s), x(rows.plate.radius, s), paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                paint.color = withAlpha(Theme.colors.plateEdge, lit)
                canvas.drawRoundRect(x(rows.plate.x, s) + .5f, x(rows.plate.inset, s) + .5f, x(rows.plate.x + rows.plate.w, s) - .5f, x(space.rowPitch - rows.plate.inset, s) - .5f, x(rows.plate.radius, s) - .5f, x(rows.plate.radius, s) - .5f, paint)
                paint.style = Paint.Style.FILL
            }
            val length = min(rows.trailMax, abs(pose.speed) * Motion.ROW.trailPerSpeed)
            val trailAlpha = Motion.ROW.trailAlpha * Motion.clamp01(abs(pose.speed) / Motion.ROW.trailAlphaSpeed)
            if (trailAlpha > .001) {
                val down = pose.speed < 0
                paint.reset()
                paint.isAntiAlias = true
                paint.shader = LinearGradient(0f, localMid, 0f, localMid + (if (down) 1 else -1) * x(length, s), withAlpha(team.text, trailAlpha.toFloat()), withAlpha(team.text, 0f), Shader.TileMode.CLAMP)
                canvas.drawRect(x(rows.trailX, s), localMid - if (down) 0f else x(length, s), x(rows.trailX + rows.trailW, s), localMid + if (down) x(length, s) else 0f, paint)
                paint.shader = null
            }
            val anchor = rows.firstRule - space.rowPitch
            label(canvas, driver.name, x(rows.nameX, s), x(rows.nameBaseline - anchor, s), rows.nameSize, Theme.FONT.bold, Theme.colors.ink, s)
            label(canvas, team.name, x(rows.teamX, s), x(rows.teamBaseline - anchor, s), rows.teamSize, Theme.FONT.semibold, team.text, s)
            for (session in 0..1) {
                drawBadge(canvas, pose.index, session, s, anchor, frame, pose.badgeProgress)
            }
            canvas.restoreToCount(rowLayer)
            canvas.restore()
        }
    }

    private fun drawBadge(canvas: Canvas, index: Int, session: Int, s: Float, anchor: Double, frame: RaceResultStandingsFrame, rowProgress: Double) {
        val rows = Theme.layout.rows
        val delta = Theme.sessions[session].delta[index]
        val q = Motion.towards(frame.from, Motion.easeOut(Motion.seg(rowProgress, Motion.WINDOWS.badge.from, Motion.WINDOWS.badge.to))).toFloat()
        val show = if (session == 0) 1f - q else q
        if (show <= .001f) return
        val dy = (1f - show) * 3f * s * if (session == 0) -1 else 1
        drawChangeBadge(canvas, x(rows.badge.x, s), x(rows.badge.y - anchor, s) + dy, s, delta, show)
        label(canvas, abs(delta).toString(), x(rows.digitX, s), x(rows.digitBaseline - anchor, s) + dy, rows.digitSize, Theme.FONT.semibold, Theme.colors.ink, s, show)
    }

    private fun drawChevron(canvas: Canvas, px: Float, py: Float, up: Boolean, stroke: Float, s: Float) {
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Theme.colors.ink
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        polyline(canvas, if (up) floatArrayOf(px + .65f * s, py + 4.3f * s, px + 4.17f * s, py + .65f * s, px + 7.68f * s, py + 4.3f * s) else floatArrayOf(px + .65f * s, py + .65f * s, px + 4.17f * s, py + 4.3f * s, px + 7.68f * s, py + .65f * s), paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawStopwatch(canvas: Canvas, px: Float, py: Float, s: Float) {
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * s
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = Theme.colors.stopwatch
        canvas.drawCircle(px + 12f * s, py + 13.6f * s, 9.55f * s, paint)
        canvas.drawLine(px + 12.33f * s, py + 4.05f * s, px + 12.33f * s, py + 2.2f * s, paint)
        canvas.drawLine(px + 9.6f * s, py + 1.35f * s, px + 14.7f * s, py + 1.35f * s, paint)
        canvas.drawLine(px + 18.9f * s, py + 6.7f * s, px + 20.2f * s, py + 5.3f * s, paint)
        polyline(canvas, floatArrayOf(px + 13.2f*s, py+9.2f*s, px+10.2f*s, py+12.3f*s, px+13.9f*s, py+14.3f*s, px+10.6f*s, py+17.8f*s), paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawAward(canvas: Canvas, px: Float, py: Float, s: Float) {
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f*s
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = Theme.colors.award
        canvas.drawCircle(px+12*s, py+9.8f*s, 6.25f*s, paint)
        polyline(canvas, floatArrayOf(px+9.6f*s, py+15.3f*s, px+3.3f*s, py+18.3f*s, px+6.7f*s, py+19.2f*s, px+7.9f*s, py+22.2f*s, px+12*s, py+17.1f*s), paint)
        polyline(canvas, floatArrayOf(px+14.4f*s, py+15.3f*s, px+20.7f*s, py+18.3f*s, px+17.3f*s, py+19.2f*s, px+16.1f*s, py+22.2f*s, px+12*s, py+17.1f*s), paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawWheel(canvas: Canvas, px: Float, py: Float, s: Float) {
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Theme.colors.wheel
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.7f*s
        canvas.drawCircle(px+12*s, py+12*s, 10.3f*s, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(px+12*s, py+12*s, 6.35f*s, paint)
        paint.color = Theme.colors.shade
        canvas.drawCircle(px+12*s, py+12*s, 4.75f*s, paint)
        paint.color = Theme.colors.wheel
        paint.strokeWidth = 1.25f*s
        paint.style = Paint.Style.STROKE
        repeat(8) { index ->
            val angle = index * PI / 4 + PI / 8
            canvas.drawLine(px+(12+cos(angle)*2.1).toFloat()*s, py+(12+sin(angle)*2.1).toFloat()*s, px+(12+cos(angle)*5).toFloat()*s, py+(12+sin(angle)*5).toFloat()*s, paint)
        }
        paint.style = Paint.Style.FILL
        canvas.drawCircle(px+12*s, py+12*s, 2.15f*s, paint)
        paint.color = Theme.colors.shade
        canvas.drawCircle(px+12*s, py+12*s, 1.05f*s, paint)
    }

    private fun drawChangeBadge(canvas: Canvas, px: Float, py: Float, s: Float, delta: Int, alpha: Float) {
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (if(delta==0)1f else 1.3f)*s
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = withAlpha(if(delta<0)Theme.colors.down else Theme.colors.up, alpha)
        fun line(vararg p: Float) = polyline(canvas, p, paint)
        if(delta==0){
            canvas.drawLine(px+.5f*s, py+2.5f*s, px+6.17f*s, py+2.5f*s, paint)
            canvas.drawLine(px+.5f*s, py+4.6f*s, px+6.17f*s, py+4.6f*s, paint)
        }
        else if(delta>0){
            line(px+.6f*s, py+3.15f*s, px+3.33f*s, py+.6f*s, px+6.07f*s, py+3.15f*s)
            line(px+.6f*s, py+6.75f*s, px+3.33f*s, py+4.2f*s, px+6.07f*s, py+6.75f*s)
        }
        else{
            line(px+.6f*s, py+.6f*s, px+3.33f*s, py+3.15f*s, px+6.07f*s, py+.6f*s)
            line(px+.6f*s, py+4.2f*s, px+3.33f*s, py+6.75f*s, px+6.07f*s, py+4.2f*s)
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawLivery(canvas: Canvas, left: Float, top: Float, s: Float, mark: Theme.MarkKind, color: Int) {
        val geometry = liveryGeometry.getOrPut(mark) {
            val definition = Theme.marks.getValue(mark)
            val streak = definition.streak?.let(PathParser::createPathFromPathData)
            val bounds = RectF()
            streak?.computeBounds(bounds, true)
            LiveryGeometry(
                streak = streak,
                bars = requireNotNull(PathParser.createPathFromPathData(definition.bars)),
                streakBounds = bounds,
            )
        }
        canvas.save()
        canvas.translate(left, top)
        canvas.scale(s, s)
        canvas.clipRect(0f, 0f, 30f, 8.33f)
        paint.reset()
        paint.isAntiAlias = true
        geometry.streak?.let {
            streak ->
            val shaderKey = "$mark:$color"
            paint.shader = liveryShaders.getOrPut(shaderKey) {
                LinearGradient(
                    geometry.streakBounds.left,
                    0f,
                    geometry.streakBounds.right,
                    0f,
                    intArrayOf(withAlpha(color, 0f), withAlpha(color, .9f), color),
                    floatArrayOf(0f, .75f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawPath(streak, paint)
            paint.shader = null
        }
        paint.color = color
        canvas.drawPath(geometry.bars, paint)
        canvas.restore()
    }

    private fun ensureTrack() {
        if (trackLoaded) {
            return
        }
        trackLoaded = true
        track = context.assets.open("raceresultstandings/raceresultstandings_track.png").use(BitmapFactory::decodeStream)
        trackLarge = context.assets.open("raceresultstandings/raceresultstandings_track-large.png").use(BitmapFactory::decodeStream)
    }

    private fun buildLine(s: Float) {
        val n = RaceResultStandingsTrackLine.LINE_POINTS
        lineX = FloatArray(n+1)
        lineY = FloatArray(n+1)
        cumulative = FloatArray(n+1)
        val k = x(Theme.layout.track.w, s)/RaceResultStandingsTrackLine.LINE_CANVAS.w
        for(i in 0..n){
            val j = (i%n)*2
            lineX[i] = (RaceResultStandingsTrackLine.LINE_XY[j].toFloat()*k)
            lineY[i] = (RaceResultStandingsTrackLine.LINE_XY[j+1].toFloat()*k)
            if(i>0)cumulative[i] = cumulative[i-1]+hypot(lineX[i]-lineX[i-1], lineY[i]-lineY[i-1])
        }
        totalLength = cumulative[n]
    }

    private fun trim(place: Double):Float {
        val f = (place*RaceResultStandingsTrackLine.LINE_POINTS).coerceIn(0.0, RaceResultStandingsTrackLine.LINE_POINTS.toDouble())
        val i = min(RaceResultStandingsTrackLine.LINE_POINTS-1, floor(f).toInt())
        return (cumulative[i]+(cumulative[i+1]-cumulative[i])*(f-i).toFloat())/totalLength
    }

    private fun pointAt(place: Double):Pair<Float, Float>{
        val f = (place*RaceResultStandingsTrackLine.LINE_POINTS).coerceIn(0.0, RaceResultStandingsTrackLine.LINE_POINTS.toDouble())
        val i = min(RaceResultStandingsTrackLine.LINE_POINTS-1, floor(f).toInt())
        val q = (f-i).toFloat()
        return Pair(lineX[i]+(lineX[i+1]-lineX[i])*q, lineY[i]+(lineY[i+1]-lineY[i])*q)
    }

    private fun carPoint(laps: Double, side: Double): Pair<Float, Float> {
        val normalized = ((laps % 1.0) + 1.0) % 1.0
        val exact = normalized * RaceResultStandingsTrackLine.LINE_POINTS
        val index = min(RaceResultStandingsTrackLine.LINE_POINTS - 1, floor(exact).toInt())
        val fraction = (exact - index).toFloat()
        val px = lineX[index] + (lineX[index + 1] - lineX[index]) * fraction
        val py = lineY[index] + (lineY[index + 1] - lineY[index]) * fraction
        val tangentX = lineX[index + 1] - lineX[index]
        val tangentY = lineY[index + 1] - lineY[index]
        val length = hypot(tangentX, tangentY).coerceAtLeast(1f)
        return Pair(
            px - tangentY / length * side.toFloat(),
            py + tangentX / length * side.toFloat(),
        )
    }

    private fun lapTrim(laps: Double): Float {
        val value = (laps + 1.0).coerceIn(0.0, LAPS_DRAWN - 0.0001)
        val whole = floor(value).toInt()
        val exact = (value - whole) * RaceResultStandingsTrackLine.LINE_POINTS
        val index = min(RaceResultStandingsTrackLine.LINE_POINTS - 1, floor(exact).toInt())
        val fraction = (exact - index).toFloat()
        val along = cumulative[index] + (cumulative[index + 1] - cumulative[index]) * fraction
        return (whole * totalLength + along) / (LAPS_DRAWN * totalLength)
    }

    private fun strokeLapTrim(
        canvas: Canvas,
        start: Float,
        end: Float,
        width: Float,
        color: Int,
        alpha: Float,
    ) {
        if (end <= start || alpha <= 0.001f) {
            return
        }
        path.reset()
        var began = false
        for (lap in 0 until LAPS_DRAWN) {
            for (index in 0 until RaceResultStandingsTrackLine.LINE_POINTS) {
                val a = (lap * totalLength + cumulative[index]) / (LAPS_DRAWN * totalLength)
                val b = (lap * totalLength + cumulative[index + 1]) / (LAPS_DRAWN * totalLength)
                if (b < start || a > end) {
                    continue
                }
                val from = max(start, a)
                val to = min(end, b)
                val startFraction = (from - a) / (b - a)
                val endFraction = (to - a) / (b - a)
                val ax = lineX[index] + (lineX[index + 1] - lineX[index]) * startFraction
                val ay = lineY[index] + (lineY[index + 1] - lineY[index]) * startFraction
                val bx = lineX[index] + (lineX[index + 1] - lineX[index]) * endFraction
                val by = lineY[index] + (lineY[index + 1] - lineY[index]) * endFraction
                if (!began) {
                    path.moveTo(ax, ay)
                    began = true
                }
                path.lineTo(bx, by)
            }
        }
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = width
        paint.color = withAlpha(color, alpha)
        canvas.drawPath(path, paint)
    }

    private fun glowBitmap(color: Int, radius: Float, blur: Float, alpha: Float): Bitmap {
        val key = "$color:${radius.roundToInt()}:${blur.roundToInt()}:${(alpha * 1000).roundToInt()}"
        return glowBitmaps.getOrPut(key) {
            val half = (radius + blur * 2f).roundToInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(half * 2, half * 2, Bitmap.Config.ARGB_8888)
            val raster = Canvas(bitmap)
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            glowPaint.color = withAlpha(color, alpha)
            glowPaint.maskFilter = BlurMaskFilter(sigmaToRadius(blur), BlurMaskFilter.Blur.NORMAL)
            raster.drawCircle(half.toFloat(), half.toFloat(), radius, glowPaint)
            bitmap
        }
    }

    private fun strokeTrim(canvas: Canvas, start: Float, end: Float, width: Float, color: Int, alpha: Float, blur: Float = 0f){
        if(end<=start)return
        path.reset()
        var began = false
        for(i in 0 until RaceResultStandingsTrackLine.LINE_POINTS){
            val a = cumulative[i]/totalLength
            val b = cumulative[i+1]/totalLength
            if(b<start||a>end)continue
            val from = max(start, a)
            val to = min(end, b)
            val q0 = (from-a)/(b-a)
            val q1 = (to-a)/(b-a)
            val ax = lineX[i]+(lineX[i+1]-lineX[i])*q0
            val ay = lineY[i]+(lineY[i+1]-lineY[i])*q0
            val bx = lineX[i]+(lineX[i+1]-lineX[i])*q1
            val by = lineY[i]+(lineY[i+1]-lineY[i])*q1
            if(!began){
                path.moveTo(ax, ay)
                began = true
            }
            path.lineTo(bx, by)
        }
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = width
        paint.color = withAlpha(color, alpha)
        paint.maskFilter = if(blur>0)BlurMaskFilter(sigmaToRadius(blur), BlurMaskFilter.Blur.NORMAL)else null
        canvas.drawPath(path, paint)
        paint.maskFilter = null
        paint.style = Paint.Style.FILL
    }

    private fun polyline(canvas: Canvas, values: FloatArray, paint: Paint){
        path.reset()
        path.moveTo(values[0], values[1])
        var i = 2
        while(i<values.size){
            path.lineTo(values[i], values[i+1])
            i+=2
        }
        canvas.drawPath(path, paint)
    }

    private fun withAlpha(color: Int, alpha: Float):Int = Color.argb((Color.alpha(color)*alpha.coerceIn(0f, 1f)).roundToInt(), Color.red(color), Color.green(color), Color.blue(color))

    private fun wordWidth(word: String, size: Double, face: String, s: Float):Float {
        font(face, size, s, Theme.colors.ink)
        return paint.measureText(word)
    }

    private fun widthForLayer(s: Float): Float = Theme.REF_W.toFloat() * s

    private fun sigmaToRadius(sigma: Float): Float = ((sigma - .5f) / .57735f).coerceAtLeast(.01f)
}
