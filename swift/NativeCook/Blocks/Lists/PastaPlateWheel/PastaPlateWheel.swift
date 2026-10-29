import SwiftUI
import UIKit

/// The pasta menu of Trattoria Lumetta, an invented restaurant. A big black disc sits half off the
/// left edge with eight plates of pasta on its rim; the wheel turns and the plates ride it, each
/// food spinning on its own plate as it rolls, while the plate at the right middle lifts out of
/// the wheel (larger, deeper shadow) as the selection. The selected dish's number, name, line and
/// price roll in below like drums, a little after the wheel.
///
/// Drag up or down to turn the wheel (it carries the finger's speed and snaps to a plate); tap a
/// plate to bring it to the front, tap the front plate to pop it. 2 s after the last touch the
/// wheel springs back to the first dish and the loop restarts. Reduced motion: the first dish, still.
struct PastaPlateWheel: View {
    static let loop = PastaPlateWheelMotion.loop

    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reducedMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var epoch = Date()
    @State private var touch = PastaPlateWheelTouch()
    @State private var awake = false
    @State private var bagCount = 2

    private typealias M = PastaPlateWheelMotion
    private typealias Art = PastaPlateWheelArt

    var body: some View {
        GeometryReader { outer in
            let safeTop = outer.safeAreaInsets.top
            let safeBottom = outer.safeAreaInsets.bottom
            GeometryReader { geometry in
                let k = geometry.size.width / 393
                let height = geometry.size.height / k
                let cy = M.centreY(safeTop: safeTop / k, height: height, safeBottom: safeBottom / k)
                TimelineView(.animation(paused: reducedMotion || scenePhase != .active || !(autoPlay || awake))) { timeline in
                    let clock = max(0, timeline.date.timeIntervalSince(epoch))
                    scene(clock: clock, k: k, height: height, cy: cy, safeTop: safeTop / k, safeBottom: safeBottom / k)
                }
                .contentShape(Rectangle())
                .gesture(drag(k: k, cy: cy))
            }
            .ignoresSafeArea()
        }
        .background(Art.color(0xECEBE7).ignoresSafeArea())
        .preferredColorScheme(.light)
        .task(id: awake) {
            guard awake, !autoPlay else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(250))
                let clock = Date().timeIntervalSince(epoch)
                if !touch.touching, touch.release != nil, clock > touch.restsAfter {
                    awake = false
                    return
                }
            }
        }
    }

    private func phi(_ clock: Double) -> Double {
        reducedMotion ? 0 : touch.phi(clock, autoPlay: autoPlay)
    }

    // MARK: Scene

    @ViewBuilder
    private func scene(clock: Double, k: CGFloat, height: Double, cy: Double, safeTop: Double, safeBottom: Double) -> some View {
        let wheel = phi(clock)
        let infoTop = M.infoTop(height: height, safeBottom: safeBottom)
        ZStack(alignment: .topLeading) {
            Image(uiImage: Art.atlas.grain)
                .resizable(resizingMode: .tile)
                .opacity(0.035)
                .allowsHitTesting(false)
            Canvas { context, _ in
                context.scaleBy(x: k, y: k)
                drawWheel(context, phi: wheel, cy: cy, clock: clock)
            }
            .allowsHitTesting(false)
            ZStack(alignment: .topLeading) {
                pill.position(x: -24 + 80, y: cy)
                header.frame(width: 393).position(x: 393 / 2, y: safeTop + 8 + 22)
                Text("Trattoria Lumetta · 8 dishes")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(Art.color(0x8A867D))
                    .frame(width: 393)
                    .position(x: 393 / 2, y: safeTop + 58)
                    .allowsHitTesting(false)
                info(clock: clock, infoTop: infoTop)
            }
            .frame(width: 393, height: height, alignment: .topLeading)
            .scaleEffect(k, anchor: .topLeading)
        }
    }

    private func drawWheel(_ context: GraphicsContext, phi: Double, cy: Double, clock: Double) {
        let cx = M.centreX
        let disc = M.discRadius
        let discRect = CGRect(x: cx - disc, y: cy - disc, width: 2 * disc, height: 2 * disc)
        context.fill(Path(ellipseIn: discRect), with: .color(Art.color(0x151413)))
        context.fill(Path(ellipseIn: discRect), with: .radialGradient(
            Gradient(stops: [.init(color: Art.color(0x2B2723, 0.95), location: 0), .init(color: Art.color(0x2B2723, 0), location: 1)]),
            center: CGPoint(x: cx + 120, y: cy - 40), startRadius: 0, endRadius: 260))
        for (r, a) in [(M.orbit - 72, 0.045), (M.orbit - 112, 0.03)] {
            context.stroke(Path(ellipseIn: CGRect(x: cx - r, y: cy - r, width: 2 * r, height: 2 * r)),
                           with: .color(.white.opacity(a)), lineWidth: 1.5)
        }
        context.stroke(Path(ellipseIn: discRect.insetBy(dx: 0.5, dy: 0.5)), with: .color(.white.opacity(0.07)), lineWidth: 1)
        for j in 0..<M.count {
            let a = (Double(j) + 0.5 - phi) * Double.pi / 4
            let x = cx + M.orbit * cos(a), y = cy + M.orbit * sin(a)
            context.fill(Path(ellipseIn: CGRect(x: x - 2.5, y: y - 2.5, width: 5, height: 5)), with: .color(.white.opacity(0.18)))
        }

        let atlas = Art.atlas
        let pop = M.pop(clock - touch.popClock)
        for p in M.plates(phi: phi, cy: cy, popIndex: touch.popIndex, pop: pop) {
            if p.x + p.size / 2 < -4 { continue }
            let dish = Art.dishes[p.index]
            let half = p.size / 2
            let f = p.front
            // Ambient shadow (light from the upper left): grows and softens as the plate lifts.
            let blur = p.size * 0.06 + 10 * f
            let outer = half + blur
            let shadowCentre = CGPoint(x: p.x + 4 + 6 * f, y: p.y + 8 + 12 * f)
            let alpha = 0.22 + 0.2 * f
            context.fill(Path(ellipseIn: CGRect(x: shadowCentre.x - outer, y: shadowCentre.y - outer, width: 2 * outer, height: 2 * outer)),
                         with: .radialGradient(Gradient(stops: [
                            .init(color: Art.color(0x2A2118, alpha), location: 0),
                            .init(color: Art.color(0x2A2118, alpha), location: max(0, (half - blur * 0.6) / outer)),
                            .init(color: Art.color(0x2A2118, 0), location: 1),
                         ]), center: shadowCentre, startRadius: 0, endRadius: outer))
            // Contact shadow: tight, fades as the plate lifts.
            let contact = half + 2.5
            let contactCentre = CGPoint(x: p.x + 1, y: p.y + 2)
            context.fill(Path(ellipseIn: CGRect(x: contactCentre.x - contact, y: contactCentre.y - contact, width: 2 * contact, height: 2 * contact)),
                         with: .radialGradient(Gradient(stops: [
                            .init(color: Art.color(0x1A140E, 0.32 - 0.15 * f), location: 0),
                            .init(color: Art.color(0x1A140E, 0.32 - 0.15 * f), location: (half - 2.5) / contact),
                            .init(color: Art.color(0x1A140E, 0), location: 1),
                         ]), center: contactCentre, startRadius: 0, endRadius: contact))

            let plate = context.resolve(Image(uiImage: atlas.plates[dish.bowl ? 1 : 0]))
            context.draw(plate, in: CGRect(x: p.x - half, y: p.y - half, width: p.size, height: p.size))
            let foodSide = p.size * Double(Art.foodFraction(bowl: dish.bowl)) * 1.08
            var spun = context
            spun.translateBy(x: p.x, y: p.y)
            spun.rotate(by: .radians(p.spin))
            spun.draw(context.resolve(Image(uiImage: atlas.foods[p.index])),
                      in: CGRect(x: -foodSide / 2, y: -foodSide / 2, width: foodSide, height: foodSide))
            context.draw(context.resolve(Image(uiImage: atlas.lights[dish.bowl ? 1 : 0])),
                         in: CGRect(x: p.x - foodSide / 2, y: p.y - foodSide / 2, width: foodSide, height: foodSide))
        }
    }

    // MARK: Chrome

    private var pill: some View {
        HStack(spacing: 0) {
            Text("Select Pasta")
                .font(.system(size: 15, weight: .medium))
                .fixedSize()
                .foregroundStyle(Art.color(0x1B1A17))
            Spacer(minLength: 0)
            Path { p in
                p.move(to: CGPoint(x: 0, y: 0))
                p.addLine(to: CGPoint(x: 4, y: 4.5))
                p.addLine(to: CGPoint(x: 0, y: 9))
            }
            .stroke(Art.color(0x1B1A17), style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
            .frame(width: 4, height: 9)
        }
        .padding(.leading, 42)
        .padding(.trailing, 16)
        .frame(width: 160, height: 44)
        .background(Capsule().fill(Art.color(0xFBFAF7)))
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private var header: some View {
        ZStack {
            Text("Delicious Pasta")
                .font(.system(size: 22, weight: .bold))
                .foregroundStyle(Art.color(0x1B1A17))
                .accessibilityAddTraits(.isHeader)
            HStack {
                VStack(alignment: .leading, spacing: 6) {
                    ForEach([22.0, 14.0, 18.0], id: \.self) { w in
                        Capsule().fill(Art.color(0x1B1A17)).frame(width: w, height: 2.2)
                    }
                }
                .frame(width: 44, height: 44, alignment: .leading)
                .accessibilityLabel("Menu")
                Spacer()
                bag
            }
            .padding(.leading, 24)
            .padding(.trailing, 20)
        }
        .frame(height: 44)
    }

    private var bag: some View {
        ZStack(alignment: .topTrailing) {
            Circle()
                .fill(Art.color(0xFBFAF7))
                .shadow(color: .black.opacity(0.12), radius: 3, y: 2)
                .frame(width: 42, height: 42)
                .overlay {
                    Path { p in
                        p.addRoundedRect(in: CGRect(x: 0, y: 5, width: 16, height: 14), cornerSize: CGSize(width: 3, height: 3))
                        p.move(to: CGPoint(x: 4.5, y: 7.5))
                        p.addLine(to: CGPoint(x: 4.5, y: 4))
                        p.addArc(center: CGPoint(x: 8, y: 4), radius: 3.5, startAngle: .degrees(180), endAngle: .degrees(360), clockwise: false)
                        p.addLine(to: CGPoint(x: 11.5, y: 7.5))
                    }
                    .stroke(Art.color(0x1B1A17), style: StrokeStyle(lineWidth: 1.8, lineCap: .round, lineJoin: .round))
                    .frame(width: 16, height: 19)
                }
            Text("\(bagCount)")
                .font(.system(size: 11, weight: .bold).monospacedDigit())
                .foregroundStyle(.white)
                .frame(minWidth: 18, minHeight: 18)
                .background(Circle().fill(Art.color(0xC8472B)))
                .keyframeAnimator(initialValue: 1.0, trigger: bagCount) { content, s in
                    content.scaleEffect(s)
                } keyframes: { _ in
                    SpringKeyframe(1.35, duration: 0.12, spring: .snappy)
                    SpringKeyframe(1.0, duration: 0.45, spring: Spring(response: 0.45, dampingRatio: 0.5))
                }
                .offset(x: 4, y: -3)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Order, \(bagCount) items")
    }

    private func info(clock: Double, infoTop: Double) -> some View {
        let names = Art.dishes.map(\.name)
        let lines = Art.dishes.map(\.line)
        let prices = Art.dishes.map(\.price)
        let counters = (1...Art.dishes.count).map { String(format: "%02d / %02d", $0, Art.dishes.count) }
        let selected = ((Int(phi(clock).rounded()) % 8) + 8) % 8
        return ZStack(alignment: .topLeading) {
            Color.clear
                .frame(width: 345, height: 90)
                .position(x: 24 + 172, y: infoTop + 45)
                .accessibilityElement()
                .accessibilityLabel("\(names[selected]), \(lines[selected]), \(prices[selected])")
            PastaPlateWheelDrum(items: counters, phi: phi(clock), row: 16,
                                font: .system(size: 12, weight: .semibold).monospacedDigit(), color: Art.color(0xC8472B))
                .position(x: 24 + 160, y: infoTop + 8)
            PastaPlateWheelDrum(items: names, phi: phi(clock - 0.04), row: 36,
                                font: .system(size: 28, weight: .semibold, design: .serif), color: Art.color(0x1B1A17))
                .position(x: 24 + 160, y: infoTop + 20 + 18)
            PastaPlateWheelDrum(items: lines, phi: phi(clock - 0.08), row: 20,
                                font: .system(size: 14), color: Art.color(0x7A766E))
                .position(x: 24 + 160, y: infoTop + 60 + 10)
            PastaPlateWheelDrum(items: prices, phi: phi(clock - 0.12), row: 32,
                                font: .system(size: 26, weight: .bold).monospacedDigit(), color: Art.color(0x1B1A17), width: 140)
                .position(x: 24 + 70, y: infoTop + 96 + 28)
            Button {
                bagCount += 1
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
            } label: {
                HStack(spacing: 12) {
                    ZStack {
                        Circle().fill(Art.color(0xC8472B))
                        Path { p in
                            p.move(to: CGPoint(x: 5, y: 0)); p.addLine(to: CGPoint(x: 5, y: 10))
                            p.move(to: CGPoint(x: 0, y: 5)); p.addLine(to: CGPoint(x: 10, y: 5))
                        }
                        .stroke(.white, style: StrokeStyle(lineWidth: 2, lineCap: .round))
                        .frame(width: 10, height: 10)
                    }
                    .frame(width: 28, height: 28)
                    Text("Add to order")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(Art.color(0xFBFAF7))
                    Spacer(minLength: 0)
                }
                .padding(.leading, 14)
                .frame(width: 196, height: 56)
                .background(Capsule().fill(Art.color(0x1B1A17)))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Add \(names[selected]) to order")
            .position(x: 393 - 20 - 98, y: infoTop + 96 + 28)
        }
        .frame(width: 393, alignment: .topLeading)
    }

    // MARK: Touch

    private func drag(k: CGFloat, cy: Double) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                guard !reducedMotion else { return }
                let clock = Date().timeIntervalSince(epoch)
                if !touch.touching {
                    touch.begin(clock: clock, autoPlay: autoPlay)
                    if !awake { awake = true }
                }
                if touch.move(dx: value.translation.width / k, dy: value.translation.height / k) {
                    UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: 0.7)
                }
            }
            .onEnded { value in
                guard !reducedMotion, touch.touching else { return }
                let clock = Date().timeIntervalSince(epoch)
                let point = CGPoint(x: value.location.x / k, y: value.location.y / k)
                switch touch.end(clock: clock, velocityY: value.velocity.height / k, point: point, cy: cy) {
                case .none: break
                case .pop: UIImpactFeedbackGenerator(style: .medium).impactOccurred(intensity: 0.6)
                default: UIImpactFeedbackGenerator(style: .light).impactOccurred()
                }
            }
    }
}

/// A line of text on a drum: rows sit `row` pt apart and roll with φ, fading away from the middle.
struct PastaPlateWheelDrum: View {
    let items: [String]
    let phi: Double
    let row: CGFloat
    let font: Font
    let color: Color
    var width: CGFloat = 320

    var body: some View {
        let base = Int(floor(phi))
        ZStack(alignment: .leading) {
            ForEach(-1...2, id: \.self) { o in
                let j = base + o
                let d = Double(j) - phi
                Text(items[((j % items.count) + items.count) % items.count])
                    .font(font)
                    .foregroundStyle(color)
                    .lineLimit(1)
                    .fixedSize()
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
                    .offset(y: d * row)
                    .opacity(max(0, 1 - 2.2 * abs(d)))
            }
        }
        .frame(width: width, height: row)
        .clipped()
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}
