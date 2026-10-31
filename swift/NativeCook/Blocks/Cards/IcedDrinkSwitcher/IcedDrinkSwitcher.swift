import CoreText
import SwiftUI
import UIKit

/// The React Native reference's three-stop carousel, using its exact bundled artwork and fonts.
/// Loop 7.5 s: each drink holds for 1.8 s, then switches with ease-in-out cubic over 0.7 s.
/// Glasses bob three times and ingredients drift twice per loop. A 300 pt swipe selects one
/// drink; release snaps over 0.35 s, holds for 2 s, then resumes the loop from that drink.
struct IcedDrinkSwitcher: View {
    static let loop: Double = 7.5

    var brand = "chill."
    var action = "Add to order"
    var autoPlay = false

    private typealias M = IcedDrinkSwitcherMotion
    private typealias A = IcedDrinkSwitcherAssets

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
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)

    var body: some View {
        GeometryReader { outer in
            let safeTop = outer.safeAreaInsets.top
            GeometryReader { geometry in
                let size = geometry.size
                TimelineView(.animation(paused: !visible || scene != .active || reduced)) { timeline in
                    let frame = frame(at: timeline.date)
                    screen(size: size, safeTop: safeTop, frame: frame)
                }
            }
            .ignoresSafeArea()
        }
        .background(M.color(0xDDEFD1).ignoresSafeArea())
        .preferredColorScheme(.light)
        .onAppear {
            A.prepare()
            accumulated = 0
            runningSince = .now
            running = scene == .active && !reduced
            visible = true
            origin = 0
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
            touching = false
            released = nil
            origin = elapsed(at: .now)
            if value { pauseClock() }
            else if visible && scene == .active { resumeClock() }
        }
        .onChange(of: panActive) { _, active in
            guard !active else { return }
            // GestureState also resets when a system gesture cancels the pan.
            Task { @MainActor in
                await Task.yield()
                if touching && !panActive { finishPan() }
            }
        }
    }

    private func screen(size: CGSize, safeTop: CGFloat, frame: M.Frame) -> some View {
        ZStack(alignment: .topLeading) {
            M.mixedColor(frame.index, deep: false)

            ZStack(alignment: .topLeading) {
                heading(width: size.width, safeTop: safeTop)
                words(size: size, index: frame.index)
                Canvas { context, _ in
                    let centre = CGPoint(x: size.width / 2, y: size.height * 0.4)
                    A.drawBits(context, front: false, frame: frame, centre: centre)
                    A.drawGlasses(context, frame: frame, centre: centre)
                    A.drawBits(context, front: true, frame: frame, centre: centre)
                }
                .allowsHitTesting(false)
                .accessibilityLabel("Drinks. Swipe to change.")
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .contentShape(Rectangle())
            .gesture(pan, including: reduced ? .none : .all)

            orderCard(size: size, index: frame.index)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
    }

    private func heading(width: CGFloat, safeTop: CGFloat) -> some View {
        HStack {
            Text(brand)
                .font(A.font(black: true, size: 22))
                .tracking(-0.5)
                .foregroundStyle(M.color(0x1C1410))
            Spacer(minLength: 0)
            Text("2")
                .font(A.font(black: true, size: 13))
                .foregroundStyle(.white)
                .frame(width: 34, height: 34)
                .background(M.color(0x1C1410), in: Circle())
        }
        .frame(width: width - 48, height: 34)
        .offset(x: 24, y: safeTop + 8)
        .allowsHitTesting(false)
    }

    private func words(size: CGSize, index: Double) -> some View {
        ZStack {
            ForEach(M.drinks.indices, id: \.self) { j in
                let r = M.offset(j, from: index)
                Text(M.drinks[j].word)
                    .font(A.font(black: true, size: 64))
                    .tracking(-2)
                    .foregroundStyle(M.color(M.drinks[j].deep))
                    .fixedSize()
                    .offset(x: r * 120)
                    .opacity(1 - M.smooth(abs(r) / 0.6))
            }
        }
        .frame(width: size.width, height: 80)
        .offset(y: size.height * 0.27 - 40)
        .allowsHitTesting(false)
    }

    private func orderCard(size: CGSize, index: Double) -> some View {
        let top = size.height * 0.64
        return VStack(alignment: .leading, spacing: 0) {
            ZStack(alignment: .topLeading) {
                ForEach(M.drinks.indices, id: \.self) { j in
                    let r = M.offset(j, from: index)
                    info(M.drinks[j])
                        .offset(y: abs(r) * 14)
                        .opacity(1 - M.smooth(abs(r) / 0.45))
                        .accessibilityHidden(abs(r) > 0.225)
                }
            }
            .frame(height: 76, alignment: .topLeading)

            HStack(spacing: 8) {
                ForEach(["S", "M", "L"], id: \.self) { label in
                    let selected = label == "M"
                    Text(label)
                        .font(A.font(black: false, size: 15))
                        .foregroundStyle(selected ? .white : M.color(0x1C1410))
                        .frame(width: 44, height: 44)
                        .background(selected ? M.color(0x1C1410) : .clear, in: Circle())
                        .overlay(Circle().strokeBorder(M.color(selected ? 0x1C1410 : 0xE4DDD7), lineWidth: 1.5))
                }
                Text("16 oz · less ice")
                    .font(.system(size: 14))
                    .foregroundStyle(M.color(0x7A6F68))
                    .padding(.leading, 6)
            }
            .padding(.top, 8)

            Text(action)
                .font(A.font(black: false, size: 17))
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .frame(height: 56)
                .background(M.mixedColor(index, deep: true), in: Capsule())
                .padding(.top, 18)
                .accessibilityAddTraits(.isButton)
        }
        .padding(.horizontal, 24)
        .padding(.top, 24)
        .frame(width: size.width, height: size.height - top, alignment: .topLeading)
        .background(.white, in: UnevenRoundedRectangle(topLeadingRadius: 32, topTrailingRadius: 32, style: .circular))
        .offset(y: top)
    }

    private func info(_ drink: M.Drink) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .firstTextBaseline, spacing: 0) {
                Text(drink.name)
                    .font(A.font(black: false, size: 24))
                    .tracking(-0.4)
                    .foregroundStyle(M.color(0x1C1410))
                    .fixedSize()
                Spacer(minLength: 0)
                Text(drink.price)
                    .font(A.font(black: true, size: 20))
                    .foregroundStyle(M.color(drink.deep))
                    .fixedSize()
            }
            Text(drink.line)
                .font(.system(size: 15))
                .foregroundStyle(M.color(0x7A6F68))
                .frame(height: 20, alignment: .center)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private func elapsed(at date: Date) -> Double {
        accumulated + (running ? max(0, date.timeIntervalSince(runningSince)) : 0)
    }

    private func frame(at date: Date) -> M.Frame {
        guard !reduced else { return M.Frame(index: 0, clock: 0) }
        let now = elapsed(at: date)
        var clock = M.mod(now - origin, Self.loop)
        if touching { return M.Frame(index: manualIndex, clock: clock) }
        if let released {
            let since = max(0, now - released)
            if since < M.snap {
                let i = snapFrom + (snapTo - snapFrom) * M.ease(since / M.snap)
                return M.Frame(index: i, clock: clock)
            }
            if since <= M.snap + M.hold {
                return M.Frame(index: snapTo, clock: clock)
            }
            // RN resets the loop's origin after the snap and hold, preserving the chosen stop.
            clock = M.mod(M.mod(snapTo, 3) * M.stop + since - M.snap - M.hold, Self.loop)
        }
        return M.Frame(index: autoPlay ? M.index(at: clock) : 0, clock: clock)
    }

    private var pan: some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($panActive) { _, active, _ in active = true }
            .onChanged { value in
                guard !reduced else { return }
                if !touching {
                    let now = Date()
                    let current = frame(at: now)
                    origin = elapsed(at: now) - current.clock
                    grabbed = current.index
                    manualIndex = current.index
                    lastStep = floor(current.index + 0.5)
                    touching = true
                    panActivated = false
                    released = nil
                }
                // Pan begins on touch down; its 4 pt activation threshold matches Gesture.Pan.
                if !panActivated {
                    guard hypot(value.translation.width, value.translation.height) >= 4 else { return }
                    panActivated = true
                }
                let index = grabbed - value.translation.width / 300
                manualIndex = index
                let step = floor(index + 0.5)
                if step != lastStep {
                    lastStep = step
                    haptic.impactOccurred()
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

private enum IcedDrinkSwitcherMotion {
    static let stop = 2.5
    static let snap = 0.35
    static let hold = 2.0

    struct Frame {
        var index: Double
        var clock: Double
    }

    struct Drink {
        var word: String
        var name: String
        var line: String
        var price: String
        var tint: UInt32
        var deep: UInt32
    }

    static let drinks = [
        Drink(word: "MATCHA", name: "Iced Matcha Latte", line: "Stone-ground matcha, oat milk, a little honey.", price: "$5.40", tint: 0xDDEFD1, deep: 0x2F6B2A),
        Drink(word: "COFFEE", name: "Iced Cold Brew", line: "Steeped for 18 hours, finished with whole milk.", price: "$4.80", tint: 0xF1E3D2, deep: 0x6A3B1D),
        Drink(word: "COCOA", name: "Iced Cocoa Cream", line: "Dark cocoa, cold milk and a cloud of cream.", price: "$5.10", tint: 0xECD5C8, deep: 0x5B271A),
    ]

    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func mod(_ x: Double, _ period: Double) -> Double {
        ((x.truncatingRemainder(dividingBy: period)) + period).truncatingRemainder(dividingBy: period)
    }
    static func ease(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }
    static func smooth(_ x: Double) -> Double {
        let u = clamp(x)
        return u * u * (3 - 2 * u)
    }
    static func index(at clock: Double) -> Double {
        let k = floor(clock / stop)
        return k + ease(clamp((clock - k * stop - 1.8) / 0.7))
    }
    static func offset(_ drink: Int, from index: Double) -> Double {
        var r = Double(drink) - mod(index, 3)
        if r > 1.5 { r -= 3 }
        if r <= -1.5 { r += 3 }
        return r
    }
    static func color(_ hex: UInt32) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, opacity: 1)
    }
    static func mixedColor(_ index: Double, deep: Bool) -> Color {
        let base = floor(index)
        let a = drinks[Int(mod(base, 3))]
        let b = drinks[Int(mod(base + 1, 3))]
        let from = deep ? a.deep : a.tint
        let to = deep ? b.deep : b.tint
        let u = index - base
        func channel(_ shift: UInt32) -> Double {
            let start = Double((from >> shift) & 255)
            let end = Double((to >> shift) & 255)
            return floor(start * (1 - u) + end * u + 0.5) / 255
        }
        return Color(.sRGB, red: channel(16), green: channel(8), blue: channel(0), opacity: 1)
    }
}

private enum IcedDrinkSwitcherAssets {
    typealias M = IcedDrinkSwitcherMotion

    private static let registered: Void = {
        for name in ["ArchivoExpanded-Black", "Archivo-SemiBold"] {
            if let url = Bundle.main.url(forResource: "IcedDrinkSwitcher-\(name)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }()

    private static let glasses = ["drink-matcha", "drink-coffee", "drink-cocoa"].map(image)
    private static let sprites = ["leaf", "bean", "cocoa"].map(image)

    struct Bit {
        var dx: Double
        var dy: Double
        var size: Double
        var depth: Double
        var turn: Double
        var phase: Double
    }

    private static let bits: [Bit] = (0..<8).map { i in
        func hash(_ n: Double) -> Double {
            let x = sin(Double(i + 1) * 31.17 + n * 11.73) * 43758.5453
            return x - floor(x)
        }
        let angle = Double(i) / 8 * .pi * 2 + (hash(1) - 0.5) * 0.6
        let radius = 120 + 60 * hash(2)
        return Bit(dx: cos(angle) * radius, dy: sin(angle) * radius * 1.15 - 10,
                   size: 44 + 46 * hash(3), depth: hash(4), turn: hash(5) * 360,
                   phase: hash(6) * .pi * 2)
    }

    static func prepare() {
        _ = registered
        _ = glasses
        _ = sprites
        _ = bits
    }

    static func font(black: Bool, size: CGFloat) -> Font {
        _ = registered
        return .custom(black ? "ArchivoExpanded-Black" : "Archivo-SemiBold", fixedSize: size)
    }

    private static func image(_ name: String) -> Image? {
        guard let source = UIImage(named: "IcedDrinkSwitcher-\(name).png") else { return nil }
        return Image(uiImage: source.preparingForDisplay() ?? source)
    }

    static func drawBits(_ context: GraphicsContext, front: Bool, frame: M.Frame, centre: CGPoint) {
        for j in 0..<3 {
            let r = M.offset(j, from: frame.index)
            let fade = 1 - 1.5 * abs(r)
            guard fade > 0, let image = sprites[j] else { continue }
            for bit in bits where (bit.depth > 0.5) == front {
                let spread = 1 + 1.2 * abs(r)
                let drift = sin(frame.clock / IcedDrinkSwitcher.loop * .pi * 4 + bit.phase)
                let x = centre.x + bit.dx * spread + r * 200
                let y = centre.y + bit.dy * spread + 8 * drift
                let size = bit.size * (front ? 1.15 : 0.8)
                var c = context
                c.opacity = fade * (front ? 1 : 0.85)
                c.translateBy(x: x, y: y)
                c.rotate(by: .degrees(bit.turn + 10 * drift))
                c.draw(image, in: CGRect(x: -size / 2, y: -size / 2, width: size, height: size))
            }
        }
    }

    static func drawGlasses(_ context: GraphicsContext, frame: M.Frame, centre: CGPoint) {
        let bob = 6 * sin(frame.clock / IcedDrinkSwitcher.loop * .pi * 6)
        for j in 0..<3 {
            let r = M.offset(j, from: frame.index)
            let fade = 1 - abs(r)
            guard fade > 0, let image = glasses[j] else { continue }
            let scale = 1 - 0.25 * abs(r)
            var c = context
            c.opacity = fade
            c.translateBy(x: centre.x + r * 360, y: centre.y + bob)
            c.rotate(by: .degrees(-14 * r))
            c.scaleBy(x: scale, y: scale)
            c.draw(image, in: CGRect(x: -170, y: -170, width: 340, height: 340))
        }
    }
}

#Preview {
    IcedDrinkSwitcher(autoPlay: true)
}
