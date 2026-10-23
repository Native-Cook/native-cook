import SwiftUI

/// What a flap face shows: a photo, or a digit on a black card.
enum TasteProfileFace {
    case photo(Int)
    case digit(Character, inkLeft: Double, inkWidth: Double)

    var isPhoto: Bool {
        if case .photo = self { return true }
        return false
    }
}

/// A split-flap cell turning from face A to face B: the halves behind the falling flap, the flap with its
/// motion-blur ghosts, the shading and the hinge seam, in perspective (the React Native build's `drawCell`).
struct TasteProfileCell: View {
    typealias M = TasteProfileMotion

    let a: TasteProfileFace
    let b: TasteProfileFace
    /// The cell in points, from the board's top-left.
    let x: Double
    let y: Double
    let w: Double
    let h: Double
    /// Angle of the falling flap (-1: none), and, while none falls, the landing bounce in degrees.
    let deg: Double
    let speed: Double
    let persp: Double
    let cardAlpha: Double
    let seam: Double
    let run: TasteProfileTheme.GlyphRun
    let cardRadius: Double
    let geo: TasteProfileGeo

    private var hinge: Double { (((y + h / 2) * geo.pixel).rounded() / geo.pixel) - y }

    var body: some View {
        ZStack(alignment: .topLeading) {
            if deg < 0 {
                half(a, top: true, shade: 0)
                if speed > 0.01 {
                    half(a, top: false, shade: M.Board.cover * 0.5 * sin(speed * .pi / 180))
                    flap(from: a, to: a, deg: 180 - speed, alpha: 1)
                } else {
                    half(a, top: false, shade: 0)
                }
            } else {
                let rad = deg * .pi / 180
                half(b, top: true, shade: deg < 90 ? M.Board.uncover * cos(rad) : 0)
                half(a, top: false, shade: deg > 90 ? M.Board.cover * -cos(rad) : 0)
                let step = speed * M.Board.shutter / Double(M.Board.ghostAlpha.count)
                ForEach([2, 1, 0], id: \.self) { index in
                    let ghost = deg - Double(index + 1) * step
                    if ghost > 0 { flap(from: a, to: b, deg: ghost, alpha: M.Board.ghostAlpha[index]) }
                }
                flap(from: a, to: b, deg: deg, alpha: 1)
            }
            if seam > 0 {
                Rectangle()
                    .fill(a.isPhoto ? Color.black : TasteProfileTheme.cardGap)
                    .opacity(a.isPhoto ? M.Board.seam * seam : 0.9 * seam)
                    .frame(width: w, height: 1.5)
                    .offset(y: hinge - 0.75)
            }
        }
        .frame(width: w, height: h, alignment: .topLeading)
        .offset(x: x, y: y)
    }

    private func flap(from: TasteProfileFace, to: TasteProfileFace, deg: Double, alpha: Double) -> some View {
        let rad = deg * .pi / 180
        let upper = deg < 90
        let phi = 180 - deg
        let shade = upper ? (from.isPhoto ? M.Board.flapDark * sin(rad) : -M.Board.glint * sin(2 * rad))
                          : -M.Board.flapLight * sin(phi * .pi / 180)
        let matrix = TasteProfileProjection.flap(deg: upper ? deg : phi, hx: w / 2, hy: hinge, d: persp, below: !upper)
        return half(upper ? from : to, top: upper, shade: shade)
            .projectionEffect(matrix)
            .opacity(alpha)
    }

    /// One half of a face, clipped at the hinge, with a black (+) or white (−) shade over it.
    private func half(_ face: TasteProfileFace, top: Bool, shade: Double) -> some View {
        let rounded = !face.isPhoto
        return ZStack(alignment: .topLeading) {
            content(face, top: top)
            if shade != 0 {
                Group {
                    if rounded {
                        RoundedRectangle(cornerRadius: cardRadius).fill(shade > 0 ? Color.black : Color.white)
                            .opacity(abs(shade) * cardAlpha)
                    } else {
                        Rectangle().fill(shade > 0 ? Color.black : Color.white).opacity(abs(shade))
                    }
                }
                .frame(width: w, height: h)
            }
        }
        .frame(width: w, height: h, alignment: .topLeading)
        .mask(alignment: top ? .top : .bottom) {
            Rectangle().frame(width: w + 8, height: top ? hinge : h - hinge)
        }
    }

    @ViewBuilder
    private func content(_ face: TasteProfileFace, top: Bool) -> some View {
        switch face {
        case let .photo(index):
            Image(uiImage: TasteProfileAssets.photos[index])
                .resizable()
                .scaledToFill()
                .frame(width: w, height: h)
                .clipped()
        case let .digit(character, inkLeft, inkWidth):
            ZStack(alignment: .topLeading) {
                if cardAlpha > 0 {
                    RoundedRectangle(cornerRadius: cardRadius)
                        .fill(top ? TasteProfileTheme.cardTop : TasteProfileTheme.cardBottom)
                        .opacity(cardAlpha)
                        .frame(width: w, height: h)
                }
                if cardAlpha < 0.999 {
                    TasteProfileGlyph(character: character, inkLeft: inkLeft, inkWidth: inkWidth, run: run,
                                      color: run.color, geo: geo, ox: x, oy: y)
                }
                if cardAlpha > 0.001 {
                    TasteProfileGlyph(character: character, inkLeft: inkLeft, inkWidth: inkWidth, run: run,
                                      color: .white, geo: geo, ox: x, oy: y)
                        .opacity(min(1, cardAlpha))
                }
            }
            .frame(width: w, height: h, alignment: .topLeading)
        }
    }
}
