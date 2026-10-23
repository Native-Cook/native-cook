import SwiftUI

/// The Material Community icons the card uses, as outlines on their 24-point grid (taken from the
/// icon font the React Native version draws them with), so they match it in size and weight.
enum SemicircleSpendGaugeIcon {
    case shopping, receipt, coins, pie, chevron, info, check

    fileprivate var outline: String {
        switch self {
        case .shopping:
            "M18.98 6H17.01Q17.01 4.64 16.33 3.49Q15.65 2.34 14.5 1.66Q13.35 0.98 12 0.98Q10.64 0.98 9.49 1.66Q8.34 2.34 7.66 3.49Q6.98 4.64 6.98 6H5.01Q4.17 6 3.58 6.58Q3 7.17 3 8.01V20.01Q3 20.81 3.58 21.39Q4.17 21.98 5.01 21.98H18.98Q19.82 21.98 20.41 21.39Q21 20.81 21 20.01V8.01Q21 7.17 20.41 6.58Q19.82 6 18.98 6ZM12 3Q13.26 3 14.13 3.86Q15 4.73 15 6H9Q9 4.73 9.86 3.86Q10.73 3 12 3ZM18.98 20.01H5.01V8.01H18.98ZM12 12Q10.73 12 9.86 11.13Q9 10.26 9 9H6.98Q6.98 10.35 7.66 11.5Q8.34 12.65 9.49 13.33Q10.64 14.01 12 14.01Q13.35 14.01 14.5 13.33Q15.65 12.65 16.33 11.5Q17.01 10.35 17.01 9H15Q15 10.26 14.13 11.13Q13.26 12 12 12Z"
        case .receipt:
            "M19.5 3.51L18 2.01L16.5 3.51L15 2.01L13.5 3.51L12 2.01L10.5 3.51L9 2.01L7.5 3.51L6 2.01L4.5 3.51L3 2.01V21.98L4.5 20.48L6 21.98L7.5 20.48L9 21.98L10.5 20.48L12 21.98L13.5 20.48L15 21.98L16.5 20.48L18 21.98L19.5 20.48L21 21.98V2.01ZM18.98 18.98H5.01V5.01H18.98ZM6 15H18V17.01H6ZM6 11.01H18V12.98H6ZM6 6.98H18V9H6Z"
        case .coins:
            "M15 3.98Q17.15 3.98 19 5.06Q20.85 6.14 21.93 7.99Q23.01 9.84 23.01 12Q23.01 14.15 21.93 16Q20.85 17.85 19 18.93Q17.15 20.01 15 20.01Q12.84 20.01 10.99 18.93Q9.14 17.85 8.06 16Q6.98 14.15 6.98 12Q6.98 9.84 8.06 7.99Q9.14 6.14 10.99 5.06Q12.84 3.98 15 3.98ZM15 18Q16.64 18 18.02 17.2Q19.4 16.4 20.2 15.02Q21 13.64 21 12Q21 10.35 20.2 8.97Q19.4 7.59 18.02 6.79Q16.64 6 15 6Q13.35 6 11.97 6.79Q10.59 7.59 9.79 8.97Q9 10.35 9 12Q9 13.64 9.79 15.02Q10.59 16.4 11.97 17.2Q13.35 18 15 18ZM3 12Q3 13.92 4.12 15.46Q5.25 17.01 6.98 17.67V19.73Q4.4 19.07 2.69 16.92Q0.98 14.76 0.98 12Q0.98 9.23 2.69 7.07Q4.4 4.92 6.98 4.26V6.32Q5.25 6.98 4.12 8.53Q3 10.07 3 12Z"
        case .pie:
            "M12.98 11.01H19.96Q19.59 8.25 17.67 6.35Q15.75 4.45 12.98 4.03ZM11.01 19.96V4.03Q8.01 4.45 6 6.7Q3.98 8.95 3.98 12Q3.98 15.04 6 17.32Q8.01 19.59 11.01 19.96ZM12.98 19.96Q15.75 19.59 17.67 17.67Q19.59 15.75 19.96 12.98H12.98ZM12 21.98Q9.93 21.98 8.1 21.21Q6.28 20.43 4.92 19.07Q3.56 17.71 2.78 15.89Q2.01 14.06 2.01 12Q2.01 9.93 2.78 8.1Q3.56 6.28 4.92 4.92Q6.28 3.56 8.1 2.78Q9.93 2.01 12 2.01Q14.06 2.01 15.89 2.78Q17.71 3.56 19.07 4.92Q20.43 6.28 21.21 8.1Q21.98 9.93 21.98 12Q21.98 14.06 21.21 15.89Q20.43 17.71 19.07 19.07Q17.71 20.43 15.89 21.21Q14.06 21.98 12 21.98Z"
        case .chevron:
            "M7.4 8.57L12 13.17L16.59 8.57L18 9.98L12 15.98L6 9.98Z"
        case .info:
            "M12.98 9H11.01V6.98H12.98ZM12.98 17.01H11.01V11.01H12.98ZM12 2.01Q10.03 2.01 8.17 2.76Q6.32 3.51 4.92 4.92Q3.51 6.32 2.76 8.17Q2.01 10.03 2.01 12Q2.01 13.96 2.76 15.82Q3.51 17.67 4.92 19.07Q6.32 20.48 8.17 21.23Q10.03 21.98 12 21.98Q13.96 21.98 15.82 21.23Q17.67 20.48 19.07 19.07Q20.48 17.67 21.23 15.82Q21.98 13.96 21.98 12Q21.98 10.03 21.23 8.17Q20.48 6.32 19.07 4.92Q17.67 3.51 15.82 2.76Q13.96 2.01 12 2.01Z"
        case .check:
            "M21 6.98L9 18.98L3.51 13.5L4.92 12.09L9 16.17L19.59 5.57Z"
        }
    }

    /// The outline as a path in a `size`-point box (M, L, H, V, Q and Z, absolute, as written above).
    fileprivate func path(size: Double) -> Path {
        let scale = size / 24
        var path = Path()
        var command: Character = "M"
        var numbers: [Double] = []
        var current = CGPoint.zero
        func point(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: x * scale, y: y * scale) }
        func flush() {
            switch command {
            case "M" where numbers.count >= 2:
                current = point(numbers[0], numbers[1])
                path.move(to: current)
            case "L" where numbers.count >= 2:
                current = point(numbers[0], numbers[1])
                path.addLine(to: current)
            case "H" where numbers.count >= 1:
                current = CGPoint(x: numbers[0] * scale, y: current.y)
                path.addLine(to: current)
            case "V" where numbers.count >= 1:
                current = CGPoint(x: current.x, y: numbers[0] * scale)
                path.addLine(to: current)
            case "Q" where numbers.count >= 4:
                current = point(numbers[2], numbers[3])
                path.addQuadCurve(to: current, control: point(numbers[0], numbers[1]))
            default:
                break
            }
            numbers = []
        }
        var token = ""
        for character in outline + " " {
            if character.isNumber || character == "." || character == "-" {
                token.append(character)
                continue
            }
            if let value = Double(token) { numbers.append(value) }
            token = ""
            if character.isLetter {
                flush()
                command = character
                if character == "Z" { path.closeSubpath() }
            }
        }
        flush()
        return path
    }
}

/// An icon of the card at the size the React Native version gives it.
struct SemicircleSpendGaugeIconView: View {
    var icon: SemicircleSpendGaugeIcon
    var size: Double
    var color: Color

    var body: some View {
        icon.path(size: size)
            .fill(color)
            .frame(width: size, height: size)
            .accessibilityHidden(true)
    }
}
