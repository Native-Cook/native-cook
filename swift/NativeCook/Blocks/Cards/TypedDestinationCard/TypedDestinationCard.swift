import SwiftUI

struct TypedDestinationCardCity: Equatable {
    var name: String
    /// Address of a portrait photo, about 3 : 4.
    var photo: String
    /// Flat colour shown until the photo arrives.
    var tint: Color

    static let samples = [
        TypedDestinationCardCity(
            name: "Paris",
            photo: "https://images.unsplash.com/photo-1502602898657-3e91760cbb34?auto=format&fit=crop&w=480&h=648&q=80",
            tint: Color(hex: 0x8c818d)
        ),
        TypedDestinationCardCity(
            name: "Tokyo",
            photo: "https://images.unsplash.com/photo-1540959733332-eab4deabeeaf?auto=format&fit=crop&w=480&h=648&q=80",
            tint: Color(hex: 0x4a848e)
        ),
        TypedDestinationCardCity(
            name: "New York",
            photo: "https://images.unsplash.com/photo-1496442226666-8d4d0e62e6e9?auto=format&fit=crop&w=480&h=648&q=80",
            tint: Color(hex: 0x6e6469)
        ),
    ]
}

/// A destination picker for an onboarding screen: a portrait photo crossed by a white search pill
/// in which a city is typed and deleted letter by letter. When a name is complete the photo
/// cross-fades to that city with a slight zoom and the map pin hops. A black pill button for the
/// current location sits underneath. Tapping the search pill moves on to the next city.
struct TypedDestinationCard: View {
    /// The cities, typed one after another; the first is shown at rest.
    var cities = TypedDestinationCardCity.samples
    /// Types its way through the cities by itself, one every 2.6 s.
    var autoPlay = false
    var buttonLabel = "Current location"
    var pillColor = Color.white
    var inkColor = Color(hex: 0x141414)
    var buttonColor = Color(hex: 0x171717)
    var buttonTextColor = Color.white
    /// Called with the index of the city a tap on the pill moves to.
    var onSelect: ((Int) -> Void)? = nil
    var onCurrentLocation: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var sim = Simulation()
    /// Keeps the timeline running for a change under reduced motion.
    @State private var settling = false
    @State private var taps = 0

    /// What is being typed: the text left from `fromCity` is deleted, then `to` is typed.
    private final class Simulation {
        var last: Date? = nil
        var time = 0.0
        var looping = false
        /// When the loop moves on to the next city.
        var nextAt = Metrics.firstAt
        var startedAt = 0.0
        var fromCity = 0
        var fromLength = 0
        var to = 0
        /// The photo under the one fading in.
        var base = 0

        func advance(to date: Date, lengths: [Int], reduced: Bool) {
            guard let last else {
                self.last = date
                return
            }
            let dt = min(date.timeIntervalSince(last), 0.064)
            guard dt > 0 else { return }
            self.last = date
            time += dt
            if looping, time >= nextAt {
                begin(at: nextAt, lengths: lengths, reduced: reduced)
                nextAt += Metrics.cityTime
            }
        }

        /// Starts deleting whatever is shown at `now` and typing the next city.
        func begin(at now: Double, lengths: [Int], reduced: Bool) {
            guard lengths.count > 1 else { return }
            let shown = typedText(now - startedAt, fromCity: fromCity, fromLength: fromLength, to: to, toLength: lengths[to])
            if now - startedAt >= completeTime(fromLength: fromLength, toLength: lengths[to]) { base = to }
            fromCity = shown.city
            fromLength = shown.count
            to = (to + 1) % lengths.count
            // Under reduced motion the name is swapped at once and only the photo fades.
            startedAt = reduced ? now - completeTime(fromLength: fromLength, toLength: lengths[to]) : now
        }

        /// Shows the first city fully typed, with the caret at the start of a blink.
        func rest(lengths: [Int]) {
            fromCity = 0
            fromLength = 0
            to = 0
            base = 0
            startedAt = time - completeTime(fromLength: 0, toLength: lengths.first ?? 0) - Metrics.caretBlink * 8
            nextAt = time + Metrics.firstAt
        }
    }

    private var auto: Bool { autoPlay && !reduceMotion }
    private var lengths: [Int] { cities.map(\.name.count) }

    var body: some View {
        TimelineView(.animation(paused: reduceMotion && !settling)) { timeline in
            let lengths = lengths
            let _ = sim.advance(to: timeline.date, lengths: lengths, reduced: reduceMotion)
            let elapsed = sim.time - sim.startedAt
            let toLength = lengths.isEmpty ? 0 : lengths[sim.to]
            let shown = typedText(elapsed, fromCity: sim.fromCity, fromLength: sim.fromLength, to: sim.to, toLength: toLength)
            // Seconds since the name was completed; negative while it is still being edited.
            let since = elapsed - completeTime(fromLength: sim.fromLength, toLength: toLength)
            let text = cities.isEmpty ? "" : String(cities[shown.city].name.prefix(shown.count))
            let fade = smooth(since / Metrics.fadeTime)
            let zoom = reduceMotion ? 1 : 1 + Metrics.zoom * (1 - easeOut(since / Metrics.zoomTime))
            let hop = reduceMotion ? 0 : pinHop(since)
            let caret = reduceMotion || since < 0 || since.truncatingRemainder(dividingBy: Metrics.caretBlink * 2) < Metrics.caretBlink

            ZStack(alignment: .topLeading) {
                ZStack {
                    ForEach(Array(cities.enumerated()), id: \.offset) { index, city in
                        TypedDestinationCardPhoto(address: city.photo, tint: city.tint)
                            .frame(width: Metrics.photoWidth, height: Metrics.photoHeight)
                            .scaleEffect(index == sim.to ? zoom : 1)
                            .opacity(index == sim.to ? fade : index == sim.base ? 1 : 0)
                            .zIndex(index == sim.to ? 2 : index == sim.base ? 1 : 0)
                    }
                }
                .frame(width: Metrics.photoWidth, height: Metrics.photoHeight)
                .clipShape(RoundedRectangle(cornerRadius: Metrics.photoRadius, style: .circular))
                .offset(x: Metrics.photoLeft)
                .accessibilityHidden(true)

                Button {
                    skip()
                } label: {
                    ZStack(alignment: .topLeading) {
                        Capsule(style: .circular)
                            .fill(pillColor)
                            .shadow(color: .black.opacity(Metrics.pillShadowOpacity), radius: Metrics.pillShadowRadius, y: Metrics.pillShadowY)
                        ZStack {
                            typedDestinationPin.fill(inkColor)
                            Circle().fill(pillColor)
                                .frame(width: Metrics.pinHole * 2, height: Metrics.pinHole * 2)
                                .position(x: Metrics.pinWidth / 2, y: Metrics.pinWidth / 2)
                        }
                        .frame(width: Metrics.pinWidth, height: Metrics.pinHeight)
                        .offset(x: Metrics.pinLeft, y: (Metrics.pillHeight - Metrics.pinHeight) / 2 + hop)

                        HStack(spacing: Metrics.caretGap) {
                            Text(text)
                                .font(.system(size: 27, weight: .regular))
                                .tracking(-0.4)
                                .foregroundStyle(inkColor)
                                .lineLimit(1)
                                .fixedSize()
                            RoundedRectangle(cornerRadius: Metrics.caretWidth / 2, style: .circular)
                                .fill(inkColor)
                                .frame(width: Metrics.caretWidth, height: Metrics.caretHeight)
                                .opacity(caret ? 1 : 0)
                        }
                        .frame(width: Metrics.pillWidth - Metrics.textLeft, height: Metrics.pillHeight, alignment: .leading)
                        .offset(x: Metrics.textLeft)
                    }
                    .frame(width: Metrics.pillWidth, height: Metrics.pillHeight)
                    .contentShape(Capsule(style: .circular))
                }
                .buttonStyle(PressStyle(scale: 0.98))
                .offset(x: Metrics.pillLeft, y: Metrics.pillTop)
                .accessibilityLabel(Text(verbatim: cities.isEmpty ? "" : cities[sim.to].name))
                .accessibilityHint("Shows the next city")

                Button {
                    onCurrentLocation?()
                } label: {
                    HStack(spacing: Metrics.buttonGap) {
                        Canvas { context, _ in
                            context.stroke(typedDestinationTarget, with: .color(buttonTextColor), style: StrokeStyle(lineWidth: Metrics.targetStroke, lineCap: .round))
                            context.fill(Path(ellipseIn: CGRect(x: 10 - Metrics.targetDot, y: 10 - Metrics.targetDot, width: Metrics.targetDot * 2, height: Metrics.targetDot * 2)), with: .color(buttonTextColor))
                        }
                        .frame(width: 20, height: 20)
                        Text(buttonLabel)
                            .font(.system(size: 17, weight: .semibold))
                            .tracking(-0.2)
                            .foregroundStyle(buttonTextColor)
                            .lineLimit(1)
                    }
                    .frame(width: Metrics.width, height: Metrics.buttonHeight)
                    .background(Capsule(style: .circular).fill(buttonColor))
                }
                .buttonStyle(PressStyle(scale: 0.97))
                .offset(y: Metrics.buttonTop)
            }
            .frame(width: Metrics.width, height: Metrics.height, alignment: .topLeading)
        }
        .onAppear { sync() }
        .onChange(of: autoPlay) { sync() }
        .onChange(of: reduceMotion) { sync() }
        .onChange(of: cities) { sync() }
        .task(id: taps) {
            guard taps > 0 else { return }
            try? await Task.sleep(for: .seconds(Metrics.fadeTime + 0.2))
            settling = false
        }
    }

    private func sync() {
        sim.looping = auto
        sim.rest(lengths: lengths)
    }

    /// A tap on the pill: on to the next city now; the loop waits before it carries on.
    private func skip() {
        guard cities.count > 1 else { return }
        sim.begin(at: sim.time, lengths: lengths, reduced: reduceMotion)
        sim.nextAt = sim.time + completeTime(fromLength: sim.fromLength, toLength: lengths[sim.to]) + Metrics.userHold
        settling = true
        taps += 1
        onSelect?(sim.to)
    }

    private struct PressStyle: ButtonStyle {
        let scale: Double

        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .scaleEffect(configuration.isPressed ? scale : 1)
                .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: configuration.isPressed)
                .sensoryFeedback(.impact(weight: .light), trigger: configuration.isPressed) { _, pressed in pressed }
        }
    }
}

/// A remote photo that covers its frame: the tint shows until it arrives, then it fades in.
private struct TypedDestinationCardPhoto: View {
    let address: String
    let tint: Color

    var body: some View {
        AsyncImage(url: URL(string: address), transaction: Transaction(animation: .easeOut(duration: 0.2))) { phase in
            ZStack {
                tint
                if let image = phase.image {
                    image.resizable().scaledToFill().transition(.opacity)
                }
            }
        }
    }
}

/// The text in the pill `elapsed` seconds into an edit: which city's name, and how many of its letters.
private func typedText(_ elapsed: Double, fromCity: Int, fromLength: Int, to: Int, toLength: Int) -> (city: Int, count: Int) {
    let deleteTime = Double(fromLength) * Metrics.deleteStep
    if elapsed < deleteTime { return (fromCity, fromLength - 1 - Int(elapsed / Metrics.deleteStep)) }
    let typing = elapsed - deleteTime - Metrics.typePause
    var count = 0
    var at = 0.0
    while count < toLength {
        at += Metrics.keyDelays[count % Metrics.keyDelays.count]
        if at > typing { break }
        count += 1
    }
    return (to, count)
}

/// Seconds an edit takes: deleting what was there, a pause, and one key after another.
private func completeTime(fromLength: Int, toLength: Int) -> Double {
    var time = Double(fromLength) * Metrics.deleteStep + Metrics.typePause
    for key in 0..<toLength { time += Metrics.keyDelays[key % Metrics.keyDelays.count] }
    return time
}

private func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

private func smooth(_ t: Double) -> Double {
    let p = clamp01(t)
    return p * p * (3 - 2 * p)
}

private func easeOut(_ t: Double) -> Double {
    let p = 1 - clamp01(t)
    return 1 - p * p * p
}

/// Height of the pin `since` seconds after a name is completed: a spring kicked upwards.
private func pinHop(_ since: Double) -> Double {
    guard since > 0 else { return 0 }
    let decay = Metrics.pinDamping / (2 * Metrics.pinMass)
    let omega = (Metrics.pinStiffness / Metrics.pinMass - decay * decay).squareRoot()
    return -Metrics.pinKick / omega * exp(-decay * since) * sin(omega * since)
}

/// A map pin, 20 × 27, with its tip at the bottom.
private let typedDestinationPin: Path = {
    var p = Path()
    p.move(to: CGPoint(x: 10, y: 0))
    p.addCurve(to: CGPoint(x: 0, y: 10), control1: CGPoint(x: 4.48, y: 0), control2: CGPoint(x: 0, y: 4.48))
    p.addCurve(to: CGPoint(x: 10, y: 27), control1: CGPoint(x: 0, y: 17.5), control2: CGPoint(x: 10, y: 27))
    p.addCurve(to: CGPoint(x: 20, y: 10), control1: CGPoint(x: 10, y: 27), control2: CGPoint(x: 20, y: 17.5))
    p.addCurve(to: CGPoint(x: 10, y: 0), control1: CGPoint(x: 20, y: 4.48), control2: CGPoint(x: 15.52, y: 0))
    p.closeSubpath()
    return p
}()

/// The ring and the four ticks of a "locate me" target in a 20 × 20 box.
private let typedDestinationTarget: Path = {
    var p = Path(ellipseIn: CGRect(x: 10 - Metrics.targetRing, y: 10 - Metrics.targetRing, width: Metrics.targetRing * 2, height: Metrics.targetRing * 2))
    for (from, to) in [(CGPoint(x: 10, y: 1.2), CGPoint(x: 10, y: 4.5)), (CGPoint(x: 10, y: 15.5), CGPoint(x: 10, y: 18.8)),
                       (CGPoint(x: 1.2, y: 10), CGPoint(x: 4.5, y: 10)), (CGPoint(x: 15.5, y: 10), CGPoint(x: 18.8, y: 10))] {
        p.move(to: from)
        p.addLine(to: to)
    }
    return p
}()

private enum Metrics {
    static let width = 320.0
    static let height = 418.0

    static let photoWidth = 240.0
    static let photoHeight = 324.0
    static let photoRadius = 14.0
    static let photoLeft = (width - photoWidth) / 2

    // The pill is wider than the photo and crosses its middle.
    static let pillWidth = 300.0
    static let pillHeight = 76.0
    static let pillLeft = (width - pillWidth) / 2
    static let pillTop = (photoHeight - pillHeight) / 2
    static let pillShadowOpacity = 0.14
    static let pillShadowRadius = 18.0
    static let pillShadowY = 10.0
    static let pinWidth = 20.0
    static let pinHeight = 27.0
    static let pinHole = 3.7
    static let pinLeft = 29.0
    static let textLeft = 68.0
    static let caretWidth = 2.0
    static let caretHeight = 33.0
    static let caretGap = 2.0

    static let buttonTop = 364.0
    static let buttonHeight = 54.0
    static let buttonGap = 9.0
    static let targetRing = 5.5
    static let targetDot = 2.2
    static let targetStroke = 1.8

    /// Seconds before each key lands, repeated along the name: an uneven but fixed rhythm.
    static let keyDelays = [0.12, 0.085, 0.14, 0.095, 0.07, 0.16, 0.09, 0.11]
    static let deleteStep = 0.045
    /// The beat between the last deleted letter and the first typed one.
    static let typePause = 0.2
    /// Half a blink of the caret: this long shown, this long hidden.
    static let caretBlink = 0.53

    // When a name is complete the next photo fades in while it settles from a slight zoom.
    static let fadeTime = 0.5
    static let zoomTime = 0.7
    static let zoom = 0.1

    // The pin's hop: a spring at rest that is kicked upwards, in points per second.
    static let pinMass = 1.0
    static let pinStiffness = 221.0
    static let pinDamping = 10.0
    static let pinKick = 170.0

    // autoPlay: the first edit starts after `firstAt`, then one city every `cityTime`, so the
    // three default cities come round every `loop` seconds.
    static let firstAt = 0.8
    static let loop = 7.8
    static let cityTime = loop / 3
    /// After a tap the loop waits this long once the name is typed.
    static let userHold = 2.0
}

#Preview {
    ZStack {
        Color(hex: 0xf5f3ee).ignoresSafeArea()
        TypedDestinationCard(autoPlay: true)
    }
}
