import SwiftUI
import UIKit

/// The resting run list. Its coordinates are measured against the unscaled 402 pt
/// stage used by the React Native source.
struct ReceiptPrinterGrid: View {
    let top: Double
    let pressed: Int
    let lift: Double
    let reducedMotion: Bool
    let onPress: (Int) -> Void
    let onLongPress: (Int) -> Void
    let onTouch: () -> Void

    private let t = ReceiptPrinterTokens.self

    var body: some View {
        ZStack(alignment: .topLeading) {
            ReceiptPrinterGridCapText(
                ReceiptPrinterTokens.COPY.title,
                cap: top + t.GRID.titleCap,
                size: t.TYPE.title,
                weight: t.FONT.heavy,
                color: t.COLOR.ink
            )
            .offset(x: t.GRID.gutter)
            .accessibilityAddTraits(.isHeader)

            ReceiptPrinterGridCapText(
                ReceiptPrinterTokens.COPY.subtitle,
                cap: top + t.GRID.subtitleCap,
                size: t.TYPE.subtitle,
                weight: t.FONT.regular,
                color: t.COLOR.inkSoft
            )
            .offset(x: t.GRID.gutter)

            Image(systemName: "info.circle")
                .font(.system(size: t.GRID.info.r * 2, weight: .semibold))
                .foregroundStyle(t.COLOR.ink)
                .frame(width: t.GRID.info.r * 2, height: t.GRID.info.r * 2)
                .offset(x: t.GRID.info.cx - t.GRID.info.r, y: top + t.GRID.info.cy - t.GRID.info.r)
                .accessibilityLabel("About")

            ForEach(Array(ReceiptPrinterTokens.PILLS.enumerated()), id: \.offset) { index, pill in
                Text(pill)
                    .font(ReceiptPrinterAssets.font(size: t.TYPE.pill, weight: t.FONT.heavy))
                    .foregroundStyle(t.COLOR.ink)
                    .frame(width: t.GRID.cardW, height: t.GRID.pillH)
                    .background(t.COLOR.card, in: RoundedRectangle(cornerRadius: t.GRID.radius, style: .circular))
                    .offset(x: column(index), y: top + t.GRID.pillTop)
            }

            ForEach(Array(ReceiptPrinterTokens.CARDS.enumerated()), id: \.offset) { index, card in
                ReceiptPrinterGridCard(
                    index: index,
                    card: card,
                    selectedLift: pressed == index ? lift : 0,
                    reducedMotion: reducedMotion,
                    onPress: onPress,
                    onLongPress: onLongPress,
                    onTouch: onTouch
                )
                .offset(
                    x: column(index % 3),
                    y: top + t.GRID.rowTop + Double(index / 3) * t.GRID.rowPitch
                )
            }
        }
        // Offsets do not participate in a ZStack's layout. Keep the whole grid
        // in its hit-test region, including the fourth and fifth card rows.
        .frame(
            width: 402,
            height: top + t.GRID.rowTop + 4 * t.GRID.rowPitch + t.GRID.cardH,
            alignment: .topLeading
        )
    }

    private func column(_ index: Int) -> Double {
        t.GRID.gutter + Double(index) * (t.GRID.cardW + t.GRID.gap)
    }
}

private struct ReceiptPrinterGridCard: View {
    let index: Int
    let card: ReceiptPrinterCard
    let selectedLift: Double
    let reducedMotion: Bool
    let onPress: (Int) -> Void
    let onLongPress: (Int) -> Void
    let onTouch: () -> Void

    @State private var hold = 0.0
    @State private var longPressCommitted = false

    private let t = ReceiptPrinterTokens.self

    private var liftScale: Double {
        1 + (ReceiptPrinterMotion.PRESS.cardLift - 1) * selectedLift
    }

    private var holdScale: Double {
        reducedMotion ? 1 : 1 - (1 - ReceiptPrinterMotion.POLISH.holdScale) * hold
    }

    var body: some View {
        Button {
            if longPressCommitted {
                longPressCommitted = false
            } else {
                onPress(index)
            }
        } label: {
            ZStack(alignment: .topLeading) {
                ReceiptPrinterGridCapText(
                    card.date[0],
                    cap: t.GRID.dateCap,
                    size: t.TYPE.cardDate,
                    weight: t.FONT.medium,
                    color: t.COLOR.date
                )
                .offset(x: t.GRID.dateInset)

                ReceiptPrinterGridCapText(
                    card.date[1],
                    cap: t.GRID.dateCap,
                    size: t.TYPE.cardDate,
                    weight: t.FONT.medium,
                    color: t.COLOR.date,
                    alignment: .trailing
                )
                .frame(width: t.GRID.cardW)
                .offset(x: -t.GRID.dateInset)

                ReceiptPrinterRoute(seed: card.seed)
                    .frame(width: ReceiptPrinterRouteArtwork.width, height: ReceiptPrinterRouteArtwork.height)
                    .offset(
                        x: (t.GRID.cardW - ReceiptPrinterRouteArtwork.width) / 2,
                        y: t.GRID.iconTop - 8
                    )

                ReceiptPrinterGridCapText(
                    card.km,
                    cap: t.GRID.cardH - t.GRID.kmCapFromBottom,
                    size: t.TYPE.cardKm,
                    weight: t.FONT.heavy,
                    color: t.COLOR.ink,
                    alignment: .center
                )
                .frame(width: t.GRID.cardW)
            }
            .frame(width: t.GRID.cardW, height: t.GRID.cardH, alignment: .topLeading)
            .background(selectedLift > 0.01 ? t.COLOR.cardPressed : t.COLOR.card, in: RoundedRectangle(cornerRadius: t.GRID.radius, style: .circular))
            .scaleEffect(liftScale * holdScale)
            .shadow(color: t.COLOR.shadow.opacity(0.12 * selectedLift), radius: 14, x: 0, y: 6)
        }
        .buttonStyle(.plain)
        .contentShape(RoundedRectangle(cornerRadius: t.GRID.radius, style: .circular))
        .simultaneousGesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in onTouch() }
        )
        .onLongPressGesture(
            minimumDuration: ReceiptPrinterMotion.PRESS.longPressMs / 1_000,
            maximumDistance: 10,
            pressing: { pressing in
                onTouch()
                if pressing { longPressCommitted = false }
                guard !reducedMotion else { return }
                withAnimation(pressing ? .timingCurve(0.23, 1, 0.32, 1, duration: ReceiptPrinterMotion.PRESS.longPressMs / 1_000) : .spring(duration: ReceiptPrinterMotion.POLISH.releaseSpring.duration / 1_000, bounce: 0.2)) {
                    hold = pressing ? 1 : 0
                }
            },
            perform: {
                longPressCommitted = true
                // RN's duration spring has dampingRatio .8. SwiftUI's duration
                // solver uses a 0.2 bounce for the same slight release overshoot.
                withAnimation(.spring(duration: ReceiptPrinterMotion.POLISH.releaseSpring.duration / 1_000, bounce: 0.2)) {
                    hold = 0
                }
                onLongPress(index)
            }
        )
        .accessibilityLabel("Run \(card.date.joined(separator: " ")), \(card.km). Opens its receipt.")
        .accessibilityHint("Double tap to open. Touch and hold for receipt options.")
    }
}

/// Places a single-line text item from the measured top of its capital glyphs.
private struct ReceiptPrinterGridCapText: View {
    let value: String
    let cap: Double
    let size: Double
    let weight: String
    let color: Color
    let alignment: Alignment

    init(_ value: String, cap: Double, size: Double, weight: String, color: Color, alignment: Alignment = .leading) {
        self.value = value
        self.cap = cap
        self.size = size
        self.weight = weight
        self.color = color
        self.alignment = alignment
    }

    var body: some View {
        Text(value)
            .font(ReceiptPrinterAssets.font(size: size, weight: weight))
            .foregroundStyle(color)
            .lineLimit(1)
            .fixedSize(horizontal: true, vertical: true)
            .offset(y: cap - ReceiptPrinterGridCapText.topToCapital(size: size, weight: weight))
            .frame(maxWidth: .infinity, alignment: alignment)
    }

    private static func topToCapital(size: Double, weight: String) -> Double {
        let font = ReceiptPrinterAssets.uiFont(size: size, weight: weight)
        return Double(font.ascender - font.capHeight)
    }
}

private struct ReceiptPrinterRoute: View {
    let seed: Int

    var body: some View {
        Image(uiImage: ReceiptPrinterRouteArtwork.image(seed: seed))
            .resizable()
            .interpolation(.high)
    }
}

/// The grid is static while visible. Render its fifteen route traces once rather
/// than rebuilding sixty-four-point paths during the grid fade and card morph.
@MainActor
private enum ReceiptPrinterRouteArtwork {
    static let width = 78.0
    static let height = 44.0

    private struct Geometry {
        let points: [CGPoint]
        let start: CGPoint
    }

    private static let images: [Int: UIImage] = Dictionary(
        uniqueKeysWithValues: Set(ReceiptPrinterTokens.CARDS.map(\.seed)).map { seed in
            (seed, render(seed: seed))
        }
    )

    static func image(seed: Int) -> UIImage {
        images[seed] ?? render(seed: seed)
    }

    private static func render(seed: Int) -> UIImage {
        let route = geometry(seed: seed)
        let format = UIGraphicsImageRendererFormat()
        format.opaque = false
        format.scale = 3
        return UIGraphicsImageRenderer(
            size: CGSize(width: width, height: height),
            format: format
        ).image { renderer in
            let context = renderer.cgContext
            context.setStrokeColor(UIColor(red: 60 / 255, green: 60 / 255, blue: 60 / 255, alpha: 1).cgColor)
            context.setLineWidth(1.6)
            context.setLineJoin(.round)
            context.setLineCap(.round)
            context.beginPath()
            guard let first = route.points.first else { return }
            context.move(to: first)
            route.points.dropFirst().forEach(context.addLine(to:))
            context.closePath()
            context.strokePath()

            context.setFillColor(UIColor(red: 126 / 255, green: 224 / 255, blue: 110 / 255, alpha: 1).cgColor)
            context.setStrokeColor(UIColor(red: 60 / 255, green: 60 / 255, blue: 60 / 255, alpha: 1).cgColor)
            context.setLineWidth(1)
            context.addEllipse(in: CGRect(x: route.start.x - 2.6, y: route.start.y - 2.6, width: 5.2, height: 5.2))
            context.drawPath(using: .fillStroke)
        }
    }

    private static func geometry(seed: Int) -> Geometry {
        func random(_ index: Int) -> Double {
            let value = sin(Double(seed) * 91.7 + Double(index) * 12.9898) * 43_758.5453
            return value - floor(value)
        }

        let a2 = 0.1 + random(1) * 0.18
        let a3 = random(2) * 0.12
        let b2 = 0.08 + random(3) * 0.22
        let p2 = random(4) * 6.28
        let p3 = random(5) * 6.28
        let q2 = random(6) * 6.28
        let raw = (0..<64).map { index -> CGPoint in
            let angle = Double(index) / 64 * .pi * 2
            return CGPoint(
                x: cos(angle) + a2 * cos(2 * angle + p2) + a3 * cos(3 * angle + p3),
                y: sin(angle) + b2 * sin(2 * angle + q2) + a3 * 0.6 * sin(3 * angle + p3)
            )
        }
        let minX = raw.map(\.x).min() ?? 0
        let maxX = raw.map(\.x).max() ?? 1
        let minY = raw.map(\.y).min() ?? 0
        let maxY = raw.map(\.y).max() ?? 1
        let scale = min((width - 6) / (maxX - minX), (height - 6) / (maxY - minY))
        let offsetX = (width - (maxX - minX) * scale) / 2 - minX * scale
        let offsetY = (height - (maxY - minY) * scale) / 2 - minY * scale
        let points = raw.map { CGPoint(x: offsetX + $0.x * scale, y: offsetY + $0.y * scale) }
        let startIndex = min(63, Int(floor(random(7) * 64)))
        return Geometry(points: points, start: points[startIndex])
    }
}
