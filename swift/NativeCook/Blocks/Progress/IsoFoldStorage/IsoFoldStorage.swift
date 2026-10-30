import CoreText
import SwiftUI
import UIKit

// A faithful port of the React Native reference. One stage drives the isometric block,
// its swept red shadow, the plan readout and the segmented control.
// Archivo Expanded Black labels the plan; Archivo SemiBold carries the supporting copy.
// The block is 32 pt deep; its length and height each grow by 116 pt in their stage.
//
// Loop (8 s): 0–1 Lite; 1–2.2 extrude to Plus (ease-out back, k = 1.4);
// 2.2–3 Plus; 3–4.2 rise to Max (same back curve); 4.2–5.6 Max;
// 5.6–6.6 fold to Plus; 6.6–7.4 retract to Lite (both ease-in-out cubic);
// 7.4–8 rest, identical to t = 0. Dragging uses 120 pt per stage. Release snaps
// in 0.35 s, holds 2 s and returns to Lite in 0.8 s before restarting the loop.
struct IsoFoldStorage: View {
    static let loop: Double = 8

    var title = "More room for\nyour memories"
    var subtitle = "Vault keeps every photo, file and note in sync."
    var action = "Continue"
    var autoPlay = false

    private enum Palette {
        static let background = color(0xF2A3A3)
        static let ink = color(0x4A0F14)
        static let soft = color(0x8C3B40)
        static let left = color(0xF6E6E6)
        static let right = color(0xEBCFCF)
        static let shadow = color(0xE3262E)
        static let track = color(0xE68B8D)

        static func color(_ hex: UInt32) -> Color {
            Color(.sRGB, red: Double((hex >> 16) & 255) / 255,
                  green: Double((hex >> 8) & 255) / 255,
                  blue: Double(hex & 255) / 255, opacity: 1)
        }
    }

    private static let plans = [
        (name: "Lite", size: "50 GB", price: "Free forever"),
        (name: "Plus", size: "200 GB", price: "$2.99 a month"),
        (name: "Max", size: "2 TB", price: "$9.99 a month")
    ]
    private static let snap = 0.35
    private static let hold = 2.0
    private static let unwind = 0.8

    private enum Typeface {
        static let black = "ArchivoExpanded-Black"
        static let semibold = "Archivo-SemiBold"
        static let registered: Void = {
            for name in ["IsoFoldStorage-ArchivoExpanded-Black", "IsoFoldStorage-Archivo-SemiBold"] {
                if let url = Bundle.main.url(forResource: name, withExtension: "ttf") {
                    CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
                }
            }
        }()

        static func font(_ name: String, size: CGFloat) -> Font {
            _ = registered
            return .custom(name, fixedSize: size)
        }

        static func uiFont(_ name: String, size: CGFloat) -> UIFont {
            _ = registered
            return UIFont(name: name, size: size) ?? .systemFont(ofSize: size)
        }
    }

    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.displayScale) private var displayScale
    @GestureState private var gestureTouching = false
    // Freeze this event-driven clock while inactive; no state is written per frame.
    @State private var accumulated = 0.0
    @State private var anchor = Date()
    @State private var running = false
    @State private var origin = 0.0
    @State private var touching = false
    @State private var manual = false
    @State private var activated = false
    @State private var grabbed = 0.0
    @State private var dragged = 0.0
    @State private var released = 0.0
    @State private var releaseFrom = 0.0
    @State private var snapTo = 0.0
    @State private var lastStep = 0
    @State private var touchID = 0
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)

    private struct SettleKey: Equatable {
        var touchID: Int
        var running: Bool
    }

    // SwiftUI clamps negative lineSpacing, so use the same fixed paragraph line height
    // as RN's native text layout instead of spreading these two lines farther apart.
    private struct Heading: UIViewRepresentable {
        var text: String

        func makeUIView(context: Context) -> UILabel {
            let label = UILabel()
            label.numberOfLines = 0
            label.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
            label.backgroundColor = .clear
            return label
        }

        func updateUIView(_ label: UILabel, context: Context) {
            let font = Typeface.uiFont(Typeface.black, size: 32)
            let paragraph = NSMutableParagraphStyle()
            paragraph.minimumLineHeight = 37
            paragraph.maximumLineHeight = 37
            label.attributedText = NSAttributedString(string: text, attributes: [
                .font: font,
                .kern: -1,
                // RN centres the font in its explicit line height with this baseline offset.
                .baselineOffset: max(0, (37 - font.lineHeight) / 2),
                .foregroundColor: UIColor(red: 74 / 255, green: 15 / 255, blue: 20 / 255, alpha: 1),
                .paragraphStyle: paragraph
            ])
        }
    }

    private static func clamp01(_ value: Double) -> Double { min(1, max(0, value)) }
    private static func segment(_ t: Double, _ a: Double, _ b: Double) -> Double {
        clamp01((t - a) / (b - a))
    }
    private static func smooth(_ value: Double) -> Double {
        let u = clamp01(value)
        return u * u * (3 - 2 * u)
    }
    private static func cubic(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }
    private static func back(_ x: Double) -> Double {
        1 + 2.4 * pow(x - 1, 3) + 1.4 * pow(x - 1, 2)
    }
    private static func stageAt(_ t: Double) -> Double {
        if t < 3 { return back(segment(t, 1, 2.2)) }
        if t < 5.6 { return 1 + back(segment(t, 3, 4.2)) }
        if t < 6.6 { return 2 - cubic(segment(t, 5.6, 6.6)) }
        return 1 - cubic(segment(t, 6.6, 7.4))
    }

    private func elapsed(at date: Date) -> Double {
        accumulated + (running ? max(0, date.timeIntervalSince(anchor)) : 0)
    }

    private func stage(at now: Double) -> Double {
        if reduced { return 1 }
        if touching { return dragged }
        if manual {
            let since = max(0, now - released)
            if since < Self.snap {
                return releaseFrom + (snapTo - releaseFrom) * Self.cubic(since / Self.snap)
            }
            if since < Self.snap + Self.hold { return snapTo }
            if since < Self.snap + Self.hold + Self.unwind {
                return snapTo * (1 - Self.cubic((since - Self.snap - Self.hold) / Self.unwind))
            }
            let restarted = since - Self.snap - Self.hold - Self.unwind
            return autoPlay ? Self.stageAt(restarted.truncatingRemainder(dividingBy: Self.loop)) : 0
        }
        return autoPlay ? Self.stageAt(max(0, now - origin).truncatingRemainder(dividingBy: Self.loop)) : 0
    }

    var body: some View {
        GeometryReader { geo in
            let safe = geo.safeAreaInsets
            let size = CGSize(width: geo.size.width + safe.leading + safe.trailing,
                              height: geo.size.height + safe.top + safe.bottom)
            let footerTop = size.height - safe.bottom - 16 - 54 - 12 - 48
            let footerBounds = CGRect(x: 24, y: footerTop, width: size.width - 48,
                                      height: size.height - footerTop)
            ZStack(alignment: .topLeading) {
                Palette.background
                TimelineView(.animation(paused: !running || touching || (!autoPlay && !manual))) { frame in
                    let value = stage(at: elapsed(at: frame.date))
                    ZStack(alignment: .topLeading) {
                        Canvas { context, _ in
                            drawBlock(context, origin: CGPoint(x: size.width / 2 - 34, y: size.height * 0.45), stage: value)
                        }
                        .accessibilityLabel("Storage block. Drag sideways to change the plan.")
                        .accessibilityValue(Self.plans[Int(min(2, max(0, value)).rounded())].name)

                        readout(stage: value, width: size.width)
                            .offset(y: size.height * 0.645)
                            .allowsHitTesting(false)

                        footer(stage: value, width: size.width - 48, bottom: safe.bottom)
                            .offset(x: 24, y: footerTop)
                    }
                    .frame(width: size.width, height: size.height, alignment: .topLeading)
                }

                header(width: size.width - 48)
                    .offset(x: 24, y: safe.top + 16)
                    .allowsHitTesting(false)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .contentShape(Rectangle())
            .gesture(pan(excluding: footerBounds))
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
        .onChange(of: scenePhase) { _, value in setClockRunning(value == .active && !reduced) }
        .onChange(of: reduced) { _, value in setClockRunning(scenePhase == .active && !value) }
        .onChange(of: gestureTouching) { _, value in if !value { endDrag() } }
        .task(id: SettleKey(touchID: touchID, running: running)) { await settle() }
    }

    private func header(width: CGFloat) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("VAULT")
                .font(Typeface.font(Typeface.black, size: 14))
                .tracking(2)
                .frame(height: ceil(Typeface.uiFont(Typeface.black, size: 14).lineHeight * displayScale) / displayScale)
            Heading(text: title)
                .frame(width: width, height: 74)
                .padding(.top, 16)
            Text(subtitle)
                .font(Typeface.font(Typeface.semibold, size: 15))
                .foregroundStyle(Palette.soft)
                .frame(maxWidth: .infinity, alignment: .leading)
                .frame(height: 21)
                .padding(.top, 10)
        }
        .foregroundStyle(Palette.ink)
        .frame(width: width, alignment: .topLeading)
    }

    private func readout(stage value: Double, width: CGFloat) -> some View {
        ZStack(alignment: .top) {
            ForEach(Self.plans.indices, id: \.self) { index in
                let plan = Self.plans[index]
                let opacity = 1 - Self.smooth(abs(value - Double(index)) / 0.5)
                VStack(spacing: 2) {
                    Text(plan.size)
                        .font(Typeface.font(Typeface.black, size: 54))
                        .tracking(-2)
                        .foregroundStyle(Palette.ink)
                        .frame(height: 62)
                    Text(plan.price)
                        .font(Typeface.font(Typeface.semibold, size: 16))
                        .foregroundStyle(Palette.soft)
                        .frame(height: 21)
                }
                .frame(width: width)
                .opacity(opacity)
                .offset(y: (value - Double(index)) * -14)
                .accessibilityHidden(opacity < 0.5)
            }
        }
        .frame(width: width, height: 86, alignment: .top)
    }

    private func footer(stage value: Double, width: CGFloat, bottom: CGFloat) -> some View {
        let segment = (width - 8) / 3
        return VStack(spacing: 12) {
            ZStack(alignment: .topLeading) {
                Capsule().fill(Palette.track)
                Capsule().fill(.white)
                    .frame(width: segment, height: 40)
                    .offset(x: 4 + min(2, max(0, value)) * segment, y: 4)
                HStack(spacing: 0) {
                    ForEach(Self.plans.indices, id: \.self) { index in
                        Text(Self.plans[index].name)
                            .font(Typeface.font(Typeface.semibold, size: 15))
                            .foregroundStyle(Palette.ink)
                            .frame(width: segment, height: 40)
                    }
                }
                .offset(x: 4, y: 4)
            }
            .frame(height: 48)
            Text(action)
                .font(Typeface.font(Typeface.semibold, size: 17))
                .foregroundStyle(.white)
                .frame(width: width, height: 54)
                .background(Palette.ink, in: Capsule())
                .accessibilityAddTraits(.isButton)
        }
        .frame(width: width)
        .padding(.bottom, bottom + 16)
    }

    private func drawBlock(_ context: GraphicsContext, origin: CGPoint, stage value: Double) {
        let length = 32 + 116 * (value <= 1 ? value : 1)
        let height = 32 + 116 * max(0, value - 1)
        let depth = 32.0
        func project(_ x: Double, _ y: Double, _ z: Double = 0) -> CGPoint {
            CGPoint(x: origin.x + 0.866 * (x - y), y: origin.y + 0.5 * (x + y) - z)
        }
        func quad(_ points: [CGPoint], _ color: Color) {
            var path = Path()
            path.addLines(points)
            path.closeSubpath()
            context.fill(path, with: .color(color))
        }
        let floor = [CGPoint.zero, CGPoint(x: length, y: 0),
                     CGPoint(x: length, y: depth), CGPoint(x: 0, y: depth)]
        let a = floor.map { project($0.x, $0.y) }
        let b = floor.map { project($0.x - 0.18 * height, $0.y + 0.9 * height) }
        quad(a, Palette.shadow)
        quad(b, Palette.shadow)
        for i in 0..<4 {
            let j = (i + 1) % 4
            quad([a[i], a[j], b[j], b[i]], Palette.shadow)
        }
        quad([project(0, depth), project(length, depth),
              project(length, depth, height), project(0, depth, height)], Palette.left)
        quad([project(length, 0), project(length, depth),
              project(length, depth, height), project(length, 0, height)], Palette.right)
        quad([project(0, 0, height), project(length, 0, height),
              project(length, depth, height), project(0, depth, height)], .white)
    }

    private func pan(excluding footer: CGRect) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($gestureTouching) { _, isTouching, _ in isTouching = true }
            .onChanged { value in
                guard !reduced, !footer.contains(value.startLocation) else { return }
                if !touching {
                    grabbed = stage(at: elapsed(at: .now))
                    dragged = grabbed
                    touching = true
                    manual = true
                    activated = false
                    lastStep = Int(min(2, max(0, grabbed)).rounded())
                    touchID += 1
                }
                // RN begins the pan on touch-down, but only updates after minDistance = 4.
                if hypot(value.translation.width, value.translation.height) >= 4 { activated = true }
                guard activated else { return }
                dragged = min(2.15, max(-0.15, grabbed + value.translation.width / 120))
                let step = Int(min(2, max(0, dragged)).rounded())
                if step != lastStep {
                    lastStep = step
                    haptic.impactOccurred()
                }
            }
            .onEnded { _ in endDrag() }
    }

    private func endDrag() {
        guard touching else { return }
        releaseFrom = dragged
        snapTo = min(2, max(0, dragged)).rounded()
        released = elapsed(at: .now)
        touching = false
        activated = false
        touchID += 1
    }

    private func setClockRunning(_ active: Bool) {
        guard active != running else { return }
        if active {
            anchor = .now
            running = true
        } else {
            endDrag()
            accumulated = elapsed(at: .now)
            running = false
        }
    }

    private func settle() async {
        guard manual, !touching, running else { return }
        let restarts = released + Self.snap + Self.hold + Self.unwind
        try? await Task.sleep(for: .seconds(max(0, restarts - elapsed(at: .now))))
        guard !Task.isCancelled, !touching, running else { return }
        origin = restarts
        manual = false
    }
}

#Preview {
    IsoFoldStorage(autoPlay: true)
}
