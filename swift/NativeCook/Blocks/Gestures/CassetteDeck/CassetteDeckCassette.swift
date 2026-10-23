import SwiftUI

/// One cassette of the endless strip: the shell with its side faces, the tape packs in the label window,
/// the reels (with their ghosts while winding), the label, and the sheen that slides over the plastic.
struct CassetteDeckCassette: View {
    let slot: Int
    let state: CassetteDeckCassetteState
    let metrics: CassetteDeckMetrics

    private typealias Motion = CassetteDeckMotion
    private typealias Palette = CassetteDeckTheme.Palette
    private typealias Layout = CassetteDeckTheme.Layout

    var body: some View {
        let s = metrics.scale
        let c = Layout.cassette
        ZStack(alignment: .topLeading) {
            sideFaces
            Image(uiImage: CassetteDeckArt.cassette)
                .resizable()
                .interpolation(.high)
                .frame(width: c.w * s, height: c.h * s)
            tapePacks
            reels
            label
            sheen
        }
        .frame(width: c.w * s, height: c.h * s, alignment: .topLeading)
        .opacity(state.opacity)
        .modifier(CassetteDeckPose(
            x: state.x * metrics.width,
            y: state.shake * s,
            yaw: state.yaw,
            distance: Motion.Pager.perspective * s
        ))
        .offset(x: c.x * s, y: metrics.offsetY + c.y * s)
    }

    // The shell's side faces show as it yaws; each grows from the face it hides behind.
    private var sideFaces: some View {
        let s = metrics.scale
        let c = Layout.cassette
        let inset = Layout.edgeInset
        let height = c.h * s - inset.top - inset.bottom
        let width = Motion.Material.edge * s
        return ZStack(alignment: .topLeading) {
            if state.edgeLeft > 0 {
                UnevenRoundedRectangle(topLeadingRadius: 6 * s, bottomLeadingRadius: 6 * s, style: .circular)
                    .fill(LinearGradient(
                        colors: Palette.edgeLeft,
                        startPoint: UnitPoint(x: 0, y: 0),
                        endPoint: UnitPoint(x: 1, y: 0)
                    ))
                    .frame(width: width, height: height)
                    .scaleEffect(x: state.edgeLeft, y: 1, anchor: .trailing)
                    .offset(x: -width + s, y: inset.top)
            }
            if state.edgeRight > 0 {
                UnevenRoundedRectangle(bottomTrailingRadius: 6 * s, topTrailingRadius: 6 * s, style: .circular)
                    .fill(LinearGradient(
                        colors: Palette.edgeRight,
                        startPoint: UnitPoint(x: 0, y: 0),
                        endPoint: UnitPoint(x: 1, y: 0)
                    ))
                    .frame(width: width, height: height)
                    .scaleEffect(x: state.edgeRight, y: 1, anchor: .leading)
                    .offset(x: c.w * s - 3 * s, y: inset.top)
            }
        }
    }

    // The dark band through the label, where the tape packs show around the hubs.
    private var tapePacks: some View {
        let s = metrics.scale
        let w = Layout.window
        let pack = Layout.tapePack.max * 2 * s
        let scales = [state.leftPack, state.rightPack]
        return ZStack(alignment: .topLeading) {
            ForEach(0..<2, id: \.self) { i in
                Circle()
                    .fill(Palette.tape)
                    .overlay { Circle().strokeBorder(Palette.tapeEdge, lineWidth: s) }
                    .frame(width: pack, height: pack)
                    .scaleEffect(scales[i])
                    .offset(x: (Layout.reels[i].cx - w.x) * s - pack / 2, y: (Layout.reels[i].cy - w.y) * s - pack / 2)
            }
        }
        .frame(width: w.w * s, height: w.h * s, alignment: .topLeading)
        .clipShape(Capsule(style: .circular))
        .offset(x: w.x * s, y: w.y * s)
    }

    // Angular motion blur when winding: ghost copies of the hub trail it by the angle turned in one 60 Hz shutter.
    private var reels: some View {
        let size = Layout.reelSize * metrics.scale
        let angles = [state.leftAngle, state.rightAngle]
        return ZStack(alignment: .topLeading) {
            if state.ghostAmount > 0 {
                ForEach(0..<2, id: \.self) { i in
                    ForEach(1...2, id: \.self) { n in
                        reel(i, size: size, angle: angles[i] - state.ghostLag * Double(n))
                            .opacity(state.ghostAmount * (0.55 - 0.15 * Double(n)))
                    }
                }
            }
            ForEach(0..<2, id: \.self) { i in
                reel(i, size: size, angle: angles[i])
            }
        }
    }

    // The hub turns about its own centre first, then sits at the reel's place on the label.
    private func reel(_ i: Int, size: CGFloat, angle: Double) -> some View {
        let s = metrics.scale
        return Image(uiImage: CassetteDeckArt.reel)
            .resizable()
            .interpolation(.high)
            .frame(width: size, height: size)
            .rotationEffect(.radians(angle))
            .offset(x: (Layout.reels[i].cx - Layout.reelSize / 2) * s, y: (Layout.reels[i].cy - Layout.reelSize / 2) * s)
    }

    private var label: some View {
        let s = metrics.scale
        let track = CassetteDeckTheme.tracks[slot]
        let c = Layout.cassette
        let cover = Layout.cover
        let title = Layout.title
        let artist = Layout.artist
        let mark = Layout.wordmark
        return ZStack(alignment: .topLeading) {
            Image(uiImage: CassetteDeckArt.covers[track.cover])
                .resizable()
                .interpolation(.high)
                .frame(width: cover.size * s, height: cover.size * s)
                .clipShape(RoundedRectangle(cornerRadius: cover.radius * s))
                .offset(x: cover.x * s, y: cover.y * s)
            Text(verbatim: track.title)
                .font(.system(size: title.size * s, weight: .bold))
                .kerning(title.tracking * s)
                .foregroundStyle(Palette.labelInk)
                .lineLimit(1)
                .fixedSize()
                .offset(x: title.x * s, y: title.y * s)
            Text(verbatim: track.artist)
                .font(.system(size: artist.size * s, weight: .regular))
                .kerning(artist.tracking * s)
                .foregroundStyle(Palette.labelMute)
                .lineLimit(1)
                .fixedSize()
                .offset(x: artist.x * s, y: artist.y * s)
            Text(verbatim: CassetteDeckTheme.brand)
                .font(.system(size: mark.size * s, weight: .black))
                .kerning(mark.tracking * s)
                .foregroundStyle(Palette.wordmark)
                .lineLimit(1)
                .fixedSize()
                .frame(width: c.w * s - mark.right * s, alignment: .trailing)
                .offset(y: mark.y * s)
        }
    }

    // A sheen fixed in the room slides over the plastic as the cassette moves.
    private var sheen: some View {
        let s = metrics.scale
        let c = Layout.cassette
        let clipWidth = c.w * s - 4 * s
        let clipHeight = c.h * s - 7 * s
        return ZStack(alignment: .topLeading) {
            LinearGradient(
                stops: Palette.sheen,
                startPoint: UnitPoint(x: 0, y: 0.2),
                endPoint: UnitPoint(x: 1, y: 0.8)
            )
            .frame(width: c.w * 2 * s, height: clipHeight)
            .opacity(state.sheenOpacity)
            .offset(x: -c.w * 0.5 * s - state.x * Motion.Material.sheenTravel * c.w * s)
        }
        .frame(width: clipWidth, height: clipHeight, alignment: .topLeading)
        .clipShape(RoundedRectangle(cornerRadius: 7 * s))
        .offset(x: s, y: s)
    }
}
