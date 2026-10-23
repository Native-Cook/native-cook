import SwiftUI
import UIKit

/// A local reservation checkout, recreated from the terminal-and-flowers payment review.
struct TerminalReservationReview: View {
    static let loop = 8.0
    var autoPlay = false
    /// Freezes a pose for previews and frame-aligned port review.
    var renderTime: Double? = nil
    @Environment(\.accessibilityReduceMotion) private var reducedMotion
    @State private var epoch = Date.now
    @State private var touchedAt: Date? = nil
    @State private var touchTime = 0.0
    @State private var dragOrigin: Double? = nil
    @State private var insurance = false
    @State private var replayAction = false
    @State private var resume: Task<Void, Never>? = nil

    private func clock(_ date: Date) -> Double {
        if reducedMotion { return touchedAt == nil ? 6.5 : (replayAction ? 6.5 : touchTime) }
        if let renderTime { return renderTime.truncatingRemainder(dividingBy: Self.loop) }
        if let touchedAt {
            if dragOrigin != nil { return touchTime }
            let idle = date.timeIntervalSince(touchedAt)
            if idle < 2 { return replayAction ? 1 + 5.5 * Self.ease(idle / 1.8) : touchTime }
            if idle < 2.6 { return (replayAction ? 6.5 : touchTime) * (1 - Self.ease((idle - 2) / 0.6)) }
            return autoPlay ? (idle - 2.6).truncatingRemainder(dividingBy: Self.loop) : 0
        }
        return autoPlay ? date.timeIntervalSince(epoch).truncatingRemainder(dividingBy: Self.loop) : 0
    }

    private static func ease(_ v: Double) -> Double {
        let p = min(1, max(0, v)); return p * p * (3 - 2 * p)
    }
    private static func phase(_ t: Double, _ a: Double, _ b: Double) -> Double { ease((t - a) / (b - a)) }
    private static func cover(_ t: Double) -> Double { 1 - pow(1 - min(1, max(0, (t - 0.70) / 0.30)), 3) }
    private func touch(_ value: Double, schedule: Bool = true) {
        replayAction = false
        touchTime = min(6.8, max(0, value)); touchedAt = .now
        resume?.cancel()
        guard schedule, !reducedMotion else { return }
        resume = Task { @MainActor in
            try? await Task.sleep(for: .seconds(2.6))
            guard !Task.isCancelled else { return }
            touchedAt = nil; replayAction = false; epoch = .now
        }
    }

    var body: some View {
        GeometryReader { proxy in
            let inset = proxy.safeAreaInsets
            let width = proxy.size.width + inset.leading + inset.trailing
            let height = proxy.size.height + inset.top + inset.bottom
            let scale = min(width / 393, height / 852)
            TimelineView(.animation(minimumInterval: 1.0 / 60, paused: reducedMotion || renderTime != nil || (!autoPlay && touchedAt == nil))) { frame in
                let t = clock(frame.date)
                let cover = Self.cover(t)
                let confirmed = Self.phase(t, 5.25, 5.60)
                let reset = Self.phase(t, 7.05, 7.6)
                ZStack(alignment: .topLeading) {
                    Color.white
                    checkout
                        .overlay(Color.black.opacity(cover * 0.10 * (1 - reset)))
                        .offset(x: -78 * cover * (1 - reset))
                        .opacity(1 - cover * (1 - reset))
                        .allowsHitTesting(cover * (1 - reset) < 0.5)
                        .accessibilityHidden(cover * (1 - reset) >= 0.5)
                    review(t: t)
                        .offset(x: (1 - cover) * 393)
                        .opacity((1 - confirmed) * (1 - reset))
                        .allowsHitTesting(false)
                        .accessibilityHidden(cover < 0.5 || confirmed > 0.5 || reset > 0.5)
                    confirmation
                        .offset(y: (1 - confirmed) * 18)
                        .opacity(confirmed * (1 - reset))
                        .allowsHitTesting(confirmed * (1 - reset) > 0.5)
                        .accessibilityHidden(confirmed * (1 - reset) <= 0.5)
                }
                .frame(width: 393, height: 852, alignment: .topLeading)
                .clipped()
                .scaleEffect(scale, anchor: .topLeading)
                .offset(x: (width - 393 * scale) / 2, y: (height - 852 * scale) / 2)
                .frame(width: width, height: height, alignment: .topLeading)
                .background(Color.white)
                .contentShape(Rectangle())
                .simultaneousGesture(DragGesture(minimumDistance: 12)
                    .onChanged { value in
                        guard !reducedMotion else { return }
                        if dragOrigin == nil { dragOrigin = t }
                        touch((dragOrigin ?? t) + value.translation.width / scale / 45, schedule: false)
                    }
                    .onEnded { value in
                        guard !reducedMotion, dragOrigin != nil else { return }
                        touch(touchTime + max(-0.4, min(0.4, value.velocity.width / 2500)))
                        dragOrigin = nil
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    })
            }
            .offset(x: -inset.leading, y: -inset.top)
        }
        .preferredColorScheme(.light)
        .onDisappear { resume?.cancel() }
    }

    private func label(_ text: String, _ x: Double, _ y: Double, _ width: Double, _ size: Double, _ weight: Font.Weight = .regular, _ color: Color = Color(white: 0.12), _ align: TextAlignment = .leading) -> some View {
        Text(text).font(.system(size: size, weight: weight)).foregroundStyle(color)
            .multilineTextAlignment(align).fixedSize(horizontal: false, vertical: true)
            .frame(width: width, alignment: align == .center ? .center : .leading).offset(x: x, y: y)
    }

    private var checkout: some View {
        ZStack(alignment: .topLeading) {
            Color.white
            label("Confirm and pay", 50, 87, 293, 17, .semibold, .black, .center)
            label("×", 354, 87, 24, 21)
            label("$1,530.00 now, $2,172.60 charged on May\n21. No extra fees. More info", 25, 133, 330, 12, .regular, .gray)
            label("Payment method", 24, 204, 345, 15, .semibold)
            RoundedRectangle(cornerRadius: 8).stroke(Color(white: 0.91)).frame(width: 345, height: 50).offset(x: 24, y: 234)
            label("CARD  0636", 43, 252, 280, 13, .semibold)
            label("›", 346, 244, 20, 22, .regular, .gray)
            label("▰  ▰   Other ways to pay", 30, 303, 330, 11, .regular, .gray)
            Rectangle().fill(Color(white: 0.94)).frame(width: 345, height: 1).offset(x: 24, y: 332)
            label("Add travel insurance?", 24, 356, 330, 15, .semibold)
            label("Yes, add peace of mind for $112.42.\nUp to 100% back for covered reasons.", 39, 400, 280, 13)
            Button { insurance.toggle(); touch(0); UIImpactFeedbackGenerator(style: .light).impactOccurred() } label: {
                Image(systemName: insurance ? "checkmark.square.fill" : "square").font(.system(size: 22)).foregroundStyle(Color(white: 0.65)).frame(width: 44, height: 44)
            }.offset(x: 323, y: 395).accessibilityLabel("Add travel insurance")
            label("Get reimbursed if you cancel due to illness, flight\ndelays, and more. Plus, get assistance services\nfor emergency help. Terms apply.", 39, 451, 310, 11, .regular, .gray)
            label("Price details", 24, 531, 330, 15, .semibold)
            ForEach(Array([("6 nights", "$3,060.00"), ("Taxes", "$642.60"), ("Total USD", "$3,702.60")].enumerated()), id: \.offset) { i, pair in
                label(pair.0, 24, 565 + Double(i) * 32, 200, 14, i == 2 ? .semibold : .regular)
                label(pair.1, 264, 565 + Double(i) * 32, 105, 14)
            }
            label("Price breakdown", 24, 679, 330, 12)
            Button { touch(1); replayAction = true; UIImpactFeedbackGenerator(style: .light).impactOccurred() } label: {
                Text("Confirm and pay").font(.system(size: 15, weight: .semibold)).foregroundStyle(.white)
                    .frame(width: 345, height: 48).background(Color(red: 0.90, green: 0, blue: 0.33), in: RoundedRectangle(cornerRadius: 7))
            }.offset(x: 24, y: 745)
            label("By selecting the button, I agree to the booking terms.", 24, 806, 345, 10, .regular, .gray, .center)
        }.frame(width: 393, height: 852, alignment: .topLeading)
    }

    private func review(t: Double) -> some View {
        ZStack(alignment: .topLeading) {
            Color.white
            TerminalReservationReviewArt(t: t).frame(width: 393, height: 852)
            label("Reviewing\npayment details", 60, 535, 273, 26, .medium, .black, .center)
                .opacity(1 - Self.phase(t, 4.30, 4.55))
        }.frame(width: 393, height: 852, alignment: .topLeading)
    }

    private var confirmation: some View {
        ZStack(alignment: .topLeading) {
            Color(red: 0.977, green: 0.977, blue: 0.961)
            label("Your reservation is\nconfirmed!", 45, 110, 303, 25, .semibold, .black, .center)
            RoundedRectangle(cornerRadius: 17).fill(.white)
                .shadow(color: .black.opacity(0.06), radius: 2, y: 2)
                .shadow(color: .black.opacity(0.10), radius: 20, y: 18)
                .frame(width: 295, height: 411).offset(x: 49, y: 225)
            Image(uiImage: TerminalReservationReviewAssets.room).resizable().scaledToFill().frame(width: 271, height: 254).clipShape(RoundedRectangle(cornerRadius: 8)).offset(x: 61, y: 237)
            label("Fern House Art Deco", 65, 511, 263, 16, .medium)
            label("May 5–11", 65, 536, 263, 12, .regular, .gray)
            label("1 guest\n$3,702.60 total", 65, 582, 263, 12, .regular, .gray)
            Button { touch(0); UIImpactFeedbackGenerator(style: .light).impactOccurred() } label: {
                Text("Done").font(.system(size: 15, weight: .medium)).foregroundStyle(.white)
                    .frame(width: 345, height: 48).background(Color(white: 0.08), in: RoundedRectangle(cornerRadius: 7))
            }.offset(x: 24, y: 768)
        }.frame(width: 393, height: 852, alignment: .topLeading)
    }
}

/// Generated alpha layers preserve independent card, terminal and flower movement.
private enum TerminalReservationReviewCard {
    static let feed: [[Double]] = [
        [0.99, -0.9303293],
        [1.071, -0.545208],
        [1.143, -0.2256194],
        [1.214, 0.0167858],
        [1.286, 0.3133213],
        [1.357, 0.510619],
        [1.429, 0.751616],
        [1.5, 0.969007],
        [1.571, 1],
    ]
    static let eject: [[Double]] = [
        [5.179, 0],
        [5.214, 0.1080205],
        [5.25, 0.2542007],
        [5.286, 0.6051038],
        [5.321, 0.7773739],
        [5.42, 1.5710126],
    ]
    static func sample(_ t: Double, _ keys: [[Double]]) -> Double {
        if t <= keys[0][0] { return keys[0][1] }
        for i in 1..<keys.count where t <= keys[i][0] {
            let a = keys[i - 1], b = keys[i], p = (t - a[0]) / (b[0] - a[0])
            return a[1] + (b[1] - a[1]) * p
        }
        return keys.last![1]
    }
}

private enum TerminalReservationReviewRing {
    // Measured source angles stay unwrapped: the semicircle travels counterclockwise.
    static let keys: [[Double]] = [
        [1.5, 259, 190],
        [1.625, 214, 190],
        [1.678571, 194.71, 190],
        [1.714286, 181.86, 190],
        [1.75, 169, 194],
        [1.875, 120, 197],
        [2, 78, 197],
        [2.125, 44, 193],
        [2.25, -11, 200],
        [2.375, -54, 187],
        [2.5, -97, 183],
        [2.625, -142, 186],
        [2.75, -188, 196],
        [2.875, -237, 199],
        [3, -292, 211],
        [3.125, -313, 187],
        [3.25, -368, 200],
        [3.375, -411, 182],
        [3.5, -457, 183],
        [3.571429, -481, 187],
        [3.642857, -504, 194],
        [3.714286, -529, 189],
        [3.785714, -563, 193],
        [3.857143, -629, 210],
        [3.928571, -664, 212],
        [4, -728, 256],
        [4.071429, -773, 274],
        [4.142857, -803, 283],
        [4.214286, -834, 309],
        [4.285714, -859, 332],
        [4.357143, -874, 345],
        [4.392857, -874, 345],
        [4.428571, -874, 360],
        [4.464286, -874, 360],
        [4.5, -874, 360],
    ]
    static func pose(_ t: Double) -> (Double, Double) {
        if t <= keys[0][0] { return (keys[0][1] + (keys[0][0] - t) * 360, keys[0][2]) }
        for i in 1..<keys.count where t <= keys[i][0] {
            let a = keys[i - 1], b = keys[i], p = (t - a[0]) / (b[0] - a[0])
            return (a[1] + (b[1] - a[1]) * p, a[2] + (b[2] - a[2]) * p)
        }
        return (keys.last![1], keys.last![2])
    }
}

private struct TerminalReservationReviewArt: View {
    let t: Double
    private func phase(_ a: Double, _ b: Double) -> Double {
        let p = min(1, max(0, (t - a) / (b - a))); return p * p * (3 - 2 * p)
    }
    private func out(_ a: Double, _ b: Double) -> Double {
        1 - pow(1 - min(1, max(0, (t - a) / (b - a))), 3)
    }
    private func pose(_ context: GraphicsContext, _ x: Double, _ y: Double, _ scale: Double, _ roll: Double, _ dx: Double = 0, _ dy: Double = 0) -> GraphicsContext {
        var c = context
        c.translateBy(x: x + dx, y: y + dy)
        c.rotate(by: .degrees(roll)); c.scaleBy(x: scale, y: scale)
        c.translateBy(x: -x, y: -y)
        return c
    }
    var body: some View {
        Canvas { context, _ in
            let reader = out(0.87, 1.08), vase = out(0.94, 1.13)
            let age = max(0, t - 0.97)
            let sway = t < 1.80 ? sin(age * 24) * exp(-age * 6) * 18 : 0
            var shade = context
            shade.opacity = max(reader, vase)
            shade.addFilter(.blur(radius: 7))
            shade.fill(Path(ellipseIn: CGRect(x: 117, y: 424, width: 141, height: 23)), with: .color(.black.opacity(0.10)))

            var flowers = pose(context, 168, 432, 0.10 + 0.90 * vase, 0, 0, (1 - vase) * 8)
            flowers.opacity = vase
            let stems = pose(flowers, 168, 373, 1, sway)
            stems.draw(Image(uiImage: TerminalReservationReviewAssets.flowers), in: CGRect(x: 134, y: 308, width: 70, height: 125))
            flowers.draw(Image(uiImage: TerminalReservationReviewAssets.vase), in: CGRect(x: 134, y: 308, width: 70, height: 125))

            // The card plane shares the generated mouth center and depth axis throughout its feed and eject.
            let feed = TerminalReservationReviewCard.sample(t, TerminalReservationReviewCard.feed)
            let eject = TerminalReservationReviewCard.sample(t, TerminalReservationReviewCard.eject)
            let travel = 95 * (1 - feed) + 110 * eject
            var paper = pose(context, 192, 471, 1.22 - 0.22 * feed + 0.05 * eject,
                             -18 * (1 - feed) - 12 * eject)
            paper.translateBy(x: -travel, y: travel * 34 / 42)
            paper.opacity = phase(0.98, 1.04) * (1 - phase(5.34, 5.48))
            paper.concatenate(CGAffineTransform(a: 42.0 / 300, b: -34.0 / 300, c: 37.0 / 190, d: 17.0 / 190, tx: 148.3, ty: 482.9))
            paper.draw(Image(uiImage: TerminalReservationReviewAssets.card), in: CGRect(x: 0, y: 0, width: 300, height: 190))

            var terminal = pose(context, 213, 443, 0.08 + 0.92 * reader, -28 * (1 - reader), -8 * (1 - reader), 20 * (1 - reader))
            terminal.opacity = reader
            terminal.draw(Image(uiImage: TerminalReservationReviewAssets.terminal), in: CGRect(x: 155, y: 385, width: 121, height: 105))
            var plane = terminal
            plane.concatenate(CGAffineTransform(a: 0.66, b: 0.30, c: -0.58, d: 0.47, tx: 204, ty: 397))
            let pink = Color(red: 0.929, green: 0.157, blue: 0.408)
            let (start, sweep) = TerminalReservationReviewRing.pose(t)
            var ring = Path()
            ring.addArc(center: CGPoint(x: 42, y: 37), radius: 28.5, startAngle: .degrees(start), endAngle: .degrees(start + sweep), clockwise: false)
            plane.opacity = reader * phase(1.25, 1.75) * (1 - phase(5.214, 5.232) * (0.96 + 0.04 * phase(5.232, 5.304)))
            var glow = plane; glow.addFilter(.blur(radius: 2))
            glow.stroke(ring, with: .color(pink.opacity(0.26)), style: StrokeStyle(lineWidth: 5, lineCap: .round))
            plane.stroke(ring, with: .color(pink), style: StrokeStyle(lineWidth: 2.8, lineCap: .round))
            // The original resolves into a clock, not a check mark.
            var hands = Path(); hands.move(to: CGPoint(x: 31.22, y: 24.73)); hands.addLine(to: CGPoint(x: 42, y: 37)); hands.addLine(to: CGPoint(x: 51.48, y: 28.82))
            plane.stroke(hands.trimmedPath(from: 0, to: out(4.40, 4.65)), with: .color(pink), style: StrokeStyle(lineWidth: 2.6, lineCap: .round, lineJoin: .round))
        }
        .accessibilityLabel("Payment terminal with pink status ring, clock, card and flowers")
    }
}

private enum TerminalReservationReviewAssets {
    private static func load(_ name: String, _ ext: String = "png") -> UIImage {
        guard let path = Bundle.main.path(forResource: "TerminalReservationReview-" + name, ofType: ext), let image = UIImage(contentsOfFile: path) else { return UIImage() }
        return image.preparingForDisplay() ?? image
    }
    static let terminal = load("terminal")
    static let vase = load("vase")
    static let flowers = load("flowers")
    static let card = load("card")
    static let room = load("room", "jpg")
}
