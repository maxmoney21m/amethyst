package com.vitorpamplona.amethyst

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.RelaySetupInfo
import com.vitorpamplona.amethyst.model.toByteArray
import com.vitorpamplona.amethyst.service.model.ContactListEvent
import com.vitorpamplona.amethyst.service.model.Event
import fr.acinq.secp256k1.Hex
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import nostr.postr.Persona
import nostr.postr.toHex
import nostr.postr.toNpub
import java.io.File
import java.util.Locale

// Release mode (!BuildConfig.DEBUG) always uses encrypted preferences
// To use plaintext SharedPreferences for debugging, set this to true
// It will only apply in Debug builds
private const val DEBUG_PLAINTEXT_PREFERENCES = false
private const val DEBUG_PREFERENCES_NAME = "debug_prefs"

data class AccountInfo(
    val npub: String,
    val hasPrivKey: Boolean,
    val current: Boolean,
    val displayName: String?,
    val profilePicture: String?
)

private object PrefKeys {
    const val CURRENT_ACCOUNT = "currently_logged_in_account"
    const val SAVED_ACCOUNTS = "all_saved_accounts"
    const val NOSTR_PRIVKEY = "nostr_privkey"
    const val NOSTR_PUBKEY = "nostr_pubkey"
    const val DISPLAY_NAME = "display_name"
    const val PROFILE_PICTURE_URL = "profile_picture"
    const val FOLLOWING_CHANNELS = "following_channels"
    const val HIDDEN_USERS = "hidden_users"
    const val RELAYS = "relays"
    const val DONT_TRANSLATE_FROM = "dontTranslateFrom"
    const val LANGUAGE_PREFS = "languagePreferences"
    const val TRANSLATE_TO = "translateTo"
    const val ZAP_AMOUNTS = "zapAmounts"
    const val LATEST_CONTACT_LIST = "latestContactList"
    const val HIDE_DELETE_REQUEST_DIALOG = "hide_delete_request_dialog"
    const val HIDE_BLOCK_ALERT_DIALOG = "hide_block_alert_dialog"
    val LAST_READ: (String) -> String = { route -> "last_read_route_$route" }
}

object LocalPreferences {
    private const val comma = ","

    private var currentAccount: String?
        get() = encryptedPreferences().getString(PrefKeys.CURRENT_ACCOUNT, null)
        set(npub) {
            val prefs = encryptedPreferences()
            prefs.edit().apply {
                putString(PrefKeys.CURRENT_ACCOUNT, npub)
            }.apply()
        }

    private val savedAccounts: List<String>
        get() = encryptedPreferences()
            .getString(PrefKeys.SAVED_ACCOUNTS, null)?.split(comma) ?: listOf()

    private val prefsDirPath: String
        get() = "${Amethyst.instance.filesDir.parent}/shared_prefs/"

    private fun addAccount(npub: String) {
        val accounts = savedAccounts.toMutableList()
        if (npub !in accounts) {
            accounts.add(npub)
        }
        val prefs = encryptedPreferences()
        prefs.edit().apply {
            putString(PrefKeys.SAVED_ACCOUNTS, accounts.joinToString(comma).ifBlank { null })
        }.apply()
    }

    private fun setCurrentAccount(account: Account) {
        val npub = account.userProfile().pubkeyNpub()
        currentAccount = npub
        addAccount(npub)
    }

    fun switchToAccount(npub: String) {
        currentAccount = npub
    }

    /**
     * Removes the account from the app level shared preferences
     */
    private fun removeAccount(npub: String) {
        val accounts = savedAccounts.toMutableList()
        if (accounts.remove(npub)) {
            val prefs = encryptedPreferences()
            prefs.edit().apply {
                putString(PrefKeys.SAVED_ACCOUNTS, accounts.joinToString(comma).ifBlank { null })
            }.apply()
        }
    }

    /**
     * Deletes the npub-specific shared preference file
     */
    private fun deleteUserPreferenceFile(npub: String) {
        val prefsDir = File(prefsDirPath)
        prefsDir.list()?.forEach {
            if (it.contains(npub)) {
                File(prefsDir, it).delete()
            }
        }
    }

    private fun encryptedPreferences(npub: String? = null): SharedPreferences {
        return if (BuildConfig.DEBUG && DEBUG_PLAINTEXT_PREFERENCES) {
            val preferenceFile = if (npub == null) DEBUG_PREFERENCES_NAME else "${DEBUG_PREFERENCES_NAME}_$npub"
            Amethyst.instance.getSharedPreferences(preferenceFile, Context.MODE_PRIVATE)
        } else {
            return EncryptedStorage.preferences(npub)
        }
    }

    /**
     * Clears the preferences for a given npub, deletes the preferences xml file,
     * and switches the user to the first account in the list if it exists
     *
     * We need to use `commit()` to write changes to disk and release the file
     * lock so that it can be deleted. If we use `apply()` there is a race
     * condition and the file will probably not be deleted
     */
    @SuppressLint("ApplySharedPref")
    fun updatePrefsForLogout(npub: String) {
        val userPrefs = encryptedPreferences(npub)
        userPrefs.edit().clear().commit()
        removeAccount(npub)
        deleteUserPreferenceFile(npub)

        if (savedAccounts.isEmpty()) {
            val appPrefs = encryptedPreferences()
            appPrefs.edit().clear().apply()
        } else if (currentAccount == npub) {
            currentAccount = savedAccounts.elementAt(0)
        }
    }

    fun updatePrefsForLogin(account: Account) {
        setCurrentAccount(account)
        saveToEncryptedStorage(account)
    }

    fun allSavedAccounts(): List<AccountInfo> {
        return savedAccounts.map { npub ->
            val prefs = encryptedPreferences(npub)
            val hasPrivKey = prefs.getString(PrefKeys.NOSTR_PRIVKEY, null) != null

            AccountInfo(
                npub = npub,
                hasPrivKey = hasPrivKey,
                current = npub == currentAccount,
                displayName = prefs.getString(PrefKeys.DISPLAY_NAME, null),
                profilePicture = prefs.getString(PrefKeys.PROFILE_PICTURE_URL, null)
            )
        }
    }

    fun saveToEncryptedStorage(account: Account) {
        val prefs = encryptedPreferences(account.userProfile().pubkeyNpub())
        prefs.edit().apply {
            account.loggedIn.privKey?.let { putString(PrefKeys.NOSTR_PRIVKEY, it.toHex()) }
            account.loggedIn.pubKey.let { putString(PrefKeys.NOSTR_PUBKEY, it.toHex()) }
            putStringSet(PrefKeys.FOLLOWING_CHANNELS, account.followingChannels)
            putStringSet(PrefKeys.HIDDEN_USERS, account.hiddenUsers)
            putString(PrefKeys.RELAYS, Json.encodeToString(account.localRelays))
            putStringSet(PrefKeys.DONT_TRANSLATE_FROM, account.dontTranslateFrom)
            putString(PrefKeys.LANGUAGE_PREFS, Json.encodeToString(account.languagePreferences))
            putString(PrefKeys.TRANSLATE_TO, account.translateTo)
            putString(PrefKeys.ZAP_AMOUNTS, Json.encodeToString(account.zapAmountChoices))
            account.backupContactList?.let {
                putString(PrefKeys.LATEST_CONTACT_LIST, Json.encodeToString(ContactListEvent.serializer(), it))
            }
            putBoolean(PrefKeys.HIDE_DELETE_REQUEST_DIALOG, account.hideDeleteRequestDialog)
            putBoolean(PrefKeys.HIDE_BLOCK_ALERT_DIALOG, account.hideBlockAlertDialog)
            putString(PrefKeys.DISPLAY_NAME, account.userProfile().toBestDisplayName())
            putString(PrefKeys.PROFILE_PICTURE_URL, account.userProfile().profilePicture())
        }.apply()
    }

    fun loadFromEncryptedStorage(): Account? {
        encryptedPreferences(currentAccount).apply {
            val pubKey = getString(PrefKeys.NOSTR_PUBKEY, null) ?: return null
            val privKey = getString(PrefKeys.NOSTR_PRIVKEY, null)
            val followingChannels = getStringSet(PrefKeys.FOLLOWING_CHANNELS, null) ?: setOf()
            val hiddenUsers = getStringSet(PrefKeys.HIDDEN_USERS, emptySet()) ?: setOf()
            val localRelays = Json.decodeFromString<Set<RelaySetupInfo>>(
                getString(PrefKeys.RELAYS, "[]") ?: "[]"
            )

            val dontTranslateFrom = getStringSet(PrefKeys.DONT_TRANSLATE_FROM, null) ?: setOf()
            val translateTo = getString(PrefKeys.TRANSLATE_TO, null) ?: Locale.getDefault().language

            val zapAmountChoices = Json.decodeFromString<List<Long>>(
                getString(PrefKeys.ZAP_AMOUNTS, "[]") ?: "[500, 1000, 5000]"
            )

            val latestContactList: ContactListEvent? = try {
                getString(PrefKeys.LATEST_CONTACT_LIST, null)?.let {
                    Event.fromJson(it)
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                null
            } as ContactListEvent?

            val languagePreferences = try {
                getString(PrefKeys.LANGUAGE_PREFS, null)?.let {
                    Json.decodeFromString<Map<String, String>>(it)
                } ?: mapOf()
            } catch (e: Throwable) {
                e.printStackTrace()
                mapOf()
            }

            val hideDeleteRequestDialog = getBoolean(PrefKeys.HIDE_DELETE_REQUEST_DIALOG, false)
            val hideBlockAlertDialog = getBoolean(PrefKeys.HIDE_BLOCK_ALERT_DIALOG, false)

            return Account(
                Persona(privKey = privKey?.toByteArray(), pubKey = pubKey.toByteArray()),
                followingChannels,
                hiddenUsers,
                localRelays,
                dontTranslateFrom,
                languagePreferences,
                translateTo,
                zapAmountChoices,
                hideDeleteRequestDialog,
                hideBlockAlertDialog,
                latestContactList
            )
        }
    }

    fun saveLastRead(route: String, timestampInSecs: Long) {
        encryptedPreferences(currentAccount).edit().apply {
            putLong(PrefKeys.LAST_READ(route), timestampInSecs)
        }.apply()
    }

    fun loadLastRead(route: String): Long {
        encryptedPreferences(currentAccount).run {
            return getLong(PrefKeys.LAST_READ(route), 0)
        }
    }

    fun migrateSingleUserPrefs() {
        if (currentAccount != null) return

        val pubkey = encryptedPreferences().getString(PrefKeys.NOSTR_PUBKEY, null) ?: return
        val npub = Hex.decode(pubkey).toNpub()

        val stringPrefs = listOf(
            PrefKeys.NOSTR_PRIVKEY,
            PrefKeys.NOSTR_PUBKEY,
            PrefKeys.RELAYS,
            PrefKeys.LANGUAGE_PREFS,
            PrefKeys.TRANSLATE_TO,
            PrefKeys.ZAP_AMOUNTS,
            PrefKeys.LATEST_CONTACT_LIST
        )

        val stringSetPrefs = listOf(
            PrefKeys.FOLLOWING_CHANNELS,
            PrefKeys.HIDDEN_USERS,
            PrefKeys.DONT_TRANSLATE_FROM
        )

        encryptedPreferences().apply {
            val appPrefs = this
            encryptedPreferences(npub).edit().apply {
                val userPrefs = this

                stringPrefs.forEach { userPrefs.putString(it, appPrefs.getString(it, null)) }
                stringSetPrefs.forEach { userPrefs.putStringSet(it, appPrefs.getStringSet(it, null)) }
                userPrefs.putBoolean(
                    PrefKeys.HIDE_DELETE_REQUEST_DIALOG,
                    appPrefs.getBoolean(PrefKeys.HIDE_DELETE_REQUEST_DIALOG, false)
                )
            }.apply()
        }

        encryptedPreferences().edit().clear().apply()
        addAccount(npub)
        currentAccount = npub
    }
}
