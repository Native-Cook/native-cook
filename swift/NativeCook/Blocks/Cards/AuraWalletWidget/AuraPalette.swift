// The measured 362-point drawing. Scale geometry by width / design.
import Foundation

enum AuraDesign {
  static let design = 362.0
  static let maximum = 362.0
  static let margin = 20.0
  static let bleed = 64.0
  enum lines {
    static let owner: Double = 16
    static let chip: Double = 14
    static let balance: Double = 42
    static let paneLabel: Double = 14
    static let title: Double = 18
    static let note: Double = 14
    static let amount: Double = 20
    static let action: Double = 20
  }
  enum page {
    static let lift: Double = 48
    static let hintGap: Double = 24
    static let hintSize: Double = 13
    static let hintLine: Double = 18
  }
  enum touch {
    static let minimum: Double = 44
    static let retention: Double = 20
  }
  enum details {
    static let chipPad: Double = 10
    static let chipStroke: Double = 1
    static let actionOpticalX: Double = 1
    static let ownerOpacity: Double = 0.85
    static let viewAllOpacity: Double = 0.9
  }
  static let radius: Double = 44
  static let inset: Double = 12
  static let tight: Double = -0.25
  static let faceZoom: Double = 1.24
  static let faceLift: Double = 0.04
  enum hero {
    static let x: Double = 12
    static let y: Double = 12
    static let w: Double = 338
    static let h: Double = 156
    static let radiusTop: Double = 32
    static let radiusBottom: Double = 40
  }
  enum lip {
    static let cy: Double = 164
    static let avatar: Double = 36
    static let ring: Double = 3
    static let pitch: Double = 50
    static let count: Double = 5
  }
  enum owner {
    static let cx: Double = 40
    static let cy: Double = 37
    static let avatar: Double = 22
    static let nameX: Double = 56
    static let nameSize: Double = 12
  }
  enum info {
    static let cx: Double = 326
    static let cy: Double = 37
    static let size: Double = 22
  }
  enum chip {
    static let cx: Double = 181
    static let cy: Double = 58
    static let w: Double = 90
    static let h: Double = 22
    static let textSize: Double = 10
    static let tracking: Double = 0.2
  }
  enum balance {
    static let cy: Double = 98
    static let size: Double = 34
    static let tracking: Double = -0.6
  }
  enum pane {
    static let x: Double = 12
    static let y: Double = 201
    static let w: Double = 338
    static let h: Double = 80
    static let radius: Double = 24
  }
  enum paneLabel {
    static let x: Double = 26
    static let cy: Double = 219
    static let size: Double = 11
    static let right: Double = 336
  }
  enum paneMark {
    static let cx: Double = 46
    static let cy: Double = 252
    static let size: Double = 36
  }
  enum paneTitle {
    static let x: Double = 72
    static let cy: Double = 245
    static let size: Double = 14
  }
  enum paneNote {
    static let x: Double = 72
    static let cy: Double = 262
    static let size: Double = 11
  }
  enum paneAmount {
    static let right: Double = 334
    static let cy: Double = 259
    static let size: Double = 16
  }
  enum actions {
    static let y: Double = 298
    static let h: Double = 52
    static let gap: Double = 6
    static let widths: [Double] = [52, 52, 120, 96]
    static let labelSize: Double = 15
    static let icon: Double = 18
    static let stroke: Double = 1.5
    static let arrow: Double = 14
    static let arrowGap: Double = 5
  }
}

func auraWidth(_ screen: Double) -> Double {
  floor(min(screen - AuraDesign.margin * 2, AuraDesign.maximum) / 2) * 2
}

// Touch responses share the source's duration, scale and cubic easing.
enum AuraMotion {
  static let pressIn = 90.0
  static let pressScale = 0.96
  static let releaseDuration = 300.0
  static let releaseDampingRatio = 0.7
  static let easeOut = SIMD4<Double>(0.23, 1, 0.32, 1)
}
