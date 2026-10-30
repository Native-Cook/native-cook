import Foundation

enum CareerWebOnboardingTheme {
    static let refW = 402.0, refH = 874.0
    static let scale = refW / 251
    static func x(_ px: Double) -> Double { (px - 56) * scale }
    static func y(_ px: Double) -> Double { (px - 143) * scale }
    static let bg = 0x101011, ink = 0x121214, white = 0xFFFFFF, highlight = 0xC8BAFF
    static let titleX = x(68), titleY = [y(214), y(252), y(290)]
    static let titleWidth = x(213) - x(68)
    static let lines = ["Build Your", "Future, Build", "Your "]
    static let mark = (x0: x(140), x1: x(247), top0: y(271), top1: y(266), bot0: y(308), bot1: y(312))
    static let arrow = (a: CGPoint(x: x(272), y: y(316)), c: CGPoint(x: x(284), y: y(350)), b: CGPoint(x: x(250), y: y(378)), head: 13.0)
    static let pops = [(99.0, 342.0, 104.0, 354.0), (114, 338, 111, 351), (89, 358, 99, 363)]
    struct Node {
        var x: Double, y: Double
        var r = 51.0
        var label: [String] = []
        var bg = 0xFFFFFF
    }
    static let nodes = [
        Node(x: x(126), y: y(398), bg: 0xDFF5E1),
        Node(x: x(186), y: y(483), bg: 0xF7D5B5),
        Node(x: x(238), y: y(560), bg: 0xCEC2F7),
        Node(x: x(207), y: y(415), label: ["UX/UI", "Designer"]),
        Node(x: x(104), y: y(483), label: ["Product", "Designer"]),
        Node(x: x(258), y: y(488), label: ["Software", "Engineer"]),
        Node(x: x(155), y: y(555), label: ["Flutter", "Developer"])
    ]
    static let bars = [[3, 4], [5, 6]], fits = [[3, 4], [4, 5], [5, 6]]
    static let ropeWidth = 12.8, roleSize = 15.5, ctaSize = 17.0
    static let button = CGRect(x: x(82), y: y(625), width: x(288) - x(82), height: y(663) - y(625))
    static let dot = (x: x(266), y: y(644), r: 27.0)
}
