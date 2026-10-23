import SwiftUI

struct ResortWeatherCardResort {
    var name: String
    var region: String
    /// Degrees Celsius, between -99 and 99: the display ticks to it, and colder resorts get more snow.
    var temperature: Int
    /// The count in the pill on the photo.
    var likes: String
    /// The three facts under the name.
    var runs: String
    var distance: String
    var lifts: String
    /// Addresses of the photo and of its small copy for the strip, and the flat colour shown until they arrive.
    var photo: String
    var thumbnail: String
    var tint: Color
}

struct ResortWeatherCardFan {
    var initials: String
    var color: Color
}

/// A ski resort on a light card inside a near-black panel: a strip of thumbnails picks the resort,
/// and the card shows its photo under a little snowfall, the temperature on a seven-segment
/// display, a cluster of fans, the name and three facts. Picking another resort wipes its photo in
/// from the top, ticks the display through the degrees in between and rolls the texts.
struct ResortWeatherCard: View {
    var resorts = resortWeatherCardResorts
    /// The three round badges on the photo, smallest first.
    var fans = [
        ResortWeatherCardFan(initials: "LM", color: Color(hex: 0x6f8fd8)),
        ResortWeatherCardFan(initials: "AK", color: Color(hex: 0xd99a4e)),
        ResortWeatherCardFan(initials: "JT", color: Color(hex: 0x58a98c)),
    ]
    /// Picks the next resort by itself every 2.2 seconds.
    var autoPlay = false
    var country = "Austria"
    var mapLabel = "Map"
    var temperatureLabel = "Temperature"
    var temperatureNote = "Currently"
    /// Called with the index of the resort a thumbnail picks.
    var onSelect: ((Int) -> Void)? = nil
    var onBack: (() -> Void)? = nil
    var onMap: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// The resort on show, as a number on a spring: thumbnails and page dots size themselves by it.
    @State private var selection = ResortWeatherCardSpring(0)
    /// The resort that is leaving, the one that is arriving, and when that started.
    @State private var from = 0
    @State private var to = 0
    @State private var changedAt = Date.distantPast
    @State private var appeared = Date.now
    /// Counts touches: a change restarts the autoPlay loop after a pause.
    @State private var touches = 0
    @State private var ticks = 0

    private var auto: Bool { autoPlay && !reduceMotion }

    private struct Run: Equatable {
        var auto: Bool
        var touches: Int
    }

    var body: some View {
        TimelineView(.animation(paused: reduceMotion)) { timeline in
            let now = timeline.date
            // Seconds since the resort changed, and the clock the snow falls on. Under reduced motion
            // a change is a jump and the snow stands still.
            let t = reduceMotion ? Metrics.changeSeconds : min(Metrics.changeSeconds, now.timeIntervalSince(changedAt))
            let time = reduceMotion ? 0 : now.timeIntervalSince(appeared)
            let sel = selection.value(at: now)

            ZStack {
                RoundedRectangle(cornerRadius: Metrics.radius, style: .circular).fill(Color(hex: 0x0f1114))
                RoundedRectangle(cornerRadius: Metrics.radius, style: .circular).strokeBorder(.white.opacity(0.07), lineWidth: 1)

                header
                thumbnails(sel: sel)
                card(t: t, time: time, sel: sel)
                    .position(x: Metrics.width / 2, y: Metrics.cardTop + Metrics.cardSize.height / 2)
            }
            .frame(width: Metrics.width, height: Metrics.height)
        }
        .sensoryFeedback(.selection, trigger: ticks)
        .task(id: Run(auto: auto, touches: touches)) { await play() }
    }

    private var header: some View {
        HStack(spacing: 0) {
            Button {
                onBack?()
            } label: {
                HStack(spacing: 8) {
                    resortWeatherCardPath(Metrics.backIcon)
                        .stroke(.white, style: StrokeStyle(lineWidth: 1.7, lineCap: .round, lineJoin: .round))
                        .frame(width: 20, height: 20)
                    Text(country)
                        .font(.system(size: 20, weight: .medium))
                        .foregroundStyle(.white)
                }
                .frame(height: 30)
                .contentShape(Rectangle())
            }
            .accessibilityLabel("Back, \(country)")
            Spacer()
            Button {
                onMap?()
            } label: {
                HStack(spacing: 6) {
                    resortWeatherCardPath(Metrics.pinIcon)
                        .stroke(.white, style: StrokeStyle(lineWidth: 1.4, lineCap: .round, lineJoin: .round))
                        .frame(width: 14, height: 16)
                    Text(mapLabel)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(.white)
                }
                .frame(width: 74, height: 30)
                .background(Capsule().fill(.white.opacity(0.1)))
                .overlay(Capsule().strokeBorder(.white.opacity(0.28), lineWidth: 1))
            }
        }
        .buttonStyle(.plain)
        .frame(width: Metrics.width - 40, height: 30)
        .position(x: Metrics.width / 2, y: Metrics.headerY)
    }

    /// The strip: the selected thumbnail is larger and outlined, and its neighbours make room.
    private func thumbnails(sel: Double) -> some View {
        let weights = resorts.indices.map { max(0, 1 - abs(Double($0) - sel)) }
        return HStack(spacing: Metrics.thumbGap) {
            ForEach(Array(resorts.enumerated()), id: \.offset) { i, resort in
                let side = Metrics.thumb + (Metrics.thumbPicked - Metrics.thumb) * weights[i]
                let radius = Metrics.thumbRadius + (Metrics.thumbPickedRadius - Metrics.thumbRadius) * weights[i]
                Button {
                    touches += 1
                    ticks += 1
                    select(i, report: true)
                } label: {
                    ResortWeatherCardPhoto(url: resort.thumbnail, tint: resort.tint)
                        .frame(width: side, height: side)
                        .clipShape(RoundedRectangle(cornerRadius: radius, style: .circular))
                        .overlay(
                            RoundedRectangle(cornerRadius: radius, style: .circular)
                                .strokeBorder(.white.opacity(0.5 * weights[i]), lineWidth: 2)
                        )
                        .opacity(0.6 + 0.4 * weights[i])
                }
                .accessibilityLabel(resort.name)
                .accessibilityAddTraits(i == to ? .isSelected : [])
            }
        }
        .buttonStyle(.plain)
        .frame(height: Metrics.thumbPicked)
        .position(x: Metrics.width / 2, y: Metrics.thumbY)
    }

    private func card(t: Double, time: Double, sel: Double) -> some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .circular).fill(Color(hex: 0xe9e9e9))

            photo(t: t, time: time)
                .frame(width: Metrics.photo.width, height: Metrics.photo.height)
                .clipShape(RoundedRectangle(cornerRadius: Metrics.photoRadius, style: .circular))
                .offset(x: Metrics.photo.minX, y: Metrics.photo.minY)

            // Page dots: the current one is a short bar.
            HStack(spacing: 4) {
                ForEach(resorts.indices, id: \.self) { i in
                    let weight = max(0, 1 - abs(Double(i) - sel))
                    Capsule()
                        .fill(Color(hex: 0x1d1f22).opacity(0.3 + 0.4 * weight))
                        .frame(width: 4 + 10 * weight, height: 4)
                }
            }
            .frame(width: Metrics.cardSize.width, height: 4)
            .offset(y: Metrics.dotsY - 2)

            rolling(at: Metrics.nameAt, height: 30, y: Metrics.nameY, t: t) { resort in
                Text(resort.name)
                    .font(.system(size: 23, weight: .semibold))
                    .tracking(-0.3)
                    .foregroundStyle(Color(hex: 0x1d1f22))
            }
            rolling(at: Metrics.regionAt, height: 20, y: Metrics.regionY, t: t) { resort in
                Text(resort.region)
                    .font(.system(size: 14, weight: .regular))
                    .foregroundStyle(Color(hex: 0x86898d))
            }

            facts(t: t)
                .offset(x: Metrics.facts.minX, y: Metrics.facts.minY)
        }
        .frame(width: Metrics.cardSize.width, height: Metrics.cardSize.height)
    }

    /// One line of text per resort in the same place: the leaving one rolls up and out while the
    /// arriving one rolls in from below, `at` seconds after the change.
    private func rolling<Content: View>(
        at start: Double, height: Double, y: Double, t: Double, @ViewBuilder content: @escaping (ResortWeatherCardResort) -> Content
    ) -> some View {
        ZStack {
            ForEach(Array(resorts.enumerated()), id: \.offset) { i, resort in
                let roll = resortWeatherCardRoll(i, from: from, to: to, t: t - start)
                content(resort)
                    .lineLimit(1)
                    .fixedSize()
                    .opacity(roll.opacity)
                    .offset(y: roll.shift * height)
            }
        }
        .frame(width: Metrics.cardSize.width, height: height)
        .clipped()
        .offset(y: y - height / 2)
    }

    private func facts(t: Double) -> some View {
        let columns: [(icon: String, width: Double, text: KeyPath<ResortWeatherCardResort, String>)] = [
            (Metrics.runsIcon, 78, \.runs), (Metrics.distanceIcon, 56, \.distance), (Metrics.liftsIcon, 56, \.lifts),
        ]
        return HStack(spacing: 0) {
            ForEach(Array(columns.enumerated()), id: \.offset) { j, column in
                HStack(spacing: 5) {
                    resortWeatherCardPath(column.icon)
                        .stroke(Metrics.fact, style: StrokeStyle(lineWidth: 1.4, lineCap: .round, lineJoin: .round))
                        .frame(width: 18, height: 18)
                    ZStack(alignment: .leading) {
                        ForEach(Array(resorts.enumerated()), id: \.offset) { i, resort in
                            let roll = resortWeatherCardRoll(i, from: from, to: to, t: t - Metrics.factsAt - Double(j) * Metrics.factsGap)
                            Text(resort[keyPath: column.text])
                                .font(.system(size: 12.5, weight: .medium))
                                .monospacedDigit()
                                .foregroundStyle(Metrics.fact)
                                .lineLimit(1)
                                .fixedSize()
                                .opacity(roll.opacity)
                                .offset(y: roll.shift * 18)
                        }
                    }
                    .frame(width: column.width, height: 18, alignment: .leading)
                    .clipped()
                }
                if j < columns.count - 1 { Spacer(minLength: 0) }
            }
        }
        .padding(.horizontal, 16)
        .frame(width: Metrics.facts.width, height: Metrics.facts.height)
        .background(Capsule().fill(Color(hex: 0xd8d8d8)))
    }

    /// The photo area: the arriving photo wiped in over the leaving one, the snow, and what sits on top.
    private func photo(t: Double, time: Double) -> some View {
        let p = min(1, max(0, t / Metrics.wipeSeconds))
        let wipe = p < 0.5 ? 4 * p * p * p : 1 - pow(-2 * p + 2, 3) / 2
        // The wipe's lower edge runs from the top to past the bottom, with a soft band above it.
        let edge = wipe * (Metrics.photo.height + Metrics.feather)
        let shown = min(1, max(0, t / Metrics.changeSeconds))
        let flakes = resortWeatherCardFlakeCount(resorts[from].temperature)
            + (resortWeatherCardFlakeCount(resorts[to].temperature) - resortWeatherCardFlakeCount(resorts[from].temperature)) * shown
        let degrees = resortWeatherCardDegrees(from: resorts[from].temperature, to: resorts[to].temperature, t: t)

        return ZStack(alignment: .topLeading) {
            ForEach(Array(resorts.enumerated()), id: \.offset) { i, resort in
                // Only the arriving photo is cut by the wipe; the leaving one lies whole under it.
                let cut = i == to ? edge : Metrics.photo.height + Metrics.feather
                ResortWeatherCardPhoto(url: resort.photo, tint: resort.tint)
                    .frame(width: Metrics.photo.width, height: Metrics.photo.height)
                    .mask(
                        LinearGradient(
                            colors: [.black, .clear],
                            startPoint: UnitPoint(x: 0.5, y: (cut - Metrics.feather) / Metrics.photo.height),
                            endPoint: UnitPoint(x: 0.5, y: cut / Metrics.photo.height)
                        )
                    )
                    .opacity(i == to || i == from ? 1 : 0)
                    .zIndex(i == to ? 1 : 0)
            }

            Canvas { context, _ in drawSnow(context, time: time, flakes: flakes) }
                .zIndex(2)
                .allowsHitTesting(false)

            // The temperature: a black pill with a seven-segment display and its label.
            HStack(spacing: 10) {
                Canvas { context, _ in drawDisplay(context, degrees: degrees) }
                    .frame(width: Metrics.display.width, height: Metrics.display.height)
                VStack(alignment: .leading, spacing: 0) {
                    Text(temperatureLabel)
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundStyle(.white)
                        .frame(height: 13)
                    Text(temperatureNote)
                        .font(.system(size: 10, weight: .regular))
                        .foregroundStyle(Color(hex: 0x8d9096))
                        .frame(height: 13)
                }
                .lineLimit(1)
                Spacer(minLength: 0)
            }
            .padding(.leading, 16)
            .frame(width: Metrics.pill.width, height: Metrics.pill.height)
            .background(RoundedRectangle(cornerRadius: 14, style: .circular).fill(Color(hex: 0x0b0d10)))
            .offset(x: (Metrics.photo.width - Metrics.pill.width) / 2, y: 12)
            .zIndex(3)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text(verbatim: "\(temperatureLabel) \(resorts[to].temperature) °C"))

            ForEach(Array(fans.prefix(Metrics.fanSpots.count).enumerated()), id: \.offset) { i, fan in
                let spot = Metrics.fanSpots[i]
                ZStack(alignment: .bottomTrailing) {
                    Text(fan.initials)
                        .font(.system(size: spot.size * 0.34, weight: .bold))
                        .foregroundStyle(.white)
                        .frame(width: spot.size, height: spot.size)
                        .background(Circle().fill(fan.color))
                        .overlay(Circle().strokeBorder(.white.opacity(0.9), lineWidth: 2))
                    resortWeatherCardPath(Metrics.heartIcon)
                        .fill(Color(hex: 0xf08a3c))
                        .frame(width: 12, height: 12)
                        .scaleEffect(1.2)
                        .offset(x: 3, y: 2)
                }
                .offset(x: spot.x, y: spot.y)
                .zIndex(3)
                .accessibilityHidden(true)
            }

            HStack(spacing: 6) {
                resortWeatherCardPath(Metrics.flakeIcon)
                    .stroke(.white, style: StrokeStyle(lineWidth: 1.4, lineCap: .round))
                    .frame(width: 14, height: 14)
                ZStack(alignment: .leading) {
                    ForEach(Array(resorts.enumerated()), id: \.offset) { i, resort in
                        let roll = resortWeatherCardRoll(i, from: from, to: to, t: t - Metrics.nameAt)
                        Text(resort.likes)
                            .font(.system(size: 13, weight: .medium))
                            .monospacedDigit()
                            .foregroundStyle(.white)
                            .lineLimit(1)
                            .fixedSize()
                            .opacity(roll.opacity)
                            .offset(y: roll.shift * 18)
                    }
                }
                .frame(width: 78, height: 18, alignment: .leading)
                .clipped()
            }
            .frame(width: Metrics.likes.width, height: Metrics.likes.height)
            .background(Capsule().fill(Color.rgba(28, 32, 36, 0.5)))
            .overlay(Capsule().strokeBorder(.white.opacity(0.3), lineWidth: 1))
            .offset(
                x: Metrics.photo.width - 12 - Metrics.likes.width,
                y: Metrics.photo.height - 12 - Metrics.likes.height
            )
            .zIndex(3)
        }
    }

    /// Light snow over the photo: the same flakes on the same paths every `loop` seconds; the
    /// colder the resort, the more of them are let in.
    private func drawSnow(_ context: GraphicsContext, time: Double, flakes: Double) {
        let turn = time.truncatingRemainder(dividingBy: Metrics.loop) / Metrics.loop
        for (i, flake) in resortWeatherCardFlakes.enumerated() {
            let grown = min(1, max(0, flakes - Double(i)))
            guard grown > 0 else { continue }
            let fall = (flake.start + flake.falls * turn).truncatingRemainder(dividingBy: 1)
            let x = flake.x * Metrics.photo.width + flake.sway * sin(tau * flake.swings * turn + flake.phase)
            let y = -6 + fall * (Metrics.photo.height + 12)
            let r = flake.radius * grown
            context.fill(
                Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2)),
                with: .color(.white.opacity(flake.radius < 1.2 ? 0.5 : flake.radius < 1.6 ? 0.7 : 0.9))
            )
        }
    }

    /// Three slots for the sign and the digits, then a degree sign and a C, each drawn from seven segments.
    private func drawDisplay(_ context: GraphicsContext, degrees: Int) {
        let size = abs(degrees)
        let minus = 0b1000000
        let tens = size >= 10 ? Metrics.digits[size / 10 % 10] : (degrees < 0 ? minus : 0)
        // The three slots also show their unlit segments, faintly, like a real display.
        let glyphs: [(x: Double, width: Double, lit: Int, ghost: Bool)] = [
            (0, Metrics.glyph.width, size >= 10 && degrees < 0 ? minus : 0, true),
            (Metrics.glyphPitch, Metrics.glyph.width, tens, true),
            (Metrics.glyphPitch * 2, Metrics.glyph.width, Metrics.digits[size % 10], true),
            (Metrics.degreeX, Metrics.degreeWidth, 0b1100011, false),
            (Metrics.unitX, Metrics.glyph.width, 0b0111001, false),
        ]
        var lit = Path()
        var ghost = Path()
        for glyph in glyphs {
            for (bit, segment) in resortWeatherCardSegments(x: glyph.x, width: glyph.width).enumerated() {
                if glyph.ghost {
                    ghost.move(to: segment.0)
                    ghost.addLine(to: segment.1)
                }
                if glyph.lit & (1 << bit) != 0 {
                    lit.move(to: segment.0)
                    lit.addLine(to: segment.1)
                }
            }
        }
        let style = StrokeStyle(lineWidth: Metrics.segment, lineCap: .round)
        context.stroke(ghost, with: .color(.white.opacity(0.1)), style: style)
        context.stroke(lit, with: .color(.white), style: style)
    }

    private func select(_ index: Int, report: Bool = false) {
        guard index != to, resorts.indices.contains(index) else { return }
        from = to
        to = index
        changedAt = .now
        if reduceMotion { selection.set(Double(index)) } else { selection.retarget(Double(index)) }
        if report { onSelect?(index) }
    }

    /// The autoPlay loop. After a touch it waits, returns to the first resort and starts over.
    private func play() async {
        guard auto, resorts.count > 1 else { return }
        do {
            if touches > 0 {
                try await Task.sleep(for: .seconds(Metrics.idle))
                if to != 0 {
                    select(0)
                    try await Task.sleep(for: .seconds(Metrics.settle))
                }
            }
            let gap = Metrics.loop / Double(resorts.count)
            while true {
                for i in 1...resorts.count {
                    try await Task.sleep(for: .seconds(i == 1 ? Metrics.first : gap))
                    select(i % resorts.count)
                }
                try await Task.sleep(for: .seconds(gap - Metrics.first))
            }
        } catch {}
    }
}

private enum Metrics {
    static let width = 340.0
    static let height = 600.0
    static let radius = 34.0

    static let headerY = 38.0
    // The strip of thumbnails: their side and corner at rest and when picked.
    static let thumbY = 96.0
    static let thumb = 44.0
    static let thumbPicked = 60.0
    static let thumbRadius = 12.0
    static let thumbPickedRadius = 16.0
    static let thumbGap = 8.0

    static let cardTop = 138.0
    static let cardSize = CGSize(width: 320, height: 440)
    static let cardRadius = 36.0
    /// The photo inside the card.
    static let photo = CGRect(x: 6, y: 6, width: 308, height: 250)
    static let photoRadius = 30.0
    static let dotsY = 272.0
    static let nameY = 302.0
    static let regionY = 330.0
    static let facts = CGRect(x: 20, y: 364, width: 280, height: 52)
    static let fact = Color(hex: 0x6c6f73)

    // On the photo: the temperature pill, the fans (smallest first) and the pill with the count.
    static let pill = CGSize(width: 178, height: 40)
    static let fanSpots: [(x: Double, y: Double, size: Double)] = [(14, 170, 36), (60, 192, 40), (50, 142, 46)]
    static let likes = CGSize(width: 118, height: 32)

    // The seven-segment display: a glyph's box, the distance between slots, and the stroke.
    static let glyph = CGSize(width: 11, height: 20)
    static let glyphPitch = 16.0
    static let degreeX = 49.0
    static let degreeWidth = 6.0
    static let unitX = 60.0
    static let segment = 1.8
    static let segmentGap = 1.6
    static let display = CGSize(width: 73, height: 22)
    /// Lit segments of 0…9, one bit each: a (top), b, c, d (bottom), e, f, g (middle).
    static let digits = [0b0111111, 0b0000110, 0b1011011, 0b1001111, 0b1100110, 0b1101101, 0b1111101, 0b0000111, 0b1111111, 0b1101111]

    // A change (seconds from the tap): the photo wipes, the display ticks, the texts roll.
    static let wipeSeconds = 0.75
    /// Height of the soft band at the wipe's edge.
    static let feather = 90.0
    static let tickAt = 0.05
    static let tickSeconds = 0.8
    static let nameAt = 0.1
    static let regionAt = 0.16
    static let factsAt = 0.22
    static let factsGap = 0.05
    static let rollSeconds = 0.4
    static let changeSeconds = 0.9
    // The selection follows a spring of mass 1.
    static let pickStiffness = 170.0
    static let pickDamping = 20.0

    // Snow: how many flakes a resort gets for its temperature.
    static let flakes = 56
    static let flakesAtZero = 12.0
    static let flakesPerDegree = 3.0
    static let flakesLeast = 8.0

    // autoPlay (seconds): the next resort every loop / count, starting at `first`.
    static let first = 0.8
    static let loop = 8.8
    /// After a touch the loop waits this long, returns to the first resort, and starts over.
    static let idle = 2.5
    static let settle = 1.2

    static let backIcon = "M17 10 L3 10 M8.5 4.5 L3 10 L8.5 15.5"
    static let pinIcon = "M7 1.5 C9.5 1.5 11.5 3.5 11.5 6 C11.5 8.5 9.5 10.5 7 10.5 C4.5 10.5 2.5 8.5 2.5 6 C2.5 3.5 4.5 1.5 7 1.5 Z M7 10.5 L7 15"
    static let flakeIcon = "M7 1 L7 13 M1.8 4 L12.2 10 M1.8 10 L12.2 4"
    static let heartIcon = "M6 10.6 C2.6 8.2 1 6.5 1 4.5 C1 3 2.2 1.9 3.6 1.9 C4.6 1.9 5.4 2.4 6 3.3 C6.6 2.4 7.4 1.9 8.4 1.9 C9.8 1.9 11 3 11 4.5 C11 6.5 9.4 8.2 6 10.6 Z"
    static let runsIcon = "M11.5 3.2 C12.3 3.2 12.9 3.8 12.9 4.6 C12.9 5.4 12.3 6 11.5 6 C10.7 6 10.1 5.4 10.1 4.6 C10.1 3.8 10.7 3.2 11.5 3.2 Z M4.5 7 L8.5 8.6 L11.6 7.8 M8.5 8.6 L10.6 12.4 L7 13.6 M2.5 13 L14.5 17 M13.2 16.6 L15.8 15.6"
    static let distanceIcon = "M2 15.5 L7.2 6 L10.2 11 L12 8.5 L16 15.5 Z M13.6 2.6 C14.6 2.6 15.4 3.4 15.4 4.4 C15.4 5.4 14.6 6.2 13.6 6.2 C12.6 6.2 11.8 5.4 11.8 4.4 C11.8 3.4 12.6 2.6 13.6 2.6 Z"
    static let liftsIcon = "M3.5 4.5 L14.5 4.5 C15.6 4.5 16 5 16 6 L16 12 C16 13 15.6 13.5 14.5 13.5 L3.5 13.5 C2.4 13.5 2 13 2 12 L2 6 C2 5 2.4 4.5 3.5 4.5 Z M2 8 L16 8"
}

/// How far the text of resort `i` has rolled, `t` seconds into its turn: `shift` is in line
/// heights, below the line while arriving and above it once it has left.
private func resortWeatherCardRoll(_ i: Int, from: Int, to: Int, t: Double) -> (shift: Double, opacity: Double) {
    let p = min(1, max(0, t / Metrics.rollSeconds))
    let e = 1 - pow(1 - p, 3)
    if i == to { return (1 - e, from == to ? 1 : e) }
    if i == from { return (-e, 1 - e) }
    return (0, 0)
}

/// The number on the display `t` seconds into a change: it counts through every degree in between, slowing down.
private func resortWeatherCardDegrees(from: Int, to: Int, t: Double) -> Int {
    let p = min(1, max(0, (t - Metrics.tickAt) / Metrics.tickSeconds))
    let e = 1 - (1 - p) * (1 - p)
    return Int((Double(from) + Double(to - from) * e).rounded())
}

private func resortWeatherCardFlakeCount(_ temperature: Int) -> Double {
    min(Double(Metrics.flakes), max(Metrics.flakesLeast, Metrics.flakesAtZero - Double(temperature) * Metrics.flakesPerDegree))
}

/// The seven segments of a glyph whose box starts at `x`, in the order a, b, c, d, e, f, g.
private func resortWeatherCardSegments(x: Double, width: Double) -> [(CGPoint, CGPoint)] {
    let top = 1.0
    let bottom = top + Metrics.glyph.height
    let middle = (top + bottom) / 2
    let left = x + 1
    let right = left + width
    let gap = Metrics.segmentGap
    return [
        (CGPoint(x: left + gap, y: top), CGPoint(x: right - gap, y: top)),
        (CGPoint(x: right, y: top + gap), CGPoint(x: right, y: middle - gap)),
        (CGPoint(x: right, y: middle + gap), CGPoint(x: right, y: bottom - gap)),
        (CGPoint(x: left + gap, y: bottom), CGPoint(x: right - gap, y: bottom)),
        (CGPoint(x: left, y: middle + gap), CGPoint(x: left, y: bottom - gap)),
        (CGPoint(x: left, y: top + gap), CGPoint(x: left, y: middle - gap)),
        (CGPoint(x: left + gap, y: middle), CGPoint(x: right - gap, y: middle)),
    ]
}

private struct ResortWeatherCardFlake {
    /// Where it starts: across the photo and down its fall, both 0…1.
    var x: Double
    var start: Double
    /// Whole falls and whole swings per loop, so every flake is back where it began when the loop ends.
    var falls: Double
    var swings: Double
    var sway: Double
    var radius: Double
    var phase: Double
}

/// The flakes, from a fixed sequence (Park–Miller): the same snowfall every time.
private let resortWeatherCardFlakes: [ResortWeatherCardFlake] = {
    var seed = 11.0
    func next() -> Double {
        seed = (seed * 16807).truncatingRemainder(dividingBy: 2147483647)
        return seed / 2147483647
    }
    return (0..<Metrics.flakes).map { _ in
        ResortWeatherCardFlake(
            x: next(), start: next(), falls: 1 + (next() * 3).rounded(.down), swings: 2 + (next() * 4).rounded(.down),
            sway: 3 + next() * 7, radius: 0.8 + next() * 1.2, phase: next() * tau
        )
    }
}()

/// A number on a spring of mass 1, sampled against the `TimelineView` clock.
private struct ResortWeatherCardSpring {
    private var offset = 0.0
    private var velocity = 0.0
    private var target: Double
    private var start = Date.distantPast

    init(_ value: Double) {
        target = value
    }

    private var decay: Double { Metrics.pickDamping / 2 }
    private var omega: Double { (Metrics.pickStiffness - decay * decay).squareRoot() }

    func value(at date: Date) -> Double {
        let t = date.timeIntervalSince(start)
        guard t < 10 else { return target }
        let swing = (velocity + decay * offset) / omega
        return target + exp(-decay * t) * (offset * cos(omega * t) + swing * sin(omega * t))
    }

    private func speed(at date: Date) -> Double {
        let t = date.timeIntervalSince(start)
        guard t < 10 else { return 0 }
        let swing = (velocity + decay * offset) / omega
        return exp(-decay * t) * ((omega * swing - decay * offset) * cos(omega * t) - (omega * offset + decay * swing) * sin(omega * t))
    }

    mutating func set(_ value: Double) {
        target = value
        offset = 0
        velocity = 0
        start = .distantPast
    }

    /// Heads for `value` from wherever it is, keeping its speed.
    mutating func retarget(_ value: Double, at date: Date = .now) {
        offset = self.value(at: date) - value
        velocity = speed(at: date)
        target = value
        start = date
    }
}

/// A remote photo cropped to fill, over a flat tint until it arrives.
private struct ResortWeatherCardPhoto: View {
    let url: String
    let tint: Color

    var body: some View {
        tint.overlay(
            AsyncImage(url: URL(string: url), transaction: Transaction(animation: .easeOut(duration: 0.2))) { phase in
                if let image = phase.image {
                    image.resizable().scaledToFill().transition(.opacity)
                }
            }
        )
        .clipped()
    }
}

private func resortWeatherCardPath(_ svg: String) -> Path {
    var path = Path()
    let scanner = Scanner(string: svg)
    func point() -> CGPoint {
        let x = scanner.scanDouble() ?? 0
        return CGPoint(x: x, y: scanner.scanDouble() ?? 0)
    }
    while let command = scanner.scanCharacter() {
        switch command {
        case "M": path.move(to: point())
        case "L": path.addLine(to: point())
        case "C":
            let first = point()
            let second = point()
            path.addCurve(to: point(), control1: first, control2: second)
        default: path.closeSubpath()
        }
    }
    return path
}

private func resortWeatherCardPhotoURL(_ id: String, width: Int) -> String {
    "https://images.unsplash.com/photo-\(id)?auto=format&fit=crop&w=\(width)&q=80"
}

let resortWeatherCardResorts: [ResortWeatherCardResort] = [
    ResortWeatherCardResort(
        name: "Obertauern Ski Resort", region: "Austria, Salzburg", temperature: -8, likes: "198 frosties",
        runs: "26 ski runs", distance: "112 km", lifts: "8 types",
        photo: resortWeatherCardPhotoURL("1454496522488-7a8e488e8606", width: 800), thumbnail: resortWeatherCardPhotoURL("1454496522488-7a8e488e8606", width: 160),
        tint: Color(hex: 0x8ea3bf)
    ),
    ResortWeatherCardResort(
        name: "St. Anton Ski Resort", region: "Austria, Tyrol", temperature: -14, likes: "342 frosties",
        runs: "88 ski runs", distance: "305 km", lifts: "9 types",
        photo: resortWeatherCardPhotoURL("1519681393784-d120267933ba", width: 800), thumbnail: resortWeatherCardPhotoURL("1519681393784-d120267933ba", width: 160),
        tint: Color(hex: 0x3a4a78)
    ),
    ResortWeatherCardResort(
        name: "Kitzsteinhorn Glacier", region: "Austria, Salzburg", temperature: 2, likes: "87 frosties",
        runs: "17 ski runs", distance: "61 km", lifts: "5 types",
        photo: resortWeatherCardPhotoURL("1506905925346-21bda4d32df4", width: 800), thumbnail: resortWeatherCardPhotoURL("1506905925346-21bda4d32df4", width: 160),
        tint: Color(hex: 0x7c8fb0)
    ),
    ResortWeatherCardResort(
        name: "Hintertux Ski Resort", region: "Austria, Tyrol", temperature: -11, likes: "256 frosties",
        runs: "34 ski runs", distance: "196 km", lifts: "7 types",
        photo: resortWeatherCardPhotoURL("1531366936337-7c912a4589a7", width: 800), thumbnail: resortWeatherCardPhotoURL("1531366936337-7c912a4589a7", width: 160),
        tint: Color(hex: 0x2f5a6e)
    ),
]

#Preview {
    ZStack {
        Color(hex: 0x060709).ignoresSafeArea()
        ResortWeatherCard(autoPlay: true)
    }
}
