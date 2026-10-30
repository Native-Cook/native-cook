import Foundation

// Exact event replay and analytic spring from cpf-motion.ts; no platform animation approximations.
enum CashbackPickFiveMotion {
  static let LOOP = 9.0, RESUME = 2.0, FLY = 0.5, NEVER = -100.0, MAX_SCROLL = 420.0
  static let SPARKLE_AT = 5.0
  struct Event {
    var t: Double
    var k: Int
    var row: Int
    var v: Double = 0
  }
  static let loopEvents: [Event] = [
    .init(t: 0.6, k: 0, row: 0), .init(t: 1.25, k: 0, row: 1),
    .init(t: 1.85, k: 0, row: 3), .init(t: 2.45, k: 0, row: 2),
    .init(t: 3.05, k: 0, row: 4), .init(t: 3.55, k: 2, row: -1, v: 240),
    .init(t: 4.45, k: 0, row: 6), .init(t: 5.45, k: 2, row: -1),
    .init(t: 6.1, k: 1, row: 4), .init(t: 6.35, k: 1, row: 2),
    .init(t: 6.6, k: 1, row: 3), .init(t: 6.85, k: 1, row: 1),
    .init(t: 7.1, k: 1, row: 0),
  ]
  static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
  static func mix(_ a: Double, _ b: Double, _ p: Double) -> Double { a + (b - a) * p }
  static func smooth(_ x: Double) -> Double {
    let p = clamp01(x)
    return p * p * (3 - 2 * p)
  }
  static func easeOut(_ x: Double) -> Double { 1 - pow(1 - clamp01(x), 3) }
  static func easeInOut(_ x: Double) -> Double {
    let p = clamp01(x)
    return p < 0.5 ? 4 * p * p * p : 1 - pow(-2 * p + 2, 3) / 2
  }
  static func spring(_ tau: Double, _ response: Double, _ damping: Double) -> Double {
    if tau <= 0 { return 0 }
    let w = 2 * Double.pi / response
    let zw = damping * w
    let wd = w * sqrt(1 - damping * damping)
    return 1 - exp(-zw * tau) * (cos(wd * tau) + zw / wd * sin(wd * tau))
  }
  struct Flight {
    var row: Int
    var from: Int
    var to: Int
    var at: Double
    var dir: Int
  }
  struct State {
    var picks: [Int] = []
    var slotOf = Array(repeating: -1, count: 9)
    var slotAt = Array(repeating: NEVER, count: 9)
    var slotFrom = Array(repeating: -1, count: 9)
    var checkAt = Array(repeating: NEVER, count: 9)
    var checked = Array(repeating: false, count: 9)
    var flights: [Flight] = []
    var nopeAt = NEVER
    var nopeRow = -1
    var countAt = NEVER
    var countFrom = 0
    var panelAt = NEVER
    var panelUp = false
    var scrollFrom = 0.0
    var scrollTo = 0.0
    var scrollAt = NEVER
  }
  static func scrollOf(_ state: State, _ now: Double) -> Double {
    mix(state.scrollFrom, state.scrollTo, easeInOut((now - state.scrollAt) / 0.7))
  }
  static func replay(_ events: [Event], _ now: Double) -> State {
    var state = State()
    for event in events {
      if event.t > now { break }
      if event.k == 2 {
        state.scrollFrom = scrollOf(state, event.t)
        state.scrollTo = event.v
        state.scrollAt = event.t
        continue
      }
      let row = event.row
      if event.k == 0 {
        if state.slotOf[row] >= 0 { continue }
        if state.picks.count >= CashbackPickFiveTheme.MAX_PICKS {
          state.nopeAt = event.t
          state.nopeRow = row
          continue
        }
        let slot = state.picks.count
        state.picks.append(row)
        state.slotOf[row] = slot
        state.slotFrom[row] = slot
        state.slotAt[row] = NEVER
        state.checked[row] = true
        state.checkAt[row] = event.t
        state.flights.append(.init(row: row, from: -1, to: slot, at: event.t, dir: 1))
        state.countFrom = state.picks.count - 1
        state.countAt = event.t
        if state.picks.count == 1 {
          state.panelUp = true
          state.panelAt = event.t
        }
      } else {
        let slot = state.slotOf[row]
        if slot < 0 { continue }
        state.picks.remove(at: slot)
        state.slotOf[row] = -1
        state.checked[row] = false
        state.checkAt[row] = event.t
        state.flights.append(.init(row: row, from: slot, to: -1, at: event.t, dir: -1))
        for k in slot..<state.picks.count {
          let shiftedRow = state.picks[k]
          state.slotFrom[shiftedRow] = k + 1
          state.slotOf[shiftedRow] = k
          state.slotAt[shiftedRow] = event.t
        }
        state.countFrom = state.picks.count + 1
        state.countAt = event.t
        if state.picks.isEmpty {
          state.panelUp = false
          state.panelAt = event.t + FLY * 0.6
        }
      }
    }
    state.flights = state.flights.filter { now - $0.at < FLY + 0.35 }
    return state
  }
  struct Driver {
    var t = 0.0
    var mode = 0
    var last = -10.0
    var amb = 0.0
    var events: [Event] = []
    var dragging = 0
    var scroll = 0.0
    var v = 0.0
    var sparkleAt = NEVER
    var homing = 0
  }
  static func takeOver(_ driver: inout Driver, _ now: Double) {
    driver.last = now
    driver.homing = 0
    if driver.mode == 2 { return }
    driver.mode = 2
    let k = now - driver.t
    driver.events = loopEvents.filter { $0.t <= driver.t }.map {
      .init(t: $0.t + k, k: $0.k, row: $0.row, v: $0.v)
    }
    driver.scroll = scrollOf(replay(driver.events, now), now)
    driver.v = 0
    driver.sparkleAt =
      driver.t >= SPARKLE_AT && driver.t < SPARKLE_AT + 1.2 ? SPARKLE_AT + k : NEVER
  }
  static func push(_ driver: inout Driver, _ event: Event) {
    driver.events.append(event)
    if driver.events.count > 40 {
      let state = replay(driver.events, event.t - 3)
      let keep = driver.events.filter { $0.t > event.t - 3 }
      var base = state.picks.map { Event(t: event.t - 3.5, k: 0, row: $0) }
      base.append(.init(t: event.t - 3.5, k: 2, row: -1, v: scrollOf(state, event.t - 3)))
      driver.events = base + keep
    }
  }
  static func stepDriver(_ driver: inout Driver, _ dt: Double, _ now: Double, _ auto: Bool) {
    driver.amb = (driver.amb + dt).truncatingRemainder(dividingBy: LOOP)
    if driver.mode == 0 {
      if auto { driver.t = (driver.t + dt).truncatingRemainder(dividingBy: LOOP) }
      return
    }
    if driver.dragging == 0 && abs(driver.v) > 1 {
      driver.scroll = max(0, min(MAX_SCROLL, driver.scroll + driver.v * dt))
      driver.v *= exp(-dt * 4)
      if abs(driver.v) < 5 { driver.v = 0 }
    }
    if !auto || driver.dragging == 1 || now - driver.last < RESUME { return }
    let state = replay(driver.events, now)
    if driver.homing == 0 {
      driver.homing = 1
      for (k, row) in state.picks.reversed().enumerated() {
        push(&driver, .init(t: now + Double(k) * 0.25, k: 1, row: row))
      }
      if driver.scroll > 0.5 {
        push(&driver, .init(t: now - 0.75, k: 2, row: -1, v: driver.scroll))
        push(&driver, .init(t: now, k: 2, row: -1))
      }
      driver.events = driver.events.enumerated().sorted {
        $0.element.t == $1.element.t ? $0.offset < $1.offset : $0.element.t < $1.element.t
      }.map(\.element)
      driver.v = 0
      return
    }
    if state.picks.isEmpty && state.flights.isEmpty && now - state.panelAt > 0.6
      && now - state.scrollAt > 0.8
      && now - state.nopeAt > 0.6
    {
      driver.mode = 0
      driver.t = 0
      driver.events = []
      driver.scroll = 0
    }
  }
  struct Frame {
    var amb: Double
    var now: Double
    var state: State
    var scroll: Double
    var sparkle: Double
    var press = 0.0
  }
  static func frameAt(_ t: Double) -> Frame {
    let state = replay(loopEvents, t)
    return .init(
      amb: t, now: t, state: state, scroll: scrollOf(state, t),
      sparkle: t >= SPARKLE_AT ? t - SPARKLE_AT : -1)
  }
  static func frameOf(_ driver: inout Driver, _ now: Double) -> Frame {
    if driver.mode == 0 { return frameAt(driver.t) }
    let state = replay(driver.events, now)
    let scripted = now - state.scrollAt < 0.7 ? scrollOf(state, now) : driver.scroll
    if now - state.scrollAt < 0.7 { driver.scroll = scripted }
    return .init(
      amb: driver.amb, now: now, state: state, scroll: scripted,
      sparkle: now - driver.sparkleAt < 1.6 ? now - driver.sparkleAt : -1)
  }
}
