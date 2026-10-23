// Scaled layout frames, also exercised by the source's numerical fixture.
struct AuraFrames: Codable {
  struct Rect: Codable {
    var x: Double
    var y: Double
    var w: Double
    var h: Double
    var radius: Double?
    var radiusTop: Double?
    var radiusBottom: Double?
  }
  struct Lip: Codable {
    let cy: Double
    let avatar: Double
    let ring: Double
    let xs: [Double]
  }
  let u: Double
  let size: Double
  let radius: Double
  let hero: Rect
  let lip: Lip
  let pane: Rect
  let actions: [Rect]
}

func auraFrames(_ width: Double) -> AuraFrames {
  let u = width / AuraDesign.design
  let actions = auraActionFrames(0)
  return .init(
    u: u, size: width, radius: AuraDesign.radius * u,
    hero: .init(
      x: 12 * u, y: 12 * u, w: 338 * u, h: 156 * u, radiusTop: 32 * u, radiusBottom: 40 * u),
    lip: .init(
      cy: 164 * u, avatar: 36 * u, ring: 3 * u, xs: (0..<5).map { auraSlotX(Double($0)) * u }),
    pane: .init(x: 12 * u, y: 201 * u, w: 338 * u, h: 80 * u, radius: 24 * u),
    actions: (0..<4).map {
      .init(x: actions.xs[$0] * u, y: 298 * u, w: actions.widths[$0] * u, h: 52 * u)
    })
}
