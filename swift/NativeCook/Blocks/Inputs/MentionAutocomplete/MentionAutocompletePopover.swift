import SwiftUI

/// A drawn avatar: initials on a three-stop gradient disc, with a top highlight and an optional
/// presence dot.
struct MentionAutocompleteAvatar: View, Equatable {
    let person: MentionAutocompletePerson
    let size: CGFloat
    var online = false
    var ring = Color(hex: 0xFBFAF7)

    var body: some View {
        let dot = (size * 0.3).rounded()
        ZStack(alignment: .bottomTrailing) {
            ZStack {
                Circle().fill(LinearGradient(stops: [
                    .init(color: Color(hex: person.colors[0]), location: 0),
                    .init(color: Color(hex: person.colors[1]), location: 0.55),
                    .init(color: Color(hex: person.colors[2]), location: 1),
                ], startPoint: UnitPoint(x: 0.15, y: 0), endPoint: UnitPoint(x: 0.85, y: 1)))
                Circle()
                    .strokeBorder(LinearGradient(colors: [.white.opacity(0.35), .white.opacity(0)], startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.25)), lineWidth: 1)
                Text(person.initials)
                    .font(.system(size: size * 0.38, weight: .semibold))
                    .kerning(0.2)
                    .foregroundStyle(Color(hex: 0xFFFDF8))
            }
            .frame(width: size, height: size)
            .shadow(color: Color.rgba(30, 26, 20, 0.18), radius: 1, y: 1)
            if online {
                Circle()
                    .fill(Color(hex: 0x3DAA63))
                    .overlay(Circle().strokeBorder(ring, lineWidth: 2))
                    .frame(width: dot, height: dot)
                    .offset(x: 1, y: 1)
            }
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

/*
 The people list that grows out of the caret. It sits above the composer, anchored at its bottom
 edge; it scales from the caret's x (`anchor`) and resizes to the rows it holds. Rows are placed by
 slot (0 = top) from the bottom edge, so the list can grow and shrink while every row slides to
 its new place.
*/
struct MentionAutocompletePopover: View {
    typealias M = MentionAutocompleteMotion

    let pose: M.Pose
    let query: String
    let open: Bool
    /// The popover lists people (loop or person), as a discrete state: VoiceOver reads only the listed rows.
    let listed: Bool
    let tappable: (Int) -> Bool
    let width: CGFloat
    /// Caret x inside the popover, the point it grows from.
    let anchor: CGFloat
    let channel: String
    let accent: Color
    let onPick: (Int) -> Void

    var body: some View {
        let H = M.popMaxH
        let n = max(0, pose.count)
        let p = pose.pop
        ZStack(alignment: .bottomLeading) {
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(Color(hex: 0xFFFEFB))
                .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(Color.rgba(30, 28, 24, 0.12), lineWidth: 0.5))
                .shadow(color: Color.rgba(30, 26, 20, 0.08), radius: 2, y: 2)
                .shadow(color: Color.rgba(30, 26, 20, 0.16), radius: 18, y: 16)
                .frame(width: width, height: M.popHead + M.popPad * 2 + n * M.rowH)
            HStack(spacing: 0) {
                (Text("People in ") + Text(channel).fontWeight(.semibold).foregroundStyle(Color(hex: 0x3F3C36)))
                    .font(.system(size: 12.5, weight: .medium))
                    .foregroundStyle(Color(hex: 0x7A766D))
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text("return to pick")
                    .font(.system(size: 12))
                    .foregroundStyle(Color(hex: 0xA7A297))
            }
            .frame(width: width - 28, height: M.popHead)
            .offset(x: 14, y: -(M.popPad + n * M.rowH))
            .accessibilityHidden(!listed)
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(accent.opacity(20.0 / 255))
                .frame(width: width - 12, height: M.rowH)
                .offset(x: 6, y: -(M.popPad + (n - 1) * M.rowH))
                .opacity(M.clamp01(n))
            ForEach(0 ..< MentionAutocompleteData.people.count, id: \.self) { i in
                row(i, n)
            }
        }
        .frame(width: width, height: H, alignment: .bottomLeading)
        .scaleEffect(0.3 + 0.7 * p, anchor: UnitPoint(x: anchor / width, y: 1))
        .opacity(M.clamp01(p * 2.2))
        .allowsHitTesting(open)
    }

    private func row(_ i: Int, _ n: Double) -> some View {
        let person = MentionAutocompleteData.people[i]
        let yy = pose.rowY.indices.contains(i) ? pose.rowY[i] : 0
        let aa = M.clamp01(pose.rowA.indices.contains(i) ? pose.rowA[i] : 0)
        return Button { onPick(i) } label: {
            HStack(spacing: 11) {
                MentionAutocompleteAvatar(person: person, size: 32, online: person.online, ring: Color(hex: 0xFFFEFB))
                VStack(alignment: .leading, spacing: 1) {
                    name(person)
                        .font(.system(size: 16))
                        .kerning(-0.2)
                        .lineLimit(1)
                    Text(person.role + (person.online ? "" : " · away"))
                        .font(.system(size: 13))
                        .foregroundStyle(Color(hex: 0x8A857B))
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.horizontal, 9)
            .frame(width: width - 12, height: M.rowH)
            .contentShape(Rectangle())
        }
        .buttonStyle(MentionAutocompleteRowStyle())
        .accessibilityLabel("Mention \(person.fullName)")
        .scaleEffect(0.96 + 0.04 * aa)
        .offset(x: 6, y: -(M.popPad + (n - 1 - yy) * M.rowH) + 6 * (1 - aa))
        .opacity(aa)
        .allowsHitTesting(tappable(i))
        .accessibilityHidden(!(listed && tappable(i)))
    }

    private func name(_ p: MentionAutocompletePerson) -> Text {
        let full = p.fullName
        let base = Color(hex: 0x57534B)
        guard let r = MentionAutocompleteData.matchRange(p, query) else { return Text(full).foregroundStyle(base) }
        let c = Array(full)
        return Text(String(c[0 ..< r.lowerBound])).foregroundStyle(base)
            + Text(String(c[r.lowerBound ..< r.upperBound])).fontWeight(.bold).foregroundStyle(Color(hex: 0x1E1D1A))
            + Text(String(c[r.upperBound...])).foregroundStyle(base)
    }
}

struct MentionAutocompleteRowStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(Color.rgba(30, 28, 24, configuration.isPressed ? 0.05 : 0))
            )
    }
}
