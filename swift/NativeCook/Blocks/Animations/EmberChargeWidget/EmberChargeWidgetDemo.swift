import SwiftUI

/// The safe-area demo page, centering the instrument and its scalable hint together.
struct EmberChargeWidgetDemo: View {
  private typealias E = EmberTheme.E

  init() { EmberFonts.register() }

  var body: some View {
    GeometryReader { geometry in
      // A positive safety floor preserves the accepted behavior for degenerate previews.
      let scale = min(1, max(0.1, (geometry.size.width - 2 * E.pageMargin) / E.size))
      VStack(spacing: E.space.corner * scale) {
        EmberCharge(autoPlay: true, scale: scale)
        Text(EmberL10n.t("hint"))
          .font(.custom("Manrope-Regular", size: EmberTheme.TypeStyle.hint, relativeTo: .footnote))
          .foregroundStyle(EmberTone.sub)
          .multilineTextAlignment(.center).frame(minHeight: E.line.hint).padding(
            .horizontal, E.space.near)
      }
      .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
    .background(EmberTone.page.ignoresSafeArea()).preferredColorScheme(.dark)
  }
}

#Preview { EmberChargeWidgetDemo() }
