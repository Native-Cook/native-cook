import CoreText
import SwiftUI

// Reels keep drawing state without publishing per-frame changes to the screen.
final class LineToCandlesSwitchRollingNumber {
    static let roll = 0.15, reveal = roll * 1.5
    static let reel = ["0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0"]
    static let easing = UnitCurve.bezier(startControlPoint: .init(x: 1.0 / 3, y: 1), endControlPoint: .init(x: 2.0 / 3, y: 1))
    private struct Digit {
        var goal: Int, present: Bool
        var position: Tween, shown: Tween
        var changed = Date.distantPast
    }
    private var digits: [Digit] = []
    private var places: [Int] = []
    private var digitWidth = 0.0, dotWidth = 0.0
    private var size = 0.0, tracking = 0.0
    private var height = 0.0, color = Color.clear
    private var font = UIFont.systemFont(ofSize: 17)

    func update(value: Double, decimals: Int, wholeDigits: Int, fontSize: Double, lineHeight: Double, weight: UIFont.Weight, color: Color, tracking: Double = 0, still: Bool, date: Date) -> Double {
        let scale = Int(pow(10, Double(decimals))), number = Int(floor(abs(value) * Double(scale) + 0.5))
        font = .monospacedDigitSystemFont(ofSize: fontSize, weight: weight); height = lineHeight; self.color = color; self.tracking = tracking
        if size != fontSize || places.count != wholeDigits + decimals {
            size = fontSize; digitWidth = ceil(("0" as NSString).size(withAttributes: [.font: font, .kern: tracking]).width)
            dotWidth = ("." as NSString).size(withAttributes: [.font: font, .kern: tracking]).width
            places = (0..<(wholeDigits + decimals)).reversed().map { Int(pow(10, Double($0))) }; digits = []
        }
        if digits.isEmpty {
            digits = places.map { place in
                let next = number / place % 10, present = place <= scale || number >= place
                var shown = Tween(1); shown.retarget(present ? 1 : 0, at: date, duration: still ? 0 : Self.reveal, curve: Self.easing)
                return Digit(goal: next, present: present, position: Tween(Double(next)), shown: shown)
            }
        }
        for i in places.indices {
            let next = number / places[i] % 10, from = (digits[i].goal % 10 + 10) % 10, step = (next - from + 15) % 10 - 5
            if step != 0 {
                digits[i].goal += step
                let quick = floor(date.timeIntervalSince1970 * 1000) - floor(digits[i].changed.timeIntervalSince1970 * 1000) < Self.roll * 1000
                digits[i].changed = date
                digits[i].position.retarget(Double(digits[i].goal), at: date, duration: still || quick ? 0 : Self.roll, curve: Self.easing)
            }
            let present = places[i] <= scale || number >= places[i]
            if digits[i].present != present {
                digits[i].present = present; digits[i].shown.retarget(present ? 1 : 0, at: date, duration: still ? 0 : Self.reveal, curve: Self.easing)
            }
        }
        return digits.reduce(dotWidth, { $0 + digitWidth * $1.shown.value(at: date) })
    }
    func draw(_ context: GraphicsContext, x: Double, y: Double, decimals: Int, date: Date) {
        let scale = Int(pow(10, Double(decimals))); var left = x
        func text(_ c: GraphicsContext, _ label: String, _ x: Double, _ top: Double, _ anchor: UnitPoint) {
            let baseline = top + (height + CTFontGetAscent(font as CTFont) - CTFontGetDescent(font as CTFont)) / 2
            let t = Text(label).font(Font(font)).tracking(tracking).foregroundStyle(color)
            c.draw(t, at: CGPoint(x: x, y: baseline + CTFontGetDescent(font as CTFont)), anchor: anchor)
        }
        for i in places.indices {
            if places[i] == scale / 10 { text(context, ".", left, y, .bottomLeading); left += dotWidth }
            let shown = digits[i].shown.value(at: date), width = digitWidth * shown
            var c = context; c.opacity *= shown; c.clip(to: Path(CGRect(x: left, y: y, width: width, height: height)))
            let at = digits[i].position.value(at: date).truncatingRemainder(dividingBy: 10), normalized = at < 0 ? at + 10 : at
            for row in 0...10 { text(c, Self.reel[row], left + digitWidth / 2, y + (Double(row) - normalized) * height, .bottom) }
            left += width
        }
    }
}
