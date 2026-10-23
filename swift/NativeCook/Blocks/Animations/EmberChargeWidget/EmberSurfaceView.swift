import Metal
import QuartzCore
import UIKit

/// A transparent CAMetalLayer at the device pixel scale; no layer-owned animation clock.
@MainActor final class EmberSurfaceView: UIView {
  override class var layerClass: AnyClass { CAMetalLayer.self }

  /// Snapshot submitted to Metal; repeated snapshots need no new draw.
  var frameValue = EmberFrame()

  /// Selects the small tube surface instead of the glass canvas.
  var isTube = false

  /// Local reusable blur targets owned by this tube surface.
  private let cache = EmberBloomCache()

  override init(frame: CGRect) {
    super.init(frame: frame)
    isUserInteractionEnabled = false
    isOpaque = false
    let metalLayer = layer as! CAMetalLayer
    metalLayer.device = EmberRenderer.shared?.device
    metalLayer.pixelFormat = EmberRenderer.format
    metalLayer.colorspace = CGColorSpace(name: CGColorSpace.sRGB)
    metalLayer.isOpaque = false
    metalLayer.framebufferOnly = true
  }
  @available(*, unavailable) required init?(coder: NSCoder) { fatalError() }

  override func didMoveToWindow() {
    super.didMoveToWindow()
    layer.contentsScale = traitCollection.displayScale
    setNeedsLayout()
  }

  override func layoutSubviews() {
    super.layoutSubviews()
    let metalLayer = layer as! CAMetalLayer
    let size = CGSize(
      width: (bounds.width * layer.contentsScale).rounded(),
      height: (bounds.height * layer.contentsScale).rounded())
    if metalLayer.drawableSize != size { metalLayer.drawableSize = size }
    render()
  }

  func render() {
    guard window != nil, bounds.width > 0 else { return }
    EmberRenderer.shared?.render(
      frameValue, into: layer as! CAMetalLayer, isTube: isTube, cache: cache)
  }
}
