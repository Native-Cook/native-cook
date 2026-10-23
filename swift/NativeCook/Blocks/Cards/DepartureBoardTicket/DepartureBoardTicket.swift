import SwiftUI

struct DepartureBoardTicketField {
    var label: String
    var value: String
}

struct DepartureBoardTicketAirport {
    /// Drawn in board dots; letters A to Z.
    var code: String
    var name: String
    var city: String
}

/// A tall boarding pass: a black top that runs into a drifting aurora, two airport codes drawn in
/// square dots like a departure board, and a pale stub under a perforated line. The codes and the
/// five fields flap through other glyphs before they land, on appear and on a tap; dragging the stub
/// down tears it off along the perforation, and letting go snaps it back.
struct DepartureBoardTicket: View {
    /// Flaps the board, tears the stub off and lets it snap back, in a loop.
    var autoPlay = false
    var fields = [
        DepartureBoardTicketField(label: "GATE", value: "42"),
        DepartureBoardTicketField(label: "SEAT", value: "11A"),
        DepartureBoardTicketField(label: "BOARDING", value: "22:10"),
        DepartureBoardTicketField(label: "DEPARTURE", value: "22:55"),
        DepartureBoardTicketField(label: "DATE", value: "4 Sep 26"),
    ]
    var origin = DepartureBoardTicketAirport(code: "HKG", name: "Hong Kong International Airport", city: "HONG KONG")
    var destination = DepartureBoardTicketAirport(code: "LHR", name: "London Heathrow Airport", city: "LONDON, UNITED KINGDOM")
    var airline = "NORTHWIND AIR"
    var flight = "NW 29"
    var cabin = "ECONOMY"
    /// The date as the stub spells it.
    var date = "4 Sep 2026"
    /// Called when the ticket is tapped.
    var onPress: (() -> Void)? = nil
    /// Called when a drag tears the stub off.
    var onTear: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var sim = Simulation()
    @State private var taps = 0
    @State private var tears = 0
    @State private var landings = 0

    /// Per-frame state: the loop's clock, when the board last flapped, and the stub chasing its
    /// target pose on a spring.
    private final class Simulation {
        var last: Date? = nil
        var time = 0.0
        var clock = 0.0
        var looping = false
        var touching = false
        /// Seconds the loop still waits after a touch.
        var hold = 0.0
        var flapAt = -10.0
        /// How far the stub is pulled down, in points, by a finger or by the loop.
        var pull = 0.0
        var detached = false
        var drop = 0.0
        var dropSpeed = 0.0
        var angle = 0.0
        var spin = 0.0

        func advance(to date: Date, calm: Bool) {
            guard let last else {
                self.last = date
                return
            }
            let dt = min(date.timeIntervalSince(last), 0.064)
            guard dt > 0 else { return }
            self.last = date
            let now = time + dt
            if touching {
                hold = Metrics.userHold
            } else if hold > 0 {
                hold -= dt
                // Time is up: the loop starts again from rest.
                if hold <= 0, looping { clock = 0 }
            } else if looping {
                var t = clock + dt
                if t >= Metrics.loop { t -= Metrics.loop }
                if clock < Metrics.flapAt, t >= Metrics.flapAt { flapAt = now }
                pull = scriptPull(t)
                detached = pull >= Metrics.tearAt
                clock = t
            }

            // Attached, the stub swings on its top left corner; torn off, it hangs below the ticket.
            let dropTarget = detached ? Metrics.drop + (pull - Metrics.tearAt) * Metrics.follow : 0
            let angleTarget = detached ? Metrics.tilt : Metrics.swing * easeInOut(min(1, pull / Metrics.tearAt))
            if calm {
                drop = dropTarget
                angle = angleTarget
            } else {
                dropSpeed += (Metrics.stiffness * (dropTarget - drop) - Metrics.damping * dropSpeed) * dt
                drop += dropSpeed * dt
                spin += (Metrics.stiffness * (angleTarget - angle) - Metrics.damping * spin) * dt
                angle += spin * dt
            }
            time = now
        }
    }

    private var auto: Bool { autoPlay && !reduceMotion }

    var body: some View {
        TimelineView(.animation) { timeline in
            let _ = sim.advance(to: timeline.date, calm: reduceMotion)
            let sinceFlap = sim.time - sim.flapAt
            // Under reduced motion the board never flaps: every glyph is the final one.
            let step = reduceMotion ? Metrics.flapSteps : Int((sinceFlap / Metrics.flapStep).rounded(.down))
            let turn = reduceMotion ? 0 : sim.time / Metrics.loop * tau
            let lift = min(1, max(0, sim.drop / Metrics.drop))

            ZStack(alignment: .topLeading) {
                ticketPieces.body
                    .fill(Color.rgba(24, 32, 72, 0.2))
                    .blur(radius: 18)
                    .offset(y: 14)
                stubPose(
                    ticketPieces.stub
                        .fill(Color.rgba(24, 32, 72, 0.2 + 0.14 * lift))
                        .blur(radius: 18)
                        .offset(y: 14 + 8 * lift)
                )

                top(step: step, turn: turn, grid: reduceMotion ? 0 : gridGlow(sinceFlap))
                    .contentShape(Rectangle())
                    .onTapGesture(perform: tap)
                stubPose(stub)
                    .gesture(drag)
            }
            .frame(width: Metrics.width, height: Metrics.height, alignment: .topLeading)
        }
        .sensoryFeedback(.impact(weight: .light), trigger: taps)
        .sensoryFeedback(.impact(weight: .medium), trigger: tears)
        .sensoryFeedback(.impact(weight: .light), trigger: landings)
        .onAppear { sync() }
        .onChange(of: auto) { sync() }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: summary))
    }

    private var summary: String {
        let details = fields.map { "\($0.label) \($0.value)" }.joined(separator: ", ")
        return "\(airline) \(flight), \(origin.name) to \(destination.name), \(cabin), \(date). \(details)"
    }

    private func sync() {
        sim.looping = auto
        sim.clock = 0
        sim.hold = 0
        // Without the loop the board flaps once as the ticket appears.
        if !auto, !reduceMotion, sim.time == 0 { sim.flapAt = Metrics.appearFlapAt }
    }

    /// A touch takes the stub out of the loop's hands.
    private func takeOver() {
        sim.hold = Metrics.userHold
        if !sim.touching {
            sim.pull = 0
            sim.detached = false
        }
    }

    private func tap() {
        takeOver()
        sim.flapAt = sim.time
        taps += 1
        onPress?()
    }

    private var drag: some Gesture {
        DragGesture(minimumDistance: 4, coordinateSpace: .global)
            .onChanged { value in
                takeOver()
                sim.touching = true
                sim.pull = max(0, value.translation.height)
                if !sim.detached, sim.pull >= Metrics.tearAt {
                    sim.detached = true
                    tears += 1
                    onTear?()
                }
            }
            .onEnded { _ in
                if sim.detached { landings += 1 }
                sim.touching = false
                sim.pull = 0
                sim.detached = false
            }
    }

    private func stubPose<Content: View>(_ view: Content) -> some View {
        view
            .frame(width: Metrics.width, height: Metrics.height - Metrics.tearY, alignment: .topLeading)
            .rotationEffect(.degrees(sim.angle), anchor: .topLeading)
            .offset(y: Metrics.tearY + sim.drop)
    }

    /// The ticket above the perforation: the aurora, the two codes, and the text over them.
    private func top(step: Int, turn: Double, grid: Double) -> some View {
        ZStack(alignment: .topLeading) {
            Canvas { context, _ in
                context.clip(to: ticketPieces.body)
                context.fill(ticketPieces.body, with: .color(.black))
                for blob in auroraBlobs {
                    let center = CGPoint(
                        x: blob.x + blob.driftX * sin(turn + blob.phase),
                        y: blob.y + blob.driftY * cos(turn + blob.phase)
                    )
                    context.fill(
                        Path(ellipseIn: CGRect(x: center.x - blob.radius, y: center.y - blob.radius, width: blob.radius * 2, height: blob.radius * 2)),
                        with: .radialGradient(
                            Gradient(stops: [
                                .init(color: blob.color, location: 0),
                                .init(color: blob.color.opacity(0.5), location: 0.5),
                                .init(color: blob.color.opacity(0), location: 1),
                            ]),
                            center: center, startRadius: 0, endRadius: blob.radius
                        )
                    )
                }
                // The colours wash out into the stub's pale blue just above the perforation.
                context.fill(
                    Path(CGRect(x: 0, y: Metrics.paleFrom, width: Metrics.width, height: Metrics.tearY - Metrics.paleFrom)),
                    with: .linearGradient(
                        Gradient(stops: [
                            .init(color: Metrics.pale.opacity(0), location: 0),
                            .init(color: Metrics.pale.opacity(0.3), location: 0.5),
                            .init(color: Metrics.pale, location: 1),
                        ]),
                        startPoint: CGPoint(x: 0, y: Metrics.paleFrom), endPoint: CGPoint(x: 0, y: Metrics.tearY)
                    )
                )
                drawCode(context, origin.code, first: 0, top: Metrics.originTop, step: step, grid: grid)
                drawCode(context, destination.code, first: origin.code.count, top: Metrics.destinationTop, step: step, grid: grid)
            }
            .frame(width: Metrics.width, height: Metrics.tearY)

            fieldRow(step: step)
                .offset(x: Metrics.fieldInset, y: 14)

            airportLines(origin, top: Metrics.originTop)
            airportLines(destination, top: Metrics.destinationTop)

            VStack(alignment: .leading, spacing: 5) {
                Text(airline)
                    .font(.system(size: 9.5, weight: .semibold))
                    .tracking(2.2)
                    .foregroundStyle(.white.opacity(0.78))
                Text(flight)
                    .font(.system(size: 12.5, weight: .bold))
                    .foregroundStyle(.white)
            }
            .lineLimit(1)
            .fixedSize()
            .offset(x: Metrics.pad, y: 427)

            Text(cabin)
                .font(.system(size: 7.5, weight: .bold))
                .tracking(1.2)
                .foregroundStyle(.white)
                .lineLimit(1)
                .fixedSize()
                .padding(.horizontal, 10)
                .frame(height: 20)
                .background(RoundedRectangle(cornerRadius: 5, style: .continuous).fill(.white.opacity(0.24)))
                .frame(width: Metrics.width - Metrics.pad * 2, alignment: .trailing)
                .offset(x: Metrics.pad, y: 427)
        }
        .frame(width: Metrics.width, height: Metrics.tearY, alignment: .topLeading)
    }

    private func fieldRow(step: Int) -> some View {
        let weights = fields.map { Double(4 + max($0.label.count, $0.value.count)) }
        let unit = (Metrics.width - Metrics.fieldInset * 2) / max(1, weights.reduce(0, +))
        return HStack(spacing: 0) {
            ForEach(Array(fields.enumerated()), id: \.offset) { index, field in
                VStack(spacing: 5) {
                    Text(field.label)
                        .font(.system(size: 6.5, weight: .semibold))
                        .tracking(0.9)
                        .foregroundStyle(.white.opacity(0.9))
                    Text(flapText(field.value, field: index, step: step))
                        .font(.system(size: 12.5, weight: .bold))
                        .monospacedDigit()
                        .foregroundStyle(.white)
                }
                .lineLimit(1)
                .fixedSize()
                .frame(width: weights[index] * unit, height: 34)
                .overlay(alignment: .leading) {
                    if index > 0 {
                        Rectangle().fill(.white.opacity(0.28)).frame(width: 1, height: 26)
                    }
                }
            }
        }
    }

    private func airportLines(_ airport: DepartureBoardTicketAirport, top: Double) -> some View {
        VStack(spacing: 7) {
            Text(airport.name)
                .font(.system(size: 11.5, weight: .semibold))
                .foregroundStyle(.white)
            Text(airport.city)
                .font(.system(size: 7.5, weight: .medium))
                .tracking(1.2)
                .foregroundStyle(.white.opacity(0.45))
        }
        .lineLimit(1)
        .frame(width: Metrics.width - Metrics.pad * 2)
        .offset(x: Metrics.pad, y: top + Metrics.cell * 7 + 24)
    }

    /// One airport code in board dots. `first` is the position of its first letter among all the
    /// letters on the ticket, which decides when each one starts to flap.
    private func drawCode(_ context: GraphicsContext, _ code: String, first: Int, top: Double, step: Int, grid: Double) {
        let letters = Array(code.uppercased())
        let pitch = Metrics.cell * 5 + Metrics.letterGap
        let left = (Metrics.width - (pitch * Double(letters.count) - Metrics.letterGap)) / 2
        var lit = Path()
        var dark = Path()
        for (n, letter) in letters.enumerated() {
            let rows = flapGlyph(letter, position: first + n, step: step)
            for row in 0..<7 {
                for column in 0..<5 {
                    let dot = CGRect(
                        x: left + pitch * Double(n) + Metrics.cell * Double(column) + (Metrics.cell - Metrics.dot) / 2,
                        y: top + Metrics.cell * Double(row) + (Metrics.cell - Metrics.dot) / 2,
                        width: Metrics.dot, height: Metrics.dot
                    )
                    let on = rows[row] & (16 >> column) != 0
                    if on {
                        lit.addRoundedRect(in: dot, cornerSize: CGSize(width: 0.9, height: 0.9))
                    } else {
                        dark.addRoundedRect(in: dot, cornerSize: CGSize(width: 0.9, height: 0.9))
                    }
                }
            }
        }
        // The unlit dots of the board show faintly while it flaps.
        context.fill(dark, with: .color(.white.opacity(0.1 * grid)))
        context.fill(lit, with: .color(.white))
    }

    /// The part under the perforation: the route in small dots, the date and a barcode.
    private var stub: some View {
        ZStack(alignment: .topLeading) {
            Canvas { context, size in
                context.clip(to: ticketPieces.stub.offsetBy(dx: 0, dy: -Metrics.tearY))
                context.fill(
                    Path(CGRect(origin: .zero, size: size)),
                    with: .linearGradient(
                        Gradient(colors: [Metrics.pale, Color(hex: 0xa9c8f7)]),
                        startPoint: .zero, endPoint: CGPoint(x: 0, y: size.height)
                    )
                )
                // A last trace of the aurora in the bottom left corner.
                let corner = CGPoint(x: 0, y: size.height)
                context.fill(
                    Path(ellipseIn: CGRect(x: -110, y: size.height - 110, width: 220, height: 220)),
                    with: .radialGradient(
                        Gradient(colors: [Color(hex: 0x4a5cc4, opacity: 0.5), Color(hex: 0x4a5cc4, opacity: 0)]),
                        center: corner, startRadius: 0, endRadius: 110
                    )
                )

                var x = Metrics.stubPad
                let ink = GraphicsContext.Shading.color(Metrics.stubInk)
                x = drawSmallCode(context, origin.code, x: x, with: ink)
                var arrow = Path()
                arrow.move(to: CGPoint(x: x + 7, y: 27.25))
                arrow.addLine(to: CGPoint(x: x + 19, y: 27.25))
                arrow.move(to: CGPoint(x: x + 15.5, y: 24))
                arrow.addLine(to: CGPoint(x: x + 19, y: 27.25))
                arrow.addLine(to: CGPoint(x: x + 15.5, y: 30.5))
                context.stroke(arrow, with: ink, lineWidth: 1.1)
                _ = drawSmallCode(context, destination.code, x: x + 26, with: ink)

                var bars = Path()
                var barX = Metrics.width - Metrics.stubPad - Metrics.barcodeWidth
                let unit = Metrics.barcodeWidth / Double(barcode.reduce(0, +))
                for (n, width) in barcode.enumerated() {
                    if n % 2 == 0 {
                        bars.addRect(CGRect(x: barX, y: 22, width: Double(width) * unit, height: 40))
                    }
                    barX += Double(width) * unit
                }
                context.fill(bars, with: ink)
            }

            VStack(alignment: .leading, spacing: 8) {
                Text(date)
                    .font(.system(size: 12, weight: .bold))
                Text(cabin)
                    .font(.system(size: 6.5, weight: .semibold))
                    .tracking(1.6)
            }
            .foregroundStyle(Metrics.stubMute)
            .lineLimit(1)
            .fixedSize()
            .offset(x: Metrics.stubPad, y: 43)
        }
        .frame(width: Metrics.width, height: Metrics.height - Metrics.tearY, alignment: .topLeading)
        .contentShape(Rectangle())
    }

    /// A code in the small dots of the stub; returns the x where it ends.
    private func drawSmallCode(_ context: GraphicsContext, _ code: String, x: Double, with ink: GraphicsContext.Shading) -> Double {
        var path = Path()
        var left = x
        for letter in code.uppercased() {
            let rows = glyphRows(letter)
            for row in 0..<7 {
                for column in 0..<5 where rows[row] & (16 >> column) != 0 {
                    path.addRect(CGRect(
                        x: left + Metrics.smallCell * Double(column), y: 22 + Metrics.smallCell * Double(row),
                        width: Metrics.smallDot, height: Metrics.smallDot
                    ))
                }
            }
            left += Metrics.smallCell * 5 + 2.6
        }
        context.fill(path, with: ink)
        return left - 2.6
    }
}

private enum Metrics {
    static let width = 328.0
    static let height = 574.0
    static let radius = 24.0
    /// Where the perforation runs.
    static let tearY = 480.0
    /// Radius of the half circles cut out of both sides, and of the holes of the perforation.
    static let notch = 13.0
    static let hole = 3.0
    static let holes = 28
    static let holeInset = 26.0

    static let pad = 30.0
    static let stubPad = 22.0
    static let fieldInset = 14.0

    // The board: a 5 x 7 grid of square dots per letter.
    static let cell = 8.0
    static let dot = 6.6
    static let letterGap = 9.0
    static let originTop = 104.0
    static let destinationTop = 299.0
    static let smallCell = 1.55
    static let smallDot = 1.15
    static let barcodeWidth = 92.0

    static let pale = Color(hex: 0xb9d6ff)
    /// The aurora starts to wash out into the pale blue here.
    static let paleFrom = 440.0
    static let stubInk = Color(hex: 0x1f2f5c)
    static let stubMute = Color(hex: 0x6c85b0)

    // A flap: every `flapStep` seconds a letter shows another glyph, `flaps` times, and each letter
    // (then each field) starts two steps after the one before it.
    static let flapStep = 0.075
    static let flaps = 8
    static let flapStagger = 2
    static let fieldFlaps = 6
    /// Steps until the last of six letters has landed.
    static let flapSteps = 18
    static let gridIn = 0.12
    static let gridOut = 0.45

    // The stub. It tears off once it is pulled `tearAt` points down.
    static let tearAt = 46.0
    /// Degrees the stub swings on its top left corner before it tears off.
    static let swing = 6.5
    /// Torn off, it hangs this far below the ticket, at this angle, and follows the pull by `follow`.
    static let drop = 20.0
    static let tilt = 2.5
    static let follow = 0.5
    /// The spring the stub's drop and angle chase their targets on (mass 1).
    static let stiffness = 240.0
    static let damping = 19.0

    // autoPlay loop (seconds): the board flaps, a finger pulls the stub down and off, then lets go.
    static let flapAt = 0.8
    static let tearStart = 2.5
    static let pullTime = 1.0
    static let pullMax = 84.0
    static let releaseAt = 4.7
    static let loop = 7.0
    /// After a touch the loop waits this long before it starts again from rest.
    static let userHold = 2.0
    /// Without the loop the board flaps once, this long after the ticket appears.
    static let appearFlapAt = 0.3
}

private struct AuroraBlob {
    let color: Color
    let x: Double
    let y: Double
    let radius: Double
    let driftX: Double
    let driftY: Double
    let phase: Double
}

// Three lights that circle slowly, once per loop: violet on the right, royal blue in the middle, cyan on the left.
private let auroraBlobs = [
    AuroraBlob(color: Color(hex: 0x5a35c8), x: 300, y: 392, radius: 150, driftX: 14, driftY: -12, phase: 2.2),
    AuroraBlob(color: Color(hex: 0x1b2fd0), x: 170, y: 424, radius: 176, driftX: -20, driftY: 10, phase: 1),
    AuroraBlob(color: Color(hex: 0x19c8f0), x: 14, y: 462, radius: 150, driftX: 16, driftY: 9, phase: 0),
]

// Bars and gaps of the barcode, in turn, in units.
private let barcode = [2, 1, 1, 3, 1, 2, 1, 1, 2, 3, 1, 1, 2, 1, 3, 1, 1, 2, 2, 1, 1, 3, 1, 2, 1, 1, 2, 1, 3, 1, 2, 1, 1, 2, 1]

/// The ticket's outline cut in two along the perforation: a rounded rectangle with a half circle
/// taken out of each side and a row of holes between them.
private let ticketPieces: (body: Path, stub: Path) = {
    let outline = Path(
        roundedRect: CGRect(x: 0, y: 0, width: Metrics.width, height: Metrics.height),
        cornerRadius: Metrics.radius, style: .circular
    )
    var holes = Path()
    func punch(_ x: Double, _ radius: Double) {
        holes.addEllipse(in: CGRect(x: x - radius, y: Metrics.tearY - radius, width: radius * 2, height: radius * 2))
    }
    punch(0, Metrics.notch)
    punch(Metrics.width, Metrics.notch)
    let pitch = (Metrics.width - Metrics.holeInset * 2) / Double(Metrics.holes - 1)
    for n in 0..<Metrics.holes { punch(Metrics.holeInset + pitch * Double(n), Metrics.hole) }
    let upper = Path(CGRect(x: 0, y: 0, width: Metrics.width, height: Metrics.tearY))
    let lower = Path(CGRect(x: 0, y: Metrics.tearY, width: Metrics.width, height: Metrics.height - Metrics.tearY))
    return (outline.intersection(upper).subtracting(holes), outline.intersection(lower).subtracting(holes))
}()

private func easeInOut(_ t: Double) -> Double { t * t * (3 - 2 * t) }

/// How far the loop's finger has pulled the stub down at `t` seconds.
private func scriptPull(_ t: Double) -> Double {
    guard t >= Metrics.tearStart, t < Metrics.releaseAt else { return 0 }
    return Metrics.pullMax * easeInOut(min(1, (t - Metrics.tearStart) / Metrics.pullTime))
}

/// How visible the unlit dots are `elapsed` seconds into a flap.
private func gridGlow(_ elapsed: Double) -> Double {
    let rise = min(1, max(0, elapsed / Metrics.gridIn))
    let fall = min(1, max(0, (elapsed - Double(Metrics.flapSteps) * Metrics.flapStep) / Metrics.gridOut))
    return rise * (1 - fall)
}

// 5 x 7 dot letters A to Z, one number per row with the left dot as the high bit.
private let glyphs: [[UInt8]] = [
    [14, 17, 17, 31, 17, 17, 17], [30, 17, 17, 30, 17, 17, 30], [14, 17, 16, 16, 16, 17, 14], [30, 17, 17, 17, 17, 17, 30],
    [31, 16, 16, 30, 16, 16, 31], [31, 16, 16, 30, 16, 16, 16], [14, 17, 16, 23, 17, 17, 14], [17, 17, 17, 31, 17, 17, 17],
    [14, 4, 4, 4, 4, 4, 14], [7, 2, 2, 2, 2, 18, 12], [17, 18, 20, 24, 20, 18, 17], [16, 16, 16, 16, 16, 16, 31],
    [17, 27, 21, 21, 17, 17, 17], [17, 25, 21, 19, 17, 17, 17], [14, 17, 17, 17, 17, 17, 14], [30, 17, 17, 30, 16, 16, 16],
    [14, 17, 17, 17, 21, 18, 13], [30, 17, 17, 30, 20, 18, 17], [15, 16, 16, 14, 1, 1, 30], [31, 4, 4, 4, 4, 4, 4],
    [17, 17, 17, 17, 17, 17, 14], [17, 17, 17, 17, 17, 10, 4], [17, 17, 17, 21, 21, 27, 17], [17, 17, 10, 4, 10, 17, 17],
    [17, 17, 10, 4, 4, 4, 4], [31, 1, 2, 4, 8, 16, 31],
]
private let blankGlyph: [UInt8] = [0, 0, 0, 0, 0, 0, 0]

// How far a flapping letter or digit is from the one it lands on, step after step. Fixed, so every
// flap shows the same glyphs.
private let letterShifts = [7, 19, 3, 14, 23, 10, 5, 17, 12, 21]
private let digitShifts = [3, 7, 1, 8, 5, 9, 2, 6]

private func letterIndex(_ letter: Character) -> Int? {
    guard let value = letter.asciiValue, value >= 65, value <= 90 else { return nil }
    return Int(value) - 65
}

private func glyphRows(_ letter: Character) -> [UInt8] {
    letterIndex(letter).map { glyphs[$0] } ?? blankGlyph
}

/// The glyph the board shows for `letter`, the `position`-th letter on the ticket, `step` steps into a flap.
private func flapGlyph(_ letter: Character, position: Int, step: Int) -> [UInt8] {
    guard let final = letterIndex(letter) else { return blankGlyph }
    let local = step - position * Metrics.flapStagger
    guard local >= 0, local < Metrics.flaps else { return glyphs[final] }
    return glyphs[(final + letterShifts[(position + local) % letterShifts.count]) % 26]
}

/// A field's value `step` steps into a flap: its digits and letters show others until the field lands.
private func flapText(_ value: String, field: Int, step: Int) -> String {
    let local = step - (field + 1) * Metrics.flapStagger
    guard local >= 0, local < Metrics.fieldFlaps else { return value }
    var out = ""
    for (n, character) in value.unicodeScalars.enumerated() {
        let code = character.value
        if code >= 48, code <= 57 {
            out.unicodeScalars.append(UnicodeScalar(48 + (code - 48 + UInt32(digitShifts[(n + local) % digitShifts.count])) % 10)!)
        } else if code >= 65, code <= 90 {
            out.unicodeScalars.append(UnicodeScalar(65 + (code - 65 + UInt32(letterShifts[(n + local) % letterShifts.count])) % 26)!)
        } else if code >= 97, code <= 122 {
            out.unicodeScalars.append(UnicodeScalar(97 + (code - 97 + UInt32(letterShifts[(n + local) % letterShifts.count])) % 26)!)
        } else {
            out.unicodeScalars.append(character)
        }
    }
    return out
}

#Preview {
    ZStack {
        Color(hex: 0xf3f3f5).ignoresSafeArea()
        DepartureBoardTicket(autoPlay: true)
    }
}
