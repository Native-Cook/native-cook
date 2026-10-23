import SwiftUI

/// Colours of Scatter Grid Album.
enum ScatterGridAlbumPalette {
    static let screen = Color.white
    /// The gradient: a near-white base with a blue light in the middle and a mint one low on the right; folded
    /// into the card they make its blue-grey middle, as in the design.
    static let base = Color(hex: 0xF4F7FA)
    static let blue = Color(hex: 0xB2C6DE)
    static let mint = Color(hex: 0xC3E0CF)
    /// A photo still loading.
    static let placeholder = Color(hex: 0xE6E9EE)
    static let icon = Color(hex: 0x9AA0A8)
    static let heart = Color(hex: 0xFF4D6A)
    static let year = Color.white.opacity(0.85)
    static let headingShadow = Color.black.opacity(0.06)
    static let headingShadowRadius = 12.0
    static let photoShadow = Color.black.opacity(0.18)
    static let photoShadowRadius = 18.0
    static let photoShadowY = 8.0
    /// A photo in its cell keeps this share of its shadow.
    static let cellShadow = 0.3
    static let cardShadow = Color.black.opacity(0.16)
    static let cardShadowRadius = 30.0
    static let cardShadowY = 16.0
    /// The soft shadow under each icon, as in the design.
    static let iconShadow = Color.black.opacity(0.14)
    static let iconShadowRadius = 3.0
    static let iconShadowY = 4.0
}

/// The two lights of the gradient, in its frame's coordinates (so they squash with it into the card):
/// centre and radii as shares of the frame's width and height. Each is full colour out to `core` of
/// its radius, then fades out.
enum ScatterGridAlbumGlow {
    static let blue = (x: 0.3, y: 0.45, rx: 0.85, ry: 0.34)
    static let mint = (x: 0.72, y: 0.64, rx: 0.6, ry: 0.28)
    static let core = 0.15
}

private typealias P = ScatterGridAlbumPalette

/// The white screen with the gradient in its moving frame: the whole screen open, the card folded.
struct ScatterGridAlbumBackdrop: View {
    let pose: ScatterGridAlbumMotion.CardPose

    var body: some View {
        Canvas { context, size in
            context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(P.screen))
            let rect = pose.rect
            let shape = Path(roundedRect: rect, cornerRadius: pose.radius, style: .continuous)
            if pose.shadow > 0 {
                var shadow = context
                shadow.addFilter(.shadow(color: P.cardShadow.opacity(pose.shadow), radius: P.cardShadowRadius, y: P.cardShadowY))
                shadow.fill(shape, with: .color(P.base))
            }
            var card = context
            if pose.feather > 0.2 { card.addFilter(.blur(radius: pose.feather)) }
            card.drawLayer { layer in
                layer.clip(to: shape)
                layer.fill(shape, with: .color(P.base))
                for (color, glow) in [(P.blue, ScatterGridAlbumGlow.blue), (P.mint, ScatterGridAlbumGlow.mint)] {
                    // An ellipse: a circle of radius rx·width, squashed to ry·height.
                    let rx = glow.rx * rect.width
                    let ry = glow.ry * rect.height
                    var blob = layer
                    blob.translateBy(x: rect.minX + glow.x * rect.width, y: rect.minY + glow.y * rect.height)
                    blob.scaleBy(x: 1, y: ry / rx)
                    blob.fill(
                        Path(CGRect(x: -rx, y: -rx, width: rx * 2, height: rx * 2)),
                        with: .radialGradient(
                            Gradient(stops: [
                                .init(color: color, location: 0),
                                .init(color: color, location: ScatterGridAlbumGlow.core),
                                .init(color: color.opacity(0), location: 1),
                            ]),
                            center: .zero, startRadius: 0, endRadius: rx
                        )
                    )
                }
            }
        }
    }
}

/// One photo: aspect-filled in its frame, rounded, with its shadow; grey while it loads.
struct ScatterGridAlbumPhoto: View {
    let image: UIImage?
    let pose: ScatterGridAlbumMotion.PhotoPose

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: pose.radius, style: .continuous)
        let lift = P.cellShadow + (1 - P.cellShadow) * pose.lift
        ZStack {
            P.placeholder
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
                    .frame(width: pose.width, height: pose.height)
            }
        }
        .frame(width: pose.width, height: pose.height)
        .clipShape(shape)
        .background {
            shape.fill(P.placeholder)
                .shadow(color: P.photoShadow.opacity(lift), radius: P.photoShadowRadius * lift, y: P.photoShadowY * lift)
        }
        .rotationEffect(.degrees(pose.tilt))
        .position(pose.center)
    }
}

// MARK: - Icons (20 × 20 boxes, stroked at 1.6)

/// Share: an open box with an arrow up out of it.
struct ScatterGridAlbumShareIcon: Shape {
    func path(in rect: CGRect) -> Path {
        let s = rect.width / 20
        var p = Path()
        p.move(to: CGPoint(x: 7.2 * s, y: 7.6 * s))
        p.addLine(to: CGPoint(x: 5.8 * s, y: 7.6 * s))
        p.addQuadCurve(to: CGPoint(x: 4.4 * s, y: 9 * s), control: CGPoint(x: 4.4 * s, y: 7.6 * s))
        p.addLine(to: CGPoint(x: 4.4 * s, y: 16.6 * s))
        p.addQuadCurve(to: CGPoint(x: 5.8 * s, y: 18 * s), control: CGPoint(x: 4.4 * s, y: 18 * s))
        p.addLine(to: CGPoint(x: 14.2 * s, y: 18 * s))
        p.addQuadCurve(to: CGPoint(x: 15.6 * s, y: 16.6 * s), control: CGPoint(x: 15.6 * s, y: 18 * s))
        p.addLine(to: CGPoint(x: 15.6 * s, y: 9 * s))
        p.addQuadCurve(to: CGPoint(x: 14.2 * s, y: 7.6 * s), control: CGPoint(x: 15.6 * s, y: 7.6 * s))
        p.addLine(to: CGPoint(x: 12.8 * s, y: 7.6 * s))
        p.move(to: CGPoint(x: 10 * s, y: 12.4 * s))
        p.addLine(to: CGPoint(x: 10 * s, y: 2.2 * s))
        p.move(to: CGPoint(x: 7.1 * s, y: 5 * s))
        p.addLine(to: CGPoint(x: 10 * s, y: 2.1 * s))
        p.addLine(to: CGPoint(x: 12.9 * s, y: 5 * s))
        return p.offsetBy(dx: rect.minX, dy: rect.minY)
    }
}

/// Heart, also filled when liked.
struct ScatterGridAlbumHeartIcon: Shape {
    func path(in rect: CGRect) -> Path {
        let s = rect.width / 20
        var p = Path()
        p.move(to: CGPoint(x: 10 * s, y: 17 * s))
        p.addCurve(to: CGPoint(x: 2.6 * s, y: 7.7 * s), control1: CGPoint(x: 6.4 * s, y: 14.6 * s), control2: CGPoint(x: 2.6 * s, y: 11.4 * s))
        p.addCurve(to: CGPoint(x: 6.6 * s, y: 3.4 * s), control1: CGPoint(x: 2.6 * s, y: 5.2 * s), control2: CGPoint(x: 4.4 * s, y: 3.4 * s))
        p.addCurve(to: CGPoint(x: 10 * s, y: 5.4 * s), control1: CGPoint(x: 8.1 * s, y: 3.4 * s), control2: CGPoint(x: 9.3 * s, y: 4.2 * s))
        p.addCurve(to: CGPoint(x: 13.4 * s, y: 3.4 * s), control1: CGPoint(x: 10.7 * s, y: 4.2 * s), control2: CGPoint(x: 11.9 * s, y: 3.4 * s))
        p.addCurve(to: CGPoint(x: 17.4 * s, y: 7.7 * s), control1: CGPoint(x: 15.6 * s, y: 3.4 * s), control2: CGPoint(x: 17.4 * s, y: 5.2 * s))
        p.addCurve(to: CGPoint(x: 10 * s, y: 17 * s), control1: CGPoint(x: 17.4 * s, y: 11.4 * s), control2: CGPoint(x: 13.6 * s, y: 14.6 * s))
        p.closeSubpath()
        return p.offsetBy(dx: rect.minX, dy: rect.minY)
    }
}

/// Info: a circle with an "i" (its dot is drawn by `ScatterGridAlbumInfoDot`).
struct ScatterGridAlbumInfoIcon: Shape {
    func path(in rect: CGRect) -> Path {
        let s = rect.width / 20
        var p = Path(ellipseIn: CGRect(x: 2.2 * s, y: 2.2 * s, width: 15.6 * s, height: 15.6 * s))
        p.move(to: CGPoint(x: 8.7 * s, y: 9 * s))
        p.addLine(to: CGPoint(x: 10 * s, y: 9 * s))
        p.addLine(to: CGPoint(x: 10 * s, y: 14 * s))
        p.move(to: CGPoint(x: 8.6 * s, y: 14 * s))
        p.addLine(to: CGPoint(x: 11.4 * s, y: 14 * s))
        return p.offsetBy(dx: rect.minX, dy: rect.minY)
    }
}

struct ScatterGridAlbumInfoDot: Shape {
    func path(in rect: CGRect) -> Path {
        let s = rect.width / 20
        return Path(ellipseIn: CGRect(x: 9.05 * s, y: 5.55 * s, width: 1.9 * s, height: 1.9 * s)).offsetBy(dx: rect.minX, dy: rect.minY)
    }
}

/// Trash: a lid with a handle, a tapered can with two ribs.
struct ScatterGridAlbumTrashIcon: Shape {
    func path(in rect: CGRect) -> Path {
        let s = rect.width / 20
        var p = Path()
        p.move(to: CGPoint(x: 3.2 * s, y: 5 * s))
        p.addLine(to: CGPoint(x: 16.8 * s, y: 5 * s))
        p.move(to: CGPoint(x: 7.6 * s, y: 5 * s))
        p.addLine(to: CGPoint(x: 7.6 * s, y: 3.6 * s))
        p.addQuadCurve(to: CGPoint(x: 8.6 * s, y: 2.6 * s), control: CGPoint(x: 7.6 * s, y: 2.6 * s))
        p.addLine(to: CGPoint(x: 11.4 * s, y: 2.6 * s))
        p.addQuadCurve(to: CGPoint(x: 12.4 * s, y: 3.6 * s), control: CGPoint(x: 12.4 * s, y: 2.6 * s))
        p.addLine(to: CGPoint(x: 12.4 * s, y: 5 * s))
        p.move(to: CGPoint(x: 4.9 * s, y: 5 * s))
        p.addLine(to: CGPoint(x: 5.8 * s, y: 16.6 * s))
        p.addQuadCurve(to: CGPoint(x: 7.2 * s, y: 18 * s), control: CGPoint(x: 5.9 * s, y: 18 * s))
        p.addLine(to: CGPoint(x: 12.8 * s, y: 18 * s))
        p.addQuadCurve(to: CGPoint(x: 14.2 * s, y: 16.6 * s), control: CGPoint(x: 14.1 * s, y: 18 * s))
        p.addLine(to: CGPoint(x: 15.1 * s, y: 5 * s))
        p.move(to: CGPoint(x: 8.4 * s, y: 8.2 * s))
        p.addLine(to: CGPoint(x: 8.4 * s, y: 14.8 * s))
        p.move(to: CGPoint(x: 11.6 * s, y: 8.2 * s))
        p.addLine(to: CGPoint(x: 11.6 * s, y: 14.8 * s))
        return p.offsetBy(dx: rect.minX, dy: rect.minY)
    }
}

/// The six dots that burst out of the heart as it fills.
struct ScatterGridAlbumBurst: View {
    /// 0 → 1 over the burst.
    let progress: Double

    var body: some View {
        typealias M = ScatterGridAlbumMotion
        let e = M.easeOut.value(at: progress)
        let radius = M.burstFrom + (M.burstTo - M.burstFrom) * e
        let dot = M.burstDot * (1 - 0.4 * progress)
        let size = (M.burstTo + M.burstDot) * 2
        return Canvas { context, canvas in
            let c = CGPoint(x: canvas.width / 2, y: canvas.height / 2)
            for k in 0..<M.burstDots {
                let a = Double(k) / Double(M.burstDots) * 2 * .pi - .pi / 2
                let p = CGPoint(x: c.x + cos(a) * radius, y: c.y + sin(a) * radius)
                context.fill(Path(ellipseIn: CGRect(x: p.x - dot / 2, y: p.y - dot / 2, width: dot, height: dot)), with: .color(P.heart))
            }
        }
        .frame(width: size, height: size)
        .opacity(1 - progress)
        .allowsHitTesting(false)
    }
}

// MARK: - Photos

/// Downloads a photo once and keeps it decoded.
enum ScatterGridAlbumPhotos {
    private static let cache = NSCache<NSString, UIImage>()

    static func cached(_ address: String) -> UIImage? {
        cache.object(forKey: address as NSString)
    }

    static func load(_ address: String) async -> UIImage? {
        if let hit = cached(address) { return hit }
        guard let url = URL(string: address),
              let (data, _) = try? await URLSession.shared.data(from: url),
              let image = UIImage(data: data)
        else { return nil }
        // Decoded off the main thread, so the first frame that draws it does not stall.
        let ready = await image.byPreparingForDisplay() ?? image
        cache.setObject(ready, forKey: address as NSString)
        return ready
    }
}

/// Shows the label as it is and reports when the finger goes down and comes up.
struct ScatterGridAlbumPressStyle: ButtonStyle {
    var onPress: (Bool) -> Void = { _ in }

    func makeBody(configuration: Configuration) -> some View {
        configuration.label.onChange(of: configuration.isPressed) { _, down in onPress(down) }
    }
}
