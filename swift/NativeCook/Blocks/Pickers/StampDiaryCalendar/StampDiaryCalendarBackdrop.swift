import SwiftUI

/// Everything still: the hero's graph paper and the calendar's tiles and lines. Drawn once.
struct StampDiaryCalendarBackdrop: View {
    typealias M = StampDiaryCalendarMotion

    var space: StampDiaryCalendarSpace
    var month: M.Month

    private static let page = Color(hex: 0x0B0B0B)
    private static let heroTop = Color(hex: 0x010101)
    private static let heroBottom = Color(hex: 0x060606)
    private static let heroLine = Color.white.opacity(0.05)
    private static let blank = Color(hex: 0x111111)
    private static let gridLine = Color(hex: 0x222222)

    var body: some View {
        let T = StampDiaryCalendarTheme.self
        let s = space.s
        let oy = space.oy
        let first = M.firstWeekday(month)
        let count = M.daysIn(month)
        let paperRows = Int(floor((T.heroBottom - T.paperPhaseY) / T.paperPitch)) + 1
        let paperCols = Int(ceil(T.refWidth / T.paperPitch)) + 1
        let bottom = T.gridTop + Double(T.rows) * T.rowHeight
        Canvas { context, size in
            context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(Self.page))
            context.translateBy(x: 0, y: oy)
            context.scaleBy(x: s, y: s)
            // Hero backdrop, from the top of the screen to where the weekday band starts.
            let hero = CGRect(x: 0, y: -oy / s, width: T.refWidth, height: T.heroBottom + oy / s)
            context.fill(Path(hero), with: .linearGradient(
                Gradient(colors: [Self.heroTop, Self.heroBottom]),
                startPoint: CGPoint(x: 0, y: 0), endPoint: CGPoint(x: 0, y: T.heroBottom)
            ))
            for i in 0..<paperCols {
                let x = T.paperPhaseX - T.paperPitch + Double(i) * T.paperPitch - 0.5
                context.fill(Path(CGRect(x: x, y: -oy / s, width: 1, height: T.heroBottom + oy / s)), with: .color(Self.heroLine))
            }
            for i in 0..<paperRows {
                let y = T.paperPhaseY + Double(i) * T.paperPitch - 0.5
                context.fill(Path(CGRect(x: 0, y: y, width: T.refWidth, height: 1)), with: .color(Self.heroLine))
            }
            for i in 0..<(T.rows * 7) {
                let day = i - first + 1
                if day >= 1 && day <= count { continue }
                let rect = CGRect(x: Double(i % 7) * T.columnWidth, y: T.gridTop + Double(i / 7) * T.rowHeight, width: T.columnWidth, height: T.rowHeight)
                context.fill(Path(rect), with: .color(Self.blank))
            }
            for i in 0..<8 {
                let rect = CGRect(x: Double(i) * T.columnWidth - T.gridLine / 2, y: T.weekdayTop, width: T.gridLine, height: bottom - T.weekdayTop)
                context.fill(Path(rect), with: .color(Self.gridLine))
            }
            for i in 0..<(T.rows + 2) {
                let y = i == 0 ? T.weekdayTop : T.gridTop + Double(i - 1) * T.rowHeight
                context.fill(Path(CGRect(x: 0, y: y - T.gridLine / 2, width: T.refWidth, height: T.gridLine)), with: .color(Self.gridLine))
            }
        }
        .frame(width: space.size.width, height: space.size.height)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}
