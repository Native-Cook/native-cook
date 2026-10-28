import SwiftUI

struct TrophySavingsMilestoneCupLight: View {
    let scene: TrophySavingsMilestoneCelebration
    var body: some View {
        Canvas { context,_ in
            let age = (scene.clock-scene.contactStart)/scene.cadence
            guard scene.active && !scene.reduced && scene.contactStart >= 0 && age >= 0 && age < 0.42 else { return }
            let flash = TrophySavingsMilestoneMotion.smooth(age/0.07)*(1-TrophySavingsMilestoneMotion.smooth((age-0.11)/0.31))
            context.clip(to:TrophySavingsMilestonePaths.bowl)
            var facet = context; facet.opacity = 0.28*flash
            facet.fill(TrophySavingsMilestonePaths.facet,with:.linearGradient(Gradient(colors:[Color(hex:0xFFFCE3),Color(hex:0xFFF4BB)]),startPoint:CGPoint(x:158,y:128),endPoint:CGPoint(x:184,y:199)))
            context.fill(TrophySavingsMilestonePaths.glint,with:.color(.white.opacity(0.65*flash)))
        }.frame(width:393,height:350).allowsHitTesting(false).accessibilityHidden(true)
    }
}
