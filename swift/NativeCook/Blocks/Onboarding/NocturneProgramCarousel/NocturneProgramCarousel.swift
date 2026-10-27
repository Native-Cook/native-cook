import SwiftUI
import UIKit

// Nocturne Program Carousel: the introduction of an invented evening-routine app, Nocturne, in navy.
// A starry welcome ("Welcome to a quieter evening" under a crescent mark with a halo) hands over to
// three blue cards on a curved shelf: a guide portrait over numbered plan strips, a miniature phone
// counting a routine score to 78, and a hologram figure under a blueprint grid with corner brackets,
// a scan line and pulsing points. A full-width blue "Build my evening plan" button sits over the
// app's tab bar with its raised Sleep button; the button opens a local evening plan.
//
// Timeline of one 17 s loop (autoPlay), t in seconds. It keeps the reference's timing through 15.1 s;
// two seconds close the loop. The loop starts and ends on the empty night with the tab bar:
//   0.00-0.50  the welcome rises out of a 1.06x scale (strong ease-out); its sparks fade in 0.20-0.80
//              (0.13 s apart) and drift until 1.85 s.
//   1.50-2.00  the welcome leaves; 1.50-2.25 the program fades in (header, cards, button).
//   2.25-3.45  the guide rises (2.25-3.10), the three plan strips come in from the left 0.1 s apart
//              (2.82-3.45); 2.40-3.05 the first sheen crosses the button; the bookmark drops 3.80-4.10.
//   4.30       the first page at rest (the anchor).
//   4.82-5.50  the second card slides in (measured edge positions, a long settling tail); its neighbours
//              stand back to 0.86, darken and turn up to 9 degrees toward the centre; the artwork of
//              each card lags its frame by a fifth of a card width; 5.00-5.40 its copy comes out of focus.
//   5.10-5.80  the phone rises into the card; 5.25-7.40 the score rolls up to 78 and its ring fills;
//              7.25-8.00 the dial swells; 8.40 the second page at rest.
//   8.90-9.55  the second sheen.
//   9.83-10.52 the third card slides in; 10.02-10.42 its copy comes out of focus.
//   10.20-11.30 the hologram and its brackets settle; the scan line goes down 10.50-11.15 and up
//              12.60-13.25; the three points light up 11.60-12.70 and pulse. 12.00 the third page at rest.
//   15.50-16.10 the program fades out; 16.10-17.00 the night alone again (the position is page 0).
//
// Touch: a finger on the page area stops the loop, 2 s after the last touch it settles on the
// current page and plays again. A swipe (10 pt sideways before 18 pt up or down) drags the row with
// rubber-banding past the ends and settles on a spring (400 ms, damping ratio 0.8) with the finger's
// speed: 18% of a card or 450 pt/s turns the page. The button opens the evening plan; "Back to the
// tour" returns to the page it left. VoiceOver adjusts the pages. Under Reduce Motion the program is
// shown at once, cards change in place, and there is no loop.
//
// `previewTime` freezes the loop at one moment (and switches touch off): the lead uses it to compare
// frames. `onStartPlan` is called when the plan opens, `onPageChange` when another page is reached.

struct NocturneProgramCarousel: View {
    /// One period of the loop in seconds (`LOOP` in the React Native file).
    static let loop: Double = 17

    var autoPlay = false
    var previewTime: Double?
    var onStartPlan: (() -> Void)?
    var onPageChange: ((Int) -> Void)?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var engine: NocturneProgramCarouselEngine

    init(
        autoPlay: Bool = false,
        previewTime: Double? = nil,
        onStartPlan: (() -> Void)? = nil,
        onPageChange: ((Int) -> Void)? = nil
    ) {
        self.autoPlay = autoPlay
        self.previewTime = previewTime
        self.onStartPlan = onStartPlan
        self.onPageChange = onPageChange
        _engine = State(
            initialValue: NocturneProgramCarouselEngine(autoPlay: autoPlay, previewTime: previewTime, reduced: false)
        )
        _ = NocturneProgramCarouselAssets.guide
        _ = NocturneProgramCarouselAssets.hologram
    }

    var body: some View {
        GeometryReader { geo in
            // The page is laid out on the whole window, as React Native's `useWindowDimensions`
            // sees it, and drawn under the status bar and the home indicator.
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            NocturneProgramCarouselScreen(
                engine: engine,
                size: size,
                safeTop: insets.top,
                safeBottom: insets.bottom,
                reduced: reduceMotion
            )
            .frame(width: size.width, height: size.height)
            .ignoresSafeArea()
        }
        .background(NocturneProgramCarouselPalette.ground.ignoresSafeArea())
        // `<StatusBar style="light" />`: light status bar content on the night.
        .preferredColorScheme(.dark)
        .onAppear {
            engine.configure(
                autoPlay: autoPlay,
                previewTime: previewTime,
                onStartPlan: onStartPlan,
                onPageChange: onPageChange
            )
            engine.setReduced(reduceMotion)
            engine.setForeground(scenePhase == .active)
            engine.start()
        }
        .onDisappear {
            engine.teardown()
        }
        .onChange(of: reduceMotion) { _, value in
            engine.setReduced(value)
        }
        .onChange(of: scenePhase) { _, value in
            engine.setForeground(value == .active)
        }
        .onChange(of: previewTime) { _, value in
            engine.configure(
                autoPlay: autoPlay,
                previewTime: value,
                onStartPlan: onStartPlan,
                onPageChange: onPageChange
            )
        }
        .onChange(of: autoPlay) { _, value in
            engine.configure(
                autoPlay: value,
                previewTime: previewTime,
                onStartPlan: onStartPlan,
                onPageChange: onPageChange
            )
        }
    }
}
