import SwiftUI

/// A white book whose pages turn with a realistic page curl: drag from the right to turn forward, from the left to
/// turn back, flick right to riffle every page back to the title. The curl is a per-pixel cylinder in a Metal
/// shader over textures of the page views. `autoPlay` runs a scripted finger through the same input as a real one.
struct PageCurlJournal: View {
    /// One autoplay period, in seconds: `PageCurlJournalMotion.loopMs` (14 000 ms) written out for the catalog check.
    static let loop: Double = 14

    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.displayScale) private var displayScale
    @State private var model = PageCurlJournalModel()

    var body: some View {
        GeometryReader { proxy in
            let size = proxy.size
            let scale = size.width / PageCurlJournalTheme.referenceWidth
            ZStack(alignment: .topLeading) {
                PageCurlJournalPageView(page: model.page, size: size, pixelScale: displayScale, twinkle: model.twinkle)
                PageCurlJournalCanvas(model: model, autoPlay: autoPlay, reduced: reduceMotion, active: scenePhase == .active)
                    .frame(width: size.width, height: size.height)
                // The binding's shadow and the edges of the pages still to turn sit over everything, like the book itself.
                LinearGradient(
                    colors: [Color.black.opacity(0.05), Color.black.opacity(0)],
                    startPoint: .leading,
                    endPoint: .trailing
                )
                .frame(width: PageCurlJournalTheme.Space.md * scale, height: size.height)
                .allowsHitTesting(false)
                ForEach(0..<3, id: \.self) { index in
                    PageCurlJournalTheme.stackInk
                        .frame(width: PageCurlJournalMotion.Stack.line, height: size.height)
                        .offset(x: size.width - (Double(index) * PageCurlJournalMotion.Stack.gap + 0.4) * scale - PageCurlJournalMotion.Stack.line)
                        .opacity(stackOpacity(index))
                        .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.26), value: model.page)
                        .allowsHitTesting(false)
                }
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Journal")
            .accessibilityValue("\(PageCurlJournalTheme.Copy.pages[model.page]), page \(model.page + 1) of 4")
            .accessibilityAdjustableAction { direction in
                switch direction {
                case .increment: model.turn?(1)
                case .decrement: model.turn?(-1)
                @unknown default: break
                }
            }
        }
        .ignoresSafeArea()
        .background(PageCurlJournalTheme.Ink.paper.ignoresSafeArea())
        // The page is white: the status bar's content is dark.
        .preferredColorScheme(.light)
    }

    /// One hairline per sheet still to turn, fading as each one goes.
    private func stackOpacity(_ index: Int) -> Double {
        let left = Double(PageCurlJournalEngine.lastPage - model.page)
        return max(0, min(1, left - Double(index)))
    }
}

private struct PageCurlJournalCanvas: UIViewRepresentable {
    let model: PageCurlJournalModel
    let autoPlay: Bool
    let reduced: Bool
    let active: Bool

    func makeUIView(context: Context) -> PageCurlJournalSurface {
        PageCurlJournalSurface(model: model, autoPlay: autoPlay, reduced: reduced)
    }

    func updateUIView(_ view: PageCurlJournalSurface, context: Context) {
        view.configure(autoPlay: autoPlay, reduced: reduced)
        view.setActive(active)
    }

    static func dismantleUIView(_ view: PageCurlJournalSurface, coordinator: ()) {
        view.setActive(false)
    }
}

#Preview {
    PageCurlJournal(autoPlay: true)
}
