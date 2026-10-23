import Foundation

struct LacedBootStudioPresentationValues {
    var materials: [Double]
    var soles: [Double]
    var savedMix: Double
}

struct LacedBootStudioMaterialChange {
    let id = UUID()
    let time: Double
    let materials: [Double]
    let soles: [Double]
    let targetMaterials: [Double]
    let targetSoles: [Double]
}

struct LacedBootStudioConfirmation {
    let id = UUID()
    let time: Double
    let ageAtAnchor: Double
    let startMix: Double
    let anchorMix: Double
    var endTime: Double { time + max(0, 1.38 - ageAtAnchor) }
    func age(at now: Double) -> Double { ageAtAnchor + max(0, now - time) }
    func mix(at now: Double) -> Double {
        if now <= time { return anchorMix }
        let age = age(at: now)
        let rise = startMix + (1 - startMix) * LacedBootStudioMath.smooth(age / 0.12)
        return rise * (1 - LacedBootStudioMath.smooth((age - 1.2) / 0.18))
    }
}

private struct LacedBootStudioPresentationHeld {
    let values: LacedBootStudioPresentationValues
    let saveAge: Double?
    let saveStartMix: Double
}

/// Explicit ownership keeps automatically fading color and copy frozen on raw Down.
struct LacedBootStudioPresentationDriver {
    private(set) var materials = [1.0, 0.0, 0.0]
    private(set) var soles = [1.0, 0.0]
    private(set) var change: LacedBootStudioMaterialChange?
    private(set) var confirmation: LacedBootStudioConfirmation?
    private var held: LacedBootStudioPresentationHeld?
    var hasActiveClock: Bool { held == nil && (change != nil || confirmation != nil) }

    func values(at now: Double) -> LacedBootStudioPresentationValues {
        if let held { return held.values }
        var values = LacedBootStudioPresentationValues(materials: materials, soles: soles, savedMix: confirmation?.mix(at: now) ?? 0)
        if let change {
            let u = LacedBootStudioMath.smooth((now - change.time) / 0.18)
            values.materials = zip(change.materials, change.targetMaterials).map { $0 + ($1 - $0) * u }
            values.soles = zip(change.soles, change.targetSoles).map { $0 + ($1 - $0) * u }
        }
        return values
    }
    mutating func select(from scene: LacedBootStudioScene, materials targetMaterials: [Double], soles targetSoles: [Double], at now: Double) {
        materials = scene.materials; soles = scene.soles
        change = .init(time: now, materials: scene.materials, soles: scene.soles,
                       targetMaterials: targetMaterials, targetSoles: targetSoles)
    }
    mutating func save(from mix: Double, at now: Double) {
        confirmation = .init(time: now, ageAtAnchor: 0, startMix: mix, anchorMix: mix)
    }
    mutating func capture(_ scene: LacedBootStudioScene, displayedAt now: Double) {
        let values = LacedBootStudioPresentationValues(materials: scene.materials, soles: scene.soles, savedMix: scene.savedMix)
        held = .init(values: values, saveAge: confirmation.map { min(1.38, $0.age(at: now)) },
                     saveStartMix: confirmation?.startMix ?? 0)
        materials = scene.materials; soles = scene.soles; change = nil; confirmation = nil
    }
    mutating func release(materials targetMaterials: [Double], soles targetSoles: [Double], at now: Double) {
        guard let captured = held else { return }
        materials = captured.values.materials; soles = captured.values.soles; held = nil
        if materials != targetMaterials || soles != targetSoles {
            change = .init(time: now, materials: materials, soles: soles,
                           targetMaterials: targetMaterials, targetSoles: targetSoles)
        }
        if let age = captured.saveAge {
            confirmation = .init(time: now, ageAtAnchor: age, startMix: captured.saveStartMix,
                                 anchorMix: captured.values.savedMix)
        }
    }
    mutating func finishMaterial(_ id: UUID) {
        guard let change, change.id == id, held == nil else { return }
        materials = change.targetMaterials; soles = change.targetSoles; self.change = nil
    }
    mutating func finishConfirmation(_ id: UUID) {
        guard confirmation?.id == id, held == nil else { return }; confirmation = nil
    }
    mutating func reset(materials: [Double], soles: [Double]) {
        self = Self(); self.materials = materials; self.soles = soles
    }
}
