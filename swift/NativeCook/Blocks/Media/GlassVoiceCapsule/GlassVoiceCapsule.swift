import SwiftUI
import UIKit

/// A silent voice-note demo; automatic frames are pure functions of the nine-second loop.
struct GlassVoiceCapsule: View {
    var autoPlay = false
    static let loop = 9.0
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @State private var epoch = Date.now
    @State private var interaction: GlassVoiceCapsuleInteraction?
    @State private var touching = false
    @State private var drag = GlassVoiceCapsuleDrag()
    @State private var resume: Task<Void, Never>?
    @State private var active = true
    @State private var settling = false

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing,
                              height: proxy.size.height + insets.top + insets.bottom)
            TimelineView(.animation(paused: !active || (!autoPlay && interaction == nil) || (reduced && !settling))) { timeline in
                let now = timeline.date.timeIntervalSinceReferenceDate
                let f = frame(at: now)
                let offset = reduced ? CGSize.zero : drag.value(at: now)
                let center = CGPoint(x: size.width / 2 + offset.width, y: size.height * 488 / 852 + f.floatY + offset.height)
                let angle = reduced ? 0 : f.angle + min(8, max(-8, offset.width / 14))
                ZStack(alignment: .topLeading) {
                    GlassVoiceCapsuleArtwork.image.resizable().scaledToFill()
                        .frame(width: size.width, height: size.height).clipped()
                    Canvas { context, _ in
                        GlassVoiceCapsulePainter(size: size, center: center, angle: angle, frame: f).draw(context)
                    }
                    Color.clear.frame(width: f.width, height: f.height)
                        .contentShape(Capsule()).position(center)
                        .accessibilityElement()
                        .accessibilityLabel(f.recording ? "Stop recording" : "Start a voice note")
                        .accessibilityAddTraits(.isButton)
                        .accessibilityAction { toggle(at: Date.now.timeIntervalSinceReferenceDate) }
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .contentShape(Rectangle()).gesture(touch(size: size))
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .offset(x: -insets.leading, y: -insets.top)
        }
        .background(GlassVoiceCapsulePainter.color(0x6E8DB3).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear { reset(); active = true }
        .onDisappear { active = false; reset() }
        .onChange(of: scenePhase) { _, phase in
            active = phase == .active
            if !active { reset() }
        }
        .onChange(of: reduced) { _, _ in reset() }
    }

    private func reset() {
        resume?.cancel(); interaction = nil; touching = false; settling = false
        drag = GlassVoiceCapsuleDrag(); epoch = .now
    }
    private func frame(at now: Double) -> GlassVoiceCapsuleFrame {
        if let interaction { return interaction.frame(at: now, reduced: reduced) }
        let t = autoPlay && !reduced ? max(0, now - epoch.timeIntervalSinceReferenceDate).truncatingRemainder(dividingBy: Self.loop) : 0
        return GlassVoiceCapsuleFrame.automatic(t)
    }
    private func takeOver(at now: Double) {
        resume?.cancel()
        if interaction == nil {
            let f = frame(at: now)
            interaction = GlassVoiceCapsuleInteraction(source: f, at: now, recording: f.recording,
                recordAt: now - f.seconds, stoppedAt: f.saved > 0 ? now : nil)
        }
        settling = true
    }
    private func toggle(at now: Double) {
        takeOver(at: now)
        let f = frame(at: now), recording = !(interaction?.recording ?? false)
        interaction = GlassVoiceCapsuleInteraction(source: f, at: now, recording: recording,
            recordAt: recording ? now : interaction?.recordAt ?? now, stoppedAt: recording ? nil : now)
        UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: recording ? 0.7 : 0.5)
        scheduleResume()
    }
    private func touch(size: CGSize) -> some Gesture {
        DragGesture(minimumDistance: 0).onChanged { value in
            let now = Date.now.timeIntervalSinceReferenceDate
            if !touching {
                let f = frame(at: now), offset = reduced ? CGSize.zero : drag.value(at: now)
                let cx = size.width / 2 + offset.width, cy = size.height * 488 / 852 + f.floatY + offset.height
                guard abs(value.startLocation.x - cx) < f.width / 2 + 12,
                      abs(value.startLocation.y - cy) < f.height / 2 + 12 else { return }
                takeOver(at: now); touching = true
            }
            resume?.cancel(); drag.retarget(value.translation, at: now, releasing: false)
        }.onEnded { value in
            guard touching else { return }; touching = false
            let now = Date.now.timeIntervalSinceReferenceDate
            let offset = reduced ? CGSize.zero : drag.value(at: now)
            drag.retarget(.zero, at: now, releasing: true)
            if hypot(value.translation.width, value.translation.height) < 8 {
                let f = frame(at: now)
                let angle = (reduced ? 0 : f.angle + min(8, max(-8, offset.width / 14))) * .pi / 180
                let dx = value.startLocation.x - size.width / 2 - offset.width
                let dy = value.startLocation.y - size.height * 488 / 852 - f.floatY - offset.height
                let localX = (dx * cos(angle) + dy * sin(angle)) / f.scale
                let localY = (-dx * sin(angle) + dy * cos(angle)) / f.scale
                if !f.recording || hypot(localX - (f.width / 2 - 50), localY) < 34 * f.droplet + 10 { toggle(at: now) }
            }
            scheduleResume()
        }
    }
    private func scheduleResume() {
        resume?.cancel()
        resume = Task { @MainActor in
            do {
                try await Task.sleep(for: .seconds(2))
                let now = Date.now.timeIntervalSinceReferenceDate, f = frame(at: Date.now.timeIntervalSinceReferenceDate)
                interaction = GlassVoiceCapsuleInteraction(source: f, at: now, recording: false,
                    recordAt: interaction?.recordAt ?? now,
                    stoppedAt: interaction?.stoppedAt ?? (f.recording ? now : nil))
                try await Task.sleep(for: .seconds(1.45))
                interaction = nil; settling = false; epoch = .now
            } catch { /* A new touch owns the clock. */ }
        }
    }
}

// MARK: - Timing contract, shared by all three implementations

struct GlassVoiceCapsuleFrame {
    var time = 0.0, amount = 0.0, scale = 1.0, content = 0.0, droplet = 0.2
    var level = 0.18, seconds = 0.0, digitAge = 1.0, timerScale = 0.86
    var floatY = 0.0, angle = 0.0, micPulse = 1.0
    var micRed = 0.0, listening = 0.0, saved = 0.0, sheen = -1.0
    var recording = false, digitRise = true
    var width: Double { 84 + 238 * amount }
    var height: Double { 84 + 12 * amount + 5 * level * GlassVoiceCapsuleMath.clamp(amount) }
    static func automatic(_ time: Double) -> Self {
        let t = time.truncatingRemainder(dividingBy: GlassVoiceCapsule.loop)
        var f = Self(); f.time = t
        let start = GlassVoiceCapsuleMath.spring(t - 0.92, response: 0.55, damping: 0.62, duration: 0.98)
        let stop = GlassVoiceCapsuleMath.spring(t - 6.82, response: 0.55, damping: 0.62, duration: 0.98)
        let raw = start * (1 - stop)
        // Rubber-band the reverse overshoot so the resting circle does not pinch shut.
        f.amount = raw < 0 ? raw / (1 + abs(raw) * 14) : raw
        let r = GlassVoiceCapsuleMath.clamp(f.amount)
        f.scale = t < 0.92 ? 1 - 0.07 * GlassVoiceCapsuleMath.out((t - 0.8) / 0.12) : 0.93 + 0.07 * start
        f.content = GlassVoiceCapsuleMath.out((t - 1.15) / 0.45) * (1 - pow(GlassVoiceCapsuleMath.clamp((t - 6.82) / 0.38), 3))
        f.timerScale = (0.86 + 0.14 * GlassVoiceCapsuleMath.out((t - 1.15) / 0.45)) * (1 - 0.7 * GlassVoiceCapsuleMath.out((t - 6.82) / 0.38))
        f.droplet = (0.2 + 0.8 * GlassVoiceCapsuleMath.spring(t - 1.2, response: 0.45, damping: 0.58, duration: 0.8))
            * (1 - 0.12 * GlassVoiceCapsuleMath.out((t - 6.7) / 0.12))
            * (1 - 0.7 * GlassVoiceCapsuleMath.out((t - 6.82) / 0.38))
        f.seconds = min(5, max(0, t - 1.7))
        f.digitAge = max(0, t - 1.7 - floor(f.seconds))
        if t >= 1.7 && t < 6.82 { f.level = r * GlassVoiceCapsuleMath.voice(f.seconds) }
        else if t >= 6.82 {
            let peak = GlassVoiceCapsuleMath.voice(5)
            f.level = peak + (0.18 - peak) * GlassVoiceCapsuleMath.out((t - 6.82) / 0.78)
        }
        f.micRed = GlassVoiceCapsuleMath.out((t - 0.92) / 0.3) * (1 - GlassVoiceCapsuleMath.out((t - 6.82) / 0.58))
        f.recording = t >= 0.92 && t < 6.82
        f.listening = GlassVoiceCapsuleMath.out((t - 0.92) / 0.25) * (1 - GlassVoiceCapsuleMath.out((t - 6.82) / 0.25))
        f.saved = GlassVoiceCapsuleMath.out((t - 6.82) / 0.25) * (1 - GlassVoiceCapsuleMath.out((t - 8.02) / 0.25))
        if t >= 1.7 && t < 6.7 {
            let phase = 2 * Double.pi * (t - 1.7) / 5
            // Fade the non-zero sine angle at each endpoint instead of introducing a jerk.
            let envelope = GlassVoiceCapsuleMath.smooth((t - 1.7) / 0.25) * (1 - GlassVoiceCapsuleMath.smooth((t - 6.45) / 0.25))
            f.floatY = -6 * sin(2 * phase) * r * envelope
            f.angle = 1.6 * sin(phase + 0.6) * r * envelope
            f.micPulse = 1 + 0.08 * pow(sin(Double.pi * (t - 1.7)), 2) * envelope
        }
        if t > 7.9 && t < 8.7 { f.sheen = GlassVoiceCapsuleMath.smooth((t - 7.9) / 0.8) }
        return f
    }
}

enum GlassVoiceCapsuleMath {
    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func out(_ x: Double) -> Double { 1 - pow(1 - clamp(x), 3) }
    static func smooth(_ x: Double) -> Double { let u = clamp(x); return u * u * (3 - 2 * u) }
    static func spring(_ age: Double, response: Double, damping: Double, duration: Double) -> Double {
        guard age > 0 else { return 0 }; guard age < duration else { return 1 }
        let w = 2 * Double.pi / response, wd = w * sqrt(1 - damping * damping)
        let value = 1 - exp(-damping * w * age) * (cos(wd * age) + damping * w / wd * sin(wd * age))
        let tail = smooth((age - duration + 0.16) / 0.16)
        return value + (1 - value) * tail
    }
    static func voice(_ seconds: Double) -> Double {
        clamp(0.46 + 0.30 * sin(2 * .pi * 1.5 * seconds) + 0.24 * sin(2 * .pi * 3.5 * seconds + 1.1))
    }
    /// Deterministic quadrature of a 90 ms one-pole response, including the clamped syllables.
    static func filteredVoice(_ seconds: Double) -> Double {
        let dt = 0.54 / 16.0
        var sum = 0.0, weight = 0.0
        for i in 0..<16 {
            let age = (Double(i) + 0.5) * dt, w = exp(-age / 0.09)
            sum += voice(max(0, seconds - age)) * w; weight += w
        }
        return sum / weight
    }
}

struct GlassVoiceCapsuleInteraction {
    var source: GlassVoiceCapsuleFrame
    var at: Double
    var recording: Bool
    var recordAt: Double
    var stoppedAt: Double?
    func frame(at now: Double, reduced: Bool) -> GlassVoiceCapsuleFrame {
        let age = max(0, now - at)
        let p = reduced ? GlassVoiceCapsuleMath.smooth(age / 0.3) : GlassVoiceCapsuleMath.spring(age, response: 0.55, damping: 0.62, duration: 0.98)
        let q = GlassVoiceCapsuleMath.out(age / 0.38), target = recording ? 1.0 : 0.0
        var f = source
        let raw = source.amount + (target - source.amount) * p
        let lower = min(0, source.amount), over = raw - lower
        f.amount = over < 0 ? lower + over / (1 + abs(over) * 14) : raw
        let r = GlassVoiceCapsuleMath.clamp(f.amount)
        f.time = source.time + age; f.scale = source.scale + (1 - source.scale) * p
        f.content = source.content + (target - source.content) * q
        f.timerScale = source.timerScale + ((recording ? 1 : 0.3) - source.timerScale) * q
        let d = reduced ? q : GlassVoiceCapsuleMath.spring(age, response: 0.45, damping: 0.58, duration: 0.8)
        f.droplet = source.droplet + ((recording ? 1 : 0.3) - source.droplet) * d
        f.seconds = max(0, (stoppedAt ?? now) - recordAt)
        f.digitAge = floor(f.seconds) == floor(source.seconds) ? source.digitAge + age : max(0, now - recordAt - floor(f.seconds))
        let level = recording ? (reduced ? 0.55 : GlassVoiceCapsuleMath.filteredVoice(f.seconds)) : 0.18
        f.level = source.level + (level - source.level) * GlassVoiceCapsuleMath.smooth(age / (reduced ? 0.4 : 0.09))
        f.micRed = source.micRed + (target - source.micRed) * GlassVoiceCapsuleMath.out(age / 0.3)
        f.listening = source.listening + (target - source.listening) * GlassVoiceCapsuleMath.out(age / 0.25)
        let saved = stoppedAt.map { 1 - GlassVoiceCapsuleMath.out((now - $0 - 1.2) / 0.25) } ?? 0
        f.saved = source.saved + (saved - source.saved) * GlassVoiceCapsuleMath.out(age / 0.25)
        let phase = 2 * Double.pi * f.seconds / 5
        let floatY = recording && !reduced ? -6 * sin(2 * phase) * r : 0
        let angle = recording && !reduced ? 1.6 * sin(phase + 0.6) * r : 0
        f.floatY = source.floatY + (floatY - source.floatY) * GlassVoiceCapsuleMath.smooth(age / 0.3)
        f.angle = source.angle + (angle - source.angle) * GlassVoiceCapsuleMath.smooth(age / 0.3)
        f.micPulse = reduced ? 1 : 1 + 0.08 * pow(sin(.pi * f.seconds), 2) * r
        f.recording = recording; f.sheen = -1; f.digitRise = !reduced
        return f
    }
}

struct GlassVoiceCapsuleDrag {
    var from = CGSize.zero, target = CGSize.zero
    var velocity = CGSize.zero
    var at = 0.0
    var releasing = false
    func value(at now: Double) -> CGSize {
        state(at: now).position
    }
    private func state(at now: Double) -> (position: CGSize, velocity: CGSize) {
        let t = max(0, now - at)
        guard t < 1.2 else { return (target, .zero) }
        let w = 2 * Double.pi / (releasing ? 0.5 : 0.38), z = releasing ? 0.55 : 0.6
        let a = z * w, b = w * sqrt(1 - z * z), e = exp(-a * t)
        func axis(_ start: Double, _ end: Double, _ speed: Double) -> (Double, Double) {
            let d = start - end, k = (speed + a * d) / b
            return (end + e * (d * cos(b * t) + k * sin(b * t)),
                    e * ((k * b - a * d) * cos(b * t) + (-d * b - a * k) * sin(b * t)))
        }
        let x = axis(from.width, target.width, velocity.width), y = axis(from.height, target.height, velocity.height)
        return (CGSize(width: x.0, height: y.0), CGSize(width: x.1, height: y.1))
    }
    mutating func retarget(_ next: CGSize, at now: Double, releasing: Bool) {
        let current = state(at: now)
        from = current.position; velocity = current.velocity; target = next; at = now; self.releasing = releasing
    }
}

enum GlassVoiceCapsuleArtwork {
    static let image: Image = {
        guard let url = Bundle.main.url(forResource: "glassvoicecapsule_spheres", withExtension: "jpg"),
              let original = UIImage(contentsOfFile: url.path) else { return Image(uiImage: UIImage()) }
        return Image(uiImage: original.preparingForDisplay() ?? original)
    }()
}

// MARK: - Magnified body, separate thick rim lens, light, then content

struct GlassVoiceCapsulePainter {
    var size: CGSize
    var center: CGPoint
    var angle: Double
    var frame: GlassVoiceCapsuleFrame
    static func color(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255,
              blue: Double(hex & 255) / 255, opacity: alpha)
    }
    private func capsule(_ rect: CGRect) -> Path { Path(roundedRect: rect, cornerRadius: rect.height / 2) }
    func draw(_ context: GraphicsContext) {
        let f = frame
        context.fill(Path(CGRect(x: 0, y: 0, width: size.width, height: 220 * size.height / 852)),
            with: .linearGradient(Gradient(colors: [Self.color(0x20324F, 0.28), .clear]), startPoint: .zero, endPoint: CGPoint(x: 0, y: 220 * size.height / 852)))
        context.draw(Text("Say it out loud").font(.system(size: 28, weight: .semibold, design: .rounded)).foregroundColor(.white), at: CGPoint(x: size.width / 2, y: 92 * size.height / 852))
        for (label, opacity) in [("Tap the mic to start a note", max(0, 1 - f.listening - f.saved)), ("Listening…", f.listening), ("Saved to Drafts", f.saved)] {
            var caption = context; caption.opacity = opacity
            caption.draw(Text(label).font(.system(size: 15)).foregroundColor(.white.opacity(0.72)), at: CGPoint(x: size.width / 2, y: 132 * size.height / 852))
        }
        var glass = context
        glass.translateBy(x: center.x, y: center.y); glass.rotate(by: .degrees(angle)); glass.scaleBy(x: f.scale, y: f.scale)
        let rect = CGRect(x: -f.width / 2, y: -f.height / 2, width: f.width, height: f.height)
        drawGlass(glass, rect: rect, zoom: 1.30, droplet: false, localCenter: .zero)
        var mic = glass
        mic.translateBy(x: -109 * f.amount, y: 0); mic.scaleBy(x: f.micPulse, y: f.micPulse)
        drawMic(mic, red: f.micRed)
        if f.content > 0.001 {
            var contents = glass; contents.opacity = f.content
            let digitScale = f.timerScale
            contents.scaleBy(x: digitScale, y: digitScale)
            let whole = Int(f.seconds)
            let swap = f.digitRise && whole > 0 ? GlassVoiceCapsuleMath.clamp(f.digitAge / 0.18) : 1
            drawTimer(contents, seconds: whole, y: 4 * (1 - swap), opacity: swap)
            if swap < 1 { drawTimer(contents, seconds: whole - 1, y: -4 * swap, opacity: 1 - swap) }
            var stop = glass; stop.opacity = f.content
            let x = f.width / 2 - 50
            stop.translateBy(x: x, y: 0); stop.scaleBy(x: f.droplet, y: f.droplet)
            drawGlass(stop, rect: CGRect(x: -34, y: -34, width: 68, height: 68), zoom: 1.28, droplet: true, localCenter: CGPoint(x: x, y: 0))
            stop.fill(Path(roundedRect: CGRect(x: -12, y: -12, width: 24, height: 24), cornerRadius: 7), with: .color(.white))
        }
    }
    private func drawTimer(_ context: GraphicsContext, seconds: Int, y: Double, opacity: Double) {
        var c = context; c.opacity *= opacity
        let label = String(format: "%02d:%02d", seconds / 60, seconds % 60)
        c.draw(Text(label).font(.system(size: 32, weight: .semibold, design: .rounded)).monospacedDigit().foregroundColor(.white), at: CGPoint(x: 4, y: y))
    }
    private func sample(_ context: GraphicsContext, zoom: Double, localCenter: CGPoint, droplet: Bool) {
        var c = context
        // Undo object rotation/scale when sampling; the room stays fixed while glass moves.
        let extra = droplet ? frame.droplet : 1
        c.scaleBy(x: 1 / max(0.01, frame.scale * extra), y: 1 / max(0.01, frame.scale * extra)); c.rotate(by: .degrees(-angle))
        let radians = angle * .pi / 180
        let worldCenter = CGPoint(x: center.x + localCenter.x * cos(radians) * frame.scale, y: center.y + localCenter.x * sin(radians) * frame.scale)
        let cover = max(size.width / 1024, size.height / 1536), w = 1024 * max(size.width / 1024, size.height / 1536)
        let h = 1536 * cover
        c.draw(c.resolve(GlassVoiceCapsuleArtwork.image), in: CGRect(x: ((size.width - w) / 2 - worldCenter.x) * zoom,
            y: ((size.height - h) / 2 - worldCenter.y) * zoom, width: w * zoom, height: h * zoom))
    }
    private func drawGlass(_ context: GraphicsContext, rect: CGRect, zoom: Double, droplet: Bool, localCenter: CGPoint) {
        let outline = capsule(rect)
        for (blur, dy, alpha) in [(22.0, 12.0, 0.30), (6.0, 3.0, 0.18)] {
            context.drawLayer { layer in
                layer.addFilter(.blur(radius: blur)); layer.translateBy(x: 0, y: dy)
                layer.fill(outline, with: .color(Self.color(0x0B1830, alpha)))
            }
        }
        var clipped = context; clipped.clip(to: outline)
        clipped.drawLayer { layer in
            layer.addFilter(.blur(radius: 1.0)); sample(layer, zoom: zoom, localCenter: localCenter, droplet: droplet)
        }
        var rim = clipped
        rim.clipToLayer { mask in
            mask.addFilter(.blur(radius: 1.5)); mask.stroke(capsule(rect.insetBy(dx: 4.5, dy: 4.5)), with: .color(.white), lineWidth: 9)
        }
        sample(rim, zoom: 1.42, localCenter: localCenter, droplet: droplet)
        clipped.fill(outline, with: .color(.white.opacity(0.07)))
        clipped.fill(outline, with: .linearGradient(Gradient(colors: [.white.opacity(0.26), .clear]),
            startPoint: CGPoint(x: 0, y: rect.minY), endPoint: CGPoint(x: 0, y: rect.minY + rect.height * 0.46)))
        if !droplet {
            let r = GlassVoiceCapsuleMath.clamp(frame.amount), colors: [UInt32] = [0xFF8A3D, 0xF23FA6, 0x3D8BFF]
            for i in 0..<3 {
                let strength = i == 1 ? 1 : r
                let drift = 10 * sin(4 * .pi * frame.time / GlassVoiceCapsule.loop + Double(i) * 2.1) * r
                let x = rect.minX + rect.width * (0.22 + Double(i) * 0.28) + drift, h = 14 + 46 * frame.level
                clipped.drawLayer { layer in
                    layer.addFilter(.blur(radius: 20))
                    layer.fill(Path(ellipseIn: CGRect(x: x - rect.width * 0.21, y: rect.maxY - 10 - h / 2, width: rect.width * 0.42, height: h)),
                        with: .color(Self.color(colors[i], (0.25 + 0.65 * frame.level) * strength)))
                }
            }
            clipped.drawLayer { layer in
                layer.addFilter(.blur(radius: 20))
                layer.fill(Path(ellipseIn: CGRect(x: -rect.width * 0.21, y: rect.maxY - 42, width: rect.width * 0.42, height: 40)), with: .color(Self.color(0x8E5BFF, 0.30 * frame.level * r)))
            }
            if frame.sheen >= 0 {
                let x = rect.minX - 30 + (rect.width + 60) * frame.sheen
                clipped.drawLayer { layer in
                    layer.addFilter(.blur(radius: 5))
                    layer.fill(Path(CGRect(x: x - 15, y: rect.minY, width: 30, height: rect.height)), with: .color(.white.opacity(0.35 * sin(.pi * frame.sheen))))
                }
            }
        }
        if !droplet {
            clipped.drawLayer { layer in
                layer.addFilter(.blur(radius: 6)); layer.stroke(capsule(rect.offsetBy(dx: 0, dy: -3)), with: .color(Self.color(0x0B1830, 0.18)), lineWidth: 6)
            }
        }
        context.stroke(capsule(rect.insetBy(dx: 0.75, dy: 0.75)),
            with: .linearGradient(Gradient(stops: [.init(color: .white.opacity(0.88), location: 0), .init(color: .white.opacity(0.12), location: 0.5), .init(color: .white.opacity(0.46), location: 1)]),
                startPoint: CGPoint(x: rect.minX, y: rect.minY), endPoint: CGPoint(x: rect.maxX, y: rect.maxY)), lineWidth: 1.5)
        context.stroke(capsule(rect.insetBy(dx: 2, dy: 2)), with: .color(.white.opacity(0.22)), lineWidth: 0.75)
        clipped.drawLayer { layer in
            layer.addFilter(.blur(radius: 1)); layer.translateBy(x: rect.minX + 26, y: rect.minY + 13); layer.rotate(by: .degrees(-18))
            layer.fill(Path(ellipseIn: CGRect(x: -10, y: -3.5, width: 20, height: 7)), with: .color(.white.opacity(0.75)))
        }
        clipped.fill(Path(ellipseIn: CGRect(x: rect.maxX - 36, y: rect.maxY - 14.5, width: 12, height: 5)), with: .color(.white.opacity(0.35)))
    }
    private func drawMic(_ context: GraphicsContext, red: Double) {
        let ink = Color(.sRGB, red: 1, green: 1 + (77.0 / 255 - 1) * red, blue: 1 + (94.0 / 255 - 1) * red, opacity: 1)
        context.fill(Path(roundedRect: CGRect(x: -5, y: -14, width: 10, height: 18), cornerRadius: 5), with: .color(ink))
        var p = Path(); p.move(to: CGPoint(x: -9, y: -3)); p.addLine(to: CGPoint(x: -9, y: 0))
        p.addCurve(to: CGPoint(x: 9, y: 0), control1: CGPoint(x: -9, y: 12), control2: CGPoint(x: 9, y: 12))
        p.addLine(to: CGPoint(x: 9, y: -3)); p.move(to: CGPoint(x: 0, y: 9)); p.addLine(to: CGPoint(x: 0, y: 14))
        p.move(to: CGPoint(x: -5, y: 14)); p.addLine(to: CGPoint(x: 5, y: 14))
        context.stroke(p, with: .color(ink), style: StrokeStyle(lineWidth: 2, lineCap: .round))
    }
}
