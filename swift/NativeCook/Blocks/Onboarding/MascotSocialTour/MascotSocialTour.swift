import CoreText
import SwiftUI
import UIKit

/// Four small stories told by the same soft, scalloped friends. All illustration poses, widgets,
/// page transitions and camera beats come from one clock, so a swipe scrubs the actual story.
struct MascotSocialTour: View {
    static let loop = 10.0

    var autoPlay = false
    /// A deterministic pose for previews, screenshots and comparison with the reference.
    var renderTime: Double? = nil
    var onLogin: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var epoch = Date()
    @State private var manualTime: Double? = nil
    @State private var dragOrigin = 0.0
    @State private var isDragging = false
    @State private var releasedAt = Date.distantPast
    @State private var releaseFrom = 0.0
    @State private var releaseTo = 0.0
    @State private var touchSerial = 0

    private typealias D = MascotSocialTourDrawing
    private typealias M = MascotSocialTourMotion

    var body: some View {
        GeometryReader { geometry in
            TimelineView(.animation(paused: renderTime != nil || reduceMotion || (!autoPlay && manualTime == nil))) { timeline in
                let time = shownTime(at: timeline.date)
                let pages = M.pages(time)

                ZStack(alignment: .topLeading) {
                    Color.white
                    Canvas { context, _ in
                        D.clouds(&context, time: time)
                        D.story(&context, time: time)
                    }
                    .frame(width: 393, height: 852)
                    .accessibilityHidden(true)

                    if pages.from != pages.to {
                        copy(page: pages.from)
                            .offset(x: -393 * pages.progress)
                            .opacity(1 - pages.progress * 0.2)
                    }
                    copy(page: pages.to)
                        .offset(x: pages.from == pages.to ? 0 : 393 * (1 - pages.progress))

                    HStack(spacing: 5) {
                        ForEach(0..<5, id: \.self) { index in
                            Capsule()
                                .fill(index == pages.active ? D.color(0x2084F8) : D.color(0xE8EAEE))
                                .frame(width: index == pages.active ? 16 : 5.8, height: 5.8)
                        }
                    }
                    .frame(width: 72, height: 44)
                    .position(x: 196.5, y: 89)
                    .accessibilityLabel("Page \(min(pages.active + 1, 4)) of 4")

                    Button {
                        pause(at: time)
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                        onLogin?()
                    } label: {
                        Text("Login")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(D.color(0x2384F7))
                            .frame(width: 73, height: 35)
                            .background(Capsule().fill(D.color(0xF0F5FF)))
                            .frame(width: 79, height: 44)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .position(x: 333, y: 89)

                    Text("By joining Nookly you acknowledge that you have read and\nagree to Nookly's Terms & Conditions and Privacy Policy.")
                        .font(.system(size: 11))
                        .foregroundStyle(D.color(0x88898E))
                        .multilineTextAlignment(.center)
                        .lineSpacing(6)
                        .frame(width: 365, height: 45)
                        .position(x: 196.5, y: 795)
                }
                .frame(width: 393, height: 852, alignment: .topLeading)
                .contentShape(Rectangle())
                .gesture(scrub)
                .scaleEffect(x: geometry.size.width / 393, y: geometry.size.height / 852, anchor: .topLeading)
                .frame(width: geometry.size.width, height: geometry.size.height, alignment: .topLeading)
                .clipped()
            }
        }
        .ignoresSafeArea()
        .preferredColorScheme(.light)
        .onAppear { _ = MascotSocialTourType.registered }
        .task(id: touchSerial) {
            guard manualTime != nil, !isDragging, !reduceMotion else { return }
            try? await Task.sleep(for: .seconds(2.55))
            guard !Task.isCancelled else { return }
            epoch = .now
            manualTime = nil
        }
    }

    private func copy(page: Int) -> some View {
        ZStack(alignment: .topLeading) {
            Text(M.titles[page])
                .font(.custom(MascotSocialTourType.name, fixedSize: 37))
                .foregroundStyle(D.color(0x080D28))
                .multilineTextAlignment(.center)
                .lineSpacing(-1)
                .frame(width: 373, height: 95)
                .scaleEffect(x: 1.15, y: 1)
                .position(x: 196.5, y: 626.5)
            Text(M.bodies[page])
                .font(.system(size: 16))
                .foregroundStyle(D.color(0x66676B))
                .multilineTextAlignment(.center)
                .lineSpacing(6)
                .frame(width: 375, height: 54)
                .position(x: 196.5, y: 716)
        }
        .frame(width: 393, height: 852, alignment: .topLeading)
        .accessibilityElement(children: .combine)
    }

    private func shownTime(at date: Date) -> Double {
        if let renderTime { return M.normalized(renderTime) }
        if reduceMotion { return manualTime ?? 8.55 }
        if let manualTime {
            if isDragging { return manualTime }
            let elapsed = max(0, date.timeIntervalSince(releasedAt))
            if elapsed < 0.6 {
                return M.mix(releaseFrom, releaseTo, M.spring(elapsed))
            }
            if elapsed < 2 { return releaseTo }
            return M.mix(releaseTo, 0, M.ease(M.segment(elapsed, 2, 2.55)))
        }
        return autoPlay ? M.normalized(date.timeIntervalSince(epoch)) : 0
    }

    private var scrub: some Gesture {
        DragGesture(minimumDistance: 6)
            .onChanged { value in
                if !isDragging {
                    dragOrigin = shownTime(at: .now)
                    isDragging = true
                    touchSerial += 1
                }
                manualTime = min(9.4, max(0, dragOrigin - value.translation.width / 80))
            }
            .onEnded { value in
                let current = manualTime ?? dragOrigin
                let projected = min(9.4, max(0, dragOrigin - (value.translation.width + 0.18 * (value.predictedEndTranslation.width - value.translation.width)) / 80))
                let target = M.anchors.min(by: { abs($0 - projected) < abs($1 - projected) }) ?? 0
                releaseFrom = current
                releaseTo = target
                manualTime = reduceMotion ? target : current
                isDragging = false
                releasedAt = .now
                touchSerial += 1
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
            }
    }

    private func pause(at time: Double) {
        releaseFrom = time
        releaseTo = time
        manualTime = time
        releasedAt = .now
        touchSerial += 1
    }
}

private enum MascotSocialTourType {
    static var name: String {
        _ = registered
        return "TitanOne"
    }
    static let registered: Void = {
        if let url = Bundle.main.url(forResource: "MascotSocialTour-TitanOne-Regular", withExtension: "ttf") {
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()
}

private enum MascotSocialTourMotion {
    static let titles = ["Hang out on\nyour phone", "Humanise\nconnection", "Build your\nown space", "Memories\nthat last"]
    static let bodies = [
        "Realtime social widgets with friends,\nbecause not everything requires a DM.",
        "Stay close through simple, subtle\nmoments — no pressure, just presence.",
        "It's yours. Each widget adds a new way\nto interact. Play, share, organise.",
        "Each day is summarised in a scrap book\nstyle memory. No effort required."
    ]
    static let anchors = [0.8, 3.85, 6.35, 8.55]

    static func normalized(_ time: Double) -> Double {
        let value = time.truncatingRemainder(dividingBy: MascotSocialTour.loop)
        return value < 0 ? value + MascotSocialTour.loop : value
    }
    static func segment(_ t: Double, _ a: Double, _ b: Double) -> Double { min(1, max(0, (t - a) / (b - a))) }
    static func ease(_ p: Double) -> Double { p * p * (3 - 2 * p) }
    static func out(_ p: Double) -> Double { 1 - pow(1 - p, 3) }
    static func mix(_ a: Double, _ b: Double, _ p: Double) -> Double { a + (b - a) * p }
    static func spring(_ time: Double) -> Double {
        if time >= 0.65 { return 1 }
        let t = max(0, time)
        return 1 - exp(-9 * t) * (cos(15 * t) + 0.6 * sin(15 * t))
    }
    static func key(_ time: Double, _ keys: [(Double, Double)]) -> Double {
        guard let first = keys.first, let last = keys.last else { return 0 }
        if time <= first.0 { return first.1 }
        for index in 1..<keys.count where time <= keys[index].0 {
            let a = keys[index - 1], b = keys[index]
            return mix(a.1, b.1, ease(segment(time, a.0, b.0)))
        }
        return last.1
    }
    static func pages(_ t: Double) -> (from: Int, to: Int, progress: Double, active: Int) {
        let cuts: [(Double, Double, Int, Int)] = [(1.7, 2.0, 0, 1), (4.1, 4.4, 1, 2), (7.0, 7.3, 2, 3), (9.45, 9.85, 3, 0)]
        var current = 0
        for (start, end, from, to) in cuts {
            if t < start { return (current, current, 1, current) }
            if t < end { return (from, to, out(segment(t, start, end)), to) }
            current = to
        }
        return (current, current, 1, current)
    }
}

private enum MascotSocialTourDrawing {
    private typealias M = MascotSocialTourMotion
    static let blue = color(0x3C71FB)
    static let green = color(0x47D87B)
    static let yellow = color(0xF7B30A)
    static let pink = color(0xF014CC)
    static let red = color(0xE64441)
    static let ink = color(0x0A1230)

    enum Kind { case blue, green, yellow, pink, red }

    static func color(_ value: UInt32) -> Color {
        Color(red: Double((value >> 16) & 255) / 255, green: Double((value >> 8) & 255) / 255, blue: Double(value & 255) / 255)
    }

    // The paths are made once. Per-frame drawing only transforms these silhouettes.
    static let flower = MascotSocialTourContours.flower
    static let smallFlower = MascotSocialTourContours.smallFlower
    static let redBloom = MascotSocialTourContours.bloom
    static let bean: Path = {
        var p = Path()
        p.move(to: CGPoint(x: -0.48, y: 0.1))
        p.addCurve(to: CGPoint(x: -0.1, y: -0.49), control1: CGPoint(x: -0.6, y: -0.2), control2: CGPoint(x: -0.36, y: -0.5))
        p.addCurve(to: CGPoint(x: 0.49, y: -0.02), control1: CGPoint(x: 0.27, y: -0.54), control2: CGPoint(x: 0.58, y: -0.27))
        p.addCurve(to: CGPoint(x: 0.1, y: 0.48), control1: CGPoint(x: 0.5, y: 0.35), control2: CGPoint(x: 0.36, y: 0.57))
        p.addCurve(to: CGPoint(x: -0.48, y: 0.1), control1: CGPoint(x: -0.07, y: 0.22), control2: CGPoint(x: -0.42, y: 0.6))
        p.closeSubpath()
        return p
    }()
    static let drop = MascotSocialTourContours.drop
    static let redDrop = MascotSocialTourContours.redDrop
    static let roundedTriangle = MascotSocialTourContours.roundedTriangle
    static let heart: Path = {
        var p = Path()
        p.move(to: CGPoint(x: 0, y: 0.45))
        p.addCurve(to: CGPoint(x: -0.42, y: -0.15), control1: CGPoint(x: -0.12, y: 0.34), control2: CGPoint(x: -0.61, y: 0.06))
        p.addCurve(to: CGPoint(x: 0, y: -0.22), control1: CGPoint(x: -0.38, y: -0.5), control2: CGPoint(x: -0.08, y: -0.45))
        p.addCurve(to: CGPoint(x: 0.42, y: -0.15), control1: CGPoint(x: 0.12, y: -0.5), control2: CGPoint(x: 0.45, y: -0.45))
        p.addCurve(to: CGPoint(x: 0, y: 0.45), control1: CGPoint(x: 0.62, y: 0.11), control2: CGPoint(x: 0.2, y: 0.37))
        p.closeSubpath()
        return p
    }()

    static func radial(lobes: Int, depth: Double, phase: Double) -> Path {
        var p = Path()
        for index in 0..<160 {
            let angle = Double(index) / 160 * .pi * 2
            let r = 0.45 * (1 + depth * cos(Double(lobes) * angle + phase))
            let point = CGPoint(x: cos(angle) * r, y: sin(angle) * r)
            if index == 0 { p.move(to: point) } else { p.addLine(to: point) }
        }
        p.closeSubpath()
        return p
    }

    static let cloudPaths: [Path] = (0..<2).map { layer in
        var p = Path()
        if layer == 0 {
            p.move(to: CGPoint(x: -120, y: 550))
            p.addCurve(to: CGPoint(x: 36, y: 499), control1: CGPoint(x: -55, y: 520), control2: CGPoint(x: -12, y: 482))
            p.addCurve(to: CGPoint(x: 105, y: 536), control1: CGPoint(x: 66, y: 498), control2: CGPoint(x: 91, y: 515))
            p.addCurve(to: CGPoint(x: 176, y: 511), control1: CGPoint(x: 124, y: 518), control2: CGPoint(x: 150, y: 500))
            p.addCurve(to: CGPoint(x: 222, y: 547), control1: CGPoint(x: 203, y: 512), control2: CGPoint(x: 217, y: 528))
            p.addCurve(to: CGPoint(x: 310, y: 520), control1: CGPoint(x: 243, y: 524), control2: CGPoint(x: 279, y: 504))
            p.addCurve(to: CGPoint(x: 373, y: 547), control1: CGPoint(x: 337, y: 521), control2: CGPoint(x: 361, y: 534))
            p.addCurve(to: CGPoint(x: 458, y: 515), control1: CGPoint(x: 394, y: 518), control2: CGPoint(x: 430, y: 498))
            p.addCurve(to: CGPoint(x: 520, y: 542), control1: CGPoint(x: 486, y: 515), control2: CGPoint(x: 506, y: 531))
        } else {
            p.move(to: CGPoint(x: -120, y: 710))
            p.addCurve(to: CGPoint(x: 173, y: 596), control1: CGPoint(x: 68, y: 735), control2: CGPoint(x: 120, y: 640))
            p.addCurve(to: CGPoint(x: 264, y: 562), control1: CGPoint(x: 185, y: 554), control2: CGPoint(x: 225, y: 545))
            p.addCurve(to: CGPoint(x: 318, y: 521), control1: CGPoint(x: 277, y: 522), control2: CGPoint(x: 300, y: 510))
            p.addCurve(to: CGPoint(x: 373, y: 547), control1: CGPoint(x: 340, y: 517), control2: CGPoint(x: 361, y: 534))
            p.addCurve(to: CGPoint(x: 460, y: 520), control1: CGPoint(x: 399, y: 521), control2: CGPoint(x: 430, y: 505))
            p.addCurve(to: CGPoint(x: 514, y: 545), control1: CGPoint(x: 484, y: 519), control2: CGPoint(x: 506, y: 531))
            p.addCurve(to: CGPoint(x: 595, y: 516), control1: CGPoint(x: 539, y: 519), control2: CGPoint(x: 568, y: 502))
            p.addCurve(to: CGPoint(x: 651, y: 548), control1: CGPoint(x: 622, y: 517), control2: CGPoint(x: 645, y: 534))
            p.addCurve(to: CGPoint(x: 737, y: 522), control1: CGPoint(x: 681, y: 520), control2: CGPoint(x: 710, y: 507))
            p.addCurve(to: CGPoint(x: 820, y: 549), control1: CGPoint(x: 770, y: 521), control2: CGPoint(x: 798, y: 537))
        }
        p.addLine(to: CGPoint(x: 900, y: 852)); p.addLine(to: CGPoint(x: -120, y: 852)); p.closeSubpath()
        return p
    }

    static func clouds(_ context: inout GraphicsContext, time: Double) {
        let home = 1 - M.ease(M.segment(time, 9.45, 9.85))
        for layer in 0..<2 {
            var c = context
            c.translateBy(x: -time * (layer == 0 ? 9 : 38) * home, y: 0)
            c.fill(cloudPaths[layer], with: .linearGradient(Gradient(colors: [color(layer == 0 ? 0xF5F9FB : 0xE6EDF1), .white]), startPoint: CGPoint(x: 0, y: 535), endPoint: CGPoint(x: 0, y: 728)))
        }
    }

    static func story(_ context: inout GraphicsContext, time: Double) {
        let first = time < 1.98 ? 1 - M.ease(M.segment(time, 1.82, 1.98)) : M.ease(M.segment(time, 9.50, 9.85))
        if first > 0.001 {
            var c = context
            c.opacity = first
            hangout(&c, time: time > 9.45 ? 0 : time)
        }
        let second = M.ease(M.segment(time, 2.02, 2.12)) * (1 - M.ease(M.segment(time, 4.30, 4.48)))
        if second > 0.001 {
            var c = context
            c.opacity = second
            connection(&c, time: time)
        }
        let third = M.ease(M.segment(time, 4.48, 4.65)) * (1 - M.ease(M.segment(time, 6.95, 7.22)))
        if third > 0.001 {
            var c = context
            c.opacity = third
            space(&c, time: time)
        }
        let fourth = M.ease(M.segment(time, 7.20, 7.48)) * (1 - M.ease(M.segment(time, 9.45, 9.78)))
        if fourth > 0.001 {
            var c = context
            c.opacity = fourth
            memories(&c, time: time)
        }
    }

    static func mascot(_ context: inout GraphicsContext, kind: Kind, x: Double, y: Double, w: Double, h: Double, angle: Double = 0, blink: Double = 0, highlight: Double = 0, look: Double = 0, bodyTurn: Double = 0) {
        guard w > 0.01, h > 0.01 else { return }
        var c = context
        c.translateBy(x: x, y: y)
        c.rotate(by: .degrees(angle))
        c.scaleBy(x: w, y: h)
        let contour: Path
        let fill: Color
        switch kind {
        case .blue: contour = flower; fill = blue
        case .green: contour = roundedTriangle; fill = green
        case .yellow: contour = smallFlower; fill = yellow
        case .pink: contour = drop; fill = pink
        case .red: contour = redDrop; fill = red
        }
        var body = c
        body.rotate(by: .degrees(bodyTurn))
        body.fill(contour, with: .color(fill))
        if highlight > 0 {
            var lit = c
            lit.clip(to: contour)
            lit.opacity = highlight
            lit.fill(Path(ellipseIn: CGRect(x: 0.02, y: -0.3, width: 0.5, height: 0.7)), with: .color(.white))
        }
        let face: [Double]
        switch kind {
        case .blue: face = [-0.0851, -0.0043, 0.0793, 0.1087, 0.1449]
        case .green: face = [-0.1688, 0.0234, 0.1044, 0.1368, 0.1517]
        case .yellow: face = [-0.1978, 0.0515, 0.0972, 0.13, 0.1442]
        case .pink: face = [0.113, 0.028, 0.1164, 0.141, 0.1084]
        case .red: face = [0.0859, -0.0144, 0.1103, 0.1675, 0.18]
        }
        let small = w < 80
        let eyeWidth = kind == .pink && small ? 0.10 : (kind == .red && small ? 0.11 : face[3])
        let normalHeight = kind == .pink && small ? 0.09 : (kind == .red && small ? 0.14 : face[4])
        let eyeHeight = M.mix(normalHeight, 0.025, blink)
        for side in [-1.0, 1.0] {
            let rect = CGRect(x: face[0] + look + side * face[2] - eyeWidth / 2, y: face[1] - eyeHeight / 2, width: eyeWidth, height: eyeHeight)
            c.fill(Path(roundedRect: rect, cornerRadius: eyeWidth / 2), with: .color(.white))
        }
    }

    static func hand(_ context: inout GraphicsContext, x: Double, y: Double, w: Double = 20, h: Double = 24, fill: Color, angle: Double = 0) {
        var c = context
        c.translateBy(x: x, y: y)
        c.rotate(by: .degrees(angle))
        c.scaleBy(x: w, y: h)
        c.fill(bean, with: .color(fill))
    }

    static func shadow(_ context: inout GraphicsContext, x: Double, y: Double, w: Double, h: Double, opacity: Double = 0.65) {
        var c = context
        c.opacity *= opacity
        c.fill(Path(ellipseIn: CGRect(x: x - w / 2, y: y - h / 2, width: w, height: h)), with: .color(color(0xECECFF)))
    }

    static func phone(_ context: inout GraphicsContext, x: Double, y: Double, w: Double, h: Double, angle: Double, upright: Double = 1, flash: Double = 0) {
        var c = context
        c.translateBy(x: x, y: y)
        c.rotate(by: .degrees(angle))
        let rect = CGRect(x: -w / 2, y: -h / 2, width: w, height: h)
        c.fill(Path(roundedRect: rect.offsetBy(dx: -1.7, dy: 1.6), cornerRadius: 4), with: .color(color(0xB7BEDC)))
        c.fill(Path(roundedRect: rect, cornerRadius: 4), with: .color(ink))
        if upright < 0.99 {
            var screen = c
            screen.opacity = 1 - upright
            screen.fill(Path(roundedRect: rect.insetBy(dx: 2.2, dy: 2), cornerRadius: 3), with: .color(color(0xDDF0FE)))
            screen.fill(Path(ellipseIn: CGRect(x: -w * 0.15, y: -h * 0.15, width: w * 0.34, height: h * 0.32)), with: .color(.white))
        }
        if upright > 0 {
            var camera = c
            camera.opacity = upright
            camera.fill(Path(roundedRect: CGRect(x: w / 2 - 12, y: -h / 2 + 3, width: 9, height: 9), cornerRadius: 2), with: .color(color(0x2A4380)))
            camera.fill(Path(ellipseIn: CGRect(x: w / 2 - 9.8, y: -h / 2 + 5.2, width: 4.6, height: 4.6)), with: .color(.white))
        }
        if flash > 0 {
            c.opacity *= flash
            c.fill(Path(ellipseIn: CGRect(x: w / 2 - 10.8, y: -h / 2 + 4.2, width: 6.6, height: 6.6)), with: .color(.white))
        }
    }

    static func hangout(_ context: inout GraphicsContext, time t: Double) {
        let x = M.key(t, [(0,137.8),(0.25,152.6),(0.38,154.7),(0.65,175.5),(1.1,186.7),(1.38,102.5),(1.65,102.5),(1.9,130.8)])
        let y = M.key(t, [(0,258.3),(0.25,237.7),(0.38,242.1),(0.65,318.8),(1.1,334.3),(1.38,268),(1.65,268),(1.9,287.2)])
        let angle = M.key(t, [(0,-12.6),(0.65,-1.7),(1.2,-5.8),(1.65,12.3),(1.9,-7.6)])
        let sinkRed = M.ease(M.segment(t, 0.04, 0.45))
        let sinkYellow = M.ease(M.segment(t, 0.01, 0.37))
        if sinkRed < 1 {
            let s = 1 - sinkRed
            mascot(&context, kind: .red, x: M.mix(96.9, 235, sinkRed), y: M.mix(415.1, 447, sinkRed), w: 89.7 * s, h: 89.8 * s, angle: -35 - 35 * sinkRed)
            hand(&context, x: 155, y: 381, w: 24 * s, h: 23 * s, fill: red, angle: -25)
        }
        if sinkYellow < 1 {
            let s = 1 - sinkYellow
            mascot(&context, kind: .yellow, x: M.mix(267.5, 235, sinkYellow), y: M.mix(325.5, 445, sinkYellow), w: 75.6 * s, h: 79.5 * s, angle: 22.4 + 60 * sinkYellow)
        }
        let reappear = M.ease(M.segment(t, 1.53, 1.91))
        if reappear > 0 {
            mascot(&context, kind: .yellow, x: M.mix(235, 282, reappear), y: M.mix(446, 279, reappear), w: 65.7 * reappear, h: 74.6 * reappear, angle: 7.5)
        }
        let held = M.ease(M.segment(t, 0.65, 0.98)) * (1 - M.ease(M.segment(t, 1.25, 1.55)))
        shadow(&context, x: 235, y: 459, w: 73 * (1 - held * 0.2), h: 20 * (1 - held * 0.2), opacity: 0.6 * (1 - held * 0.6))
        phone(&context, x: M.mix(235, 252, held), y: M.mix(450, 375, held), w: M.mix(49, 30, held), h: M.mix(22, 48, held), angle: M.mix(-5, 6, held), upright: held)
        mascot(&context, kind: .blue, x: x, y: y, w: 115.4, h: 109.2, angle: angle)
        hand(&context, x: x - 57, y: y + 60, w: 25, h: 30, fill: blue, angle: -20)
        hand(&context, x: M.mix(x + 78, 240, held), y: M.mix(y + 12, 381, held), w: 28, h: 28, fill: blue, angle: 15)
        for index in 0..<5 {
            let p = M.segment(t, 1.43 + Double(index) * 0.065, 2.06 + Double(index) * 0.02)
            if p > 0, p < 1 {
                let side = index % 2 == 0 ? -1.0 : 1.0
                var c = context
                c.opacity = sin(.pi * p)
                note(&c, x: 235 + side * (10 + 80 * p), y: 441 - 140 * p, scale: 0.6 + 0.5 * p, angle: side * 25, fill: [green, pink, blue, red, green][index])
            }
        }
    }

    static func connection(_ context: inout GraphicsContext, time t: Double) {
        let x = M.key(t, [(2.0,196.5),(2.125,234.2),(2.25,242.9),(2.375,236.6),(2.5,206.6),(2.625,149.6),(2.75,140),(2.875,147.1),(3,173.6),(3.125,194),(3.25,196.5),(4.4,196.5)])
        let y = M.key(t, [(2.0,316),(2.125,283),(2.25,269.6),(2.375,277.8),(2.5,295.8),(2.625,280.6),(2.75,272.4),(2.875,317.4),(3,352.9),(3.125,328),(3.25,324.5),(4.4,322)])
        let width = M.key(t, [(2.0,3),(2.125,130),(2.25,156),(2.375,147),(2.5,142),(2.75,142),(2.875,161),(3,188),(3.125,217),(3.25,231),(3.375,228),(3.5,212),(3.625,178),(4.4,178)])
        let height = M.key(t, [(2.0,3),(2.125,123),(2.25,147),(2.375,133),(2.5,120),(2.75,135),(2.875,141),(3,150),(3.125,197),(3.25,220),(3.375,216),(3.5,201),(3.625,169),(4.4,168)])
        let blink = sin(.pi * M.segment(t, 2.41, 2.59))
        let look = M.key(t, [(2.0,0),(2.25,0.15),(2.75,-0.04),(3.2,0.13),(4.4,0.13)])
        let leave = 1 - M.ease(M.segment(t, 4.30, 4.48))
        let friends: [(Kind, Double, Double, Double)] = [(.green,105.7,227,13.6),(.pink,301.4,241.2,-15.8),(.red,93.2,403.1,-21.1),(.yellow,302,399.1,23.5)]
        for (index, friend) in friends.enumerated() {
            let emerge = M.spring(max(0, t - 3.20 - Double(index) * 0.045))
            let p = max(0, emerge)
            let bob = 2.5 * sin((t - 3.2) * 2 * .pi + Double(index)) * M.segment(t, 3.65, 3.9)
            mascot(&context, kind: friend.0, x: M.mix(196.5, friend.1, p), y: M.mix(316, friend.2, p) + bob, w: [99.7,89,91.5,102.9][index] * leave * min(1, p * 1.8), h: [97.1,108.3,94.7,115.5][index] * leave * min(1, p * 1.8), angle: friend.3 * p, blink: M.segment(t, 3.31, 3.39) * (1 - M.segment(t, 3.42, 3.5)), look: index == 2 ? -0.15 : 0, bodyTurn: index == 2 ? -90 : 0)
        }
        mascot(&context, kind: .blue, x: x, y: y, w: width * leave, h: height * leave, angle: M.key(t, [(2,0),(3.6,0),(4.3,-11)]), blink: pow(blink, 3), look: look)
    }

    static func space(_ context: inout GraphicsContext, time t: Double) {
        let born = M.spring(max(0, t - 4.47))
        let end = 1 - M.ease(M.segment(t, 6.95, 7.22))
        var c = context
        c.translateBy(x: 196.5, y: 326)
        c.scaleBy(x: max(0.001, born * end), y: max(0.001, born * end))
        c.translateBy(x: -196.5, y: -326)
        let flap = M.ease(M.segment(t, 4.65, 5.22))
        let flapRight = M.ease(M.segment(t, 4.71, 5.28))
        c.fill(Path(roundedRect: CGRect(x: 120, y: 218, width: 151, height: 213), cornerRadius: 9), with: .color(yellow))
        c.fill(Path(CGRect(x: 120, y: 242, width: 151, height: 165)), with: .color(color(0xF1F6F8)))
        var left = Path()
        left.move(to: CGPoint(x: 120, y: 242))
        left.addLine(to: CGPoint(x: M.mix(196, 92, flap), y: M.mix(242, 229, flap)))
        left.addQuadCurve(to: CGPoint(x: M.mix(196, 92, flap), y: M.mix(407, 390, flap)), control: CGPoint(x: M.mix(200, 87, flap), y: 323))
        left.addLine(to: CGPoint(x: 120, y: 407))
        left.closeSubpath()
        c.fill(left, with: .color(color(0xFFECB0)))
        var right = Path()
        right.move(to: CGPoint(x: 271, y: 242))
        right.addLine(to: CGPoint(x: M.mix(196, 299, flapRight), y: M.mix(242, 227, flapRight)))
        right.addQuadCurve(to: CGPoint(x: M.mix(196, 299, flapRight), y: M.mix(407, 390, flapRight)), control: CGPoint(x: M.mix(192, 305, flapRight), y: 323))
        right.addLine(to: CGPoint(x: 271, y: 407))
        right.closeSubpath()
        c.fill(right, with: .color(color(0xFFECB0)))
        shadow(&c, x: 82, y: 255, w: 32, h: 7)
        shadow(&c, x: 265, y: 483, w: 46, h: 10)
        mascot(&c, kind: .pink, x: M.key(t, [(4.48,73),(5.4,70.5),(6.1,83.3),(6.7,72.1)]), y: M.key(t, [(4.48,231),(5.4,212.2),(6.1,214.9),(6.7,224.6)]), w: 54, h: 64, angle: M.key(t, [(4.48,-15.4),(5.4,-15.9),(6.1,-36.4),(6.7,-0.7)]))
        mascot(&c, kind: .red, x: M.key(t, [(4.48,286),(5.4,305.5),(6.1,256),(6.7,265.1)]), y: M.key(t, [(4.48,423.4),(5.4,421.3),(6.1,438.1),(6.7,428.3)]), w: 66, h: 66, angle: M.key(t, [(4.48,14.8),(5.4,17.2),(6.1,-24.8),(6.7,-8.9)]), blink: M.key(t, [(4.8,0),(6.1,0),(6.7,0.85),(6.95,0)]), look: -0.17)
        if t > 5.7 {
            let gx = M.key(t, [(5.7,81),(5.75,76.1),(5.875,72),(6,77.2),(6.125,102.1),(6.25,129.9),(6.375,147.1),(6.5,148.5)])
            let gy = M.key(t, [(5.7,274),(5.75,269.4),(5.875,281.4),(6,284.7),(6.125,298.3),(6.25,313.6),(6.375,323.7),(6.5,324.2)])
            c.fill(Path(roundedRect: CGRect(x: gx - 23.5, y: gy - 23.5, width: 47, height: 47), cornerRadius: 7), with: .color(green))
            let bx = M.key(t, [(5.7,306),(5.75,308.4),(5.875,322),(6,315.5),(6.125,287.9),(6.25,256.8),(6.375,239.3),(6.5,236.6)])
            let by = M.key(t, [(5.7,392),(5.75,388.4),(5.875,376.4),(6,372.5),(6.125,355.3),(6.25,337.1),(6.375,325),(6.5,324.2)])
            c.fill(Path(ellipseIn: CGRect(x: bx - 25.5, y: by - 25.5, width: 51, height: 51)), with: .color(blue))
        }
        hand(&c, x: M.key(t, [(4.8,178),(5.4,114),(6.1,138.6),(6.7,137.5)]), y: M.key(t, [(4.8,286),(5.4,246.7),(6.1,227.3),(6.7,283.8)]), w: 25, h: 28, fill: pink, angle: -15)
        hand(&c, x: M.key(t, [(4.8,181),(5.4,82.4),(6.1,63),(6.7,110.8)]), y: M.key(t, [(4.8,353),(5.4,274),(6.1,300),(6.7,302.1)]), w: 25, h: 28, fill: pink, angle: 18)
        hand(&c, x: M.key(t, [(4.8,211),(5.4,255),(6.1,219.4),(6.7,242.9)]), y: M.key(t, [(4.8,320),(5.4,345),(6.1,371.2),(6.7,368.2)]), w: 29, h: 27, fill: red, angle: 15)
        hand(&c, x: M.key(t, [(4.8,207),(5.4,300),(6.1,340),(6.7,272.7)]), y: M.key(t, [(4.8,383),(5.4,412),(6.1,374),(6.7,375.8)]), w: 25, h: 28, fill: red, angle: 10)
    }

    static func memories(_ context: inout GraphicsContext, time t: Double) {
        let entrance = max(0, M.spring(max(0, t - 7.2)))
        let bob = sin((t - 7.45) * 2 * .pi) * 3
        let flash = pulse(t, center: 7.86, duration: 0.18) + pulse(t, center: 8.55, duration: 0.18) + pulse(t, center: 9.22, duration: 0.18)
        shadow(&context, x: 112, y: 473, w: 44, h: 7, opacity: 0.45)
        mascot(&context, kind: .pink, x: 109.3, y: 373 + bob, w: 118.6 * entrance, h: 136.6 * entrance, angle: M.key(t, [(7.48,-2.8),(7.78,-2.8),(8.3,-15.4),(8.9,-13),(9.4,-2.8)]), highlight: 0.23 + flash * 0.25, look: 0.06)
        phone(&context, x: M.key(t, [(7.48,149.8),(7.85,174),(8.9,174),(9.4,160)]), y: M.key(t, [(7.48,425.8),(7.85,402),(8.3,399.5),(8.9,405),(9.4,416)]) + bob, w: 31 * entrance, h: 52 * entrance, angle: -9 + 4 * flash, flash: flash * 0.6)
        let redIn = M.ease(M.segment(t, 7.96, 8.13))
        let pinkIn = M.ease(M.segment(t, 8.9, 9.2))
        let tint = redIn < 0.5 ? color(0xFFFBEE) : (pinkIn < 0.5 ? color(0xFFF0EF) : color(0xFFF0FB))
        let bubbleScale = entrance * (1 + flash * 0.055)
        var c = context
        c.translateBy(x: 252, y: 255 - bob * 0.6)
        c.scaleBy(x: bubbleScale, y: bubbleScale)
        c.fill(Path(ellipseIn: CGRect(x: -88, y: -63, width: 176, height: 126)), with: .color(tint))
        var bubble = Path()
        bubble.move(to: CGPoint(x: -42, y: 43))
        bubble.addQuadCurve(to: CGPoint(x: -29, y: 78), control: CGPoint(x: -38, y: 80))
        bubble.addLine(to: CGPoint(x: 2, y: 57))
        bubble.closeSubpath()
        c.fill(bubble, with: .color(tint))
        var object = c
        object.opacity = 1 - redIn
        object.rotate(by: .degrees(-16))
        object.fill(Path(roundedRect: CGRect(x: -31, y: -31, width: 62, height: 62), cornerRadius: 7), with: .color(yellow))
        if redIn > 0 {
            var flowerContext = c
            flowerContext.opacity = redIn * (1 - pinkIn)
            transformed(&flowerContext, path: redBloom, x: 0, y: 0, w: 80.8, h: 85.2, angle: 0, fill: red)
        }
        if pinkIn > 0 {
            var triangles = c
            triangles.opacity = pinkIn
            roundedPlay(&triangles, x: -10, y: -9.558013, w: 74, h: 83, small: false)
            roundedPlay(&triangles, x: 48.5, y: 0.441987, w: 49, h: 51, small: true)
        }
    }

    static func pulse(_ t: Double, center: Double, duration: Double) -> Double {
        let p = M.segment(t, center - duration / 2, center + duration / 2)
        return p > 0 && p < 1 ? sin(.pi * p) : 0
    }

    static func transformed(_ context: inout GraphicsContext, path: Path, x: Double, y: Double, w: Double, h: Double, angle: Double = 0, fill: Color) {
        var c = context
        c.translateBy(x: x, y: y)
        c.rotate(by: .degrees(angle))
        c.scaleBy(x: w, y: h)
        c.fill(path, with: .color(fill))
    }

    static func note(_ context: inout GraphicsContext, x: Double, y: Double, scale: Double, angle: Double, fill: Color) {
        var c = context
        c.translateBy(x: x, y: y)
        c.rotate(by: .degrees(angle))
        c.scaleBy(x: scale, y: scale)
        c.fill(Path(ellipseIn: CGRect(x: -5, y: 5, width: 7, height: 5)), with: .color(fill))
        c.fill(Path(CGRect(x: 0, y: -8, width: 2, height: 15)), with: .color(fill))
        var flag = Path()
        flag.move(to: CGPoint(x: 1, y: -8))
        flag.addQuadCurve(to: CGPoint(x: 7, y: -2), control: CGPoint(x: 6, y: -8))
        flag.addLine(to: CGPoint(x: 6, y: 1))
        flag.addQuadCurve(to: CGPoint(x: 1, y: -4), control: CGPoint(x: 4, y: -4))
        flag.closeSubpath()
        c.fill(flag, with: .color(fill))
    }

    static func roundedPlay(_ context: inout GraphicsContext, x: Double, y: Double, w: Double, h: Double, small: Bool) {
        // Source silhouettes include their own orientation and rounded corners.
        var c = context
        c.translateBy(x: x - w / 2, y: y - h / 2)
        c.scaleBy(x: w, y: h)
        var p = Path()
        if small {
            p.move(to: CGPoint(x: 0.113345, y: 0.000089))
            p.addCurve(to: CGPoint(x: 0.012199, y: 0.252632), control1: CGPoint(x: -0.003439, y: -0.004944), control2: CGPoint(x: -0.014833, y: 0.197077))
            p.addLine(to: CGPoint(x: 0.338096, y: 0.922400))
            p.addCurve(to: CGPoint(x: 0.630270, y: 0.878484), control1: CGPoint(x: 0.413287, y: 1.076929), control2: CGPoint(x: 0.578986, y: 0.967737))
            p.addLine(to: CGPoint(x: 0.989879, y: 0.252632))
            p.addCurve(to: CGPoint(x: 0.877499, y: 0.033026), control1: CGPoint(x: 1.025614, y: 0.190439), control2: CGPoint(x: 0.961646, y: 0.036653))
            p.addLine(to: CGPoint(x: 0.113345, y: 0.000089))
            p.closeSubpath()
        } else {
            p.move(to: CGPoint(x: 0.008226, y: 0.798017))
            p.addCurve(to: CGPoint(x: 0.316465, y: 0.978922), control1: CGPoint(x: -0.043648, y: 1.008039), control2: CGPoint(x: 0.160612, y: 1.025120))
            p.addLine(to: CGPoint(x: 0.881571, y: 0.811412))
            p.addCurve(to: CGPoint(x: 0.947625, y: 0.530013), control1: CGPoint(x: 1.011260, y: 0.772969), control2: CGPoint(x: 1.036216, y: 0.617018))
            p.addLine(to: CGPoint(x: 0.463247, y: 0.054304))
            p.addCurve(to: CGPoint(x: 0.177024, y: 0.114602), control1: CGPoint(x: 0.375095, y: -0.032270), control2: CGPoint(x: 0.209671, y: -0.017579))
            p.addLine(to: CGPoint(x: 0.008226, y: 0.798017))
            p.closeSubpath()
        }
        c.fill(p, with: .color(pink))
    }
}
