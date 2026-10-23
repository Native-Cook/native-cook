import SwiftUI

/// The pin's lower half: shortcut rail and bento, drawn like the sibling Paddock block (same app): Montserrat labels
/// placed by baseline, SF Symbols, Paddock's tile faces. Copied, not imported, so each block stays self-contained.
enum PitStopReviewDestination: String, Identifiable {
    case pass, profile, calendar, reflex, customize, laps, pitcast, results, circuits
    var id: String { rawValue }
}

private typealias B = PitStopReviewTheme.Bento
private typealias Line = PitStopReviewLine
private typealias Mark = PitStopReviewMark

/// Tile press feedback: instant 0.97 scale, like the React Native tile.
private struct PitStopReviewTilePress: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.scaleEffect(configuration.isPressed ? 0.97 : 1)
    }
}

/// A bento tile: a button over its face, the face's children placed in the tile's own points.
private struct PitStopReviewTile<Face: View>: View {
    let left: Double, top: Double, width: Double, height: Double, corner: Double
    var fill: Color?
    let label: String
    let scale: CGFloat
    let action: () -> Void
    @ViewBuilder let face: () -> Face

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: corner * scale, style: .continuous)
        Button(action: action) {
            ZStack(alignment: .topLeading) { face() }
                .frame(width: width * scale, height: height * scale, alignment: .topLeading)
                .background { if let fill { shape.fill(fill) } }
                .contentShape(shape)
        }
        .buttonStyle(PitStopReviewTilePress())
        .accessibilityLabel(label)
        .offset(x: left * scale, y: top * scale)
    }
}

private struct PitStopReviewTileLabel: View {
    let eyebrow: String, title: String, x: Double
    let color: Color
    let scale: CGFloat
    var body: some View {
        Line(text: eyebrow, x: x, baseline: B.baselineEyebrow, size: B.eyebrow, face: .semibold, color: color, tracking: B.eyebrowTracking, scale: scale)
        Line(text: title, x: x, baseline: B.baselineTitle, size: B.title, face: .bold, color: color, tracking: B.titleTracking, scale: scale)
    }
}

struct PitStopReviewBento: View {
    let scale: CGFloat
    let cards: [Bool]
    let onOpen: (PitStopReviewDestination) -> Void

    var body: some View {
        let s = scale
        let T = B.tile, P = B.pitcast
        let mid = T.height / 2
        let rows = [0, 1, 2].filter { cards[$0] }
        let rowTop = { (i: Int) in B.top + Double(rows.firstIndex(of: i) ?? 0) * T.pitch }
        ZStack(alignment: .topLeading) {
            if cards[0] {
                PitStopReviewTile(left: B.left, top: rowTop(0), width: T.width, height: T.height, corner: T.radius, fill: B.violet, label: "every lap counts", scale: s, action: { onOpen(.laps) }) {
                    let badge = B.violetBadge
                    Capsule().strokeBorder(.white, lineWidth: badge.stroke * s)
                        .frame(width: badge.width * s, height: badge.height * s)
                        .overlay { Text("TN").font(PitStopReviewFont.font(.black, size: B.badge * s)).tracking(-0.4 * s).foregroundStyle(.white) }
                        .offset(x: (badge.centerX - badge.width / 2) * s, y: (mid - badge.height / 2) * s)
                    Line(text: "every lap", x: B.violetX, baseline: B.baselineViolet[0], size: B.violetSize, face: .extraBold, color: .white, tracking: B.violetTracking, scale: s)
                    Line(text: "counts", x: B.violetX, baseline: B.baselineViolet[1], size: B.violetSize, face: .extraBold, color: .white, tracking: B.violetTracking, scale: s)
                }
            }
            if cards[1] {
                PitStopReviewTile(left: B.left, top: rowTop(1), width: T.width, height: T.height, corner: T.radius, fill: B.orange, label: "Race day Reflex test", scale: s, action: { onOpen(.reflex) }) {
                    PitStopReviewTileLabel(eyebrow: "Race day", title: "Reflex test", x: B.tileInset, color: .white, scale: s)
                    let chip = B.orangeChip
                    RoundedRectangle(cornerRadius: chip.radius * s, style: .continuous).fill(.white)
                        .frame(width: chip.width * s, height: chip.height * s)
                        .overlay { Image(systemName: "gamecontroller.fill").font(.system(size: B.chipIcon * s, weight: .bold)).foregroundStyle(B.orange) }
                        .offset(x: (T.width - chip.trailing - chip.width) * s, y: (mid - chip.height / 2) * s)
                }
            }
            if cards[2] {
                PitStopReviewTile(left: B.left, top: rowTop(2), width: T.width, height: T.height, corner: T.radius, fill: B.yellow, label: "custom home cards", scale: s, action: { onOpen(.customize) }) {
                    Mark(symbol: "square.grid.2x2.fill", cx: B.tileMarkCenter, cy: mid, size: B.cardIcon, color: .black, weight: .bold, scale: s)
                    PitStopReviewTileLabel(eyebrow: "custom", title: "home cards", x: B.tileTextAfterMark, color: .black, scale: s)
                }
            }
            PitStopReviewTile(left: P.left, top: B.top, width: P.width, height: P.height, corner: P.radius, fill: B.cream,
                              label: "Read the weekend brief, Alex Vale and Sam Ives", scale: s, action: { onOpen(.pitcast) }) {
                RoundedRectangle(cornerRadius: P.micRadius * s, style: .continuous).fill(B.orange)
                    .frame(width: P.mic * s, height: P.mic * s)
                    .overlay { Image(systemName: "mic.fill").font(.system(size: B.pitcastIcon * s, weight: .semibold)).foregroundStyle(B.micInk) }
                    .offset(x: P.inset * s, y: P.inset * s)
                Circle().fill(.black).frame(width: P.arrow * s, height: P.arrow * s)
                    .overlay { Image(systemName: "arrow.up.right").font(.system(size: B.pitcastArrow * s, weight: .heavy)).foregroundStyle(.white) }
                    .offset(x: (P.width - P.arrowInset - P.arrow) * s, y: P.arrowInset * s)
                HStack(alignment: .firstTextBaseline, spacing: 0) {
                    Text("pit").font(PitStopReviewFont.font(.black, size: B.pitcastTitle * s)).tracking(-0.6 * s)
                    Text("cast").font(.system(size: B.pitcastTitle * s, weight: .semibold, design: .serif)).italic().tracking(-0.8 * s)
                }
                .foregroundStyle(.black).fixedSize()
                .alignmentGuide(.top) { $0[.firstTextBaseline] }
                .offset(x: P.inset * s, y: B.baselinePitcastTitle * s)
                Line(text: "Every Friday at 6pm", x: P.inset, baseline: B.baselinePitcastSubtitle, size: B.pitcastSubtitle, face: .medium, color: .black, scale: s)
                Line(text: "Alex Vale / Sam Ives", x: P.inset, baseline: B.baselinePitcastNames, size: B.pitcastNames, face: .medium, color: .black, opacity: 0.9, scale: s)
                ForEach(Array(["headphones.circle.fill", "play.rectangle.fill", "antenna.radiowaves.left.and.right"].enumerated()), id: \.offset) { i, symbol in
                    Mark(symbol: symbol, cx: B.glyphX[i], cy: P.glyphRow, size: P.glyph, color: .black, weight: .regular, scale: s)
                }
            }
        }
    }
}

/// The race stats card under the bento (Paddock's face, art left out: it sits under the dock).
struct PitStopReviewStatsCard: View {
    let scale: CGFloat
    let onOpen: (PitStopReviewDestination) -> Void

    var body: some View {
        let s = scale, S = B.stats
        PitStopReviewTile(left: B.left, top: B.statsTop, width: S.width, height: S.height, corner: S.radius, fill: nil, label: "Turn Nine stats", scale: s, action: { onOpen(.results) }) {
            LinearGradient(colors: [B.statsGlow, B.statsShade], startPoint: UnitPoint(x: 0.12, y: 0.85), endPoint: UnitPoint(x: 1, y: 0.15))
                .frame(width: S.width * s, height: S.height * s)
                .clipShape(RoundedRectangle(cornerRadius: S.radius * s, style: .continuous))
            Image(systemName: "doc.text.fill").font(.system(size: B.statsIconSize * s)).foregroundStyle(.white)
                .frame(width: B.statsIcon.width * s, height: B.statsIcon.height * s)
                .offset(x: B.statsIcon.x * s, y: B.statsIcon.y * s)
            Line(text: "Turn Nine", x: S.x, baseline: B.statsEyebrowBaseline, size: B.statsEyebrow, face: .bold, color: .white, scale: s)
            Line(text: "Stats", x: S.x, baseline: B.statsWordBaseline, size: B.statsWord, face: .bold, color: .white, scale: s)
            ForEach(0..<2, id: \.self) { i in
                Line(text: B.caption[i], x: B.captionX, baseline: B.captionBaselines[i], size: B.captionSize, face: .medium, color: .white, scale: s)
            }
        }
    }
}

/// "+ ● / ■ ×" shapes glyph of the pin's third rail icon, in a 24-unit box.
private struct PitStopReviewShapesIcon: View {
    let size: CGFloat
    var body: some View {
        Canvas { context, canvasSize in
            let k = canvasSize.width / 24
            let ink = GraphicsContext.Shading.color(PitStopReviewTheme.Colors.ink)
            let stroke = StrokeStyle(lineWidth: 2.6 * k, lineCap: .round)
            var plus = Path()
            plus.move(to: CGPoint(x: 5.5 * k, y: 1.5 * k)); plus.addLine(to: CGPoint(x: 5.5 * k, y: 9.5 * k))
            plus.move(to: CGPoint(x: 1.5 * k, y: 5.5 * k)); plus.addLine(to: CGPoint(x: 9.5 * k, y: 5.5 * k))
            context.stroke(plus, with: ink, style: stroke)
            context.fill(Path(ellipseIn: CGRect(x: (18.5 - 4.3) * k, y: (5.5 - 4.3) * k, width: 8.6 * k, height: 8.6 * k)), with: ink)
            context.fill(Path(roundedRect: CGRect(x: 1.3 * k, y: 14.3 * k, width: 8.4 * k, height: 8.4 * k), cornerRadius: 2.2 * k), with: ink)
            var cross = Path()
            cross.move(to: CGPoint(x: 15.2 * k, y: 15.2 * k)); cross.addLine(to: CGPoint(x: 21.8 * k, y: 21.8 * k))
            cross.move(to: CGPoint(x: 21.8 * k, y: 15.2 * k)); cross.addLine(to: CGPoint(x: 15.2 * k, y: 21.8 * k))
            context.stroke(cross, with: ink, style: stroke)
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

/// Circle with a four-point sparkle, the pin's fourth rail icon.
private struct PitStopReviewSparkleIcon: View {
    let size: CGFloat
    var body: some View {
        Canvas { context, canvasSize in
            let k = canvasSize.width / 24
            let ink = GraphicsContext.Shading.color(PitStopReviewTheme.Colors.ink)
            context.stroke(Path(ellipseIn: CGRect(x: (12 - 10.6) * k, y: (12 - 10.6) * k, width: 21.2 * k, height: 21.2 * k)), with: ink, lineWidth: 2 * k)
            var spark = Path()
            func p(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: x * k, y: y * k) }
            spark.move(to: p(12, 5.4))
            spark.addQuadCurve(to: p(18.6, 12), control: p(12.9, 11.1))
            spark.addQuadCurve(to: p(12, 18.6), control: p(12.9, 12.9))
            spark.addQuadCurve(to: p(5.4, 12), control: p(11.1, 12.9))
            spark.addQuadCurve(to: p(12, 5.4), control: p(11.1, 11.1))
            spark.closeSubpath()
            context.fill(spark, with: ink)
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

/// The shortcut rail: four buttons, glyphs and hairline dividers at the pin's centres.
struct PitStopReviewRail: View {
    typealias RL = PitStopReviewTheme.Rail
    let scale: CGFloat
    let width: CGFloat
    let onOpen: (PitStopReviewDestination) -> Void

    private static let actions: [(String, PitStopReviewDestination)] = [("Race weekends", .calendar), ("Reaction test", .reflex), ("Customize home cards", .customize), ("Club Pass", .pass)]

    var body: some View {
        let s = scale
        let left = (Double(width / s) - RL.width) / 2
        let ink = PitStopReviewTheme.Colors.ink
        ZStack(alignment: .topLeading) {
            ForEach(0..<4, id: \.self) { i in
                let cx = RL.iconX[i] - left
                Button { onOpen(Self.actions[i].1) } label: {
                    Group {
                        switch i {
                        case 0: Image(systemName: "calendar.badge.clock").font(.system(size: RL.calendar * s, weight: .medium)).foregroundStyle(ink)
                        case 1: Image(systemName: "gamecontroller").font(.system(size: RL.controller * s, weight: .medium)).foregroundStyle(ink)
                        case 2: PitStopReviewShapesIcon(size: RL.shapes * s)
                        default: PitStopReviewSparkleIcon(size: RL.sparkle * s)
                        }
                    }
                    .frame(width: 60 * s, height: RL.height * s)
                    .contentShape(Rectangle())
                    .offset(y: (RL.iconY - RL.height / 2) * s)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Self.actions[i].0)
                .offset(x: (cx - 30) * s)
            }
            ForEach(0..<3, id: \.self) { i in
                let x = (RL.iconX[i] + RL.iconX[i + 1]) / 2 - left
                Rectangle().fill(RL.divider).frame(width: 1, height: RL.dividerHeight * s)
                    .offset(x: x * s, y: (RL.height - RL.dividerHeight) / 2 * s).allowsHitTesting(false)
            }
        }
        .frame(width: RL.width * s, height: RL.height * s, alignment: .topLeading)
        .background(Capsule().fill(RL.fill))
        .overlay(Capsule().strokeBorder(RL.stroke, lineWidth: 1).allowsHitTesting(false))
        .offset(x: left * s, y: RL.top * s)
    }
}
