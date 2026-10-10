package app.keeply.sync

import android.content.Context

/**
 * Item ids whose latest local change the server hasn't confirmed yet, kept per account.
 * Survives restarts, so a write that was rejected or never sent is retried on the next start.
 */
class SyncOutbox(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("keeply_sync_outbox", Context.MODE_PRIVATE)

    private fun upsertKey(uid: String) = "$uid.upserts"
    private fun deleteKey(uid: String) = "$uid.deletes"
    private fun initKey(uid: String) = "$uid.initialised"

    @Synchronized fun pendingUpserts(uid: String): Set<String> = prefs.getStringSet(upsertKey(uid), emptySet())!!.toSet()
    @Synchronized fun pendingDeletes(uid: String): Set<String> = prefs.getStringSet(deleteKey(uid), emptySet())!!.toSet()

    @Synchronized fun markUpsert(uid: String, id: String) = edit(uid) { up, del -> up += id; del -= id }
    @Synchronized fun markDelete(uid: String, id: String) = edit(uid) { up, del -> del += id; up -= id }
    @Synchronized fun clear(uid: String, id: String) = edit(uid) { up, del -> up -= id; del -= id }

    fun isInitialised(uid: String) = prefs.getBoolean(initKey(uid), false)
    fun setInitialised(uid: String) = prefs.edit().putBoolean(initKey(uid), true).apply()

    /** Signing out or deleting the account: nothing of this user's should be retried on this device. */
    @Synchronized fun forget(uid: String) =
        prefs.edit().remove(upsertKey(uid)).remove(deleteKey(uid)).remove(initKey(uid)).apply()

    private fun edit(uid: String, change: (MutableSet<String>, MutableSet<String>) -> Unit) {
        val up = pendingUpserts(uid).toMutableSet()
        val del = pendingDeletes(uid).toMutableSet()
        change(up, del)
        prefs.edit().putStringSet(upsertKey(uid), up).putStringSet(deleteKey(uid), del).apply()
    }
}
