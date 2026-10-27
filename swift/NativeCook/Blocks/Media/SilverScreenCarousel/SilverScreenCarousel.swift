import SwiftUI
import UIKit

// Silver Screen Carousel: an editorial black welcome for an invented film-profile camera app. A row
// of four tall cards, each an original photograph with a two-letter profile code, its name and
// description and a colour bar, drifts across the screen; the arrow at the foot applies the profile
// in the gate.
//
// Timeline of one 29 s loop (autoPlay), t in seconds:
//   0.00-27.98  the row drifts left at a constant 25.17 source pixels a second (238 px stride, 9.457 s
//               a card), no hold and no easing; each photograph lags 22 pt behind its frame and the
//               frames at the edges are a step darker.
//   27.98-28.35 the row fades out (smoothstep).
//   28.35-28.70 it is back at the first card and fades in.
//   28.70-29.00 at rest again, identical to t = 0.
//
// Touch: a horizontal drag moves the row 1:1 (rubber-banding past the ends) and settles on a spring
// (400 ms, damping ratio 0.8) that carries the finger's speed, with a selection haptic; 2 s after the
// last touch the row springs home and the drift starts again. The arrow applies the profile in the
// gate: the shutter flashes once (420 ms) with a success haptic and the arrow becomes a check.
// Under Reduce Motion the row does not drift and changes in place.

struct SilverScreenCarousel: View {
    static let loop: Double = 29

    var autoPlay = false
    var previewTime: Double?
    var onSelect: ((String) -> Void)?
    var onApply: ((String) -> Void)?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var model: SilverScreenCarouselModel

    init(
        autoPlay: Bool = false,
        previewTime: Double? = nil,
        onSelect: ((String) -> Void)? = nil,
        onApply: ((String) -> Void)? = nil
    ) {
        self.autoPlay = autoPlay
        self.previewTime = previewTime
        self.onSelect = onSelect
        self.onApply = onApply
        _model = State(initialValue: SilverScreenCarouselModel(autoPlay: autoPlay, previewTime: previewTime))
        SilverScreenCarouselAssets.prepare()
    }

    var body: some View {
        let _ = bindCallbacks()
        GeometryReader { geo in
            // The page is laid out on the whole window, as React Native's `useWindowDimensions` sees
            // it, and drawn under the status bar and the home indicator.
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            SilverScreenCarouselScreen(
                model: model,
                size: size,
                safeTop: insets.top,
                safeBottom: insets.bottom,
                reduced: reduceMotion
            )
            .frame(width: size.width, height: size.height)
            .ignoresSafeArea()
        }
        .background(SilverScreenCarouselPalette.screen.ignoresSafeArea())
        // `<StatusBar style="light" />`: light status bar content on the black page.
        .preferredColorScheme(.dark)
        .onAppear {
            model.configure(reduced: reduceMotion, foreground: scenePhase == .active)
        }
        .onDisappear {
            model.stop()
        }
        .onChange(of: reduceMotion) { _, value in
            model.setReduced(value)
        }
        .onChange(of: scenePhase) { _, value in
            model.setForeground(value == .active)
        }
    }

    /// Hands the model the callbacks of the latest render (they are not state).
    private func bindCallbacks() {
        model.onSelect = onSelect
        model.onApply = onApply
    }
}

// MARK: - Screen

/// The page: a scroll view holding the heading, the row of cards and the footer, with a progressive
/// blur at its top and bottom edges when the copy is taller than the screen.
struct SilverScreenCarouselScreen: View {
    let model: SilverScreenCarouselModel
    /// The window's size in points.
    let size: CGSize
    let safeTop: CGFloat
    let safeBottom: CGFloat
    let reduced: Bool

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    @State private var contentHeight: CGFloat = 0

    var body: some View {
        let fontScale = SilverScreenCarouselLayout.fontScale(for: dynamicTypeSize)
        let layout = SilverScreenCarouselLayout(size: size, safeTop: safeTop, fontScale: fontScale)
        // The progressive blur at the edges only shows when the page is taller than the screen.
        let overflows = contentHeight > size.height + 4
        ScrollView {
            page(layout: layout)
        }
        .scrollIndicators(.hidden)
        // `alwaysBounceVertical` is true for a vertical React Native scroll view.
        .scrollBounceBehavior(.always, axes: .vertical)
        .onPreferenceChange(SilverScreenCarouselHeightKey.self) { contentHeight = $0 }
        .overlay(alignment: .top) {
            if overflows {
                SilverScreenCarouselEdgeBlur(edge: .top, reduceTransparency: reduceTransparency, viewportHeight: size.height)
            }
        }
        .overlay(alignment: .bottom) {
            if overflows {
                SilverScreenCarouselEdgeBlur(edge: .bottom, reduceTransparency: reduceTransparency, viewportHeight: size.height)
            }
        }
        .frame(width: size.width, height: size.height)
        .background(SilverScreenCarouselPalette.screen)
        .clipped()
    }

    // MARK: Page

    /// The scroll view's content: `contentContainerStyle` is `minHeight: height`, `paddingTop:
    /// inset.top + 16`, `paddingBottom: inset.bottom + 16`.
    private func page(layout: SilverScreenCarouselLayout) -> some View {
        VStack(spacing: 0) {
            heading(layout: layout)
            carousel(layout: layout)
            SilverScreenCarouselFooter(model: model, layout: layout, reduced: reduced)
        }
        .padding(.top, safeTop + 16)
        .padding(.bottom, safeBottom + 16)
        .frame(width: size.width, alignment: .top)
        .frame(minHeight: size.height, alignment: .top)
        .background(
            GeometryReader { proxy in
                Color.clear.preference(key: SilverScreenCarouselHeightKey.self, value: proxy.size.height)
            }
        )
        .background(scrollProbe)
    }

    /// `styles.heading`: the title and subtitle, centred.
    private func heading(layout: SilverScreenCarouselLayout) -> some View {
        VStack(spacing: 0) {
            SilverScreenCarouselLabel(
                text: "Aesthetic shots\nwithout editing", style: .title, fontScale: layout.fontScale, alignment: .center)
                .accessibilityAddTraits(.isHeader)
            SilverScreenCarouselLabel(
                text: "Not another filter, but soft\nfilm-inspired profiles", style: .subtitle,
                fontScale: layout.fontScale, alignment: .center)
                .padding(.top, 12)
        }
        .padding(.horizontal, 24)
        .padding(.top, layout.headingMargin)
        .padding(.bottom, layout.cardMargin)
        .frame(maxWidth: .infinity)
    }

    // MARK: Carousel

    /// `styles.carousel`: the row of cards, clipped to the screen's width, with the pan on top.
    private func carousel(layout: SilverScreenCarouselLayout) -> some View {
        let profiles = SilverScreenCarouselProfiles.all
        let selectedName = profiles[model.selected].name
        return TimelineView(.animation(paused: !model.isMoving)) { timeline in
            track(at: timeline.date, layout: layout)
        }
        .frame(width: size.width, alignment: .leading)
        .clipped()
        .overlay(touchLayer(layout: layout))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Film profile: \(selectedName)")
        .accessibilityValue("\(model.selected + 1) of \(profiles.count)")
        .accessibilityAdjustableAction { direction in
            switch direction {
            case .increment:
                model.stepProfile(by: 1)
            case .decrement:
                model.stepProfile(by: -1)
            @unknown default:
                break
            }
        }
    }

    /// `styles.track`: the cards side by side, `paddingLeft` centring the first, moved by
    /// `-position * step`; every value is a function of the timeline's date.
    private func track(at date: Date, layout: SilverScreenCarouselLayout) -> some View {
        let position = model.position(at: date)
        let flash = model.flash(at: date)
        let gateIndex = SilverScreenCarouselMotion.index(for: position)
        let leading = (size.width - layout.cardWidth) / 2
        return HStack(alignment: .top, spacing: layout.gap) {
            ForEach(SilverScreenCarouselProfiles.all) { profile in
                SilverScreenCarouselCard(
                    profile: profile,
                    width: layout.cardWidth,
                    minHeight: layout.cardHeight,
                    position: position,
                    active: model.selected == profile.index,
                    flash: flash,
                    reduced: reduced,
                    fontScale: layout.fontScale
                )
            }
        }
        .fixedSize(horizontal: false, vertical: true)
        .padding(.leading, leading)
        .offset(x: -CGFloat(position) * layout.step)
        .opacity(model.trackOpacity(at: date))
        .onChange(of: gateIndex) { _, next in
            model.syncSelected(next)
        }
    }

    @ViewBuilder
    private func touchLayer(layout: SilverScreenCarouselLayout) -> some View {
        if model.interactive {
            let step = Double(layout.step)
            SilverScreenCarouselTouchLayer(
                onStart: {
                    model.panStarted()
                },
                onMove: { translation in
                    model.panMoved(translationX: Double(translation), step: step)
                },
                onEnd: { velocity, success in
                    model.panEnded(velocityX: Double(velocity), step: step, success: success)
                },
                onFinish: {
                    model.panFinished()
                }
            )
        }
    }

    /// The page's scroll events (`onScrollBeginDrag` and the others), unless the screen is a still frame.
    @ViewBuilder
    private var scrollProbe: some View {
        if model.interactive {
            SilverScreenCarouselScrollProbe(
                onBeginDrag: { model.scrollBegan() },
                onEndDrag: { model.scrollEnded() },
                onMomentumBegin: { model.momentumBegan() },
                onMomentumEnd: { model.scrollEnded() }
            )
        }
    }
}

/// The height of the scroll content, to know whether the page overflows the screen.
private struct SilverScreenCarouselHeightKey: PreferenceKey {
    static let defaultValue: CGFloat = 0

    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = nextValue()
    }
}
