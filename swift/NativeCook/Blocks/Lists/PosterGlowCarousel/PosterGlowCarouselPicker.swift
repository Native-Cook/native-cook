import SwiftUI

struct PosterGlowCarouselPickerView: View {
    let width: Double
    let height: Double
    let autoPress: Double
    let onPick: (Int) -> Void

    var body: some View {
        ZStack(alignment: .topLeading) {
            PosterGlowCarouselPickerBackdrop(width: width, height: height)
            Text("Edit Profile")
                .font(.custom(PosterGlowCarouselFont.regular, fixedSize: PosterGlowCarouselMetrics.pickerEditSize))
                .foregroundStyle(PosterGlowCarouselColor.ink)
                .frame(width: 120, height: 16, alignment: .trailing)
                .offset(x: width - PosterGlowCarouselMetrics.pickerEditRight - 120,
                        y: PosterGlowCarouselMetrics.pickerEditY - 8)
                .accessibilityAddTraits(.isButton)
            Text("Who’s Watching?")
                .font(.custom(PosterGlowCarouselFont.semibold, fixedSize: PosterGlowCarouselMetrics.pickerTitleSize))
                .foregroundStyle(PosterGlowCarouselColor.ink)
                .frame(width: width, height: 40, alignment: .center)
                .offset(x: PosterGlowCarouselMetrics.pickerTitleDX, y: PosterGlowCarouselMetrics.pickerTitleY - 20)
                .accessibilityAddTraits(.isHeader)

            ForEach(Array(PosterGlowCarouselContent.profiles.enumerated()), id: \.element.id) { index, profile in
                PosterGlowCarouselProfileButton(index: index, row: index / 2,
                                                name: profile.name, image: profile.image,
                                                autoPress: index == 0 ? autoPress : 0,
                                                onPick: onPick)
                    .position(x: PosterGlowCarouselMetrics.pickerColX[index % 2],
                              y: PosterGlowCarouselMetrics.pickerRowY[index / 2] + 35)
            }

            Text("\(PosterGlowCarouselContent.appName), 2023")
                .font(.custom(PosterGlowCarouselFont.regular, fixedSize: PosterGlowCarouselMetrics.pickerFooterSize))
                .foregroundStyle(PosterGlowCarouselColor.inkSoft)
                .frame(width: width, height: 18, alignment: .center)
                .offset(y: height - (PosterGlowCarouselMetrics.pickerFooterFromBottom - 9) - 18)
        }
        .frame(width: width, height: height, alignment: .topLeading)
    }
}

private struct PosterGlowCarouselPickerBackdrop: View {
    let width: Double
    let height: Double

    var body: some View {
        ZStack {
            LinearGradient(colors: [PosterGlowCarouselColor.pickerTop, PosterGlowCarouselColor.pickerBottom],
                           startPoint: .top, endPoint: .bottom)
            RadialGradient(colors: [PosterGlowCarouselColor.pickerTeal, PosterGlowCarouselColor.pickerTeal.opacity(0)],
                           center: UnitPoint(x: 0, y: 0.5), startRadius: 0, endRadius: width * 0.62)
            RadialGradient(colors: [PosterGlowCarouselColor.pickerMagenta.opacity(0.95), PosterGlowCarouselColor.pickerMagenta.opacity(0)],
                           center: UnitPoint(x: 0.74, y: 0.31), startRadius: 0, endRadius: width * 0.6)
            RadialGradient(colors: [PosterGlowCarouselColor.pickerPlum.opacity(0.9), PosterGlowCarouselColor.pickerPlum.opacity(0)],
                           center: UnitPoint(x: 0.72, y: 0.76), startRadius: 0, endRadius: width * 0.5)
        }
        .frame(width: width, height: height)
        .allowsHitTesting(false)
    }
}

private struct PosterGlowCarouselProfileButton: View {
    let index: Int
    let row: Int
    let name: String
    let image: String?
    let autoPress: Double
    let onPick: (Int) -> Void

    var body: some View {
        VStack(spacing: 0) {
            Button {
                onPick(index)
            } label: {
                ZStack {
                    if let image {
                        PosterGlowCarouselAssets.avatar(image)
                            .resizable()
                            .scaledToFill()
                    } else {
                        PosterGlowCarouselColor.plus
                        RoundedRectangle(cornerRadius: 4)
                            .fill(PosterGlowCarouselColor.ink)
                            .frame(width: PosterGlowCarouselMetrics.pickerPlusArm,
                                   height: PosterGlowCarouselMetrics.pickerPlusStroke)
                        RoundedRectangle(cornerRadius: 4)
                            .fill(PosterGlowCarouselColor.ink)
                            .frame(width: PosterGlowCarouselMetrics.pickerPlusStroke,
                                   height: PosterGlowCarouselMetrics.pickerPlusArm)
                    }
                }
                .frame(width: PosterGlowCarouselMetrics.pickerAvatar,
                       height: PosterGlowCarouselMetrics.pickerAvatar)
                .clipShape(Circle())
            }
            .buttonStyle(PosterGlowCarouselPressStyle())
            .scaleEffect(1 - (1 - 0.97) * autoPress)
            .accessibilityLabel(name)
            Spacer().frame(height: PosterGlowCarouselMetrics.pickerNameGap[row] - PosterGlowCarouselMetrics.pickerAvatar / 2 - 12)
            Text(name)
                .font(.custom(PosterGlowCarouselFont.regular, fixedSize: PosterGlowCarouselMetrics.pickerNameSize))
                .foregroundStyle(PosterGlowCarouselColor.ink)
                .lineLimit(1)
                .frame(width: PosterGlowCarouselMetrics.pickerAvatar + 60, height: 24, alignment: .center)
                .offset(x: PosterGlowCarouselMetrics.pickerNameDX)
        }
        .frame(width: PosterGlowCarouselMetrics.pickerAvatar,
               height: PosterGlowCarouselMetrics.pickerAvatar + 70,
               alignment: .top)
    }
}
