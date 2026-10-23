import Foundation

/// Native translation of the reference simulation; all timing and spring constants are preserved.
enum GlassPeakChartSimulation {
  static let PERIODS = [
    GlassPeakChartPeriod(
      label: "Monthly", sub: "increase vs last month", value: 300.0, level: 1.0,
      knots: [
        [0.0, 0.074], [0.103, 0.153], [0.21, 0.09], [0.318, 0.049], [0.38, 0.14], [0.418, 0.47],
        [0.45, 0.87], [0.488, 0.985], [0.526, 1.0], [0.571, 0.73], [0.626, 0.4], [0.692, 0.206],
        [0.733, 0.153], [0.828, 0.115], [1.0, 0.13],
      ], nodes: [0.103, 0.318, 0.526, 0.733]),
    GlassPeakChartPeriod(
      label: "Weekly", sub: "increase vs last week", value: 48.0, level: 0.42,
      knots: [
        [0.0, 0.13], [0.14, 0.23], [0.3, 0.1], [0.44, 0.2], [0.56, 0.4], [0.62, 0.455],
        [0.68, 0.47], [0.74, 0.44], [0.8, 0.33], [0.88, 0.2], [1.0, 0.27],
      ], nodes: [0.14, 0.3, 0.68, 0.88]),
    GlassPeakChartPeriod(
      label: "Yearly", sub: "increase vs last year", value: 124.0, level: 0.68,
      knots: [
        [0.0, 0.05], [0.16, 0.1], [0.28, 0.07], [0.42, 0.22], [0.54, 0.3], [0.64, 0.34],
        [0.72, 0.56], [0.77, 0.69], [0.8, 0.71], [0.84, 0.67], [0.94, 0.46], [1.0, 0.5],
      ], nodes: [0.16, 0.42, 0.8, 0.94]),
  ]
  static let CURVES = PERIODS.map { buildYs($0.knots) }
  static let REST = [GlassPeakChartFrame(0), GlassPeakChartFrame(1), GlassPeakChartFrame(2)]
  static let SAMPLES = 96.0
  static let SPLASH = 8.0
  static let LOOP = 13.0
  static let ORB_R = 11.0
  static let PEAK_NODE = 2.0
  static let PLOT = (left: 28.0, right: 374.0, top: 294.0, zero: 476.0)
  static let CARD = (x: 28.0, y: 176.0, w: 346.0, h: 480.0)
  static let TEXT = (axis: 201.0, unused: 0.0)
  static let HIT = (plot: (y: 258.0, h: 236.0), unused: 0.0)
  static func buildYs(_ knots: [[Double]]) -> [Double] {
    let n = Double(knots.count)
    var d: [Double] = []
    var m: [Double] = []
    do {
      var i = 0.0
      while i < n - 1.0 {
        defer { i += 1 }
        d.append(
          (knots[Int(i + 1.0)][Int(1.0)] - knots[Int(i)][Int(1.0)])
            / (knots[Int(i + 1.0)][Int(0.0)] - knots[Int(i)][Int(0.0)]))
      }
    }
    m.append(d[Int(0.0)])
    do {
      var i = 1.0
      while i < n - 1.0 {
        defer { i += 1 }
        m.append((d[Int(i - 1.0)] * d[Int(i)] <= 0.0 ? 0.0 : (d[Int(i - 1.0)] + d[Int(i)]) / 2.0))
      }
    }
    m.append(d[Int(n - 2.0)])
    do {
      var i = 0.0
      while i < n - 1.0 {
        defer { i += 1 }
        if d[Int(i)] == 0.0 {
          m[Int(i)] = 0.0
          m[Int(i + 1.0)] = 0.0
        } else {
          let a = m[Int(i)] / d[Int(i)]
          let b = m[Int(i + 1.0)] / d[Int(i)]
          let s = a * a + b * b
          if s > 9.0 {
            let t = 3.0 / sqrt(s)
            m[Int(i)] = t * a * d[Int(i)]
            m[Int(i + 1.0)] = t * b * d[Int(i)]
          }
        }
      }
    }
    var ys: [Double] = []
    var k = 0.0
    do {
      var j = 0.0
      while j < SAMPLES {
        defer { j += 1 }
        let u = j / (SAMPLES - 1.0)
        while k < n - 2.0 && u > knots[Int(k + 1.0)][Int(0.0)] {
          k += 1
        }
        let h = knots[Int(k + 1.0)][Int(0.0)] - knots[Int(k)][Int(0.0)]
        let t = (u - knots[Int(k)][Int(0.0)]) / h
        let t2 = t * t
        let t3 = t2 * t
        ys.append(
          (2.0 * t3 - 3.0 * t2 + 1.0) * knots[Int(k)][Int(1.0)] + (t3 - 2.0 * t2 + t) * h
            * m[Int(k)] + (-2.0 * t3 + 3.0 * t2) * knots[Int(k + 1.0)][Int(1.0)] + (t3 - t2) * h
            * m[Int(k + 1.0)])
      }
    }
    do {
      var pass = 0.0
      while pass < 4.0 {
        defer { pass += 1 }
        var prev = ys[Int(0.0)]
        do {
          var j = 1.0
          while j < SAMPLES - 1.0 {
            defer { j += 1 }
            let here = ys[Int(j)]
            ys[Int(j)] = (prev + 2.0 * here + ys[Int(j + 1.0)]) / 4.0
            prev = here
          }
        }
      }
    }
    return ys
  }
  static func heightAt(_ ys: [Double], _ u: Double) -> Double {

    let f = min(1.0, max(0.0, u)) * (SAMPLES - 1.0)
    let i = min(SAMPLES - 2.0, floor(f))
    return ys[Int(i)] + (ys[Int(i + 1.0)] - ys[Int(i)]) * (f - i)
  }
  static func xOf(_ u: Double) -> Double {

    return PLOT.left + u * (PLOT.right - PLOT.left)
  }
  static func yOf(_ v: Double) -> Double {

    return PLOT.zero - v * (PLOT.zero - PLOT.top)
  }
  static func poolFrontY(
    _ level: Double, _ hump: Double, _ humpX: Double, _ slope: Double, _ x: Double
  ) -> Double {

    let dx = x - humpX
    return
      (CARD.y + CARD.h - (10.0 + 140.0 * level) - hump * 84.0
      * exp(-(dx * dx) / (2.0 * 58.0 * 58.0)) - slope * (x - 201.0) * 0.4)
  }
  static func poolSurfaceY(
    _ level: Double, _ hump: Double, _ humpX: Double, _ slope: Double, _ x: Double
  ) -> Double {

    let bottom = CARD.y + CARD.h
    return bottom - 0.42 * (bottom - poolFrontY(level, hump, humpX, slope, x))
  }
  static func dropBottom(_ x: Double, _ peak: Bool) -> Double {

    let off = abs(x - TEXT.axis)
    if off < 64.0 {
      return 492.0
    }
    if peak {
      return 516.0
    }
    return (off < 100.0 ? 516.0 : 556.0)
  }
  static let FIXED_DT = 1.0 / 240.0
  static let TAU = Double.pi * 2.0
  static let IMPACT = 1.6
  static let HIT_STOP = 0.05
  static let IGNITION = 1.88
  static let COUNT_START = IGNITION - 0.04
  static let STATUS_RETURN = IGNITION + 0.15
  static let SPLASH_LAUNCH = [
    [-14.0, -120.0, 260.0], [-9.0, -78.0, 365.0], [-4.0, -38.0, 430.0], [2.0, 24.0, 405.0],
    [9.0, 87.0, 305.0], [14.0, 120.0, 280.0], [-7.0, -57.0, 420.0], [6.0, 53.0, 385.0],
  ]
  static let SPRING_KEYS = [
    "orbU", "orbR", "orbGain", "cardScale", "tiltX", "tiltY", "poolLevel", "poolHumpX", "poolHump",
    "poolSlope", "poolGain", "beam", "numberScale", "pillPress", "refreshPress",
  ]
  static func clamp(_ value: Double, _ low: Double = 0.0, _ high: Double = 1.0) -> Double {

    return max(low, min(high, value))
  }
  static func bezier(_ t: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double
  {

    if t <= 0.0 {
      return 0.0
    }
    if t >= 1.0 {
      return 1.0
    }
    var lo = 0.0
    var hi = 1.0
    var u = t
    do {
      var i = 0.0
      while i < 18.0 {
        defer { i += 1 }
        let inv = 1.0 - u
        let x = 3.0 * inv * inv * u * x1 + 3.0 * inv * u * u * x2 + u * u * u
        if x < t {
          lo = u
        } else {
          hi = u
        }
        u = (lo + hi) * 0.5
      }
    }
    let inv = 1.0 - u
    return 3.0 * inv * inv * u * y1 + 3.0 * inv * u * u * y2 + u * u * u
  }
  static func easeOut(_ t: Double) -> Double {

    return bezier(t, 0.23, 1.0, 0.32, 1.0)
  }
  static func easeMove(_ t: Double) -> Double {

    return bezier(t, 0.77, 0.0, 0.175, 1.0)
  }
  static func spring(
    _ b: GlassPeakChartBody, _ target: Double, _ stiffness: Double, _ damping: Double, _ dt: Double
  ) -> Double {

    let offset = b.x - target
    let decay = damping / 2.0
    let discriminant = stiffness - decay * decay
    let attenuation = exp(-decay * dt)
    if discriminant > 0.00001 {
      let omega = sqrt(discriminant)
      let co = cos(omega * dt)
      let si = sin(omega * dt)
      let c = (b.v + decay * offset) / omega
      b.x = target + attenuation * (offset * co + c * si)
      b.v = attenuation * (b.v * co - (decay * c + omega * offset) * si)
    } else if discriminant < -0.00001 {
      let root = sqrt(-discriminant)
      let r1 = -decay + root
      let r2 = -decay - root
      let a = (b.v - r2 * offset) / (r1 - r2)
      let c = offset - a
      b.x = target + a * exp(r1 * dt) + c * exp(r2 * dt)
      b.v = a * r1 * exp(r1 * dt) + c * r2 * exp(r2 * dt)
    } else {
      let c = b.v + decay * offset
      b.x = target + attenuation * (offset + c * dt)
      b.v = attenuation * (b.v - decay * c * dt)
    }
    return b.x
  }
  static func put(_ b: GlassPeakChartBody, _ value: Double, _ velocity: Double = 0.0) {

    b.x = value
    b.v = velocity
  }
  static func nearest(_ s: GlassPeakChartState, _ u: Double) -> Double {

    var best = 0.0
    do {
      var i = 1.0
      while i < 4.0 {
        defer { i += 1 }
        if abs(s.nodes[Int(i)] - u) < abs(s.nodes[Int(best)] - u) {
          best = i
        }
      }
    }
    return best
  }
  static func ratio(_ s: GlassPeakChartState, _ u: Double) -> Double {

    return clamp(heightAt(s.f.ys, u) / max(0.001, heightAt(s.f.ys, s.nodes[Int(PEAK_NODE)])))
  }
  static func place(_ s: GlassPeakChartState) {

    let f = s.f
    f.orbX = xOf(s.orbU)
    f.orbY = yOf(heightAt(f.ys, s.orbU))
    do {
      var i = 0.0
      while i < 4.0 {
        defer { i += 1 }
        f.nodeX[Int(i)] = xOf(s.nodes[Int(i)])
        f.nodeY[Int(i)] = yOf(heightAt(f.ys, s.nodes[Int(i)]))
      }
    }
  }
  static func buildArc(_ s: GlassPeakChartState) {

    s.arc = Array(repeating: 0.0, count: 96)
    do {
      var i = 1.0
      while i < SAMPLES {
        defer { i += 1 }
        let dy = yOf(s.f.ys[Int(i)]) - yOf(s.f.ys[Int(i - 1.0)])
        let dx = (PLOT.right - PLOT.left) / (SAMPLES - 1.0)
        s.arc[Int(i)] = s.arc[Int(i - 1.0)] + sqrt(dx * dx + dy * dy)
      }
    }
    let at = s.nodes[Int(PEAK_NODE)] * (SAMPLES - 1.0)
    let i = floor(at)
    s.arcPeak = s.arc[Int(i)] + (s.arc[Int(i + 1.0)] - s.arc[Int(i)]) * (at - i)
  }
  static func arcU(_ s: GlassPeakChartState, _ progress: Double) -> Double {

    let goal = s.arcPeak * clamp(progress)
    do {
      var i = 1.0
      while i < SAMPLES {
        defer { i += 1 }
        if s.arc[Int(i)] >= goal {
          return
            (i - 1.0 + (goal - s.arc[Int(i - 1.0)])
              / max(0.001, s.arc[Int(i)] - s.arc[Int(i - 1.0)]))
            / (SAMPLES - 1.0)
        }
      }
    }
    return s.nodes[Int(PEAK_NODE)]
  }
  static func home(_ s: GlassPeakChartState, _ period: Double) {

    let presented = s.f.presented
    s.f = copyFrame(REST[Int(period)])
    s.f.presented = presented
    s.orbU = PERIODS[Int(period)].nodes[Int(PEAK_NODE)]
    s.node = PEAK_NODE
    s.nodes = PERIODS[Int(period)].nodes
    do {
      var i = 0.0
      while i < Double(SPRING_KEYS.count) {
        defer { i += 1 }
        let key = SPRING_KEYS[Int(i)]
        put(s.bodies[key], (key == "orbU" ? s.orbU : s.f[key]))
      }
    }
    do {
      var i = 0.0
      while i < 4.0 {
        defer { i += 1 }
        s.nodeAge[Int(i)] = 10.0
        s.nodeVelocity[Int(i)] = 0.0
        s.nodeActive[Int(i)] = true
      }
    }
    s.mode = "rest"
    s.idleLife = 1.0
    s.age = 0.0
    s.settling = false
    s.ringAge = 10.0
    s.glintAge = s.ringAge
    s.flashAge = s.glintAge
    s.ringPower = 0.0
    s.glintPower = s.ringPower
    s.flashPower = s.glintPower
    s.splashAge = 10.0
    s.stirAge = s.splashAge
    s.beamAge = s.stirAge
    s.splashPending = 0.0
    s.scrubStir = s.splashPending
    s.stirPower = s.scrubStir
    s.beamPower = s.stirPower
    do {
      var i = 0.0
      while i < SPLASH {
        defer { i += 1 }
        s.splashVY[Int(i)] = 0.0
        s.splashVX[Int(i)] = s.splashVY[Int(i)]
      }
    }
    s.scrubbing = false
    s.finger = s.scrubbing
    s.touchOn = false
    s.releaseAge = 10.0
  }
  static func isRest(_ s: GlassPeakChartState) -> Bool {

    return s.mode == "rest" && !s.finger && !s.settling && !s.pressPill && !s.pressRefresh
      && s.queue == nil
  }
  static func contact(_ s: GlassPeakChartState, _ down: Bool) {

    s.contact = down
    s.restWait = 0.0
    if down {
      let wasPaused = s.paused
      s.paused = true
      s.returning = false
      s.closing = 0.0
      if s.finger && !s.fingerUser {
        s.node = nearest(s, s.orbU)
        s.scrubbing = false
        s.finger = s.scrubbing
        s.settling = true
        s.settleAge = 0.0
        s.touchFrom = s.f.touchA
        s.releaseAge = 0.0
        s.touchOn = false
      }
      if !wasPaused {
        s.pressRefresh = false
        s.pressPill = s.pressRefresh
      }
    }
  }
  static func press(_ s: GlassPeakChartState, _ action: String, _ down: Bool, _ user: Bool) {

    if user && down {
      contact(s, true)
    }
    if action == "period" {
      s.pressPill = down
      s.pillAge = 0.0
      s.pillFrom = s.f.pillPress
    } else {
      s.pressRefresh = down
      s.refreshAge = 0.0
      s.refreshFrom = s.f.refreshPress
    }
    if user && down {
      s.light += 1
    }
    if user && !down {
      contact(s, false)
    }
  }
  static func begin(_ s: GlassPeakChartState, _ action: String, _ user: Bool) {

    s.from = copyFrame(s.f)
    s.fromU = s.orbU
    s.age = 0.0
    s.userRun = user
    s.arrivalDone = false
    s.ignitionDone = s.arrivalDone
    s.impactDone = s.ignitionDone
    s.settling = false
    s.revision += 1
    if s.reduced {
      s.mode = "fade"
      s.fadePeriod =
        (action == "period"
          ? (s.f.period + 1.0).truncatingRemainder(dividingBy: Double(PERIODS.count)) : s.f.period)
      s.fadeSwapped = false
      return
    }
    if action == "refresh" {
      s.beamAge = 10.0
      s.beamPower = 0.0
      s.mode = "refresh"
      s.f.statusPrev = s.f.status
      s.f.status = 1.0
      s.f.statusMix = 0.0
      s.spinFrom = s.f.spin
      s.node = PEAK_NODE
      buildArc(s)
      do {
        var i = 0.0
        while i < 4.0 {
          defer { i += 1 }
          s.nodeActive[Int(i)] = false
          s.nodeAge[Int(i)] = -1.0
        }
      }
    } else {
      s.mode = "morph"
      s.f.periodPrev = s.f.period
      s.f.period = (s.f.period + 1.0).truncatingRemainder(dividingBy: Double(PERIODS.count))
      s.f.periodMix = 0.0
    }
  }
  static func request(_ s: GlassPeakChartState, _ action: String, _ user: Bool) {

    if user {
      s.paused = true
      s.restWait = 0.0
    }
    if s.mode != "rest" || s.finger {
      s.queue = action
      s.queueUser = user
      return
    }
    begin(s, action, user)
  }
  static func pop(_ s: GlassPeakChartState, _ node: Double, _ amount: Double) {

    s.nodeVelocity[Int(node)] += amount * 34.0
  }
  static func flare(_ s: GlassPeakChartState, _ power: Double, _ glint: Double, _ ring: Double) {

    s.glintAge = 0.0
    s.flashAge = s.glintAge
    s.flashPower = power
    s.glintPower = glint
    if ring > 0.0 {
      s.ringAge = 0.0
      s.ringPower = ring
      s.f.ringX = s.f.orbX
      s.f.ringY = s.f.orbY
    }
  }
  static func fingerDown(_ s: GlassPeakChartState, _ x: Double, _ y: Double, _ user: Bool) {

    if user {
      contact(s, true)
    }
    s.finger = true
    s.fingerUser = user
    s.scrubbing =
      s.mode == "rest" && s.f.lit >= 0.9999 && y >= HIT.plot.y && y <= HIT.plot.y + HIT.plot.h
    s.fingerAge = 0.0
    s.catchFromU = s.orbU
    s.fingerU = clamp((x - PLOT.left) / (PLOT.right - PLOT.left), 0.03, 0.97)
    s.fingerVelocity = 0.0
    do {
      var i = 0.0
      while i < 4.0 {
        defer { i += 1 }
        let gap = s.fingerU - s.nodes[Int(i)]
        s.nodeSide[Int(i)] = (gap > 0.000001 ? 1.0 : (gap < -0.000001 ? -1.0 : 0.0))
      }
    }
    s.f.touchX = x
    s.f.touchY = y
    s.touchFrom = s.f.touchA
    s.touchOn = true
    s.releaseAge = 0.0
    if s.scrubbing {
      s.settling = false
    }
    if !s.reduced && !s.scrubbing {
      s.bodies.poolSlope.v += clamp((x - 201.0) / 173.0, -1.0, 1.0) * 1.8
    }
  }
  static func fingerMove(_ s: GlassPeakChartState, _ x: Double, _ y: Double, _ velocityX: Double) {

    if !s.finger {
      return
    }
    s.f.touchX = x
    s.f.touchY = y
    if !s.scrubbing {
      return
    }
    let u = clamp((x - PLOT.left) / (PLOT.right - PLOT.left), 0.03, 0.97)
    do {
      var i = 0.0
      while i < 4.0 {
        defer { i += 1 }
        let gap = u - s.nodes[Int(i)]
        let side = (gap > 0.000001 ? 1.0 : (gap < -0.000001 ? -1.0 : 0.0))
        if s.nodeSide[Int(i)] != 0.0 && side != s.nodeSide[Int(i)] {
          if !s.reduced {
            pop(s, i, 0.35)
          }
          if s.fingerUser {
            s.selection += 1
          }
          if i == PEAK_NODE {
            if !s.reduced {
              flare(s, 0.35, 0.3, 0.0)
            }
            if s.fingerUser {
              s.medium += 1
            }
          }
        }
        s.nodeSide[Int(i)] = side
      }
    }
    s.fingerU = u
    s.fingerVelocity = velocityX / (PLOT.right - PLOT.left)
  }
  static func fingerUp(_ s: GlassPeakChartState, _ velocityX: Double, _ user: Bool) {

    if !s.finger {
      return
    }
    if s.scrubbing {
      s.node = nearest(s, s.fingerU)
      if s.reduced {
        s.orbU = s.fingerU
        put(s.bodies.orbU, s.orbU)
      } else {
        put(s.bodies.orbU, s.orbU, velocityX / (PLOT.right - PLOT.left))
      }
      s.settling = !s.reduced
      s.settleAge = 0.0
      s.revision += 1
      if user {
        s.announcements += 1
      }
    }
    s.scrubbing = false
    s.finger = s.scrubbing
    s.touchOn = false
    s.touchFrom = s.f.touchA
    s.releaseAge = 0.0
    if user {
      contact(s, false)
    }
  }
  static func adjust(_ s: GlassPeakChartState, _ direction: Double) {

    contact(s, true)
    contact(s, false)
    if s.mode != "rest" {
      return
    }
    s.node = clamp(((s.settling ? s.node : nearest(s, s.orbU))) + direction, 0.0, 3.0)
    s.settling = !s.reduced
    s.settleAge = 0.0
    if s.reduced {
      s.orbU = s.nodes[Int(s.node)]
    }
    put(s.bodies.orbU, s.orbU)
    s.selection += 1
    s.revision += 1
    s.announcements += 1
  }
  static func returnHome(_ s: GlassPeakChartState) {

    if !isRest(s) {
      return
    }
    s.returning = true
    if s.f.period != 0.0 {
      request(s, "period", false)
    } else if abs(s.orbU - s.nodes[Int(PEAK_NODE)]) > 0.00001 {
      s.node = PEAK_NODE
      s.settling = true
      s.settleAge = 0.0
    }
  }
  static func resetLoop(_ s: GlassPeakChartState) {

    home(s, 0.0)
    s.loop = 0.0
    s.time = 0.0
    s.demoEvent = 0.0
    s.closing = 0.0
    s.returning = false
    s.paused = s.returning
    s.restWait = 0.0
    s.pressRefresh = false
    s.pressPill = s.pressRefresh
    s.queue = nil
    s.revision += 1
  }
  static func closeLoop(_ s: GlassPeakChartState, _ progressInput: Double) {
    var progress = progressInput

    if progress >= 1.0 - 1e-9 {
      progress = 1.0
    }
    let previous = easeOut(s.closing)
    let next = easeOut(progress)
    let remaining = max(1e-9, 1.0 - previous)
    let keep = clamp((1.0 - next) / remaining)
    s.closing = progress
    let rest = REST[Int(0.0)]
    do {
      var i = 0.0
      while i < Double(SPRING_KEYS.count) {
        defer { i += 1 }
        let key = SPRING_KEYS[Int(i)]
        let b = s.bodies[key]
        let target = (key == "orbU" ? PERIODS[Int(0.0)].nodes[Int(PEAK_NODE)] : rest[key])
        b.x = target + (b.x - target) * keep
        b.v *= keep
      }
    }
    do {
      var i = 0.0
      while i < 4.0 {
        defer { i += 1 }
        s.nodeVelocity[Int(i)] *= keep
      }
    }
    s.fingerVelocity *= keep
  }
  static func pluck(_ t: Double) -> Double {

    if t <= 0.0 {
      return 0.0
    }
    let zeta = 0.55
    let omega = 9.93
    let wd = omega * sqrt(1.0 - zeta * zeta)
    let raw =
      1.0 - exp(-zeta * omega * t) * (cos(wd * t) + zeta / sqrt(1.0 - zeta * zeta) * sin(wd * t))
    return raw + (1.0 - raw) * easeOut((t - 0.6) / 0.14)
  }
  static func liquidSurface(_ s: GlassPeakChartState, _ x: Double) -> Double {

    let hump = clamp(s.f.poolHump + 0.05 * sin(TAU * s.time / LOOP * 3.0) * s.idleLife)
    return poolSurfaceY(s.f.poolLevel, hump, s.f.poolHumpX, s.f.poolSlope, x)
  }
  static func splashStep(_ s: GlassPeakChartState, _ dt: Double) {

    let f = s.f
    if s.splashPending > 0.0 {
      s.splashAge = 0.0
      do {
        var i = 0.0
        while i < SPLASH {
          defer { i += 1 }
          let launch = SPLASH_LAUNCH[Int(i)]
          if i < s.splashPending {
            f.splashX[Int(i)] = clamp(f.orbX + launch[Int(0.0)], 49.0, 353.0)
            f.splashY[Int(i)] = liquidSurface(s, f.splashX[Int(i)])
            f.splashR[Int(i)] = 5.0
            s.splashVX[Int(i)] = launch[Int(1.0)] * s.splashSpeed
            s.splashVY[Int(i)] = -launch[Int(2.0)] * s.splashSpeed
          } else {
            f.splashR[Int(i)] = 0.0
            f.splashY[Int(i)] = f.splashR[Int(i)]
            f.splashX[Int(i)] = f.splashY[Int(i)]
            s.splashVY[Int(i)] = 0.0
            s.splashVX[Int(i)] = s.splashVY[Int(i)]
          }
        }
      }
      s.splashPending = 0.0
      return
    }
    s.splashAge += dt
    do {
      var i = 0.0
      while i < SPLASH {
        defer { i += 1 }
        if f.splashR[Int(i)] == 0.0 {
          continue
        }
        f.splashX[Int(i)] += s.splashVX[Int(i)] * dt
        f.splashY[Int(i)] += s.splashVY[Int(i)] * dt + 0.5 * 700.0 * dt * dt
        s.splashVY[Int(i)] += 700.0 * dt
        let flight = min(1.1, 2.0 * SPLASH_LAUNCH[Int(i)][Int(2.0)] * s.splashSpeed / 700.0)
        f.splashR[Int(i)] = 5.0 - 2.8 * clamp(s.splashAge / flight)
        f.splashX[Int(i)] = clamp(
          f.splashX[Int(i)], 44.0 + f.splashR[Int(i)], 358.0 - f.splashR[Int(i)])
        if f.splashY[Int(i)] < 500.0 + f.splashR[Int(i)] {
          f.splashY[Int(i)] = 500.0 + f.splashR[Int(i)]
          s.splashVY[Int(i)] = max(0.0, s.splashVY[Int(i)])
        }
        if s.splashAge >= 1.1 - 1e-9
          || f.splashY[Int(i)] >= liquidSurface(s, f.splashX[Int(i)]) - 1e-7
        {
          f.splashR[Int(i)] = 0.0
          f.splashY[Int(i)] = f.splashR[Int(i)]
          f.splashX[Int(i)] = f.splashY[Int(i)]
          s.splashVY[Int(i)] = 0.0
          s.splashVX[Int(i)] = s.splashVY[Int(i)]
        }
      }
    }
  }
  static func liquidStep(_ s: GlassPeakChartState, _ dt: Double, _ orbSpeed: Double) {

    let f = s.f
    if s.reduced {
      f.beam = 0.22
      f.beamReach = 1.0
      f.poolStir = 0.0
      return
    }
    if s.mode == "rest" || s.mode == "morph" {
      let pulse =
        (s.beamAge < 0.7 ? 0.6 * s.beamPower * sin(Double.pi * clamp(s.beamAge / 0.7)) : 0.0)
      let target = (s.scrubbing ? 0.6 : 0.22)
      var base = spring(s.bodies.beam, target, 900.0, 60.0, dt)
      if abs(base - target) < 0.00001 && abs(s.bodies.beam.v) < 0.0001 {
        put(s.bodies.beam, target)
        base = target
      }
      f.beam = clamp(base + pulse)
      f.beamReach = 1.0
    } else {
      put(s.bodies.beam, f.beam)
    }
    let stirring = (s.scrubbing ? clamp(max(abs(orbSpeed) / 500.0, abs(f.poolSlope) * 4.0)) : 0.0)
    s.scrubStir += (stirring - s.scrubStir) * (1.0 - exp(-8.0 * dt))
    if stirring == 0.0 && s.scrubStir < 0.00001 {
      s.scrubStir = 0.0
    }
    f.poolStir = clamp(max(s.scrubStir, s.stirPower * (1.0 - easeOut(s.stirAge / s.stirDuration))))
    splashStep(s, dt)
  }
  static func refreshStep(_ s: GlassPeakChartState, _ dt: Double) {

    let f = s.f
    let a = (s.age >= IMPACT ? max(IMPACT, s.age - HIT_STOP) : s.age)
    let p = PERIODS[Int(f.period)]
    let b = s.bodies
    let power = p.value / 300.0
    f.tube = 1.0 + ((a >= IMPACT ? 0.8 * power * (1.0 - easeOut((a - IMPACT) / 0.6)) : 0.0))
    f.beam =
      (a < IMPACT
        ? s.from.beam * (1.0 - easeOut(a / 0.15)) : 1.0 - 0.78 * easeOut((a - IMPACT) / 1.4))
    f.beamReach =
      (a < IMPACT ? s.from.beamReach * (1.0 - easeOut(a / 0.15)) : easeOut((a - IMPACT) / 0.24))
    if a < 0.42 {
      let travel = easeMove(a / 0.42)
      f.lit = s.from.lit * (1.0 - travel)
      s.orbU = min(s.fromU, f.lit)
      f.orbR = s.from.orbR + (7.0 - s.from.orbR) * travel
      f.orbGain =
        (s.from.orbGain + (0.8 - s.from.orbGain) * travel) * (1.0 - easeOut((a - 0.34) / 0.08))
      f.value = s.from.value * (1.0 - easeOut(a / 0.42))
      f.numberAlpha = s.from.numberAlpha + (0.35 - s.from.numberAlpha) * easeOut(a / 0.24)
      do {
        var i = 0.0
        while i < 4.0 {
          defer { i += 1 }
          if s.nodeAge[Int(i)] < 0.0 && f.lit <= s.nodes[Int(i)] {
            s.nodeAge[Int(i)] = 0.0
          }
          if s.nodeAge[Int(i)] >= 0.0 {
            s.nodeAge[Int(i)] += dt
            f.nodeLit[Int(i)] = s.from.nodeLit[Int(i)] * (1.0 - easeOut(s.nodeAge[Int(i)] / 0.12))
            f.drop[Int(i)] = s.from.drop[Int(i)] * (1.0 - easeOut(s.nodeAge[Int(i)] / 0.2))
          }
        }
      }
    } else if a < 0.7 {
      f.lit = 0.0
      s.orbU = 0.0
      f.orbR = 7.0
      f.value = 0.0
      f.orbGain = f.value
      f.numberAlpha = 0.35
      do {
        var i = 0.0
        while i < 4.0 {
          defer { i += 1 }
          if s.nodeAge[Int(i)] >= 0.0 {
            s.nodeAge[Int(i)] += dt
          }
          f.nodeLit[Int(i)] = 0.0
          f.drop[Int(i)] = s.from.drop[Int(i)] * (1.0 - easeOut(s.nodeAge[Int(i)] / 0.2))
        }
      }
    } else if a < 1.6 {
      let run = clamp((a - 0.7) / 0.9)
      f.lit = arcU(s, 0.3 * run + 0.7 * run * run)
      s.orbU = f.lit
      f.orbR = 7.0 + easeOut((a - 0.7) / 0.12) + 2.0 * run
      f.orbGain = 1.2 * easeOut((a - 0.7) / 0.12)
      f.tip = easeOut((a - 0.7) / 0.12)
    } else {
      if !s.impactDone {
        s.impactDone = true
        s.impacts += 1
        s.impactClock = (s.autoPlay && !s.paused ? s.loop + dt : s.time)
        s.orbU = s.nodes[Int(PEAK_NODE)]
        place(s)
        flare(s, 1.0, 0.6 * power, 1.0)
        put(b.orbR, 10.0, 247.0)
        b.cardScale.v = 0.39
        b.tiltX.v = -1.1
        if s.userRun {
          s.heavy += 1
        }
      }
      s.orbU = s.nodes[Int(PEAK_NODE)]
      f.lit = s.nodes[Int(PEAK_NODE)] + (1.0 - s.nodes[Int(PEAK_NODE)]) * easeOut((a - 1.6) / 0.38)
      f.tip = 1.0 - easeOut((a - 1.88) / 0.1)
      f.orbR = spring(b.orbR, ORB_R, 320.0, 14.0, dt)
      f.orbGain = 1.2 + 0.8 * easeOut((a - 1.6) / 0.035) - easeOut((a - 1.635) / 0.565)
    }
    if a >= 0.7 {
      do {
        var i = 0.0
        while i < 4.0 {
          defer { i += 1 }
          if !s.nodeActive[Int(i)] && f.lit >= s.nodes[Int(i)] - 0.000001 {
            s.nodeActive[Int(i)] = true
            s.nodeAge[Int(i)] = 0.0
            pop(s, i, 0.5)
          }
          if s.nodeActive[Int(i)] {
            s.nodeAge[Int(i)] += dt
            f.nodeLit[Int(i)] = easeOut(s.nodeAge[Int(i)] / 0.12)
            f.drop[Int(i)] = easeOut(s.nodeAge[Int(i)] / ((i == PEAK_NODE ? 0.26 : 0.32)))
          } else {
            f.drop[Int(i)] = 0.0
            f.nodeLit[Int(i)] = f.drop[Int(i)]
          }
        }
      }
    }
    if a >= IGNITION && !s.ignitionDone {
      s.ignitionDone = true
      b.poolSlope.v += 3.5
      s.stirAge = 0.0
      s.stirPower = 1.0
      s.stirDuration = 1.2
      s.splashPending = round(SPLASH * max(p.value / 300.0, 0.4))
      s.splashSpeed = 0.6 + 0.4 * p.value / 300.0
      put(b.numberScale, 0.96, 1.25 * power)
    }
    let ignited = a >= IGNITION
    f.poolLevel = spring(
      b.poolLevel, (ignited ? p.level : 0.08), (ignited ? 60.0 : 180.0), (ignited ? 9.0 : 27.0), dt)
    f.poolHump = spring(
      b.poolHump, (ignited ? 0.35 + 0.6 * (1.0 - easeOut((a - 2.05) / 0.55)) : 0.0), 100.0, 15.0, dt
    )
    f.poolGain = spring(
      b.poolGain,
      (ignited ? 1.0 + 0.35 * (1.0 - easeOut((a - 2.08) / 0.5)) : (a > 1.4 ? 0.55 : 0.7)), 180.0,
      25.0, dt)
    f.cardScale = spring(
      b.cardScale, (a >= 1.4 && a < 1.6 ? 0.992 : 1.0), (a < 1.6 ? 240.0 : 140.0),
      (a < 1.6 ? 26.0 : 14.0), dt)
    f.tiltX = spring(b.tiltX, 0.0, 180.0, 20.0, dt)
    if a >= COUNT_START {
      let roll = clamp((a - COUNT_START) / 1.1)
      f.value = p.value * easeOut(roll)
      f.numberAlpha = 0.35 + 0.65 * easeOut((a - COUNT_START) / 0.26)
      f.numberGlow = sin(Double.pi * roll)
    }
    f.numberScale =
      (ignited
        ? spring(b.numberScale, 1.0, 260.0, 16.0, min(dt, a - IGNITION))
        : 1.0 - 0.04 * easeOut((a - 0.7) / 0.9))
    if a < STATUS_RETURN {
      f.statusMix = easeOut(a / 0.24)
      f.spin = s.spinFrom + a * TAU / 0.8
    } else {
      if f.status != 0.0 {
        f.statusPrev = 1.0
        f.status = 0.0
        s.spinTarget = ceil((s.spinFrom + STATUS_RETURN * TAU / 0.8) / TAU) * TAU
        s.revision += 1
      }
      f.statusMix = easeOut((a - STATUS_RETURN) / 0.24)
      let atStop = s.spinFrom + STATUS_RETURN * TAU / 0.8
      f.spin = atStop + (s.spinTarget - atStop) * easeOut((a - STATUS_RETURN) / 0.4)
    }
    if a >= 3.4 {
      s.mode = "rest"
      f.statusPrev = 0.0
      f.statusMix = 1.0
      s.revision += 1
      put(b.orbU, s.orbU)
      s.settling = true
      s.settleAge = 1.25
    }
  }
  static func morphStep(_ s: GlassPeakChartState, _ dt: Double) {

    let f = s.f
    let a = s.age
    let p = PERIODS[Int(f.period)]
    let move = easeMove(a / 0.6)
    do {
      var i = 0.0
      while i < SAMPLES {
        defer { i += 1 }
        f.ys[Int(i)] = clamp(
          s.from.ys[Int(i)] + (CURVES[Int(f.period)][Int(i)] - s.from.ys[Int(i)])
            * pluck(a - 0.26 * i / (SAMPLES - 1.0)))
      }
    }
    do {
      var i = 0.0
      while i < 4.0 {
        defer { i += 1 }
        let oldU = (s.from.nodeX[Int(i)] - PLOT.left) / (PLOT.right - PLOT.left)
        s.nodes[Int(i)] = oldU + (p.nodes[Int(i)] - oldU) * move
        f.nodeBottom[Int(i)] =
          s.from.nodeBottom[Int(i)]
          + (dropBottom(xOf(p.nodes[Int(i)]), i == PEAK_NODE) - s.from.nodeBottom[Int(i)]) * move
      }
    }
    s.orbU = s.fromU + (p.nodes[Int(s.node)] - s.fromU) * move
    put(s.bodies.orbU, s.orbU)
    f.periodMix = easeOut(a / 0.42)
    f.chevron = (a < 0.52 ? TAU * easeMove(a / 0.52) : 0.0)
    let newRatio = clamp(
      heightAt(CURVES[Int(f.period)], p.nodes[Int(s.node)])
        / heightAt(CURVES[Int(f.period)], p.nodes[Int(PEAK_NODE)]))
    f.value = s.from.value + (p.value * newRatio - s.from.value) * easeOut(a / 0.7)
    f.numberGlow = 0.5 * sin(Double.pi * clamp(a / 0.7))
    f.poolLevel = spring(s.bodies.poolLevel, p.level * (0.22 + 0.78 * newRatio), 60.0, 9.0, dt)
    f.poolHump = spring(s.bodies.poolHump, 0.35, 90.0, 9.0, dt)
    if a >= 0.6 && !s.arrivalDone {
      s.arrivalDone = true
      s.arrivals += 1
      let power = p.value / 300.0
      s.stirAge = 0.0
      s.beamAge = s.stirAge
      s.beamPower = power
      s.stirPower = 0.5
      s.stirDuration = 0.8
      if f.period == 0.0 {
        s.splashPending = 5.0
        s.splashSpeed = 0.7
      }
      place(s)
      flare(s, 0.25 * power, 0.4 * power, (f.period == 0.0 ? 0.5 : 0.0))
      s.bodies.orbR.v += 150.0 * power
      if f.period == 0.0 && s.userRun {
        s.medium += 1
      }
      s.revision += 1
    }
    f.orbR = spring(
      s.bodies.orbR, (s.node == PEAK_NODE ? ORB_R : 7.5 + 3.5 * newRatio), 320.0, 18.0, dt)
    f.orbGain = spring(s.bodies.orbGain, 1.0, 180.0, 25.0, dt)
    f.poolGain = spring(
      s.bodies.poolGain,
      (f.period == 0.0 && a >= 0.6 ? 1.0 + 0.25 * sin(Double.pi * clamp((a - 0.6) / 0.7)) : 1.0),
      180.0, 25.0, dt)
    if a >= 1.05 {
      s.mode = "rest"
      f.periodPrev = f.period
      f.periodMix = 1.0
      s.settling = true
      s.settleAge = 1.2
      s.revision += 1
    }
  }
  static func integrate(_ s: GlassPeakChartState, _ dt: Double) {

    s.time += dt
    s.age += dt
    if s.mode == "refresh" {
      if abs(s.age - IMPACT) < 1e-9 {
        s.age = IMPACT
      }
      if abs(s.age - IMPACT - HIT_STOP) < 1e-9 {
        s.age = IMPACT + HIT_STOP
      }
      if abs(s.age - IGNITION - HIT_STOP) < 1e-9 {
        s.age = IGNITION + HIT_STOP
      }
    } else if s.mode == "morph" && abs(s.age - 0.6) < 1e-9 {
      s.age = 0.6
    }
    if s.mode == "refresh" && s.impactDone && s.age <= IMPACT + HIT_STOP + 1e-9 {
      return
    }
    let previousOrbX = s.f.orbX
    let idleTarget = (s.mode == "rest" && !s.finger ? 1.0 : 0.0)
    s.idleLife += (idleTarget - s.idleLife) * (1.0 - exp(-18.0 * dt))
    s.fingerAge += dt
    s.releaseAge += dt
    s.pillAge += dt
    s.refreshAge += dt
    s.flashAge += dt
    s.glintAge += dt
    s.ringAge += dt
    s.beamAge += dt
    s.stirAge += dt
    let f = s.f
    let b = s.bodies
    if s.mode == "opening" {
      f.spin += dt * TAU / 0.8
      if s.age >= 0.35 {
        begin(s, "refresh", false)
        s.age = 0.42
        s.spinFrom = f.spin - 0.42 * TAU / 0.8
        do {
          var i = 0.0
          while i < 4.0 {
            defer { i += 1 }
            s.nodeAge[Int(i)] = 1.0
          }
        }
      }
    }
    if s.mode == "fade" {
      if s.age < 0.12 {
        f.presented = 1.0 - easeOut(s.age / 0.12)
      } else {
        if !s.fadeSwapped {
          let age = s.age
          home(s, s.fadePeriod)
          s.mode = "fade"
          s.age = age
          s.fadeSwapped = true
          s.revision += 1
        }
        s.f.presented = easeOut((s.age - 0.12) / 0.16)
        if s.age >= 0.28 {
          s.mode = "rest"
          s.f.presented = 1.0
          s.revision += 1
        }
      }
    } else if s.mode == "refresh" {
      refreshStep(s, dt)
    } else if s.mode == "morph" {
      morphStep(s, dt)
    } else if s.mode == "rest" {
      if s.scrubbing {
        let catchup = (s.reduced ? 1.0 : easeOut(s.fingerAge / 0.09))
        s.orbU = s.catchFromU + (s.fingerU - s.catchFromU) * catchup
        put(b.orbU, s.orbU, s.fingerVelocity)
      } else if !s.reduced {
        s.orbU = clamp(spring(b.orbU, s.nodes[Int(s.node)], 260.0, 22.0, dt), 0.03, 0.97)
        if s.orbU != b.orbU.x {
          put(b.orbU, s.orbU)
        }
      }
      let height = ratio(s, s.orbU)
      let p = PERIODS[Int(f.period)]
      f.value = p.value * height
      let level = p.level * (0.22 + 0.78 * height)
      f.poolLevel = (s.reduced ? level : spring(b.poolLevel, level, 120.0, 16.0, dt))
      f.poolHump =
        (s.reduced
          ? ((s.scrubbing ? 0.75 : 0.35))
          : spring(b.poolHump, (s.scrubbing ? 0.75 : 0.35), 90.0, 9.0, dt))
      f.poolGain = (s.reduced ? 1.0 : spring(b.poolGain, 1.0, 160.0, 22.0, dt))
      let radius = (s.node == PEAK_NODE && !s.scrubbing && !s.settling ? ORB_R : 7.5 + 3.5 * height)
      f.orbR = (s.reduced || s.scrubbing ? radius : spring(b.orbR, radius, 320.0, 14.0, dt))
      if s.scrubbing {
        put(b.orbR, f.orbR)
      }
      f.orbGain =
        (s.reduced
          ? 1.0 : (s.scrubbing ? 0.8 + 0.4 * height : spring(b.orbGain, 1.0, 180.0, 24.0, dt)))
      if s.scrubbing {
        put(b.orbGain, f.orbGain)
      }
      f.numberAlpha = 1.0
      f.numberScale += (1.0 - f.numberScale) * (1.0 - exp(-18.0 * dt))
      f.numberGlow = 0.0
      if s.settling {
        s.settleAge += dt
        if s.settleAge >= 1.3 && abs(b.orbU.v) < 0.001
          && abs(s.orbU - s.nodes[Int(s.node)]) < 0.0001
        {
          s.settling = false
          s.revision += 1
        }
      }
    }
    if s.mode != "fade" {
      if s.mode != "rest" {
        put(b.orbGain, f.orbGain)
      }
      place(s)
      if s.mode != "refresh" {
        f.cardScale =
          (s.reduced
            ? 1.0
            : spring(
              b.cardScale, (s.finger ? 0.985 : 1.0), (s.finger ? 300.0 : 140.0),
              (s.finger ? 28.0 : 14.0), dt))
        f.tiltX =
          (s.reduced
            ? 0.0
            : spring(
              b.tiltX, (s.finger ? clamp((416.0 - f.touchY) / 240.0, -1.0, 1.0) * 0.06 : 0.0),
              (s.finger ? 180.0 : 140.0), (s.finger ? 20.0 : 14.0), dt))
      }
      f.tiltY =
        (s.reduced
          ? 0.0
          : spring(
            b.tiltY, (s.finger ? clamp((f.touchX - 201.0) / 173.0, -1.0, 1.0) * 0.1 : 0.0),
            (s.finger ? 180.0 : 140.0), (s.finger ? 20.0 : 14.0), dt))
      f.poolHumpX = (s.reduced ? f.orbX : spring(b.poolHumpX, f.orbX, 90.0, 9.0, dt))
      f.poolSlope =
        (s.reduced
          ? 0.0
          : spring(
            b.poolSlope,
            (s.scrubbing ? -clamp(s.fingerVelocity * 346.0 / 900.0, -1.0, 1.0) * 0.25 : 0.0), 110.0,
            10.0, dt))
      let orbSpeed =
        (s.scrubbing && s.fingerAge >= 0.09
          ? s.fingerVelocity * (PLOT.right - PLOT.left) : (f.orbX - previousOrbX) / max(dt, 1e-9))
      liquidStep(s, dt, orbSpeed)
      f.touchA =
        (s.touchOn
          ? s.touchFrom + (1.0 - s.touchFrom) * easeOut(s.fingerAge / 0.12)
          : s.touchFrom * (1.0 - easeOut(s.releaseAge / 0.22)))
      if !s.touchOn {
        f.touchX += (201.0 - f.touchX) * (1.0 - exp(-18.0 * dt))
        f.touchY += (416.0 - f.touchY) * (1.0 - exp(-18.0 * dt))
      }
      f.pillPress =
        (s.pressPill
          ? s.pillFrom + (1.0 - s.pillFrom) * easeOut(s.pillAge / 0.12)
          : spring(b.pillPress, 0.0, 280.0, 23.0, dt))
      f.refreshPress =
        (s.pressRefresh
          ? s.refreshFrom + (1.0 - s.refreshFrom) * easeOut(s.refreshAge / 0.12)
          : spring(b.refreshPress, 0.0, 280.0, 23.0, dt))
      if s.pressPill {
        put(b.pillPress, f.pillPress)
      }
      if s.pressRefresh {
        put(b.refreshPress, f.refreshPress)
      }
      f.pillPress = clamp(f.pillPress)
      f.refreshPress = clamp(f.refreshPress)
      do {
        var i = 0.0
        while i < 4.0 {
          defer { i += 1 }
          s.nodeVelocity[Int(i)] +=
            ((1.0 - f.nodeScale[Int(i)]) * 420.0 - 20.0 * s.nodeVelocity[Int(i)]) * dt
          f.nodeScale[Int(i)] += s.nodeVelocity[Int(i)] * dt
        }
      }
      f.flash =
        (s.reduced
          ? 0.0 : s.flashPower * (1.0 - easeOut(s.flashAge / ((s.flashPower == 1.0 ? 0.42 : 0.3)))))
      f.rimGlint =
        (s.reduced
          ? 0.0
          : s.glintPower * easeOut(s.glintAge / 0.3) * (1.0 - easeOut((s.glintAge - 0.3) / 0.5)))
      if s.mode == "refresh" && s.impactDone {
        f.rimGlint = max(
          f.rimGlint,
          0.8 * PERIODS[Int(f.period)].value / 300.0 * (1.0 - easeOut(s.glintAge / 0.25)))
      }
      if s.mode == "refresh" && s.age < 0.7 {
        f.rimGlint += 0.5 * easeOut((s.age - 0.34) / 0.08) * (1.0 - easeOut((s.age - 0.42) / 0.28))
      }
      f.ringA = (s.reduced ? 0.0 : s.ringPower * (1.0 - easeOut((s.ringAge - 0.288) / 0.432)))
      f.ringR =
        (s.ringPower > 0.0
          ? 300.0 * easeOut(s.ringAge / 0.72) * (1.0 - easeOut((s.ringAge - 0.72) / 0.3)) : 0.0)
      if s.ringAge > 0.72 {
        f.ringX += (f.orbX - f.ringX) * (1.0 - exp(-18.0 * dt))
        f.ringY += (f.orbY - f.ringY) * (1.0 - exp(-18.0 * dt))
      }
    }
    if isRest(s) && s.autoPlay && s.paused && !s.contact {
      s.restWait += dt
    } else {
      s.restWait = 0.0
    }
    if s.mode == "rest" && !s.finger && !s.settling && s.queue != nil {
      let action = s.queue!
      let user = s.queueUser
      s.queue = nil
      begin(s, action, user)
    }
  }
  static func step(_ s: GlassPeakChartState, _ dt: Double) {

    var remaining = clamp(dt, 0.0, 0.05)
    while remaining > 1e-9 {
      var slice = min(FIXED_DT, remaining)
      if s.mode == "refresh" {
        let boundary =
          (!s.impactDone
            ? IMPACT
            : (s.age < IMPACT + HIT_STOP - 1e-9
              ? IMPACT + HIT_STOP : (!s.ignitionDone ? IGNITION + HIT_STOP : 0.0)))
        if boundary > s.age + 1e-9 {
          slice = min(slice, boundary - s.age)
        }
      } else if s.mode == "morph" && !s.arrivalDone && s.age < 0.6 - 1e-9 {
        slice = min(slice, 0.6 - s.age)
      }
      integrate(s, slice)
      remaining -= slice
    }
  }
  static func copyFrame(_ f: GlassPeakChartFrame) -> GlassPeakChartFrame { f.copy() }
  static func toFrame(_ s: GlassPeakChartState) -> GlassPeakChartFrame {
    let f = s.f.copy()
    f.t = s.reduced ? 0 : s.autoPlay && !s.paused ? s.loop : s.time
    if !s.reduced && s.mode == "refresh" && s.impactDone && s.age <= IMPACT + HIT_STOP + 0.000000001
    {
      f.t = s.impactClock
    }
    if !s.reduced {
      let phase = TAU * f.t / LOOP
      f.tube += 0.04 * sin(phase * 2) * s.idleLife
      f.poolHump = clamp(f.poolHump + 0.05 * sin(phase * 3) * s.idleLife)
      f.orbGain = clamp(f.orbGain + 0.06 * sin(phase) * s.idleLife, 0, 2)
    }
    if s.closing > 0 {
      let rest = REST[0]
      let blend = easeOut(s.closing)
      for i in f.ys.indices { f.ys[i] += (rest.ys[i] - f.ys[i]) * blend }
      f.lit = f.lit * (1 - blend) + rest.lit * blend
      f.tip = f.tip * (1 - blend) + rest.tip * blend
      f.tube = f.tube * (1 - blend) + rest.tube * blend
      f.orbX = f.orbX * (1 - blend) + rest.orbX * blend
      f.orbY = f.orbY * (1 - blend) + rest.orbY * blend
      f.orbR = f.orbR * (1 - blend) + rest.orbR * blend
      f.orbGain = f.orbGain * (1 - blend) + rest.orbGain * blend
      for i in f.nodeX.indices { f.nodeX[i] += (rest.nodeX[i] - f.nodeX[i]) * blend }
      for i in f.nodeY.indices { f.nodeY[i] += (rest.nodeY[i] - f.nodeY[i]) * blend }
      for i in f.nodeLit.indices { f.nodeLit[i] += (rest.nodeLit[i] - f.nodeLit[i]) * blend }
      for i in f.nodeScale.indices {
        f.nodeScale[i] += (rest.nodeScale[i] - f.nodeScale[i]) * blend
      }
      for i in f.drop.indices { f.drop[i] += (rest.drop[i] - f.drop[i]) * blend }
      for i in f.nodeBottom.indices {
        f.nodeBottom[i] += (rest.nodeBottom[i] - f.nodeBottom[i]) * blend
      }
      f.poolLevel = f.poolLevel * (1 - blend) + rest.poolLevel * blend
      f.poolHumpX = f.poolHumpX * (1 - blend) + rest.poolHumpX * blend
      f.poolHump = f.poolHump * (1 - blend) + rest.poolHump * blend
      f.poolSlope = f.poolSlope * (1 - blend) + rest.poolSlope * blend
      f.poolGain = f.poolGain * (1 - blend) + rest.poolGain * blend
      f.poolStir = f.poolStir * (1 - blend) + rest.poolStir * blend
      for i in f.splashX.indices { f.splashX[i] += (rest.splashX[i] - f.splashX[i]) * blend }
      for i in f.splashY.indices { f.splashY[i] += (rest.splashY[i] - f.splashY[i]) * blend }
      for i in f.splashR.indices { f.splashR[i] += (rest.splashR[i] - f.splashR[i]) * blend }
      f.beam = f.beam * (1 - blend) + rest.beam * blend
      f.beamReach = f.beamReach * (1 - blend) + rest.beamReach * blend
      f.ringX = f.ringX * (1 - blend) + rest.ringX * blend
      f.ringY = f.ringY * (1 - blend) + rest.ringY * blend
      f.ringR = f.ringR * (1 - blend) + rest.ringR * blend
      f.ringA = f.ringA * (1 - blend) + rest.ringA * blend
      f.flash = f.flash * (1 - blend) + rest.flash * blend
      f.rimGlint = f.rimGlint * (1 - blend) + rest.rimGlint * blend
      f.tiltX = f.tiltX * (1 - blend) + rest.tiltX * blend
      f.tiltY = f.tiltY * (1 - blend) + rest.tiltY * blend
      f.cardScale = f.cardScale * (1 - blend) + rest.cardScale * blend
      f.value = f.value * (1 - blend) + rest.value * blend
      f.numberAlpha = f.numberAlpha * (1 - blend) + rest.numberAlpha * blend
      f.numberScale = f.numberScale * (1 - blend) + rest.numberScale * blend
      f.numberGlow = f.numberGlow * (1 - blend) + rest.numberGlow * blend
      f.period = f.period * (1 - blend) + rest.period * blend
      f.periodPrev = f.periodPrev * (1 - blend) + rest.periodPrev * blend
      f.periodMix = f.periodMix * (1 - blend) + rest.periodMix * blend
      f.status = f.status * (1 - blend) + rest.status * blend
      f.statusPrev = f.statusPrev * (1 - blend) + rest.statusPrev * blend
      f.statusMix = f.statusMix * (1 - blend) + rest.statusMix * blend
      f.spin = (f.spin - TAU * round(f.spin / TAU)) * (1 - blend) + rest.spin * blend
      f.pillPress = f.pillPress * (1 - blend) + rest.pillPress * blend
      f.refreshPress = f.refreshPress * (1 - blend) + rest.refreshPress * blend
      f.chevron = (f.chevron - TAU * round(f.chevron / TAU)) * (1 - blend) + rest.chevron * blend
      f.touchX = f.touchX * (1 - blend) + rest.touchX * blend
      f.touchY = f.touchY * (1 - blend) + rest.touchY * blend
      f.touchA = f.touchA * (1 - blend) + rest.touchA * blend
      if s.closing >= 1 {
        f.t = 0
        f.presented = 1
      }
    }
    return f
  }
  static func scriptedU(_ t: Double) -> Double {

    let n = PERIODS[Int(0.0)].nodes
    if t < 4.75 {
      return n[Int(2.0)]
    }
    if t < 5.55 {
      return n[Int(2.0)] + (n[Int(1.0)] - n[Int(2.0)]) * easeMove((t - 4.75) / 0.8)
    }
    if t < 5.7 {
      return n[Int(1.0)]
    }
    if t < 6.75 {
      return n[Int(1.0)] + (n[Int(3.0)] - n[Int(1.0)]) * easeMove((t - 5.7) / 1.05)
    }
    if t < 7.35 {
      return n[Int(3.0)] + (n[Int(2.0)] - n[Int(3.0)]) * easeMove((t - 6.75) / 0.6)
    }
    return n[Int(2.0)]
  }
  static func stepDemo(_ s: GlassPeakChartState, _ dt: Double) {

    var remaining = max(0.0, min(0.05, dt))
    if s.reduced || !s.autoPlay {
      step(s, remaining)
      return
    }
    if s.paused {
      step(s, remaining)
      if (s.restWait >= 3.5 || s.returning) && isRest(s) && !s.contact {
        returnHome(s)
        if isRest(s) && s.f.period == 0.0
          && abs(s.orbU - PERIODS[Int(0.0)].nodes[Int(2.0)]) < 0.00001
        {
          resetLoop(s)
        }
      }
      return
    }
    while remaining > 1e-9 {
      if s.demoEvent < Double(DEMO_ACTIONS.count)
        && s.loop >= DEMO_ACTIONS[Int(s.demoEvent)].at - 1e-9
      {
        dispatch(s, s.demoEvent)
        s.demoEvent += 1
      }
      let next =
        (s.demoEvent < Double(DEMO_ACTIONS.count) ? DEMO_ACTIONS[Int(s.demoEvent)].at : LOOP)
      let h = min(FIXED_DT, remaining, max(1e-9, next - s.loop), LOOP - s.loop)
      let nextClock = s.loop + h
      if s.finger && !s.fingerUser {
        let u = scriptedU(nextClock)
        let velocity = (xOf(u) - xOf(scriptedU(s.loop))) / max(h, 1e-9)
        fingerMove(s, xOf(u), yOf(heightAt(s.f.ys, u)), velocity)
      }
      step(s, h)
      s.loop = nextClock
      if s.loop >= 12.5 {
        closeLoop(s, min(1.0, (s.loop - 12.5) / 0.5))
      }
      remaining -= h
      if s.loop >= LOOP - 1e-9 {
        s.cycles += 1
        resetLoop(s)
      }
    }
    if s.demoEvent < Double(DEMO_ACTIONS.count)
      && s.loop >= DEMO_ACTIONS[Int(s.demoEvent)].at - 1e-9
    {
      dispatch(s, s.demoEvent)
      s.demoEvent += 1
    }
  }
  static let DEMO_ACTIONS: [(at: Double, action: String)] = [
    (0.7, "refresh-in"), (0.82, "refresh-out"), (4.6, "finger-down"), (7.45, "finger-up"),
    (8, "period-in"), (8.12, "period-out"), (9.4, "period-in"), (9.52, "period-out"),
    (10.8, "period-in"), (10.92, "period-out"),
  ]
  static func dispatch(_ s: GlassPeakChartState, _ index: Double) {
    let action = DEMO_ACTIONS[Int(index)].action
    if action == "refresh-in" {
      press(s, "refresh", true, false)
    } else if action == "refresh-out" {
      press(s, "refresh", false, false)
      request(s, "refresh", false)
    } else if action == "period-in" {
      press(s, "period", true, false)
    } else if action == "period-out" {
      press(s, "period", false, false)
      request(s, "period", false)
    } else if action == "finger-down" {
      let u = PERIODS[0].nodes[2]
      fingerDown(s, xOf(u), yOf(heightAt(s.f.ys, u)), false)
    } else {
      fingerUp(s, 0, false)
    }
  }
}
