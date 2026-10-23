import SwiftUI

/// A figure that rolls in from zeros when it appears and rolls to every new value, digit by digit.
/// Plain data sampled against the timeline's date, so the roll needs no state change per frame.
struct NeonBarBootOdometer {
    // Each digit rolls for ROLL plus SLOWER per place to its right, after waiting LATER per place:
    // the last digits settle last.
    private static let roll = 0.42
    private static let slower = 0.04
    private static let later = 0.055
    private static let settle = UnitCurve.bezier(
        startControlPoint: UnitPoint(x: 0.2, y: 0.8),
        endControlPoint: UnitPoint(x: 0.2, y: 1)
    )

    /// One character, keyed from the right as in React Native, so a digit keeps its column when
    /// the figure changes.
    private struct Cell {
        var key: Int
        var character: Character
        var digit: Int?
        var from = 0.0
        var to = 0.0
        var rollAt = Date.distantPast
        var rollFor = 0.0
        /// When the cell starts fading in; nil once it is simply shown.
        var fadeAt: Date?
        var fadeFor = 0.0

        func value(at date: Date) -> Double {
            guard rollFor > 0 else { return to }
            let t = min(max(date.timeIntervalSince(rollAt) / rollFor, 0), 1)
            return from + (to - from) * NeonBarBootOdometer.settle.value(at: t)
        }

        func opacity(at date: Date) -> Double {
            guard let fadeAt else { return 1 }
            let t = min(max(date.timeIntervalSince(fadeAt) / fadeFor, 0), 1)
            // Reanimated's default timing curve.
            return t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2
        }
    }

    private(set) var text = ""
    private var cells: [Cell] = []

    /// Shows `text`. `fresh` rolls every digit in from zero, as a newly mounted figure does; otherwise
    /// only the digits that change roll, from wherever they are.
    mutating func show(_ text: String, at date: Date, delay: Double, fresh: Bool, still: Bool) {
        let characters = Array(text)
        let old = fresh ? [:] : Dictionary(uniqueKeysWithValues: cells.map { ($0.key, $0) })
        self.text = text
        cells = characters.enumerated().map { place, character in
            let key = characters.count - place
            let digit = character.wholeNumberValue
            let start = date.addingTimeInterval(delay + Self.later * Double(place))
            let duration = Self.roll + Self.slower * Double(place)
            if var cell = old[key], (cell.digit == nil) == (digit == nil) {
                cell.character = character
                if let digit, digit != cell.digit {
                    cell.from = cell.value(at: start)
                    cell.to = Double(digit)
                    cell.rollAt = start
                    cell.rollFor = still ? 0 : duration
                    cell.digit = digit
                }
                return cell
            }
            var cell = Cell(key: key, character: character, digit: digit)
            cell.to = Double(digit ?? 0)
            if !still {
                cell.rollAt = start
                cell.rollFor = duration
                cell.fadeAt = start
                // A digit shows as it rolls up; other characters fade in over a whole roll.
                cell.fadeFor = digit == nil ? Self.roll : Self.roll / 2
            }
            return cell
        }
    }

    func view(at date: Date, font: Font, lineHeight: Double, color: Color) -> some View {
        HStack(spacing: 0) {
            ForEach(cells, id: \.key) { cell in
                Group {
                    if cell.digit != nil {
                        VStack(spacing: 0) {
                            ForEach(0..<10, id: \.self) { d in
                                Text(String(d)).frame(height: lineHeight)
                            }
                        }
                        .offset(y: -cell.value(at: date) * lineHeight)
                        .frame(height: lineHeight, alignment: .top)
                        .clipped()
                    } else {
                        Text(String(cell.character)).frame(height: lineHeight)
                    }
                }
                .opacity(cell.opacity(at: date))
            }
        }
        .font(font)
        .monospacedDigit()
        .foregroundStyle(color)
        .fixedSize()
    }
}
