import SwiftUI

/// Running counts never acquire a numeric transition. Every endpoint mounts
/// its own landing view, so interrupted rolls cannot leave outgoing glyphs.
struct EmberCount: View {
  let value: Int, from: Int, goal: Int
  let reduced: Bool

  var body: some View {
    Group {
      if !reduced && (value == EmberTheme.E.restSteps || value == goal) {
        EmberLandingCount(from: from, to: value).id(value)
      } else {
        Text(EmberL10n.number(value)).transaction { $0.animation = nil }
      }
    }
    .font(EmberFonts.font(EmberTheme.E.count.size, semibold: true)).tracking(
      EmberTheme.TypeStyle.countTracking
    ).foregroundStyle(EmberTone.ink)
    .fixedSize().frame(height: EmberTheme.E.count.h)
  }
}
