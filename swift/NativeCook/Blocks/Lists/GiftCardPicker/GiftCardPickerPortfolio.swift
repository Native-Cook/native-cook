import SwiftUI
import UIKit

struct GiftCardPickerPortfolio: View {
    let width: CGFloat
    let height: CGFloat
    let topInset: CGFloat
    let bottomInset: CGFloat
    let scale: CGFloat
    let press: Bool
    let landing: Double
    let onGiftPress: (Bool) -> Void
    let onGift: () -> Void
    private var dy: CGFloat { topInset - 62 }
    private var inner: CGFloat { width - 40 }
    private var changeCopy: AttributedString {
        var copy = AttributedString("USD\u{2009}0.00 · 0.00% · All time")
        copy.foregroundColor = GiftCardPickerTheme.muted
        if let range = copy.range(of: "All time") {
            copy[range].foregroundColor = GiftCardPickerTheme.ink
        }
        return copy
    }

    var body: some View {
        ZStack(alignment: .topLeading) {
            GiftCardPickerTheme.page
            Circle().fill(.white).frame(width: 44, height: 44)
                .shadow(color: .black.opacity(0.05), radius: 12, y: 4)
                .overlay { GiftCardPickerGlyph(kind: .back).frame(width: 20, height: 20) }
                .position(x: 37, y: 84 + dy)
                .accessibilityLabel("Back").accessibilityAddTraits(.isButton)
            Text("Portfolio").font(.system(size: 17)).foregroundStyle(GiftCardPickerTheme.ink)
                .frame(width: width, height: 22).position(x: width / 2, y: 146 + dy)
            Text("USD 8,000.00").font(.system(size: 43, weight: .bold).monospacedDigit())
                .foregroundStyle(GiftCardPickerTheme.ink).frame(width: width, height: 58)
                .position(x: width / 2, y: 189 + dy).accessibilityAddTraits(.isHeader)
            Text(changeCopy)
                .font(.system(size: 17).monospacedDigit()).frame(width: width, height: 22)
                .position(x: width / 2, y: 235 + dy)
            HStack(spacing: 20) {
                GiftCardPickerTile(label: "Deposit", kind: .deposit)
                GiftCardPickerTile(label: "Analyze", kind: .analyze)
                GiftCardPickerTile(label: "Transfer", kind: .transfer)
            }
            .frame(width: inner, height: 87).position(x: width / 2, y: 277 + dy + 43.5)

            ZStack(alignment: .topLeading) {
                cardBackground
                Text("Cash").font(.system(size: 20, weight: .bold)).frame(height: 25).offset(x: 20, y: 20)
                Text("USD\u{2009}8,000.00").font(.system(size: 15).monospacedDigit()).frame(height: 22).offset(x: 20, y: 47)
                Text("Get 3% annual interest for free").font(.system(size: 15, weight: .semibold)).foregroundStyle(.white)
                    .frame(width: inner - 40, height: 47)
                    .background(GiftCardPickerTheme.orange, in: .rect(cornerRadius: 16, style: .continuous))
                    .offset(x: 20, y: 85).accessibilityAddTraits(.isButton)
            }
            .foregroundStyle(GiftCardPickerTheme.ink)
            .frame(width: inner, height: 150, alignment: .topLeading).position(x: width / 2, y: 383 + dy + 75)

            Button(action: onGift) {
                ZStack(alignment: .topLeading) {
                    LinearGradient(stops: [.init(color: GiftCardPickerTheme.tile, location: 0), .init(color: GiftCardPickerTheme.tile, location: 0.34), .init(color: Color(hex: 0xFFD2B4), location: 0.62), .init(color: Color(hex: 0xFF9A5E), location: 1)], startPoint: .leading, endPoint: .trailing)
                    GiftCardPickerImage(name: "GiftCardPicker-tile-art.jpg").resizable()
                        .frame(width: 142, height: 87).offset(x: inner - 142)
                    VStack(alignment: .leading, spacing: 0) {
                        Text("Choose your").frame(height: 24)
                        Text("gift stock").frame(height: 24)
                    }
                    .font(.system(size: 20, weight: .bold)).foregroundStyle(GiftCardPickerTheme.ink2)
                    .offset(x: 20, y: 19)
                }
                .frame(width: inner, height: 87, alignment: .topLeading)
                .clipShape(.rect(cornerRadius: 24, style: .continuous))
                .shadow(color: .black.opacity(0.05), radius: 12, y: 4)
                .scaleEffect((press ? 0.97 : 1) * (1 + 0.03 * sin(min(1, max(0, (landing - 2_120.0 / 2_540.0) / (420.0 / 2_540.0))) * .pi)))
                .overlay {
                    RoundedRectangle(cornerRadius: 24, style: .continuous)
                        .stroke(GiftCardPickerTheme.orange.opacity(0.8 * sin(min(1, max(0, (landing - 2_120.0 / 2_540.0) / (420.0 / 2_540.0))) * .pi)), lineWidth: 3)
                }
            }
            .buttonStyle(GiftCardPickerPressStyle(onChanged: onGiftPress))
            .accessibilityLabel("Choose your gift stock")
            .position(x: width / 2, y: 557 + dy + 43.5)

            ZStack(alignment: .leading) {
                cardBackground
                VStack(alignment: .leading, spacing: 2) {
                    Text("Transactions").font(.system(size: 20, weight: .bold)).frame(height: 25)
                    Text("4 in June").font(.system(size: 15).monospacedDigit()).frame(height: 22)
                }.padding(.horizontal, 20)
            }
            .foregroundStyle(GiftCardPickerTheme.ink)
            .frame(width: inner, height: 85, alignment: .leading).position(x: width / 2, y: 665 + dy + 42.5)
            ZStack(alignment: .topLeading) {
                cardBackground
                Text("How to grow your gift").font(.system(size: 30, weight: .heavy)).foregroundStyle(Color(hex: 0x3A3A3E))
                    .frame(width: inner - 40, alignment: .leading).padding(.top, 18).padding(.horizontal, 20)
            }
            .frame(width: inner, height: 160, alignment: .topLeading).position(x: width / 2, y: 771 + dy + 80)
            GiftCardPickerBlur(intensity: 22).frame(width: width, height: max(0, height - (768 + dy)))
                .offset(y: 768 + dy).allowsHitTesting(false)
            GiftCardPickerTabBar(width: inner)
                // RN: bottom = 874 - 793 - 58 + (safeBottom - 34).
                .position(x: width / 2, y: height - (23 + bottomInset - 34) - 29)
        }
        .frame(width: width, height: height, alignment: .topLeading)
    }

    private var cardBackground: some View {
        RoundedRectangle(cornerRadius: 24, style: .continuous).fill(GiftCardPickerTheme.tile)
            .shadow(color: .black.opacity(0.05), radius: 12, y: 4)
    }
}

struct GiftCardPickerTile: View {
    let label: String
    let kind: GiftCardPickerGlyph.Kind
    var body: some View {
        VStack(spacing: 8) {
            GiftCardPickerGlyph(kind: kind).frame(width: 28, height: 28)
            Text(label).font(.system(size: 13, weight: .semibold)).foregroundStyle(GiftCardPickerTheme.ink)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(GiftCardPickerTheme.tile, in: .rect(cornerRadius: 24, style: .continuous))
        .shadow(color: .black.opacity(0.05), radius: 12, y: 4)
        .accessibilityElement(children: .ignore).accessibilityLabel(label).accessibilityAddTraits(.isButton)
    }
}

struct GiftCardPickerTabBar: View {
    let width: CGFloat
    var body: some View {
        HStack(spacing: 0) {
            HStack(spacing: 0) {
                GiftCardPickerTab(label: "Home", icon: .home, badge: true)
                GiftCardPickerTab(label: "Pay", icon: .pay)
                GiftCardPickerTab(label: "Invest", icon: .invest, active: true)
                GiftCardPickerTab(label: "Invite", icon: nil)
            }
            .padding(.horizontal, 4).frame(width: 291, height: 58)
            .background { GiftCardPickerBlur(intensity: 40).overlay(.white.opacity(0.72)) }
            .clipShape(Capsule())
            Spacer(minLength: 0)
            GiftCardPickerGlyph(kind: .chat, color: Color(hex: 0x6D6D72)).frame(width: 26, height: 26)
                .frame(width: 61, height: 61)
                .background { GiftCardPickerBlur(intensity: 40).overlay(.white.opacity(0.72)) }
                .clipShape(Circle()).accessibilityLabel("Chat").accessibilityAddTraits(.isButton)
        }
        .frame(width: width, height: 58)
    }
}

struct GiftCardPickerTab: View {
    let label: String
    let icon: GiftCardPickerGlyph.Kind?
    var active = false
    var badge = false
    var body: some View {
        VStack(spacing: 5) {
            Group {
                if let icon { GiftCardPickerGlyph(kind: icon, color: Color(hex: 0x6D6D72)).frame(width: 26, height: 26) }
                else { Text("$100").font(.system(size: 17, weight: .semibold)).foregroundStyle(Color(hex: 0x6D6D72)).frame(height: 26) }
            }
            .overlay(alignment: .topTrailing) {
                if badge {
                    Text("1").font(.system(size: 11, weight: .bold)).foregroundStyle(.white)
                        .frame(minWidth: 16).frame(height: 16).background(Color(hex: 0xFF3B30), in: Circle())
                        .offset(x: 8, y: -5)
                }
            }
            Text(label).font(.system(size: 10, weight: active ? .semibold : .medium))
                .foregroundStyle(active ? GiftCardPickerTheme.orange : Color(hex: 0x6D6D72))
        }
        .frame(maxWidth: .infinity).frame(height: 50)
        .background(active ? Color(hex: 0x767680, opacity: 0.06) : .clear, in: Capsule())
        .accessibilityElement(children: .ignore).accessibilityLabel(label)
        .accessibilityAddTraits(active ? .isSelected : [])
    }
}

func GiftCardPickerImage(name: String) -> Image {
    guard let image = UIImage(named: name) else {
        assertionFailure("Missing Gift Card Picker image: \(name)")
        return Image(uiImage: UIImage())
    }
    return Image(uiImage: image)
}
