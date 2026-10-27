import SwiftUI

/// The tallest page of the scene, in points (`onMeasure` of `NewsPage`): the scene is as tall as its
/// tallest page, since the pages themselves are laid out on top of one another.
struct SpectrumNewsCarouselSceneHeightKey: PreferenceKey {
    static let defaultValue: CGFloat = 0

    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = max(value, nextValue())
    }
}

/// `NewsPage`: one page of the tour, its artwork over its words. All five sit at the top of the scene
/// and slide by their distance from the current page position, fading as they go; the copy hands over
/// faster than the art, so two headlines never meet mid-swipe.
struct SpectrumNewsCarouselPage: View {
    private typealias M = SpectrumNewsCarouselMotion

    let index: Int
    /// The window's width: pages are a whole width apart.
    let width: CGFloat
    let frame: SpectrumNewsCarouselFrame
    let reduced: Bool
    /// The artwork's scale to the screen.
    let scale: CGFloat
    let fontScale: Double

    var body: some View {
        let distance = Double(index) - frame.position
        // The welcome page fades in over the first half second of the timeline and out at its end.
        let skipsIntro = index != 0 || reduced || !frame.timelineDriven || frame.renderManual
        let intro = skipsIntro ? 1 : M.introOpacity(at: frame.t)
        let current = M.jsRound(frame.position) == Double(index)
        let opacity = reduced ? (current ? 1 : 0) : M.clamp(1.3 - abs(distance) * 0.3) * intro
        let shift = reduced ? 0 : CGFloat(distance) * width
        VStack(spacing: 0) {
            SpectrumNewsCarouselIllustration(page: index, t: frame.t, scale: scale)
            copy(distance: distance)
                // `copy: { marginTop: 35 }`, `copyWelcome: { marginTop: 10 }`.
                .padding(.top, index == 0 ? 10 : 35)
        }
        // `styles.page`: `paddingHorizontal: 24`, `width`.
        .padding(.horizontal, 24)
        .frame(width: width, alignment: .top)
        .background {
            GeometryReader { proxy in
                Color.clear.preference(key: SpectrumNewsCarouselSceneHeightKey.self, value: proxy.size.height)
            }
        }
        .opacity(opacity)
        .offset(x: shift)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    /// The headline and the words under it (the tagline alone on the welcome page).
    private func copy(distance: Double) -> some View {
        let page = SpectrumNewsCarouselCopy.pages[index]
        let headingStyle = SpectrumNewsCarouselTextStyle.heading
        let bodyStyle = SpectrumNewsCarouselTextStyle.body
        let taglineStyle = SpectrumNewsCarouselTextStyle.tagline
        let opacity = reduced ? 1 : M.clamp(1 - abs(distance) * 1.7)
        let shift = reduced ? 0 : CGFloat(distance) * -width * 0.18
        return VStack(spacing: 0) {
            if index == 0 {
                SpectrumNewsCarouselLabel(page.body, style: taglineStyle, scale: taglineStyle.scale(for: fontScale))
            } else {
                SpectrumNewsCarouselLabel(page.title, style: headingStyle, scale: headingStyle.scale(for: fontScale))
                SpectrumNewsCarouselLabel(page.body, style: bodyStyle, scale: bodyStyle.scale(for: fontScale))
                    .padding(.top, 12)
            }
        }
        .opacity(opacity)
        .offset(x: shift)
    }
}
