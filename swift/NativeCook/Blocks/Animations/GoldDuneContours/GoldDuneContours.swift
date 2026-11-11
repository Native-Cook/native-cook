import SwiftUI
import UIKit

/// A hidden-line dune field. All artwork and the eight-second loop are deterministic.
struct GoldDuneContours: View {
    static let loop: Double = 8
    var autoPlay = false
    var previewTime: Double? = nil

    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @State private var epoch = Date.timeIntervalSinceReferenceDate
    @State private var interaction = GoldDuneContoursInteraction()
    @State private var settling = false
    @GestureState private var gestureActive = false

    private var safeInsets: UIEdgeInsets {
        UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows).first(where: \.isKeyWindow)?.safeAreaInsets ?? .zero
    }

    var body: some View {
        GeometryReader { geometry in
            let sx = geometry.size.width / 393
            let sy = geometry.size.height / 852
            let top = max(0, safeInsets.top - 59)
            let bottom = max(0, safeInsets.bottom - 34)
            TimelineView(.animation(paused: scenePhase != .active || previewTime != nil
                || (!settling && (reduced || !autoPlay)))) { frame in
                let now = frame.date.timeIntervalSinceReferenceDate
                let t = reduced ? 0 : (previewTime.map { $0.truncatingRemainder(dividingBy: Self.loop) }
                    ?? interaction.time(now, epoch: epoch, autoPlay: autoPlay))
                let playing = interaction.isPlaying(now)
                let ridge = interaction.ridge.value(now)
                let finger = CGPoint(x: interaction.x.value(now), y: interaction.y.value(now))
                ZStack(alignment: .topLeading) {
                    Canvas { context, size in
                        context.scaleBy(x: size.width / 393, y: size.height / 852)
                        Self.draw(&context, time: t, reduced: reduced, ridge: ridge, finger: finger, lightBlend: interaction.lightBlend(now))
                    }
                    .accessibilityHidden(true)

                    VStack(alignment: .leading, spacing: 0) {
                        Text("TONIGHT · 23:40")
                            .font(.system(size: 12, weight: .medium)).tracking(1.6)
                            .foregroundStyle(Self.ink.opacity(0.62))
                    }.offset(x: 28 * sx, y: 76 * sy + top)
                    Text("Dune Wind")
                        .font(.system(size: 40, weight: .regular, design: .serif))
                        .foregroundStyle(Self.ink)
                        .offset(x: 28 * sx, y: 104 * sy + top)
                    Text("Low wind over warm sand, recorded at dusk.")
                        .font(.system(size: 15)).foregroundStyle(Self.ink.opacity(0.62))
                        .fixedSize(horizontal: false, vertical: true)
                        .frame(width: 250 * sx, alignment: .leading)
                        .offset(x: 28 * sx, y: 158 * sy + top)

                    Canvas { context, size in
                        context.scaleBy(x: size.width / 40, y: size.height / 40)
                        let center = CGPoint(x: 20, y: 20)
                        var moon = Path(ellipseIn: CGRect(x: center.x - 8, y: center.y - 8, width: 16, height: 16))
                        context.clip(to: moon)
                        moon.addEllipse(in: CGRect(x: center.x - 3, y: center.y - 11, width: 16, height: 16))
                        context.fill(moon, with: .color(Self.cream), style: FillStyle(eoFill: true))
                    }
                    .frame(width: 40 * sx, height: 40 * sy)
                    .background(.white.opacity(0.07), in: Circle())
                    .overlay(Circle().stroke(Self.cream.opacity(0.22), lineWidth: 1))
                    .clipShape(Circle())
                    .offset(x: 325 * sx, y: 64 * sy + top)
                    .accessibilityHidden(true)

                    // The player sits above the terrain hit surface and keeps its own button.
                    Color.clear.contentShape(Rectangle())
                        .frame(width: geometry.size.width, height: 552 * sy)
                        .offset(y: 300 * sy)
                        .gesture(terrainGesture(sx: sx, sy: sy, cardTop: 716 - bottom / sy))
                        .accessibilityLabel("Dune contours")
                        .accessibilityHint("Drag to raise a ridge in the sand")

                    player(sx: sx, sy: sy, time: t, playing: playing)
                        .offset(x: 20 * sx, y: 716 * sy - bottom)
                }
                .frame(width: geometry.size.width, height: geometry.size.height, alignment: .topLeading)
            }
        }
        .ignoresSafeArea()
        .preferredColorScheme(.dark)
        .onAppear { epoch = Date.timeIntervalSinceReferenceDate }
        .onChange(of: gestureActive) { _, active in
            if !active && interaction.dragging { release() }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase != .active && interaction.dragging { release() }
        }
        .task(id: interaction.lastTouch) {
            guard interaction.lastTouch != nil else { return }
            try? await Task.sleep(for: .seconds(2.8))
            guard !Task.isCancelled, !interaction.dragging else { return }
            settling = false
        }
    }

    private func player(sx: CGFloat, sy: CGFloat, time: Double, playing: Bool) -> some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 28)
                .fill(.ultraThinMaterial)
                .environment(\.colorScheme, .dark)
                .overlay(RoundedRectangle(cornerRadius: 28).fill(Self.ground.opacity(0.46)))
                .overlay(RoundedRectangle(cornerRadius: 28).fill(.white.opacity(0.07)))
                .overlay(RoundedRectangle(cornerRadius: 28).stroke(Self.cream.opacity(0.22), lineWidth: 1))
            Button {
                let stamp = Date.timeIntervalSinceReferenceDate
                interaction.toggle(stamp, epoch: epoch, autoPlay: autoPlay, reduced: reduced)
                settling = true
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
            } label: {
                Canvas { context, size in
                    context.scaleBy(x: size.width / 52, y: size.height / 52)
                    if playing {
                        for x in [20.0, 28.0] {
                            context.fill(Path(roundedRect: CGRect(x: x, y: 18, width: 4, height: 16), cornerRadius: 2), with: .color(Color(hex: 0x1A120A)))
                        }
                    } else {
                        var path = Path(); path.move(to: CGPoint(x: 21, y: 17))
                        path.addLine(to: CGPoint(x: 35, y: 26)); path.addLine(to: CGPoint(x: 21, y: 35)); path.closeSubpath()
                        context.fill(path, with: .color(Color(hex: 0x1A120A)))
                    }
                }
                .frame(width: 52 * sx, height: 52 * sy)
                .background(LinearGradient(colors: [Self.cream, Color(hex: 0xE8B36A)], startPoint: .topLeading, endPoint: .bottomTrailing), in: Circle())
            }
            .buttonStyle(.plain).offset(x: 16 * sx, y: 16 * sy)
            .accessibilityLabel(playing ? "Pause Dune Wind" : "Play Dune Wind")
            Text("Dune Wind").font(.system(size: 16, weight: .semibold)).foregroundStyle(Self.ink)
                .offset(x: 84 * sx, y: 22 * sy)
            Text("45 min · fades out").font(.system(size: 13)).foregroundStyle(Self.ink.opacity(0.62))
                .offset(x: 84 * sx, y: 44 * sy)
            Canvas { context, _ in
                for k in 0..<5 {
                    let h = playing && !reduced ? 8 + 14 * (0.5 + 0.5 * sin(4 * .pi * time / Self.loop + Double(k) * 1.1)) : 8
                    let rect = CGRect(x: Double(k) * 6 * sx, y: (22 - h) / 2 * sy, width: 3 * sx, height: h * sy)
                    context.fill(Path(roundedRect: rect, cornerRadius: 1.5), with: .color(Self.cream.opacity(0.8)))
                }
            }.frame(width: 27 * sx, height: 22 * sy).offset(x: 302 * sx, y: 31 * sy)
                .accessibilityHidden(true)
        }.frame(width: 353 * sx, height: 84 * sy)
    }

    private func terrainGesture(sx: CGFloat, sy: CGFloat, cardTop: CGFloat) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($gestureActive) { _, active, _ in active = true }
            .onChanged { value in
                let now = Date.timeIntervalSinceReferenceDate
                let point = CGPoint(x: value.location.x / sx, y: value.location.y / sy + 300)
                if !interaction.dragging {
                    let start = CGPoint(x: value.startLocation.x / sx, y: value.startLocation.y / sy + 300)
                    guard !(start.x >= 20 && start.x <= 373 && start.y >= cardTop && start.y <= cardTop + 84) else { return }
                    interaction.begin(now, epoch: epoch, autoPlay: autoPlay, reduced: reduced, point: point)
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                } else {
                    interaction.x.retarget(Double(point.x), at: now, response: 0.30, damping: reduced ? 1 : 0.72)
                    interaction.y.retarget(Double(point.y), at: now, response: 0.30, damping: reduced ? 1 : 0.72)
                }
                settling = true
            }
            .onEnded { _ in release() }
    }

    private func release() {
        guard interaction.dragging else { return }
        let now = Date.timeIntervalSinceReferenceDate
        interaction.dragging = false
        interaction.lastTouch = now
        let x = interaction.x.value(now), y = interaction.y.value(now)
        interaction.x = GoldDuneContoursSpring(from: x, target: x)
        interaction.y = GoldDuneContoursSpring(from: y, target: y)
        interaction.ridge.retarget(0, at: now, response: 0.65, damping: reduced ? 1 : 0.55)
        if reduced { interaction.ridge.velocity = 0 }
        UIImpactFeedbackGenerator(style: .soft).impactOccurred()
    }

    private static let ink = Color(hex: 0xFFF3DC)
    private static let cream = Color(hex: 0xFFE9C4)
    private static let night = Color(hex: 0x0A0806)
    private static let ground = Color(hex: 0x120D09)

    private static func draw(_ context: inout GraphicsContext, time: Double, reduced: Bool, ridge: Double, finger: CGPoint, lightBlend: Double) {
        let t = time.truncatingRemainder(dividingBy: loop)
        let tau = 2 * Double.pi * t / loop
        let bounds = CGRect(x: 0, y: 0, width: 393, height: 852)
        context.fill(Path(bounds), with: .linearGradient(Gradient(colors: [night, ground]), startPoint: .zero, endPoint: CGPoint(x: 0, y: 852)))
        // A cosine bump honors the specified 190 -> 206 -> 190 radius.
        let radius = reduced ? 190 : 190 + 16 * pow(sin(tau / 2), 2)
        context.fill(Path(bounds), with: .radialGradient(Gradient(colors: [Color(hex: 0xFFB45E).opacity(0.34), .clear]), center: CGPoint(x: 268, y: 318), startRadius: 0, endRadius: radius))
        context.fill(Path(ellipseIn: CGRect(x: 234, y: 284, width: 68, height: 68)), with: .radialGradient(Gradient(colors: [Color(hex: 0xFFF6E0).opacity(0.92), Color(hex: 0xFFD9A0).opacity(0.92)]), center: CGPoint(x: 268, y: 318), startRadius: 0, endRadius: 34))
        for k in 0..<26 {
            let seed = hash(Double(k) + 1)
            // Each mote wraps its own 18 x 8 pt drift lane, fading at the seam.
            let age = (seed + (reduced ? 0 : t / loop)).truncatingRemainder(dividingBy: 1)
            let x = (hash(Double(k) + 31) * 393 + age * 18).truncatingRemainder(dividingBy: 393)
            let y = 90 + hash(Double(k) + 71) * 330 - age * 8
            let radius = 0.6 + 0.8 * hash(Double(k) + 111)
            let opacity = (0.1 + 0.28 * seed) * (0.5 + 0.5 * sin(2 * tau + seed * 2 * .pi)) * pow(sin(.pi * age), 2)
            context.fill(Path(ellipseIn: CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2)), with: .color(cream.opacity(opacity)))
        }
        for i in 0..<46 {
            let v = Double(i) / 45
            let baseline = 352 + 548 * pow(v, 1.55)
            let amplitude = 22 + 150 * pow(v, 1.3)
            var line = Path()
            // Include the right edge explicitly; the nominal six-point grid ends at 414.
            for sample in 0...74 {
                let x = min(417, -24 + Double(sample) * 6)
                let u = x / 393
                // A soft-cusped crest gives the dunes a wind-cut ridge instead of a round swell.
                let crest = 1 - 2 * sqrt(pow(sin(.pi * (0.8 * u + 0.9 * v) + tau / 2), 2) + 0.02)
                let h = 0.52 * crest
                    + 0.30 * sin(2 * .pi * (1.6 * u - 1.05 * v) - tau + 1.3)
                    + 0.18 * sin(2 * .pi * (2.9 * u + 2.2 * v) + 2 * tau + 0.4)
                let lift = ridge * 78 * (0.35 + 0.65 * v) * exp(-pow((x - finger.x) / 86, 2)) * exp(-pow((baseline - finger.y) / 110, 2))
                let point = CGPoint(x: x, y: baseline - amplitude * (0.5 + 0.5 * h) - lift)
                if sample == 0 { line.move(to: point) } else { line.addLine(to: point) }
            }
            var fill = line
            fill.addLine(to: CGPoint(x: 417, y: 960)); fill.addLine(to: CGPoint(x: -24, y: 960)); fill.closeSubpath()
            context.fill(fill, with: .color(Color(.sRGB, red: (10 + 8 * v) / 255, green: (8 + 5 * v) / 255, blue: (6 + 3 * v) / 255, opacity: 1)))
            let automatic = reduced ? 0.55 : pow(max(0, sin(.pi * t / loop)), 0.6)
            let mix = lightBlend
            let center = (reduced ? 0.62 : -0.45 + 1.9 * t / loop + 0.22 * v) * (1 - mix) + finger.x / 393 * mix
            let strength = automatic * (1 - mix) + min(1, max(0, ridge)) * mix
            if i % 3 == 0 {
                context.drawLayer { glow in
                    glow.addFilter(.blur(radius: 6))
                    let nearLight = max(0, 1 - abs(center - 0.5) / 0.76) * strength
                    glow.stroke(line, with: .color(Color(hex: 0xFFC978).opacity((0.16 + 0.30 * v) * (0.6 + 0.4 * nearLight))), lineWidth: 3 + 3 * v)
                }
            }
            if i >= 40 {
                context.drawLayer { glow in
                    glow.addFilter(.blur(radius: 12))
                    glow.stroke(line, with: .color(Color(hex: 0xFFC978).opacity(0.16)), lineWidth: 6)
                }
            }
            // Keep the base stops and insert the moving highlight in sorted order.
            let locations = ([0.0, 0.5, 1.0] + [center - 0.26, center, center + 0.26].map { min(1, max(0, $0)) }).sorted()
            let stops = locations.map { u -> Gradient.Stop in
                let base = baseColor(u)
                let light = max(0, 1 - abs(u - center) / 0.26) * strength
                let hot = [255.0, 246.0, 224.0]
                let color = Color(.sRGB, red: (base[0] + (hot[0] - base[0]) * light) / 255,
                                  green: (base[1] + (hot[1] - base[1]) * light) / 255,
                                  blue: (base[2] + (hot[2] - base[2]) * light) / 255,
                                  opacity: (0.30 + 0.65 * v) + (1 - (0.30 + 0.65 * v)) * light)
                return Gradient.Stop(color: color, location: u)
            }
            context.stroke(line, with: .linearGradient(Gradient(stops: stops), startPoint: .zero, endPoint: CGPoint(x: 393, y: 0)), style: StrokeStyle(lineWidth: 0.6 + 0.9 * v, lineCap: .round, lineJoin: .round))
        }
        context.fill(Path(CGRect(x: 0, y: 702, width: 393, height: 150)), with: .linearGradient(Gradient(colors: [ground.opacity(0), ground.opacity(0.7)]), startPoint: CGPoint(x: 0, y: 702), endPoint: CGPoint(x: 0, y: 852)))
    }

    private static func hash(_ seed: Double) -> Double {
        let n = sin(seed * 12.9898) * 43758.5453
        return n - floor(n)
    }
    private static func baseColor(_ u: Double) -> [Double] {
        let a = u < 0.5 ? [255.0, 233.0, 196.0] : [232.0, 179.0, 106.0]
        let b = u < 0.5 ? [232.0, 179.0, 106.0] : [201.0, 138.0, 107.0]
        let f = u < 0.5 ? u * 2 : (u - 0.5) * 2
        return zip(a, b).map { $0.0 + ($0.1 - $0.0) * f }
    }
}

/// Analytic spring: events change targets, drawing never accumulates frame state.
private struct GoldDuneContoursSpring {
    var from = 0.0
    var velocity = 0.0
    var target = 0.0
    var stamp = 0.0
    var response = 0.3
    var damping = 0.72

    func sample(_ now: Double) -> (value: Double, velocity: Double) {
        let t = max(0, now - stamp), w = 2 * Double.pi / response, d = from - target
        if t > 3 { return (target, 0) }
        if damping >= 1 {
            let b = velocity + w * d, decay = exp(-w * t)
            return (target + (d + b * t) * decay, (b - w * (d + b * t)) * decay)
        }
        let a = damping * w, wd = w * sqrt(1 - damping * damping)
        let b = (velocity + a * d) / wd, decay = exp(-a * t)
        let wave = d * cos(wd * t) + b * sin(wd * t)
        return (target + decay * wave, decay * (-a * wave - d * wd * sin(wd * t) + b * wd * cos(wd * t)))
    }
    func value(_ now: Double) -> Double { sample(now).value }
    mutating func retarget(_ value: Double, at now: Double, response: Double, damping: Double) {
        let current = sample(now)
        from = current.value; velocity = current.velocity; target = value; stamp = now
        self.response = response; self.damping = damping
    }
}

private struct GoldDuneContoursInteraction {
    var lastTouch: Double?
    var dragging = false
    var paused = false
    var heldByTerrain = false
    var held = 0.0
    var x = GoldDuneContoursSpring(from: 196.5, target: 196.5)
    var y = GoldDuneContoursSpring(from: 500, target: 500)
    var ridge = GoldDuneContoursSpring()

    // Keep light position continuous even when a touch ends before the ridge rises.
    func lightBlend(_ now: Double) -> Double {
        if dragging { return 1 }
        guard heldByTerrain, let lastTouch else { return 0 }
        let u = min(1, max(0, (now - lastTouch) / 0.65))
        return 1 - u * u * (3 - 2 * u)
    }
    func isPlaying(_ now: Double) -> Bool { !paused || (!dragging && now - (lastTouch ?? now) >= 2) }
    func time(_ now: Double, epoch: Double, autoPlay: Bool) -> Double {
        guard let lastTouch else { return autoPlay ? max(0, now - epoch).truncatingRemainder(dividingBy: 8) : 0 }
        if dragging { return held }
        let elapsed = max(0, now - lastTouch)
        if elapsed < 2 { return paused || heldByTerrain ? held : (held + elapsed).truncatingRemainder(dividingBy: 8) }
        let start = paused || heldByTerrain ? held : (held + 2).truncatingRemainder(dividingBy: 8)
        if elapsed < 2.8 {
            let u = (elapsed - 2) / 0.8, ease = u * u * (3 - 2 * u)
            return start + (8 - start) * ease
        }
        return autoPlay ? (elapsed - 2.8).truncatingRemainder(dividingBy: 8) : 0
    }
    mutating func begin(_ now: Double, epoch: Double, autoPlay: Bool, reduced: Bool, point: CGPoint) {
        held = reduced ? 0 : time(now, epoch: epoch, autoPlay: autoPlay)
        paused = !isPlaying(now)
        dragging = true; heldByTerrain = true; lastTouch = now
        x = GoldDuneContoursSpring(from: point.x, target: point.x)
        y = GoldDuneContoursSpring(from: point.y, target: point.y)
        ridge.retarget(1, at: now, response: 0.35, damping: reduced ? 1 : 0.70)
    }
    mutating func toggle(_ now: Double, epoch: Double, autoPlay: Bool, reduced: Bool) {
        let wasPlaying = isPlaying(now)
        held = reduced ? 0 : time(now, epoch: epoch, autoPlay: autoPlay)
        paused = wasPlaying; heldByTerrain = false; lastTouch = now
    }
}
