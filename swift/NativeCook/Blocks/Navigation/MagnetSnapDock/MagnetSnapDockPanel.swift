import SwiftUI

/*
 The tool panel above the dock. The card stays; its six contents (one per tool) are stacked and
 the selection cross-fades them: the old one lifts 8 pt and fades, the new one rises 8 pt into
 place, the title shrinking from 1.04× as it lands.
 */
struct MagnetSnapDockPanel: View {
    static let toolNames = ["Select", "Pen", "Brush", "Shapes", "Type", "Layers"]
    private static let subtitles = ["Move, scale and rotate", "Bézier paths · round caps", "Gouache round · wet edges", "Rectangle · 12 pt corners", "Grotesk Bold · 48 pt", "6 layers · 2 hidden"]
    fileprivate static let rows: [[(String, String, Double)]] = [
        [("Snap to grid", "8 px", 0.4), ("Nudge", "1 px", 0.15), ("Rotate step", "15°", 0.25)],
        [("Stroke", "2 pt", 0.2), ("Smoothing", "40 %", 0.4), ("Pressure", "Off", 0.02)],
        [("Size", "12 px", 0.48), ("Flow", "68 %", 0.68), ("Wetness", "30 %", 0.3)],
        [("Corner", "12 pt", 0.5), ("Fill", "80 %", 0.8), ("Stroke", "0 pt", 0.02)],
        [("Size", "48 pt", 0.6), ("Tracking", "−1 %", 0.45), ("Leading", "1.1", 0.55)],
        [("Opacity", "100 %", 1), ("Visible", "4 of 6", 0.66), ("Locked", "2", 0.33)],
    ]
    fileprivate static let ink = Color(hex: 0xF1EDE6)
    fileprivate static let inkSoft = Color(hex: 0x9A9CA3)

    let sel: MagnetSnapDockSelection
    let clock: Double

    var body: some View {
        let q = MagnetSnapDockMotion.swapAt(sel, clock)
        let shape = RoundedRectangle(cornerRadius: 24, style: .continuous)
        ZStack(alignment: .topLeading) {
            ForEach(0..<MagnetSnapDockMotion.tools, id: \.self) { i in
                let st = Self.state(i, sel, q)
                if st.0 > 0.001 {
                    MagnetSnapDockPanelContent(
                        index: i,
                        subtitle: Self.subtitles[i],
                        titleScale: i == sel.to ? 1.04 - 0.04 * q : 1
                    )
                    .opacity(st.0)
                    .offset(y: st.1)
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(shape.fill(Color(hex: 0x1F2126)))
        .clipShape(shape)
        .overlay(
            shape.strokeBorder(
                LinearGradient(colors: [.white.opacity(0.06), .clear], startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.05)),
                lineWidth: 1
            )
        )
        .overlay(shape.strokeBorder(.white.opacity(0.1), lineWidth: 0.5))
        .background(
            shape.fill(Color(hex: 0x1F2126))
                .shadow(color: .black.opacity(0.4), radius: 1, y: 1)
                .shadow(color: .black.opacity(0.35), radius: 20, y: 18)
        )
        .allowsHitTesting(false)
        .accessibilityElement()
        .accessibilityLabel("Tool settings")
    }

    private static func state(_ i: Int, _ s: MagnetSnapDockSelection, _ q: Double) -> (Double, Double) {
        if s.from == s.to { return (i == s.to ? 1 : 0, 0) }
        if i == s.to { return (q, 8 * (1 - q)) }
        if i == s.from { return (1 - q, -8 * q) }
        return (0, 0)
    }
}

private struct MagnetSnapDockPanelContent: View {
    let index: Int
    let subtitle: String
    let titleScale: Double

    var body: some View {
        let color = Color(hex: MagnetSnapDockArt.toolColors[index])
        let ink = MagnetSnapDockPanel.ink
        let inkSoft = MagnetSnapDockPanel.inkSoft
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 12) {
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(color)
                    .overlay(
                        RoundedRectangle(cornerRadius: 10, style: .continuous).strokeBorder(
                            LinearGradient(colors: [.white.opacity(0.25), .clear], startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.12)),
                            lineWidth: 1
                        )
                    )
                    .frame(width: 36, height: 36)
                    .shadow(color: .black.opacity(0.3), radius: 3, y: 2)
                VStack(alignment: .leading, spacing: 1) {
                    Text(MagnetSnapDockPanel.toolNames[index])
                        .font(.system(size: 22, weight: .bold))
                        .kerning(-0.3)
                        .foregroundStyle(ink)
                        .scaleEffect(titleScale, anchor: .leading)
                    Text(subtitle)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(inkSoft)
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                HStack(spacing: 6) {
                    Circle().fill(color).frame(width: 6, height: 6)
                    Text("Active")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(ink)
                }
                .padding(.horizontal, 10)
                .frame(height: 26)
                .background(Capsule().fill(.white.opacity(0.06)))
            }
            .padding(.bottom, 2)
            ForEach(MagnetSnapDockPanel.rows[index].indices, id: \.self) { r in
                let row = MagnetSnapDockPanel.rows[index][r]
                VStack(spacing: 6) {
                    HStack {
                        Text(row.0)
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(inkSoft)
                        Spacer(minLength: 0)
                        Text(row.1)
                            .font(.system(size: 14, weight: .semibold).monospacedDigit())
                            .foregroundStyle(ink)
                    }
                    Capsule()
                        .fill(.white.opacity(0.08))
                        .frame(height: 4)
                        .overlay(alignment: .leading) {
                            GeometryReader { geo in
                                Capsule()
                                    .fill(color)
                                    .frame(width: geo.size.width * (row.2 * 100).rounded() / 100, height: 4)
                            }
                        }
                        .clipShape(Capsule())
                }
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .topLeading)
    }
}
