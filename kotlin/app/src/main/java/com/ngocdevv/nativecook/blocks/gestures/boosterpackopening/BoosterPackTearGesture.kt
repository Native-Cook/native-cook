package com.ngocdevv.nativecook.blocks.gestures.boosterpackopening

private const val TRACE_INTERVALS = 80

/** Advance the uncut edge while keeping the last point attached to the finger. */
internal fun advanceTearPoints(
    current: List<Point>,
    position: Point,
    pack: Rect,
    direction: Int,
): List<Point>? {
    if (current.isEmpty() || pack.width <= 0) return null

    val last = current[current.size - 1]
    val rawTravel = direction * (position.x - last.x)
    // Moving over material already torn cannot move the cut's frontier,
    // including its height while the finger is behind that frontier.
    if (rawTravel < 0) return null

    val entryX = if (direction == 1) pack.x else pack.x + pack.width
    val x = clamp(position.x, pack.x, pack.x + pack.width)
    val corridorTop = pack.y + pack.height * 0.08
    val corridorBottom = pack.y + pack.height * 0.27
    val fraction = if (rawTravel > 0) direction * (x - last.x) / rawTravel else 1.0
    // A fast event can land outside the pack. Keep the actual segment's
    // intersection with the edge, instead of using the outside finger's y.
    val y =
        clamp(
            last.y + (position.y - last.y) * fraction,
            corridorTop,
            corridorBottom,
        )
    if (x == last.x && y == last.y) return null

    // The entry and historical anchors are permanent; only the live endpoint
    // is replaced. At most 79 interior grid anchors survive any number of events.
    val next = ArrayList<Point>(current.size + 2)
    if (current.size > 1) {
        for (index in 0 until current.size - 1) next.add(current[index])
    } else {
        next.add(current[0])
    }
    if (rawTravel > 0) {
        for (index in 1 until TRACE_INTERVALS) {
            val anchorX = entryX + direction * ((pack.width * index) / TRACE_INTERVALS)
            val fromLast = direction * (anchorX - last.x)
            if (fromLast < 0 || direction * (x - anchorX) <= 0) continue
            next.add(
                Point(
                    anchorX,
                    clamp(
                        last.y + (position.y - last.y) * (fromLast / rawTravel),
                        corridorTop,
                        corridorBottom,
                    ),
                ),
            )
        }
    }
    next.add(Point(x, y))
    return next
}
