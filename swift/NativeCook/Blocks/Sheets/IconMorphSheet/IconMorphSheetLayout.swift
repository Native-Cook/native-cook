import SwiftUI

struct IconMorphSheetLayout {
    var width: Double
    var height: Double
    var top: Double
    var bottom: Double
    var compact: Bool { height < 780 }
    var buttonTop: Double { height - bottom - (compact ? 16 : 8) - 56 }
    var stageHeight: Double { compact ? 208 : 264 }
    var stageTop: Double { buttonTop - 32 - stageHeight }
    var headerY: Double { top + (compact ? 20 : 22) }
    var titleY: Double { headerY + (compact ? 88 : 142) }
    var titleLine: Double { compact ? 28 : 36 }
    var avatarSize: Double { compact ? 36 : 44 }
    var avatarY: Double { headerY + (compact ? 44 : 66) }
    var titleBottom: Double { titleY + titleLine * (compact ? 1.5 : 2.5) }
    var balanceTop: Double { (((titleBottom + buttonTop) / 2 - 24) / 4).rounded() * 4 }
    var balanceVisible: Bool { balanceTop >= titleBottom + 16 && balanceTop + 48 <= buttonTop - 16 }
    var short: Double { compact ? 466 : 522 }
    var tall: Double { compact ? 486 : 542 }
    var unit: Double { compact ? 66 : 84 }
    var button: CGRect { CGRect(x: 16, y: buttonTop, width: width - 32, height: 56) }

    func sheetHeight(_ position: Double) -> Double { IconMorphSheetTimeline.sheetHeight(position, short, tall) }
    func travel(_ f: IconMorphSheetTimeline.Frame, reduced: Bool) -> Double {
        reduced ? 0 : (1 - f.open) * sheetHeight(f.position) + f.drag
    }
}
