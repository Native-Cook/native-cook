import SwiftUI

enum PressedStampCardDrawing {
    static let page = Color(red: 244/255, green: 237/255, blue: 224/255)
    static let espresso = Color(red: 42/255, green: 27/255, blue: 12/255)
    static let caramel = Color(red: 184/255, green: 129/255, blue: 59/255)
    static let gold = Color(red: 232/255, green: 194/255, blue: 114/255)
    static let row = Color(red: 251/255, green: 246/255, blue: 234/255)
    static let hairline = Color(red: 234/255, green: 223/255, blue: 200/255)

    static func scene(_ context: inout GraphicsContext, size: CGSize, layout: PressedStampCardLayout, frame: PressedStampCardFrame, hand: CGPoint?) {
        context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(page))
        context.translateBy(x: layout.origin.x, y: layout.origin.y)
        context.scaleBy(x: layout.scale, y: layout.scale)
        context.draw(Image(uiImage: PressedStampCardResources.page), in: CGRect(x: 0, y: 0, width: 402, height: 874))
        text(&context, "Your card", at: CGPoint(x: 20, y: 97), size: 33, face: .serif, color: espresso)
        card(&context, frame: frame)
        button(&context, frame: frame)
        orders(&context, frame: frame)
        if frame.tool > 0 || hand != nil { die(&context, frame: frame, hand: hand) }
        tabs(&context, y: layout.tabsY)
    }

    static func card(_ context: inout GraphicsContext, frame: PressedStampCardFrame) {
        var layer = context
        layer.translateBy(x: 201, y: 290 + frame.dip); layer.scaleBy(x: max(0.001, cos(frame.flip * .pi / 180)), y: 1); layer.translateBy(x: -201, y: -290)
        let rect = CGRect(x: 20, y: 118, width: 362, height: 344), path = Path(roundedRect: rect, cornerRadius: 24)
        var ambient = layer
        ambient.addFilter(.shadow(color: Color(red: 90/255, green: 67/255, blue: 38/255).opacity(0.18), radius: 28, x: 0, y: 18))
        ambient.fill(path, with: .color(espresso))
        var contact = layer
        contact.addFilter(.shadow(color: .black.opacity(0.3), radius: 4, x: 0, y: 2)); contact.fill(path, with: .color(espresso))
        layer.fill(path, with: .linearGradient(Gradient(stops: [.init(color: Color(red: 0.2275, green: 0.149, blue: 0.0627), location: 0), .init(color: Color(red: 0.1922, green: 0.1255, blue: 0.0471), location: 0.4), .init(color: Color(red: 0.1216, green: 0.0824, blue: 0.0314), location: 1)]), startPoint: CGPoint(x: 20, y: 118), endPoint: CGPoint(x: 382, y: 462)))
        var texture = layer; texture.clip(to: path); texture.blendMode = .softLight; texture.opacity = 0.7
        for x in 0..<3 { for y in 0..<3 { texture.draw(Image(uiImage: PressedStampCardResources.leather), in: CGRect(x: 20 + x * 128, y: 118 + y * 128, width: 128, height: 128)) } }
        layer.stroke(path, with: .linearGradient(Gradient(colors: [Color.white.opacity(0.12), .black.opacity(0.25)]), startPoint: CGPoint(x: 20, y: 118), endPoint: CGPoint(x: 382, y: 462)), lineWidth: 0.7)
        text(&layer, "STAMP CARD", at: CGPoint(x: 40, y: 157), size: 13, face: .semibold, color: Color(red: 191/255, green: 167/255, blue: 131/255), tracking: 1.8)
        var counter = layer; counter.clip(to: Path(CGRect(x: 284, y: 130, width: 84, height: 40)))
        let countTint = frame.gold > 0.4 ? gold : Color(red: 199/255, green: 154/255, blue: 91/255)
        if frame.previousCount != frame.count, frame.roll < 1 {
            var outgoing = counter; outgoing.opacity = Double(1 - frame.roll)
            text(&outgoing, "\(frame.previousCount)/8", at: CGPoint(x: 362, y: 162 - 40 * frame.roll), size: 29, face: .serif, color: countTint, anchor: .trailing)
        }
        var incoming = counter; incoming.opacity = Double(frame.previousCount == frame.count ? 1 : frame.roll)
        text(&incoming, "\(frame.count)/8", at: CGPoint(x: 362, y: 162 + (frame.previousCount == frame.count ? 0 : 40 * (1 - frame.roll))), size: 29, face: .serif, color: countTint, anchor: .trailing)
        for index in 0..<8 {
            let local = PressedStampCardMotion.well(index), centre = CGPoint(x: 20 + local.x, y: 118 + local.y)
            let well = Path(ellipseIn: CGRect(x: centre.x - 36, y: centre.y - 36, width: 72, height: 72))
            layer.fill(well, with: .radialGradient(Gradient(colors: [Color(red: 0.082, green: 0.051, blue: 0.016), Color(red: 0.035, green: 0.022, blue: 0.008)]), center: CGPoint(x: centre.x - 7, y: centre.y - 7), startRadius: 0, endRadius: 40))
            layer.stroke(Path(ellipseIn: CGRect(x: centre.x - 35.3, y: centre.y - 35.3, width: 70.6, height: 70.6)), with: .linearGradient(Gradient(colors: [Color(red: 0.478, green: 0.341, blue: 0.188).opacity(0.6), Color(red: 0.478, green: 0.341, blue: 0.188)]), startPoint: CGPoint(x: centre.x - 30, y: centre.y - 30), endPoint: CGPoint(x: centre.x + 30, y: centre.y + 30)), lineWidth: 1)
            if index < frame.count { seal(&layer, index: index, frame: frame) }
            else { text(&layer, String(format: "%02d", index + 1), at: CGPoint(x: centre.x, y: centre.y + 5.5), size: 16, face: .serif, color: Color(red: 138/255, green: 106/255, blue: 64/255), anchor: .center) }
        }
        let remaining = max(0, 8 - frame.count)
        let sentence = remaining == 0 ? "Complete! Your next coffee is on us." : "\(remaining) more stamp\(remaining == 1 ? "" : "s") until your free coffee"
        text(&layer, sentence, at: CGPoint(x: 40, y: 421), size: 15, face: .regular, color: Color(red: 205/255, green: 187/255, blue: 158/255))
    }

    static func seal(_ context: inout GraphicsContext, index: Int, frame: PressedStampCardFrame) {
        let local = PressedStampCardMotion.seal(index)
        var layer = context; layer.translateBy(x: 20 + local.x, y: 118 + local.y + frame.sealBounce[index]); layer.rotate(by: .degrees(Double(PressedStampCardMotion.rotations[index] + frame.sealBounce[index] * (index % 2 == 0 ? -0.6 : 0.6))))
        let circle = Path(ellipseIn: CGRect(x: -34, y: -34, width: 68, height: 68)), tint = frame.sealGold[index]
        var shadow = layer; shadow.addFilter(.shadow(color: .black.opacity(0.55), radius: 2, x: 1, y: 1.5)); shadow.fill(circle, with: .color(.black))
        layer.fill(circle, with: .linearGradient(Gradient(colors: [Color(red: 0.97, green: 0.94, blue: 0.86), Color(red: 0.87, green: 0.83, blue: 0.75)]), startPoint: CGPoint(x: -24, y: -28), endPoint: CGPoint(x: 26, y: 30)))
        if tint > 0 {
            var foil = layer; foil.opacity = Double(tint)
            foil.fill(circle, with: .linearGradient(Gradient(stops: [.init(color: Color(red: 0.85, green: 0.64, blue: 0.25), location: 0), .init(color: Color(red: 1, green: 0.914, blue: 0.659), location: 0.38), .init(color: Color(red: 0.42, green: 0.29, blue: 0.078), location: 0.62), .init(color: gold, location: 1)]), startPoint: CGPoint(x: -34, y: -14), endPoint: CGPoint(x: 34, y: 14)))
        }
        layer.stroke(circle, with: .linearGradient(Gradient(colors: [Color.white.opacity(0.8), Color.black.opacity(0.35)]), startPoint: CGPoint(x: -20, y: -28), endPoint: CGPoint(x: 26, y: 30)), lineWidth: 1)
        layer.draw(Image(uiImage: PressedStampCardResources.ink), in: CGRect(x: -34, y: -34, width: 68, height: 68))
        if frame.wet[index] > 0 {
            layer.fill(circle, with: .radialGradient(Gradient(colors: [Color.white.opacity(Double(frame.wet[index] * 0.26)), .clear]), center: CGPoint(x: -10, y: -12), startRadius: 0, endRadius: 30))
        }
        if tint > 0, frame.foil >= 0 {
            var reflection = layer; reflection.clip(to: circle)
            let bandX = 290 - 580 * frame.foil - (local.x - local.y) * 0.707107
            reflection.fill(Path(CGRect(x: -50, y: -50, width: 100, height: 100)), with: .linearGradient(Gradient(colors: [.clear, Color(red: 1, green: 0.914, blue: 0.659).opacity(0.42), .clear]), startPoint: CGPoint(x: bandX - 28, y: -34), endPoint: CGPoint(x: bandX + 28, y: 34)))
        }
    }

    static func button(_ context: inout GraphicsContext, frame: PressedStampCardFrame) {
        let rect = CGRect(x: 20, y: 478, width: 362, height: 56), path = Path(roundedRect: rect, cornerRadius: 18)
        var shadow = context; shadow.addFilter(.shadow(color: Color(red: 61/255, green: 42/255, blue: 18/255).opacity(0.15), radius: 8, x: 0, y: 6)); shadow.fill(path, with: .color(espresso))
        context.fill(path, with: .color(espresso))
        if frame.buttonGold > 0 {
            var foil = context; foil.opacity = Double(frame.buttonGold)
            foil.fill(path, with: .linearGradient(Gradient(colors: [Color(red: 0.42, green: 0.29, blue: 0.078), gold, Color(red: 1, green: 0.914, blue: 0.659)]), startPoint: CGPoint(x: 20, y: 478), endPoint: CGPoint(x: 382, y: 534)))
        }
        context.stroke(path, with: .color(Color(red: 97/255, green: 74/255, blue: 45/255)), lineWidth: 0.7)
        let complete = frame.buttonGold > 0.5
        if !complete { icon(&context, name: "qr", rect: CGRect(x: 92, y: 495, width: 21, height: 21), color: row) }
        text(&context, complete ? "Redeem free coffee" : "Scan at the counter", at: CGPoint(x: complete ? 201 : 221, y: 512), size: 16, face: .semibold, color: complete ? espresso : row, anchor: .center)
    }

    static func orders(_ context: inout GraphicsContext, frame: PressedStampCardFrame) {
        text(&context, "Recent orders", at: CGPoint(x: 20, y: 582), size: 26, face: .serif, color: espresso)
        text(&context, "View all", at: CGPoint(x: 382, y: 579), size: 14, face: .semibold, color: Color(red: 145/255, green: 98/255, blue: 43/255), anchor: .trailing)
        var list = context; list.clip(to: Path(CGRect(x: 20, y: 610, width: 362, height: 183)))
        for index in 1...8 {
            let y = 610 + (frame.orders - CGFloat(index)) * 76
            guard y > 530, y < 793 else { continue }
            let rect = CGRect(x: 20, y: y, width: 362, height: 68), path = Path(roundedRect: rect, cornerRadius: 20)
            list.fill(path, with: .color(row)); list.stroke(path, with: .color(hairline), lineWidth: 1)
            list.fill(Path(ellipseIn: CGRect(x: 32, y: y + 11, width: 44, height: 44)), with: .color(Color(red: 237/255, green: 226/255, blue: 201/255)))
            icon(&list, name: "cup", rect: CGRect(x: 42.5, y: y + 21.5, width: 23, height: 23), color: Color(red: 138/255, green: 106/255, blue: 58/255))
            let value = order(index)
            text(&list, value.0, at: CGPoint(x: 90, y: y + 27), size: 16, face: .semibold, color: espresso)
            text(&list, value.1, at: CGPoint(x: 90, y: y + 50), size: 13, face: .regular, color: Color(red: 129/255, green: 115/255, blue: 97/255))
            text(&list, value.2, at: CGPoint(x: 366, y: y + 27), size: 16, face: .bold, color: espresso, anchor: .trailing)
            if index - 1 < 5 || frame.count <= index - 1 { text(&list, "+1 stamp", at: CGPoint(x: 366, y: y + 50), size: 13, face: .semibold, color: Color(red: 145/255, green: 98/255, blue: 43/255), anchor: .trailing) }
        }
    }
    static func order(_ index: Int) -> (String, String, String) {
        [("Doppio", "4 days ago", "€2.80"), ("Oat latte", "3 days ago", "€4.20"), ("Espresso", "2 days ago", "€2.60"), ("Latte macchiato", "Yesterday · 08:40", "€3.80"), ("Americano", "Today · 09:41", "€3.00"), ("Flat white", "Just now", "€3.90"), ("Cortado", "Just now", "€3.40"), ("Cappuccino", "Just now", "€3.60")][index - 1]
    }

    static func die(_ context: inout GraphicsContext, frame: PressedStampCardFrame, hand: CGPoint?) {
        let p = hand ?? CGPoint(x: frame.toolPoint.x + 20, y: frame.toolPoint.y + 118 + frame.dip), radius = hand == nil ? frame.toolRadius : 52
        var layer = context; layer.translateBy(x: p.x, y: p.y); layer.rotate(by: .degrees(Double(frame.toolRotation)))
        var shadow = layer; shadow.addFilter(.shadow(color: .black.opacity(hand == nil ? Double(0.6 - 0.25 * frame.toolLift) : 0.35), radius: hand == nil ? 3 + 27 * frame.toolLift : 30, x: hand == nil ? 18 * frame.toolLift : 18, y: hand == nil ? 2 + 24 * frame.toolLift : 26))
        shadow.fill(Path(ellipseIn: CGRect(x: -radius, y: -radius, width: radius * 2, height: radius * 2)), with: .color(Color(red: 147/255, green: 98/255, blue: 31/255)))
        layer.draw(Image(uiImage: PressedStampCardResources.dieBase), in: CGRect(x: -radius, y: -radius, width: radius * 2, height: radius * 2))
        let offset = frame.toolRotation / 8 * radius * 9 / 55
        layer.draw(Image(uiImage: PressedStampCardResources.dieKnob), in: CGRect(x: -radius * 0.58 + offset, y: -radius * 0.58 - offset, width: radius * 1.16, height: radius * 1.16))
    }

    static func tabs(_ context: inout GraphicsContext, y: CGFloat) {
        let rect = CGRect(x: 16, y: y, width: 370, height: 64), path = Path(roundedRect: rect, cornerRadius: 32)
        var shadow = context; shadow.addFilter(.shadow(color: .black.opacity(0.12), radius: 18, x: 0, y: 6)); shadow.fill(path, with: .color(row))
        context.fill(path, with: .color(row)); context.stroke(path, with: .color(hairline), lineWidth: 1)
        for (index, name) in ["Home", "Card", "Rewards", "Account"].enumerated() {
            let x = 24 + CGFloat(index) * 88.5
            if index == 1 { context.fill(Path(roundedRect: CGRect(x: x, y: y + 8, width: 88.5, height: 48), cornerRadius: 24), with: .color(hairline)) }
            icon(&context, name: ["home", "card", "gift", "person"][index], rect: CGRect(x: x + 33.75, y: y + 13, width: 21, height: 21), color: espresso)
            text(&context, name, at: CGPoint(x: x + 44.25, y: y + 50), size: 13, face: .medium, color: espresso, anchor: .center)
        }
    }
    static func text(_ context: inout GraphicsContext, _ value: String, at point: CGPoint, size: CGFloat, face: PressedStampCardResources.Face, color: Color, anchor: UnitPoint = .leading, tracking: CGFloat = 0) {
        let resolved = context.resolve(Text(value).font(PressedStampCardResources.font(face, size: size)).tracking(tracking).foregroundColor(color))
        let bounds = CGSize(width: 402, height: 200), width = resolved.measure(in: bounds).width
        context.draw(resolved, at: CGPoint(x: point.x - width * anchor.x, y: point.y - resolved.firstBaseline(in: bounds)), anchor: .topLeading)
    }
    static func icon(_ context: inout GraphicsContext, name: String, rect: CGRect, color: Color) {
        var path = Path()
        func move(_ x: CGFloat, _ y: CGFloat) { path.move(to: CGPoint(x: x, y: y)) }
        func line(_ x: CGFloat, _ y: CGFloat) { path.addLine(to: CGPoint(x: x, y: y)) }
        func box(_ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat) { path.addRect(CGRect(x: x, y: y, width: w, height: h)) }
        switch name {
        case "qr": box(3,3,6,6); box(15,3,6,6); box(3,15,6,6); box(15,15,2,2); move(20,14); line(20,17); move(14,20); line(17,20); line(17,18); move(20,20); line(21,20); line(21,21)
        case "cup": path.addRoundedRect(in: CGRect(x: 4, y: 3, width: 16, height: 3), cornerSize: CGSize(width: 1.5, height: 1.5)); move(5.5,7); line(7.5,21); line(16.5,21); line(18.5,7); move(6.5,12); line(17.5,12); move(7.1,16); line(16.9,16)
        case "home": move(3,10); line(12,3); line(21,10); move(5,9); line(5,21); line(10,21); line(10,14); line(14,14); line(14,21); line(19,21); line(19,9)
        case "card": box(3,6,18,14); move(3,10); line(21,10); move(6,15); line(10,15)
        case "gift": box(3,9,18,5); box(5,14,14,7); move(12,9); line(12,21); move(12,9); path.addCurve(to: CGPoint(x: 8, y: 3), control1: CGPoint(x: 4,y: 9), control2: CGPoint(x: 5,y: 2)); path.addQuadCurve(to: CGPoint(x: 12,y: 9), control: CGPoint(x: 10,y: 3)); path.addCurve(to: CGPoint(x: 16,y: 3), control1: CGPoint(x: 14,y: 3), control2: CGPoint(x: 16,y: 3)); path.addCurve(to: CGPoint(x: 12,y: 9), control1: CGPoint(x: 19,y: 2), control2: CGPoint(x: 20,y: 9))
        default: path.addEllipse(in: CGRect(x: 8,y: 3,width: 8,height: 8)); move(4,21); line(4,19); path.addCurve(to: CGPoint(x: 20,y: 19), control1: CGPoint(x: 4,y: 8), control2: CGPoint(x: 20,y: 8)); line(20,21)
        }
        var layer = context; layer.translateBy(x: rect.minX, y: rect.minY); layer.scaleBy(x: rect.width / 24, y: rect.height / 24)
        layer.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
    }
}
