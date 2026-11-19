import SwiftUI
import UIKit

/// A photo editor's Light tool on a product shot. The photo is a G-buffer rendered in Blender (a pocket watch on a
/// seamless paper sweep: colour, normals, depth, roughness, metal, occlusion) shaded live by a Metal shader, so the
/// light really moves: drag the softbox over the photo, pick Studio, Golden hour or Moody blue, set the intensity, tap
/// Compare and drag the divider to see the flat original. A 16-second film uses every control once; it stops when
/// touched and comes back 2 s after the last touch. Reduce Motion (or `autoPlay` off) shows the poster and leaves every
/// control to the finger.
struct ProductRelightStudio: View {
    var autoPlay: Bool = true

    static let loop: Double = ProductRelightStudioMotion.loop

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var touch = ProductRelightStudioTouch()
    @State private var awake = false
    @State private var slideEndedAt = Date.distantPast

    private typealias M = ProductRelightStudioMotion
    private var film: Bool { autoPlay && !reduceMotion }

    static let ink = Color(.sRGB, red: 0xF5 / 255, green: 0xF5 / 255, blue: 0xF4 / 255)
    static let secondary = ink.opacity(0.64)
    static let accent = Color(.sRGB, red: 1, green: 0xD6 / 255, blue: 0x0A / 255)
    static let bg = Color(.sRGB, red: 0x0C / 255, green: 0x0C / 255, blue: 0x0D / 255)

    var body: some View {
        GeometryReader { outer in
            let insets = outer.safeAreaInsets
            let width = outer.size.width + insets.leading + insets.trailing
            let height = outer.size.height + insets.top + insets.bottom
            let l = ProductRelightStudioLayout(width: width, height: height, top: insets.top, bottom: insets.bottom)
            TimelineView(.animation(minimumInterval: nil, paused: !film && !awake)) { tl in
                let v = touch.frame(at: tl.date, film: film)
                screen(v, l)
            }
            .frame(width: width, height: height, alignment: .topLeading)
            .offset(x: -insets.leading, y: -insets.top)
        }
        .background(Self.bg.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear {
            _ = ProductRelightStudioImages.atlas // decode and pack the passes before the clock starts
            touch.origin = Date()
        }
    }

    private func screen(_ v: ProductRelightStudioValues, _ l: ProductRelightStudioLayout) -> some View {
        let s = l.scale
        return ZStack(alignment: .topLeading) {
            // the bar
            HStack {
                Color.clear.frame(width: 80 * s, height: 1)
                Spacer(minLength: 0)
                Text("Light").font(.system(size: 17 * s, weight: .semibold)).foregroundStyle(Self.ink)
                Spacer(minLength: 0)
                Button(action: compare) {
                    Text("Compare")
                        .font(.system(size: 17 * s, weight: .semibold))
                        .foregroundStyle(Self.accent)
                        .opacity(1 - 0.55 * v.dip)
                        .frame(width: 80 * s, alignment: .trailing)
                        .fixedSize()
                        .contentShape(Rectangle().inset(by: -10))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Compare with the original")
            }
            .padding(.horizontal, 20 * s)
            .frame(width: l.width, height: 44 * s)
            .offset(y: l.top)

            photo(v, l)
                .offset(x: l.side, y: l.photoTop)

            controls(v, l)
                .frame(width: l.column, alignment: .topLeading)
                .environment(\.colorScheme, .dark)
                .offset(x: (l.width - l.column) / 2, y: l.photoTop + l.size + 20 * s)
        }
        .frame(width: l.width, height: l.height, alignment: .topLeading)
    }

    // MARK: the photo

    private func photo(_ v: ProductRelightStudioValues, _ l: ProductRelightStudioLayout) -> some View {
        let size = l.size, s = l.scale
        let R = ProductRelightStudioImages.self
        return ZStack(alignment: .topLeading) {
            Rectangle()
                .fill(.white)
                .frame(width: size, height: size)
                .colorEffect(ShaderLibrary.productRelightStudioShade(
                    .floatArray(v.uniforms(size: size)), .image(R.atlas)))
            Canvas { ctx, _ in
                drawSplit(&ctx, v, size: size, s: s)
                drawBox(&ctx, v, size: size, s: s)
            }
            .frame(width: size, height: size)
            pill("Before", s)
                .opacity(0.9 * M.clamp01((v.split - 0.14) * 8))
                .offset(x: 12 * s, y: 12 * s)
            pill("After", s)
                .opacity(0.9 * M.clamp01((0.86 - v.split) * 8) * M.clamp01(v.split * 30))
                .frame(width: size - 12 * s, alignment: .trailing)
                .offset(y: 12 * s)
        }
        .frame(width: size, height: size, alignment: .topLeading)
        .clipped()
        .contentShape(Rectangle())
        .gesture(pan(size: size))
        .accessibilityElement()
        .accessibilityLabel("Product photo of a pocket watch")
        .accessibilityHint("Drag on the photo to move the softbox; drag the divider to compare")
    }

    private func pill(_ text: String, _ s: Double) -> some View {
        Text(text)
            .font(.system(size: 12 * s, weight: .medium))
            .foregroundStyle(.white)
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(Capsule().fill(Color.black.opacity(0.28)))
            .allowsHitTesting(false)
    }

    private func drawSplit(_ ctx: inout GraphicsContext, _ v: ProductRelightStudioValues, size: Double, s: Double) {
        let shown = M.clamp01(v.split * 30)
        guard shown > 0 else { return }
        var g = ctx
        g.opacity = shown
        let x = v.split * size
        var line = Path()
        line.move(to: CGPoint(x: x, y: 0))
        line.addLine(to: CGPoint(x: x, y: size))
        g.stroke(line, with: .color(.white.opacity(0.7)), lineWidth: 1)
        g.translateBy(x: x, y: size * 0.5)
        let ks = 1 + 0.12 * v.splitGrab
        g.scaleBy(x: ks, y: ks)
        let knob = 11 * s
        g.drawLayer { b in
            b.addFilter(.blur(radius: 2 * s))
            b.fill(Path(ellipseIn: CGRect(x: -(knob + 1), y: 1.5 * s - (knob + 1), width: 2 * (knob + 1), height: 2 * (knob + 1))),
                   with: .color(.black.opacity(0.18)))
        }
        g.fill(Path(ellipseIn: CGRect(x: -knob, y: -knob, width: 2 * knob, height: 2 * knob)), with: .color(.white.opacity(0.92)))
        var chev = Path()
        chev.move(to: CGPoint(x: -3 * s, y: -3.5 * s))
        chev.addLine(to: CGPoint(x: -6 * s, y: 0))
        chev.addLine(to: CGPoint(x: -3 * s, y: 3.5 * s))
        chev.move(to: CGPoint(x: 3 * s, y: -3.5 * s))
        chev.addLine(to: CGPoint(x: 6 * s, y: 0))
        chev.addLine(to: CGPoint(x: 3 * s, y: 3.5 * s))
        g.stroke(chev, with: .color(Color(.sRGB, red: 0x3A / 255, green: 0x3A / 255, blue: 0x3C / 255)),
                 style: StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round))
    }

    private func drawBox(_ ctx: inout GraphicsContext, _ v: ProductRelightStudioValues, size: Double, s: Double) {
        var g = ctx
        g.opacity = 0.3 + 0.7 * v.grab
        g.translateBy(x: size * (0.5 + v.lx), y: size * (0.5 - v.ly))
        let sc = 1 + 0.08 * v.grab
        g.scaleBy(x: sc, y: sc)
        let bw = 22 * s, bh = 30 * s
        let rect = CGRect(x: -bw / 2, y: -bh / 2, width: bw, height: bh)
        let rr = Path(roundedRect: rect, cornerRadius: 6 * s, style: .continuous)
        g.drawLayer { b in
            b.addFilter(.blur(radius: 1))
            b.stroke(rr, with: .color(.black.opacity(0.22)), lineWidth: 3)
        }
        g.stroke(rr, with: .color(.white.opacity(0.95)), lineWidth: 1.25)
        g.fill(Path(ellipseIn: CGRect(x: -4 * s, y: -4 * s, width: 8 * s, height: 8 * s)), with: .color(.black.opacity(0.2)))
        g.fill(Path(ellipseIn: CGRect(x: -3 * s, y: -3 * s, width: 6 * s, height: 6 * s)), with: .color(.white))
    }

    // MARK: the controls

    private func controls(_ v: ProductRelightStudioValues, _ l: ProductRelightStudioLayout) -> some View {
        let s = l.scale
        let ws = [v.w0, v.w1, v.w2]
        let selected = ws[0] >= ws[1] && ws[0] >= ws[2] ? 0 : (ws[1] >= ws[2] ? 1 : 2)
        let level = (v.level * 100).rounded() / 100
        return VStack(alignment: .leading, spacing: 0) {
            ZStack(alignment: .topLeading) {
                ForEach(0..<3, id: \.self) { i in
                    let k = M.clamp01(ws[i])
                    VStack(alignment: .leading, spacing: 2 * s) {
                        Text(M.presetNames[i])
                            .font(.system(size: 28 * s, weight: .bold))
                            .tracking(-0.4)
                            .foregroundStyle(Self.ink)
                            .frame(height: 34 * s, alignment: .leading)
                        Text(M.presetHints[i])
                            .font(.system(size: 15 * s))
                            .foregroundStyle(Self.secondary)
                            .lineLimit(1)
                            .frame(height: 20 * s, alignment: .leading)
                    }
                    .opacity(M.clamp01((k - 0.35) / 0.65))
                    .offset(y: (1 - k) * 6 * s)
                    .accessibilityHidden(i != selected)
                }
            }
            .frame(height: 58 * s, alignment: .topLeading)
            ProductRelightStudioPresetPicker(selection: selected) { choose($0) }
                .equatable()
            .frame(height: 32)
            .padding(.top, 14 * s)
            HStack(spacing: 12 * s) {
                Text("Intensity")
                    .font(.system(size: 15 * s, weight: .medium))
                    .foregroundStyle(Self.ink)
                    .fixedSize()
                Slider(value: Binding(get: { level }, set: { setIntensity($0) }), in: 0...1) { editing in
                    sliding(editing)
                }
                .tint(Self.ink)
                .frame(minWidth: 0, maxWidth: .infinity)
                Text("\(Int((level * 100).rounded()))")
                    .font(.system(size: 15 * s, weight: .medium).monospacedDigit())
                    .foregroundStyle(Self.secondary)
                    .frame(width: 32 * s, alignment: .trailing)
            }
            .frame(height: 30)
            .padding(.top, 18 * s)
        }
    }

    // MARK: touch

    private func pan(size: Double) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { g in
                let now = Date()
                let p = g.location
                if !touch.dragging && !touch.draggingSplit {
                    touch.takeOver(at: now, film: film)
                    let split = touch.split.at(now)
                    if split > 0.02 && abs(p.x - split * size) < 28 {
                        touch.draggingSplit = true
                        touch.splitGrab = .to(1, from: touch.splitGrab.at(now), start: now, duration: 0.16)
                    } else {
                        touch.dragging = true
                        touch.grab = .to(1, from: touch.grab.at(now), start: now, duration: 0.16)
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    }
                }
                if touch.draggingSplit {
                    touch.split = .constant(M.clamp01(p.x / size))
                } else {
                    let r = M.lightReach
                    touch.lx = max(-r, min(r, p.x / size - 0.5))
                    touch.ly = max(-r, min(r, -(p.y / size - 0.5)))
                }
                touch.lastActivity = now
                wake()
            }
            .onEnded { _ in
                let now = Date()
                touch.dragging = false
                touch.draggingSplit = false
                touch.grab = .to(0, from: touch.grab.at(now), start: now, duration: 0.26)
                touch.splitGrab = .to(0, from: touch.splitGrab.at(now), start: now, duration: 0.26)
                touch.lastActivity = now
                wake()
            }
    }

    private func choose(_ i: Int) {
        let now = Date()
        UISelectionFeedbackGenerator().selectionChanged()
        touch.takeOver(at: now, film: film)
        let w = [touch.w0, touch.w1, touch.w2].map { $0.at(now) }
        touch.w0 = .to(i == 0 ? 1 : 0, from: w[0], start: now, duration: M.rigDur, bezier: true)
        touch.w1 = .to(i == 1 ? 1 : 0, from: w[1], start: now, duration: M.rigDur, bezier: true)
        touch.w2 = .to(i == 2 ? 1 : 0, from: w[2], start: now, duration: M.rigDur, bezier: true)
        touch.lastActivity = now.addingTimeInterval(M.rigDur)
        wake()
    }

    private func setIntensity(_ v: Double) {
        let now = Date()
        touch.takeOver(at: now, film: film)
        touch.level = v
        touch.lastActivity = now
        wake()
    }

    private func sliding(_ active: Bool) {
        let now = Date()
        // Once its thumb has glided to rest (a preset tapped meanwhile) the slider can report the end of an edit
        // and a new start in the same instant, with no finger on it; that start was never ended and kept the film
        // from coming back, so it is ignored.
        if active && now.timeIntervalSince(slideEndedAt) < 0.1 { return }
        if active { touch.takeOver(at: now, film: film) }
        touch.sliding = active
        if !active { slideEndedAt = now }
        touch.lastActivity = now
        wake()
    }

    private func compare() {
        let now = Date()
        UISelectionFeedbackGenerator().selectionChanged()
        touch.takeOver(at: now, film: film)
        let cur = touch.split.at(now)
        touch.split = .to(cur > 0.02 ? 0 : 0.5, from: cur, start: now, duration: 0.5, bezier: true)
        touch.lastActivity = now
        wake()
    }

    /// Keeps the timeline running while a touch animates something with the film off.
    private func wake() {
        guard !film else { return }
        awake = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 1) {
            let quiet = Date().timeIntervalSince(touch.lastActivity) > 0.9
            if quiet && !touch.dragging && !touch.draggingSplit && !touch.sliding { awake = false } else { wake() }
        }
    }
}

// MARK: - layout and resources

struct ProductRelightStudioLayout {
    let width: Double
    let height: Double
    let top: Double
    let scale: Double
    let photoTop: Double
    let size: Double
    let side: Double
    let column: Double

    init(width: Double, height: Double, top: Double, bottom: Double) {
        self.width = width
        self.height = height
        self.top = top
        scale = min(1.1, max(0.88, width / 393))
        photoTop = top + 44 * scale
        size = min(width, 560, height - photoTop - bottom - 250 * scale)
        side = (width - size) / 2
        column = min(width - 40 * scale, 520)
    }
}

enum ProductRelightStudioImages {
    /// The five passes in one 3 x 2 atlas of 1024 px tiles (SwiftUI takes few texture arguments): albedo, gbuffer,
    /// material on top; matte, before below. Drawn once, in sRGB with no colour conversion, so the data passes stay
    /// exactly as stored.
    static let atlas: Image = {
        let tile = 1024
        let names: [(String, String)] = [("albedo", "jpg"), ("gbuffer", "png"), ("material", "jpg"), ("matte", "png"), ("before", "jpg")]
        guard let space = CGColorSpace(name: CGColorSpace.sRGB),
              let ctx = CGContext(data: nil, width: 3 * tile, height: 2 * tile, bitsPerComponent: 8, bytesPerRow: 0, space: space,
                                  bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue) else { return Image(uiImage: UIImage()) }
        ctx.interpolationQuality = .none
        for (i, n) in names.enumerated() {
            guard let url = Bundle.main.url(forResource: "ProductRelightStudio-\(n.0)", withExtension: n.1),
                  let cg = UIImage(contentsOfFile: url.path)?.cgImage else { continue }
            // CoreGraphics' origin is the bottom left
            ctx.draw(cg, in: CGRect(x: (i % 3) * tile, y: (1 - i / 3) * tile, width: tile, height: tile))
        }
        guard let out = ctx.makeImage() else { return Image(uiImage: UIImage()) }
        return Image(uiImage: UIImage(cgImage: out))
    }()
}

// MARK: - values

/// Everything one frame shows.
struct ProductRelightStudioValues {
    var lx: Double, ly: Double
    var w0: Double, w1: Double, w2: Double
    var level: Double
    var grab: Double
    var split: Double
    var splitGrab: Double
    var dip: Double

    private typealias M = ProductRelightStudioMotion

    static func film(_ t: Double) -> Self {
        let l = M.lightAt(t)
        let w = M.weightsAt(t)
        return .init(lx: l.0, ly: l.1, w0: w[0], w1: w[1], w2: w[2], level: M.levelAt(t), grab: M.grabAt(t),
                     split: M.splitAt(t), splitGrab: M.splitGrabAt(t), dip: M.compareDipAt(t))
    }

    static func mix(_ a: Self, _ b: Self, _ k: Double) -> Self {
        func m(_ x: Double, _ y: Double) -> Double { M.lerp(x, y, k) }
        return .init(lx: m(a.lx, b.lx), ly: m(a.ly, b.ly), w0: m(a.w0, b.w0), w1: m(a.w1, b.w1), w2: m(a.w2, b.w2),
                     level: m(a.level, b.level), grab: m(a.grab, b.grab), split: m(a.split, b.split),
                     splitGrab: m(a.splitGrab, b.splitGrab), dip: m(a.dip, b.dip))
    }

    /// The shader's uniforms; the slider's level scales the key's power. A system that hands the shader linear-light
    /// textures is undone in the shader (`decode`).
    func uniforms(size: Double) -> [Float] {
        let r = M.rigMix([w0, w1, w2])
        let k = r[3] * (max(0.04, level) / M.level)
        let u: [Double] = [size, lx, ly, r[4], r[0] * k, r[1] * k, r[2] * k, r[5] * r[8], r[6] * r[8], r[7] * r[8],
                           r[9], r[10], r[11], r[12], r[13], r[14], r[15], split, ProductRelightStudioValues.decode]
        return u.map { Float($0) }
    }

    static let decode: Double = 0
}

/// A value easing from one number to another from a moment on (or holding still).
struct ProductRelightStudioTween {
    var from: Double
    var to: Double
    var start: Date
    var duration: Double
    var bezier: Bool

    static func constant(_ v: Double) -> Self { .init(from: v, to: v, start: .distantPast, duration: 0, bezier: false) }
    static func to(_ v: Double, from: Double, start: Date, duration: Double, bezier: Bool = false) -> Self {
        .init(from: from, to: v, start: start, duration: duration, bezier: bezier)
    }

    func at(_ now: Date) -> Double {
        guard duration > 0 else { return to }
        let x = ProductRelightStudioMotion.clamp01(now.timeIntervalSince(start) / duration)
        return ProductRelightStudioMotion.lerp(from, to, bezier ? ProductRelightStudioMotion.easeBezier(x) : x)
    }

    func done(_ now: Date) -> Bool { now.timeIntervalSince(start) >= duration }
}

/// What a finger set: written only by touches, read every frame.
struct ProductRelightStudioTouch {
    private typealias M = ProductRelightStudioMotion
    typealias Tw = ProductRelightStudioTween

    var origin = Date()
    var manual = false
    var lx = 0.0, ly = 0.0
    var w0 = Tw.constant(1), w1 = Tw.constant(0), w2 = Tw.constant(0)
    var level = M.level
    var split = Tw.constant(0)
    var grab = Tw.constant(0)
    var splitGrab = Tw.constant(0)
    var dragging = false
    var draggingSplit = false
    var sliding = false
    var lastActivity = Date.distantPast

    private func manualValues(_ now: Date) -> ProductRelightStudioValues {
        .init(lx: lx, ly: ly, w0: w0.at(now), w1: w1.at(now), w2: w2.at(now), level: level, grab: grab.at(now),
              split: split.at(now), splitGrab: splitGrab.at(now), dip: 0)
    }

    private enum Phase { case manual, returning(Double), auto(Double) }

    private func phase(_ now: Date, film: Bool) -> Phase {
        if !manual {
            guard film else { return .manual }
            return .auto(max(0, now.timeIntervalSince(origin)).truncatingRemainder(dividingBy: M.loop))
        }
        if !film || dragging || draggingSplit || sliding { return .manual }
        let quiet = now.timeIntervalSince(lastActivity) - M.idle
        if quiet <= 0 { return .manual }
        if quiet < M.returnDur { return .returning(M.easeBezier(quiet / M.returnDur)) }
        return .auto((quiet - M.returnDur).truncatingRemainder(dividingBy: M.loop))
    }

    func frame(at now: Date, film: Bool) -> ProductRelightStudioValues {
        switch phase(now, film: film) {
        case .manual: return manual ? manualValues(now) : .film(0)
        case .returning(let k): return .mix(manualValues(now), .film(0), k)
        case .auto(let t): return .film(t)
        }
    }

    /// Any touch takes the photo over where the film is.
    mutating func takeOver(at now: Date, film: Bool) {
        let ph = phase(now, film: film)
        if case .manual = ph, manual { return }
        let v = frame(at: now, film: film)
        manual = true
        lx = v.lx
        ly = v.ly
        w0 = .constant(v.w0)
        w1 = .constant(v.w1)
        w2 = .constant(v.w2)
        level = v.level
        split = .constant(v.split)
        grab = .constant(v.grab)
        splitGrab = .constant(v.splitGrab)
        lastActivity = now
    }
}

/// The preset control sits in a TimelineView that rebuilds it every frame, and a UISegmentedControl updated mid-touch
/// drops the touch, so it was dead to the finger. As an Equatable view SwiftUI only updates it when the selection
/// changes.
private struct ProductRelightStudioPresetPicker: View, Equatable {
    typealias M = ProductRelightStudioMotion
    let selection: Int
    let pick: (Int) -> Void

    static func == (a: Self, b: Self) -> Bool { a.selection == b.selection }

    var body: some View {
        Picker("Preset", selection: Binding(get: { selection }, set: { pick($0) })) {
            ForEach(0..<3, id: \.self) { i in Text(M.presetNames[i]).tag(i) }
        }
        .pickerStyle(.segmented)
    }
}
