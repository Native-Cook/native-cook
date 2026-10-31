import SwiftUI

/// Night-station pass: bring the acrylic card to the reader, then print and raise a ride ticket.
/// The 9 s autoplay tells the same story as a touch; the driver handles interruption and inactivity.
/// Nested geometry readers retain the safe-area insets while artwork fills the screen in points.
struct TapRidePass: View {
    static let loop = TapRidePassMotion.loop
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var driver = TapRidePassDriver()
    @State private var touching = false
    private var pinned: Double? {
        #if DEBUG
            if let v = UserDefaults.standard.object(forKey: "tapRidePassTime") {
                return (v as? NSNumber)?.doubleValue ?? Double("\(v)") ?? 0
            }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { safeArea in
            GeometryReader { viewport in
                let layout = TapRidePassGeometry(
                    width: viewport.size.width, height: viewport.size.height, safeTop: safeArea.safeAreaInsets.top,
                    safeBottom: safeArea.safeAreaInsets.bottom)
                // The reader's gentle 3 s breathing remains active in RN, including reduced motion.
                TimelineView(.animation(paused: pinned != nil || scenePhase != .active)) { timeline in
                    let _ = driver.sample(timeline.date, layout, autoPlay, reduceMotion, pinned)
                    let _ = TapRidePassResources.shared.prepare(layout, driver.balance, driver.balanceAfterFare)
                    let drawing = TapRidePassDrawing(driver, layout, reduceMotion)
                    let art = TapRidePassArt(
                        layout: layout, driver: driver, drawing: drawing, reduceMotion: reduceMotion)
                    ZStack(alignment: .topLeading) {
                        Canvas { context, _ in art.background(context) }
                        if driver.ticketProgress > 0.0005 && drawing.inSlot {
                            TapRidePassPaper(layout: layout, drawing: drawing).frame(
                                width: layout.width, height: layout.height, alignment: .topLeading
                            ).mask(alignment: .topLeading) {
                                Rectangle().frame(width: layout.width, height: layout.slotY)
                            }
                        }
                        Canvas { context, _ in art.spill(context) }.blendMode(.plusLighter)
                        RoundedRectangle(cornerRadius: 18, style: .circular)
                            .fill(.black.opacity(0.55 - 0.15 * driver.lift))
                            .frame(width: layout.cardWidth - 12, height: layout.cardHeight - 8).offset(y: 2)
                            .blur(radius: 10 + 14 * driver.lift * (1 - drawing.dockProgress))
                            .frame(width: layout.cardWidth, height: layout.cardHeight).projectionEffect(
                                art.projection(drawing.cardScale * 0.97)
                            )
                            .position(
                                x: drawing.cardX + 4 * (1 - drawing.dockProgress),
                                y: drawing.cardY + (14 + 30 * driver.lift) * (1 - 0.75 * drawing.dockProgress))
                        Canvas { context, _ in art.card(context) }.frame(
                            width: layout.cardWidth, height: layout.cardHeight
                        ).projectionEffect(art.projection(drawing.cardScale)).position(
                            x: drawing.cardX, y: drawing.cardY)
                        // Skia's transformed Group is not isolated: each powered coil adds to the
                        // validator pixels underneath, rather than to a transparent card render target.
                        Image(uiImage: TapRidePassResources.shared.coil).renderingMode(.template).resizable()
                            .foregroundStyle(TapRidePassResources.color(0xFF9A3C))
                            .frame(width: layout.cardWidth, height: layout.cardHeight).blur(radius: 5).opacity(
                                drawing.coil * 0.9
                            )
                            .projectionEffect(art.projection(drawing.cardScale)).position(
                                x: drawing.cardX, y: drawing.cardY
                            ).blendMode(.plusLighter)
                        Image(uiImage: TapRidePassResources.shared.coil).renderingMode(.template).resizable()
                            .foregroundStyle(TapRidePassResources.color(0xFFD9A0))
                            .frame(width: layout.cardWidth, height: layout.cardHeight).opacity(drawing.coil)
                            .projectionEffect(art.projection(drawing.cardScale)).position(
                                x: drawing.cardX, y: drawing.cardY
                            ).blendMode(.plusLighter)
                        Canvas { context, _ in art.cardPrinting(context) }.frame(
                            width: layout.cardWidth, height: layout.cardHeight
                        )
                        .projectionEffect(art.projection(drawing.cardScale)).position(
                            x: drawing.cardX, y: drawing.cardY)
                        Rectangle().fill(
                            TapRidePassResources.color(
                                0x050506, 0.62 * TapRidePassMotion.smooth(0.4, 0.85, driver.ticketProgress)))
                        if driver.ticketProgress > 0.0005 && !drawing.inSlot {
                            TapRidePassPaper(layout: layout, drawing: drawing, shadow: true)
                        }
                        Canvas { context, _ in art.labels(context) }.allowsHitTesting(false)
                        Button {
                            driver.close()
                        } label: {
                            Image(uiImage: TapRidePassResources.shared.done).resizable().frame(
                                width: 140, height: 48)
                        }.buttonStyle(TapRidePassDoneStyle())
                            .opacity(TapRidePassMotion.smooth(0.85, 1, driver.ticketProgress))
                            .position(
                                x: layout.width / 2,
                                y: layout.ticketY + layout.ticketHeight / 2 + 46 + 10
                                    * (1 - TapRidePassMotion.smooth(0.85, 1, driver.ticketProgress))
                            )
                            .allowsHitTesting(pinned == nil && driver.ticketProgress > 0.9).accessibilityLabel(
                                "Done"
                            )
                            .accessibilityHidden(driver.ticketProgress <= 0.9)
                        Color.clear.frame(width: layout.width - 48, height: 42).position(
                            x: layout.width / 2, y: layout.safeTop + 67
                        )
                        .allowsHitTesting(false).accessibilityElement()
                        .accessibilityLabel(driver.ticketProgress > 0.7 ? "You’re through" : "Harbor Gate")
                        .accessibilityAddTraits(.isHeader)
                        if pinned == nil {
                            if driver.ticketProgress <= 0.9 {
                                Color.clear.contentShape(Rectangle()).frame(
                                    width: layout.cardWidth * drawing.cardScale,
                                    height: layout.cardHeight * drawing.cardScale * cos(drawing.rotationX)
                                ).position(x: drawing.cardX, y: drawing.cardY)
                                    .gesture(
                                        DragGesture(minimumDistance: 0, coordinateSpace: .global).onChanged {
                                            gesture in
                                            if !touching {
                                                touching = true
                                                driver.begin()
                                            }
                                            driver.drag(gesture.translation)
                                        }.onEnded { _ in
                                            touching = false
                                            driver.end()
                                        }
                                    )
                                    .accessibilityElement().accessibilityAddTraits(.isButton)
                                    .accessibilityLabel(
                                        "Riverline commuter card, balance \(String(format:"$%.2f",driver.balance))"
                                    )
                                    .accessibilityHint("Touches the card to the reader to pay the fare")
                                    .accessibilityAction { driver.assist() }
                            } else {
                                Color.clear.contentShape(Rectangle()).frame(
                                    width: drawing.paperWidth, height: drawing.paperHeight
                                ).position(x: drawing.paperX, y: drawing.paperY).onTapGesture { driver.close() }
                                    .accessibilityElement().accessibilityLabel(
                                        "Single ride ticket, Harbor Gate to Old Mill, fare $2.75, balance "
                                            + "\(String(format: "$%.2f", driver.balanceAfterFare)). Gate 3 open."
                                    )
                            }
                        }
                    }.frame(width: layout.width, height: layout.height, alignment: .topLeading).clipped()
                }
            }.ignoresSafeArea()
        }.background(TapRidePassResources.color(0x070708)).preferredColorScheme(.dark)
            .onChange(of: scenePhase) { _, phase in driver.active(phase == .active) }
            .onDisappear { driver.active(false) }
    }
}

/// Subtle pressed feedback for the ticket dismissal control.
struct TapRidePassDoneStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.background(Capsule().fill(.white.opacity(configuration.isPressed ? 0.067 : 0)))
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
    }
}
