import SwiftUI
import UIKit
import CoreText
import Accelerate

// An image generator's thinking screen, matching the React Native reference.
// Five 14 pt thick, 72-point glass squircles turn about the vertical axis (z foreshortened .36).
//
// autoPlay timeline (loop = 8 s), twist between neighbouring layers in radians:
//   0.0–0.8  aligned, 38 pt gap
//   0.8–3.2  twist 0 → .42, ease-in-out cubic; gap 38 → 52 pt
//   3.2–4.8  twist .42 ± .05, one sine
//   4.8–7.2  .42 (1-u)^3 cos(1.6 π u), u = smoothstep; unwinds past straight
//   7.2–8.0  aligned again
// The base turns 90° per 8 s, including during touch; a squircle repeats every quarter turn.
// Drag x / 260 sets twist (−.7… .7). Release holds 2 s, eases to zero over .8 s, then
// restarts the loop. The progress shimmer freezes during touch and the return to rest.
// Instrument Serif italic 50/54 sits at safe.top + 54, over a lilac gradient and violet aura.
// The stack is centred at 48% height; the bottom prompt card holds a blurred harbour draft.
struct TwistGlassStack: View {
    static let loop: Double = 8

    var brand = "Atelier"
    var title = "Dreaming up\nyour scene…"
    var prompt = "A quiet harbour at dawn, soft film grain, two boats"
    var action = "Cancel"
    var autoPlay = false

    private static let peak = 0.42
    private static let hold = 2.0
    private static let returnDuration = 0.8
    private static let widths = [0.86, 1.0, 1.06, 0.98, 0.84]
    private static let tops = [0xB49BFF, 0xA7A2FF, 0xA2B3FF, 0x9CC6FF, 0xA6DBFF].map(color)
    private static let bodies = [0x7C4DFF, 0x7F68FF, 0x7D86FF, 0x6FA2FF, 0x6FC0FF].map(color)
    private static let background = color(0xECECF1)
    private static let ink = color(0x17161F)
    private static let soft = color(0x73717F)
    private static let track = color(0xE2E1EA)
    private static let bar = color(0x7C5CFF)

    private static let registeredFonts: Void = {
        if let url = Bundle.main.url(forResource: "TwistGlassStack-InstrumentSerif-Italic", withExtension: "ttf") {
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()

    private static var serif: UIFont {
        _ = registeredFonts
        return UIFont(name: "InstrumentSerif-Italic", size: 50) ?? .systemFont(ofSize: 50)
    }

    // Match RN's resized, edge-extended box blur once, before displaying the draft.
    private static let harbour: UIImage? = {
        guard let url = Bundle.main.url(forResource: "TwistGlassStack-harbour", withExtension: "jpg"),
              let image = UIImage(contentsOfFile: url.path) else { return nil }
        let format = UIGraphicsImageRendererFormat.default()
        format.preferredRange = .standard
        let resized = UIGraphicsImageRenderer(size: CGSize(width: 60, height: 60), format: format).image { _ in
            image.draw(in: CGRect(x: 0, y: 0, width: 60, height: 60))
        }
        guard let cg = resized.cgImage, let data = cg.dataProvider?.data,
              let source = CFDataGetBytePtr(data) else { return resized }
        let bytes = cg.bytesPerRow * cg.height
        guard let first = malloc(bytes) else { return resized }
        defer { free(first) }
        guard let second = malloc(bytes) else { return resized }
        defer { free(second) }
        memcpy(first, source, bytes)
        var a = vImage_Buffer(data: first, height: vImagePixelCount(cg.height),
                             width: vImagePixelCount(cg.width), rowBytes: cg.bytesPerRow)
        var b = vImage_Buffer(data: second, height: a.height, width: a.width, rowBytes: a.rowBytes)
        let kernel = UInt32(floor((6 * resized.scale * 3 * sqrt(2 * .pi) / 4 + 0.5) / 2)) | 1
        // RN returns buffer one after its second pass; its third pass is discarded.
        let flags = vImage_Flags(kvImageEdgeExtend)
        guard vImageBoxConvolve_ARGB8888(&a, &b, nil, 0, 0, kernel, kernel, nil, flags) == kvImageNoError,
              vImageBoxConvolve_ARGB8888(&b, &a, nil, 0, 0, kernel, kernel, nil, flags) == kvImageNoError,
              let colorSpace = cg.colorSpace,
              let context = CGContext(data: first, width: cg.width, height: cg.height,
                                      bitsPerComponent: 8, bytesPerRow: cg.bytesPerRow,
                                      space: colorSpace, bitmapInfo: cg.bitmapInfo.rawValue),
              let blurred = context.makeImage() else { return resized }
        return UIImage(cgImage: blurred, scale: resized.scale, orientation: .up)
    }()

    // Only event handlers write state. Every frame is derived from the active clock's date.
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @GestureState private var gestureTouching = false
    @State private var accumulated = 0.0
    @State private var anchor = Date()
    @State private var running = false
    @State private var origin = 0.0
    @State private var touching = false
    @State private var manual = false
    @State private var released = 0.0
    @State private var userTwist = 0.0
    @State private var frozenTime = 0.0
    @State private var touchID = 0
    @State private var haptic = UIImpactFeedbackGenerator(style: .soft)

    private struct SettleKey: Equatable {
        var touchID: Int
        var running: Bool
    }

    private struct Frame {
        var base: Double
        var twist: Double
        var time: Double
    }

    private static func color(_ hex: Int) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255,
              green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, opacity: 1)
    }

    private static func segment(_ t: Double, _ a: Double, _ b: Double) -> Double {
        min(1, max(0, (t - a) / (b - a)))
    }

    private static func easeInOutCubic(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }

    private static func twistAt(_ t: Double) -> Double {
        if t < 4.8 {
            return peak * easeInOutCubic(segment(t, 0.8, 3.2))
                + 0.05 * sin(2 * .pi * segment(t, 3.2, 4.8))
        }
        let x = segment(t, 4.8, 7.2)
        let u = x * x * (3 - 2 * x)
        return peak * pow(1 - u, 3) * cos(1.6 * .pi * u)
    }

    private func elapsed(at date: Date) -> Double {
        accumulated + (running ? max(0, date.timeIntervalSince(anchor)) : 0)
    }

    private func frame(at now: Double) -> Frame {
        if reduced { return Frame(base: 0, twist: 0, time: 0) }
        let base = autoPlay ? (.pi / 2) * now / Self.loop : 0
        if touching { return Frame(base: base, twist: userTwist, time: frozenTime) }
        var loopOrigin = origin
        if manual {
            let since = now - released
            if since < Self.hold {
                return Frame(base: base, twist: userTwist, time: frozenTime)
            }
            if since < Self.hold + Self.returnDuration {
                let u = (since - Self.hold) / Self.returnDuration
                return Frame(base: base, twist: userTwist * (1 - Self.easeInOutCubic(u)), time: frozenTime)
            }
            loopOrigin = released + Self.hold + Self.returnDuration
        }
        let t = autoPlay ? max(0, now - loopOrigin).truncatingRemainder(dividingBy: Self.loop) : 0
        return Frame(base: base, twist: autoPlay ? Self.twistAt(t) : 0, time: t)
    }

    var body: some View {
        GeometryReader { geo in
            let safe = geo.safeAreaInsets
            let size = CGSize(width: geo.size.width + safe.leading + safe.trailing,
                              height: geo.size.height + safe.top + safe.bottom)
            TimelineView(.animation(paused: !running || (!autoPlay && !manual))) { timeline in
                let value = frame(at: elapsed(at: timeline.date))
                ZStack(alignment: .topLeading) {
                    Self.background

                    glass(size: size, value: value)
                        .contentShape(Rectangle())
                        .gesture(pan, including: reduced ? .none : .all)

                    header(safe: safe, width: size.width)
                        .allowsHitTesting(false)

                    TwistGlassStackTitle(text: title, font: Self.serif)
                        .frame(width: size.width - 48, alignment: .leading)
                        .offset(x: 24, y: safe.top + 54)
                        .allowsHitTesting(false)

                    bottom(safe: safe, size: size, time: value.time)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .ignoresSafeArea()
        }
        .preferredColorScheme(.light)
        .onAppear {
            accumulated = 0
            origin = 0
            anchor = .now
            running = scenePhase == .active && !reduced
            haptic.prepare()
        }
        .onChange(of: scenePhase) { _, phase in
            setClockRunning(phase == .active && !reduced)
        }
        .onChange(of: reduced) { _, value in
            setClockRunning(scenePhase == .active && !value)
        }
        .onChange(of: gestureTouching) { _, value in
            if !value { endPan() }
        }
        .task(id: SettleKey(touchID: touchID, running: running)) {
            await settle()
        }
    }

    private func header(safe: EdgeInsets, width: CGFloat) -> some View {
        HStack(spacing: 8) {
            Text(brand)
                .font(.system(size: 20, weight: .bold))
                .tracking(-0.3)
                .foregroundStyle(Self.ink)
            Text("AI")
                .font(.system(size: 11, weight: .bold))
                .tracking(1)
                .foregroundStyle(.white)
                .padding(.horizontal, 8)
                .frame(height: 22)
                .background(Capsule().fill(Self.ink))
            Spacer(minLength: 0)
        }
        .frame(width: width - 48, height: 30, alignment: .leading)
        .offset(x: 24, y: safe.top + 10)
    }

    private func bottom(safe: EdgeInsets, size: CGSize, time: Double) -> some View {
        VStack(spacing: 0) {
            Spacer(minLength: 0)
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 12) {
                    Group {
                        if let image = Self.harbour {
                            Image(uiImage: image)
                                .resizable()
                                .scaledToFill()
                        }
                    }
                    .frame(width: 60, height: 60)
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .circular))
                    .accessibilityHidden(true)

                    VStack(alignment: .leading, spacing: 4) {
                        Text("PROMPT · 62%")
                            .font(.system(size: 11, weight: .bold))
                            .tracking(1.5)
                            .foregroundStyle(Self.soft)
                            .frame(height: UIFont.systemFont(ofSize: 11, weight: .bold).lineHeight)
                        Text(prompt)
                            .font(.system(size: 15))
                            .foregroundStyle(Self.ink)
                            .lineSpacing(20 - UIFont.systemFont(ofSize: 15).lineHeight)
                            .lineLimit(2)
                            .fixedSize(horizontal: false, vertical: true)
                            // Match RN's two 20 pt line boxes, including their outer leading.
                            .padding(.vertical, (20 - UIFont.systemFont(ofSize: 15).lineHeight) / 2)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
                .frame(height: 60)
                Capsule()
                    .fill(Self.track)
                    .frame(height: 6)
                    .overlay(alignment: .leading) {
                        Capsule()
                            .fill(Self.bar)
                            .frame(width: 120, height: 6)
                            .offset(x: -120 + (size.width - 48 - 28 + 240) * time / Self.loop)
                    }
                    .clipShape(Capsule())
                    .padding(.top, 14)
            }
            .padding(14)
            .background(RoundedRectangle(cornerRadius: 24, style: .circular).fill(.white.opacity(0.85)))
            .shadow(color: Self.color(0x3B2A8C).opacity(0.12), radius: 20, x: 0, y: 8)

            Text(action)
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(Self.ink)
                .frame(maxWidth: .infinity)
                .frame(height: 54)
                .overlay(Capsule().strokeBorder(Self.color(0xCFCDD9), lineWidth: 1.5))
                .padding(.top, 14)
                .accessibilityAddTraits(.isButton)
        }
        .padding(.horizontal, 24)
        .padding(.bottom, safe.bottom + 16)
        .frame(width: size.width, height: size.height, alignment: .bottom)
    }

    // Precompute the reference's 72 vertices once, including the signed square roots.
    private static let outline: [CGPoint] = (0..<72).map { i in
        let t = Double(i) / 72 * 2 * .pi
        let c = cos(t), s = sin(t)
        return CGPoint(x: (c < 0 ? -1 : 1) * sqrt(abs(c)),
                       y: (s < 0 ? -1 : 1) * sqrt(abs(s)))
    }

    private static func layerPath(cx: Double, cy: Double, half: Double, angle: Double) -> Path {
        let ca = cos(angle), sa = sin(angle)
        return Path { path in
            for (i, vertex) in outline.enumerated() {
                let x = vertex.x * half, z = vertex.y * half
                let point = CGPoint(x: cx + x * ca - z * sa, y: cy + (x * sa + z * ca) * 0.36)
                if i == 0 { path.move(to: point) } else { path.addLine(to: point) }
            }
            path.closeSubpath()
        }
    }

    private func glass(size: CGSize, value: Frame) -> some View {
        Canvas(opaque: false, colorMode: .nonLinear) { context, _ in
            let cx = size.width / 2, cy = size.height * 0.48
            context.fill(Path(CGRect(origin: .zero, size: size)), with: .linearGradient(
                Gradient(colors: [Self.color(0xF5F2FF), Self.color(0xDCE2F6)]),
                startPoint: .zero, endPoint: CGPoint(x: 0, y: size.height)))
            context.fill(Path(ellipseIn: CGRect(x: cx - 220, y: cy - 220, width: 440, height: 440)),
                         with: .radialGradient(
                            Gradient(colors: [Self.color(0x8C6EFF).opacity(0.22), Self.color(0x8C6EFF).opacity(0)]),
                            center: CGPoint(x: cx, y: cy), startRadius: 0, endRadius: 220))
            var shadow = context
            shadow.opacity = 0.18
            shadow.addFilter(.blur(radius: 18))
            shadow.fill(Path(ellipseIn: CGRect(x: cx - 130, y: cy + 144, width: 260, height: 40)),
                        with: .color(Self.color(0x3B2A8C)))

            let open = min(1, abs(value.twist) / Self.peak)
            let gap = 38 + 14 * open
            for i in stride(from: 4, through: 0, by: -1) {
                let y = cy + Double(i - 2) * gap
                let half = 104 * Self.widths[i] * (1 + 0.04 * open * sin(Double(i) * 1.7))
                let angle = value.base + value.twist * Double(i - 2)
                let face = Self.layerPath(cx: cx, cy: y, half: half, angle: angle)
                context.fill(Self.layerPath(cx: cx, cy: y + 14, half: half, angle: angle),
                             with: .color(Self.bodies[i].opacity(0.78)))

                let gradient = Gradient(stops: [
                    .init(color: .white.opacity(0.86), location: 0),
                    .init(color: Self.tops[i].opacity(0.86), location: 0.45),
                    .init(color: Self.bodies[i].opacity(0.86), location: 1)
                ])
                let depth = half * 0.36
                context.fill(face, with: .linearGradient(gradient,
                             startPoint: CGPoint(x: cx, y: y - depth * 1.2),
                             endPoint: CGPoint(x: cx, y: y + depth * 1.2)))
                context.stroke(face, with: .color(.white.opacity(0.75)), lineWidth: 1.4)
            }
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .accessibilityLabel("Glass layers twisting while the image is generated. Drag to twist them.")
    }

    private var pan: some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($gestureTouching) { _, state, _ in state = true }
            .onChanged { value in
                guard !reduced else { return }
                let now = elapsed(at: .now)
                let wasTouching = touching
                if !wasTouching {
                    let current = frame(at: now)
                    touching = true
                    manual = true
                    userTwist = current.twist
                    frozenTime = current.time
                    touchID += 1
                    haptic.impactOccurred()
                }
                // Begin picks up the current twist; updates use translation alone, as in RN.
                if wasTouching || value.translation.width != 0 {
                    userTwist = min(0.7, max(-0.7, value.translation.width / 260))
                }
            }
            .onEnded { _ in endPan() }
    }

    private func endPan() {
        guard touching else { return }
        touching = false
        released = elapsed(at: .now)
        touchID += 1
    }

    private func setClockRunning(_ value: Bool) {
        guard value != running else { return }
        if !value { endPan() }
        let now = Date()
        accumulated = elapsed(at: now)
        anchor = now
        running = value
    }

    private func settle() async {
        guard manual, !touching, running else { return }
        let end = released + Self.hold + Self.returnDuration
        let remaining = max(0, end - elapsed(at: .now))
        try? await Task.sleep(for: .seconds(remaining))
        guard !Task.isCancelled, !touching, running else { return }
        origin = end
        userTwist = 0
        manual = false
    }
}

// TextKit honours a 54 pt line box even though this font's natural line height is 65 pt.
// SwiftUI's negative lineSpacing is clamped, so use the same paragraph metrics as RN.
private struct TwistGlassStackTitle: UIViewRepresentable {
    var text: String
    var font: UIFont

    func makeUIView(context: Context) -> UILabel {
        let label = UILabel()
        label.numberOfLines = 0
        label.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        return label
    }

    func updateUIView(_ label: UILabel, context: Context) {
        let paragraph = NSMutableParagraphStyle()
        paragraph.minimumLineHeight = 54
        paragraph.maximumLineHeight = 54
        label.attributedText = NSAttributedString(string: text, attributes: [
            .font: font,
            .kern: -0.5,
            .foregroundColor: UIColor(red: 23 / 255, green: 22 / 255, blue: 31 / 255, alpha: 1),
            .paragraphStyle: paragraph
        ])
    }

    func sizeThatFits(_ proposal: ProposedViewSize, uiView: UILabel, context: Context) -> CGSize? {
        let width = proposal.width ?? 393
        return CGSize(width: width, height: uiView.sizeThatFits(
            CGSize(width: width, height: CGFloat.greatestFiniteMagnitude)).height)
    }
}

#Preview {
    TwistGlassStack(autoPlay: true)
}
