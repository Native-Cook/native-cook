import SwiftUI

/// "Complete Order" pill: on tap a parcel is loaded into a truck that drives off along a dashed
/// road, then the pill confirms with "Order Placed ✓" and returns to rest.
struct OrderTruckButton: View {
    var label = "Complete Order"
    var doneLabel = "Order Placed"
    /// Place an order by itself every few seconds. Tapping still works while idle.
    var autoPlay = false
    /// Called when the order is placed (on tap, before the truck sets off).
    var action: () -> Void = {}

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// When the current run started; `nil` while idle.
    @State private var started: Date?
    @State private var pressed = false

    var body: some View {
        Button {
            guard started == nil else { return }
            action()
            started = .now
        } label: {
            Text(label)
        }
        .buttonStyle(Style(label: label, doneLabel: doneLabel, started: started, still: reduceMotion, pressed: $pressed))
        .accessibilityLabel(label)
        .task(id: RunKey(started: started, auto: autoPlay && !reduceMotion && !pressed)) {
            if let started {
                let remaining = started.addingTimeInterval(Timeline.length(still: reduceMotion)).timeIntervalSinceNow
                try? await Task.sleep(for: .seconds(max(remaining, 0)))
                if !Task.isCancelled { self.started = nil }
            } else if autoPlay, !reduceMotion, !pressed {
                try? await Task.sleep(for: .seconds(Metrics.autoPauseSeconds))
                if !Task.isCancelled { started = .now }
            }
        }
    }

    private struct RunKey: Equatable {
        let started: Date?
        let auto: Bool
    }

    private struct Style: ButtonStyle {
        let label: String
        let doneLabel: String
        let started: Date?
        let still: Bool
        @Binding var pressed: Bool

        func makeBody(configuration: Configuration) -> some View {
            let isPressed = configuration.isPressed
            Surface(label: label, doneLabel: doneLabel, started: started, still: still)
                .contentShape(Capsule())
                .scaleEffect(isPressed ? 0.97 : 1)
                .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: isPressed)
                .sensoryFeedback(.impact(weight: .light), trigger: isPressed) { _, pressed in pressed }
                .onChange(of: isPressed) { _, isPressed in pressed = isPressed }
        }
    }
}

private enum Metrics {
    static let width = 212.0
    static let height = 56.0
    static let autoPauseSeconds = 1.3

    // The truck, seen from above and drawn with its cargo box's rear-left corner at the origin.
    static let boxW = 52.0
    static let boxH = 37.0
    static let cabX = boxW + 1
    static let cabW = 21.0
    static let truckW = cabX + cabW
    static let door = boxH / 2
    static let doorSwing = 115.0
    static let parcel = 19.0
    static let dash = 7.0
    static let dashGap = 5.5
    /// How fast the road runs under the truck, in points per second.
    static let roadSpeed = 48.0

    // Where the truck's rear sits along the pill.
    static let xOff = width + 8
    static let xRest = (width - truckW) / 2
    static let xAhead = 120.0
    static let xBack = 15.0
    static let parcelOff = -parcel - 3
    static let parcelRest = 16.0
}

/// The whole story is a function of one clock (seconds since the tap).
private enum Timeline {
    static let labelOut = 0.0...0.25
    static let enter = 0.15...1.15
    static let parcelIn = 0.2...0.55
    static let doorsOpen = 0.45...0.9
    static let load = 1.4...2.0
    static let doorsClose = 2.0...2.75
    static let ahead = 3.2...3.9
    static let lights = 3.2...3.5
    static let roadIn = 3.3...3.7
    static let drift = 4.2...6.1
    static let exit = 6.1...6.7
    static let roadOut = 6.45...6.85
    static let doneIn = 7.0...7.3
    static let check = 7.35...7.65
    static let doneOut = 9.0...9.25
    static let labelIn = 9.25...9.6
    static let total = 9.6
    /// Reduced motion skips the delivery: the label simply cross-fades to the confirmation and back.
    static let skipFrom = doneIn.lowerBound - 0.1

    static func length(still: Bool) -> Double {
        still ? labelOut.upperBound + total - skipFrom : total
    }

    static func clock(elapsed: Double, still: Bool) -> Double {
        guard still else { return min(max(elapsed, 0), total) }
        if elapsed < labelOut.upperBound { return max(elapsed, 0) }
        return min(skipFrom + elapsed - labelOut.upperBound, total)
    }
}

private func seg(_ t: Double, _ range: ClosedRange<Double>) -> Double {
    min(1, max(0, (t - range.lowerBound) / (range.upperBound - range.lowerBound)))
}
private func easeOut(_ u: Double) -> Double { 1 - (1 - u) * (1 - u) * (1 - u) }
private func easeIn(_ u: Double) -> Double { u * u * u }
private func easeInOut(_ u: Double) -> Double { u < 0.5 ? 4 * u * u * u : 1 - pow(-2 * u + 2, 3) / 2 }
/// Sets off gently, then spends most of the trip slowing down.
private func easeDrift(_ u: Double) -> Double {
    let s = u * u * (3 - 2 * u)
    return 1 - (1 - s) * (1 - s)
}

private func truckX(_ t: Double) -> Double {
    Metrics.xOff
        + (Metrics.xRest - Metrics.xOff) * easeOut(seg(t, Timeline.enter))
        + (Metrics.xAhead - Metrics.xRest) * easeInOut(seg(t, Timeline.ahead))
        + (Metrics.xBack - Metrics.xAhead) * easeDrift(seg(t, Timeline.drift))
        + (Metrics.xOff - Metrics.xBack) * easeIn(seg(t, Timeline.exit))
}

private let pillColor = Color(hex: 0x1c212b)

private struct Surface: View {
    let label: String
    let doneLabel: String
    let started: Date?
    let still: Bool

    var body: some View {
        TimelineView(.animation(paused: started == nil)) { timeline in
            // The clock ends where it began (idle label showing), so going back to idle is invisible.
            let t = started.map { Timeline.clock(elapsed: timeline.date.timeIntervalSince($0), still: still) } ?? 0

            ZStack {
                Canvas { context, size in
                    draw(context, size: size, t: t)
                }
                Text(label)
                    .opacity(1 - seg(t, Timeline.labelOut) + seg(t, Timeline.labelIn))
                    .accessibilityHidden(true)
                HStack(spacing: 6) {
                    Text(doneLabel)
                    CheckMark()
                        .trim(from: 0, to: easeOut(seg(t, Timeline.check)))
                        .stroke(Color(hex: 0x2fcf8e), style: StrokeStyle(lineWidth: 1.8, lineCap: .round, lineJoin: .round))
                        .frame(width: 16, height: 16)
                }
                .opacity(seg(t, Timeline.doneIn) - seg(t, Timeline.doneOut))
                .accessibilityHidden(true)
            }
            .font(.system(size: 15, weight: .medium))
            .lineLimit(1)
            .foregroundStyle(.white)
            .frame(width: Metrics.width, height: Metrics.height)
        }
    }

    private func draw(_ context: GraphicsContext, size: CGSize, t: Double) {
        let pill = capsule(CGRect(origin: .zero, size: size))
        context.fill(pill, with: .color(pillColor))
        guard !still, t > 0 else { return }

        var scene = context
        scene.clip(to: pill)
        let cy = size.height / 2

        // Road: dashes running right to left under the truck.
        let period = Metrics.dash + Metrics.dashGap
        let shift = (max(0, t - Timeline.roadIn.lowerBound) * Metrics.roadSpeed).truncatingRemainder(dividingBy: period)
        var road = scene
        road.opacity = seg(t, Timeline.roadIn) - seg(t, Timeline.roadOut)
        road.stroke(
            Path { path in
                path.move(to: CGPoint(x: -shift, y: cy))
                path.addLine(to: CGPoint(x: size.width + period, y: cy))
            },
            with: .color(.white.opacity(0.92)),
            style: StrokeStyle(lineWidth: 1.4, dash: [Metrics.dash, Metrics.dashGap])
        )

        // Once the parcel is under the cargo box it stays hidden, or the truck would drive off and leave it behind.
        if t > Timeline.parcelIn.lowerBound, t < Timeline.load.upperBound {
            let x = Metrics.parcelOff
                + (Metrics.parcelRest - Metrics.parcelOff) * easeOut(seg(t, Timeline.parcelIn))
                + (Metrics.xRest + 8 - Metrics.parcelRest) * easeIn(seg(t, Timeline.load))
            let box = CGRect(x: x, y: cy - Metrics.parcel / 2, width: Metrics.parcel, height: Metrics.parcel)
            scene.fill(
                Path(roundedRect: box, cornerRadius: 2.5),
                with: .linearGradient(
                    Gradient(colors: [Color(hex: 0xedd09a), Color(hex: 0xdcb777)]),
                    startPoint: CGPoint(x: 0, y: box.minY), endPoint: CGPoint(x: 0, y: box.maxY)
                )
            )
            scene.stroke(
                Path { path in
                    path.move(to: CGPoint(x: box.minX, y: cy))
                    path.addLine(to: CGPoint(x: box.maxX, y: cy))
                },
                with: .color(Color(hex: 0xc9a05c)), lineWidth: 1.2
            )
        }

        var truck = scene
        truck.translateBy(x: truckX(t), y: cy)
        drawTruck(truck, t: t)
    }

    private func drawTruck(_ context: GraphicsContext, t: Double) {
        let top = -Metrics.boxH / 2
        let bottom = Metrics.boxH / 2
        let cabX = Metrics.cabX
        let front = Metrics.truckW

        // Headlight beams, behind the cab.
        var beams = context
        beams.opacity = seg(t, Timeline.lights)
        let beam = GraphicsContext.Shading.linearGradient(
            Gradient(colors: [.rgba(244, 214, 84, 0.8), .rgba(214, 200, 130, 0.16)]),
            startPoint: CGPoint(x: front, y: 0), endPoint: CGPoint(x: front + 41, y: 0)
        )
        for side in [-1.0, 1.0] {
            // -1 is the top light, +1 the bottom one: the same beam mirrored about the truck's axis.
            let edge = side * bottom
            beams.fill(
                Path { path in
                    path.move(to: CGPoint(x: front, y: edge - side * 5))
                    path.addLine(to: CGPoint(x: front + 41, y: edge + side * 9))
                    path.addLine(to: CGPoint(x: front + 41, y: edge - side * 15))
                    path.addLine(to: CGPoint(x: front, y: edge - side * 9))
                    path.closeSubpath()
                },
                with: beam
            )
        }

        // Cab: blue shell, lighter roof slab, dark windscreen with two reflections.
        context.fill(
            Path { path in
                path.move(to: CGPoint(x: cabX, y: top + 0.5))
                path.addLine(to: CGPoint(x: cabX + 11, y: top + 0.5))
                path.addQuadCurve(to: CGPoint(x: front, y: top + 11), control: CGPoint(x: front, y: top + 0.5))
                path.addLine(to: CGPoint(x: front, y: bottom - 11))
                path.addQuadCurve(to: CGPoint(x: cabX + 11, y: bottom - 0.5), control: CGPoint(x: front, y: bottom - 0.5))
                path.addLine(to: CGPoint(x: cabX, y: bottom - 0.5))
                path.closeSubpath()
            },
            with: .color(Color(hex: 0x2b5df5))
        )
        context.fill(
            Path { path in
                path.move(to: CGPoint(x: cabX + 6.5, y: top + 3.5))
                path.addLine(to: CGPoint(x: cabX + 10.5, y: top + 3.5))
                path.addQuadCurve(to: CGPoint(x: front - 3.5, y: top + 12), control: CGPoint(x: front - 3.5, y: top + 4))
                path.addLine(to: CGPoint(x: front - 3.5, y: bottom - 12))
                path.addQuadCurve(to: CGPoint(x: cabX + 10.5, y: bottom - 3.5), control: CGPoint(x: front - 3.5, y: bottom - 4))
                path.addLine(to: CGPoint(x: cabX + 6.5, y: bottom - 3.5))
                path.closeSubpath()
            },
            with: .color(pillColor)
        )
        context.stroke(
            Path { path in
                path.move(to: CGPoint(x: cabX + 8, y: -3))
                path.addLine(to: CGPoint(x: cabX + 15.5, y: -9))
                path.move(to: CGPoint(x: cabX + 8, y: 1.5))
                path.addLine(to: CGPoint(x: cabX + 15.5, y: -4.5))
            },
            with: .color(.white.opacity(0.2)), lineWidth: 1.3
        )
        context.fill(
            Path(roundedRect: CGRect(x: cabX, y: top + 0.5, width: 6.5, height: Metrics.boxH - 1), cornerRadius: 1.2),
            with: .color(Color(hex: 0x7096f8))
        )
        let lamp = Color(hex: 0xf2d24e)
        context.fill(Path(roundedRect: CGRect(x: front - 1.2, y: top + 3, width: 2.6, height: 7), cornerRadius: 0.8), with: .color(lamp))
        context.fill(Path(roundedRect: CGRect(x: front - 1.2, y: bottom - 10, width: 2.6, height: 7), cornerRadius: 0.8), with: .color(lamp))

        context.fill(
            Path(roundedRect: CGRect(x: 0, y: top, width: Metrics.boxW, height: Metrics.boxH), cornerRadius: 2.5),
            with: .linearGradient(
                Gradient(colors: [Color(hex: 0xf7f9fd), Color(hex: 0xcdd6ea)]),
                startPoint: CGPoint(x: 0, y: top), endPoint: CGPoint(x: 0, y: bottom)
            )
        )

        // Rear doors, hinged on the box's back corners. Shut, they are simply its rear edge.
        let swing = Metrics.doorSwing * .pi / 180
            * (easeOut(seg(t, Timeline.doorsOpen)) - easeInOut(seg(t, Timeline.doorsClose)))
        var doors = context
        doors.opacity = min(1, swing * 12)
        for side in [-1.0, 1.0] {
            var door = doors
            door.translateBy(x: 0, y: side * bottom)
            door.rotate(by: .radians(-side * swing))
            door.stroke(
                Path { path in
                    path.move(to: .zero)
                    path.addLine(to: CGPoint(x: 0, y: -side * Metrics.door))
                },
                with: .color(.white), style: StrokeStyle(lineWidth: 1.3, lineCap: .round)
            )
        }
    }
}

private struct CheckMark: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: 2.5, y: 8.5))
        path.addLine(to: CGPoint(x: 6.2, y: 12))
        path.addLine(to: CGPoint(x: 13.5, y: 4.2))
        return path
    }
}

#Preview {
    ZStack {
        Color(hex: 0xe1ebf7).ignoresSafeArea()
        OrderTruckButton(autoPlay: true)
    }
}
