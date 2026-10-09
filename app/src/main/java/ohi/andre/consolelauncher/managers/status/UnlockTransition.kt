package ohi.andre.consolelauncher.managers.status

/** Tracks screen cycles independently of status rendering and wall-clock time. */
internal class UnlockTransition(private var pending: Boolean = false) {
    fun onScreenEvent(screenOff: Boolean, locked: Boolean): Boolean {
        if (screenOff || locked) {
            pending = true
            return false
        }
        val unlocked = pending
        pending = false
        return unlocked
    }
}
