package com.ngocdevv.nativecook.blocks.cards.foliofeaturelibrary

import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.ngocdevv.nativecook.shared.EaseOutStrong
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** The three haptics of the block (`Haptics.impactAsync(Light)`, `selectionAsync`, `notificationAsync(Success)`). */
internal enum class FolioHaptic { Light, Selection, Success }

/** The scroll states the model drives while the autoplay script owns them. */
internal class FolioScrollHost(
    val outer: ScrollState,
    val carousel: LazyListState,
    val library: ScrollState,
    val detail: ScrollState,
    val topics: ScrollState,
)

/**
 * The state and behaviour of the React Native component `FolioFeatureLibrary`: the same states, the same
 * handlers, the same autoplay script. React's `setTimeout` is the [scheduler]; Reanimated shared values are
 * [FolioTween]s; both run on the [clock], so a frame is a function of time.
 */
internal class FolioFeatureLibraryModel(
    private val clock: FolioFeatureLibraryClock,
    initialMetrics: FolioMetrics,
) {
    // Inputs, refreshed by the composable after every composition.
    var autoPlay = false
    var reduced = false
    var metrics: FolioMetrics = initialMetrics
    var onBookCreated: ((List<String>) -> Unit)? = null
    var haptic: (FolioHaptic) -> Unit = {}
    var dismissKeyboard: () -> Unit = {}

    /** Measures where a card of the origin kind is, relative to the block's root; null when unknown. */
    var frameOf: (FolioReturnOrigin) -> FolioFrame? = { null }
    var density = 1f
    var userOwned = false

    val frozen: Boolean get() = clock.frozen
    val scheduler = FolioFeatureLibraryScheduler()

    private var host: FolioScrollHost? = null
    private var mounted = true
    private var eventTime = Double.NaN

    /** Time of the action being run (its scheduled time), else the clock's time. */
    private val timeNow: Double get() = if (eventTime.isNaN()) clock.now() else eventTime

    // Discrete state (React `useState`).
    var screen by mutableStateOf(FolioScreen.Explore)
    var customTopic by mutableStateOf("")
    var topicInputActive by mutableStateOf(false)
    var generating by mutableStateOf(false)
    var saved by mutableStateOf(false)
    var selectedBookId by mutableStateOf(FolioBookId.Noticing)
    var bookmarks by mutableStateOf(emptyList<FolioBookId>())
    var manual by mutableStateOf(false)
    var foreground by mutableStateOf(true)
    var activeCategory by mutableStateOf("Health & Fitness")
    var addingTopic by mutableStateOf(false)
    var authorReady by mutableStateOf(false)
    var feature by mutableStateOf(0)
    var introSize by mutableFloatStateOf(310f)
    var introAccessible by mutableStateOf(true)
    var openingOperation by mutableStateOf<FolioOpening?>(null)
    var lastNotice by mutableStateOf("")
    var origin by mutableStateOf(FolioFrame(16f, initialMetrics.top + 78f, initialMetrics.cardWidth, 292f))

    /** Library scroll offset in dp as the animated scroll handler reports it (`scrollY`). */
    var scrollY by mutableFloatStateOf(0f)

    private val topicsOpenState = mutableStateOf(false)
    private val selectedState = mutableStateOf(emptyList<String>())
    private val noticeState = mutableStateOf<String?>(null)
    private val generatedState = mutableStateOf(false)
    private val coverReadyState = mutableStateOf(false)
    private val detailReadyState = mutableStateOf(false)

    // Times at which a state last turned on, read by the entering animations (seconds on the clock).
    var generatedAt = 0.0
        private set
    var coverReadyAt = 0.0
        private set
    var detailReadyAt = 0.0
        private set
    var authorReadyAt = 0.0
        private set

    // Animated values (Reanimated shared values).
    val appProgress = FolioTween(0f)
    val detailProgress = FolioTween(0f)
    val headerCollapse = FolioTween(0f)
    val sheet = FolioTween(0f)
    val noticeFade = FolioTween(0f)

    // Programmatic scrolling.
    val outerScript = FolioScriptedScroll(
        actual = { (host?.outer?.value ?: 0) / density },
        limit = { (host?.outer?.maxValue ?: 0) / density },
    )
    val detailScript = FolioScriptedScroll(
        actual = { (host?.detail?.value ?: 0) / density },
        limit = { (host?.detail?.maxValue ?: 0) / density },
    )
    val topicScript = FolioScriptedScroll(
        actual = { (host?.topics?.value ?: 0) / density },
        limit = { (host?.topics?.maxValue ?: 0) / density },
    )
    val carouselScript = FolioScriptedScroll(
        actual = { carouselOffsetPx() / density },
        limit = { Float.MAX_VALUE },
    )
    private var topicUntil = 0.0

    // Refs and timers (React `useRef`).
    private var screenRef = FolioScreen.Explore
    private var returnOrigin: FolioReturnOrigin = FolioReturnOrigin.Feature(0)
    private var touching = false
    private val scrolling = HashSet<String>()
    private var resumeTimer: FolioTimer? = null
    private var headerTimer: FolioTimer? = null
    private var openingTimer: FolioTimer? = null
    private var generationTimer: FolioTimer? = null
    private var coverTimer: FolioTimer? = null
    private var detailTimer: FolioTimer? = null
    private var authorTimer: FolioTimer? = null
    private var shimmerStart = Double.NaN
    private var loaderHold = 0f
    private var lastLibraryDp = 0f

    /** The picked topics. The first pick widens the list, and the new chips fade in from that moment. */
    var selected: List<String>
        get() = selectedState.value
        set(value) {
            if (value.isNotEmpty() && selectedState.value.isEmpty()) moreTopicsAt = timeNow
            selectedState.value = value
        }

    /** When the suggested topics last appeared (sheet opened with a pick, or the first pick), for their fade-in. */
    var moreTopicsAt = 0.0
        private set

    var topicsOpen: Boolean
        get() = topicsOpenState.value
        set(value) {
            if (value == topicsOpenState.value) return
            topicsOpenState.value = value
            if (value) moreTopicsAt = timeNow
            // Modal animationType: slide, or fade under Reduce Motion.
            val seconds = if (reduced) 0.2 else 0.3
            sheet.animateTo(if (value) 1f else 0f, timeNow, seconds, if (reduced) LinearEasing else EaseOutStrong)
        }

    var notice: String?
        get() = noticeState.value
        set(value) {
            if (value != null) lastNotice = value
            noticeState.value = value
            noticeFade.animateTo(if (value != null) 1f else 0f, timeNow, 0.2, LinearEasing)
        }

    var generated: Boolean
        get() = generatedState.value
        set(value) {
            if (value && !generatedState.value) generatedAt = timeNow
            generatedState.value = value
        }

    var coverReady: Boolean
        get() = coverReadyState.value
        set(value) {
            if (value && !coverReadyState.value) coverReadyAt = timeNow
            coverReadyState.value = value
        }

    /** Setting it also schedules the author's portrait, which arrives 4.4 s after the text (instantly under Reduce Motion). */
    var detailReady: Boolean
        get() = detailReadyState.value
        set(value) {
            if (value == detailReadyState.value) return
            if (value) detailReadyAt = timeNow
            detailReadyState.value = value
            scheduler.cancel(authorTimer)
            val delay = if (value && !reduced) FOLIO_AUTHOR_DELAY else 0.0
            authorTimer = scheduler.at(timeNow + delay, FolioFeatureLibraryScheduler.TIMERS) {
                authorTimer = null
                applyAuthorReady(value)
            }
        }

    val libraryVisible: Boolean get() = screen == FolioScreen.Library && !topicsOpen && notice == null
    val detailVisible: Boolean get() = screen == FolioScreen.Detail && !topicsOpen && notice == null
    val exploreVisible: Boolean get() = screen == FolioScreen.Explore && notice == null
    val statusText: String?
        get() = when {
            generating || (generated && !coverReady) -> "Creating content"
            screen == FolioScreen.Detail && !authorReady -> if (detailReady) "Searching images" else "Creating content"
            else -> null
        }

    val topicList: List<String> get() = if (selected.isNotEmpty()) FolioTopics + FolioMoreTopics else FolioTopics

    private fun applyAuthorReady(value: Boolean) {
        if (value && !authorReady) authorReadyAt = timeNow
        authorReady = value
    }

    fun attach(host: FolioScrollHost, density: Float) {
        this.host = host
        this.density = density
    }

    private fun carouselOffsetPx(): Float {
        val state = host?.carousel ?: return 0f
        val step = (metrics.cardWidth * density).roundToInt() + (14f * density).roundToInt()
        return (state.firstVisibleItemIndex * step + state.firstVisibleItemScrollOffset).toFloat()
    }

    /** `useAnimatedStyle` reads of `collapse`: the header collapses by itself, or as soon as the library is scrolled. */
    fun collapse(now: Double): Float = max(headerCollapse.value(now), folioClamp01(scrollY / 90f))

    /** The eased collapse the header card and orb use; a plain 0 or 1 under Reduce Motion. */
    fun headerK(now: Double): Float {
        val c = collapse(now)
        return if (reduced) (if (c > 0.5f) 1f else 0f) else EaseOutStrong.transform(c)
    }

    /** The loading screen's progress 0 to 1, linear over the operation's duration. */
    fun loaderProgress(now: Double): Float {
        if (reduced) return 1f
        val op = openingOperation ?: return loaderHold
        return folioClamp01(((now - op.startedAt) / op.duration).toFloat())
    }

    fun shimmer(now: Double): Float {
        if (shimmerStart.isNaN()) return 0f
        return (((now - shimmerStart) % 0.9) / 0.9).toFloat()
    }

    private fun refreshShimmer(now: Double) {
        val on = !reduced && foreground && (generating || screen == FolioScreen.Loading || (generated && !coverReady))
        if (on) {
            if (shimmerStart.isNaN()) shimmerStart = now
        } else {
            shimmerStart = Double.NaN
        }
    }

    // Interruption and resume.

    private fun queueResume() {
        scheduler.cancel(resumeTimer)
        resumeTimer = null
        val blocked = !autoPlay || reduced || !foreground || notice != null || topicsOpen ||
            screen != FolioScreen.Explore || userOwned || touching || scrolling.isNotEmpty()
        if (blocked) return
        resumeTimer = scheduler.at(timeNow + 2.0, FolioFeatureLibraryScheduler.TIMERS) {
            resumeTimer = null
            if (mounted && !userOwned && !touching && scrolling.isEmpty()) manual = false
        }
    }

    /** Called when the resume conditions may have changed; re-arms the 2 s timer while a person is in control. */
    fun resumeConditionsChanged() {
        if (manual) queueResume()
    }

    fun cancelResume() {
        scheduler.cancel(resumeTimer)
        resumeTimer = null
    }

    private fun stopScripts() {
        outerScript.stop()
        carouselScript.stop()
        detailScript.stop()
    }

    fun interrupt() {
        manual = true
        cancelResume()
        scheduler.cancel(headerTimer)
        headerTimer = null
        headerCollapse.cancel(timeNow)
        stopScripts()
        // Once a person uses a library, later demo cycles must never reset its data.
        if (screen != FolioScreen.Explore) userOwned = true
        queueResume()
    }

    fun touchBegin() {
        touching = true
        interrupt()
    }

    fun touchEnd(stillDown: Boolean) {
        touching = stillDown
        queueResume()
    }

    fun scrollingChanged(key: String, active: Boolean) {
        if (active) {
            // The script's own scrolling is not a person's drag.
            if ((key == "outer" && outerScript.driving) || (key == "carousel" && carouselScript.driving)) return
            scrolling.add(key)
            interrupt()
        } else {
            scrolling.remove(key)
            queueResume()
        }
    }

    fun applyForeground(active: Boolean) {
        foreground = active
        if (!active) {
            touching = false
            scrolling.clear()
            scheduler.cancel(headerTimer)
            headerTimer = null
            appProgress.cancel(timeNow)
            detailProgress.cancel(timeNow)
            headerCollapse.cancel(timeNow)
        } else {
            appProgress.set(if (screenRef == FolioScreen.Explore) 0f else 1f)
            detailProgress.set(if (screenRef == FolioScreen.Detail) 1f else 0f)
        }
    }

    fun dispose() {
        mounted = false
    }

    // Navigation.

    private fun captureOrigin(node: FolioReturnOrigin?, done: () -> Unit) {
        val frame = if (node != null) frameOf(node) else null
        if (frame != null && frame.width > 0f && frame.height > 0f) origin = frame
        done()
    }

    fun moveTo(next: FolioScreen) {
        if (next != FolioScreen.Explore) cancelResume()
        screenRef = next
        screen = next
        val instant = reduced || !foreground
        val now = timeNow
        val appTarget = if (next == FolioScreen.Explore) 0f else 1f
        val detailTarget = if (next == FolioScreen.Detail) 1f else 0f
        if (instant) {
            appProgress.set(appTarget)
            detailProgress.set(detailTarget)
        } else {
            appProgress.animateTo(appTarget, now, if (next == FolioScreen.Explore) 0.22 else 0.28, EaseOutStrong)
            detailProgress.animateTo(detailTarget, now, 0.26, EaseOutStrong)
        }
    }

    private fun collapseHeader() {
        if (!foreground || !mounted || touching || scrolling.isNotEmpty()) return
        if (reduced) headerCollapse.set(1f) else headerCollapse.animateTo(1f, timeNow, 1.0, EaseOutStrong)
    }

    private fun clearOpening() {
        if (openingOperation != null) loaderHold = loaderProgress(timeNow)
        openingOperation = null
        scheduler.cancel(openingTimer)
        openingTimer = null
    }

    private fun clearGeneration() {
        scheduler.cancel(generationTimer)
        generationTimer = null
    }

    private fun clearCover() {
        scheduler.cancel(coverTimer)
        coverTimer = null
    }

    private fun clearDetail() {
        scheduler.cancel(detailTimer)
        detailTimer = null
    }

    private fun beginOpening(seconds: Double) {
        val operation = FolioOpening(timeNow, seconds)
        openingOperation = operation
        openingTimer = scheduler.at(operation.deadline, FolioFeatureLibraryScheduler.TIMERS) {
            openingTimer = null
            openingOperation = null
            moveTo(FolioScreen.Library)
            headerTimer = scheduler.at(operation.deadline + 1.0, FolioFeatureLibraryScheduler.TIMERS) {
                headerTimer = null
                collapseHeader()
            }
        }
        moveTo(FolioScreen.Loading)
    }

    private fun beginDetail(book: FolioBookId, seconds: Double) {
        selectedBookId = book
        detailReady = false
        clearDetail()
        detailTimer = scheduler.at(timeNow + seconds, FolioFeatureLibraryScheduler.TIMERS) {
            detailTimer = null
            detailReady = true
        }
        detailScript.jump(0f)
        moveTo(FolioScreen.Detail)
    }

    private fun beginGeneration(topics: List<String>, seconds: Double, coverDelay: Double, notifyHost: Boolean) {
        generating = true
        val operation = FolioGeneration(timeNow + seconds, topics.toList(), notifyHost, coverDelay)
        clearGeneration()
        generationTimer = scheduler.at(operation.deadline, FolioFeatureLibraryScheduler.TIMERS) {
            generationTimer = null
            generated = true
            coverReady = false
            generating = false
            topicsOpen = false
            clearCover()
            coverTimer = scheduler.at(operation.deadline + operation.coverDelay, FolioFeatureLibraryScheduler.TIMERS) {
                coverTimer = null
                coverReady = true
            }
            if (operation.notifyHost) {
                onBookCreated?.invoke(operation.topics)
                haptic(FolioHaptic.Success)
            }
        }
    }

    // Handlers of the screens.

    fun openFeature(index: Int, node: FolioReturnOrigin? = null) {
        interrupt()
        clearOpening()
        clearDetail()
        if (index != 0) {
            feature = index
            notice = if (index == 1) {
                "Keep a little list of places that spark your curiosity. Start with the coast, a forest path, or a city you have never seen."
            } else {
                "A small portrait can become a different way to remember a moment. This original illustration is a preview from the collection."
            }
            return
        }
        returnOrigin = when (node) {
            FolioReturnOrigin.Recent -> FolioReturnOrigin.Recent
            FolioReturnOrigin.Plus -> FolioReturnOrigin.Plus
            else -> FolioReturnOrigin.Feature(index)
        }
        userOwned = true
        cancelResume()
        feature = index
        scrollY = 0f
        headerCollapse.set(0f)
        captureOrigin(node ?: FolioReturnOrigin.Feature(index)) {
            beginOpening(if (reduced) 0.15 else 0.9)
        }
        haptic(FolioHaptic.Light)
    }

    fun openDetail(book: FolioBookId) {
        interrupt()
        beginDetail(book, if (reduced) 0.12 else 0.65)
    }

    fun closeLibrary() {
        interrupt()
        clearOpening()
        clearDetail()
        captureOrigin(returnOrigin) { moveTo(FolioScreen.Explore) }
    }

    fun backToLibrary() {
        interrupt()
        clearDetail()
        moveTo(FolioScreen.Library)
    }

    fun readBook() {
        interrupt()
        clearDetail()
        detailReady = true
        detailScript.scrollTo(560f, timeNow, !reduced)
    }

    fun toggleBookmark() {
        interrupt()
        val book = selectedBookId
        bookmarks = if (book in bookmarks) bookmarks - book else bookmarks + book
        haptic(FolioHaptic.Selection)
    }

    fun toggleSaved() {
        interrupt()
        saved = !saved
    }

    fun openTopics() {
        interrupt()
        topicsOpen = true
    }

    fun closeTopics() {
        dismissKeyboard()
        topicInputActive = false
        interrupt()
        clearGeneration()
        topicsOpen = false
        generating = false
    }

    fun generate() {
        if (selected.isEmpty() || generating) return
        dismissKeyboard()
        topicInputActive = false
        interrupt()
        beginGeneration(selected, if (reduced) 0.25 else 1.6, if (reduced) 0.12 else 0.7, true)
    }

    fun toggleTopic(topic: String) {
        interrupt()
        selected = if (topic in selected) selected - topic else selected + topic
        haptic(FolioHaptic.Selection)
    }

    fun commitCustomTopic() {
        val value = customTopic.trim()
        if (value.isNotEmpty() && value !in selected) selected = selected + value
        customTopic = ""
        addingTopic = false
    }

    fun startAddingTopic() {
        interrupt()
        addingTopic = true
    }

    fun topicInputFocused() {
        topicInputActive = true
        interrupt()
        topicScrollToEnd()
    }

    fun topicInputBlurred() {
        topicInputActive = false
        if (customTopic.trim().isEmpty()) addingTopic = false
    }

    fun topicScrollToEnd() {
        topicScript.scrollToEnd(timeNow, !reduced)
        topicUntil = timeNow + 0.8
    }

    fun selectCategory(category: String) {
        interrupt()
        activeCategory = category
        haptic(FolioHaptic.Selection)
    }

    fun showInvite() {
        interrupt()
        notice = "Your invitation is ready. Explore any collection to make it yours."
    }

    fun showApp(app: FolioApp) {
        interrupt()
        notice = "${app.name}: ${app.body}"
    }

    fun showShare() {
        interrupt()
        notice = "A personal shelf of original sample stories. Generated books stay in this session when you go back."
    }

    fun scrollHome() {
        interrupt()
        outerScript.scrollTo(0f, timeNow, !reduced)
    }

    // The autoplay script (the `useEffect` that calls `cycle()`).

    fun startTimeline() {
        cycle(if (frozen) 0.0 else clock.now())
    }

    fun stopTimeline() {
        scheduler.cancelTag(FolioFeatureLibraryScheduler.TIMELINE)
        headerCollapse.cancel(timeNow)
    }

    private fun cycle(base: Double) {
        returnOrigin = FolioReturnOrigin.Feature(0)
        screenRef = FolioScreen.Explore
        clearOpening()
        clearGeneration()
        clearCover()
        clearDetail()
        screen = FolioScreen.Explore
        feature = 0
        generated = false
        coverReady = false
        selected = emptyList()
        topicsOpen = false
        generating = false
        saved = false
        bookmarks = emptyList()
        selectedBookId = FolioBookId.Noticing
        detailReady = false
        appProgress.set(0f)
        detailProgress.set(0f)
        scrollY = 0f
        headerCollapse.set(0f)
        outerScript.jump(0f)
        carouselScript.jump(0f)

        fun at(seconds: Double, action: () -> Unit) {
            scheduler.at(base + seconds, FolioFeatureLibraryScheduler.TIMELINE, action)
        }

        val step = metrics.cardWidth + 12f
        at(0.5) { carouselScript.scrollTo(step, timeNow, true) }
        at(1.5) { carouselScript.scrollTo(step * 2f, timeNow, true) }
        at(2.5) { carouselScript.scrollTo(0f, timeNow, true) }
        at(3.5) { outerScript.scrollTo(240f, timeNow, true) }
        at(5.5) { outerScript.scrollTo(480f, timeNow, true) }
        at(6.6) { outerScript.scrollTo(900f, timeNow, true) }
        at(8.0) { outerScript.scrollTo(0f, timeNow, true) }
        at(9.75) { captureOrigin(FolioReturnOrigin.Feature(0)) { beginOpening(2.75) } }
        at(16.0) { topicsOpen = true }
        at(17.25) { selected = listOf("Productivity") }
        at(18.25) { beginGeneration(listOf("Productivity"), 4.25, 1.5, false) }
        at(26.5) { beginDetail(FolioBookId.Rituals, 2.0) }
        // The reading pass of the reference: a short lift, a long read down to the author, back to the top.
        at(29.8) { detailScript.scrollTo(170f, timeNow, true) }
        at(30.6) { detailScript.scrollTo(720f, timeNow, true) }
        at(31.8) { detailScript.scrollTo(960f, timeNow, true) }
        at(32.8) { detailScript.scrollToEnd(timeNow, true) }
        at(33.8) { detailScript.scrollTo(170f, timeNow, true) }
        at(34.6) { detailScript.scrollTo(0f, timeNow, true) }
        at(35.5) { moveTo(FolioScreen.Library) }
        at(36.75) { captureOrigin(returnOrigin) { moveTo(FolioScreen.Explore) } }
        at(FOLIO_FEATURE_LIBRARY_LOOP) { cycle(base + FOLIO_FEATURE_LIBRARY_LOOP) }
    }

    // The frame loop.

    private fun runAt(time: Double, action: () -> Unit) {
        eventTime = time
        try {
            action()
        } finally {
            eventTime = Double.NaN
        }
        refreshShimmer(time)
    }

    /** One tick: fire what is due, publish the time to the draw phase, and let scripted scrolls move the lists. */
    suspend fun pump() {
        // A still waits for the first layout, so the origin of a card can be measured.
        if (frozen) {
            withFrameNanos { }
            withFrameNanos { }
        }
        while (true) {
            withFrameNanos { }
            val now = clock.now()
            clock.publish()
            scheduler.advanceTo(now) { time, action -> runAt(time, action) }
            refreshShimmer(now)
            val scrolls = host ?: continue
            val libraryDp = scrolls.library.value / density
            if (libraryDp != lastLibraryDp) {
                lastLibraryDp = libraryDp
                scrollY = libraryDp
            }
            val accessible = collapse(now) < 0.5f
            if (accessible != introAccessible) introAccessible = accessible
            driveScrolls(scrolls, now)
        }
    }

    private suspend fun driveScrolls(scrolls: FolioScrollHost, now: Double) {
        if (outerScript.driving) applyVertical(scrolls.outer, outerScript.valueDp(now))
        if (detailScript.driving) applyVertical(scrolls.detail, detailScript.valueDp(now))
        if (topicScript.driving) {
            applyVertical(scrolls.topics, topicScript.valueDp(now))
            if (now > topicUntil) topicScript.stop()
        }
        if (carouselScript.driving) {
            val step = (metrics.cardWidth * density).roundToInt() + (14f * density).roundToInt()
            val target = (carouselScript.valueDp(now) * density).roundToInt().coerceAtLeast(0)
            val current = scrolls.carousel.firstVisibleItemIndex * step + scrolls.carousel.firstVisibleItemScrollOffset
            if (step > 0 && abs(current - target) >= 1) scrolls.carousel.scrollToItem(target / step, target % step)
        }
    }

    private fun applyVertical(state: ScrollState, targetDp: Float) {
        val delta = (targetDp * density).roundToInt() - state.value
        if (delta != 0) state.dispatchRawDelta(delta.toFloat())
    }
}
