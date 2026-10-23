package com.ngocdevv.nativecook.blocks.animations.particlesigninring

import kotlin.math.*

/** Pure native port of the active source motion and measured carrier response.
 * Coordinates are logical dp; Android density is applied only by the renderer.
 */
object SignInMotion {
    const val STAGE_SIZE = 331.0
    const val STAGE_OFFSET_X = -0.0044
    const val STAGE_OFFSET_Y = 0.0276
    const val TEXT_OFFSET_Y = 0.0141
    const val AVATAR_SIZE = 0.47
    const val APPLE_SIZE = 0.28
    const val IDENTITY_DURATION = 1050.0
    const val INTRO_DURATION = 800.0
    const val ORBIT_PERIOD = 12000.0
    const val PHASE_DEGREES = -0.314
    const val FIRST_ICON = 800.0
    const val ICON_INTERVAL = 1500.0
    const val ICON_DURATION = 1700.0
    const val ICON_BOX_SIZE = 90.384615385
    const val RESTING_SCALE = 0.182995745
    const val TAU = PI * 2

    data class ParticleMeta(val angle: Double, val radius: Double, val dotRadius: Double, val revealDelay: Double)
    data class Identity(val appleOpacity: Double, val appleScale: Double, val blueOpacity: Double,
                        val blackOpacity: Double, val avatarOpacity: Double, val avatarScale: Double)
    data class Icon(val angle: Double, val scale: Double, val glyphOpacity: Double, val opacity: Double)
    data class Response(val radial: Double, val tangential: Double, val scale: Double)

    private val laneRadii = doubleArrayOf(0.304229607, 0.346394763, 0.383635448, 0.437734139)
    private val laneDotRadii = doubleArrayOf(0.014431017, 0.018650554, 0.017945619, 0.024984894)
    val particles = Array(96) { index ->
        val lane = index % 4
        val slot = index / 4
        ParticleMeta(slot / 24.0 * TAU + if (lane % 2 == 0) 0.0 else TAU / 48,
            laneRadii[lane], laneDotRadii[lane], ((slot * 5 + lane * 11) % 24) / 24.0 * 0.42)
    }
    val carrierIndices = IntArray(4) { iconIndex ->
        val target = -PI / 2 + iconIndex * PI / 4
        var closest = 3
        var distance = Double.POSITIVE_INFINITY
        for (index in 3 until particles.size step 4) {
            val delta = particles[index].angle - target
            val angularDistance = abs(atan2(sin(delta), cos(delta)))
            if (angularDistance < distance) { closest = index; distance = angularDistance }
        }
        closest
    }
    private val carrierSlots = IntArray(96) { carrierIndices.indexOf(it) }

    private val radialTimes = doubleArrayOf(0.0,50.0,100.0,150.0,200.0,250.0,300.0,350.0,400.0,450.0,500.0,550.0,600.0,650.0,700.0,750.0,800.0)
    private val radialValues = doubleArrayOf(0.307917,0.317723,0.345109,0.385948,0.457981,0.543629,0.635995,0.7246,0.801949,0.864439,0.911718,0.945457,0.96833,0.983003,0.991954,0.997012,1.0)
    private val rotationTimes = doubleArrayOf(0.0,16.667,33.333,50.0,66.667,83.333,100.0,116.667,135.0,150.0,201.7,251.7,301.7,351.7,401.7,503.3,553.3,605.0,656.7,705.0,753.3,800.0)
    private val rotationValues = doubleArrayOf(-73.7132,-73.0679,-72.3491,-71.5324,-70.6177,-69.605,-68.4943,-67.3151,-65.8897,-64.6,-57.0,-47.3,-37.5,-29.5,-23.1,-13.7,-10.3,-7.4,-5.0,-3.0,-1.3,0.0)
    private val sizeTimes = doubleArrayOf(0.0,150.0,200.0,250.0,300.0,350.0,400.0,500.0,650.0,800.0)
    private val innerSizes = doubleArrayOf(0.2,0.227,0.333,0.437,0.548,0.654,0.745,0.881,0.98,1.0)
    private val outerSizes = doubleArrayOf(0.4,0.471,0.532,0.606,0.693,0.776,0.843,0.934,0.99,1.0)
    private val colorTimes = doubleArrayOf(0.0,100.0,150.0,200.0,250.0,300.0,350.0,400.0,800.0)
    private val colorValues = doubleArrayOf(0.180758,0.259296,0.468568,0.602909,0.74193,0.837831,0.913511,0.997463,1.0)
    private val morphTimes = doubleArrayOf(0.0,36.7,101.7,168.3,233.3,401.7,900.0,1203.3,1403.3,1505.0,1538.3,1591.7,1690.0,1700.0)
    private val morphValues = doubleArrayOf(0.0,0.114,0.531,0.91,0.995,0.916,0.9,0.812,0.663,0.541,0.487,0.395,0.092,0.0)
    private val colorStops = arrayOf(
        doubleArrayOf(0.0,0.6481,0.3644,0.8077), doubleArrayOf(0.12,0.5204,0.4438,0.9178),
        doubleArrayOf(0.25,0.5047,0.7263,0.9175), doubleArrayOf(0.38,0.7629,0.7879,0.7529),
        doubleArrayOf(0.5,0.8747,0.7479,0.6191), doubleArrayOf(0.62,0.8898,0.6237,0.5132),
        doubleArrayOf(0.75,0.8345,0.4229,0.4616), doubleArrayOf(0.87,0.7759,0.3873,0.6345),
        doubleArrayOf(1.0,0.6481,0.3644,0.8077))
    private val colorLut = Array(256) { index ->
        val at = index / 255.0
        var stop = 0
        while (stop < colorStops.size - 2 && at > colorStops[stop + 1][0]) stop++
        val from = colorStops[stop]; val to = colorStops[stop + 1]
        val progress = (at - from[0]) / (to[0] - from[0])
        DoubleArray(3) { channel -> mix(from[channel + 1], to[channel + 1], progress) }
    }
    private val carrierColors = arrayOf(doubleArrayOf(244/255.0,244/255.0,244/255.0),
        doubleArrayOf(22/255.0,139/255.0,214/255.0), doubleArrayOf(22/255.0,138/255.0,244/255.0),
        doubleArrayOf(40/255.0,220/255.0,74/255.0))
    private val introBlue = doubleArrayOf(0.24,0.53,0.92)

    fun clamp(value: Double) = value.coerceIn(0.0, 1.0)
    fun mix(from: Double, to: Double, progress: Double) = from + (to - from) * progress
    fun smoothstep(from: Double, to: Double, value: Double): Double {
        val progress = clamp((value - from) / (to - from))
        return progress * progress * (3 - 2 * progress)
    }
    fun modulo(value: Double, divisor: Double) = ((value % divisor) + divisor) % divisor
    fun frameDelta(previousFrameMs: Double?, speed: Double) =
        min(32.0, previousFrameMs ?: (1000.0 / 60)) * (if (speed.isFinite()) speed.coerceIn(0.25,4.0) else 1.0)

    private fun track(time: Double, times: DoubleArray, values: DoubleArray): Double {
        if (time <= times[0]) return values[0]
        for (index in 1 until times.size) if (time <= times[index])
            return mix(values[index - 1], values[index], clamp((time - times[index - 1]) / (times[index] - times[index - 1])))
        return values.last()
    }
    fun radialScale(elapsedMs: Double) = track(max(0.0,elapsedMs), radialTimes, radialValues)
    fun orbitRotation(elapsedMs: Double) = if (elapsedMs < INTRO_DURATION)
        (track(max(0.0,elapsedMs),rotationTimes,rotationValues) + PHASE_DEGREES) * PI / 180
    else PHASE_DEGREES * PI / 180 + ((elapsedMs - INTRO_DURATION) / ORBIT_PERIOD) * TAU

    fun identity(elapsedMs: Double): Identity {
        val progress = clamp(elapsedMs / IDENTITY_DURATION)
        val grow = smoothstep(0.0,0.62,progress)
        val avatar = smoothstep(0.78,0.98,progress)
        val black = clamp((progress - 0.08) / 0.72)
        return Identity(1-avatar,mix(0.42,1.03,grow),1-black,black,avatar,mix(0.74,1.0,avatar))
    }
    fun icon(elapsedMs: Double, index: Int): Icon {
        val angle = particles[carrierIndices[index]].angle
        val firstAppearance = FIRST_ICON + index * ICON_INTERVAL
        if (elapsedMs < firstAppearance) return Icon(angle,RESTING_SCALE,0.0,0.0)
        val local = modulo(elapsedMs-firstAppearance,ICON_INTERVAL*4)
        if (local > ICON_DURATION) return Icon(angle,RESTING_SCALE,0.0,0.0)
        val scale = mix(RESTING_SCALE,1.04,track(local,morphTimes,morphValues))
        return Icon(angle,scale,smoothstep(RESTING_SCALE,RESTING_SCALE+0.3,scale),
            smoothstep(0.0,150.0,local)*(1-smoothstep(ICON_DURATION-300,ICON_DURATION,local)))
    }
    fun entrance(progress: Double, delay: Double, lane: Int): Double {
        val settled = mix(0.7,1.0,smoothstep(0.0,0.25,clamp(progress)))
        if (lane % 2 == 1) return settled
        val start = max(0.0,delay)*2/3
        return settled*smoothstep(start,start+0.04,clamp(progress))
    }
    fun response(particleIndex: Int, carrierIndex: Int, strength: Double, ratio: Double): Response {
        val active = clamp(strength)
        if (active <= 0 || ratio <= 0 || particleIndex !in particles.indices || carrierIndex !in particles.indices)
            return Response(0.0,0.0,1.0)
        val lane = particleIndex % 4
        val angularDelta = atan2(sin(particles[particleIndex].angle-particles[carrierIndex].angle),
            cos(particles[particleIndex].angle-particles[carrierIndex].angle))
        val delta = abs(angularDelta)
        val direction = if (angularDelta < 0) -1 else 1
        val halfStep = PI/24; val fullStep = PI/12
        val measured = when {
            lane == 0 && abs(delta-halfStep) <= 0.001 -> Response(-17.4,direction*15.1,0.735)
            lane == 0 && abs(delta-halfStep*3) <= 0.001 -> Response(-9.6,direction*11.2,0.738)
            lane == 1 && delta <= 0.001 -> Response(-81.6,0.0,0.75)
            lane == 1 && abs(delta-fullStep) <= 0.001 -> Response(-21.2,direction*22.6,0.75)
            lane == 2 && abs(delta-halfStep) <= 0.001 -> Response(33.2,direction*120.9,0.75)
            lane == 3 && abs(delta-fullStep) <= 0.001 -> Response(43.1,direction*45.2,0.7)
            else -> Response(0.0,0.0,1.0)
        }
        return Response(measured.radial/3*ratio*active,measured.tangential/3*ratio*active,1-(1-measured.scale)*active)
    }

    class ParticleState {
        var x = 0.0; var y = 0.0; var radius = 0.0
        var red = 0.0; var green = 0.0; var blue = 0.0; var alpha = 0.0
    }
    class Scene {
        val dots = Array(96) { ParticleState() }
        val icons = Array(4) { Icon(0.0,RESTING_SCALE,0.0,0.0) }
        var identity = identity(0.0)
        var radialScale = 0.0
        var rotation = 0.0
    }
    // Precompute the measured response once; only its strength changes over time.
    private val responses = Array(4) { icon -> Array(96) { dot -> response(dot,carrierIndices[icon],1.0,1.0) } }

    fun sample(elapsedMs: Double, size: Double = STAGE_SIZE, into: Scene = Scene()): Scene {
        val ratio = size/STAGE_SIZE
        val intro = clamp(elapsedMs/INTRO_DURATION)
        val bloom = track(intro*INTRO_DURATION,colorTimes,colorValues)
        into.identity = identity(elapsedMs)
        into.radialScale = radialScale(elapsedMs)
        into.rotation = orbitRotation(elapsedMs)
        for (index in 0..3) into.icons[index] = icon(elapsedMs,index)
        for (index in particles.indices) {
            val particle = particles[index]
            val angle = particle.angle+into.rotation
            val cosAngle = cos(angle); val sinAngle = sin(angle)
            val radius = size*particle.radius*into.radialScale
            var x = cosAngle*radius; var y = sinAngle*radius; var scale = 1.0
            for (icon in 0..3) {
                val strength = clamp((into.icons[icon].scale-RESTING_SCALE)/(1.04-RESTING_SCALE))
                val response = responses[icon][index]
                val radial = response.radial*ratio*strength
                val tangential = response.tangential*ratio*strength
                x += cosAngle*radial-sinAngle*tangential
                y += sinAngle*radial+cosAngle*tangential
                scale = min(scale,1-(1-response.scale)*strength)
            }
            val dot = into.dots[index]
            dot.x = x; dot.y = y
            dot.radius = size*particle.dotRadius*track(intro*INTRO_DURATION,sizeTimes,if (index%4 >= 2) outerSizes else innerSizes)*scale
            val slot = carrierSlots[index]
            val carrierActive = slot >= 0 && elapsedMs >= FIRST_ICON+slot*ICON_INTERVAL
            if (carrierActive) dot.radius = max(dot.radius,ICON_BOX_SIZE*ratio*max(0.0,into.icons[slot].scale)/2)
            val colorIndex = min(255,floor(modulo(atan2(y,x),TAU)/TAU*255).toInt())
            dot.red = mix(introBlue[0],colorLut[colorIndex][0],bloom)
            dot.green = mix(introBlue[1],colorLut[colorIndex][1],bloom)
            dot.blue = mix(introBlue[2],colorLut[colorIndex][2],bloom)
            dot.alpha = entrance(intro,particle.revealDelay,index%4)*0.96
            if (carrierActive) {
                val frame = into.icons[slot]; val color = carrierColors[slot]
                dot.red = mix(dot.red,color[0],frame.glyphOpacity)
                dot.green = mix(dot.green,color[1],frame.glyphOpacity)
                dot.blue = mix(dot.blue,color[2],frame.glyphOpacity)
                dot.alpha = max(dot.alpha,frame.opacity*0.96)
            }
        }
        return into
    }
}
