import SwiftUI
import UIKit

/// Tiles folds its widgets into a drafting grid, then assembles them one at a time.
/// The React Native reference supplies the geometry, artwork and deterministic 8 s clock.
struct DotMatrixWidgets: View {
    var autoPlay = false

    static let loop: Double = 8

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var epoch = Date()
    @State private var accumulated = 0.0
    @State private var running = true
    @State private var phaseAnchor = 0.0
    @State private var clockAnchor = 0.0
    @State private var touching = false
    @State private var released: Double?
    @State private var held = CGPoint.zero
    @State private var settled = true
    @State private var touches = 0
    @State private var haptic = UISelectionFeedbackGenerator()
    @GestureState private var dragActive = false

    var body: some View {
        GeometryReader { geo in
            let safe = geo.safeAreaInsets
            let size = CGSize(width: geo.size.width + safe.leading + safe.trailing,
                              height: geo.size.height + safe.top + safe.bottom)
            ZStack(alignment: .topLeading) {
                DotMatrixWidgetsBackdrop()
                    .frame(width: size.width, height: size.height)

                TimelineView(.animation(paused: !running || reduceMotion || (!autoPlay && settled))) { timeline in
                    let now = elapsed(at: timeline.date)
                    let clock = clock(at: now)
                    let tilt = tilt(at: now)
                    board(size: size, top: safe.top + 64, clock: clock)
                        .modifier(DotMatrixWidgetsProjection(x: tilt.x, y: tilt.y, distance: 900))
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .allowsHitTesting(false)

                HStack(spacing: 0) {
                    Text("Tiles")
                        .font(.system(size: 20, weight: .black))
                        .foregroundStyle(.white)
                    Spacer(minLength: 0)
                    Text("Edit home screen")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(.white.opacity(0.75))
                }
                .frame(width: size.width - 46, height: 34)
                .offset(x: 23, y: safe.top + 8)
                .allowsHitTesting(false)

                VStack(alignment: .leading, spacing: 6) {
                    Text("Your stack is ready")
                        .font(.system(size: 26, weight: .black))
                        .tracking(-0.6)
                        .foregroundStyle(.white)
                    Text("Hold any tile to resize it, or drag it somewhere new.")
                        .font(.system(size: 15))
                        .foregroundStyle(.white.opacity(0.8))
                        .lineSpacing(21 - UIFont.systemFont(ofSize: 15).lineHeight)
                        .padding(.vertical, (21 - UIFont.systemFont(ofSize: 15).lineHeight) / 2)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .frame(width: size.width - 46, alignment: .leading)
                .padding(.bottom, safe.bottom + 18)
                .frame(width: size.width, height: size.height, alignment: .bottom)
                .allowsHitTesting(false)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .contentShape(Rectangle())
            .gesture(drag)
            .ignoresSafeArea()
        }
        .preferredColorScheme(.dark)
        .onAppear {
            epoch = .now
            accumulated = 0
            running = scenePhase == .active && !reduceMotion
            haptic.prepare()
        }
        .onChange(of: scenePhase) { _, phase in
            updateRunning(phase == .active && !reduceMotion)
        }
        .onChange(of: reduceMotion) { _, reduced in
            if reduced { endTouch() }
            updateRunning(scenePhase == .active && !reduced)
        }
        .onChange(of: dragActive) { _, active in
            if !active { endTouch() }
        }
        .task(id: touches) {
            guard touches > 0, !touching, let released else { return }
            let remaining = max(0, 2 - (elapsed(at: .now) - released))
            try? await Task.sleep(for: .seconds(remaining))
            guard !Task.isCancelled, !touching else { return }
            settled = elapsed(at: .now) - released >= 2
        }
    }

    private func board(size: CGSize, top: CGFloat, clock: Double) -> some View {
        let full = size.width - 46
        let half = (full - 17) / 2
        let icon = (half - 17) / 2
        return ZStack(alignment: .topLeading) {
            tile(n: 6, clock: clock, frame: CGRect(x: 23, y: top, width: full, height: 136)) {
                DotMatrixWidgetsMap()
            }
            tile(n: 1, clock: clock, frame: CGRect(x: 23, y: top + 153, width: half, height: half)) {
                DotMatrixWidgetsWeather()
            }
            ForEach(0..<4) { kind in
                tile(n: kind + 2, clock: clock,
                     frame: CGRect(x: 23 + half + 17 + CGFloat(kind % 2) * (icon + 17),
                                   y: top + 153 + CGFloat(kind / 2) * (icon + 17), width: icon, height: icon)) {
                    DotMatrixWidgetsIcon(kind: kind)
                }
            }
            tile(n: 0, clock: clock, frame: CGRect(x: 23, y: top + 170 + half, width: full, height: 230)) {
                DotMatrixWidgetsCalendar(clock: clock)
            }
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
    }

    private func tile<Content: View>(n: Int, clock: Double, frame: CGRect,
                                      @ViewBuilder content: () -> Content) -> some View {
        let s = Self.shown(n, at: clock)
        let k = min(1, max(0, s))
        return content()
            .frame(width: frame.width, height: frame.height)
            .modifier(DotMatrixWidgetsProjection(x: 40 * (1 - s), y: -22 * (1 - s),
                                                 scale: 0.2 + 0.8 * s, rise: 40 * (1 - k), distance: 700))
            .opacity(min(1, max(0, s * 2.5)))
            .offset(x: frame.minX, y: frame.minY)
    }

    private static func shown(_ n: Int, at t: Double) -> Double {
        let exitStart = 1 + Double(6 - n) * 0.08
        let away = min(1, max(0, (t - exitStart) / 0.4))
        if t < 1.9 { return 1 - away * away * away }
        let entrance = min(1, max(0, (t - 2.3 - Double(n) * 0.42) / 0.6))
        return 1 + 2.6 * pow(entrance - 1, 3) + 1.6 * pow(entrance - 1, 2)
    }

    private func elapsed(at date: Date) -> Double {
        accumulated + (running ? max(0, date.timeIntervalSince(epoch)) : 0)
    }

    private func clock(at now: Double) -> Double {
        guard autoPlay, !reduceMotion else { return 0 }
        let t = phaseAnchor + (touching ? 0 : max(0, now - clockAnchor - (released == nil ? 0 : 2)))
        return t.truncatingRemainder(dividingBy: Self.loop)
    }

    private func tilt(at now: Double) -> CGPoint {
        guard !reduceMotion else { return .zero }
        let k = touching ? 1 : released.map { exp(-6 * min(2, max(0, now - $0))) } ?? 0
        return CGPoint(x: held.x * k, y: held.y * k)
    }

    private func updateRunning(_ active: Bool) {
        let now = Date()
        accumulated = elapsed(at: now)
        epoch = now
        running = active
        if active, !touching, let released, accumulated - released < 2 {
            settled = false
            touches += 1
        }
    }

    private var drag: some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($dragActive) { _, active, _ in active = !reduceMotion }
            .onChanged { value in
                guard !reduceMotion else { return }
                if !touching {
                    phaseAnchor = clock(at: elapsed(at: .now))
                    touching = true
                    released = nil
                    settled = false
                    touches += 1
                    haptic.selectionChanged()
                }
                held = CGPoint(x: min(14, max(-14, -value.translation.height * 0.08)),
                               y: min(14, max(-14, value.translation.width * 0.08)))
            }
            .onEnded { _ in endTouch() }
    }

    private func endTouch() {
        guard touching else { return }
        let now = elapsed(at: .now)
        touching = false
        clockAnchor = now
        released = now
        touches += 1
    }
}

/// One perspective precedes translate, X rotation, Y rotation and scale, about the centre.
private struct DotMatrixWidgetsProjection: GeometryEffect {
    var x: Double
    var y: Double
    var scale: Double = 1
    var rise: Double = 0
    var distance: Double

    func effectValue(size: CGSize) -> ProjectionTransform {
        var transform = CATransform3DIdentity
        transform.m34 = -1 / distance
        transform = CATransform3DTranslate(transform, 0, rise, 0)
        transform = CATransform3DRotate(transform, x * .pi / 180, 1, 0, 0)
        transform = CATransform3DRotate(transform, y * .pi / 180, 0, 1, 0)
        transform = CATransform3DScale(transform, scale, scale, 1)
        let toOrigin = CATransform3DMakeTranslation(-size.width / 2, -size.height / 2, 0)
        let toCentre = CATransform3DMakeTranslation(size.width / 2, size.height / 2, 0)
        return ProjectionTransform(CATransform3DConcat(CATransform3DConcat(toOrigin, transform), toCentre))
    }
}

private struct DotMatrixWidgetsBackdrop: View {
    var body: some View {
        LinearGradient(colors: [Color(hex: 0x2C7FB8), Color(hex: 0x1B5D8E), Color(hex: 0x123F63)],
                       startPoint: .top, endPoint: .bottom)
            .overlay {
                Canvas { context, size in
                    var grid = Path()
                    for i in 0..<30 {
                        grid.move(to: CGPoint(x: 0, y: i * 30))
                        grid.addLine(to: CGPoint(x: size.width, y: CGFloat(i * 30)))
                    }
                    for i in 0..<15 {
                        grid.move(to: CGPoint(x: i * 30, y: 0))
                        grid.addLine(to: CGPoint(x: i * 30, y: 900))
                    }
                    context.stroke(grid, with: .color(.white.opacity(0.12)), lineWidth: 1)
                }
            }
    }
}

private struct DotMatrixWidgetsMap: View {
    var body: some View {
        ZStack(alignment: .bottomLeading) {
            Color(hex: 0xEEF0EC)
            Canvas { context, size in
                let scale = min(size.width / 347, size.height / 136)
                context.translateBy(x: (size.width - 347 * scale) / 2, y: (size.height - 136 * scale) / 2)
                context.scaleBy(x: scale, y: scale)
                var river = Path()
                river.move(to: CGPoint(x: 0, y: 96))
                river.addCurve(to: CGPoint(x: 220, y: 98), control1: CGPoint(x: 80, y: 80), control2: CGPoint(x: 140, y: 120))
                river.addCurve(to: CGPoint(x: 347, y: 82), control1: CGPoint(x: 300, y: 76), control2: CGPoint(x: 320, y: 70))
                river.addLine(to: CGPoint(x: 347, y: 104))
                river.addCurve(to: CGPoint(x: 200, y: 120), control1: CGPoint(x: 300, y: 96), control2: CGPoint(x: 250, y: 126))
                river.addCurve(to: CGPoint(x: 0, y: 116), control1: CGPoint(x: 150, y: 114), control2: CGPoint(x: 80, y: 104))
                river.closeSubpath()
                context.fill(river, with: .color(Color(hex: 0x9CCBF0)))
                context.fill(Path(roundedRect: CGRect(x: 240, y: 14, width: 80, height: 46), cornerRadius: 10),
                             with: .color(Color(hex: 0xCDE8C3)))
                let roads: [(CGPoint, CGPoint, CGFloat, Color)] = [
                    (CGPoint(x: 0, y: 40), CGPoint(x: 347, y: 30), 9, .white),
                    (CGPoint(x: 120, y: 0), CGPoint(x: 150, y: 136), 11, .white),
                    (CGPoint(x: 0, y: 64), CGPoint(x: 347, y: 60), 5, .white),
                    (CGPoint(x: 210, y: 0), CGPoint(x: 196, y: 136), 7, Color(hex: 0xFFD27A)),
                ]
                for (a, b, width, color) in roads {
                    var path = Path()
                    path.move(to: a)
                    path.addLine(to: b)
                    context.stroke(path, with: .color(color), lineWidth: width)
                }
                let dot = Path(ellipseIn: CGRect(x: 129, y: 41, width: 22, height: 22))
                context.fill(dot, with: .color(Color(hex: 0x1F7BFF)))
                context.stroke(dot, with: .color(.white), lineWidth: 3)
            }
            Text("12 min to Studio")
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(Color(hex: 0x1C1C1E))
                .padding(.horizontal, 10)
                .frame(height: 26)
                .background(.white, in: RoundedRectangle(cornerRadius: 13))
                .padding(.leading, 12)
                .padding(.bottom, 10)
        }
        .clipShape(RoundedRectangle(cornerRadius: 26))
    }
}

private struct DotMatrixWidgetsWeather: View {
    var body: some View {
        ZStack(alignment: .topLeading) {
            LinearGradient(colors: [Color(hex: 0x4FA3FF), Color(hex: 0x1F5BEA)], startPoint: .top, endPoint: .bottom)
            VStack(alignment: .leading, spacing: 0) {
                Text("Harbourview ◂")
                    .font(.system(size: 13, weight: .bold))
                Text("18°")
                    .font(.system(size: 44, weight: .light))
                    .padding(.top, 2)
                Circle()
                    .fill(Color(hex: 0xFFD34D))
                    .frame(width: 16, height: 16)
                    .padding(.top, 4)
                Text("Mostly sunny")
                    .font(.system(size: 13, weight: .bold))
                    .padding(.top, 6)
                Text("H:21° L:12°")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(.white.opacity(0.8))
            }
            .foregroundStyle(.white)
            .padding(14)
        }
        .clipShape(RoundedRectangle(cornerRadius: 26))
    }
}

private struct DotMatrixWidgetsCalendar: View {
    var clock: Double

    var body: some View {
        ZStack(alignment: .topLeading) {
            Color(hex: 0x0B0B0D)
            HStack(alignment: .bottom, spacing: 0) {
                Text("OCTOBER")
                    .font(.system(size: 18, weight: .light))
                    .tracking(1)
                Spacer(minLength: 0)
                Text("2026")
                    .font(.system(size: 22, weight: .light))
            }
            .foregroundStyle(Color(hex: 0xE8E8EC))
            .frame(height: 50, alignment: .bottom)
            .padding(.horizontal, 20)
            ForEach(0..<3) { col in
                Circle()
                    .fill(Color(hex: 0x1E1E22))
                    .frame(width: 30, height: 30)
                    .offset(x: CGFloat(20 + col * 44), y: 50)
            }
            ForEach(1..<32) { day in
                let index = day + 2
                Text("\(day)")
                    .font(.system(size: 12, weight: (12...15).contains(day) ? .heavy : .semibold))
                    .foregroundStyle(Color(hex: (12...15).contains(day) ? 0x0B0B0D : 0xC9C9CF))
                    .frame(width: 30, height: 30)
                    .background(color(day), in: Circle())
                    .offset(x: CGFloat(20 + index % 7 * 44), y: CGFloat(50 + index / 7 * 34))
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 30))
        .accessibilityElement(children: .combine)
    }

    private func color(_ day: Int) -> Color {
        if (12...14).contains(day) {
            let dark = (clock >= 1 && clock < 2.3) || (clock >= 2.3 && clock < 2.7 + Double(day - 12) * 0.2)
            return Color(hex: dark ? 0x2E2E33 : 0x43D86B)
        }
        if day == 15 { return Color(hex: clock >= 2.3 && clock < 3.5 ? 0x2E2E33 : 0xFF3B4E) }
        return Color(hex: 0x2E2E33)
    }
}

private struct DotMatrixWidgetsIcon: View {
    var kind: Int

    var body: some View {
        ZStack {
            Color(hex: kind == 3 ? 0x1C1C1E : 0xFFFFFF)
            Canvas { context, size in
                context.translateBy(x: size.width / 2, y: size.height / 2)
                context.scaleBy(x: 23, y: 23)
                switch kind {
                case 0:
                    context.stroke(Path(ellipseIn: CGRect(x: -0.85, y: -0.85, width: 1.7, height: 1.7)),
                                   with: .color(Color(hex: 0xD7D9DE)), lineWidth: 0.08)
                    context.fill(polygon([CGPoint(x: 0.5, y: -0.5), CGPoint(x: 0.08, y: 0.08), CGPoint(x: -0.08, y: -0.08)]),
                                 with: .color(Color(hex: 0xE8344A)))
                    context.fill(polygon([CGPoint(x: -0.5, y: 0.5), CGPoint(x: -0.08, y: -0.08), CGPoint(x: 0.08, y: 0.08)]),
                                 with: .color(Color(hex: 0x2F6BFF)))
                case 1:
                    context.fill(Path(roundedRect: CGRect(x: -0.85, y: -0.55, width: 1.7, height: 1.2), cornerRadius: 0.25),
                                 with: .color(Color(hex: 0x1C1C1E)))
                    context.fill(Path(ellipseIn: CGRect(x: -0.38, y: -0.33, width: 0.76, height: 0.76)), with: .color(.white))
                    context.fill(Path(ellipseIn: CGRect(x: -0.24, y: -0.19, width: 0.48, height: 0.48)),
                                 with: .color(Color(hex: 0x1C1C1E)))
                case 2:
                    context.fill(Path(roundedRect: CGRect(x: -0.85, y: -0.6, width: 1.7, height: 1.2), cornerRadius: 0.2),
                                 with: .color(Color(hex: 0x2F80FF)))
                    var flap = Path()
                    flap.move(to: CGPoint(x: -0.85, y: -0.5))
                    flap.addLine(to: CGPoint(x: 0, y: 0.15))
                    flap.addLine(to: CGPoint(x: 0.85, y: -0.5))
                    context.stroke(flap, with: .color(.white), lineWidth: 0.12)
                default:
                    var note = Path()
                    note.move(to: CGPoint(x: -0.2, y: 0.6))
                    note.addLine(to: CGPoint(x: -0.2, y: -0.6))
                    note.addLine(to: CGPoint(x: 0.6, y: -0.8))
                    note.addLine(to: CGPoint(x: 0.6, y: 0.4))
                    // SVG's almost-complete large arcs, with the same clockwise sweep.
                    note.move(to: CGPoint(x: -0.2, y: 0.6))
                    note.addArc(center: CGPoint(x: -0.381706700507647, y: 0.771706700507647), radius: 0.25,
                                startAngle: .radians(-0.7571101181), endAngle: .radians(5.4695011867), clockwise: false)
                    note.move(to: CGPoint(x: 0.6, y: 0.4))
                    note.addArc(center: CGPoint(x: 0.418293299492353, y: 0.571706700507647), radius: 0.25,
                                startAngle: .radians(-0.7571101181), endAngle: .radians(5.4695011867), clockwise: false)
                    context.stroke(note, with: .color(Color(hex: 0xFF4F7B)), lineWidth: 0.14)
                }
            }
            .frame(width: 46, height: 46)
            .clipped()
        }
        .clipShape(RoundedRectangle(cornerRadius: 18))
    }

    private func polygon(_ points: [CGPoint]) -> Path {
        Path { path in
            guard let first = points.first else { return }
            path.move(to: first)
            for point in points.dropFirst() { path.addLine(to: point) }
            path.closeSubpath()
        }
    }
}

#Preview {
    DotMatrixWidgets(autoPlay: true)
}
