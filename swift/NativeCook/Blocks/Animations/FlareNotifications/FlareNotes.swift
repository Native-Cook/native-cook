import SwiftUI

/// Flare — three notification pills, each with a small fire at its cap.
/// After a reference clip of brand notifications whose trailing ends burn in
/// the brand's colors. Heat here means "new": a notification arrives and
/// ignites, tap to read it and the fire cools to an ember, tap again to
/// relight, hold to stoke it.
///
/// Port of `FlareNotes` in `flare-notes.tsx`; laid out
/// in the safe-area content box like the RN page (`onLayout` of the
/// absolute-fill view inside the safe-area padding).
struct FlareNotes: View {
  @State private var model = FlareNotesModel()
  @Environment(\.displayScale) private var displayScale

  var body: some View {
    GeometryReader { proxy in
      if proxy.size.width > 0 && proxy.size.height > 0 {
        content(FlareLayout(boxWidth: proxy.size.width, boxHeight: proxy.size.height))
      }
    }
  }

  private func content(_ layout: FlareLayout) -> some View {
    // Yoga puts every view on the device-pixel grid: the stack's left edge
    // (47.5 on a 402-wide phone) lands on 47.667 at 3×.
    let x = flarePixelRound(layout.x, scale: displayScale)
    return ZStack(alignment: .topLeading) {
      ForEach(Array(model.pills.enumerated()), id: \.offset) { i, pill in
        FlarePillView(pill: pill, width: layout.width, round: model.round) { model.toggle(pill) }
          .frame(width: layout.width, height: FlareMotion.height)
          .offset(x: x, y: layout.pillTop(i))
      }
      // The controls wait for the last note to land.
      if model.live {
        controls
          .offset(y: layout.controlsTop)
          .opacity(model.controlsOpacity)
      }
    }
    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    .onAppear { model.pillsAppeared() }
  }

  /// Hint (absolute, at the controls' top) and "Deliver again" 34 below it.
  private var controls: some View {
    ZStack(alignment: .top) {
      FlareHint(text: FlareL10n.hint, color: FlareTone.pageHint)
      FlareReplayPill(label: FlareL10n.deliverAgain) { model.replay() }
        .padding(.top, 34)
    }
    .frame(maxWidth: .infinity, alignment: .top)
  }
}
