import Foundation

/// Complete numeric drawing contract, in the original 402 × 874 design space.
final class GlassPeakChartFrame {
  var t: Double = 0
  var presented: Double = 0
  var ys: [Double] = []
  var lit: Double = 0
  var tip: Double = 0
  var tube: Double = 0
  var orbX: Double = 0
  var orbY: Double = 0
  var orbR: Double = 0
  var orbGain: Double = 0
  var nodeX: [Double] = []
  var nodeY: [Double] = []
  var nodeLit: [Double] = []
  var nodeScale: [Double] = []
  var drop: [Double] = []
  var nodeBottom: [Double] = []
  var poolLevel: Double = 0
  var poolHumpX: Double = 0
  var poolHump: Double = 0
  var poolSlope: Double = 0
  var poolGain: Double = 0
  var poolStir: Double = 0
  var splashX: [Double] = []
  var splashY: [Double] = []
  var splashR: [Double] = []
  var beam: Double = 0
  var beamReach: Double = 0
  var ringX: Double = 0
  var ringY: Double = 0
  var ringR: Double = 0
  var ringA: Double = 0
  var flash: Double = 0
  var rimGlint: Double = 0
  var tiltX: Double = 0
  var tiltY: Double = 0
  var cardScale: Double = 0
  var value: Double = 0
  var numberAlpha: Double = 0
  var numberScale: Double = 0
  var numberGlow: Double = 0
  var period: Double = 0
  var periodPrev: Double = 0
  var periodMix: Double = 0
  var status: Double = 0
  var statusPrev: Double = 0
  var statusMix: Double = 0
  var spin: Double = 0
  var pillPress: Double = 0
  var refreshPress: Double = 0
  var chevron: Double = 0
  var touchX: Double = 0
  var touchY: Double = 0
  var touchA: Double = 0
  init(_ period: Double = 0) {
    let p = GlassPeakChartSimulation.PERIODS[Int(period)]
    let ys = GlassPeakChartSimulation.CURVES[Int(period)]
    let nodeX = p.nodes.map { GlassPeakChartSimulation.xOf($0) }
    let nodeY = p.nodes.map {
      GlassPeakChartSimulation.yOf(GlassPeakChartSimulation.heightAt(ys, $0))
    }
    let nodeBottom = nodeX.enumerated().map {
      GlassPeakChartSimulation.dropBottom($0.element, $0.offset == 2)
    }
    self.t = 0.0
    self.presented = 1.0
    self.ys = ys
    self.lit = 1.0
    self.tip = 0.0
    self.tube = 1.0
    self.orbX = nodeX[Int(2)]
    self.orbY = nodeY[Int(2)]
    self.orbR = 11
    self.orbGain = 1.0
    self.nodeX = nodeX
    self.nodeY = nodeY
    self.nodeLit = [1.0, 1.0, 1.0, 1.0]
    self.nodeScale = [1.0, 1.0, 1.0, 1.0]
    self.drop = [1.0, 1.0, 1.0, 1.0]
    self.nodeBottom = nodeBottom
    self.poolLevel = p.level
    self.poolHumpX = nodeX[Int(2)]
    self.poolHump = 0.35
    self.poolSlope = 0.0
    self.poolGain = 1.0
    self.poolStir = 0.0
    self.splashX = [0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0]
    self.splashY = [0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0]
    self.splashR = [0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0]
    self.beam = 0.22
    self.beamReach = 1.0
    self.ringX = nodeX[Int(2)]
    self.ringY = nodeY[Int(2)]
    self.ringR = 0.0
    self.ringA = 0.0
    self.flash = 0.0
    self.rimGlint = 0.0
    self.tiltX = 0.0
    self.tiltY = 0.0
    self.cardScale = 1.0
    self.value = p.value
    self.numberAlpha = 1.0
    self.numberScale = 1.0
    self.numberGlow = 0.0
    self.period = period
    self.periodPrev = period
    self.periodMix = 1.0
    self.status = 0.0
    self.statusPrev = 0.0
    self.statusMix = 1.0
    self.spin = 0.0
    self.pillPress = 0.0
    self.refreshPress = 0.0
    self.chevron = 0.0
    self.touchX = 201
    self.touchY = 416
    self.touchA = 0.0
  }
  func copy() -> GlassPeakChartFrame {
    let f = GlassPeakChartFrame()
    f.t = t
    f.presented = presented
    f.ys = ys
    f.lit = lit
    f.tip = tip
    f.tube = tube
    f.orbX = orbX
    f.orbY = orbY
    f.orbR = orbR
    f.orbGain = orbGain
    f.nodeX = nodeX
    f.nodeY = nodeY
    f.nodeLit = nodeLit
    f.nodeScale = nodeScale
    f.drop = drop
    f.nodeBottom = nodeBottom
    f.poolLevel = poolLevel
    f.poolHumpX = poolHumpX
    f.poolHump = poolHump
    f.poolSlope = poolSlope
    f.poolGain = poolGain
    f.poolStir = poolStir
    f.splashX = splashX
    f.splashY = splashY
    f.splashR = splashR
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
    return f
  }
  subscript(_ key: String) -> Double {
    get {
      switch key {
      case "t": return t
      case "presented": return presented
      case "lit": return lit
      case "tip": return tip
      case "tube": return tube
      case "orbX": return orbX
      case "orbY": return orbY
      case "orbR": return orbR
      case "orbGain": return orbGain
      case "poolLevel": return poolLevel
      case "poolHumpX": return poolHumpX
      case "poolHump": return poolHump
      case "poolSlope": return poolSlope
      case "poolGain": return poolGain
      case "poolStir": return poolStir
      case "beam": return beam
      case "beamReach": return beamReach
      case "ringX": return ringX
      case "ringY": return ringY
      case "ringR": return ringR
      case "ringA": return ringA
      case "flash": return flash
      case "rimGlint": return rimGlint
      case "tiltX": return tiltX
      case "tiltY": return tiltY
      case "cardScale": return cardScale
      case "value": return value
      case "numberAlpha": return numberAlpha
      case "numberScale": return numberScale
      case "numberGlow": return numberGlow
      case "period": return period
      case "periodPrev": return periodPrev
      case "periodMix": return periodMix
      case "status": return status
      case "statusPrev": return statusPrev
      case "statusMix": return statusMix
      case "spin": return spin
      case "pillPress": return pillPress
      case "refreshPress": return refreshPress
      case "chevron": return chevron
      case "touchX": return touchX
      case "touchY": return touchY
      case "touchA": return touchA
      default: preconditionFailure("Unknown frame channel")
      }
    }
    set {
      switch key {
      case "t": t = newValue
      case "presented": presented = newValue
      case "lit": lit = newValue
      case "tip": tip = newValue
      case "tube": tube = newValue
      case "orbX": orbX = newValue
      case "orbY": orbY = newValue
      case "orbR": orbR = newValue
      case "orbGain": orbGain = newValue
      case "poolLevel": poolLevel = newValue
      case "poolHumpX": poolHumpX = newValue
      case "poolHump": poolHump = newValue
      case "poolSlope": poolSlope = newValue
      case "poolGain": poolGain = newValue
      case "poolStir": poolStir = newValue
      case "beam": beam = newValue
      case "beamReach": beamReach = newValue
      case "ringX": ringX = newValue
      case "ringY": ringY = newValue
      case "ringR": ringR = newValue
      case "ringA": ringA = newValue
      case "flash": flash = newValue
      case "rimGlint": rimGlint = newValue
      case "tiltX": tiltX = newValue
      case "tiltY": tiltY = newValue
      case "cardScale": cardScale = newValue
      case "value": value = newValue
      case "numberAlpha": numberAlpha = newValue
      case "numberScale": numberScale = newValue
      case "numberGlow": numberGlow = newValue
      case "period": period = newValue
      case "periodPrev": periodPrev = newValue
      case "periodMix": periodMix = newValue
      case "status": status = newValue
      case "statusPrev": statusPrev = newValue
      case "statusMix": statusMix = newValue
      case "spin": spin = newValue
      case "pillPress": pillPress = newValue
      case "refreshPress": refreshPress = newValue
      case "chevron": chevron = newValue
      case "touchX": touchX = newValue
      case "touchY": touchY = newValue
      case "touchA": touchA = newValue
      default: preconditionFailure("Unknown frame channel")
      }
    }
  }
}
