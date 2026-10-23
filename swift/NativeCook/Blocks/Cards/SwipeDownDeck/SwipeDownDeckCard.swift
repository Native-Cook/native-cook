import SwiftUI

enum SwipeDownDeckArt {
    case barrel, pills, capsules, tablet, plate, bowl, cup
}

struct SwipeDownDeckCard: Equatable {
    var title: String
    /// The card's flat colour, as 0xrrggbb.
    var color: UInt32
    var art: SwipeDownDeckArt
    var level: Int
    var badge: String
    var tags: [String]
    /// The chips that show this card.
    var filters: [String]
    var description: String

    static let samples = [
        SwipeDownDeckCard(
            title: "Ice Bath", color: 0x2f5be6, art: .barrel, level: 1, badge: "Updated",
            tags: ["Protocols", "Recovery"], filters: ["Protocols"],
            description: "Ice baths involve immersion in cold water to reduce inflammation, enhance recovery, and build mental resilience."
        ),
        SwipeDownDeckCard(
            title: "Magnesium", color: 0xf07a25, art: .pills, level: 2, badge: "Updated",
            tags: ["Supplements", "Sleep"], filters: ["Supplements"],
            description: "Magnesium is a mineral used in over 300 processes in the body, important for sleep, muscles and calm nerves."
        ),
        SwipeDownDeckCard(
            title: "Zinc", color: 0x2f5be6, art: .capsules, level: 1, badge: "New",
            tags: ["Supplements", "Immunity"], filters: ["New", "Supplements"],
            description: "Zinc is an essential trace mineral that supports immune function, wound healing and protein production."
        ),
        SwipeDownDeckCard(
            title: "Vitamin D", color: 0xf0b21e, art: .tablet, level: 3, badge: "Updated",
            tags: ["Supplements", "Immunity"], filters: ["Supplements"],
            description: "Vitamin D, often called the sunshine vitamin, is crucial for bone health, immune function and mood."
        ),
        SwipeDownDeckCard(
            title: "Carnivore Diet", color: 0xc4451c, art: .plate, level: 2, badge: "Updated",
            tags: ["Diets", "Performance"], filters: ["Diets"],
            description: "The carnivore diet focuses on animal-based foods, said to reduce inflammation and improve digestion for some."
        ),
        SwipeDownDeckCard(
            title: "Vegan Diet", color: 0x7fd43a, art: .bowl, level: 1, badge: "Updated",
            tags: ["Diets", "Longevity"], filters: ["Diets"],
            description: "A vegan diet excludes all animal products, relying on plant foods rich in fibre, vitamins and antioxidants."
        ),
        SwipeDownDeckCard(
            title: "Water Fast", color: 0x2ea8e8, art: .cup, level: 3, badge: "New",
            tags: ["Protocols", "Longevity"], filters: ["New", "Protocols"],
            description: "Water fasting means consuming only water for a set period, to promote autophagy and cellular repair."
        ),
    ]
}

/// The face of a card: badges and the add button, the illustration on its plate, name, tags, text.
struct SwipeDownDeckCardFace: View {
    let card: SwipeDownDeckCard
    let width: Double
    let height: Double
    let onAdd: () -> Void

    private static let pad = 16.0
    private static let pillHeight = 26.0
    private static let round = 32.0
    private static let plate = 184.0
    private static let art = 150.0
    private static let radius = 28.0
    private static let wash = Color.black.opacity(0.18)

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 6) {
                pill {
                    SwipeDownDeckPerson().fill(.white).frame(width: 12, height: 12)
                    Text("\(card.level)")
                }
                pill {
                    SwipeDownDeckAlert(ink: Color(hex: card.color)).frame(width: 12, height: 12)
                    Text(card.badge)
                }
                Spacer(minLength: 0)
                Button(action: onAdd) {
                    SwipeDownDeckIcon.add.path
                        .stroke(.white, style: StrokeStyle(lineWidth: 1.8, lineCap: .round, lineJoin: .round))
                        .frame(width: 16, height: 16)
                        .frame(width: Self.round, height: Self.round)
                        .contentShape(Circle())
                }
                .buttonStyle(SwipeDownDeckRoundStyle())
                .accessibilityLabel("Add \(card.title) to the deck")
            }
            .frame(height: Self.round)

            Spacer(minLength: 0)
            SwipeDownDeckArtView(art: card.art, plate: Self.plate, size: Self.art)
                .frame(width: Self.plate, height: Self.plate)
            Text(card.title)
                .font(.system(size: 30, weight: .regular))
                .tracking(-0.4)
                .foregroundStyle(.white)
                .lineLimit(1)
                .frame(height: 36)
                .padding(.top, 18)
            HStack(spacing: 6) {
                ForEach(card.tags, id: \.self) { tag in
                    Text(tag)
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 10)
                        .frame(height: 24)
                        .background(Capsule().fill(Self.wash))
                }
            }
            .padding(.top, 10)
            Text(card.description)
                .font(.system(size: 13))
                .lineSpacing(18 - 15.5)
                .foregroundStyle(.white.opacity(0.82))
                .multilineTextAlignment(.center)
                .lineLimit(3)
                .padding(.top, 14)
            Spacer(minLength: 0)
        }
        .padding(.top, Self.pad)
        .padding(.horizontal, Self.pad)
        .padding(.bottom, 24)
        .frame(width: width, height: height)
        .background {
            let shape = RoundedRectangle(cornerRadius: Self.radius, style: .continuous)
            shape.fill(Color(hex: card.color))
                // Light from above and a little shade at the foot, so a flat colour reads as a surface.
                .overlay(
                    shape.fill(LinearGradient(
                        stops: [
                            .init(color: .white.opacity(0.16), location: 0),
                            .init(color: .white.opacity(0), location: 0.45),
                            .init(color: .black.opacity(0.18), location: 1),
                        ],
                        startPoint: .top, endPoint: .bottom
                    ))
                )
                .overlay(shape.strokeBorder(.white.opacity(0.16), lineWidth: 1))
        }
        .clipShape(RoundedRectangle(cornerRadius: Self.radius, style: .continuous))
    }

    private func pill<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        HStack(spacing: 5) { content() }
            .font(.system(size: 12, weight: .semibold))
            .foregroundStyle(.white)
            .padding(.horizontal, 10)
            .frame(height: Self.pillHeight)
            .background(Capsule().fill(Self.wash))
    }
}

private struct SwipeDownDeckRoundStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .background(Circle().fill(.black.opacity(configuration.isPressed ? 0.32 : 0.18)))
    }
}

/// An illustration on its round, lit plate. Shapes are in a 100 × 100 box: an ellipse or a rounded
/// rect, its corner radius, a rotation in degrees about its own centre, colour and alpha.
struct SwipeDownDeckArtView: View {
    let art: SwipeDownDeckArt
    let plate: Double
    let size: Double

    private enum Kind { case e, r }
    private typealias Shape = (Kind, Double, Double, Double, Double, Double, Double, UInt32, Double)

    private static let shadow: Shape = (.e, 18, 80, 64, 12, 0, 0, 0x000000, 0.22)

    private static func shapes(_ art: SwipeDownDeckArt) -> [Shape] {
        switch art {
        case .barrel: [
            shadow,
            (.r, 20, 34, 60, 52, 14, 0, 0xc98a4b, 1),
            (.r, 18, 46, 64, 6, 3, 0, 0x9aa3ab, 1),
            (.r, 18, 70, 64, 6, 3, 0, 0x9aa3ab, 1),
            (.e, 17, 24, 66, 22, 0, 0, 0xb07235, 1),
            (.e, 22, 27, 56, 16, 0, 0, 0xcfeaf8, 1),
            (.r, 30, 28, 11, 9, 2.5, 18, 0xffffff, 1),
            (.r, 45, 29, 11, 9, 2.5, -12, 0xf1faff, 1),
            (.r, 58, 30, 10, 8, 2.5, 24, 0xffffff, 1),
        ]
        case .pills: [
            shadow,
            (.r, 12, 40, 58, 26, 13, -32, 0xf3ece2, 1),
            (.r, 21, 50, 40, 6, 3, -32, 0xffffff, 0.8),
            (.r, 32, 46, 58, 26, 13, 18, 0xe8decf, 1),
            (.r, 41, 56, 40, 6, 3, 18, 0xffffff, 0.7),
        ]
        case .capsules: [
            shadow,
            (.r, 16, 34, 62, 26, 13, -40, 0x5cc2ff, 1),
            (.r, 26, 44, 42, 6, 3, -40, 0xb8e6ff, 1),
            (.r, 30, 50, 60, 24, 12, -40, 0x3aa0f0, 1),
            (.r, 40, 59, 40, 6, 3, -40, 0x9ad8ff, 1),
        ]
        case .tablet: [
            shadow,
            (.e, 16, 36, 68, 44, 0, 0, 0xd9c9a6, 1),
            (.e, 16, 28, 68, 44, 0, 0, 0xf4e9d0, 1),
            (.r, 26, 48, 48, 4, 2, 0, 0xd9c9a6, 1),
        ]
        case .plate: [
            (.e, 12, 76, 76, 14, 0, 0, 0x000000, 0.22),
            (.e, 10, 28, 80, 54, 0, 0, 0xe9e2d6, 1),
            (.e, 16, 32, 68, 44, 0, 0, 0xf6f1e8, 1),
            (.e, 20, 38, 34, 26, 0, -18, 0x6e2f1f, 1),
            (.e, 24, 42, 22, 14, 0, -18, 0x8a4130, 1),
            (.e, 50, 36, 28, 22, 0, 0, 0xffffff, 1),
            (.e, 58, 42, 12, 10, 0, 0, 0xf5a623, 1),
            (.e, 52, 54, 24, 18, 0, 0, 0xffffff, 1),
            (.e, 59, 58, 11, 9, 0, 0, 0xf5a623, 1),
        ]
        case .bowl: [
            shadow,
            (.e, 12, 34, 76, 50, 0, 0, 0xefe8dc, 1),
            (.e, 18, 36, 64, 36, 0, 0, 0x3f7a2c, 1),
            (.e, 22, 38, 22, 16, 0, -20, 0x7bc043, 1),
            (.e, 40, 37, 20, 15, 0, 15, 0x5fa83a, 1),
            (.e, 58, 40, 18, 14, 0, 0, 0xe2483a, 1),
            (.e, 30, 52, 16, 12, 0, 0, 0x9c6b4a, 1),
            (.e, 48, 52, 14, 11, 0, 0, 0xe2483a, 1),
            (.e, 62, 52, 14, 10, 0, 0, 0xc9a27e, 1),
        ]
        case .cup: [
            (.e, 26, 80, 48, 10, 0, 0, 0x000000, 0.22),
            (.r, 28, 30, 44, 54, 12, 0, 0xf1e9dc, 1),
            (.e, 28, 22, 44, 16, 0, 0, 0xe3d7c3, 1),
            (.e, 31, 24, 38, 11, 0, 0, 0x7fc8ee, 1),
            (.r, 33, 36, 6, 40, 3, 0, 0xffffff, 0.4),
        ]
        }
    }

    var body: some View {
        Canvas { context, _ in
            let disc = Path(ellipseIn: CGRect(x: 0, y: 0, width: plate, height: plate))
            context.fill(disc, with: .radialGradient(
                Gradient(colors: [.white.opacity(0.26), .white.opacity(0.04)]),
                center: CGPoint(x: plate * 0.5, y: plate * 0.38), startRadius: 0, endRadius: plate * 0.6
            ))
            context.stroke(Path(ellipseIn: CGRect(x: 0.5, y: 0.5, width: plate - 1, height: plate - 1)), with: .color(.white.opacity(0.14)), lineWidth: 1)
            let scale = size / 100
            let inset = (plate - size) / 2
            for (kind, x, y, w, h, r, deg, color, alpha) in Self.shapes(art) {
                let rect = CGRect(x: -w * scale / 2, y: -h * scale / 2, width: w * scale, height: h * scale)
                var layer = context
                layer.translateBy(x: inset + (x + w / 2) * scale, y: inset + (y + h / 2) * scale)
                layer.rotate(by: .degrees(deg))
                let path = kind == .e ? Path(ellipseIn: rect) : Path(roundedRect: rect, cornerRadius: r * scale, style: .circular)
                layer.fill(path, with: .color(Color(hex: color, opacity: alpha)))
            }
        }
    }
}

// Small icons, in a 16 × 16 box (strokes) or a 12 × 12 box (the pills' filled marks).
struct SwipeDownDeckPerson: Shape {
    func path(in rect: CGRect) -> Path {
        let k = rect.width / 12
        var path = Path(ellipseIn: CGRect(x: (6 - 2.3) * k, y: (3.2 - 2.3) * k, width: 4.6 * k, height: 4.6 * k))
        path.addRoundedRect(in: CGRect(x: 3 * k, y: 6.4 * k, width: 6 * k, height: 5.2 * k), cornerSize: CGSize(width: 2.4 * k, height: 2.4 * k))
        return path
    }
}

struct SwipeDownDeckAlert: View {
    let ink: Color

    var body: some View {
        Canvas { context, size in
            let k = size.width / 12
            context.fill(Path(ellipseIn: CGRect(x: 0, y: 0, width: 12 * k, height: 12 * k)), with: .color(.white))
            context.fill(Path(roundedRect: CGRect(x: 5.2 * k, y: 2.6 * k, width: 1.6 * k, height: 4.6 * k), cornerRadius: 0.8 * k), with: .color(ink))
            context.fill(Path(ellipseIn: CGRect(x: (6 - 0.95) * k, y: (9 - 0.95) * k, width: 1.9 * k, height: 1.9 * k)), with: .color(ink))
        }
    }
}

enum SwipeDownDeckIcon {
    case add, search, menu, tray, up

    /// The icon in its 16 × 16 box, to be stroked.
    var path: SwipeDownDeckIconShape { SwipeDownDeckIconShape(icon: self) }
}

struct SwipeDownDeckIconShape: Shape {
    let icon: SwipeDownDeckIcon

    func path(in rect: CGRect) -> Path {
        var p = Path()
        switch icon {
        case .add:
            p.move(to: CGPoint(x: 8, y: 2.5)); p.addLine(to: CGPoint(x: 8, y: 11.5))
            p.move(to: CGPoint(x: 4.5, y: 8)); p.addLine(to: CGPoint(x: 8, y: 11.5)); p.addLine(to: CGPoint(x: 11.5, y: 8))
            p.move(to: CGPoint(x: 3, y: 14)); p.addLine(to: CGPoint(x: 13, y: 14))
        case .search:
            p.addEllipse(in: CGRect(x: 2, y: 2, width: 10.2, height: 10.2))
            p.move(to: CGPoint(x: 11.6, y: 11.6)); p.addLine(to: CGPoint(x: 14.5, y: 14.5))
        case .menu:
            for x in [4.0, 8, 12] {
                p.move(to: CGPoint(x: x, y: 3.5)); p.addLine(to: CGPoint(x: x, y: 12.5))
            }
        case .tray:
            p.move(to: CGPoint(x: 5, y: 1.5))
            p.addArc(tangent1End: CGPoint(x: 12, y: 1.5), tangent2End: CGPoint(x: 12, y: 3.5), radius: 2)
            p.addLine(to: CGPoint(x: 12, y: 7))
            p.move(to: CGPoint(x: 8.5, y: 14))
            p.addArc(tangent1End: CGPoint(x: 3, y: 14), tangent2End: CGPoint(x: 3, y: 12), radius: 2)
            p.addArc(tangent1End: CGPoint(x: 3, y: 1.5), tangent2End: CGPoint(x: 5, y: 1.5), radius: 2)
            p.move(to: CGPoint(x: 12, y: 9.5)); p.addLine(to: CGPoint(x: 12, y: 15))
            p.move(to: CGPoint(x: 9.8, y: 12.8)); p.addLine(to: CGPoint(x: 12, y: 15)); p.addLine(to: CGPoint(x: 14.2, y: 12.8))
        case .up:
            p.move(to: CGPoint(x: 8, y: 13)); p.addLine(to: CGPoint(x: 8, y: 3.5))
            p.move(to: CGPoint(x: 4.5, y: 7)); p.addLine(to: CGPoint(x: 8, y: 3.5)); p.addLine(to: CGPoint(x: 11.5, y: 7))
        }
        return p.applying(CGAffineTransform(scaleX: rect.width / 16, y: rect.height / 16))
    }
}
