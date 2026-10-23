import SwiftUI
import UIKit

struct CubeStoryViewerStory {
    var photo: String
    var caption: String
}

struct CubeStoryViewerUser {
    var name: String
    var place: String
    /// How long ago the stories were posted, e.g. "2h".
    var ago: String
    var avatar: String
    var stories: [CubeStoryViewerStory]
}

/// Stories of a few people on the faces of a cube. Each person's photos play full screen under a
/// row of progress bars, their avatar and name; swiping sideways turns the cube to the next person
/// with the finger, and the turning face darkens as it swings away. Tap the right side for the next
/// photo, the left for the one before, and hold to pause and see the photo alone.
struct CubeStoryViewer: View {
    /// The people whose stories sit on the faces of the cube, in order. Three or more.
    var users: [CubeStoryViewerUser] = CubeStoryViewerUser.samples
    /// Plays every story, turns the cube to the next person after the last one and starts over.
    var autoPlay = false
    /// Called with a person's index each time the cube comes to rest on them.
    var onUserChange: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    // The viewer's motion lives in a plain object stepped once a frame, like the RN frame callback:
    // nothing in it is observed, the timeline redraws every frame anyway.
    @State private var engine = CubeStoryViewerEngine()
    // True while the touch is down; the system resets it when it takes the touch away without onEnded.
    @GestureState private var touchDown = false

    var body: some View {
        // The block draws under the system bars and keeps its chrome clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            stage(size: size, insets: insets)
                .frame(width: size.width, height: size.height)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .task(id: users.map(\.name)) { await loadPhotos() }
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        TimelineView(.animation) { timeline in
            let frame = engine.step(
                to: timeline.date,
                width: size.width,
                lengths: users.map(\.stories.count),
                autoPlay: autoPlay,
                reducedMotion: reduceMotion
            )
            ZStack(alignment: .topLeading) {
                Color.black
                ForEach(users.indices, id: \.self) { u in
                    face(u, frame: frame, size: size, insets: insets)
                }
            }
            .frame(width: size.width, height: size.height)
        }
        .frame(width: size.width, height: size.height)
        .background(Color.black)
        .clipped()
        .contentShape(Rectangle())
        .gesture(touch)
        .onChange(of: touchDown) { _, now in
            // After onEnded has had its turn: a touch that did end is already over by then.
            if !now { DispatchQueue.main.async { engine.touchCancelled() } }
        }
        .onAppear { engine.onUserChange = onUserChange }
        .accessibilityElement()
        .accessibilityLabel("Stories from \(users.map(\.name).joined(separator: ", "))")
        .accessibilityHint("Swipe sideways for the next person, tap for the next photo, hold to pause")
    }

    @ViewBuilder
    private func face(_ u: Int, frame: CubeStoryViewerFrame, size: CGSize, insets: EdgeInsets) -> some View {
        let p = CubeStoryViewerEngine.centred(Double(u) - frame.cube, users.count)
        let off = abs(p) >= 1
        if off || frame.marks.count != users.count || users[u].stories.isEmpty {
            // Kept in the tree so a face turning back keeps its views.
            Color.clear.frame(width: size.width, height: size.height)
        } else {
            let mark = frame.marks[u]
            let story = min(max(mark.index, 0), users[u].stories.count - 1)
            CubeStoryViewerFace(
                user: users[u],
                index: story,
                fill: mark.fill,
                photo: engine.photo(users[u].stories[story].photo),
                avatar: engine.photo(users[u].avatar)?.image,
                elapsed: frame.elapsed,
                chrome: frame.chrome,
                shade: min(1, abs(p)) * CubeStoryViewerMetrics.dim,
                reducedMotion: reduceMotion,
                size: size,
                insets: insets
            )
            .opacity(reduceMotion ? 1 - abs(p) : 1)
            .projectionEffect(reduceMotion ? ProjectionTransform() : CubeStoryViewerProjection.transform(p: p, width: size.width, height: size.height))
        }
    }

    private var touch: some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($touchDown) { _, state, _ in state = true }
            .onChanged { value in engine.touchChanged(value) }
            .onEnded { value in engine.touchEnded(value) }
    }

    // Each photo arrives on its own and fades in; until then its face shows its dark ground.
    private func loadPhotos() async {
        // The first photo of each person first, then the rest in order.
        let first = users.compactMap { $0.stories.first?.photo } + users.map(\.avatar)
        let rest = users.flatMap { $0.stories.dropFirst().map(\.photo) }
        for batch in [first, rest] {
            await withTaskGroup(of: (String, UIImage?).self) { group in
                for address in batch {
                    group.addTask { (address, await CubeStoryViewerPhotos.load(address)) }
                }
                for await (address, image) in group {
                    if let image { engine.photoArrived(image, for: address) }
                }
            }
        }
    }
}

/// One person's stories: a face of the cube, laid out flat at the screen's size. The perspective
/// is applied to it as a whole afterwards.
private struct CubeStoryViewerFace: View {
    let user: CubeStoryViewerUser
    let index: Int
    let fill: Double
    let photo: (image: UIImage, at: Double)?
    let avatar: UIImage?
    let elapsed: Double
    let chrome: Double
    let shade: Double
    let reducedMotion: Bool
    let size: CGSize
    let insets: EdgeInsets

    private typealias M = CubeStoryViewerMetrics

    var body: some View {
        ZStack(alignment: .topLeading) {
            Color(hex: 0x0d0d0d)
            photoLayer
            chromeLayer.opacity(chrome)
            Color.black.opacity(shade > 0.002 ? shade : 0)
        }
        .frame(width: size.width, height: size.height)
        .clipped()
        // The face turns as one flat piece.
        .compositingGroup()
    }

    // The photo on show drifts in while its bar fills; alternate photos drift the other way.
    @ViewBuilder
    private var photoLayer: some View {
        if let photo {
            let t = reducedMotion ? 0 : fill
            let side: Double = index % 2 == 0 ? 1 : -1
            Image(uiImage: photo.image)
                .resizable()
                .interpolation(.high)
                .aspectRatio(contentMode: .fill)
                .frame(width: size.width, height: size.height)
                .clipped()
                .scaleEffect(1 + M.kenBurns * t + M.drift / 400)
                .offset(x: side * M.drift * (t - 0.5))
                .opacity(min(max((elapsed - photo.at) / M.imageFade, 0), 1))
        }
    }

    private var chromeLayer: some View {
        ZStack(alignment: .topLeading) {
            LinearGradient(
                stops: [
                    .init(color: .black.opacity(0.55), location: 0),
                    .init(color: .black.opacity(0.18), location: 0.55),
                    .init(color: .black.opacity(0), location: 1),
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .frame(width: size.width, height: insets.top + 150)

            LinearGradient(
                stops: [
                    .init(color: .black.opacity(0), location: 0),
                    .init(color: .black.opacity(0.35), location: 0.45),
                    .init(color: .black.opacity(0.72), location: 1),
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .frame(width: size.width, height: insets.bottom + 300)
            .offset(y: size.height - insets.bottom - 300)

            bars
                .frame(width: max(0, size.width - 20))
                .offset(x: 10, y: insets.top + 8)

            header
                .frame(width: max(0, size.width - 28))
                .offset(x: 12, y: insets.top + 24)

            footer
                .frame(width: size.width, height: size.height, alignment: .bottom)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
    }

    // One segment per photo: full for photos seen, filling for the one on show.
    private var bars: some View {
        HStack(spacing: 4) {
            ForEach(user.stories.indices, id: \.self) { k in
                let f = k < index ? 1 : k == index ? min(1, fill) : 0
                Color.white.opacity(0.32)
                    .overlay(alignment: .leading) {
                        GeometryReader { geo in
                            Color.white.frame(width: max(0, geo.size.width * f))
                        }
                    }
                    .frame(height: 2.5)
                    .clipShape(RoundedRectangle(cornerRadius: 2))
            }
        }
    }

    private var header: some View {
        HStack(spacing: 10) {
            ZStack {
                Circle().strokeBorder(Color.white, lineWidth: 1.5)
                Group {
                    if let avatar {
                        Image(uiImage: avatar).resizable().aspectRatio(contentMode: .fill)
                    } else {
                        Color.white.opacity(0.15)
                    }
                }
                .frame(width: 31, height: 31)
                .clipShape(Circle())
            }
            .frame(width: 38, height: 38)

            VStack(alignment: .leading, spacing: 2) {
                (Text(user.name)
                    .font(.system(size: 15, weight: .semibold))
                    .kerning(-0.1)
                    .foregroundStyle(.white)
                 + Text("  \(user.ago)")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(.white.opacity(0.7)))
                    .lineLimit(1)
                HStack(spacing: 4) {
                    CubeStoryViewerSymbol(name: "mappin", size: 11, color: .white.opacity(0.8))
                    Text(user.place)
                        .font(.system(size: 12.5, weight: .medium))
                        .foregroundStyle(.white.opacity(0.8))
                        .lineLimit(1)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            HStack(spacing: 20) {
                CubeStoryViewerSymbol(name: "ellipsis", size: 20, color: .white)
                CubeStoryViewerSymbol(name: "xmark", size: 19, color: .white)
            }
        }
    }

    private var footer: some View {
        let first = user.name.split(separator: " ").first.map(String.init) ?? user.name
        return VStack(alignment: .leading, spacing: 22) {
            // The line under a photo, with its number; it changes with the photo.
            VStack(alignment: .leading, spacing: 8) {
                Text(String(format: "%02d / %02d", index + 1, user.stories.count))
                    .font(.system(size: 12, weight: .semibold).monospacedDigit())
                    .kerning(1.2)
                    .foregroundStyle(.white.opacity(0.72))
                Text(user.stories[index].caption)
                    .font(.system(size: 27, weight: .bold))
                    .kerning(-0.6)
                    .foregroundStyle(.white)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.leading, 4)
            .padding(.trailing, 28)
            .frame(maxWidth: .infinity, minHeight: 104, maxHeight: 104, alignment: .bottomLeading)

            HStack(spacing: 18) {
                Text("Reply to \(first)…")
                    .font(.system(size: 15))
                    .foregroundStyle(.white.opacity(0.88))
                    .lineLimit(1)
                    .padding(.horizontal, 18)
                    .frame(maxWidth: .infinity, minHeight: 46, maxHeight: 46, alignment: .leading)
                    .overlay(Capsule().strokeBorder(Color.white.opacity(0.55), lineWidth: 1))
                CubeStoryViewerSymbol(name: "heart", size: 25, color: .white)
                CubeStoryViewerSymbol(name: "paperplane", size: 23, color: .white)
            }
        }
        .padding(.horizontal, 16)
        .padding(.bottom, insets.bottom + 10)
    }
}

/// An SF Symbol fitted into a square of `size` points, like `SymbolView` in the RN version.
private struct CubeStoryViewerSymbol: View {
    let name: String
    let size: CGFloat
    let color: Color

    var body: some View {
        Image(systemName: name)
            .resizable()
            .aspectRatio(contentMode: .fit)
            .foregroundStyle(color)
            .frame(width: size, height: size)
    }
}

// MARK: - Samples

private func cubeStoryViewerPhoto(_ id: String) -> String {
    "https://images.unsplash.com/\(id)?auto=format&fit=crop&w=1000&h=2100&q=80"
}

private func cubeStoryViewerAvatar(_ id: String) -> String {
    "https://images.unsplash.com/\(id)?auto=format&fit=crop&w=120&h=120&q=80"
}

extension CubeStoryViewerUser {
    static let samples: [CubeStoryViewerUser] = [
        .init(
            name: "Maren Holt",
            place: "Larch Hollow",
            ago: "2h",
            avatar: cubeStoryViewerAvatar("photo-1453959022778-cfda85dbe0f9"),
            stories: [
                .init(photo: cubeStoryViewerPhoto("photo-1523712999610-f77fbcfc3843"), caption: "Morning comes in sideways through the larches"),
                .init(photo: cubeStoryViewerPhoto("photo-1543683840-c66117bdb1f8"), caption: "One gust, and it rained copper"),
                .init(photo: cubeStoryViewerPhoto("photo-1573776396359-5576727fa835"), caption: "The last leaves on the old oak"),
                .init(photo: cubeStoryViewerPhoto("photo-1637162330294-28cc8540d41d"), caption: "The whole path, gold"),
            ]
        ),
        .init(
            name: "Ines Calder",
            place: "Ocotillo Flats",
            ago: "5h",
            avatar: cubeStoryViewerAvatar("photo-1573689705887-bc0763c82ea2"),
            stories: [
                .init(photo: cubeStoryViewerPhoto("photo-1517025423291-770fb99ae547"), caption: "Four hundred years of standing still"),
                .init(photo: cubeStoryViewerPhoto("photo-1533066636271-fdbe3e84ad80"), caption: "Pink hour at the trailhead"),
                .init(photo: cubeStoryViewerPhoto("photo-1517408191923-f82a669f4ea1"), caption: "Moonrise over the red ridge"),
            ]
        ),
        .init(
            name: "Tove Lind",
            place: "Kvasstind",
            ago: "9h",
            avatar: cubeStoryViewerAvatar("photo-1614358606268-aa86853578b4"),
            stories: [
                .init(photo: cubeStoryViewerPhoto("photo-1565992441121-4367c2967103"), caption: "Air off the cornice, nobody watching"),
                .init(photo: cubeStoryViewerPhoto("photo-1516351950028-b976f7373d70"), caption: "North face, first tracks"),
                .init(photo: cubeStoryViewerPhoto("photo-1609952769887-7905b6b02c0f"), caption: "Waist deep between the pines"),
                .init(photo: cubeStoryViewerPhoto("photo-1610131279204-6d859b4b0563"), caption: "Our lines from today"),
            ]
        ),
    ]
}
