// Adapted from the original renderer/segmentation core without Expo.
package com.ngocdevv.nativecook.blocks.animations.particledispersion

import android.content.Context
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.OptionalModuleApi
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusCodes
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Installs ML Kit's optional subject-segmentation module and waits for the
 * install-status callback. The Task returned by installModules only confirms
 * that Google Play services accepted the request; it does not mean the model is ready.
 */
internal class SubjectModelInstaller(context: Context) {
  private val moduleInstallClient = ModuleInstall.getClient(context.applicationContext)
  private val installMutex = Mutex()

  suspend fun ensureAvailable(api: OptionalModuleApi) {
    installMutex.withLock {
      try {
        withTimeout(MODEL_INSTALL_TIMEOUT_MILLIS) {
          if (!areModulesAvailable(api)) {
            awaitUrgentInstall(api)
          }
        }
      } catch (error: TimeoutCancellationException) {
        throw segmentationError(
          MODEL_DOWNLOAD_ERROR_CODE,
          "Preparing the subject isolation model timed out. Check the internet connection and Google Play services, then try again.",
          error
        )
      }
    }
  }

  private suspend fun areModulesAvailable(api: OptionalModuleApi): Boolean =
    awaitTask(moduleInstallClient.areModulesAvailable(api)).areModulesAvailable()

  private suspend fun awaitUrgentInstall(api: OptionalModuleApi) {
    suspendCancellableCoroutine { continuation ->
      val settled = AtomicBoolean(false)
      lateinit var listener: InstallStatusListener

      fun unregisterListener() {
        try {
          moduleInstallClient.unregisterListener(listener)
        } catch (_: Throwable) {
          // Listener cleanup is best effort; it must never replace the install result.
        }
      }

      fun resolve() {
        if (!settled.compareAndSet(false, true)) return
        unregisterListener()
        if (continuation.isActive) continuation.resume(Unit)
      }

      fun reject(error: Throwable) {
        if (!settled.compareAndSet(false, true)) return
        unregisterListener()
        if (continuation.isActive) continuation.resumeWithException(error)
      }

      listener = InstallStatusListener { update ->
        when (update.installState) {
          ModuleInstallStatusUpdate.InstallState.STATE_COMPLETED -> resolve()
          ModuleInstallStatusUpdate.InstallState.STATE_FAILED -> {
            reject(modelInstallError(update.errorCode))
          }
          ModuleInstallStatusUpdate.InstallState.STATE_CANCELED -> {
            reject(
              segmentationError(
                MODEL_DOWNLOAD_ERROR_CODE,
                "Google Play services canceled the subject isolation model download. Check the internet connection, then try again."
              )
            )
          }
        }
      }

      continuation.invokeOnCancellation {
        settled.compareAndSet(false, true)
        // The install itself intentionally continues so a later request can use it.
        unregisterListener()
      }

      val installTask = try {
        val request = ModuleInstallRequest.newBuilder()
          .addApi(api)
          .setListener(listener, DIRECT_EXECUTOR)
          .build()
        moduleInstallClient.installModules(request)
      } catch (error: Throwable) {
        reject(modelInstallError(error = error))
        return@suspendCancellableCoroutine
      }

      installTask
        .addOnSuccessListener(DIRECT_EXECUTOR) { response ->
          if (response.areModulesAlreadyInstalled() || response.sessionId == 0) {
            resolve()
          }
        }
        .addOnFailureListener(DIRECT_EXECUTOR) { error ->
          reject(modelInstallError((error as? ApiException)?.statusCode, error))
        }
        .addOnCanceledListener(DIRECT_EXECUTOR) {
          reject(
            segmentationError(
              MODEL_DOWNLOAD_ERROR_CODE,
              "Google Play services canceled the subject isolation model request. Check the internet connection, then try again."
            )
          )
        }
        .addOnCompleteListener(DIRECT_EXECUTOR) {
          // Cancellation can race Play services registering the listener. Retry
          // cleanup once the request Task settles so that race cannot leak it.
          if (settled.get() || !continuation.isActive) unregisterListener()
        }
    }
  }

  private suspend fun <T> awaitTask(task: Task<T>): T =
    suspendCancellableCoroutine { continuation ->
      task
        .addOnSuccessListener(DIRECT_EXECUTOR) { value ->
          if (continuation.isActive) continuation.resume(value)
        }
        .addOnFailureListener(DIRECT_EXECUTOR) { error ->
          if (continuation.isActive) {
            continuation.resumeWithException(
              modelInstallError((error as? ApiException)?.statusCode, error)
            )
          }
        }
        .addOnCanceledListener(DIRECT_EXECUTOR) {
          if (continuation.isActive) {
            continuation.resumeWithException(
              segmentationError(
                MODEL_DOWNLOAD_ERROR_CODE,
                "Google Play services canceled the subject isolation model availability check. Try again."
              )
            )
          }
        }
    }

  private fun modelInstallError(
    statusCode: Int? = null,
    error: Throwable? = null
  ): NativeProcessingException {
    val status = statusCode?.let(ModuleInstallStatusCodes::getStatusCodeString)
    val statusSuffix = status?.let { " ($it)" }.orEmpty()
    return when (statusCode) {
      ModuleInstallStatusCodes.UNKNOWN_MODULE,
      ModuleInstallStatusCodes.NOT_ALLOWED_MODULE,
      ModuleInstallStatusCodes.MODULE_NOT_FOUND -> segmentationError(
        MODEL_UNSUPPORTED_ERROR_CODE,
        "This device does not support the on-device subject isolation model$statusSuffix.",
        error
      )
      ModuleInstallStatusCodes.INSUFFICIENT_STORAGE -> segmentationError(
        MODEL_STORAGE_ERROR_CODE,
        "There is not enough free storage to install the subject isolation model$statusSuffix.",
        error
      )
      ModuleInstallStatusCodes.METERED_NETWORK_NOT_ALLOWED -> segmentationError(
        MODEL_DOWNLOAD_ERROR_CODE,
        "Google Play services is waiting for Wi-Fi to download the subject isolation model$statusSuffix.",
        error
      )
      else -> segmentationError(
        MODEL_DOWNLOAD_ERROR_CODE,
        "Could not download the on-device subject isolation model$statusSuffix. Check the internet connection and Google Play services, then try again.",
        error
      )
    }
  }

  companion object {
    private const val MODEL_INSTALL_TIMEOUT_MILLIS = 180_000L
    private const val MODEL_DOWNLOAD_ERROR_CODE = "ERR_SUBJECT_SEGMENTATION_MODEL_DOWNLOAD"
    private const val MODEL_UNSUPPORTED_ERROR_CODE = "ERR_SUBJECT_SEGMENTATION_MODEL_UNSUPPORTED"
    private const val MODEL_STORAGE_ERROR_CODE = "ERR_SUBJECT_SEGMENTATION_STORAGE"
    private val DIRECT_EXECUTOR = Executor { runnable -> runnable.run() }
  }
}
