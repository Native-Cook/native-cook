import SwiftUI
import UIKit

private typealias M = HabitatSpecimenCarouselMotion
private typealias Specimen = HabitatSpecimenCarouselSpecimen

private let pressCurve = Animation.timingCurve(0.23, 1, 0.32, 1, duration: 0.12)
private let swapCurve = Animation.timingCurve(0.2, 0, 0, 1, duration: 0.3)

/// The header's round-ish buttons: 48 pt targets that dim and sink a little while pressed.
struct HabitatSpecimenCarouselButtonStyle: ButtonStyle {
    let reduced: Bool

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .frame(minWidth: 48, minHeight: 48)
            .opacity(configuration.isPressed ? 0.78 : 1)
            .scaleEffect(configuration.isPressed && !reduced ? 0.97 : 1)
            .animation(reduced ? .timingCurve(0.23, 1, 0.32, 1, duration: 0.12) : pressCurve, value: configuration.isPressed)
            .contentShape(Rectangle())
    }
}

/// The favourite icon cross-fades (scale 0.25 to 1, blur 4 to 0) and, when it turns on, throws six
/// small sparks in the specimen's colour. Under Reduced Motion only the opacity changes.
struct HabitatSpecimenCarouselHeart: View {
    let on: Bool
    let size: CGFloat
    let reduced: Bool
    let burst: Color

    var body: some View {
        ZStack {
            icon("heart-outline", color: Color(habitatSC: 0xD7E3D2), shown: !on)
            icon("heart", color: Color(habitatSC: 0xEDC591), shown: on)
            if on && !reduced {
                ForEach(0..<6, id: \.self) { index in
                    Spark(index: index, color: burst, size: size)
                }
            }
        }
        .frame(width: size, height: size)
    }

    private func icon(_ name: String, color: Color, shown: Bool) -> some View {
        HabitatSpecimenCarouselIcon(name: name, size: size, color: color)
            .opacity(shown ? 1 : 0)
            .scaleEffect(shown || reduced ? 1 : 0.25)
            .blur(radius: reduced ? 0 : (shown ? 0 : 4))
            .animation(swapCurve, value: on)
    }

    private struct Spark: View {
        let index: Int
        let color: Color
        let size: CGFloat
        @State private var gone = false

        var body: some View {
            let angle = (Double(index) / 6) * Double.pi * 2 - Double.pi / 2 + Double(index % 2) * 0.22
            let reach = size * (0.95 + CGFloat(index % 3) * 0.12)
            Circle().fill(color)
                .frame(width: 4, height: 4)
                .scaleEffect(gone ? 0.4 : 1)
                .offset(x: gone ? cos(angle) * reach : 0, y: gone ? sin(angle) * reach : 0)
                .opacity(gone ? 0 : 1)
                .allowsHitTesting(false)
                .onAppear { withAnimation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.52)) { gone = true } }
        }
    }
}

/// POKEDEX and the dex number.
struct HabitatSpecimenCarouselEyebrow: View {
    let model: HabitatSpecimenCarouselModel
    let layout: HabitatSpecimenCarouselLayout
    let selectedIndex: Int

    var body: some View {
        let scale = CGFloat(layout.scale)
        HStack(alignment: .top, spacing: 0) {
            Text("POKÉDEX")
                .font(.system(size: 10 * scale, weight: .medium))
                .tracking(3)
                .foregroundStyle(Color(habitatSC: 0xD0D8C9))
            Spacer(minLength: 0)
            HabitatSpecimenCarouselTextSwap(model: model, count: 3, height: 17 * scale, selectedIndex: selectedIndex, alignment: .topTrailing) { index in
                Text("#" + Specimen.all[index].number)
                    .font(.system(size: 12 * scale, weight: .semibold))
                    .tracking(2)
                    .monospacedDigit()
                    .foregroundStyle(Color(habitatSC: 0xF1F4E7))
                    .frame(height: 17 * scale)
            }
            .frame(width: 64 * scale)
        }
        .frame(width: CGFloat(layout.width) - 53 * scale, alignment: .topLeading)
        .offset(x: 26 * scale, y: CGFloat(layout.insetTop) + 58 * scale)
        .allowsHitTesting(false)
    }
}

/// The dex number behind the hero, rewritten with the same morph: the shared 0s glide, the last digit swaps.
struct HabitatSpecimenCarouselGhost: View {
    @ObservedObject var model: HabitatSpecimenCarouselModel
    let layout: HabitatSpecimenCarouselLayout

    var body: some View {
        let scale = CGFloat(layout.scale)
        let fontSize = 168 * scale
        let height = (fontSize * 1.34).rounded()
        HabitatSpecimenCarouselMorphingText(
            text: Specimen.all[model.selectedIndex].number, fitTexts: Specimen.all.map(\.number), fontSize: fontSize, height: height,
            tracking: -6 * scale, width: CGFloat(layout.width), weight: .bold, color: Color(habitatSC: 0xC5DAC7), reduceMotion: model.reduced
        )
        .opacity(0.065)
        .offset(y: CGFloat(layout.heroTop) - 10 * scale)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

/// Species and name, written with the morph.
struct HabitatSpecimenCarouselIdentity: View {
    @ObservedObject var model: HabitatSpecimenCarouselModel
    let layout: HabitatSpecimenCarouselLayout

    var body: some View {
        let scale = CGFloat(layout.scale)
        let specimen = Specimen.all[model.selectedIndex]
        let reduced = model.reduced
        VStack(spacing: 0) {
            HabitatSpecimenCarouselMorphingText(
                text: specimen.species, fitTexts: Specimen.all.map(\.species), fontSize: 10 * scale, height: 15 * scale,
                tracking: 1 * scale, width: CGFloat(layout.width), weight: .regular, color: Color(habitatSC: 0xB3C1AD), reduceMotion: reduced
            )
            HabitatSpecimenCarouselMorphingText(
                text: specimen.name, fitTexts: Specimen.all.map(\.name), fontSize: 37 * scale, height: 45 * scale,
                tracking: -1.3 * scale, width: CGFloat(layout.width), weight: .semibold, color: Color(habitatSC: 0xF5F4E9), reduceMotion: reduced
            )
        }
        .frame(width: CGFloat(layout.width), height: CGFloat(layout.identityHeight), alignment: .top)
        .offset(y: CGFloat(layout.identityTop))
        .allowsHitTesting(false)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(specimen.name), \(specimen.species)")
        .accessibilityAddTraits(.isHeader)
    }
}

/// Type pills under the name.
struct HabitatSpecimenCarouselTypes: View {
    let model: HabitatSpecimenCarouselModel
    let layout: HabitatSpecimenCarouselLayout
    let selectedIndex: Int

    var body: some View {
        let scale = CGFloat(layout.scale)
        let row = 25 * scale
        HabitatSpecimenCarouselTextSwap(model: model, count: 3, height: row, selectedIndex: selectedIndex, alignment: .top) { index in
            HStack(spacing: 8 * scale) {
                ForEach(Specimen.all[index].types, id: \.label) { type in
                    HStack(spacing: 5) {
                        HabitatSpecimenCarouselIcon(name: type.icon, size: 12 * scale, color: Color(habitatSC: type.color))
                        Text(type.label)
                            .font(.system(size: 10 * scale, weight: .semibold))
                            .foregroundStyle(Color(habitatSC: type.color))
                    }
                    .padding(.horizontal, 11 * scale)
                    .frame(height: row)
                    .background(Color(habitatSC: type.color, 0x08 / 255.0), in: RoundedRectangle(cornerRadius: 15 * scale, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 15 * scale, style: .continuous).stroke(Color(habitatSC: type.color, 0x45 / 255.0), lineWidth: 1 / 3))
                }
            }
            .frame(maxWidth: .infinity)
        }
        .frame(width: CGFloat(layout.width) - 32)
        .offset(x: 16, y: CGFloat(layout.identityTop + layout.identityHeight) + 9 * scale)
        .allowsHitTesting(false)
    }
}

/// Weight and height, on the line above the strip.
struct HabitatSpecimenCarouselFacts: View {
    let model: HabitatSpecimenCarouselModel
    let layout: HabitatSpecimenCarouselLayout
    let selectedIndex: Int

    var body: some View {
        let scale = CGFloat(layout.scale)
        let line = 17 * scale
        let width = CGFloat(layout.width) - 50 * scale
        let specimen = Specimen.all[selectedIndex]
        HStack(alignment: .top, spacing: 0) {
            fact(icon: "weight-kilogram", label: "WEIGHT", scale: scale, line: line) { $0.weight }
                .frame(width: width / 2)
            fact(icon: "ruler", label: "HEIGHT", scale: scale, line: line) { $0.height }
                .frame(width: width / 2)
                .overlay(alignment: .leading) { Rectangle().fill(Color(habitatSC: 0xD4E4DA, 0x1A / 255.0)).frame(width: 1 / 3) }
        }
        .padding(.top, 10 * scale)
        .frame(width: width, height: 44 * scale, alignment: .top)
        .overlay(alignment: .top) { Rectangle().fill(Color(habitatSC: 0xD4E4DA, 0x19 / 255.0)).frame(height: 1 / 3) }
        .offset(x: 25 * scale, y: CGFloat(layout.factsTop))
        .allowsHitTesting(false)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Weight \(specimen.weight), height \(specimen.height)")
    }

    private func fact(icon: String, label: String, scale: CGFloat, line: CGFloat, value: @escaping (HabitatSpecimenCarouselSpecimen) -> String) -> some View {
        VStack(spacing: 5) {
            HStack(spacing: 7) {
                HabitatSpecimenCarouselIcon(name: icon, size: 15 * scale, color: Color(habitatSC: 0xB1C1B0))
                HabitatSpecimenCarouselTextSwap(model: model, count: 3, height: line, selectedIndex: selectedIndex) { index in
                    Text(value(Specimen.all[index]))
                        .font(.system(size: 12 * scale, weight: .semibold))
                        .foregroundStyle(Color(habitatSC: 0xEFF3E4))
                        .frame(height: line)
                }
                .frame(width: 58 * scale)
            }
            Text(label)
                .font(.system(size: 8 * scale))
                .tracking(1)
                .foregroundStyle(Color(habitatSC: 0xA5B49D))
        }
    }
}

/// Choose Your Ball's belt index, with the specimens: each thumbnail places itself by its distance from centre.
struct HabitatSpecimenCarouselFooter: View {
    let model: HabitatSpecimenCarouselModel
    let layout: HabitatSpecimenCarouselLayout
    let selectedIndex: Int
    let onPick: (Int) -> Void

    private static let stride: CGFloat = 40
    private static let thumb: CGFloat = 24
    private static let selected: CGFloat = 34

    var body: some View {
        let scale = CGFloat(layout.scale)
        let width = CGFloat(layout.width) - 54 * scale
        ZStack {
            HStack(spacing: 0) {
                HStack(spacing: 8) {
                    HabitatSpecimenCarouselIcon(name: "pokeball", size: 14 * scale, color: Color(habitatSC: 0xA9B8A3))
                    Text("KANTO REGION")
                        .font(.system(size: 8 * scale))
                        .tracking(2)
                        .foregroundStyle(Color(habitatSC: 0xA1B297))
                }
                Spacer(minLength: 0)
                HabitatSpecimenCarouselTextSwap(model: model, count: 3, height: 12 * scale, selectedIndex: selectedIndex, alignment: .topTrailing) { index in
                    Text(Specimen.all[index].number + " / 151")
                        .font(.system(size: 8 * scale))
                        .tracking(2)
                        .foregroundStyle(Color(habitatSC: 0xA1B297))
                        .frame(height: 12 * scale)
                }
                .frame(width: 70 * scale)
                .allowsHitTesting(false)
            }
            ForEach(0..<3, id: \.self) { index in
                Thumb(model: model, index: index, onPick: onPick)
            }
        }
        .frame(width: width, height: Self.selected)
        .offset(x: 27 * scale, y: CGFloat(layout.height - layout.insetBottom) - 4 * scale - Self.selected)
    }

    private struct Thumb: View {
        let model: HabitatSpecimenCarouselModel
        let index: Int
        let onPick: (Int) -> Void

        var body: some View {
            TimelineView(.animation) { _ in
                let p = model.position
                let slot = M.specimenDistance(index, model.reduced ? M.round(p) : p)
                let distance = abs(slot)
                let opacity = distance <= 1 ? 1 + (0.42 - 1) * distance : max(0.2, 0.42 + (0.2 - 0.42) * ((distance - 1) / 0.5))
                let scale = distance >= 1 ? 1 : (Self_selected / Self_thumb) + (1 - Self_selected / Self_thumb) * distance
                Button { onPick(index) } label: {
                    HabitatSpecimenCarouselImage(asset: Specimen.all[index].asset)
                        .frame(width: Self_thumb, height: Self_thumb)
                }
                .buttonStyle(ThumbStyle(reduced: model.reduced))
                .accessibilityLabel("Select \(Specimen.all[index].name)")
                .scaleEffect(scale)
                .offset(x: slot * Self_stride)
                .opacity(opacity)
            }
        }
    }

    private struct ThumbStyle: ButtonStyle {
        let reduced: Bool
        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .scaleEffect(configuration.isPressed && !reduced ? 0.96 : 1)
                .opacity(configuration.isPressed ? 0.8 : 1)
                .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.15), value: configuration.isPressed)
                .padding(10)
                .contentShape(Rectangle())
                .padding(-10)
        }
    }
}

private let Self_thumb: CGFloat = 24
private let Self_selected: CGFloat = 34
private let Self_stride: CGFloat = 40

/// The two hidden gestures, named once: a quiet pill under the stage that appears after the first
/// look, leaves on its own, and never comes back once the person has touched the specimen.
struct HabitatSpecimenCarouselHint: View {
    let layout: HabitatSpecimenCarouselLayout
    let reduced: Bool
    let touched: Bool

    private enum Phase { case waiting, shown, gone }
    @State private var phase = Phase.waiting

    var body: some View {
        let scale = CGFloat(layout.scale)
        let shown = phase == .shown
        HStack(spacing: 6 * scale) {
            HabitatSpecimenCarouselIcon(name: "gesture-tap", size: 12 * scale, color: Color(habitatSC: 0xD7E3D2))
            Text("Tap for its move").font(.system(size: 10 * scale, weight: .medium)).tracking(0.2).foregroundStyle(Color(habitatSC: 0xD7E3D2))
            Circle().fill(Color(habitatSC: 0xD7E3D2, 0x66 / 255.0)).frame(width: 3 * scale, height: 3 * scale).padding(.horizontal, 2)
            HabitatSpecimenCarouselIcon(name: "gesture-tap-hold", size: 12 * scale, color: Color(habitatSC: 0xD7E3D2))
            Text("Hold to pet").font(.system(size: 10 * scale, weight: .medium)).tracking(0.2).foregroundStyle(Color(habitatSC: 0xD7E3D2))
        }
        .padding(.horizontal, 10 * scale)
        .frame(height: 22 * scale)
        .background(Color(habitatSC: 0x0B1411, 0x99 / 255.0), in: RoundedRectangle(cornerRadius: 11 * scale, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 11 * scale, style: .continuous).stroke(Color(habitatSC: 0xD4E4DA, 0x2E / 255.0), lineWidth: 1 / 3))
        .opacity(shown ? 1 : 0)
        .offset(y: shown || reduced ? 0 : 4 * scale)
        .scaleEffect(shown || reduced ? 1 : 0.96)
        .animation(.timingCurve(0.23, 1, 0.32, 1, duration: shown ? 0.3 : 0.2), value: shown)
        .frame(width: CGFloat(layout.width))
        .offset(y: CGFloat(layout.heroTop + layout.heroHeight) + 2 * scale)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
        .task(id: phase) {
            switch phase {
            case .waiting:
                try? await Task.sleep(nanoseconds: 1_600_000_000)
                if !Task.isCancelled, phase == .waiting { phase = .shown }
            case .shown:
                try? await Task.sleep(nanoseconds: 4_200_000_000)
                if !Task.isCancelled, phase == .shown { phase = .gone }
            case .gone:
                break
            }
        }
        .onChange(of: touched) { _, now in if now { phase = .gone } }
    }
}
