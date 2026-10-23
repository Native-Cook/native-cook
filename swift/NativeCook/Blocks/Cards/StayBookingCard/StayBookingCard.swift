import SwiftUI

struct StayBookingCardStay: Equatable {
    var name: String
    var address: String
    var beds: Int
    var baths: Int
    /// Floor area in square feet.
    var area: Int
    /// Price per night in whole dollars.
    var price: Int
    var photo: String
    /// The deep colour the photo sinks into under the texts; also shown flat until the photo arrives.
    var tint: Color

    static let samples = [
        StayBookingCardStay(
            name: "The Hill Guest House", address: "Alpweg 12, Lauterbrunnen, 3822", beds: 2, baths: 1, area: 1150, price: 620,
            photo: "https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?auto=format&fit=crop&w=1056&h=880&q=80",
            tint: Color(hex: 0x2f4a1c)
        ),
        StayBookingCardStay(
            name: "Casa Caldera", address: "Nomikou 7, Oia, Santorini 847 02", beds: 3, baths: 2, area: 1480, price: 895,
            photo: "https://images.unsplash.com/photo-1570077188670-e3a8d69ac5ff?auto=format&fit=crop&w=1056&h=880&q=80",
            tint: Color(hex: 0x1c3a5e)
        ),
        StayBookingCardStay(
            name: "Lagoon Water Villa", address: "Jetty 3, Baa Atoll, Maldives", beds: 1, baths: 1, area: 920, price: 740,
            photo: "https://images.unsplash.com/photo-1573843981267-be1999ff37cd?auto=format&fit=crop&w=1056&h=880&q=80",
            tint: Color(hex: 0x0e4a52)
        ),
    ]
}

/// A photo card for a place to stay: its lower part sinks, blurred, into a deep tint that carries the
/// name, the address, three facts, the price and a "Reserve Now" button. A sideways swipe changes the
/// stay: the photo slides with parallax, the texts follow one after another, the tint changes colour
/// and the price rolls like a counter. A tap on the button shrinks it to a circle, draws a check and
/// widens it again as "Reserved".
struct StayBookingCard: View {
    /// The stays, in a ring. At least three.
    var stays = StayBookingCardStay.samples
    /// Swipes through the stays and reserves the last one, as if a finger did it.
    var autoPlay = false
    var bedsLabel = "Bed"
    var bathsLabel = "Baths"
    var areaLabel = "Sqft"
    var currency = "$"
    var reserveLabel = "Reserve Now"
    var reservedLabel = "Reserved"
    /// Called with the index of the stay the card is settling on.
    var onChange: ((Int) -> Void)? = nil
    /// Called with the index of the stay when it is reserved.
    var onReserve: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var pager = Pager()
    /// Keeps the timeline running while a touch is being followed.
    @State private var awake = false
    @State private var touches = 0
    @State private var ticks = 0
    @State private var taps = 0

    private var auto: Bool { autoPlay && !reduceMotion }

    var body: some View {
        TimelineView(.animation(paused: !auto && !awake)) { timeline in
            let count = stays.count
            let _ = pager.advance(to: timeline.date, count: count)
            let pos = pager.pos
            let current = wrapIndex(Int(pager.target.rounded()), count)
            let digits = String(stays.map(\.price).max() ?? 0).count

            ZStack(alignment: .topLeading) {
                ForEach(stays.indices, id: \.self) { index in
                    let place = centered(index, pos, count)
                    // The slide moves a full card per stay while the photo inside it lags behind.
                    ZStack(alignment: .topLeading) {
                        picture(stays[index], place: place)
                        picture(stays[index], place: place)
                            .blur(radius: Metrics.blur)
                            .mask(
                                LinearGradient(
                                    stops: [
                                        .init(color: .clear, location: Metrics.blurFrom / Metrics.height),
                                        .init(color: .black, location: Metrics.blurTo / Metrics.height),
                                    ],
                                    startPoint: .top, endPoint: .bottom
                                )
                            )
                    }
                    .frame(width: Metrics.width, height: Metrics.height, alignment: .topLeading)
                    .clipped()
                    .offset(x: reduceMotion ? 0 : place * Metrics.width)
                    .opacity(reduceMotion ? crossFade(place) : 1)
                    .zIndex(abs(place) > 0.5 ? 1 : 0)
                }

                // The tint under the texts, in the colour of the stay in view.
                ForEach(stays.indices, id: \.self) { index in
                    LinearGradient(
                        stops: [
                            .init(color: stays[index].tint.opacity(0), location: 0),
                            .init(color: stays[index].tint.opacity(0.55), location: 0.38),
                            .init(color: stays[index].tint.opacity(0.92), location: 0.66),
                            .init(color: stays[index].tint.opacity(0.97), location: 1),
                        ],
                        startPoint: .top, endPoint: .bottom
                    )
                    .frame(width: Metrics.width, height: Metrics.height - Metrics.tintY)
                    .offset(y: Metrics.tintY)
                    .opacity(weight(centered(index, pos, count)))
                }
                .zIndex(2)

                Group {
                    dots(pos: pos, count: count)
                    ForEach(stays.indices, id: \.self) { index in
                        texts(stays[index], place: centered(index, pos, count))
                    }
                    price(pos: pos, count: count, digits: digits)
                    button(stay: stays[current], index: current)
                }
                .zIndex(3)
            }
            .frame(width: Metrics.width, height: Metrics.height, alignment: .topLeading)
            .clipShape(RoundedRectangle(cornerRadius: Metrics.radius, style: .continuous))
            .contentShape(Rectangle())
            .gesture(drag)
            .onChange(of: current) { onChange?(current) }
            .accessibilityElement(children: .contain)
            .accessibilityAdjustableAction { direction in
                touch()
                pager.target = pager.target.rounded() + (direction == .increment ? 1 : -1)
            }
        }
        .shadow(color: Color.rgba(30, 34, 40, 0.22), radius: 26, y: 18)
        .sensoryFeedback(.selection, trigger: ticks)
        .sensoryFeedback(.impact(weight: .light), trigger: taps)
        .onAppear { sync() }
        .onChange(of: autoPlay) { sync() }
        .onChange(of: reduceMotion) { sync() }
        .task(id: touches) {
            guard touches > 0 else { return }
            try? await Task.sleep(for: .seconds(Metrics.userHold + Metrics.reserve))
            awake = false
        }
    }

    /// The photo, wider than the card, shifted against the slide's travel.
    private func picture(_ stay: StayBookingCardStay, place: Double) -> some View {
        Photo(url: stay.photo, tint: stay.tint)
            .frame(width: Metrics.photoWidth, height: Metrics.height)
            .clipped()
            .offset(x: -(Metrics.photoWidth - Metrics.width) / 2 - (reduceMotion ? 0 : place * Metrics.width * Metrics.parallax))
    }

    private func dots(pos: Double, count: Int) -> some View {
        HStack(spacing: 5) {
            ForEach(stays.indices, id: \.self) { index in
                let on = weight(centered(index, pos, count))
                Capsule()
                    .fill(.white.opacity(0.45 + 0.55 * on))
                    .frame(width: 6 + 10 * on, height: 6)
            }
        }
        .frame(width: Metrics.width, height: 6)
        .offset(y: Metrics.dotsY)
        .accessibilityHidden(true)
    }

    /// Name, address and facts of one stay: each line travels a little further than the one above.
    private func texts(_ stay: StayBookingCardStay, place: Double) -> some View {
        let shift = reduceMotion ? 0.0 : place
        return ZStack(alignment: .topLeading) {
            Text(stay.name)
                .font(.system(size: 19, weight: .semibold))
                .foregroundStyle(.white)
                .frame(height: 24)
                .offset(x: Metrics.pad + shift * Metrics.travel, y: Metrics.nameY)
                .opacity(lineFade(place, 0))

            HStack(spacing: 5) {
                Glyph(icon: .map)
                Text(stay.address).font(.system(size: 13))
            }
            .frame(height: 18)
            .offset(x: Metrics.pad + shift * (Metrics.travel + Metrics.stagger), y: Metrics.addressY)
            .opacity(lineFade(place, 1))

            HStack(spacing: 0) {
                fact(.bed, bedsLabel, String(stay.beds)).frame(maxWidth: .infinity, alignment: .leading)
                Rectangle().fill(.white.opacity(0.25)).frame(width: 1, height: 20)
                fact(.bath, bathsLabel, String(stay.baths)).frame(maxWidth: .infinity)
                Rectangle().fill(.white.opacity(0.25)).frame(width: 1, height: 20)
                fact(.area, areaLabel, String(stay.area)).frame(maxWidth: .infinity, alignment: .trailing)
            }
            .frame(width: Metrics.width - Metrics.pad * 2, height: 20)
            .offset(x: Metrics.pad + shift * (Metrics.travel + Metrics.stagger * 2), y: Metrics.factsY)
            .opacity(lineFade(place, 2))
        }
        .foregroundStyle(.white.opacity(0.74))
        .lineLimit(1)
        .accessibilityElement(children: .combine)
        .accessibilityHidden(abs(place) > 0.5)
    }

    private func fact(_ icon: StayBookingCardIcon, _ label: String, _ value: String) -> some View {
        HStack(spacing: 5) {
            Glyph(icon: icon)
            (Text(label + ": ") + Text(value).fontWeight(.semibold).foregroundColor(.white))
                .font(.system(size: 12.5))
                .monospacedDigit()
        }
        .fixedSize()
    }

    /// The price: each digit rolls in its own column, the last one first.
    private func price(pos: Double, count: Int, digits: Int) -> some View {
        HStack(spacing: 0) {
            Text(currency)
            ForEach(0..<digits, id: \.self) { column in
                let at = columnPosition(pos, column: column, of: digits)
                ZStack {
                    ForEach(stays.indices, id: \.self) { index in
                        let place = centered(index, at, count)
                        Text(digit(stays[index].price, column: column, of: digits))
                            .offset(y: reduceMotion ? 0 : place * Metrics.priceLine)
                            .opacity(reduceMotion ? max(0, 1 - abs(place)) : rollFade(place))
                    }
                }
                .frame(width: Metrics.digit, height: Metrics.priceLine)
                .clipped()
            }
        }
        .font(.system(size: 20, weight: .medium))
        .monospacedDigit()
        .foregroundStyle(.white)
        .frame(width: Metrics.priceWidth, height: Metrics.rowHeight)
        .background(Capsule().fill(.white.opacity(0.2)))
        .overlay(Capsule().strokeBorder(.white.opacity(0.12), lineWidth: 1))
        .offset(x: Metrics.pad, y: Metrics.rowY)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(currency)\(stays[wrapIndex(Int(pos.rounded()), count)].price) per night")
    }

    private func button(stay: StayBookingCardStay, index: Int) -> some View {
        let t = pager.confirm
        let shrink = easeInOut(segment(t, 0, 0.22))
        let widen = easeOutBack(segment(t, 0.6, 0.92))
        let width = Metrics.buttonWidth - (Metrics.buttonWidth - Metrics.rowHeight) * (shrink - widen)
        return Button {
            touch()
            taps += 1
            if pager.reserved == index {
                pager.reserved = -1
            } else {
                pager.reserved = index
                onReserve?(index)
            }
        } label: {
            ZStack {
                Capsule().fill(.white).frame(width: reduceMotion ? Metrics.buttonWidth : width)
                Text(reserveLabel)
                    .foregroundStyle(Color(hex: 0x111111))
                    .opacity(1 - segment(t, 0, 0.12))
                Check()
                    .trim(from: 0, to: easeOut(segment(t, 0.24, 0.52)))
                    .stroke(stay.tint, style: StrokeStyle(lineWidth: 2.4, lineCap: .round, lineJoin: .round))
                    .frame(width: 16, height: 16)
                    .offset(x: -Metrics.checkShift * widen)
                Text(reservedLabel)
                    .foregroundStyle(stay.tint)
                    .opacity(segment(t, 0.72, 0.95))
                    .offset(x: Metrics.reservedShift)
            }
            .font(.system(size: 14.5, weight: .semibold))
            .lineLimit(1)
            .frame(width: Metrics.buttonWidth, height: Metrics.rowHeight)
            .contentShape(Capsule())
        }
        .buttonStyle(StayBookingCardPress())
        .offset(x: Metrics.width - Metrics.pad - Metrics.buttonWidth, y: Metrics.rowY)
        .accessibilityLabel(pager.reserved == index ? reservedLabel : reserveLabel)
    }

    private var drag: some Gesture {
        DragGesture(minimumDistance: 8)
            .onChanged { value in
                touch()
                if !pager.touching {
                    pager.touching = true
                    pager.grabbed = pager.pos
                }
                // One stay at most per swipe.
                let home = pager.grabbed.rounded()
                pager.pos = min(home + 1, max(home - 1, pager.grabbed - value.translation.width / Metrics.dragSpan))
            }
            .onEnded { _ in
                guard pager.touching else { return }
                pager.touching = false
                let home = pager.grabbed.rounded()
                let landing = (pager.pos + pager.velocity * Metrics.throwTime).rounded()
                let next = min(home + 1, max(home - 1, landing))
                if next != pager.target.rounded() { ticks += 1 }
                pager.target = next
            }
    }

    private func sync() {
        pager.looping = auto
        pager.hold = 0
        pager.clock = 0
        pager.base = pager.target
    }

    /// A touch takes over from the loop, which starts again from rest a little later.
    private func touch() {
        pager.hold = Metrics.userHold
        awake = true
        touches += 1
    }
}

private struct StayBookingCardPress: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: configuration.isPressed)
    }
}

private enum StayBookingCardIcon {
    case map, bed, bath, area
}

private enum Metrics {
    static let width = 330.0
    static let height = 440.0
    static let radius = 26.0
    static let pad = 16.0
    /// The photo is wider than the card, so it can lag behind its slide without showing an edge.
    static let photoWidth = width * 1.6
    /// Share of a card's width the photo stays behind over one stay.
    static let parallax = 0.3
    // The photo is blurred from `blurFrom` down, fully so at `blurTo`; the tint starts at `tintY`.
    static let blur = 14.0
    static let blurFrom = 250.0
    static let blurTo = 304.0
    static let tintY = 215.0

    static let dotsY = 267.0
    static let nameY = 293.0
    static let addressY = 324.0
    static let factsY = 349.0
    /// The row with the price and the button.
    static let rowY = 382.0
    static let rowHeight = 42.0
    static let priceWidth = 82.0
    static let buttonWidth = 204.0
    static let digit = 12.5
    static let priceLine = 26.0
    /// How far the check moves left to make room for "Reserved", and where that word sits.
    static let checkShift = 37.0
    static let reservedShift = 12.0

    /// Points the name travels over one stay; each line below travels `stagger` more.
    static let travel = 70.0
    static let stagger = 34.0

    // The spring that carries the stays to rest (mass 1).
    static let stiffness = 150.0
    static let damping = 20.0
    /// Finger travel that changes one stay.
    static let dragSpan = 260.0
    /// A release lands on the stay the slide would reach after this many seconds at its speed.
    static let throwTime = 0.18

    /// Seconds the button takes to confirm, and to go back when the stay changes.
    static let reserve = 1.5
    static let unreserve = 0.35

    // autoPlay (seconds): a swipe at `first` and every `step` until the last stay, which is reserved
    // one step later and kept for `keep` before the swipe back to the first. With three stays one
    // round takes `loop`.
    static let loop = 9.6
    static let first = 0.8
    static let step = 2.2
    static let keep = 2.4
    /// After a touch the loop waits this long before it starts again.
    static let userHold = 2.5
}

/// Per-frame state of the card. `pos` is the stay in view, with fractions while it slides.
private final class Pager {
    var last: Date? = nil
    var pos = 0.0
    var target = 0.0
    /// Stays per second.
    var velocity = 0.0
    /// The stay that is reserved, or −1, and how far its button has come, 0…1.
    var reserved = -1
    var confirm = 0.0
    var looping = false
    /// Where the loop started counting from, and its clock.
    var base = 0.0
    var clock = 0.0
    /// Seconds the loop still waits after a touch.
    var hold = 0.0
    var touching = false
    /// Where the stays were when the finger took them.
    var grabbed = 0.0
    private var previous = 0.0

    func advance(to date: Date, count: Int) {
        guard let last else {
            self.last = date
            return
        }
        let dt = min(date.timeIntervalSince(last), 0.064)
        guard dt > 0 else { return }
        self.last = date
        let n = Double(count)

        if looping, hold <= 0 {
            let reserveAt = Metrics.first + Metrics.step * (n - 1)
            let backAt = reserveAt + Metrics.keep
            let loop = Metrics.loop + Metrics.step * (n - 3)
            clock += dt
            if clock >= loop {
                // Once round the ring: the same stays under smaller numbers.
                clock -= loop
                pos -= n
                previous -= n
            }
            var swipes = clock >= backAt ? 1.0 : 0.0
            for index in 0..<(count - 1) where clock >= Metrics.first + Metrics.step * Double(index) { swipes += 1 }
            target = base + swipes
            reserved = clock >= reserveAt && clock < backAt ? wrapIndex(Int(base) + count - 1, count) : -1
        } else if hold > 0, !touching {
            hold -= dt
            if hold <= 0 {
                if pos == target, confirm == 0 || confirm == 1 {
                    base = Double(wrapIndex(Int(pos.rounded()), count))
                    pos = base
                    previous = base
                    target = base
                    clock = 0
                } else {
                    hold = 0.01
                }
            }
        }

        if touching {
            // The finger moves the stays; keep a smoothed speed for the release.
            velocity += ((pos - previous) / dt - velocity) * min(1, dt * 30)
        } else {
            velocity += (Metrics.stiffness * (target - pos) - Metrics.damping * velocity) * dt
            pos += velocity * dt
            if abs(target - pos) < 0.0004, abs(velocity) < 0.004 {
                pos = target
                velocity = 0
            }
        }
        previous = pos

        // The button confirms while its stay is the one in view, and goes back as soon as it is not.
        if reserved >= 0, reserved == wrapIndex(Int(target.rounded()), count) {
            confirm = min(1, confirm + dt / Metrics.reserve)
        } else {
            confirm = max(0, confirm - dt / Metrics.unreserve)
        }
    }
}

private func wrapIndex(_ index: Int, _ count: Int) -> Int {
    ((index % count) + count) % count
}

private func segment(_ t: Double, _ from: Double, _ to: Double) -> Double {
    min(1, max(0, (t - from) / (to - from)))
}

private func easeOut(_ t: Double) -> Double { 1 - pow(1 - t, 3) }

private func easeInOut(_ t: Double) -> Double { t < 0.5 ? 4 * t * t * t : 1 - pow(2 - 2 * t, 3) / 2 }

/// Ease-out that runs about 10% past its end before it settles.
private func easeOutBack(_ t: Double) -> Double {
    1 + 2.70158 * pow(t - 1, 3) + 1.70158 * pow(t - 1, 2)
}

private func smooth(_ t: Double) -> Double {
    let x = min(1, max(0, t))
    return x * x * (3 - 2 * x)
}

/// Where stay `index` stands: 0 in view, 1 one card to the right, −1 one card to the left.
private func centered(_ index: Int, _ pos: Double, _ count: Int) -> Double {
    let n = Double(count)
    var place = (Double(index) - pos).truncatingRemainder(dividingBy: n)
    if place <= -n / 2 { place += n }
    if place > n / 2 { place -= n }
    return place
}

/// How much the stay at `place` is the one in view, 0…1.
private func weight(_ place: Double) -> Double {
    smooth(1 - abs(place))
}

/// Opacity of text line number `order` of the stay at `place`: the lower the line, the sooner it goes.
private func lineFade(_ place: Double, _ order: Double) -> Double {
    smooth(1 - abs(place) * (2.2 + 0.5 * order))
}

/// Reduced motion: the photos stay in place and the one further away fades over the nearer one.
private func crossFade(_ place: Double) -> Double {
    abs(place) <= 0.5 ? 1 : max(0, 1 - abs(place))
}

/// Opacity of a digit that rolls through its column at `place`: it fades only as it leaves.
private func rollFade(_ place: Double) -> Double {
    smooth((0.75 - abs(place)) / 0.3)
}

/// Each column of the price changes during its own part of the slide, the last digit first.
private func columnPosition(_ pos: Double, column: Int, of digits: Int) -> Double {
    let whole = pos.rounded(.down)
    let from = 0.1 * Double(digits - 1 - column)
    return whole + easeInOut(segment(pos - whole, from, from + 0.6))
}

/// The character of `price` in `column` of a right-aligned field `digits` wide.
private func digit(_ price: Int, column: Int, of digits: Int) -> String {
    let text = String(price)
    let index = column - (digits - text.count)
    return index < 0 ? "" : String(Array(text)[index])
}

/// The photo over a flat tint, faded in when it arrives.
private struct Photo: View {
    let url: String
    let tint: Color

    var body: some View {
        tint.overlay {
            AsyncImage(url: URL(string: url), transaction: Transaction(animation: .easeOut(duration: 0.2))) { phase in
                if let image = phase.image {
                    image.resizable().scaledToFill().transition(.opacity)
                } else {
                    Color.clear
                }
            }
        }
        .accessibilityHidden(true)
    }
}

/// The check mark the button draws, in a 16 × 16 box.
private struct Check: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: 2, y: 8.5))
        path.addLine(to: CGPoint(x: 6, y: 12.5))
        path.addLine(to: CGPoint(x: 14.5, y: 3.5))
        return path
    }
}

/// A small line icon, drawn from the same strokes in every stack.
private struct Glyph: View {
    let icon: StayBookingCardIcon

    var body: some View {
        Path { path in
            let unit = 14.0 / 16
            for line in strokes(icon) {
                for index in stride(from: 0, to: line.count, by: 2) {
                    let point = CGPoint(x: line[index] * unit, y: line[index + 1] * unit)
                    if index == 0 { path.move(to: point) } else { path.addLine(to: point) }
                }
            }
        }
        .stroke(style: StrokeStyle(lineWidth: 1.2, lineCap: .round, lineJoin: .round))
        .frame(width: 14, height: 14)
        .accessibilityHidden(true)
    }
}

/// Polylines of an icon in a 16 × 16 box, as x, y pairs.
private func strokes(_ icon: StayBookingCardIcon) -> [[Double]] {
    switch icon {
    case .map:
        [[2, 4, 6, 2.5, 10, 4, 14, 2.5, 14, 12, 10, 13.5, 6, 12, 2, 13.5, 2, 4], [6, 2.5, 6, 12], [10, 4, 10, 13.5]]
    case .bed:
        [[1.8, 13, 1.8, 4.5], [1.8, 10.5, 14.2, 10.5], [14.2, 13, 14.2, 8.2, 7.2, 8.2, 7.2, 10.5], [3.4, 8.4, 5.6, 8.4]]
    case .bath:
        [[1.5, 8.5, 14.5, 8.5], [2.5, 8.5, 3.2, 12, 4.6, 13, 11.4, 13, 12.8, 12, 13.5, 8.5], [4, 8.5, 4, 3.6, 5.2, 2.6, 6.6, 3.6]]
    case .area:
        [[2.5, 2.5, 13.5, 2.5, 13.5, 13.5, 2.5, 13.5, 2.5, 2.5], [5.5, 10.5, 10.5, 5.5], [8, 5.5, 10.5, 5.5, 10.5, 8], [5.5, 8, 5.5, 10.5, 8, 10.5]]
    }
}

#Preview {
    ZStack {
        Color(hex: 0xececee).ignoresSafeArea()
        StayBookingCard(autoPlay: true)
    }
}
