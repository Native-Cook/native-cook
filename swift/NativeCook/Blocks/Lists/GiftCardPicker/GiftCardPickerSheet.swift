import SwiftUI

struct GiftCardPickerSheet: View {
    let width: CGFloat
    let height: CGFloat
    let scale: CGFloat
    let ky: CGFloat
    let frame: GiftCardPickerMotion.Frame
    let time: Double
    let pressLight: Bool
    let pressDark: Bool
    let reduceMotion: Bool
    let onDiscover: () -> Void
    let onChooseStock: () -> Void
    let onClose: () -> Void
    let onCard: (Int) -> Void
    let onChoose: () -> Void
    let onLightPress: (Bool) -> Void
    let onDarkPress: (Bool) -> Void
    let onDragStart: () -> Void
    let onDrag: (CGFloat) -> Void
    let onDragEnd: (CGFloat, CGFloat, CGFloat, Bool) -> Void

    private var closeBackground: Color {
        let r = min(1, max(0, frame.reveal))
        // Reanimated interpolateColor uses gamma 2.2 by default.
        func channel(_ a: Double, _ b: Double, _ p: Double) -> Double {
            pow(pow(a / 255, 2.2) * (1 - p) + pow(b / 255, 2.2) * p, 1 / 2.2)
        }
        let start = r > 0 ? 90.0 : 255.0
        let end = r > 0 ? 255.0 : 90.0
        let t = r > 0 ? r : frame.push
        return Color(.sRGB, red: channel(start, end, t), green: channel(start, end, t), blue: channel(r > 0 ? 94 : 255, r > 0 ? 255 : 94, t), opacity: r > 0 ? 1 - 0.94 * r : 1)
    }

    var body: some View {
        ZStack(alignment: .topLeading) {
            GiftCardPickerDiscoverSheet(width: width, height: height, scale: scale, ky: ky, stage: frame.stage, time: time, press: pressLight, reduceMotion: reduceMotion, onDiscover: onDiscover, onChoose: onChooseStock, onPress: onLightPress)
                .offset(x: reduceMotion ? 0 : -frame.push * width * 0.3)
                .accessibilityHidden(frame.layer != 1)
            GiftCardPickerStockSheet(width: width, height: height, ky: ky, frame: frame, press: pressDark, reduceMotion: reduceMotion, onCard: onCard, onChoose: onChoose, onPress: onDarkPress)
                .overlay(alignment: .topLeading) {
                    GiftCardPickerTouchSurface(enabled: frame.layer == 2, onBegan: onDragStart, onChanged: onDrag, onEnded: onDragEnd)
                        .frame(width: width, height: 300)
                        .offset(y: (328 - 62) * ky - 20)
                }
                .clipShape(.rect(topLeadingRadius: 30, topTrailingRadius: 30))
                .offset(x: reduceMotion ? 0 : (1 - frame.push) * width)
                .opacity(reduceMotion ? frame.push : (frame.push > 0.001 ? 1 : 0))
                .allowsHitTesting(frame.layer == 2 && frame.commit <= 0.001)
                .accessibilityHidden(frame.layer != 2)
            Button(action: onClose) {
                Circle().fill(closeBackground)
                    .frame(width: 44, height: 44)
                    .shadow(color: .black.opacity(0.08 * (1 - frame.push)), radius: 10, y: 3)
                    .overlay {
                        ZStack {
                            GiftCardPickerGlyph(kind: .close, color: GiftCardPickerTheme.ink)
                                .opacity(1 - min(1, frame.push * 8))
                            GiftCardPickerGlyph(kind: .close, color: .white)
                                .opacity(min(1, max(0, (frame.push - 0.9) * 10)))
                        }
                        .frame(width: 18, height: 18)
                    }
            }
            .buttonStyle(GiftCardPickerPressStyle())
            .disabled(frame.layer != 1 && frame.layer != 2)
            .position(x: 15 + 22, y: 16 + 22)
            .accessibilityLabel("Close")
            .accessibilityHidden(frame.layer != 1 && frame.layer != 2)
            .opacity(1 - min(1, frame.commit * (reduceMotion ? 4 : 15.875)))
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .background(GiftCardPickerTheme.lightSheet)
    }
}

struct GiftCardPickerDiscoverSheet: View {
    let width: CGFloat
    let height: CGFloat
    let scale: CGFloat
    let ky: CGFloat
    let stage: Double
    let time: Double
    let press: Bool
    let reduceMotion: Bool
    let onDiscover: () -> Void
    let onChoose: () -> Void
    let onPress: (Bool) -> Void

    private func clamp(_ v: Double) -> Double { min(1, max(0, v)) }
    var body: some View {
        let first = 1 - clamp(stage / 0.1)
        let second = clamp((stage - 1) / 0.05)
        ZStack(alignment: .topLeading) {
            GiftCardPickerOrb(width: width, height: height, scale: scale, ky: ky, stage: stage, reduceMotion: reduceMotion, time: time)
            Text("Terms and Conditions").font(.system(size: 13)).foregroundStyle(Color(hex: 0x6E6E73)).underline()
                .frame(width: width, height: 20).position(x: width / 2, y: height - 143).opacity(first)
            Text("Discovering your gift share value...").font(.system(size: 17)).foregroundStyle(Color(hex: 0x74747A))
                .frame(width: width, height: 22).position(x: width / 2, y: height - 64)
                .opacity(min(clamp(stage / 0.1), 1 - second))
            GiftCardPickerActionButton(label: "Discover amount", secondLabel: "Choose a gift stock", firstOpacity: first, secondOpacity: second, dark: false, pressed: press, action: {
                if stage < 0.5 { onDiscover() }
                else if stage > 1.9 { onChoose() }
            }, onPress: onPress)
                .frame(width: width - 80, height: 55)
                .position(x: width / 2, y: height - 82.5)
                .opacity(max(first, second))
                .accessibilityLabel("Discover amount, then choose a gift stock")
        }
        .frame(width: width, height: height, alignment: .topLeading)
    }
}

struct GiftCardPickerStockSheet: View {
    let width: CGFloat
    let height: CGFloat
    let ky: CGFloat
    let frame: GiftCardPickerMotion.Frame
    let press: Bool
    let reduceMotion: Bool
    let onCard: (Int) -> Void
    let onChoose: () -> Void
    let onPress: (Bool) -> Void
    private func y(_ pin: CGFloat) -> CGFloat { (pin - 62) * ky }

    var body: some View {
        let content = min(1, max(0, frame.reveal))
        let commitOpacity = 1 - min(1, frame.commit * (reduceMotion ? 4 : 15.875))
        ZStack(alignment: .topLeading) {
            GiftCardPickerSkeleton(width: width, height: height, ky: ky).opacity((1 - content) * commitOpacity)
            ZStack(alignment: .topLeading) {
                ForEach(Array(GiftCardPickerData.stocks.enumerated()), id: \.element.id) { index, _ in
                    GiftCardPickerStockBackground(index: index, width: width, height: height).opacity(frame.backgrounds[index])
                }
                GiftCardPickerDust(width: width, height: height).allowsHitTesting(false)
                VStack(spacing: 0) {
                    Text("Choose your").frame(height: 34)
                    Text("USD 3 gift stock").frame(height: 34)
                }
                .font(.system(size: 28, weight: .bold)).kerning(-0.4).foregroundStyle(.white)
                .frame(width: width).position(x: width / 2, y: y(169) + 17)
                .accessibilityAddTraits(.isHeader)
                GiftCardPickerCardTrack(width: width, scroll: frame.scroll, reveal: frame.reveal, onCard: onCard)
                    .opacity(frame.trackFade).position(x: width / 2, y: y(328) + 130)
                HStack(spacing: 9) {
                    ForEach(Array(GiftCardPickerData.stocks.enumerated()), id: \.element.id) { index, _ in
                        Circle().fill(.white).frame(width: 7, height: 7).opacity(index == frame.active ? 1 : 0.38)
                    }
                }
                .position(x: width / 2, y: y(634)).accessibilityHidden(true)
                ForEach(GiftCardPickerData.stocks) { stock in
                    let index = GiftCardPickerData.stocks.firstIndex(where: { $0.id == stock.id }) ?? 0
                    GiftCardPickerDescription(stock: stock)
                        .frame(width: width - 48, height: 42)
                        .position(x: width / 2, y: height - 168 + 10.5)
                        .opacity(frame.descriptions[index])
                        .accessibilityHidden(index != frame.active)
                }
                GiftCardPickerActionButton(label: "Choose \(GiftCardPickerData.stocks[frame.active].name)", dark: true, pressed: press, action: onChoose, onPress: onPress)
                    .frame(width: width - 80, height: 55).position(x: width / 2, y: height - 82.5)
                    .accessibilityLabel("Choose this stock")
            }
            .frame(width: width, height: height, alignment: .topLeading).opacity(content * commitOpacity)
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .background(GiftCardPickerTheme.skeletonBackground)
    }
}

struct GiftCardPickerSkeleton: View {
    let width: CGFloat
    let height: CGFloat
    let ky: CGFloat
    private func y(_ pin: CGFloat) -> CGFloat { (pin - 62) * ky }
    private func bone(_ w: CGFloat, _ h: CGFloat, _ r: CGFloat) -> some View {
        RoundedRectangle(cornerRadius: r, style: .continuous).fill(GiftCardPickerTheme.skeleton).frame(width: w, height: h)
    }
    var body: some View {
        ZStack(alignment: .topLeading) {
            bone(165, 23, 7).position(x: width / 2, y: y(157) + 11.5)
            bone(210, 23, 7).position(x: width / 2, y: y(191) + 11.5)
            bone(196, 260, 18).position(x: width / 2, y: y(328) + 130)
            bone(196, 260, 18).modifier(GiftCardPickerCardPose(distance: 1)).position(x: width / 2, y: y(328) + 130)
            bone(44, 7, 4).position(x: width / 2, y: y(631) + 3.5)
            bone(235, 6, 3).position(x: width / 2, y: height - (874 - 703) + 3)
            bone(195, 6, 3).position(x: width / 2, y: height - (874 - 721) + 3)
            bone(width - 80, 55, 20).position(x: width / 2, y: height - 82.5)
        }
        .frame(width: width, height: height, alignment: .topLeading)
    }
}

struct GiftCardPickerActionButton: View {
    let label: String
    var secondLabel: String? = nil
    var firstOpacity: Double = 1
    var secondOpacity: Double = 0
    let dark: Bool
    let pressed: Bool
    let action: () -> Void
    let onPress: (Bool) -> Void
    var body: some View {
        Button(action: action) {
            ZStack {
                Text(label).opacity(firstOpacity)
                if let secondLabel { Text(secondLabel).opacity(secondOpacity) }
            }
            .font(.system(size: 17, weight: dark ? .regular : .medium))
            .foregroundStyle(dark ? Color(hex: 0x1C1C1E) : .white)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(dark ? GiftCardPickerTheme.button : GiftCardPickerTheme.orange, in: .rect(cornerRadius: 20, style: .continuous))
            .scaleEffect(pressed ? 0.97 : 1)
        }
        .buttonStyle(GiftCardPickerPressStyle(onChanged: onPress))
    }
}
