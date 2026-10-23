import CoreText
import SwiftUI

final class MetricDropdownLineScrubRoller {
    static let roll = 0.26, digits = ["0", "1", "2", "3", "4", "5", "6", "7", "8", "9"]
    static let ease = UnitCurve.bezier(startControlPoint: .zero, endControlPoint: .init(x: 0.58, y: 1))
    private struct Digit { var goal: Int; var at: Tween }
    private var columns: [Int: Digit] = [:]
    private(set) var end = Date.distantPast
    func draw(_ c: GraphicsContext, _ text: String, _ date: Date, _ still: Bool, _ x: Double, _ y: Double) -> Double {
        let font = UIFont.monospacedDigitSystemFont(ofSize: 26, weight: .semibold), height = 30.0, tracking = -0.5
        let baseline = (height + CTFontGetAscent(font as CTFont) - CTFontGetDescent(font as CTFont)) / 2 + CTFontGetDescent(font as CTFont)
        let characters = Array(text); var left = x, keys: Set<Int> = []
        for (i, char) in characters.enumerated() {
            let key = characters.count - i, label = String(char), width = (label as NSString).size(withAttributes: [.font: font, .kern: tracking]).width
            if let digit = Int(label) {
                keys.insert(key)
                if columns[key] == nil { columns[key] = Digit(goal: digit, at: Tween(Double(digit))) }
                if columns[key]!.goal != digit { columns[key]!.goal = digit; columns[key]!.at.retarget(Double(digit), at: date, duration: still ? 0 : Self.roll, curve: Self.ease); end = date.addingTimeInterval(still ? 0 : Self.roll) }
                let pos = columns[key]!.at.value(at: date)
                var clip = c; clip.clip(to: Path(CGRect(x: left, y: y, width: width, height: height)))
                for row in 0..<10 { clip.draw(Text(Self.digits[row]).font(Font(font)).tracking(tracking).foregroundStyle(.white), at: CGPoint(x: left, y: y + baseline + (Double(row) - pos) * height), anchor: .bottomLeading) }
            } else { c.draw(Text(label).font(Font(font)).tracking(tracking).foregroundStyle(.white), at: CGPoint(x: left, y: y + baseline), anchor: .bottomLeading) }
            left += width
        }
        columns = columns.filter { keys.contains($0.key) }; return left - x
    }
}
