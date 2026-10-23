import Foundation
import simd

/// Triangle soup with groups, as three.js BufferGeometry keeps it.
struct GlowknobMeshData {
  var positions: [SIMD3<Float>] = []
  var normals: [SIMD3<Float>] = []
  var uvs: [SIMD2<Float>] = []
  var indices: [UInt32]? = nil
  /// (start, count, materialIndex) in vertices (non-indexed) or indices.
  var groups: [(start: Int, count: Int, material: Int)] = []
}

/// three.js `Shape.extractPoints(curveSegments)` for the console's capsule
/// (`capsule()` in lift-scene.ts): moveTo, lineTo, absarc, lineTo, absarc.
/// Line curves contribute their two ends, ellipse curves `2·curveSegments`
/// divisions; consecutive duplicates are skipped as Path.getPoints does.
func glowknobCapsulePoints(width: Double, height: Double, curveSegments: Int) -> [SIMD2<Double>] {
  let r = height / 2, x = width / 2 - r
  var points: [SIMD2<Double>] = []
  func push(_ p: SIMD2<Double>) {
    if let last = points.last, last == p { return }
    points.append(p)
  }
  func arc(_ cx: Double, _ cy: Double, _ start: Double, _ end: Double) -> [SIMD2<Double>] {
    // EllipseCurve, anticlockwise = false.
    var delta = end - start
    while delta < 0 { delta += Double.pi * 2 }
    while delta > Double.pi * 2 { delta -= Double.pi * 2 }
    let divisions = curveSegments * 2
    return (0...divisions).map { d in
      let angle = start + Double(d) / Double(divisions) * delta
      return SIMD2(cx + r * cos(angle), cy + r * sin(angle))
    }
  }
  var current = SIMD2(-x, -r)
  // moveTo(-x, -r), lineTo(x, -r)
  push(current)
  current = SIMD2(x, -r)
  push(current)
  // absarc(x, 0, r, -π/2, π/2): a lineTo is inserted only if it does not start
  // at the current point.
  let a1 = arc(x, 0, -Double.pi / 2, Double.pi / 2)
  if a1[0] != current { push(a1[0]) }
  a1.forEach(push)
  // lineTo(-x, r)
  push(SIMD2(-x, r))
  let a2 = arc(-x, 0, Double.pi / 2, Double.pi * 3 / 2)
  a2.forEach(push)
  return points
}

/// `ShapeUtils.area` / `isClockWise`.
private func area(_ c: [SIMD2<Double>]) -> Double {
  var a = 0.0
  var p = c.count - 1
  for q in 0..<c.count {
    a += c[p].x * c[q].y - c[q].x * c[p].y
    p = q
  }
  return a * 0.5
}

/// ExtrudeGeometry's `getBevelVec`: the offset direction of a contour vertex,
/// scaled so that offset edges stay parallel to the originals.
private func bevelVec(_ inPt: SIMD2<Double>, _ inPrev: SIMD2<Double>, _ inNext: SIMD2<Double>) -> SIMD2<Double> {
  var tx: Double, ty: Double, shrinkBy: Double
  let vpx = inPt.x - inPrev.x, vpy = inPt.y - inPrev.y
  let vnx = inNext.x - inPt.x, vny = inNext.y - inPt.y
  let vpLenSq = vpx * vpx + vpy * vpy
  let collinear0 = vpx * vny - vpy * vnx
  if abs(collinear0) > Double.ulpOfOne {
    let vpLen = vpLenSq.squareRoot()
    let vnLen = (vnx * vnx + vny * vny).squareRoot()
    let prevShiftX = inPrev.x - vpy / vpLen, prevShiftY = inPrev.y + vpx / vpLen
    let nextShiftX = inNext.x - vny / vnLen, nextShiftY = inNext.y + vnx / vnLen
    let sf = ((nextShiftX - prevShiftX) * vny - (nextShiftY - prevShiftY) * vnx) / (vpx * vny - vpy * vnx)
    tx = prevShiftX + vpx * sf - inPt.x
    ty = prevShiftY + vpy * sf - inPt.y
    let lenSq = tx * tx + ty * ty
    if lenSq <= 2 { return SIMD2(tx, ty) }
    shrinkBy = (lenSq / 2).squareRoot()
  } else {
    var directionEq = false
    if vpx > Double.ulpOfOne {
      if vnx > Double.ulpOfOne { directionEq = true }
    } else if vpx < -Double.ulpOfOne {
      if vnx < -Double.ulpOfOne { directionEq = true }
    } else if glowknobJsSign(vpy) == glowknobJsSign(vny) {
      directionEq = true
    }
    if directionEq {
      tx = -vpy
      ty = vpx
      shrinkBy = vpLenSq.squareRoot()
    } else {
      tx = vpx
      ty = vpy
      shrinkBy = (vpLenSq / 2).squareRoot()
    }
  }
  return SIMD2(tx / shrinkBy, ty / shrinkBy)
}

/// Port of `THREE.ExtrudeGeometry` (r186) for one hole-less shape with
/// `steps = 1`, `bevelEnabled`, `bevelOffset = 0`: bevel layers, the two lids
/// (group 0: back lid then front lid) and the side walls (group 1), as a
/// non-indexed triangle list with `computeVertexNormals()`'s flat normals.
func glowknobExtrudeGeometry(contour inPoints: [SIMD2<Double>], depth: Double, bevelThickness: Double,
                     bevelSize: Double, bevelSegments: Int) -> GlowknobMeshData {
  var vertices = inPoints
  if area(vertices) >= 0 { vertices.reverse() } // reverse = !isClockWise
  // triangulateShape → removeDupEndPts (mutates the contour).
  if vertices.count > 2, vertices[0] == vertices[vertices.count - 1] { vertices.removeLast() }
  let contour = vertices
  let vlen = contour.count
  // The lid is planar and convex: a fan from vertex 0 covers it exactly as
  // Earcut's triangles do; lid normals and face UVs depend only on position.
  // Fan triangles are counter-clockwise seen from +Z (the contour is
  // clockwise), so the top lid faces +Z and the reversed bottom lid −Z.
  var faces: [(Int, Int, Int)] = []
  for i in 1..<(vlen - 1) { faces.append((0, i + 1, i)) }

  var movements: [SIMD2<Double>] = []
  for i in 0..<vlen {
    let j = (i - 1 + vlen) % vlen, k = (i + 1) % vlen
    movements.append(bevelVec(contour[i], contour[j], contour[k]))
  }

  var placeholder: [SIMD3<Double>] = []
  // Back bevel layers.
  for b in 0..<bevelSegments {
    let t = Double(b) / Double(bevelSegments)
    let z = bevelThickness * cos(t * Double.pi / 2)
    let bs = bevelSize * sin(t * Double.pi / 2)
    for i in 0..<vlen {
      let v = contour[i] + movements[i] * bs
      placeholder.append(SIMD3(v.x, v.y, -z))
    }
  }
  let bs = bevelSize
  // Back facing vertices, then the single step.
  for i in 0..<vlen {
    let v = contour[i] + movements[i] * bs
    placeholder.append(SIMD3(v.x, v.y, 0))
  }
  for i in 0..<vlen {
    let v = contour[i] + movements[i] * bs
    placeholder.append(SIMD3(v.x, v.y, depth))
  }
  // Front bevel layers.
  for b in stride(from: bevelSegments - 1, through: 0, by: -1) {
    let t = Double(b) / Double(bevelSegments)
    let z = bevelThickness * cos(t * Double.pi / 2)
    let bs = bevelSize * sin(t * Double.pi / 2)
    for i in 0..<vlen {
      let v = contour[i] + movements[i] * bs
      placeholder.append(SIMD3(v.x, v.y, depth + z))
    }
  }

  var mesh = GlowknobMeshData()
  func addVertex(_ index: Int) {
    let p = placeholder[index]
    mesh.positions.append(SIMD3<Float>(Float(p.x), Float(p.y), Float(p.z)))
  }
  func f3(_ a: Int, _ b: Int, _ c: Int) {
    addVertex(a)
    addVertex(b)
    addVertex(c)
  }
  func f4(_ a: Int, _ b: Int, _ c: Int, _ d: Int) {
    addVertex(a)
    addVertex(b)
    addVertex(d)
    addVertex(b)
    addVertex(c)
    addVertex(d)
  }
  // Lids: bottom faces reversed, then top faces.
  var start = mesh.positions.count
  let layers = 1 + bevelSegments * 2
  for face in faces { f3(face.2, face.1, face.0) }
  let topOffset = vlen * layers
  for face in faces { f3(face.0 + topOffset, face.1 + topOffset, face.2 + topOffset) }
  mesh.groups.append((start, mesh.positions.count - start, 0))
  // Side walls.
  start = mesh.positions.count
  var i = contour.count
  while true {
    i -= 1
    if i < 0 { break }
    let j = i
    var k = i - 1
    if k < 0 { k = contour.count - 1 }
    for s in 0..<layers {
      let slen1 = vlen * s, slen2 = vlen * (s + 1)
      f4(j + slen1, k + slen1, k + slen2, j + slen2)
    }
  }
  mesh.groups.append((start, mesh.positions.count - start, 1))

  // computeVertexNormals() on a non-indexed geometry: one flat normal per
  // triangle, cross(C − B, A − B).
  mesh.normals = [SIMD3<Float>](repeating: .zero, count: mesh.positions.count)
  var t = 0
  while t < mesh.positions.count {
    let a = mesh.positions[t], b = mesh.positions[t + 1], c = mesh.positions[t + 2]
    let n = simd_normalize(simd_cross(c - b, a - b))
    mesh.normals[t] = n
    mesh.normals[t + 1] = n
    mesh.normals[t + 2] = n
    t += 3
  }
  mesh.uvs = [SIMD2<Float>](repeating: .zero, count: mesh.positions.count)
  return mesh
}

/// Port of `THREE.CylinderGeometry(r, r, h, radialSegments, 1)`: indexed,
/// smooth torso normals; groups torso 0, top cap 1, bottom cap 2.
func glowknobCylinderGeometry(radius: Double, height: Double, radialSegments: Int) -> GlowknobMeshData {
  var m = GlowknobMeshData()
  var indices: [UInt32] = []
  let halfHeight = height / 2
  var indexArray: [[Int]] = []
  var index = 0
  var groupStart = 0
  // Torso.
  for y in 0...1 {
    var row: [Int] = []
    let v = Double(y)
    for x in 0...radialSegments {
      let u = Double(x) / Double(radialSegments)
      let theta = u * Double.pi * 2
      let s = sin(theta), c = cos(theta)
      m.positions.append(SIMD3<Float>(Float(radius * s), Float(-v * height + halfHeight), Float(radius * c)))
      m.normals.append(simd_normalize(SIMD3<Float>(Float(s), 0, Float(c))))
      m.uvs.append(SIMD2<Float>(Float(u), Float(1 - v)))
      row.append(index)
      index += 1
    }
    indexArray.append(row)
  }
  for x in 0..<radialSegments {
    let a = indexArray[0][x], b = indexArray[1][x], c = indexArray[1][x + 1], d = indexArray[0][x + 1]
    indices += [a, b, d, b, c, d].map(UInt32.init)
  }
  m.groups.append((groupStart, indices.count - groupStart, 0))
  // Caps.
  for top in [true, false] {
    groupStart = indices.count
    let sign: Double = top ? 1 : -1
    let centerStart = index
    for _ in 1...radialSegments {
      m.positions.append(SIMD3<Float>(0, Float(halfHeight * sign), 0))
      m.normals.append(SIMD3<Float>(0, Float(sign), 0))
      m.uvs.append(SIMD2<Float>(0.5, 0.5))
      index += 1
    }
    let centerEnd = index
    for x in 0...radialSegments {
      let u = Double(x) / Double(radialSegments)
      let theta = u * Double.pi * 2
      let s = sin(theta), c = cos(theta)
      m.positions.append(SIMD3<Float>(Float(radius * s), Float(halfHeight * sign), Float(radius * c)))
      m.normals.append(SIMD3<Float>(0, Float(sign), 0))
      m.uvs.append(SIMD2<Float>(Float(c * 0.5 + 0.5), Float(s * 0.5 * sign + 0.5)))
      index += 1
    }
    for x in 0..<radialSegments {
      let c = centerStart + x, i = centerEnd + x
      if top { indices += [i, i + 1, c].map(UInt32.init) } else { indices += [i + 1, i, c].map(UInt32.init) }
    }
    m.groups.append((groupStart, indices.count - groupStart, top ? 1 : 2))
  }
  m.indices = indices
  return m
}

extension GlowknobMeshData {
  mutating func rotateX(_ angle: Double) {
    let c = Float(cos(angle)), s = Float(sin(angle))
    func rot(_ v: SIMD3<Float>) -> SIMD3<Float> { SIMD3(v.x, v.y * c - v.z * s, v.y * s + v.z * c) }
    positions = positions.map(rot)
    normals = normals.map(rot)
  }

  mutating func translate(_ x: Double, _ y: Double, _ z: Double) {
    let d = SIMD3<Float>(Float(x), Float(y), Float(z))
    positions = positions.map { $0 + d }
  }

  /// lift-scene.ts `faceUV`: UVs in face space for the vertices of `groups`
  /// (all when nil): (0,0) the housing's top-left, (1,1) its bottom-right.
  mutating func faceUV(width: Double, height: Double, groups only: [Int]? = nil) {
    func set(_ v: Int) {
      let p = positions[v]
      uvs[v] = SIMD2<Float>(Float(Double(p.x) / width + 0.5), Float(0.5 - Double(p.y) / height))
    }
    guard let only else {
      for v in positions.indices { set(v) }
      return
    }
    for g in groups where only.contains(g.material) {
      for i in g.start..<(g.start + g.count) { set(indices.map { Int($0[i]) } ?? i) }
    }
  }
}
