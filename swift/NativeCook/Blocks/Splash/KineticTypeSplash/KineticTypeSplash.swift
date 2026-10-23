import SwiftUI
import UIKit

/// Kinetic type for a splash. Huge condensed capitals rise line by line out of their own baselines,
/// letter after letter; the last line rolls through its words like a slot machine; the last word
/// fills with a photo, which then grows out of the letters into the whole screen, with a welcome and
/// a button over it. Tap to skip ahead, drag the last line to roll it yourself.
struct KineticTypeSplash: View {
    /// Types the lines, rolls the last word, reveals the photo through it and starts over.
    var autoPlay = false
    /// The two lines that stay, and the words the last line rolls through (the last one opens).
    var lines = ["FIND", "YOUR"]
    var words = ["CROWD", "SCENE", "GLOW"]
    var name = "NIGHTJAR"
    var title = "The city after dark,\none tap away."
    var caption = "Shows, late kitchens and rooftops near you, picked fresh every night."
    var cta = "Get started"
    var background: UInt32 = 0x0a0a0b
    var type: UInt32 = 0xf3ede3
    var accent: UInt32 = 0xff3d7f
    var onGetStarted: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    // The splash's motion lives in a plain object stepped once a frame, like the RN frame callback.
    @State private var engine = KineticTypeSplashEngine()
    @State private var photo: Image?
    // False again once the finger is gone, also when the system takes the touch away.
    @GestureState private var touching = false

    var body: some View {
        // The block draws under the system bars and keeps its words clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            let layout = KineticTypeSplashLayout(width: size.width, height: size.height, top: insets.top, bottom: insets.bottom)
            stage(layout: layout, type: engine.type(width: size.width, lines: lines, words: words))
            .frame(width: size.width, height: size.height)
            .offset(x: -insets.leading, y: -insets.top)
        }
        .task { await loadPhoto() }
    }

    private func stage(layout: KineticTypeSplashLayout, type set: KineticTypeSplashType) -> some View {
        let windowHeight = set.cap + set.size * KineticTypeSplashMetrics.pad * 2
        let settings = KineticTypeSplashSettings(
            autoPlay: autoPlay, reducedMotion: reduceMotion, layout: layout,
            words: words.count, windowHeight: windowHeight, onGetStarted: onGetStarted
        )
        return TimelineView(.animation) { timeline in
            let frame = engine.step(to: timeline.date, settings: settings)
            Canvas(rendersAsynchronously: false) { context, _ in
                KineticTypeSplashPainter(
                    frame: frame, type: set, photo: photo, name: name, title: title, caption: caption, cta: cta,
                    background: Color(hex: background), typeColor: Color(hex: type), accent: Color(hex: accent)
                ).draw(in: context)
            }
        }
        .frame(width: layout.width, height: layout.height)
        .background(Color(hex: background))
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .updating($touching) { _, state, _ in state = true }
                .onChanged { engine.touchChanged($0) }
                .onEnded { engine.touchEnded($0) }
        )
        .onChange(of: touching) { _, now in
            if !now { engine.touchLost() }
        }
        .accessibilityElement()
        .accessibilityAddTraits(.isButton)
        .accessibilityLabel("\(lines.joined(separator: " ")) \(words.last ?? ""). \(title.replacingOccurrences(of: "\n", with: " ")) \(cta)")
    }

    private func loadPhoto() async {
        let address = "https://images.unsplash.com/photo-1581300740943-cfa5f847db2c?auto=format&fit=crop&w=1200&q=80"
        guard let url = URL(string: address),
              let (data, _) = try? await URLSession.shared.data(from: url),
              let image = UIImage(data: data)
        else { return }
        // Decoded off the main thread, so the first frame that draws it does not stall.
        let ready = await image.byPreparingForDisplay() ?? image
        photo = Image(uiImage: ready)
    }
}

/// Draws one frame: the lines, the reel, the photo through the last word, the welcome, the way out.
private struct KineticTypeSplashPainter {
    let frame: KineticTypeSplashFrame
    let type: KineticTypeSplashType
    let photo: Image?
    let name: String
    let title: String
    let caption: String
    let cta: String
    let background: Color
    let typeColor: Color
    let accent: Color

    private typealias M = KineticTypeSplashMetrics

    func draw(in context: GraphicsContext) {
        let f = frame
        let l = f.layout
        let W = l.width
        let H = l.height
        let reduced = f.reducedMotion
        let cap = type.cap
        let pad = type.size * M.pad
        let windowH = cap + pad * 2
        let pitch = cap * M.leading + pad
        // Baselines of the three lines, the block centred a little above the middle.
        let blockTop = H * 0.45 - (pitch * 2 + cap) / 2
        let baselines = (0..<3).map { blockTop + cap + Double($0) * pitch }
        let full = CGRect(x: 0, y: 0, width: W, height: H)
        context.fill(Path(full), with: .color(background))

        // The lines that stay: each letter rises out of its baseline, and leaves upwards for the reveal.
        for (line, word) in type.lines.enumerated() {
            var c = context
            c.clip(to: Path(CGRect(x: 0, y: baselines[line] - cap - pad, width: W, height: windowH)))
            for (index, letter) in word.letters.enumerated() {
                let from = Double(line) * M.lineLag + Double(index) * M.letterLag
                let enter = M.seg(f.intro, from, from + M.letterTime)
                let away = M.seg(f.outro, Double(index) * M.letterLag, Double(index) * M.letterLag + M.letterTime)
                var g = c
                if reduced {
                    g.opacity = enter * (1 - away)
                } else {
                    g.translateBy(x: 0, y: (1 - M.easeOut.value(at: enter)) * windowH - M.easeInOut.value(at: away) * windowH)
                }
                g.translateBy(x: letter.x, y: baselines[line])
                g.fill(letter.path, with: .color(typeColor))
            }
        }

        // The reel: every word's letters on their own columns, so a word rolls letter after letter.
        let reelAlpha = 1 - f.fill
        if reelAlpha > 0 {
            var c = context
            c.clip(to: Path(CGRect(x: 0, y: baselines[2] - cap - pad, width: W, height: windowH)))
            c.opacity = reelAlpha
            c.drawLayer { layer in
                var shapes = Path()
                for (w, word) in type.words.enumerated() {
                    for (index, letter) in word.letters.enumerated() {
                        let from = 2 * M.lineLag + Double(index) * M.letterLag
                        let enter = M.easeOut.value(at: M.seg(f.intro, from, from + M.letterTime))
                        let offset = Double(w) - f.cols[min(index, f.cols.count - 1)]
                        if reduced {
                            // Letters fade in place and crossfade between words.
                            let alpha = max(0, 1 - abs(offset)) * M.seg(f.intro, from, from + M.letterTime)
                            guard alpha > 0 else { continue }
                            var one = layer
                            one.opacity = alpha
                            let shape = letter.path.applying(CGAffineTransform(translationX: letter.x, y: baselines[2]))
                            drawNeon(shape, in: one)
                            continue
                        }
                        guard abs(offset) <= 1.05 else { continue }
                        let dy = offset * windowH + (1 - enter) * windowH
                        shapes.addPath(letter.path, transform: CGAffineTransform(translationX: letter.x, y: baselines[2] + dy))
                    }
                }
                if !reduced { drawNeon(shapes, in: layer) }
            }
        }

        let lastWord = type.words[type.words.count - 1]
        let lastPath = lastWord.path.applying(CGAffineTransform(translationX: 0, y: baselines[2]))
        // The halo stays under the photo-filled word until it starts to grow.
        let halo = 0.7 * f.fill * (1 - M.seg(f.zoom, 0, 0.25))
        if halo > 0 {
            var c = context
            c.opacity = halo
            c.addFilter(.blur(radius: M.glowBlur * 1.4))
            c.drawLayer { $0.fill(lastPath, with: .color(accent)) }
        }

        // The last word as a window onto the photo, growing about its first stem until it is the screen.
        let photoInType = f.fill * (reduced ? 1 - f.zoom : 1)
        if photoInType > 0 {
            var c = context
            c.opacity = photoInType
            c.drawLayer { layer in
                let z = reduced ? 1 : pow(M.zoomMax, M.easeInOut.value(at: f.zoom))
                let focus = CGPoint(x: M.margin + cap * 0.12, y: baselines[2] - cap * 0.45)
                var letters = layer
                letters.translateBy(x: focus.x, y: focus.y)
                letters.scaleBy(x: z, y: z)
                letters.translateBy(x: -focus.x, y: -focus.y)
                letters.fill(lastPath, with: .color(.white))
                var paint = layer
                paint.blendMode = .sourceIn
                drawPhoto(in: paint, full: full)
            }
        }
        let photoFull = reduced ? f.zoom : M.seg(f.zoom, 0.9, 1)
        if photoFull > 0 {
            var c = context
            c.opacity = photoFull
            drawPhoto(in: c, full: full)
        }
        if f.final > 0 {
            var c = context
            c.opacity = f.final
            c.fill(Path(full), with: .linearGradient(
                Gradient(stops: [
                    .init(color: .rgba(8, 6, 10, 0), location: 0),
                    .init(color: .rgba(8, 6, 10, 0.72), location: 0.42),
                    .init(color: .rgba(8, 6, 10, 0.95), location: 1),
                ]),
                startPoint: CGPoint(x: 0, y: H * 0.4), endPoint: CGPoint(x: 0, y: H)
            ))
            drawWelcome(context)
        }

        // The way out: a panel of the background rises over everything (fades in, under reduced motion).
        if f.exit > 0 {
            var c = context
            if reduced {
                c.opacity = f.exit
                c.fill(Path(full), with: .color(background))
            } else {
                let y = H * (1 - M.easeInOut.value(at: f.exit))
                c.fill(Path(CGRect(x: 0, y: y, width: W, height: H)), with: .color(background))
            }
        }
    }

    /// A word of the reel: a soft neon halo under the letters, then the letters.
    private func drawNeon(_ shape: Path, in context: GraphicsContext) {
        var halo = context
        halo.opacity = 0.45
        halo.addFilter(.blur(radius: M.glowBlur))
        halo.drawLayer { $0.fill(shape, with: .color(accent)) }
        context.fill(shape, with: .color(accent))
    }

    /// The photo, screen-sized and cropped to cover, drifting in a little as the welcome settles; a
    /// gradient in the accent stands in until it has loaded.
    private func drawPhoto(in context: GraphicsContext, full: CGRect) {
        guard let photo else {
            context.fill(Path(full), with: .linearGradient(
                Gradient(colors: [accent, Color(hex: 0x5b2bff)]),
                startPoint: .zero, endPoint: CGPoint(x: full.width, y: full.height)
            ))
            return
        }
        let f = frame
        let scale = 1.08 - 0.08 * M.easeOut.value(at: M.seg(f.zoom, 0.3, 1) * 0.6 + f.final * 0.4)
        var c = context.scaled(scale, about: CGPoint(x: full.midX, y: full.midY))
        let image = c.resolve(photo)
        let s = max(full.width / image.size.width, full.height / image.size.height)
        let w = image.size.width * s
        let h = image.size.height * s
        c.clip(to: Path(full))
        c.draw(image, in: CGRect(x: full.midX - w / 2, y: full.midY - h / 2, width: w, height: h))
    }

    // MARK: - Welcome

    /// A welcome element's fade and rise: in from `from` of the welcome, out in the first 40% of the way out.
    private func rise(_ from: Double, _ distance: Double) -> (alpha: Double, dy: Double) {
        let p = M.easeOut.value(at: M.seg(frame.final, from, from + 0.6))
        let alpha = M.seg(frame.final, from, from + 0.4) * (1 - M.seg(frame.exit, 0, 0.4))
        return (alpha, frame.reducedMotion ? 0 : (1 - p) * distance)
    }

    /// The copy broken into lines that fit `width`, word by word.
    private func wrap(_ text: String, font: UIFont, width: Double) -> [String] {
        var lines: [String] = []
        var line = ""
        for word in text.split(separator: " ") {
            let candidate = line.isEmpty ? String(word) : line + " " + word
            if !line.isEmpty && (candidate as NSString).size(withAttributes: [.font: font]).width > width {
                lines.append(line)
                line = String(word)
            } else {
                line = candidate
            }
        }
        if !line.isEmpty { lines.append(line) }
        return lines
    }

    private func drawWelcome(_ context: GraphicsContext) {
        let l = frame.layout
        let W = l.width
        let ctaTop = l.ctaTop

        let nameRise = rise(0, 10)
        if nameRise.alpha > 0 {
            var c = context
            c.opacity = nameRise.alpha
            c.draw(
                Text(name).font(.custom(M.fontName, fixedSize: 17)).kerning(3).foregroundColor(typeColor),
                at: CGPoint(x: M.margin, y: l.top + 16 + nameRise.dy), anchor: .topLeading
            )
        }

        // The title and the caption sit together 30 pt above the button.
        let titleFont = UIFont(name: M.fontName, size: 38) ?? .systemFont(ofSize: 38, weight: .heavy)
        let captionFont = UIFont.systemFont(ofSize: 16, weight: .medium)
        let titleLines = title.components(separatedBy: "\n")
        let captionLines = wrap(caption, font: captionFont, width: min(320, W - M.margin * 2))
        let captionTop = ctaTop - 30 - 22 * Double(captionLines.count)
        let titleTop = captionTop - 12 - 40 * Double(titleLines.count)

        let titleRise = rise(0.1, 30)
        if titleRise.alpha > 0 {
            var c = context
            c.opacity = titleRise.alpha
            // Each line sits in a 40 pt box, its glyphs centred in it.
            let glyphs = titleFont.ascender - titleFont.descender
            for (i, line) in titleLines.enumerated() {
                let baseline = titleTop + Double(i) * 40 + (40 - glyphs) / 2 + titleFont.ascender + titleRise.dy
                let text = c.resolve(Text(line).font(.custom(M.fontName, fixedSize: 38)).kerning(-0.3).foregroundColor(typeColor))
                let size = text.measure(in: CGSize(width: 2000, height: 200))
                c.draw(text, at: CGPoint(x: M.margin, y: baseline - text.firstBaseline(in: size)), anchor: .topLeading)
            }
        }
        let captionRise = rise(0.22, 24)
        if captionRise.alpha > 0 {
            var c = context
            c.opacity = captionRise.alpha
            let top = captionTop + (22 - captionFont.lineHeight) / 2 + captionRise.dy
            for (i, line) in captionLines.enumerated() {
                c.draw(
                    Text(line).font(.system(size: 16, weight: .medium)).foregroundColor(typeColor.opacity(Double(0xc0) / 255)),
                    at: CGPoint(x: M.margin, y: top + Double(i) * 22), anchor: .topLeading
                )
            }
        }

        let button = rise(0.32, 50)
        if button.alpha > 0 {
            var c = context
            c.opacity = button.alpha
            let width = W - M.margin * 2
            c.translateBy(x: W / 2, y: ctaTop + 28 + button.dy)
            let scale = 1 - frame.press * 0.03
            c.scaleBy(x: scale, y: scale)
            c.drawLayer { b in
                b.fill(Path(roundedRect: CGRect(x: -width / 2, y: -28, width: width, height: 56), cornerRadius: 28), with: .color(typeColor))
                b.draw(Text(cta).font(.system(size: 17, weight: .bold)).foregroundColor(Color(hex: 0x0a0a0b)), at: .zero)
            }
        }
    }
}
