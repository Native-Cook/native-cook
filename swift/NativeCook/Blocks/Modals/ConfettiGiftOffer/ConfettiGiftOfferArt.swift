import SwiftUI
import UIKit

/// Miso, the original raccoon (art.tsx `Mascot`): a square picture shown "contain" in a square box.
struct ConfettiGiftOfferMascot: View {
    let pose: ConfettiGiftOfferAssets.Pose
    var size: CGFloat = 144

    var body: some View {
        Group {
            if let image = ConfettiGiftOfferAssets.image(pose) {
                Image(uiImage: image)
                    .resizable()
                    .interpolation(.high)
                    .aspectRatio(contentMode: .fit)
            } else {
                Color.clear
            }
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

/// The gift of art.tsx: an upright checked box with a heavy blue outline, its lid knocked askew by a striped
/// tail poking out, and a two-loop bow on top. Drawn on a 270 x 310 pt board; `open` is 0 (shut) to 1 (lid gone).
struct ConfettiGiftOfferGift: View {
    var open: Double

    private static let board = CGSize(width: 270, height: 310)

    var body: some View {
        // Two rocks build anticipation (art.tsx:21 `shake`).
        let wave = open >= 1 ? 0 : sin(min(open / 0.72, 1) * .pi * 4) * (0.6 + open * 0.6)
        // The box squats, then the lid kicks free just before the cut (`squat`).
        let squat = open < 0.6 ? 0 : open < 0.82 ? (open - 0.6) / 0.22 : 1 - (open - 0.82) / 0.18
        // `pop`: the lid flies up and tips over its left edge.
        let lift = max(0, (open - 0.78) / 0.22)
        let eased = 1 - pow(1 - min(1, lift), 3)
        ZStack(alignment: .topLeading) {
            ConfettiGiftOfferGiftBase()
                .scaleEffect(x: 1 + 0.04 * squat, y: 1 - 0.06 * squat, anchor: .bottom)
                .offset(y: 6 * squat)
            ConfettiGiftOfferGiftLid()
                // transformOrigin '1% 52%' of the 270 x 310 board.
                .rotationEffect(.degrees(-12 * eased), anchor: UnitPoint(x: 0.01, y: 0.52))
                .offset(y: -44 * eased)
        }
        .frame(width: Self.board.width, height: Self.board.height, alignment: .topLeading)
        .rotationEffect(.degrees(wave * 4))
        .offset(x: wave * 3.4)
        .accessibilityHidden(true)
    }
}

/// The checks both the box and the lid are filled with: 40 pt cells, teal with two pale squares.
private func confettiGiftOfferDrawChecks(_ context: inout GraphicsContext, clip: Path) {
    context.clip(to: clip)
    let bounds = clip.boundingRect
    let first = (bounds.minX / 40).rounded(.down) * 40
    let top = (bounds.minY / 40).rounded(.down) * 40
    context.fill(Path(bounds), with: .color(ConfettiGiftOfferPalette.hex(0xA6E7DD)))
    var y = top
    while y < bounds.maxY {
        var x = first
        while x < bounds.maxX {
            let pale = ConfettiGiftOfferPalette.hex(0xF3FBFA)
            context.fill(Path(CGRect(x: x, y: y, width: 20, height: 20)), with: .color(pale))
            context.fill(Path(CGRect(x: x + 20, y: y + 20, width: 20, height: 20)), with: .color(pale))
            x += 40
        }
        y += 40
    }
}

/// The box under the lid, with the tail behind it (art.tsx:23-31).
private struct ConfettiGiftOfferGiftBase: View {
    var body: some View {
        ZStack(alignment: .topLeading) {
            if let tail = ConfettiGiftOfferAssets.image(.tail) {
                Image(uiImage: tail)
                    .resizable()
                    .interpolation(.high)
                    .aspectRatio(contentMode: .fit)
                    .frame(width: 190, height: 190)
                    .offset(x: 100, y: 4)
            }
            Canvas { context, _ in
                let blue = ConfettiGiftOfferPalette.blue
                context.fill(
                    Path(roundedRect: CGRect(x: 8, y: 300, width: 240, height: 5), cornerRadius: 2.5),
                    with: .color(ConfettiGiftOfferPalette.hex(0xCDD0E6))
                )
                let box = Path(roundedRect: CGRect(x: 5, y: 160, width: 243, height: 140), cornerRadius: 14)
                var filled = context
                confettiGiftOfferDrawChecks(&filled, clip: box)
                context.stroke(box, with: .color(blue), lineWidth: 5)
                context.fill(Path(CGRect(x: 108, y: 162.5, width: 34, height: 135)), with: .color(blue))
            }
            .frame(width: 270, height: 310)
        }
        .frame(width: 270, height: 310, alignment: .topLeading)
    }
}

/// The lid, its bow and its knot (art.tsx:33-47).
private struct ConfettiGiftOfferGiftLid: View {
    private static let bowLeft = ConfettiGiftOfferSVG.path("M114 96C74 104 30 76 38 38 46 2 96 6 108 46c4 14 6 32 6 50Z")
    private static let bowRight = ConfettiGiftOfferSVG.path("M114 96c2-30 12-62 40-74 30-12 56 16 40 42-14 22-44 32-80 32Z")
    private static let folds = ConfettiGiftOfferSVG.path("M62 34c18 10 34 32 44 54M176 36c-18 6-38 26-54 52")

    var body: some View {
        Canvas { context, _ in
            let blue = ConfettiGiftOfferPalette.blue
            // The bow: two fat lobes meeting at a knot, in the reference's flat blue.
            context.fill(Self.bowLeft, with: .color(blue))
            context.fill(Self.bowRight, with: .color(blue))
            context.stroke(
                Self.folds,
                with: .color(ConfettiGiftOfferPalette.hex(0x2F6AD0)),
                style: StrokeStyle(lineWidth: 5, lineCap: .round)
            )
            // The lid itself, turned -13 degrees about (4, 162).
            var tilted = context
            tilted.translateBy(x: 4, y: 162)
            tilted.rotate(by: .degrees(-13))
            tilted.translateBy(x: -4, y: -162)
            let lid = Path(roundedRect: CGRect(x: 2, y: 107, width: 146, height: 55), cornerRadius: 10)
            var checks = tilted
            confettiGiftOfferDrawChecks(&checks, clip: lid)
            tilted.stroke(lid, with: .color(blue), lineWidth: 5)
            tilted.fill(Path(CGRect(x: 86, y: 109.5, width: 30, height: 50)), with: .color(blue))
            context.fill(Path(ellipseIn: CGRect(x: 99, y: 82, width: 30, height: 24)), with: .color(blue))
        }
        .frame(width: 270, height: 310)
    }
}

/// Three marks drift and twinkle around the account mascot: a teal dot, a blue star and a teal star
/// (ConfettiGiftOffer.tsx:91-101). `time` is the loop clock.
struct ConfettiGiftOfferSparkles: View {
    var time: Double
    var reduced: Bool

    private static let star = ConfettiGiftOfferSVG.path("M10 0c.8 6.2 3.3 8.9 10 10-6.7 1.1-9.2 3.8-10 10-.8-6.2-3.3-8.9-10-10 6.7-1.1 9.2-3.8 10-10Z")

    var body: some View {
        let dot = reduced ? 1 : 0.55 + 0.45 * sin(time * 3.1)
        let dotLift = reduced ? 0 : sin(time * 1.7) * 4
        let blueStar = reduced ? 1 : 0.5 + 0.5 * sin(time * 2.6 + 1.2)
        let blueScale = reduced ? 1 : 0.85 + 0.2 * sin(time * 2.6 + 1.2)
        let tealStar = reduced ? 1 : 0.6 + 0.4 * sin(time * 2.2 + 2.4)
        let tealScale = reduced ? 1 : 0.9 + 0.15 * sin(time * 2.2 + 2.4)
        ZStack(alignment: .topLeading) {
            Circle()
                .fill(ConfettiGiftOfferPalette.hex(0x8ED9CF))
                .frame(width: 12, height: 12)
                .opacity(dot)
                .offset(x: 65, y: -33 + dotLift)
            star(size: 18, color: ConfettiGiftOfferPalette.hex(0x3D6FE0))
                .opacity(blueStar)
                .scaleEffect(blueScale)
                .offset(x: 42, y: 6)
            GeometryReader { box in
                star(size: 26, color: ConfettiGiftOfferPalette.hex(0x8ED9CF))
                    .opacity(tealStar)
                    .scaleEffect(tealScale)
                    .offset(x: box.size.width - 62 - 26, y: 12)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private func star(size: CGFloat, color: Color) -> some View {
        ConfettiGiftOfferVectorIcon(path: Self.star, viewBox: CGSize(width: 20, height: 20), color: color)
            .frame(width: size, height: size)
    }
}

/// A filled SVG path scaled from its viewBox to the frame it is given.
struct ConfettiGiftOfferVectorIcon: View {
    let path: Path
    let viewBox: CGSize
    let color: Color

    var body: some View {
        Canvas { context, size in
            context.scaleBy(x: size.width / viewBox.width, y: size.height / viewBox.height)
            context.fill(path, with: .color(color))
        }
    }
}

/// The Apple mark of the first sign-in button (17 x 20 viewBox, white).
struct ConfettiGiftOfferAppleLogo: View {
    private static let mark = ConfettiGiftOfferSVG.path(
        "M14.1 10.6c0-2.6 2.1-3.8 2.2-3.9-1.2-1.8-3.1-2-3.7-2-1.6-.2-3.1.9-3.9.9-.8 0-2-.9-3.4-.9C3.6 4.8 1.9 5.8 1 7.4c-1.9 3.3-.5 8.1 1.3 10.8.9 1.3 1.9 2.7 3.3 2.7 1.3-.1 1.8-.9 3.4-.9 1.6 0 2 .9 3.4.8 1.4 0 2.3-1.3 3.2-2.6 1-1.5 1.4-2.9 1.4-3-.1 0-2.9-1.1-2.9-4.6ZM11.6 3c.7-.9 1.2-2 1.1-3.2-1 0-2.3.7-3 1.6-.7.8-1.2 2-1.1 3.1 1.1.1 2.3-.6 3-1.5Z"
    )

    var body: some View {
        ConfettiGiftOfferVectorIcon(path: Self.mark, viewBox: CGSize(width: 17, height: 20), color: .white)
            .frame(width: 17, height: 20)
            .accessibilityHidden(true)
    }
}

/// The four-colour mark of the second sign-in button (18 x 18 viewBox).
struct ConfettiGiftOfferGoogleLogo: View {
    private static let parts: [(Path, Color)] = [
        (ConfettiGiftOfferSVG.path("M17.6 9.2c0-.6-.1-1.2-.2-1.8H9v3.4h4.8a4.1 4.1 0 0 1-1.8 2.7v2.2h2.9c1.7-1.6 2.7-3.9 2.7-6.5Z"), ConfettiGiftOfferPalette.hex(0x4285F4)),
        (ConfettiGiftOfferSVG.path("M9 18c2.4 0 4.5-.8 5.9-2.2L12 13.6c-.8.5-1.8.9-3 .9-2.3 0-4.3-1.6-5-3.7h-3v2.3A9 9 0 0 0 9 18Z"), ConfettiGiftOfferPalette.hex(0x34A853)),
        (ConfettiGiftOfferSVG.path("M4 10.8a5.4 5.4 0 0 1 0-3.5V5H1a9 9 0 0 0 0 8.1l3-2.3Z"), ConfettiGiftOfferPalette.hex(0xFBBC05)),
        (ConfettiGiftOfferSVG.path("M9 3.6c1.3 0 2.5.5 3.4 1.3L15 2.4A9 9 0 0 0 1 5l3 2.3c.7-2.1 2.7-3.7 5-3.7Z"), ConfettiGiftOfferPalette.hex(0xEA4335)),
    ]

    var body: some View {
        Canvas { context, _ in
            for part in Self.parts {
                context.fill(part.0, with: .color(part.1))
            }
        }
        .frame(width: 18, height: 18)
        .accessibilityHidden(true)
    }
}

/// The close mark of the offer's chip (ConfettiGiftOffer.tsx:86): two 2.2 pt round strokes on a 20 pt box.
struct ConfettiGiftOfferCloseIcon: View {
    private static let cross = ConfettiGiftOfferSVG.path("m4.5 4.5 11 11M15.5 4.5l-11 11")

    var body: some View {
        Canvas { context, _ in
            context.stroke(
                Self.cross,
                with: .color(ConfettiGiftOfferPalette.grey),
                style: StrokeStyle(lineWidth: 2.2, lineCap: .round)
            )
        }
        .frame(width: 20, height: 20)
        .accessibilityHidden(true)
    }
}
