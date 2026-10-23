import SwiftUI

/// The sample screen the drawer moves aside: a shop with a top bar, a search field, the content of
/// the chosen destination and a tab bar. Replace it with the app's own screens.
struct DepthSideDrawerPage: View {
    /// The destination's name; the page under it is the shop front when `index` is 0.
    var title: String
    /// The destination's place in the menu, which picks what its shelf holds.
    var index: Int
    /// The destination's id: the tab with the same id is lit.
    var active: String
    var accent: Color
    var insets: EdgeInsets
    var onMenu: () -> Void
    var onTab: (String) -> Void

    private struct Product {
        var label: String
        var color: Color
        /// An SF Symbol.
        var glyph: String
    }

    private struct Tab {
        var id: String
        var label: String
        var icon: String
    }

    private enum Palette {
        static let background = Color(hex: 0xFAFCFE)
        static let ink = Color(hex: 0x2B2B33)
        static let mute = Color(hex: 0xA9B0BA)
        static let outline = Color(hex: 0xEEF1F4)
        static let tabIdle = Color(hex: 0xB0B5BF)
        static let alert = Color(hex: 0xF0443C)
        static let glyph = Color.white.opacity(0.8)
        static let brandSky = [Color(hex: 0x7ED8F5), Color(hex: 0x35A7F0)]
        static let night = Color(hex: 0x23232B)
    }

    private enum Metrics {
        static let margin = 20.0
        static let gap = 12.0
        static let tile = 112.0
        static let tileRadius = 16.0
        static let brand = CGSize(width: 240, height: 130)
        static let dealHeight = 150.0
        static let tabs = 50.0
        /// The old destination fades out while the new one fades in.
        static let swap = 0.25
        /// A symbol is drawn larger than an icon-font glyph of the same size.
        static let symbol = 0.72
    }

    private static let products = [
        Product(label: "Laptops", color: Color(hex: 0xF6B8C8), glyph: "laptopcomputer"),
        Product(label: "Headphones", color: Color(hex: 0xFBC813), glyph: "headphones"),
        Product(label: "Storage", color: Color(hex: 0xF4A6A6), glyph: "mediastick"),
        Product(label: "Tablets", color: Color(hex: 0xBCF5C8), glyph: "ipad"),
        Product(label: "Watches", color: Palette.night, glyph: "applewatch"),
        Product(label: "Earbuds", color: Color(hex: 0xBCF5C8), glyph: "earbuds"),
        Product(label: "Cameras", color: Color(hex: 0xF6B8C8), glyph: "camera"),
    ]

    /// The bar's tabs. A tab opens the menu item that has its id.
    private static let bar = [
        Tab(id: "home", label: "Home", icon: "house"),
        Tab(id: "cart", label: "Cart", icon: "cart"),
        Tab(id: "wishlist", label: "Wishlist", icon: "heart"),
        Tab(id: "account", label: "Account", icon: "person"),
    ]

    var body: some View {
        VStack(spacing: 0) {
            topBar
                .padding(.top, insets.top - 4)
            search
                .padding(.top, 5)
                .padding(.horizontal, Metrics.margin)

            ZStack(alignment: .topLeading) {
                Group {
                    if index == 0 { front } else { shelf }
                }
                .id(active)
                .transition(.opacity)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .clipped()
            .animation(.linear(duration: Metrics.swap), value: active)

            tabs
        }
        .background(Palette.background)
    }

    // MARK: Bars

    private var topBar: some View {
        HStack(spacing: 16) {
            Button(action: onMenu) {
                VStack(spacing: 5) {
                    ForEach(0..<3, id: \.self) { _ in
                        Capsule().fill(Palette.ink).frame(width: 22, height: 2)
                    }
                }
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Menu")

            Spacer(minLength: 0)

            symbol("envelope", size: 22, color: Palette.ink)
            symbol("bell", size: 22, color: Palette.ink)
                .overlay(alignment: .topLeading) {
                    Circle().fill(Palette.alert).frame(width: 7, height: 7).offset(x: 2, y: 1)
                }
        }
        .frame(height: 44)
        .padding(.leading, 9)
        .padding(.trailing, Metrics.margin)
    }

    private var search: some View {
        HStack(spacing: 12) {
            symbol("magnifyingglass", size: 18, color: Palette.ink)
            Text("Find your items")
                .font(.system(size: 14))
                .foregroundStyle(Palette.mute)
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)
            symbol("viewfinder", size: 18, color: Palette.ink)
        }
        .padding(.horizontal, 14)
        .frame(height: 44)
        .background(Color.white, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(Palette.outline, lineWidth: 1))
    }

    private var tabs: some View {
        HStack(spacing: 0) {
            ForEach(Self.bar, id: \.id) { tab in
                let lit = tab.id == active
                Button {
                    onTab(tab.id)
                } label: {
                    VStack(spacing: 3) {
                        symbol(lit ? tab.icon + ".fill" : tab.icon, size: 22, color: lit ? accent : Palette.tabIdle)
                        Text(tab.label)
                            .font(.system(size: 11))
                            .foregroundStyle(lit ? accent : Palette.tabIdle)
                            .lineLimit(1)
                            .frame(height: 14)
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: Metrics.tabs)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(tab.label)
                .accessibilityAddTraits(lit ? .isSelected : [])
            }
        }
        .padding(.horizontal, 8)
        .padding(.bottom, insets.bottom)
        .background {
            UnevenRoundedRectangle(topLeadingRadius: 24, topTrailingRadius: 24, style: .continuous)
                .fill(Color.white)
                .shadow(color: Palette.ink.opacity(0.06), radius: 8, y: -4)
        }
    }

    // MARK: Destinations

    /// The shop front: three shelves, each running off the right edge or filling the width.
    private var front: some View {
        VStack(alignment: .leading, spacing: 0) {
            heading("Recommended")
            row {
                ForEach(Self.products.prefix(4), id: \.label) { product in
                    tile(product, glyph: 48).frame(width: Metrics.tile, height: Metrics.tile)
                }
            }
            heading("Popular Brand")
            row {
                RoundedRectangle(cornerRadius: Metrics.tileRadius, style: .continuous)
                    .fill(LinearGradient(colors: Palette.brandSky, startPoint: .top, endPoint: .bottom))
                    .overlay { symbol("ipad", size: 64, color: Palette.glyph) }
                    .overlay(alignment: .bottomTrailing) {
                        Text("Northwind")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(.white)
                            .padding(.trailing, 14)
                            .padding(.bottom, 12)
                    }
                    .frame(width: Metrics.brand.width, height: Metrics.brand.height)
                tile(Self.products[4], glyph: 56).frame(width: Metrics.brand.width, height: Metrics.brand.height)
            }
            heading("Daily Deals")
            row {
                tile(Self.products[5], glyph: 56).frame(height: Metrics.dealHeight)
                tile(Self.products[6], glyph: 56).frame(height: Metrics.dealHeight)
            }
        }
    }

    /// Any other destination: its name over four tiles.
    private var shelf: some View {
        let picks = (0..<4).map { Self.products[(index * 2 + $0) % Self.products.count] }
        return VStack(alignment: .leading, spacing: 0) {
            Text(title)
                .font(.system(size: 24, weight: .bold))
                .foregroundStyle(Palette.ink)
                .lineLimit(1)
                .frame(height: 30)
                .padding(.top, 20)
                .padding(.horizontal, Metrics.margin)
            Text("\(picks.count) items")
                .font(.system(size: 13))
                .foregroundStyle(Palette.mute)
                .frame(height: 18)
                .padding(.top, 2)
                .padding(.bottom, 16)
                .padding(.horizontal, Metrics.margin)
            ForEach([0, 2], id: \.self) { first in
                row {
                    tile(picks[first], glyph: 56).frame(height: Metrics.dealHeight)
                    tile(picks[first + 1], glyph: 56).frame(height: Metrics.dealHeight)
                }
                .padding(.bottom, Metrics.gap)
            }
        }
    }

    private func heading(_ title: String) -> some View {
        HStack {
            Text(title)
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(Palette.ink)
                .lineLimit(1)
            Spacer(minLength: 8)
            Text("View all")
                .font(.system(size: 12))
                .foregroundStyle(Palette.mute)
        }
        .frame(height: 22)
        .padding(.top, 20)
        .padding(.bottom, Metrics.gap)
        .padding(.horizontal, Metrics.margin)
    }

    /// A row of tiles from the left margin; what does not fit runs off the right edge.
    private func row(@ViewBuilder _ tiles: () -> some View) -> some View {
        HStack(spacing: Metrics.gap, content: tiles)
            .padding(.horizontal, Metrics.margin)
            // The minimum keeps the row as wide as the page when its tiles are wider.
            .frame(minWidth: 0, maxWidth: .infinity, alignment: .leading)
    }

    private func tile(_ product: Product, glyph: Double) -> some View {
        RoundedRectangle(cornerRadius: Metrics.tileRadius, style: .continuous)
            .fill(product.color)
            .overlay { symbol(product.glyph, size: glyph, color: Palette.glyph) }
            .overlay(alignment: .bottomLeading) {
                Text(product.label)
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                    .padding(.leading, 10)
                    .padding(.bottom, 8)
            }
    }

    /// An SF Symbol in a box of `size` points, drawn as large as an icon font's glyph of that size.
    private func symbol(_ name: String, size: Double, color: Color) -> some View {
        Image(systemName: name)
            .font(.system(size: size * Metrics.symbol))
            .foregroundStyle(color)
            .frame(width: size, height: size)
            .accessibilityHidden(true)
    }
}
