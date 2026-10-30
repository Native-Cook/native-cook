import SwiftUI
import UIKit

// A faithful port of the React Native reference: a night-blue sky, a 150 pt
// hairline reading, a translucent hourly forecast, and seeded flake phases.
// Loop (8 s): 0–0.8 rest; 0.8–1.4 damped cloud breath; 1.2–2 snow builds;
// 2–4 full snow; 4–4.8 emission fades; 4.8–6.8 the last flakes fall; 6.8–8 rest.
// Each of 72 six-armed flakes falls 380 pt in 2 s. Its position and visibility use
// the cloud position and intensity when it was born, sampled at 30 Hz.
// Drag anywhere: clamp x to centre ±110 pt, emit fully, then fade emission over
// 0.6 s on release. Hold x for 2 s, return over 0.8 s, and restart the loop.
struct SnowCloudForecast: View {
    static let loop: Double = 8

    var city = "Northfield"
    var other = "Halden"
    var temperature = "−8°"
    var range = "↑ 2°   ↓ −11°"
    var summary = "Snow is on its way"
    var autoPlay = false

    private static let cloudSize = 320.0
    private static let fall = 2.0
    private static let drop = 380.0
    private static let historyRate = 30.0
    private static let hold = 2.0
    private static let returnDuration = 0.8

    private enum Palette {
        static let background = color(0x070B18)
        static let skyTop = color(0x060914)
        static let skyBottom = color(0x1B2547)
        static let dim = color(0x8D8F98)
        static let flake = color(0xDCEBFF)
        static let cloudMark = color(0x5B5E68)

        static func color(_ hex: UInt32) -> Color {
            Color(.sRGB, red: Double((hex >> 16) & 255) / 255,
                  green: Double((hex >> 8) & 255) / 255,
                  blue: Double(hex & 255) / 255, opacity: 1)
        }
    }

    private static let cloud: UIImage = {
        guard let url = Bundle.main.url(forResource: "SnowCloudForecast-cloud", withExtension: "png"),
              let image = UIImage(contentsOfFile: url.path) else { return UIImage() }
        return image.preparingForDisplay() ?? image
    }()

    private struct Flake {
        let phase: Double
        let dx: Double
        let size: Double
        let sway: Double
        let threshold: Double
        let spin: Double
        let path: Path
    }

    // Paths and artwork are prepared once, never rebuilt or decoded per frame.
    private static let flakes: [Flake] = (0..<72).map { i in
        func hash(_ n: Double) -> Double {
            let x = sin(Double(i + 1) * 91.345 + n * 47.853) * 43758.5453
            return x - floor(x)
        }
        let size = 3.5 + 5 * hash(3)
        var path = Path()
        for k in 0..<6 {
            let a = Double(k) * .pi / 3
            let ca = cos(a), sa = sin(a)
            path.move(to: .zero)
            path.addLine(to: CGPoint(x: ca * size, y: sa * size))
            let bx = ca * size * 0.6, by = sa * size * 0.6
            let b = size * 0.32
            for direction in [-0.7, 0.7] {
                path.move(to: CGPoint(x: bx, y: by))
                path.addLine(to: CGPoint(x: bx + cos(a + direction) * b,
                                        y: by + sin(a + direction) * b))
            }
        }
        return Flake(phase: hash(1), dx: (hash(2) - 0.5) * 260, size: size,
                     sway: 5 + 10 * hash(4), threshold: 0.05 + 0.9 * hash(5),
                     spin: (hash(6) - 0.5) * 4, path: path)
    }

    private static let hours = [
        (time: "Now", temperature: "−8°", snow: true),
        (time: "15", temperature: "−8°", snow: true),
        (time: "16", temperature: "−9°", snow: true),
        (time: "17", temperature: "−9°", snow: false),
        (time: "18", temperature: "−10°", snow: false),
        (time: "19", temperature: "−11°", snow: true),
        (time: "20", temperature: "−11°", snow: false)
    ]

    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.displayScale) private var displayScale
    @GestureState private var gestureTouching = false
    @State private var accumulated = 0.0
    @State private var anchor = Date()
    @State private var running = false
    @State private var interactions: [Interaction] = []
    @State private var haptic = UIImpactFeedbackGenerator(style: .soft)

    private struct FingerSample {
        var time: Double
        var x: Double
    }

    private struct Interaction {
        var start: Double
        var origin: Double
        var breath: Double
        var samples: [FingerSample]
        var released: Double?
    }

    private struct Field {
        var origin: Double
        var x: Double
        var intensity: Double
        var breath: Double
        var tau: Double
        var bob: Double
    }

    private static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    private static func segment(_ t: Double, _ a: Double, _ b: Double) -> Double {
        clamp((t - a) / (b - a))
    }
    private static func smooth(_ x: Double) -> Double {
        let u = clamp(x)
        return u * u * (3 - 2 * u)
    }
    private static func cubic(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }
    private static func intensity(at t: Double) -> Double {
        smooth(segment(t, 1.2, 2)) * (1 - smooth(segment(t, 4, 4.8)))
    }
    private static func breath(at t: Double) -> Double {
        let x = segment(t, 0.8, 1.4)
        if x <= 0 || x >= 1 {
            return 0.02 * smooth(segment(t, 1.4, 1.6)) * (1 - smooth(segment(t, 4.8, 6)))
        }
        return 0.04 * sin(x * .pi) + 0.02 * smooth(x)
    }

    private func elapsed(at date: Date) -> Double {
        accumulated + (running ? max(0, date.timeIntervalSince(anchor)) : 0)
    }

    // Event history replaces RN's mutable 90-slot ring. Sampling a past event at
    // 30 Hz preserves the emission trail without writing view state every frame.
    private func field(at now: Double, centre: Double) -> Field {
        guard !reduced else {
            return Field(origin: 0, x: centre, intensity: 0, breath: 0, tau: 0, bob: 0)
        }
        var origin = 0.0
        var x = centre
        var intensity = 0.0
        var breath = 0.0
        var manual = false
        if let interaction = interactions.last(where: { $0.start <= now }) {
            origin = interaction.origin
            if let released = interaction.released, now >= released + Self.hold + Self.returnDuration {
                origin = released + Self.hold + Self.returnDuration
            } else {
                manual = true
                breath = interaction.breath
                x = interaction.samples.last(where: { $0.time <= now })?.x ?? centre
                if let released = interaction.released, now >= released {
                    let since = now - released
                    let k = Self.cubic(Self.clamp((since - Self.hold) / Self.returnDuration))
                    x += (centre - x) * k
                    intensity = 1 - Self.smooth(since / 0.6)
                } else {
                    intensity = 1
                }
            }
        }
        let tau = max(0, now - origin).truncatingRemainder(dividingBy: Self.loop)
        if !manual, autoPlay {
            intensity = Self.intensity(at: tau)
            breath = Self.breath(at: tau)
        }
        return Field(origin: origin, x: x, intensity: intensity, breath: breath,
                     tau: tau, bob: sin(tau / Self.loop * .pi * 2) * 4)
    }

    var body: some View {
        GeometryReader { geo in
            let safe = geo.safeAreaInsets
            let size = CGSize(width: geo.size.width + safe.leading + safe.trailing,
                              height: geo.size.height + safe.top + safe.bottom)
            let cloudTop = safe.top + 20
            let snowTop = cloudTop + Self.cloudSize * 0.66
            ZStack(alignment: .topLeading) {
                LinearGradient(colors: [Palette.skyTop, Palette.skyBottom],
                               startPoint: .top, endPoint: .bottom)
                reading(top: snowTop + 60, width: size.width)

                TimelineView(.animation(paused: !running || reduced)) { frame in
                    let now = elapsed(at: frame.date)
                    let state = field(at: now, centre: size.width / 2)
                    Canvas { context, _ in
                        if !reduced {
                            drawSnow(context: context, now: now, tau: state.tau,
                                     top: snowTop, centre: size.width / 2)
                        }
                        var cloudContext = context
                        cloudContext.translateBy(x: state.x, y: cloudTop + Self.cloudSize / 2 + state.bob)
                        cloudContext.scaleBy(x: 1 + state.breath, y: 1 + state.breath)
                        cloudContext.draw(Image(uiImage: Self.cloud), in: CGRect(
                            x: -Self.cloudSize / 2, y: -Self.cloudSize / 2,
                            width: Self.cloudSize, height: Self.cloudSize))
                    }
                    .frame(width: size.width, height: size.height)
                    .accessibilityLabel("\(city), \(temperature), \(summary)")
                }

                HStack(spacing: 22) {
                    Text(city).font(.system(size: 17, weight: .bold)).foregroundStyle(.white)
                    Text(other).font(.system(size: 17, weight: .medium)).foregroundStyle(Palette.dim)
                }
                .frame(width: size.width, height: 24)
                .offset(y: safe.top + 10)
                .allowsHitTesting(false)

            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .contentShape(Rectangle())
            .gesture(drag(centre: size.width / 2))
            .overlay { hours(safe: safe, size: size) }
            .ignoresSafeArea()
        }
        .background(Palette.background)
        .preferredColorScheme(.dark)
        .onAppear {
            updateClock()
            haptic.prepare()
        }
        .onChange(of: scenePhase) { _, _ in updateClock() }
        .onChange(of: reduced) { _, _ in
            if reduced { finishTouch() }
            updateClock()
        }
        .onChange(of: gestureTouching) { _, touching in
            if !touching { finishTouch() }
        }
    }

    private func reading(top: Double, width: Double) -> some View {
        Reading(temperature: temperature, range: range, summary: summary)
            .frame(width: width - 48, height: 214, alignment: .topLeading)
            .offset(x: 24, y: top)
            .allowsHitTesting(false)
    }

    // RN uses TextKit with fixed paragraph heights and no font leading. In
    // particular, 150 pt type has a natural height greater than its 156 pt row;
    // centring SwiftUI Text inside that row would move the temperature baseline.
    private struct Reading: UIViewRepresentable {
        var temperature: String
        var range: String
        var summary: String

        func makeUIView(context: Context) -> ReadingView {
            let view = ReadingView()
            view.backgroundColor = .clear
            view.isOpaque = false
            view.contentMode = .redraw
            view.isUserInteractionEnabled = false
            return view
        }

        func updateUIView(_ view: ReadingView, context: Context) {
            view.copy = [temperature, range, summary]
        }
    }

    private final class ReadingView: UIView {
        var copy: [String] = [] {
            didSet { if copy != oldValue { setNeedsDisplay() } }
        }

        override func draw(_ rect: CGRect) {
            guard copy.count == 3 else { return }
            draw(copy[0], font: .systemFont(ofSize: 150, weight: .ultraLight),
                 left: -6, top: 0, lineHeight: 156, kern: -8)
            draw(copy[1], font: .systemFont(ofSize: 16, weight: .semibold),
                 top: 152, lineHeight: 22)
            draw(copy[2], font: .systemFont(ofSize: 30, weight: .light),
                 top: 178, lineHeight: 36, kern: -0.5)
        }

        private func draw(_ text: String, font: UIFont, left: CGFloat = 0, top: CGFloat,
                          lineHeight: CGFloat, kern: CGFloat = 0) {
            let paragraph = NSMutableParagraphStyle()
            paragraph.minimumLineHeight = lineHeight
            paragraph.maximumLineHeight = lineHeight
            var attributes: [NSAttributedString.Key: Any] = [
                .font: font, .foregroundColor: UIColor.white,
                .paragraphStyle: paragraph, .kern: kern
            ]
            if lineHeight >= font.lineHeight {
                attributes[.baselineOffset] = (lineHeight - font.lineHeight) / 2
            }
            let text = NSTextStorage(string: text, attributes: attributes)
            let layout = NSLayoutManager()
            layout.usesFontLeading = false
            let container = NSTextContainer(size: CGSize(width: bounds.width - left, height: lineHeight))
            container.lineFragmentPadding = 0
            container.lineBreakMode = .byClipping
            layout.addTextContainer(container)
            text.addLayoutManager(layout)
            let glyphs = layout.glyphRange(for: container)
            layout.drawGlyphs(forGlyphRange: glyphs, at: CGPoint(x: left, y: top))
        }
    }

    private func hours(safe: EdgeInsets, size: CGSize) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Snow from 14:00 · 4 cm by evening")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Palette.dim)
                .padding(.leading, 4)
                .padding(.bottom, 10)
            Rectangle().fill(.white.opacity(0.12))
                .frame(height: 1 / displayScale)
                .padding(.bottom, 8)
            HStack(spacing: 0) {
                ForEach(Self.hours.indices, id: \.self) { i in
                    let hour = Self.hours[i]
                    VStack(spacing: 0) {
                        Text(hour.time)
                            .font(.system(size: 13, weight: .medium))
                            .foregroundStyle(i == 0 ? .white : Palette.dim)
                            .frame(height: 16)
                        Circle()
                            .fill(hour.snow ? .clear : Palette.cloudMark)
                            .overlay {
                                if hour.snow { Circle().strokeBorder(Palette.flake, lineWidth: 2) }
                            }
                            .frame(width: 14, height: 14)
                            .padding(.vertical, 10)
                        Text(hour.temperature)
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(.white)
                            .frame(height: 17)
                    }
                    .padding(.vertical, 10)
                    .frame(width: 46)
                    .background(i == 0 ? .white.opacity(0.12) : .clear,
                                in: RoundedRectangle(cornerRadius: 23))
                    if i < Self.hours.count - 1 { Spacer(minLength: 0) }
                }
            }
        }
        .padding(14)
        .padding(1)
        .frame(width: size.width - 28)
        .background(.white.opacity(0.07), in: RoundedRectangle(cornerRadius: 26))
        .overlay { RoundedRectangle(cornerRadius: 26).strokeBorder(.white.opacity(0.10), lineWidth: 1) }
        .padding(.bottom, safe.bottom + 14)
        .frame(width: size.width, height: size.height, alignment: .bottom)
    }

    private func drawSnow(context: GraphicsContext, now: Double, tau: Double, top: Double, centre: Double) {
        for flake in Self.flakes {
            let q = tau / Self.fall + flake.phase
            let p = q - floor(q)
            let born = now - p * Self.fall
            // Before first appearance the RN history ring contains zero intensity.
            guard born >= 0 else { continue }
            let sampleTime = floor(born * Self.historyRate) / Self.historyRate
            let emission = field(at: sampleTime, centre: centre)
            guard emission.intensity >= flake.threshold else { continue }
            let alpha = Self.smooth(p / 0.1) * (1 - Self.smooth((p - 0.75) / 0.25))
                * Self.clamp((emission.intensity - flake.threshold) * 6)
            guard alpha > 0.01 else { continue }
            let x = emission.x + flake.dx * (0.55 + 0.45 * p)
                + sin(p * .pi * 2.6 + flake.phase * 6.28) * flake.sway
            var c = context
            c.opacity = alpha * 0.95
            c.translateBy(x: x, y: top + p * Self.drop)
            c.rotate(by: .radians(p * flake.spin))
            c.stroke(flake.path, with: .color(Palette.flake),
                     style: StrokeStyle(lineWidth: max(1, flake.size * 0.22), lineCap: .round))
        }
    }

    private func updateClock() {
        let date = Date()
        accumulated = elapsed(at: date)
        anchor = date
        running = scenePhase == .active && !reduced
    }

    private func drag(centre: Double) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($gestureTouching) { _, touching, _ in touching = true }
            .onChanged { value in
                guard !reduced else { return }
                let now = elapsed(at: .now)
                let x = min(centre + 110, max(centre - 110, value.location.x))
                if interactions.last?.released != nil || interactions.isEmpty {
                    let current = field(at: now, centre: centre)
                    interactions.append(Interaction(start: now, origin: current.origin,
                                                    breath: current.breath,
                                                    samples: [FingerSample(time: now, x: x)]))
                    haptic.impactOccurred()
                } else {
                    let last = interactions.count - 1
                    interactions[last].samples.append(FingerSample(time: now, x: x))
                    // Only a two-second trail can still contain visible snow.
                    while interactions[last].samples.count > 2,
                          interactions[last].samples[1].time < now - 3 {
                        interactions[last].samples.removeFirst()
                    }
                }
            }
            .onEnded { _ in finishTouch() }
    }

    private func finishTouch() {
        guard !interactions.isEmpty, interactions[interactions.count - 1].released == nil else { return }
        interactions[interactions.count - 1].released = elapsed(at: .now)
    }
}

#Preview {
    SnowCloudForecast(autoPlay: true)
}
