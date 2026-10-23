import Foundation

final class GlassPeakChartState {
  var f: GlassPeakChartFrame = GlassPeakChartFrame()
  var bodies: GlassPeakChartBodies = GlassPeakChartBodies()
  var mode: String = "rest"
  var age: Double = 0.0
  var time: Double = 0.0
  var reduced: Bool = false
  var idleLife: Double = 0.0
  var from: GlassPeakChartFrame = GlassPeakChartFrame()
  var fromU: Double = 0.0
  var catchFromU: Double = 0.0
  var node: Double = 0.0
  var orbU: Double = 0.0
  var nodes: [Double] = Array(repeating: 0, count: 4)
  var nodeAge: [Double] = Array(repeating: 0, count: 4)
  var nodeVelocity: [Double] = Array(repeating: 0, count: 4)
  var nodeActive: [Bool] = Array(repeating: false, count: 4)
  var nodeSide: [Double] = Array(repeating: 0, count: 4)
  var arc: [Double] = Array(repeating: 0, count: 96)
  var arcPeak: Double = 0.0
  var queue: String? = nil
  var queueUser: Bool = false
  var userRun: Bool = false
  var impactDone: Bool = false
  var ignitionDone: Bool = false
  var arrivalDone: Bool = false
  var impactClock: Double = 0.0
  var beamAge: Double = 0.0
  var beamPower: Double = 0.0
  var stirAge: Double = 0.0
  var stirPower: Double = 0.0
  var stirDuration: Double = 0.0
  var scrubStir: Double = 0.0
  var splashAge: Double = 0.0
  var splashPending: Double = 0.0
  var splashSpeed: Double = 0.0
  var splashVX: [Double] = Array(repeating: 0, count: 8)
  var splashVY: [Double] = Array(repeating: 0, count: 8)
  var fadePeriod: Double = 0.0
  var fadeSwapped: Bool = false
  var spinFrom: Double = 0.0
  var spinTarget: Double = 0.0
  var flashAge: Double = 0.0
  var flashPower: Double = 0.0
  var glintAge: Double = 0.0
  var glintPower: Double = 0.0
  var ringAge: Double = 0.0
  var ringPower: Double = 0.0
  var finger: Bool = false
  var scrubbing: Bool = false
  var fingerUser: Bool = false
  var fingerAge: Double = 0.0
  var fingerU: Double = 0.0
  var fingerVelocity: Double = 0.0
  var releaseAge: Double = 0.0
  var touchFrom: Double = 0.0
  var touchOn: Bool = false
  var pressPill: Bool = false
  var pressRefresh: Bool = false
  var pillAge: Double = 0.0
  var refreshAge: Double = 0.0
  var pillFrom: Double = 0.0
  var refreshFrom: Double = 0.0
  var settling: Bool = false
  var settleAge: Double = 0.0
  var revision: Double = 0.0
  var announcements: Double = 0.0
  var light: Double = 0.0
  var selection: Double = 0.0
  var medium: Double = 0.0
  var heavy: Double = 0.0
  var impacts: Double = 0.0
  var arrivals: Double = 0.0
  var autoPlay: Bool = false
  var paused: Bool = false
  var contact: Bool = false
  var restWait: Double = 0.0
  var returning: Bool = false
  var loop: Double = 0.0
  var cycles: Double = 0.0
  var demoEvent: Double = 0.0
  var closing: Double = 0.0
  init(autoPlay: Bool = false, reduced: Bool = false) {
    self.autoPlay = autoPlay
    self.reduced = reduced
    GlassPeakChartSimulation.home(self, 0)
    from = f.copy()
    idleLife = autoPlay || reduced ? 1 : 0
    stirDuration = 1.2
    splashSpeed = 1
    arcPeak = 1
    if !autoPlay && !reduced {
      mode = "opening"
      f.lit = 0
      f.orbGain = 0
      f.orbR = 7
      orbU = 0
      f.poolLevel = 0.08
      f.poolHump = 0
      f.poolGain = 0.7
      f.beam = 0
      f.beamReach = 0
      f.value = 0
      f.numberAlpha = 0.35
      f.status = 1
      f.statusPrev = 1
      for i in 0..<4 {
        f.nodeLit[i] = 0
        f.drop[i] = 0
      }
      for key in GlassPeakChartSimulation.SPRING_KEYS {
        GlassPeakChartSimulation.put(bodies[key], key == "orbU" ? orbU : f[key])
      }
      GlassPeakChartSimulation.place(self)
    }
  }
}
