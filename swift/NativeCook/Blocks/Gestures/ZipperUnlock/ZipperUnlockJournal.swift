import SwiftUI

/*
 The journal under the denim (a port of ZipperJournal.tsx): the header, the week, three note
 cards, a memory and the New note button; each comes in 40 ms after the one before it (0.36 s
 ease-out, 18 pt rise, from scale 0.98), driven by the seconds since the halves started to open.
*/

struct ZipperUnlockJournal: View {
    let since: Double
    /// How far the zipper is pulled (0..1): the journal shows through the opening as it widens.
    let pulled: Double
    let insets: EdgeInsets
    let onLock: () -> Void

    private static let paper = Color(hex: 0xF3EEE4)
    private static let ink = Color(hex: 0x1F2433)
    private static let inkSoft = Color.rgba(31, 36, 51, 0.6)
    private static let brown = Color(hex: 0x9A6B2F)
    private static let navy = Color(hex: 0x22365E)

    private static let notes: [(title: String, body: String, meta: String, tint: UInt32)] = [
        ("Things the sea taught me", "Tide pools at seven. Bring the green notebook and the good pencil.", "10:12 · Morning", 0x3A5A8C),
        ("Gift list", "Fountain pen for Mara, a linen scarf, the plum jam from the Saturday market.", "Yesterday · Lists", 0xC2852F),
        ("Half a poem", "Some doors are opened by the hand that waits. Some by the one that knocks twice.", "Thu · Drafts", 0x6E8B68),
    ]

    private static let week: [(day: String, num: Int, notes: Bool, today: Bool)] = [
        ("Mon", 20, true, false), ("Tue", 21, false, false), ("Wed", 22, true, false), ("Thu", 23, true, false),
        ("Fri", 24, true, false), ("Sat", 25, true, true), ("Sun", 26, false, false),
    ]

    var body: some View {
        ZStack(alignment: .bottom) {
            LinearGradient(
                stops: [
                    .init(color: Color(hex: 0xF6F1E8), location: 0),
                    .init(color: Self.paper, location: 0.5),
                    .init(color: Color(hex: 0xE9E1D3), location: 1),
                ],
                startPoint: .top, endPoint: .bottom
            )
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 20) {
                    header.reveal(since, pulled, 0)
                    weekRow.reveal(since, pulled, 1)
                    VStack(spacing: 12) {
                        ForEach(0..<Self.notes.count, id: \.self) { i in
                            card(Self.notes[i]).reveal(since, pulled, i + 2)
                        }
                    }
                    memory.reveal(since, pulled, 5)
                }
                .padding(.horizontal, 20)
                .padding(.top, insets.top + 24)
                .padding(.bottom, insets.bottom + 96)
            }
            newNote
                .reveal(since, pulled, 6)
                .padding(.bottom, insets.bottom + 20)
        }
    }

    private var header: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 2) {
                Text("SAT 25 OCT")
                    .font(.system(size: 12, weight: .bold))
                    .kerning(1.4)
                    .foregroundStyle(Self.brown)
                Text("Pocket")
                    .font(.system(size: 34, weight: .bold))
                    .kerning(-0.5)
                    .foregroundStyle(Self.ink)
                Text("3 notes · last edit 10:12")
                    .font(.system(size: 15, weight: .medium).monospacedDigit())
                    .foregroundStyle(Self.inkSoft)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Button(action: onLock) {
                ZipperUnlockLockGlyph()
                    .frame(width: 18, height: 18)
                    .frame(width: 44, height: 44)
                    .background(
                        Circle()
                            .fill(Color(hex: 0xFBF8F2))
                            .shadow(color: Color.rgba(60, 44, 20, 0.10), radius: 9, y: 8)
                            .shadow(color: Color.rgba(60, 44, 20, 0.18), radius: 1, y: 1)
                    )
                    .contentShape(Circle().inset(by: -8))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Lock the journal")
        }
    }

    private var weekRow: some View {
        HStack(spacing: 0) {
            ForEach(0..<Self.week.count, id: \.self) { i in
                let d = Self.week[i]
                if i > 0 { Spacer(minLength: 0) }
                VStack(spacing: 2) {
                    Text(d.day)
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(d.today ? Self.paper : Self.inkSoft)
                    Text("\(d.num)")
                        .font(.system(size: 17, weight: .semibold).monospacedDigit())
                        .foregroundStyle(d.today ? Self.paper : Self.ink)
                    Circle()
                        .fill(d.today ? Self.paper : Color(hex: 0xC2852F))
                        .frame(width: 4, height: 4)
                        .opacity(d.notes ? 1 : 0)
                }
                .frame(width: 44, height: 64)
                .background(
                    Capsule()
                        .fill(d.today ? Self.navy : Color.rgba(80, 60, 30, 0.05))
                        .shadow(color: d.today ? Color.rgba(20, 30, 60, 0.2) : .clear, radius: 9, y: 8)
                        .shadow(color: d.today ? Color.rgba(20, 30, 60, 0.3) : .clear, radius: 1, y: 1)
                )
            }
        }
    }

    private func card(_ n: (title: String, body: String, meta: String, tint: UInt32)) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 8) {
                Circle().fill(Color(hex: n.tint)).frame(width: 8, height: 8)
                Text(n.title)
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Self.ink)
                    .lineLimit(1)
            }
            Text(n.body)
                .font(.system(size: 15))
                .lineSpacing(3)
                .foregroundStyle(Color.rgba(31, 36, 51, 0.78))
                .lineLimit(2)
            Text(n.meta)
                .font(.system(size: 12, weight: .medium).monospacedDigit())
                .foregroundStyle(Self.inkSoft)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(Color(hex: 0xFCFAF5))
                .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(Color.rgba(80, 60, 30, 0.12), lineWidth: 0.5))
                .shadow(color: Color.rgba(60, 44, 20, 0.10), radius: 14, y: 12)
                .shadow(color: Color.rgba(60, 44, 20, 0.14), radius: 1, y: 1)
        )
    }

    private var memory: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("A YEAR AGO TODAY")
                .font(.system(size: 11, weight: .bold))
                .kerning(1.2)
                .foregroundStyle(Self.brown)
            Text("\u{201C}Rain on the ferry, and the best soup I have ever had in a paper cup.\u{201D}")
                .font(.system(size: 16).italic())
                .lineSpacing(3)
                .foregroundStyle(Self.ink)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(Color.rgba(194, 133, 47, 0.10))
                .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(Color.rgba(194, 133, 47, 0.3), lineWidth: 0.5))
        )
    }

    private var newNote: some View {
        HStack(spacing: 8) {
            Text("+")
                .font(.system(size: 20, weight: .medium))
                .offset(y: -1)
            Text("New note")
                .font(.system(size: 16, weight: .semibold))
        }
        .foregroundStyle(Self.paper)
        .padding(.horizontal, 24)
        .frame(height: 48)
        .background(
            Capsule()
                .fill(Self.navy)
                .shadow(color: Color.rgba(20, 30, 60, 0.22), radius: 11, y: 10)
                .shadow(color: Color.rgba(20, 30, 60, 0.3), radius: 1, y: 1)
        )
    }
}

/// The padlock on the lock button.
private struct ZipperUnlockLockGlyph: View {
    var body: some View {
        Canvas { ctx, _ in
            let ink = Color(hex: 0x1F2433)
            ctx.fill(Path(roundedRect: CGRect(x: 3.5, y: 8, width: 11, height: 8), cornerRadius: 2.2), with: .color(ink))
            var shackle = Path()
            shackle.move(to: CGPoint(x: 6, y: 8))
            shackle.addLine(to: CGPoint(x: 6, y: 5.8))
            shackle.addArc(center: CGPoint(x: 9, y: 5.8), radius: 3, startAngle: .degrees(180), endAngle: .degrees(0), clockwise: false)
            shackle.addLine(to: CGPoint(x: 12, y: 8))
            ctx.stroke(shackle, with: .color(ink), style: StrokeStyle(lineWidth: 1.8, lineCap: .round))
        }
    }
}

private extension View {
    /// The journal's entrance for element i: fade, an 18 pt rise and a 2 % grow.
    func reveal(_ since: Double, _ pulled: Double, _ i: Int) -> some View {
        let r = max(ZipperUnlockMotion.revealOf(since, i), ZipperUnlockMotion.smooth(pulled * 4))
        return opacity(r)
            .scaleEffect(0.98 + 0.02 * r)
            .offset(y: (1 - r) * 18)
    }
}
