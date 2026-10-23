import QuartzCore
import Foundation

/// Reusable glyph window; implicit Core Animation actions would fight the shared clock.
final class HorizonNumberLayer {
  var id: Int64?
  let window = CALayer(), content = CALayer()
  init() {
    window.isHidden = true; window.masksToBounds = true
    window.actions = ["position": NSNull(), "bounds": NSNull(), "hidden": NSNull()]
    content.actions = ["position": NSNull(), "bounds": NSNull(), "transform": NSNull(),
      "opacity": NSNull(), "contents": NSNull(), "contentsScale": NSNull()]
    window.addSublayer(content)
  }
}
