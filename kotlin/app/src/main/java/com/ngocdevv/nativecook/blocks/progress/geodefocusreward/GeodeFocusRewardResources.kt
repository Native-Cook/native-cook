package com.ngocdevv.nativecook.blocks.progress.geodefocusreward

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import org.json.JSONArray
import org.json.JSONObject

internal data class GeodeFocusRewardVertex(val point: GeodeFocusRewardVector, val u: Double, val v: Double)
internal data class GeodeFocusRewardTriangle(val vertices: List<GeodeFocusRewardVertex>, val cut: Boolean) {
    val source = FloatArray(6) { i -> if (i % 2 == 0) vertices[i / 2].u.toFloat() else vertices[i / 2].v.toFloat() }
}
internal data class GeodeFocusRewardMeshPiece(val center: GeodeFocusRewardVector, val pivot: GeodeFocusRewardVector,
    val finalCenter: GeodeFocusRewardVector, val finalRotation: GeodeFocusRewardVector, val delay: Double, val triangles: List<GeodeFocusRewardTriangle>)
internal data class GeodeFocusRewardCrack(val piece: Int, val a: GeodeFocusRewardVector, val b: GeodeFocusRewardVector)

/** Exact frozen Swift geometry, decoded once before the first motion frame. */
internal object GeodeFocusRewardGeometry {
    lateinit var pieces: List<GeodeFocusRewardMeshPiece>; private set
    lateinit var cracks: List<GeodeFocusRewardCrack>; private set
    fun install(json: JSONObject) {
        fun vector(a: JSONArray) = GeodeFocusRewardVector(a.getDouble(0), a.getDouble(1), a.getDouble(2))
        fun vertex(v: JSONObject): GeodeFocusRewardVertex { val uv = v.getJSONArray("uv"); return GeodeFocusRewardVertex(vector(v.getJSONArray("point")), uv.getDouble(0), uv.getDouble(1)) }
        val p = json.getJSONArray("pieces")
        pieces = List(p.length()) { i -> val a = p.getJSONObject(i); val ts = a.getJSONArray("triangles")
            GeodeFocusRewardMeshPiece(vector(a.getJSONArray("center")), vector(a.getJSONArray("pivot")), vector(a.getJSONArray("finalCenter")), vector(a.getJSONArray("finalRotation")), a.getDouble("delay"),
                List(ts.length()) { k -> val t = ts.getJSONObject(k); GeodeFocusRewardTriangle(listOf(vertex(t.getJSONObject("a")), vertex(t.getJSONObject("b")), vertex(t.getJSONObject("c"))), t.getBoolean("cut")) }) }
        val c = json.getJSONArray("cracks"); cracks = List(c.length()) { i -> val a = c.getJSONObject(i); GeodeFocusRewardCrack(a.getInt("piece"), vector(a.getJSONArray("a")), vector(a.getJSONArray("b"))) }
        check(pieces.size == 12 && cracks.size == 4)
    }
    fun hash(value: Int): Double {
        var x = value.toUInt() + 0x9E3779B9u
        x = (x xor (x shr 16)) * 0x7FEB352Du; x = (x xor (x shr 15)) * 0x846CA68Bu
        return (x xor (x shr 16)).toDouble() / 4294967295.0
    }
}

/** Ordinary tagged sRGB PNGs exported verbatim from the frozen Swift cache. */
internal class GeodeFocusRewardResources private constructor(context: Context) {
    private val assets = context.assets
    private fun image(name: String): Bitmap = assets.open("geodefocusreward/geodefocusreward_$name.png").use { stream ->
        requireNotNull(BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888; inPremultiplied = true })) }
    val rock: Bitmap; val cut: Bitmap; val floor: Bitmap; val contact: Bitmap; val glow: Bitmap; val cores: List<Bitmap>
    val rockRect = RectF(-80f, -120f, 80f, 120f)
    val cutRect = RectF(0f, 0f, 64f, 24f)
    val coreRect = RectF(-82f, -106f, 82f, 106f)
    init {
        context.assets.open("geodefocusreward/geodefocusreward_geometry.json").bufferedReader().use { GeodeFocusRewardGeometry.install(JSONObject(it.readText())) }
        rock = image("rock"); cut = image("cut"); floor = image("floor-light"); contact = image("contact"); glow = image("glow")
        cores = List(14) { image("core-%02d".format(java.util.Locale.US, it)) }
    }
    companion object {
        @Volatile private var cached: GeodeFocusRewardResources? = null
        fun load(context: Context): GeodeFocusRewardResources = cached ?: synchronized(this) {
            cached ?: GeodeFocusRewardResources(context.applicationContext).also { cached = it }
        }
    }
}
