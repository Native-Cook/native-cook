import UIKit
import QuartzCore

/// A passive Metal layer. Its owner renders it on the one animation clock.
final class HorizonSurface: UIView {
  enum Kind { case scene, bar, sheet, front, back }
  let kind: Kind
  override class var layerClass: AnyClass { CAMetalLayer.self }
  var metal: CAMetalLayer { layer as! CAMetalLayer }
  init(_ kind: Kind) {
    self.kind = kind
    super.init(frame: .zero)
    isUserInteractionEnabled = false; isAccessibilityElement = false
    isOpaque = kind == .scene
    metal.device = HorizonRenderer.shared.device
    metal.pixelFormat = .bgra8Unorm; metal.framebufferOnly = true
    metal.isOpaque = kind == .scene; metal.colorspace = CGColorSpace(name: CGColorSpace.sRGB)
  }
  @available(*, unavailable) required init?(coder: NSCoder) { fatalError() }
  override func didMoveToWindow() {
    super.didMoveToWindow(); layer.contentsScale = traitCollection.displayScale; setNeedsLayout()
  }
  override func layoutSubviews() {
    super.layoutSubviews()
    let size = CGSize(width: (bounds.width * layer.contentsScale).rounded(), height: (bounds.height * layer.contentsScale).rounded())
    if metal.drawableSize != size { metal.drawableSize = size }
  }
}
