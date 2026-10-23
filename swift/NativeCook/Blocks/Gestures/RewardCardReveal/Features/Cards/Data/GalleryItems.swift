import Foundation

let galleryItems: [GalleryItem] = [
    .owned(OwnedGalleryItem(
        cardId: "verdant-sproutling",
        count: 1,
        id: "the-awakening",
        image: "the-awakening",
        name: "The Awakening"
    )),
    .locked(LockedGalleryItem(
        id: "locked-awakening-set",
        lockCount: 3
    )),
    .owned(OwnedGalleryItem(
        cardId: "tide-fox",
        count: 1,
        id: "wandering-mind",
        image: "wandering-mind",
        name: "Wandering Mind"
    )),
    .locked(LockedGalleryItem(
        id: "locked-wandering-set",
        lockCount: 2
    )),
]
