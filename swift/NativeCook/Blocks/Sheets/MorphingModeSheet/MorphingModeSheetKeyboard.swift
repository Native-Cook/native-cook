import SwiftUI

private enum K {
    static let rows = ["QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM"]
    static let words = ["I", "The", "I’m"]

    static let bed = Color(hex: 0xd3d6dc)
    static let key = Color.white
    static let special = Color(hex: 0xaeb3bd)
    static let ink = Color(hex: 0x111111)
    static let glyph = Color(hex: 0x4d5057)
    static let side = 3.0
    static let gap = 6.0
    static let keyHeight = 43.0
    static let rowGap = 11.0
    static let suggestions = 44.0
}

enum MorphingModeSheetKeyboardMetrics {
    /// Height of the drawn keyboard above the home indicator's strip.
    static let height = K.suggestions + K.keyHeight * 4 + K.rowGap * 3 + 8 + 50
}

/// A drawing of the system's light keyboard, for the page behind the sheet. It is not a keyboard:
/// it takes no input, and the block never opens the real one.
struct MorphingModeSheetKeyboard: View {
    let width: Double
    let bottomInset: Double

    var body: some View {
        let key = (width - K.side * 2 - K.gap * 9) / 10
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                ForEach(Array(K.words.enumerated()), id: \.offset) { index, word in
                    Text(word)
                        .font(.system(size: 16, weight: index == 1 ? .medium : .regular))
                        .foregroundStyle(K.ink)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .overlay(alignment: .leading) {
                            if index > 0 {
                                Rectangle()
                                    .fill(Color.rgba(60, 60, 67, 0.25))
                                    .frame(width: 2.0 / 3)
                                    .padding(.vertical, 12)
                            }
                        }
                }
            }
            .frame(height: K.suggestions)

            VStack(spacing: K.rowGap) {
                HStack(spacing: K.gap) {
                    ForEach(letters(0), id: \.self) { letter($0, width: key) }
                }
                HStack(spacing: K.gap) {
                    ForEach(letters(1), id: \.self) { letter($0, width: key) }
                }
                HStack(spacing: 0) {
                    special(width: key * 1.3) { Image(systemName: "shift").font(.system(size: 18)) }
                    Spacer(minLength: 0)
                    HStack(spacing: K.gap) {
                        ForEach(letters(2), id: \.self) { letter($0, width: key) }
                    }
                    Spacer(minLength: 0)
                    special(width: key * 1.3) { Image(systemName: "delete.left").font(.system(size: 17)) }
                }
                HStack(spacing: K.gap) {
                    special(width: key * 1.3 + K.gap + key * 0.9) { Text("123").font(.system(size: 16)) }
                    keyFace(fill: K.key) { Text("space").font(.system(size: 16)) }
                        .frame(maxWidth: .infinity)
                    special(width: key * 2.3 + K.gap) { Text("return").font(.system(size: 16)) }
                }
            }
            .padding(.horizontal, K.side)

            HStack {
                Image(systemName: "face.smiling").font(.system(size: 22))
                Spacer(minLength: 0)
                Image(systemName: "mic").font(.system(size: 21))
            }
            .foregroundStyle(K.glyph)
            .padding(.horizontal, 22)
            .frame(height: 50)
            .padding(.top, 8)
        }
        .padding(.bottom, max(bottomInset - 20, 0))
        .frame(width: width)
        .background(K.bed)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private func letters(_ row: Int) -> [String] { K.rows[row].map(String.init) }

    private func letter(_ label: String, width: Double) -> some View {
        keyFace(fill: K.key) { Text(label).font(.system(size: 23)) }
            .frame(width: width)
    }

    private func special<Label: View>(width: Double, @ViewBuilder label: () -> Label) -> some View {
        keyFace(fill: K.special, label: label)
            .frame(width: width)
    }

    private func keyFace<Label: View>(fill: Color, @ViewBuilder label: () -> Label) -> some View {
        label()
            .foregroundStyle(K.ink)
            .frame(maxWidth: .infinity)
            .frame(height: K.keyHeight)
            .background(fill, in: RoundedRectangle(cornerRadius: 8))
            .compositingGroup()
            .shadow(color: .black.opacity(0.3), radius: 0, y: 1)
    }
}
