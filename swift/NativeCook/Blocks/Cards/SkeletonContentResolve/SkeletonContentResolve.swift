import SwiftUI
import UIKit

/// A podcast home whose loading state turns into its content instead of being swapped for it.
/// Pull down to refresh: every line becomes a bar with one band of light crossing all of them; then,
/// top to bottom, each bar stretches to the exact width of the text it stood for, the text fades in
/// inside it and the bar dissolves, while the covers open through a soft radial wipe.
struct SkeletonContentResolve: View {
    static let loop: Double = SkeletonContentResolveMotion.loop

    private typealias M = SkeletonContentResolveMotion
    private typealias D = SkeletonContentResolveData

    private static let page = D.color(0x15120f)
    private static let card = D.color(0x211c17)
    private static let raised = D.color(0x2a231c)
    private static let ink = D.color(0xf5ede1)
    private static let mute = D.color(0xa3978a)
    private static let faint = D.color(0x6f665c)
    private static let ember = D.color(0xf0803c)
    private static let hair = D.color(0xf5e8d6, 0.09)
    private static let side: CGFloat = 16
    private static let dot: CGFloat = 46
    private static let spinner: CGFloat = 28

    private let autoPlay: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var clock = SkeletonContentResolveClock()
    @State private var live = false
    @State private var liveToken = 0
    @State private var dragPhase = DragPhase.idle

    private enum DragPhase { case idle, waiting, pulling, failed }

    init(autoPlay: Bool = true) {
        self.autoPlay = autoPlay
    }

    private struct Layout {
        let screen: CGSize
        let contentW: CGFloat
        let w: CGFloat
        let top: CGFloat
        let headerH: CGFloat
        let gap: CGFloat
        let tabH: CGFloat
        let bottomInset: CGFloat
        let miniH: CGFloat
        let miniBottom: CGFloat
        let listTop: CGFloat
        let listH: CGFloat
        let featuredH: CGFloat
        let sectionGap: CGFloat
        let sectionH: CGFloat
        let rowH: CGFloat
        let coverSize: CGFloat
        let bigCover: CGFloat
        let ghostY: CGFloat
        let titleSize: CGFloat
        let featuredTitle: CGFloat

        init(size: CGSize, insets: EdgeInsets) {
            let screenH = size.height + insets.top + insets.bottom
            screen = CGSize(width: size.width + insets.leading + insets.trailing, height: screenH)
            let compact = screenH < 740
            contentW = min(screen.width, 480)
            w = contentW - SkeletonContentResolve.side * 2
            top = insets.top + (compact ? 2 : 6)
            headerH = compact ? 52 : 60
            gap = compact ? 10 : 14
            bottomInset = max(insets.bottom, 6)
            tabH = 49 + bottomInset
            miniH = compact ? 54 : 60
            miniBottom = tabH + 8
            listTop = top + headerH + gap
            listH = screenH - listTop - miniBottom - miniH - (compact ? 6 : 10)
            featuredH = compact ? 148 : 160
            sectionGap = compact ? 12 : 18
            sectionH = compact ? 26 : 28
            rowH = min(84, max(56, floor((listH - 4 - featuredH - sectionGap - sectionH - 4) / 4)))
            coverSize = min(60, rowH - 18)
            bigCover = featuredH - 28
            ghostY = listTop + featuredH * 0.55
            titleSize = compact ? 28 : 32
            featuredTitle = compact ? 18 : 19
        }
    }

    private var film: Bool { autoPlay && !reduceMotion }

    var body: some View {
        GeometryReader { proxy in
            let layout = Layout(size: proxy.size, insets: proxy.safeAreaInsets)
            ZStack(alignment: .topLeading) {
                Self.page
                TimelineView(.animation(minimumInterval: nil, paused: !film && !live)) { context in
                    let frame = advance(context.date)
                    screen(frame, layout)
                }
                touchLayer(layout)
            }
            .frame(width: layout.screen.width, height: layout.screen.height, alignment: .topLeading)
            .ignoresSafeArea()
        }
        .preferredColorScheme(.dark)
        .task(id: liveToken) {
            guard liveToken > 0 else { return }
            while !Task.isCancelled {
                let now = Date().timeIntervalSinceReferenceDate
                if !clock.holding && now - clock.lastTouch > 4.5 { break }
                try? await Task.sleep(for: .milliseconds(250))
            }
            if !Task.isCancelled { live = false }
        }
    }

    private func advance(_ date: Date) -> SkeletonContentResolveClock.Frame {
        clock.film = film
        return clock.frame(date.timeIntervalSinceReferenceDate)
    }

    private func wake() {
        live = true
        liveToken += 1
    }

    // MARK: Touch

    private func touchLayer(_ layout: Layout) -> some View {
        Color.clear
            .frame(width: layout.contentW, height: max(0, layout.listH))
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in dragChanged(value) }
                    .onEnded { _ in dragEnded() }
            )
            .accessibilityElement()
            .accessibilityLabel("Episodes")
            .accessibilityAction(named: "Refresh") {
                clock.refresh(Date().timeIntervalSinceReferenceDate)
                wake()
            }
            .offset(x: (layout.screen.width - layout.contentW) / 2, y: layout.listTop)
    }

    private func dragChanged(_ value: DragGesture.Value) {
        let now = Date().timeIntervalSinceReferenceDate
        if dragPhase == .idle {
            dragPhase = .waiting
            clock.take(now)
            wake()
        }
        let dx = value.translation.width
        let dy = value.translation.height
        if dragPhase == .waiting {
            if abs(dx) > 24 {
                dragPhase = .failed
            } else if dy >= 8 {
                dragPhase = .pulling
                clock.startPull()
            }
        }
        if dragPhase == .pulling, clock.pull(dy: dy, now) {
            UISelectionFeedbackGenerator().selectionChanged()
        }
    }

    private func dragEnded() {
        clock.endPull(Date().timeIntervalSinceReferenceDate)
        dragPhase = .idle
        wake()
    }

    // MARK: Drawing

    private func screen(_ frame: SkeletonContentResolveClock.Frame, _ layout: Layout) -> some View {
        let group = SkeletonContentResolveGroup(sinceHide: frame.sinceHide, sinceShow: frame.sinceShow,
                                                span: layout.contentW + M.slant * layout.listH)
        return ZStack(alignment: .topLeading) {
            VStack(spacing: 0) {
                header(layout)
                    .padding(.top, layout.top)
                ZStack(alignment: .top) {
                    spinner(frame)
                        .padding(.top, 12)
                    list(frame.data, group, layout)
                        .offset(y: frame.offset)
                }
                .frame(width: layout.contentW, height: max(0, layout.listH), alignment: .top)
                .clipped()
                .padding(.top, layout.gap)
                Spacer(minLength: 0)
            }
            .frame(width: layout.contentW, height: layout.screen.height, alignment: .top)

            miniPlayer(layout)
                .offset(x: Self.side, y: layout.screen.height - layout.miniBottom - layout.miniH)
            tabBar(layout)
                .offset(y: layout.screen.height - layout.tabH)

            Circle()
                .fill(.white.opacity(0.22))
                .overlay(Circle().strokeBorder(.white.opacity(0.6), lineWidth: 1.5))
                .frame(width: Self.dot, height: Self.dot)
                .offset(x: layout.screen.width / 2 - Self.dot / 2, y: layout.ghostY + frame.fingerDy - Self.dot / 2)
                .opacity(frame.ghost * 0.9)
                .allowsHitTesting(false)
        }
        .offset(x: (layout.screen.width - layout.contentW) / 2)
        .frame(width: layout.screen.width, height: layout.screen.height, alignment: .topLeading)
    }

    private func header(_ layout: Layout) -> some View {
        HStack(alignment: .bottom, spacing: 0) {
            VStack(alignment: .leading, spacing: 0) {
                Text("Tuesday evening")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(Self.mute)
                    .frame(height: 18)
                Text("Listen now")
                    .font(.system(size: layout.titleSize, weight: .bold))
                    .tracking(-0.5)
                    .foregroundStyle(Self.ink)
                    .frame(height: layout.titleSize + 5)
            }
            Spacer(minLength: 0)
            Text("MR")
                .font(.system(size: 13, weight: .bold))
                .tracking(0.2)
                .foregroundStyle(D.color(0xfff6e8))
                .frame(width: 36, height: 36)
                .background(
                    LinearGradient(colors: [D.color(0xf3a25c), D.color(0xd9653a), D.color(0x8a3a3f)],
                                   startPoint: UnitPoint(x: 0.1, y: 0), endPoint: UnitPoint(x: 0.9, y: 1))
                )
                .clipShape(Circle())
                .padding(.bottom, 3)
        }
        .padding(.horizontal, Self.side)
        .frame(height: layout.headerH, alignment: .bottom)
    }

    private func spinner(_ frame: SkeletonContentResolveClock.Frame) -> some View {
        let spinning = frame.sinceHide >= 0 && frame.sinceShow < M.spinFade
        let alpha: Double
        if spinning {
            alpha = frame.sinceShow < 0 ? 1 : 1 - M.seg(frame.sinceShow, 0, M.spinFade)
        } else {
            alpha = frame.reveal > 0 ? 1 : 0
        }
        return Canvas { context, size in
            let c = CGPoint(x: size.width / 2, y: size.height / 2)
            for i in 0..<M.ticks {
                let a = spinning ? M.tickSpinning(i, frame.sinceHide) : M.tickArming(i, frame.reveal)
                guard a > 0 else { continue }
                var tick = context
                tick.opacity = a
                tick.translateBy(x: c.x, y: c.y)
                tick.rotate(by: .degrees(Double(i) * 360 / Double(M.ticks)))
                let rect = CGRect(x: -1.25, y: -8.5 - 3.5, width: 2.5, height: 7)
                tick.fill(Path(roundedRect: rect, cornerRadius: 1.25), with: .color(Self.ink))
            }
        }
        .frame(width: Self.spinner, height: Self.spinner)
        .opacity(alpha)
        .scaleEffect(0.8 + 0.2 * alpha)
    }

    private func list(_ data: Int, _ group: SkeletonContentResolveGroup, _ layout: Layout) -> some View {
        let feed = D.feeds[data]
        return VStack(spacing: 0) {
            featured(feed, group, layout)
            HStack {
                Text("Up next")
                    .font(.system(size: 20, weight: .bold))
                    .tracking(-0.3)
                    .foregroundStyle(Self.ink)
                Spacer(minLength: 0)
                Text("See all")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(Self.ember)
            }
            .frame(height: layout.sectionH)
            .padding(.top, layout.sectionGap)
            VStack(spacing: 0) {
                ForEach(0..<feed.episodes.count, id: \.self) { r in
                    row(feed.episodes[r], index: r, last: r == feed.episodes.count - 1, group, layout)
                }
            }
            .padding(.top, 4)
        }
        .padding(.horizontal, Self.side)
        .padding(.top, 4)
        .coordinateSpace(.named(SkeletonContentResolveGroup.space))
        .background(Self.page)
    }

    private func featured(_ feed: D.Feed, _ group: SkeletonContentResolveGroup, _ layout: Layout) -> some View {
        let shape = RoundedRectangle(cornerRadius: 24, style: .continuous)
        let titleLine = layout.featuredTitle + 4
        return HStack(alignment: .top, spacing: 14) {
            SkeletonContentResolveCover(group: group, order: 0, size: layout.bigCover, radius: 16, spec: feed.cover)
            VStack(alignment: .leading, spacing: 0) {
                VStack(alignment: .leading, spacing: 3) {
                    SkeletonContentResolveLine(group: group, order: 0.5, placeholder: D.eyebrowBar, barHeight: 10) {
                        Text(feed.eyebrow)
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(Self.ember)
                            .lineLimit(1)
                            .frame(height: 16)
                    }
                    SkeletonContentResolveLine(group: group, order: 1, placeholder: D.titleABar, barHeight: 15) {
                        featuredTitle(feed.title[0], layout, titleLine)
                    }
                    SkeletonContentResolveLine(group: group, order: 1.5, placeholder: D.titleBBar, barHeight: 15) {
                        featuredTitle(feed.title[1], layout, titleLine)
                    }
                    SkeletonContentResolveLine(group: group, order: 2.2, placeholder: D.metaBar, barHeight: 10) {
                        Text("\(feed.show) · \(feed.minutes) min")
                            .font(.system(size: 13).monospacedDigit())
                            .foregroundStyle(Self.mute)
                            .lineLimit(1)
                            .frame(height: 17)
                    }
                }
                Spacer(minLength: 0)
                SkeletonContentResolveLine(group: group, order: 3, placeholder: D.playBar, barHeight: 30, radius: 15) {
                    HStack(spacing: 6) {
                        Image(systemName: "play.fill")
                            .font(.system(size: 11))
                        Text("Play")
                            .font(.system(size: 14, weight: .semibold))
                    }
                    .foregroundStyle(Self.page)
                    .padding(.horizontal, 14)
                    .frame(height: 30)
                    .background(Capsule().fill(Self.ink))
                }
            }
            .frame(maxWidth: .infinity, minHeight: layout.bigCover, maxHeight: layout.bigCover, alignment: .topLeading)
        }
        .padding(14)
        .frame(height: layout.featuredH)
        .background {
            shape
                .fill(Self.card.shadow(.inner(color: D.color(0xfff0dc, 0.07), radius: 0, x: 0, y: 1)))
                .shadow(color: .black.opacity(0.4), radius: 1, x: 0, y: 1)
                .shadow(color: .black.opacity(0.3), radius: 14, x: 0, y: 14)
        }
    }

    private func featuredTitle(_ text: String, _ layout: Layout, _ line: CGFloat) -> some View {
        Text(text)
            .font(.system(size: layout.featuredTitle, weight: .bold))
            .tracking(-0.3)
            .foregroundStyle(Self.ink)
            .lineLimit(1)
            .frame(height: line)
    }

    private func row(_ episode: D.Episode, index: Int, last: Bool, _ group: SkeletonContentResolveGroup, _ layout: Layout) -> some View {
        let order = 4 + Double(index) * 2
        return HStack(spacing: 0) {
            SkeletonContentResolveCover(group: group, order: order, size: layout.coverSize, radius: 12, spec: episode.cover)
            VStack(alignment: .leading, spacing: 3) {
                SkeletonContentResolveLine(group: group, order: order + 0.5, placeholder: D.rowTitleBars[index], barHeight: 12) {
                    Text(episode.title)
                        .font(.system(size: 16, weight: .semibold))
                        .tracking(-0.2)
                        .foregroundStyle(Self.ink)
                        .lineLimit(1)
                        .frame(height: 21)
                }
                SkeletonContentResolveLine(group: group, order: order + 1, placeholder: D.rowMetaBars[index], barHeight: 9) {
                    Text("\(episode.show) · \(episode.minutes) min")
                        .font(.system(size: 13).monospacedDigit())
                        .foregroundStyle(Self.mute)
                        .lineLimit(1)
                        .frame(height: 17)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.leading, 12)
            .padding(.trailing, 10)
            SkeletonContentResolveLine(group: group, order: order + 0.5, placeholder: D.dayBar, barHeight: 9, trailing: true) {
                Text(episode.day)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(Self.faint)
                    .frame(height: 17)
            }
        }
        .frame(height: layout.rowH)
        .overlay(alignment: .bottom) {
            if !last {
                Rectangle()
                    .fill(Self.hair)
                    .frame(height: 1 / 3)
                    .padding(.leading, layout.coverSize + 12)
            }
        }
    }

    private func miniPlayer(_ layout: Layout) -> some View {
        let shape = RoundedRectangle(cornerRadius: 18, style: .continuous)
        let art = layout.miniH - 16
        return HStack(spacing: 0) {
            SkeletonContentResolveCoverArt(spec: D.nowPlaying, size: art)
                .equatable()
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            VStack(alignment: .leading, spacing: 1) {
                Text("Night Swim Radio")
                    .font(.system(size: 15, weight: .semibold))
                    .tracking(-0.2)
                    .foregroundStyle(Self.ink)
                    .lineLimit(1)
                Text("Low Tide Hours · 12:08 left")
                    .font(.system(size: 12.5).monospacedDigit())
                    .foregroundStyle(Self.mute)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 10)
            Image(systemName: "pause.fill")
                .font(.system(size: 20))
                .foregroundStyle(Self.ink)
                .frame(width: 24)
        }
        .padding(.horizontal, 8)
        .frame(width: layout.w, height: layout.miniH)
        .background(alignment: .bottomLeading) {
            ZStack(alignment: .bottomLeading) {
                Self.raised
                Rectangle().fill(D.color(0xf5e8d6, 0.08)).frame(height: 2)
                Rectangle().fill(Self.ember).frame(width: layout.w * 0.38, height: 2)
            }
        }
        .clipShape(shape)
        .background {
            shape
                .fill(Self.raised.shadow(.inner(color: D.color(0xfff0dc, 0.08), radius: 0, x: 0, y: 1)))
                .shadow(color: .black.opacity(0.4), radius: 2, x: 0, y: 2)
                .shadow(color: .black.opacity(0.38), radius: 15, x: 0, y: 16)
        }
    }

    private func tabBar(_ layout: Layout) -> some View {
        let tabs: [(String, String)] = [
            ("house.fill", "Home"),
            ("magnifyingglass", "Search"),
            ("square.stack.fill", "Library"),
            ("arrow.down.circle", "Downloads"),
        ]
        return HStack(alignment: .top, spacing: 0) {
            ForEach(0..<tabs.count, id: \.self) { i in
                VStack(spacing: 3) {
                    Image(systemName: tabs[i].0)
                        .font(.system(size: 19))
                        .frame(height: 22)
                    Text(tabs[i].1)
                        .font(.system(size: 10, weight: .medium))
                }
                .foregroundStyle(i == 0 ? Self.ember : Self.faint)
                .frame(maxWidth: .infinity)
            }
        }
        .padding(.top, 7)
        .frame(width: layout.contentW, height: layout.tabH, alignment: .top)
        .background(D.color(0x191511))
        .overlay(alignment: .top) {
            Rectangle().fill(Self.hair).frame(height: 1 / 3)
        }
    }
}
