package app.keeply.security

import android.content.Context
import android.content.Intent
import java.util.UUID

/**
 * Keeply's own notifications carry a secret only this install knows. MainActivity is open to
 * other apps (for sharing), so without it any app could launch Keeply with a fake reminder or
 * item id; with it, those extras are ignored unless they really came from Keeply.
 */
object IntentToken {
    private const val EXTRA = "app.keeply.extra.TOKEN"

    private fun value(context: Context): String {
        val prefs = context.getSharedPreferences("keeply_intent_token", Context.MODE_PRIVATE)
        return prefs.getString("t", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("t", it).apply() }
    }

    fun sign(context: Context, intent: Intent): Intent = intent.putExtra(EXTRA, value(context))

    fun isFromKeeply(context: Context, intent: Intent): Boolean = intent.getStringExtra(EXTRA) == value(context)
}
