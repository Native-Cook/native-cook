import SwiftUI
import UIKit

/// "Longhand": a long read whose words ink in as you scroll. The article is dim; a soft front moves word by word
/// under a reading line that runs from three lines into the body to the last line at the end of the scroll. One
/// scroll value drives the front, the amber progress line and the minutes left in the nav bar.
///
/// The text is laid out once (ScrollReadHighlightPage.swift, CoreText at the 12 pt optical size, as the React
/// Native build's Skia paragraphs); a frame fills each visible paragraph's outline dim or ink and masks only the
/// paragraph at the front, word by word. The scroll is a real `UIScrollView`: the 10 s loop drives its offset (three
/// flicks down, a hold, an eased return); a finger takes over, and 2 s after the scroll comes to rest it eases back
/// to the top and the loop restarts. Reduce Motion: the article fully inked, no loop.
struct ScrollReadHighlight: View {
    struct Article {
        let section: String
        let title: String
        let byline: String
        let paragraphs: [String]
    }

    var autoPlay: Bool = true
    var article: Article = ScrollReadHighlight.defaultArticle

    static let loop: Double = ScrollReadHighlightMotion.loop

    static let defaultArticle = Article(
        section: "Longhand",
        title: "The last ferry to Orrin",
        byline: "Words by Tomas Vale",
        paragraphs: [
            "The boat leaves at ten past six, whether or not anyone is on it. Most evenings there are four of us: a nurse going home, two boys with a crate of bait, and whoever has misread the timetable and is now committed to an hour on the water.",
            "Nobody talks much on the way out. The engine takes up the room where a conversation would go, and the harbour lights slide behind the stern one at a time, until the only thing left to look at is the long grey line where the sea stops pretending to be flat.",
            "Ines has run this crossing for twenty-six years. She knows the channel by the colour of it, and she will tell you the sandbar moved in the winter of the big storm, as if it had packed a bag and left without saying where it was going.",
            "When the council proposed a bridge, she went to the meeting and said nothing at all. She sat in the second row with her coat on, listened to the engineers talk about journey times, and went back to work in the morning. The bridge was never funded.",
            "What a bridge would save is forty minutes. What it would cost is harder to put in a report. On the ferry you cannot be reached, and you cannot hurry, and for one hour a day the island is still far enough away to be somewhere you arrive.",
            "Halfway across, the nurse usually takes off her shoes. The boys stop arguing. The light goes the colour of weak tea, and for a little while everyone on board looks like someone who has remembered what they were going to say and decided it can wait.",
            "Then the jetty comes up out of the dusk, smaller than you expected, with one lamp and a dog that belongs to nobody. Ines cuts the engine early and lets the tide do the last of the work. The rope goes over, the boat settles, and we all step off without saying goodnight, because we will see each other tomorrow.",
        ]
    )

    private typealias M = ScrollReadHighlightMotion

    private static let background = Color(hex: 0x15171B)
    private static let ink = Color(hex: 0xF1ECE2)
    private static let dim = Color.rgba(241, 236, 226, 0.26)
    private static let soft = Color(hex: 0x9A988F)
    private static let amber = Color(hex: 0xE9B872)

    private static let side = 22.0
    private static let maxWidth = 620.0
    private static let nav = 48.0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var model = ScrollReadHighlightModel()
    @State private var cache = ScrollReadHighlightCache()
    /// True while a finger or the scroll's momentum moves the page, so a paused timeline (Reduce Motion) redraws.
    @State private var scrolling = false

    private var playing: Bool { autoPlay && !reduceMotion }

    var body: some View {
        GeometryReader { outer in
            let ins = outer.safeAreaInsets
            GeometryReader { full in
                TimelineView(.animation(minimumInterval: nil, paused: reduceMotion && !scrolling)) { tl in
                    screen(tl.date.timeIntervalSinceReferenceDate, size: full.size, ins: ins)
                }
            }
            .ignoresSafeArea()
        }
        .background(Self.background.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear { model.reset() }
        .onChange(of: reduceMotion) { _, _ in model.reset() }
    }

    private func screen(_ now: Double, size: CGSize, ins: EdgeInsets) -> some View {
        let W = size.width, H = size.height
        let navHeight = ins.top + Self.nav
        let viewport = max(H - navHeight, 1)
        let column = min(W - Self.side * 2, Self.maxWidth)
        let left = (W - column) / 2
        let bodySize = W >= 390 ? 20.0 : 18.0
        let bodyLeading = W >= 390 ? 31.0 : 28.0
        let page = cache.page(article, x: left, width: column, bodySize: bodySize, bodyLeading: bodyLeading)
        let contentHeight = page.height + ins.bottom
        let maxScroll = max(contentHeight - viewport, 1)
        // The reading line starts three lines into the body and reaches the last line exactly at the end of the scroll.
        let readStart = page.firstBodyTop + bodyLeading * 3.4
        let readEnd = page.lastBottom + 2 - maxScroll

        let f = model.step(now: now, playing: playing, maxScroll: maxScroll)
        let y = f.offset
        let front: Double
        if reduceMotion {
            front = Double(page.wordCount) + M.fadeWords
        } else {
            let share = min(max(y / maxScroll, 0), 1)
            let line = max(y, 0) + M.mix(readStart, readEnd, share)
            // At the very end the last words are carried home, so a finished article is fully inked.
            front = M.frontAt(line, page) + M.seg(share, 0.985, 1) * M.fadeWords
        }
        let wordsLeft = max(Double(page.wordCount) - front, 0)
        let minutes = wordsLeft < 0.5 ? 0 : max(Int((wordsLeft / M.wordsPerMinute).rounded(.up)), 1)
        let allInked = reduceMotion

        return ZStack(alignment: .topLeading) {
            // The reader.
            ZStack(alignment: .top) {
                Canvas { ctx, _ in
                    Self.drawArticle(&ctx, page, offset: y, viewport: viewport, front: front, allInked: allInked)
                }
                .frame(width: W, height: viewport)
                .allowsHitTesting(false)

                ScrollReadHighlightScroller(model: model, contentHeight: contentHeight, onActive: { active in
                    if scrolling != active { scrolling = active }
                })
                .frame(width: W, height: viewport)
                .accessibilityElement()
                .accessibilityLabel(([article.title, article.byline] + article.paragraphs).joined(separator: "\n\n"))

                LinearGradient(stops: [
                    .init(color: Self.background, location: 0),
                    .init(color: Color(hex: 0x15171B, opacity: 0.6), location: 0.4),
                    .init(color: Color(hex: 0x15171B, opacity: 0), location: 1),
                ], startPoint: .top, endPoint: .bottom)
                .frame(width: W, height: 22)
                .allowsHitTesting(false)

                LinearGradient(stops: [
                    .init(color: Color(hex: 0x15171B, opacity: 0), location: 0),
                    .init(color: Color(hex: 0x15171B, opacity: 0.7), location: 0.6),
                    .init(color: Self.background, location: 1),
                ], startPoint: .top, endPoint: .bottom)
                .frame(width: W, height: 56 + ins.bottom)
                .frame(width: W, height: viewport, alignment: .bottom)
                .allowsHitTesting(false)
            }
            .frame(width: W, height: viewport, alignment: .top)
            .clipped()
            .offset(y: navHeight)

            // The nav bar.
            HStack {
                Text(article.section)
                    .font(.system(size: 17, weight: .bold))
                    .tracking(-0.2)
                    .foregroundStyle(Self.ink)
                Spacer(minLength: 0)
                Text(minutes == 0 ? "Finished" : "\(minutes) min left")
                    .font(.system(size: 14, weight: .semibold).monospacedDigit())
                    .foregroundStyle(Self.amber)
            }
            .padding(.horizontal, Self.side)
            .padding(.top, ins.top)
            .frame(width: W, height: navHeight)
            .background(Self.background)
            .overlay(alignment: .bottomLeading) {
                Rectangle().fill(Color.rgba(241, 236, 226, 0.1))
                    .frame(width: W, height: 2)
                    .overlay(alignment: .leading) {
                        Rectangle().fill(Self.amber)
                            .scaleEffect(x: min(max(front / Double(max(page.wordCount, 1)), 0), 1), y: 1, anchor: .leading)
                    }
            }
            .accessibilityElement(children: .combine)

            // The ghost finger of the loop.
            Circle()
                .fill(Color.rgba(241, 236, 226, 0.2))
                .overlay(Circle().strokeBorder(Color.rgba(241, 236, 226, 0.55), lineWidth: 1))
                .frame(width: 44, height: 44)
                .opacity(f.auto && playing ? M.ghostOpacityAt(f.clock) : 0)
                .offset(x: W - 84, y: H * 0.66 - M.ghostTravelAt(f.clock) * 120)
                .allowsHitTesting(false)
                .accessibilityHidden(true)
        }
        .frame(width: W, height: H, alignment: .topLeading)
    }

    // MARK: - Drawing

    private static func drawArticle(_ ctx: inout GraphicsContext, _ page: ScrollReadHighlightPage, offset: Double, viewport: Double, front: Double, allInked: Bool) {
        ctx.translateBy(x: 0, y: -offset)
        if offset < page.headHeight {
            var head = ctx
            head.translateBy(x: page.x, y: page.titleY)
            head.fill(page.title.path, with: .color(ink))
            var by = ctx
            by.translateBy(x: page.x, y: page.bylineY)
            by.fill(page.byline.path, with: .color(soft))
            page.drawCover(&ctx)
        }
        for block in page.blocks {
            if block.y + block.height < offset - 4 || block.y > offset + viewport + 4 { continue }
            var c = ctx
            c.translateBy(x: page.x, y: block.y)
            if allInked || front >= Double(block.last) + M.fadeWords {
                c.fill(block.path, with: .color(ink))
            } else if front <= Double(block.first) {
                c.fill(block.path, with: .color(dim))
            } else {
                c.fill(block.path, with: .color(dim))
                c.drawLayer { layer in
                    layer.fill(block.path, with: .color(ink))
                    var mask = layer
                    mask.blendMode = .destinationIn
                    mask.drawLayer { m in
                        for word in block.first...block.last {
                            let alpha = M.wordInk(front, word)
                            if alpha <= 0 { break }
                            let r = CGRect(x: page.wordX[word] - page.x - 2, y: page.wordY[word] - block.y - 2, width: page.wordW[word] + 4, height: page.wordH[word] + 4)
                            m.fill(Path(r), with: .color(.white.opacity(alpha)))
                        }
                    }
                }
            }
        }
    }
}

/// The article measured once per width and kept across frames.
final class ScrollReadHighlightCache {
    private var key = ""
    private var built: ScrollReadHighlightPage?

    func page(_ a: ScrollReadHighlight.Article, x: Double, width: Double, bodySize: Double, bodyLeading: Double) -> ScrollReadHighlightPage {
        let k = "\(x)|\(width)|\(bodySize)|\(bodyLeading)|\(a.title)|\(a.byline)|\(a.paragraphs.joined(separator: "|"))"
        if let built, k == key { return built }
        let page = ScrollReadHighlightPage(title: a.title, byline: a.byline, paragraphs: a.paragraphs, x: x, width: width, bodySize: bodySize, bodyLeading: bodyLeading)
        key = k
        built = page
        return page
    }
}

/// The loop clock and who owns the scroll. A plain class read by the timeline: stepping it never invalidates
/// SwiftUI. In the loop and on the way back it sets the scroll view's offset; under a finger it reads it.
final class ScrollReadHighlightModel: NSObject, UIScrollViewDelegate {
    struct Frame {
        let offset: Double
        let clock: Double
        let auto: Bool
    }

    private enum Mode { case auto, manual, back }
    private typealias M = ScrollReadHighlightMotion

    weak var scroll: UIScrollView?
    var onActive: ((Bool) -> Void)?

    private var mode = Mode.auto
    private var last: Double?
    private var clock = 0.0
    private var offset = 0.0
    private var touching = false
    private var dragging = false
    private var idle = 0.0
    private var returnFrom = 0.0
    private var returnClock = 0.0

    func reset() {
        clock = 0
        mode = .auto
        offset = 0
        last = nil
        scroll?.setContentOffset(.zero, animated: false)
    }

    private func set(_ y: Double) {
        offset = y
        if let scroll, abs(scroll.contentOffset.y - y) > 0.001 { scroll.contentOffset = CGPoint(x: 0, y: y) }
    }

    func step(now: Double, playing: Bool, maxScroll: Double) -> Frame {
        let dt = last.map { min(max(now - $0, 0), 0.048) } ?? 0
        last = now
        if mode == .manual, let scroll {
            let y = Double(scroll.contentOffset.y)
            if y != offset { idle = 0 }
            offset = y
        }
        guard dt > 0, playing else { return Frame(offset: offset, clock: clock, auto: mode == .auto) }
        switch mode {
        case .auto:
            var t = clock + dt
            if t >= M.loop { t -= M.loop }
            clock = t
            set(M.scriptAt(t) * maxScroll)
        case .manual:
            if !(touching || dragging) {
                idle += dt
                if idle >= M.resumeAfter {
                    returnFrom = offset
                    returnClock = 0
                    mode = .back
                }
            }
        case .back:
            let k = min(returnClock + dt / M.returnSeconds, 1)
            returnClock = k
            set(returnFrom * (1 - M.easeInOutCubic(k)))
            if k >= 1 {
                clock = 0
                mode = .auto
            }
        }
        return Frame(offset: offset, clock: clock, auto: mode == .auto)
    }

    // A finger resting on the page stops the script too, before it becomes a drag.
    func touchBegan() {
        mode = .manual
        touching = true
        idle = 0
        if let scroll { offset = Double(scroll.contentOffset.y) }
        onActive?(true)
    }

    func touchEnded() {
        touching = false
        idle = 0
        if !dragging, let scroll, !scroll.isDecelerating { onActive?(false) }
    }

    func scrollViewWillBeginDragging(_ scrollView: UIScrollView) {
        mode = .manual
        dragging = true
        idle = 0
        onActive?(true)
    }

    func scrollViewDidEndDragging(_ scrollView: UIScrollView, willDecelerate decelerate: Bool) {
        dragging = false
        idle = 0
        if !decelerate { onActive?(false) }
    }

    func scrollViewDidEndDecelerating(_ scrollView: UIScrollView) {
        onActive?(false)
    }
}

/// A transparent scroll view over the canvas: real scrolling, momentum and bounce, nothing drawn.
struct ScrollReadHighlightScroller: UIViewRepresentable {
    let model: ScrollReadHighlightModel
    let contentHeight: Double
    let onActive: (Bool) -> Void

    final class Scroll: UIScrollView {
        weak var model: ScrollReadHighlightModel?
        override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
            model?.touchBegan()
            super.touchesBegan(touches, with: event)
        }
        override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
            model?.touchEnded()
            super.touchesEnded(touches, with: event)
        }
        override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
            model?.touchEnded()
            super.touchesCancelled(touches, with: event)
        }
    }

    func makeUIView(context: Context) -> Scroll {
        let view = Scroll()
        view.backgroundColor = .clear
        view.showsVerticalScrollIndicator = false
        view.showsHorizontalScrollIndicator = false
        view.contentInsetAdjustmentBehavior = .never
        view.alwaysBounceVertical = true
        view.delaysContentTouches = false
        view.model = model
        view.delegate = model
        model.scroll = view
        return view
    }

    func updateUIView(_ view: Scroll, context: Context) {
        model.onActive = onActive
        let size = CGSize(width: view.bounds.width, height: contentHeight)
        if view.contentSize != size { view.contentSize = size }
    }
}
