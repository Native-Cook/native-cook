package com.ngocdevv.nativecook.blocks.onboarding.buzzstreaks

import android.content.res.AssetManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/** Sharp silhouettes and independently moving keycaps, sampled from the scene clock. */
internal class BuzzStreaksMascots(assets: AssetManager) {
    private val characters: Array<Character>
    private val sparks: Array<Array<Spark>>
    private val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        fun json(name: String): JSONObject {
            return JSONObject(assets.open("buzzstreaks/buzzstreaks_$name.json").bufferedReader().use { it.readText() })
        }
        val geometry = json("geometry")
        val rig = json("rig").getJSONObject("characters")
        val keys = json("key-vectors").getJSONObject("characters")
        val bodies = geometry.getJSONObject("characters")
        val colors = geometry.getJSONObject("colors")
        val pointScale = geometry.getDouble("pointScale").toFloat()
        characters = arrayOf("orange", "pink").map { kind ->
            val rgb = colors.getJSONArray(kind)
            Character(kind, bodies.getJSONObject(kind), rig.getJSONObject(kind),
                Color.rgb(rgb.getInt(0), rgb.getInt(1), rgb.getInt(2)), pointScale, keys.getJSONObject(kind))
        }.toTypedArray()
        val sparkData = json("sparks")
        val sparkRGB = sparkData.getJSONArray("fillRGB")
        sparkPaint.color = Color.rgb(sparkRGB.getInt(0), sparkRGB.getInt(1), sparkRGB.getInt(2))
        val sparkFrames = sparkData.getJSONArray("frames")
        sparks = Array(sparkFrames.length()) { frame ->
            val glyphs = sparkFrames.getJSONArray(frame)
            Array(glyphs.length()) { index ->
                val glyph = glyphs.getJSONObject(index)
                val vertices = glyph.getJSONArray("points")
                val path = Path()
                for (vertex in 0 until vertices.length()) {
                    val point = vertices.getJSONArray(vertex)
                    val x = point.getDouble(0).toFloat()
                    val y = point.getDouble(1).toFloat()
                    if (vertex == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                Spark(path, (glyph.getDouble("opacity") * 255).roundToInt().coerceIn(0, 255))
            }
        }
    }

    fun draw(time: Double, canvas: Canvas, signature: BuzzStreaksSignature? = null) {
        val frame = (time * 60 + 1e-7).toInt().coerceIn(0, sparks.lastIndex)
        for (spark in sparks[frame]) {
            sparkPaint.alpha = spark.alpha
            canvas.drawPath(spark.path, sparkPaint)
        }
        for (character in characters) character.draw(time, canvas, signature)
    }

    private class Spark(val path: Path, val alpha: Int)

    private class KeyDrawing(values: JSONObject) {
        val width = values.getJSONArray("baseline_size").getDouble(0).toFloat()
        val height = values.getJSONArray("baseline_size").getDouble(1).toFloat()
        private val outer = softPath(values.getJSONArray("outer"))
        private val face = softPath(values.getJSONArray("face"))
        private val letters = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            val contours = values.getJSONArray("letterCurves")
            for (index in 0 until contours.length()) {
                val curve = contours.getJSONArray(index)
                if (curve.length() == 0) continue
                moveTo(curve.getDouble(0).toFloat(), curve.getDouble(1).toFloat())
                var coordinate = 2
                while (coordinate < curve.length()) {
                    cubicTo(curve.getDouble(coordinate).toFloat(), curve.getDouble(coordinate + 1).toFloat(),
                        curve.getDouble(coordinate + 2).toFloat(), curve.getDouble(coordinate + 3).toFloat(),
                        curve.getDouble(coordinate + 4).toFloat(), curve.getDouble(coordinate + 5).toFloat())
                    coordinate += 6
                }
                close()
            }
        }
        private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = keyGradient(values.getJSONObject("outer_gradient")) }
        private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = keyGradient(values.getJSONObject("face_gradient")) }
        private val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = rgb(values.getJSONArray("ink_rgb")) }

        fun draw(canvas: Canvas, opacity: Float, sx: Float, sy: Float, kind: String, time: Double,
            signature: BuzzStreaksSignature?) {
            val inset = if (sy > 0f) (signature?.contactInset(kind, time) ?: 0f) / sy else 0f
            val reflection = if (kind == "pink") signature?.reflectionAlpha(kind, time) ?: 0f else 0f
            val alpha = (opacity.coerceIn(0f, 1f) * 255).roundToInt()
            outerPaint.alpha = alpha
            facePaint.alpha = alpha
            inkPaint.alpha = alpha
            canvas.drawPath(outer, outerPaint)
            if (inset == 0f && reflection == 0f) {
                canvas.drawPath(face, facePaint)
                canvas.drawPath(letters, inkPaint)
                return
            }
            val saved = canvas.save()
            canvas.translate(0f, inset)
            canvas.drawPath(face, facePaint)
            if (reflection > 0f && sx > 0f && sy > 0f) {
                val reflected = canvas.save()
                canvas.clipPath(face)
                canvas.scale(1f / sx, 1f / sy)
                signature?.drawReflection(kind, time, opacity, canvas)
                canvas.restoreToCount(reflected)
            }
            canvas.drawPath(letters, inkPaint)
            canvas.restoreToCount(saved)
        }
    }

    private class Character(
        private val kind: String,
        body: JSONObject,
        rig: JSONObject,
        color: Int,
        pointScale: Float,
        drawing: JSONObject,
    ) {
        private val pointCounts = body.getJSONArray("paths").let { paths ->
            IntArray(paths.length()) { index -> paths.getJSONArray(index).length() / 2 }
        }
        private val curves = body.getJSONArray("paths").let { paths ->
            Array(paths.length()) { index ->
                val values = paths.getJSONArray(index)
                closedCubic(DoubleArray(values.length()) { coordinate -> values.getDouble(coordinate) / pointScale.toDouble() })
            }
        }
        private val pathBank = Array(curves.size) { index -> cubicPath(curves[index]) }
        private val primary = indices(body.getJSONArray("primary"))
        private val secondary = body.getJSONObject("secondary").let { islands ->
            islands.keys().asSequence().sorted().map { name -> indices(islands.getJSONArray(name)) }.toList()
        }
        private val key = track(rig.getJSONArray("key"))
        private val face = track(rig.getJSONArray("face"))
        private val faceBaselineAngle = rig.getDouble("faceBaselineAngle").toFloat()
        private val arm = track(rig.getJSONArray("arm"))
        private val bodyGradients = rig.getJSONArray("bodyGradient").let { frames ->
            val values = FloatArray(10)
            Array(frames.length()) { index ->
                val frame = frames.getJSONArray(index)
                for (channel in values.indices) values[channel] = frame.getDouble(channel).toFloat()
                LinearGradient(values[0], values[1], values[2], values[3],
                    gradientColor(values, 4), gradientColor(values, 7), Shader.TileMode.CLAMP)
            }
        }
        private val keyDrawing = KeyDrawing(drawing)
        private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE }
        private val pupilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.BLACK }
        private val smilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 1.25f
            strokeCap = Paint.Cap.ROUND
        }
        private val silhouette = Path()
        private val islands = Array(secondary.size) { Path() }
        private val eyePath = Path()
        private val eyeRect = RectF()
        private val smile = Path().apply {
            if (kind == "orange") {
                moveTo(-17f, 12f)
                quadTo(-10f, 21f, -3f, 13f)
            } else {
                moveTo(-2f, 18f)
                quadTo(5f, 23f, 10f, 16f)
            }
        }
        private val keyValue = FloatArray(6)
        private val faceValue = FloatArray(15)
        private val armValue = FloatArray(5)

        fun draw(time: Double, canvas: Canvas, signature: BuzzStreaksSignature?) {
            val frame = (time * 60).coerceIn(0.0, (primary.size - 1).toDouble())
            val current = frame.toInt()
            val next = min(current + 1, primary.lastIndex)
            val pathProgress = frame - current
            val progress = pathProgress.toFloat()
            // Colors/endpoints stay at source 60 Hz while geometry interpolates below.
            // Cached native shaders avoid allocating one per character per display frame.
            // Every body island and the foreground arm share this exact shader.
            bodyPaint.shader = bodyGradients[(frame + 1e-7).toInt().coerceIn(0, bodyGradients.lastIndex)]
            updatePath(silhouette, primary, current, next, pathProgress)
            for (index in secondary.indices) updatePath(islands[index], secondary[index], current, next, pathProgress)
            drawBody(canvas)
            if (kind == "orange") {
                val reflection = signature?.reflectionAlpha(kind, time) ?: 0f
                if (reflection > 0f) {
                    val saved = canvas.save()
                    canvas.clipPath(silhouette)
                    signature?.drawReflection(kind, time, 1f, canvas)
                    canvas.restoreToCount(saved)
                }
            }

            sample(key, keyValue, current, next, progress)
            if (keyValue[5] > 0.001f) {
                val saved = canvas.save()
                canvas.translate(keyValue[0], keyValue[1])
                canvas.rotate(keyValue[4])
                val sx = keyValue[2] / keyDrawing.width
                val sy = keyValue[3] / keyDrawing.height
                canvas.scale(sx, sy)
                keyDrawing.draw(canvas, keyValue[5], sx, sy, kind, time, signature)
                canvas.restoreToCount(saved)

                sample(arm, armValue, current, next, progress)
                val front = canvas.save()
                val left = min(armValue[0], armValue[2]) - 7f
                val top = min(armValue[1], armValue[3])
                canvas.clipRect(left, top, left + abs(armValue[2] - armValue[0]) + 14f,
                    top + abs(armValue[3] - armValue[1]) + 8f)
                // Only the measured source forearm comes back in front of the key.
                drawBody(canvas)
                canvas.restoreToCount(front)
            }
            sample(face, faceValue, current, next, progress)
            drawFace(canvas, signature?.gazeOffset(kind, time, 0) ?: 0f, signature?.gazeOffset(kind, time, 1) ?: 0f)
        }

        private fun drawBody(canvas: Canvas) {
            canvas.drawPath(silhouette, bodyPaint)
            for (path in islands) canvas.drawPath(path, bodyPaint)
        }

        private fun updatePath(path: Path, frames: IntArray, current: Int, next: Int, progress: Double) {
            path.rewind()
            val currentID = frames[current]
            val nextID = frames[next]
            val a = curves[currentID]
            if (a.isEmpty()) return
            if (progress == 0.0 || currentID == nextID || pointCounts[currentID] != pointCounts[nextID]) {
                path.set(pathBank[currentID])
                return
            }
            val b = curves[nextID]
            path.moveTo(coordinate(a, b, 0, progress), coordinate(a, b, 1, progress))
            var index = 2
            while (index < a.size) {
                path.cubicTo(coordinate(a, b, index, progress), coordinate(a, b, index + 1, progress),
                    coordinate(a, b, index + 2, progress), coordinate(a, b, index + 3, progress),
                    coordinate(a, b, index + 4, progress), coordinate(a, b, index + 5, progress))
                index += 6
            }
            path.close()
        }

        private fun coordinate(a: DoubleArray, b: DoubleArray, index: Int, progress: Double): Float {
            return (a[index] + (b[index] - a[index]) * progress).toFloat()
        }

        private fun drawFace(canvas: Canvas, gazeX: Float, gazeY: Float) {
            val values = faceValue
            for (eye in 0..1) {
                val offset = eye * 4
                val saved = canvas.save()
                canvas.translate(values[offset], values[offset + 1])
                canvas.rotate(values[13 + eye])
                eyeRect.set(-values[offset + 2] / 2f, -values[offset + 3] / 2f,
                    values[offset + 2] / 2f, values[offset + 3] / 2f)
                eyePath.rewind()
                if (kind == "orange") {
                    val rx = values[offset + 2] / 2f
                    val height = values[offset + 3]
                    val bottom = height / 2f
                    val k = 0.5522847498f
                    eyePath.moveTo(-rx, bottom)
                    eyePath.cubicTo(-rx, bottom - k * height, -k * rx, bottom - height, 0f, bottom - height)
                    eyePath.cubicTo(k * rx, bottom - height, rx, bottom - k * height, rx, bottom)
                    eyePath.close()
                } else if (eye == 0) eyePath.addRoundRect(eyeRect, 1f, 1f, Path.Direction.CW)
                else eyePath.addOval(eyeRect, Path.Direction.CW)
                canvas.drawPath(eyePath, whitePaint)
                canvas.clipPath(eyePath)
                canvas.rotate(-values[13 + eye])
                val radius = values[offset + 3] * if (kind == "orange") 0.35f else 0.29f
                canvas.drawCircle(values[8 + eye * 2] - values[offset] + gazeX,
                    values[9 + eye * 2] - values[offset + 1] + gazeY, radius, pupilPaint)
                canvas.restoreToCount(saved)
            }
            val saved = canvas.save()
            canvas.translate((values[0] + values[4]) / 2f, (values[1] + values[5]) / 2f)
            canvas.rotate(values[12] - faceBaselineAngle)
            val scale = (values[2] + values[6]) / if (kind == "orange") 34.24f else 33.42f
            canvas.scale(scale, scale)
            canvas.drawPath(smile, smilePaint)
            canvas.restoreToCount(saved)
        }
    }

    private companion object {
        fun closedCubic(points: DoubleArray): DoubleArray {
            if (points.isEmpty()) return DoubleArray(0)
            val count = points.size / 2
            val handles = DoubleArray(points.size)
            for (vertex in 0 until count) {
                val current = vertex * 2
                val previous = ((vertex + count - 1) % count) * 2
                val next = ((vertex + 1) % count) * 2
                val ix = points[current] - points[previous]
                val iy = points[current + 1] - points[previous + 1]
                val ox = points[next] - points[current]
                val oy = points[next + 1] - points[current + 1]
                val incoming = hypot(ix, iy)
                val outgoing = hypot(ox, oy)
                val edge = min(incoming, outgoing)
                if (edge <= 1e-8 || (ix * ox + iy * oy) / (incoming * outgoing) < 0.42261826174069944) continue
                var hx = (points[next] - points[previous]) / 6.0
                var hy = (points[next + 1] - points[previous + 1]) / 6.0
                val length = hypot(hx, hy)
                val cap = 0.45 * edge
                if (length > cap) {
                    hx *= cap / length
                    hy *= cap / length
                }
                handles[current] = hx
                handles[current + 1] = hy
            }
            val result = DoubleArray(2 + 6 * count)
            result[0] = points[0]
            result[1] = points[1]
            var write = 2
            for (vertex in 0 until count) {
                val current = vertex * 2
                val next = ((vertex + 1) % count) * 2
                result[write++] = points[current] + handles[current]
                result[write++] = points[current + 1] + handles[current + 1]
                result[write++] = points[next] - handles[next]
                result[write++] = points[next + 1] - handles[next + 1]
                result[write++] = points[next]
                result[write++] = points[next + 1]
            }
            return result
        }

        fun cubicPath(curve: DoubleArray): Path {
            val path = Path()
            if (curve.isEmpty()) return path
            path.moveTo(curve[0].toFloat(), curve[1].toFloat())
            var index = 2
            while (index < curve.size) {
                path.cubicTo(curve[index].toFloat(), curve[index + 1].toFloat(), curve[index + 2].toFloat(),
                    curve[index + 3].toFloat(), curve[index + 4].toFloat(), curve[index + 5].toFloat())
                index += 6
            }
            path.close()
            return path
        }

        fun rgb(values: JSONArray): Int {
            return Color.rgb(values.getDouble(0).roundToInt().coerceIn(0, 255),
                values.getDouble(1).roundToInt().coerceIn(0, 255), values.getDouble(2).roundToInt().coerceIn(0, 255))
        }

        fun keyGradient(values: JSONObject): LinearGradient {
            val start = values.getJSONArray("start")
            val end = values.getJSONArray("end")
            val stops = values.getJSONArray("colors")
            val colors = IntArray(stops.length()) { rgb(stops.getJSONArray(it)) }
            return LinearGradient(start.getDouble(0).toFloat(), start.getDouble(1).toFloat(),
                end.getDouble(0).toFloat(), end.getDouble(1).toFloat(), colors, null, Shader.TileMode.CLAMP)
        }

        fun softPath(points: JSONArray): Path {
            val path = Path()
            if (points.length() < 3) return path
            val first = points.getJSONArray(0)
            val last = points.getJSONArray(points.length() - 1)
            path.moveTo(((last.getDouble(0) + first.getDouble(0)) / 2).toFloat(),
                ((last.getDouble(1) + first.getDouble(1)) / 2).toFloat())
            for (index in 0 until points.length()) {
                val point = points.getJSONArray(index)
                val next = points.getJSONArray((index + 1) % points.length())
                path.quadTo(point.getDouble(0).toFloat(), point.getDouble(1).toFloat(),
                    ((point.getDouble(0) + next.getDouble(0)) / 2).toFloat(),
                    ((point.getDouble(1) + next.getDouble(1)) / 2).toFloat())
            }
            path.close()
            return path
        }

        fun gradientColor(values: FloatArray, start: Int): Int {
            return Color.rgb(values[start].roundToInt().coerceIn(0, 255),
                values[start + 1].roundToInt().coerceIn(0, 255),
                values[start + 2].roundToInt().coerceIn(0, 255))
        }

        fun indices(values: JSONArray): IntArray {
            return IntArray(values.length()) { values.getInt(it) }
        }

        fun track(values: JSONArray): Array<FloatArray> {
            return Array(values.length()) { index ->
                val frame = values.getJSONArray(index)
                FloatArray(frame.length()) { channel -> frame.getDouble(channel).toFloat() }
            }
        }

        fun sample(frames: Array<FloatArray>, output: FloatArray, current: Int, next: Int, progress: Float) {
            val a = frames[current]
            val b = frames[next]
            for (index in output.indices) output[index] = a[index] + (b[index] - a[index]) * progress
        }
    }
}
