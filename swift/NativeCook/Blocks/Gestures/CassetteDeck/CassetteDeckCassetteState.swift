import Foundation

/// What one cassette needs from the simulation to be drawn. Equal states draw the same, so a cassette
/// that is not moving costs nothing.
struct CassetteDeckCassetteState: Equatable {
    private typealias Motion = CassetteDeckMotion

    /// Cassette widths from the deck.
    var x: Double
    /// Degrees of yaw.
    var yaw: Double
    /// Chassis dip plus rattle, in points before scaling.
    var shake: Double
    var opacity: Double
    /// How much of each side face of the shell shows (0...1).
    var edgeLeft: Double
    var edgeRight: Double
    var sheenOpacity: Double
    var leftAngle: Double
    var rightAngle: Double
    /// Tape pack scale against the largest pack.
    var leftPack: Double
    var rightPack: Double
    /// Strength of the ghost hubs, and the angle each ghost step trails by.
    var ghostAmount: Double
    var ghostLag: Double

    init(sim: CassetteDeckSim, slot: Int) {
        let x = sim.slotX(slot)
        let yawUnit = sim.reduced
            ? 0
            : max(-1, min(1, (-sim.pv * Motion.Pager.rotatePerSpeed) / Motion.Pager.rotateMax))
        self.x = x
        yaw = sim.reduced
            ? 0
            : max(-Motion.Pager.rotateMax, min(Motion.Pager.rotateMax, -sim.pv * Motion.Pager.rotatePerSpeed))
        shake = sim.dip * Motion.Chassis.dipTravel + sim.rattle * Motion.Chassis.rattleTravel
        opacity = sim.reduced ? 0.35 + 0.65 * sim.fade : 1
        edgeLeft = max(0, yawUnit)
        edgeRight = max(0, -yawUnit)
        sheenOpacity = sim.reduced ? 0 : min(1, abs(x) * Motion.Material.sheenGain + abs(yawUnit))
        leftAngle = sim.ang[slot * 2]
        rightAngle = sim.ang[slot * 2 + 1]
        let maxPack = CassetteDeckTheme.Layout.tapePack.max
        leftPack = CassetteDeckSim.tapeRadius(1 - sim.prog[slot]) / maxPack
        rightPack = CassetteDeckSim.tapeRadius(sim.prog[slot]) / maxPack
        let omega = abs(sim.w[slot]) * Motion.Transport.baseOmega
        ghostAmount = sim.reduced
            ? 0
            : max(0, min(1, (omega - Motion.Material.blurFrom) / Motion.Material.blurSpan))
        ghostLag = (sim.w[slot] * Motion.Transport.baseOmega * Motion.Material.shutter) / Motion.Material.ghosts
    }
}
