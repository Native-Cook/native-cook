import Foundation

/// One deterministic clock serves gestures, accessibility and the fifteen-second performance.
struct CrystalTrophyRevealMotion {
  enum Phase { case closed, revealing, open, closing }
  var phase: Phase = .closed
  var clock = 0.0
  var phaseTime = 0.0
  var demoTime = 0.0
  var paused = false
  var recovering = false
  var reduced = false
  var auto = false
  var started = false
  var realDown = false
  var lastInput = 0.0
  var open = 0.0
  var hem = 0.0
  var hemRaw = 0.0
  var hemV = 0.0
  var shiver = 0.0
  var seam = 0.37
  var lamp = 0.0
  var beam = 0.0
  var under = 0.0
  var rise = 0.0
  var yaw = 0.0
  var yawV = 0.0
  var restTarget = 0.0
  var etch = 0.0
  var touch = 0.0
  var touchFrom = 0.0
  var touchX = 0.0
  var touchY = 1.8
  var flash = 0.0
  var flashAge = 100.0
  var down = false
  var downAt = 0.0
  var dragBase = 0.0
  var maxDrag = 0.0
  var crossed = false
  var spin = false
  var spinTarget = 0.0
  var spinStart = 0.0
  var caught = false
  var frontReported = false
  var releaseAge = 100.0
  var releaseYaw = 0.0
  var startOpen = 0.0
  var startRise = 0.0
  var startLamp = 0.0
  var startYaw = 0.0
  var startUi = 0.0
  var titleTime = -1.0
  var ui = 0.0
  var veil = 0.0
  var hint = 0.0
  var cameraScale = 1.0
  var cameraY = 0.0
  var cameraV = 0.0
  var seated = false
  var startCamera = 1.0
  var shareX = 0.5
  var shareY = 0.5
  var closeX = 0.5
  var closeY = 0.5
  var shareDown = false
  var closeDown = false
  var sharePressAge = 100.0
  var closePressAge = 100.0
  var sharePress = 0.0
  var sharePressV = 0.0
  var closePress = 0.0
  var closePressV = 0.0
  var shareAge = 100.0
  var copied = 0.0
  var check = 0.0
  var sheen = -1.0
  var events = 0
  static let tau = Double.pi * 2

  init(auto: Bool = false, reduced: Bool = false) {
    self.auto = auto
    self.reduced = reduced
    if auto {
      phase = .open
      started = true
      open = 1
      hem = 1
      hemRaw = 1
      lamp = 1
      beam = 1
      rise = 1
      etch = 1
      titleTime = 10
      ui = 1
      seam = 0
    }
  }

  static func clamp(_ x: Double, _ low: Double = 0, _ high: Double = 1) -> Double {
    max(low, min(high, x))
  }

  static func bezier(_ x: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double
  {
    let p = clamp(x)
    if p == 0 || p == 1 { return p }
    var low = 0.0
    var high = 1.0
    var t = p
    for _ in 0..<14 {
      t = (low + high) * 0.5
      let u = 1 - t
      let bx = 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t
      if bx < p { low = t } else { high = t }
    }
    return 3 * (1 - t) * (1 - t) * t * y1 + 3 * (1 - t) * t * t * y2 + t * t * t
  }

  static func enter(_ x: Double) -> Double { bezier(x, 0.23, 1, 0.32, 1) }
  static func travel(_ x: Double) -> Double { bezier(x, 0.77, 0, 0.175, 1) }

  static func spring(
    _ x: Double, _ v: Double, _ target: Double, _ stiffness: Double, _ damping: Double, _ dt: Double
  ) -> (x: Double, v: Double) {
    let a = damping * 0.5
    let w = sqrt(max(0.0001, stiffness - a * a))
    let decay = exp(-a * dt)
    let c = cos(w * dt)
    let sn = sin(w * dt)
    let y = x - target
    let b = (v + a * y) / w
    return (target + decay * (y * c + b * sn), decay * (v * c - (a * b + w * y) * sn))
  }

  static func pulse(_ age: Double, _ tail: Double = 0.52) -> Double {
    if age < 0 || age > 0.06 + tail { return 0 }
    return age < 0.06 ? enter(age / 0.06) : 1 - enter((age - 0.06) / tail)
  }

  mutating func interrupt(_ isDown: Bool) {
    paused = auto
    recovering = false
    realDown = isDown
    lastInput = clock
    if isDown {
      touchFrom = touch
      releaseAge = 0
      down = false
      shareDown = false
      closeDown = false
      spin = false
    }
  }

  mutating func reveal() {
    guard phase == .closed else { return }
    phase = .revealing
    phaseTime = 0
    startOpen = open
    started = true
    seated = false
    cameraY = 0
    cameraV = 0
    titleTime = -1
    ui = 0
    hint = 0
    events |= 1
  }

  mutating func close() {
    guard phase == .open || phase == .revealing else { return }
    startCamera = cameraScale
    phase = .closing
    phaseTime = 0
    startOpen = open
    startRise = rise
    startLamp = lamp
    startYaw = atan2(sin(yaw), cos(yaw))
    startUi = ui
    down = false
    spin = false
    yawV = 0
    shareDown = false
  }

  mutating func touchDown(_ x: Double, _ y: Double) {
    guard phase == .open || phase == .closed else { return }
    touchFrom = touch
    down = true
    downAt = clock
    dragBase = yaw
    restTarget = floor(yaw / Self.tau + 0.5) * Self.tau
    maxDrag = 0
    crossed = abs(yaw - restTarget) > 0.52
    spin = false
    yawV = 0
    touchX = Self.clamp(x, -0.86, 0.86)
    touchY = Self.clamp(y, 0, 2.9)
  }

  mutating func dragTo(_ dx: Double, _ dy: Double, _ x: Double, _ y: Double, _ width: Double) {
    guard down else { return }
    maxDrag = max(maxDrag, hypot(dx, dy))
    touchX = Self.clamp(x, -0.86, 0.86)
    touchY = Self.clamp(y, 0, 2.9)
    if phase == .closed {
      if reduced { return }
      let pull = abs(dx) / (width * 0.5)
      open = pull < 0.22 ? pull : min(0.32, 0.22 + (pull - 0.22) / (1 + (pull - 0.22) * 10))
    } else if phase == .open {
      let base = dragBase - restTarget
      let raw = base + dx * 0.0062
      let extra = max(0, abs(raw) - 0.52)
      let baseExtra = max(0, abs(base) - 0.52)
      let baseBand =
        (base < 0 ? -1.0 : base > 0 ? 1.0 : 0)
        * (min(abs(base), 0.52) + baseExtra / (1 + baseExtra * 3.2))
      let band =
        (raw < 0 ? -1.0 : raw > 0 ? 1.0 : 0) * (min(abs(raw), 0.52) + extra / (1 + extra * 3.2))
      yaw = dragBase + band - baseBand
      if extra > 0 && !crossed {
        crossed = true
        events |= 16
      }
    }
  }

  mutating func tapAt(_ x: Double, _ y: Double) {
    guard phase == .open else { return }
    touchX = Self.clamp(x, -0.86, 0.86)
    touchY = Self.clamp(y, 0, 2.9)
    flashAge = 0
    yawV += reduced ? 0 : (x < 0 ? -0.6 : 0.6)
    events |= 4
  }

  mutating func release(_ velocity: Double, cancelled: Bool = false) {
    guard down else { return }
    touchFrom = touch
    down = false
    lastInput = clock
    releaseAge = 0
    releaseYaw = yaw
    if phase == .closed {
      if !cancelled && (maxDrag < 8 || open > 0.12 || abs(velocity) > 600) { reveal() }
      return
    }
    guard phase == .open else { return }
    if !cancelled && clock - downAt <= 0.25 && maxDrag < 8 {
      tapAt(touchX, touchY)
      return
    }
    yawV = cancelled ? 0 : velocity * 0.0062
    if !reduced && !cancelled && abs(velocity) > 900 {
      let sign = velocity < 0 ? -1.0 : 1.0
      spin = true
      caught = false
      frontReported = false
      spinStart = yaw
      spinTarget = (floor(yaw / Self.tau + 0.5) + sign) * Self.tau
      yawV = sign * max(10.8, min(16, abs(yawV)))
    }
  }

  mutating func pressShare(
    _ pressed: Bool, activate: Bool = false, x: Double = 0.5, y: Double = 0.5
  ) {
    if pressed && !shareDown { sharePressAge = 0 }
    if pressed {
      shareX = Self.clamp(x)
      shareY = Self.clamp(y)
    }
    shareDown = pressed && phase == .open
    if activate && phase == .open {
      shareAge = 0
      events |= 4 | 64
    }
  }

  mutating func pressClose(
    _ pressed: Bool, activate: Bool = false, x: Double = 0.5, y: Double = 0.5
  ) {
    if pressed && !closeDown { closePressAge = 0 }
    if pressed {
      closeX = Self.clamp(x)
      closeY = Self.clamp(y)
    }
    closeDown = pressed
    if activate { close() }
  }

  func uiProgress(_ start: Double, _ duration: Double = 0.32) -> Double {
    reduced ? ui : Self.enter((titleTime - start) / duration) * ui
  }

  mutating func step(_ dt: Double) {
    let previous = phaseTime
    let previousRise = rise
    clock += dt
    phaseTime += dt
    flashAge += dt
    releaseAge += dt
    shareAge += dt
    let t = phaseTime
    flash = reduced ? (flashAge < 0.3 ? sin(.pi * flashAge / 0.3) * 0.35 : 0) : Self.pulse(flashAge)
    touch =
      down && phase == .open
      ? touchFrom + (1 - touchFrom) * Self.enter((clock - downAt) / 0.12)
      : touchFrom * (1 - Self.enter(releaseAge / 0.22))
    sharePressAge += dt
    closePressAge += dt
    let sp = Self.spring(sharePress, sharePressV, shareDown ? 1 : 0, 500, 32, dt)
    sharePress = shareDown ? Self.enter(sharePressAge / 0.12) : sp.x
    sharePressV = shareDown ? 0 : sp.v
    let cp = Self.spring(closePress, closePressV, closeDown ? 1 : 0, 500, 32, dt)
    closePress = closeDown ? Self.enter(closePressAge / 0.12) : cp.x
    closePressV = closeDown ? 0 : cp.v
    copied = Self.enter(shareAge / 0.22) * (1 - Self.enter((shareAge - 1.43) / 0.22))
    check = Self.clamp(shareAge / 0.22) * copied
    sheen = shareAge < 0.65 ? shareAge / 0.65 : -1
    if shareAge > 2 {
      copied = 0
      check = 0
    }
    if reduced && (phase == .revealing || phase == .closing) {
      let opening = phase == .revealing
      veil = t < 0.18 ? Self.enter(t / 0.18) : 1 - Self.enter((t - 0.18) / 0.32)
      let shown = t < 0.18 ? !opening : opening
      open = shown ? 1 : 0
      hem = open
      hemRaw = open
      rise = open
      etch = open
      lamp = open
      beam = open
      under = 0
      shiver = 0
      ui = opening ? Self.enter((t - 0.30) / 0.32) : 1 - Self.enter(t / 0.18)
      titleTime = opening ? 10 : -1
      yaw = 0
      if t >= 0.62 {
        phase = opening ? .open : .closed
        phaseTime = 0
        veil = 0
        if !opening { events |= 1 | 32 }
      }
    } else if phase == .revealing {
      open = startOpen + (1 - startOpen) * Self.travel((t - 0.3) / 0.7)
      shiver = Self.clamp(t / 0.32)
      seam = (0.37 + 0.63 * Self.enter(t / 0.32)) * (1 - Self.clamp((t - 0.32) / 0.68))
      let l = t - 0.86
      lamp =
        l < 0
        ? 0
        : l < 0.06
          ? 0.55 * l / 0.06
          : l < 0.15 ? 0.55 - 0.43 * (l - 0.06) / 0.09 : 0.12 + 0.88 * Self.enter((l - 0.15) / 0.24)
      beam = Self.travel((t - 0.92) / 0.66)
      under = Self.enter((t - 1.44) / 0.2) * (1 - Self.enter((t - 2) / 0.6))
      let rising = Self.spring(0, 0, 1, 110, 11.5, max(0, t - 1.56)).x
      rise = rising <= 1 ? rising : 1 + 0.065 * tanh((rising - 1) / 0.065)
      if previous < 1.56 && t >= 1.56 { events |= 2 }
      if previous < 2.3 && t >= 2.3 {
        flashAge = 0
        touchX = 0.05
        touchY = 2.85
        events |= 4
      }
      lamp += 0.2 * flash
      etch = Self.bezier((t - 2.36) / 1.15, 0.4, 0, 0.2, 1)
      if previous < 3.51 && t >= 3.51 {
        flashAge = 0
        touchX = 0.02
        touchY = 2
        events |= 8
      }
      titleTime = t
      ui = 1
      if t >= 4 {
        phase = .open
        phaseTime = 0
        rise = 1
        hem = 1
        hemRaw = 1
        hemV = 0
        shiver = 0
      }
    } else if phase == .closing {
      ui = startUi * (1 - Self.enter(t / 0.2))
      lamp = startLamp * (1 - Self.enter(t / 0.32))
      rise = startRise * (1 - Self.travel((t - 0.08) / 0.48))
      if rise < 0.001 { etch = 0 }
      yaw = startYaw * (1 - Self.enter(t / 0.3))
      open = startOpen * (1 - Self.travel((t - 0.42) / 0.62))
      under = 0
      flash = 0
      seam = 0.37 * Self.enter((t - 1.02) / 0.3)
      if previous < 1.04 && t >= 1.04 { events |= 1 }
      if t >= 1.32 {
        phase = .closed
        phaseTime = 0
        events |= 32
        titleTime = -1
      }
    } else if phase == .closed {
      if !down {
        let rail = Self.spring(open, yawV, 0, 180, 22, dt)
        open = rail.x
        yawV = rail.v
      }
      let seamClock = clock.truncatingRemainder(dividingBy: 3.2)
      let demoSwell = demoTime >= 2.1 && demoTime < 2.8 ? sin(.pi * (demoTime - 2.1) / 0.7) : 0
      seam =
        0.37
        + (reduced
          ? 0 : auto && !paused ? 0.45 * demoSwell : 0.09 * sin(Self.tau * seamClock / 3.2))
        + max(0, open)
      let hintTime = (auto && !paused ? demoTime : clock).truncatingRemainder(dividingBy: 3.2)
      hint =
        auto
        ? 0
        : Self.enter((t - 1.4) / 0.4) * (reduced ? 1 : 0.8 + 0.2 * sin(Self.tau * hintTime / 3.2))
      if !started && t >= 0.5 && !down { reveal() }
    } else {
      titleTime = min(10, titleTime + dt)
      ui = 1
      lamp = 1 + 0.22 * flash
      shiver = 0
      if !down {
        if reduced {
          yaw = releaseYaw * (1 - Self.enter(releaseAge / 0.24))
          yawV = 0
        } else if spin && !caught {
          let old = yaw
          yaw += yawV * (1 - exp(-1.45 * dt)) / 1.45
          yawV *= exp(-1.45 * dt)
          if abs(yaw - spinStart) >= .pi || abs(yawV) < 2 { caught = true }
          if (old - spinTarget) * (yaw - spinTarget) <= 0 { caught = true }
        } else {
          let idleAge = max(0, clock - lastInput - 1.2)
          let target =
            spin
            ? spinTarget
            : restTarget
              + (auto
                ? 0
                : 0.07 * sin(Self.tau * clock.truncatingRemainder(dividingBy: 7) / 7)
                  * Self.enter(idleAge / 0.8))
          let turn = Self.spring(yaw, yawV, target, 120, 14, dt)
          if spin && !frontReported && (yaw - target) * (turn.x - target) <= 0 {
            events |= 2
            frontReported = true
          }
          yaw = turn.x
          yawV = turn.v
          if spin && abs(yaw - target) < 0.002 && abs(yawV) < 0.02 {
            yaw = 0
            yawV = 0
            restTarget = 0
            spin = false
            caught = false
          } else if !spin && restTarget != 0 && abs(yaw - restTarget) < 0.002 && abs(yawV) < 0.02 {
            yaw -= restTarget
            restTarget = 0
          } else if !spin && auto && abs(yaw) < 0.0001 && abs(yawV) < 0.001 {
            yaw = 0
            yawV = 0
          }
        }
      }
    }
    if reduced {
      cameraScale = 1
      cameraY = 0
      cameraV = 0
    } else {
      let push =
        phase == .revealing
        ? (t < 0.3 ? Self.enter(t / 0.3) : 1 - Self.enter((t - 0.3) / 3.7)) * 0.07
        : phase == .closing ? (startCamera - 1) * (1 - Self.enter(t / 0.32)) : 0
      cameraScale = (1 + push) * (1 + 0.012 * flash)
      if phase == .revealing && !seated && previousRise < 1 && rise >= 1 {
        seated = true
        cameraY = 2
        cameraV = 0
      } else {
        let seat = Self.spring(cameraY, cameraV, 0, 420, 26, dt)
        cameraY = seat.x
        cameraV = seat.v
        if abs(cameraY) < 0.0001 && abs(cameraV) < 0.001 {
          cameraY = 0
          cameraV = 0
        }
      }
      let result = Self.spring(hemRaw, hemV, open, 70, 9, dt)
      hemRaw = result.x
      hemV = result.v
      hem =
        result.x < 0
        ? 0.06 * tanh(result.x / 0.06)
        : result.x > 1 ? 1 + 0.06 * tanh((result.x - 1) / 0.06) : result.x
    }
  }

  mutating func advance(_ elapsed: Double, width: Double) {
    var remaining = min(max(elapsed, 0), 1.0 / 30)
    let beats = [0.7, 2.1, 2.8, 7.4, 8.1, 9.3, 9.6, 12, 12.8, 12.95, 15]
    for _ in 0..<8 {
      if remaining < 1e-9 { break }
      var dt = min(remaining, 1.0 / 120)
      let scripted = auto && !reduced && !paused
      var beat = -1.0
      if scripted {
        for event in beats {
          let gap = event - demoTime
          if gap > 1e-8 && gap <= dt + 1e-8 {
            dt = gap
            beat = event
            break
          }
        }
      }
      step(dt)
      remaining -= dt
      if scripted {
        demoTime += dt
        if demoTime >= 7.4 && demoTime < 9.6 {
          let angle =
            demoTime < 8.1
            ? 0.52 * Self.travel((demoTime - 7.4) / 0.7)
            : demoTime < 9.3
              ? 0.52 - 1.04 * Self.travel((demoTime - 8.1) / 1)
              : -0.52 + 1.04 * Self.travel((demoTime - 9.3) / 0.3)
          dragTo(angle / 0.0062, 0, 0.20 + angle * 0.55, 2.05 + angle * 0.19, width)
        }
        if beat == 0.7 { close() }
        if beat == 2.8 { reveal() }
        if beat == 7.4 { touchDown(0.20, 2.05) }
        if beat == 9.6 { release(1500) }
        if beat == 12 { tapAt(-0.48, 2.15) }
        if beat == 12.8 { pressShare(true) }
        if beat == 12.95 { pressShare(false, activate: true) }
        if demoTime >= 15 - 1e-8 { restart() }
      }
      if auto && paused && !realDown && clock - lastInput >= 3.5 {
        if !recovering {
          recovering = true
          down = false
          spin = false
          releaseAge = 0
          releaseYaw = yaw
          if phase == .closed { reveal() }
        }
        if phase == .closed { reveal() }
        if phase == .open && abs(yaw) < 0.002 && flash == 0 && copied == 0 && touch == 0 {
          restart()
        }
      }
    }
  }

  private mutating func restart() {
    let savedClock = clock
    let savedEvents = events
    self = Self(auto: true, reduced: reduced)
    clock = savedClock
    lastInput = savedClock
    events = savedEvents
  }
}
