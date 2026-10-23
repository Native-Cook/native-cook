import SwiftUI

let cardAspectRatio: CGFloat = 63 / 88
let cardHeightRatio: CGFloat = 88 / 63

enum CollectionCardBackPalette {
    static let background = Color(css: "#e5e9ef")
    static let patternDark = Color(css: "#cfd5df")
    static let edge = Color(css: "rgba(255, 255, 255, 0.76)")
    static let edgeInner = Color(css: "rgba(255, 255, 255, 0.48)")
    static let seal = Color(css: "#737b89")
    static let markerBorder = Color(css: "rgba(105, 115, 132, 0.19)")
    static let markerInk = Color(css: "rgba(105, 115, 132, 0.22)")
}

enum CollectionGalleryPalette {
    static let background = Color(css: "#e9edf3")
    static let text = Color(css: "#202631")
}

enum CollectionCardBackdropPalette {
    static let background = CollectionCardBackPalette.background
    static let pattern = Color(css: "#b9c3cf")
    static let seal = CollectionCardBackPalette.seal
}

struct CardTemplatePalette {
    let frame: Color
    let frameLight: Color
    let header: Color
    let panel: Color
    let panelDeep: Color
    let ink: Color
    let mutedInk: Color
    let artBorder: Color
    let energy: Color
    let energyInk: Color
}

let cardTemplatePalettes: [CardFinish: CardTemplatePalette] = [
    .rareHolo: CardTemplatePalette(
        frame: Color(css: "#b6b7b2"),
        frameLight: Color(css: "#f1eee6"),
        header: Color(css: "#ef7e55"),
        panel: Color(css: "#efaa7b"),
        panelDeep: Color(css: "#d45f3f"),
        ink: Color(css: "#17140f"),
        mutedInk: Color(css: "#4f342b"),
        artBorder: Color(css: "#7d3c2b"),
        energy: Color(css: "#d85333"),
        energyInk: Color(css: "#fff2df")
    ),
    .doubleRare: CardTemplatePalette(
        frame: Color(css: "#b7bab4"),
        frameLight: Color(css: "#f5f2e8"),
        header: Color(css: "#b5d74c"),
        panel: Color(css: "#d6ea85"),
        panelDeep: Color(css: "#8bad3e"),
        ink: Color(css: "#171b0d"),
        mutedInk: Color(css: "#3f4d24"),
        artBorder: Color(css: "#59752d"),
        energy: Color(css: "#356b32"),
        energyInk: Color(css: "#f4ffe0")
    ),
    .specialIllustration: CardTemplatePalette(
        frame: Color(css: "#c5d2da"),
        frameLight: Color(css: "#f7fbfc"),
        header: Color(css: "#75c4e4"),
        panel: Color(css: "#a9dded"),
        panelDeep: Color(css: "#4b9ec7"),
        ink: Color(css: "#10202b"),
        mutedInk: Color(css: "#315467"),
        artBorder: Color(css: "#3a7295"),
        energy: Color(css: "#2f83b0"),
        energyInk: Color(css: "#effcff")
    ),
    .hyperRare: CardTemplatePalette(
        frame: Color(css: "#b88832"),
        frameLight: Color(css: "#fff0a8"),
        header: Color(css: "#d9a441"),
        panel: Color(css: "#edcd75"),
        panelDeep: Color(css: "#a97222"),
        ink: Color(css: "#23170a"),
        mutedInk: Color(css: "#5e421a"),
        artBorder: Color(css: "#83551a"),
        energy: Color(css: "#4f2d72"),
        energyInk: Color(css: "#fff2bc")
    ),
]
