package expo.modules.subjectsegmentation

import expo.modules.kotlin.exception.CodedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal const val CANCELLED_ERROR_CODE = "ERR_SUBJECT_SEGMENTATION_CANCELLED"

internal fun segmentationError(
  code: String,
  message: String,
  cause: Throwable? = null
): CodedException = CodedException(code, message, cause)

internal class SegmentationCancelledException(
  val requestId: String
) : CancellationException("Subject segmentation request '$requestId' was cancelled.")

/**
 * Tracks one native operation. The terminal state is atomically claimed before
 * its promise is settled so cancellation can never race a stale successful result.
 */
internal class SegmentationRequestToken(
  val requestId: String
) {
  private val state = AtomicInteger(STATE_ACTIVE)
  private val job = AtomicReference<Job?>(null)

  val isCancelled: Boolean
    get() = state.get() == STATE_CANCELLED

  fun attach(job: Job) {
    this.job.set(job)
    if (isCancelled) {
      job.cancel(SegmentationCancelledException(requestId))
    }
  }

  fun cancel() {
    if (state.compareAndSet(STATE_ACTIVE, STATE_CANCELLED)) {
      job.get()?.cancel(SegmentationCancelledException(requestId))
    }
  }

  fun throwIfCancelled() {
    if (isCancelled) {
      throw SegmentationCancelledException(requestId)
    }
  }

  fun tryFinish(): Boolean = state.compareAndSet(STATE_ACTIVE, STATE_FINISHED)

  companion object {
    private const val STATE_ACTIVE = 0
    private const val STATE_CANCELLED = 1
    private const val STATE_FINISHED = 2
  }
}

/**
 * A cancelled request id stays cancelled for the lifetime of the module. This
 * also covers cancellation arriving between frame preparation and segmentation.
 */
internal class SegmentationRequestRegistry {
  private val lock = Any()
  private val cancelledRequestIds = mutableSetOf<String>()
  private val activeRequests = mutableMapOf<String, MutableSet<SegmentationRequestToken>>()

  fun register(requestId: String): SegmentationRequestToken {
    val token = SegmentationRequestToken(requestId)
    synchronized(lock) {
      activeRequests.getOrPut(requestId) { mutableSetOf() }.add(token)
      if (requestId in cancelledRequestIds) {
        token.cancel()
      }
    }
    return token
  }

  fun unregister(token: SegmentationRequestToken) {
    synchronized(lock) {
      activeRequests[token.requestId]?.let { requests ->
        requests.remove(token)
        if (requests.isEmpty()) {
          activeRequests.remove(token.requestId)
        }
      }
    }
  }

  fun cancel(requestId: String) {
    val requests = synchronized(lock) {
      cancelledRequestIds.add(requestId)
      activeRequests[requestId]?.toList().orEmpty()
    }
    requests.forEach(SegmentationRequestToken::cancel)
  }

  fun cancelAll() {
    val requests = synchronized(lock) {
      activeRequests.values.flatten().also { tokens ->
        tokens.forEach { cancelledRequestIds.add(it.requestId) }
      }
    }
    requests.forEach(SegmentationRequestToken::cancel)
  }
}
