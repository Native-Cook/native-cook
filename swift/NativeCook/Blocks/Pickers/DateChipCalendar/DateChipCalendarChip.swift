import SwiftUI

/// The reusable piece: a date chip that grows into an inline month grid and folds back. The range is
/// one rounded shape per month; the page around it only reads `f.open` to push its content down.
struct DateChipCalendarChip: View {
    @Bindable var model: DateChipCalendarModel
    let f: DateChipCalendarFrame
    let width: Double
    var placeholder = "Add dates"
    var caption = "Dates"

    private typealias M = DateChipCalendarMotion

    private var pageWidth: Double { width - M.gridPad * 2 }
    private var cell: Double { pageWidth / 7 }

    var body: some View {
        let o = f.open
        let height = M.mix(o, M.chipHeight, M.openHeight)
        let radius = M.mix(M.clamp01(o), M.radiusClosed, M.radiusOpen)
        let shape = RoundedRectangle(cornerRadius: radius, style: .continuous)
        return ZStack(alignment: .topLeading) {
            chip
            trailing
            grid
                .frame(width: width, height: M.openHeight - M.chipHeight, alignment: .topLeading)
                .opacity(M.clamp01(f.reveal * 5))
                .offset(y: M.chipHeight)
                .allowsHitTesting(model.isOpen)
        }
        .frame(width: width, height: max(height, 0), alignment: .topLeading)
        .clipShape(shape)
        .background(
            ZStack {
                shape.fill(M.card)
                    .shadow(color: Color.rgba(16, 40, 48, 0.10), radius: 13, y: 10)
                shape.fill(M.card)
                    .shadow(color: Color.rgba(16, 40, 48, 0.10), radius: 1, y: 1)
                shape.strokeBorder(LinearGradient(colors: [.white.opacity(0.9), .clear], startPoint: .top,
                                                  endPoint: UnitPoint(x: 0.5, y: min(0.05, 3 / max(height, 1)))),
                                   lineWidth: 1)
            }
        )
        .scaleEffect(1 - 0.015 * f.press)
    }

    // MARK: Chip

    private var chip: some View {
        Button { model.isOpen ? model.fold() : model.openCalendar() } label: {
            HStack(spacing: 0) {
                calendarIcon
                VStack(alignment: .leading, spacing: 0) {
                    Text(caption)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(M.mute)
                    VStack(alignment: .leading, spacing: 0) {
                        Text(placeholder)
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundStyle(M.mute)
                            .frame(height: M.line)
                        Text(model.label)
                            .font(.system(size: 17, weight: .semibold).monospacedDigit())
                            .foregroundStyle(M.ink)
                            .frame(height: M.line)
                    }
                    .lineLimit(1)
                    .offset(y: -M.clamp01(f.roll) * M.line)
                    .frame(height: M.line, alignment: .top)
                    .clipped()
                }
                .padding(.leading, 12)
                Spacer(minLength: 0)
            }
            .padding(.leading, 16)
            .padding(.trailing, 88)
            .frame(width: width, height: M.chipHeight)
            .contentShape(Rectangle())
        }
        .buttonStyle(DateChipCalendarPlainButton())
        .accessibilityLabel(model.hasRange ? "\(caption), \(model.label)" : "\(caption), \(placeholder)")
        .accessibilityValue(model.isOpen ? "expanded" : "collapsed")
    }

    private var calendarIcon: some View {
        VStack(spacing: 0) {
            Rectangle().fill(M.tide).frame(width: 36, height: 10)
            RoundedRectangle(cornerRadius: 2.5, style: .continuous).fill(M.tide)
                .frame(width: 8, height: 8)
                .padding(.top, 6)
            Spacer(minLength: 0)
        }
        .frame(width: 36, height: 36)
        .background(M.tideSoft)
        .clipShape(RoundedRectangle(cornerRadius: 11, style: .continuous))
    }

    private var trailing: some View {
        let closed = 1 - M.clamp01(f.open * 2.5)
        return ZStack(alignment: .trailing) {
            // The chevron while empty.
            Path { p in
                p.move(to: CGPoint(x: 8, y: 0)); p.addLine(to: CGPoint(x: 8, y: 8)); p.addLine(to: CGPoint(x: 0, y: 8))
            }
            .stroke(M.mute, style: StrokeStyle(lineWidth: 2, lineCap: .butt, lineJoin: .miter))
            .frame(width: 9, height: 9)
            .rotationEffect(.degrees(45))
            .offset(y: -2)
            .padding(.trailing, 8)
            .opacity(closed * (1 - f.hasValue))
            .allowsHitTesting(false)

            // The clear button once there are dates.
            Button { model.clear() } label: {
                ZStack {
                    Circle().fill(M.chipFill)
                    Capsule().fill(M.ink).frame(width: 12, height: 1.8).rotationEffect(.degrees(45))
                    Capsule().fill(M.ink).frame(width: 12, height: 1.8).rotationEffect(.degrees(-45))
                }
                .frame(width: 30, height: 30)
                .contentShape(Rectangle().inset(by: -10))
            }
            .buttonStyle(DateChipCalendarPlainButton())
            .accessibilityLabel("Clear dates")
            .opacity(closed * f.hasValue)
            .allowsHitTesting(model.hasRange && !model.isOpen)

            // Done while open.
            Button { model.fold() } label: {
                Text("Done")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(M.card)
                    .padding(.horizontal, 14)
                    .frame(height: 32)
                    .background(Capsule(style: .continuous).fill(M.tide))
                    .contentShape(Rectangle().inset(by: -8))
            }
            .buttonStyle(DateChipCalendarPlainButton())
            .accessibilityLabel("Done")
            .opacity(M.clamp01((f.open - 0.4) * 2.5))
            .scaleEffect(0.92 + 0.08 * M.clamp01(f.open), anchor: .trailing)
            .allowsHitTesting(model.isOpen)
        }
        .frame(width: 72, height: M.chipHeight, alignment: .trailing)
        .offset(x: width - 12 - 72)
    }

    // MARK: Grid

    private var grid: some View {
        let pages = [model.page - 1, model.page, model.page + 1].filter { $0 >= 0 && $0 < model.months.count }
        return ZStack(alignment: .topLeading) {
            ZStack(alignment: .topLeading) {
                ForEach(pages, id: \.self) { index in
                    page(model.months[index])
                        .offset(x: Double(index) * pageWidth - f.page * pageWidth)
                }
            }
            .frame(width: pageWidth, height: M.openHeight - M.chipHeight, alignment: .topLeading)
            .clipped()
            .offset(x: M.gridPad)

            HStack(spacing: 4) {
                arrow(left: true, enabled: model.page > 0) { model.changePage(model.page - 1) }
                    .accessibilityLabel("Previous month")
                arrow(left: false, enabled: model.page < model.months.count - 1) { model.changePage(model.page + 1) }
                    .accessibilityLabel("Next month")
            }
            .frame(height: 32)
            .offset(x: width - M.gridPad - 68, y: 4)
        }
        .frame(width: width, height: M.openHeight - M.chipHeight, alignment: .topLeading)
        .contentShape(Rectangle())
        .gesture(dayGesture)
    }

    private func arrow(left: Bool, enabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            ZStack {
                Circle().fill(M.chipFill)
                Path { p in
                    p.move(to: CGPoint(x: 0, y: 0)); p.addLine(to: CGPoint(x: 7, y: 0)); p.addLine(to: CGPoint(x: 7, y: 7))
                }
                .stroke(M.ink, lineWidth: 2)
                .frame(width: 8, height: 8)
                .rotationEffect(.degrees(left ? 225 : 45))
                .offset(x: left ? 1.5 : -1.5)
            }
            .frame(width: 32, height: 32)
            .contentShape(Rectangle().inset(by: -6))
        }
        .buttonStyle(DateChipCalendarPlainButton())
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.4)
    }

    private var dayGesture: some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .local)
            .onChanged { value in
                let travel = hypot(value.translation.width, value.translation.height)
                if !panning {
                    guard travel >= 8 else { return }
                    panning = true
                    model.panBegan(startX: value.startLocation.x, startY: value.startLocation.y,
                                   dx: value.translation.width, dy: value.translation.height)
                }
                model.panMoved(x: value.location.x, y: value.location.y, dx: value.translation.width)
            }
            .onEnded { value in
                if panning {
                    model.panEnded(vx: value.velocity.width)
                } else {
                    model.tap(value.location.x, value.location.y)
                }
                panning = false
            }
    }

    @State private var panning = false

    private func page(_ month: DateChipCalendarMonth) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(month.title)
                .font(.system(size: 17, weight: .bold))
                .foregroundStyle(M.ink)
                .padding(.leading, 4)
                .frame(height: M.titleRow)
            HStack(spacing: 0) {
                ForEach(0..<7, id: \.self) { i in
                    Text(DateChipCalendarMonth.weekdays[i])
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(M.mute)
                        .frame(width: cell)
                }
            }
            .frame(height: M.weekRow)
            days(month)
                .frame(width: pageWidth, height: M.dayRow * Double(M.gridRows), alignment: .topLeading)
        }
        .frame(width: pageWidth, alignment: .topLeading)
    }

    private func days(_ month: DateChipCalendarMonth) -> some View {
        ZStack(alignment: .topLeading) {
            DateChipCalendarRange.path(start: f.start, head: f.head, month: month, cell: cell,
                                       row: M.dayRow, inset: M.bandInset)
                .fill(M.tideSoft)
            ForEach(0..<month.days, id: \.self) { d in
                let place = d + month.offset
                let col = place % 7, row = place / 7
                let value = month.first + d
                let delay = Double(col + row) / 11 * M.cellSpread
                let u = M.seg(f.reveal, delay, M.cellSpan)
                let muted = value < DateChipCalendarStay.today
                let blocked = DateChipCalendarStay.blocked.contains(value)
                Text("\(d + 1)")
                    .font(.system(size: 16, weight: .medium).monospacedDigit())
                    .foregroundStyle(muted || blocked ? M.faint : M.ink)
                    .strikethrough(blocked, color: M.faint)
                    .frame(width: cell, height: M.dayRow)
                    .opacity(u)
                    .offset(x: Double(col) * cell, y: Double(row) * M.dayRow + (1 - u) * M.cellRise)
            }
            caps(month)
        }
    }

    @ViewBuilder
    private func caps(_ month: DateChipCalendarMonth) -> some View {
        let first = Double(month.first), lastPlusOne = Double(month.last + 1)
        if f.start >= first, f.start <= Double(month.last) {
            cap(day: f.start, month: month, amount: f.capStart)
        }
        if f.start >= 0, f.head >= first, f.head < lastPlusOne, abs(f.head - f.start) > 0.5 {
            cap(day: f.head, month: month, amount: f.capEnd)
        }
    }

    /// A cap's place in the grid; a head between two weeks stays on the last column, then jumps to the next row.
    private func cap(day: Double, month: DateChipCalendarMonth, amount: Double) -> some View {
        let index = day - Double(month.first) + Double(month.offset)
        var row = floor(index / 7)
        var col = index - row * 7
        if col > 6.5 { row += 1; col = 0 } else if col > 6 { col = 6 }
        let number = Int(day.rounded()) - month.first + 1
        return Text(number > 0 ? "\(number)" : "")
            .font(.system(size: 16, weight: .bold).monospacedDigit())
            .foregroundStyle(M.card)
            .frame(width: M.cap, height: M.cap)
            .background(
                ZStack {
                    Circle().fill(M.tide).shadow(color: Color.rgba(16, 60, 70, 0.22), radius: 5, y: 4)
                    Circle().fill(M.tide).shadow(color: Color.rgba(16, 60, 70, 0.30), radius: 1, y: 1)
                }
            )
            .scaleEffect(0.7 + 0.3 * amount)
            .opacity(M.clamp01(amount * 2))
            .offset(x: col * cell + (cell - M.cap) / 2, y: row * M.dayRow + (M.dayRow - M.cap) / 2)
            .allowsHitTesting(false)
    }
}

struct DateChipCalendarPlainButton: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View { configuration.label }
}
