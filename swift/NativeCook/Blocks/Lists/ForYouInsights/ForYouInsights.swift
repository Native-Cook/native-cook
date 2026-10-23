import SwiftUI
import UIKit

private struct ForYouInsightsScrollKey: PreferenceKey {
    static var defaultValue = 0.0
    static func reduce(value: inout Double, nextValue: () -> Double) { value = nextValue() }
}

struct ForYouInsights: View {
    typealias T = ForYouInsightsTheme
    typealias L = ForYouInsightsTheme.Layout
    typealias M = ForYouInsightsMotion

    static let loop = ForYouInsightsMotion.loop

    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var controller = ForYouInsightsController()
    @State private var running = true
    @State private var section = ForYouInsightsSection.insights
    @State private var selectionTick = 0
    @State private var impactTick = 0

    private var now: TimeInterval { Date().timeIntervalSinceReferenceDate }

    var body: some View {
        GeometryReader { geo in
            let insets = ForYouInsightsSafeArea.insets
            scene(size: geo.size, safeTop: insets.top, bottomInset: insets.bottom)
        }
        .ignoresSafeArea()
        .background(T.Colors.page.ignoresSafeArea())
        .preferredColorScheme(.light)
        .sensoryFeedback(.selection, trigger: selectionTick)
        .sensoryFeedback(.impact(weight: .light), trigger: impactTick)
        .onAppear { configure() }
        .onChange(of: autoPlay) { _, _ in configure() }
        .onChange(of: reduceMotion) { _, _ in configure() }
    }

    private func configure() {
        let t = now
        controller.configure(autoPlay: autoPlay, quiet: reduceMotion, at: t)
        controller.setLens(section.rawValue, animated: false, at: t)
        wake()
    }

    private func wake() { running = controller.wantsFrames(at: now) }

    private func scene(size: CGSize, safeTop: Double, bottomInset: Double) -> some View {
        let k = size.width / T.refWidth
        return TimelineView(.animation(paused: !running)) { _ in
            let t = now
            let f = controller.tick(at: t)
            content(f, size: size, k: k, safeTop: safeTop, bottomInset: bottomInset)
                .onChange(of: controller.wantsFrames(at: t)) { _, wants in running = wants }
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
    }

    // MARK: content

    private func content(_ f: ForYouInsightsFrame, size: CGSize, k: Double, safeTop: Double, bottomInset: Double) -> some View {
        let y: (Double) -> Double = { safeTop + ($0 - T.refSafeTop) * k }
        let quiet = controller.quiet
        let g = f.g
        let heroX = L.heroInset * k
        let heroY = y(L.heroTop)
        let heroW = size.width - heroX * 2
        let heroH = L.heroHeight * k
        let actor = actorFrame(f, k: k, safeTop: safeTop, heroX: heroX, heroY: heroY, heroW: heroW, heroH: heroH)
        let backdrop = quiet ? M.clamp01(g) : M.smooth(M.seg(g, M.Reader.backdrop.0, M.Reader.backdrop.1))
        let closeP = M.seg(g, M.Reader.closeIn.0, M.Reader.closeIn.1)
        let tabP = quiet ? 0 : M.easeOut(M.seg(g, M.Reader.tabOut.0, M.Reader.tabOut.1))
        return ZStack(alignment: .topLeading) {
            T.Colors.page
            ForYouInsightsWarmLayers().equatable()
            feed(f, size: size, k: k, safeTop: safeTop, bottomInset: bottomInset, y: y)
                .scaleEffect(quiet ? 1 : M.lerp(1, M.Reader.feedScale, M.smooth(M.seg(g, 0.05, 0.8))))
            T.Colors.page.frame(width: size.width, height: safeTop).allowsHitTesting(false)
            T.Colors.page.opacity(backdrop).allowsHitTesting(false)
            ForYouInsightsActor(actor: actor, article: f.actorIndex, size: size)
            ZStack(alignment: .topLeading) {
                ForEach(0..<3, id: \.self) { i in
                    ForYouInsightsReader(article: i, k: k, width: size.width,
                                         top: heroY + heroH + L.readerTitleGap * k,
                                         height: size.height - heroY - heroH - L.readerTitleGap * k,
                                         bottomInset: bottomInset,
                                         g: g, dragY: f.dragY, actorIndex: f.actorIndex,
                                         quiet: quiet, live: f.readerLive && f.actorIndex == i)
                }
            }
            ForYouInsightsHeroPan(
                onChanged: { controller.dragChanged(translation: $0, scale: k); wake() },
                onEnded: { d, v in
                    if controller.dragEnded(translationY: d.y, velocityY: v, scale: k, at: now) {
                        impactTick += 1
                    }
                    wake()
                },
                onCancelled: { wake() })
                .frame(width: heroW, height: heroH)
                .offset(x: heroX, y: heroY)
                .allowsHitTesting(f.readerLive)
            closeButton(f, k: k, heroX: heroX, heroY: heroY, closeP: closeP, quiet: quiet)
            ForYouInsightsTabBar(k: k, section: section, lens: f.lens, onSelect: selectSection)
                .offset(x: (size.width - L.tabBarWidth * k) / 2, y: y(L.tabBarTop) + T.Layout.railTop * 0 + (quiet ? 0 : M.Reader.tabDrop * k * tabP))
                .opacity(quiet ? 1 - M.clamp01(g) : (g > M.Reader.tabHidden ? 0 : 1))
                .allowsHitTesting(!f.readerLive && g < 0.01)
            ForYouInsightsTouchProbe(
                onBegan: { controller.touchBegan(at: now); wake() },
                onEnded: { controller.touchEnded(at: now); wake() })
                .frame(width: 1, height: 1)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
    }

    private func closeButton(_ f: ForYouInsightsFrame, k: Double, heroX: Double, heroY: Double, closeP: Double, quiet: Bool) -> some View {
        let item = ForYouInsightsContent.articles[f.actorIndex]
        return Button {
            selectionTick += 1
            controller.close(at: now)
            wake()
        } label: {
            ZStack {
                Circle().fill(T.Colors.closeDisc)
                Circle().strokeBorder(T.Colors.tabEdge, lineWidth: 1.0 / 3.0)
                Image(systemName: "xmark")
                    .font(.system(size: 15 * k, weight: .bold))
                    .foregroundStyle(T.Colors.ink)
            }
            .frame(width: L.closeSize * k, height: L.closeSize * k)
            .shadow(color: T.Colors.shadow.opacity(0.12), radius: 6 * k, y: 2 * k)
            .frame(width: (L.closeSize + 8) * k, height: (L.closeSize + 8) * k)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Close \(item.title)")
        .scaleEffect(quiet ? 1 : M.lerp(M.Reader.closeScaleFrom, 1, M.easeOut(closeP)))
        .opacity(M.smooth(closeP))
        .offset(x: heroX + L.closeInset * k - 4 * k, y: heroY + L.closeInset * k - 4 * k + f.dragY)
        .allowsHitTesting(f.readerLive)
        .accessibilityHidden(!f.readerLive)
    }

    private func actorFrame(_ f: ForYouInsightsFrame, k: Double, safeTop: Double,
                            heroX: Double, heroY: Double, heroW: Double, heroH: Double) -> ForYouInsightsActorFrame {
        let q = controller.quiet
        let g = f.g
        let srcX = L.rowImageX * k
        let srcS = L.rowImageSize * k
        let srcY = safeTop + (controller.rowBase + L.rowPitch * Double(f.actorIndex) - T.refSafeTop) * k - controller.scrollY
        let m = q ? 1 : g
        let tilt = M.tiltOf(g, f.driftTime, f.drift)
        let tx = f.touchX / (M.Depth.touchTiltRange * k)
        let ty = f.touchY / (M.Depth.touchTiltRange * k)
        let d = f.dragY
        let press = 1 - (1 - M.pressScale) * f.press * (1 - M.smooth(M.seg(g, 0, 0.5)))
        let flight = 1 + M.Depth.liftScale * sin(Double.pi * M.clamp01(g))
        let pull = 1 - M.Dismiss.scaleLoss * M.clamp01(d / (M.Dismiss.distance * k * 2))
        var a = ForYouInsightsActorFrame()
        a.visible = q ? M.clamp01(g) : (g > 0.0005 ? 1 : 0)
        a.x = M.lerp(srcX, heroX, m)
        a.y = M.lerp(srcY, heroY, m) + d
        a.w = M.lerp(srcS, heroW, m)
        a.h = M.lerp(srcS, heroH, m)
        a.r = M.lerp(T.Radius.image, T.Radius.hero, M.clamp01(m)) * k
        a.scale = q ? 1 : press * flight * pull
        a.tiltX = q ? 0 : tilt.x - tanh(ty) * M.Depth.touchTiltX
        a.tiltY = q ? 0 : tilt.y + tanh(tx) * M.Depth.touchTiltY
        a.lift = q ? 0 : M.liftOf(g)
        a.sep = q ? 0 : M.separationOf(g)
        return a
    }

    // MARK: feed

    private func feed(_ f: ForYouInsightsFrame, size: CGSize, k: Double, safeTop: Double, bottomInset: Double,
                      y: @escaping (Double) -> Double) -> some View {
        let feedHeight = y(L.rowTop + L.rowPitch * 3) + (L.tabBarHeight + T.Space.screen) * k + bottomInset
        let W = size.width
        return ScrollViewReader { proxy in
            ScrollView(.vertical, showsIndicators: false) {
                ZStack(alignment: .topLeading) {
                    Color.clear.frame(width: 1, height: 1).id("top")
                    layer(.insights) { insightsLayer(f, k: k, W: W, y: y) }
                    layer(.longevity) {
                        ZStack(alignment: .topLeading) {
                            ForYouInsightsHeading(k: k, y: y, title: "Longevity").equatable()
                            ForYouInsightsText(text: "Small habits that support a steadier day", style: T.Typo.sectionSubhead, color: T.Colors.secondary, k: k)
                                .offset(x: L.column * k, y: y(L.labelTop))
                            ForYouInsightsHabits(k: k, y: y).equatable()
                        }
                    }
                    layer(.browse) {
                        ZStack(alignment: .topLeading) {
                            ForYouInsightsHeading(k: k, y: y, title: "Browse").equatable()
                            ForYouInsightsText(text: "Guides for everyday routines", style: T.Typo.sectionSubhead, color: T.Colors.secondary, k: k)
                                .offset(x: L.column * k, y: y(L.labelTop))
                            rows(f, base: L.browseRowTop, k: k, y: y)
                        }
                    }
                }
                .frame(width: W, height: feedHeight, alignment: .topLeading)
                .background {
                    GeometryReader { p in
                        Color.clear.preference(key: ForYouInsightsScrollKey.self, value: -p.frame(in: .named("ForYouInsightsFeed")).minY)
                    }
                }
            }
            .coordinateSpace(name: "ForYouInsightsFeed")
            .onPreferenceChange(ForYouInsightsScrollKey.self) { controller.scrollY = $0 }
            .scrollDisabled(f.readerLive)
            .accessibilityHidden(f.readerLive)
            .onChange(of: f.scrollToken) { _, _ in withAnimation(.easeOut(duration: 0.35)) { proxy.scrollTo("top", anchor: .top) } }
            .onChange(of: section) { _, _ in proxy.scrollTo("top", anchor: .top) }
        }
    }

    private func layer<Content: View>(_ target: ForYouInsightsSection, @ViewBuilder _ content: () -> Content) -> some View {
        content()
            .opacity(section == target ? 1 : 0)
            .allowsHitTesting(section == target)
            .accessibilityHidden(section != target)
    }

    private func insightsLayer(_ f: ForYouInsightsFrame, k: Double, W: Double, y: @escaping (Double) -> Double) -> some View {
        ZStack(alignment: .topLeading) {
            ForYouInsightsRail(k: k).equatable()
                .frame(width: W)
                .offset(y: y(L.railTop))
            T.Colors.separator.frame(width: W, height: L.divider).offset(y: y(L.railDivider))
            ForYouInsightsHeading(k: k, y: y, title: "For You").equatable()
            HStack(spacing: L.labelIconGap * k) {
                Image(systemName: "person.fill").font(.system(size: L.labelIcon * k)).foregroundStyle(T.Colors.pink)
                ForYouInsightsText(text: ForYouInsightsContent.label, style: T.Typo.label, color: T.Colors.pink, k: k)
            }
            .frame(height: L.labelHeight * k)
            .offset(x: (L.column + L.labelInset) * k, y: y(L.labelTop))
            Button {
                selectionTick += 1
                controller.regenerate(at: now)
                wake()
            } label: {
                ZStack {
                    RoundedRectangle(cornerRadius: T.Radius.ellipsis * k, style: .circular)
                        .fill(T.Colors.ellipsisFill)
                        .frame(width: L.ellipsisSize * k, height: L.ellipsisSize * k)
                    Image(systemName: "ellipsis").font(.system(size: L.ellipsisGlyph * k * 0.85, weight: .semibold)).foregroundStyle(T.Colors.ink)
                }
                .frame(width: 44 * k, height: 44 * k)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Write this insight again")
            .offset(x: (L.ellipsisCenter.x - 22) * k, y: y(L.ellipsisCenter.y) - 22 * k)
            ForYouInsightsTip(k: k, frame: f.tip)
                .offset(x: L.column * k, y: y(L.tipTop))
            T.Colors.separator.frame(width: L.columnWidth * k, height: L.divider)
                .offset(x: L.column * k, y: y(L.tipDivider))
            rows(f, base: L.rowTop, k: k, y: y)
        }
    }

    private func rows(_ f: ForYouInsightsFrame, base: Double, k: Double, y: @escaping (Double) -> Double) -> some View {
        ZStack(alignment: .topLeading) {
            ForEach(0..<3, id: \.self) { i in
                ForYouInsightsRow(
                    index: i, top: base + L.rowPitch * Double(i), k: k, y: y, frame: f,
                    onPress: { down in controller.pressRow(i, down: down, at: now); wake() },
                    onOpen: {
                        if controller.readerLive { return }
                        if controller.open(i, base: base, at: now) { selectionTick += 1 }
                        wake()
                    })
            }
        }
    }

    private func selectSection(_ next: ForYouInsightsSection) {
        if next == section || controller.readerLive { return }
        selectionTick += 1
        section = next
        let t = now
        controller.selectSection(insights: next == .insights, at: t)
        controller.setLens(next.rawValue, animated: true, at: t)
        wake()
    }
}

/// The window's safe-area insets. The block lays out edge to edge (`.ignoresSafeArea()`), so a
/// GeometryReader inside it reports zero insets; read them from the key window instead.
enum ForYouInsightsSafeArea {
    @MainActor static var insets: EdgeInsets {
        let window = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first { $0.isKeyWindow }
        let i = window?.safeAreaInsets ?? .zero
        return EdgeInsets(top: i.top, leading: i.left, bottom: i.bottom, trailing: i.right)
    }
}
