package app.gyrolet.mpvrx.ui.player

import android.util.Log
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Serializes destruction of libmpv's process-global native core away from Android's UI thread. */
internal object MpvTeardownCoordinator {
  private const val TAG = "MpvTeardown"

  private val lock = Any()
  private val executor =
    Executors.newSingleThreadExecutor { task ->
      Thread(task, "mpvrx-mpv-teardown").apply { isDaemon = true }
    }

  @Volatile
  private var pendingTeardown: Future<*>? = null

  fun destroyAsync(reason: String, operation: () -> Unit) {
    synchronized(lock) {
      pendingTeardown =
        executor.submit {
          Log.d(TAG, "Starting native teardown: $reason")
          runCatching(operation)
            .onFailure { error -> Log.e(TAG, "Native teardown failed: $reason", error) }
          Log.d(TAG, "Finished native teardown: $reason")
        }
    }
  }

  /** Prevents a new player from initializing while the previous global core is still closing. */
  fun awaitIdle() {
    val teardown = pendingTeardown ?: return
    var interrupted = false

    while (true) {
      try {
        teardown.get()
        break
      } catch (_: InterruptedException) {
        interrupted = true
      } catch (error: ExecutionException) {
        // destroyAsync contains failures, but keep this defensive guard around the executor itself.
        Log.e(TAG, "Unable to await native teardown", error.cause ?: error)
        break
      }
    }

    if (interrupted) Thread.currentThread().interrupt()
  }
}
