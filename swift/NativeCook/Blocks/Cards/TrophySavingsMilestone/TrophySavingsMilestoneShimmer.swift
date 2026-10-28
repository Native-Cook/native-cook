import SwiftUI

struct TrophySavingsMilestoneShimmer: View {
    let scene: TrophySavingsMilestoneCelebration
    var body: some View {
        let age = (scene.clock-scene.contactStart)/max(0.001,scene.cadence)
        let visible = scene.active && !scene.reduced && scene.contactStart >= 0 && age >= 0 && age < 0.30
        let travel = TrophySavingsMilestoneMotion.clamp(age/0.22)
        let sweep = 123+160*(1-(1-travel)*(1-travel))
        let strength = visible ? TrophySavingsMilestoneMotion.smooth(age/0.035)*(1-TrophySavingsMilestoneMotion.smooth((age-0.08)/0.22)) : 0
        Color.white.frame(width:393,height:350)
            .colorEffect(ShaderLibrary.trophyPaperApplauseShimmer(.float(Float(sweep)),.float(Float(strength))))
            .mask(TrophySavingsMilestonePaths.gold.fill(.white)).allowsHitTesting(false).accessibilityHidden(true)
    }
}
