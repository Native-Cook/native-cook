import Foundation

enum CardFinish: String, CaseIterable {
    case rareHolo = "rare-holo"
    case doubleRare = "double-rare"
    case specialIllustration = "special-illustration"
    case hyperRare = "hyper-rare"
}

enum CardStage: String {
    case basic = "BASIC"
    case stage1 = "STAGE 1"
    case stage2 = "STAGE 2"
}

struct CardAttack: Hashable {
    let name: String
    let damage: Int
    let cost: Int
    let description: String
}

struct CardAbility: Hashable {
    let name: String
    let description: String
}

struct DemoCard: Identifiable, Hashable {
    let id: String
    let number: String
    let name: String
    let hp: Int
    let element: String
    let rarity: String
    let finish: CardFinish
    let description: String
    let accent: String
    let accentSoft: String
    let stage: CardStage
    var evolvesFrom: String? = nil
    let category: String
    let height: String
    let weight: String
    var ability: CardAbility? = nil
    let attacks: [CardAttack]
    let weakness: String
    let resistance: String
    let retreat: Int
    let flavorText: String
    let illustrator: String
    let setCode: String
    let elementSymbol: String
}
