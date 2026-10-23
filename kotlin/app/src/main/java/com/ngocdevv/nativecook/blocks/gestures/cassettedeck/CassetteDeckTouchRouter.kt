package com.ngocdevv.nativecook.blocks.gestures.cassettedeck

import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import kotlin.math.hypot

/**
 * Sends each finger to the key or the cassette under it, so several fingers work at once, like the separate
 * gesture detectors of the React Native view. A finger belongs to the thing it first landed on.
 */
internal class CassetteDeckTouchRouter(private val controller: CassetteDeckController) {
    private val tracks = HashMap<PointerId, CassetteDeckTouchTrack>()
    private var cassetteFinger: PointerId? = null

    fun onEvent(event: PointerEvent, geometry: CassetteDeckGeometry, density: Float) {
        for (change in event.changes) {
            val x = change.position.x / density.toDouble()
            val y = change.position.y / density.toDouble()
            val track = tracks[change.id]
            when {
                track == null && change.pressed && !change.previousPressed -> down(change, x, y, geometry)
                track == null -> Unit
                !change.pressed -> up(change, track, x, y, geometry, density)
                else -> move(change, track, x, y, geometry, density)
            }
        }
    }

    /** The window went away mid-touch: let go of everything, as a cancelled gesture does. */
    fun cancelAll(geometry: CassetteDeckGeometry?) {
        for (track in tracks.values) {
            when (track) {
                is CassetteDeckTouchTrack.Key -> controller.press(track.index, false)
                is CassetteDeckTouchTrack.Cassette -> if (geometry != null) controller.cassetteEnd(0.0, geometry)
            }
        }
        tracks.clear()
        cassetteFinger = null
    }

    private fun down(change: PointerInputChange, x: Double, y: Double, geometry: CassetteDeckGeometry) {
        for (index in 0 until CassetteDeckSim.KEYS) {
            val left = geometry.keyLeft(index)
            if (x >= left && x < left + geometry.keyWidth && y >= geometry.keyTop && y < geometry.keyTop + geometry.keyHeight) {
                tracks[change.id] = CassetteDeckTouchTrack.Key(index)
                controller.press(index, true)
                change.consume()
                return
            }
        }
        // One pan at a time on the cassette: a second finger there is ignored.
        val onCassette = y >= geometry.swipeTop && y < geometry.swipeTop + geometry.swipeHeight
        if (onCassette && cassetteFinger == null) {
            val track = CassetteDeckTouchTrack.Cassette(x, y)
            track.velocity.addPosition(change.uptimeMillis, change.position)
            tracks[change.id] = track
            cassetteFinger = change.id
            controller.cassetteBegin(x, y - geometry.swipeTop, geometry)
            change.consume()
        }
    }

    private fun move(
        change: PointerInputChange,
        track: CassetteDeckTouchTrack,
        x: Double,
        y: Double,
        geometry: CassetteDeckGeometry,
        density: Float,
    ) {
        if (track !is CassetteDeckTouchTrack.Cassette) return
        track.velocity.addPosition(change.uptimeMillis, change.position)
        if (!track.active) {
            if (hypot(x - track.downX, y - track.downY) < PAN_SLOP) return
            // The pan counts from where it started, so the cassette does not jump by the slop.
            track.active = true
            track.startX = x
            controller.cassetteStart()
        }
        val velocity = track.velocity.calculateVelocity()
        controller.cassetteUpdate(
            x = x,
            y = y - geometry.swipeTop,
            translationX = x - track.startX,
            velocityX = velocity.x / density.toDouble(),
            velocityY = velocity.y / density.toDouble(),
            geometry = geometry,
        )
        change.consume()
    }

    private fun up(
        change: PointerInputChange,
        track: CassetteDeckTouchTrack,
        x: Double,
        y: Double,
        geometry: CassetteDeckGeometry,
        density: Float,
    ) {
        tracks.remove(change.id)
        when (track) {
            is CassetteDeckTouchTrack.Key -> controller.press(track.index, false)
            is CassetteDeckTouchTrack.Cassette -> {
                cassetteFinger = null
                track.velocity.addPosition(change.uptimeMillis, change.position)
                val velocity = if (track.active) track.velocity.calculateVelocity().x / density.toDouble() else 0.0
                controller.cassetteEnd(velocity, geometry)
            }
        }
        change.consume()
    }

    private companion object {
        // RN `minDistance(2)`, in points.
        const val PAN_SLOP = 2.0
    }
}
