import CoreText
import SwiftUI

// A character-diffing text morph. When the text changes, the characters the two strings share
// keep their identity and glide to their new place; removed ones leave (up, right, shrink, blur,
// fade) and added ones arrive (rise, grow, sharpen, fade), each a beat after the one before it.
// Every glyph is a view that can be sent back in while it is still leaving, so a fling across
// several balls never strands a letter. Mirrors MorphingText.tsx.

struct ChooseYourBallMorphingText: View {
    /// The string to show. Changing it starts the morph.
    let text: String
    /// Every string this view will ever show; the size is fitted to the widest of them once.
    let fitTexts: [String]
    /// The size asked for; treated as a maximum.
    let fontSize: CGFloat
    let height: CGFloat
    let paddingLeft: CGFloat
    /// Extra advance per glyph, in points. Negative tightens.
    let tracking: CGFloat
    let width: CGFloat
    let reduceMotion: Bool

    @State private var cells: [Cell] = []
    @State private var laidOutText: String?

    var body: some View {
        let metrics = Metrics(
            fitTexts: fitTexts, fontSize: fontSize, tracking: tracking, usableWidth: width - paddingLeft
        )
        // Baseline that puts the visual centre of a line of caps in the middle of the box.
        let baseline = height / 2 + metrics.size * Motion.centreRatio

        ZStack(alignment: .topLeading) {
            ForEach(cells) { cell in
                Glyph(
                    cell: cell,
                    baseline: baseline,
                    centreAboveBaseline: metrics.size * Motion.centreRatio,
                    blur: metrics.size * Motion.blur,
                    reduceMotion: reduceMotion,
                    onExited: remove
                )
            }
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .onAppear { reconcile(metrics) }
        .onChange(of: text) { reconcile(metrics) }
        .onChange(of: metrics.size) { relayout(metrics) }
        .accessibilityHidden(true)
    }

    /// Present glyphs take the new layout; glyphs the new text dropped stay, marked as leaving.
    private func reconcile(_ metrics: Metrics) {
        guard laidOutText != text else { return }
        laidOutText = text
        let next = metrics.layout(text, from: paddingLeft)
        let keys = Set(next.map(\.key))
        var merged = next
        for old in cells where !keys.contains(old.key) {
            var leaving = old
            leaving.isExiting = true
            merged.append(leaving)
        }
        cells = merged
    }

    private func relayout(_ metrics: Metrics) {
        cells = metrics.layout(text, from: paddingLeft)
    }

    private func remove(_ key: String) {
        // It may have come back while its exit was finishing; then it stays.
        cells.removeAll { $0.key == key && $0.isExiting }
    }
}

private extension ChooseYourBallMorphingText {
    enum Motion {
        /// Delay per character through the string.
        static let stagger = 0.022
        /// Lead time, so the outgoing letters have cleared before the new ones arrive.
        static let enterDelay = 0.110
        /// Points an incoming glyph rises from, and where an outgoing one goes.
        static let enterRise: CGFloat = 14
        static let exitUp: CGFloat = 12
        static let exitRight: CGFloat = 8
        /// Scale a glyph starts and ends at.
        static let shrink: CGFloat = 0.7
        /// Blur a glyph starts and ends at, as a fraction of the font size.
        static let blur: CGFloat = 0.11
        static let move = 0.260
        static let exit = 0.240
        /// Surviving glyphs wait, so the rewrite reads as a sequence and not as a slide.
        static let glideDelay = 0.140
        static let glide = 0.320
        /// From the baseline up to the visual centre of a line of caps, as a fraction of the size.
        static let centreRatio: CGFloat = 0.34

        /// The reference's timed animations all run on its default curve, quadratic in and out.
        static func timed(_ duration: Double) -> Animation {
            .chooseYourBallEased(.inOutQuad, duration: duration)
        }

        /// Critically damped: arrives without bouncing.
        static let arrive = Animation.interpolatingSpring(mass: 4, stiffness: 900, damping: 120)
    }

    struct Cell: Identifiable, Equatable {
        /// The character and which occurrence of it this is, so the second "L" of one name
        /// reconciles with the second "L" of the next.
        let key: String
        /// The glyph's outline, with its origin on the baseline at the glyph's left edge.
        let outline: Path
        /// Place in the string, which sets the stagger.
        var index: Int
        var width: CGFloat
        /// Left edge within the view.
        var x: CGFloat
        var isExiting = false

        var id: String { key }
    }

    /// The fitted size and the glyphs at that size.
    ///
    /// The face is the system's black weight in its text cut, enlarged: the reference draws this
    /// line through Skia, which takes the face at its default size and scales the outlines. So
    /// the headline has the text cut's wider spacing and deeper joins, not the tighter display
    /// cut the system picks by itself for type this large.
    struct Metrics {
        let size: CGFloat
        let tracking: CGFloat

        /// 12 pt is in the text cut, and the system adds no tracking of its own at that size.
        private static let designSize: CGFloat = 12
        private static let face = UIFont.systemFont(ofSize: designSize, weight: .black) as CTFont

        init(fitTexts: [String], fontSize: CGFloat, tracking: CGFloat, usableWidth: CGFloat) {
            let widest = fitTexts.map { text in
                Self.glyphs(text, size: fontSize).reduce(0) { $0 + $1.advance } + tracking * CGFloat(text.count)
            }.max() ?? 0
            size = widest <= usableWidth || widest == 0 ? fontSize : (fontSize * usableWidth / widest).rounded(.down)
            // Tracking shrinks with the type, or a fitted headline comes apart.
            self.tracking = tracking * size / fontSize
        }

        func layout(_ text: String, from left: CGFloat) -> [Cell] {
            var cursor = left
            var seen: [Character: Int] = [:]
            return zip(text, Self.glyphs(text, size: size)).enumerated().map { index, pair in
                let (character, glyph) = pair
                let occurrence = seen[character, default: 0]
                seen[character] = occurrence + 1
                let cell = Cell(
                    key: "\(character)#\(occurrence)", outline: glyph.outline, index: index, width: glyph.advance, x: cursor
                )
                cursor += glyph.advance + tracking
                return cell
            }
        }

        /// Each character's advance and outline at `size`, set on its own.
        private static func glyphs(_ text: String, size: CGFloat) -> [(advance: CGFloat, outline: Path)] {
            let scale = size / designSize
            // Glyph outlines are drawn y up; the view is y down.
            var flip = CGAffineTransform(scaleX: scale, y: -scale)
            return text.map { character in
                let units = Array(String(character).utf16)
                var glyphs = [CGGlyph](repeating: 0, count: units.count)
                CTFontGetGlyphsForCharacters(face, units, &glyphs, units.count)
                var advance = CGSize.zero
                CTFontGetAdvancesForGlyphs(face, .horizontal, glyphs, &advance, 1)
                let outline = CTFontCreatePathForGlyph(face, glyphs[0], &flip).map { Path($0) } ?? Path()
                return (advance.width * scale, outline)
            }
        }
    }

    struct Glyph: View {
        let cell: Cell
        /// Where the baseline runs within the view.
        let baseline: CGFloat
        /// The visual centre of the caps, above the baseline: what the glyph scales about.
        let centreAboveBaseline: CGFloat
        /// Blur at the far end of the fade.
        let blur: CGFloat
        let reduceMotion: Bool
        let onExited: (String) -> Void

        /// Layout position, animated on its own so a glide can run while the glyph enters or leaves.
        @State private var x: CGFloat?
        /// 0 away, 1 in place: drives the offset and the scale.
        @State private var motion: CGFloat = 0
        /// Drives the opacity and the blur.
        @State private var fade: CGFloat = 0
        /// Which way "away" is. Set, never animated.
        @State private var leaving = false

        var body: some View {
            let away = 1 - motion
            let shrink = reduceMotion ? 1 : Motion.shrink
            let rise = reduceMotion ? 0 : Motion.enterRise
            let up = reduceMotion ? 0 : Motion.exitUp
            let right = reduceMotion ? 0 : Motion.exitRight
            // A box around the glyph's own origin, tall enough for an accent and a descender.
            let box = CGSize(width: max(cell.width, 1), height: centreAboveBaseline * 2 * 2.4)
            let origin = CGPoint(x: 0, y: box.height / 2 + centreAboveBaseline)

            cell.outline
                .offsetBy(dx: origin.x, dy: origin.y)
                .fill(.white)
                .frame(width: box.width, height: box.height)
                .blur(radius: reduceMotion ? 0 : blur * (1 - fade))
                .opacity(fade)
                // The box is centred on the caps' visual centre, so the glyph shrinks in place
                // and does not sink toward its baseline.
                .scaleEffect(shrink + (1 - shrink) * motion)
                .offset(x: leaving ? right * away : 0, y: (leaving ? -up : rise) * away)
                .offset(x: x ?? cell.x, y: baseline - origin.y)
                .onAppear {
                    x = cell.x
                    apply(exiting: cell.isExiting)
                }
                .onChange(of: cell.isExiting) { _, exiting in apply(exiting: exiting) }
                .onChange(of: cell.x) { _, target in
                    if reduceMotion {
                        x = target
                    } else {
                        withAnimation(Motion.timed(Motion.glide).delay(Motion.glideDelay)) { x = target }
                    }
                }
        }

        /// In or out, from wherever the glyph is now.
        private func apply(exiting: Bool) {
            let stagger = reduceMotion ? 0 : Motion.stagger
            leaving = exiting

            // With Reduce Motion on nothing is played, delays included: the letter is there or
            // it is gone, as the reference's animations are under that setting.
            if reduceMotion {
                motion = exiting ? 0 : 1
                fade = exiting ? 0 : 1
                if exiting { onExited(cell.key) }
                return
            }

            if exiting {
                let delay = Double(cell.index) * stagger
                let key = cell.key
                withAnimation(Motion.timed(Motion.exit).delay(delay)) { motion = 0 }
                withAnimation(Motion.timed(Motion.exit).delay(delay)) {
                    fade = 0
                } completion: {
                    onExited(key)
                }
            } else {
                let delay = Motion.enterDelay + Double(cell.index) * stagger
                withAnimation(Motion.arrive.delay(delay)) { motion = 1 }
                withAnimation(Motion.timed(Motion.move).delay(delay)) { fade = 1 }
            }
        }
    }
}
