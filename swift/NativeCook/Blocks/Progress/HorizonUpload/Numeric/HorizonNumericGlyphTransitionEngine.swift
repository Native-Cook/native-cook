// Native percentage engine from expo-content-transition (MIT; see HorizonUpload-Numeric-LICENSE.txt).
// Namespaced only; the original glyph reconciliation, springs and blur math are preserved.
import CoreGraphics
import Foundation

final class HorizonNumericGlyphTransitionEngine {
  private(set) var glyphs: [HorizonNumericGlyphState] = []

  private(set) var contentSize: CGSize = .zero

  private(set) var alignment: HorizonNumericNumericTextAlignment = .leading
  private(set) var clipEnabled = true

  private var springs = HorizonNumericTransitionSprings(
    duration: HorizonNumericTransitionSprings.referenceDuration,
    bounce: HorizonNumericTransitionSprings.defaultBounce
  )

  private var currentText = ""
  private var hasContent = false
  private var countsDown = false
  private var nextId: Int64 = 0
  private var lastTypesetter: HorizonNumericGlyphTypesetter?

  func apply(spec: HorizonNumericNumericTextSpec, typesetter: HorizonNumericGlyphTypesetter) {
    let typographyChanged = lastTypesetter != nil && lastTypesetter != typesetter
    lastTypesetter = typesetter

    let requested = HorizonNumericTransitionSprings(duration: spec.duration, bounce: spec.bounce)
    if requested != springs {
      springs = requested
      glyphs.forEach { $0.adoptSprings(springs) }
    }

    alignment = spec.alignment
    clipEnabled = spec.clipEnabled

    let leftAnchored = !spec.text.contains(where: \.isNumber)
      && !currentText.contains(where: \.isNumber)
    let line = typesetter.typeset(
      spec.text,
      decimalSeparator: spec.decimalSeparator,
      leftAnchored: leftAnchored
    )

    countsDown = resolveDirection(spec)

    let shape = spec.effectiveShape
    let animate = spec.animationsEnabled && hasContent && !typographyChanged
    if animate {
      reconcile(line: line, shape: shape, blurEnabled: spec.blurActive)
    } else {
      rebuild(line: line, shape: shape)
    }

    if !spec.blurActive {
      glyphs.forEach { $0.clearBlur() }
    }

    contentSize = CGSize(width: line.width, height: line.height)
    currentText = spec.text
    hasContent = true
  }

  @discardableResult
  func tick(_ deltaTime: CGFloat) -> Bool {
    var running = false
    var index = 0

    while index < glyphs.count {
      let glyph = glyphs[index]
      if glyph.delay > 0 {
        glyph.delay -= deltaTime
        running = true
        index += 1
        continue
      }

      if glyph.tick(deltaTime) { running = true }
      if glyph.invalid && !glyph.isVisible {
        glyphs.remove(at: index)
        continue
      }

      index += 1
    }

    return running
  }

  func anchorBase(layoutWidth: CGFloat) -> CGFloat {
    switch alignment {
    case .leading: return 0
    case .center: return layoutWidth / 2
    case .trailing: return layoutWidth
    }
  }

  private func rebuild(line: HorizonNumericTypesetLine, shape: HorizonNumericTransitionShape) {
    glyphs = line.placements.map { placement in
      let glyph = HorizonNumericGlyphState(id: nextIdentifier(), unit: placement.unit, springs: springs)
      glyph.slot = placement.slot
      glyph.updateMetrics(placement: placement, line: line, shape: shape)
      glyph.x.snap(to: anchoredX(placement: placement, lineWidth: line.width))
      glyph.snapToVisible()
      return glyph
    }
  }

  private func reconcile(line: HorizonNumericTypesetLine, shape: HorizonNumericTransitionShape, blurEnabled: Bool) {
    var buckets: [MatchKey: [HorizonNumericGlyphState]] = [:]
    var occupiedSlots: [HorizonNumericGlyphSlot: HorizonNumericGlyphState] = [:]
    for glyph in glyphs {
      glyph.invalid = true
      guard let slot = glyph.slot else { continue }
      buckets[MatchKey(slot: slot, unit: glyph.unit), default: []].append(glyph)
      if occupiedSlots[slot] == nil {
        occupiedSlots[slot] = glyph
      }
    }

    var arrived: [HorizonNumericGlyphState] = []
    var slotTargets: [HorizonNumericGlyphSlot: CGFloat] = [:]
    for placement in line.placements {
      slotTargets[placement.slot] = anchoredX(placement: placement, lineWidth: line.width)
    }

    for placement in line.placements {
      let key = MatchKey(slot: placement.slot, unit: placement.unit)
      var reused: HorizonNumericGlyphState?

      if var bucket = buckets[key], !bucket.isEmpty {
        let index = bucket.firstIndex { !$0.disappearing } ?? 0
        reused = bucket.remove(at: index)
        buckets[key] = bucket
      }

      let targetX = anchoredX(placement: placement, lineWidth: line.width)
      if let glyph = reused {
        glyph.invalid = false
        glyph.updateMetrics(placement: placement, line: line, shape: shape)
        glyph.x.retarget(targetX)
        glyph.returnToVisible()
      } else {
        let glyph = HorizonNumericGlyphState(id: nextIdentifier(), unit: placement.unit, springs: springs)
        glyph.slot = placement.slot
        glyph.updateMetrics(placement: placement, line: line, shape: shape)
        if let predecessor = occupiedSlots[placement.slot] {
          glyph.x.reset(from: predecessor.x.value, to: targetX)
        } else {
          glyph.x.snap(to: targetX)
        }

        glyph.beginAppear(countsDown: countsDown, blurEnabled: blurEnabled)
        glyphs.append(glyph)
        arrived.append(glyph)
      }
    }

    let leaving = glyphs.filter(\.invalid).sorted { $0.x.value < $1.x.value }
    for glyph in leaving {
      guard let slot = glyph.slot, let target = slotTargets[slot] else { continue }
      glyph.x.retarget(target)
    }

    stagger(arrived: arrived, leaving: leaving, blurEnabled: blurEnabled)
  }

  private func stagger(arrived: [HorizonNumericGlyphState], leaving: [HorizonNumericGlyphState], blurEnabled: Bool) {
    let count = max(arrived.count, leaving.count)
    let step: CGFloat = count == 0 ? 0 : springs.staggerWindow / CGFloat(count)

    for (index, glyph) in arrived.enumerated() {
      glyph.delay = CGFloat(index) * step
    }

    for (index, glyph) in leaving.enumerated() {
      if !glyph.isAnimating {
        glyph.delay = CGFloat(index) * step
      }

      glyph.beginDisappear(countsDown: countsDown, blurEnabled: blurEnabled)
    }
  }

  private func resolveDirection(_ spec: HorizonNumericNumericTextSpec) -> Bool {
    switch spec.direction {
    case .up: return false
    case .down: return true
    case .auto:
      let previous = HorizonNumericGlyphSlots.numericValue(currentText, decimalSeparator: spec.decimalSeparator)
      let next = HorizonNumericGlyphSlots.numericValue(spec.text, decimalSeparator: spec.decimalSeparator)
      if let previous, let next, previous != next {
        return next < previous
      }

      return countsDown
    }
  }

  private func anchoredX(placement: HorizonNumericGlyphPlacement, lineWidth: CGFloat) -> CGFloat {
    switch alignment {
    case .leading: return placement.x
    case .center: return placement.x - lineWidth / 2
    case .trailing: return placement.x - lineWidth
    }
  }

  private func nextIdentifier() -> Int64 {
    defer { nextId += 1 }
    return nextId
  }

  private struct MatchKey: Hashable {
    let slot: HorizonNumericGlyphSlot
    let unit: String
  }
}
