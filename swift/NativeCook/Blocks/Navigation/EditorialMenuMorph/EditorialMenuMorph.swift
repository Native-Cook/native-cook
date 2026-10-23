import SwiftUI
import UIKit

/// A museum's site with a hamburger button. Tapping it morphs the two lines into a cross and rolls
/// "Menu" to "Close" while a curtain with a soft liquid edge comes down over the sinking page and
/// the menu's serif words rise letter by letter from their clipped baselines. Running a finger
/// down the words turns the one under it italic, rules it and floats a lagging, leaning photo
/// card that wipes to each word's photo while the menu takes that word's colour. Lifting on a
/// word flies the card into its page's hero as the curtain lifts, then the page writes itself in.
struct EditorialMenuMorph: View {
    /// Opens the menu, glides down the words, opens a page from it and comes back, on a loop.
    var autoPlay = false
    /// Called with a section's word when it is chosen from the menu.
    var onSelect: ((String) -> Void)? = nil

    private let sections = EditorialMenuMorphSection.samples

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine = EditorialMenuMorphEngine()
    @State private var photos: [UIImage?] = Array(repeating: nil, count: EditorialMenuMorphSection.samples.count)

    var body: some View {
        // The block draws under the system bars and keeps its content clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            let stage = EditorialMenuMorphStage(width: size.width, height: size.height, top: insets.top, bottom: insets.bottom)
            scene(stage)
                .frame(width: size.width, height: size.height)
                .offset(x: -insets.leading, y: -insets.top)
                .task(id: "\(Int(stage.layout.heroW))x\(Int(stage.layout.heroH))") { await loadPhotos(stage.layout) }
        }
    }

    private func scene(_ stage: EditorialMenuMorphStage) -> some View {
        let words = sections.map(\.word)
        let select = onSelect
        engine.onSelect = select.map { callback in { i in if words.indices.contains(i) { callback(words[i]) } } }
        let tints = sections.map(\.tint)
        return TimelineView(.animation) { timeline in
            let frame = engine.step(to: timeline.date, stage: stage, tints: tints, autoPlay: autoPlay && !reduceMotion, reducedMotion: reduceMotion)
            EditorialMenuMorphScene(frame: frame, stage: stage, sections: sections, photos: photos, reduced: reduceMotion)
        }
        .frame(width: stage.width, height: stage.height)
        .background(Color(hex: EditorialMenuMorphStyle.paper))
        .clipped()
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { engine.touchChanged($0) }
                .onEnded { engine.touchEnded($0) }
        )
        .accessibilityElement()
        .accessibilityLabel("Orsel, Museum of Form. A menu button at the top right opens the sections: \(words.joined(separator: ", ")).")
    }

    /// One photo per section, at the hero's size: the menu's card and the page's hero draw the same image.
    private func loadPhotos(_ layout: EditorialMenuMorphLayout) async {
        await withTaskGroup(of: (Int, UIImage?).self) { group in
            for (i, s) in sections.enumerated() {
                let address = EditorialMenuMorphStyle.photo(s.photo, layout.heroW, layout.heroH)
                group.addTask { (i, await EditorialMenuMorphPhotos.load(address)) }
            }
            for await (i, image) in group where photos.indices.contains(i) {
                photos[i] = image
            }
        }
    }
}

private struct EditorialMenuMorphScene: View {
    let frame: EditorialMenuMorphFrame
    let stage: EditorialMenuMorphStage
    let sections: [EditorialMenuMorphSection]
    let photos: [UIImage?]
    let reduced: Bool

    private typealias M = EditorialMenuMorphMetrics
    private typealias E = EditorialMenuMorphEase
    private typealias S = EditorialMenuMorphStyle

    private func part(_ slice: (Double, Double)) -> Double { E.part(frame.open, slice) }

    private func photo(_ i: Int) -> UIImage? { photos.indices.contains(i) ? photos[i] : nil }

    var body: some View {
        let W = stage.width
        let H = stage.height
        let p = E.inOut(part(M.panel))
        let sink = reduced ? 0 : p
        ZStack(alignment: .topLeading) {
            EditorialMenuMorphPage(
                section: frame.section, previous: frame.previousSection, stage: stage, image: photo(frame.section),
                pageIn: frame.pageIn, heroShown: frame.heroShown, reduced: reduced
            )
            .equatable()
            .scaleEffect(1 - M.sinkScale * sink)
            .offset(y: M.sink * sink)

            Color.black.opacity(M.sinkShade * p).allowsHitTesting(false)

            curtain(p)

            menu(width: W, height: H)

            card

            header(width: W)

            Circle()
                .fill(Color.rgba(120, 110, 98, 0.35))
                .overlay(Circle().strokeBorder(Color.white.opacity(0.8), lineWidth: 1.5))
                .frame(width: 44, height: 44)
                .scaleEffect(1 - 0.2 * frame.ghostPress)
                .opacity(frame.ghostAlpha * 0.85)
                .offset(x: frame.ghostX - 22, y: frame.ghostY - 22)
        }
        .frame(width: W, height: H, alignment: .topLeading)
        .allowsHitTesting(false)
    }

    // MARK: The curtain

    /// The curtain down to `p` of the screen, its leading edge bowed ahead in the direction it moves.
    private func curtain(_ p: Double) -> some View {
        let color = frame.background.color
        let dir = frame.dir
        let reduced = reduced
        return Canvas { context, size in
            let W = size.width
            if reduced {
                guard p > 0 else { return }
                var faded = context
                faded.opacity = p
                faded.fill(Path(CGRect(origin: .zero, size: size)), with: .color(color))
                return
            }
            let edge = p * size.height
            guard edge > 0.5, W > 0 else { return }
            let bow = edge + 4.0 / 3.0 * dir * M.bulge * sin(.pi * p)
            var path = Path()
            path.move(to: CGPoint(x: -2, y: -2))
            path.addLine(to: CGPoint(x: W + 2, y: -2))
            path.addLine(to: CGPoint(x: W + 2, y: edge))
            path.addCurve(to: CGPoint(x: -2, y: edge), control1: CGPoint(x: W * 0.72, y: bow), control2: CGPoint(x: W * 0.28, y: bow))
            path.closeSubpath()
            context.fill(path, with: .color(color))
        }
        .frame(width: stage.width, height: stage.height)
    }

    // MARK: The menu

    private func menu(width W: Double, height H: Double) -> some View {
        let paper = Color(hex: S.paper)
        let f = E.out(part(M.footer))
        return ZStack(alignment: .topLeading) {
            VStack(alignment: .leading, spacing: 0) {
                ForEach(sections.indices, id: \.self) { i in
                    word(i)
                }
            }
            .padding(.leading, M.listLeft)
            .padding(.top, stage.listTop)

            HStack(alignment: .bottom) {
                VStack(alignment: .leading, spacing: 3) {
                    Text("Open today, 10:00–18:00").font(S.display(16, italic: true)).foregroundStyle(paper)
                    Text("Kestrel Quay 4, Lisbourne").font(S.sans(12, .medium)).foregroundStyle(paper.opacity(0.7))
                }
                Spacer(minLength: 0)
                Text("Tickets · Members · Shop").font(S.sans(12, .medium)).foregroundStyle(paper.opacity(0.7))
            }
            .padding(.top, 16)
            .overlay(alignment: .top) { paper.opacity(0.35).frame(height: 1 / 3) }
            .padding(.leading, M.listLeft)
            .padding(.trailing, 22)
            .opacity(f)
            .offset(y: reduced ? 0 : (1 - f) * 16)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
            .padding(.bottom, stage.bottom + 22)
        }
        .frame(width: W, height: H, alignment: .topLeading)
    }

    /// One menu word. Its letters rise one by one; under the finger it crossfades to its italic,
    /// steps right and draws a rule under itself while the other words dim.
    private func word(_ i: Int) -> some View {
        let s = sections[i]
        let R = E.wordReveal(frame.open, i)
        let italic = frame.italic.indices.contains(i) ? frame.italic[i] : 0
        let rule = frame.rule.indices.contains(i) ? frame.rule[i] : 0
        let focus = frame.focus.indices.contains(i) ? frame.focus[i] : 1
        let shift = frame.shift.indices.contains(i) ? frame.shift[i] : 0
        let widths = EditorialMenuMorphWordWidths.of(s.word)
        let paper = Color(hex: S.paper)
        let letters = Array(s.word).map(String.init)
        return HStack(alignment: .top, spacing: 0) {
            Text(String(format: "%02d", i + 1))
                .font(S.display(15, italic: true))
                .monospacedDigit()
                .foregroundStyle(paper.opacity(0.72))
                .frame(width: M.numberWidth, alignment: .leading)
                .padding(.top, 12)
                .opacity(R)
            ZStack(alignment: .topLeading) {
                HStack(spacing: 0) {
                    ForEach(letters.indices, id: \.self) { j in
                        let r = E.letterReveal(frame.open, i, j)
                        Text(letters[j])
                            .font(S.display(M.wordSize))
                            .kerning(-0.6)
                            .foregroundStyle(paper)
                            .fixedSize()
                            .frame(height: M.clip)
                            .rotationEffect(.degrees(reduced ? 0 : (1 - r) * 4))
                            .offset(y: reduced ? 0 : (1 - r) * M.clip)
                            .opacity(reduced ? R * (1 - italic) : 1 - italic)
                    }
                }
                Text(s.word)
                    .font(S.display(M.wordSize, italic: true))
                    .kerning(-0.6)
                    .foregroundStyle(paper)
                    .fixedSize()
                    .frame(height: M.clip)
                    .offset(y: reduced ? 0 : (1 - R) * M.clip)
                    .opacity(reduced ? italic * R : italic)
            }
            .frame(height: M.clip, alignment: .topLeading)
            .clipped()
            .overlay(alignment: .topLeading) {
                paper.opacity(0.85)
                    .frame(width: max(0, rule * E.lerp(widths.roman, widths.italic, italic)), height: 1)
                    .offset(y: M.ruleY)
                    .opacity(R)
            }
            Text(s.count)
                .font(S.sans(11, .semibold))
                .kerning(0.4)
                .monospacedDigit()
                .foregroundStyle(paper.opacity(0.62))
                .padding(.leading, 6)
                .padding(.top, 11)
                .offset(x: -(widths.roman - widths.italic) * italic)
                .opacity(R)
        }
        .frame(height: M.row, alignment: .topLeading)
        .opacity(focus)
        .offset(x: shift)
    }

    // MARK: The card

    /// The floating preview and, after a lift, the hero in flight: the photos split by the wipe,
    /// drawn "cover" into the card's animating rect, and the band on the wipe's edge.
    private var card: some View {
        let c = frame.card
        let current = frame.current
        let previous = frame.previous
        let wipe = frame.wipe
        let tone = EditorialMenuMorphRGB(S.cardTone).mix(EditorialMenuMorphRGB(S.heroPlaceholder), c.tone).color
        let band = sections.indices.contains(current) ? Color(hex: sections[current].tint) : Color(hex: S.baseTint)
        let currentPhoto = photo(current)
        let previousPhoto = previous >= 0 ? photo(previous) : nil
        return Canvas { context, _ in
            guard c.alpha > 0.002, c.w > 0.5, c.h > 0.5, c.x.isFinite, c.y.isFinite else { return }
            var outer = context
            outer.opacity = c.alpha
            outer.drawLayer { g in
                g.translateBy(x: c.x, y: c.y)
                g.rotate(by: .degrees(c.rot))
                g.translateBy(x: -c.w / 2, y: -c.h / 2)
                let rect = CGRect(x: 0, y: 0, width: c.w, height: c.h)
                g.clip(to: Path(roundedRect: rect, cornerRadius: max(0, c.radius), style: .circular))
                g.fill(Path(rect), with: .color(tone))
                let edge = c.h * (1 - wipe)
                if let image = previousPhoto {
                    var layer = g
                    layer.clip(to: Path(CGRect(x: 0, y: 0, width: c.w, height: edge)))
                    S.drawCover(layer, image, in: rect)
                }
                if let image = currentPhoto {
                    var layer = g
                    layer.clip(to: Path(CGRect(x: 0, y: edge, width: c.w, height: c.h - edge)))
                    // The incoming photo settles from slightly zoomed in as it is uncovered.
                    S.drawCover(layer, image, in: rect, zoom: M.wipeZoom - (M.wipeZoom - 1) * E.out(wipe))
                }
                if wipe > 0.001 && wipe < 0.999 {
                    g.fill(Path(CGRect(x: 0, y: edge - M.band, width: c.w, height: M.band)), with: .color(band))
                }
            }
            // The paper ring, drawn just inside the edge so the clip never cuts it; it thins and
            // fades as the photo flies into the hero.
            let ringAlpha = E.clamp(1 - c.tone * 1.6)
            if ringAlpha > 0.002, c.w > M.border * 2, c.h > M.border * 2 {
                var ring = context
                ring.opacity = c.alpha * ringAlpha
                ring.translateBy(x: c.x, y: c.y)
                ring.rotate(by: .degrees(c.rot))
                ring.translateBy(x: -c.w / 2, y: -c.h / 2)
                let inset = M.border / 2
                let rect = CGRect(x: inset, y: inset, width: c.w - M.border, height: c.h - M.border)
                let path = Path(roundedRect: rect, cornerRadius: max(0, c.radius - inset), style: .circular)
                ring.stroke(path, with: .color(Color(hex: S.paper)), lineWidth: max(0.5, M.border * (1 - c.tone)))
            }
        }
        .frame(width: stage.width, height: stage.height)
    }

    // MARK: The header

    private func header(width W: Double) -> some View {
        let b = E.inOut(part(M.burger))
        let ink = EditorialMenuMorphRGB(S.ink).mix(EditorialMenuMorphRGB(S.paper), b).color
        let meet = E.clamp(b * 2)
        let turn = E.clamp(b * 2 - 1)
        let short = 17.0 / 26
        let label = S.sans(11, .semibold)
        return HStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 0) {
                Text("ORSEL").font(S.display(24, bold: true)).kerning(5).foregroundStyle(ink)
                Text("MUSEUM OF FORM").font(S.sans(8.5, .semibold)).kerning(2.6).foregroundStyle(ink).padding(.top, -1)
            }
            Spacer(minLength: 0)
            // "Menu" rolls up and out as "Close" rolls in, with the burger's morph.
            ZStack(alignment: .trailing) {
                Text("MENU").font(label).kerning(1.6).foregroundStyle(ink)
                    .opacity(reduced ? 1 - b : 1)
                    .offset(y: reduced ? 0 : -b * 14)
                Text("CLOSE").font(label).kerning(1.6).foregroundStyle(ink)
                    .opacity(reduced ? b : 1)
                    .offset(y: reduced ? 0 : (1 - b) * 14)
            }
            .frame(minWidth: 44, alignment: .trailing)
            .frame(height: 14)
            .clipped()
            ZStack(alignment: .topLeading) {
                Color.clear
                line(ink)
                    .scaleEffect(x: 1 - 0.08 * meet, y: 1)
                    .rotationEffect(.degrees(45 * turn))
                    .offset(x: 9, y: 21.2 - 5 * (1 - meet))
                line(ink)
                    .scaleEffect(x: short + (0.92 - short) * meet, y: 1)
                    .rotationEffect(.degrees(-45 * turn))
                    .offset(x: 9 + (1 - meet) * ((26 - 17) / 2), y: 21.2 + 5 * (1 - meet))
            }
            .frame(width: 44, height: 44)
            .padding(.trailing, -6)
        }
        .frame(height: 48)
        .padding(.horizontal, 20)
        .padding(.top, stage.top + 6)
    }

    private func line(_ ink: Color) -> some View {
        RoundedRectangle(cornerRadius: 1)
            .fill(ink)
            .frame(width: 26, height: 1.6)
    }
}

/// A menu word's width set roman (letter by letter, as the menu draws it) and italic, so its
/// count and rule can follow the crossfade.
enum EditorialMenuMorphWordWidths {
    @MainActor private static var cache: [String: (roman: Double, italic: Double)] = [:]

    @MainActor
    static func of(_ word: String) -> (roman: Double, italic: Double) {
        if let hit = cache[word] { return hit }
        let size = EditorialMenuMorphMetrics.wordSize
        let roman = EditorialMenuMorphStyle.displayUIFont(size)
        let italic = EditorialMenuMorphStyle.displayUIFont(size, italic: true)
        let romanW = word.map { ch in
            (String(ch) as NSString).size(withAttributes: [.font: roman, .kern: -0.6]).width
        }.reduce(0, +)
        let italicW = (word as NSString).size(withAttributes: [.font: italic, .kern: -0.6]).width
        let widths = (roman: Double(romanW), italic: Double(italicW))
        cache[word] = widths
        return widths
    }
}
