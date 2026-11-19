import SwiftUI
import UIKit

/// A weather screen whose whole background is a living mesh gradient. The hour the hourly row is scrolled to is
/// the one number the sky, the temperature, the condition and the tiles are drawn from, so scrubbing the row
/// morphs the mesh through the day: dawn, a sunny noon, an afternoon of rain on the glass, dusk, night.
///
/// The film (one 16 s loop) only moves the row. Drag the row (it snaps per hour, with a selection tick) or tap an
/// hour to take over; 2 s after the last touch the row scrolls back to Now in 1.1 s and the film starts again, while
/// the drift never resets. Under Reduce Motion the sky holds still at the hour shown and the film does not run; the
/// row still scrubs it.
struct MeshGradientWeather: View {
    var autoPlay: Bool = true

    static let loop: Double = MeshGradientWeatherMotion.loop

    private typealias M = MeshGradientWeatherMotion
    private enum Mode { case auto, manual, ret }
    private enum HourAnim {
        case fixed(Double)
        case timing(from: Double, to: Double, at: Date, duration: Double, settle: Bool)
    }

    private static let item: CGFloat = 62
    private static let returnS = 1.1
    private static let shadow = Color(red: 16 / 255, green: 20 / 255, blue: 44 / 255).opacity(0.22)
    private static let rangeColors = [0x8FD3E8, 0xB5E2B0, 0xF3D67F, 0xF2A862].map { hex in
        Color(red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255)
    }
    private static let days: [(day: String, symbol: String, lo: Double, hi: Double)] = [
        ("Today", "cloud.sun.rain.fill", 11, 20),
        ("Fri", "sun.max.fill", 12, 22),
        ("Sat", "sun.max.fill", 13, 23),
        ("Sun", "cloud.fill", 12, 19),
        ("Mon", "cloud.rain.fill", 10, 17),
    ]
    private static let weekLo = 10.0, weekHi = 23.0
    private static let dayCol: CGFloat = 52, numCol: CGFloat = 34

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var origin = Date()
    @State private var loopStart = Date()
    @State private var mode: Mode = .auto
    @State private var hourAnim: HourAnim = .fixed(0)
    @State private var retFrom = 0.0
    @State private var retAt = Date.distantFuture
    @State private var dragFrom: Double?
    @State private var idleToken = 0
    @State private var awake = false

    private var film: Bool { autoPlay && !reduceMotion }

    var body: some View {
        GeometryReader { outer in
            let ins = outer.safeAreaInsets
            GeometryReader { full in
                TimelineView(.animation(minimumInterval: nil, paused: reduceMotion && !awake)) { tl in
                    frameView(tl.date, size: full.size, ins: ins)
                }
            }
            .ignoresSafeArea()
        }
        .background(Color(red: 0x7C / 255, green: 0x86 / 255, blue: 0xC8 / 255).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear {
            let now = Date()
            origin = now
            loopStart = now
            mode = film ? .auto : .manual
        }
    }

    // MARK: - Clock

    private func state(_ now: Date) -> (mode: Mode, hour: Double, gate: Double) {
        if mode == .ret {
            let p = min(1, now.timeIntervalSince(retAt) / Self.returnS)
            if p < 1 { return (.ret, retFrom * (1 - M.easeInOut(p)), 0) }
        }
        if mode == .auto || mode == .ret {
            let start = mode == .ret ? retAt.addingTimeInterval(Self.returnS) : loopStart
            let t = now.timeIntervalSince(start).truncatingRemainder(dividingBy: M.loop)
            return (.auto, M.filmHour(t), M.filmRainGate(t))
        }
        switch hourAnim {
        case .fixed(let h): return (.manual, h, 1)
        case .timing(let from, let to, let at, let d, let settle):
            let x = M.clamp01(now.timeIntervalSince(at) / d)
            let f = settle ? 1 - pow(1 - x, 3) : M.easeInOut(x)
            return (.manual, from + (to - from) * f, 1)
        }
    }

    // MARK: - Screen

    @ViewBuilder
    private func frameView(_ now: Date, size: CGSize, ins: EdgeInsets) -> some View {
        let width = size.width, height = size.height
        let compact = height < 740
        let contentW = min(width, 480)
        let st = state(now)
        let h = st.hour
        let amb = reduceMotion ? 0 : now.timeIntervalSince(origin).truncatingRemainder(dividingBy: M.loop)
        let rain = M.rain(at: h) * st.gate
        let u: [Float] = ([width, height, rain, amb / M.loop, Double(UIScreen.main.scale)]
            + M.offsets(h, amb, rain) + M.sky(at: h)).map(Float.init)

        ZStack(alignment: .top) {
            Rectangle()
                .fill(ShaderLibrary.meshGradientWeatherSky(.floatArray(u)))
                .frame(width: width, height: height)
                .allowsHitTesting(false)
                .accessibilityHidden(true)

            ScrollView(.vertical, showsIndicators: false) {
                page(h, contentW: contentW, compact: compact)
                    .padding(.horizontal, 16)
                    .padding(.top, ins.top + (compact ? 12 : 24))
                    .padding(.bottom, ins.bottom + 16)
                    .frame(width: contentW)
            }
            .frame(width: width, height: height)
        }
        .frame(width: width, height: height)
        .onChange(of: st.mode == .manual ? Int(h.rounded()) : -1) { old, new in
            if old >= 0 && new >= 0 { UISelectionFeedbackGenerator().selectionChanged() }
        }
    }

    private func page(_ h: Double, contentW: CGFloat, compact: Bool) -> some View {
        let cardW = contentW - 32
        let tempSize: CGFloat = compact ? 84 : 100
        return VStack(alignment: .leading, spacing: 0) {
            // header
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 5) {
                    Image(systemName: "location.fill").font(.system(size: 13)).foregroundStyle(.white.opacity(0.9))
                    Text("My Location").font(.system(size: 13, weight: .semibold)).foregroundStyle(.white.opacity(0.88))
                }
                .textShadow()
                Text("Porto").font(.system(size: compact ? 28 : 34, weight: .bold)).foregroundStyle(.white)
                    .textShadow().padding(.top, 2)
                Text("\(Int(M.sample(M.temps, h).rounded()))°")
                    .font(.system(size: tempSize, weight: .thin).monospacedDigit())
                    .foregroundStyle(.white)
                    .frame(height: tempSize * 1.08)
                    .padding(.leading, -tempSize * 0.05)
                    .textShadow()
                ZStack(alignment: .topLeading) {
                    ForEach(Array(M.conditions.enumerated()), id: \.offset) { i, c in
                        let w = M.condWeight(i, h)
                        Text(c).font(.system(size: 21, weight: .semibold)).foregroundStyle(.white)
                            .textShadow()
                            .opacity(w)
                            .offset(y: (1 - w) * 6)
                    }
                }
                .frame(height: compact ? 24 : 26, alignment: .topLeading)
                Text("H:20°  L:11°").font(.system(size: 17, weight: .medium).monospacedDigit())
                    .foregroundStyle(.white.opacity(0.88)).textShadow().padding(.top, 2)
            }
            .padding(.horizontal, 8)

            // the hourly card
            VStack(spacing: 0) {
                cardHead("cloud.rain.fill", "Rain from 2 PM, clearing by 6 PM.")
                rule
                hourRow(h, cardW: cardW)
            }
            .glass(radius: 26)
            .padding(.top, compact ? 18 : 22)

            HStack(spacing: 12) {
                tile("umbrella.fill", "Chance of rain", "\(Int(M.sample(M.rainPct, h).rounded()))%", "Bring a jacket after lunch.", innerW: (cardW - 12) / 2 - 28)
                tile("wind", "Wind", "\(Int(M.sample(M.wind, h).rounded())) km/h", "From the northwest.", innerW: (cardW - 12) / 2 - 28)
            }
            .fixedSize(horizontal: false, vertical: true)
            .padding(.top, 12)

            forecast(h, width: cardW).padding(.top, 12)
        }
        .frame(width: cardW, alignment: .leading)
    }

    private func cardHead(_ icon: String, _ text: String) -> some View {
        HStack(spacing: 7) {
            Image(systemName: icon).font(.system(size: 14)).foregroundStyle(.white.opacity(0.75))
            Text(text).font(.system(size: 14, weight: .medium)).foregroundStyle(.white.opacity(0.86))
            Spacer(minLength: 0)
        }
        .frame(height: 17)
        .padding(.horizontal, 16)
        .padding(.top, 13)
        .padding(.bottom, 11)
    }

    /// The note on one line when it fits; otherwise broken greedily, as React Native does (SwiftUI's own breaking
    /// pushes a lone last word down a line).
    private func noteText(_ note: String, width: CGFloat) -> some View {
        ViewThatFits(in: .horizontal) {
            Text(note).fixedSize()
            Text(Self.greedy(note, width: width, font: .systemFont(ofSize: 13, weight: .medium)))
        }
        .accessibilityLabel(note)
    }

    private static func greedy(_ text: String, width: CGFloat, font: UIFont) -> String {
        var lines: [String] = [], line = ""
        for word in text.split(separator: " ") {
            let next = line.isEmpty ? String(word) : line + " " + word
            if (next as NSString).size(withAttributes: [.font: font]).width <= width || line.isEmpty { line = next } else { lines.append(line); line = String(word) }
        }
        lines.append(line)
        return lines.joined(separator: "\n")
    }

    private var rule: some View {
        Rectangle().fill(.white.opacity(0.3)).frame(height: 1 / UIScreen.main.scale).padding(.horizontal, 16)
    }

    private func hourRow(_ h: Double, cardW: CGFloat) -> some View {
        let pad = (cardW - Self.item) / 2
        return ZStack(alignment: .leading) {
            RoundedRectangle(cornerRadius: 28, style: .continuous)
                .fill(.white.opacity(0.16))
                .overlay(RoundedRectangle(cornerRadius: 28, style: .continuous).strokeBorder(.white.opacity(0.4), lineWidth: 1 / UIScreen.main.scale))
                .frame(width: Self.item - 6)
                .padding(.vertical, 8)
                .offset(x: pad + 3)
            HStack(spacing: 0) {
                ForEach(Array(M.hours.enumerated()), id: \.offset) { i, hr in
                    VStack(spacing: 0) {
                        Text(hr.label).font(.system(size: 13, weight: .semibold)).foregroundStyle(.white.opacity(0.9))
                        Image(systemName: hr.symbol)
                            .symbolRenderingMode(.multicolor)
                            .font(.system(size: 22)) // measured against the React Native SymbolView of size 26
                            .frame(width: 28, height: 28)
                            .accessibilityHidden(true)
                            .padding(.vertical, 8)
                        Text("\(Int(hr.temp))°").font(.system(size: 18, weight: .semibold).monospacedDigit()).foregroundStyle(.white)
                    }
                    .frame(width: Self.item)
                    .padding(.vertical, 10)
                    .contentShape(Rectangle())
                    .onTapGesture { tapHour(i) }
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("\(hr.label), \(Int(hr.temp)) degrees")
                    .accessibilityAddTraits(.isButton)
                }
            }
            .offset(x: pad - h * Self.item)
        }
        .frame(width: cardW, height: 108, alignment: .leading)
        .clipped()
        .contentShape(Rectangle())
        .simultaneousGesture(rowDrag)
    }

    private func tile(_ icon: String, _ title: String, _ value: String, _ note: String, innerW: CGFloat) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 6) {
                Image(systemName: icon).font(.system(size: 13)).foregroundStyle(.white.opacity(0.72))
                Text(title).font(.system(size: 13, weight: .semibold)).foregroundStyle(.white.opacity(0.75))
            }
            Text(value).font(.system(size: 28, weight: .semibold).monospacedDigit()).foregroundStyle(.white).padding(.top, 8)
            noteText(note, width: innerW).font(.system(size: 13, weight: .medium)).foregroundStyle(.white.opacity(0.75)).padding(.top, 4)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(14)
        .frame(minWidth: 0, maxWidth: .infinity, minHeight: 112, maxHeight: .infinity, alignment: .topLeading)
        .glass(radius: 22)
    }

    private func forecast(_ h: Double, width: CGFloat) -> some View {
        let track = width - 32 - Self.dayCol - 30 - Self.numCol * 2 - 8 * 4
        func x(_ v: Double) -> CGFloat { (v - Self.weekLo) / (Self.weekHi - Self.weekLo) * track }
        return VStack(spacing: 0) {
            cardHead("calendar", "5-Day Forecast")
            rule
            ForEach(Array(Self.days.enumerated()), id: \.offset) { i, d in
                HStack(spacing: 8) {
                    Text(d.day).font(.system(size: 16, weight: .semibold)).foregroundStyle(.white)
                        .frame(width: Self.dayCol, alignment: .leading)
                    Image(systemName: d.symbol).resizable().symbolRenderingMode(.multicolor).scaledToFit()
                        .frame(width: 27, height: 20) // measured against the React Native SymbolView (size 20, 30 x 22)
                        .frame(width: 30, height: 22)
                        .accessibilityHidden(true)
                    Text("\(Int(d.lo))°").font(.system(size: 16, weight: .semibold).monospacedDigit())
                        .foregroundStyle(.white.opacity(0.6)).frame(width: Self.numCol, alignment: .trailing)
                    ZStack(alignment: .leading) {
                        Capsule().fill(Self.shadow).frame(width: track, height: 5)
                        LinearGradient(colors: Self.rangeColors, startPoint: .leading, endPoint: .trailing)
                            .frame(width: track, height: 5)
                            .mask(alignment: .leading) {
                                Capsule().frame(width: x(d.hi) - x(d.lo)).offset(x: x(d.lo))
                            }
                        if i == 0 {
                            Circle().fill(.white)
                                .overlay(Circle().strokeBorder(Color(red: 16 / 255, green: 20 / 255, blue: 44 / 255).opacity(0.35), lineWidth: 2))
                                .frame(width: 10, height: 10)
                                .offset(x: x(M.sample(M.temps, h)) - 5)
                        }
                    }
                    .frame(width: track, height: 10)
                    Text("\(Int(d.hi))°").font(.system(size: 16, weight: .semibold).monospacedDigit())
                        .foregroundStyle(.white).frame(width: Self.numCol, alignment: .leading)
                }
                .frame(height: 33)
                .overlay(alignment: .top) {
                    if i > 0 { Rectangle().fill(.white.opacity(0.18)).frame(height: 1 / UIScreen.main.scale) }
                }
                .padding(.horizontal, 16)
            }
        }
        .padding(.bottom, 4)
        .glass(radius: 22)
    }

    // MARK: - Touch

    private var rowDrag: some Gesture {
        DragGesture(minimumDistance: 8)
            .onChanged { v in
                let now = Date()
                if dragFrom == nil {
                    guard abs(v.translation.width) > abs(v.translation.height) else { return }
                    let cur = state(now).hour
                    pause()
                    dragFrom = cur
                }
                guard let from = dragFrom else { return }
                hourAnim = .fixed(min(max(from - v.translation.width / Self.item, 0), M.lastHour))
                touched()
            }
            .onEnded { v in
                guard let from = dragFrom else { return }
                dragFrom = nil
                let now = Date()
                let cur = min(max(from - v.translation.width / Self.item, 0), M.lastHour)
                // a fast-decelerating scroll view that snaps per hour
                let projected = cur - v.velocity.width / Self.item * 0.1
                let target = min(max(projected.rounded(), 0), M.lastHour)
                let d = 0.25 + 0.06 * min(abs(target - cur), 4)
                hourAnim = .timing(from: cur, to: target, at: now, duration: d, settle: true)
                touched(settling: d)
            }
    }

    private func tapHour(_ i: Int) {
        let now = Date()
        let cur = state(now).hour
        pause()
        let d = 0.3 + 0.03 * min(abs(Double(i) - cur), 6)
        hourAnim = .timing(from: cur, to: Double(i), at: now, duration: d, settle: false)
        touched(settling: d)
    }

    /// Stops the film (or a running return) where it is.
    private func pause() {
        let now = Date()
        if mode != .manual {
            hourAnim = .fixed(state(now).hour)
            retAt = .distantFuture
            mode = .manual
        }
    }

    /// Restarts the 2 s wait after the last touch.
    private func touched(settling: Double = 0) {
        idleToken += 1
        let token = idleToken
        awake = true
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(2 + settling))
            guard token == idleToken else { return }
            awake = false
            guard film, mode == .manual, dragFrom == nil else { return }
            let now = Date()
            retFrom = state(now).hour
            retAt = now
            mode = .ret
            awake = true
            try? await Task.sleep(for: .seconds(Self.returnS))
            guard token == idleToken, mode == .ret else { return }
            loopStart = retAt.addingTimeInterval(Self.returnS)
            retAt = .distantFuture
            mode = .auto
        }
    }
}

private extension View {
    func textShadow() -> some View {
        shadow(color: Color(red: 16 / 255, green: 20 / 255, blue: 44 / 255).opacity(0.22), radius: 7, x: 0, y: 1)
    }

    /// Liquid Glass on iOS 26 (dark, faintly tinted); a dark blur before it. A hairline rim on both.
    @ViewBuilder
    func glass(radius: CGFloat) -> some View {
        let shape = RoundedRectangle(cornerRadius: radius, style: .continuous)
        let rim = shape.strokeBorder(.white.opacity(0.28), lineWidth: 1 / UIScreen.main.scale).allowsHitTesting(false)
        if #available(iOS 26, *) {
            self
                .glassEffect(.regular.tint(Color(red: 28 / 255, green: 34 / 255, blue: 58 / 255).opacity(0.16)), in: shape)
                .overlay(rim)
                .environment(\.colorScheme, .dark)
        } else {
            self
                .background(shape.fill(Color(red: 28 / 255, green: 34 / 255, blue: 58 / 255).opacity(0.24)))
                .background(.ultraThinMaterial, in: shape)
                .overlay(rim)
                .environment(\.colorScheme, .dark)
        }
    }
}
