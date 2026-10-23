import SwiftUI
import UIKit

struct MemoCardFanMemo {
    let title: String
    let subtitle: String
    let count: String
    let unit: String
    let notes: String
    let label: String
    let photos: [UIImage]

    static let sample = MemoCardFanMemo(
        title: "Daily Memo",
        subtitle: "Notes & Journaling",
        count: "09",
        unit: "Doc",
        notes: "11th Notes",
        label: "Daily Task\nCard Design",
        photos: [
            MemoCardFanImages.profile,
            MemoCardFanImages.shades,
            MemoCardFanImages.up,
        ]
    )
}

struct MemoCardFanFolder: View {
    let memo: MemoCardFanMemo
    let open: Double
    let press: Double
    let pull: Double
    let spread: Double
    let reduceMotion: Bool

    private let scale = MemoCardFanLayout.scale
    private var shownOpen: Double { reduceMotion ? (open < 0.5 ? 0 : 1) : open }

    var body: some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: MemoCardFanLayout.cardRadius * scale, style: .continuous)
                .fill(MemoCardFanColor.folder)
                .shadow(color: .black.opacity(0.72), radius: 10, x: 0, y: 7)

            ForEach([2, 1, 0], id: \.self) { index in
                MemoCardFanPrint(
                    image: memo.photos[index],
                    index: index,
                    open: shownOpen,
                    pull: pull,
                    spread: spread,
                    overPocket: false
                )
            }

            Text(memo.label)
                .font(.custom(MemoCardFanFonts.regular, fixedSize: 19.9 * scale))
                .lineSpacing((23 - 19.9) * scale)
                .foregroundStyle(MemoCardFanColor.ink)
                .multilineTextAlignment(.leading)
                .fixedSize()
                .frame(
                    width: (MemoCardFanLayout.cardWidth - 38.5) * scale,
                    alignment: .trailing
                )
                .offset(y: (46 - 19.9 * 0.62) * scale)
                .opacity(1 - min(1, shownOpen * 3))

            MemoCardFanPocket(open: shownOpen)
                .fill(MemoCardFanColor.folder)
                .frame(
                    width: MemoCardFanLayout.cardWidth * scale,
                    height: MemoCardFanLayout.cardHeight * scale,
                    alignment: .topLeading
                )

            MemoCardFanPrint(
                image: memo.photos[0],
                index: 0,
                open: shownOpen,
                pull: pull,
                spread: spread,
                overPocket: true
            )

            folderType
        }
        .frame(
            width: MemoCardFanLayout.cardWidth * scale,
            height: MemoCardFanLayout.cardHeight * scale,
            alignment: .topLeading
        )
        .scaleEffect(1 - (1 - MemoCardFanMotion.pressScale) * press)
        .opacity(reduceMotion ? 1 - sin(.pi * open) : 1)
    }

    private var folderType: some View {
        let typeScale = 1 + (1.05 - 1) * shownOpen

        return ZStack(alignment: .topLeading) {
            Text(memo.title)
                .font(.custom(MemoCardFanFonts.semibold, fixedSize: 20 * scale))
                .foregroundStyle(MemoCardFanColor.ink)
                .fixedSize()
                .scaleEffect(typeScale, anchor: .topLeading)
                .offset(
                    x: (18 - 88 * shownOpen) * scale,
                    y: (179 - 20 * 0.62 + 94 * shownOpen) * scale
                )

            Text(memo.subtitle)
                .font(.custom(MemoCardFanFonts.regular, fixedSize: 19 * scale))
                .tracking(0.2)
                .foregroundStyle(MemoCardFanColor.mute)
                .fixedSize()
                .scaleEffect(typeScale, anchor: .topLeading)
                .offset(
                    x: (18 - 68 * shownOpen) * scale,
                    y: (210 - 19 * 0.62 + 94 * shownOpen) * scale
                )

            HStack(alignment: .firstTextBaseline, spacing: 7 * scale) {
                Text(memo.count)
                    .font(.custom(MemoCardFanFonts.regular, fixedSize: 44 * scale))
                    .tracking(-0.5)
                    .monospacedDigit()
                    .foregroundStyle(MemoCardFanColor.ink)
                Text(memo.unit)
                    .font(.custom(MemoCardFanFonts.regular, fixedSize: 19 * scale))
                    .foregroundStyle(MemoCardFanColor.mute)
                    .offset(y: -9.5 * scale)
            }
            .fixedSize()
            .scaleEffect(typeScale, anchor: .topLeading)
            .offset(
                x: (25 - 10 * shownOpen) * scale,
                y: (390 - 44 * 0.62 + 7 * shownOpen) * scale
            )

            Text(memo.notes)
                .font(.custom(MemoCardFanFonts.semibold, fixedSize: 19 * scale))
                .foregroundStyle(MemoCardFanColor.ink)
                .fixedSize()
                .scaleEffect(typeScale, anchor: .topTrailing)
                .frame(
                    width: (MemoCardFanLayout.cardWidth - 51) * scale,
                    alignment: .trailing
                )
                .offset(
                    x: 27 * shownOpen * scale,
                    y: (390 - 19 * 0.62 + 5 * shownOpen) * scale
                )
        }
        .frame(
            width: MemoCardFanLayout.cardWidth * scale,
            height: MemoCardFanLayout.cardHeight * scale,
            alignment: .topLeading
        )
    }
}

private struct MemoCardFanPrint: View {
    let image: UIImage
    let index: Int
    let open: Double
    let pull: Double
    let spread: Double
    let overPocket: Bool

    private let scale = MemoCardFanLayout.scale

    var body: some View {
        let destinations: [(x: Double, y: Double, rotation: Double)] = [
            (189.5, 178, 0),
            (23, 202, -22.5),
            (381, 202, 22.5),
        ]
        let destination = destinations[index]
        let lag = index == 0 ? 0 : MemoCardFanMotion.stagger
        let progress = min(1, max(0, (open - lag) / (1 - lag)))
        let side = index == 1 ? -1.0 : index == 2 ? 1.0 : 0.0
        let cardPull = index == 0 ? pull : 0
        let lifted = max(0, cardPull)
        let x = 217.5 + (destination.x - 217.5) * progress + side * 14 * spread
        let y = 217 + (destination.y - 217) * progress
            + side * side * -12 * spread
            - MemoCardFanLayout.pullRise * cardPull
        let closedScale = MemoCardFanLayout.cardWidth / MemoCardFanLayout.photoWidth
        let cardScale = closedScale + (1 - closedScale) * progress
        let rotation = destination.rotation * progress + side * 10 * spread - 4 * lifted
        let visible = index != 0 || ((cardPull > 0.9) == overPocket)

        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: MemoCardFanLayout.photoRadius * scale, style: .continuous)
                .fill(MemoCardFanColor.frame)
                .opacity(min(1, open * 2.5))

            photo(fillProgress: min(1, open * 2.5), lifted: lifted)
        }
        .frame(
            width: MemoCardFanLayout.photoWidth * scale,
            height: MemoCardFanLayout.photoHeight * scale,
            alignment: .topLeading
        )
        .shadow(
            color: .black.opacity(0.55 * min(1, lifted)),
            radius: 6 + (22 - 6) * lifted,
            x: 0,
            y: 4 + (24 - 4) * lifted
        )
        .scaleEffect(cardScale * (1 + 0.1 * lifted))
        .rotationEffect(.degrees(rotation))
        .position(x: x * scale, y: y * scale)
        .opacity(visible ? 1 : 0)
    }

    private func photo(fillProgress: Double, lifted: Double) -> some View {
        let frame = MemoCardFanLayout.photoFrame * scale
        let fill = MemoCardFanLayout.photoWidth
            / (MemoCardFanLayout.photoWidth - MemoCardFanLayout.photoFrame * 2)
        let photoScale = fill + (1 - fill) * fillProgress
        let sideShade = index == 0 ? 0 : 0.3 * min(1, max(0, pull))
        let sheenProgress = min(1, lifted)

        return ZStack(alignment: .topLeading) {
            Image(uiImage: image)
                .resizable()
                .scaledToFill()
                .frame(
                    width: (MemoCardFanLayout.photoWidth - MemoCardFanLayout.photoFrame * 2) * scale,
                    height: (MemoCardFanLayout.photoHeight - MemoCardFanLayout.photoFrame * 2) * scale
                )
                .clipped()

            Color.black.opacity(sideShade)

            LinearGradient(
                colors: [.white.opacity(0), .white.opacity(0.9), .white.opacity(0)],
                startPoint: .leading,
                endPoint: .trailing
            )
            .frame(
                width: MemoCardFanLayout.photoWidth * 0.35 * scale,
                height: MemoCardFanLayout.photoHeight * 1.6 * scale
            )
            .rotationEffect(.degrees(20))
            .offset(
                x: (-MemoCardFanLayout.photoWidth * 0.7
                    + MemoCardFanLayout.photoWidth * 1.4 * sheenProgress) * scale,
                y: -MemoCardFanLayout.photoHeight * 0.3 * scale
            )
            .opacity(0.28 * sin(.pi * sheenProgress))
        }
        .frame(
            width: (MemoCardFanLayout.photoWidth - MemoCardFanLayout.photoFrame * 2) * scale,
            height: (MemoCardFanLayout.photoHeight - MemoCardFanLayout.photoFrame * 2) * scale,
            alignment: .topLeading
        )
        .clipShape(RoundedRectangle(
            cornerRadius: (MemoCardFanLayout.photoRadius - MemoCardFanLayout.photoFrame) * scale,
            style: .continuous
        ))
        .scaleEffect(photoScale)
        .offset(x: frame, y: frame)
    }
}

private struct MemoCardFanPocket: Shape {
    var open: Double

    var animatableData: Double {
        get { open }
        set { open = newValue }
    }

    func path(in rect: CGRect) -> Path {
        let scale = MemoCardFanLayout.scale

        func point(_ x: Double, _ y: Double, _ openX: Double, _ openY: Double) -> CGPoint {
            return CGPoint(
                x: (x + (openX - x) * open) * scale,
                y: (y + (openY - y) * open) * scale
            )
        }

        var path = Path()
        path.move(to: point(0, 173, -101, 257))
        path.addQuadCurve(to: point(18, 155, -85, 240), control: point(0, 155, -106, 240))
        path.addLine(to: point(150, 155, 158, 240))
        path.addCurve(
            to: point(221, 202, 226, 274),
            control1: point(172, 155, 183, 240),
            control2: point(176, 202, 185, 274)
        )
        path.addLine(to: point(413, 202, 508, 275))
        path.addQuadCurve(to: point(435, 224, 523, 310), control: point(435, 202, 535, 276))
        path.addLine(to: point(435, 394, 439, 418))
        path.addQuadCurve(to: point(395, 434, 415, 442), control: point(435, 434, 425, 442))
        path.addLine(to: point(40, 434, 24, 442))
        path.addQuadCurve(to: point(0, 394, -8, 420), control: point(0, 434, 6, 442))
        path.addCurve(
            to: point(0, 173, -101, 257),
            control1: point(0, 320.3, -33.7, 365.7),
            control2: point(0, 246.7, -82.1, 311.3)
        )
        path.closeSubpath()
        return path
    }
}

private enum MemoCardFanImages {
    static let profile = load("MemoCardFan-photo-profile")
    static let shades = load("MemoCardFan-photo-shades-v2")
    static let up = load("MemoCardFan-photo-up-v4")

    private static func load(_ name: String) -> UIImage {
        guard let image = UIImage(named: "\(name).jpg") else {
            assertionFailure("Missing MemoCardFan image: \(name)")
            return UIImage()
        }
        return image.preparingForDisplay() ?? image
    }
}
