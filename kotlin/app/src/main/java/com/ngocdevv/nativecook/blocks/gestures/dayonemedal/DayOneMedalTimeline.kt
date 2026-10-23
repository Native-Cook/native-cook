package com.ngocdevv.nativecook.blocks.gestures.dayonemedal

/** Event and dwell driven translation of medal-demo.ts; time is simulation seconds. */
internal class DayOneMedalTimeline {
    enum class Command { RESET, OPEN, FORGE, TURN, FLICK, WEAR, NEXT, CLOSE }
    enum class Event { RESET, ARRIVED, STRUCK, COOLED, LANDED, BACK, FRONT, ENGRAVED, CAUGHT, UNWORN, MILESTONE, CLOSED }
    private data class Step(val command: Command?, val event: Event?, val dwell: Float = 0f)
    private val steps = listOf(
        Step(Command.RESET, Event.RESET),
        Step(Command.OPEN, Event.ARRIVED, 0.65f),
        Step(Command.FORGE, Event.STRUCK),
        Step(null, Event.COOLED, 1f),
        Step(Command.TURN, Event.BACK),
        Step(null, Event.ENGRAVED, 0.7f),
        Step(Command.TURN, Event.FRONT, 0.9f),
        Step(Command.FLICK, Event.LANDED, 0.7f),
        Step(Command.WEAR, Event.CAUGHT, 1.4f),
        Step(Command.FLICK, null, 3f),
        Step(Command.WEAR, Event.UNWORN, 0.6f),
        Step(Command.NEXT, Event.MILESTONE, 1.4f),
        Step(Command.NEXT, Event.MILESTONE, 1.6f),
        Step(Command.CLOSE, Event.CLOSED, 0.9f),
    )
    private val seen = mutableSetOf<Event>()
    private var index = -1
    private var waiting: Event? = null
    private var deadline = Float.POSITIVE_INFINITY
    private var watchdog = Float.POSITIVE_INFINITY
    private var running = false

    fun start(now: Float) {
        stop()
        index = -1
        running = true
        deadline = now
    }

    fun stop() {
        running = false
        waiting = null
        deadline = Float.POSITIVE_INFINITY
        watchdog = Float.POSITIVE_INFINITY
        seen.clear()
    }

    fun signal(event: Event, now: Float) {
        if (!running) return
        seen += event
        if (waiting == event) complete(now)
    }

    private fun complete(now: Float) {
        waiting = null
        watchdog = Float.POSITIVE_INFINITY
        deadline = now + steps[index].dwell
    }

    fun advance(now: Float): Command? {
        if (!running) return null
        if (now >= watchdog) {
            stop()
            return Command.CLOSE
        }
        if (now + 1e-10f < deadline) return null
        deadline = Float.POSITIVE_INFINITY
        index = (index + 1) % steps.size
        val step = steps[index]
        waiting = step.event
        watchdog = now + 60f
        if (step.command != null) seen.clear()
        if (step.event == null || seen.contains(step.event)) complete(now)
        return step.command
    }
}
