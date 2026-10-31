import CoreText
import SwiftUI
import UIKit

// Faithful port of the React Native reference. Four 2 s stops form an 8 s loop.
// In each stop: pan until 1.25 s, close the shade over 1.25–1.60 s (in cubic),
// swap the view at 1.65 s, then lift over 1.70–2.00 s (out cubic).
// Caption progression smoothsteps over 1.55–1.85 s; its opacity smoothsteps again.
// A pull past halfway closes in .2 s, swaps, and lifts over .3–.6 s. Autoplay
// continues from the selected stop after that .6 s animation and a 2 s hold.
struct WindowSeatDestinations: View {
    var brand = "porthole"
    var seat = "Seat 14A"
    var headline = "Where to\nnext?"
    var action = "Search flights"
    var autoPlay = false

    static let loop: Double = 8

    @Environment(\.accessibilityReduceMotion) private var reducedMotion
    @Environment(\.scenePhase) private var scenePhase
    @GestureState private var fingerDown = false
    @State private var epoch = Date()
    @State private var ready = false
    @State private var inactiveAt: Date?
    @State private var loopOrigin = 0.0
    @State private var manual = false
    @State private var touching = false
    @State private var panActivated = false
    @State private var grabShade = 0.0
    @State private var held = Frame.rest
    @State private var retained = Frame.rest
    @State private var released = 0.0
    @State private var fromShade = 0.0
    @State private var advance = false
    @State private var target = 0
    @State private var touches = 0
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)

    private typealias T = WindowSeatDestinationsTypography
    private typealias P = WindowSeatDestinationsPalette

    var body: some View {
        GeometryReader { geometry in
            let safe = geometry.safeAreaInsets
            let size = CGSize(width: geometry.size.width + safe.leading + safe.trailing,
                              height: geometry.size.height + safe.top + safe.bottom)
            let captionTop: CGFloat = size.height * 0.49 + 238
            ZStack(alignment: .topLeading) {
                TimelineView(.animation(paused: reducedMotion || scenePhase != .active)) { timeline in
                    let now = elapsed(at: timeline.date)
                    let frame = frame(at: now)
                    ZStack(alignment: .topLeading) {
                        Canvas(opaque: true, colorMode: .nonLinear) { context, _ in
                            draw(context, size: size, frame: frame)
                        }
                        .accessibilityLabel("Airplane window. Pull the shade down to fly to the next destination.")

                        ForEach(0..<4, id: \.self) { index in
                            let opacity = Self.captionOpacity(frame.caption, index)
                            let offsetY = captionTop + CGFloat(12 * (1 - opacity))
                            T.caption(width: size.width - 56, index: index)
                                .opacity(opacity)
                                .offset(x: 28, y: offsetY)
                        }
                        .allowsHitTesting(false)
                    }
                    .frame(width: size.width, height: size.height, alignment: .topLeading)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .contentShape(Rectangle())
                .gesture(pull)

                T.header(width: size.width - 48, brand: brand, seat: seat, headline: headline)
                    .offset(x: 24, y: safe.top + 8)
                    .accessibilityLabel("\(brand). \(seat). \(headline)")
                    .allowsHitTesting(false)

                T.footer(width: size.width - 48, action: action)
                    .offset(x: 24, y: size.height - safe.bottom - 14 - 58)
                    .accessibilityLabel(action)
                    .accessibilityAddTraits(.isButton)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .ignoresSafeArea()
        }
        .background(P.wallTop.ignoresSafeArea())
        .preferredColorScheme(.light)
        .onAppear {
            // Decode before starting the clock so the first visible frame is the resting stop.
            _ = WindowSeatDestinationsArtwork.images
            epoch = .now
            ready = true
            haptic.prepare()
        }
        .onChange(of: fingerDown) { _, down in
            if !down { releaseTouch() }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase != .active {
                if inactiveAt == nil { inactiveAt = .now }
            } else if let paused = inactiveAt {
                epoch = epoch.addingTimeInterval(Date().timeIntervalSince(paused))
                inactiveAt = nil
            }
        }
        .onChange(of: reducedMotion) { _, _ in
            retained = .rest
            manual = false
            touching = false
            loopOrigin = elapsed(at: .now)
            touches += 1
        }
        .task(id: SettleTask(touch: touches, active: scenePhase == .active)) {
            await settle()
        }
    }

    private struct SettleTask: Equatable {
        let touch: Int
        let active: Bool
    }

    private struct Frame {
        var clock = 0.0
        var shade = 0.0
        var view = 0
        var caption = 0.0
        var pan = 0.175
        static let rest = Frame()
    }

    private func elapsed(at date: Date) -> Double {
        max(0, (inactiveAt ?? date).timeIntervalSince(epoch))
    }

    private func frame(at now: Double) -> Frame {
        guard ready, !reducedMotion else { return .rest }
        let clock = max(0, now - loopOrigin).truncatingRemainder(dividingBy: Self.loop)
        if manual {
            var result = held
            result.clock = clock
            if touching { return result }
            let since = max(0, now - released)
            if advance {
                if since < 0.2 {
                    result.shade = fromShade + (1 - fromShade) * Self.easeIn(since / 0.2)
                } else {
                    result.view = target
                    result.caption = Double(target)
                    result.shade = since < 0.3 ? 1 : 1 - Self.easeOut(Self.clamp((since - 0.3) / 0.3))
                }
            } else {
                result.shade = fromShade * (1 - Self.easeOut(Self.clamp(since / 0.3)))
            }
            result.pan = min(1, since / 2)
            return result
        }
        guard autoPlay else {
            var result = retained
            result.clock = clock
            return result
        }
        return Self.stop(at: clock)
    }

    private static func stop(at time: Double) -> Frame {
        let t = max(0, time).truncatingRemainder(dividingBy: loop)
        let k = Int(floor(t / 2))
        let u = t - Double(k) * 2
        return Frame(clock: t,
                     shade: u < 1.65 ? easeIn(segment(u, 1.25, 1.6)) : 1 - easeOut(segment(u, 1.7, 2)),
                     view: u < 1.65 ? k % 4 : (k + 1) % 4,
                     caption: Double(k) + smooth(segment(u, 1.55, 1.85)),
                     pan: u < 1.65 ? (u + 0.35) / 2 : (u - 1.65) / 2)
    }

    private var pull: some Gesture {
        // RN's onBegin takes the current shade on touch-down. Its onUpdate starts at 4 pt.
        DragGesture(minimumDistance: 0)
            .updating($fingerDown) { _, down, _ in down = true }
            .onChanged { value in
                guard !reducedMotion else { return }
                if !touching {
                    let current = frame(at: elapsed(at: .now))
                    touching = true
                    manual = true
                    panActivated = false
                    grabShade = current.shade
                    held = current
                    held.caption = floor(current.caption + 0.5).truncatingRemainder(dividingBy: 4)
                    held.view = Int(held.caption)
                    touches += 1
                }
                if hypot(value.translation.width, value.translation.height) >= 4 { panActivated = true }
                if panActivated { held.shade = Self.clamp(grabShade + value.translation.height / 368) }
            }
            .onEnded { _ in releaseTouch() }
    }

    private func releaseTouch() {
        guard touching, !reducedMotion else { return }
        touching = false
        fromShade = held.shade
        advance = held.shade > 0.5
        target = (held.view + 1) % 4
        if advance { haptic.impactOccurred() }
        held.pan = 0
        released = elapsed(at: .now)
        touches += 1
    }

    private func settle() async {
        guard manual, !touching, !reducedMotion, scenePhase == .active else { return }
        let remaining = max(0, 2.6 - (elapsed(at: .now) - released))
        try? await Task.sleep(for: .seconds(remaining))
        guard !Task.isCancelled, !touching, scenePhase == .active else { return }
        let now = elapsed(at: .now)
        retained = frame(at: now)
        loopOrigin = now - Double(retained.view) * 2
        manual = false
    }

    private static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    private static func segment(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a)) }
    private static func easeIn(_ x: Double) -> Double { x * x * x }
    private static func easeOut(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    private static func smooth(_ x: Double) -> Double {
        let u = clamp(x)
        return u * u * (3 - 2 * u)
    }

    private static func captionOpacity(_ caption: Double, _ index: Int) -> Double {
        let c = (caption.truncatingRemainder(dividingBy: 4) + 4).truncatingRemainder(dividingBy: 4)
        let distance = abs(c - Double(index))
        return 1 - smooth(min(distance, 4 - distance) / 0.5)
    }

    private func draw(_ context: GraphicsContext, size: CGSize, frame: Frame) {
        let cx = size.width / 2
        let cy = size.height * 0.49
        let fx = cx - 150, fy = cy - 212
        let ox = cx - 122, oy = cy - 184
        context.fill(Path(CGRect(origin: .zero, size: size)), with: .linearGradient(
            Gradient(colors: [P.wallTop, P.wallBottom]), startPoint: .zero, endPoint: CGPoint(x: 0, y: size.height)))
        for x in [cx - 184, cx + 184] {
            context.fill(Path(CGRect(x: x, y: 0, width: 1, height: size.height)), with: .color(P.ink.opacity(0.06)))
        }
        var window = context
        window.translateBy(x: 0, y: 1.2 * sin(frame.clock / Self.loop * 2 * .pi * 5))
        window.drawLayer { shadow in
            shadow.addFilter(.blur(radius: 10))
            shadow.fill(Path(roundedRect: CGRect(x: fx - 14, y: fy - 14, width: 328, height: 452), cornerRadius: 154),
                        with: .color(P.ink.opacity(0.10)))
        }
        window.fill(Path(roundedRect: CGRect(x: fx, y: fy, width: 300, height: 424), cornerRadius: 140),
                    with: .linearGradient(Gradient(colors: [.white, Color(hex: 0xE2E8EE), Color(hex: 0xBFC9D3)]),
                                          startPoint: CGPoint(x: fx, y: fy), endPoint: CGPoint(x: fx + 300, y: fy + 424)))
        window.fill(Path(roundedRect: CGRect(x: ox - 8, y: oy - 8, width: 260, height: 384), cornerRadius: 122),
                    with: .linearGradient(Gradient(colors: [Color(hex: 0x9EABB8), Color(hex: 0xE9EEF2)]),
                                          startPoint: CGPoint(x: ox, y: oy), endPoint: CGPoint(x: ox, y: oy + 368)))
        let opening = Path(roundedRect: CGRect(x: ox, y: oy, width: 244, height: 368), cornerRadius: 114)
        var view = window
        view.clip(to: opening)
        view.fill(opening, with: .color(Color(hex: 0x8FA6BC)))
        view.draw(WindowSeatDestinationsArtwork.images[frame.view],
                  in: CGRect(x: ox - 62 + 35 - 70 * frame.pan, y: oy, width: 368, height: 368))
        let cloudA = ox + 244 + 60 - (frame.clock / Self.loop * 3).truncatingRemainder(dividingBy: 1) * 464
        let cloudB = ox + 244 + 60 - (frame.clock / Self.loop * 2 + 0.55).truncatingRemainder(dividingBy: 1) * 464
        view.drawLayer { clouds in
            clouds.opacity = 0.75
            var a = clouds
            a.addFilter(.blur(radius: 16))
            a.fill(Path(ellipseIn: CGRect(x: cloudA, y: oy + 70, width: 170, height: 54)), with: .color(.white))
            var b = clouds
            b.addFilter(.blur(radius: 18))
            b.fill(Path(ellipseIn: CGRect(x: cloudB, y: oy + 210, width: 210, height: 64)), with: .color(.white))
        }
        view.fill(opening, with: .linearGradient(Gradient(stops: [
            .init(color: .white.opacity(0.35), location: 0),
            .init(color: .white.opacity(0), location: 0.45),
            .init(color: .white.opacity(0.12), location: 1)
        ]), startPoint: CGPoint(x: ox, y: oy), endPoint: CGPoint(x: ox + 244, y: oy + 368 * 0.6)))
        view.fill(Path(CGRect(x: ox, y: oy, width: 244, height: 368 * frame.shade)),
                  with: .linearGradient(Gradient(colors: [P.shadeRib, P.shade, P.shadeRib]),
                                        startPoint: CGPoint(x: ox, y: 0), endPoint: CGPoint(x: ox + 244, y: 0)))
        view.fill(Path(roundedRect: CGRect(x: cx - 26, y: oy + 368 * frame.shade - 12, width: 52, height: 10), cornerRadius: 5),
                  with: .color(Color(hex: 0xC3CCD5)))
        window.stroke(opening, with: .color(P.ink.opacity(0.25)), lineWidth: 2)
    }
}

private enum WindowSeatDestinationsPalette {
    static let wallTop = Color(hex: 0xF1F4F7)
    static let wallBottom = Color(hex: 0xC9D3DC)
    static let ink = Color(hex: 0x13243A)
    static let shade = Color(hex: 0xEEF1F4)
    static let shadeRib = Color(hex: 0xDDE3E9)

    static func ui(_ hex: UInt32, alpha: CGFloat = 1) -> UIColor {
        UIColor(red: CGFloat((hex >> 16) & 255) / 255, green: CGFloat((hex >> 8) & 255) / 255,
                blue: CGFloat(hex & 255) / 255, alpha: alpha)
    }
}

private enum WindowSeatDestinationsArtwork {
    // Decode the same four square photos once; animation only translates them.
    static let images: [Image] = ["alps", "lagoon", "city", "dunes"].map { name in
        guard let url = Bundle.main.url(forResource: "WindowSeatDestinations-view-\(name)", withExtension: "jpg"),
              let source = UIImage(contentsOfFile: url.path) else { return Image(uiImage: UIImage()) }
        return Image(uiImage: source.preparingForDisplay() ?? source)
    }
}

private enum WindowSeatDestinationsTypography {
    private typealias P = WindowSeatDestinationsPalette
    static let stops = [
        ("Alpine Ridge", "Geneva → Sion · 55 min", "$129"),
        ("Nalu Atoll", "Malé → Nalu · 1 h 20 min", "$214"),
        ("Velmar Harbour", "Porto → Velmar · 1 h 05 min", "$96"),
        ("Amber Dunes", "Ouarzi → Merz · 1 h 40 min", "$158")
    ]
    private static let registered: Void = {
        for face in ["Regular", "Italic"] {
            if let url = Bundle.main.url(forResource: "WindowSeatDestinations-InstrumentSerif-\(face)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }()
    private static var cache: [String: Image] = [:]

    static func serif(_ size: CGFloat, italic: Bool = false) -> UIFont {
        _ = registered
        return UIFont(name: italic ? "InstrumentSerif-Italic" : "InstrumentSerif-Regular", size: size)
            ?? .systemFont(ofSize: size)
    }

    // RN draws TextKit glyphs without font leading. Cache that exact line layout rather
    // than letting SwiftUI add a different inset to the short explicit serif line heights.
    @discardableResult
    private static func label(_ text: String, font: UIFont, color: UIColor, x: CGFloat, y: CGFloat,
                              width: CGFloat, lineHeight: CGFloat? = nil, tracking: CGFloat = 0,
                              alignment: NSTextAlignment = .left, draw: Bool = true) -> CGFloat {
        let paragraph = NSMutableParagraphStyle()
        paragraph.alignment = alignment
        if let lineHeight {
            paragraph.minimumLineHeight = lineHeight
            paragraph.maximumLineHeight = lineHeight
        }
        var attributes: [NSAttributedString.Key: Any] = [
            .font: font, .foregroundColor: color, .kern: tracking, .paragraphStyle: paragraph
        ]
        if let lineHeight, lineHeight >= font.lineHeight {
            attributes[.baselineOffset] = (lineHeight - font.lineHeight) / 2
        }
        let storage = NSTextStorage(attributedString: NSAttributedString(string: text, attributes: attributes))
        let layout = NSLayoutManager()
        layout.usesFontLeading = false
        let container = NSTextContainer(size: CGSize(width: width, height: .greatestFiniteMagnitude))
        container.lineFragmentPadding = 0
        layout.addTextContainer(container)
        storage.addLayoutManager(layout)
        let glyphs = layout.glyphRange(for: container)
        if draw { layout.drawGlyphs(forGlyphRange: glyphs, at: CGPoint(x: x, y: y)) }
        return y + (layout.numberOfGlyphs > 0 ? layout.location(forGlyphAt: 0).y : 0)
    }

    private static func image(key: String, size: CGSize, draw: () -> Void) -> Image {
        if let cached = cache[key] { return cached }
        let format = UIGraphicsImageRendererFormat()
        format.scale = 3
        format.opaque = false
        let rendered = UIGraphicsImageRenderer(size: size, format: format).image { _ in draw() }
        let result = Image(uiImage: rendered)
        cache[key] = result
        return result
    }

    static func header(width: CGFloat, brand: String, seat: String, headline: String) -> Image {
        image(key: "header|\(width)|\(brand)|\(seat)|\(headline)", size: CGSize(width: width, height: 156)) {
            let ink = P.ui(0x13243A)
            let brandFont = serif(28, italic: true)
            label(brand, font: brandFont, color: ink, x: 0, y: (34 - brandFont.lineHeight) / 2, width: width)
            let seatFont = UIFont.systemFont(ofSize: 13, weight: .bold)
            let seatWidth = ceil((seat as NSString).size(withAttributes: [.font: seatFont, .kern: 0.3]).width) + 24
            P.ui(0x13243A, alpha: 0.08).setFill()
            UIBezierPath(roundedRect: CGRect(x: width - seatWidth, y: 3, width: seatWidth, height: 28), cornerRadius: 14).fill()
            label(seat, font: seatFont, color: ink, x: width - seatWidth + 12,
                  y: 3 + (28 - seatFont.lineHeight) / 2, width: seatWidth - 24, tracking: 0.3)
            label(headline, font: serif(54), color: ink, x: 0, y: 40, width: width, lineHeight: 56, tracking: -1)
        }
    }

    static func caption(width: CGFloat, index: Int) -> Image {
        image(key: "caption|\(width)|\(index)", size: CGSize(width: width, height: 70)) {
            let stop = stops[index]
            let ink = P.ui(0x13243A)
            let baseline = label(stop.0, font: serif(36), color: ink, x: 0, y: 0, width: width, lineHeight: 42)
            let fareFont = UIFont.systemFont(ofSize: 17, weight: .bold)
            let fareBaseline = label(stop.2, font: fareFont, color: ink, x: 0, y: 0, width: width, draw: false)
            label(stop.2, font: fareFont, color: ink, x: 0, y: baseline - fareBaseline, width: width, alignment: .right)
            label(stop.1, font: .systemFont(ofSize: 15, weight: .medium), color: P.ui(0x5E6E80),
                  x: 0, y: 44, width: width, lineHeight: 20)
        }
    }

    static func footer(width: CGFloat, action: String) -> Image {
        image(key: "footer|\(width)|\(action)", size: CGSize(width: width, height: 58)) {
            P.ui(0x13243A).setFill()
            UIBezierPath(roundedRect: CGRect(x: 0, y: 0, width: width, height: 58), cornerRadius: 29).fill()
            let font = UIFont.systemFont(ofSize: 17, weight: .bold)
            label(action, font: font, color: .white, x: 0, y: (58 - font.lineHeight) / 2, width: width, alignment: .center)
        }
    }
}

#Preview {
    WindowSeatDestinations(autoPlay: true)
}
