import SwiftUI
import UIKit

/// An article's cover, drawn once: a three-stop gradient, a few flat shapes and a light from the top left. It is laid
/// out for the open page's cover box; every smaller cover is this same bitmap scaled to cover its box.
enum PeekPopPreviewCover {
    private static func color(_ hex: UInt32, _ alpha: Double = 1) -> UIColor {
        UIColor(red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255, alpha: alpha)
    }

    private static func gradient(_ ctx: CGContext, _ colors: [CGColor], _ stops: [CGFloat], _ from: CGPoint, _ to: CGPoint) {
        let space = CGColorSpace(name: CGColorSpace.sRGB)!
        guard let shading = CGGradient(colorsSpace: space, colors: colors as CFArray, locations: stops) else { return }
        ctx.drawLinearGradient(shading, start: from, end: to, options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
    }

    static func image(_ article: PeekPopPreviewArticle, size: CGSize) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = UIScreen.main.scale
        format.opaque = true
        format.preferredRange = .standard
        return UIGraphicsImageRenderer(size: size, format: format).image { renderer in
            let ctx = renderer.cgContext
            let w = size.width, h = size.height
            gradient(ctx, article.colors.map { color($0).cgColor }, [0, 0.55, 1], .zero, CGPoint(x: w, y: h))
            for shape in article.shapes { draw(shape, ctx, cx: w / 2, cy: h / 2, unit: h) }
            // Light from the top left, a little weight at the bottom: printed card stock rather than a flat fill.
            gradient(ctx, [UIColor(white: 1, alpha: 0.20).cgColor, UIColor(white: 1, alpha: 0).cgColor, UIColor(white: 0, alpha: 0.14).cgColor],
                     [0, 0.45, 1], CGPoint(x: w * 0.1, y: 0), CGPoint(x: w * 0.7, y: h))
        }
    }

    private static func draw(_ shape: PeekPopPreviewArticle.Shape, _ ctx: CGContext, cx: CGFloat, cy: CGFloat, unit: CGFloat) {
        switch shape {
        case let .circle(x, y, size, hex, opacity):
            let d = size * unit
            ctx.setFillColor(color(hex, opacity).cgColor)
            ctx.fillEllipse(in: CGRect(x: cx + x * unit - d / 2, y: cy + y * unit - d / 2, width: d, height: d))
        case let .ring(x, y, size, stroke, hex, opacity):
            // A border sits inside the box.
            let d = size * unit, line = stroke * unit
            ctx.setStrokeColor(color(hex, opacity).cgColor)
            ctx.setLineWidth(line)
            ctx.strokeEllipse(in: CGRect(x: cx + x * unit - d / 2, y: cy + y * unit - d / 2, width: d, height: d).insetBy(dx: line / 2, dy: line / 2))
        case let .bar(x, y, width, height, turn, radius, hex, opacity):
            let bw = width * unit, bh = height * unit
            ctx.saveGState()
            ctx.translateBy(x: cx + x * unit, y: cy + y * unit)
            ctx.rotate(by: turn * .pi / 180)
            ctx.setFillColor(color(hex, opacity).cgColor)
            let box = CGRect(x: -bw / 2, y: -bh / 2, width: bw, height: bh)
            let corner = min(radius * unit, min(bw, bh) / 2)
            ctx.addPath(CGPath(roundedRect: box, cornerWidth: corner, cornerHeight: corner, transform: nil))
            ctx.fillPath()
            ctx.restoreGState()
        }
    }
}

/// expo-blur's light blur at a partial intensity: a system blur held part way through its animator.
struct PeekPopPreviewBlur: UIViewRepresentable {
    var fraction: Double

    final class Host: UIVisualEffectView {
        private var animator: UIViewPropertyAnimator?

        func apply(_ fraction: Double) {
            if animator == nil {
                effect = nil
                let made = UIViewPropertyAnimator(duration: 1, curve: .linear) { [unowned self] in
                    self.effect = UIBlurEffect(style: .light)
                }
                made.pausesOnCompletion = true
                animator = made
            }
            animator?.fractionComplete = fraction
        }

        deinit {
            animator?.stopAnimation(false)
            animator?.finishAnimation(at: .current)
        }
    }

    func makeUIView(context: Context) -> Host {
        let view = Host(effect: nil)
        view.isUserInteractionEnabled = false
        return view
    }

    func updateUIView(_ view: Host, context: Context) {
        view.apply(fraction)
        view.isHidden = fraction <= 0.0005
    }
}
