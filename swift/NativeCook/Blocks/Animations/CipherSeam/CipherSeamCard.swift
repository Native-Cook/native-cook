import SwiftUI

/// The sample card of Cipher Seam, in its 210 x 135 pt design size (`CARD_WIDTH` x `CARD_HEIGHT`:
/// 0.52 of the 402 pt design width). `Card` of the React Native file.
struct CipherSeamCardFace: View {
    static let width: CGFloat = 210
    static let height: CGFloat = 135

    private static let radius: CGFloat = 11
    private static let mark = CipherSeamPalette.rgb(0x8F978C)

    var body: some View {
        // Border 1 + padding 13: the content starts 14 pt in.
        VStack(alignment: .leading, spacing: 0) {
            topRow
            Spacer(minLength: 0)
            Text(verbatim: "4821 0000 5577 0816")
                .font(.custom("Menlo-Regular", fixedSize: 10.5))
                .foregroundStyle(CipherSeamPalette.rgb(0xE2DFE9))
                .lineLimit(1)
                .fixedSize()
            Spacer(minLength: 0)
            bottomRow
        }
        .padding(14)
        .frame(width: Self.width, height: Self.height, alignment: .topLeading)
        .background { surface }
        .clipShape(RoundedRectangle(cornerRadius: Self.radius, style: .circular))
        .overlay {
            RoundedRectangle(cornerRadius: Self.radius, style: .circular)
                .strokeBorder(CipherSeamPalette.rgb(0xFFFFFF, alpha: 0x0D), lineWidth: 1)
        }
    }

    /// The face: a dark diagonal gradient with a faint highlight over the padding box.
    private var surface: some View {
        ZStack {
            LinearGradient(
                stops: [
                    Gradient.Stop(color: CipherSeamPalette.rgb(0x212027), location: 0),
                    Gradient.Stop(color: CipherSeamPalette.rgb(0x1C1B21), location: 0.55),
                    Gradient.Stop(color: CipherSeamPalette.rgb(0x18171C), location: 1),
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            LinearGradient(
                colors: [CipherSeamPalette.rgb(0xFFFFFF, alpha: 0x0C), CipherSeamPalette.rgb(0xFFFFFF, alpha: 0x00)],
                startPoint: .topLeading,
                endPoint: UnitPoint(x: 0.6, y: 0.7)
            )
            .padding(1)
        }
    }

    /// Chip and contactless mark on the left, the two overlapping circles on the right.
    private var topRow: some View {
        HStack(spacing: 0) {
            HStack(spacing: 8) {
                chip
                CipherSeamIcon(glyph: .wifi, size: 14, color: CipherSeamPalette.rgb(0xA9A6B4))
                    .rotationEffect(.degrees(90))
            }
            Spacer(minLength: 0)
            circles
        }
    }

    /// `chip`: 30 x 23, radius 6, with the centre pad and the two hairlines.
    private var chip: some View {
        ZStack {
            LinearGradient(
                colors: [CipherSeamPalette.rgb(0xF4F6EE), CipherSeamPalette.rgb(0xC9D4C6), CipherSeamPalette.rgb(0xEEE8D6)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            RoundedRectangle(cornerRadius: 3, style: .circular)
                .strokeBorder(Self.mark, lineWidth: 0.6)
                .frame(width: 12, height: 15)
            Rectangle()
                .fill(Self.mark)
                .frame(width: 30, height: 0.6)
            Rectangle()
                .fill(Self.mark)
                .frame(width: 0.6, height: 23)
        }
        .frame(width: 30, height: 23)
        .clipShape(RoundedRectangle(cornerRadius: 6, style: .circular))
    }

    /// `cardCircles`: a 15 pt disc and, 6 pt over it, a 16.5 pt one with a dark 1.5 pt ring.
    private var circles: some View {
        let light = CipherSeamPalette.rgb(0xF4F2F8)
        return ZStack(alignment: .topLeading) {
            Circle()
                .fill(light)
                .frame(width: 15, height: 15)
            Circle()
                .fill(light)
                .overlay { Circle().strokeBorder(CipherSeamPalette.rgb(0x1F1E25), lineWidth: 1.5) }
                .frame(width: 16.5, height: 16.5)
                .offset(x: 9, y: -0.75)
        }
        .frame(width: 25.5, height: 15.75, alignment: .topLeading)
    }

    /// Holder and expiry, spread over the width with a 40 pt spacer after them (`space-between`).
    private var bottomRow: some View {
        HStack(alignment: .bottom, spacing: 0) {
            field(label: "Card Holder", value: "Alex River")
            Spacer(minLength: 0)
            field(label: "Expiry", value: "08/29")
            Spacer(minLength: 0)
            Color.clear.frame(width: 40, height: 0)
        }
    }

    private func field(label: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            CipherSeamLabel(text: label, size: 6.5, color: CipherSeamPalette.rgb(0x8D889B), scales: false)
                .padding(.bottom, 2)
            CipherSeamLabel(text: value, size: 9, weight: .medium, color: CipherSeamPalette.rgb(0xE4E0EC), scales: false)
        }
    }
}

/// The token matrix the card turns into (`TokenField`): 16 rows of 35 small glyphs on a 5.7 pt
/// pitch (7 pt Courier Bold at 0.6 em plus 1.5 pt of spacing), 8 pt apart, over a smoked panel.
struct CipherSeamTokenField: View {
    /// Which of the eight glyph sets to show (it changes twelve times a second).
    let phase: Int

    var body: some View {
        let rows = CipherSeamField.glyphRows[phase % CipherSeamField.glyphRows.count]
        VStack(alignment: .leading, spacing: 0) {
            ForEach(0..<rows.count, id: \.self) { row in
                Text(verbatim: rows[row])
                    .font(.custom("Courier-Bold", fixedSize: 7))
                    .kerning(1.5)
                    .foregroundStyle(CipherSeamPalette.rgb(0xE9E6F1))
                    .lineLimit(1)
                    .fixedSize()
                    .frame(height: 8)
            }
        }
        .opacity(0.62)
        // Border 1 + padding 2.
        .padding(3)
        .frame(width: CipherSeamCardFace.width, height: CipherSeamCardFace.height, alignment: .topLeading)
        .background(CipherSeamPalette.rgb(0x05030E, alpha: 0x59))
        .clipShape(RoundedRectangle(cornerRadius: 11, style: .circular))
    }
}

/// One half of the card's plane (`Plane`): the card face or the token matrix, drawn at
/// `scale` from its top-left corner and clipped at the seam. Both halves of the one plane move
/// together; the left shows what is before the seam and the right what is after it.
struct CipherSeamPlane: View {
    enum Face {
        case card
        case tokens(phase: Int)
    }

    let face: Face
    /// The plane's left edge, in hero coordinates.
    let left: CGFloat
    /// The half that is clipped to the right of the seam.
    let isRight: Bool
    /// The hero's width; the seam is at half of it.
    let width: CGFloat
    /// `width / 402`.
    let scale: CGFloat

    var body: some View {
        let half = width / 2
        ZStack(alignment: .topLeading) {
            faceView
                .scaleEffect(scale, anchor: .topLeading)
                .offset(x: left - (isRight ? half : 0))
        }
        .frame(width: half, height: CipherSeamCardFace.height * scale, alignment: .topLeading)
        .clipped()
        .offset(x: isRight ? half : 0)
    }

    @ViewBuilder
    private var faceView: some View {
        switch face {
        case .card:
            CipherSeamCardFace()
        case .tokens(let phase):
            CipherSeamTokenField(phase: phase)
        }
    }
}
