import UIKit

/// The block's own content: an invented restaurant guide. Photos are credited in the block's notes.
struct MorphingCardDialogPlace {
    struct Fact {
        let value: String
        let label: String
    }

    let key: String
    let name: String
    let area: String
    let about: String
    let facts: [Fact]
    let photo: UIImage

    static let brand = "Forkful"
    static let title = "Weekend tables"
    static let meals = ["Brunch", "Dinner", "Late"]
    static let reserve = "Reserve a table"

    private static func photo(_ name: String) -> UIImage {
        guard let path = Bundle.main.path(forResource: "MorphingCardDialog-\(name)", ofType: "webp"),
              let image = UIImage(contentsOfFile: path) else { return UIImage() }
        return image.preparingForDisplay() ?? image
    }

    private static func facts(_ rating: String, _ time: String, _ price: String) -> [Fact] {
        [Fact(value: rating, label: "Rating"), Fact(value: time, label: "Next table"), Fact(value: price, label: "Price")]
    }

    static let all: [MorphingCardDialogPlace] = [
        MorphingCardDialogPlace(
            key: "ember", name: "Ember & Rye", area: "Canal Quarter",
            about: "Squash, peppers and beets roasted over coals, with a short list of natural reds. The counter seats face the fire.",
            facts: facts("4.8", "7:30 PM", "$$$"), photo: photo("veg")),
        MorphingCardDialogPlace(
            key: "lume", name: "Osteria Lume", area: "Old Mill Row",
            about: "Hand-cut tagliatelle, slow tomato and a wedge of aged cheese grated at the table. Loud, warm and always full.",
            facts: facts("4.7", "8:15 PM", "$$"), photo: photo("pasta")),
        MorphingCardDialogPlace(
            key: "saltwater", name: "Saltwater", area: "Harbour Steps",
            about: "Seared scallops and line-caught fish, plated simply. Ask for a window table when the boats come in.",
            facts: facts("4.9", "6:45 PM", "$$$$"), photo: photo("scallop")),
        MorphingCardDialogPlace(
            key: "quay", name: "Quay Nine", area: "North Quay",
            about: "Charred cauliflower, black rice cakes and a view of the water under strings of lights. Good for a long evening.",
            facts: facts("4.6", "9:00 PM", "$$"), photo: photo("lights")),
        MorphingCardDialogPlace(
            key: "oak", name: "Hollow Oak", area: "Fernhill",
            about: "Crisp duck leg, herb dumplings and whatever came out of the garden that morning. Twelve tables, one sitting.",
            facts: facts("4.8", "7:00 PM", "$$$"), photo: photo("duck")),
        MorphingCardDialogPlace(
            key: "lantern", name: "Tin Lantern", area: "Market Lane",
            about: "Thick pork chops with sweet peppers, cooked in cast iron and carried to the table still spitting.",
            facts: facts("4.5", "8:45 PM", "$$"), photo: photo("chop")),
    ]
}
