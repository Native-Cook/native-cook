import SwiftUI

enum PullLeverRefreshStage: Equatable {
    case rest
    case pumping(Int)
    case full
    case brewing
    case done

    var title: String {
        switch self {
        case .rest: "Pull a shot"
        case .pumping, .full: "Building pressure"
        case .brewing: "Brewing"
        case .done: "Espresso ready"
        }
    }

    func subtitle(seconds: Int) -> String {
        switch self {
        case .rest: "Pump the lever to 9 bar"
        case let .pumping(bar): "\(bar) bar"
        case .full: "Release to brew"
        case .brewing: "\(seconds) s"
        case .done: "9 bar · 27 s · 36 g"
        }
    }
}

struct PullLeverRefreshLayout: Equatable {
    let size: CGSize
    let safeTop: CGFloat
    let scale: CGFloat
    let pivot: CGPoint

    init(size: CGSize, safeTop: CGFloat) {
        self.size = size
        self.safeTop = safeTop
        scale = min(0.92, max(0.78, (size.width - 32) / 411))
        pivot = CGPoint(x: size.width * 0.407, y: safeTop + 265)
    }

    func point(_ x: CGFloat, _ y: CGFloat) -> CGPoint {
        CGPoint(x: pivot.x + x * scale, y: pivot.y + y * scale)
    }
}

enum PullLeverRefreshTokens {
    static let loop: TimeInterval = 12
    static let resumeDelay: TimeInterval = 3.5
    static let brewDuration: TimeInterval = 4.6
    static let homeAngle: CGFloat = -82
    static let bottomAngle: CGFloat = -6
    static let strokeAngle: CGFloat = -16
    static let rearmAngle: CGFloat = -58
    static let leverLength: CGFloat = 160
    static let degreesPerPoint: CGFloat = 180 / (.pi * 136)

    static let ink = Color(red: 0.957, green: 0.945, blue: 0.925)
    static let muted = Color(red: 0.58, green: 0.55, blue: 0.51)
    static let eyebrow = Color(red: 0.75, green: 0.54, blue: 0.35)
    static let needle = Color(red: 0.86, green: 0.22, blue: 0.05)
    static let zone = Color(red: 0.95, green: 0.50, blue: 0.16)
    static let digit = Color(red: 0.945, green: 0.925, blue: 0.894)

    static func easeOut(_ value: CGFloat) -> CGFloat {
        let x = min(max(value, 0), 1)
        return 1 - pow(1 - x, 3)
    }

    static func easeInOut(_ value: CGFloat) -> CGFloat {
        let x = min(max(value, 0), 1)
        return x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }

    static func smooth(_ edge0: CGFloat, _ edge1: CGFloat, _ x: CGFloat) -> CGFloat {
        guard edge0 != edge1 else { return x < edge0 ? 0 : 1 }
        let t = min(max((x - edge0) / (edge1 - edge0), 0), 1)
        return t * t * (3 - 2 * t)
    }
}
