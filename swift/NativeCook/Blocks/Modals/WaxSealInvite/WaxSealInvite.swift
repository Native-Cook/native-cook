import QuartzCore
import SwiftUI
import UIKit

/// Wax Seal Invite — an invitation that arrives as a modal over the inbox: a cream envelope closed
/// with an oxblood wax seal stamped "L&T". Pull the seal down (or hold it) and it cracks into seven
/// pieces that fall away; the flap swings open about its top edge on its red liner, and the card
/// rises out of the envelope, comes forward and settles with "Attending" / "Can't make it". The
/// loop (WaxSealInviteMotion, loop 10 s) shows the same and closes it all again in reverse.
///
/// Touch: drag the seal down; it follows on a rubber band (at most 30 pt) while hairline cracks
/// start; past 60 pt of pull, or after a 0.7 s hold, it breaks and the envelope opens (3.6 s).
/// Releasing earlier springs it back and heals the cracks. The close button re-seals it (2.4 s).
/// 2 s after the last touch an open invitation closes itself in 0.9 s and the loop restarts.
/// Reduced motion: the invitation shown open and still; the buttons still answer.
struct WaxSealInvite: View {
    /// Breaks the seal, opens the envelope, shows the card and closes it again, on a loop of `loop` s.
    var autoPlay = false

    static let loop = WaxSealInviteMotion.loop

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        // The block draws under the system bars and keeps its content clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            let layout = WaxSealInviteLayout(width: size.width, height: size.height, top: insets.top, bottom: insets.bottom)
            ZStack(alignment: .topLeading) {
                WaxSealInviteInbox(top: insets.top)
                    .frame(width: size.width, height: size.height, alignment: .topLeading)
                    .blur(radius: 12, opaque: true)
                WaxSealInvitePalette.dim
                WaxSealInviteScene(layout: layout, autoPlay: autoPlay && !reduceMotion, reduced: reduceMotion)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .clipped()
            .offset(x: -insets.leading, y: -insets.top)
        }
        .background(WaxSealInvitePalette.background.ignoresSafeArea())
    }
}

enum WaxSealInvitePalette {
    static func hex(_ v: UInt32, _ a: Double = 1) -> Color {
        Color(
            red: Double((v >> 16) & 0xFF) / 255,
            green: Double((v >> 8) & 0xFF) / 255,
            blue: Double(v & 0xFF) / 255,
            opacity: a
        )
    }

    static func rgba(_ r: Double, _ g: Double, _ b: Double, _ a: Double) -> Color {
        Color(red: r / 255, green: g / 255, blue: b / 255, opacity: a)
    }

    static let background = hex(0xF2EDE6)
    static let dim = rgba(30, 16, 12, 0.62)
    static let light = hex(0xFBF3EA)
}

private struct WaxSealInviteScene: View {
    let layout: WaxSealInviteLayout
    let autoPlay: Bool
    let reduced: Bool

    @Environment(\.displayScale) private var displayScale
    @State private var engine = WaxSealInviteEngine()
    /// Keeps the timeline running while a touch's animations play outside autoPlay.
    @State private var awake = false
    @State private var down = false
    @State private var answer: WaxSealInviteAnswer?

    private typealias P = WaxSealInvitePalette
    private typealias M = WaxSealInviteMotion

    private static let chips = ["Sat 14 June", "Lake Orta", "RSVP by 1 May"]

    var body: some View {
        let scale = min(displayScale, 3)
        let mask = WaxSealInviteArt.sealMask(R: layout.sealR, scale: scale)
        let card = WaxSealInviteArt.card(w: layout.cardW, h: layout.cardH, scale: scale)
        let geometry = engine.geometry(R: layout.sealR)
        let seal = layout.seal
        let R = layout.sealR

        ZStack(alignment: .topLeading) {
            TimelineView(.animation(paused: !(autoPlay || awake))) { timeline in
                let frame = engine.frame(at: timeline.date.timeIntervalSinceReferenceDate, autoPlay: autoPlay, reduced: reduced)
                let _ = react(frame)
                let pose = frame.pose
                ZStack(alignment: .topLeading) {
                    header(pose)
                    WaxSealInviteEnvelopeBack(layout: layout).equatable()
                    flap(pose, inside: true)
                    if pose.card > 0.001 && pose.card <= 1 { cardBehind(card, pose) }
                    WaxSealInviteEnvelopeFront(layout: layout).equatable()
                    flapShadow(pose)
                    flap(pose, inside: false)
                    WaxSealInviteSeal(layout: layout, geometry: geometry, mask: mask, pose: pose)
                    if pose.card > 1 { cardFront(card, pose) }
                    rsvp(pose, open: frame.open)
                    closeButton(pose, closable: frame.open && !reduced)
                    Text("Pull the seal down to open")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(P.light.opacity(0.55))
                        .multilineTextAlignment(.center)
                        .frame(width: layout.width - 48)
                        .opacity(max(0, 1 - pose.crack * 3 - pose.card))
                        .offset(x: 24, y: layout.hintTop)
                        .allowsHitTesting(false)

                    // The seal's hit area (it stays where the seal rests).
                    if !reduced {
                        Color.clear
                            .frame(width: R * 2 + 44, height: R * 2 + 44)
                            .contentShape(Circle())
                            .gesture(sealGesture)
                            .allowsHitTesting(!frame.open)
                            .accessibilityElement()
                            .accessibilityAddTraits(.isButton)
                            .accessibilityLabel("Wax seal. Pull down or hold to open the invitation.")
                            .offset(x: seal.x - R - 22, y: seal.y - R - 22)
                    }
                }
                .frame(width: layout.width, height: layout.height, alignment: .topLeading)
            }

            chipsRow
        }
        .frame(width: layout.width, height: layout.height, alignment: .topLeading)
    }

    /// Side effects of a frame, run after the view update: haptics, the answer reset, sleeping.
    private func react(_ frame: WaxSealInviteEngine.Frame) {
        if frame.openedNow {
            DispatchQueue.main.async { UISelectionFeedbackGenerator().selectionChanged() }
        }
        if frame.closedNow {
            DispatchQueue.main.async { answer = nil }
        }
        if frame.settled && awake {
            DispatchQueue.main.async { awake = false }
        }
    }

    private func wake() {
        if !awake { awake = true }
    }

    // MARK: Pieces of the scene

    private func header(_ pose: WaxSealInvitePose) -> some View {
        // On short screens the card rises past the header: the header steps aside while it does.
        let fades = layout.clearTop < layout.headerTop + 86
        let c = pose.card
        let opacity = fades ? 1 - 0.9 * M.clamp01((c - 0.4) / 0.4) * M.clamp01((1.9 - c) / 0.4) : 1
        return VStack(alignment: .leading, spacing: 4) {
            Text("YOU'RE INVITED")
                .font(.system(size: 11, weight: .bold))
                .kerning(2.4)
                .foregroundStyle(P.hex(0xE9B9A4))
            Text("A letter from Lena & Theo")
                .font(.system(size: 22, weight: .bold))
                .kerning(-0.3)
                .foregroundStyle(P.light)
            Text("Delivered yesterday by courier")
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(P.light.opacity(0.62))
        }
        .frame(width: max(0, layout.width - 96), alignment: .leading)
        .opacity(opacity)
        .offset(x: 24, y: layout.headerTop)
        .allowsHitTesting(false)
    }

    private var chipsRow: some View {
        HStack(spacing: 8) {
            ForEach(Self.chips, id: \.self) { c in
                Text(c)
                    .font(.system(size: 13, weight: .semibold).monospacedDigit())
                    .foregroundStyle(P.light.opacity(0.85))
                    .padding(.horizontal, 12)
                    .frame(height: 30)
                    .background(
                        Capsule()
                            .fill(P.rgba(255, 240, 225, 0.1))
                            .overlay(Capsule().strokeBorder(P.rgba(255, 240, 225, 0.28), lineWidth: 1 / max(displayScale, 1)))
                    )
            }
        }
        .frame(width: layout.width - 32)
        .offset(x: 16, y: layout.chipsTop)
        .allowsHitTesting(false)
    }

    /// The flap, turned about its top edge with perspective 900: its outside while it is less than
    /// upright, its lined inside once past.
    @ViewBuilder
    private func flap(_ pose: WaxSealInvitePose, inside: Bool) -> some View {
        let a = pose.flap
        if inside == (a > .pi / 2) {
            let env = layout.env
            let shade = inside ? (Double.pi - a) / (Double.pi / 2) * 0.5 : a / (Double.pi / 2) * 0.45
            ZStack(alignment: .topLeading) {
                if inside {
                    WaxSealInviteFlapInside(layout: layout).equatable()
                } else {
                    WaxSealInviteFlapOutside(layout: layout).equatable()
                }
                WaxSealInviteShape(path: WaxSealInviteArt.flapPath(w: env.width, fh: layout.flap))
                    .fill(inside ? P.hex(0x1A0606) : P.hex(0x2A1A0C))
                    .opacity(shade)
            }
            .frame(width: env.width, height: layout.flap + 4, alignment: .topLeading)
            .projectionEffect(Self.hinge(angle: a, x: env.width / 2))
            .offset(x: env.minX, y: env.minY)
            .allowsHitTesting(false)
        }
    }

    /// Skia's [{ perspective: 900 }, { rotateX: a }] about (x, 0).
    private static func hinge(angle a: Double, x: Double) -> ProjectionTransform {
        var p = CATransform3DIdentity
        p.m34 = -1 / 900
        let r = CATransform3DMakeRotation(a, 1, 0, 0)
        // Row vectors: move the hinge to the origin, rotate, project, move back.
        var m = CATransform3DConcat(CATransform3DMakeTranslation(-x, 0, 0), r)
        m = CATransform3DConcat(m, p)
        m = CATransform3DConcat(m, CATransform3DMakeTranslation(x, 0, 0))
        return ProjectionTransform(m)
    }

    private func flapShadow(_ pose: WaxSealInvitePose) -> some View {
        let env = layout.env
        return WaxSealInviteShape(path: WaxSealInviteArt.flapPath(w: env.width, fh: layout.flap))
            .fill(P.hex(0x462810))
            .frame(width: env.width, height: layout.flap + 4)
            .blur(radius: 3)
            .opacity(0.22 * max(0, cos(pose.flap)))
            .offset(x: env.minX, y: env.minY + 3)
            .allowsHitTesting(false)
    }

    /// The card going in or out: behind the envelope's front, clipped to above its mouth.
    private func cardBehind(_ image: UIImage, _ pose: WaxSealInvitePose) -> some View {
        let place = layout.cardPlace(pose.card)
        return ZStack(alignment: .topLeading) {
            Image(uiImage: image)
                .resizable()
                .frame(width: layout.cardW, height: layout.cardH)
                .scaleEffect(place.scale)
                .offset(x: layout.cardX, y: place.top)
        }
        .frame(width: layout.width, height: layout.height, alignment: .topLeading)
        .mask(alignment: .topLeading) {
            Rectangle().frame(width: layout.width, height: max(0, layout.env.minY + layout.env.height * 0.5))
        }
        .allowsHitTesting(false)
    }

    /// The card out in front, with its two shadows.
    private func cardFront(_ image: UIImage, _ pose: WaxSealInvitePose) -> some View {
        let place = layout.cardPlace(pose.card)
        let w = layout.cardW
        let h = layout.cardH
        return ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 10)
                .fill(P.rgba(30, 8, 4, 0.4))
                .frame(width: w - 12, height: h - 4)
                .blur(radius: 16)
                .offset(x: 6, y: 18)
            RoundedRectangle(cornerRadius: 8)
                .fill(P.rgba(30, 8, 4, 0.3))
                .frame(width: w - 1, height: h)
                .blur(radius: 2)
                .offset(x: 0.5, y: 1.5)
            Image(uiImage: image)
                .resizable()
                .frame(width: w, height: h)
        }
        .frame(width: w, height: h, alignment: .topLeading)
        .scaleEffect(place.scale)
        .offset(x: layout.cardX, y: place.top)
        .allowsHitTesting(false)
    }

    /// The card's RSVP buttons; they follow the card.
    private func rsvp(_ pose: WaxSealInvitePose, open: Bool) -> some View {
        let place = layout.cardPlace(pose.card)
        return HStack(spacing: 10) {
            WaxSealInvitePill(
                title: answer == .yes ? "See you there" : "Attending",
                yes: true,
                faded: answer == .no
            ) { choose(.yes) }
            WaxSealInvitePill(
                title: answer == .no ? "Sent your regrets" : "Can't make it",
                yes: false,
                faded: answer == .yes
            ) { choose(.no) }
        }
        .padding(.horizontal, 18)
        .padding(.bottom, 16)
        .frame(width: layout.cardW, height: layout.cardH, alignment: .bottom)
        .scaleEffect(place.scale)
        .opacity(pose.buttons)
        .offset(x: layout.cardX, y: place.top)
        .allowsHitTesting(open)
    }

    private func closeButton(_ pose: WaxSealInvitePose, closable: Bool) -> some View {
        Button {
            answer = nil
            wake()
            engine.close(now: Date.timeIntervalSinceReferenceDate)
        } label: {
            WaxSealInviteCross()
                .stroke(P.hex(0xF3E6D4), style: StrokeStyle(lineWidth: 1.8, lineCap: .round))
                .frame(width: 14, height: 14)
                .frame(width: 36, height: 36)
                .background(
                    Circle()
                        .fill(P.rgba(255, 240, 225, 0.14))
                        .overlay(Circle().strokeBorder(P.rgba(255, 240, 225, 0.3), lineWidth: 1 / max(displayScale, 1)))
                )
                .contentShape(Circle().inset(by: -8))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Close and seal again")
        .opacity(pose.buttons)
        .offset(x: layout.width - 20 - 36, y: layout.headerTop - 4)
        .allowsHitTesting(closable)
    }

    private func choose(_ a: WaxSealInviteAnswer) {
        wake()
        engine.touched(now: Date.timeIntervalSinceReferenceDate)
        answer = a
        if a == .yes {
            UINotificationFeedbackGenerator().notificationOccurred(.success)
        } else {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        }
    }

    // MARK: Touch

    private var sealGesture: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                let now = Date.timeIntervalSinceReferenceDate
                if !down {
                    down = true
                    wake()
                    engine.sealBegan(now: now)
                } else {
                    engine.sealMoved(pull: value.translation.height, now: now)
                }
            }
            .onEnded { _ in
                down = false
                engine.sealEnded(now: Date.timeIntervalSinceReferenceDate)
            }
    }
}

enum WaxSealInviteAnswer {
    case yes
    case no
}

private struct WaxSealInvitePill: View {
    let title: String
    let yes: Bool
    let faded: Bool
    let action: () -> Void

    private typealias P = WaxSealInvitePalette

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 14, weight: yes ? .bold : .semibold))
                .foregroundStyle(yes ? P.hex(0xFBF3E6) : P.hex(0x4A2A22))
                .lineLimit(1)
                .minimumScaleFactor(0.8)
                .frame(maxWidth: .infinity)
                .frame(height: 40)
                .background {
                    if yes {
                        Capsule()
                            .fill(P.hex(0x7C1A20))
                            .shadow(color: P.rgba(60, 10, 10, 0.35), radius: 1, x: 0, y: 1)
                            .shadow(color: P.rgba(60, 10, 10, 0.18), radius: 7, x: 0, y: 6)
                    } else {
                        Capsule()
                            .fill(P.rgba(255, 250, 240, 0.6))
                            .overlay(Capsule().strokeBorder(P.hex(0xB48F58), lineWidth: 1))
                    }
                }
                .opacity(faded ? 0.45 : 1)
        }
        .buttonStyle(WaxSealInvitePressStyle())
    }
}

/// Scale 0.97 while pressed, as the React Native Pressable.
private struct WaxSealInvitePressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.scaleEffect(configuration.isPressed ? 0.97 : 1)
    }
}

private struct WaxSealInviteCross: Shape {
    func path(in rect: CGRect) -> Path {
        let k = rect.width / 14
        var p = Path()
        p.move(to: CGPoint(x: 2 * k, y: 2 * k))
        p.addLine(to: CGPoint(x: 12 * k, y: 12 * k))
        p.move(to: CGPoint(x: 12 * k, y: 2 * k))
        p.addLine(to: CGPoint(x: 2 * k, y: 12 * k))
        return p
    }
}

/// A fixed path as a shape, drawn in the frame's own coordinates.
struct WaxSealInviteShape: Shape {
    let path: Path
    func path(in rect: CGRect) -> Path { path }
}

/// Paper (or liner) for a fixed path, coloured by the paper shader in the view's own points.
private struct WaxSealInvitePaper: View {
    let path: Path
    let tint: (Double, Double, Double)
    var liner = false

    var body: some View {
        WaxSealInviteShape(path: path)
            .fill(.white)
            .colorEffect(ShaderLibrary.waxSealInvitePaper(
                .float3(tint.0, tint.1, tint.2),
                .float(liner ? 1 : 0)
            ))
    }
}

// MARK: Envelope layers (static; drawn once, the flaps then turned as layers)

private struct WaxSealInviteEnvelopeBack: View, Equatable {
    let layout: WaxSealInviteLayout

    var body: some View {
        let env = layout.env
        let w = env.width
        let h = env.height
        ZStack(alignment: .topLeading) {
            // The envelope's two shadows.
            RoundedRectangle(cornerRadius: 12)
                .fill(Color(red: 8 / 255, green: 2 / 255, blue: 0, opacity: 0.55))
                .frame(width: w - 16, height: h)
                .blur(radius: 22)
                .offset(x: 8, y: 18)
            RoundedRectangle(cornerRadius: 10)
                .fill(Color(red: 8 / 255, green: 2 / 255, blue: 0, opacity: 0.45))
                .frame(width: w - 2, height: h)
                .blur(radius: 2.5)
                .offset(x: 1, y: 2)
            // The back with its liner, shaded at the top where the flap's hinge hides the light.
            ZStack(alignment: .topLeading) {
                WaxSealInvitePaper(path: Path(roundedRect: CGRect(x: 0, y: 0, width: w, height: h), cornerRadius: 10), tint: WaxSealInviteArt.cream)
                WaxSealInvitePaper(path: Path(CGRect(x: 5, y: 4, width: w - 10, height: h * 0.66)), tint: WaxSealInviteArt.liner, liner: true)
                WaxSealInviteShape(path: Path(CGRect(x: 5, y: 4, width: w - 10, height: h * 0.4)))
                    .fill(LinearGradient(
                        colors: [
                            Color(red: 20 / 255, green: 4 / 255, blue: 6 / 255, opacity: 0.55),
                            Color(red: 20 / 255, green: 4 / 255, blue: 6 / 255, opacity: 0),
                        ],
                        // From the envelope's top to 40 % of its height, as in React Native.
                        startPoint: .init(x: 0.5, y: 0), endPoint: .init(x: 0.5, y: 0.4)
                    ))
            }
            .frame(width: w, height: h, alignment: .topLeading)
        }
        .frame(width: w, height: h, alignment: .topLeading)
        .offset(x: env.minX, y: env.minY)
        .allowsHitTesting(false)
    }
}

private struct WaxSealInviteEnvelopeFront: View, Equatable {
    let layout: WaxSealInviteLayout

    var body: some View {
        let env = layout.env
        let w = env.width
        let h = env.height
        var fold = Path()
        fold.move(to: CGPoint(x: 6, y: h - 3))
        fold.addQuadCurve(to: CGPoint(x: w / 2, y: h * 0.64), control: CGPoint(x: w * 0.3, y: h * 0.7))
        fold.addQuadCurve(to: CGPoint(x: w - 6, y: h - 3), control: CGPoint(x: w * 0.7, y: h * 0.7))
        var lip = Path()
        lip.move(to: CGPoint(x: 0, y: 2))
        lip.addLine(to: CGPoint(x: w / 2, y: h * 0.5))
        lip.addLine(to: CGPoint(x: w, y: 2))
        return ZStack(alignment: .topLeading) {
            WaxSealInvitePaper(path: WaxSealInviteArt.frontPath(w: w, h: h), tint: WaxSealInviteArt.creamFront)
            // The bottom flap's edges: a fold casting a hair of shadow, with light below it.
            WaxSealInviteShape(path: fold)
                .stroke(Color(red: 90 / 255, green: 60 / 255, blue: 30 / 255, opacity: 0.14), lineWidth: 2.2)
                .blur(radius: 1.4)
                .offset(y: -1.2)
            WaxSealInviteShape(path: fold)
                .stroke(Color(red: 1, green: 252 / 255, blue: 244 / 255, opacity: 0.75), lineWidth: 0.8)
            // The V's edge: a lit paper edge over the dark inside.
            WaxSealInviteShape(path: lip)
                .stroke(Color(red: 1, green: 250 / 255, blue: 240 / 255, opacity: 0.9), lineWidth: 1)
        }
        .frame(width: w, height: h, alignment: .topLeading)
        .offset(x: env.minX, y: env.minY)
        .allowsHitTesting(false)
    }
}

private struct WaxSealInviteFlapOutside: View, Equatable {
    let layout: WaxSealInviteLayout

    var body: some View {
        let w = layout.env.width
        let fh = layout.flap
        let path = WaxSealInviteArt.flapPath(w: w, fh: fh)
        ZStack(alignment: .topLeading) {
            WaxSealInvitePaper(path: path, tint: WaxSealInviteArt.creamFlap)
            // Lighter toward the tip (it bulges a little), a lit edge along its lower sides.
            WaxSealInviteShape(path: path)
                .fill(LinearGradient(
                    stops: [
                        .init(color: Color(red: 120 / 255, green: 90 / 255, blue: 50 / 255, opacity: 0.12), location: 0),
                        .init(color: Color(red: 120 / 255, green: 90 / 255, blue: 50 / 255, opacity: 0.02), location: 0.35),
                        .init(color: Color(red: 1, green: 1, blue: 250 / 255, opacity: 0.12), location: 1),
                    ],
                    // From the hinge to the flap's depth (the frame is 4 pt taller).
                    startPoint: .init(x: 0.5, y: 0), endPoint: .init(x: 0.5, y: fh / (fh + 4))
                ))
            WaxSealInviteShape(path: path)
                .stroke(Color(red: 1, green: 253 / 255, blue: 246 / 255, opacity: 0.9), lineWidth: 1)
        }
        .frame(width: w, height: fh + 4, alignment: .topLeading)
    }
}

private struct WaxSealInviteFlapInside: View, Equatable {
    let layout: WaxSealInviteLayout

    var body: some View {
        let w = layout.env.width
        let fh = layout.flap
        let path = WaxSealInviteArt.flapPath(w: w, fh: fh)
        let lined = path.applying(CGAffineTransform(a: 0.95, b: 0, c: 0, d: 0.9, tx: w * 0.025, ty: 4))
        ZStack(alignment: .topLeading) {
            WaxSealInvitePaper(path: path, tint: WaxSealInviteArt.cream)
            WaxSealInvitePaper(path: lined, tint: WaxSealInviteArt.liner, liner: true)
        }
        .frame(width: w, height: fh + 4, alignment: .topLeading)
    }
}

// MARK: The seal

/// Cracks, wedges and directions, fixed per radius.
struct WaxSealInviteGeometry {
    var cracks: [[CGPoint]]
    var wedges: [Path]
    var mids: [Double]

    init(R: Double) {
        typealias M = WaxSealInviteMotion
        cracks = M.makeCracks(R)
        let c = cracks
        wedges = (0..<M.cracks).map { M.wedge(c, $0, R: R) }
        mids = (0..<M.cracks).map { M.pieceAngle(c, $0) }
    }
}

/// The wax seal on the flap's tip: whole, or its seven pieces under wedge clips, each thrown by
/// its own motion; the cracks drawn over it.
private struct WaxSealInviteSeal: View {
    let layout: WaxSealInviteLayout
    let geometry: WaxSealInviteGeometry
    let mask: UIImage
    let pose: WaxSealInvitePose

    private typealias M = WaxSealInviteMotion

    var body: some View {
        let R = layout.sealR
        let side = layout.sealSide
        let half = side / 2
        let toBox = CGAffineTransform(translationX: half, y: half)
        let crackOpacity = max(0, 1 - pose.fall * 6)
        ZStack(alignment: .topLeading) {
            Circle()
                .fill(Color(red: 60 / 255, green: 10 / 255, blue: 10 / 255))
                .frame(width: R * 1.96, height: R * 1.96)
                .blur(radius: 4)
                .opacity(0.4 * max(0, 1 - pose.fall * 5))
                .offset(x: half - R * 0.98 + 1, y: half - R * 0.98 + 3.5)
            if !pose.broken {
                wax
            } else {
                ForEach(0..<M.cracks, id: \.self) { i in
                    let m = M.pieceMotion(i, mid: geometry.mids[i], fall: pose.fall, R: R)
                    wax
                        .clipShape(WaxSealInviteShape(path: geometry.wedges[i].applying(toBox)))
                        .rotationEffect(.radians(m.rot), anchor: UnitPoint(x: (m.px + half) / side, y: (m.py + half) / side))
                        .offset(x: m.dx, y: m.dy)
                }
            }
            if crackOpacity > 0.001 && pose.crack > 0.001 {
                let lines = M.crackPath(geometry.cracks, amount: pose.crack).applying(toBox)
                ZStack(alignment: .topLeading) {
                    WaxSealInviteShape(path: lines)
                        .stroke(Color(red: 42 / 255, green: 5 / 255, blue: 7 / 255),
                                style: StrokeStyle(lineWidth: 1.2, lineCap: .round, lineJoin: .round))
                    WaxSealInviteShape(path: lines)
                        .stroke(Color(red: 212 / 255, green: 100 / 255, blue: 106 / 255),
                                style: StrokeStyle(lineWidth: 0.6, lineCap: .round))
                        .offset(x: 0.6, y: 0.8)
                }
                .frame(width: side, height: side, alignment: .topLeading)
                .opacity(crackOpacity)
            }
        }
        .frame(width: side, height: side, alignment: .topLeading)
        .scaleEffect(1 - 0.05 * pose.press)
        .offset(x: layout.seal.x - half, y: layout.seal.y + pose.pull - half)
        .allowsHitTesting(false)
    }

    private var wax: some View {
        Image(uiImage: mask)
            .resizable()
            .frame(width: layout.sealSide, height: layout.sealSide)
            .layerEffect(
                ShaderLibrary.waxSealInviteWax(
                    .float(layout.sealR),
                    .float(WaxSealInviteLayout.sealPad),
                    .float(pose.gleam)
                ),
                maxSampleOffset: CGSize(width: 1, height: 1)
            )
    }
}

// MARK: The inbox under the modal

private struct WaxSealInviteInbox: View {
    let top: Double

    private static let mail = [
        ("Studio Morrow", "Your proofs are ready to review", "9:41"),
        ("Ada Brennan", "Train times for Saturday", "Yesterday"),
        ("Lena & Theo", "Something special in the post", "Yesterday"),
        ("Harbor Books", "Your order has shipped", "Tue"),
        ("Nico Alvarez", "Dinner on Friday?", "Mon"),
        ("Pine & Plaster", "Quote for the hallway", "Sun"),
    ]

    private typealias P = WaxSealInvitePalette

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Inbox")
                .font(.system(size: 34, weight: .heavy))
                .foregroundStyle(P.hex(0x1D1714))
                .padding(.bottom, 8)
            ForEach(0..<Self.mail.count, id: \.self) { i in
                let m = Self.mail[i]
                HStack(alignment: .top, spacing: 12) {
                    Circle().fill(P.hex(0xCDB9A6)).frame(width: 40, height: 40)
                    VStack(alignment: .leading, spacing: 2) {
                        HStack {
                            Text(m.0)
                                .font(.system(size: 16, weight: .bold))
                                .foregroundStyle(P.hex(0x1D1714))
                            Spacer(minLength: 8)
                            Text(m.2)
                                .font(.system(size: 13))
                                .foregroundStyle(P.hex(0x8A7D72))
                        }
                        Text(m.1)
                            .font(.system(size: 14))
                            .foregroundStyle(P.hex(0x5D524A))
                    }
                }
                .padding(.vertical, 6)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 20)
        .padding(.top, top + 12)
        .background(P.background)
    }
}

// MARK: Engine

/// Who owns the screen (the loop or the finger), and the finger's state: the seal whole (pressed,
/// pulled, hairline cracks) or broken (a time on the loop's own script, played forward).
@MainActor
final class WaxSealInviteEngine {
    private typealias M = WaxSealInviteMotion

    // The user's timeline runs the loop's own script: the break starts it at breakAt (cracks a
    // third grown, as the pull left them), it opens until openAt and closes from closeAt to loop.
    private static let breakAt = 2.48
    private static let openAt = 6.0
    private static let closeAt = 7.6
    // The seal breaks past breakPull pt of pull or after breakHold s held.
    private static let breakPull = 60.0
    private static let breakHold = 0.7
    private static let idle = 2.0
    private static let reset = 0.9

    private var cachedGeometry: WaxSealInviteGeometry?
    private var cachedR = -1.0

    private var configured = false
    private var reduced = false
    private var loopStart: Double?
    private var clock = 0.0
    private var user = false
    private var lastTouch = 0.0
    private var resetting = false
    private var pressing = false
    private var pressStart = 0.0
    private var openedAt = -1.0
    private var wasOpen = false
    private(set) var isOpen = false

    private var broken = false
    /// The script's time while broken, and whether it is playing toward the end of the loop.
    private var scriptT = WaxSealInviteTrack(0)
    private var closing = false
    private var press = WaxSealInviteTrack(0)
    private var pull = WaxSealInviteTrack(0)
    private var crack = WaxSealInviteTrack(0)

    private let impact = UIImpactFeedbackGenerator(style: .light)

    func geometry(R: Double) -> WaxSealInviteGeometry {
        if let cachedGeometry, cachedR == R { return cachedGeometry }
        let g = WaxSealInviteGeometry(R: R)
        cachedGeometry = g
        cachedR = R
        return g
    }

    struct Frame {
        var pose: WaxSealInvitePose
        /// The card is out with its buttons shown.
        var open: Bool
        var openedNow: Bool
        var closedNow: Bool
        /// Nothing is moving and nothing will until the next touch.
        var settled: Bool
    }

    private func configure(reduced: Bool) {
        guard !configured || reduced != self.reduced else { return }
        configured = true
        self.reduced = reduced
        user = false
        broken = false
        pressing = false
        resetting = false
        closing = false
        loopStart = nil
        clock = 0
        press.set(0)
        pull.set(0)
        crack.set(0)
    }

    func frame(at now: Double, autoPlay: Bool, reduced: Bool) -> Frame {
        configure(reduced: reduced)
        var settled = false
        let pose: WaxSealInvitePose
        if reduced {
            pose = M.openPose
            settled = true
        } else {
            if user { step(now) }
            if !user {
                if autoPlay {
                    let start = loopStart ?? now
                    loopStart = start
                    clock = (now - start).truncatingRemainder(dividingBy: M.loop)
                } else {
                    loopStart = nil
                    clock = 0
                }
                settled = !autoPlay
                pose = M.loopPose(clock)
            } else if broken {
                pose = M.loopPose(scriptT.value(at: now))
            } else {
                var p = WaxSealInvitePose()
                p.press = press.value(at: now)
                p.pull = pull.value(at: now)
                p.crack = crack.value(at: now)
                pose = p
            }
        }
        let open = pose.card > 1.9 && pose.buttons > 0.5
        let openedNow = open && !wasOpen && user && !reduced
        let closedNow = !open && wasOpen
        wasOpen = open
        isOpen = open
        return Frame(pose: pose, open: open, openedNow: openedNow, closedNow: closedNow, settled: settled)
    }

    private func step(_ t: Double) {
        if broken && closing && scriptT.done(at: t) { finishClose(t) }
        if pressing && !broken {
            let held = t - pressStart
            crack.set(max(crack.value(at: t), min(0.3, held / Self.breakHold * 0.3)))
            if held >= Self.breakHold { breakSeal(t) }
            return
        }
        let u = scriptT.value(at: t)
        let open = broken && u > Self.openAt - 0.001 && u < Self.closeAt
        if !open {
            openedAt = -1
        } else if openedAt < 0 {
            openedAt = t
        }
        if resetting || t - max(lastTouch, openedAt) < Self.idle { return }
        if !broken {
            user = false
            clock = 0
            loopStart = t
        } else if open {
            // An open invitation closes itself, squeezed into `reset` s.
            resetting = true
            scriptT.set(Self.closeAt)
            play(M.loop, squeezed: Self.reset, now: t)
        }
    }

    private func finishClose(_ t: Double) {
        broken = false
        closing = false
        press.set(0)
        pull.set(0)
        crack.set(0)
        if resetting {
            resetting = false
            clock = 0
            loopStart = t
            user = false
        }
    }

    /// Runs the script from where it is to `to`, at real speed or squeezed into `squeezed` s.
    private func play(_ to: Double, squeezed: Double? = nil, now: Double) {
        let from = scriptT.value(at: now)
        let duration = squeezed ?? max(0, to - from)
        scriptT.timing(to, duration: duration, curve: squeezed == nil ? .linear : .easeInOut, now: now)
        closing = to >= M.loop
    }

    /// Takes over from the loop where it is; a moving envelope finishes the move it was making.
    func touched(now: Double) {
        lastTouch = now
        if resetting || user { return }
        user = true
        let t = clock
        if t < 2.4 {
            let p = M.loopPose(t)
            broken = false
            press.set(p.press)
            pull.set(p.pull)
            crack.set(p.crack)
            press.timing(0, duration: 0.25, now: now)
            pull.spring(0, duration: 0.45, dampingRatio: 0.6, now: now)
            crack.timing(0, duration: 0.3, now: now)
            return
        }
        broken = true
        scriptT.set(t)
        closing = false
        if t < Self.openAt {
            play(Self.openAt, now: now)
        } else if t >= Self.closeAt {
            play(M.loop, now: now)
        }
    }

    private func breakSeal(_ now: Double) {
        pressing = false
        broken = true
        scriptT.set(Self.breakAt)
        play(Self.openAt, now: now)
        impact.impactOccurred()
    }

    func close(now: Double) {
        touched(now: now)
        let u = scriptT.value(at: now)
        if !broken || u < Self.openAt - 0.01 { return }
        if u < Self.closeAt { scriptT.set(Self.closeAt) }
        play(M.loop, now: now)
    }

    func sealBegan(now: Double) {
        touched(now: now)
        if broken { return }
        pressing = true
        pressStart = now
        press.timing(1, duration: 0.12, now: now)
    }

    func sealMoved(pull dy: Double, now: Double) {
        if broken || !pressing { return }
        lastTouch = now
        let raw = max(0, dy)
        pull.set(M.rubber(raw, M.pullLimit))
        crack.set(max(crack.value(at: now), min(0.3, raw / Self.breakPull * 0.3)))
        if raw > Self.breakPull { breakSeal(now) }
    }

    func sealEnded(now: Double) {
        lastTouch = now
        if !pressing || broken { return }
        pressing = false
        press.timing(0, duration: 0.2, now: now)
        pull.spring(0, duration: 0.45, dampingRatio: 0.55, now: now)
        crack.timing(0, duration: 0.35, now: now)
    }
}
