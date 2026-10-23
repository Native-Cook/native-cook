import Foundation

/// Where everything sits on one screen, in the block's root view coordinates (jar-sim.ts `Geometry`).
struct MoodOrbJarGeometry: Equatable {
    typealias Layout = MoodOrbJarTokens.Layout

    let width: Double
    let height: Double
    let safeTop: Double
    let scale: Double
    let top: Double
    let centerX: Double
    let mouthY: Double
    let floor: Double
    let left: Double
    let right: Double
    let shoulderEnd: Double
    let mouthHalf: Double
    let corner: Double
    let slotY: Double
    let slotX: [Double]
    let rowY: Double
    let rowHeight: Double
    let lidX: Double
    let lidY: Double

    init(width: Double, height: Double, safeTop: Double) {
        let s = width / MoodOrbJarTokens.stageWidth
        let top = safeTop + Layout.header * s
        let cx = width / 2
        let jarWidth = Layout.jarWidth * s
        let mouthY = top + Layout.jarTop * s
        let moodCount = MoodOrbJarTokens.moods.count
        let middle = Double(moodCount - 1) / 2

        self.width = width
        self.height = height
        self.safeTop = safeTop
        self.scale = s
        self.top = top
        self.centerX = cx
        self.mouthY = mouthY
        self.floor = mouthY + (Layout.jarHeight - Layout.base) * s
        self.left = cx - jarWidth / 2 + Layout.wall * s
        self.right = cx + jarWidth / 2 - Layout.wall * s
        self.shoulderEnd = mouthY + Layout.shoulder * s
        self.mouthHalf = Layout.mouthHalf * s
        self.corner = Layout.corner * s
        self.slotY = top + (Layout.pickerTop + Layout.pickerOrbY) * s
        self.slotX = (0..<moodCount).map { index in
            cx + (Layout.slotDx + (Double(index) - middle) * Layout.slotGap) * s
        }
        self.rowY = top + Layout.entriesTop * s
        self.rowHeight = Layout.entryHeight * s
        self.lidX = cx - (Layout.lidWidth * s) / 2
        self.lidY = mouthY - (Layout.lidHeight - Layout.lidOverlap) * s
    }
}
