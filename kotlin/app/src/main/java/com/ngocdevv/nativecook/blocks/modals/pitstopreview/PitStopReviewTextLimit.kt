package com.ngocdevv.nativecook.blocks.modals.pitstopreview

import android.icu.text.BreakIterator

/** Limits by grapheme clusters like Intl.Segmenter, without splitting emoji or surrogate pairs. */
internal fun pitStopReviewLimitNote(value: String): String {
    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(value)
    var end = iterator.first()
    repeat(280) {
        val next = iterator.next()
        if (next == BreakIterator.DONE) return value
        end = next
    }
    return value.substring(0, end)
}

internal fun pitStopReviewGraphemeCount(value: String): Int {
    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(value)
    var count = 0
    var boundary = iterator.first()
    while (boundary != BreakIterator.DONE) {
        val next = iterator.next()
        if (next == BreakIterator.DONE) break
        count += 1
        boundary = next
    }
    return count
}
