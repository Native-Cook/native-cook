import SwiftUI
import UIKit

/// Thousands of small glowing particles that spell a word on a deep navy night. Every few seconds they
/// flow into the next word: each particle sets off a moment after its neighbour to the left, swings
/// along a curved path through a slowly turning field, and settles into the new letters while the
/// colours shift to the new word's. A finger pushes the particles aside like sand and they spring
/// back when it moves on; a tap forms the next word.
struct ParticleWordMorph: View {
    /// The words the particles spell, in order. Short words (up to six letters) read best.
    var words = ParticleWordMorphMetrics.words
    /// Two colours per word, left to right across it; the particles shift to them as it forms.
    var palettes: [(UInt32, UInt32)] = ParticleWordMorphMetrics.palettes
    /// Cycles the words on a loop, with one pass of a finger through the third word.
    var autoPlay = false
    /// Called with a word's index when it starts to form after a tap.
    var onWord: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine = ParticleWordMorphEngine()
    @GestureState private var touching = false

    private typealias M = ParticleWordMorphMetrics
    private static let label = Color.rgba(205, 214, 255, 0.55)

    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(width: geo.size.width + insets.leading + insets.trailing, height: geo.size.height + insets.top + insets.bottom)
            screen(size: size, insets: insets)
                .frame(width: size.width, height: size.height)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .onAppear {
            let onWord = onWord
            engine.onWord = { index in
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                onWord?(index)
            }
        }
    }

    private func screen(size: CGSize, insets: EdgeInsets) -> some View {
        let shownWords = words.isEmpty ? M.words : words
        return ZStack(alignment: .top) {
            TimelineView(.animation) { timeline in
                let frame = engine.step(to: timeline.date, size: size, bottomInset: insets.bottom, words: shownWords, palettes: palettes, autoPlay: autoPlay, reducedMotion: reduceMotion)
                Canvas { context, _ in
                    guard let frame else { return }
                    ParticleWordMorphPainter.draw(context, frame: frame, size: size, engine: engine)
                }
            }
            HStack {
                labelText("WORDS IN DUST")
                Spacer(minLength: 0)
                labelText("\(M.count.formatted(.number.locale(Locale(identifier: "en_US")))) PARTICLES")
            }
            .padding(.horizontal, M.edge)
            .offset(y: insets.top + 22)
            // The hint's bottom sits hintAbove pt above the word row's middle.
            Text("Touch to scatter · Tap for the next word")
                .font(.custom("AvenirNext-Medium", fixedSize: 13))
                .foregroundStyle(Self.label)
                .multilineTextAlignment(.center)
                .padding(.bottom, insets.bottom + M.rowAbove + M.hintAbove)
                .frame(width: size.width, height: size.height, alignment: .bottom)
                .allowsHitTesting(false)
        }
        .frame(width: size.width, height: size.height)
        .background(Color(hex: M.night))
        .clipped()
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .updating($touching) { _, state, _ in state = true }
                .onChanged { engine.touchChanged($0) }
                .onEnded { engine.touchEnded($0) }
        )
        // A touch the system takes away (no end) still lets go of the particles.
        .onChange(of: touching) { _, now in
            if !now { engine.touchCancelled() }
        }
        .accessibilityElement()
        .accessibilityLabel("Particles spelling \(shownWords.joined(separator: ", ")). Touch to scatter them; tap for the next word.")
    }

    private func labelText(_ text: String) -> some View {
        Text(text)
            .font(.custom("AvenirNext-DemiBold", fixedSize: 11).monospacedDigit())
            .kerning(2.2)
            .foregroundStyle(Self.label)
    }
}

/// Draws the night, the particles with their tint and halo, and the row of words.
enum ParticleWordMorphPainter {
    private typealias M = ParticleWordMorphMetrics

    /// A soft white dot: solid in the middle, fading out towards its edge.
    static let sprite: UIImage = {
        let side = M.sprite * 3
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: side, height: side), format: {
            let format = UIGraphicsImageRendererFormat()
            format.scale = 1
            return format
        }())
        return renderer.image { ctx in
            let colors = [UIColor.white.cgColor, UIColor(white: 1, alpha: 0.85).cgColor, UIColor(white: 1, alpha: 0).cgColor] as CFArray
            guard let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors, locations: [0, 0.45, 1]) else { return }
            let c = CGPoint(x: side / 2, y: side / 2)
            ctx.cgContext.drawRadialGradient(gradient, startCenter: c, startRadius: 0, endCenter: c, endRadius: side / 2, options: [])
        }
    }()

    static func draw(_ context: GraphicsContext, frame: ParticleWordMorphFrame, size: CGSize, engine: ParticleWordMorphEngine) {
        let w = size.width
        let h = size.height
        let night = Gradient(stops: [
            .init(color: Color(hex: M.nightGlow), location: 0),
            .init(color: Color(hex: M.night), location: 0.48),
            .init(color: Color(hex: M.nightEdge), location: 1),
        ])
        context.fill(
            Path(CGRect(origin: .zero, size: size)),
            with: .radialGradient(night, center: CGPoint(x: w / 2, y: h * M.wordAt), startRadius: 0, endRadius: max(w, h) * 0.78)
        )

        // The particles in one layer, tinted by the word's gradient and haloed in its colour.
        var glowing = context
        glowing.addFilter(.shadow(color: frame.glow, radius: M.glowBlur))
        let image = glowing.resolve(Image(uiImage: sprite))
        let colors = Gradient(colors: [frame.colors.0, frame.colors.1])
        glowing.drawLayer { layer in
            for i in 0..<min(frame.xs.count, frame.ys.count, engine.scale.count) {
                let side = M.sprite * engine.scale[i]
                layer.draw(image, in: CGRect(x: frame.xs[i] - side / 2, y: frame.ys[i] - side / 2, width: side, height: side))
            }
            layer.blendMode = .sourceIn
            layer.fill(
                Path(CGRect(origin: .zero, size: size)),
                with: .linearGradient(colors, startPoint: CGPoint(x: M.wordMargin, y: 0), endPoint: CGPoint(x: w - M.wordMargin, y: h * 0.2))
            )
        }

        // Which word is on screen: the words in a row of small capitals, the current one lit and
        // underlined. The hairline glides from word to word as the particles fly, stretching or
        // shrinking to each word's width, and the labels brighten and dim with it.
        let count = min(frame.words.count, frame.labelX.count, frame.labelW.count)
        guard count > 0, frame.rowY.isFinite, frame.mark.isFinite else { return }
        let ink = Color(hex: M.ink)
        for k in 0..<count {
            let lit = M.dim + (1 - M.dim) * max(0, min(1, 1 - abs(frame.mark - Double(k))))
            let label = Text(frame.words[k])
                .font(.custom("AvenirNext-DemiBold", fixedSize: M.labelSize))
                .kerning(M.tracking)
                .foregroundStyle(ink.opacity(lit))
            context.draw(label, at: CGPoint(x: frame.labelX[k], y: frame.rowY), anchor: .leading)
        }
        let m = max(0, min(Double(count - 1), frame.mark))
        let a = Int(m.rounded(.down))
        let b = min(count - 1, a + 1)
        let t = m - Double(a)
        let markX = frame.labelX[a] + (frame.labelX[b] - frame.labelX[a]) * t
        let markW = frame.labelW[a] + (frame.labelW[b] - frame.labelW[a]) * t - M.tracking
        guard markX.isFinite, markW.isFinite, markW > 0 else { return }
        context.fill(
            Path(roundedRect: CGRect(x: markX, y: frame.rowY + M.markBelow, width: markW, height: M.markHeight), cornerRadius: M.markHeight / 2),
            with: .color(ink)
        )
    }
}
