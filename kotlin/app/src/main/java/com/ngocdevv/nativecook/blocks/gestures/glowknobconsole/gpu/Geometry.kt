// The block is built for Android 12 (API 31) and newer; the demo checks before showing it.
@file:androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.S)

package com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.gpu

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A triangle mesh with material groups, as a three.js BufferGeometry. */
class MeshData(
    val positions: FloatArray,
    val normals: FloatArray,
    val uvs: FloatArray,
    val indices: IntArray,
    /** (start index, index count, material index) */
    val groups: List<Triple<Int, Int, Int>>,
) {
    val vertexCount get() = positions.size / 3
}

private data class V2(val x: Double, val y: Double) {
    fun add(v: V2, s: Double) = V2(x + v.x * s, y + v.y * s)
}

/**
 * THREE.Shape for the console's capsule (lift-scene.ts `capsule`) and
 * `extractPoints(curveSegments)`: line → absarc → line → absarc, each
 * EllipseCurve sampled at divisions·2, consecutive duplicates dropped.
 */
private fun capsulePoints(width: Double, height: Double, curveSegments: Int): MutableList<V2> {
    val r = height / 2
    val x = width / 2 - r
    val points = ArrayList<V2>()
    var last: V2? = null
    fun push(p: V2) {
        if (last != null && last == p) return
        points.add(p); last = p
    }
    fun line(a: V2, b: V2) { push(a); push(b) }
    fun arc(cx: Double, cy: Double, start: Double, end: Double, current: V2) {
        val first = V2(cx + r * cos(start), cy + r * sin(start))
        if (first != current) line(current, first)
        val divisions = curveSegments * 2
        var delta = end - start
        while (delta < 0) delta += 2 * PI
        while (delta > 2 * PI) delta -= 2 * PI
        for (d in 0..divisions) {
            val angle = start + d.toDouble() / divisions * delta
            push(V2(cx + r * cos(angle), cy + r * sin(angle)))
        }
    }
    val p0 = V2(-x, -r)
    line(p0, V2(x, -r))
    arc(x, 0.0, -PI / 2, PI / 2, V2(x, -r))
    val arcEnd = V2(x + r * cos(PI / 2), r * sin(PI / 2))
    line(arcEnd, V2(-x, r))
    arc(-x, 0.0, PI / 2, PI * 3 / 2, V2(-x, r))
    return points
}

private fun area(c: List<V2>): Double {
    var a = 0.0
    var p = c.size - 1
    for (q in c.indices) {
        a += c[p].x * c[q].y - c[q].x * c[p].y
        p = q
    }
    return a * 0.5
}

private fun mergeOverlappingPoints(points: MutableList<V2>) {
    val thresholdSq = 1e-10 * 1e-10
    var prev = points[0]
    var i = 1
    while (i <= points.size) {
        val idx = i % points.size
        val cur = points[idx]
        val dx = cur.x - prev.x
        val dy = cur.y - prev.y
        val distSq = dx * dx + dy * dy
        val scale = maxOf(abs(cur.x), abs(cur.y), abs(prev.x), abs(prev.y))
        if (distSq <= thresholdSq * scale * scale) {
            points.removeAt(idx)
            i--
            i++
            continue
        }
        prev = cur
        i++
    }
}

/** ExtrudeGeometry's getBevelVec. */
private fun bevelVec(pt: V2, prev: V2, next: V2): V2 {
    val vPrevX = pt.x - prev.x
    val vPrevY = pt.y - prev.y
    val vNextX = next.x - pt.x
    val vNextY = next.y - pt.y
    val vPrevLenSq = vPrevX * vPrevX + vPrevY * vPrevY
    val collinear0 = vPrevX * vNextY - vPrevY * vNextX
    val tx: Double
    val ty: Double
    val shrink: Double
    if (abs(collinear0) > Math.ulp(1.0)) {
        val vPrevLen = sqrt(vPrevLenSq)
        val vNextLen = sqrt(vNextX * vNextX + vNextY * vNextY)
        val ptPrevShiftX = prev.x - vPrevY / vPrevLen
        val ptPrevShiftY = prev.y + vPrevX / vPrevLen
        val ptNextShiftX = next.x - vNextY / vNextLen
        val ptNextShiftY = next.y + vNextX / vNextLen
        val sf = ((ptNextShiftX - ptPrevShiftX) * vNextY - (ptNextShiftY - ptPrevShiftY) * vNextX) /
            (vPrevX * vNextY - vPrevY * vNextX)
        val vx = ptPrevShiftX + vPrevX * sf - pt.x
        val vy = ptPrevShiftY + vPrevY * sf - pt.y
        val lenSq = vx * vx + vy * vy
        if (lenSq <= 2) return V2(vx, vy)
        tx = vx; ty = vy; shrink = sqrt(lenSq / 2)
    } else {
        var directionEq = false
        if (vPrevX > Math.ulp(1.0)) {
            if (vNextX > Math.ulp(1.0)) directionEq = true
        } else if (vPrevX < -Math.ulp(1.0)) {
            if (vNextX < -Math.ulp(1.0)) directionEq = true
        } else if (Math.signum(vPrevY) == Math.signum(vNextY)) directionEq = true
        if (directionEq) {
            tx = -vPrevY; ty = vPrevX; shrink = sqrt(vPrevLenSq)
        } else {
            tx = vPrevX; ty = vPrevY; shrink = sqrt(vPrevLenSq / 2)
        }
    }
    return V2(tx / shrink, ty / shrink)
}

/**
 * THREE.ExtrudeGeometry(capsule(width, height), { depth, bevelEnabled,
 * bevelThickness, bevelSize, bevelSegments, curveSegments }), non-indexed
 * with flat per-triangle normals (computeVertexNormals on a non-indexed
 * geometry). Groups: 0 = both lids (back lid first), 1 = walls. UVs are left
 * zero: lift-scene replaces them all with face UVs.
 */
fun extrudeCapsule(
    width: Double,
    height: Double,
    depth: Double,
    bevelThickness: Double,
    bevelSize: Double,
    bevelSegments: Int,
    curveSegments: Int,
): MeshData {
    var vertices: MutableList<V2> = capsulePoints(width, height, curveSegments)
    if (area(vertices) >= 0) vertices = vertices.reversed().toMutableList() // CW
    mergeOverlappingPoints(vertices)
    val contour = vertices
    val vlen = vertices.size
    val moves = List(contour.size) { i ->
        val j = if (i == 0) contour.size - 1 else i - 1
        val k = if (i == contour.size - 1) 0 else i + 1
        bevelVec(contour[i], contour[j], contour[k])
    }
    val placeholder = ArrayList<Double>()
    fun v(x: Double, y: Double, z: Double) { placeholder.add(x); placeholder.add(y); placeholder.add(z) }
    for (b in 0 until bevelSegments) {
        val t = b.toDouble() / bevelSegments
        val z = bevelThickness * cos(t * PI / 2)
        val bs = bevelSize * sin(t * PI / 2)
        for (i in contour.indices) {
            val p = contour[i].add(moves[i], bs)
            v(p.x, p.y, -z)
        }
    }
    val steps = 1
    for (s in 0..steps) for (i in 0 until vlen) {
        val p = vertices[i].add(moves[i], bevelSize)
        v(p.x, p.y, depth / steps * s)
    }
    for (b in bevelSegments - 1 downTo 0) {
        val t = b.toDouble() / bevelSegments
        val z = bevelThickness * cos(t * PI / 2)
        val bs = bevelSize * sin(t * PI / 2)
        for (i in contour.indices) {
            val p = contour[i].add(moves[i], bs)
            v(p.x, p.y, depth + z)
        }
    }

    val out = ArrayList<Int>() // placeholder indices, flattened triangles
    // Lids: a fan over the (convex) lid contour, back lid facing −z, front +z.
    // ShapeUtils.triangulateShape (earcut) picks other diagonals; a flat lid
    // renders identically for any triangulation.
    fun lid(offset: Int, front: Boolean) {
        for (i in 1 until vlen - 1) {
            val a = offset; val b = offset + i; val c = offset + i + 1
            // Contour is CW seen from +z, so (a, b, c) faces −z.
            if (front) { out.add(a); out.add(c); out.add(b) } else { out.add(a); out.add(b); out.add(c) }
        }
    }
    lid(0, front = false)
    lid(vlen * (steps + bevelSegments * 2), front = true)
    val lidsCount = out.size
    // Side walls (sidewalls + f4).
    var i = contour.size
    while (--i >= 0) {
        val j = i
        var k = i - 1
        if (k < 0) k = contour.size - 1
        for (s in 0 until steps + bevelSegments * 2) {
            val slen1 = vlen * s
            val slen2 = vlen * (s + 1)
            val a = j + slen1; val b = k + slen1; val c = k + slen2; val d = j + slen2
            out.add(a); out.add(b); out.add(d)
            out.add(b); out.add(c); out.add(d)
        }
    }
    val n = out.size
    val positions = FloatArray(n * 3)
    val normals = FloatArray(n * 3)
    for (t in 0 until n) {
        val src = out[t] * 3
        positions[t * 3] = placeholder[src].toFloat()
        positions[t * 3 + 1] = placeholder[src + 1].toFloat()
        positions[t * 3 + 2] = placeholder[src + 2].toFloat()
    }
    // computeVertexNormals (non-indexed): cb × ab per triangle.
    for (t in 0 until n / 3) {
        val o = t * 9
        val ax = positions[o]; val ay = positions[o + 1]; val az = positions[o + 2]
        val bx = positions[o + 3]; val by = positions[o + 4]; val bz = positions[o + 5]
        val cx = positions[o + 6]; val cy = positions[o + 7]; val cz = positions[o + 8]
        val cbx = cx - bx; val cby = cy - by; val cbz = cz - bz
        val abx = ax - bx; val aby = ay - by; val abz = az - bz
        var nx = cby * abz - cbz * aby
        var ny = cbz * abx - cbx * abz
        var nz = cbx * aby - cby * abx
        val len = sqrt(nx * nx + ny * ny + nz * nz).let { if (it == 0f) 1f else it }
        nx /= len; ny /= len; nz /= len
        for (q in 0 until 3) {
            normals[o + q * 3] = nx; normals[o + q * 3 + 1] = ny; normals[o + q * 3 + 2] = nz
        }
    }
    return MeshData(
        positions, normals, FloatArray(n * 2), IntArray(n) { it },
        listOf(Triple(0, lidsCount, 0), Triple(lidsCount, n - lidsCount, 1)),
    )
}

/** THREE.CylinderGeometry(r, r, height, radialSegments, 1): torso 0, top cap 1, bottom cap 2. */
fun cylinder(radius: Double, height: Double, radialSegments: Int): MeshData {
    val pos = ArrayList<Float>()
    val nor = ArrayList<Float>()
    val uv = ArrayList<Float>()
    val idx = ArrayList<Int>()
    val groups = ArrayList<Triple<Int, Int, Int>>()
    val half = height / 2
    var index = 0
    val heightSegments = 1
    val grid = Array(heightSegments + 1) { IntArray(radialSegments + 1) }
    for (y in 0..heightSegments) {
        val v = y.toDouble() / heightSegments
        for (x in 0..radialSegments) {
            val u = x.toDouble() / radialSegments
            val theta = u * 2 * PI
            val s = sin(theta); val c = cos(theta)
            pos.add((radius * s).toFloat()); pos.add((-v * height + half).toFloat()); pos.add((radius * c).toFloat())
            val l = sqrt(s * s + c * c)
            nor.add((s / l).toFloat()); nor.add(0f); nor.add((c / l).toFloat())
            uv.add(u.toFloat()); uv.add((1 - v).toFloat())
            grid[y][x] = index++
        }
    }
    var groupStart = 0
    for (x in 0 until radialSegments) for (y in 0 until heightSegments) {
        val a = grid[y][x]; val b = grid[y + 1][x]; val c = grid[y + 1][x + 1]; val d = grid[y][x + 1]
        idx.addAll(listOf(a, b, d, b, c, d))
    }
    groups.add(Triple(groupStart, idx.size - groupStart, 0))
    for (top in listOf(true, false)) {
        groupStart = idx.size
        val sign = if (top) 1.0 else -1.0
        val centerStart = index
        for (x in 1..radialSegments) {
            pos.add(0f); pos.add((half * sign).toFloat()); pos.add(0f)
            nor.add(0f); nor.add(sign.toFloat()); nor.add(0f)
            uv.add(0.5f); uv.add(0.5f)
            index++
        }
        val centerEnd = index
        for (x in 0..radialSegments) {
            val theta = x.toDouble() / radialSegments * 2 * PI
            val c = cos(theta); val s = sin(theta)
            pos.add((radius * s).toFloat()); pos.add((half * sign).toFloat()); pos.add((radius * c).toFloat())
            nor.add(0f); nor.add(sign.toFloat()); nor.add(0f)
            uv.add((c * 0.5 + 0.5).toFloat()); uv.add((s * 0.5 * sign + 0.5).toFloat())
            index++
        }
        for (x in 0 until radialSegments) {
            val c = centerStart + x
            val i = centerEnd + x
            if (top) idx.addAll(listOf(i, i + 1, c)) else idx.addAll(listOf(i + 1, i, c))
        }
        groups.add(Triple(groupStart, idx.size - groupStart, if (top) 1 else 2))
    }
    return MeshData(pos.toFloatArray(), nor.toFloatArray(), uv.toFloatArray(), idx.toIntArray(), groups)
}

/** In place: rotateX(angle) then translate (positions and normals), like BufferGeometry. */
fun MeshData.rotateXTranslate(angle: Double, tx: Double, ty: Double, tz: Double) {
    val c = cos(angle).toFloat(); val s = sin(angle).toFloat()
    for (i in 0 until vertexCount) {
        val y = positions[i * 3 + 1]; val z = positions[i * 3 + 2]
        positions[i * 3 + 1] = c * y - s * z + ty.toFloat()
        positions[i * 3 + 2] = s * y + c * z + tz.toFloat()
        positions[i * 3] += tx.toFloat()
        val ny = normals[i * 3 + 1]; val nz = normals[i * 3 + 2]
        normals[i * 3 + 1] = c * ny - s * nz
        normals[i * 3 + 2] = s * ny + c * nz
    }
}

fun MeshData.translate(tx: Double, ty: Double, tz: Double) {
    for (i in 0 until vertexCount) {
        positions[i * 3] += tx.toFloat(); positions[i * 3 + 1] += ty.toFloat(); positions[i * 3 + 2] += tz.toFloat()
    }
}

/**
 * lift-scene.ts `faceUV`: (0,0) is the housing's top-left corner, (1,1) its
 * bottom-right, for every vertex (or only those used by `groups`).
 */
fun MeshData.faceUV(width: Double, height: Double, onlyGroups: List<Int>? = null) {
    fun set(vtx: Int) {
        uvs[vtx * 2] = (positions[vtx * 3] / width + 0.5).toFloat()
        uvs[vtx * 2 + 1] = (0.5 - positions[vtx * 3 + 1] / height).toFloat()
    }
    if (onlyGroups == null) for (v in 0 until vertexCount) set(v)
    else for ((start, count, material) in groups) if (material in onlyGroups) {
        for (i in start until start + count) set(indices[i])
    }
}

