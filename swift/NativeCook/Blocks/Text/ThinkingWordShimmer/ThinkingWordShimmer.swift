import CoreText
import SwiftUI
import UIKit

// An assistant at work: the request on top, and in a black card one status word at a time
// ("Thinking...", "Processing...", "Analyzing...", "Computing...", "Almost..."). Each word rises
// in sharpening from a blur, a bright band sweeps through its letters while its three dots pulse
// one after the other, then it lifts away blurring as the next word rises in under it.
//
// Every word gets a 2 s slot; times below are from the start of the slot, when the word has
// just landed (ThinkingWordShimmerMotion):
//   0.00-0.15   at rest, dots pulsing (each dot 1 s around, 0.9 rad behind the one before)
//   0.15-1.15   a white band 0.6 of the word wide crosses it, left to right (ease-in-out);
//               outside the band the word is at 50% white
//   1.45-1.80   the word lifts 14 pt, blurs to 6 pt and fades (ease-in)
//   1.60-2.00   the next word rises 14 pt into place, sharpening and fading in (ease-out)
// autoPlay loop: the five words, 10 s, back on "Thinking..." exactly where it started. The
// ambient glows drift on the same 10 s period.

enum ThinkingWordShimmerMotion {
    static let slot = 2.0
    static let sweepAt = 0.15
    static let sweepFor = 1.0
    /// From the start of a slot to the start of the change to the next word.
    static let switchAt = 1.45
    static let exitFor = 0.35
    static let enterDelay = 0.15
    static let enterFor = 0.4
    /// From the start of a change to the moment the new word has landed (its slot's start).
    static var landed: Double { enterDelay + enterFor }
    static let travel = 14.0
    static let blur = 6.0
    /// The word's brightness outside the band, which is white; the band is 0.6 of the word wide.
    static let base = 0.5
    static let dotPeriod = 1.0
    static let dotLag = 0.9
    static let userHold = 2.0

    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func easeInOut(_ t: Double) -> Double { t * t * (3 - 2 * t) }
    static func easeIn(_ t: Double) -> Double { t * t }
    static func easeOut(_ t: Double) -> Double { 1 - (1 - t) * (1 - t) }

    /// Where the bright band's centre is, in units of the word's width, `s` seconds into its slot.
    static func band(_ s: Double) -> Double {
        let t = s.truncatingRemainder(dividingBy: slot)
        return -0.35 + 1.7 * easeInOut(clamp((t - sweepAt) / sweepFor))
    }

    /// A dot's opacity, `s` seconds into the word's slot.
    static func dot(_ k: Int, _ s: Double) -> Double {
        0.3 + 0.7 * (0.5 + 0.5 * sin(2 * .pi * s / dotPeriod - Double(k) * dotLag))
    }
}

enum ThinkingWordShimmerPalette {
    static let background = Color(hex: 0x04080A)
    static let teal = Color(hex: 0x12505F)
    static let deepTeal = Color(hex: 0x0B3A47)
    static let warm = Color(hex: 0x8C7A4E)
    static let card = Color.black
    static let text = Color(hex: 0xE6ECEE)
    static let label = Color(hex: 0x7D8A90)
    static let step = Color(hex: 0x263034)
}

enum ThinkingWordShimmerType {
    static let registered: Void = {
        if let url = Bundle.main.url(forResource: "ThinkingWordShimmer-Inter-SemiBold", withExtension: "ttf") {
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()

    static func font(_ size: CGFloat) -> Font {
        _ = registered
        return .custom("Inter-SemiBold", fixedSize: size)
    }
}

struct ThinkingWordShimmer: View {
    var words = ["Thinking", "Processing", "Analyzing", "Computing", "Almost"]
    var label = "Assistant"
    var request = "Summarise this week's customer calls and flag anything urgent."
    var hint = "Tap to skip ahead"
    /// Goes through the words by itself, one every 2 s.
    var autoPlay = false

    private typealias M = ThinkingWordShimmerMotion
    private typealias P = ThinkingWordShimmerPalette

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var current = 0
    @State private var previous: Int? = nil
    /// When the change to `current` began; `current` landed `M.landed` later.
    @State private var changedAt = Date.distantPast
    /// Where the ambient glows' period starts.
    @State private var origin = Date()
    @State private var touches = 0
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)

    /// The loop's period: every word once.
    var loop: Double { M.slot * Double(words.count) }

    var body: some View {
        GeometryReader { geo in
            let safe = geo.safeAreaInsets
            let full = CGSize(width: geo.size.width + safe.leading + safe.trailing, height: geo.size.height + safe.top + safe.bottom)
            let cardW = full.width - 40
            TimelineView(.animation(paused: reduceMotion)) { context in
                let now = context.date
                ZStack {
                    backdrop(at: now, size: full)

                    VStack(alignment: .leading, spacing: 0) {
                        Text(label.uppercased())
                            .font(.system(size: 13, weight: .semibold))
                            .tracking(1.6)
                            .foregroundStyle(P.label)
                        Text(request)
                            .font(ThinkingWordShimmerType.font(22))
                            .foregroundStyle(P.text.opacity(0.92))
                            .lineSpacing(4)
                            .fixedSize(horizontal: false, vertical: true)
                            .padding(.top, 10)

                        Spacer(minLength: 0)

                        card(at: now, width: cardW)
                            .frame(maxWidth: .infinity)

                        steps
                            .frame(maxWidth: .infinity)
                            .padding(.top, 24)

                        Spacer(minLength: 0)

                        Text(hint)
                            .font(.system(size: 13, weight: .medium))
                            .foregroundStyle(P.label.opacity(0.8))
                            .frame(maxWidth: .infinity)
                            .padding(.bottom, safe.bottom + 28)
                    }
                    .padding(.top, safe.top + 20)
                    .padding(.horizontal, 20)
                    .frame(width: full.width, height: full.height)
                }
            }
            .frame(width: full.width, height: full.height)
            .contentShape(Rectangle())
            .onTapGesture { tapped() }
            .ignoresSafeArea()
        }
        .preferredColorScheme(.dark)
        .onAppear {
            let now = Date()
            origin = now
            changedAt = now.addingTimeInterval(-M.landed)
            haptic.prepare()
        }
        .task(id: LoopKey(on: autoPlay && !reduceMotion, touches: touches)) { await run() }
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }

    /// Near-black with a teal glow up high, a deeper teal low on the left and a warm one low in the
    /// middle, each drifting a little once per loop.
    private func backdrop(at now: Date, size: CGSize) -> some View {
        let phase = reduceMotion ? 0 : 2 * Double.pi * now.timeIntervalSince(origin).truncatingRemainder(dividingBy: loop) / loop
        let w = size.width
        let h = size.height
        return ZStack {
            P.background
            RadialGradient(colors: [P.teal.opacity(0.75), .clear], center: UnitPoint(x: 0.7 + 0.05 * sin(phase), y: 0.3 + 0.03 * cos(phase)), startRadius: 0, endRadius: w * 0.75)
            RadialGradient(colors: [P.deepTeal.opacity(0.85), .clear], center: UnitPoint(x: 0.2 + 0.04 * cos(phase), y: 0.7), startRadius: 0, endRadius: w * 0.65)
            RadialGradient(colors: [P.warm.opacity(0.6), .clear], center: UnitPoint(x: 0.56 + 0.06 * sin(phase + 1.3), y: 0.68 + 0.02 * sin(phase)), startRadius: 0, endRadius: w * 0.6)
        }
        .frame(width: w, height: h)
    }

    private func card(at now: Date, width: CGFloat) -> some View {
        let sinceChange = now.timeIntervalSince(changedAt)
        // Seconds into the current word's slot (negative while it is still rising in).
        let s = sinceChange - M.landed
        return ZStack {
            RoundedRectangle(cornerRadius: 28, style: .continuous)
                .fill(P.card)
                .overlay(RoundedRectangle(cornerRadius: 28, style: .continuous).strokeBorder(Color.white.opacity(0.07), lineWidth: 1))
                .shadow(color: .black.opacity(0.6), radius: 30, y: 18)

            if reduceMotion {
                let p = M.clamp(sinceChange / 0.25)
                if let prev = previous, p < 1 {
                    status(prev, s: 0).opacity(1 - p)
                }
                status(current, s: 0).opacity(p)
            } else {
                if let prev = previous, sinceChange < M.exitFor {
                    let q = M.easeIn(M.clamp(sinceChange / M.exitFor))
                    status(prev, s: M.switchAt + sinceChange)
                        .blur(radius: M.blur * q)
                        .opacity(1 - q)
                        .offset(y: -M.travel * q)
                }
                let q = M.easeOut(M.clamp((sinceChange - M.enterDelay) / M.enterFor))
                status(current, s: max(0, s))
                    .blur(radius: M.blur * (1 - q))
                    .opacity(q)
                    .offset(y: M.travel * (1 - q))
            }
        }
        .frame(width: width, height: round(width * 0.62))
        .accessibilityElement()
        .accessibilityLabel(words[current] + "…")
        .accessibilityHint("Tap to skip ahead")
    }

    /// A status word and its three dots: the word is lit by the moving band, the dots pulse.
    private func status(_ i: Int, s: Double) -> some View {
        let c = reduceMotion ? 2.0 : M.band(s)
        let base = Color.white.opacity(reduceMotion ? 0.9 : M.base)
        let gradient = LinearGradient(
            stops: [
                .init(color: base, location: 0.0),
                .init(color: base, location: 0.2),
                .init(color: .white, location: 0.5),
                .init(color: base, location: 0.8),
                .init(color: base, location: 1.0),
            ],
            startPoint: UnitPoint(x: c - 0.5, y: 0.5),
            endPoint: UnitPoint(x: c + 0.5, y: 0.5)
        )
        return HStack(spacing: 0) {
            Text(words[i]).foregroundStyle(gradient)
            ForEach(0..<3, id: \.self) { k in
                Text(".").foregroundStyle(Color.white.opacity(reduceMotion ? 0.9 : M.dot(k, s)))
            }
        }
        .font(ThinkingWordShimmerType.font(24))
        .lineLimit(1)
        .fixedSize()
    }

    private var steps: some View {
        HStack(spacing: 6) {
            ForEach(words.indices, id: \.self) { i in
                Capsule()
                    .fill(i == current ? P.text : P.step)
                    .frame(width: i == current ? 28 : 14, height: 3)
            }
        }
        .animation(reduceMotion ? nil : .spring(response: 0.4, dampingFraction: 0.85), value: current)
    }

    // MARK: Actions

    private func change(to i: Int, at date: Date = .now) {
        previous = current
        current = i
        changedAt = date
    }

    private func tapped() {
        touches += 1
        haptic.impactOccurred()
        change(to: (current + 1) % words.count)
    }

    private func run() async {
        guard autoPlay, !reduceMotion else { return }
        let clock = ContinuousClock()
        var landed: ContinuousClock.Instant
        if touches > 0 {
            try? await Task.sleep(for: .seconds(M.userHold))
            guard !Task.isCancelled else { return }
            // Back to the first word, unless it is already up and not yet leaving.
            let s = Date().timeIntervalSince(changedAt) - M.landed
            if current == 0, s >= 0, s < M.switchAt {
                landed = clock.now - .seconds(s)
            } else {
                change(to: 0)
                landed = clock.now + .seconds(M.landed)
            }
        } else {
            landed = clock.now - .seconds(max(0, Date().timeIntervalSince(changedAt) - M.landed))
        }
        if touches > 0 {
            // The glows' period starts over with the loop.
            let d = landed - clock.now
            origin = Date().addingTimeInterval(Double(d.components.seconds) + Double(d.components.attoseconds) / 1e18)
        }
        // `landed` is when the word on screen landed; each slot changes word at 1.45 s.
        while !Task.isCancelled {
            try? await clock.sleep(until: landed + .seconds(M.switchAt))
            guard !Task.isCancelled else { return }
            change(to: (current + 1) % words.count)
            landed += .seconds(M.slot)
        }
    }
}

#Preview {
    ThinkingWordShimmer(autoPlay: true)
}
