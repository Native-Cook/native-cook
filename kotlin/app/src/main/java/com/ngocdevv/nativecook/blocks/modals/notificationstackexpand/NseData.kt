package com.ngocdevv.nativecook.blocks.modals.notificationstackexpand

import androidx.compose.ui.graphics.Color

// Relay, a team inbox for the (invented) Fernway team. Names, channels and numbers are made up.

enum class NoticeBadge { Channel, File }
enum class NoticeApp { Calendar, Files }

data class Notice(
    val id: String,
    /** Who it is from; their initials draw the avatar. */
    val from: String,
    val body: String,
    val time: String,
    /** Three stops for the avatar disc, top-left to bottom-right. */
    val tint: List<Color>,
    /** A small glyph on the avatar's corner. */
    val badge: NoticeBadge? = null,
    /** From an app rather than a person: a squircle with the app's glyph instead of initials. */
    val app: NoticeApp? = null,
)

data class NoticeGroup(val title: String, val channel: String, val notices: List<Notice>)

private fun c(hex: Long) = Color(0xFF000000 or hex)

/** The hero group: grouped messages from one channel. The stack shows the first four. */
val LAUNCH = NoticeGroup(
    title = "Launch review",
    channel = "#launch-review",
    notices = listOf(
        Notice("l1", "Priya Nair", "Signed off on the onboarding copy. Ship it with the new empty states?", "now",
            listOf(c(0xF2B48A), c(0xE07A5F), c(0xB5525C)), NoticeBadge.Channel),
        Notice("l2", "Tomás Reyes", "Build 4.12 is on TestFlight. Crash rate is down to 0.3% since Monday.", "4m",
            listOf(c(0x9CC9B8), c(0x4E9A86), c(0x2F6F66)), NoticeBadge.Channel),
        Notice("l3", "Ines Kowal", "Moved the review to 3:30 so design can join from the Lisbon office.", "12m",
            listOf(c(0xB9B4E8), c(0x7F79C9), c(0x5A5497)), NoticeBadge.Channel),
        Notice("l4", "Dev Shah", "The pricing page still shows last year’s annual price. Fixing it now.", "18m",
            listOf(c(0xF1D58E), c(0xD9A74A), c(0xA8772F)), NoticeBadge.Channel),
    ),
)

/** A single notice under the group. */
internal val STANDUP = Notice("s1", "Calendar", "Standup in 10 min · Larch room, second floor", "9:50",
    listOf(c(0xF5A3A0), c(0xE5605B), c(0xC23F44)), app = NoticeApp.Calendar)

/** A second, smaller group that stays collapsed. */
internal val CRIT = NoticeGroup(
    title = "Design crit",
    channel = "#design-crit",
    notices = listOf(
        Notice("c1", "Noor Amari", "Uploaded 6 frames of Checkout v3. Comments welcome before Thursday.", "9:12",
            listOf(c(0xA7C7EE), c(0x5C8FD6), c(0x3A64A8)), NoticeBadge.File),
        Notice("c2", "Leo Brandt", "Left notes on the receipt screen.", "8:47",
            listOf(c(0xC9D8A2), c(0x93AE5B), c(0x64803A)), NoticeBadge.Channel),
    ),
)

internal val YESTERDAY = listOf(
    Notice("y1", "Hana Ito", "Thanks for covering the release notes. The changelog reads great.", "Mon",
        listOf(c(0xF0B6CF), c(0xD86F9A), c(0xA84A74)), NoticeBadge.Channel),
    Notice("y2", "Files", "Q4 roadmap.key was shared with you by Ines Kowal.", "Mon",
        listOf(c(0xB7C2CC), c(0x7D8B98), c(0x56636F)), app = NoticeApp.Files),
)

internal val FILTERS = listOf("All", "Mentions", "Threads")
