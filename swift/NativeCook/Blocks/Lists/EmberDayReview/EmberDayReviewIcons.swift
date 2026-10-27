import SwiftUI

/// The six Ionicons the React Native file uses, redrawn from the 512 x 512 outlines of Ionicons 7
/// (`checkmark`, `ellipsis-horizontal`, `add`, `arrow-forward`, `lock-closed`, `lock-open`).
enum EmberDayReviewIconKind {
    case checkmark
    case ellipsis
    case add
    case arrowForward
    case lockClosed
    case lockOpen
}

struct EmberDayReviewIcon: View {
    let kind: EmberDayReviewIconKind
    let size: CGFloat
    let color: Color

    var body: some View {
        Canvas { context, canvas in
            let scale = canvas.width / 512
            draw(&context, scale: scale)
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }

    private func draw(_ context: inout GraphicsContext, scale: CGFloat) {
        func point(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: x * scale, y: y * scale) }
        func stroke(_ path: Path, width: CGFloat) {
            context.stroke(
                path,
                with: .color(color),
                style: StrokeStyle(lineWidth: width * scale, lineCap: .round, lineJoin: .round)
            )
        }
        switch kind {
        case .checkmark:
            var path = Path()
            path.move(to: point(416, 128))
            path.addLine(to: point(192, 384))
            path.addLine(to: point(96, 288))
            stroke(path, width: 32)
        case .ellipsis:
            for x in [96.0, 256.0, 416.0] {
                let dot = CGRect(x: (x - 48) * scale, y: (256 - 48) * scale, width: 96 * scale, height: 96 * scale)
                context.fill(Path(ellipseIn: dot), with: .color(color))
            }
        case .add:
            var path = Path()
            path.move(to: point(256, 112))
            path.addLine(to: point(256, 400))
            path.move(to: point(400, 256))
            path.addLine(to: point(112, 256))
            stroke(path, width: 32)
        case .arrowForward:
            var path = Path()
            path.move(to: point(268, 112))
            path.addLine(to: point(412, 256))
            path.addLine(to: point(268, 400))
            path.move(to: point(392, 256))
            path.addLine(to: point(100, 256))
            stroke(path, width: 48)
        case .lockClosed:
            var shackle = Path()
            shackle.move(to: point(336, 208))
            shackle.addLine(to: point(336, 113))
            addShackleArc(&shackle, centerY: 113, scale: scale)
            shackle.addLine(to: point(176, 208))
            stroke(shackle, width: 32)
            context.fill(body(scale: scale), with: .color(color))
        case .lockOpen:
            var shackle = Path()
            shackle.move(to: point(336, 112))
            addShackleArc(&shackle, centerY: 112, scale: scale)
            shackle.addLine(to: point(176, 208))
            stroke(shackle, width: 32)
            context.fill(body(scale: scale), with: .color(color))
        }
    }

    /// `a80 80 0 00-160 0`: the half circle over the top from x = 336 to x = 176, as two cubics.
    private func addShackleArc(_ path: inout Path, centerY: CGFloat, scale: CGFloat) {
        let k: CGFloat = 0.5522847498 * 80
        path.addCurve(
            to: CGPoint(x: 256 * scale, y: (centerY - 80) * scale),
            control1: CGPoint(x: 336 * scale, y: (centerY - k) * scale),
            control2: CGPoint(x: (256 + k) * scale, y: (centerY - 80) * scale)
        )
        path.addCurve(
            to: CGPoint(x: 176 * scale, y: centerY * scale),
            control1: CGPoint(x: (256 - k) * scale, y: (centerY - 80) * scale),
            control2: CGPoint(x: 176 * scale, y: (centerY - k) * scale)
        )
    }

    /// `<rect x="96" y="208" width="320" height="272" rx="48" />`.
    private func body(scale: CGFloat) -> Path {
        Path(
            roundedRect: CGRect(x: 96 * scale, y: 208 * scale, width: 320 * scale, height: 272 * scale),
            cornerRadius: 48 * scale
        )
    }
}

/// A laurel sprig drawn as five leaves on a curved stem; mirrored for the right side.
struct EmberDayReviewLaurel: View {
    var flip = false

    /// `[x, y, rotation]` of each leaf in the React Native file.
    private static let leaves: [(x: CGFloat, y: CGFloat, angle: Double)] = [
        (13, 30, -38), (10.5, 23, -22), (9.5, 16, -6), (10.5, 9.5, 12), (13.5, 4, 30),
    ]

    var body: some View {
        Canvas { context, _ in
            var stem = Path()
            stem.move(to: CGPoint(x: 16, y: 34))
            stem.addCurve(to: CGPoint(x: 13, y: 3), control1: CGPoint(x: 9, y: 28), control2: CGPoint(x: 7, y: 18))
            context.stroke(
                stem,
                with: .color(EmberDayReviewPalette.laurelStem),
                style: StrokeStyle(lineWidth: 1.4, lineCap: .round)
            )
            for leaf in Self.leaves {
                // `M x y c-5-1-7-4-7-7 4 0 7 2 7 7Z`, rotated about (x, y).
                var path = Path()
                let x = leaf.x
                let y = leaf.y
                path.move(to: CGPoint(x: x, y: y))
                path.addCurve(
                    to: CGPoint(x: x - 7, y: y - 7),
                    control1: CGPoint(x: x - 5, y: y - 1),
                    control2: CGPoint(x: x - 7, y: y - 4)
                )
                path.addCurve(
                    to: CGPoint(x: x, y: y),
                    control1: CGPoint(x: x - 3, y: y - 7),
                    control2: CGPoint(x: x, y: y - 5)
                )
                path.closeSubpath()
                let turn = CGAffineTransform(translationX: x, y: y)
                    .rotated(by: leaf.angle * .pi / 180)
                    .translatedBy(x: -x, y: -y)
                context.fill(path.applying(turn), with: .color(EmberDayReviewPalette.laurelLeaf))
            }
        }
        .frame(width: 22, height: 36)
        .scaleEffect(x: flip ? -1 : 1, y: 1)
        .accessibilityHidden(true)
    }
}
