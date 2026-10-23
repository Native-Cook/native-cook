import SwiftUI
import UIKit

/// A six-petal aperture that turns a quiet mark into a welcoming color field.
struct PetalBurstSplash: View {
    static let loop = 8.0
    var autoPlay = false
    var previewTime: Double? = nil
    @Environment(\.accessibilityReduceMotion) private var reducedMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var epoch = Date()
    @State private var heldTime = 0.0
    @State private var releasedAt: Date?
    @State private var touching = false

    private func time(_ now: Date) -> Double {
        if let previewTime { return previewTime.truncatingRemainder(dividingBy: Self.loop) }
        if reducedMotion { return 4.8 }
        if touching { return heldTime }
        if let releasedAt {
            let elapsed = now.timeIntervalSince(releasedAt)
            if elapsed < 2 { return heldTime }
            if elapsed < 2.6 { return heldTime * (1 - Self.ease((elapsed - 2) / 0.6)) }
            return autoPlay ? (elapsed - 2.6).truncatingRemainder(dividingBy: Self.loop) : 0
        }
        return autoPlay ? max(0, now.timeIntervalSince(epoch)).truncatingRemainder(dividingBy: Self.loop) : 0
    }

    var body: some View {
        GeometryReader { geometry in
            TimelineView(.animation(paused: reducedMotion || previewTime != nil || scenePhase != .active || (!autoPlay && releasedAt == nil && !touching))) { clock in
                Canvas { context, size in
                    context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(Self.color(0xFFF9F7)))
                    let scale = min(size.width / 393, size.height / 852)
                    context.translateBy(x: (size.width - 393 * scale) / 2, y: (size.height - 852 * scale) / 2)
                    context.scaleBy(x: scale, y: scale)
                    Self.draw(context, time: time(clock.date))
                }
            }
            .contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0)
                .onChanged { value in
                    guard !reducedMotion else { return }
                    if !touching {
                        heldTime = time(Date())
                        touching = true
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    }
                    heldTime = min(7.4, max(0, value.location.x / geometry.size.width * 7.4))
                }
                .onEnded { value in
                    guard !reducedMotion else { return }
                    if abs(value.translation.width) < 8 { heldTime = 4.8 }
                    touching = false
                    releasedAt = Date()
                })
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Flori. Good things start with hello. Drag to open the petals.")
            .accessibilityAction(named: "Replay splash") { epoch = Date(); releasedAt = nil }
        }
        .ignoresSafeArea()
        .preferredColorScheme(.light)
    }

    private static func ease(_ value: Double) -> Double {
        let u = min(1, max(0, value)); return u * u * (3 - 2 * u)
    }
    private static func color(_ hex: UInt32) -> Color {
        Color(red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255)
    }
    private static func label(_ text: String, _ x: Double, _ y: Double, _ size: Double, _ ink: Color, _ weight: Font.Weight = .regular, context: GraphicsContext) {
        context.draw(Text(text).font(.system(size: size, weight: weight)).foregroundColor(ink), at: CGPoint(x: x, y: y))
    }

    private static func draw(_ context: GraphicsContext, time t: Double) {
        let reset = 1 - ease((t - 5.8) / 1.6)
        let bloom = ease((t - 0.6) / 1.4) * reset
        let open = ease((t - 2.0) / 1.2) * reset
        let reveal = ease((t - 3.0) / 0.6) * (1 - ease((t - 5.8) / 0.5))
        let pink = color(0xFF526B), orange = color(0xFF885E)
        let radius = (22 + 26 * bloom) * (1 + 18 * open)
        var petals = Path()
        for i in 0...240 {
            let angle = Double(i) / 240 * .pi * 2
            let r = radius * (1 + 0.24 * cos(6 * angle))
            let rotation = bloom * .pi / 3
            let point = CGPoint(x: 196.5 + r * cos(angle + rotation), y: 426 + r * sin(angle + rotation))
            if i == 0 { petals.move(to: point) } else { petals.addLine(to: point) }
        }
        petals.closeSubpath()
        context.fill(petals, with: .linearGradient(Gradient(colors: [orange, pink]), startPoint: CGPoint(x: 80, y: 0), endPoint: CGPoint(x: 313, y: 852)))
        for i in 0..<12 {
            let a = Double(i) * .pi / 6 + bloom * 0.65
            let distance = 52 + 54 * bloom
            let r = 1.5 + Double(i % 3)
            let dot = CGRect(x: 196.5 + cos(a) * distance - r, y: 426 + sin(a) * distance - r, width: r * 2, height: r * 2)
            context.fill(Path(ellipseIn: dot), with: .color((i % 2 == 0 ? pink : orange).opacity(bloom * (1 - open))))
        }
        var word = context
        word.opacity = reveal
        var emblem = Path()
        for i in 0...120 {
            let angle = Double(i) / 120 * .pi * 2
            let r = 14 * (1 + 0.24 * cos(6 * angle))
            let point = CGPoint(x: 140 + r * cos(angle), y: 426 - 20 * reveal + r * sin(angle))
            if i == 0 { emblem.move(to: point) } else { emblem.addLine(to: point) }
        }
        emblem.closeSubpath()
        word.fill(emblem, with: .color(.white))
        label("flori", 220, 426 - 20 * reveal, 46, .white, .bold, context: word)
        label("Good things start", 196.5, 568 + 16 * (1 - reveal), 30, .white, .bold, context: word)
        label("with hello.", 196.5, 605 + 16 * (1 - reveal), 30, .white, .bold, context: word)
        label("A little spark. A new connection.", 196.5, 649, 14, .white.opacity(0.88), context: word)
        word.fill(Path(roundedRect: CGRect(x: 28, y: 700, width: 337, height: 54), cornerRadius: 27), with: .color(.white))
        label("Make a connection", 196.5, 727, 15, pink, .semibold, context: word)
        label("Your next chapter starts here", 196.5, 783, 11, .white.opacity(0.8), context: word)
        var resting = context
        resting.opacity = 1 - ease((t - 1.2) / 0.5) * reset
        label("FLORI", 196.5, 494, 12, pink, .semibold, context: resting)
    }
}
