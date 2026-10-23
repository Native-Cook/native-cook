import CoreGraphics

/// The sampled tear: an entry anchor, anchors every eightieth of the pack's width, and a
/// live end point under the finger.
enum BoosterPackTearGesture {
  private typealias M = BoosterPackModel
  static let TRACE_INTERVALS = 80

  /// Advance the uncut edge while keeping the last point attached to the finger.
  static func advanceTearPoints(
    _ current: [CGPoint], _ position: CGPoint, _ pack: BoosterPackRect, _ direction: Double
  ) -> [CGPoint]? {
    if current.isEmpty || pack.width <= 0 { return nil }

    let last = current[current.count - 1]
    let rawTravel = direction * (position.x - last.x)
    // Moving over material already torn cannot move the cut's frontier,
    // including its height while the finger is behind that frontier.
    if rawTravel < 0 { return nil }

    let entryX = direction == 1 ? pack.x : pack.x + pack.width
    let x = M.clamp(position.x, pack.x, pack.x + pack.width)
    let corridorTop = pack.y + pack.height * 0.08
    let corridorBottom = pack.y + pack.height * 0.27
    let fraction = rawTravel > 0 ? direction * (x - last.x) / rawTravel : 1
    // A fast event can land outside the pack. Keep the actual segment's
    // intersection with the edge, instead of using the outside finger's y.
    let y = M.clamp(last.y + (position.y - last.y) * fraction, corridorTop, corridorBottom)
    if x == last.x && y == last.y { return nil }

    // The entry and historical anchors are permanent; only the live endpoint
    // is replaced. At most 79 interior grid anchors survive any number of events.
    var next = current.count > 1 ? Array(current.dropLast()) : [current[0]]
    if rawTravel > 0 {
      for index in 1..<TRACE_INTERVALS {
        let anchorX = entryX + direction * ((pack.width * Double(index)) / Double(TRACE_INTERVALS))
        let fromLast = direction * (anchorX - last.x)
        if fromLast < 0 || direction * (x - anchorX) <= 0 { continue }
        next.append(CGPoint(
          x: anchorX,
          y: M.clamp(last.y + (position.y - last.y) * (fromLast / rawTravel), corridorTop, corridorBottom)))
      }
    }
    next.append(CGPoint(x: x, y: y))
    return next
  }
}
