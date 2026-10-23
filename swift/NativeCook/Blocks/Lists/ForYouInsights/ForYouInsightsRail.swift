import SwiftUI

/// The context rail: four tiles in a horizontal scroll (the fourth runs off the edge, as in the pin).
struct ForYouInsightsRail: View, Equatable {
    typealias T = ForYouInsightsTheme
    typealias L = ForYouInsightsTheme.Layout
    var k: Double

    private enum Kind { case heart, sleep, ready, fitness }
    private let kinds: [Kind] = [.heart, .sleep, .ready, .fitness]
    private let titles = ["", "Sleep", "Readiness", "Fitness"]
    private let values = ["", "High", "Ready", "5 of 7"]

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: L.railGap * k) {
                ForEach(0..<4, id: \.self) { i in tile(i) }
            }
            .padding(.horizontal, L.railInset * k)
        }
        .frame(height: L.railHeight * k)
        .accessibilityLabel("Today's context")
    }

    @ViewBuilder private func tile(_ i: Int) -> some View {
        let shape = RoundedRectangle(cornerRadius: T.Radius.tile * k, style: .circular)
        let heart = kinds[i] == .heart
        ZStack(alignment: .topLeading) {
            shape.fill(heart ? T.Colors.white : T.Colors.tile)
            if !heart { shape.strokeBorder(T.Colors.tileBorder, lineWidth: 2.0 / 3.0) }
            if heart { heartTile } else {
                ring(i)
                    .frame(width: L.railRing * k, height: L.railRing * k)
                    .offset(x: L.railRingPad * k, y: (L.railHeight - L.railRing) / 2 * k)
                ForYouInsightsText(text: titles[i], style: T.Typo.railTitle, color: T.Colors.railTitle, k: k)
                    .lineLimit(1)
                    .offset(x: (L.railRingPad + L.railRing + L.railTextGap) * k, y: (L.railTitleTop - L.railTop) * k)
                ForYouInsightsText(text: values[i], style: T.Typo.railValue, color: T.Colors.ink, k: k)
                    .lineLimit(1)
                    .offset(x: (L.railRingPad + L.railRing + L.railTextGap) * k, y: (L.railValueTop - L.railTop) * k)
            }
        }
        .frame(width: L.tileWidths[i] * k, height: L.railHeight * k, alignment: .topLeading)
        .shadow(color: T.Colors.shadow.opacity(0.06), radius: 6 * k, y: 2 * k)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(heart ? "Heart health" : "\(titles[i]): \(values[i])")
    }

    private var heartTile: some View {
        ZStack(alignment: .topLeading) {
            Image(systemName: "heart.fill")
                .font(.system(size: L.heartSize * k))
                .foregroundStyle(T.Colors.heart)
                .frame(width: L.tileWidths[0] * k, height: L.heartSize * k)
                .offset(y: (L.heartTop - L.railTop) * k)
            ForEach(Array(L.heartLines.enumerated()), id: \.element.top) { _, line in
                RoundedRectangle(cornerRadius: k, style: .continuous)
                    .fill(T.Colors.heartLine)
                    .frame(width: line.width * k, height: 2 * k)
                    .offset(x: (L.tileWidths[0] - line.width) / 2 * k, y: (line.top - L.railTop) * k)
            }
        }
    }

    @ViewBuilder private func ring(_ i: Int) -> some View {
        switch kinds[i] {
        case .sleep: Canvas { ctx, size in Self.sleep(&ctx, size.width / 40) }
        case .ready: Canvas { ctx, size in Self.ready(&ctx, size.width / 40) }
        default: Canvas { ctx, size in Self.fitness(&ctx, size.width / 40) }
        }
    }

    /// An arc in a 40-unit view, angles in degrees clockwise from 12 o'clock.
    private static func arc(_ r: Double, _ from: Double, _ to: Double, _ s: Double) -> Path {
        var p = Path()
        let steps = max(2, Int(abs(to - from) / 2))
        for n in 0...steps {
            let a = (from + (to - from) * Double(n) / Double(steps) - 90) * .pi / 180
            let pt = CGPoint(x: (20 + r * cos(a)) * s, y: (20 + r * sin(a)) * s)
            if n == 0 { p.move(to: pt) } else { p.addLine(to: pt) }
        }
        return p
    }

    private static func sleep(_ ctx: inout GraphicsContext, _ s: Double) {
        let parts: [(Double, Double, Color)] = [(0.015, 0.2, T.Colors.sleepOrange), (0.215, 0.72, T.Colors.sleepBlue), (0.735, 1, T.Colors.sleepTeal)]
        for (a, b, c) in parts {
            ctx.stroke(arc(16.5, a * 360, b * 360, s), with: .color(c), style: StrokeStyle(lineWidth: 6.5 * s, lineCap: .butt))
        }
        let text = ctx.resolve(Text("84").font(.system(size: T.Typo.railNumber.size * (40 / L.railRing) * s, weight: .bold)).foregroundStyle(T.Colors.ink))
        ctx.draw(text, at: CGPoint(x: 20 * s, y: 18.9 * s), anchor: .center)
    }

    private static func ready(_ ctx: inout GraphicsContext, _ s: Double) {
        let start = -115.0, end = 115.0
        let value = start + (end - start) * 0.6
        let style = StrokeStyle(lineWidth: 4 * s, lineCap: .round)
        ctx.stroke(arc(16, start, end, s), with: .color(T.Colors.readyTrack), style: style)
        let gradient = GraphicsContext.Shading.linearGradient(
            Gradient(colors: [T.Colors.readyLight, T.Colors.readyStrong]),
            startPoint: CGPoint(x: 0, y: 40 * s), endPoint: CGPoint(x: 40 * s, y: 0))
        ctx.stroke(arc(16, start, value, s), with: gradient, style: style)
        let a = (value - 90) * .pi / 180
        let dot = CGPoint(x: (20 + 16 * cos(a)) * s, y: (20 + 16 * sin(a)) * s)
        ctx.fill(Path(ellipseIn: CGRect(x: dot.x - 3.4 * s, y: dot.y - 3.4 * s, width: 6.8 * s, height: 6.8 * s)), with: .color(T.Colors.readyStrong))
        let text = ctx.resolve(Text("7").font(.system(size: (T.Typo.railNumber.size + 0.5) * (40 / L.railRing) * s, weight: .bold)).foregroundStyle(T.Colors.readyStrong))
        ctx.draw(text, at: CGPoint(x: 20 * s, y: 22.6 * s), anchor: .center)
    }

    private static func fitness(_ ctx: inout GraphicsContext, _ s: Double) {
        let rings: [(Double, Double, Color)] = [(16.5, 0.86, T.Colors.fitMove), (11.5, 0.72, T.Colors.fitExercise), (6.5, 0.58, T.Colors.fitStand)]
        for (r, share, c) in rings {
            ctx.stroke(arc(r, 0, 360, s), with: .color(c.opacity(0.18)), style: StrokeStyle(lineWidth: 4.2 * s))
            ctx.stroke(arc(r, 0, 360 * share, s), with: .color(c), style: StrokeStyle(lineWidth: 4.2 * s, lineCap: .round))
        }
    }
}
