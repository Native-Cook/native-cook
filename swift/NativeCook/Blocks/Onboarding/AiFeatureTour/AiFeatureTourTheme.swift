import Foundation

enum AiFeatureTourTheme {
    static let REF_W = 402.0
    static let REF_H = 874.0
    static let S = 402.0 / 720.0
    enum C {
        static let bgTop = "#D3C9FD"
        static let bgMid = "#EEEBFC"
        static let bg = "#F8F8FA"
        static let glow = "#ECE8F8"
        static let title = "#1D1D22"
        static let rowTitle = "#1A1A1F"
        static let sub = "#8A8A90"
        static let tile = "#FFFFFF"
        static let tileEdge = "#E4E6EA"
        static let tileShade = "#B9BCC6"
        static let calTop = "#DCE2E6"
        static let calRing = "#A9B2BA"
        static let dotBlue = "#4D96F4"
        static let dotGrey = "#D9DDE2"
        static let green = "#43B97A"
        static let greenLine = "#6BCB93"
        static let grid = "#EEF1F3"
        static let check = "#2FC06A"
        static let checkGrey = "#C6CBD1"
        static let line = "#E3E6EA"
        static let runner = "#3D7BEA"
        static let avoSkin = "#3E6B2A"
        static let avoFlesh = "#D9E66A"
        static let avoInner = "#F2F49A"
        static let pit = "#A35A26"
        static let button = "#1F2023"
        static let sparkle = "#C9B8FF"
    }
    static let title = "Halden Intelligence can:"
    static let button = "Continue"
    static let rows: [(title: String, sub: [String])] = [
        ("Create check ins", ["Reminders that follow up and", "keep you on track."]),
        ("Understand your trends", ["See what's moving your metrics", "and where they're heading."]),
        ("Create workout templates", ["Strength templates built around", "your goals and style."]),
        ("Log food", ["Snap a photo or describe it and", "let Halden handle the rest."]),
        ("Create a training plan", ["A personalized plan based on", "your goals and activity."]),
        ("Explain your records", ["Upload records to see your", "biomarkers explained."]),
    ]
    enum UI {
        static let title = (x: 360.0, y: 235.0)
        static let rows = (iconX: 147.0, textX: 264.0, y0: 355.0, pitch: 220.0, sub: [42.0, 84.0], tile: 114.0)
        static let button = (x: 45.0, y: 1402.0, w: 630.0, h: 95.0)
    }
}
