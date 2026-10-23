import SwiftUI

/// The emoji in the dialog's header: a glossy yellow disc in a thin ring, with five expressions drawn
/// on top of each other. `face` runs from 0 (frown) to 4 (heart eyes); between two whole numbers the
/// neighbouring expressions cross-fade.
struct EmojiRatingPopupMood: View {
    static let size = 84.0
    private static let center = size / 2
    private static let ring = 39.0
    private static let disc = 32.0
    // The features are drawn for a face 50 across and scaled to the disc.
    private static let featureScale = disc / 25

    private static let eyeX = 8.0
    private static let eyeY = -4.0
    private static let ink = Color(hex: 0x3d2300)
    private static let heartTop = Color(hex: 0xff6b81)
    private static let heartBottom = Color(hex: 0xe0193a)
    private static let mouthTop = Color(hex: 0x5a1020)
    private static let mouthBottom = Color(hex: 0x8e2034)
    private static let tongue = Color(hex: 0xff7d8f)
    private static let blush = Color.rgba(255, 110, 90, 0.38)
    private static let faces = 5

    // Faces by place in the row, from the saddest to the happiest. The shapes sit around (0, 0).
    private static let frown = EmojiRatingPopupPath.svg("M-8 12Q0 4 8 12")
    private static let browLeft = EmojiRatingPopupPath.svg("M-12 -12Q-8 -14 -4 -11")
    private static let browRight = EmojiRatingPopupPath.svg("M12 -12Q8 -14 4 -11")
    private static let flat = EmojiRatingPopupPath.svg("M-7 9L7 9")
    private static let smile = EmojiRatingPopupPath.svg("M-9 6Q0 15 9 6")
    private static let happyEye = EmojiRatingPopupPath.svg("M-4 2Q0 -5 4 2")
    private static let openMouth = EmojiRatingPopupPath.svg("M-12 2Q0 4 12 2Q11 18 0 18Q-11 18 -12 2Z")
    private static let grinTeeth = EmojiRatingPopupPath.svg("M-11.4 2.6Q0 4.6 11.4 2.6L11 6Q0 7.6 -11 6Z")
    private static let smallOpenMouth = EmojiRatingPopupPath.svg("M-9 5Q0 7 9 5Q8 16 0 16Q-8 16 -9 5Z")
    private static let smallTeeth = EmojiRatingPopupPath.svg("M-8.6 5.4Q0 7.4 8.6 5.4L8.3 8.2Q0 9.8 -8.3 8.2Z")
    private static let heart = EmojiRatingPopupPath.svg("M0 6.5C-10 0 -8 -9 0 -4.5C8 -9 10 0 0 6.5Z")

    var face: Double

    var body: some View {
        Canvas { context, _ in
            let c = Self.center
            let ring = Self.circle(c, c, Self.ring)
            context.drawLayer { layer in
                layer.addFilter(.shadow(color: Color.rgba(120, 80, 0, 0.18), radius: 5, x: 0, y: 3))
                layer.fill(ring, with: .color(.white))
            }
            context.stroke(Self.circle(c, c, Self.ring - 1.5), with: .color(Color(hex: 0xf6d66a)), lineWidth: 2)
            // The disc: warm yellow at the top to orange at the chin, a darker rim, a soft gloss.
            context.fill(
                Self.circle(c, c, Self.disc),
                with: .radialGradient(
                    Gradient(stops: [
                        .init(color: Color(hex: 0xffe680), location: 0),
                        .init(color: Color(hex: 0xffc53a), location: 0.45),
                        .init(color: Color(hex: 0xff9a1f), location: 0.8),
                        .init(color: Color(hex: 0xf2731a), location: 1),
                    ]),
                    center: CGPoint(x: c, y: c - Self.disc * 0.35), startRadius: 0, endRadius: Self.disc * 1.45
                )
            )
            context.stroke(Self.circle(c, c, Self.disc - 0.5), with: .color(Color.rgba(214, 110, 0, 0.35)), lineWidth: 1)
            context.drawLayer { layer in
                layer.addFilter(.blur(radius: 3))
                layer.fill(
                    Path(ellipseIn: CGRect(x: c - Self.disc * 0.55, y: c - Self.disc * 0.88, width: Self.disc * 1.1, height: Self.disc * 0.5)),
                    with: .color(.white.opacity(0.45))
                )
            }
            for place in 0..<Self.faces {
                let opacity = min(1, max(0, 1 - abs(face - Double(place))))
                guard opacity > 0 else { continue }
                context.drawLayer { layer in
                    layer.opacity = opacity
                    layer.translateBy(x: c, y: c)
                    layer.scaleBy(x: Self.featureScale, y: Self.featureScale)
                    Self.expression(layer, place)
                }
            }
        }
        .frame(width: Self.size, height: Self.size)
    }

    private static func circle(_ x: Double, _ y: Double, _ r: Double) -> Path {
        Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
    }

    private static func eyes(_ context: GraphicsContext) {
        for x in [-eyeX, eyeX] {
            context.fill(Path(ellipseIn: CGRect(x: x - 2.6, y: eyeY - 3.8, width: 5.2, height: 7.6)), with: .color(ink))
            context.fill(circle(x + 0.8, eyeY - 1.8, 1.1), with: .color(.white.opacity(0.85)))
        }
    }

    private static func stroke(_ context: GraphicsContext, _ path: Path, width: Double = 2.6, dx: Double = 0, dy: Double = 0) {
        var copy = context
        copy.translateBy(x: dx, y: dy)
        copy.stroke(path, with: .color(ink), style: StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round))
    }

    private static func cheeks(_ context: GraphicsContext) {
        context.drawLayer { layer in
            layer.addFilter(.blur(radius: 1.6))
            layer.fill(Path(ellipseIn: CGRect(x: -17, y: 3, width: 8, height: 5)), with: .color(blush))
            layer.fill(Path(ellipseIn: CGRect(x: 9, y: 3, width: 8, height: 5)), with: .color(blush))
        }
    }

    /// An open mouth with a dark gradient, a row of teeth and a tongue.
    private static func mouth(_ context: GraphicsContext, _ shape: Path, teeth: Path, tongueY: Double, tongueR: Double) {
        var inside = context
        inside.clip(to: shape)
        inside.fill(shape, with: .linearGradient(Gradient(colors: [mouthTop, mouthBottom]), startPoint: CGPoint(x: 0, y: 2), endPoint: CGPoint(x: 0, y: 18)))
        inside.fill(teeth, with: .color(.white))
        inside.fill(circle(0, tongueY, tongueR), with: .color(tongue))
    }

    private static func expression(_ context: GraphicsContext, _ place: Int) {
        switch place {
        case 0:
            stroke(context, browLeft, width: 2)
            stroke(context, browRight, width: 2)
            eyes(context)
            stroke(context, frown)
        case 1:
            eyes(context)
            stroke(context, flat)
        case 2:
            cheeks(context)
            eyes(context)
            stroke(context, smile)
        case 3:
            cheeks(context)
            stroke(context, happyEye, dx: -eyeX, dy: eyeY - 1)
            stroke(context, happyEye, dx: eyeX, dy: eyeY - 1)
            mouth(context, openMouth, teeth: grinTeeth, tongueY: 19, tongueR: 6.5)
        default:
            cheeks(context)
            for x in [-eyeX - 1, eyeX + 1] {
                var copy = context
                copy.translateBy(x: x, y: eyeY - 1)
                copy.fill(heart, with: .linearGradient(Gradient(colors: [heartTop, heartBottom]), startPoint: CGPoint(x: 0, y: -8), endPoint: CGPoint(x: 0, y: 7)))
                copy.fill(Path(ellipseIn: CGRect(x: -5.5, y: -5, width: 3, height: 2)), with: .color(.white.opacity(0.7)))
            }
            mouth(context, smallOpenMouth, teeth: smallTeeth, tongueY: 17, tongueR: 5)
        }
    }
}

/// Paths written as SVG path data, absolute commands only (M, L, H, V, Q, C, Z), as the React
/// Native version writes them.
enum EmojiRatingPopupPath {
    static func svg(_ data: String) -> Path {
        var tokens: [String] = []
        var number = ""
        for char in data {
            if char.isLetter {
                if !number.isEmpty { tokens.append(number); number = "" }
                tokens.append(String(char))
            } else if char == " " || char == "," {
                if !number.isEmpty { tokens.append(number); number = "" }
            } else if char == "-", !number.isEmpty {
                tokens.append(number)
                number = "-"
            } else {
                number.append(char)
            }
        }
        if !number.isEmpty { tokens.append(number) }

        var path = Path()
        var command = "M"
        var i = 0
        var current = CGPoint.zero
        func next() -> Double {
            defer { i += 1 }
            return Double(tokens[i]) ?? 0
        }
        func point() -> CGPoint { CGPoint(x: next(), y: next()) }
        while i < tokens.count {
            if Double(tokens[i]) == nil {
                command = tokens[i]
                i += 1
                if command == "Z" { path.closeSubpath(); continue }
            }
            switch command {
            case "M": current = point(); path.move(to: current); command = "L"
            case "L": current = point(); path.addLine(to: current)
            case "H": current.x = next(); path.addLine(to: current)
            case "V": current.y = next(); path.addLine(to: current)
            case "Q":
                let control = point()
                current = point()
                path.addQuadCurve(to: current, control: control)
            case "C":
                let a = point()
                let b = point()
                current = point()
                path.addCurve(to: current, control1: a, control2: b)
            default: i += 1
            }
        }
        return path
    }
}
