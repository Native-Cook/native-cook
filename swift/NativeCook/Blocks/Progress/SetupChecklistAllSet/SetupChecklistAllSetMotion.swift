import Foundation

/// Flags retain their change times so takeover preserves the exact in-flight frame.
enum SetupChecklistAllSetMotion {
  static let LOOP = 9.0
  static let RESUME = 2.0
  static let N = 6
  static let NEVER = -100.0
  enum D {
    static let tick = 0.42, morph = 0.55, bloom = 0.6, leave = 0.5, enter = 0.7
  }
  struct State {
    var done = [false, false, false, false, false, true]
    var doneAt = Array(repeating: NEVER, count: N)
    var open = [true, false, false, false, false, false]
    var openAt = Array(repeating: NEVER, count: N)
    var pressAt = NEVER
    var pressRow = -1
    var allSetAt = NEVER
    var leaveAt = NEVER
    var backAt = NEVER
    var reset = false
    var ctaAt = NEVER
    var laterAt = NEVER
  }
  struct Live {
    var now: Double
    var amb: Double
    var s: State
  }
  static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
  static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
  static func mix(_ a: Double, _ b: Double, _ p: Double) -> Double { a + (b - a) * p }
  static func easeOut(_ x: Double) -> Double { 1 - pow(1 - clamp01(x), 3) }
  static func easeInOut(_ x: Double) -> Double {
    let p = clamp01(x)
    return p < 0.5 ? 4 * p * p * p : 1 - pow(-2 * p + 2, 3) / 2
  }
  static func smooth(_ x: Double) -> Double {
    let p = clamp01(x)
    return p * p * (3 - 2 * p)
  }
  static func spring(_ tau: Double, _ response: Double, _ damping: Double) -> Double {
    if tau <= 0 { return 0 }
    let w = 2 * Double.pi / response
    let zw = damping * w
    let wd = w * sqrt(1 - damping * damping)
    return 1 - exp(-zw * tau) * (cos(wd * tau) + (zw / wd) * sin(wd * tau))
  }
  static func loopState(_ t: Double) -> State {
    var s = State()
    if t >= 7.9 {
      s.backAt = 7.9
      s.leaveAt = 7.2
      return s
    }
    let done = [0.7, 1.45, 2.15, 2.85, 3.55]
    let press = [0.52, 1.27, 1.97, 2.67, 3.37]
    for i in 0..<5 {
      let dn = done[i]
      if t >= dn {
        s.done[i] = true
        s.doneAt[i] = dn
      }
      if t >= press[i] {
        s.pressAt = press[i]
        s.pressRow = i
      }
      let openT = i == 0 ? NEVER : done[i - 1] + 0.24
      let closeT = dn + 0.16
      if t >= closeT {
        s.open[i] = false
        s.openAt[i] = closeT
      } else if t >= openT {
        s.open[i] = true
        s.openAt[i] = openT
      } else {
        s.open[i] = false
        s.openAt[i] = NEVER
      }
    }
    if t >= 3.95 { s.allSetAt = 3.95 }
    if t >= 7 { s.laterAt = 7 }
    if t >= 7.2 {
      s.leaveAt = 7.2
      s.backAt = 7.9
      s.reset = true
    }
    return s
  }
  static func openness(_ s: State, _ i: Int, _ now: Double) -> Double {
    let k =
      s.openAt[i] <= NEVER
      ? 1
      : s.open[i]
        ? spring(now - s.openAt[i], 0.42, 0.86) : spring(now - s.openAt[i], 0.36, 0.92)
    return s.open[i] ? k : 1 - k
  }
  static func doneness(_ s: State, _ i: Int, _ now: Double) -> Double {
    if s.doneAt[i] <= NEVER { return s.done[i] ? 1 : 0 }
    return s.done[i]
      ? clamp01((now - s.doneAt[i]) / D.tick) : 1 - clamp01((now - s.doneAt[i]) / 0.22)
  }
  static func countRolled(_ s: State, _ now: Double) -> Double {
    var v = 0.0
    for i in 0..<N {
      let k = s.doneAt[i] <= NEVER ? 1 : smooth((now - s.doneAt[i] - 0.08) / 0.3)
      v += s.done[i] ? k : 1 - k
    }
    return v
  }
  struct Driver {
    var t = 0.0
    var mode = 0
    var last = -10.0
    var amb = 0.0
    var s = State()
    mutating func step(_ dt: Double, _ now: Double, _ auto: Bool) {
      amb = (amb + dt).truncatingRemainder(dividingBy: LOOP)
      if mode == 0 {
        if auto { t = (t + dt).truncatingRemainder(dividingBy: LOOP) }
        return
      }
      if s.reset && s.backAt > NEVER && now >= s.backAt {
        var fresh = State()
        fresh.leaveAt = s.leaveAt
        fresh.backAt = s.backAt
        s = fresh
      }
      if !auto || now - last < RESUME { return }
      if s.leaveAt > NEVER && now < s.backAt + D.enter { return }
      let fresh =
        s.allSetAt <= NEVER
        && (0..<N).allSatisfy { s.done[$0] == ($0 == 5) && s.open[$0] == ($0 == 0) }
      if fresh && openness(s, 0, now) > 0.999 {
        mode = 0
        t = 0
      } else if now - last >= RESUME + 2 {
        s.leaveAt = now
        s.backAt = now + D.leave + 0.2
        s.reset = true
      }
    }
    mutating func takeOver(_ now: Double) {
      last = now
      if mode == 2 { return }
      var state = loopState(t)
      let k = now - t
      func shift(_ x: Double) -> Double { x <= NEVER ? x : x + k }
      for i in 0..<N {
        state.doneAt[i] = shift(state.doneAt[i])
        state.openAt[i] = shift(state.openAt[i])
      }
      state.pressAt = shift(state.pressAt)
      state.allSetAt = shift(state.allSetAt)
      state.laterAt = shift(state.laterAt)
      state.leaveAt = shift(state.leaveAt)
      state.backAt = shift(state.backAt)
      if state.backAt > NEVER && now >= state.backAt { state.reset = false }
      if state.allSetAt <= NEVER && state.leaveAt <= NEVER {
        var next = -1
        var anyOpen = false
        for i in 0..<N {
          if state.open[i] && state.done[i] {
            state.open[i] = false
            state.openAt[i] = max(now, state.doneAt[i] + 0.16)
          }
          if state.open[i] { anyOpen = true }
          if next < 0 && !state.done[i] { next = i }
        }
        if next < 0 {
          state.allSetAt = now + 0.3
        } else if !anyOpen {
          state.open[next] = true
          state.openAt[next] = now + 0.08
        }
      }
      s = state
      mode = 2
    }
    mutating func toggle(_ i: Int, _ now: Double) -> String {
      if s.done[i] {
        s.done[i] = false
        s.doneAt[i] = now
        return "uncheck"
      }
      s.done[i] = true
      s.doneAt[i] = now
      s.pressAt = now
      s.pressRow = i
      if s.open[i] {
        s.open[i] = false
        s.openAt[i] = now + 0.16
      }
      var next = -1
      for k in 1...N {
        let j = (i + k) % N
        if !s.done[j] {
          next = j
          break
        }
      }
      if next < 0 {
        s.allSetAt = now + 0.4
        return "complete"
      }
      if !s.open[next] {
        s.open[next] = true
        s.openAt[next] = now + 0.24
      }
      return "check"
    }
    mutating func toggleRow(_ i: Int, _ now: Double) {
      if s.open[i] {
        s.open[i] = false
        s.openAt[i] = now
        return
      }
      for j in 0..<N where j != i && s.open[j] {
        s.open[j] = false
        s.openAt[j] = now
      }
      s.open[i] = true
      s.openAt[i] = now
    }
    mutating func skip(_ i: Int, _ now: Double) {
      s.open[i] = false
      s.openAt[i] = now
      for k in 1..<N {
        let j = (i + k) % N
        if !s.done[j] {
          s.open[j] = true
          s.openAt[j] = now + 0.12
          return
        }
      }
    }
    mutating func dismiss(_ now: Double, _ fresh: Bool) -> Bool {
      if s.leaveAt > NEVER && now < s.backAt + D.enter { return false }
      s.leaveAt = now
      s.backAt = now + D.leave + 0.35
      s.reset = fresh
      return true
    }
    func hit(_ x: Double, _ y: Double, _ now: Double) -> (Int, Int) {
      if s.leaveAt > NEVER && now < s.backAt + D.enter { return (0, -1) }
      if s.allSetAt > NEVER {
        if now - s.allSetAt < D.morph + D.bloom { return (0, -1) }
        if x > 16 && x < 386 && y > 683 && y < 746 { return (6, -1) }
        if abs(y - 775.5) < 24 && abs(x - 201) < 80 { return (7, -1) }
        return (0, -1)
      }
      if hypot(x - 371, y - 338) < 24 { return (5, -1) }
      var top = 396.5
      for i in 0..<N {
        let e = openness(s, i, now)
        let height = 54.3 + 115.5 * e
        if y >= top && y < top + height {
          if y < top + 54.3 { return (x < 46 ? 1 : 2, i) }
          if e > 0.6 && abs(y - (top + 54.3 / 2 + 100.5)) < 22 {
            let width = 26 + Double(SetupChecklistAllSetTheme.items[i].action.count) * 8.4
            if x > 49.4 - 6 && x < 49.4 + width + 6 { return (3, i) }
            if x > 49.4 + width + 10 && x < 49.4 + width + 90 { return (4, i) }
          }
          return (0, i)
        }
        top += height
      }
      return (0, -1)
    }
  }
}
