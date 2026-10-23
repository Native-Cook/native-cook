package com.ngocdevv.nativecook.blocks.cards.dreambookhingejournal

import android.app.Activity
import android.content.ContextWrapper
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.floor

private const val LOOP = 8.0
private fun nowSeconds() = System.nanoTime() / 1e9
private fun ease(raw: Double): Double { val u = raw.coerceIn(0.0, 1.0); return u * u * (3 - 2 * u) }
private fun mix(a: Double, b: Double, p: Double) = a + (b - a) * p
private fun hash(value: Double): Float { val n = sin(value * 12.9898) * 43758.5453; return (n - floor(n)).toFloat() }
private fun rubberBand(raw: Double): Double = when {
    raw < 0 -> raw / (1 + abs(raw) / 0.2) * 0.25
    raw > 1 -> 1 + (raw - 1) / (1 + abs(raw - 1) / 0.2) * 0.25
    else -> raw
}

private data class DreambookHingeJournalPose(
    val openness: Double = 0.0, val expansion: Double = 0.0, val fullscreen: Double = 0.0,
    var angle: Double = 0.0, var lift: Double = 0.0, var scale: Double = 1.0,
    var header: Double = 1.0, var calendar: Double = 0.0, var closing: Double = 0.0,
) {
    val pageFrame: RectF get() {
        val e = expansion.coerceIn(0.0, 1.0); val q = fullscreen.coerceIn(0.0, 1.0)
        val x = mix(mix(68.41, 10.19, e), 0.0, q); val y = mix(mix(250.36, 189.22, e), 52.0, q)
        val w = mix(mix(254.72, 372.62, e), 393.0, q); val h = mix(mix(356.61, 481.79, e), 800.0, q)
        return RectF(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat())
    }
    fun blend(other: DreambookHingeJournalPose, amount: Double) = DreambookHingeJournalPose(
        mix(openness, other.openness, amount), mix(expansion, other.expansion, amount), mix(fullscreen, other.fullscreen, amount),
        mix(angle, other.angle, amount), mix(lift, other.lift, amount), mix(scale, other.scale, amount),
        mix(header, other.header, amount), mix(calendar, other.calendar, amount), mix(closing, other.closing, amount),
    )
    companion object {
        fun rest() = DreambookHingeJournalPose()
        fun reading() = manual(1.0, 1.0, 0.0)
        fun full() = manual(1.0, 1.0, 1.0)
        fun reduced() = reading().copy(header = 1.0, calendar = 0.0)
        fun manual(openness: Double, expansion: Double, fullscreen: Double, extraAngle: Double = 0.0): DreambookHingeJournalPose {
            val e = expansion.coerceIn(0.0, 1.0); val q = fullscreen.coerceIn(0.0, 1.0)
            return DreambookHingeJournalPose(openness, e, q, extraAngle, 0.0, 1.0,
                1 - ease((e - 0.05) / 0.6), ease((e - 0.05) / 0.6))
        }
        fun transition(from: DreambookHingeJournalPose, to: DreambookHingeJournalPose, age: Double, velocity: Double): DreambookHingeJournalPose {
            val opening = to.openness > from.openness + 0.1
            val closing = to.openness < from.openness - 0.1
            if (!opening && !closing) return from.blend(to, ease(age / 0.5))
            val oAge = if (closing) max(0.0, age - 0.14) else age
            val spring = if (oAge >= 0.65) 1.0 else 1 - exp(-9 * oAge) * (cos(15 * oAge) + 0.6 * sin(15 * oAge))
            val o = mix(from.openness, to.openness, spring) + velocity * oAge * exp(-9 * oAge)
            val e = mix(from.expansion, to.expansion, ease((age - if (opening) 0.30 else 0.0) / 0.55))
            val q = mix(from.fullscreen, to.fullscreen, ease((age - if (opening) 0.72 else 0.0) / 0.50))
            val pose = manual(o, e, q)
            pose.closing = mix(from.closing, if (closing) 1.0 else if (opening) 0.0 else to.closing, ease(age / .18))
            val settle = ease(age / 0.22)
            pose.angle = mix(from.angle, pose.angle, settle); pose.lift = mix(from.lift, pose.lift, settle); pose.scale = mix(from.scale, pose.scale, settle)
            pose.header = mix(from.header, pose.header, ease(age / 0.18)); pose.calendar = mix(from.calendar, pose.calendar, ease(age / 0.18))
            return pose
        }
    }
}

private fun loopPose(raw: Double): DreambookHingeJournalPose {
    val t = ((raw % LOOP) + LOOP) % LOOP
    val o = ((t - 1.2) / (2.0 / 3)).coerceIn(0.0,1.0) * (1 - ((t - 6.05) / (2.0 / 3)).coerceIn(0.0,1.0))
    val e = ease((o - .45) / .55)
    val q = ease((t - 3.45) / .55) * (1 - ease((t - 4.60) / .55))
    val pose = DreambookHingeJournalPose.manual(o,e,q)
    pose.closing = if (t >= 6.05 && t < 6.05 + 2.0 / 3) 1.0 else 0.0
    pose.header = min(1.0,1 - ease((t - 1.20) / .35) + ease((t - 6.35) / .40))
    pose.calendar = ease((t - 1.45) / .40) * (1 - ease((t - 5.90) / .30))
    return pose
}

private class DreambookHingeJournalMotion {
    var frame by mutableDoubleStateOf(nowSeconds())
    var manualLive by mutableStateOf(false)
    var epoch = nowSeconds()
    var lastTouch = Double.NEGATIVE_INFINITY
    var transitionStarted = Double.NEGATIVE_INFINITY
    var transitionFrom = DreambookHingeJournalPose.rest()
    var transitionTo = DreambookHingeJournalPose.rest()
    var held = DreambookHingeJournalPose.rest()
    var dragStart = DreambookHingeJournalPose.rest()
    var dragging = false
    var releaseVelocity = 0.0
    var lastHaptic = Double.NEGATIVE_INFINITY
    var crossedOpen = false
    var reflected by mutableStateOf(false)
    var resume: Job? = null
    fun pose(reduced: Boolean, autoPlay: Boolean, previewTime: Double?): DreambookHingeJournalPose {
        val now = frame
        if (previewTime != null) return if (reduced) DreambookHingeJournalPose.reduced() else loopPose(previewTime)
        if (reduced) return DreambookHingeJournalPose.reduced()
        if (dragging) return held
        val touchAge = now - lastTouch
        if (touchAge < 2.6) return DreambookHingeJournalPose.transition(transitionFrom, transitionTo, max(0.0, now - transitionStarted), releaseVelocity)
            .blend(DreambookHingeJournalPose.rest().copy(closing = 1.0), ease((touchAge - 2) / 0.6))
        if (!autoPlay) return DreambookHingeJournalPose.rest()
        return loopPose(if (lastTouch == Double.NEGATIVE_INFINITY) now - epoch else touchAge - 2.6)
    }
}


/** Measured screen-space tracks use monotone, endpoint-clamped cubic Hermite interpolation. */
private object DreambookHingeJournalGeometry {
    fun sample(rows: Array<DoubleArray>, t: Double): DoubleArray {
        if (t <= rows.first()[0]) return rows.first().copyOfRange(1, rows.first().size)
        if (t >= rows.last()[0]) return rows.last().copyOfRange(1, rows.last().size)
        var i = 0
        while (i + 1 < rows.size && rows[i + 1][0] < t) i++
        val a = rows[i]; val b = rows[i + 1]; val dt = b[0] - a[0]
        val u = (t - a[0]) / dt; val u2 = u*u; val u3 = u2*u
        fun tangent(index: Int, column: Int): Double {
            if (index == 0) return (rows[1][column]-rows[0][column])/(rows[1][0]-rows[0][0])
            if (index == rows.lastIndex) return (rows[index][column]-rows[index-1][column])/(rows[index][0]-rows[index-1][0])
            val h0 = rows[index][0]-rows[index-1][0]; val h1 = rows[index+1][0]-rows[index][0]
            val d0 = (rows[index][column]-rows[index-1][column])/h0
            val d1 = (rows[index+1][column]-rows[index][column])/h1
            if (d0*d1 <= 0) return 0.0
            val w0 = 2*h1+h0; val w1 = h1+2*h0
            return (w0+w1)/(w0/d0+w1/d1)
        }
        return DoubleArray(a.size-1) { j ->
            val k = j+1
            val v = (2*u3-3*u2+1)*a[k] + (u3-2*u2+u)*dt*tangent(i,k) + (-2*u3+3*u2)*b[k] + (u3-u2)*dt*tangent(i+1,k)
            v.coerceIn(min(a[k],b[k]),max(a[k],b[k]))
        }
    }
    val reverseDepth = arrayOf(
        doubleArrayOf(0.4, 0.0),
        doubleArrayOf(0.65, 0.0),
        doubleArrayOf(0.66666667, 12.0),
        doubleArrayOf(0.7, 36.0),
        doubleArrayOf(0.73333333, 38.0),
        doubleArrayOf(0.8, 32.0),
        doubleArrayOf(0.86666667, 14.0),
        doubleArrayOf(0.93333333, 3.0),
        doubleArrayOf(1.06666667, 3.0),
    )
    val opening = arrayOf(
        doubleArrayOf(0.40000000, 318.77000000, 606.97000000, 318.77000000, 250.36000000, 320.22000000, 250.36000000, 323.13000000, 250.36000000, 323.13000000, 606.97000000, 320.22000000, 606.97000000),
        doubleArrayOf(0.46666667, 314.40000000, 620.07000000, 320.22000000, 260.54000000, 327.50000000, 264.91000000, 331.87000000, 269.28000000, 324.59000000, 615.70000000, 320.22000000, 618.61000000),
        doubleArrayOf(0.53333333, 253.27000000, 621.52000000, 295.48000000, 267.82000000, 308.58000000, 282.38000000, 353.70000000, 314.40000000, 311.49000000, 620.07000000, 279.47000000, 636.08000000),
        doubleArrayOf(0.60000000, 142.64000000, 644.81000000, 251.81000000, 276.56000000, 279.47000000, 301.30000000, 382.81000000, 339.14000000, 273.64000000, 669.56000000, 171.76000000, 637.53000000),
        doubleArrayOf(0.66666667, 24.74000000, 582.22000000, 196.50000000, 278.01000000, 235.80000000, 298.39000000, 423.57000000, 400.28000000, 234.34000000, 703.03000000, 66.96000000, 605.51000000),
        doubleArrayOf(0.73333333, -32.02000000, 490.52000000, 221.24000000, 243.08000000, 251.81000000, 302.76000000, 435.21000000, 480.33000000, 181.94000000, 727.78000000, -1.46000000, 550.20000000),
        doubleArrayOf(0.80000000, -69.87000000, 375.53000000, 231.43000000, 197.96000000, 301.30000000, 339.14000000, 423.57000000, 558.93000000, 122.27000000, 736.51000000, 0.00000000, 516.72000000),
        doubleArrayOf(0.86666667, -65.50000000, 275.10000000, 276.56000000, 158.66000000, 347.88000000, 372.62000000, 413.38000000, 598.23000000, 71.32000000, 714.68000000, 5.82000000, 489.07000000),
        doubleArrayOf(0.93333333, -16.01000000, 216.88000000, 333.32000000, 163.02000000, 358.07000000, 397.37000000, 398.82000000, 636.08000000, 36.39000000, 694.30000000, 10.19000000, 455.59000000),
        doubleArrayOf(1.00000000, -4.37000000, 197.96000000, 366.80000000, 179.03000000, 369.71000000, 420.66000000, 391.54000000, 665.19000000, 18.92000000, 684.11000000, 16.01000000, 439.58000000),
        doubleArrayOf(1.06666667, 10.19000000, 189.22000000, 382.81000000, 189.22000000, 372.62000000, 427.93000000, 382.81000000, 671.01000000, 10.19000000, 671.01000000, 18.92000000, 427.93000000),
    )
    val closing = arrayOf(
        doubleArrayOf(5.30000000, 10.19000000, 189.22000000, 382.81000000, 189.22000000, 372.62000000, 427.93000000, 382.81000000, 671.01000000, 10.19000000, 671.01000000, 18.92000000, 427.93000000),
        doubleArrayOf(5.40000000, -17.47000000, 267.82000000, 368.26000000, 237.26000000, 368.26000000, 420.66000000, 391.54000000, 660.82000000, 20.38000000, 682.66000000, 16.01000000, 443.94000000),
        doubleArrayOf(5.46666667, -29.11000000, 369.71000000, 355.16000000, 289.66000000, 340.60000000, 385.72000000, 401.73000000, 618.61000000, 49.49000000, 694.30000000, 5.82000000, 471.60000000),
        doubleArrayOf(5.53333333, -13.10000000, 477.42000000, 330.41000000, 342.06000000, 317.31000000, 362.43000000, 410.47000000, 576.40000000, 82.97000000, 707.40000000, 0.00000000, 499.26000000),
        doubleArrayOf(5.60000000, 23.29000000, 592.41000000, 328.96000000, 359.52000000, 334.78000000, 393.00000000, 416.29000000, 483.24000000, 135.37000000, 707.40000000, 55.31000000, 611.33000000),
        doubleArrayOf(5.66666667, 123.72000000, 665.19000000, 353.70000000, 365.34000000, 360.98000000, 387.18000000, 397.37000000, 411.92000000, 183.40000000, 700.12000000, 142.64000000, 666.64000000),
        doubleArrayOf(5.80000000, 315.86000000, 606.97000000, 317.31000000, 250.36000000, 318.77000000, 250.36000000, 323.13000000, 250.36000000, 323.13000000, 606.97000000, 318.77000000, 606.97000000),
        doubleArrayOf(5.96666667, 318.77000000, 606.97000000, 318.77000000, 250.36000000, 320.22000000, 250.36000000, 323.13000000, 250.36000000, 323.13000000, 606.97000000, 320.22000000, 606.97000000),
    )
    val returnCover = arrayOf(
        doubleArrayOf(5.56666670, 283.80000000, 315.90000000, 324.60000000, 334.80000000, 0.00000000, 524.00000000, -42.20000000, 505.10000000),
        doubleArrayOf(5.60000000, 257.60000000, 296.90000000, 331.90000000, 353.70000000, 16.00000000, 595.30000000, -40.80000000, 522.50000000),
        doubleArrayOf(5.66666670, 206.70000000, 266.40000000, 362.40000000, 362.40000000, 120.80000000, 673.90000000, -16.00000000, 542.90000000),
        doubleArrayOf(5.73333330, 154.30000000, 247.40000000, 377.00000000, 337.70000000, 221.20000000, 679.70000000, 5.80000000, 573.50000000),
        doubleArrayOf(5.80000000, 112.10000000, 246.00000000, 362.40000000, 302.80000000, 279.50000000, 650.60000000, 32.00000000, 592.40000000),
        doubleArrayOf(5.90000000, 77.10000000, 250.40000000, 329.00000000, 260.50000000, 312.90000000, 615.70000000, 61.10000000, 604.10000000),
        doubleArrayOf(5.96666670, 68.40000000, 251.80000000, 323.10000000, 251.80000000, 323.10000000, 607.00000000, 68.40000000, 607.00000000),
    )
    val frontCover = arrayOf(
        doubleArrayOf(0.40000000, 68.40000000, 251.80000000, 323.10000000, 251.80000000, 323.10000000, 607.00000000, 68.40000000, 607.00000000),
        doubleArrayOf(0.46670000, 78.60000000, 251.80000000, 323.10000000, 251.80000000, 308.60000000, 624.40000000, 64.00000000, 608.40000000),
        doubleArrayOf(0.53330000, 103.30000000, 253.30000000, 299.80000000, 262.00000000, 248.90000000, 647.70000000, 53.90000000, 608.40000000),
        doubleArrayOf(0.60000000, 144.10000000, 259.10000000, 257.60000000, 267.80000000, 141.20000000, 652.10000000, 40.80000000, 604.10000000),
        doubleArrayOf(0.63330000, 170.30000000, 264.90000000, 228.50000000, 266.40000000, 75.70000000, 636.10000000, 32.00000000, 596.80000000),
    )
}

private class DreambookHingeJournalMesh(pose: DreambookHingeJournalPose) {
    val paper: DoubleArray
    val cover: DoubleArray
    val reverse: DoubleArray
    val paperAlpha: Double
    val coverAlpha: Double
    val reverseAlpha: Double
    val ink: Double
    init {
        val o = pose.openness.coerceIn(0.0,1.0); val q = pose.fullscreen.coerceIn(0.0,1.0); val closing = pose.closing.coerceIn(0.0,1.0)
        val source = .4 + o*(2.0/3); val returnSource = 5.3 + (1-o)*(2.0/3)
        val openPaper = DreambookHingeJournalGeometry.sample(DreambookHingeJournalGeometry.opening,source)
        val closePaper = DreambookHingeJournalGeometry.sample(DreambookHingeJournalGeometry.closing,returnSource)
        paper = DoubleArray(12) { mix(openPaper[it],closePaper[it],closing) }
        val r = pose.pageFrame
        val flat = doubleArrayOf(r.left.toDouble(),r.top.toDouble(),r.right.toDouble(),r.top.toDouble(),r.right.toDouble(),r.centerY().toDouble(),r.right.toDouble(),r.bottom.toDouble(),r.left.toDouble(),r.bottom.toDouble(),r.left.toDouble(),r.centerY().toDouble())
        val flatten = ease(q/.12)
        for (i in paper.indices) paper[i] = mix(paper[i],flat[i],flatten)
        val openCover = DreambookHingeJournalGeometry.sample(DreambookHingeJournalGeometry.frontCover,source)
        val closeCover = DreambookHingeJournalGeometry.sample(DreambookHingeJournalGeometry.returnCover,returnSource)
        cover = DoubleArray(8) { mix(openCover[it],closeCover[it],closing) }
        paperAlpha = ease((source-.42)/.06)*(1-closing) + (1-ease((returnSource-5.73333333)/(1.0/30)))*closing
        coverAlpha = ((1-ease((source-.63333333)/.02666667))*(1-closing)+ease((returnSource-5.55)/(1.0/60))*closing)*(1-q)
        ink = ease((source-.93333333)/(2.0/15))*(1-closing)+(1-ease((returnSource-5.3)/.10))*closing
        val ex = paper[2]-paper[0]; val ey = paper[3]-paper[1]; val length = max(.001,hypot(ex,ey))
        val depth = DreambookHingeJournalGeometry.sample(DreambookHingeJournalGeometry.reverseDepth,source)[0]*(1-closing)+3*closing
        val nx = ey/length*depth; val ny = -ex/length*depth
        reverse = doubleArrayOf(paper[0]+nx,paper[1]+ny,paper[2]+nx,paper[3]+ny,paper[2],paper[3],paper[0],paper[1])
        reverseAlpha = paperAlpha*(1-coverAlpha)*(1-q)
        val a = pose.angle*PI/180; val c = cos(a)*pose.scale; val s = sin(a)*pose.scale
        for (points in arrayOf(paper,cover,reverse)) for (i in points.indices step 2) {
            val x = points[i]-196.5; val y = points[i+1]-429
            points[i] = 196.5+c*x-s*y; points[i+1] = 429+s*x+c*y+pose.lift
        }
    }
    private val hitPolygons get() = listOfNotNull(if(paperAlpha>.01) paper else null,if(coverAlpha>.01) cover else null)
    val hitFrame: RectF get() {
        val points = hitPolygons
        if(points.isEmpty()) return RectF(0f,0f,393f,852f)
        var x0=Double.POSITIVE_INFINITY;var y0=Double.POSITIVE_INFINITY;var x1=Double.NEGATIVE_INFINITY;var y1=Double.NEGATIVE_INFINITY
        for (p in points) for(i in p.indices step 2) { x0=min(x0,p[i]);x1=max(x1,p[i]);y0=min(y0,p[i+1]);y1=max(y1,p[i+1]) }
        return RectF(x0.toFloat(),y0.toFloat(),(x0+max(1.0,x1-x0)).toFloat(),(y0+max(1.0,y1-y0)).toFloat())
    }
    fun contains(x: Double,y: Double): Boolean = hitPolygons.any { p ->
        var inside=false;var j=p.size-2
        for(i in p.indices step 2) {
            if((p[i+1]>y)!=(p[j+1]>y) && x<(p[j]-p[i])*(y-p[i+1])/(p[j+1]-p[i+1])+p[i]) inside=!inside
            j=i
        }
        inside
    }
}

/** A clothbound journal unfolds through measured projective cover and paper-panel poses. */
@Composable
fun DreambookHingeJournal(modifier: Modifier = Modifier, autoPlay: Boolean = false, previewTime: Double? = null) {
    val reduced = dreambookHingeJournalReducedMotion()
    val density = LocalDensity.current.density
    val view = LocalView.current
    val c = remember { DreambookHingeJournalMotion() }
    val painter = remember(density) { DreambookHingeJournalPainter(density) }
    val background = remember(painter) { painter.background.asImageBitmap() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(c, autoPlay, previewTime, reduced, c.manualLive) {
        if (reduced || previewTime != null || (!autoPlay && !c.manualLive)) return@LaunchedEffect
        while (true) withFrameNanos { c.frame = it / 1e9 }
    }
    DisposableEffect(c) { onDispose { c.resume?.cancel() } }
    DisposableEffect(view) {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        val status = controller?.isAppearanceLightStatusBars; val navigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false; controller?.isAppearanceLightNavigationBars = false
        onDispose {
            if (status != null) controller.isAppearanceLightStatusBars = status
            if (navigation != null) controller.isAppearanceLightNavigationBars = navigation
        }
    }
    fun haptic() {
        val now = nowSeconds()
        if (now - c.lastHaptic > 0.16) { c.lastHaptic = now; view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    }
    fun resumeLater() {
        c.resume?.cancel()
        c.resume = scope.launch { delay(2650); if (!c.dragging) { c.manualLive = false; c.frame = nowSeconds() } }
    }
    fun animate(target: DreambookHingeJournalPose, from: DreambookHingeJournalPose? = null, preserveVelocity: Boolean = false) {
        c.frame = nowSeconds()
        c.transitionFrom = from ?: c.pose(reduced, autoPlay, previewTime)
        c.transitionTo = target; c.transitionStarted = c.frame; c.lastTouch = c.frame; c.manualLive = true
        if (!preserveVelocity) c.releaseVelocity = 0.0
        haptic(); resumeLater()
    }
    fun toggle(from: DreambookHingeJournalPose? = null) {
        c.frame = nowSeconds()
        val shown = from ?: c.pose(reduced, autoPlay, previewTime)
        if (reduced) { c.reflected = !c.reflected; animate(DreambookHingeJournalPose.reading()) }
        else if (shown.openness < 0.5) animate(DreambookHingeJournalPose.reading(), shown)
        else animate(if (shown.fullscreen > 0.5) DreambookHingeJournalPose.reading() else DreambookHingeJournalPose.full(), shown)
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFF2A2629)), contentAlignment = Alignment.Center) {
        val fit = min(maxWidth.value / 393, maxHeight.value / 852)
        Box(Modifier.requiredSize(393.dp, 852.dp).graphicsLayer { scaleX = fit; scaleY = fit; clip = true }
            .pointerInput(c, reduced, previewTime) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val tracker = VelocityTracker(); tracker.addPointerInputChange(down)
                    val p0 = down.position / density
                    c.frame = nowSeconds()
                    val shown = c.pose(reduced, autoPlay, previewTime)
                    val mesh = DreambookHingeJournalMesh(shown)
                    val footerY = 720 + 120 * (1 - shown.header)
                    val action = when {
                        shown.fullscreen > 0.4 && p0.x in 333f..377f && p0.y in 62f..106f -> 1
                        shown.header > 0.45 && p0.x in 30f..363f && p0.y >= footerY && p0.y <= footerY + 94 -> if (p0.x < 196.5f) 2 else 3
                        mesh.contains(p0.x.toDouble(), p0.y.toDouble()) -> 4
                        else -> 0
                    }
                    if (action == 0) return@awaitEachGesture
                    c.resume?.cancel()
                    if (action == 4) {
                        c.dragStart = shown; c.held = shown; c.crossedOpen = shown.openness > 0.86; c.dragging = true
                        c.lastTouch = nowSeconds(); c.manualLive = true; c.frame = c.lastTouch
                    }
                    var delta = Offset.Zero
                    try {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPointerInputChange(change)
                            delta = (change.position - down.position) / density
                            if (!change.pressed) break
                            if (action == 4) {
                                if (!reduced) {
                                    val raw = c.dragStart.openness - delta.x / 180
                                    val o = rubberBand(raw)
                                    val activation = ease(abs(delta.x) / 24.0)
                                    val expanded = ease((o - 0.66) / 0.34)
                                    val held = DreambookHingeJournalPose.manual(o, mix(c.dragStart.expansion, expanded, activation), c.dragStart.fullscreen * (1 - activation), (delta.y * 0.035).coerceIn(-4.0, 4.0))
                                    held.closing = c.dragStart.closing
                                    held.header = mix(c.dragStart.header, held.header, activation); held.calendar = mix(c.dragStart.calendar, held.calendar, activation)
                                    held.angle = mix(c.dragStart.angle, held.angle, activation); held.lift = mix(c.dragStart.lift, held.lift, activation)
                                    held.scale = mix(c.dragStart.scale, 1.0, activation); c.held = held
                                    if (o > 0.86 && !c.crossedOpen) { c.crossedOpen = true; haptic() }
                                    if (o < 0.08 && c.crossedOpen) { c.crossedOpen = false; haptic() }
                                }
                                c.lastTouch = nowSeconds(); c.frame = c.lastTouch; c.manualLive = true
                            }
                            change.consume()
                        }
                    } finally {
                        if (action == 4) {
                            c.dragging = false
                            if (hypot(delta.x, delta.y) < 6) toggle(c.held)
                            else {
                                val vx = tracker.calculateVelocity().x / density
                                val projected = c.held.openness - vx * 0.0008
                                c.releaseVelocity = (-vx / 180.0).coerceIn(-2.0, 2.0)
                                animate(if (projected > 0.5) DreambookHingeJournalPose.reading() else DreambookHingeJournalPose.rest(), c.held, true)
                            }
                        } else if (hypot(delta.x, delta.y) < 12) {
                            when (action) {
                                1 -> animate(DreambookHingeJournalPose.reading())
                                2 -> { if (reduced) c.reflected = !c.reflected; animate(DreambookHingeJournalPose.full()) }
                                3 -> { c.reflected = !c.reflected; animate(DreambookHingeJournalPose.full()) }
                            }
                        }
                    }
                }
            }) {
            Image(background, null, Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize()) {
                val canvas = drawContext.canvas.nativeCanvas
                canvas.save(); canvas.scale(density, density)
                painter.draw(canvas, c.pose(reduced, autoPlay, previewTime), c.reflected, reduced)
                canvas.restore()
            }
            Box(Modifier.requiredSize(253.dp, 352.dp).graphicsLayer {
                val rect = DreambookHingeJournalMesh(c.pose(reduced, autoPlay, previewTime)).hitFrame
                scaleX = rect.width() / 253; scaleY = rect.height() / 352
                translationX = (rect.centerX() - 126.5f) * density; translationY = (rect.centerY() - 176) * density
            }.semantics {
                val pose = c.pose(reduced, autoPlay, previewTime)
                contentDescription = if (pose.openness < 0.5) "Night Atlas journal" else "Dream journal page"
                stateDescription = if (pose.openness < 0.5) "Closed" else "Open"
                onClick(label = "Open or look closer") { toggle(); true }
            })
            for (index in 0..1) Box(Modifier.offset((30f + index * 166.5f).dp, 720.dp).size(166.5.dp, 94.dp).graphicsLayer {
                translationY = (120 * (1 - c.pose(reduced, autoPlay, previewTime).header) * density).toFloat()
            }.semantics {
                if (c.pose(reduced, autoPlay, previewTime).header > 0.45) {
                    role = Role.Button; contentDescription = if (index == 0) "Write" else "Reflect"
                    onClick {
                        if (index == 0) { if (reduced) c.reflected = !c.reflected; animate(DreambookHingeJournalPose.full()) }
                        else { c.reflected = !c.reflected; animate(DreambookHingeJournalPose.full()) }
                        true
                    }
                } else hideFromAccessibility()
            })
            Box(Modifier.offset(333.dp, 62.dp).size(44.dp).semantics {
                if (c.pose(reduced, autoPlay, previewTime).fullscreen > 0.4) {
                    role = Role.Button; contentDescription = "Close writing page"
                    onClick { animate(DreambookHingeJournalPose.reading()); true }
                } else hideFromAccessibility()
            })
        }
    }
}

/** Cloth, grain and fixed UI are cached; two exact homographies carry live page typography. */
private class DreambookHingeJournalPainter(private val density: Float) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val normal = Typeface.create("sans-serif", Typeface.NORMAL)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val serif = Typeface.create("serif", Typeface.NORMAL)
    private val projection = Matrix()
    private val flecks = List(90) { hash(it + 8.0) to hash(it + 63.0) }
    val background = bitmap(393, 852) { c ->
        c.drawColor(rgba(0x2A2629))
        fill(0xFFFFFF); paint.shader = LinearGradient(0f,0f,0f,852f,intArrayOf(0,0,rgba(0x242225,.65)),null,Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,393f,852f,paint)
    }
    private val header = bitmap(393,205) { c ->
        label(c,"Thursday, 15 October",196.5f,72f,16f,0xA5A1A5,serif)
        stroke(0xA5A1A5,.7f)
        for (i in 0..2) {
            val y = 66f + i * 6
            c.drawLine(357f,y,373f,y,paint)
            val x = floatArrayOf(362f,369f,360f)[i]
            fill(0x2A2629); c.drawCircle(x,y,1.8f,paint); stroke(0xA5A1A5,.7f); c.drawCircle(x,y,1.8f,paint)
        }
    }
    private val introduction = bitmap(393,205) { c ->
        label(c,"Give your dreams",196.5f,124f,31f,0xDEDCDE,serif)
        label(c,"a place to stay.",196.5f,160f,31f,0xDEDCDE,serif)
    }
    private val readingTitle = bitmap(393,205) { c -> label(c,"Pages from the night",196.5f,121f,26f,0xDEDCDE,serif) }
    private val footer = bitmap(393,852) { footer(it,false) }
    private val reducedFooter = bitmap(393,852) { footer(it,true) }
    private val calendar = bitmap(373,132) { c ->
        fill(0x454246); c.drawRoundRect(0f,0f,373f,132f,27f,27f,paint)
        stroke(0xFFFFFF,1f,.045); c.drawRoundRect(0f,0f,373f,132f,27f,27f,paint)
        fill(0xFFFFFF,.25); c.drawRoundRect(165.5f,9f,207.5f,12f,1.5f,1.5f,paint)
        label(c,"October 2026 ›",21f,24f,15f,0xCDC9CD,serif,left=true,top=true)
        stroke(0xCDC9CD,.6f); c.drawCircle(338f,34f,7f,paint); fill(0xCDC9CD); c.drawCircle(338f,34f,2.5f,paint)
        val days = listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun")
        for (i in days.indices) {
            val x = 39.5f + i * 49
            label(c,days[i],x,71.5f,10f,0xABA7AB,serif)
            if (i == 3) { fill(0xFFFFFF,.08); c.drawCircle(x,96f,13f,paint) }
            label(c,(12 + i).toString(),x,96f,14f,if(i==3)0xD8D4D8 else 0x8C888D,serif)
        }
    }
    private val front = bitmap(253,352) { cover(it,true) }
    private val back = bitmap(253,352) { cover(it,false) }

    private fun rgba(hex: Int, alpha: Double = 1.0) = AndroidColor.argb((alpha.coerceIn(0.0, 1.0) * 255).roundToInt(), hex shr 16 and 255, hex shr 8 and 255, hex and 255)
    private fun fill(hex: Int, alpha: Double = 1.0) { paint.color = rgba(hex, alpha); paint.shader = null; paint.maskFilter = null; paint.clearShadowLayer(); paint.style = Paint.Style.FILL }
    private fun stroke(hex: Int, width: Float, alpha: Double = 1.0) { fill(hex, alpha); paint.style = Paint.Style.STROKE; paint.strokeWidth = width; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND }
    private fun bitmap(w: Int, h: Int, draw: (AndroidCanvas) -> Unit): Bitmap = Bitmap.createBitmap((w * density).roundToInt(), (h * density).roundToInt(), Bitmap.Config.ARGB_8888).also {
        val c = AndroidCanvas(it); c.scale(density, density); draw(c)
    }
    private fun image(c: AndroidCanvas, image: Bitmap, rect: RectF, alpha: Double = 1.0) { fill(0xFFFFFF, alpha); c.drawBitmap(image, null, rect, paint) }
    private fun label(c: AndroidCanvas, word: String, x: Float, y: Float, size: Float, hex: Int, font: Typeface = normal, tracking: Float = 0f, left: Boolean = false, top: Boolean = false, alpha: Double = 1.0) {
        text.typeface = font; text.textSize = size; text.color = rgba(hex,alpha)
        // New York is narrower than Android system serif; preserve measured reference widths.
        text.textScaleX = if(font == serif) .93f else 1f
        text.letterSpacing = tracking / size
        val cursor = if (left) x else x - text.measureText(word) / 2
        val baseline = if (top) y - text.fontMetrics.ascent else y - (text.fontMetrics.ascent + text.fontMetrics.descent) / 2
        c.drawText(word,cursor,baseline,text)
    }
    private fun moon(c: AndroidCanvas, x: Float, y: Float, r: Float, hex: Int) {
        c.save(); c.translate(x, y)
        val path = Path().apply {
            moveTo(r * 0.12f, -r)
            cubicTo(-r * 1.4f, -r * 0.8f, -r * 1.2f, r * 0.9f, r * 0.1f, r)
            cubicTo(r * 0.8f, r * 0.95f, r * 1.1f, r * 0.35f, r, -r * 0.15f)
            cubicTo(-r * 0.15f, r * 0.4f, -r * 0.8f, -r * 0.55f, r * 0.12f, -r)
        }
        fill(hex); c.drawPath(path, paint); c.restore()
    }
    private fun footer(c: AndroidCanvas, reduced: Boolean) {
        if (!reduced) {
            label(c,"Save a dream while it is still near.",196.5f,672f,18f,0xB2AEB3,serif)
            label(c,"Return when the day grows quiet.",196.5f,694f,18f,0xB2AEB3,serif)
        }
        fill(0x000000,.16); paint.maskFilter = BlurMaskFilter(3f,BlurMaskFilter.Blur.NORMAL)
        c.drawRoundRect(30f,722f,363f,816f,47f,47f,paint)
        fill(0xFFFFFF); paint.shader = LinearGradient(0f,720f,0f,814f,intArrayOf(rgba(0x858286),rgba(0x626064),rgba(0x4B484D)),null,Shader.TileMode.CLAMP)
        c.drawRoundRect(30f,720f,363f,814f,47f,47f,paint)
        stroke(0xFFFFFF,1.5f); paint.shader = LinearGradient(0f,720f,0f,814f,rgba(0xFFFFFF,.6),rgba(0xFFFFFF,.04),Shader.TileMode.CLAMP)
        c.drawRoundRect(30f,720f,363f,814f,47f,47f,paint)
        fill(0x000000,.22); c.drawRect(196f,722f,197f,812f,paint)
        fill(0xFFFFFF,.08); c.drawRect(196.95f,723f,197.65f,811f,paint)
        label(c,"Write",113.25f,784f,18f,0xDBD7DC,serif)
        label(c,"Reflect",279.75f,784f,18f,0xDBD7DC,serif)
        stroke(0xDBD7DC,.85f)
        val pencil = Path().apply { moveTo(104f,761f); lineTo(104f,766f); lineTo(109f,765f); lineTo(124f,750f); lineTo(119f,745f); close() }
        c.drawPath(pencil,paint); c.drawLine(104f,768f,122f,768f,paint)
        val star = Path().apply { moveTo(279.75f,742f); quadTo(279.75f,754f,291.75f,754f); quadTo(279.75f,754f,279.75f,766f); quadTo(279.75f,754f,267.75f,754f); quadTo(279.75f,754f,279.75f,742f) }
        fill(0xDBD7DC); c.drawPath(star,paint)
    }
    private fun cover(c: AndroidCanvas, front: Boolean) {
        fill(0xFFFFFF)
        paint.shader = if(front) LinearGradient(0f,0f,253f,352f,intArrayOf(rgba(0x4A484B),rgba(0x484649),rgba(0x454346)),null,Shader.TileMode.CLAMP)
            else LinearGradient(0f,0f,253f,0f,rgba(0x514C52),rgba(0x413C43),Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,253f,352f,paint)
        stroke(0xFFFFFF,.45f,.03)
        for(i in 0 until 240) { val x=hash(i+12.0)*253;val y=hash(i+39.0)*352; c.drawLine(x,y,x+1+hash(i.toDouble())*4,y+.5f,paint) }
        if(front) {
            fill(0x373538,.35); c.drawRect(0f,0f,12f,352f,paint)
            fill(0xFFFFFF,.035); c.drawRect(10f,1f,11.2f,351f,paint)
            label(c,"Night Atlas",126.5f,74f,24f,0x000000,serif,alpha=.5)
            label(c,"Night Atlas",126.5f,73f,24f,0xB8B4B9,serif)
            label(c,"Notes from the night.",126.5f,99f,12f,0x99959B,serif)
            moon(c,126.5f,315f,10f,0x8F8B91)
            fill(0x8F8B91); c.drawCircle(136f,305f,1.5f,paint)
            stroke(0x8F8B91,.8f); c.drawLine(139f,307f,139f,313f,paint);c.drawLine(136f,310f,142f,310f,paint)
        } else {
            fill(0x000000,.15); c.drawRect(0f,0f,12f,352f,paint)
            stroke(0xFFFFFF,1f,.035); c.drawRoundRect(13f,13f,240f,339f,4f,4f,paint)
        }
    }
    private val sourceQuad = FloatArray(8)
    private val destinationQuad = FloatArray(8)
    private val projectedPath = Path()
    private fun outline(points: DoubleArray): Path {
        projectedPath.rewind(); projectedPath.moveTo(points[0].toFloat(),points[1].toFloat())
        for(i in 2 until points.size step 2) projectedPath.lineTo(points[i].toFloat(),points[i+1].toFloat())
        projectedPath.close(); return projectedPath
    }
    private fun transform(c: AndroidCanvas, points: DoubleArray, indices: IntArray, x: Float, y: Float, w: Float, h: Float, draw: () -> Unit) {
        sourceQuad[0]=x;sourceQuad[1]=y;sourceQuad[2]=x+w;sourceQuad[3]=y
        sourceQuad[4]=x+w;sourceQuad[5]=y+h;sourceQuad[6]=x;sourceQuad[7]=y+h
        for(i in 0..3) { destinationQuad[2*i]=points[2*indices[i]].toFloat(); destinationQuad[2*i+1]=points[2*indices[i]+1].toFloat() }
        // An edge-on panel has no area; never reuse the preceding frame's matrix after a failed solve.
        if (!projection.setPolyToPoly(sourceQuad,0,destinationQuad,0,4)) return
        c.save(); c.concat(projection); draw(); c.restore()
    }
    private fun shadow(c: AndroidCanvas, path: Path, radius: Float, dy: Float, alpha: Double) {
        // setShadowLayer also paints its source. Keep only the outer halo, avoiding a hard quad
        // behind the rounded cached cover and page corners.
        c.save(); c.clipOutPath(path)
        fill(0x000000,alpha); paint.setShadowLayer(radius,0f,dy,rgba(0x000000,alpha)); c.drawPath(path,paint)
        c.restore()
    }
    private val allCorners = intArrayOf(0,1,2,3)
    private val upperCorners = intArrayOf(0,1,2,5)
    private val lowerCorners = intArrayOf(5,2,3,4)
    fun draw(c: AndroidCanvas, pose: DreambookHingeJournalPose, reflected: Boolean, reduced: Boolean) {
        image(c,header,RectF(0f,0f,393f,205f),1-pose.fullscreen)
        image(c,introduction,RectF(0f,0f,393f,205f),ease((pose.header-.45)/.55)*(1-pose.fullscreen))
        image(c,readingTitle,RectF(0f,0f,393f,205f),(1-ease(pose.header/.55))*(1-pose.fullscreen))
        val mesh=DreambookHingeJournalMesh(pose)
        if(mesh.paperAlpha>.001) {
            val path=outline(mesh.paper)
            shadow(c,path,26f,16f,.20*mesh.paperAlpha)
            shadow(c,path,3f,2f,.22*mesh.paperAlpha)
            if(pose.fullscreen<1) for(i in 5 downTo 1) {
                c.save();c.translate(i*.38f,i*.45f);fill(if(i%2==0)0x68656A else 0x79767A,(1-pose.fullscreen)*mesh.paperAlpha);c.drawPath(path,paint);c.restore()
            }
        }
        if(mesh.coverAlpha>.001) {
            shadow(c,outline(mesh.cover),20f,12f,.25*mesh.coverAlpha)
        }
        if(mesh.reverseAlpha>.001) transform(c,mesh.reverse,allCorners,0f,0f,253f,352f) {
            image(c,back,RectF(0f,0f,253f,352f),mesh.reverseAlpha)
        }
        if(mesh.paperAlpha>.001) {
            val rect=pose.pageFrame;val w=rect.width();val h=rect.height();val half=h/2
            for(lower in listOf(false,true)) transform(c,mesh.paper,if(lower)lowerCorners else upperCorners,0f,if(lower)half else 0f,w,half) {
                c.clipRect(0f,if(lower)half else 0f,w,if(lower)h else half)
                val layer=c.saveLayerAlpha(0f,0f,w,h,(mesh.paperAlpha*255).roundToInt().coerceIn(0,255))
                page(c,pose,reflected,mesh.ink)
                c.restoreToCount(layer)
            }
        }
        val wave=sin(PI*pose.openness.coerceIn(0.0,1.0))
        stroke(0x8E8990,(.6+1.8*wave).toFloat(),(.16+.22*wave)*mesh.paperAlpha*(1-pose.fullscreen))
        c.drawLine(mesh.paper[10].toFloat(),mesh.paper[11].toFloat(),mesh.paper[4].toFloat(),mesh.paper[5].toFloat(),paint)
        if(mesh.coverAlpha>.001) transform(c,mesh.cover,allCorners,0f,0f,253f,352f) {
            val clip=Path().apply { addRoundRect(0f,0f,253f,352f,floatArrayOf(2f,2f,17f,17f,17f,17f,2f,2f),Path.Direction.CW) }
            c.clipPath(clip)
            image(c,front,RectF(0f,0f,253f,352f),mesh.coverAlpha)
            val left=(6+180*pose.openness).toFloat()
            fill(0xFFFFFF);paint.shader=LinearGradient(left,0f,left+105,0f,intArrayOf(0x00FFFFFF,rgba(0xFFFFFF,.10*max(0.0,sin(PI*pose.openness))*mesh.coverAlpha),0x00FFFFFF),null,Shader.TileMode.CLAMP)
            c.drawRect(left,0f,left+105,352f,paint)
            stroke(0xFFFFFF,.7f,.09*mesh.coverAlpha);c.drawPath(clip,paint)
        }
        // Calendar occludes the swinging paper; the closed-state footer slides below it.
        image(c,calendar,RectF(10f,711f,383f,843f),pose.calendar*(1-pose.fullscreen))
        val footerY=(120*(1-pose.header)).toFloat()
        image(c,if(reduced)reducedFooter else footer,RectF(0f,footerY,393f,852+footerY),pose.header)
        if(pose.fullscreen>.4) {
            fill(0x000000,.04*pose.fullscreen);c.drawCircle(355f,84f,14f,paint)
            stroke(0x777378,1f,pose.fullscreen);c.drawLine(351f,80f,359f,88f,paint);c.drawLine(359f,80f,351f,88f,paint)
        }
    }
    private fun page(c: AndroidCanvas, pose: DreambookHingeJournalPose, reflected: Boolean, ink: Double) {
        val rect=pose.pageFrame;val w=rect.width();val h=rect.height();val e=pose.expansion;val q=pose.fullscreen
        val corner=(17+7*q).toFloat()
        val outline=Path().apply { addRoundRect(0f,0f,w,h,corner,corner,Path.Direction.CW) }
        c.save();c.clipPath(outline)
        fill(0xFFFFFF);paint.shader=LinearGradient(0f,0f,w,h,intArrayOf(rgba(0xC7C4C7),rgba(0xBDBABD),rgba(0xC6C3C6)),null,Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,w,h,paint)
        val inset=mix(18.0,24.0,e).toFloat()
        val dateY=mix(mix(23.0,27.0,e),25.0,q).toFloat();val bodyY=mix(mix(54.0,59.0,e),62.0,q).toFloat()
        val dateSize=mix(mix(9.0,12.0,e),13.0,q).toFloat();val bodySize=mix(mix(11.0,15.0,e),17.0,q).toFloat()
        val lineHeight=mix(mix(15.0,21.0,e),25.0,q).toFloat()
        stroke(0xA8A4A9,.5f,.18*q)
        val start=mix(140.0,190.0,e).toFloat()
        for(i in 0 until 8) { val y=start+i*104;if(y<h-30)c.drawLine(inset,y,w-inset,y,paint) }
        fill(0x756F77,.025)
        for((x,y)in flecks)c.drawOval(x*w,y*h,x*w+.6f,y*h+.6f,paint)
        label(c,"Thursday, 15 October",inset,dateY,dateSize,0x666267,serif,left=true,top=true,alpha=ink)
        val lines=if(reflected)listOf("The door was already open. I stepped","into a room of little stars, and","every one of them felt familiar.") else listOf("I found a quiet stairway above","the clouds. At the top, a door","was already open.")
        lines.forEachIndexed { i,line->label(c,line,inset,bodyY+i*lineHeight,bodySize,0x514D52,serif,left=true,top=true,alpha=ink) }
        val fold=1+.12*sin(PI*pose.openness.coerceIn(0.0,1.0))
        fill(0xFFFFFF);paint.shader=LinearGradient(0f,h/2-60,0f,h/2+60,intArrayOf(0,rgba(0x000000,.025*fold*(1-q)),rgba(0x000000,.13*fold*(1-q)),rgba(0xFFFFFF,.025*fold*(1-q)),0),floatArrayOf(0f,.30f,.49f,.56f,1f),Shader.TileMode.CLAMP)
        c.drawRect(0f,h/2-60,w,h/2+60,paint)
        val pageLabels=c.saveLayerAlpha(0f,0f,w,h,((1-q)*ink*255).roundToInt())
        label(c,"Tap to write",inset,h-34,9f,0x969197,serif,left=true,top=true)
        label(c,"1 / 1",w-32,h-27,9f,0x969197,serif)
        c.restoreToCount(pageLabels)
        stroke(0xFFFFFF, .65f, .35); c.drawPath(outline, paint)
        c.restore()
    }

}

@Composable
private fun dreambookHingeJournalReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduced by remember(resolver) { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) { override fun onChange(selfChange: Boolean) { reduced = read() } }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}
