import SwiftUI
import UIKit

/// A scratch-off lottery ticket on a dark green table. A finger rubs the holographic foil away
/// along a soft brush, tearing off little foil flakes that fall with gravity; once enough is
/// cleared the rest dissolves with a golden edge, the prize photo settles into view, the stub
/// prints the prize and confetti bursts. "New ticket" (or throwing the ticket left) deals the next.
struct ScratchTicket: View {
    /// Scratches, reveals and deals a new ticket on its own, on a loop of 9 s.
    var autoPlay = false
    /// Called with the prize when a ticket is revealed.
    var onReveal: ((String) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    // The table's motion lives in a plain object stepped once a frame, like the RN frame callback.
    @State private var engine = ScratchTicketEngine()
    @State private var photos = ScratchTicketPhotos()
    // True while a drag is under way; reset by SwiftUI when the system cancels the drag.
    @GestureState private var dragging = false

    private typealias M = ScratchTicketMetrics
    static let background = Color(hex: 0x13251b)

    var body: some View {
        // The block draws under the system bars and keeps the ticket clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            let ticketGeo = ScratchTicketGeometry(width: size.width, height: size.height, top: insets.top, bottom: insets.bottom)
            // Nothing to lay out until the screen is measured (a zero first pass gives negative sizes).
            if ticketGeo.scratch.width > 0 && ticketGeo.scratch.height > 0 {
                stage(geo: ticketGeo)
                    .frame(width: size.width, height: size.height)
                    .offset(x: -insets.leading, y: -insets.top)
            }
        }
        .background(Self.background.ignoresSafeArea())
        .task { await photos.load(engine.prizes) }
    }

    private func stage(geo: ScratchTicketGeometry) -> some View {
        let settings = ScratchTicketSettings(geo: geo, autoPlay: autoPlay, reducedMotion: reduceMotion, onReveal: onReveal)
        return TimelineView(.animation) { timeline in
            let _ = engine.step(to: timeline.date, settings: settings)
            ZStack(alignment: .topLeading) {
                Canvas { ctx, size in drawTable(in: ctx, size: size) }
                    .allowsHitTesting(false)

                ZStack(alignment: .topLeading) {
                    ForEach(0..<2, id: \.self) { k in
                        let slot = engine.slots[k]
                        ScratchTicketCard(
                            slot: slot,
                            prize: engine.prizes[slot.ticket],
                            photo: photos.images[slot.ticket],
                            geo: geo,
                            phase: engine.phase,
                            tilt: engine.tilt,
                            now: engine.elapsed,
                            reducedMotion: reduceMotion
                        )
                        .offset(x: geo.ticket.minX, y: geo.ticket.minY)
                    }
                }
                .frame(width: geo.width, height: geo.height, alignment: .topLeading)
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .updating($dragging) { _, state, _ in state = true }
                        .onChanged { engine.touchMoved($0, settings: settings) }
                        .onEnded { engine.touchEnded($0, settings: settings) }
                )
                .onChange(of: dragging) { _, down in
                    // A drag the system cancels never reaches onEnded.
                    if !down { DispatchQueue.main.async { engine.touchCancelled() } }
                }

                button(settings: settings)
                    .frame(width: geo.ticket.width, height: M.buttonHeight)
                    .offset(x: geo.ticket.minX, y: geo.buttonY)

                Canvas { ctx, size in drawPieces(in: ctx, size: size) }
                    .allowsHitTesting(false)
            }
            .frame(width: geo.width, height: geo.height, alignment: .topLeading)
        }
        .clipped()
    }

    private func button(settings: ScratchTicketSettings) -> some View {
        let emphasis = engine.emphasis.value
        return Button {
            engine.newTicketPressed(settings)
        } label: {
            HStack(spacing: 8) {
                Text("New ticket")
                    .font(.custom("AvenirNext-DemiBold", fixedSize: 16))
                    .kerning(0.2)
                    .foregroundStyle(ScratchTicketColor.mix(0xf4ecd9, 0x14231a, emphasis))
                ScratchTicketArrow()
                    .stroke(Color(hex: 0x14231a), style: StrokeStyle(lineWidth: 1.8, lineCap: .round, lineJoin: .round))
                    .frame(width: 16, height: 16)
                    .opacity(emphasis)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(ScratchTicketCard.paper.opacity(emphasis), in: Capsule())
            .overlay(Capsule().strokeBorder(Color.rgba(244, 236, 217, 0.28), lineWidth: 1))
            .contentShape(Capsule())
        }
        .buttonStyle(ScratchTicketPressStyle { engine.buttonPressing($0) })
        .scaleEffect(engine.press.value)
        .accessibilityLabel("New ticket")
    }

    // MARK: Drawing

    /// The green baize: a soft pool of light and a fine grain.
    private func drawTable(in ctx: GraphicsContext, size: CGSize) {
        let rect = Path(CGRect(origin: .zero, size: size))
        let pool = Gradient(stops: [
            .init(color: Color(hex: 0x1d3a29), location: 0),
            .init(color: Self.background, location: 0.55),
            .init(color: Color(hex: 0x0a150f), location: 1),
        ])
        ctx.fill(rect, with: .radialGradient(pool, center: CGPoint(x: size.width / 2, y: size.height * 0.42), startRadius: 0, endRadius: size.height * 0.7))
        var grain = ctx
        grain.blendMode = .overlay
        grain.fill(rect, with: .shader(ShaderLibrary.scratchTicketGrain(.float(0.8), .float(0.33), .float(0.34))))
    }

    /// Foil flakes (drawn with the foil itself), confetti and autoPlay's virtual fingertip.
    private func drawPieces(in ctx: GraphicsContext, size: CGSize) {
        if !engine.flakes.isEmpty {
            let foil = ShaderLibrary.scratchTicketFoil(
                .float2(CGPoint.zero), .float2(size), .float(engine.phase), .float2(engine.tilt), .float(0)
            )
            ctx.fill(ScratchTicketPieces.path(engine.flakes, colour: -1, aspect: 0.8), with: .shader(foil))
        }
        if !engine.confetti.isEmpty {
            for (k, colour) in M.confettiColours.enumerated() {
                ctx.fill(ScratchTicketPieces.path(engine.confetti, colour: k, aspect: 1.7), with: .color(Color(hex: colour)))
            }
        }
        // The virtual finger of autoPlay, so a recording shows what is scratching.
        let alpha = engine.fingerAlpha.value
        if alpha > 0.001 {
            var c = ctx
            c.opacity = alpha
            let f = engine.finger
            let dot = Path(ellipseIn: CGRect(x: f.x - 22, y: f.y - 22, width: 44, height: 44))
            c.fill(dot, with: .color(.rgba(255, 255, 255, 0.22)))
            c.stroke(dot, with: .color(.rgba(255, 255, 255, 0.6)), lineWidth: 1.5)
        }
    }
}

/// The arrow beside "New ticket" once a prize is out: a shaft and an open head, in a 16 × 16 box.
private struct ScratchTicketArrow: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: 2, y: 8))
        p.addLine(to: CGPoint(x: 13, y: 8))
        p.move(to: CGPoint(x: 9, y: 3.5))
        p.addLine(to: CGPoint(x: 13.5, y: 8))
        p.addLine(to: CGPoint(x: 9, y: 12.5))
        return p
    }
}

/// The pill reports its press so the engine can ease it down and back like the RN Pressable.
private struct ScratchTicketPressStyle: ButtonStyle {
    let pressing: (Bool) -> Void

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .onChange(of: configuration.isPressed) { _, down in pressing(down) }
    }
}

enum ScratchTicketColor {
    /// Reanimated's `interpolateColor`: each channel mixed in linear light (gamma 2.2) and rounded.
    static func mix(_ from: UInt32, _ to: UInt32, _ t: Double) -> Color {
        func channel(_ shift: UInt32) -> Double {
            let a = pow(Double((from >> shift) & 0xff) / 255, 2.2)
            let b = pow(Double((to >> shift) & 0xff) / 255, 2.2)
            return (pow(a + (b - a) * min(1, max(0, t)), 1 / 2.2) * 255).rounded() / 255
        }
        return Color(.sRGB, red: channel(16), green: channel(8), blue: channel(0), opacity: 1)
    }
}
