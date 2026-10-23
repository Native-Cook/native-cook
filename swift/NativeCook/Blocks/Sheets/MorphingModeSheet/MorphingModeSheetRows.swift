import SwiftUI

struct MorphingModeSheetMode {
    var label: String
    var caption: String
    /// An SF Symbol name.
    var icon: String
    var pro = false
}

struct MorphingModeSheetModel {
    var name: String
    var caption: String
    /// A small badge after the name.
    var tag: Tag? = nil
    /// A group heading shown above this row.
    var heading: String? = nil

    enum Tag { case new, max }
}

enum MorphingModeSheetRowMetrics {
    static let modeHeight = 66.0
    static let modeGap = 8.0
    static let modelHeight = 56.0
    static let modelGap = 4.0
    static let headingHeight = 30.0
}

private enum R {
    static let teal = Color(hex: 0x1e8f95)
    static let tealSoft = Color(hex: 0xcfebec)
    static let selectedFill = Color(hex: 0xe4f2f2)
    static let selectedBorder = Color(hex: 0x4cb8bd)
    static let rowFill = Color(hex: 0xf5f5f2)
    static let ink = Color(hex: 0x1c1c1c)
    static let mute = Color(hex: 0x8a8a8a)
    static let switchOff = Color(hex: 0xd8d8d8)
    static let switchOn = Color(hex: 0x1fa37a)
    static let tagBlue = Color(hex: 0x3b82f6)
    static let tagGold = Color(hex: 0xd99a1c)
    static let fade = Animation.easeInOut(duration: 0.2)
}

/// The white disc an icon of the compact sheet sits on.
private struct MorphingModeSheetIconDisc: View {
    let symbol: String
    let color: Color

    var body: some View {
        Image(systemName: symbol)
            .font(.system(size: 16))
            .foregroundStyle(color)
            .frame(width: 38, height: 38)
            .background(.white, in: Circle())
            .compositingGroup()
            .shadow(color: .black.opacity(0.06), radius: 3, y: 1)
    }
}

/// A name with a small badge after it, on the rows of both sheets.
private struct MorphingModeSheetBadge: View {
    let text: String
    let ink: Color
    let fill: Color
    var tracking = 0.0

    var body: some View {
        Text(text)
            .font(.system(size: 9.5, weight: .heavy))
            .tracking(tracking)
            .foregroundStyle(ink)
            .padding(.horizontal, 5)
            .frame(height: 16)
            .background(fill, in: RoundedRectangle(cornerRadius: 5))
    }
}

/// A row of the compact sheet; the selected one takes a teal fill and border.
struct MorphingModeSheetModeRow<Trailing: View>: View {
    let mode: MorphingModeSheetMode
    let selected: Bool
    let onPress: () -> Void
    @ViewBuilder let trailing: Trailing

    var body: some View {
        HStack(spacing: 12) {
            MorphingModeSheetIconDisc(symbol: mode.icon, color: selected ? R.teal : R.ink)
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(mode.label)
                        .font(.system(size: 15.5, weight: .semibold))
                        .tracking(-0.2)
                        .foregroundStyle(R.ink)
                    if mode.pro {
                        MorphingModeSheetBadge(text: "PRO", ink: R.teal, fill: R.tealSoft, tracking: 0.4)
                    }
                }
                Text(mode.caption)
                    .font(.system(size: 12.5))
                    .foregroundStyle(R.mute)
                    .lineLimit(1)
            }
            // Takes the room left, so the row's spacing is not counted twice around a spacer.
            .frame(maxWidth: .infinity, alignment: .leading)
            trailing
        }
        .padding(.horizontal, 12)
        .frame(height: MorphingModeSheetRowMetrics.modeHeight)
        .background(selected ? R.selectedFill : R.rowFill, in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(selected ? R.selectedBorder : .clear, lineWidth: 1.5))
        .animation(R.fade, value: selected)
        .contentShape(Rectangle())
        .onTapGesture(perform: onPress)
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    }
}

/// The incognito row: a switch that slides its knob while the track changes colour.
struct MorphingModeSheetSwitchRow: View {
    let label: String
    let caption: String
    let on: Bool
    let onPress: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            MorphingModeSheetIconDisc(symbol: "shield.lefthalf.filled", color: R.ink)
            VStack(alignment: .leading, spacing: 2) {
                Text(label)
                    .font(.system(size: 15.5, weight: .semibold))
                    .tracking(-0.2)
                    .foregroundStyle(R.ink)
                Text(caption)
                    .font(.system(size: 12.5))
                    .foregroundStyle(R.mute)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Capsule()
                .fill(on ? R.switchOn : R.switchOff)
                .frame(width: 50, height: 30)
                .overlay(alignment: .leading) {
                    Circle()
                        .fill(.white)
                        .frame(width: 26, height: 26)
                        .shadow(color: .black.opacity(0.2), radius: 3, y: 1.5)
                        .offset(x: on ? 22 : 2)
                }
        }
        .padding(.horizontal, 12)
        .frame(height: MorphingModeSheetRowMetrics.modeHeight)
        .background(R.rowFill, in: RoundedRectangle(cornerRadius: 16))
        .animation(R.fade, value: on)
        .contentShape(Rectangle())
        .onTapGesture(perform: onPress)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(label)
        .accessibilityValue(on ? "On" : "Off")
        .accessibilityAddTraits(.isButton)
    }
}

/// A row of the tall sheet. It slides up 12 points and fades in as the sheet grows, a little after the row above.
struct MorphingModeSheetModelRow: View {
    let model: MorphingModeSheetModel
    let index: Int
    let selected: Bool
    /// 0 compact, 1 tall.
    let grow: Double
    let onPress: () -> Void

    var body: some View {
        let from = 0.4 + Double(index) * 0.02
        let raw = min(1, max(0, (grow - from) / (0.85 + Double(index) * 0.02 - from)))
        let t = raw * raw * (3 - 2 * raw)
        VStack(alignment: .leading, spacing: 0) {
            if let heading = model.heading {
                Text(heading.uppercased())
                    .font(.system(size: 12, weight: .bold))
                    .tracking(0.6)
                    .foregroundStyle(R.mute)
                    .padding(.top, 12)
                    .padding(.horizontal, 20)
                    .frame(maxWidth: .infinity, minHeight: MorphingModeSheetRowMetrics.headingHeight, alignment: .topLeading)
            }
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text(model.name)
                            .font(.system(size: 15, weight: .semibold))
                            .tracking(-0.2)
                            .foregroundStyle(R.ink)
                        if let tag = model.tag {
                            MorphingModeSheetBadge(text: tag == .new ? "New" : "Max", ink: .white, fill: tag == .new ? R.tagBlue : R.tagGold)
                        }
                    }
                    Text(model.caption)
                        .font(.system(size: 12))
                        .foregroundStyle(R.mute)
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "checkmark")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: 22, height: 22)
                    .background(R.teal, in: Circle())
                    .opacity(selected ? 1 : 0)
                    .scaleEffect(selected ? 1 : 0.4)
                    .animation(R.fade, value: selected)
            }
            .padding(.horizontal, 14)
            .frame(height: MorphingModeSheetRowMetrics.modelHeight)
            .background(R.rowFill, in: RoundedRectangle(cornerRadius: 14))
            .padding(.horizontal, 12)
            .padding(.bottom, MorphingModeSheetRowMetrics.modelGap)
            .contentShape(Rectangle())
            .onTapGesture(perform: onPress)
            .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
        }
        .opacity(t)
        .offset(y: 12 * (1 - t))
    }
}
