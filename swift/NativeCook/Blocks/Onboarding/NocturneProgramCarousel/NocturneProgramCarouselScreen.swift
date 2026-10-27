import SwiftUI
import UIKit

private typealias M = NocturneProgramCarouselMotion
private typealias P = NocturneProgramCarouselPalette

/// The screen: the night gradient, then either the tour (welcome, program and the app's tab bar) or
/// the evening plan. The tour is drawn from the loop time of one `TimelineView`, which is paused
/// whenever nothing moves.
struct NocturneProgramCarouselScreen: View {
    let engine: NocturneProgramCarouselEngine
    /// The window's size in points (`useWindowDimensions`).
    let size: CGSize
    let safeTop: CGFloat
    let safeBottom: CGFloat
    let reduced: Bool

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    /// Where the card row is, so a pan only begins on it.
    @State private var region = NocturneProgramCarouselRegion()

    var body: some View {
        let metrics = NocturneProgramCarouselMetrics(
            size: size,
            safeTop: safeTop,
            safeBottom: safeBottom,
            fontScale: nocturneProgramCarouselFontScale(dynamicTypeSize)
        )
        ZStack(alignment: .topLeading) {
            // `colors={['#0A1A3A', '#03101F', '#010A16']} locations={[0, .3, 1]}`.
            LinearGradient(
                stops: [
                    Gradient.Stop(color: P.groundTop, location: 0),
                    Gradient.Stop(color: P.groundMid, location: 0.3),
                    Gradient.Stop(color: P.groundBottom, location: 1),
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .frame(width: size.width, height: size.height)
            if engine.plan && !engine.previewing {
                NocturneProgramCarouselPlan(
                    metrics: metrics,
                    reduced: reduced,
                    onPressIn: { engine.controlPressIn() },
                    onPressOut: { engine.controlPressOut() },
                    onBack: { engine.backToTour() }
                )
            } else {
                TimelineView(.animation(paused: !engine.moving)) { timeline in
                    let frame = engine.frame(at: timeline.date, reduced: reduced)
                    ZStack(alignment: .topLeading) {
                        NocturneProgramCarouselWelcome(
                            t: frame.t,
                            held: frame.held,
                            reduced: reduced,
                            fontScale: metrics.fontScale
                        )
                        .accessibilityHidden(frame.programVisible)
                        NocturneProgramCarouselProgram(
                            frame: frame,
                            metrics: metrics,
                            engine: engine,
                            region: region
                        )
                    }
                    .frame(width: size.width, height: size.height, alignment: .topLeading)
                }
                // The tab bar belongs to the app around the tour, so it stays put while the
                // welcome hands over.
                NocturneProgramCarouselTabBar()
                    .padding(.horizontal, 30)
                    .padding(.bottom, metrics.footerBottomInset)
                    .frame(width: size.width, height: size.height, alignment: .bottom)
                    .allowsHitTesting(false)
            }
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
    }
}

/// The program of the tour: the header, the card row in a scroll view, and the footer with the
/// button. It fades in over 1.5 to 2.25 s and out over 15.5 to 16.1 s of the loop.
struct NocturneProgramCarouselProgram: View {
    let frame: NocturneProgramCarouselFrame
    let metrics: NocturneProgramCarouselMetrics
    let engine: NocturneProgramCarouselEngine
    let region: NocturneProgramCarouselRegion

    var body: some View {
        let opacity = frame.held ? 1 : M.programOpacity(t: frame.t)
        VStack(spacing: 0) {
            if !metrics.largeText {
                NocturneProgramCarouselHeader(fontScale: metrics.fontScale)
            }
            NocturneProgramCarouselViewport(enabled: frame.programVisible, showsIndicators: false) { viewport in
                pageArea(viewport: viewport)
            }
            .background(
                NocturneProgramCarouselTouchObserver(
                    isEnabled: frame.programVisible && !engine.previewing,
                    panEnabled: !engine.previewing,
                    region: region,
                    handlers: touchHandlers
                )
            )
            NocturneProgramCarouselFooter(
                frame: frame,
                metrics: metrics,
                onPressIn: { engine.controlPressIn() },
                onPressOut: { engine.controlPressOut() },
                onPress: { engine.startPlan() }
            )
        }
        .padding(.top, metrics.safeTop + 18)
        .frame(width: metrics.size.width, height: metrics.size.height, alignment: .top)
        .opacity(opacity)
        .allowsHitTesting(frame.programVisible)
        .accessibilityHidden(!frame.programVisible)
    }

    /// `readingHandlers` and the pan, as the engine takes them.
    private var touchHandlers: NocturneProgramCarouselTouchHandlers {
        let stride = Double(metrics.stride)
        return NocturneProgramCarouselTouchHandlers(
            touchStart: { engine.touchStart(count: $0) },
            touchEnd: { engine.touchEnd(remaining: $0) },
            panStart: { engine.panStart() },
            panUpdate: { engine.panUpdate(translationX: $0, stride: stride) },
            panFinish: { engine.panFinish(translationX: $0, velocityX: $1, success: $2, stride: stride) }
        )
    }

    /// The scroll content: the card row, centred in the viewport, or at the top under the header
    /// when the text is large. `contentContainerStyle` of the React Native scroll view.
    private func pageArea(viewport: CGSize) -> some View {
        let large = metrics.largeText
        let top: CGFloat = large ? 8 : 20
        let bottom: CGFloat = large ? 8 : 20 + metrics.size.height * 0.028
        return VStack(spacing: large ? 24 : 0) {
            if large {
                NocturneProgramCarouselHeader(fontScale: metrics.fontScale)
            }
            NocturneProgramCarouselTrack(
                frame: frame,
                metrics: metrics,
                region: region,
                onAdjust: { delta in engine.go(frame.page + delta) }
            )
        }
        .frame(
            maxWidth: .infinity,
            minHeight: max(0, viewport.height - top - bottom),
            alignment: large ? .top : .center
        )
        .padding(.top, top)
        .padding(.bottom, bottom)
    }
}
