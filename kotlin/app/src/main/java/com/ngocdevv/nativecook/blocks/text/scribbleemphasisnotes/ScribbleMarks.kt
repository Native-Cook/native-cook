package com.ngocdevv.nativecook.blocks.text.scribbleemphasisnotes

import kotlin.math.max
import kotlin.math.min

// The finger's own highlights: a fixed number of slots in one flat array, stepped and drawn without objects.

/** Slot layout: first word, last word, amount on the page 0..1, target 0|1, tint index, used 0|1, age stamp. */
internal const val SLOT = 7
internal const val S_FIRST = 0
internal const val S_LAST = 1
internal const val S_AMOUNT = 2
internal const val S_TARGET = 3
internal const val S_TINT = 4
internal const val S_USED = 5
internal const val S_STAMP = 6

internal fun emptyMarks() = FloatArray(USER_MARKS * SLOT)

/** Moves every mark toward its target; a mark that has lifted off frees its slot. True if anything changed. */
internal fun stepMarks(list: FloatArray, dt: Float, snap: Boolean): Boolean {
    var changed = false
    for (i in 0 until USER_MARKS) {
        val o = i * SLOT
        if (list[o + S_USED] == 1f && (list[o + S_AMOUNT] != list[o + S_TARGET] || list[o + S_TARGET] == 0f)) changed = true
    }
    if (!changed) return false
    for (i in 0 until USER_MARKS) {
        val o = i * SLOT
        if (list[o + S_USED] == 0f) continue
        val target = list[o + S_TARGET]
        var amount = list[o + S_AMOUNT]
        if (snap) {
            amount = target
        } else if (amount < target) {
            amount = min(target, amount + dt / USER_DRAW)
        } else if (amount > target) {
            amount = max(target, amount - dt / USER_LIFT)
        }
        list[o + S_AMOUNT] = amount
        if (target == 0f && amount <= 0f) list[o + S_USED] = 0f
    }
    return true
}

/** The slot of a mark that is staying and covers [word], or -1. */
internal fun markOver(list: FloatArray, word: Int): Int {
    var found = -1
    var newest = -1f
    for (i in 0 until USER_MARKS) {
        val o = i * SLOT
        if (list[o + S_USED] == 1f && list[o + S_TARGET] == 1f && word >= list[o + S_FIRST] && word <= list[o + S_LAST] && list[o + S_STAMP] > newest) {
            newest = list[o + S_STAMP]
            found = i
        }
    }
    return found
}

/** A free slot, or the oldest one. */
internal fun takeSlot(list: FloatArray): Int {
    var oldest = 0
    for (i in 0 until USER_MARKS) {
        val o = i * SLOT
        if (list[o + S_USED] == 0f) return i
        if (list[o + S_STAMP] < list[oldest * SLOT + S_STAMP]) oldest = i
    }
    return oldest
}

/** Writes a mark into a slot. [amount] 0 swipes it on; 1 puts it down whole (a live drag). */
internal fun putMark(list: FloatArray, slot: Int, first: Int, last: Int, amount: Float, tint: Int, stamp: Int) {
    val o = slot * SLOT
    list[o + S_FIRST] = first.toFloat()
    list[o + S_LAST] = last.toFloat()
    list[o + S_AMOUNT] = amount
    list[o + S_TARGET] = 1f
    list[o + S_TINT] = tint.toFloat()
    list[o + S_USED] = 1f
    list[o + S_STAMP] = stamp.toFloat()
}

/** Sends one mark (or, with slot -1, all of them) off the page. */
internal fun liftMarks(list: FloatArray, slot: Int) {
    for (i in 0 until USER_MARKS) {
        if (slot >= 0 && i != slot) continue
        list[i * SLOT + S_TARGET] = 0f
    }
}
