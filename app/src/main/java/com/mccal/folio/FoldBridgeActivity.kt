package com.mccal.folio

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.lang.ref.WeakReference

/**
 * Keeps the phone awake when folding from Home.
 *
 * One UI's folding policy sends the phone to sleep when the top task is Home/Recents, even with
 * "Continue apps on cover screen: Always". While the hinge starts closing, Folio puts this invisible,
 * ordinary activity on top; the fold then counts as an app continuing to the cover screen. As soon
 * as the cover display takes over, it finishes and Home shows on the cover, still awake. It also
 * finishes if the fold is abandoned.
 */
class FoldBridgeActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val giveUp = Runnable { finishQuietly("timeout") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = WeakReference(this)
        handler.postDelayed(giveUp, TIMEOUT_MS)
        Log.d(TAG, "bridge up width=${resources.configuration.screenWidthDp}")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.screenWidthDp < 600) {
            // On the cover now: hand back to Home after the switch settles.
            handler.removeCallbacks(giveUp)
            handler.postDelayed({ finishQuietly("cover") }, HANDOFF_MS)
        }
    }

    // Any touch means the person is using the phone half-open: get out of the way.
    override fun onUserInteraction() = finishQuietly("touch")

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (instance.get() === this) instance.clear()
        super.onDestroy()
    }

    private fun finishQuietly(reason: String) {
        if (isFinishing) return
        Log.d(TAG, "bridge done: $reason")
        finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }

    companion object {
        private const val TAG = "FolioFold"
        private const val TIMEOUT_MS = 2_500L
        private const val HANDOFF_MS = 350L
        private var instance = WeakReference<FoldBridgeActivity>(null)

        fun start(context: Context) {
            if (instance.get()?.isFinishing == false) return
            val options = ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle()
            runCatching {
                context.startActivity(Intent(context, FoldBridgeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION), options)
            }.onFailure { Log.w(TAG, "bridge failed", it) }
        }

        fun cancel() { instance.get()?.finishQuietly("reopened") }
    }
}
