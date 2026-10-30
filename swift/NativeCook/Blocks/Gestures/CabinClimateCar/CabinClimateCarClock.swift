import SwiftUI

final class CabinClimateCarClock: ObservableObject {
  private(set) var driver = CabinClimateCarDriver()
  private let audio = CabinClimateCarAudio()
  private var previous: Date?
  private var now = 0.0
  private var startTemp = 70.0
  private var finger = false

  func resetDate() { previous = nil }
  func reset() {
    driver = CabinClimateCarDriver()
    now = 0
    previous = nil
    finger = false
  }
  func stop() {
    previous = nil
    audio.stop()
  }
  func sample(_ date: Date, auto: Bool, reduced: Bool, pinned: Double?)
    -> CabinClimateCarMotion.Frame
  {
    if let pinned {
      driver.mode = 0
      driver.t = pinned
      driver.amb = pinned
    } else {
      let dt = min(max(0, previous.map { date.timeIntervalSince($0) } ?? 0.016), 0.05)
      previous = date
      now += dt
      if driver.step(dt, now, auto && !reduced) { audio.play("tick", reduced) }
    }
    var f = driver.frame(now)
    if reduced {
      f.amb = 0
      f.tempAt = 10
      f.heat = f.temp
      f.leg = f.legOn ? 1 : 0
    }
    return f
  }
  func begin(_ point: CGPoint, _ width: CGFloat) {
    guard !finger else { return }
    finger = true
    driver.takeOver(now)
    driver.released = 0
    startTemp = driver.temp
    let x = point.x / (width / 720)
    let y = point.y / (width / 720)
    func inside(_ rect: CGRect) -> Bool {
      x > rect.minX && x < rect.maxX && y > rect.minY && y < rect.maxY
    }
    if inside(CabinClimateCarTheme.temp) {
      driver.pressing = abs(y - 670) < 70 ? 1 : abs(y - 978) < 70 ? 2 : 6
    } else if inside(CabinClimateCarTheme.legroom) {
      driver.pressing = 3
    } else if inside(CabinClimateCarTheme.fan) {
      driver.pressing = 4
    } else if abs(x - 360) < 85 && abs(y - 181) < 39 {
      driver.pressing = 5
    } else {
      driver.pressing = 0
    }
    driver.pressAt = now
    driver.repeatAt = now
  }
  func move(_ dy: CGFloat, _ width: CGFloat, _ reduced: Bool) {
    guard finger else { return }
    driver.last = now
    let delta = dy / (width / 720)
    if [1, 2, 6].contains(driver.pressing) && abs(delta) > 18 { driver.pressing = 6 }
    if driver.pressing == 6 {
      // JS Math.round chooses +infinity for ties, unlike Swift's default rounding.
      let next = floor(startTemp - delta / 36 + 0.5)
      if driver.setTemp(next, reduced ? now - 2 : now) { audio.play("tick", reduced) }
    }
  }
  func end(_ reduced: Bool) {
    guard finger else { return }
    finger = false
    driver.last = now
    let was = driver.pressing
    driver.pressing = 0
    driver.released = was
    driver.releaseAt = now
    if was == 1 || was == 2 {
      if now - driver.pressAt <= 0.45
        && driver.setTemp(driver.temp + (was == 1 ? 1 : -1), reduced ? now - 2 : now)
      {
        audio.play("tick", reduced)
      }
    } else if was == 3 {
      driver.toggleLeg(reduced ? now - 2 : now)
      audio.play("slide", reduced)
    } else if was == 4 {
      driver.fan = (driver.fan + 1) % 3
      driver.fanAt = now
      audio.play("air", reduced)
    } else if was == 5 {
      audio.play("tap", reduced)
    }
  }
  func warmer() {
    driver.takeOver(now)
    driver.setTemp(driver.temp + 1, now)
  }
}
