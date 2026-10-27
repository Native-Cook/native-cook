package com.ngocdevv.nativecook.blocks.lists.emberdayreview

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/** `Haptics.impactAsync(Light)`: a typed action joins the list. */
internal fun HapticFeedback.emberDayReviewImpact() = performHapticFeedback(HapticFeedbackType.VirtualKey)

/** `Haptics.selectionAsync()`: a day is picked or an action is ticked. */
internal fun HapticFeedback.emberDayReviewSelection() = performHapticFeedback(HapticFeedbackType.SegmentTick)

/** `Haptics.notificationAsync(Success)`: Commit. */
internal fun HapticFeedback.emberDayReviewSuccess() = performHapticFeedback(HapticFeedbackType.Confirm)
