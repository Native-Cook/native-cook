import SwiftUI

// All geometry is in the reference's 280 px pin coordinates; only width determines scale.
enum AuroraTeammatesOnboardingTheme {
    struct Layout: Equatable {
        var W: Double
        var H: Double
        var S: Double { W / 280 }
        var cx: Double { W / 2 }
        var sheetTop: [Double] { [238.5, 305.5, 305.5].map { $0 * S } }
        var half: Double { 78 * S }
        var c1: Double { 26 * S }
        var c2: Double { 50 * S }
        var depth: Double { 21 * S }
        var sheetCorner: Double { 11.7 * S }
        var titleSize: Double { 28.85 * S }
        var titleTracking: Double { -0.036 * titleSize }
        var titleCap: Double { 21 * S }
        var titleStep: Double { 30 * S }
        var titleTop: Double { 54.5 * S }
        var subtitleSize: Double { 9.06 * S }
        var subtitleStep: Double { 16 * S }
        var subtitleBase: [Double] { [166.4, 136.4, 136.4].map { $0 * S } }
        var dotsCentre: [Double] { [223, 176.5, 176.5].map { $0 * S } }
        var trackH: Double { 15 * S }
        var dot: Double { 4 * S }
        var pillW: Double { 13 * S }
        var pillH: Double { 5 * S }
        var gap: Double { 5.8 * S }
        var pad: Double { 6.75 * S }
        var buttonW: Double { 229 * S }
        var buttonH: Double { 39 * S }
        var buttonBottom: [Double] { [35, 22, 22].map { $0 * S } }
        var buttonLabel: Double { 9.75 * S }
        var coreDy: Double { 6 * S }
    }
    // centre x/y, in-plane degrees, depth, opacity, resting width/height.
    static let rest: [[Double]] = [
        [100.8,173.3,0,0.72,1,0,0], [180.9,144.3,0,1,1,0,0],
        [135.5,108.8,2.4,1,0.24,218.75,43], [152.1,143.9,-2.6,0.75,0.82,218.75,43],
        [126.5,184.3,3.1,0.5,0.9,218.75,43], [153,228.6,-1.3,0.25,0.95,218.75,43],
        [128,274.4,8.4,0,1,218.75,43],
        [83.4,128.9,-13,0,0.55,116.5,75], [203.9,131.4,0,0,0.52,116.5,75],
        [81.4,196.4,14.5,0,0.78,116.5,75], [198.9,193.4,23,0,0.82,116.5,75],
        [78,266.6,-12,0,1,116.5,75], [201,263.1,0,0,1,116.5,75]
    ]
    static let tokens = ["RUN", "MEET", "CONNECT", "YOUR", "BUSINESS", "WITH", "AI TEAMMATES", "WORKSPACE"]
    static let lines = [[[0,3],[4,5],[6]],[[1,3],[6]],[[2,3],[7]]]
    static let subtitles = [
        ["Projects, clients, meetings, tasks, & growth managed", "from one AI-powered workspace."],
        ["A specialized AI for every part of your business."], ["Sync the tools your team already uses."]
    ]
    static let accessibility = [
        "Run your business with AI teammates. Projects, clients, meetings, tasks and growth managed from one AI-powered workspace.",
        "Meet your AI teammates. A specialized AI for every part of your business.",
        "Connect your workspace. Sync the tools your team already uses."
    ]
    static let agents = ["AI Chat Assistant", "AI Project Manager", "AI Meeting Assistant", "AI Content Assistant", "AI Finance Manager"]
    static let agentSubs = ["Chats, writes, plans", "Plans sprints & tracks delivery", "Qualifies leads & writes outreach", "Drafts proposals & reports", "Invoices, expenses & forecasts"]
    static let integrations = ["Daybook", "Huddle", "Parley", "Inkwell", "Postbox", "Leadflow"]
    static let logos = ["daybook", "huddle", "parley", "inkwell", "postbox", "leadflow"]
    static let rainbow: [UInt32] = [0x9F5AEE,0xA966EB,0xCA9FBE,0xDFC89A,0xF3F274,0xEDF877,0xB8E698,0x88D2BA,0x49B3E5,0x0180F7]
    static let rainbowStops = [0.0,0.17,0.27,0.38,0.5,0.57,0.65,0.77,0.85,1]
    static func color(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red:Double((hex >> 16) & 255)/255, green:Double((hex >> 8) & 255)/255, blue:Double(hex & 255)/255, opacity:alpha)
    }
}
