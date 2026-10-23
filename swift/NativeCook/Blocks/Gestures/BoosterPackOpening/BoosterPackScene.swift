import CoreGraphics

/// One card of the stack as it is drawn this frame.
struct BoosterPackSceneCard {
  /// Row-major homographies from the card's centred coordinates to the screen.
  var frontMatrix: [Double] = [1, 0, 0, 0, 1, 0, 0, 0, 1]
  var backMatrix: [Double] = [1, 0, 0, 0, 1, 0, 0, 0, 1]
  var frontOpacity = 0.0
  var backOpacity = 0.0
  var artworkOpacity = 0.0
  var tilt = CGPoint.zero
  var holo = 0.0
  var shine = 0.0
  var sweep = 0.0
  var flash = 0.0
  var borderGlow = 0.0
}

/// One of the marks under the top card.
struct BoosterPackSceneMark {
  var opacity = 0.0
  var epic = false
}

/// The frame: every value the Metal renderer and the vector overlay draw from. The experience
/// writes it once per tick, so both read the same clock and the same geometry.
struct BoosterPackScene {
  var width = 0.0
  var height = 0.0
  var pack = BoosterPackRect.zero
  var seamY = 0.0
  var direction = 1.0
  var reducedMotion = false
  var committed = false
  var postCutMs = 0.0
  var punch = 1.0

  var backgroundMorph = 0.0
  var dimOpacity = 0.0
  var packAuraOpacity = 0.0

  var intactOpacity = 1.0
  var previewOpacity = 0.0
  var flatFoilOpacity = 0.0
  var previewBottomMask = BoosterPackGeometry.makeEmptyPath()
  var previewFoilVertices: [CGPoint] = []
  var previewMeshTextures: [CGPoint] = []
  var fold = BoosterPackMaterials.Fold()
  var sheenStart = CGPoint.zero
  var sheenEnd = CGPoint.zero
  var sheenOpacity = 0.0

  var meshOpacity = 0.0
  var foilVertices: [CGPoint] = []
  var meshTextures: [CGPoint] = []
  var foil = BoosterPackMaterials.Foil()
  var foilEdge: [CGPoint] = []

  var bodyPose = BoosterPackLightingPose(originX: 0, originY: 0, translateX: 0, translateY: 0, scale: 1, rotation: 0)
  var bodyOpacity = 0.0
  var bodyDeglareStrength = 0.0
  var bodyTextureRect = BoosterPackRect.zero
  var body = BoosterPackMesh(vertices: [], textures: [])
  var postCutBottomMask = BoosterPackGeometry.makeEmptyPath()
  var entryBodyWedgePath = BoosterPackGeometry.makeEmptyPath()
  var mouthFrontEdgePath = BoosterPackGeometry.makeEmptyPath()
  var cavityStrength = 0.0
  var cavityOpacity = 0.0
  var cavitySpecularOpacity = 0.0
  var edgeOpacity = 0.0
  var mouthLighting = BoosterPackMouthLight()
  var mouthSurfaceVertices: [CGPoint] = []
  var mouthSurfaceTextures: [CGPoint] = []
  var mouthSurfaceOutline = BoosterPackGeometry.makeEmptyPath()
  var rimSurfaceVertices: [CGPoint] = []
  var rimSurfaceTextures: [CGPoint] = []
  var rimSurfaceOutline = BoosterPackGeometry.makeEmptyPath()
  var mouthGlowCenter = CGPoint.zero

  var cardTop = 0.0
  var cardOpacity = 0.0
  var cardShadowOpacity = 0.0
  var cardContactShadowOpacity = 0.0
  var riseLightOpacity = 0.0

  // The vector overlay.
  var flashPacketMask = BoosterPackGeometry.makeEmptyPath()
  var visibleTraceOpacity = 0.0
  var traceSize = 0.0
  var traceBodyPath = BoosterPackGeometry.makeEmptyPath()
  var traceCorePath = BoosterPackGeometry.makeEmptyPath()
  var traceGradientStart = CGPoint.zero
  var traceGradientEnd = CGPoint.zero
  var traceCoreGradientEnd = CGPoint.zero
  var seamHintOpacity = 0.0
  var seamHintHead = 0.0
  var seamHintTail = 0.0
  var sparks: [BoosterPackTearSpark] = []
  var burstLive = false
  var overlayActive = false
  var overlayWasActive = false

  // The backdrop and the revealed cards.
  var backdropOpacity = 0.0
  var backdropZoom = 0.0
  var backdropRotation = 0.0
  var particlePhase = 0.0
  var cardsOpacity = 0.0
  var auraOpacity = 0.0
  var sparkleOpacity: [Double] = []
  var cards: [BoosterPackSceneCard] = []
  var marks: [BoosterPackSceneMark] = []
}
