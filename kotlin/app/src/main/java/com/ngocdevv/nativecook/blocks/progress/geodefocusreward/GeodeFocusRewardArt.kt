package com.ngocdevv.nativecook.blocks.progress.geodefocusreward

import android.graphics.*
import kotlin.math.*

/** The same projected solid triangles and cached sRGB material on every supported Android API. */
internal class GeodeFocusRewardArt(private val resources: GeodeFocusRewardResources) {
    private val m = GeodeFocusRewardMath
    private val image = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val group = Paint()
    private val add = PorterDuffXfermode(PorterDuff.Mode.ADD)
    private val matrix = Matrix()
    private val path = Path()
    private val rect = RectF()
    private val destination = FloatArray(6)
    private val pieces = IntArray(12) { it }
    private val depths = DoubleArray(12)
    private val faces = GeodeFocusRewardGeometry.pieces.map { p -> p.triangles.map { Face(it) } }
    private val light = GeodeFocusRewardVector(-.5, -.7, 1.0).normalized()
    private class Face(val triangle: GeodeFocusRewardTriangle) {
        val world = Array(3) { GeodeFocusRewardVector.Zero }
        var visible = false; var depth = 0.0; var shade = 1.0
    }
    fun draw(canvas: Canvas, pose: GeodeFocusRewardHeroPose) {
        image.alpha = 255; image.xfermode = null
        rect.set(46.5f, 544f, 346.5f, 644f); canvas.drawBitmap(resources.floor, null, rect, image)
        rect.set(144.5f, 577f, 248.5f, 613f); canvas.drawBitmap(resources.contact, null, rect, image)
        canvas.save(); canvas.clipRect(16f, 250f, 377f, 600f)
        canvas.translate(196.5f, (446 + pose.globalY).toFloat()); canvas.scale(pose.globalScale.toFloat(), pose.globalScale.toFloat()); canvas.translate(-196.5f, -446f)
        image.alpha = alpha(pose.glowGain); rect.set(76.5f, 283f, 316.5f, 593f); canvas.drawBitmap(resources.glow, null, rect, image); image.alpha = 255
        val yaw = GeodeFocusRewardVector(pose.manualYaw, 0.0, 0.0)
        repeat(12) { i -> pieces[i] = i; depths[i] = m.rotate(pose.pieces[i].center, yaw).z }
        for (i in 1..11) { val key = pieces[i]; var j = i - 1; while (j >= 0 && depths[pieces[j]] > depths[key]) { pieces[j + 1] = pieces[j]; j-- }; pieces[j + 1] = key }
        var core = false
        for (i in pieces) {
            if (!core && depths[i] >= -16) { drawCore(canvas, pose); core = true }
            if (pose.pieces[i].alpha > .0001) drawPiece(canvas, i, pose)
        }
        if (!core) drawCore(canvas, pose)
        if (pose.crumbAmount > .0001) drawCrumbs(canvas, pose)
        canvas.restore()
    }
    private fun drawCore(c: Canvas, p: GeodeFocusRewardHeroPose) {
        val index = (m.clamp(p.coreYaw + p.manualYaw, -26.0, 26.0) + 26) / 4
        val a = floor(index).toInt().coerceIn(0, 13); val b = min(13, a + 1); val q = index - a
        c.save(); c.translate(196.5f, 438f); c.scale(p.coreScale.toFloat(), p.coreScale.toFloat())
        val layer = c.saveLayer(resources.coreRect, null)
        image.xfermode = null
        image.alpha = if (a == b || q <= 0) 255 else alpha(1 - q)
        c.drawBitmap(resources.cores[a], null, resources.coreRect, image)
        if (a != b && q > 0) { image.xfermode = add; image.alpha = alpha(q); c.drawBitmap(resources.cores[b], null, resources.coreRect, image) }
        image.xfermode = null; image.alpha = 255; c.restoreToCount(layer); c.restore()
    }
    private fun transformed(v: GeodeFocusRewardVector, i: Int, p: GeodeFocusRewardHeroPose): GeodeFocusRewardVector {
        val mesh = GeodeFocusRewardGeometry.pieces[i]; val state = p.pieces[i]
        return m.rotate(state.center + m.rotate(v - mesh.center, state.rotation), GeodeFocusRewardVector(p.manualYaw, 0.0, 0.0))
    }
    private fun project(p: GeodeFocusRewardVector): Pair<Double, Double> { val k = 760 / (760 - p.z); return 196.5 + p.x * k to 446 + p.y * k }
    private fun drawPiece(c: Canvas, i: Int, p: GeodeFocusRewardHeroPose) {
        val sorted = faces[i]
        var minX = Double.POSITIVE_INFINITY; var minY = minX; var maxX = Double.NEGATIVE_INFINITY; var maxY = maxX
        for (face in sorted) {
            repeat(3) { k -> face.world[k] = transformed(face.triangle.vertices[k].point, i, p) }
            val a = face.world[0]; val b = face.world[1]; val d = face.world[2]
            val normal = (b - a).cross(d - a).normalized(); val center = (a + b + d) * (1.0 / 3)
            face.visible = normal.dot(GeodeFocusRewardVector(-center.x, -center.y, 760 - center.z)) > 0
            if (face.visible) {
                face.depth = center.z; val diffuse = max(0.0, normal.dot(light))
                face.shade = if (face.triangle.cut) .63 + .36 * diffuse else .78 + .22 * diffuse
                for (v in face.world) { val at = project(v); minX = min(minX, at.first); maxX = max(maxX, at.first); minY = min(minY, at.second); maxY = max(maxY, at.second) }
            }
        }
        if (!minX.isFinite()) return
        group.alpha = alpha(p.pieces[i].alpha); rect.set((minX - 2).toFloat(), (minY - 2).toFloat(), (maxX + 2).toFloat(), (maxY + 2).toFloat())
        val layer = c.saveLayer(rect, group)
        sorted.sortedBy { if (it.visible) it.depth else Double.POSITIVE_INFINITY }.forEach { face -> if (face.visible) paintTriangle(c, face, p.glowGain) }
        if (p.crack > .00001) drawCracks(c, i, p)
        c.restoreToCount(layer)
    }
    private fun paintTriangle(c: Canvas, face: Face, glow: Double) {
        repeat(3) { k -> val p = project(face.world[k]); destination[k * 2] = p.first.toFloat(); destination[k * 2 + 1] = p.second.toFloat() }
        if (!matrix.setPolyToPoly(face.triangle.source, 0, destination, 0, 3)) return
        val cx = (destination[0] + destination[2] + destination[4]) / 3; val cy = (destination[1] + destination[3] + destination[5]) / 3
        path.rewind()
        repeat(3) { k -> val x = destination[k * 2]; val y = destination[k * 2 + 1]; val length = max(.001f, hypot(x - cx, y - cy)); val px = x + (x - cx) / length * .32f; val py = y + (y - cy) / length * .32f; if (k == 0) path.moveTo(px, py) else path.lineTo(px, py) }
        path.close()
        c.save(); c.clipPath(path); c.concat(matrix)
        image.alpha = 255; image.xfermode = null
        c.drawBitmap(if (face.triangle.cut) resources.cut else resources.rock, null, if (face.triangle.cut) resources.cutRect else resources.rockRect, image)
        c.restore()
        fill.shader = null; fill.style = Paint.Style.FILL; fill.xfermode = null; fill.color = Color.argb(alpha(1 - face.shade), 0, 0, 0); c.drawPath(path, fill)
        if (face.triangle.cut) { fill.xfermode = add; fill.color = Color.argb(alpha(.06 * glow), 224, 112, 20); c.drawPath(path, fill); fill.xfermode = null }
    }
    private fun drawCracks(c: Canvas, i: Int, p: GeodeFocusRewardHeroPose) {
        GeodeFocusRewardGeometry.cracks.forEachIndexed { branch, crack ->
            if (crack.piece != i) return@forEachIndexed
            val amount = m.clamp(p.crack * 4 - branch); if (amount <= 0) return@forEachIndexed
            val a = project(transformed(crack.a, i, p)); val b = project(transformed(crack.a + (crack.b - crack.a) * amount, i, p))
            path.rewind(); path.moveTo(a.first.toFloat(), a.second.toFloat()); path.lineTo(b.first.toFloat(), b.second.toFloat())
            val width = .6 + 2.4 * p.crack
            fill.xfermode = null; fill.shader = null; fill.style = Paint.Style.STROKE; fill.strokeCap = Paint.Cap.ROUND; fill.strokeWidth = (width + .6).toFloat(); fill.color = Color.rgb(6, 13, 8); c.drawPath(path, fill)
            fill.xfermode = add; fill.strokeWidth = max(.4, width * .45).toFloat(); fill.shader = LinearGradient(a.first.toFloat(), a.second.toFloat(), b.first.toFloat(), b.second.toFloat(), intArrayOf(Color.rgb(232, 99, 20), Color.rgb(115, 184, 41)), null, Shader.TileMode.CLAMP)
            c.drawPath(path, fill); fill.xfermode = null; fill.shader = null; fill.style = Paint.Style.FILL
        }
    }
    private fun drawCrumbs(c: Canvas, p: GeodeFocusRewardHeroPose) {
        repeat(12) { i -> val state = p.pieces[i]; val angle = GeodeFocusRewardGeometry.hash(i + 5513) * 2 * PI; val size = 1.5 + 2.5 * GeodeFocusRewardGeometry.hash(i + 5669)
            val travel = p.crumbAmount * (8 + 18 * GeodeFocusRewardGeometry.hash(i + 5781))
            val at = project(m.rotate(state.center + GeodeFocusRewardVector(cos(angle) * travel, sin(angle) * travel, 4.0), GeodeFocusRewardVector(p.manualYaw, 0.0, 0.0)))
            path.rewind(); path.moveTo((at.first - size * .6).toFloat(), (at.second - size * .4).toFloat()); path.lineTo((at.first + size * .6).toFloat(), (at.second - size * .25).toFloat()); path.lineTo((at.first + size * .1).toFloat(), (at.second + size * .6).toFloat()); path.close()
            fill.shader = null; fill.style = Paint.Style.FILL; fill.color = Color.argb(alpha(p.crumbAmount * state.alpha * .65), 61, 71, 64); c.drawPath(path, fill)
        }
    }
    private fun alpha(a: Double) = (a * 255).roundToInt().coerceIn(0, 255)
}
