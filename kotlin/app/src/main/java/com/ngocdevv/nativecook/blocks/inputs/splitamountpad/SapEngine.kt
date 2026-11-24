// Split Amount Pad — everything that moves, off Compose state: the typed amount and its pooled tiles,
// the shares, the keys' presses, the clock that plays SCRIPT and the hand-over between the loop and a
// finger. One frame callback calls tick(); the draw lambdas read the numbers after touching `frame`,
// so a key press or a frame never recomposes anything.
package com.ngocdevv.nativecook.blocks.inputs.splitamountpad

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlin.math.max

internal const val HIDDEN = 0
internal const val LIVE = 1
internal const val LEAVING = 2

private const val MODE_AUTO = 0
private const val MODE_TOUCH = 1
private const val MODE_RETURN = 2
/** what a press came to: typed, ignored, refused at the limit */
private const val DONE = 1
private const val IGNORED = 0
private const val REFUSED = -1

private val POWERS = intArrayOf(1, 10, 100, 1000, 10000, 100000)

/** one pooled digit tile: its row of the table (what the pad last asked for) and how it is reacting */
internal class Tile {
    var rowState = HIDDEN
    var rowGlyph = 0
    var rowCentre = 0f
    var rowBorn = 0
    var rowOrder = 0

    var glyph = 0
    val x = Anim(0f)
    val enter = Anim(0f)
    val leave = Anim(0f)
    var turn = 0f
    private var seenBorn = -1
    private var seenState = HIDDEN
    private var seenCentre = 0f

    /** a newly born tile takes its glyph and drops in, a living one slides, a removed one is flicked away */
    fun react(instant: Boolean) {
        if (rowState == LIVE) {
            if (rowBorn != seenBorn) {
                seenBorn = rowBorn
                glyph = rowGlyph
                x.snap(rowCentre)
                leave.snap(0f)
                turn = (if (rowBorn % 2 == 0) 1f else -1f) * (DROP_TURN_MIN + (rowBorn * 13) % DROP_TURN_SPAN)
                if (instant) {
                    enter.snap(1f)
                } else {
                    enter.snap(0f)
                    enter.spring(1f, SPRING_DROP, rowOrder * T_DROP_STAGGER)
                }
            } else if (rowCentre != seenCentre) {
                if (instant) x.snap(rowCentre) else x.spring(rowCentre, SPRING_SLIDE)
            }
            seenCentre = rowCentre
        } else if (rowState == LEAVING && seenState == LIVE) {
            if (instant) leave.snap(1f) else leave.tween(1f, T_FLICK, rowOrder * T_FLICK_STAGGER, EaseOutQuad)
        }
        seenState = rowState
    }

    fun step(dt: Float) {
        x.step(dt)
        enter.step(dt)
        leave.step(dt)
    }
}

/** one friend's rolling share: six digit columns, the leading ones hidden while the share is small */
internal class ShareRoll {
    /** 0 the cents' ones … 5 the thousands */
    val digit = Array(6) { Anim(0f) }
    val shown = Array(6) { place -> Anim(if (place <= 2) 1f else 0f) }
    /** the width of the leading columns that are hidden */
    val hidden = Anim(SHARE_DIGIT * 3 + SHARE_MARK)
    val thousands = Anim(0f)

    fun show(cents: Int, friend: Int, instant: Boolean) {
        for (place in 0 until 6) {
            val d = (cents / POWERS[place]) % 10
            val on = if (place <= 2 || cents >= POWERS[place]) 1f else 0f
            if (instant) {
                digit[place].snap(d.toFloat())
                shown[place].snap(on)
            } else {
                digit[place].spring(d.toFloat(), SPRING_ROLL, friend * T_ROLL_FRIEND + place * T_ROLL_PLACE)
                shown[place].tween(on, 140f)
            }
        }
        val gone = (if (cents < 1000) 1 else 0) + (if (cents < 10000) 1 else 0) + (if (cents < 100000) 1 else 0)
        val w = gone * SHARE_DIGIT + (if (cents < 100000) SHARE_MARK else 0f)
        val big = if (cents >= 100000) 1f else 0f
        if (instant) {
            hidden.snap(w)
            thousands.snap(big)
        } else {
            hidden.spring(w, SPRING_SLIDE)
            thousands.tween(big, 140f)
        }
    }

    fun step(dt: Float) {
        for (a in digit) a.step(dt)
        for (a in shown) a.step(dt)
        hidden.step(dt)
        thousands.step(dt)
    }
}

internal class SapEngine(private val m: Metrics) {
    /** bumped once a frame; a draw lambda reads it so it is drawn again with the new numbers */
    var frame by mutableIntStateOf(0)
        private set

    /* what the composition tells the engine (plain fields, written from a side effect) */
    var auto = false
    var reduced = false
    var layout: SapLayout? = null
    /** where the pad and the avatars start, dp from the screen's top */
    var padY = 0f
    var seatsY = 0f
    var onTick: () -> Unit = {}
    var onWarn: () -> Unit = {}

    /* ------------------------------------------------------------ the amount */
    val tiles = Array(POOL) { Tile() }
    private var typed = IntArray(0)
    /** the pooled tile showing each typed code */
    private var seats = IntArray(0)
    private var cursor = 0
    private var births = 0
    val dollarX = Anim(-(m.dollar + DOLLAR_GAP + m.digit) / 2f)
    val commaX = Anim(0f)
    val commaOn = Anim(0f)
    val empty = Anim(1f)
    val shake = Anim(0f)
    val limit = Anim(0f)
    private var cents = 0
    private var active = intArrayOf(1, 1, 1, 1)
    private var shares = intArrayOf(Int.MIN_VALUE, Int.MIN_VALUE, Int.MIN_VALUE, Int.MIN_VALUE)

    /* ------------------------------------------------------------ the people and the keys */
    val rolls = Array(FRIENDS) { ShareRoll() }
    val friendOn = Array(FRIENDS) { Anim(1f) }
    val friendPress = Array(FRIENDS) { Anim(0f) }
    val keyPress = Array(12) { Anim(0f) }

    /* ------------------------------------------------------------ the clock and the hand-over */
    private var clock = 0.0
    private var mode = MODE_AUTO
    private var autoStart = 0.0
    private var prevT = -1.0
    private var lastTouch = 0.0
    private var returnAt = 0.0
    private var held = 0
    var ghostX = 0f
        private set
    var ghostY = 0f
        private set
    val ghostA = Anim(0f)
    var autoKey = -1
        private set
    var autoFriend = -1
        private set
    var autoPress = 0f
        private set

    init {
        setShares(splitShares(0, active))
    }

    private fun setShares(next: IntArray) {
        for (i in 0 until FRIENDS) {
            // someone who sits out keeps the digits they had
            if (next[i] != shares[i] && next[i] >= 0) rolls[i].show(next[i], i, reduced)
        }
        shares = next
    }

    private fun setActive(next: IntArray) {
        for (i in 0 until FRIENDS) {
            if (next[i] != active[i]) {
                if (reduced) friendOn[i].snap(next[i].toFloat()) else friendOn[i].spring(next[i].toFloat(), SPRING_TOGGLE)
            }
        }
        active = next
    }

    /** the next pooled tile that is not on screen as a live digit */
    private fun takeTile(): Int {
        val from = cursor
        for (n in 0 until POOL) {
            val i = (from + n) % POOL
            if (tiles[i].rowState != LIVE) {
                cursor = i + 1
                return i
            }
        }
        return from % POOL
    }

    /** put these codes on screen: tiles of codes that are gone leave, new codes (tile -1) are born, the rest slide */
    private fun show(codes: IntArray, seatsIn: IntArray) {
        val next = seatsIn.copyOf()
        val before = seats
        var leaving = 0
        for (i in before.indices.reversed()) {
            if (before[i] !in next) {
                tiles[before[i]].rowState = LEAVING
                tiles[before[i]].rowOrder = leaving
                leaving += 1
            }
        }
        val lay = amountLayout(codes, m)
        var arriving = 0
        for (i in codes.indices) {
            if (next[i] < 0) {
                val tile = takeTile()
                next[i] = tile
                births += 1
                tiles[tile].rowState = LIVE
                tiles[tile].rowGlyph = codes[i]
                tiles[tile].rowBorn = births
                tiles[tile].rowOrder = arriving
                arriving += 1
            }
            tiles[next[i]].rowCentre = lay.centres[i]
        }
        typed = codes
        seats = next
        for (tile in tiles) tile.react(reduced)

        val isEmpty = codes.isEmpty()
        if (reduced) {
            dollarX.snap(lay.dollar)
            commaX.snap(lay.comma)
            commaOn.snap(if (lay.hasComma) 1f else 0f)
            empty.snap(if (isEmpty) 1f else 0f)
        } else {
            dollarX.spring(lay.dollar, SPRING_SLIDE)
            // a comma that was hidden appears in place; one on screen slides with the digits
            if (lay.hasComma) {
                if (commaOn.value < 0.05f) commaX.snap(lay.comma) else commaX.spring(lay.comma, SPRING_SLIDE)
                commaOn.spring(1f, SPRING_DROP)
            } else {
                commaOn.tween(0f, 120f)
            }
            empty.tween(if (isEmpty) 1f else 0f, if (isEmpty) 220f else 90f)
        }
        cents = centsOf(codes)
        setShares(splitShares(cents, active))
    }

    private fun refuse(hard: Boolean) {
        if (reduced) return
        if (hard) {
            shake.tween(*Array(SHAKE_HARD.size) { Leg(SHAKE_HARD[it], T_SHAKE) })
            limit.tween(Leg(1f, 120f), Leg(0f, 400f, delayMs = 900f))
        } else {
            shake.tween(*Array(SHAKE_SOFT.size) { Leg(SHAKE_SOFT[it], T_SHAKE) })
        }
    }

    /** one key: the amount's rules. Returns DONE, IGNORED or REFUSED (over the limit). */
    private fun pressKey(code: Int): Int {
        val codes = typed
        if (code == KEY_CLEAR) {
            if (codes.isEmpty()) return IGNORED
            show(IntArray(0), IntArray(0))
            return DONE
        }
        if (code == KEY_BACK) {
            if (codes.isEmpty()) {
                refuse(false)
                return IGNORED
            }
            show(codes.copyOf(codes.size - 1), seats.copyOf(seats.size - 1))
            return DONE
        }
        val dot = dotAt(codes)
        if (code == KEY_DOT) {
            if (dot >= 0) {
                refuse(false)
                return IGNORED
            }
            // a dot on an empty amount brings its zero with it
            if (codes.isEmpty()) show(intArrayOf(0, KEY_DOT), intArrayOf(-1, -1)) else show(codes + KEY_DOT, seats + -1)
            return DONE
        }
        val nextCodes: IntArray
        val nextSeats: IntArray
        if (dot < 0 && codes.size == 1 && codes[0] == 0) {
            // a lone zero is replaced, not followed
            if (code == 0) {
                refuse(false)
                return IGNORED
            }
            nextCodes = intArrayOf(code)
            nextSeats = intArrayOf(-1)
        } else {
            if (dot >= 0 && codes.size - dot - 1 >= MAX_DECIMALS) {
                refuse(false)
                return IGNORED
            }
            if (dot < 0 && codes.size >= MAX_INT_DIGITS) {
                refuse(true)
                return REFUSED
            }
            nextCodes = codes + code
            nextSeats = seats + -1
        }
        if (centsOf(nextCodes) > LIMIT_CENTS) {
            refuse(true)
            return REFUSED
        }
        show(nextCodes, nextSeats)
        return DONE
    }

    /** tap a friend in or out of the split; the payer (seat 0) always stays in */
    private fun toggleFriend(index: Int): Int {
        if (index == 0) return IGNORED
        val next = active.copyOf()
        next[index] = if (next[index] > 0) 0 else 1
        setActive(next)
        setShares(splitShares(cents, next))
        return DONE
    }

    /** back to the resting screen: nothing typed, everyone in */
    private fun rest() {
        if (typed.isNotEmpty()) show(IntArray(0), IntArray(0))
        if (active.any { it == 0 }) {
            setActive(intArrayOf(1, 1, 1, 1))
            setShares(splitShares(cents, active))
        }
    }

    /** reduced motion: the finished split, standing still */
    fun showFinal() {
        if (typed.isEmpty()) show(intArrayOf(1, 2, 8, KEY_DOT, 4, 0), intArrayOf(-1, -1, -1, -1, -1, -1))
    }

    /* ------------------------------------------------------------ a finger */

    private fun takeOver() {
        lastTouch = clock
        if (mode == MODE_TOUCH) return
        mode = MODE_TOUCH
        ghostA.tween(0f, 150f)
        autoPress = 0f
        autoKey = -1
        autoFriend = -1
    }

    /** a finger landed on a pad cell: the digit is committed now, not when it lifts */
    fun keyDown(cell: Int) {
        keyPress[cell].tween(1f, T_KEY_DOWN)
        userKey(KEY_CODES[cell])
    }

    /** the held backspace fired */
    fun keyHeld() = userKey(KEY_CLEAR)

    fun keyUp(cell: Int) {
        keyPress[cell].tween(0f, T_KEY_UP)
        userRelease()
    }

    fun friendDown(index: Int) {
        friendPress[index].tween(1f, T_KEY_DOWN)
        takeOver()
        held += 1
        if (toggleFriend(index) == DONE) onTick()
    }

    fun friendUp(index: Int) {
        friendPress[index].tween(0f, T_KEY_UP)
        userRelease()
    }

    private fun userKey(code: Int) {
        takeOver()
        held += if (code == KEY_CLEAR) 0 else 1
        when (pressKey(code)) {
            DONE -> onTick()
            REFUSED -> onWarn()
        }
    }

    private fun userRelease() {
        held = max(0, held - 1)
        lastTouch = clock
    }

    /* ------------------------------------------------------------ the clock */

    /** where the ghost finger aims for a script row, dp */
    private fun aimX(row: Int, l: SapLayout): Float {
        val cue = SCRIPT[row]
        if (cue.kind == DO_FRIEND) return SIDE + (cue.arg + 0.5f) * l.column
        return SIDE + (cellOf(cue.arg) % 3) * (l.keyW + l.gap) + l.keyW / 2f
    }

    private fun aimY(row: Int, l: SapLayout): Float {
        val cue = SCRIPT[row]
        if (cue.kind == DO_FRIEND) return seatsY + l.disc / 2f
        return padY + (cellOf(cue.arg) / 3) * (l.keyH + l.gap) + l.keyH / 2f
    }

    fun tick(dt: Float) {
        clock += dt
        val now = clock
        val l = layout
        if (mode == MODE_AUTO) {
            if (!auto || padY == 0f || l == null) {
                // not playing, or not laid out yet: the loop waits at t = 0
                ghostA.snap(0f)
                autoStart = now
                prevT = -1.0
            } else {
                val t = (((now - autoStart) % LOOP) + LOOP) % LOOP
                if (t < prevT) rest()
                for (cue in SCRIPT) {
                    if (prevT < cue.at && t >= cue.at) {
                        when (cue.kind) {
                            DO_KEY -> pressKey(cue.arg)
                            DO_FRIEND -> toggleFriend(cue.arg)
                            DO_CLEAR -> pressKey(KEY_CLEAR)
                        }
                    }
                }
                // the ghost finger: glides to its next target, presses on the beat
                val row = ghostRow(t)
                val from = ghostPrev(row)
                val g = ghostGlide(t, row)
                ghostX = aimX(from, l) + (aimX(row, l) - aimX(from, l)) * g
                ghostY = aimY(from, l) + (aimY(row, l) - aimY(from, l)) * g
                ghostA.snap(ghostAlpha(t))
                autoPress = ghostPress(t, row)
                autoKey = if (SCRIPT[row].kind == DO_KEY) cellOf(SCRIPT[row].arg) else -1
                autoFriend = if (SCRIPT[row].kind == DO_FRIEND) SCRIPT[row].arg else -1
                prevT = t
            }
        } else if (mode == MODE_TOUCH) {
            if (auto && held == 0 && now - lastTouch >= IDLE) {
                mode = MODE_RETURN
                returnAt = now
                rest()
            }
        } else if (now - returnAt >= RETURN) {
            autoStart = now
            prevT = -1.0
            mode = MODE_AUTO
        }

        for (tile in tiles) tile.step(dt)
        for (roll in rolls) roll.step(dt)
        for (a in friendOn) a.step(dt)
        for (a in friendPress) a.step(dt)
        for (a in keyPress) a.step(dt)
        dollarX.step(dt)
        commaX.step(dt)
        commaOn.step(dt)
        empty.step(dt)
        shake.step(dt)
        limit.step(dt)
        ghostA.step(dt)
        frame += 1
    }
}
