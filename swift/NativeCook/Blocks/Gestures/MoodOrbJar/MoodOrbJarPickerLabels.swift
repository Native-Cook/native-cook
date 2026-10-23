import SwiftUI

/// The five mood names under the picker orbs. Each is a button for VoiceOver ("Add Good"); a finger
/// reaches the orb through the touch surface instead.
struct MoodOrbJarPickerLabels: View {
    typealias Layout = MoodOrbJarTokens.Layout

    let controller: MoodOrbJarController
    let geometry: MoodOrbJarGeometry

    var body: some View {
        let s = geometry.scale
        // One container, so each label's offset is measured from the same corner.
        ZStack(alignment: .topLeading) {
            ForEach(Array(MoodOrbJarTokens.moods.enumerated()), id: \.element.name) { index, mood in
                Text(mood.name)
                    .font(.system(size: Layout.labelSize, weight: .medium))
                    .foregroundStyle(MoodOrbJarTokens.Palette.label)
                    .frame(width: 60 * s, height: Layout.slotLabelHeight * s, alignment: .bottom)
                    .offset(x: geometry.slotX[index] - 30 * s, y: geometry.slotY - 24 * s)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(MoodOrbJarTokens.Copy.add(mood.name))
                    .accessibilityAddTraits(.isButton)
                    .accessibilityAction {
                        controller.add(mood: index)
                    }
            }
        }
    }
}
