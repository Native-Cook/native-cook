package com.ngocdevv.nativecook.blocks.animations.flarenotifications

import android.content.Context
import org.json.JSONObject

/**
 * The fitted flame data (flare-blobs.ts), read from
 * the asset `flarenotifications/flare-blobs.json`. Each pill's
 * flame is [count] soft elliptical blobs keyframed every [dt] seconds over a
 * [period]-second loop. Per key and blob, 9 numbers: u, v (pill heights from
 * the trailing edge / top), log a, log b (radii above the minimums), angle,
 * alpha logit, and OKLab L, a, b.
 */
class FlareBlobs(
    val dt: Double,
    val period: Double,
    val count: Int,
    val minA: Double,
    val minB: Double,
    private val keys: Map<String, DoubleArray>,
) {
    fun keysFor(id: String): DoubleArray = keys.getValue(id)

    companion object {
        @Volatile private var cached: FlareBlobs? = null

        fun load(context: Context): FlareBlobs = cached ?: synchronized(this) {
            cached ?: parse(context.assets.open("flarenotifications/flare-blobs.json").bufferedReader().use { it.readText() }).also { cached = it }
        }

        private fun parse(text: String): FlareBlobs {
            val json = JSONObject(text)
            val keys = FLARES.associate { flare ->
                val array = json.getJSONArray(flare.id)
                flare.id to DoubleArray(array.length()) { array.getDouble(it) }
            }
            check(json.getInt("count") == BLOB_COUNT) { "flare-blobs.json has ${json.getInt("count")} blobs; the shader expects $BLOB_COUNT" }
            return FlareBlobs(
                dt = json.getDouble("dt"),
                period = json.getDouble("period"),
                count = json.getInt("count"),
                minA = json.getDouble("minA"),
                minB = json.getDouble("minB"),
                keys = keys,
            )
        }
    }
}
