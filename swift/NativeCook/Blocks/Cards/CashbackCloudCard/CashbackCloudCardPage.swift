import SwiftUI

// Immutable display list: the timeline never invalidates this view.
struct CashbackCloudCardPage: View {
    var top: Double
    @Environment(\.displayScale) private var displayScale
    private let art = CashbackCloudCardResources.shared
    private func c(_ hex: UInt32) -> Color { CashbackCloudCardResources.color(hex) }
    var body: some View {
        Canvas { ctx, size in
            let _ = art
            let w = size.width - 32
            let y = top + 120
            func rect(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ radius: Double, _ color: UInt32) {
                ctx.fill(Path(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerRadius: radius), with: .color(c(color)))
            }
            func text(_ value: String, _ x: Double, _ y: Double, _ size: Double, _ weight: String, _ color: UInt32, _ tracking: Double = 0) {
                let base = Text(value).font(.custom(weight == "SemiBold" ? "Inter-SemiBold" : "Inter24pt-" + weight, fixedSize: size)).foregroundColor(c(color))
                let t = tracking == 0 ? base : base.tracking(tracking)
                ctx.draw(t, at: CGPoint(x: x, y: y), anchor: .topLeading)
            }
            func icon(_ glyph: UInt32, _ x: Double, _ y: Double, _ size: Double, _ color: UInt32) {
                let t = Text(String(UnicodeScalar(glyph)!)).font(.custom("Ionicons", fixedSize: size)).foregroundColor(c(color))
                ctx.draw(t, at: CGPoint(x: x, y: y), anchor: .topLeading)
            }
            rect(0, 0, size.width, size.height, 0, 0xEEF3FF)
            text("Rewards", 20, top + 12 + (44 - 37) / 2 + 1.0 / 3, 30, "Bold", 0x1E2A4A, -0.5)
            rect(size.width - 64, top + 12, 44, 44, 22, 0xFFFFFF)
            icon(62591, size.width - 64 + 11.5, top + 12 + 11.5, 21, 0x1E2A4A)
            text("Main balance", 20, top + 70 - 1.0 / 3, 14, "Regular", 0x5A6B8F)
            var card = ctx
            card.clip(to: Path(roundedRect: CGRect(x: 16, y: y, width: w, height: 430), cornerRadius: 30))
            // Match RN's overflow-hidden view: composite the contents before clipping the edge.
            card.drawLayer { layer in
                layer.fill(Path(CGRect(x: 16, y: y, width: w, height: 430)), with: .color(c(0x3D7BFF)))
                layer.fill(Path(roundedRect: CGRect(x: 16, y: y + 280, width: w, height: 190), cornerRadius: 40), with: .color(.white))
            }
            let newWidth = ceil(ctx.resolve(Text("New").font(.custom("Inter-SemiBold", fixedSize: 14))).measure(in: CGSize(width: 200, height: 50)).width * displayScale) / displayScale + 28
            rect(size.width - 34 - newWidth, y + 18, newWidth, 30, 15, 0xD7F5E2)
            text("New", size.width - 34 - newWidth + 14, y + 18 + 6.5 - 1.0 / 3, 14, "SemiBold", 0x1F7A45)
            text("Cashback this week", 42, y + 306 + 53 + 4 + 1.0 / 3, 16, "Regular", 0x5A6B8F)
            let hint = "Squeeze the cloud to collect"
            let hintWidth = ctx.resolve(Text(hint).font(.custom("Inter-SemiBold", fixedSize: 14))).measure(in: CGSize(width: 1000, height: 50)).width
            let hx = (size.width - hintWidth - 26) / 2
            icon(62300, hx, y + 448, 18, 0x5A6B8F)
            text(hint, hx + 26, y + 448 + 2.0 / 3, 14, "SemiBold", 0x5A6B8F)
            for i in 0..<2 {
                let ry = y + 488 + Double(i) * 70
                rect(16, ry, w, 60, 18, 0xFFFFFF)
                rect(30, ry + 12, 36, 36, 12, 0xE6EEFF)
                icon(i == 0 ? 61898 : 61955, 39, ry + 21, 18, 0x3D7BFF)
                text(i == 0 ? "Corner Café" : "Greenleaf Market", 78, ry + 21 - 1.0 / 3, 15, "SemiBold", 0x1E2A4A)
                let value = i == 0 ? "+$0.12" : "+$0.24"
                let tw = ctx.resolve(Text(value).font(.custom("Inter-SemiBold", fixedSize: 15))).measure(in: CGSize(width: 200, height: 30)).width
                text(value, size.width - 30 - tw, ry + 21 - 1.0 / 3, 15, "SemiBold", 0x1F7A45)
            }
        }
        .accessibilityRepresentation {
            VStack {
                Text("Rewards")
                Text("New")
                Text("Cashback this week")
                Text("Squeeze the cloud to collect")
                Text("Corner Café")
                Text("+$0.12")
                Text("Greenleaf Market")
                Text("+$0.24")
            }
        }
    }
}
