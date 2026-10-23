import SwiftUI

struct BubbleMediaBurstItem: Equatable {
    /// Read out by VoiceOver and passed to `onSelect`.
    var title: String
    var photo: String
    /// Shown while the photo loads.
    var tint: Color
    /// The light the glass catches along its bottom edge.
    var glow: Color
}

// The glass, in shares of the bubble's diameter: the rim darkens, a crescent of light lies along the
// top left with a glint in it, and a coloured reflection along the bottom right.
private enum Glass {
    static let vignette = Gradient(stops: [
        .init(color: .black.opacity(0), location: 0),
        .init(color: .black.opacity(0), location: 0.55),
        .init(color: .black.opacity(0.1), location: 0.8),
        .init(color: .black.opacity(0.32), location: 0.94),
        .init(color: .black.opacity(0.22), location: 1),
    ])
    static let shineOuter = 0.465
    static let shineInner = (x: 0.628, y: 0.678, r: 0.61)
    static let shine = Gradient(colors: [.white.opacity(0.4), .white.opacity(0)])
    static let glint = (x: 0.3, y: 0.2, r: 0.07)
    static let glintColors = Gradient(colors: [.white.opacity(0.55), .white.opacity(0)])
    static let reflectOuter = 0.47
    static let reflectInner = (x: 0.45, y: 0.306, r: 0.61)
    static let reflectAlpha = 0.5
    static let rim = Color.white.opacity(0.25)
    // The soft shadow under each bubble.
    static let shadow = Color.black.opacity(0.12)
    static let shadowY = 6.0
    static let shadowRadius = 12.0
    // A picked bubble: a white ring on its edge and a check on its rim at the top right.
    static let ring = 2.0
    static let badge = 24.0
    static let badgeFill = Color(hex: 0x1A1A1A)
    static let badgeBorder = 2.0
    static let badgeAt = 0.5.squareRoot() / 2
}

private func crescent(_ size: Double, outer: Double, inner: (x: Double, y: Double, r: Double)) -> Path {
    let a = Path(ellipseIn: CGRect(x: size / 2 - outer * size, y: size / 2 - outer * size, width: outer * size * 2, height: outer * size * 2))
    let b = Path(ellipseIn: CGRect(x: (inner.x - inner.r) * size, y: (inner.y - inner.r) * size, width: inner.r * size * 2, height: inner.r * size * 2))
    return a.subtracting(b)
}

/// The glass drawn over a photo: rim shade, top light, glint, bottom reflection and a fine rim.
private struct BubbleMediaBurstGlass: View {
    let glow: Color

    var body: some View {
        Canvas { context, canvas in
            let size = canvas.width
            let r = size / 2
            let disc = Path(ellipseIn: CGRect(x: 0, y: 0, width: size, height: size))
            context.fill(disc, with: .radialGradient(Glass.vignette, center: CGPoint(x: r, y: r), startRadius: 0, endRadius: r))
            context.fill(
                crescent(size, outer: Glass.reflectOuter, inner: Glass.reflectInner),
                with: .linearGradient(
                    Gradient(colors: [glow.opacity(Glass.reflectAlpha), glow.opacity(0)]),
                    startPoint: CGPoint(x: 0.62 * size, y: 0.99 * size),
                    endPoint: CGPoint(x: 0.5 * size, y: 0.62 * size)
                )
            )
            context.fill(
                crescent(size, outer: Glass.shineOuter, inner: Glass.shineInner),
                with: .linearGradient(Glass.shine, startPoint: CGPoint(x: 0.16 * size, y: 0.1 * size), endPoint: CGPoint(x: 0.5 * size, y: 0.5 * size))
            )
            let glint = CGPoint(x: Glass.glint.x * size, y: Glass.glint.y * size)
            let gr = Glass.glint.r * size
            context.fill(
                Path(ellipseIn: CGRect(x: glint.x - gr, y: glint.y - gr, width: gr * 2, height: gr * 2)),
                with: .radialGradient(Glass.glintColors, center: glint, startRadius: 0, endRadius: gr)
            )
            context.stroke(Path(ellipseIn: CGRect(x: 0.5, y: 0.5, width: size - 1, height: size - 1)), with: .color(Glass.rim), lineWidth: 1)
        }
    }
}

private struct BubbleMediaBurstBadge: View {
    var body: some View {
        Circle()
            .fill(Glass.badgeFill)
            .overlay(Circle().strokeBorder(.white, lineWidth: Glass.badgeBorder))
            .overlay {
                Canvas { context, _ in
                    var check = Path()
                    check.move(to: CGPoint(x: 7.5, y: 12.3))
                    check.addLine(to: CGPoint(x: 10.6, y: 15.3))
                    check.addLine(to: CGPoint(x: 16.6, y: 9.1))
                    context.stroke(check, with: .color(.white), style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
                }
            }
            .frame(width: Glass.badge, height: Glass.badge)
    }
}

/// One photo in a glass bubble, with its shadow; the ring and the check show how picked it is.
struct BubbleMediaBurstBubble: View {
    let item: BubbleMediaBurstItem
    let size: Double
    /// 0 to 1, a little over 1 while the check pops in.
    let pick: Double

    var body: some View {
        BubbleMediaBurstPhoto(item: item, size: size)
            .overlay {
                Circle()
                    .strokeBorder(.white, lineWidth: Glass.ring)
                    .opacity(min(1, max(0, pick)))
            }
            .overlay(alignment: .topLeading) {
                BubbleMediaBurstBadge()
                    .scaleEffect(max(0.001, pick))
                    .opacity(min(1, max(0, pick * 2)))
                    .offset(x: size / 2 + size * Glass.badgeAt - Glass.badge / 2, y: size / 2 - size * Glass.badgeAt - Glass.badge / 2)
            }
    }
}

/// The photo, its glass and its shadow; kept apart so a frame of motion does not rebuild the image.
private struct BubbleMediaBurstPhoto: View, Equatable {
    let item: BubbleMediaBurstItem
    let size: Double

    var body: some View {
        item.tint
            .overlay {
                AsyncImage(url: URL(string: item.photo), transaction: Transaction(animation: .easeOut(duration: 0.2))) { phase in
                    if let image = phase.image {
                        image.resizable().scaledToFill().transition(.opacity)
                    }
                }
            }
            .frame(width: size, height: size)
            .clipShape(Circle())
            .overlay { BubbleMediaBurstGlass(glow: item.glow) }
            .background {
                Circle()
                    .fill(item.tint)
                    .shadow(color: Glass.shadow, radius: Glass.shadowRadius, y: Glass.shadowY)
            }
    }
}
