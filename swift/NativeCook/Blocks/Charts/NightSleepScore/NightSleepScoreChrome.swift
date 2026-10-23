import SwiftUI

enum NightSleepScoreTab: Int, CaseIterable {
    case insights, longevity, browse
    var title: String {
        switch self {
        case .insights: "Insights"
        case .longevity: "Longevity"
        case .browse: "Browse"
        }
    }
}

/// Arc of a circle as a polyline, from/to in turns (0 = 3 o'clock, clockwise), so caps and direction are unambiguous.
func nightSleepScoreArc(center: CGPoint, radius: Double, from: Double, to: Double) -> Path {
    var p = Path()
    let steps = max(2, Int((abs(to - from) * 360 / 1.5).rounded(.up)))
    for i in 0...steps {
        let a = (from + (to - from) * Double(i) / Double(steps)) * 2 * .pi
        let point = CGPoint(x: center.x + radius * cos(a), y: center.y + radius * sin(a))
        if i == 0 { p.move(to: point) } else { p.addLine(to: point) }
    }
    return p
}

/// The Material heart in a 24-unit box.
struct NightSleepScoreHeart: Shape {
    func path(in rect: CGRect) -> Path {
        let s = rect.width / 24
        func q(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: rect.minX + x * s, y: rect.minY + y * s) }
        var p = Path()
        p.move(to: q(12, 21.35))
        p.addLine(to: q(10.55, 20))
        p.addCurve(to: q(2, 8.5), control1: q(5.4, 15.36), control2: q(2, 12.28))
        p.addCurve(to: q(7.5, 3), control1: q(2, 5.42), control2: q(4.42, 3))
        p.addCurve(to: q(12, 5.09), control1: q(9.24, 3), control2: q(10.91, 3.81))
        p.addCurve(to: q(16.5, 3), control1: q(13.09, 3.81), control2: q(14.76, 3))
        p.addCurve(to: q(22, 8.5), control1: q(19.58, 3), control2: q(22, 5.42))
        p.addCurve(to: q(13.45, 20), control1: q(22, 12.28), control2: q(18.6, 15.36))
        p.addLine(to: q(12, 21.35))
        p.closeSubpath()
        return p
    }
}

// MARK: - Header rail

struct NightSleepScoreMetricRail: View {
    let scale: Double
    private typealias R = NightSleepScoreTheme.Rail

    var body: some View {
        ZStack(alignment: .topLeading) {
            heartTile
            chip(R.sleep.left, R.sleep.width, "Sleep", "High") { sleepIcon }
            chip(R.readiness.left, R.readiness.width, "Readiness", "Ready") { readinessIcon }
            chip(R.fitness.left, R.fitness.width, "Fitness", "Steady") { fitnessIcon }
        }
        .frame(width: 402 * scale, height: 62 * scale, alignment: .topLeading)
        .clipped()
        .accessibilityHidden(true)
        .allowsHitTesting(false)
    }

    private var heartTile: some View {
        let H = R.Heart.self
        return ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: R.radius * scale, style: .continuous).fill(R.tile)
            NightSleepScoreHeart().fill(R.heart)
                .frame(width: H.heartSize * scale, height: H.heartSize * scale)
                .offset(x: (H.width / 2 - H.heartSize / 2) * scale, y: (H.heartCentre - H.heartSize / 2) * scale)
            ForEach(0..<H.lines.count, id: \.self) { i in
                Capsule().fill(R.line)
                    .frame(width: H.lines[i].width * scale, height: H.lineHeight * scale)
                    .offset(x: H.lineLeft * scale, y: H.lines[i].top * scale)
            }
        }
        .frame(width: H.width * scale, height: R.height * scale, alignment: .topLeading)
        .offset(x: H.left * scale, y: R.top * scale)
    }

    private func chip<Icon: View>(_ left: Double, _ width: Double, _ title: String, _ value: String,
                                  @ViewBuilder icon: () -> Icon) -> some View {
        let shape = RoundedRectangle(cornerRadius: R.radius * scale, style: .continuous)
        return ZStack(alignment: .topLeading) {
            shape.fill(R.chip)
            shape.strokeBorder(R.chipBorder, lineWidth: 0.67)
            RoundedRectangle(cornerRadius: (R.radius - 1) * scale, style: .continuous)
                .strokeBorder(R.chipRim, lineWidth: 1.5)
            icon()
                .frame(width: R.icon * scale, height: R.icon * scale)
                .offset(x: (R.iconCentre - R.icon / 2) * scale, y: (R.height / 2 - R.icon / 2) * scale)
            Text(title).font(.system(size: R.textSize * scale)).foregroundStyle(R.title).lineLimit(1)
                .frame(height: R.textLine * scale, alignment: .leading)
                .offset(x: R.textLeft * scale, y: R.titleTop * scale)
            Text(value).font(.system(size: R.valueSize * scale, weight: .semibold)).foregroundStyle(R.value).lineLimit(1)
                .frame(height: R.textLine * scale, alignment: .leading)
                .offset(x: R.textLeft * scale, y: R.valueTop * scale)
        }
        .frame(width: width * scale, height: R.height * scale, alignment: .topLeading)
        .offset(x: left * scale, y: R.top * scale)
    }

    // Icons are drawn in a 40-unit box shown at the measured 30.7 pt.
    private func icon(_ draw: @escaping (inout GraphicsContext) -> Void, label: [(String, Double, Double, Color)]) -> some View {
        ZStack {
            Canvas { context, size in
                context.scaleBy(x: size.width / 40, y: size.height / 40)
                draw(&context)
            }
            ForEach(0..<label.count, id: \.self) { i in
                Text(label[i].0).font(.system(size: 13 * scale * R.icon / 40, weight: .bold)).foregroundStyle(label[i].3)
                    .position(x: R.icon * scale * label[i].1 / 40, y: R.icon * scale * (label[i].2 - 13 * 0.36) / 40)
            }
        }
    }

    private static let ringR = 16.2

    private var sleepIcon: some View {
        icon({ c in
            let center = CGPoint(x: 20, y: 20)
            func seg(_ from: Double, _ to: Double, _ color: Color) {
                c.stroke(nightSleepScoreArc(center: center, radius: Self.ringR, from: from - 0.25, to: to - 0.25), with: .color(color), lineWidth: 7.6)
            }
            seg(0.01, 0.19, Color(hex: 0xF67D65))
            seg(0.22, 0.69, Color(hex: 0x4E63F8))
            seg(0.72, 0.99, Color(hex: 0x13C9BB))
        }, label: [("84", 20, 24.6, R.value)])
    }

    private var readinessIcon: some View {
        icon({ c in
            let center = CGPoint(x: 20, y: 21)
            let style = StrokeStyle(lineWidth: 6.4, lineCap: .round)
            c.stroke(nightSleepScoreArc(center: center, radius: Self.ringR, from: 0.44, to: 1.06), with: .color(R.track), style: style)
            c.stroke(nightSleepScoreArc(center: center, radius: Self.ringR, from: 0.75, to: 0.87), with: .color(R.gaugeLight), style: style)
            let a = 0.875 * 2 * Double.pi
            let d = CGPoint(x: 20 + Self.ringR * cos(a), y: 21 + Self.ringR * sin(a))
            c.fill(Path(ellipseIn: CGRect(x: d.x - 3.3, y: d.y - 3.3, width: 6.6, height: 6.6)), with: .color(R.gaugeBlue))
        }, label: [("7", 20, 30, R.gaugeBlue)])
    }

    private var fitnessIcon: some View {
        icon({ c in
            let center = CGPoint(x: 20, y: 20)
            c.stroke(Path(ellipseIn: CGRect(x: 20 - 16.6, y: 20 - 16.6, width: 33.2, height: 33.2)), with: .color(R.fitnessPink), lineWidth: 6.4)
            c.stroke(nightSleepScoreArc(center: center, radius: 16.6, from: -0.25, to: -0.25 + 0.86), with: .color(R.fitnessRed),
                     style: StrokeStyle(lineWidth: 6.4, lineCap: .round))
            c.stroke(nightSleepScoreArc(center: center, radius: 10.2, from: -0.25, to: -0.25 + 0.8), with: .color(R.fitnessGreen),
                     style: StrokeStyle(lineWidth: 5.2, lineCap: .round))
            c.fill(Path(ellipseIn: CGRect(x: 20 - 4.6, y: 20 - 4.6, width: 9.2, height: 9.2)), with: .color(R.fitnessCyan))
        }, label: [])
    }
}

// MARK: - Scroll-edge blur

/// The feed blurs progressively as it reaches the bar (a blur masked by a gradient).
struct NightSleepScoreScrollEdgeBlur: View {
    let scale: Double
    var body: some View {
        Rectangle().fill(.ultraThinMaterial)
            .mask(LinearGradient(stops: [.init(color: .clear, location: 0),
                                         .init(color: .black, location: NightSleepScoreTheme.Tab.edgeSolidFrom),
                                         .init(color: .black, location: 1)], startPoint: .top, endPoint: .bottom))
            .frame(height: NightSleepScoreTheme.Tab.edgeHeight * scale)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }
}

// MARK: - Liquid Glass tab bar

private struct NightSleepScoreGlassBackground: View {
    enum Kind { case bar, lens }
    let kind: Kind
    let radius: Double

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: radius, style: .continuous)
        if #available(iOS 26, *) {
            Color.clear.glassEffect(kind == .bar ? .regular.tint(NightSleepScoreTheme.Tab.glassTint)
                                                 : .clear.tint(NightSleepScoreTheme.Tab.lensGlassTint), in: shape)
        } else {
            ZStack {
                Rectangle().fill(.ultraThinMaterial)
                (kind == .bar ? NightSleepScoreTheme.Tab.fallback : NightSleepScoreTheme.Tab.lensFallback)
            }
            .clipShape(shape)
        }
    }
}

private struct NightSleepScorePressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        let M = NightSleepScoreMotion.TabMotion.self
        configuration.label
            .scaleEffect(configuration.isPressed ? M.pressScale : 1)
            .animation(.timingCurve(0.23, 1, 0.32, 1, duration: configuration.isPressed ? M.pressIn : M.pressOut), value: configuration.isPressed)
    }
}

private struct NightSleepScoreApertureIcon: View {
    let color: Color
    var body: some View {
        Canvas { c, size in
            c.scaleBy(x: size.width / 24, y: size.height / 24)
            c.fill(Path(ellipseIn: CGRect(x: 12 - 10.4, y: 12 - 10.4, width: 20.8, height: 20.8)), with: .color(color))
            for i in 0..<8 {
                let a = Double(i) * .pi / 4 + .pi / 8
                var p = Path()
                p.move(to: CGPoint(x: 12, y: 12))
                p.addLine(to: CGPoint(x: 12 + 11 * cos(a), y: 12 + 11 * sin(a)))
                c.stroke(p, with: .color(NightSleepScoreTheme.Tab.cardWhite), style: StrokeStyle(lineWidth: 1.7, lineCap: .round))
            }
            c.fill(Path(ellipseIn: CGRect(x: 8, y: 8, width: 8, height: 8)), with: .color(NightSleepScoreTheme.Tab.cardWhite))
        }
    }
}

private struct NightSleepScoreBrowseIcon: View {
    let color: Color
    var body: some View {
        Canvas { c, size in
            c.scaleBy(x: size.width / 24, y: size.height / 24)
            for row in 0..<2 { for column in 0..<2 {
                c.fill(Path(roundedRect: CGRect(x: 1.5 + Double(column) * 11, y: 1.5 + Double(row) * 11, width: 10, height: 10), cornerRadius: 2.3), with: .color(color))
            } }
        }
    }
}

/// The pin's floating tab bar as iOS 26 Liquid Glass: the page refracts under the capsule and the selection lens
/// slides to a tapped tab on a spring, stretching as it travels. Still under Reduce Motion.
struct NightSleepScoreGlassTabBar: View {
    let active: NightSleepScoreTab
    let scale: Double
    let still: Bool
    let onSelect: (NightSleepScoreTab) -> Void

    @State private var lensX = 0.0
    @State private var lensStretch = 1.0
    private typealias T = NightSleepScoreTheme.Tab
    private typealias M = NightSleepScoreMotion.TabMotion

    var body: some View {
        let s = scale
        let barShape = RoundedRectangle(cornerRadius: T.radius * s, style: .continuous)
        ZStack(alignment: .topLeading) {
            NightSleepScoreGlassBackground(kind: .bar, radius: T.radius * s)
            lens
                .frame(width: T.item * s, height: T.itemHeight * s)
                .scaleEffect(x: lensStretch, y: 1)
                .offset(x: T.padding * s + lensX * T.item * s, y: T.padding * s)
                .allowsHitTesting(false)
            HStack(spacing: 0) {
                ForEach(NightSleepScoreTab.allCases, id: \.rawValue) { tab in item(tab) }
            }
            .offset(x: T.padding * s, y: T.padding * s)
        }
        .frame(width: T.width * s, height: T.height * s, alignment: .topLeading)
        .clipShape(barShape)
        .overlay { barShape.strokeBorder(T.border, lineWidth: 1).allowsHitTesting(false) }
        .overlay { barShape.strokeBorder(T.outline, lineWidth: 0.67).allowsHitTesting(false).padding(-0.67) }
        .shadow(color: T.shadow, radius: 12 * s, x: 0, y: 5 * s)
        .onAppear { lensX = Double(active.rawValue) }
        .onChange(of: active) { _, next in move(to: next.rawValue) }
        .accessibilityElement(children: .contain)
    }

    private var lens: some View {
        let radius = T.itemHeight / 2 * scale
        let shape = RoundedRectangle(cornerRadius: radius, style: .continuous)
        return ZStack {
            NightSleepScoreGlassBackground(kind: .lens, radius: radius)
            LinearGradient(stops: [.init(color: T.lensSheen, location: 0), .init(color: .clear, location: T.lensSheenTo)],
                           startPoint: .top, endPoint: .bottom)
            shape.strokeBorder(T.lensRim, lineWidth: 1)
        }
        .clipShape(shape)
    }

    private func move(to index: Int) {
        if still {
            lensX = Double(index)
            return
        }
        withAnimation(.spring(duration: M.lensDuration, bounce: 1 - M.lensDamping)) { lensX = Double(index) }
        withAnimation(.timingCurve(0.23, 1, 0.32, 1, duration: M.stretchMs)) { lensStretch = M.stretch }
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: UInt64(M.stretchMs * 1_000_000_000))
            withAnimation(.spring(duration: M.settleDuration, bounce: 1 - M.settleDamping)) { lensStretch = 1 }
        }
    }

    private func item(_ tab: NightSleepScoreTab) -> some View {
        let s = scale
        let selected = tab == active
        let tint = selected ? T.active : T.ink
        let icon = T.icon * s
        return Button { onSelect(tab) } label: {
            ZStack(alignment: .top) {
                Group {
                    switch tab {
                    case .insights: NightSleepScoreHeart().fill(tint)
                    case .longevity: NightSleepScoreApertureIcon(color: tint)
                    case .browse: NightSleepScoreBrowseIcon(color: tint)
                    }
                }
                .frame(width: icon, height: icon)
                .offset(y: (T.iconCentre - T.icon / 2) * s)
                Text(tab.title).font(.system(size: T.labelSize * s, weight: .semibold)).foregroundStyle(tint)
                    .frame(height: T.labelLine * s)
                    .offset(y: T.labelTop * s)
            }
            .frame(width: T.item * s, height: T.itemHeight * s, alignment: .top)
            .contentShape(Rectangle())
        }
        .buttonStyle(NightSleepScorePressStyle())
        .accessibilityLabel(tab.title)
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    }
}
