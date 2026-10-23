import SwiftUI

struct TravelPassStackItem: Equatable {
    var name: String
    /// Address of a wide photo, about 2 : 1.
    var photo: String
    /// Flat colour shown until the photo arrives.
    var tint: Color
    /// Path data of the glyph in a 20 × 20 box: absolute M, L, Q and Z commands separated by spaces.
    var glyph: String

    static let samples = [
        TravelPassStackItem(
            name: "Kestrel Heli Transfer",
            photo: "https://images.unsplash.com/photo-1513635269975-59663e0ac1ad?auto=format&fit=crop&w=640&h=330&q=80",
            tint: Color(hex: 0x7c7c7c),
            glyph: "M 4 4.5 L 16 4.5 M 10 4.5 L 10 7.5 M 7 7.5 L 12 7.5 Q 16.5 7.5 16.5 11.5 Q 16.5 14 14 14 L 9 14 Q 6 14 6 11 L 6 8.5 Q 6 7.5 7 7.5 Z M 6 10 L 2 9 M 2 7.5 L 2 10.5 M 8 16.5 L 15 16.5"
        ),
        TravelPassStackItem(
            name: "First Light Lounge",
            photo: "https://images.unsplash.com/photo-1512453979798-5ea266f8880c?auto=format&fit=crop&w=640&h=330&q=80",
            tint: Color(hex: 0x978477),
            glyph: "M 6 9 L 6 6.5 Q 6 4.5 8 4.5 L 12 4.5 Q 14 4.5 14 6.5 L 14 9 M 4 9 Q 4 8 5 8 Q 6 8 6 9 L 6 11.5 L 14 11.5 L 14 9 Q 14 8 15 8 Q 16 8 16 9 L 16 14 L 4 14 Z M 6 14 L 6 16 M 14 14 L 14 16"
        ),
        TravelPassStackItem(
            name: "Northwind Charter",
            photo: "https://images.unsplash.com/photo-1436491865332-7a61a109cc05?auto=format&fit=crop&w=640&h=330&q=80",
            tint: Color(hex: 0xadb3b8),
            glyph: "M 3 10 L 17 10 M 11.5 10 L 7.5 3.5 M 11.5 10 L 7.5 16.5 M 5 10 L 3.2 7 M 5 10 L 3.2 13"
        ),
        TravelPassStackItem(
            name: "Hollow Pine Cabins",
            photo: "https://images.unsplash.com/photo-1441974231531-c6227db76b6e?auto=format&fit=crop&w=640&h=330&q=80",
            tint: Color(hex: 0x454732),
            glyph: "M 3 16 L 10 4 L 17 16 Z M 8.5 16 L 8.5 12.5 L 11.5 12.5 L 11.5 16"
        ),
        TravelPassStackItem(
            name: "Quiet Hours Spa",
            photo: "https://images.unsplash.com/photo-1573843981267-be1999ff37cd?auto=format&fit=crop&w=640&h=330&q=80",
            tint: Color(hex: 0x80a7a0),
            glyph: "M 10 4 Q 13.5 8.5 10 14 Q 6.5 8.5 10 4 Z M 10 14 Q 4.5 14 3 8.5 Q 6 8.5 7.4 10 M 10 14 Q 15.5 14 17 8.5 Q 14 8.5 12.6 10 M 5 16.5 L 15 16.5"
        ),
        TravelPassStackItem(
            name: "Coastliner Rail",
            photo: "https://images.unsplash.com/photo-1516483638261-f4dbaf036963?auto=format&fit=crop&w=640&h=330&q=80",
            tint: Color(hex: 0x7e726b),
            glyph: "M 5.5 3.5 L 14.5 3.5 Q 16 3.5 16 5 L 16 12.5 Q 16 14 14.5 14 L 5.5 14 Q 4 14 4 12.5 L 4 5 Q 4 3.5 5.5 3.5 Z M 4 8.5 L 16 8.5 M 7 11.2 L 7.1 11.2 M 13 11.2 L 12.9 11.2 M 6.5 14 L 5 17 M 13.5 14 L 15 17"
        ),
    ]
}

/// A wallet of travel passes seen in perspective: wide photo cards overlap down a dusk panel, and
/// dragging scrolls them. Cards pushed past the top shrink, dim and blur as if they moved away,
/// new ones rise from under the bottom edge, and the gaps stretch with the speed of the finger.
/// Releasing snaps the stack to a card.
struct TravelPassStack: View {
    /// The passes, first on top. Four fit at full size; the rest wait under the bottom edge.
    var passes = TravelPassStackItem.samples
    /// Drags the stack up by two cards, pauses, drags it back down and starts over.
    var autoPlay = false
    /// The panel's gradient, top and bottom.
    var skyTop = Color(hex: 0x3e4063)
    var skyBottom = Color(hex: 0x7a7594)
    var cardColor = Color(hex: 0x3a3a40)
    var textColor = Color.white
    /// Called with the index of the card the stack snaps to when a drag ends.
    var onSelect: ((Int) -> Void)? = nil
    /// Called with the index of a tapped card.
    var onOpen: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var sim = Simulation()
    /// Keeps the timeline running while a touch moves the stack without autoPlay.
    @State private var awake = false
    @State private var releases = 0
    @State private var dragFrom: Double? = nil
    @State private var dragIndex = 0
    @State private var ticks = 0

    /// Per-frame state of the stack: `scroll` counts cards pushed past the top.
    private final class Simulation {
        var last: Date? = nil
        var clock = 0.0
        var looping = false
        /// Seconds the loop still waits after a touch.
        var hold = 0.0
        /// A real finger is on the stack.
        var dragging = false
        /// The loop's own finger is on the stack.
        var scripted = false
        var scroll = 0.0
        /// `scroll` at the previous frame, to measure the finger's speed.
        var seen = 0.0
        var velocity = 0.0
        var target = 0.0
        /// 0…`stretchMax`: how far the gaps are pulled open by the speed.
        var stretch = 0.0

        func advance(to date: Date, limit: Double, reduced: Bool) {
            guard let last else {
                self.last = date
                return
            }
            let dt = min(date.timeIntervalSince(last), 0.064)
            guard dt > 0 else { return }
            self.last = date
            if dragging {
                velocity += ((scroll - seen) / dt - velocity) * 0.5
            } else {
                if hold > 0 {
                    hold -= dt
                    // Time is up: the stack goes home and the loop starts over from its rest.
                    if hold <= 0, looping {
                        target = 0
                        clock = Metrics.loop - Metrics.resumeRest
                    }
                } else if looping {
                    clock += dt
                    if clock >= Metrics.loop {
                        clock -= Metrics.loop
                        scroll = 0
                        velocity = 0
                        target = 0
                    }
                    if let finger = scriptFinger(clock, travel: min(Metrics.autoCards, limit)) {
                        velocity = (finger - scroll) / dt
                        scroll = finger
                        scripted = true
                    } else if scripted {
                        scripted = false
                        target = snapTarget(scroll, velocity: velocity, limit: limit)
                    }
                }
                if !scripted {
                    if reduced {
                        scroll = target
                        velocity = 0
                    } else {
                        velocity += (Metrics.stiffness * (target - scroll) - Metrics.damping * velocity) / Metrics.mass * dt
                        scroll += velocity * dt
                    }
                }
            }
            seen = scroll
            let pull = reduced ? 0 : min(abs(velocity) * Metrics.stretchGain, Metrics.stretchMax)
            stretch += (pull - stretch) * (1 - exp(-dt * Metrics.stretchRate))
        }
    }

    private var auto: Bool { autoPlay && !reduceMotion }
    /// The furthest the stack scrolls: the last card then sits in the lowest full slot.
    private var limit: Double { Double(max(0, passes.count - Metrics.slots)) }

    var body: some View {
        TimelineView(.animation(paused: !(auto || awake))) { timeline in
            let _ = sim.advance(to: timeline.date, limit: limit, reduced: reduceMotion)
            let scroll = sim.scroll
            let stretch = sim.stretch

            ZStack(alignment: .top) {
                LinearGradient(colors: [skyTop, skyBottom], startPoint: .top, endPoint: .bottom)

                ForEach(Array(passes.enumerated()), id: \.offset) { index, pass in
                    let place = placement(Double(index) - scroll, stretch: stretch)
                    TravelPassStackCard(pass: pass, color: cardColor, textColor: textColor, parallax: place.parallax)
                        .compositingGroup()
                        .shadow(color: .black.opacity(Metrics.shadowOpacity), radius: Metrics.shadowRadius)
                        .blur(radius: place.blur)
                        .opacity(place.opacity)
                        .scaleEffect(place.scale, anchor: .top)
                        .offset(y: place.top)
                        .onTapGesture {
                            sim.hold = Metrics.userHold
                            onOpen?(index)
                        }
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel(Text(verbatim: pass.name))
                        .accessibilityAddTraits(.isButton)
                        .accessibilityHidden(place.opacity < 0.1 || place.top > Metrics.panelHeight)
                }

                // A little dusk haze over the bottom edge, where the next cards wait.
                LinearGradient(colors: [skyBottom.opacity(0), skyBottom.opacity(Metrics.hazeOpacity)], startPoint: .top, endPoint: .bottom)
                    .frame(height: Metrics.hazeHeight)
                    .frame(maxHeight: .infinity, alignment: .bottom)
                    .allowsHitTesting(false)
            }
            .frame(width: Metrics.panelWidth, height: Metrics.panelHeight)
            .clipShape(RoundedRectangle(cornerRadius: Metrics.panelRadius, style: .circular))
            .contentShape(RoundedRectangle(cornerRadius: Metrics.panelRadius, style: .circular))
            .gesture(drag)
            .accessibilityScrollAction { edge in
                if edge == .bottom { step(1) } else if edge == .top { step(-1) }
            }
        }
        .sensoryFeedback(.selection, trigger: ticks)
        .onAppear { sync() }
        .onChange(of: autoPlay) { sync() }
        .onChange(of: reduceMotion) { sync() }
        .task(id: releases) {
            guard releases > 0 else { return }
            // Long enough for the snap to settle; the timeline may sleep again after it.
            try? await Task.sleep(for: .seconds(Metrics.userHold))
            if !sim.dragging { awake = false }
        }
    }

    private var drag: some Gesture {
        DragGesture(minimumDistance: 6)
            .onChanged { value in
                if dragFrom == nil {
                    dragFrom = sim.scroll
                    dragIndex = nearest(sim.scroll)
                    sim.dragging = true
                    sim.scripted = false
                    sim.seen = sim.scroll
                    awake = true
                }
                sim.scroll = rubber((dragFrom ?? 0) - value.translation.height / Metrics.step)
                let index = nearest(sim.scroll)
                guard index != dragIndex else { return }
                dragIndex = index
                ticks += 1
            }
            .onEnded { _ in
                dragFrom = nil
                sim.dragging = false
                sim.target = snapTarget(sim.scroll, velocity: sim.velocity, limit: limit)
                sim.hold = Metrics.userHold
                releases += 1
                onSelect?(Int(sim.target))
            }
    }

    private func sync() {
        sim.looping = auto
        sim.hold = 0
        sim.clock = 0
        sim.scripted = false
        sim.target = 0
        if auto {
            sim.scroll = 0
            sim.velocity = 0
        }
    }

    /// Moves the stack by whole cards, for assistive scrolling.
    private func step(_ cards: Double) {
        sim.target = min(limit, max(0, sim.target + cards))
        sim.hold = Metrics.userHold
        awake = true
        releases += 1
        onSelect?(Int(sim.target))
    }

    private func nearest(_ scroll: Double) -> Int {
        Int(min(limit, max(0, scroll.rounded())))
    }

    /// Past either end the stack follows the finger at a third of its speed.
    private func rubber(_ scroll: Double) -> Double {
        if scroll < 0 { return scroll * Metrics.rubber }
        if scroll > limit { return limit + (scroll - limit) * Metrics.rubber }
        return scroll
    }
}

/// Where a card sits for `u` = its index minus the scroll: 0 is the top full-size slot, negative
/// values have been pushed past it and recede.
private func placement(_ u: Double, stretch: Double) -> (top: Double, scale: Double, opacity: Double, blur: Double, parallax: Double) {
    let r = min(max(-u, 0), 2)
    let top = Metrics.top + max(u, 0) * Metrics.step
        - (Metrics.recedeLift * r - Metrics.recedeEase * r * r)
        + Metrics.step * stretch * (max(u, -2) - 1)
    let opacity = r <= 1
        ? 1 - (1 - Metrics.recedeOpacity) * r
        : max(0, Metrics.recedeOpacity * (1 - (r - 1) / Metrics.recedeFade))
    let parallax = min(Metrics.parallaxRoom, max(-Metrics.parallaxRoom, (1.5 - u) * Metrics.parallaxStep))
    return (top, 1 - Metrics.recedeScale * r, opacity, Metrics.recedeBlur * r, parallax)
}

/// Where the loop's finger holds the stack at `t` seconds, or nil while it is lifted.
private func scriptFinger(_ t: Double, travel: Double) -> Double? {
    if t >= Metrics.dragUpAt, t < Metrics.dragUpAt + Metrics.dragTime {
        return travel * Metrics.dragShare * flick((t - Metrics.dragUpAt) / Metrics.dragTime)
    }
    if t >= Metrics.dragDownAt, t < Metrics.dragDownAt + Metrics.dragTime {
        return travel * (1 - Metrics.dragShare * flick((t - Metrics.dragDownAt) / Metrics.dragTime))
    }
    return nil
}

/// A finger's stroke, 0…1: it starts from rest, speeds up and is still moving when it lets go.
private func flick(_ p: Double) -> Double {
    (1 - cos(p * Metrics.flickAngle)) / (1 - cos(Metrics.flickAngle))
}

/// The card a release lands on: the nearest one to where the speed would carry the stack.
private func snapTarget(_ scroll: Double, velocity: Double, limit: Double) -> Double {
    min(limit, max(0, (scroll + velocity * Metrics.throwTime).rounded()))
}

/// One pass: the glyph and the name over a wide photo.
private struct TravelPassStackCard: View {
    let pass: TravelPassStackItem
    let color: Color
    let textColor: Color
    /// The photo's shift inside its window, in points.
    let parallax: Double

    var body: some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .circular).fill(color)
            RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .circular)
                .strokeBorder(Color.white.opacity(0.08), lineWidth: 1)

            travelPassGlyph(pass.glyph)
                .stroke(textColor, style: StrokeStyle(lineWidth: Metrics.glyphStroke, lineCap: .round, lineJoin: .round))
                .frame(width: Metrics.glyph, height: Metrics.glyph)
                .offset(x: Metrics.glyphLeft, y: (Metrics.header - Metrics.glyph) / 2)

            Text(pass.name)
                .font(.system(size: 15.5, weight: .medium))
                .tracking(-0.1)
                .foregroundStyle(textColor)
                .lineLimit(1)
                .frame(width: Metrics.cardWidth - Metrics.titleLeft - Metrics.photoInset, height: Metrics.header, alignment: .leading)
                .offset(x: Metrics.titleLeft)

            TravelPassStackPhoto(address: pass.photo, tint: pass.tint)
                .frame(width: Metrics.photoWidth, height: Metrics.photoHeight + Metrics.parallaxRoom * 2)
                .offset(y: parallax)
                .frame(width: Metrics.photoWidth, height: Metrics.photoHeight)
                .clipShape(RoundedRectangle(cornerRadius: Metrics.photoRadius, style: .circular))
                .offset(x: Metrics.photoInset, y: Metrics.header)
        }
        .frame(width: Metrics.cardWidth, height: Metrics.cardHeight, alignment: .topLeading)
    }
}

/// A remote photo that covers its frame: the tint shows until it arrives, then it fades in.
private struct TravelPassStackPhoto: View {
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

private func travelPassGlyph(_ data: String) -> Path {
    var path = Path()
    var command = ""
    var numbers: [Double] = []
    for token in data.split(separator: " ") {
        if let number = Double(token) {
            numbers.append(number)
        } else {
            command = String(token)
            numbers = []
        }
        switch (command, numbers.count) {
        case ("M", 2):
            path.move(to: CGPoint(x: numbers[0], y: numbers[1]))
            numbers = []
        case ("L", 2):
            path.addLine(to: CGPoint(x: numbers[0], y: numbers[1]))
            numbers = []
        case ("Q", 4):
            path.addQuadCurve(to: CGPoint(x: numbers[2], y: numbers[3]), control: CGPoint(x: numbers[0], y: numbers[1]))
            numbers = []
        case ("Z", 0):
            path.closeSubpath()
        default:
            break
        }
    }
    return path
}

private enum Metrics {
    static let panelWidth = 350.0
    static let panelHeight = 620.0
    static let panelRadius = 40.0

    static let cardWidth = 310.0
    static let cardHeight = 184.0
    static let cardRadius = 18.0
    /// Height of the row with the glyph and the name.
    static let header = 48.0
    static let glyph = 20.0
    static let glyphStroke = 1.6
    static let glyphLeft = 22.0
    static let titleLeft = 50.0
    static let photoInset = 24.0
    static let photoWidth = cardWidth - photoInset * 2
    static let photoHeight = 116.0
    static let photoRadius = 12.0
    static let shadowOpacity = 0.3
    static let shadowRadius = 16.0

    /// Top of the first full-size card, and the distance from one card's top to the next.
    static let top = 84.0
    static let step = 106.0
    /// Cards that fit at full size.
    static let slots = 4

    // A card pushed one step past the top: how far it still rises, and what is left of it.
    static let recedeLift = 62.0
    static let recedeEase = 12.0
    static let recedeScale = 0.19
    static let recedeOpacity = 0.7
    static let recedeBlur = 2.5
    /// Steps past the first over which a receding card fades out completely.
    static let recedeFade = 0.7

    /// The photo is taller than its window by this much on each side and slides inside it.
    static let parallaxRoom = 10.0
    static let parallaxStep = 6.0

    static let hazeHeight = 64.0
    static let hazeOpacity = 0.5

    // The snap after a release: a spring on the scroll position, in cards.
    static let mass = 1.0
    static let stiffness = 180.0
    static let damping = 20.0
    /// Seconds of speed a release throws the stack ahead by.
    static let throwTime = 0.15
    static let rubber = 0.35

    // The gaps open by this share of a step per card/second of speed, up to a limit.
    static let stretchGain = 0.03
    static let stretchMax = 0.1
    static let stretchRate = 10.0

    // autoPlay loop (seconds): a drag up by two cards, a pause, a drag back down, a rest.
    static let autoCards = 2.0
    static let dragUpAt = 0.8
    static let dragDownAt = 3.4
    static let dragTime = 0.9
    /// Share of the way the finger covers before it lets go; the throw does the rest.
    static let dragShare = 0.85
    static let flickAngle = 2.2
    static let loop = 6.2
    /// After a touch the loop waits this long, then goes home and rests this long before it restarts.
    static let userHold = 2.0
    static let resumeRest = 1.2
}

#Preview {
    ZStack {
        Color(hex: 0x1d1e30).ignoresSafeArea()
        TravelPassStack(autoPlay: true)
    }
}
