import SwiftUI
import UIKit

struct GlassPlaylistFolderTouch: UIViewRepresentable {
  var tapOnly = false
  var event: (UIGestureRecognizer.State, CGPoint, CGSize, CGVector) -> Void
  func makeUIView(context: Context) -> GlassPlaylistFolderTouchSurface {
    let v = GlassPlaylistFolderTouchSurface()
    v.event = event
    v.tapOnly = tapOnly
    return v
  }
  func updateUIView(_ v: GlassPlaylistFolderTouchSurface, context: Context) {
    v.event = event
    v.tapOnly = tapOnly
  }
}
final class GlassPlaylistFolderTouchSurface: UIView, UIGestureRecognizerDelegate {
  var event: ((UIGestureRecognizer.State, CGPoint, CGSize, CGVector) -> Void)?
  var tapOnly = false
  private var invalid = false
  private var beganAt = 0.0
  private var tapTimeout: Timer?
  private var point = CGPoint.zero
  private var start = CGPoint.zero
  private let pan = UIPanGestureRecognizer()
  override init(frame: CGRect) {
    super.init(frame: frame)
    backgroundColor = .clear
    isAccessibilityElement = false
    let g = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
    g.minimumPressDuration = 0
    g.allowableMovement = .greatestFiniteMagnitude
    g.cancelsTouchesInView = false
    g.delegate = self
    addGestureRecognizer(g)
    pan.cancelsTouchesInView = false
    pan.delegate = self
    addGestureRecognizer(pan)
  }
  @available(*, unavailable) required init?(coder: NSCoder) { nil }
  func gestureRecognizer(
    _ gestureRecognizer: UIGestureRecognizer,
    shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
  ) -> Bool { true }
  @objc private func handle(_ g: UILongPressGestureRecognizer) {
    let p = g.location(in: self)
    point = p
    if g.state == .ended || g.state == .cancelled || g.state == .failed { tapTimeout?.invalidate() }
    if g.state == .began {
      start = p
      invalid = false
      beganAt = ProcessInfo.processInfo.systemUptime
      tapTimeout?.invalidate()
      if tapOnly {
        let timer = Timer(timeInterval: 2, repeats: false) { [weak self] _ in
          guard let self, !self.invalid else { return }
          self.invalid = true
          self.event?(
            .cancelled, self.point,
            CGSize(width: self.point.x - self.start.x, height: self.point.y - self.start.y), .zero)
        }
        tapTimeout = timer
        RunLoop.main.add(timer, forMode: .common)
      }
    }
    if tapOnly {
      if invalid { return }
      if !bounds.contains(p) || hypot(p.x - start.x, p.y - start.y) > 10
        || ProcessInfo.processInfo.systemUptime - beganAt > 2
      {
        invalid = true
        tapTimeout?.invalidate()
        event?(.cancelled, p, CGSize(width: p.x - start.x, height: p.y - start.y), .zero)
        return
      }
    }
    let velocity = pan.velocity(in: self)
    event?(
      g.state, p, CGSize(width: p.x - start.x, height: p.y - start.y),
      CGVector(dx: velocity.x, dy: velocity.y))
  }
}
