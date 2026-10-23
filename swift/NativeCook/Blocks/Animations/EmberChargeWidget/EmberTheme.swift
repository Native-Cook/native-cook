import SwiftUI

/// `ember-theme.ts`: all geometry is in card points, from the top-left corner.
/// Gaps belong to a four-point scale; sizes and line boxes have their own tokens.
enum EmberTheme {
  static let space = (
    hair: 4.0, tight: 12.0, column: 16.0, near: 20.0,
    edge: 28.0, corner: 32.0, cornerText: 36.0
  )
  static let size = 340.0
  static let dim = (
    disc: 56.0, glyph: 16.0, barH: 56.0, countH: 60.0,
    heart: (w: 56.0, h: 52.0),
    line: (title: 32.0, subtitle: 20.0, time: 16.0, value: 28.0, unit: 16.0, hint: 18.0, bpm: 20.0)
  )

  // Each band starts after the preceding band and its relationship gap.
  static let header = (top: space.corner, h: dim.disc)
  static let metrics = (
    top: header.top + header.h + space.corner, h: dim.line.value + dim.line.unit
  )
  static let bar = (top: metrics.top + metrics.h + space.near, h: dim.barH)
  static let footer = (top: bar.top + bar.h + space.tight, h: dim.countH)
  static let contentWidth = size - space.edge * 2
  static let columnWidth = (contentWidth - space.column * 2) / 3
  static let titleTop = header.top + (header.h - (dim.line.title + dim.line.subtitle)) / 2

  /// Names match the reference's derived layout, so the two ports can be read together.
  enum E {
    static let size = EmberTheme.size
    static let space = EmberTheme.space
    static let bands = (
      header: EmberTheme.header, metrics: EmberTheme.metrics,
      bar: EmberTheme.bar, footer: EmberTheme.footer
    )
    /// Cubic squircle corner; `radius` is only the tube's circular parent clip.
    static let corner = 96.0
    static let radius = 76.0
    static let bleed = 60.0
    static let inset = space.edge
    static let pageMargin = 16.0
    static let line = dim.line
    static let disc = (x: space.corner, y: header.top, d: dim.disc)
    static let title = (
      x: space.corner + dim.disc + space.near, top: titleTop, sub: titleTop + dim.line.title
    )
    static let time = (right: space.cornerText, top: titleTop + space.hair)
    static let metrics = (
      x: [
        space.edge, space.edge + columnWidth + space.column,
        space.edge + 2 * (columnWidth + space.column),
      ],
      w: columnWidth, text: dim.glyph + space.hair, glyph: dim.glyph,
      glyphTop: EmberTheme.metrics.top + (dim.line.value - dim.glyph) / 2,
      valueTop: EmberTheme.metrics.top, unitTop: EmberTheme.metrics.top + dim.line.value
    )
    static let bar = (
      x: space.edge, y: EmberTheme.bar.top, w: contentWidth, h: EmberTheme.bar.h,
      r: 14.0, bloom: 12.0, core: 4.0
    )
    /// Two-point optical overhang aligns the count ink to the straight inset.
    static let count = (
      x: space.edge - 2, size: 54.0, h: footer.h, top: footer.top,
      unitGap: space.tight, unitDrop: 8.0
    )
    /// Rate offset was calibrated against the path's area centroid at 3 px/pt in RN.
    static let heart = (
      x: size - space.edge - dim.heart.w, y: footer.top + footer.h - dim.heart.h,
      w: dim.heart.w, h: dim.heart.h, rateOffset: 12.72
    )
    static let embers = (x: 44.0, pitch: 36.0, below: 20.0)
    enum stripe { static let period = 28.0 }
    static let restSteps = 4151
    static let goal = 6400
    static let restBpm = 80
    static let goalBpm = 96
  }

  /// The floor opens gradually toward the bar and heart, without a rectangular edge.
  static let countFootprint = (
    x: E.inset + E.metrics.w, y: E.count.top + E.count.h / 2,
    rx: E.heart.x - E.inset, ry: E.count.h + space.corner
  )

  static func countLight(_ x: Double, _ y: Double) -> Double {
    let dx = (x - countFootprint.x) / countFootprint.rx
    let dy = (y - countFootprint.y) / countFootprint.ry
    let radius2 = dx * dx + dy * dy
    return 1 - exp(-radius2 * radius2)
  }

  /// Must match the shader's KEY_ANGLE; the theme does not import shader source.
  static let emberHeartKeyAngle = -2.356194

  // Swift rendering boxes, derived from the same layout and blur tokens.
  static let canvas = E.size + 2 * E.bleed
  static let tubePad = E.bar.bloom * 4
  static let tube = (
    x: E.bar.x - tubePad, y: E.bar.y - tubePad,
    w: E.bar.w + 2 * tubePad, h: E.bar.h + 2 * tubePad
  )
  /// Touch light expands from a local pool to the nearby wall.
  static let touchRadius = (rest: 60.0, growth: 90.0)

  static let barCenterY = E.bar.y + E.bar.h / 2

  /// Manrope sizes/tracking from styles in ember-charge.tsx, ember-count.tsx and ui.tsx.
  enum TypeStyle {
    static let title = (size: 26.0, tracking: -0.8)
    static let subtitle = (size: 16.0, tracking: -0.2)
    static let time = 12.0
    static let value = (size: 24.0, tracking: -0.65)
    static let unit = 12.0
    static let countTracking = -2.2
    static let bpm = (size: 15.0, tracking: -0.2)
    static let hint = 13.0
  }

  /// Capture-calibrated SwiftUI corrections; preserve the RN line boxes around the ink.
  enum Optical {
    /// Raise the title's SwiftUI baseline to the accepted RN ink bounds.
    static let titleRise = -5.0 / 3
    /// The subtitle needs two physical pixels of rise at 3 px/pt.
    static let subtitleRise = -2.0 / 3
    /// Time and unit ink need one physical pixel below their native line box.
    static let smallTextDrop = 1.0 / 3
    /// Align the metric value cores to RN's explicit 28-point line box.
    static let valueRise = -7.0 / 3
    /// NumericText's 60-point view is bottom-biased relative to SwiftUI Text.
    static let countDrop = 22.0 / 3
    /// Two physical pixels align the fixed heart-rate ink with the vessel centroid.
    static let rateDrop = 2.0 / 3
  }

  /// Local pictogram construction from EmberLight in ember-surface.tsx.
  enum Glyph {
    static let grid = 24.0
    static let stroke = 1.8
    static let discLightRise = 2.0
    static let walkSize = 29.0
    /// Half a point left/up balances the forward arm and feet.
    static let walkInset = 15.0
  }

  /// Heart material geometry from ember-heart.tsx, in unscaled local points.
  enum Heart {
    static let center = CGPoint(x: E.heart.w / 2, y: E.heart.h / 2)
    static let contactClip = CGRect(x: -7, y: -7, width: 70, height: 70)
    static let contactBlur = 5.0
    static let shadowDrop = 2.0
    static let shadowAlpha = 0.18
    static let caustic = CGRect(x: 20, y: 51, width: 16, height: 4)
    static let causticAlpha = (rest: 0.16, beat: 0.22)
    static let bodyAlpha = 0.86
    static let bodyStops = [0.0, 0.55, 1.0]
    static let top = 3.0
    static let tip = 50.0
    static let pool = (level: 37.0, heat: 3.0, beat: 4.0, alpha: 0.78, alphaBeat: 0.22)
    static let depthEnd = 38.0
    static let depthWidth = 3.0
    static let rimTop = 22.0
    static let rimWidth = 2.0
    static let rimAlpha = (rest: 0.55, beat: 0.35)
    static let keyAlpha = 0.30
    static let keyReach = 35.0
    static let keyStops = [0.0, 0.18]
    static let crescentCenter = CGPoint(x: 14, y: 16.75)
    static let crescentRadius = 10.75
    static let crescentHalfAngle = Double.pi / 8
    static let crescentWidth = 1.5
    static let crescentAlpha = 0.5
  }

  /// Airborne geometry from EmberEffects; deterministic particle seeds remain in the drawing formulas.
  enum Effects {
    static let emberCount = 8
    static let highSparks = 18
    static let mediumSparks = 10
    static let emberRadius = 1.6
    static let emberTravel = 190.0
    static let emberSway = 7.0
    static let emberAlpha = 0.8
    static let headInset = 2.0
    static let waveRadius = 260.0
    static let waveWidth = 3.0
    static let waveThinning = 2.5
    static let waveAlpha = 0.55
    static let sparkRadius = 3.5
  }
}

/// Ink follows its surface, not the adjacent copy's hierarchy.
enum EmberTone {
  /// Page behind the glass and its halo.
  static let page = Color(ember: 0x000000)
  /// Primary ink on the dark face.
  static let ink = Color(ember: 0xffffff)
  /// Secondary labels on the dark face, about 6:1 at rest.
  static let sub = Color(ember: 0x8d8d8d)
  /// Count unit on the ember floor; white alone clears the resting contrast target.
  static let onEmber = Color(ember: 0xffffff)
  /// Header disc at its upper light source.
  static let discTop = Color(ember: 0x454543)
  /// Header disc at its smoked lower edge.
  static let discBottom = Color(ember: 0x222220)
  /// Hot ignition particles above the card.
  static let spark = Color(ember: 0xffd23f)
  /// Cooling ignition particles above the card.
  static let sparkCool = Color(ember: 0xff7a00)
  /// Leading wave and rising embers above the copy.
  static let wave = Color(ember: 0xffd98a)
}

/// The smoked vessel palette, including transparent gradient endpoints.
enum EmberHeartTone {
  /// Floor contact shadow beneath the heart.
  static let contactShadow = Color(ember: 0x1f0b02)
  /// Vessel light spilling onto the floor beneath its tip.
  static let contactCaustic = Color(ember: 0xffc35d)
  /// Smoked body at the shoulders.
  static let bodyTop = Color(ember: 0x2b1406)
  /// Smoked body at 55 percent of its height.
  static let bodyMiddle = Color(ember: 0x4b2504)
  /// Smoked body at the tip.
  static let bodyTip = Color(ember: 0x6d3803)
  /// Transparent liquid surface.
  static let poolSurface = Color(ember: 0xca6816, alpha: 0)
  /// Warm liquid middle.
  static let poolMiddle = Color(ember: 0xfa9527)
  /// Bright liquid at the tip.
  static let poolTip = Color(ember: 0xffc965)
  /// Inner wall shade at the upper edge.
  static let depthTop = Color(ember: 0x1f0b02, alpha: 0.4)
  /// Transparent end of the inner wall shade.
  static let depthClear = Color(ember: 0x1f0b02, alpha: 0)
  /// Ember-lit lower flanks and tip.
  static let rimEmber = Color(ember: 0xffd79a)
  /// Transparent upper end of the ember rim.
  static let rimEmberClear = Color(ember: 0xffd79a, alpha: 0)
  /// Faint upper-left key rim.
  static let rimKey = Color(ember: 0xfef1d0)
  /// Transparent end of the key rim.
  static let rimKeyClear = Color(ember: 0xfef1d0, alpha: 0)
  /// Crisp reflection on the upper-left lobe.
  static let specularCrescent = Color(ember: 0xfef1d0)
  /// Fixed native heart rate on the smoked glass.
  static let ink = Color(ember: 0xffffff)
}
