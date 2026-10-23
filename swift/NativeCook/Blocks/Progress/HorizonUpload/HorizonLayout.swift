import UIKit

/// The RN point-space contract, including small-phone artwork fit and capped type metrics.
struct HorizonLayout: Equatable {
  var size: CGSize = .zero
  var safe: UIEdgeInsets = .zero
  var fontScale: Double = 1
  var width: Double { floor(min(size.width - 72, 360) / 2) * 2 }
  var x: Double { floor((size.width - width) / 2) }
  var y: Double { round(safe.top + (size.height - safe.top - safe.bottom - 240) * 0.42) }
  var card: CGRect { CGRect(x: x, y: y, width: width, height: 240) }
  var panel: CGRect { CGRect(x: 24, y: 158, width: width - 48, height: 58) }
  var barWidth: Double { width - 72 }
  var copyLeft: Double { min(152, width - 120) }
  var copyWidth: Double { width - 24 - copyLeft }
  var fileScale: Double { min(1, (copyLeft - 36) / 116) }
  var typeScale: Double {
    let fit = (barWidth - 18 - 10 - 8) / (78 + 114)
    let ascent = (20 + 15 * (1.066 - 0.3)) / 2
    let baseline = max(0, (36 - 26 * 1.366) / 2) + 26 * 1.066
    return min(fontScale, 1.3, 39 / (baseline + 20 - ascent), width >= 303 ? max(1, fit) : 1.3)
  }
  var figureTop: Double { max(0, 20 - 19 * typeScale) }
  var baseline: Double { figureTop + (max(0, (36 - 26 * 1.366) / 2) + 26 * 1.066) * typeScale }
  var labelTop: Double { baseline - (20 + 15 * (1.066 - 0.3)) / 2 * typeScale }
  var labelWidth: Double { panel.width - 12 - 78 * typeScale - 8 - 40 }
  var glyphTop: Double { baseline - 5.5 * typeScale - 9 }
  var figureClipHeight: Double { min(36 * typeScale, 39 - figureTop) }
}
