// Louver Theme Switch — the demo's screen: Almanac's settings, drawn for one theme.
//
// Plain SwiftUI views, so the louver has real content to snapshot; the segmented control and the
// switches are the system's own.

import SwiftUI

struct LouverThemeSwitchPrefs: Hashable {
    var weekNumbers = true
    var declined = false
    var travel = true
}

struct LouverThemeSwitchAlmanac: View {
    let screen: LouverScreen
    @Binding var prefs: LouverThemeSwitchPrefs

    private struct Palette {
        let bg, surface, ink, mute, line, accent, onAccent, teal: Color
        let shadowFar, shadowNear, inset: Color
    }

    private static let light = Palette(
        bg: Color(hex: 0xF3F0E8), surface: Color(hex: 0xFFFDF8), ink: Color(hex: 0x1F1D19), mute: Color(hex: 0x7F796D),
        line: .rgba(40, 36, 28, 0.11), accent: Color(hex: 0xC4513A), onAccent: Color(hex: 0xFFF8F2), teal: Color(hex: 0x2F7F78),
        shadowFar: .rgba(66, 54, 36, 0.10), shadowNear: .rgba(66, 54, 36, 0.12), inset: .rgba(255, 255, 255, 0.9)
    )
    private static let dark = Palette(
        bg: Color(hex: 0x14151B), surface: Color(hex: 0x202129), ink: Color(hex: 0xECE9E2), mute: Color(hex: 0x918F99),
        line: .rgba(236, 233, 226, 0.10), accent: Color(hex: 0xEC7E63), onAccent: Color(hex: 0x2A120C), teal: Color(hex: 0x62BDB3),
        shadowFar: .rgba(0, 0, 0, 0.38), shadowNear: .rgba(0, 0, 0, 0.4), inset: .rgba(255, 255, 255, 0.07)
    )

    private static let week: [(String, Int)] = [("M", 23), ("T", 24), ("W", 25), ("T", 26), ("F", 27), ("S", 28), ("S", 29)]
    private static let today = 24
    private static let agenda: [(title: String, time: String, accent: Bool)] = [
        ("Design review", "10:30", true),
        ("Lunch with Noor", "12:45", false),
    ]

    private var c: Palette { screen.theme == .dark ? Self.dark : Self.light }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let height = proxy.size.height + insets.top + insets.bottom
            let compact = height < 740
            content(compact: compact)
                .padding(.horizontal, 20)
                .padding(.top, insets.top + (compact ? 6 : 14))
                .padding(.bottom, max(insets.bottom, 12) + 4)
                .frame(width: proxy.size.width, height: height, alignment: .top)
                .offset(y: -insets.top)
        }
        .background(c.bg.ignoresSafeArea())
        .environment(\.colorScheme, screen.theme == .dark ? .dark : .light)
    }

    private func content(compact: Bool) -> some View {
        let rowH: Double = compact ? 40 : 50
        return VStack(alignment: .leading, spacing: 0) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Almanac")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(c.mute)
                Text("Settings")
                    .font(.system(size: compact ? 28 : 34, weight: .bold))
                    .tracking(-0.6)
                    .foregroundStyle(c.ink)
                    .frame(height: compact ? 33 : 40)
            }
            Spacer(minLength: 0)
            preview(compact: compact)
            Spacer(minLength: 0)
            VStack(alignment: .leading, spacing: 0) {
                section("Appearance")
                Picker("Appearance", selection: Binding(get: { screen.theme }, set: { screen.select($0) })) {
                    Text("Light").tag(LouverTheme.light)
                    Text("Dark").tag(LouverTheme.dark)
                }
                .pickerStyle(.segmented)
                .labelsHidden()
                .id(screen.resetKey)
                .louverControl(screen)
            }
            Spacer(minLength: 0)
            VStack(alignment: .leading, spacing: 0) {
                section("Calendar")
                card {
                    valueRow("Start week on", "Monday", rowH)
                    valueRow("Default length", "45 min", rowH)
                    toggleRow("Week numbers", \.weekNumbers, rowH)
                    toggleRow("Show declined", \.declined, rowH, last: true)
                }
            }
            Spacer(minLength: 0)
            VStack(alignment: .leading, spacing: 0) {
                section("Alerts")
                card {
                    valueRow("Morning brief", "07:30", rowH)
                    toggleRow("Travel time", \.travel, rowH, last: true)
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    private func preview(compact: Bool) -> some View {
        card(vertical: compact ? 10 : 16) {
            HStack(alignment: .firstTextBaseline) {
                Text("November")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(c.ink)
                Spacer(minLength: 0)
                Text("Week 48")
                    .font(.system(size: 13, weight: .medium).monospacedDigit())
                    .foregroundStyle(c.mute)
            }
            HStack(spacing: 0) {
                ForEach(Array(Self.week.enumerated()), id: \.offset) { i, entry in
                    if i > 0 { Spacer(minLength: 0) }
                    let on = entry.1 == Self.today
                    VStack(spacing: 4) {
                        Text(entry.0)
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(c.mute)
                        Text("\(entry.1)")
                            .font(.system(size: 15, weight: .semibold).monospacedDigit())
                            .foregroundStyle(on ? c.onAccent : i > 4 ? c.mute : c.ink)
                            .frame(width: 32, height: 32)
                            .background(Circle().fill(on ? c.accent : .clear))
                    }
                    .frame(width: 36)
                }
            }
            .padding(.top, 10)
            .padding(.bottom, 8)
            ForEach(Array(Self.agenda.prefix(compact ? 1 : 2).enumerated()), id: \.offset) { _, e in
                HStack(spacing: 0) {
                    RoundedRectangle(cornerRadius: 1.5)
                        .fill(e.accent ? c.accent : c.teal)
                        .frame(width: 3, height: 16)
                        .padding(.trailing, 10)
                    Text(e.title)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(c.ink)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Text(e.time)
                        .font(.system(size: 13, weight: .medium).monospacedDigit())
                        .foregroundStyle(c.mute)
                }
                .frame(height: 26)
            }
        }
    }

    private func section(_ title: String) -> some View {
        Text(title)
            .font(.system(size: 13, weight: .semibold))
            .foregroundStyle(c.mute)
            .padding(.leading, 4)
            .padding(.bottom, 6)
    }

    /// a rounded card with the RN box shadow: two drop shadows and a 1 pt highlight inside the top edge
    private func card<Content: View>(vertical: Double = 0, @ViewBuilder _ content: () -> Content) -> some View {
        let shape = RoundedRectangle(cornerRadius: 18, style: .continuous)
        return VStack(alignment: .leading, spacing: 0, content: content)
            .padding(.horizontal, 16)
            .padding(.vertical, vertical)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                ZStack {
                    shape.fill(c.inset)
                    shape.fill(c.surface).offset(y: 1)
                }
                .clipShape(shape)
                .background(shape.fill(c.surface).shadow(color: c.shadowNear, radius: 1.5, y: 1))
                .background(shape.fill(c.surface).shadow(color: c.shadowFar, radius: 12, y: 10))
            )
    }

    private func valueRow(_ label: String, _ value: String, _ height: Double, last: Bool = false) -> some View {
        HStack(spacing: 0) {
            Text(label)
                .font(.system(size: 16))
                .foregroundStyle(c.ink)
                .frame(maxWidth: .infinity, alignment: .leading)
            Text(value)
                .font(.system(size: 16).monospacedDigit())
                .foregroundStyle(c.mute)
                .padding(.trailing, 8)
            Chevron()
                .stroke(c.mute, style: StrokeStyle(lineWidth: 1.6, lineCap: .butt, lineJoin: .miter))
                .frame(width: 7, height: 7)
                .rotationEffect(.degrees(45))
                .padding(.trailing, 2)
        }
        .frame(height: height)
        .overlay(alignment: .bottom) { if !last { hairline } }
    }

    private func toggleRow(_ label: String, _ key: WritableKeyPath<LouverThemeSwitchPrefs, Bool>, _ height: Double, last: Bool = false) -> some View {
        HStack(spacing: 0) {
            Text(label)
                .font(.system(size: 16))
                .foregroundStyle(c.ink)
                .frame(maxWidth: .infinity, alignment: .leading)
            Toggle(label, isOn: Binding(get: { prefs[keyPath: key] }, set: { prefs[keyPath: key] = $0 }))
                .labelsHidden()
                .tint(c.accent)
                .id(screen.resetKey)
        }
        .frame(height: height)
        .overlay(alignment: .bottom) { if !last { hairline } }
    }

    private var hairline: some View {
        Rectangle().fill(c.line).frame(height: 1 / 3)
    }
}

/// the RN chevron: the top and right borders of a 7 pt square (turned 45° by the caller)
private struct Chevron: Shape {
    func path(in rect: CGRect) -> Path {
        let inset = 0.8
        var p = Path()
        p.move(to: CGPoint(x: rect.minX, y: rect.minY + inset))
        p.addLine(to: CGPoint(x: rect.maxX - inset, y: rect.minY + inset))
        p.addLine(to: CGPoint(x: rect.maxX - inset, y: rect.maxY))
        return p
    }
}
