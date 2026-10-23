import SwiftUI

enum StampDiaryCalendarColors {
    static let weekday = Color(hex: 0x737373)
    static let day = Color.white
    static let year = Color(hex: 0x9A9A9A)
    static let month = Color.white
    static let headerFill = Color.rgba(36, 36, 36, 0.55)
    static let headerIcon = Color.white
    static let selection = Color.white.opacity(0.72)
}

/// Weekday band and the dated cells (the still part): numbers, and accessibility for every day.
struct StampDiaryCalendarDays: View {
    typealias M = StampDiaryCalendarMotion

    var space: StampDiaryCalendarSpace
    var month: M.Month
    var onSelect: (Int) -> Void

    var body: some View {
        let T = StampDiaryCalendarTheme.self
        let s = space.s
        let count = M.daysIn(month)
        let may = M.isMay2026(month)
        ZStack(alignment: .topLeading) {
            HStack(spacing: 0) {
                ForEach(0..<7, id: \.self) { i in
                    Text(T.weekdays[i])
                        .font(.system(size: T.weekdaySize * s, weight: .semibold))
                        .foregroundStyle(StampDiaryCalendarColors.weekday)
                        .frame(width: T.columnWidth * s)
                }
            }
            .frame(width: space.size.width, height: (T.gridTop - T.weekdayTop) * s, alignment: .leading)
            .offset(y: space.y(T.weekdayTop))
            .accessibilityHidden(true)

            ForEach(1...count, id: \.self) { day in
                let cell = M.cellOf(day, month)
                let memory = may ? M.memoryIndex(day, month) : -1
                let label = T.monthNames[month.month - 1] + " \(day), \(month.year)" + (memory >= 0 ? ", memory" : "")
                ZStack {
                    if memory < 0 {
                        Text("\(day)")
                            .font(.system(size: T.daySize * s, weight: .semibold).monospacedDigit())
                            .foregroundStyle(StampDiaryCalendarColors.day)
                    }
                }
                .frame(width: T.columnWidth * s, height: T.rowHeight * s)
                .contentShape(Rectangle())
                .offset(x: Double(cell.column) * T.columnWidth * s, y: space.y(T.gridTop + Double(cell.row) * T.rowHeight))
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(label)
                .accessibilityAddTraits(.isButton)
                .accessibilityAction { onSelect(day) }
            }
        }
        .frame(width: space.size.width, height: space.size.height, alignment: .topLeading)
        .allowsHitTesting(false)
    }
}

/// The day numbers of the emptied cells and the selection rings, driven by the frame.
struct StampDiaryCalendarReveal: View {
    typealias M = StampDiaryCalendarMotion

    var space: StampDiaryCalendarSpace
    var pose: M.Pose
    var month: M.Month
    var emptyDay: Int
    var emptyAlpha: Double

    var body: some View {
        let T = StampDiaryCalendarTheme.self
        let s = space.s
        ZStack(alignment: .topLeading) {
            if M.isMay2026(month) {
                ForEach(0..<StampDiaryCalendarData.memories.count, id: \.self) { index in
                    let mem = StampDiaryCalendarData.memories[index]
                    let c = M.cellOf(mem.day, M.may2026)
                    ZStack {
                        Text("\(mem.day)")
                            .font(.system(size: T.daySize * s, weight: .semibold).monospacedDigit())
                            .foregroundStyle(StampDiaryCalendarColors.day)
                        ring
                    }
                    .frame(width: T.columnWidth * s, height: T.rowHeight * s)
                    .opacity(pose.memory == index ? pose.reveal : 0)
                    .offset(x: Double(c.column) * T.columnWidth * s, y: space.y(T.gridTop + Double(c.row) * T.rowHeight))
                }
            }
            if emptyDay > 0 && emptyDay <= M.daysIn(month) {
                let c = M.cellOf(emptyDay, month)
                ring
                    .frame(width: T.columnWidth * s, height: T.rowHeight * s)
                    .opacity(emptyAlpha)
                    .offset(x: Double(c.column) * T.columnWidth * s, y: space.y(T.gridTop + Double(c.row) * T.rowHeight))
            }
        }
        .frame(width: space.size.width, height: space.size.height, alignment: .topLeading)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private var ring: some View {
        let T = StampDiaryCalendarTheme.self
        return RoundedRectangle(cornerRadius: T.selectionRadius * space.s, style: .continuous)
            .strokeBorder(StampDiaryCalendarColors.selection, lineWidth: T.selectionLine)
            .padding(T.selectionInset * space.s)
    }
}
