import SwiftUI

/// A wheel of words turning in the dark. The word in front is bright, with an arrow pointing at it;
/// the others fan away above and below like spokes of a wheel whose hub is off the right edge,
/// dimmer and more blurred the further they are, and the whole wheel blurs while it spins. Swiping
/// turns it a word at a time; tapping the word in front chooses it, and a run of blue stripes sweeps
/// across to show it on its own.
struct WordDrum: View {
    /// The words on the drum, in order. Twelve or more keep a word from showing twice.
    var words = [
        "Visibility", "Strategy", "Growth", "Solutions", "Progress", "Momentum",
        "Boost", "Scaling", "Innovation", "Clarity", "Reach", "Impact",
    ]
    /// The line above the drum.
    var lead = "What we bring"
    /// What follows the chosen word once it is chosen.
    var promise = "built into everything we ship."
    /// Turns the drum a word at a time, chooses one, goes back and starts over.
    var autoPlay = false
    var background: UInt32 = 0x000000
    /// The word in front, and the rest of the drum.
    var activeColor: UInt32 = 0x8cc4ff
    var idleColor: UInt32 = 0x3f6fae
    var textColor: UInt32 = 0xe9f2ff
    /// The stripes that sweep across when a word is chosen, darkest first.
    var stripes: [UInt32] = [0x1f63d8, 0x3f86ee, 0x7cb4f7, 0xc3defc]
    /// Called with a word when it is chosen.
    var onChoose: ((String) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    // The drum's motion lives in a plain object stepped once a frame, like the RN frame callback.
    @State private var engine = WordDrumEngine()

    private typealias M = WordDrumMetrics

    var body: some View {
        // The block draws under the system bars and keeps its text clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            stage(size: size, insets: insets)
                .frame(width: size.width, height: size.height)
                .offset(x: -insets.leading, y: -insets.top)
        }
    }

    private func settings(axisY: Double) -> WordDrumSettings {
        WordDrumSettings(
            count: words.count,
            autoPlay: autoPlay && !reduceMotion,
            reducedMotion: reduceMotion,
            axisY: axisY,
            onChoose: { [words, onChoose] index in onChoose?(words[index]) }
        )
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let axisY = size.height * M.axisAt
        let settings = settings(axisY: axisY)
        return TimelineView(.animation) { timeline in
            let frame = engine.step(to: timeline.date, settings: settings)
            ZStack(alignment: .topLeading) {
                Color(hex: background)

                drum(frame: frame, size: size, insets: insets, axisY: axisY)
                    .opacity(1 - frame.showing)

                chosenWord(frame: frame, size: size)

                ForEach(stripes.indices, id: \.self) { k in
                    Color(hex: stripes[k])
                        .frame(width: size.width * M.stripeLength, height: size.height)
                        .offset(x: M.stripeAt(frame.wipe, k) * size.width)
                }
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .allowsHitTesting(false)
        }
        .frame(width: size.width, height: size.height)
        .clipped()
        .contentShape(Rectangle())
        .gesture(
            // One recogniser stands in for the race of a pan (after 6 pt of travel) and a tap (a
            // touch lifted within 400 ms that never travelled that far).
            DragGesture(minimumDistance: 0)
                .onChanged { engine.touchMoved($0, settings: settings) }
                .onEnded { engine.touchEnded($0, settings: settings) }
        )
        .accessibilityElement()
        .accessibilityLabel("\(lead): \(words.joined(separator: ", "))")
    }

    private func drum(frame: WordDrumFrame, size: CGSize, insets: EdgeInsets, axisY: Double) -> some View {
        ZStack(alignment: .topLeading) {
            Text(lead)
                .font(.custom("AvenirNext-DemiBold", fixedSize: 15))
                .kerning(0.4)
                .foregroundStyle(Color(hex: idleColor))
                .fixedSize()
                .offset(x: M.arrowLeft, y: insets.top + 32)

            ForEach(words.indices, id: \.self) { index in
                row(index: index, frame: frame, axisY: axisY)
            }

            // The arrow stays on the front row; each word arriving there kicks it to the right.
            WordDrumArrow(axisY: axisY)
                .stroke(Color(hex: activeColor), style: StrokeStyle(lineWidth: M.arrowStroke, lineCap: .round, lineJoin: .round))
                .frame(width: size.width, height: size.height)
                .offset(x: frame.kick)

            Text("Swipe to turn · Tap to choose")
                .font(.custom("AvenirNext-Medium", fixedSize: 13))
                .foregroundStyle(Color(hex: idleColor))
                .fixedSize()
                .frame(width: size.width, height: size.height - insets.bottom - 28, alignment: .bottom)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
    }

    /// One word on the wheel's rim, turned with its spoke and blurred by its distance from the front.
    @ViewBuilder
    private func row(index: Int, frame: WordDrumFrame, axisY: Double) -> some View {
        let d = M.centred(Double(index) - frame.scroll, words.count)
        let away = abs(d)
        let opacity = away > M.maxRows ? 0 : max(0, 1 - away * M.fadePerRow) * min(1, (M.maxRows - away) * 1.5)
        if opacity > 0 {
            let angle = d * M.angle
            // 1 for the word in front, 0 from one row away.
            let front = max(0, 1 - away)
            let blur = max(0.01, min(M.blurMax, away * M.blurPerRow + frame.speed * M.blurPerSpeed))
            // Hung from its baseline's left end, so the turn and the growth happen about that point.
            Color.clear
                .frame(width: 0, height: 0)
                .overlay(alignment: Alignment(horizontal: .leading, vertical: .firstTextBaseline)) {
                    Text(words[index])
                        .font(M.wordFont)
                        .foregroundStyle(WordDrumColor.mix(activeColor, idleColor, min(1, away)))
                        .fixedSize()
                        .offset(y: M.fontSize * M.baseline)
                        .blur(radius: WordDrumMetrics.blurRadius(blur, scale: displayScale))
                }
                .scaleEffect(1 + front * M.frontGrow, anchor: .topLeading)
                .rotationEffect(.radians(angle), anchor: .topLeading)
                .offset(
                    x: M.wordLeft - M.radius * (1 - cos(angle)) + front * (M.frontShift + frame.kick),
                    y: axisY + M.radius * sin(angle)
                )
                .opacity(opacity)
        }
    }

    private func chosenWord(frame: WordDrumFrame, size: CGSize) -> some View {
        let rise = reduceMotion ? 0 : M.revealRise * (1 - min(1, max(0, (frame.wipe - 0.45) / 0.55)))
        return VStack(alignment: .leading, spacing: 10) {
            Text(words[min(frame.chosen, words.count - 1)])
                .font(.custom("AvenirNext-Bold", fixedSize: 56))
                .kerning(-1)
                .foregroundStyle(Color(hex: activeColor))
            Text(promise)
                .font(.custom("AvenirNext-Medium", fixedSize: 24))
                // RN sets the lines 31 pt apart, a little tighter than the font's own spacing.
                .lineSpacing(31 - WordDrumColor.lineHeight("AvenirNext-Medium", 24))
                .foregroundStyle(Color(hex: textColor))
                .frame(maxWidth: 260, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
            Text("Tap to turn again")
                .font(.custom("AvenirNext-Medium", fixedSize: 13))
                .foregroundStyle(Color(hex: idleColor))
                .padding(.top, 28)
        }
        .padding(.horizontal, M.arrowLeft)
        .frame(width: size.width, height: size.height, alignment: .leading)
        .opacity(frame.showing)
        .offset(y: rise)
    }
}

/// The arrow left of the front row: a shaft and an open head.
private struct WordDrumArrow: Shape {
    let axisY: Double

    func path(in rect: CGRect) -> Path {
        let m = WordDrumMetrics.self
        let tip = m.arrowLeft + m.arrowWidth
        var path = Path()
        path.move(to: CGPoint(x: m.arrowLeft, y: axisY))
        path.addLine(to: CGPoint(x: tip, y: axisY))
        path.move(to: CGPoint(x: tip - m.arrowHead, y: axisY - m.arrowHead))
        path.addLine(to: CGPoint(x: tip, y: axisY))
        path.addLine(to: CGPoint(x: tip - m.arrowHead, y: axisY + m.arrowHead))
        return path
    }
}

enum WordDrumColor {
    /// Reanimated's `interpolateColor`: each channel mixed in linear light (gamma 2.2) and rounded.
    static func mix(_ from: UInt32, _ to: UInt32, _ t: Double) -> Color {
        func channel(_ shift: UInt32) -> Double {
            let a = pow(Double((from >> shift) & 0xff) / 255, 2.2)
            let b = pow(Double((to >> shift) & 0xff) / 255, 2.2)
            return (pow(a + (b - a) * t, 1 / 2.2) * 255).rounded() / 255
        }
        return Color(.sRGB, red: channel(16), green: channel(8), blue: channel(0), opacity: 1)
    }

    static func lineHeight(_ name: String, _ size: CGFloat) -> CGFloat {
        UIFont(name: name, size: size)?.lineHeight ?? size * 1.2
    }
}
