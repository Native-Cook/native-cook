package com.ngocdevv.nativecook.blocks.gestures.boosterpackopening

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.util.Log

private const val ASSETS = "boosterpackopening"
private const val TAG = "BoosterPackOpening"

/**
 * The block's sounds on three kinds of channel, as in the source: the ambience loops for as long
 * as the block is on screen and the app is in front, the tearing sound loops only while the cut
 * advances, and the opening and the card cues play once. Short sounds come from a [SoundPool],
 * the ambience from a looping [MediaPlayer]. A sound that has not finished loading is skipped,
 * never queued, and a failure leaves the block silent but usable.
 */
internal class PackAudio(
    context: Context,
    rarities: List<CardRarity>,
) {
    private val attributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    private val pool = SoundPool.Builder().setMaxStreams(6).setAudioAttributes(attributes).build()
    private val loaded = HashSet<Int>()
    private val tear: Int
    private val open: Int
    private val nextCard: Int
    private val reveals = HashMap<CardRarity, Int>()
    private var background: MediaPlayer? = null
    private var backgroundReady = false
    private var backgroundPlaying = false

    private var enabled = false
    private var disposed = false
    private var phase = IDLE
    private var moving = false
    private var freshSwipe = true
    private var tearStream = 0
    private var openStream = 0
    private var cardStream = 0
    private var revealStream = 0
    private var lastAdvanceIndex = -1
    private var lastRevealIndex = -1

    init {
        pool.setOnLoadCompleteListener { _, sound, status -> if (status == 0) loaded.add(sound) }
        tear = load(context, "booster-tear.wav")
        open = load(context, "booster-open.wav")
        nextCard = load(context, "booster-card-next.wav")
        // Only the cues this pack uses are loaded.
        for (rarity in cardAudioCues(rarities)) {
            reveals[rarity] = load(context, "booster-reveal-${rarity.cue}.wav")
        }
        try {
            val player = MediaPlayer()
            context.assets.openFd("$ASSETS/booster-ambience-loop.wav").use {
                player.setDataSource(it.fileDescriptor, it.startOffset, it.length)
            }
            player.setAudioAttributes(attributes)
            player.isLooping = true
            player.setOnPreparedListener {
                backgroundReady = true
                refreshBackground()
            }
            player.setOnErrorListener { _, _, _ ->
                backgroundReady = false
                backgroundPlaying = false
                true
            }
            player.prepareAsync()
            background = player
        } catch (error: Exception) {
            Log.w(TAG, "The ambience could not be loaded", error)
        }
    }

    private fun load(context: Context, name: String): Int =
        try {
            context.assets.openFd("$ASSETS/$name").use { pool.load(it, 1) }
        } catch (error: Exception) {
            Log.w(TAG, "$name could not be loaded", error)
            0
        }

    private fun play(sound: Int, loop: Boolean = false): Int {
        if (!enabled || disposed || sound == 0 || sound !in loaded) return 0
        return pool.play(sound, 1f, 1f, 1, if (loop) -1 else 0, 1f)
    }

    private fun stop(stream: Int) {
        if (stream != 0) pool.stop(stream)
    }

    // Music follows the screen and the app only; no gesture path touches this channel.
    private fun refreshBackground() {
        val player = background ?: return
        if (!enabled || disposed || backgroundPlaying || !backgroundReady) return
        try {
            // Resume the existing playback head, including after the app was in the background.
            player.start()
            backgroundPlaying = true
        } catch (error: IllegalStateException) {
            Log.w(TAG, "The ambience could not start", error)
        }
    }

    private fun stopBackground() {
        val player = background ?: return
        if (!backgroundPlaying) return
        backgroundPlaying = false
        try {
            player.pause()
        } catch (error: IllegalStateException) {
            Log.w(TAG, "The ambience could not pause", error)
        }
    }

    private fun resetEffects() {
        if (disposed) return
        phase = IDLE
        moving = false
        freshSwipe = true
        stop(tearStream)
        stop(openStream)
        stop(cardStream)
        stop(revealStream)
        tearStream = 0
        openStream = 0
        cardStream = 0
        revealStream = 0
        lastAdvanceIndex = -1
        lastRevealIndex = -1
    }

    /** A valid tear begins: old effects stop, and the tearing sound waits for movement. */
    fun beginTear() {
        if (!enabled || disposed) return
        resetEffects()
        phase = TEARING
    }

    /** The cut started or stopped advancing. A hold or a move backward pauses the sound. */
    fun setTearing(nextMoving: Boolean) {
        if (!enabled || disposed || phase != TEARING || nextMoving == moving) return
        moving = nextMoving
        if (moving) {
            if (freshSwipe || tearStream == 0) {
                stop(tearStream)
                tearStream = play(tear, loop = true)
                if (tearStream != 0) freshSwipe = false
            } else {
                pool.resume(tearStream)
            }
        } else if (tearStream != 0) {
            pool.pause(tearStream)
        }
    }

    /** The cut reached the far edge: the opening sound, once. */
    fun commitCut() {
        if (!enabled || disposed || phase != TEARING) return
        phase = OPENED
        moving = false
        stop(tearStream)
        tearStream = 0
        stop(openStream)
        openStream = play(open)
    }

    fun beginCardAdvance(fromIndex: Int) {
        if (!enabled || disposed || fromIndex < 0 || fromIndex <= lastAdvanceIndex || fromIndex < lastRevealIndex) return
        lastAdvanceIndex = fromIndex
        // End the previous card's tail before accepting a new swipe.
        stop(cardStream)
        stop(revealStream)
        revealStream = 0
        cardStream = play(nextCard)
    }

    fun revealCard(index: Int, rarity: CardRarity) {
        if (!enabled || disposed || index < 0 || index <= lastRevealIndex || index <= lastAdvanceIndex) return
        lastRevealIndex = index
        stop(revealStream)
        revealStream = play(reveals[rarity] ?: 0)
    }

    /** An early release or a new pack: effects stop, the ambience carries on. */
    fun reset() = resetEffects()

    /** Follows the screen and the app: everything stops while the block is not in front. */
    fun setEnabled(nextEnabled: Boolean) {
        if (disposed || enabled == nextEnabled) return
        enabled = nextEnabled
        if (enabled) {
            refreshBackground()
        } else {
            resetEffects()
            stopBackground()
        }
    }

    fun dispose() {
        if (disposed) return
        resetEffects()
        stopBackground()
        disposed = true
        enabled = false
        pool.release()
        background?.release()
        background = null
    }

    private companion object {
        const val IDLE = 0
        const val TEARING = 1
        const val OPENED = 2
    }
}
