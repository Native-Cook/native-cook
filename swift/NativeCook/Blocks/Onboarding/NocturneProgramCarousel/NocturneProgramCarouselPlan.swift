import SwiftUI
import UIKit

private typealias M = NocturneProgramCarouselMotion
private typealias P = NocturneProgramCarouselPalette

/// The evening plan that "Build my evening plan" opens: the mark, an eyebrow, a title, a line, three
/// timed steps and a "Back to the tour" button, in a scroll view with the dark progressive blur at
/// its edges. The pieces arrive as a short cascade (`rise`): 70 ms a line, each settling 12 pt.
struct NocturneProgramCarouselPlan: View {
    let metrics: NocturneProgramCarouselMetrics
    let reduced: Bool
    let onPressIn: () -> Void
    let onPressOut: () -> Void
    let onBack: () -> Void

    /// When the plan appeared: the cascade is worked out from the time since.
    @State private var start = Date()
    /// The cascade is over, so the timeline may pause.
    @State private var settled = false

    /// The three steps of the plan, character for character.
    private static let steps: [(clock: String, title: String, body: String)] = [
        ("21:15", "Put the day down", "Write one thing you want to leave for tomorrow."),
        ("21:25", "Find a quiet minute", "Dim the room and take a comfortable breath."),
        ("21:30", "Choose something gentle", "A few pages, a warm drink, a little less noise."),
    ]

    var body: some View {
        TimelineView(.animation(paused: settled || reduced)) { timeline in
            let elapsed = timeline.date.timeIntervalSince(start)
            NocturneProgramCarouselViewport(enabled: true, showsIndicators: true) { viewport in
                content(elapsed: elapsed, viewport: viewport)
            }
        }
        .task {
            // The last line starts at 80 + 5 x 70 ms and takes 380 ms.
            try? await Task.sleep(for: .seconds(1.0))
            settled = true
        }
        .frame(width: metrics.size.width, height: metrics.size.height)
    }

    /// `rise(order, reduced)`: `FadeInDown.duration(380).delay(80 + order * 70)` on the strong
    /// ease-out, from opacity 0 and 12 pt lower; nothing under Reduce Motion.
    private func rise(_ order: Int, elapsed: Double) -> (opacity: Double, lift: CGFloat) {
        if reduced {
            return (1, 0)
        }
        let delay = 0.08 + Double(order) * 0.07
        let p = M.enter(M.segment(elapsed, delay, delay + 0.38))
        return (p, CGFloat(12 * (1 - p)))
    }

    private func content(elapsed: Double, viewport: CGSize) -> some View {
        let top = metrics.safeTop + 32
        let bottom = metrics.safeBottom + 14
        let scale = metrics.fontScale
        let mark = rise(0, elapsed: elapsed)
        let heading = rise(1, elapsed: elapsed)
        let line = rise(2, elapsed: elapsed)
        return VStack(alignment: .leading, spacing: 0) {
            NocturneProgramCarouselMark(size: 50)
                .opacity(mark.opacity)
                .offset(y: mark.lift)
            VStack(alignment: .leading, spacing: 0) {
                NocturneProgramCarouselLabel(
                    text: "YOUR EVENING PLAN", style: .resultEyebrow, fontScale: scale, alignment: .leading)
                    .padding(.top, 24)
                NocturneProgramCarouselLabel(
                    text: "A softer landing.", style: .resultTitle, fontScale: scale, alignment: .leading)
                    .padding(.top, 12)
            }
            .opacity(heading.opacity)
            .offset(y: heading.lift)
            NocturneProgramCarouselLabel(
                text: "Three small moments to make tonight your own.",
                style: .resultBody,
                fontScale: scale,
                alignment: .leading
            )
            .padding(.top, 14)
            .opacity(line.opacity)
            .offset(y: line.lift)
            VStack(alignment: .leading, spacing: 28) {
                ForEach(0..<Self.steps.count, id: \.self) { index in
                    stepRow(index, rise: rise(3 + index, elapsed: elapsed))
                }
            }
            .padding(.top, 42)
            Spacer(minLength: 0)
            NocturneProgramCarouselTourButton(
                label: "Back to the tour",
                reduced: reduced,
                fontScale: scale,
                onPressIn: onPressIn,
                onPressOut: onPressOut,
                onPress: onBack
            )
        }
        .frame(maxWidth: .infinity, minHeight: max(0, viewport.height - top - bottom), alignment: .top)
        .padding(.horizontal, 28)
        .padding(.top, top)
        .padding(.bottom, bottom)
    }

    /// One timed step: the clock, then the title and the line under it.
    private func stepRow(_ index: Int, rise: (opacity: Double, lift: CGFloat)) -> some View {
        let step = Self.steps[index]
        return HStack(alignment: .top, spacing: 20) {
            NocturneProgramCarouselLabel(
                text: step.clock,
                style: .planTime,
                fontScale: metrics.fontScale,
                alignment: .leading,
                tabularFigures: true
            )
            .padding(.top, 3)
            VStack(alignment: .leading, spacing: 0) {
                NocturneProgramCarouselLabel(
                    text: step.title, style: .planTitle, fontScale: metrics.fontScale, alignment: .leading)
                NocturneProgramCarouselLabel(
                    text: step.body, style: .planBody, fontScale: metrics.fontScale, alignment: .leading)
                    .padding(.top, 7)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .opacity(rise.opacity)
        .offset(y: rise.lift)
    }
}
