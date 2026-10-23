import SwiftUI

/// The pin's smoked glass: a dark blur of what is behind, under a dark tint. Fills its parent.
struct StampDiaryCalendarGlass: View {
    var fill: Color = StampDiaryCalendarColors.headerFill

    var body: some View {
        ZStack {
            Rectangle().fill(.ultraThinMaterial).environment(\.colorScheme, .dark)
            Rectangle().fill(fill)
        }
        .allowsHitTesting(false)
    }
}

private struct StampDiaryCalendarPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.scaleEffect(configuration.isPressed ? 0.97 : 1)
    }
}

/// The two header circles: close (x) at the left, month menu (...) at the right.
struct StampDiaryCalendarHeader: View {
    var space: StampDiaryCalendarSpace
    var onClose: () -> Void
    var onMonth: () -> Void

    var body: some View {
        let T = StampDiaryCalendarTheme.self
        let s = space.s
        let top = space.y(T.headerCenterY - T.headerSize / 2)
        ZStack(alignment: .topLeading) {
            Button(action: onClose) { circle(close) }
                .buttonStyle(StampDiaryCalendarPressStyle())
                .accessibilityLabel("Close")
                .offset(x: (T.headerCenterInset - T.headerSize / 2) * s, y: top)
            Button(action: onMonth) { circle(dots) }
                .buttonStyle(StampDiaryCalendarPressStyle())
                .accessibilityLabel("Choose month")
                .offset(x: (T.refWidth - T.headerCenterInset - T.headerSize / 2) * s, y: top)
        }
        .frame(width: space.size.width, height: space.size.height, alignment: .topLeading)
    }

    private func circle<Icon: View>(_ icon: Icon) -> some View {
        let T = StampDiaryCalendarTheme.self
        let size = T.headerSize * space.s
        return ZStack {
            StampDiaryCalendarGlass()
            icon
        }
        .frame(width: size, height: size)
        .clipShape(RoundedRectangle(cornerRadius: T.headerRadius * space.s, style: .continuous))
        .contentShape(Rectangle().inset(by: -4 * space.s))
    }

    private var close: some View {
        let T = StampDiaryCalendarTheme.self
        let s = space.s
        let g = T.closeGlyph / 2
        return Path { path in
            path.move(to: CGPoint(x: (22 - g) * s, y: (22 - g) * s))
            path.addLine(to: CGPoint(x: (22 + g) * s, y: (22 + g) * s))
            path.move(to: CGPoint(x: (22 + g) * s, y: (22 - g) * s))
            path.addLine(to: CGPoint(x: (22 - g) * s, y: (22 + g) * s))
        }
        .stroke(StampDiaryCalendarColors.headerIcon, style: StrokeStyle(lineWidth: T.closeStroke * s, lineCap: .round))
        .frame(width: T.headerSize * s, height: T.headerSize * s)
    }

    private var dots: some View {
        let T = StampDiaryCalendarTheme.self
        let s = space.s
        return Path { path in
            for i in [-1.0, 0, 1] {
                let r = T.dot / 2 * s
                path.addEllipse(in: CGRect(x: (22 + i * T.dotGap) * s - r, y: 21.5 * s - r, width: 2 * r, height: 2 * r))
            }
        }
        .fill(StampDiaryCalendarColors.headerIcon)
        .frame(width: T.headerSize * s, height: T.headerSize * s)
    }
}

/// Year and month, right aligned on the pin's baselines. Under the stamps: a stamp may fly over it.
struct StampDiaryCalendarTitle: View {
    typealias M = StampDiaryCalendarMotion

    var space: StampDiaryCalendarSpace
    var month: M.Month
    var onMonth: () -> Void

    var body: some View {
        let T = StampDiaryCalendarTheme.self
        let s = space.s
        let name = T.monthNames[month.month - 1]
        // Each piece sits in an overlay of a fixed-size clear view, so the stack never re-normalises
        // the offsets (a baseline guide moves the text above the stack's top).
        Color.clear
            .frame(width: space.size.width, height: space.size.height)
            .overlay(alignment: .topTrailing) {
                Text(String(month.year))
                    .font(.system(size: T.yearSize * s, weight: .regular))
                    .foregroundStyle(StampDiaryCalendarColors.year)
                    .alignmentGuide(.top) { $0[.firstTextBaseline] }
                    .padding(.trailing, T.titleInset * s)
                    .offset(y: space.y(T.yearBaseline))
                    .accessibilityHidden(true)
            }
            .overlay(alignment: .topTrailing) {
                Text(name)
                    .font(.system(size: T.monthSize * s, weight: .bold))
                    .foregroundStyle(StampDiaryCalendarColors.month)
                    .alignmentGuide(.top) { $0[.firstTextBaseline] }
                    .padding(.trailing, T.titleInset * s)
                    .offset(y: space.y(T.monthBaseline))
                    .accessibilityHidden(true)
            }
            .overlay(alignment: .topTrailing) {
                Button(action: onMonth) {
                    Color.clear.frame(width: 150 * s, height: (T.monthBaseline + 10 - (T.yearBaseline - T.yearSize)) * s)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(name + " \(month.year). Choose month.")
                .padding(.trailing, T.titleInset * s)
                .offset(y: space.y(T.yearBaseline - T.yearSize))
            }
    }
}

/// The month menu (title or ...): a small smoked-glass card under the ... circle with the
/// neighbouring months and, away from May 2026, a way back. Reduce Motion: a fade only.
struct StampDiaryCalendarMenu: View {
    typealias M = StampDiaryCalendarMotion

    var space: StampDiaryCalendarSpace
    var month: M.Month
    var open: Bool
    var reduceMotion: Bool
    var onClose: () -> Void
    var onPick: (M.Month) -> Void

    private var items: [(label: String, a11y: String, action: () -> Void)] {
        let T = StampDiaryCalendarTheme.self
        let prev = M.addMonths(month, -1)
        let next = M.addMonths(month, 1)
        func name(_ m: M.Month) -> String { T.monthNames[m.month - 1] + " \(m.year)" }
        var list: [(label: String, a11y: String, action: () -> Void)] = [
            ("Previous month", "Previous month, " + name(prev), { onPick(prev) }),
            ("Next month", "Next month, " + name(next), { onPick(next) }),
        ]
        if !M.isMay2026(month) { list.append(("Back to May 2026", "Back to May 2026", { onPick(M.may2026) })) }
        list.append(("Cancel", "Cancel", onClose))
        return list
    }

    private static let fill = Color.rgba(30, 30, 30, 0.6)
    private static let divider = Color.white.opacity(0.1)

    var body: some View {
        let T = StampDiaryCalendarTheme.self
        let s = space.s
        let top = space.y(T.headerCenterY + T.headerSize / 2 + T.menuGap)
        let right = (T.headerCenterInset - T.headerSize / 2) * s
        ZStack(alignment: .topTrailing) {
            Color.clear
                .contentShape(Rectangle())
                .onTapGesture(perform: onClose)
                .accessibilityLabel("Close menu")
                .accessibilityAddTraits(.isButton)
            VStack(spacing: 0) {
                ForEach(Array(items.enumerated()), id: \.offset) { i, item in
                    Button(action: item.action) {
                        Text(item.label)
                            .font(.system(size: T.menuTextSize * s, weight: item.label == "Cancel" ? .regular : .semibold))
                            .foregroundStyle(.white)
                            .padding(.horizontal, T.menuPadX * s)
                            .frame(maxWidth: .infinity, minHeight: T.menuRow * s, maxHeight: T.menuRow * s, alignment: .leading)
                            .overlay(alignment: .top) {
                                if i > 0 { Rectangle().fill(Self.divider).frame(height: 1 / UIScreen.main.scale) }
                            }
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(StampDiaryCalendarMenuRowStyle())
                    .accessibilityLabel(item.a11y)
                }
            }
            .frame(width: T.menuWidth * s)
            .background { StampDiaryCalendarGlass(fill: Self.fill) }
            .clipShape(RoundedRectangle(cornerRadius: T.menuRadius * s, style: .continuous))
            .scaleEffect(reduceMotion ? 1 : (open ? 1 : T.menuScaleFrom), anchor: .topTrailing)
            .opacity(open ? 1 : 0)
            .animation(.timingCurve(0.23, 1, 0.32, 1, duration: open ? T.menuInSeconds : T.menuOutSeconds), value: open)
            .padding(.top, top)
            .padding(.trailing, right)
            .accessibilityAddTraits(.isModal)
        }
        .frame(width: space.size.width, height: space.size.height, alignment: .topTrailing)
        .allowsHitTesting(open)
        .accessibilityHidden(!open)
    }
}

private struct StampDiaryCalendarMenuRowStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.opacity(configuration.isPressed ? 0.6 : 1)
    }
}
