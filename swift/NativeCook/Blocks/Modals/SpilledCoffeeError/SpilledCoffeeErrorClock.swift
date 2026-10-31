import SwiftUI
import UIKit

// TimelineView samples this non-published driver; gestures never force per-frame view state writes.
final class SpilledCoffeeErrorClock: ObservableObject {
  typealias M = SpilledCoffeeErrorMotion
  typealias H = SpilledCoffeeErrorTheme
  var driver = M.Driver()
  let audio = SpilledCoffeeErrorAudio()
  private var date: Date?
  private var now = 0.0
  private var prevTheta = 90.0
  private var zone = 0
  private var startX = 0.0
  private var startTheta = 0.0
  func resetDate() { date = nil }
  func reset() {
    driver = M.Driver()
    date = nil
    now = 0
    prevTheta = 90
    zone = 0
  }
  func sample(_ at: Date, auto: Bool, reduced: Bool, pinned: Double?) -> M.Pose {
    audio.enabled = !reduced
    if let pinned { return M.poseAt(pinned) }
    let dt = min(max(0, date.map { at.timeIntervalSince($0) } ?? 0.016), 0.05)
    date = at
    now += dt
    let before = driver.t
    M.stepDriver(&driver, dt, now, auto && !reduced)
    // The site's recording plays the sounds a touch would make (DemoSound): tap and rewind at
    // the press, then the script playScript() schedules for a real retry.
    if DemoSound.inAutoPlay && auto && !reduced && driver.mode == 0 && driver.t > before {
      typealias T = M.T
      let t = driver.t
      if before < T.press && t >= T.press {
        audio.play("tap")
        audio.play("rewind")
      }
      if before < T.rise + 0.25 && t >= T.rise + 0.25 { audio.play("clack") }
      if before < T.land - 0.05 && t >= T.land - 0.05 { audio.play("tip") }
      if before < T.land + 0.1 && t >= T.land + 0.1 { audio.play("splash") }
    }
    var pose = M.poseOf(driver, now)
    if reduced {
      pose.rewind = 0
      pose.steam = 0
    }
    if driver.mode == 2 && driver.retryAt < 0 {
      if prevTheta < 86 && pose.theta >= 86 {
        audio.play("tip")
        audio.buzz(1)
      } else if prevTheta > 3 && pose.theta <= 3 && driver.dragging == 0 {
        audio.play("clack")
        audio.buzz(0)
      }
    }
    prevTheta = pose.theta
    return pose
  }
  private func onCup(_ point: CGPoint, _ theta: Double) -> Bool {
    let t = theta * Double.pi / 180
    let fx = -cos(H.yaw)
    // This is the reference hit segment (including its intentionally simplified projection).
    let b = CGPoint(x: H.base.x + H.rb * (1 - cos(t)) * fx, y: H.base.y - H.rb * sin(t))
    let m = CGPoint(x: b.x + sin(t) * fx * H.L, y: b.y - cos(t) * H.L * cos(H.elev))
    let vx = m.x - b.x
    let vy = m.y - b.y
    let u = max(0, min(1, ((point.x - b.x) * vx + (point.y - b.y) * vy) / (vx * vx + vy * vy)))
    return hypot(point.x - (b.x + vx * u), point.y - (b.y + vy * u)) < H.rm + 24
  }
  private func inButton(_ p: CGPoint, _ margin: Double) -> Bool {
    let b = H.button
    return p.x > b.minX - margin && p.x < b.maxX + margin && p.y > b.minY - margin
      && p.y < b.maxY + margin
  }
  func touch(_ phase: UIGestureRecognizer.State, _ point: CGPoint, size: CGSize, reduced: Bool) {
    audio.enabled = !reduced
    let scale = size.width / H.REF_W
    let p = CGPoint(x: point.x / scale / H.S, y: (point.y / scale - H.DY) / H.S)
    switch phase {
    case .began:
      M.takeOver(&driver, now)
      let pose = M.poseOf(driver, now)
      if inButton(p, 10) {
        zone = 1
        driver.pressing = 1
        driver.pressAt = now
      } else if onCup(p, pose.theta) {
        zone = 2
        M.grabCup(&driver, now)
        audio.cancelScript()
        startX = p.x
        startTheta = driver.theta
      } else if pose.lid > 0.95 && hypot(p.x - H.lid.x, (p.y - H.lid.y) * 2.5) < H.lidR + 20 {
        zone = 3
      } else {
        zone = 0
      }
    case .changed:
      driver.last = now
      if zone == 2 { driver.dragTheta = max(0, min(95, startTheta - (p.x - startX) * 0.5)) }
    case .ended, .cancelled, .failed:
      // RN onFinalize handles cancellations as releases too.
      driver.last = now
      let target = zone
      zone = 0
      if target == 2 {
        driver.dragging = 0
        return
      }
      if target == 1 {
        driver.pressing = 0
        if inButton(p, 20) {
          M.retry(&driver, now)
          audio.play("tap")
          audio.buzz(0)
          if !reduced { audio.playScript() }
        }
      } else if target == 3 {
        driver.hopAt = now
        audio.play("tap")
        audio.buzz(2)
      }
    default: break
    }
  }
  func accessibilityRetry() {
    M.takeOver(&driver, now)
    M.retry(&driver, now)
  }
}
