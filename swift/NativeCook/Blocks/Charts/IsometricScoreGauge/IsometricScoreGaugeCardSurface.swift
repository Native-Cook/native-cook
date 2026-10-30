import UIKit

final class IsometricScoreGaugeCardSurface: UIView {
    private let cards = (0..<8).map { _ in CALayer() }
    private var lastAmbient = 0.0
    private var lastFrame = IsometricScoreGaugeMotion.Frame()
    private var cache: [Int: (lift: Double, angle: Double, scale: Double, image: CGImage)] = [:]
    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        isUserInteractionEnabled = false
        clipsToBounds = true
        cards.forEach {
            $0.bounds = CGRect(x: 0, y: 0, width: 219, height: 267)
            $0.actions = ["contents": NSNull(), "position": NSNull(), "transform": NSNull(), "hidden": NSNull()]
            layer.addSublayer($0)
        }
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { nil }
    override func layoutSubviews() {
        super.layoutSubviews()
        draw(lastFrame, ambient: lastAmbient)
    }
    func draw(_ frame: IsometricScoreGaugeMotion.Frame, ambient: Double) {
        let ambientChanged = ambient != lastAmbient
        lastAmbient = ambient
        lastFrame = frame
        let scale = bounds.width / IsometricScoreGaugeTheme.width
        guard scale > 0 else { return }
        let dy = (bounds.height - IsometricScoreGaugeTheme.height * scale) / 2
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        for index in cards.indices {
            let pose = IsometricScoreGaugeMotion.columnPose(index / 2, scroll: frame.scroll)
            let card = cards[index]
            card.isHidden = pose.x < -90 || pose.x > 492
            if card.isHidden { continue }
            let lift = index == frame.sel ? frame.selW : 0
            let rasterScale = traitCollection.displayScale * scale
            let cached = cache[index]
            if (lift > 0 && ambientChanged) || cached?.lift != lift || cached?.angle != pose.angle || cached?.scale != rasterScale {
                if let image = IsometricScoreGaugeArt.cardImage(index: index, lift: lift, angle: pose.angle, scale: rasterScale, ambient: ambient) {
                    cache[index] = (lift, pose.angle, rasterScale, image)
                    card.contents = image
                }
            }
            card.position = CGPoint(x: pose.x * scale, y: dy + (IsometricScoreGaugeTheme.UI.rows[index % 2] + 91.5 - 6 * lift) * scale)
            // Scale screen space outside perspective; reference perspective is 520 design points.
            var transform = CATransform3DIdentity
            transform.m34 = -1 / (520 * scale)
            transform = CATransform3DRotate(transform, pose.angle * .pi / 180, 0, 1, 0)
            let depthScale = pose.scale * (1 + 0.04 * lift) * scale
            card.transform = CATransform3DScale(transform, depthScale, depthScale, depthScale)
        }
        CATransaction.commit()
    }
}
