import SwiftUI

/// The page gradient and the pictures that never change (portrait, avatars, the two big photos).
struct TasteProfileBackdrop: View, Equatable {
    let geo: TasteProfileGeo

    var body: some View {
        let stops = TasteProfileTheme.ColorToken.pageStops.map { item in
            Gradient.Stop(color: item.1, location: min(1, max(0, geo.y(item.0) / geo.height)))
        }
        ZStack(alignment: .topLeading) {
            LinearGradient(stops: stops, startPoint: .top, endPoint: .bottom)
                .frame(width: geo.width, height: geo.height)
            portrait
            avatar(TasteProfileAssets.mutual1, token: TasteProfileTheme.Box.avatars[0])
            avatar(TasteProfileAssets.mutual2, token: TasteProfileTheme.Box.avatars[1])
            placed(TasteProfileAssets.grid5, in: TasteProfileTheme.Box.photoL)
            placed(TasteProfileAssets.grid6, in: TasteProfileTheme.Box.photoR)
            LinearGradient(colors: [
                TasteProfileTheme.color(0x1A1E18), TasteProfileTheme.color(0x10110C), TasteProfileTheme.color(0x090B09),
                TasteProfileTheme.color(0x14130F), TasteProfileTheme.color(0x4B4A46), TasteProfileTheme.color(0x1A1C1C),
            ], startPoint: .top, endPoint: .bottom)
            .frame(width: geo.p(TasteProfileTheme.Box.peek.w), height: geo.p(TasteProfileTheme.Box.peek.h))
            .clipShape(.rect(cornerRadius: geo.p(TasteProfileTheme.Radius.photo)))
            .offset(x: geo.p(TasteProfileTheme.Box.peek.x), y: geo.y(TasteProfileTheme.Box.peek.y))
        }
        .frame(width: geo.width, height: geo.height, alignment: .topLeading)
        .clipped()
    }

    private var portrait: some View {
        let box = TasteProfileTheme.Box.capsule
        return Image(uiImage: TasteProfileAssets.profile)
            .resizable()
            .scaledToFill()
            .frame(width: geo.p(box.w - 2 * TasteProfileTheme.Box.capsuleBorder),
                   height: geo.p(box.h - 2 * TasteProfileTheme.Box.capsuleBorder))
            .clipShape(Capsule())
            .padding(geo.p(TasteProfileTheme.Box.capsuleBorder))
            .background(TasteProfileTheme.ColorToken.white, in: Capsule())
            .shadow(color: .black.opacity(0.12), radius: 6 * geo.pointScale, y: 3 * geo.pointScale)
            .frame(width: geo.p(box.w), height: geo.p(box.h))
            .offset(x: geo.p(box.x), y: geo.y(box.y))
    }

    private func avatar(_ image: UIImage, token: (d: Double, cx: Double, cy: Double)) -> some View {
        Image(uiImage: image)
            .resizable()
            .scaledToFill()
            .frame(width: geo.p(token.d - 6), height: geo.p(token.d - 6))
            .clipShape(Circle())
            .padding(geo.p(3))
            .background(TasteProfileTheme.ColorToken.white, in: Circle())
            .frame(width: geo.p(token.d), height: geo.p(token.d))
            .offset(x: geo.p(token.cx - token.d / 2), y: geo.y(token.cy - token.d / 2))
    }

    private func placed(_ image: UIImage, in box: TasteProfileTheme.BoxToken) -> some View {
        Image(uiImage: image)
            .resizable()
            .scaledToFill()
            .frame(width: geo.p(box.w), height: geo.p(box.h))
            .clipped()
            .clipShape(.rect(cornerRadius: geo.p(TasteProfileTheme.Radius.photo)))
            .offset(x: geo.p(box.x), y: geo.y(box.y))
    }
}

/// Nav row, names, labels and the rest that stands still.
struct TasteProfileStaticLayer: View, Equatable {
    let geo: TasteProfileGeo

    var body: some View {
        Canvas { context, _ in
            TasteProfileArt(geo: geo).drawStatic(in: &context)
        }
        .frame(width: geo.width, height: geo.height)
    }
}

/// The stat tiles; the held one is left out (the chart draws it).
struct TasteProfileTilesLayer: View, Equatable {
    let geo: TasteProfileGeo
    let held: Int

    var body: some View {
        Canvas { context, _ in
            TasteProfileArt(geo: geo).drawTilesLayer(skipping: held, in: &context)
        }
        .frame(width: geo.width, height: geo.height)
    }
}

/// Follow button, counts, coin, gem, rank label and the city chips with the black pill.
struct TasteProfileMotionLayer: View {
    let geo: TasteProfileGeo
    let frame: TasteProfileFrame

    var body: some View {
        Canvas { context, _ in
            TasteProfileArt(geo: geo, frame: frame).drawMotion(in: &context)
        }
        .frame(width: geo.width, height: geo.height)
    }
}
