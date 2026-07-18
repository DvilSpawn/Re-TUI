package ohi.andre.consolelauncher.managers

import android.content.Context
import kotlin.math.max
import kotlin.random.Random

object RetuiCreditManager {
    const val PREFS: String = "retui_focus_friction"
    const val ESCAPE_COST: Int = 500
    const val FIRST_GRANT: Int = 1000
    const val GRANT_VERSION: Int = 1
    const val KEY_CHANCE_PERCENT: Int = 5
    const val BREACH_EXIT_COST: Int = 1
    const val BREACH_FAILURE_COST: Int = 25

    data class Wallet(val credits: Int, val keys: Int)
    data class Reward(val credits: Int, val keyAwarded: Boolean, val wallet: Wallet)

    fun isDystopiaEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DYSTOPIA_ENABLED, false)

    fun setDystopiaEnabled(context: Context, enabled: Boolean): Wallet {
        prefs(context).edit().putBoolean(KEY_DYSTOPIA_ENABLED, enabled).apply()
        return if (enabled) {
            ensureGrant(context)
            wallet(context)
        } else {
            Wallet(0, 0)
        }
    }

    fun wallet(context: Context): Wallet {
        if (!isDystopiaEnabled(context)) return Wallet(0, 0)
        ensureGrant(context)
        val prefs = prefs(context)
        return Wallet(
            max(0, prefs.getInt(KEY_CREDITS, 0)),
            max(0, prefs.getInt(KEY_KEYS, 0))
        )
    }

    fun addCredits(context: Context, amount: Int): Wallet {
        if (!isDystopiaEnabled(context)) return Wallet(0, 0)
        if (amount <= 0) return wallet(context)
        ensureGrant(context)
        val prefs = prefs(context)
        val next = max(0, prefs.getInt(KEY_CREDITS, 0) + amount)
        prefs.edit().putInt(KEY_CREDITS, next).apply()
        return wallet(context)
    }

    fun spendCredits(context: Context, amount: Int = ESCAPE_COST): Boolean {
        if (!isDystopiaEnabled(context)) return false
        ensureGrant(context)
        val prefs = prefs(context)
        val current = max(0, prefs.getInt(KEY_CREDITS, 0))
        if (current < amount) return false
        prefs.edit().putInt(KEY_CREDITS, current - amount).apply()
        return true
    }

    fun spendKey(context: Context): Boolean {
        if (!isDystopiaEnabled(context)) return false
        ensureGrant(context)
        val prefs = prefs(context)
        val current = max(0, prefs.getInt(KEY_KEYS, 0))
        if (current <= 0) return false
        prefs.edit().putInt(KEY_KEYS, current - 1).apply()
        return true
    }

    fun rewardBreach(context: Context, amount: Int): Reward {
        if (!isDystopiaEnabled(context)) return Reward(0, false, Wallet(0, 0))
        val keyAwarded = Random.nextInt(100) < KEY_CHANCE_PERCENT
        ensureGrant(context)
        val prefs = prefs(context)
        val wallet = wallet(context)
        val nextCredits = wallet.credits + amount
        val nextKeys = wallet.keys + if (keyAwarded) 1 else 0
        prefs.edit()
            .putInt(KEY_CREDITS, nextCredits)
            .putInt(KEY_KEYS, nextKeys)
            .apply()
        return Reward(amount, keyAwarded, Wallet(nextCredits, nextKeys))
    }

    fun recordBreachFailure(context: Context): Wallet {
        if (!isDystopiaEnabled(context)) return Wallet(0, 0)
        ensureGrant(context)
        val prefs = prefs(context)
        val next = max(0, prefs.getInt(KEY_CREDITS, 0) - BREACH_FAILURE_COST)
        prefs.edit().putInt(KEY_CREDITS, next).apply()
        return wallet(context)
    }

    fun status(context: Context): String {
        if (!isDystopiaEnabled(context)) {
            return "Retui Credits are disabled. Enable 'Sign up for Retui Credits' under Personalization."
        }
        val wallet = wallet(context)
        return "Retui Credits: ${wallet.credits}\nBreach Keys: ${wallet.keys}"
    }

    private fun ensureGrant(context: Context) {
        val prefs = prefs(context)
        if (prefs.getInt(KEY_GRANT_VERSION, 0) >= GRANT_VERSION) return
        val current = max(0, prefs.getInt(KEY_CREDITS, 0))
        prefs.edit()
            .putInt(KEY_CREDITS, current + FIRST_GRANT)
            .putInt(KEY_GRANT_VERSION, GRANT_VERSION)
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val KEY_CREDITS = "credits"
    private const val KEY_KEYS = "breach_keys"
    private const val KEY_GRANT_VERSION = "grant_version"
    private const val KEY_DYSTOPIA_ENABLED = "dystopia_enabled"
}
