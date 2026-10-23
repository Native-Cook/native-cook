import SwiftUI
import UIKit

struct HoloAgendaCarouselDemo: View {
    var body: some View {
        HoloAgendaCarousel(autoPlay: true)
    }
}

/// A continuously wrapping agenda. Disable autoPlay to begin in the interactive rest state.
struct HoloAgendaCarousel: View {
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var motion = HoloAgendaCarouselMotion()

    var body: some View {
        GeometryReader { geometry in
            let k = min(1, geometry.size.width / 402, geometry.size.height / 778)
            let left = (geometry.size.width - 402 * k) / 2
            TimelineView(.animation) { timeline in
                let frame = motion.tick(timeline.date, autoPlay: autoPlay, reduced: reduceMotion)
                HoloAgendaCarouselStage(frame: frame, k: k, reduced: reduceMotion)
                    .frame(width: 402 * k, height: 778 * k)
                    .offset(x: left)
                    .frame(width: geometry.size.width, height: geometry.size.height, alignment: .topLeading)
                    .overlay {
                        HoloAgendaCarouselTouchSurface(k: k, left: left, motion: motion)
                            .accessibilityRepresentation {
                                VStack {
                                    Text("IRIDIA SUMMIT. OCT 14–15.")
                                    Text("Agenda")
                                        .accessibilityValue("\(HoloAgendaCarouselTheme.sessions[frame.centre].title), \(frame.centre + 1) of 4")
                                        .accessibilityAdjustableAction { direction in
                                            motion.move(direction == .increment ? 1 : -1)
                                        }
                                    Text(HoloAgendaCarouselTheme.sessions[frame.centre].overline + ". "
                                         + HoloAgendaCarouselTheme.sessions[frame.centre].body + " "
                                         + HoloAgendaCarouselTheme.sessions[frame.centre].name + ", "
                                         + HoloAgendaCarouselTheme.sessions[frame.centre].role)
                                    Button(frame.selected ? "In your agenda" : "Add to agenda") { motion.toggle() }
                                        .accessibilityValue(frame.selected ? "Selected" : "Not selected")
                                        .accessibilityAddTraits(frame.selected ? .isSelected : [])
                                }
                            }
                    }
            }
        }
        .background(Color.black.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .statusBarHidden()
    }
}

private struct HoloAgendaCarouselStage: View {
    typealias Theme = HoloAgendaCarouselTheme
    let frame: HoloAgendaCarouselMotion.Frame
    let k: Double
    let reduced: Bool
    private let dim = Color(red: 142 / 255, green: 142 / 255, blue: 153 / 255)

    private func grid(_ n: Double) -> Double { (n * k / 4).rounded() * 4 }

    var body: some View {
        ZStack(alignment: .topLeading) {
            backdrop
            HStack {
                Text("IRIDIA SUMMIT")
                    .font(.system(size: 13 * k, weight: .bold))
                    .tracking(1.6 * k)
                    .foregroundStyle(.white.opacity(235 / 255))
                Spacer()
                Text("OCT 14–15")
                    .font(.custom("Menlo", size: 12 * k))
                    .foregroundStyle(dim)
            }
            .frame(width: 354 * k, height: 28 * k)
            .offset(x: 24 * k, y: grid(12))

            ForEach(0..<4, id: \.self) { index in
                card(index)
                    .zIndex(Theme.pose(Theme.wrap(Double(index) - frame.position)).z)
            }
            pager
                .offset(y: grid(408) - 3 * k)
            ForEach(0..<4, id: \.self) { index in
                details(index)
            }
            agendaButton
                .offset(x: 24 * k, y: grid(706))
        }
        .accessibilityHidden(true)
    }

    private func material(_ index: Int, rect: CGRect, radius: Double = 20,
                          detail: Double = 0, age: Double = 900, fade: Double = 0,
                          accent: Bool = false) -> Shader {
        let shift = Theme.wrap(Double(index) - frame.position)
        let clock = accent ? 0 : frame.clock
        let loop = Theme.mod(clock, Theme.loop) / Theme.loop
        let angle = loop * .pi * 2
        let surge = accent ? 0 : frame.surge[index]
        return ShaderLibrary.holoAgendaMaterial(
            .float2(rect.origin), .float2(rect.size), .float(radius), .float(Double(index)),
            .float(Theme.mod(loop + shift + surge + Theme.phases[index], 1)),
            .float(shift), .float(accent ? 0 : frame.velocity),
            .float(index == frame.centre ? frame.arrival : 0),
            .float2(cos(angle), sin(angle)), .float(detail), .float(age), .float(fade)
        )
    }

    private func follow(_ index: Int) -> Double {
        index == frame.centre ? Theme.arrivalScale(frame.age) : 1
    }

    private func card(_ index: Int) -> some View {
        let pose = Theme.pose(Theme.wrap(Double(index) - frame.position))
        let pressed = frame.pressed == index ? frame.cardPress : 1
        let scale = k * pose.side / 192 * follow(index) * frame.squeeze * pressed
        let rect = CGRect(x: 0, y: 0, width: 192, height: 192)
        return ZStack {
            Canvas { context, _ in
                context.fill(Path(roundedRect: rect, cornerRadius: 20),
                             with: .shader(material(index, rect: rect, detail: 1,
                                                    age: index == frame.centre ? frame.age : 900), bounds: rect))
            }
            Text(Theme.sessions[index].label)
                .font(.system(size: 26, weight: .bold))
                .tracking(-0.5)
                .foregroundStyle(Color(red: 11 / 255, green: 11 / 255, blue: 16 / 255))
                .lineLimit(1)
        }
        .frame(width: 192, height: 192)
        .shadow(color: .black.opacity(0.5), radius: 16, x: 0, y: 8)
        .scaleEffect(scale)
        .rotation3DEffect(.degrees(reduced ? 0 : Theme.clamp(-frame.velocity * 2.2, -11, 11)),
                          axis: (x: 0, y: 1, z: 0), perspective: 192 / 700)
        .opacity(pose.opacity)
        .position(x: 201 * k + pose.x * k, y: grid(264))
    }

    private var backdrop: some View {
        Canvas { context, size in
            var base = context
            base.scaleBy(x: k, y: k)
            let centre = grid(264) / k
            let order = (0..<4).sorted {
                Theme.pose(Theme.wrap(Double($0) - frame.position)).z
                    < Theme.pose(Theme.wrap(Double($1) - frame.position)).z
            }
            let rect = CGRect(x: 0, y: 0, width: 192, height: 192)
            let shape = Path(roundedRect: rect, cornerRadius: 20)
            for index in order {
                let pose = Theme.pose(Theme.wrap(Double(index) - frame.position))
                let side = pose.side * frame.squeeze * follow(index)
                var reflection = base
                reflection.opacity = pose.opacity * 0.26
                reflection.translateBy(x: 201 + pose.x - side / 2, y: centre + side / 2 + 12 + side)
                reflection.scaleBy(x: side / 192, y: -side / 192)
                maskedMaterial(reflection, shape: shape,
                               shader: material(index, rect: rect, fade: 36 * 192 / side), blur: 3)
            }
            for wide in [true, false] {
                for index in 0..<4 {
                    let pose = Theme.pose(Theme.wrap(Double(index) - frame.position))
                    let pulse = index == frame.centre ? frame.arrival : 0
                    let spread = wide ? 1.1 + pulse * 0.08 : 1.02
                    let side = pose.side * spread * frame.squeeze * follow(index)
                    var glow = base
                    glow.opacity = pose.opacity * (wide ? 0.26 : 0.55) * (1 + pulse * 0.8)
                    glow.translateBy(x: 201 + pose.x - side / 2, y: centre - side / 2)
                    glow.scaleBy(x: side / 192, y: side / 192)
                    maskedMaterial(glow, shape: shape, shader: material(index, rect: rect), blur: wide ? 34 : 10)
                }
            }
            let gradient = Gradient(stops: [
                .init(color: .black, location: 0), .init(color: .black.opacity(0.95), location: 0.15),
                .init(color: .black.opacity(0.5), location: 0.5), .init(color: .clear, location: 1)
            ])
            for right in [false, true] {
                let x = right ? size.width : 0
                context.fill(Path(CGRect(x: right ? size.width - 32 * k : 0, y: 0,
                                         width: 32 * k, height: size.height)),
                             with: .linearGradient(gradient, startPoint: CGPoint(x: x, y: 0),
                                                   endPoint: CGPoint(x: x + (right ? -32 : 32) * k, y: 0)))
            }
        }
        .frame(width: 402 * k, height: grid(480))
        .allowsHitTesting(false)
    }

    /// Skia's BlurMask softens coverage, not the foil's colors. Keep the field sharp inside
    /// a separately blurred alpha mask so a glow does not turn into an averaged white haze.
    private func maskedMaterial(_ context: GraphicsContext, shape: Path, shader: Shader, blur: Double) {
        context.drawLayer { layer in
            var mask = layer
            mask.opacity = 1
            mask.addFilter(.blur(radius: blur))
            mask.fill(shape, with: .color(.white))
            var color = layer
            color.opacity = 1
            color.blendMode = .sourceIn
            let bounds = CGRect(x: -110, y: -110, width: 412, height: 412)
            color.fill(Path(bounds), with: .shader(shader, bounds: bounds))
        }
    }

    private var pager: some View {
        Canvas { context, _ in
            var context = context
            context.scaleBy(x: k, y: k)
            var x = (402.0 - 66) / 2
            for index in 0..<4 {
                let weight = max(0, 1 - abs(Theme.wrap(Double(index) - frame.position)))
                let width = 6 + 18 * weight
                let rect = CGRect(x: x, y: 0, width: width, height: 6)
                let shape = Path(roundedRect: rect, cornerRadius: 3)
                context.fill(shape, with: .color(.white.opacity(0.22)))
                var active = context
                active.opacity = weight
                active.fill(shape, with: .shader(material(index, rect: CGRect(x: x, y: 0, width: 24, height: 6),
                                                           radius: 3, accent: true), bounds: rect))
                x += width + 8
            }
        }
        .frame(width: 402 * k, height: 6 * k)
    }

    private func details(_ index: Int) -> some View {
        let session = Theme.sessions[index]
        let distance = Theme.wrap(Double(index) - frame.position)
        return ZStack(alignment: .top) {
            Text(session.overline)
                .font(.custom("Menlo", size: 12 * k))
                .tracking(1.2 * k)
                .foregroundStyle(dim)
                .frame(width: 402 * k, height: 16 * k)
                .offset(y: grid(456))
            Text(session.title)
                .font(.system(size: 32 * k, weight: .bold))
                .tracking(-0.6 * k)
                .foregroundStyle(.white)
                .lineLimit(1)
                .frame(width: 402 * k, height: 40 * k)
                .offset(y: grid(480))
            HoloAgendaCarouselDescription(text: session.body, scale: k)
                .frame(width: 300 * k, height: 44 * k)
                .offset(y: grid(532))
            HStack(spacing: 12 * k) {
                ZStack {
                    Canvas { context, size in
                        let rect = CGRect(origin: .zero, size: size)
                        context.fill(Path(ellipseIn: rect), with: .shader(material(index, rect: rect,
                                                                                  radius: 18 * k, accent: true), bounds: rect))
                    }
                    Text(session.initials)
                        .font(.system(size: 13 * k, weight: .bold))
                        .foregroundStyle(Color(red: 11 / 255, green: 11 / 255, blue: 16 / 255))
                }
                .frame(width: 36 * k, height: 36 * k)
                VStack(alignment: .leading, spacing: 0) {
                    Text(session.name)
                        .font(.system(size: 15 * k, weight: .semibold))
                        .foregroundStyle(.white)
                        .frame(height: 20 * k)
                    Text(session.role)
                        .font(.system(size: 13 * k))
                        .foregroundStyle(dim)
                        .frame(height: 16 * k)
                }
            }
            .offset(y: grid(600))
        }
        .frame(width: 402 * k, height: 778 * k, alignment: .top)
        .offset(x: distance * 28 * k)
        .opacity(1 - Theme.smooth(0.12, 0.5, abs(distance)))
        .allowsHitTesting(false)
    }

    private var agendaButton: some View {
        ZStack {
            Canvas { context, _ in
                var context = context
                context.scaleBy(x: k, y: k)
                let rect = CGRect(x: 0, y: 0, width: 354, height: 56)
                let shape = Path(roundedRect: rect, cornerRadius: 28)
                let shader = material(frame.centre, rect: rect, radius: 28, accent: true)
                var glow = context
                glow.opacity = frame.morph
                glow.addFilter(.blur(radius: 5))
                glow.stroke(shape, with: .shader(shader, bounds: rect), lineWidth: 1.5)
                context.fill(shape, with: .color(Color(red: 19 / 255, green: 19 / 255, blue: 22 / 255)))
                context.stroke(Path(roundedRect: rect.insetBy(dx: 0.5, dy: 0.5), cornerRadius: 27.5),
                               with: .linearGradient(Gradient(colors: [.white.opacity(0.14), .white.opacity(0.04)]),
                                                     startPoint: .zero, endPoint: CGPoint(x: 0, y: 56)), lineWidth: 1)
                context.opacity = frame.morph
                context.stroke(Path(roundedRect: rect.insetBy(dx: 0.75, dy: 0.75), cornerRadius: 27.25),
                               with: .shader(shader, bounds: rect), lineWidth: 1.5)
            }
            HStack(spacing: 8 * k) {
                Canvas { context, _ in
                    var context = context
                    context.scaleBy(x: k, y: k)
                    let p = reduced ? (frame.morph >= 0.5 ? 1.0 : 0.0) : frame.morph
                    if reduced { context.opacity = abs(frame.morph * 2 - 1) }
                    var path = Path()
                    path.move(to: CGPoint(x: 3, y: 10))
                    path.addLine(to: CGPoint(x: 17 - 9 * p, y: 10 + 5 * p))
                    path.move(to: CGPoint(x: 10 - 2 * p, y: 3 + 12 * p))
                    path.addLine(to: CGPoint(x: 10 + 7 * p, y: 17 - 12 * p))
                    let rect = CGRect(x: 0, y: 0, width: 20, height: 20)
                    context.stroke(path, with: .shader(material(frame.centre, rect: rect, radius: 0,
                                                               accent: true), bounds: rect),
                                   style: StrokeStyle(lineWidth: 2.25, lineCap: .round, lineJoin: .round))
                }
                .frame(width: 20 * k, height: 20 * k)
                ZStack(alignment: .leading) {
                    Text("Add to agenda")
                        .opacity(Theme.clamp(1 - frame.labelMix * 2))
                        .offset(y: -4 * k * frame.labelMix)
                    Text("In your agenda")
                        .opacity(Theme.clamp(frame.labelMix * 2 - 1))
                        .offset(y: 4 * k * (1 - frame.labelMix))
                }
                .font(.system(size: 17 * k, weight: .semibold))
                .foregroundStyle(.white)
            }
            .offset(x: 4 * k)
        }
        .frame(width: 354 * k, height: 56 * k)
        .scaleEffect(frame.buttonScale)
    }
}

/// A zero-delay UIKit hold survives the gallery's competing edge drag.
private struct HoloAgendaCarouselTouchSurface: UIViewRepresentable {
    let k: Double
    let left: Double
    let motion: HoloAgendaCarouselMotion

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        let recognizer = UILongPressGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.receive(_:)))
        recognizer.minimumPressDuration = 0
        recognizer.allowableMovement = .greatestFiniteMagnitude
        recognizer.cancelsTouchesInView = false
        recognizer.delegate = context.coordinator
        view.addGestureRecognizer(recognizer)
        view.backgroundColor = .clear
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.parent = self
    }

    func makeCoordinator() -> Coordinator { Coordinator(parent: self) }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var parent: HoloAgendaCarouselTouchSurface
        init(parent: HoloAgendaCarouselTouchSurface) { self.parent = parent }

        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,
                               shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }

        @objc func receive(_ recognizer: UILongPressGestureRecognizer) {
            let raw = recognizer.location(in: recognizer.view)
            let point = CGPoint(x: (raw.x - parent.left) / parent.k, y: raw.y / parent.k)
            switch recognizer.state {
            case .began: parent.motion.begin(point)
            case .changed: parent.motion.change(point)
            case .ended: parent.motion.end(point)
            case .cancelled, .failed: parent.motion.end(point, cancelled: true)
            default: break
            }
        }
    }
}

#Preview {
    HoloAgendaCarouselDemo()
}

/// UILabel preserves the reference's greedy word wrapping and explicit 22-point line boxes.
private struct HoloAgendaCarouselDescription: UIViewRepresentable {
    let text: String
    let scale: Double

    func makeUIView(context: Context) -> UILabel {
        let label = UILabel()
        label.numberOfLines = 2
        label.isAccessibilityElement = false
        label.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        return label
    }

    func updateUIView(_ label: UILabel, context: Context) {
        let paragraph = NSMutableParagraphStyle()
        paragraph.alignment = .center
        paragraph.minimumLineHeight = 22 * scale
        paragraph.maximumLineHeight = 22 * scale
        paragraph.lineBreakStrategy = []
        let font = UIFont.systemFont(ofSize: 15 * scale)
        label.attributedText = NSAttributedString(string: text, attributes: [
            .font: font,
            .foregroundColor: UIColor(red: 142 / 255, green: 142 / 255, blue: 153 / 255, alpha: 1),
            .paragraphStyle: paragraph,
            .baselineOffset: (22 * scale - font.lineHeight) / 2
        ])
    }
}
