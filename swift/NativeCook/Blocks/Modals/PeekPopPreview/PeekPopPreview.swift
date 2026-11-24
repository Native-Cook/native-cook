import SwiftUI
import UIKit

/// "Margin": a reading list whose rows can be peeked (long press) and popped into the article (swipe up).
/// The state and touch rules are PeekPreviewModel (reusable); this file is the screen, what is drawn inside the
/// frame, and the ghost finger. One clock drives the 10.5 s loop; a touch takes over and the loop restarts 2 s after
/// the last one. Under Reduce Motion there is no loop and every spring is a short timing.
struct PeekPopPreview: View {
    var autoPlay: Bool = true

    static let loop: Double = PeekPopPreviewMotion.loop

    fileprivate typealias M = PeekPopPreviewMotion

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var model = PeekPreviewModel()
    @State private var cache = PeekPopPreviewCache()
    /// The status bar turns light over the open page's cover; it flips once.
    @State private var onPage = false
    /// While the loop is off, a touch wakes the timeline until everything has settled.
    @State private var awake = false

    private var playing: Bool { autoPlay && !reduceMotion }
    private let articles = PeekPopPreviewArticle.all

    var body: some View {
        GeometryReader { outer in
            let ins = outer.safeAreaInsets
            GeometryReader { full in
                let m = cache.metrics(full.size, top: ins.top, bottom: ins.bottom, articles: articles)
                TimelineView(.animation(minimumInterval: nil, paused: !playing && !awake)) { tl in
                    screen(tl.date, m)
                }
                .onAppear { model.maxScrolls = m.maxScrolls }
                .onChange(of: m.maxScrolls) { _, now in model.maxScrolls = now }
            }
            .ignoresSafeArea()
        }
        .background(PeekPopPalette.desk.ignoresSafeArea())
        .preferredColorScheme(onPage ? .dark : .light)
        .onAppear { model.reset(playing: playing, reduced: reduceMotion) }
        .onChange(of: playing) { _, now in model.reset(playing: now, reduced: reduceMotion) }
    }

    // MARK: Screen

    private func screen(_ date: Date, _ m: PeekPopPreviewMetrics) -> some View {
        model.step(date, playing: playing)
        let active = model.active
        let pk = model.card, pp = model.pop.value
        let clamped = M.clamp(pk, 0, 1)
        syncFlags(pp)

        return ZStack(alignment: .topLeading) {
            PeekPopPreviewScene(m: m, articles: articles, covers: cache.allCovers, hidden: active)
                .equatable()
                .scaleEffect(M.mix(1, M.sceneScale, clamped))
                .allowsHitTesting(false)

            PeekPopPreviewBlur(fraction: M.sceneBlur / 100 * clamped * (1 - M.seg(pp, 0.9, 1)))
                .frame(width: m.width, height: m.height)
                .allowsHitTesting(false)
                .accessibilityHidden(true)

            Color(hex: 0x14110D)
                .opacity(M.sceneDim * clamped)
                .frame(width: m.width, height: m.height)
                .allowsHitTesting(false)

            touchLayer(m)

            actions(m, active: active, pk: clamped, pp: pp)

            if active >= 0 { frame(m, index: active, pk: pk, pp: pp) }

            ghost(m)
        }
        .frame(width: m.width, height: m.height, alignment: .topLeading)
        .coordinateSpace(name: "PeekPopPreview")
    }

    /// Thresholds only: these writes happen a handful of times per loop.
    private func syncFlags(_ pp: Double) {
        let page = pp > 0.6
        if page != onPage { DispatchQueue.main.async { onPage = page } }
        if awake && !playing && model.settled { DispatchQueue.main.async { awake = false } }
    }

    // MARK: Touch

    private func touchLayer(_ m: PeekPopPreviewMetrics) -> some View {
        ZStack(alignment: .topLeading) {
            Color.clear
            ForEach(articles.indices, id: \.self) { i in
                Color.clear
                    .frame(width: m.rows[i].width, height: m.rows[i].height)
                    .accessibilityElement()
                    .accessibilityLabel("\(articles[i].title), \(articles[i].source), \(articles[i].minutes) minutes")
                    .accessibilityHint("Opens the article. Touch and hold for a preview.")
                    .accessibilityAddTraits(.isButton)
                    .accessibilityHidden(model.frameIsUp)
                    .accessibilityAction { openFromAccessibility(i, m) }
                    .offset(x: m.rows[i].minX, y: m.rows[i].minY)
            }
        }
        .frame(width: m.width, height: m.height, alignment: .topLeading)
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0, coordinateSpace: .named("PeekPopPreview"))
                .onChanged { value in
                    if !tracking.on {
                        tracking.on = true
                        awake = true
                        model.down(at: value.startLocation, frame: frameRect(m), rows: m.rows, date: value.time)
                    }
                    model.moved(translation: value.translation)
                }
                .onEnded { value in
                    tracking.on = false
                    model.up(translation: value.translation, velocity: value.velocity, date: value.time)
                }
        )
    }

    @State private var tracking = PeekPopTracking()

    private func openFromAccessibility(_ index: Int, _ m: PeekPopPreviewMetrics) {
        awake = true
        let now = Date()
        let point = CGPoint(x: m.rows[index].midX, y: m.rows[index].midY)
        model.down(at: point, frame: frameRect(m), rows: m.rows, date: now)
        model.up(translation: .zero, velocity: .zero, date: now)
    }

    /// Where the frame is drawn right now, for deciding what a touch landed on.
    private func frameRect(_ m: PeekPopPreviewMetrics) -> CGRect {
        guard model.active >= 0 else { return .zero }
        let row = m.rows[model.active]
        let pk = model.card, pp = model.pop.value
        return CGRect(x: M.mix3(row.minX, m.card.minX, 0, pk, pp),
                      y: M.mix3(row.minY, m.card.minY, 0, pk, pp),
                      width: M.mix3(row.width, m.card.width, m.width, pk, pp),
                      height: M.mix3(row.height, m.card.height, m.height, pk, pp))
    }

    // MARK: The frame

    /// One rounded, shadowed box that is the row, then the preview card, then the page.
    private func frame(_ m: PeekPopPreviewMetrics, index: Int, pk: Double, pp: Double) -> some View {
        let row = m.rows[index]
        let w = max(1, M.mix3(row.width, m.card.width, m.width, pk, pp))
        let h = max(1, M.mix3(row.height, m.card.height, m.height, pk, pp))
        let x = M.mix3(row.minX, m.card.minX, 0, pk, pp)
        let y = M.mix3(row.minY, m.card.minY, 0, pk, pp)
        let radius = max(0, M.mix3(M.radiusRow, M.radiusCard, 0, M.clamp(pk, 0, 1), M.clamp(pp, 0, 1)))
        let lifted = M.clamp(max(model.press.value, pk), 0, 1) * (1 - M.seg(pp, 0.7, 1))
        let shape = RoundedRectangle(cornerRadius: radius, style: .continuous)
        let fade = M.seg(pk, 0.4, 0.8) * (1 - M.seg(pp, 0.05, 0.45))

        return ZStack(alignment: .topLeading) {
            shape.fill(PeekPopPalette.sheet)
                .shadow(color: .rgba(26, 24, 18, 0.16), radius: 3, x: 0, y: 2)
                .shadow(color: .rgba(26, 24, 18, 0.30), radius: 26, x: 0, y: 26)
                .opacity(lifted)
                .allowsHitTesting(false)
            shape.fill(PeekPopPalette.sheet)
                .shadow(color: .rgba(26, 24, 18, 0.08), radius: 1, x: 0, y: 1)
                .shadow(color: .rgba(26, 24, 18, 0.05), radius: 5, x: 0, y: 4)
                .allowsHitTesting(false)

            ZStack(alignment: .topLeading) {
                PeekPopPreviewFace(article: articles[index], image: cache.cover(articles[index], m), m: m, pk: pk, pp: pp, reveal: model.reveal)
                    .offset(y: -model.scroll.value)
                LinearGradient(stops: [.init(color: PeekPopPalette.sheet.opacity(0), location: 0),
                                       .init(color: PeekPopPalette.sheet.opacity(0.92), location: 0.6),
                                       .init(color: PeekPopPalette.sheet, location: 1)],
                               startPoint: .top, endPoint: .bottom)
                    .frame(width: w, height: 56)
                    .offset(y: h - 56)
                    .opacity(fade)
            }
            .frame(width: w, height: h, alignment: .topLeading)
            .background(PeekPopPalette.sheet)
            .clipShape(shape)
            .allowsHitTesting(false)

            nav(m, pp: pp, width: w)
        }
        .frame(width: w, height: h, alignment: .topLeading)
        .scaleEffect(1 + M.pressScale * model.press.value)
        .offset(x: x, y: y)
    }

    /// The page's two round buttons over a scrim, settling in late in the pop.
    private func nav(_ m: PeekPopPreviewMetrics, pp: Double, width: Double) -> some View {
        let u = M.easeOutCubic(M.seg(pp, M.navAt.0, M.navAt.1))
        let size = PeekPopPreviewMetrics.nav
        return ZStack(alignment: .topLeading) {
            LinearGradient(colors: [.rgba(10, 10, 12, 0.36), .rgba(10, 10, 12, 0)], startPoint: .top, endPoint: .bottom)
                .frame(width: width, height: m.navTop + size + 28)
                .allowsHitTesting(false)
            Button {
                awake = true
                model.closeFromTouch()
            } label: {
                navFace("chevron.left", weight: .semibold)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Back to Reading list")
            .offset(x: m.titleX[2] - 6, y: m.navTop)
            navFace("textformat.size", weight: .medium)
                .accessibilityHidden(true)
                .allowsHitTesting(false)
                .offset(x: width - (m.titleX[2] - 6) - size, y: m.navTop)
        }
        .frame(width: width, height: m.navTop + size + 28, alignment: .topLeading)
        .opacity(u)
        .offset(y: -M.navDrop * (1 - u))
        .allowsHitTesting(pp > 0.8)
    }

    private func navFace(_ symbol: String, weight: Font.Weight) -> some View {
        Image(systemName: symbol)
            .font(.system(size: 14.5, weight: weight)) // expo-symbols fits the glyph inside a 17 pt box
            .foregroundStyle(PeekPopPalette.ink)
            .frame(width: PeekPopPreviewMetrics.nav, height: PeekPopPreviewMetrics.nav)
            .background(
                Circle()
                    .fill(Color.rgba(253, 252, 249, 0.90))
                    .shadow(color: .rgba(10, 10, 12, 0.16), radius: 1, x: 0, y: 1)
                    .shadow(color: .rgba(10, 10, 12, 0.14), radius: 7, x: 0, y: 6)
            )
            .contentShape(Circle().inset(by: -8))
    }

    // MARK: Quick actions

    private func actions(_ m: PeekPopPreviewMetrics, active: Int, pk: Double, pp: Double) -> some View {
        let reveal = model.reveal
        let gone = M.seg(pp, 0, 0.35)
        let u = M.easeOutCubic(M.seg(reveal, M.actionAt[0] - 0.12, M.actionAt[0] - 0.12 + M.revealSpan)) * M.seg(pk, 0.5, 0.85)
        let shape = RoundedRectangle(cornerRadius: 16, style: .continuous)
        return VStack(spacing: 0) {
            ForEach(PeekPopPreviewMetrics.actions.indices, id: \.self) { i in
                actionRow(i, reveal: reveal)
            }
        }
        .frame(width: m.actionsWidth)
        .background(PeekPopPalette.sheet)
        .clipShape(shape)
        .background(
            shape.fill(PeekPopPalette.sheet)
                .shadow(color: .rgba(20, 17, 13, 0.14), radius: 1.5, x: 0, y: 1)
                .shadow(color: .rgba(20, 17, 13, 0.22), radius: 20, x: 0, y: 18)
        )
        .scaleEffect(M.mix(M.panelScale, 1, u), anchor: .topLeading)
        .opacity(active < 0 ? 0 : u * (1 - gone))
        .offset(x: m.actionsAt.x, y: m.actionsAt.y - M.panelRise * (1 - u) + 36 * gone)
        .allowsHitTesting(active >= 0 && reveal > 0.9 && pp < 0.1)
        .accessibilityHidden(active < 0 || reveal < 0.9)
    }

    private func actionRow(_ i: Int, reveal: Double) -> some View {
        let action = PeekPopPreviewMetrics.actions[i]
        let tint = action.destructive ? PeekPopPalette.danger : PeekPopPalette.ink
        let start = M.actionAt[min(i, M.actionAt.count - 1)]
        let u = M.easeOutCubic(M.seg(reveal, start, start + M.revealSpan))
        return Button {
            awake = true
            model.closeFromTouch()
        } label: {
            HStack(spacing: 0) {
                Text(action.label)
                    .font(.system(size: 17, weight: .regular)).tracking(-0.3)
                    .foregroundStyle(tint)
                Spacer(minLength: 0)
                Image(systemName: action.symbol)
                    .font(.system(size: 16, weight: .medium)) // expo-symbols fits the glyph inside a 19 pt box
                    .foregroundStyle(tint)
            }
            .padding(.leading, 16)
            .padding(.trailing, 14)
            .frame(height: PeekPopPreviewMetrics.actionRow)
            .opacity(u)
            .offset(y: M.actionRise * (1 - u))
            .contentShape(Rectangle())
        }
        .buttonStyle(PeekPopActionStyle())
        .overlay(alignment: .top) {
            if i > 0 { Color.rgba(26, 27, 30, 0.12).frame(height: 1 / UIScreen.main.scale) }
        }
        .accessibilityLabel(action.label)
    }

    // MARK: Ghost

    private func ghost(_ m: PeekPopPreviewMetrics) -> some View {
        let a = m.rows[min(M.rowA, m.rows.count - 1)]
        let b = m.rows[min(M.rowB, m.rows.count - 1)]
        let g = M.ghostAt(model.clock,
                          a: CGPoint(x: a.minX + a.width * 0.55, y: a.midY),
                          b: CGPoint(x: b.minX + b.width * 0.7, y: b.midY),
                          s: CGPoint(x: m.width * 0.62, y: m.height * 0.66),
                          d: CGPoint(x: m.width * 0.5, y: m.height * 0.3))
        return Circle()
            .fill(Color.rgba(26, 27, 30, 0.20))
            .overlay(Circle().strokeBorder(Color.white.opacity(0.9), lineWidth: 1.5))
            .frame(width: M.ghostSize, height: M.ghostSize)
            .scaleEffect(1 - M.ghostPress * g.pressed)
            .opacity(model.scripted ? g.shown : 0)
            .offset(x: g.x - M.ghostSize / 2, y: g.y - M.ghostSize / 2)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }
}

/// Whether a finger is being tracked. A box, so a gesture's first change is seen without a redraw.
private final class PeekPopTracking {
    var on = false
}

private struct PeekPopActionStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.background(configuration.isPressed ? Color.rgba(26, 27, 30, 0.07) : Color.clear)
    }
}

enum PeekPopPalette {
    static let desk = Color(hex: 0xEFEEE9)
    static let sheet = Color(hex: 0xFDFCF9)
    static let ink = Color(hex: 0x1A1B1E)
    static let inkBody = Color(hex: 0x2C2D32)
    static let inkSoft = Color(hex: 0x7C7D84)
    static let leaf = Color(hex: 0x0F7B5F)
    static let danger = Color(hex: 0xD23A2E)
}

/// Covers and layout, made once per size.
final class PeekPopPreviewCache {
    private var key: [Double] = []
    private var made: PeekPopPreviewMetrics?
    private var covers: [String: UIImage] = [:]

    func metrics(_ size: CGSize, top: Double, bottom: Double, articles: [PeekPopPreviewArticle]) -> PeekPopPreviewMetrics {
        let next = [size.width, size.height, top, bottom]
        if next == key, let made { return made }
        let m = PeekPopPreviewMetrics(width: size.width, height: size.height, top: top, bottom: bottom, articles: articles)
        key = next
        made = m
        covers = Dictionary(uniqueKeysWithValues: articles.map {
            ($0.id, PeekPopPreviewCover.image($0, size: CGSize(width: m.width, height: m.heroHeight)))
        })
        return m
    }

    func cover(_ article: PeekPopPreviewArticle, _ m: PeekPopPreviewMetrics) -> UIImage {
        covers[article.id] ?? PeekPopPreviewCover.image(article, size: CGSize(width: m.width, height: m.heroHeight))
    }

    var allCovers: [String: UIImage] { covers }
}

/// Where things sit, in points: the same numbers as React Native's `measure`.
struct PeekPopPreviewMetrics: Equatable {
    static let side: Double = 16
    static let maxColumn: Double = 520
    static let header: Double = 104
    static let rowGap: Double = 8
    static let rowMax: Double = 88
    static let rowMin: Double = 72
    static let foot: Double = 60
    static let coverInset: Double = 12
    static let thumbRadius: Double = 12
    static let cardSide: Double = 14
    static let cardMax: Double = 400
    static let cardPad: Double = 18
    static let pagePad: Double = 20
    static let actionsGap: Double = 10
    static let actionsMaxWidth: Double = 250
    static let actionRow: Double = 46
    static let titleSize: Double = 26
    static let titleLine: Double = 31
    static let titleRow: Double = 16.0 / 26.0
    static let titleCardScale: Double = 21.0 / 26.0
    static let metaLine: Double = 18
    static let bodySize: Double = 17
    static let bodyLine: Double = 26
    static let excerptLines: Double = 4
    static let nav: Double = 38

    static let actions: [(label: String, symbol: String, destructive: Bool)] = [
        ("Save offline", "arrow.down.circle", false),
        ("Share", "square.and.arrow.up", false),
        ("Archive", "archivebox", true),
    ]

    let width: Double
    let height: Double
    let topInset: Double
    let rows: [CGRect]
    let rowHeight: Double
    let thumb: Double
    let card: CGRect
    let coverHeight: Double
    let heroHeight: Double
    let textWidth: Double
    let titleX: [Double]
    let titleY: [Double]
    let metaY: [Double]
    let contentY: [Double]
    let contentScale: Double
    let titleCard: Double
    let actionsAt: CGPoint
    let actionsWidth: Double
    let footer: Double?
    let navTop: Double
    let maxScrolls: [Double]

    init(width: Double, height: Double, top: Double, bottom: Double, articles: [PeekPopPreviewArticle]) {
        typealias K = PeekPopPreviewMetrics
        self.width = width
        self.height = height
        topInset = top
        let count = articles.count
        let column = min(width, K.maxColumn)
        let ox = (width - column) / 2
        let listTop = top + 8 + K.header
        let room = height - max(bottom, 12) - listTop
        let gaps = K.rowGap * Double(count - 1)
        var rowH = floor((room - K.foot - gaps) / Double(count))
        var foot: Double?
        if rowH >= K.rowMin {
            rowH = min(rowH, K.rowMax)
            let end = listTop + rowH * Double(count) + gaps
            foot = end + (height - max(bottom, 12) - end - 48) / 2
        } else {
            rowH = min(floor((room - gaps) / Double(count)), K.rowMax)
        }
        footer = foot
        rowHeight = rowH
        rows = (0..<count).map { CGRect(x: ox + K.side, y: listTop + Double($0) * (rowH + K.rowGap), width: column - K.side * 2, height: rowH) }
        let thumbSize = rowH - 24
        thumb = thumbSize
        let textW = column - K.pagePad * 2
        textWidth = textW

        let cardWidth = min(column - K.cardSide * 2, K.cardMax)
        let coverH = ((min(cardWidth * 0.5, height * 0.21))).rounded()
        coverHeight = coverH
        let scale = (cardWidth - K.cardPad * 2) / textW
        contentScale = scale
        let titleScale = min(K.titleCardScale, scale)
        titleCard = titleScale
        let titleInCard = coverH + 14
        let metaInCard = titleInCard + (K.titleLine * 2 * titleScale).rounded() + 5
        let contentInCard = metaInCard + K.metaLine + 10
        let cardH = contentInCard + (K.excerptLines * K.bodyLine * scale).rounded() + 16
        let actionsH = K.actionRow * Double(K.actions.count)
        let stack = cardH + K.actionsGap + actionsH
        let cardTop = top + max(8, (height - top - bottom - stack) / 2)
        let cardRect = CGRect(x: (width - cardWidth) / 2, y: cardTop, width: cardWidth, height: cardH)
        card = cardRect
        actionsAt = CGPoint(x: cardRect.minX, y: cardRect.minY + cardH + K.actionsGap)
        actionsWidth = min(K.actionsMaxWidth, cardWidth)

        let hero = (height * 0.36).rounded()
        heroHeight = hero
        let titleFull = hero + 20
        let metaFull = titleFull + K.titleLine * 2 + 8
        let titleInRow = ((rowH - ((K.titleLine * 2 * K.titleRow).rounded() + 4 + K.metaLine)) / 2).rounded()
        titleX = [K.coverInset + thumbSize + 12, K.cardPad, ox + K.pagePad]
        titleY = [titleInRow, titleInCard, titleFull]
        metaY = [titleInRow + (K.titleLine * 2 * K.titleRow).rounded() + 4, metaInCard, metaFull]
        let contentFull = metaFull + K.metaLine + 18
        contentY = [contentInCard, contentFull]
        navTop = top + 4

        let pageBottom = max(bottom, 12) + 28
        maxScrolls = articles.map { article in
            max(0, contentFull + K.textHeight(article, width: textW) + pageBottom - height)
        }
    }

    /// The height of an article's text at the page's size, for how far the page scrolls.
    private static func textHeight(_ article: PeekPopPreviewArticle, width: Double) -> Double {
        let style = NSMutableParagraphStyle()
        style.minimumLineHeight = bodyLine
        style.maximumLineHeight = bodyLine
        let attributes: [NSAttributedString.Key: Any] = [.font: UIFont.systemFont(ofSize: bodySize), .kern: -0.2, .paragraphStyle: style]
        let paragraphs = [article.excerpt] + article.body
        let heights = paragraphs.map { text in
            ceil((text as NSString).boundingRect(with: CGSize(width: width, height: .greatestFiniteMagnitude),
                                                 options: [.usesLineFragmentOrigin, .usesFontLeading],
                                                 attributes: attributes, context: nil).height)
        }
        return heights.reduce(0, +) + 16 * Double(max(paragraphs.count - 1, 0))
    }

    static func == (a: PeekPopPreviewMetrics, b: PeekPopPreviewMetrics) -> Bool {
        a.width == b.width && a.height == b.height && a.topInset == b.topInset && a.rowHeight == b.rowHeight
    }
}

/// Text at a line height, laid out the way React Native centres the extra leading.
private struct PeekPopLeading: ViewModifier {
    let font: UIFont
    let line: Double

    func body(content: Content) -> some View {
        let extra = max(0, line - font.lineHeight)
        return content.lineSpacing(extra).padding(.vertical, extra / 2)
    }
}

private extension UIFont {
    static func peekPop(_ size: CGFloat, _ weight: UIFont.Weight) -> UIFont { .systemFont(ofSize: size, weight: weight) }
}

/// The list and its header. It redraws only when the item that is out changes.
private struct PeekPopPreviewScene: View, Equatable {
    let m: PeekPopPreviewMetrics
    let articles: [PeekPopPreviewArticle]
    let covers: [String: UIImage]
    let hidden: Int

    static func == (a: Self, b: Self) -> Bool { a.m == b.m && a.hidden == b.hidden }

    private typealias K = PeekPopPreviewMetrics

    var body: some View {
        let minutes = articles.reduce(0) { $0 + $1.minutes }
        ZStack(alignment: .topLeading) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(alignment: .firstTextBaseline) {
                    Text("Margin").font(.system(size: 15, weight: .bold)).tracking(-0.2).foregroundStyle(PeekPopPalette.leaf)
                    Spacer(minLength: 0)
                    Text("Edit").font(.system(size: 16, weight: .medium)).foregroundStyle(PeekPopPalette.leaf)
                }
                Text("Reading list")
                    .font(.system(size: 33, weight: .heavy)).tracking(-0.8)
                    .foregroundStyle(PeekPopPalette.ink)
                    .frame(height: 38)
                    .padding(.top, 6)
                    .accessibilityAddTraits(.isHeader)
                Text("\(articles.count) saved · \(minutes) min of reading")
                    .font(.system(size: 14, weight: .medium)).monospacedDigit()
                    .foregroundStyle(PeekPopPalette.inkSoft)
                    .padding(.top, 2)
            }
            .padding(.horizontal, 4)
            .frame(width: m.rows[0].width, alignment: .topLeading)
            .offset(x: m.rows[0].minX, y: m.topInset + 8)

            ForEach(articles.indices, id: \.self) { i in
                PeekPopPreviewRow(article: articles[i], image: covers[articles[i].id], m: m)
                    .frame(width: m.rows[i].width, height: m.rows[i].height, alignment: .topLeading)
                    .opacity(hidden == i ? 0 : 1)
                    .offset(x: m.rows[i].minX, y: m.rows[i].minY)
                    .accessibilityHidden(true)
            }

            if let footer = m.footer {
                HStack(spacing: 8) {
                    Image(systemName: "plus").font(.system(size: 13, weight: .bold))
                    Text("Save a link").font(.system(size: 16, weight: .semibold)).tracking(-0.2)
                }
                .foregroundStyle(PeekPopPalette.sheet)
                .frame(width: 164, height: 48)
                .background(
                    Capsule()
                        .fill(PeekPopPalette.ink)
                        .shadow(color: .rgba(26, 24, 18, 0.20), radius: 1, x: 0, y: 1)
                        .shadow(color: .rgba(26, 24, 18, 0.18), radius: 11, x: 0, y: 10)
                )
                .offset(x: (m.width - 164) / 2, y: footer)
            }
        }
        .frame(width: m.width, height: m.height, alignment: .topLeading)
    }
}

/// A row at rest. It is drawn exactly as the frame is at peek 0, so the hand-over is invisible.
private struct PeekPopPreviewRow: View {
    let article: PeekPopPreviewArticle
    let image: UIImage?
    let m: PeekPopPreviewMetrics

    private typealias K = PeekPopPreviewMetrics

    var body: some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: PeekPopPreviewMotion.radiusRow, style: .continuous)
                .fill(PeekPopPalette.sheet)
                .shadow(color: .rgba(26, 24, 18, 0.08), radius: 1, x: 0, y: 1)
                .shadow(color: .rgba(26, 24, 18, 0.05), radius: 5, x: 0, y: 4)
            Group {
                if let image {
                    Image(uiImage: image)
                        .resizable()
                        .interpolation(.high)
                        .frame(width: m.width, height: m.heroHeight)
                        .scaleEffect(max(m.thumb / m.width, m.thumb / m.heroHeight))
                        .frame(width: m.thumb, height: m.thumb)
                        .clipShape(RoundedRectangle(cornerRadius: K.thumbRadius, style: .continuous))
                }
            }
            .offset(x: K.coverInset, y: (m.rowHeight - m.thumb) / 2)

            PeekPopTitle(text: article.title, width: m.textWidth)
                .scaleEffect(K.titleRow, anchor: .topLeading)
                .offset(x: m.titleX[0], y: m.titleY[0])
            PeekPopMeta(text: article.meta, width: m.textWidth)
                .offset(x: m.titleX[0], y: m.metaY[0])
            PeekPopTopic(text: article.topic, width: m.rows[0].width)
                .offset(y: m.metaY[0])
        }
    }
}

private struct PeekPopTitle: View {
    let text: String
    let width: Double

    /// Breaks the title greedily, as React Native does: SwiftUI would rebalance it to avoid a one-word last line,
    /// and the row, the preview and the page must all show the same two lines.
    private var wrapped: String {
        let font = UIFont.systemFont(ofSize: PeekPopPreviewMetrics.titleSize, weight: .heavy)
        let attributes: [NSAttributedString.Key: Any] = [.font: font, .kern: -0.6]
        var lines: [String] = []
        var line = ""
        for word in text.split(separator: " ") {
            let next = line.isEmpty ? String(word) : line + " " + word
            if !line.isEmpty && (next as NSString).size(withAttributes: attributes).width > width {
                lines.append(line)
                line = String(word)
            } else {
                line = next
            }
        }
        lines.append(line)
        return lines.joined(separator: "\n")
    }

    var body: some View {
        Text(wrapped)
            .font(.system(size: PeekPopPreviewMetrics.titleSize, weight: .heavy))
            .tracking(-0.6)
            .foregroundStyle(PeekPopPalette.ink)
            .lineLimit(2)
            .modifier(PeekPopLeading(font: .peekPop(PeekPopPreviewMetrics.titleSize, .heavy), line: PeekPopPreviewMetrics.titleLine))
            .frame(width: width, alignment: .topLeading)
    }
}

private struct PeekPopMeta: View {
    let text: String
    let width: Double

    var body: some View {
        Text(text)
            .font(.system(size: 13, weight: .medium)).monospacedDigit()
            .foregroundStyle(PeekPopPalette.inkSoft)
            .lineLimit(1)
            .frame(width: width, height: PeekPopPreviewMetrics.metaLine, alignment: .leading)
    }
}

private struct PeekPopTopic: View {
    let text: String
    let width: Double

    var body: some View {
        Text(text)
            .font(.system(size: 12, weight: .semibold))
            .foregroundStyle(PeekPopPalette.leaf)
            .frame(width: width - 14, height: PeekPopPreviewMetrics.metaLine, alignment: .trailing)
    }
}

/// The same article inside the frame: every piece travels row → preview → page on the shared values.
private struct PeekPopPreviewFace: View {
    let article: PeekPopPreviewArticle
    let image: UIImage
    let m: PeekPopPreviewMetrics
    let pk: Double
    let pp: Double
    let reveal: Double

    private typealias M = PeekPopPreviewMotion
    private typealias K = PeekPopPreviewMetrics

    var body: some View {
        let coverW = max(1, M.mix3(m.thumb, m.card.width, m.width, pk, pp))
        let coverH = max(1, M.mix3(m.thumb, m.coverHeight, m.heroHeight, pk, pp))
        let coverRadius = max(0, M.mix3(K.thumbRadius, 0, 0, M.clamp(pk, 0, 1), M.clamp(pp, 0, 1)))
        let lands = M.easeOutCubic(M.seg(reveal, M.excerptAt, M.excerptAt + M.revealSpan)) * M.seg(pk, 0.45, 0.8)
        let u = max(lands, M.seg(pp, 0, 0.3))

        ZStack(alignment: .topLeading) {
            Image(uiImage: image)
                .resizable()
                .interpolation(.high)
                .frame(width: m.width, height: m.heroHeight)
                .scaleEffect(max(coverW / m.width, coverH / m.heroHeight))
                .frame(width: coverW, height: coverH)
                .clipShape(RoundedRectangle(cornerRadius: coverRadius, style: .continuous))
                .offset(x: M.mix3(K.coverInset, 0, 0, pk, pp), y: M.mix3((m.rowHeight - m.thumb) / 2, 0, 0, pk, pp))

            PeekPopTitle(text: article.title, width: m.textWidth)
                .scaleEffect(M.mix3(K.titleRow, m.titleCard, 1, pk, pp), anchor: .topLeading)
                .offset(x: M.mix3(m.titleX[0], m.titleX[1], m.titleX[2], pk, pp),
                        y: M.mix3(m.titleY[0], m.titleY[1], m.titleY[2], pk, pp))
            PeekPopMeta(text: article.meta, width: m.textWidth)
                .offset(x: M.mix3(m.titleX[0], m.titleX[1], m.titleX[2], pk, pp),
                        y: M.mix3(m.metaY[0], m.metaY[1], m.metaY[2], pk, pp))
            PeekPopTopic(text: article.topic, width: m.rows[0].width)
                .opacity(1 - M.seg(pk, 0, 0.3))
                .offset(y: m.metaY[0])

            if u > 0.001 {
                PeekPopBodyText(article: article, width: m.textWidth)
                    .blur(radius: (1 - u) * M.excerptBlur)
                    .opacity(u)
                    .scaleEffect(M.mix3(m.contentScale, m.contentScale, 1, pk, pp), anchor: .topLeading)
                    .offset(x: M.mix3(m.titleX[1], m.titleX[1], m.titleX[2], pk, pp),
                            y: M.mix3(m.contentY[0] - 10, m.contentY[0], m.contentY[1], pk, pp))
            }
        }
    }
}

private struct PeekPopBodyText: View, Equatable {
    let article: PeekPopPreviewArticle
    let width: Double

    static func == (a: Self, b: Self) -> Bool { a.article.id == b.article.id && a.width == b.width }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            paragraph(article.excerpt)
            ForEach(article.body.indices, id: \.self) { i in paragraph(article.body[i]) }
        }
        .frame(width: width, alignment: .topLeading)
        .fixedSize(horizontal: false, vertical: true)
    }

    private func paragraph(_ text: String) -> some View {
        Text(text)
            .font(.system(size: PeekPopPreviewMetrics.bodySize))
            .tracking(-0.2)
            .foregroundStyle(PeekPopPalette.inkBody)
            .modifier(PeekPopLeading(font: .peekPop(PeekPopPreviewMetrics.bodySize, .regular), line: PeekPopPreviewMetrics.bodyLine))
            .fixedSize(horizontal: false, vertical: true)
    }
}
