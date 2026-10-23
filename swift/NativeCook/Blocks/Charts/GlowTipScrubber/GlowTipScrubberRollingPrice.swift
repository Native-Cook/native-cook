import CoreText
import SwiftUI

final class GlowTipScrubberRollingPrice {
    static let roll = 0.14, reel = ["0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0"]
    static let easing = UnitCurve.bezier(startControlPoint: .init(x: 1.0 / 3, y: 1), endControlPoint: .init(x: 2.0 / 3, y: 1))
    private struct Digit { var goal: Int; var position: Tween; var changed = Date.distantPast }
    private var digits: [Digit] = [], pattern = ""
    func draw(_ context: GraphicsContext, value: Double, pattern: String, font: UIFont, lineHeight: Double, tracking: Double, color: Color, still: Bool, date: Date, x: Double = 0, y: Double = 0) {
        let decimals = pattern.contains(".") ? pattern.split(separator: ".", omittingEmptySubsequences: false).last!.count : 0
        let scale = pow(10, Double(decimals)), number = Int(floor(abs(value) * scale + 0.5)), count = pattern.filter { $0 == "0" }.count
        let places = (0..<count).reversed().map { Int(pow(10, Double($0))) }
        if self.pattern != pattern { self.pattern = pattern; digits = places.map { let next = number / $0 % 10; return Digit(goal: next, position: Tween(Double(next))) } }
        for i in places.indices {
            let next = number / places[i] % 10, from = (digits[i].goal % 10 + 10) % 10, step = (next - from + 15) % 10 - 5
            if step != 0 {
                digits[i].goal += step
                let quick = floor(date.timeIntervalSince1970 * 1000) - floor(digits[i].changed.timeIntervalSince1970 * 1000) < Self.roll * 1000
                digits[i].changed = date; digits[i].position.retarget(Double(digits[i].goal), at: date, duration: still || quick ? 0 : Self.roll, curve: Self.easing)
            }
        }
        let width = ceil(("0" as NSString).size(withAttributes: [.font: font, .kern: tracking]).width)
        let baseline = (lineHeight + CTFontGetAscent(font as CTFont) - CTFontGetDescent(font as CTFont)) / 2 + CTFontGetDescent(font as CTFont)
        var left = x, digit = 0
        for char in pattern {
            if char != "0" {
                let label = String(char); context.draw(Text(label).font(Font(font)).tracking(tracking).foregroundStyle(color), at: CGPoint(x: left, y: y + baseline), anchor: .bottomLeading)
                left += (label as NSString).size(withAttributes: [.font: font, .kern: tracking]).width
            } else {
                var c = context; c.clip(to: Path(CGRect(x: left, y: y, width: width, height: lineHeight)))
                let pos = digits[digit].position.value(at: date), at = (pos.truncatingRemainder(dividingBy: 10) + 10).truncatingRemainder(dividingBy: 10)
                for row in 0...10 { c.draw(Text(Self.reel[row]).font(Font(font)).tracking(tracking).foregroundStyle(color), at: CGPoint(x: left + width / 2, y: y + baseline + (Double(row) - at) * lineHeight), anchor: .bottom) }
                left += width; digit += 1
            }
        }
    }
}
