import SwiftUI

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

                ReceiptPrinterTreadmill()
                    .frame(width: t.GRID.iconW, height: t.GRID.iconH)
                    .offset(x: (t.GRID.cardW - t.GRID.iconW) / 2, y: t.GRID.iconTop)

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

/// The source's white, ink-outlined three-quarter treadmill, kept as vector art.
private struct ReceiptPrinterTreadmill: View {
    private let t = ReceiptPrinterTokens.self

    var body: some View {
        Canvas { context, _ in
            context.fill(Path(ellipseIn: CGRect(x: 6, y: 30.6, width: 4.2, height: 2)), with: .color(t.COLOR.icon))

            let bars: [(CGPoint, CGPoint, CGFloat)] = [
                (CGPoint(x: 2.8, y: 28.4), CGPoint(x: 42.6, y: 25.6), 5.4),
                (CGPoint(x: 34.6, y: 21.8), CGPoint(x: 43.4, y: 21.2), 5),
                (CGPoint(x: 30.6, y: 30.4), CGPoint(x: 28.2, y: 9.8), 4),
                (CGPoint(x: 7.6, y: 9.8), CGPoint(x: 39.2, y: 7.4), 4.2),
            ]
            for (from, to, width) in bars {
                var path = Path()
                path.move(to: from)
                path.addLine(to: to)
                context.stroke(path, with: .color(t.COLOR.icon), style: StrokeStyle(lineWidth: width + 1.3, lineCap: .round, lineJoin: .round))
                context.stroke(path, with: .color(t.COLOR.paper), style: StrokeStyle(lineWidth: width - 1.3, lineCap: .round, lineJoin: .round))
            }

            var console = Path()
            console.move(to: CGPoint(x: 33.8, y: 0.6))
            console.addLine(to: CGPoint(x: 38.6, y: 6.2))
            console.addLine(to: CGPoint(x: 30.4, y: 6.8))
            console.closeSubpath()
            context.fill(console, with: .color(t.COLOR.paper))
            context.stroke(console, with: .color(t.COLOR.icon), lineWidth: 1.3)
        }
        .drawingGroup()
    }
}
