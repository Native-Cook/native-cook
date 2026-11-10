import SwiftUI

struct ChampagneRsvp: View {
    static let loop = ChampagneRsvpMotion.loop
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var clock = ChampagneRsvpClock()
    private var pinned: Double? {
        #if DEBUG
        if let value = UserDefaults.standard.object(forKey: "champagneRsvpTime") {
            return (value as? NSNumber)?.doubleValue ?? Double("\(value)")
        }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { safe in
          GeometryReader { proxy in
            let l = ChampagneRsvpLayout(proxy.size, safe.safeAreaInsets.top, safe.safeAreaInsets.bottom)
            let reply = pinned.map { ChampagneRsvpMotion.at($0).done } ?? clock.done
            TimelineView(.animation(paused: pinned != nil || scenePhase != .active || (reduced && !clock.running))) { timeline in
                let f = clock.sample(timeline.date, autoPlay, reduced, pinned)
                ZStack(alignment: .topLeading) {
                    ChampagneRsvpGlassView(layout: l, frame: f).allowsHitTesting(false)
                    Rectangle().fill(.clear).contentShape(Rectangle())
                        .frame(width: l.stage.width, height: l.stage.height)
                        .position(x: l.stage.midX, y: l.stage.midY)
                        .gesture(DragGesture(minimumDistance: 0).onChanged { value in
                            if pinned == nil { clock.drag(value, l, reduced) }
                        }.onEnded { _ in if pinned == nil { clock.endDrag() } })
                        .accessibilityElement(children: .ignore)
                        .accessibilityAddTraits(.isImage)
                        .accessibilityLabel("Two flutes of champagne")
                        .accessibilityHint("Drag them together to clink")
                    ChampagneRsvpCopy(layout: l, done: reply, reduced: reduced, pinned: pinned != nil,
                        accept: { clock.accept(reduced) }, decline: { clock.decline() })
                    ChampagneRsvpFinger(layout: l, frame: f).allowsHitTesting(false)
                }
            }
            .frame(width: l.w, height: l.h, alignment: .topLeading)
            .clipped()
          }.ignoresSafeArea()
        }
        .background(ChampagneRsvpTheme.page.ignoresSafeArea())
        .preferredColorScheme(.light)
        .onAppear { _ = ChampagneRsvpResources.registered; clock.reset(autoPlay, reduced) }
        .onChange(of: scenePhase) { _, _ in clock.active() }
        .onChange(of: reduced) { _, r in clock.reset(autoPlay, r) }
        .onChange(of: autoPlay) { _, _ in clock.reset(autoPlay, reduced) }
    }
}

private struct ChampagneRsvpFinger: View {
    let layout: ChampagneRsvpLayout
    let frame: ChampagneRsvpMotion.Frame
    var body: some View {
        let r = 22 * (1 - 0.12 * frame.fingerPress)
        Circle().fill(ChampagneRsvpTheme.ink.opacity(0.1 * frame.finger))
            .overlay(Circle().stroke(ChampagneRsvpTheme.ink.opacity(0.32 * frame.finger), lineWidth: 1.5))
            .frame(width: r * 2, height: r * 2).position(x: layout.w / 2, y: layout.ctaTop + 28)
    }
}

private struct ChampagneRsvpCopy: View {
    let layout: ChampagneRsvpLayout
    let done: Bool, reduced: Bool, pinned: Bool
    let accept: () -> Void, decline: () -> Void
    private func label(_ text: String, _ face: String, _ size: Double, _ height: Double, _ y: Double, _ color: Color, _ tracking: Double = 0) -> some View {
        Text(text).font(ChampagneRsvpResources.font(face, size)).tracking(tracking)
            .foregroundStyle(color).frame(width: layout.w, height: height)
            // RN's explicit 48-pt line height uses the serif's typographic baseline.
            .position(x: layout.w / 2, y: y + height / 2 - (face == "InstrumentSerif-Regular" ? 13.0 / 3.0 : 0) + (size == 12 ? 1.0 / 3.0 : 0))
    }
    var body: some View {
        ZStack(alignment: .topLeading) {
            label("THE GLASSHOUSE · LISBON", "Inter-Medium", 12, 16, layout.venueTop, ChampagneRsvpTheme.gold, 1.6)
            label("Clara & Theo", "InstrumentSerif-Regular", 44, 48, layout.namesTop, ChampagneRsvpTheme.ink)
                .accessibilityAddTraits(.isHeader)
            label("Saturday 14 June · 6 pm", "Inter-Regular", 15, 22, layout.dateTop, ChampagneRsvpTheme.mute)
            ForEach(0..<2) { i in
                let on = (i == 1) == done
                label(i == 1 ? ChampagneRsvpTheme.doneLine : ChampagneRsvpTheme.idleLine,
                      "Inter-Medium", 15, 22, layout.lineTop, ChampagneRsvpTheme.ink)
                    .opacity(on ? 1 : 0).offset(y: on || reduced ? 0 : 6)
                    .animation(pinned ? nil : ChampagneRsvpTheme.ease, value: done)
                    .accessibilityHidden(!on)
            }
            Button(action: accept) {
                Text(done ? "Add to Calendar" : "Joyfully Accept")
                    .font(ChampagneRsvpResources.font("Inter-SemiBold", 17)).foregroundStyle(ChampagneRsvpTheme.ctaInk)
                    .offset(y: 1.0 / 3.0)
                    .frame(width: layout.w - 32, height: 56)
                    .background(ChampagneRsvpTheme.ink, in: RoundedRectangle(cornerRadius: 28, style: .circular))
            }.buttonStyle(ChampagneRsvpPressStyle(reduced: reduced))
                .position(x: layout.w / 2, y: layout.ctaTop + 28).disabled(pinned)
            Button(action: decline) {
                Text(done ? "Change my reply" : "Regretfully Decline")
                    .font(ChampagneRsvpResources.font("Inter-Medium", 15)).foregroundStyle(ChampagneRsvpTheme.mute)
                    .offset(x: -1.0 / 3.0, y: -1.0 / 3.0)
                    .frame(width: layout.w - 32, height: 44).contentShape(Rectangle())
            }.buttonStyle(ChampagneRsvpPressStyle(reduced: reduced))
                .position(x: layout.w / 2, y: layout.linkTop + 22).disabled(pinned)
        }.frame(width: layout.w, height: layout.h, alignment: .topLeading)
    }
}

private struct ChampagneRsvpPressStyle: ButtonStyle {
    let reduced: Bool
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.scaleEffect(configuration.isPressed && !reduced ? 0.96 : 1)
            .opacity(configuration.isPressed && reduced ? 0.72 : 1)
            .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.15), value: configuration.isPressed)
    }
}
