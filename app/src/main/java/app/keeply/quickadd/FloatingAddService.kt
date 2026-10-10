package app.keeply.quickadd

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.keeply.KeeplyApp
import app.keeply.MainActivity
import app.keeply.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The floating "+" over other apps. Drag to move (it settles on the nearest side edge), tap to
 * open Keeply's Add menu. Android requires an ongoing notification while it runs; that
 * notification also turns it off.
 */
class FloatingAddService : Service() {
    private var view: View? = null
    private lateinit var wm: WindowManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { (application as KeeplyApp).container.settings.setFloatingButton(false) }
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
        startInForeground()
        if (view == null) addBubble()
        return START_STICKY
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Floating add button", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Shown while the floating + button is on"
                setShowBadge(false)
            })
        }
        val open = PendingIntent.getActivity(this, 1, addIntent(this), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 2, Intent(this, FloatingAddService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_keeply)
            .setContentTitle("Quick add is on")
            .setContentText("Tap the + on your screen to save something")
            .setContentIntent(open)
            .addAction(0, "Turn off", stop)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, n,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addBubble() {
        wm = getSystemService(WindowManager::class.java)
        val density = resources.displayMetrics.density
        val sizePx = (56 * density).toInt()
        val bubble = ImageView(this).apply {
            setImageResource(R.drawable.ic_quick_add_plus)
            scaleType = ImageView.ScaleType.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFFFF5A1F.toInt(), 0xFFE5400A.toInt())).apply { shape = GradientDrawable.OVAL }
            elevation = 8 * density
            contentDescription = "Add to Keeply"
        }
        val params = WindowManager.LayoutParams(
            sizePx, sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
            x = prefs.getInt("x", resources.displayMetrics.widthPixels - sizePx - (12 * density).toInt())
            y = prefs.getInt("y", (resources.displayMetrics.heightPixels * 0.6f).toInt())
        }
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        bubble.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startX = params.x; startY = params.y; moved = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (abs(dx) > 8 * density || abs(dy) > 8 * density) moved = true
                    if (moved) { params.x = startX + dx.toInt(); params.y = startY + dy.toInt(); wm.updateViewLayout(v, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        startActivity(addIntent(this))
                    } else {
                        // Settle on whichever side edge is closer, so it never sits over content in the middle.
                        val w = resources.displayMetrics.widthPixels
                        params.x = if (params.x + sizePx / 2 < w / 2) (8 * density).toInt() else w - sizePx - (8 * density).toInt()
                        params.y = params.y.coerceIn(0, resources.displayMetrics.heightPixels - sizePx)
                        wm.updateViewLayout(v, params)
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt("x", params.x).putInt("y", params.y).apply()
                    }
                    true
                }
                else -> false
            }
        }
        wm.addView(bubble, params)
        view = bubble
        bubble.visibility = if (keeplyInFront) View.GONE else View.VISIBLE
        instance = this
    }

    /** Pointless (and in the way) while Keeply itself is on screen. */
    private fun applyVisibility() {
        view?.visibility = if (keeplyInFront) View.GONE else View.VISIBLE
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "floating_add"
        private const val NOTIFICATION_ID = 0x4B41 // "KA"
        private const val PREFS = "keeply_floating"
        private const val ACTION_STOP = "app.keeply.action.FLOATING_STOP"

        fun addIntent(context: Context) = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_QUICK_ADD)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        fun canShow(context: Context) = Settings.canDrawOverlays(context)

        fun start(context: Context) {
            if (!canShow(context)) return
            runCatching { ContextCompat.startForegroundService(context, Intent(context, FloatingAddService::class.java)) }
        }

        fun stop(context: Context) = context.stopService(Intent(context, FloatingAddService::class.java))

        private var instance: FloatingAddService? = null
        private var keeplyInFront = false

        /** Called by MainActivity as it comes to the front and leaves it. */
        fun setKeeplyInFront(inFront: Boolean) {
            keeplyInFront = inFront
            instance?.applyVisibility()
        }
    }
}
