import SwiftUI
import UIKit

/// Letterpress Headline — a print studio's new edition, "Press & Paper". On thick cotton paper the
/// headline "Made by hand." is printed one letter at a time: a metal type block (its face bearing
/// the mirrored letter) comes down, its shadow tightening, presses the letter into the paper in
/// vermilion ink and lifts away; then an ink roller sweeps the second line on, and the lamp swings
/// so the debossed walls catch the light. The loop is in LetterpressHeadlineMotion (loop 9 s).
///
/// Touch: press and hold the paper and the platen presses every letter at once: the blocks come
/// down in 0.25 s, then the impression deepens with the hold (full at about 1.1 s) and stays when
/// you let go. Dragging swings the lamp (sideways = round, up and down = higher and lower,
/// rubber-banded, sprung back on release). 2 s after the last touch the impression fades (0.9 s)
/// and the loop starts again. Reduced motion: the printed page, still.
struct LetterpressHeadline: View {
    /// Prints, inks, lights and clears the headline by itself, on a loop of `loop` s.
    var autoPlay = false

    static let loop = LetterpressHeadlineMotion.loop

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        // The block draws under the system bars and keeps its content clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            let layout = LetterpressHeadlineLayout(width: size.width, height: size.height, top: insets.top, bottom: insets.bottom)
            LetterpressHeadlineScene(layout: layout, autoPlay: autoPlay && !reduceMotion, reduced: reduceMotion)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .background(LetterpressHeadlinePalette.background.ignoresSafeArea())
    }
}

enum LetterpressHeadlinePalette {
    static func hex(_ v: UInt32, _ a: Double = 1) -> Color {
        Color(
            red: Double((v >> 16) & 0xFF) / 255,
            green: Double((v >> 8) & 0xFF) / 255,
            blue: Double(v & 0xFF) / 255,
            opacity: a
        )
    }

    static let background = hex(0xEFE8DC)
    static let ink = hex(0x2B2622)
    static let mute = hex(0x7B6F62)
    static let vermilion = hex(0xB4442F)
    static let rule = hex(0x2B2622, 0.25)
}

private struct LetterpressHeadlineScene: View {
    let layout: LetterpressHeadlineLayout
    let autoPlay: Bool
    let reduced: Bool

    @Environment(\.displayScale) private var displayScale
    @State private var engine = LetterpressHeadlineEngine()
    /// Keeps the timeline running while a touch's animations play outside autoPlay.
    @State private var awake = false
    @State private var reserved = false
    @State private var down = false

    private typealias P = LetterpressHeadlinePalette
    private typealias M = LetterpressHeadlineMotion

    private static let specs = [
        ("Paper", "600 gsm cotton, deckled"),
        ("Ink", "Vermilion 032, oil based"),
        ("Press", "Albion No. 3, 1904"),
    ]

    var body: some View {
        let scale = min(displayScale, 3)
        let mask = LetterpressHeadlineArt.mask(layout, scale: scale)
        let faces = LetterpressHeadlineArt.blockFaces(layout, scale: scale)
        let second = LetterpressHeadlineArt.secondLine(layout, scale: scale)
        let region = layout.region
        let boxes = Self.flatBoxes(layout)

        ZStack(alignment: .topLeading) {
            LetterpressHeadlinePaper(layout: layout).equatable()

            TimelineView(.animation(paused: !(autoPlay || awake))) { timeline in
                let frame = engine.frame(
                    at: timeline.date.timeIntervalSinceReferenceDate, autoPlay: autoPlay, reduced: reduced, count: layout.count
                )
                let _ = settle(frame)
                let pose = frame.pose
                ZStack(alignment: .topLeading) {
                    Image(uiImage: mask)
                        .resizable()
                        .frame(width: region.width, height: region.height)
                        .layerEffect(
                            ShaderLibrary.letterpressHeadlinePress(
                                .float2(region.minX, region.minY),
                                .floatArray(boxes),
                                .floatArray(pose.depth.map { Float($0) }),
                                .float(Float(layout.count)),
                                .float(layout.split),
                                .float(pose.light),
                                .float(pose.elevation)
                            ),
                            maxSampleOffset: CGSize(width: 1, height: 1)
                        )
                        .offset(x: region.minX, y: region.minY)

                    LetterpressHeadlineCanvas(layout: layout, pose: pose, faces: faces, second: second)
                        .frame(width: layout.width, height: layout.height)
                }
                .frame(width: layout.width, height: layout.height, alignment: .topLeading)
            }
            .allowsHitTesting(false)

            // The paper: hold to print, drag to move the light.
            Color.clear
                .frame(width: layout.width, height: max(0, layout.detailsTop - 8))
                .contentShape(Rectangle())
                .gesture(press, isEnabled: !reduced)
                .accessibilityElement()
                .accessibilityLabel("Paper. Press and hold to print the headline; drag to move the light.")

            chrome
            Text("NEW EDITION · AUTUMN 2027")
                .font(.system(size: 11, weight: .bold).monospacedDigit())
                .kerning(2.2)
                .foregroundStyle(P.vermilion)
                .offset(x: LetterpressHeadlineLayout.margin, y: layout.kickerTop)
                .allowsHitTesting(false)
            specsList
            details
            button
            Text("Hold the paper to print · drag to move the light")
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(P.mute)
                .opacity(0.8)
                .multilineTextAlignment(.center)
                .frame(width: layout.width - 48)
                .offset(x: 24, y: layout.hintTop)
                .allowsHitTesting(false)
        }
        .frame(width: layout.width, height: layout.height, alignment: .topLeading)
        .clipped()
    }

    private static func flatBoxes(_ l: LetterpressHeadlineLayout) -> [Float] {
        var out: [Float] = []
        for i in 0..<M.maxGlyphs {
            let b = i < l.boxes.count ? l.boxes[i] : [-1000, -1000, -999, -999]
            out.append(contentsOf: b.map { Float($0) })
        }
        return out
    }

    private func settle(_ frame: LetterpressHeadlineEngine.Frame) {
        if frame.resumed {
            DispatchQueue.main.async { reserved = false }
        }
        guard frame.settled, awake else { return }
        DispatchQueue.main.async { awake = false }
    }

    private var hairline: Double { 1 / max(displayScale, 1) }

    // MARK: Chrome

    private var chrome: some View {
        HStack {
            Text("Press & Paper")
                .font(.system(size: 17, weight: .bold))
                .kerning(-0.2)
                .foregroundStyle(P.ink)
            Spacer(minLength: 8)
            Text("Edition 14")
                .font(.system(size: 12, weight: .semibold).monospacedDigit())
                .foregroundStyle(P.ink)
                .padding(.horizontal, 10)
                .frame(height: 26)
                .overlay(Capsule().strokeBorder(P.hex(0x2B2622, 0.35), lineWidth: hairline))
        }
        .frame(width: layout.width - 56, height: 26)
        .offset(x: 28, y: layout.chromeTop)
        .allowsHitTesting(false)
    }

    private var specsList: some View {
        VStack(spacing: 0) {
            Rectangle().fill(P.rule).frame(height: hairline)
            ForEach(0..<Self.specs.count, id: \.self) { i in
                HStack {
                    Text(Self.specs[i].0)
                        .font(.system(size: 13, weight: .semibold))
                        .kerning(0.4)
                        .foregroundStyle(P.mute)
                    Spacer(minLength: 8)
                    Text(Self.specs[i].1)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(P.ink)
                }
                .frame(height: 38 - hairline)
                Rectangle().fill(P.rule).frame(height: hairline)
            }
        }
        .frame(width: layout.width - 56)
        .offset(x: 28, y: layout.specsTop)
        .allowsHitTesting(false)
    }

    private var details: some View {
        HStack(alignment: .firstTextBaseline) {
            Text("12 prints · 300 × 400 mm")
                .font(.system(size: 14, weight: .medium).monospacedDigit())
                .foregroundStyle(P.mute)
            Spacer(minLength: 8)
            Text("$180")
                .font(.system(size: 18, weight: .bold).monospacedDigit())
                .foregroundStyle(P.ink)
        }
        .frame(width: layout.width - 56)
        .offset(x: 28, y: layout.detailsTop)
        .allowsHitTesting(false)
    }

    private var button: some View {
        Button {
            reserved = true
            UINotificationFeedbackGenerator().notificationOccurred(.success)
        } label: {
            Text(reserved ? "Print no. 7 reserved" : "Print edition")
                .font(.system(size: 17, weight: .semibold).monospacedDigit())
                .foregroundStyle(P.hex(0xF6EFE3))
                .frame(width: layout.width - 56, height: 56)
                .background {
                    Capsule()
                        .fill(P.ink)
                        .overlay(
                            // The inset highlight along the top edge.
                            Capsule()
                                .strokeBorder(
                                    LinearGradient(colors: [.white.opacity(0.12), .clear], startPoint: .top, endPoint: .center),
                                    lineWidth: 1
                                )
                        )
                        .shadow(color: P.hex(0x2B1E14, 0.3), radius: 1, x: 0, y: 1)
                        .shadow(color: P.hex(0x2B1E14, 0.18), radius: 12, x: 0, y: 10)
                }
        }
        .buttonStyle(LetterpressHeadlinePressStyle())
        .offset(x: 28, y: layout.buttonTop)
    }

    // MARK: Touch

    private var press: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                let now = Date.timeIntervalSinceReferenceDate
                if !down {
                    down = true
                    if !awake { awake = true }
                    engine.pressBegan(now: now, count: layout.count)
                }
                engine.pressMoved(value.translation, now: now)
            }
            .onEnded { value in
                down = false
                engine.pressEnded(velocityX: value.velocity.width, now: Date.timeIntervalSinceReferenceDate)
            }
    }
}

/// Scale 0.97 while pressed, as the React Native Pressable.
private struct LetterpressHeadlinePressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.scaleEffect(configuration.isPressed ? 0.97 : 1)
    }
}

/// The paper never changes, so it lives outside the timeline and is drawn by its own shader once.
private struct LetterpressHeadlinePaper: View, Equatable {
    let layout: LetterpressHeadlineLayout

    var body: some View {
        Rectangle()
            .fill(.white)
            .frame(width: layout.width, height: layout.height)
            .colorEffect(ShaderLibrary.letterpressHeadlinePaper(.float2(layout.width, layout.height)))
            .allowsHitTesting(false)
    }
}

/// The inked second line, the roller and the type blocks, for one pose.
private struct LetterpressHeadlineCanvas: View {
    let layout: LetterpressHeadlineLayout
    let pose: LetterpressHeadlinePose
    let faces: [UIImage]
    let second: UIImage

    private typealias P = LetterpressHeadlinePalette

    var body: some View {
        Canvas { ctx, _ in
            drawSecond(&ctx)
            drawRoller(&ctx)
            drawBlocks(&ctx)
        }
    }

    private func drawSecond(_ ctx: inout GraphicsContext) {
        let alpha = pose.inkAlpha * 0.92
        guard alpha > 0.001, pose.ink > 0 else { return }
        let top = layout.lineTop
        let h = layout.lineBottom - top
        let right = layout.rollFrom + (layout.rollTo - layout.rollFrom) * pose.ink
        var l = ctx
        l.opacity = alpha
        l.clip(to: Path(CGRect(x: 0, y: top, width: max(0, right), height: h)))
        l.draw(Image(uiImage: second), in: CGRect(x: 0, y: top, width: layout.width, height: h))
    }

    /// A rubber cylinder on a steel frame with a wooden grip.
    private func drawRoller(_ ctx: inout GraphicsContext) {
        guard pose.rollerAlpha > 0.001 else { return }
        let top = layout.lineTop
        let rollH = layout.lineBottom - top + 8
        var l = ctx
        l.opacity = pose.rollerAlpha
        l.translateBy(x: layout.rollFrom + (layout.rollTo - layout.rollFrom) * pose.roller, y: 0)
        l.drawLayer { s in
            s.addFilter(.blur(radius: 6))
            s.fill(Path(roundedRect: CGRect(x: -11, y: top + 4, width: 26, height: rollH), cornerRadius: 13),
                   with: .color(Color(red: 30 / 255, green: 20 / 255, blue: 10 / 255, opacity: 0.35)))
        }
        l.fill(Path(CGRect(x: -1.5, y: top - 46, width: 3, height: 50)), with: .color(P.hex(0x8C9097)))
        l.fill(Path(roundedRect: CGRect(x: -6, y: top - 86, width: 12, height: 44), cornerRadius: 6), with: .linearGradient(
            Gradient(colors: [P.hex(0x6B4426), P.hex(0xA8754A), P.hex(0x5A3A21)]),
            startPoint: CGPoint(x: -6, y: 0), endPoint: CGPoint(x: 6, y: 0)
        ))
        l.fill(Path(roundedRect: CGRect(x: -13, y: top - 4, width: 26, height: rollH), cornerRadius: 13), with: .linearGradient(
            Gradient(stops: [
                .init(color: P.hex(0x17181C), location: 0),
                .init(color: P.hex(0x3F434C), location: 0.35),
                .init(color: P.hex(0x2A3140), location: 0.6),
                .init(color: P.hex(0x121316), location: 1),
            ]),
            startPoint: CGPoint(x: -13, y: 0), endPoint: CGPoint(x: 13, y: 0)
        ))
        l.fill(Path(roundedRect: CGRect(x: -7, y: top, width: 3, height: rollH - 8), cornerRadius: 1.5),
               with: .color(.white.opacity(0.18)))
    }

    /// One metal type block per letter: its shadow falls away from the lamp, further and softer the
    /// higher the block is; the block shrinks toward the paper as it comes down.
    private func drawBlocks(_ ctx: inout GraphicsContext) {
        for (i, g) in layout.glyphs.prefix(layout.count).enumerated() {
            let a = pose.block[i]
            guard a > 0.001, i < faces.count else { continue }
            let opacity = pose.falling[i] ? min(1, a / 0.3) : a * a
            let rect = layout.blockRect(g)
            var l = ctx
            l.opacity = opacity
            let lift = 1.5 + 16 * (1 - a)
            l.drawLayer { s in
                s.translateBy(x: -cos(pose.light) * lift, y: -sin(pose.light) * lift)
                s.addFilter(.blur(radius: 2 + 9 * (1 - a)))
                s.fill(Path(roundedRect: rect, cornerRadius: 3), with: .color(Color(red: 40 / 255, green: 26 / 255, blue: 14 / 255, opacity: 0.42)))
            }
            let k = 1 + 0.25 * (1 - a)
            l.translateBy(x: rect.midX, y: rect.midY - 8 * (1 - a))
            l.scaleBy(x: k, y: k)
            l.translateBy(x: -rect.midX, y: -rect.midY)
            l.draw(Image(uiImage: faces[i]), in: rect)
        }
    }
}

/// Who owns the screen (the loop or the finger), and the finger's animated values.
@MainActor
final class LetterpressHeadlineEngine {
    private typealias M = LetterpressHeadlineMotion

    private static let idle = 2.0
    private static let reset = 0.9
    // Holding: the blocks reach the paper after blockDown s; the impression is full holdFull s later.
    private static let blockDown = 0.25
    private static let holdFull = 0.9

    private var loopStart: Double?
    private var clock = 0.0
    private var user = false
    private var lastTouch = 0.0
    private var resetAt = -1.0
    private var holding = false
    private var holdStart = 0.0
    private var contact = 0
    private var grab = (0.0, 0.0)
    private var count = 0
    private var resumedPending = false

    private var base = [Double](repeating: 0, count: LetterpressHeadlineMotion.maxGlyphs)
    private var fade = LetterpressHeadlineTrack(1)
    private var platen = LetterpressHeadlineTrack(0)
    private var block = LetterpressHeadlineTrack(0)
    private var ink = 0.0
    private var inkAlpha = LetterpressHeadlineTrack(1)
    private var az = LetterpressHeadlineTrack(0)
    private var el = LetterpressHeadlineTrack(0)

    private let light = UIImpactFeedbackGenerator(style: .light)
    private let select = UISelectionFeedbackGenerator()

    struct Frame {
        var pose: LetterpressHeadlinePose
        /// Nothing is moving and nothing will until the next touch.
        var settled: Bool
        /// The loop has just taken the screen back from the finger.
        var resumed: Bool
    }

    func frame(at now: Double, autoPlay: Bool, reduced: Bool, count: Int) -> Frame {
        self.count = count
        if reduced {
            user = false
            holding = false
            return Frame(pose: M.printedPose(count: count), settled: true, resumed: false)
        }
        if user { step(now) }
        let resumed = resumedPending
        resumedPending = false
        if !user {
            if autoPlay {
                let start = loopStart ?? now
                loopStart = start
                clock = (now - start).truncatingRemainder(dividingBy: M.loop)
            } else {
                loopStart = nil
                clock = 0
            }
            return Frame(pose: M.loopPose(clock, count: count), settled: !autoPlay, resumed: resumed)
        }
        return Frame(pose: userPose(now), settled: false, resumed: false)
    }

    private func step(_ t: Double) {
        if holding {
            let held = t - holdStart
            block.set(M.easeInOut(min(1, held / Self.blockDown)))
            if held >= Self.blockDown {
                platen.set(max(platen.value(at: t), M.easeOut((held - Self.blockDown) / Self.holdFull)))
                if contact == 0 {
                    contact = 1
                    light.impactOccurred()
                } else if contact == 1 && held >= Self.blockDown + Self.holdFull {
                    contact = 2
                    select.selectionChanged()
                }
            }
            return
        }
        if resetAt < 0 && t - lastTouch > Self.idle {
            fade.timing(0, duration: Self.reset, curve: .easeInOut, now: t)
            platen.timing(0, duration: Self.reset, curve: .easeInOut, now: t)
            inkAlpha.timing(0, duration: Self.reset, curve: .easeInOut, now: t)
            az.timing(0, duration: Self.reset, curve: .easeInOut, now: t)
            el.timing(0, duration: Self.reset, curve: .easeInOut, now: t)
            resetAt = t + Self.reset + 0.05
        } else if resetAt > 0 && t >= resetAt {
            resetAt = -1
            clock = 0
            loopStart = t
            user = false
            resumedPending = true
        }
    }

    private func userPose(_ t: Double) -> LetterpressHeadlinePose {
        var p = LetterpressHeadlinePose()
        let f = fade.value(at: t)
        let pl = platen.value(at: t)
        let b = block.value(at: t)
        for i in 0..<min(count, M.maxGlyphs) {
            p.depth[i] = max(base[i] * f, pl)
            p.block[i] = b
            p.falling[i] = holding
        }
        p.roller = 0
        p.rollerAlpha = 0
        p.ink = ink
        p.inkAlpha = inkAlpha.value(at: t) * f
        p.light = M.lightAz + az.value(at: t)
        p.elevation = M.lightEl + el.value(at: t)
        return p
    }

    /// Takes over from the loop without a jump: the finger's pose starts as the loop's frame.
    private func touched(_ now: Double) {
        lastTouch = now
        resetAt = -1
        if user { return }
        let p = M.loopPose(clock, count: count)
        base = p.depth
        fade.set(1)
        platen.set(0)
        block.set(0)
        ink = p.ink
        inkAlpha.set(p.inkAlpha)
        az.set(p.light - M.lightAz)
        el.set(p.elevation - M.lightEl)
        user = true
    }

    func pressBegan(now: Double, count: Int) {
        self.count = count
        touched(now)
        holding = true
        holdStart = now
        contact = 0
        grab = (az.value(at: now), el.value(at: now))
    }

    func pressMoved(_ translation: CGSize, now: Double) {
        lastTouch = now
        az.set(grab.0 + (M.rubber(translation.width, 160) / 160) * 1.3)
        el.set(max(-0.4, min(0.3, grab.1 - M.rubber(translation.height, 200) / 400)))
    }

    func pressEnded(velocityX: Double, now: Double) {
        holding = false
        lastTouch = now
        block.timing(0, duration: 0.26, curve: .easeOut, now: now)
        az.spring(0, duration: 0.7, dampingRatio: 0.7, velocity: (velocityX / 160) * 0.5, now: now)
        el.spring(0, duration: 0.7, dampingRatio: 0.8, now: now)
    }
}
