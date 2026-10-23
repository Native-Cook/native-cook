import SwiftUI

/// Everything that is drawn, rebuilt once per frame from the simulation while the deck moves. Each part
/// takes plain values, so a part whose values did not change is not redrawn.
struct CassetteDeckStage: View {
    let model: CassetteDeckModel
    let metrics: CassetteDeckMetrics

    var body: some View {
        let sim = model.snapshot()
        ZStack(alignment: .topLeading) {
            CassetteDeckBackdrop(state: CassetteDeckBackdropState(sim: sim), metrics: metrics)
            ForEach(0..<CassetteDeckSim.slotCount, id: \.self) { slot in
                CassetteDeckCassette(slot: slot, state: CassetteDeckCassetteState(sim: sim, slot: slot), metrics: metrics)
            }
            ForEach(0..<CassetteDeckSim.keyCount, id: \.self) { index in
                CassetteDeckKeyCap(index: index, depth: sim.d[index], dip: sim.dip, reduced: sim.reduced, metrics: metrics)
            }
        }
        .frame(width: metrics.width, height: metrics.height, alignment: .topLeading)
    }
}
