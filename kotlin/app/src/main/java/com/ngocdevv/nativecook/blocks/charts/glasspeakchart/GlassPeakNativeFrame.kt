package com.ngocdevv.nativecook.blocks.charts.glasspeakchart

/** Mutable, double precision simulation picture. Only [render] crosses into Android floats. */
internal class GlassPeakNativeFrame(periodIndex: Int = 0) {
    var t = 0.0
    var presented = 1.0
    var ys = GlassPeakNativeContract.curves[periodIndex].copyOf()
    var lit = 1.0
    var tip = 0.0
    var tube = 1.0
    var orbX = 0.0
    var orbY = 0.0
    var orbR = 11.0
    var orbGain = 1.0
    var nodeX = DoubleArray(4)
    var nodeY = DoubleArray(4)
    var nodeLit = DoubleArray(4) { 1.0 }
    var nodeScale = DoubleArray(4) { 1.0 }
    var drop = DoubleArray(4) { 1.0 }
    var nodeBottom = DoubleArray(4)
    var poolLevel = GlassPeakNativeContract.periods[periodIndex].level
    var poolHumpX = 0.0
    var poolHump = 0.35
    var poolSlope = 0.0
    var poolGain = 1.0
    var poolStir = 0.0
    var splashX = DoubleArray(8)
    var splashY = DoubleArray(8)
    var splashR = DoubleArray(8)
    var beam = 0.22
    var beamReach = 1.0
    var ringX = 0.0
    var ringY = 0.0
    var ringR = 0.0
    var ringA = 0.0
    var flash = 0.0
    var rimGlint = 0.0
    var tiltX = 0.0
    var tiltY = 0.0
    var cardScale = 1.0
    var value = GlassPeakNativeContract.periods[periodIndex].value
    var numberAlpha = 1.0
    var numberScale = 1.0
    var numberGlow = 0.0
    var period = periodIndex
    var periodPrev = periodIndex
    var periodMix = 1.0
    var status = 0
    var statusPrev = 0
    var statusMix = 1.0
    var spin = 0.0
    var pillPress = 0.0
    var refreshPress = 0.0
    var chevron = 0.0
    var touchX = 201.0
    var touchY = 416.0
    var touchA = 0.0

    init {
        val nodes = GlassPeakNativeContract.periods[periodIndex].nodes
        for (i in 0..3) {
            nodeX[i] = GlassPeakNativeContract.xOf(nodes[i])
            nodeY[i] = GlassPeakNativeContract.yOf(GlassPeakNativeContract.heightAt(ys, nodes[i]))
            nodeBottom[i] = GlassPeakNativeContract.dropBottom(nodeX[i], i == 2)
        }
        orbX = nodeX[2]
        orbY = nodeY[2]
        poolHumpX = orbX
        ringX = orbX
        ringY = orbY
    }

    fun copyDeep(): GlassPeakNativeFrame = GlassPeakNativeFrame(period).also { f ->
        f.t = t
        f.presented = presented
        f.ys = ys.copyOf()
        f.lit = lit
        f.tip = tip
        f.tube = tube
        f.orbX = orbX
        f.orbY = orbY
        f.orbR = orbR
        f.orbGain = orbGain
        f.nodeX = nodeX.copyOf()
        f.nodeY = nodeY.copyOf()
        f.nodeLit = nodeLit.copyOf()
        f.nodeScale = nodeScale.copyOf()
        f.drop = drop.copyOf()
        f.nodeBottom = nodeBottom.copyOf()
        f.poolLevel = poolLevel
        f.poolHumpX = poolHumpX
        f.poolHump = poolHump
        f.poolSlope = poolSlope
        f.poolGain = poolGain
        f.poolStir = poolStir
        f.splashX = splashX.copyOf()
        f.splashY = splashY.copyOf()
        f.splashR = splashR.copyOf()
        f.beam = beam
        f.beamReach = beamReach
        f.ringX = ringX
        f.ringY = ringY
        f.ringR = ringR
        f.ringA = ringA
        f.flash = flash
        f.rimGlint = rimGlint
        f.tiltX = tiltX
        f.tiltY = tiltY
        f.cardScale = cardScale
        f.value = value
        f.numberAlpha = numberAlpha
        f.numberScale = numberScale
        f.numberGlow = numberGlow
        f.period = period
        f.periodPrev = periodPrev
        f.periodMix = periodMix
        f.status = status
        f.statusPrev = statusPrev
        f.statusMix = statusMix
        f.spin = spin
        f.pillPress = pillPress
        f.refreshPress = refreshPress
        f.chevron = chevron
        f.touchX = touchX
        f.touchY = touchY
        f.touchA = touchA
    }

    operator fun get(key: String): Double = when (key) {
        "orbU" -> error("orbU belongs to the simulation state")
        "orbR" -> orbR
        "orbGain" -> orbGain
        "cardScale" -> cardScale
        "tiltX" -> tiltX
        "tiltY" -> tiltY
        "poolLevel" -> poolLevel
        "poolHumpX" -> poolHumpX
        "poolHump" -> poolHump
        "poolSlope" -> poolSlope
        "poolGain" -> poolGain
        "beam" -> beam
        "numberScale" -> numberScale
        "pillPress" -> pillPress
        "refreshPress" -> refreshPress
        "tube" -> tube
        "lit" -> lit
        "tip" -> tip
        "orbX" -> orbX
        "orbY" -> orbY
        "poolStir" -> poolStir
        "beamReach" -> beamReach
        "ringX" -> ringX
        "ringY" -> ringY
        "ringR" -> ringR
        "ringA" -> ringA
        "flash" -> flash
        "rimGlint" -> rimGlint
        "value" -> value
        "numberAlpha" -> numberAlpha
        "numberGlow" -> numberGlow
        "periodMix" -> periodMix
        "statusMix" -> statusMix
        "spin" -> spin
        "chevron" -> chevron
        "touchX" -> touchX
        "touchY" -> touchY
        "touchA" -> touchA
        else -> error("Unknown frame channel $key")
    }

    operator fun set(key: String, value: Double) {
        when (key) {
            "orbR" -> orbR = value
            "orbGain" -> orbGain = value
            "cardScale" -> cardScale = value
            "tiltX" -> tiltX = value
            "tiltY" -> tiltY = value
            "poolLevel" -> poolLevel = value
            "poolHumpX" -> poolHumpX = value
            "poolHump" -> poolHump = value
            "poolSlope" -> poolSlope = value
            "poolGain" -> poolGain = value
            "beam" -> beam = value
            "numberScale" -> numberScale = value
            "pillPress" -> pillPress = value
            "refreshPress" -> refreshPress = value
            "tube" -> tube = value
            "lit" -> lit = value
            "tip" -> tip = value
            "orbX" -> orbX = value
            "orbY" -> orbY = value
            "poolStir" -> poolStir = value
            "beamReach" -> beamReach = value
            "ringX" -> ringX = value
            "ringY" -> ringY = value
            "ringR" -> ringR = value
            "ringA" -> ringA = value
            "flash" -> flash = value
            "rimGlint" -> rimGlint = value
            "value" -> this.value = value
            "numberAlpha" -> numberAlpha = value
            "numberGlow" -> numberGlow = value
            "periodMix" -> periodMix = value
            "statusMix" -> statusMix = value
            "spin" -> spin = value
            "chevron" -> chevron = value
            "touchX" -> touchX = value
            "touchY" -> touchY = value
            "touchA" -> touchA = value
            else -> error("Unknown frame channel $key")
        }
    }

    fun render(): GlassPeakFrame = GlassPeakFrame(
        time = t.toFloat(), presented = presented.toFloat(), ys = floatArray(ys),
        lit = lit.toFloat(), tip = tip.toFloat(), tube = tube.toFloat(),
        orbX = orbX.toFloat(), orbY = orbY.toFloat(), orbRadius = orbR.toFloat(),
        orbGain = orbGain.toFloat(), nodeX = floatArray(nodeX), nodeY = floatArray(nodeY),
        nodeLit = floatArray(nodeLit), nodeScale = floatArray(nodeScale),
        drops = floatArray(drop), nodeBottom = floatArray(nodeBottom),
        poolLevel = poolLevel.toFloat(), poolHumpX = poolHumpX.toFloat(),
        poolHump = poolHump.toFloat(), poolSlope = poolSlope.toFloat(),
        poolGain = poolGain.toFloat(), poolStir = poolStir.toFloat(),
        splashX = floatArray(splashX), splashY = floatArray(splashY),
        splashRadius = floatArray(splashR), beam = beam.toFloat(),
        beamReach = beamReach.toFloat(), ringX = ringX.toFloat(), ringY = ringY.toFloat(),
        ringRadius = ringR.toFloat(), ringAlpha = ringA.toFloat(), flash = flash.toFloat(),
        rimGlint = rimGlint.toFloat(), tiltX = tiltX.toFloat(), tiltY = tiltY.toFloat(),
        cardScale = cardScale.toFloat(), value = value.toFloat(),
        numberAlpha = numberAlpha.toFloat(), numberScale = numberScale.toFloat(),
        numberGlow = numberGlow.toFloat(), period = period, periodPrevious = periodPrev,
        periodMix = periodMix.toFloat(), status = status, statusPrevious = statusPrev,
        statusMix = statusMix.toFloat(), spin = spin.toFloat(), pillPress = pillPress.toFloat(),
        refreshPress = refreshPress.toFloat(), chevron = chevron.toFloat(),
        touchX = touchX.toFloat(), touchY = touchY.toFloat(), touchAlpha = touchA.toFloat(),
    )
}

private fun floatArray(values: DoubleArray) = FloatArray(values.size) { values[it].toFloat() }
