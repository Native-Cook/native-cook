import UIKit

struct ParticleDispersionEffectScene {
  let frame: URL
  let mask: URL
  let imageSize: CGSize
  let subjectBounds: CGRect
  let ownsFiles: Bool

  func displayBounds(in viewport: CGSize) -> SIMD4<Float> {
    let scale = max(viewport.width / imageSize.width, viewport.height / imageSize.height)
    let size = CGSize(width: imageSize.width * scale, height: imageSize.height * scale)
    return SIMD4(
      Float(((viewport.width - size.width) / 2 + subjectBounds.minX * size.width) / viewport.width),
      Float(((viewport.height - size.height) / 2 + subjectBounds.minY * size.height) / viewport.height),
      Float(subjectBounds.width * size.width / viewport.width),
      Float(subjectBounds.height * size.height / viewport.height)
    )
  }

  func release() {
    guard ownsFiles else { return }
    try? FileManager.default.removeItem(at: frame)
    try? FileManager.default.removeItem(at: mask)
  }
}
