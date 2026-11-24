import Foundation
import QuartzCore

/// The film's clock and the screen's geometry: the one source both SceneKit's render loop (the 3D sheet) and
/// SwiftUI's TimelineView (the chat, the 2D paper, the shadow) read, so nothing writes SwiftUI state per frame.
/// A finger only moves the clock (to the send) and sets the throw's size.
final class PaperPlaneSendController {
  typealias M = PaperPlaneSendMotion

  private let lock = NSLock()
  let film: Bool
  let reduced: Bool
  private var geo: M.Geo?
  private var start: Double?
  private var running: Bool
  private var sends = 0

  init(autoPlay: Bool, reduced: Bool) {
    self.reduced = reduced
    film = autoPlay && !reduced
    running = film
  }

  struct Frame {
    var t: Double
    var geo: M.Geo?
    /// How many times a finger has sent (for the haptic).
    var sends: Int
  }

  /// The screen's real geometry, published by the layout before the film can reach the send (the clock does not
  /// start without it).
  func setGeometry(_ g: M.Geo) {
    lock.lock(); defer { lock.unlock() }
    var next = g
    next.amp = geo?.amp ?? 1
    geo = next
  }

  func frame(at now: Double) -> Frame {
    lock.lock(); defer { lock.unlock() }
    return frameLocked(now)
  }

  private func frameLocked(_ now: Double) -> Frame {
    if reduced { return Frame(t: M.reducedT, geo: geo, sends: sends) }
    guard running, geo != nil else { return Frame(t: 0, geo: geo, sends: sends) }
    if start == nil { start = now }
    var el = max(0, now - start!)
    if el >= M.loop {
      // the loop's seam: a throw's bigger arc is over; without autoplay the film stops here
      start! += M.loop * floor(el / M.loop)
      el = now - start!
      geo?.amp = 1
      if !film {
        running = false
        start = nil
        return Frame(t: 0, geo: geo, sends: sends)
      }
    }
    return Frame(t: el, geo: geo, sends: sends)
  }

  /// A finger sends: the clock jumps to the moment the message leaves the field. `amp` is the throw's size.
  func send(_ now: Double, amp: Double) {
    lock.lock(); defer { lock.unlock() }
    guard !reduced, geo != nil else { return }
    let t = frameLocked(now).t
    // already on its way (or the receipt is still up): let it finish
    if running, t >= M.lift, t < M.resetEnd { return }
    geo?.amp = amp
    start = now - M.lift
    running = true
    sends += 1
  }
}
