import CoreText
import SwiftUI
import UIKit

/// A faithful port of the React Native travel posters, including its bundled photographs.
/// Four 2.2 s stops make an 8.8 s loop. Each stop holds for 1.4 s, then the poster turns
/// through 90 degrees and returns as the next trip over 0.8 s (ease-in-out cubic).
/// Behind it the next photo wipes in from the right while the current photo drifts 60 pt left.
/// A 300 pt swipe changes one trip; release snaps for 0.45 s, holds for 2 s, and resumes there.
struct EscapePosterCards: View {
    static let loop: Double = 8.8

    var brand = "Wander Post"
    var action = "See this trip"
    var autoPlay = false

    private typealias M = EscapePosterCardsMotion
    private typealias A = EscapePosterCardsAssets

    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scene
    @State private var visible = false
    @State private var accumulated = 0.0
    @State private var runningSince = Date()
    @State private var running = false
    @State private var origin = 0.0
    @State private var touching = false
    @State private var panActivated = false
    @GestureState private var panActive = false
    @State private var grabbed = 0.0
    @State private var manualIndex = 0.0
    @State private var lastStep = 0.0
    @State private var released: Double?
    @State private var snapFrom = 0.0
    @State private var snapTo = 0.0
    @State private var haptic = UISelectionFeedbackGenerator()

    var body: some View {
        GeometryReader { outer in
            let safe = outer.safeAreaInsets
            GeometryReader { geometry in
                let size = geometry.size
                TimelineView(.animation(paused: !visible || scene != .active || reduced || (!autoPlay && !touching && released == nil))) { timeline in
                    screen(size: size, safe: safe, index: index(at: timeline.date))
                }
            }
            .ignoresSafeArea()
        }
        .background(M.color(0x1B2A22).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear {
            A.prepare()
            accumulated = 0
            origin = 0
            runningSince = .now
            running = scene == .active && !reduced
            visible = true
            released = nil
            haptic.prepare()
        }
        .onDisappear {
            pauseClock()
            visible = false
            touching = false
        }
        .onChange(of: scene) { _, phase in
            if phase == .active && visible && !reduced { resumeClock() }
            else { pauseClock() }
        }
        .onChange(of: reduced) { _, value in
            if value { pauseClock() }
            else if visible && scene == .active { resumeClock() }
        }
        .onChange(of: panActive) { _, active in
            guard !active else { return }
            // Finalize cancelled gestures as well as successful pans, like RN onFinalize.
            Task { @MainActor in
                await Task.yield()
                if touching && !panActive { finishPan() }
            }
        }
    }

    private func screen(size: CGSize, safe: EdgeInsets, index: Double) -> some View {
        let base = floor(index)
        let fraction = index - base
        let face = M.wrap(fraction < 0.5 ? base : base + 1)
        let angle = fraction < 0.5 ? fraction * 180 : (fraction - 1) * 180
        let scale = 1 - 0.06 * sin(.pi * fraction)

        return ZStack(alignment: .topLeading) {
            ZStack(alignment: .topLeading) {
                Canvas { context, _ in
                    let current = M.wrap(base)
                    let next = M.wrap(base + 1)
                    A.drawPhoto(current, context: context, frame: CGRect(x: -60 * fraction, y: 0, width: size.width + 60, height: size.height))
                    var incoming = context
                    let edge = size.width * (1 - fraction)
                    incoming.clip(to: Path(CGRect(x: edge, y: 0, width: size.width - edge, height: size.height)))
                    A.drawPhoto(next, context: incoming, frame: CGRect(x: 60 * (1 - fraction), y: 0, width: size.width + 60, height: size.height))
                }
                .frame(width: size.width, height: size.height)

                LinearGradient(colors: [.black.opacity(0.35), .clear], startPoint: .top, endPoint: .bottom)
                    .frame(width: size.width, height: safe.top + 90)
                LinearGradient(colors: [.clear, .black.opacity(0.55)], startPoint: .top, endPoint: .bottom)
                    .frame(width: size.width, height: 220)
                    .offset(y: size.height - 220)

                heading(width: size.width, safeTop: safe.top)

                Image(uiImage: A.posters[face])
                    .resizable()
                    .frame(width: 264, height: 360)
                    .shadow(color: .black.opacity(0.3), radius: 24, x: 0, y: 14)
                    .modifier(EscapePosterCardsTurn(angle: angle, scale: scale))
                    .offset(x: (size.width - 264) / 2, y: size.height * 0.47 - 180)
                    .accessibilityLabel("Trip poster. Swipe for the next trip.")
                    .accessibilityValue(M.trips[face].title.replacingOccurrences(of: "\n", with: " "))
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .contentShape(Rectangle())
            .gesture(pan, including: reduced ? .none : .all)

            footer(size: size, safeBottom: safe.bottom, index: index)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
    }

    private func heading(width: CGFloat, safeTop: CGFloat) -> some View {
        HStack(spacing: 0) {
            Text(brand)
                .font(A.font(size: 17))
                .foregroundStyle(.white)
                .fixedSize()
            Spacer(minLength: 0)
            Text("Deals · Spring")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(.white.opacity(0.8))
                .fixedSize()
        }
        .frame(width: width - 48, height: 34)
        .offset(x: 24, y: safeTop + 8)
        .allowsHitTesting(false)
    }

    private func footer(size: CGSize, safeBottom: CGFloat, index: Double) -> some View {
        VStack(spacing: 18) {
            HStack(spacing: 8) {
                ForEach(0..<4, id: \.self) { k in
                    let distance = min(1, abs(M.mod(index - Double(k) + 2, 4) - 2))
                    Capsule()
                        .fill(.white)
                        .frame(width: 8 + 18 * (1 - distance), height: 8)
                        .opacity(0.45 + 0.55 * (1 - distance))
                }
            }
            .frame(height: 8)
            .accessibilityHidden(true)

            Text(action)
                .font(A.font(size: 15))
                .foregroundStyle(M.color(0x1E4D2B))
                .frame(width: size.width - 48, height: 58)
                .background(M.color(0xF6F2E7), in: Capsule())
                .accessibilityAddTraits(.isButton)
        }
        .frame(width: size.width - 48, height: 84, alignment: .top)
        .offset(x: 24, y: size.height - safeBottom - 14 - 84)
    }

    private func elapsed(at date: Date) -> Double {
        accumulated + (running ? max(0, date.timeIntervalSince(runningSince)) : 0)
    }

    private func index(at date: Date) -> Double {
        let now = elapsed(at: date)
        if touching { return manualIndex }
        if let released {
            let since = max(0, now - released)
            if since < M.snap {
                return snapFrom + (snapTo - snapFrom) * M.ease(since / M.snap)
            }
            if since <= M.snap + M.hold || !autoPlay { return snapTo }
            let phase = M.mod(Double(M.wrap(snapTo)) * M.stop + since - M.snap - M.hold, Self.loop)
            return M.index(at: phase)
        }
        return autoPlay ? M.index(at: M.mod(now - origin, Self.loop)) : manualIndex
    }

    private var pan: some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($panActive) { _, active, _ in active = true }
            .onChanged { value in
                guard !reduced else { return }
                if !touching {
                    let current = index(at: .now)
                    grabbed = current
                    manualIndex = current
                    lastStep = floor(current + 0.5)
                    touching = true
                    panActivated = false
                    released = nil
                }
                if !panActivated {
                    guard hypot(value.translation.width, value.translation.height) >= 4 else { return }
                    panActivated = true
                }
                let current = grabbed - value.translation.width / 300
                manualIndex = current
                let step = floor(current + 0.5)
                if step != lastStep {
                    lastStep = step
                    haptic.selectionChanged()
                    haptic.prepare()
                }
            }
            .onEnded { _ in finishPan() }
    }

    private func finishPan() {
        guard touching else { return }
        touching = false
        panActivated = false
        snapFrom = manualIndex
        snapTo = floor(manualIndex + 0.5)
        released = elapsed(at: .now)
    }

    private func pauseClock() {
        accumulated = elapsed(at: .now)
        running = false
    }

    private func resumeClock() {
        guard !running else { return }
        runningSince = .now
        running = true
    }
}

private struct EscapePosterCardsTurn: GeometryEffect {
    var angle: Double
    var scale: Double

    func effectValue(size: CGSize) -> ProjectionTransform {
        var turn = CATransform3DIdentity
        turn.m34 = -1 / 900
        turn = CATransform3DRotate(turn, angle * .pi / 180, 0, 1, 0)
        turn = CATransform3DScale(turn, scale, scale, 1)
        return ProjectionTransform(CGAffineTransform(translationX: -size.width / 2, y: -size.height / 2))
            .concatenating(ProjectionTransform(turn))
            .concatenating(ProjectionTransform(CGAffineTransform(translationX: size.width / 2, y: size.height / 2)))
    }
}

private enum EscapePosterCardsMotion {
    static let stop = 2.2
    static let snap = 0.45
    static let hold = 2.0

    struct Trip {
        var title: String
        var date: String
        var from: String
        var nights: String
        var board: String
        var photo: String
    }

    static let trips = [
        Trip(title: "Villa\nEscapes", date: "03 May 2026", from: "Harbour City", nights: "4 nights", board: "Self-catering", photo: "photo-villa"),
        Trip(title: "Winter\nBreaks", date: "11 Jan 2026", from: "Harbour City", nights: "6 nights", board: "Half board", photo: "photo-snow"),
        Trip(title: "Lake\nWeekends", date: "19 Jun 2026", from: "North Gate", nights: "2 nights", board: "Breakfast", photo: "photo-forest"),
        Trip(title: "Night Food\nTrails", date: "07 Oct 2026", from: "Harbour City", nights: "5 nights", board: "Room only", photo: "photo-street"),
    ]

    static func mod(_ x: Double, _ period: Double) -> Double {
        (x.truncatingRemainder(dividingBy: period) + period).truncatingRemainder(dividingBy: period)
    }
    static func wrap(_ x: Double) -> Int { Int(mod(x, 4)) }
    static func ease(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }
    static func index(at clock: Double) -> Double {
        let k = floor(clock / stop)
        let u = min(1, max(0, (clock - k * stop - 1.4) / (stop - 1.4)))
        return k + ease(u)
    }
    static func color(_ hex: UInt32) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, opacity: 1)
    }
}

private enum EscapePosterCardsAssets {
    private typealias M = EscapePosterCardsMotion

    private static let registered: Void = {
        if let url = Bundle.main.url(forResource: "EscapePosterCards-ArchivoExpanded-Black", withExtension: "ttf") {
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()

    private static let photos: [UIImage] = M.trips.map { trip in
        guard let url = Bundle.main.url(forResource: "EscapePosterCards-\(trip.photo)", withExtension: "jpg"),
              let source = UIImage(contentsOfFile: url.path) else { return UIImage() }
        return source.preparingForDisplay() ?? source
    }

    // Static card faces are drawn once; turning the poster never decodes a JPEG or lays out text.
    static let posters: [UIImage] = M.trips.indices.map(makePoster)

    static func prepare() {
        _ = registered
        _ = photos
        _ = posters
    }

    static func font(size: CGFloat) -> Font {
        _ = registered
        return .custom("ArchivoExpanded-Black", fixedSize: size)
    }

    private static func uiFont(size: CGFloat) -> UIFont {
        _ = registered
        return UIFont(name: "ArchivoExpanded-Black", size: size) ?? .systemFont(ofSize: size, weight: .black)
    }

    static func drawPhoto(_ k: Int, context: GraphicsContext, frame: CGRect) {
        let photo = photos[k]
        guard photo.size.width > 0, photo.size.height > 0 else { return }
        let scale = max(frame.width / photo.size.width, frame.height / photo.size.height)
        let width = photo.size.width * scale
        let height = photo.size.height * scale
        var clipped = context
        clipped.clip(to: Path(frame))
        clipped.draw(Image(uiImage: photo), in: CGRect(x: frame.midX - width / 2, y: frame.midY - height / 2, width: width, height: height))
    }

    private static func makePoster(_ k: Int) -> UIImage {
        let trip = M.trips[k]
        let format = UIGraphicsImageRendererFormat()
        format.scale = 3
        format.opaque = false
        return UIGraphicsImageRenderer(size: CGSize(width: 264, height: 360), format: format).image { renderer in
            let cream = UIColor(red: 246 / 255, green: 242 / 255, blue: 231 / 255, alpha: 1)
            let green = UIColor(red: 30 / 255, green: 77 / 255, blue: 43 / 255, alpha: 1)
            cream.setFill()
            UIBezierPath(roundedRect: CGRect(x: 0, y: 0, width: 264, height: 360), cornerRadius: 10).fill()

            let window = CGRect(x: 10, y: 10, width: 244, height: 156)
            renderer.cgContext.saveGState()
            UIBezierPath(roundedRect: window, cornerRadius: 3).addClip()
            let photo = photos[k]
            if photo.size.width > 0, photo.size.height > 0 {
                let scale = max(window.width / photo.size.width, window.height / photo.size.height)
                let size = CGSize(width: photo.size.width * scale, height: photo.size.height * scale)
                photo.draw(in: CGRect(x: window.midX - size.width / 2, y: window.midY - size.height / 2, width: size.width, height: size.height))
            }
            renderer.cgContext.restoreGState()

            let titleParagraph = NSMutableParagraphStyle()
            titleParagraph.minimumLineHeight = 32
            titleParagraph.maximumLineHeight = 32
            let title = NSAttributedString(string: trip.title, attributes: [
                .font: uiFont(size: 30), .foregroundColor: green,
                .kern: -0.8, .paragraphStyle: titleParagraph,
            ])
            title.draw(with: CGRect(x: 14, y: 180, width: 236, height: 96), options: [.usesLineFragmentOrigin], context: nil)

            let paragraph = NSMutableParagraphStyle()
            paragraph.minimumLineHeight = 15
            paragraph.maximumLineHeight = 15
            let regular = UIFont.systemFont(ofSize: 11)
            let heavy = UIFont.systemFont(ofSize: 11, weight: .heavy)
            // RN centres the font inside an explicit line height when that height is larger.
            let baseline = max(0, (15 - max(regular.lineHeight, heavy.lineHeight)) / 2)
            let rows = NSMutableAttributedString(string: "")
            let values = [("DATE", trip.date), ("FROM", trip.from), ("FOR", trip.nights), ("BOARD", trip.board)]
            for (i, (label, value)) in values.enumerated() {
                rows.append(NSAttributedString(string: label + " ", attributes: [
                    .font: heavy, .foregroundColor: green, .paragraphStyle: paragraph, .baselineOffset: baseline,
                ]))
                rows.append(NSAttributedString(string: value + (i < 3 ? "\n" : ""), attributes: [
                    .font: regular, .foregroundColor: green, .paragraphStyle: paragraph, .baselineOffset: baseline,
                ]))
            }
            rows.draw(with: CGRect(x: 14, y: 286, width: 180, height: 60), options: [.usesLineFragmentOrigin], context: nil)

            let price = NSAttributedString(string: "from $640", attributes: [.font: uiFont(size: 13), .foregroundColor: green])
            let priceSize = price.size()
            // RN's single-line text box puts this face's baseline 1 pt below NSString's box.
            price.draw(at: CGPoint(x: 250 - priceSize.width, y: 347 - ceil(priceSize.height)))
        }
    }
}

#Preview {
    EscapePosterCards(autoPlay: true)
}
