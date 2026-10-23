// Each canvas retains its argument storage; the page redraws only after layout changes.
import Metal
import QuartzCore
import UIKit

@MainActor final class AuraSurfaceView: UIView {
  override class var layerClass: AnyClass { CAMetalLayer.self }
  private var metal: CAMetalLayer { layer as! CAMetalLayer }
  var isPage = false {
    didSet { if isPage { alpha = 0 } }
  }
  var cardWidth = 362.0
  var cardOrigin = CGPoint(x: 64, y: 64)
  var onShown: (@MainActor (Double) -> Void)?
  private var pageDrawn = false
  private var uniforms: [Float] = []
  private var lastWidth = 0.0
  private var lastScale = 0.0
  private var lastOrigin = CGPoint.zero
  var drive = AuraDrive()
  var reduceTransparency = false

  override init(frame: CGRect) {
    super.init(frame: frame)
    isUserInteractionEnabled = false
    isOpaque = false
    metal.device = AuraRenderer.shared.device
    metal.pixelFormat = .bgra8Unorm
    metal.framebufferOnly = true
    // The shell and its UIKit portraits must reach the compositor together.
    metal.presentsWithTransaction = true
    metal.isOpaque = false
    metal.colorspace = CGColorSpace(name: CGColorSpace.sRGB)
  }

  @available(*, unavailable) required init?(coder: NSCoder) { fatalError("Created in code") }

  override func didMoveToWindow() {
    super.didMoveToWindow()
    layer.contentsScale = traitCollection.displayScale
    setNeedsLayout()
  }

  override func layoutSubviews() {
    super.layoutSubviews()
    let size = CGSize(
      width: (bounds.width * layer.contentsScale).rounded(),
      height: (bounds.height * layer.contentsScale).rounded())
    if metal.drawableSize != size { metal.drawableSize = size }
    render()
  }

  func render() {
    guard window != nil, bounds.width > 0 else { return }
    if lastWidth != cardWidth || lastScale != layer.contentsScale || uniforms.isEmpty {
      uniforms = AuraUniforms.shell(width: cardWidth, pixelRatio: layer.contentsScale)
      lastWidth = cardWidth
      lastScale = layer.contentsScale
    }
    if lastOrigin != cardOrigin || isPage {
      AuraUniforms.put(&uniforms, AuraUniformSlot.origin, cardOrigin.x, cardOrigin.y)
      lastOrigin = cardOrigin
    }
    if !isPage {
      AuraUniforms.motion(&uniforms, width: cardWidth, drive: drive, opaque: reduceTransparency)
    }
    // Only closed-lip shell frames contribute to the arrival hold. Stop asking
    // Metal for presentation callbacks once arrival has started.
    let track = isPage ? !pageDrawn : drive.entered == 0
    let onShown: (@MainActor @Sendable (Double) -> Void)?
    if track {
      onShown = { [weak self] time in self?.didShowFrame(at: time) }
    } else {
      onShown = nil
    }
    AuraRenderer.shared.draw(
      uniforms, on: metal, page: isPage, onShown: onShown)
  }

  private func didShowFrame(at time: Double) {
    guard window != nil else { return }
    if isPage {
      guard !pageDrawn else { return }
      pageDrawn = true
      // Alpha is still zero until the first page command has actually finished.
      UIView.animate(withDuration: 0.14, delay: 0, options: [.curveEaseOut]) {
        self.alpha = 1
      }
    }
    onShown?(time)
  }
}
