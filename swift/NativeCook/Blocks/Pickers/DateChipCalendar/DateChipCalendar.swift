import SwiftUI

/// Harbor, a stay-booking form: a date chip grows into an inline month grid in place, pushing the
/// form below by transform. Day cells rise in a diagonal stagger, the stay is one continuous rounded
/// range through the weeks, the chip's text rolls to the dates and the grid folds back into the chip.
/// In `autoPlay` every frame is a pure function of the 10 s loop time; a finger takes over at once
/// and the loop resumes 2 s after the last touch.
struct DateChipCalendar: View {
    var autoPlay = true
    var onReserve: (Int, Int) -> Void = { _, _ in }

    static let loop: Double = DateChipCalendarMotion.loop

    @Environment(\.accessibilityReduceMotion) private var reducedMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var model = DateChipCalendarModel()
    @State private var awake = false

    private typealias M = DateChipCalendarMotion
    private typealias Stay = DateChipCalendarStay

    init(autoPlay: Bool = true, onReserve: @escaping (Int, Int) -> Void = { _, _ in }) {
        self.autoPlay = autoPlay
        self.onReserve = onReserve
    }

    private var playing: Bool { autoPlay && !reducedMotion }

    var body: some View {
        GeometryReader { outer in
            let insets = outer.safeAreaInsets
            GeometryReader { proxy in
                let g = DateChipCalendarGeometry(width: proxy.size.width, height: proxy.size.height,
                                                 top: insets.top, bottomInset: insets.bottom)
                TimelineView(.animation(paused: scenePhase != .active || !(playing || awake))) { timeline in
                    let f = frame(timeline.date, g)
                    screen(f, g)
                }
            }
            .ignoresSafeArea()
        }
        .background(M.paper.ignoresSafeArea())
        .preferredColorScheme(.light)
        .background(DateChipCalendarTouchSpy { count in
            model.touch(fingers: count)
            awake = true
        })
        .task(id: awake) {
            // Without the loop the timeline only runs while something moves.
            guard awake else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(250))
                if !playing, model.resting(at: DateChipCalendarAnim.now) { awake = false }
            }
        }
    }

    private func frame(_ date: Date, _ g: DateChipCalendarGeometry) -> DateChipCalendarFrame {
        model.geometry = g
        model.reducedMotion = reducedMotion
        return model.frame(at: date.timeIntervalSinceReferenceDate, playing: playing)
    }

    // MARK: Screen

    private func screen(_ f: DateChipCalendarFrame, _ g: DateChipCalendarGeometry) -> some View {
        ZStack(alignment: .topLeading) {
            VStack(alignment: .leading, spacing: 0) {
                Text("Harbor")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(M.tide)
                Text("Book your stay")
                    .font(.system(size: 28, weight: .bold))
                    .foregroundStyle(M.ink)
                    .frame(height: 34)
                    .padding(.top, 1)
                    .accessibilityAddTraits(.isHeader)
            }
            .offset(x: M.side + 4, y: g.top + 8)

            art(g)
                .offset(x: M.side, y: g.top + M.header)

            below
                .frame(width: g.cardWidth, alignment: .topLeading)
                .offset(x: M.side, y: g.chipTop + M.chipHeight + M.gap + f.open * (M.openHeight - M.chipHeight))

            DateChipCalendarChip(model: model, f: f, width: g.cardWidth)
                .offset(x: M.side, y: g.chipTop)

            bar(f, g)
                .offset(y: g.height - g.barHeight)

            if f.auto, playing {
                Circle()
                    .fill(Color.rgba(90, 110, 116, 0.34))
                    .overlay(Circle().strokeBorder(Color.white.opacity(0.72), lineWidth: 1))
                    .frame(width: M.ghost, height: M.ghost)
                    .scaleEffect(1 - 0.16 * f.ghostPress)
                    .opacity(f.ghostOpacity * 0.9)
                    .offset(x: f.ghost.x - M.ghost / 2, y: f.ghost.y - M.ghost / 2)
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
            }
        }
        .frame(width: g.width, height: g.height, alignment: .topLeading)
        .clipped()
    }

    private func art(_ g: DateChipCalendarGeometry) -> some View {
        let h = g.artHeight
        return ZStack(alignment: .bottomLeading) {
            DateChipCalendarHarbourArt()
            VStack(alignment: .leading, spacing: 0) {
                Text("Saltline Cottage")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(Color(hex: 0xFFF9EE))
                Text("Port Ellis · $\(M.rate) a night")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(Color.rgba(255, 249, 238, 0.86))
            }
            .padding(.leading, 14)
            .padding(.bottom, 10)
        }
        .frame(width: g.cardWidth, height: h)
        .background(Color(hex: 0x23566A))
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .accessibilityElement(children: .combine)
    }

    private var below: some View {
        VStack(alignment: .leading, spacing: M.gap) {
            option(title: "Guests", meta: "Sleeps up to 4") {
                HStack(spacing: 10) {
                    stepButton(plus: false) { model.stepGuests(-1) }
                        .accessibilityLabel("Fewer guests")
                    Text("\(model.guests)")
                        .font(.system(size: 16, weight: .semibold).monospacedDigit())
                        .foregroundStyle(M.ink)
                        .frame(minWidth: 16)
                    stepButton(plus: true) { model.stepGuests(1) }
                        .accessibilityLabel("More guests")
                }
            }
            option(title: "Breakfast", meta: "$\(M.breakfast) a night") {
                Toggle("Breakfast", isOn: Binding(get: { model.breakfast }, set: { value in
                    model.bump()
                    model.breakfast = value
                }))
                .labelsHidden()
                .fixedSize()
            }
            HStack(spacing: 8) {
                ForEach(Stay.facts, id: \.label) { fact in
                    VStack(spacing: 1) {
                        Text(fact.value)
                            .font(.system(size: 18, weight: .bold).monospacedDigit())
                            .foregroundStyle(M.ink)
                        Text(fact.label)
                            .font(.system(size: 12))
                            .foregroundStyle(M.mute)
                    }
                    .frame(minWidth: 0, maxWidth: .infinity, maxHeight: .infinity)
                    .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(M.pageFill))
                    .accessibilityElement(children: .combine)
                }
            }
            .frame(height: M.facts)
            Text("Free cancellation until 7 days before check-in.")
                .font(.system(size: 13))
                .foregroundStyle(M.mute)
                .lineLimit(1)
                .padding(.horizontal, 4)
                .frame(height: M.note)
        }
    }

    private func option<Trailing: View>(title: String, meta: String, @ViewBuilder trailing: () -> Trailing) -> some View {
        let shape = RoundedRectangle(cornerRadius: 18, style: .continuous)
        return HStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 0) {
                Text(title)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(M.ink)
                Text(meta)
                    .font(.system(size: 13))
                    .foregroundStyle(M.mute)
            }
            Spacer(minLength: 8)
            trailing()
        }
        .padding(.horizontal, 16)
        .frame(height: M.option)
        .background(
            ZStack {
                shape.fill(M.card).shadow(color: Color.rgba(16, 40, 48, 0.05), radius: 7, y: 6)
                shape.fill(M.card).shadow(color: Color.rgba(16, 40, 48, 0.07), radius: 1, y: 1)
            }
        )
    }

    private func stepButton(plus: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            ZStack {
                Circle().fill(M.pageFill)
                Capsule().fill(M.ink).frame(width: 12, height: 2)
                if plus { Capsule().fill(M.ink).frame(width: 2, height: 12) }
            }
            .frame(width: 32, height: 32)
            .contentShape(Rectangle().inset(by: -6))
        }
        .buttonStyle(DateChipCalendarPlainButton())
    }

    private func bar(_ f: DateChipCalendarFrame, _ g: DateChipCalendarGeometry) -> some View {
        let total = Stay.totalLabel(nights: model.shownNights, breakfast: model.breakfast)
        return HStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 0) {
                Text("Total")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(M.mute)
                VStack(alignment: .leading, spacing: 0) {
                    Text("Select dates")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(M.mute)
                        .frame(height: M.line)
                    Text(total)
                        .font(.system(size: 17, weight: .bold).monospacedDigit())
                        .foregroundStyle(M.ink)
                        .frame(height: M.line)
                }
                .lineLimit(1)
                .offset(y: -M.clamp01(f.roll) * M.line)
                .frame(height: M.line, alignment: .top)
                .clipped()
            }
            .frame(minWidth: 0, maxWidth: .infinity, alignment: .leading)
            .padding(.trailing, 12)
            Button {
                if let range = model.range { onReserve(range.start, range.end) }
            } label: {
                Text("Reserve")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Color(hex: 0xF7FBFA))
                    .padding(.horizontal, 28)
                    .frame(height: 48)
                    .background(Capsule(style: .continuous).fill(M.tide))
            }
            .buttonStyle(DateChipCalendarPlainButton())
            .disabled(model.range == nil)
            .opacity(0.42 + 0.58 * f.hasValue)
            .accessibilityLabel("Reserve")
        }
        .padding(.horizontal, M.side + 4)
        .padding(.bottom, max(g.bottomInset, 12))
        .frame(width: g.width, height: g.barHeight)
        .background(
            M.card
                .shadow(color: Color.rgba(16, 40, 48, 0.06), radius: 9, y: -6)
                .overlay(alignment: .top) { Rectangle().fill(M.hairline).frame(height: 1 / 3) }
        )
    }
}

/// The harbour at dusk, drawn from the RN block's 360 × 132 SVG, sliced to fill like `xMinYMid slice`.
struct DateChipCalendarHarbourArt: View {
    var body: some View {
        Canvas { context, size in
            let s = max(size.width / 360, size.height / 132)
            context.translateBy(x: 0, y: (size.height - 132 * s) / 2)
            context.scaleBy(x: s, y: s)

            let sky = Gradient(stops: [
                .init(color: Color(hex: 0x23566A), location: 0),
                .init(color: Color(hex: 0xE0A77E), location: 0.55),
                .init(color: Color(hex: 0xF4D7A8), location: 1),
            ])
            context.fill(Path(CGRect(x: 0, y: 0, width: 360, height: 132)),
                         with: .linearGradient(sky, startPoint: .zero, endPoint: CGPoint(x: 0, y: 132)))
            context.fill(Path(ellipseIn: CGRect(x: 156, y: 50, width: 40, height: 40)),
                         with: .color(Color(hex: 0xFBE9C6).opacity(0.92)))

            var hills = Path()
            hills.move(to: CGPoint(x: 0, y: 82))
            hills.addCurve(to: CGPoint(x: 130, y: 80), control1: CGPoint(x: 40, y: 66), control2: CGPoint(x: 86, y: 70))
            hills.addCurve(to: CGPoint(x: 232, y: 78), control1: CGPoint(x: 170, y: 88), control2: CGPoint(x: 196, y: 76))
            hills.addLine(to: CGPoint(x: 232, y: 92))
            hills.addLine(to: CGPoint(x: 0, y: 92))
            hills.closeSubpath()
            context.fill(hills, with: .color(Color(hex: 0x2C5A63).opacity(0.75)))

            let sea = Gradient(stops: [
                .init(color: Color(hex: 0x3E7E86), location: 0),
                .init(color: Color(hex: 0x1F5766), location: 0.5),
                .init(color: Color(hex: 0x143F4C), location: 1),
            ])
            context.fill(Path(CGRect(x: 0, y: 86, width: 360, height: 46)),
                         with: .linearGradient(sea, startPoint: CGPoint(x: 0, y: 86), endPoint: CGPoint(x: 0, y: 132)))

            var glints = Path()
            glints.move(to: CGPoint(x: 136, y: 96)); glints.addLine(to: CGPoint(x: 216, y: 96))
            glints.move(to: CGPoint(x: 150, y: 106)); glints.addLine(to: CGPoint(x: 202, y: 106))
            glints.move(to: CGPoint(x: 164, y: 116)); glints.addLine(to: CGPoint(x: 192, y: 116))
            context.stroke(glints, with: .color(Color(hex: 0xF8DDB1).opacity(0.5)),
                           style: StrokeStyle(lineWidth: 2, lineCap: .round))

            var house = Path()
            house.move(to: CGPoint(x: 58, y: 92)); house.addLine(to: CGPoint(x: 58, y: 70))
            house.addLine(to: CGPoint(x: 76, y: 58)); house.addLine(to: CGPoint(x: 94, y: 70))
            house.addLine(to: CGPoint(x: 94, y: 92)); house.closeSubpath()
            context.fill(house, with: .color(Color(hex: 0xF2EADB)))

            var roof = Path()
            roof.move(to: CGPoint(x: 54, y: 72)); roof.addLine(to: CGPoint(x: 76, y: 55)); roof.addLine(to: CGPoint(x: 98, y: 72))
            context.stroke(roof, with: .color(Color(hex: 0x7C4A38)),
                           style: StrokeStyle(lineWidth: 4, lineCap: .round, lineJoin: .round))

            context.fill(Path(roundedRect: CGRect(x: 71, y: 78, width: 10, height: 14), cornerRadius: 1.5),
                         with: .color(Color(hex: 0x34565F)))

            var hull = Path()
            hull.move(to: CGPoint(x: 108, y: 98)); hull.addLine(to: CGPoint(x: 144, y: 98))
            hull.addLine(to: CGPoint(x: 138, y: 106)); hull.addLine(to: CGPoint(x: 114, y: 106)); hull.closeSubpath()
            context.fill(hull, with: .color(Color(hex: 0xF2EADB)))

            var sail = Path()
            sail.move(to: CGPoint(x: 126, y: 98)); sail.addLine(to: CGPoint(x: 126, y: 76))
            sail.addLine(to: CGPoint(x: 140, y: 94)); sail.closeSubpath()
            context.fill(sail, with: .color(Color(hex: 0xFBF3E4)))
        }
        .accessibilityHidden(true)
    }
}
