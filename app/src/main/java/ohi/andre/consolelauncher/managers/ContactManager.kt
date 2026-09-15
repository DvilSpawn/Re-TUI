package ohi.andre.consolelauncher.managers

import ohi.andre.consolelauncher.R
import android.Manifest
import android.content.Context
import android.net.Uri
import ohi.andre.consolelauncher.BuildConfig
import ohi.andre.consolelauncher.LauncherActivity
import java.util.Collections
import java.util.Locale
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.ContactsContract
import androidx.annotation.NonNull
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.util.ArrayList
import java.util.Iterator
import it.andreuzzi.comparestring2.StringableObject
import ohi.andre.consolelauncher.tuils.StoppableThread
import ohi.andre.consolelauncher.tuils.Tuils

class ContactManager(private val context: Context) {
    private var contacts: MutableList<Contact>? = null

    private val receiver: BroadcastReceiver

    fun destroy(context: Context) {
        LocalBroadcastManager.getInstance(context.getApplicationContext())
            .unregisterReceiver(receiver)
    }

    fun refreshContacts(context: Context) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                context as Activity,
                arrayOf<String>(Manifest.permission.READ_CONTACTS),
                LauncherActivity.COMMAND_SUGGESTION_REQUEST_PERMISSION
            )
            return
        }

        object : StoppableThread() {
            override fun run() {
                super.run()

                if (contacts == null) {
                    contacts = ArrayList<Contact>()
                } else {
                    contacts!!.clear()
                }
                val contacts = this@ContactManager.contacts

                val phones = context.getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf<String>(
                        ContactsContract.Contacts.DISPLAY_NAME,
                        ContactsContract.Data.CONTACT_ID,
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.Data.IS_SUPER_PRIMARY,
                    ), null, null,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
                )

                if (phones != null) {
                    var lastId = -1
                    var lastNumbers: MutableList<String?> = ArrayList<String?>()
                    var nrml: MutableList<String?> = ArrayList<String?>()
                    var defaultNumber = 0
                    var name: String? = null
                    var number: String?
                    var id: Int
                    var prim: Int

                    val contactIdIndex = phones.getColumnIndex(ContactsContract.Data.CONTACT_ID)
                    val numberIndex =
                        phones.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val isSuperPrimaryIndex =
                        phones.getColumnIndex(ContactsContract.Data.IS_SUPER_PRIMARY)
                    val displayNameIndex =
                        phones.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)

                    while (phones.moveToNext()) {
                        id = if (contactIdIndex != -1) phones.getInt(contactIdIndex) else -1
                        number = if (numberIndex != -1) phones.getString(numberIndex) else null

                        prim =
                            if (isSuperPrimaryIndex != -1) phones.getInt(isSuperPrimaryIndex) else 0
                        if (prim > 0) {
                            defaultNumber = lastNumbers.size
                        }

                        if (number == null || number.length == 0) continue

                        if (phones.isFirst()) {
                            lastId = id
                            name =
                                if (displayNameIndex != -1) phones.getString(displayNameIndex) else null
                        } else if (id != lastId || phones.isLast()) {
                            lastId = id

                            contacts!!.add(Contact(name!!, lastNumbers, defaultNumber))

                            lastNumbers = ArrayList<String?>()
                            nrml = ArrayList<String?>()
                            name = null
                            defaultNumber = 0

                            name =
                                if (displayNameIndex != -1) phones.getString(displayNameIndex) else null
                        }

                        val normalized: String =
                            number.replace(Tuils.SPACE.toRegex(), Tuils.EMPTYSTRING)
                        if (!nrml.contains(normalized)) {
                            nrml.add(normalized)
                            lastNumbers.add(number)
                        }

                        if (name != null && phones.isLast()) {
                            contacts!!.add(Contact(name, lastNumbers, defaultNumber))
                        }
                    }
                    phones.close()
                }

                val iterator = contacts!!.iterator()
                while (iterator.hasNext()) {
                    val c = iterator.next()
                    if (c.numbers.size == 0) iterator.remove()
                }

                Collections.sort<Contact?>(contacts)
            }
        }.start()
    }

    fun listNames(): MutableList<String?> {
        if (contacts == null || contacts!!.size == 0) refreshContacts(context)

        val names: MutableList<String?> = ArrayList<String?>()
        for (c in contacts!!) names.add(c.string)
        return names
    }

    fun getContacts(): MutableList<Contact> {
        if (contacts == null || contacts!!.size == 0) refreshContacts(context)

        return ArrayList<Contact>(contacts ?: emptyList())
    }

    fun listNamesAndNumbers(): MutableList<String?> {
        if (contacts == null || contacts!!.size == 0) refreshContacts(context)

        val c: MutableList<String?> = ArrayList<String?>()

        for (count in contacts!!.indices) {
            val cnt = contacts!!.get(count)

            val b = StringBuilder()
            b.append(cnt.string)

            for (n in cnt.numbers) {
                b.append(Tuils.NEWLINE)
                b.append("\t")
                b.append(n)
            }

            c.add(b.toString())
        }

        return c
    }

    init {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            refreshContacts(context)
        }

        val filter: IntentFilter = IntentFilter()
        filter.addAction(ACTION_REFRESH)

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.getAction() == ACTION_REFRESH) {
                    refreshContacts(context)
                }
            }
        }

        LocalBroadcastManager.getInstance(context.getApplicationContext())
            .registerReceiver(receiver, filter)
    }

    fun about(phone: String?): Array<String?>? {
        val firstCursor = context.getContentResolver().query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf<String>(ContactsContract.CommonDataKinds.Phone.CONTACT_ID),
            ContactsContract.CommonDataKinds.Phone.NUMBER + " = ?", arrayOf<String?>(phone),
            null
        ) ?: return null

        firstCursor.use { cursor ->
            if (cursor.getCount() == 0) return null
            val about = arrayOfNulls<String>(SIZE)

            cursor.moveToNext()

            val contactIdIndex =
                cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val id = if (contactIdIndex != -1) cursor.getString(contactIdIndex) else null
            about[CONTACT_ID] = id

            val detailsCursor = context.getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf<String>(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.TIMES_CONTACTED,
                    ContactsContract.CommonDataKinds.Phone.LAST_TIME_CONTACTED,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?", arrayOf<String?>(id),
                null
            ) ?: return null

            detailsCursor.use { details ->
                if (details.getCount() == 0) return null
                details.moveToNext()

                val displayNameIndex =
                    details.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val timesContactedIndex =
                    details.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TIMES_CONTACTED)
                val lastTimeContactedIndex =
                    details.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LAST_TIME_CONTACTED)
                val numberIndex = details.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                about[NAME] = if (displayNameIndex != -1) details.getString(displayNameIndex) else null
                about[NUMBERS] = Tuils.EMPTYSTRING

                var timesContacted = -1
                var lastContacted = Long.Companion.MAX_VALUE
                do {
                    val tempT = if (timesContactedIndex != -1) details.getInt(timesContactedIndex) else 0
                    val tempL =
                        if (lastTimeContactedIndex != -1) details.getLong(lastTimeContactedIndex) else 0

                    timesContacted = if (tempT > timesContacted) tempT else timesContacted
                    if (tempL > 0) lastContacted = if (tempL < lastContacted) tempL else lastContacted

                    val n = if (numberIndex != -1) details.getString(numberIndex) else null
                    about[NUMBERS] =
                        (if (about[NUMBERS]!!.length > 0) about[NUMBERS] + Tuils.NEWLINE else Tuils.EMPTYSTRING) + n
                } while (details.moveToNext())

                about[TIME_CONTACTED] = timesContacted.toString()
                if (lastContacted != Long.Companion.MAX_VALUE) {
                    val difference = System.currentTimeMillis() - lastContacted
                    var sc = difference / 1000
                    if (sc < 60) {
                        about[LAST_CONTACTED] = context.getString(R.string.manager_contactmanager_sec_8055d, lastContacted.toString())
                    } else {
                        var ms = (sc / 60).toInt()
                        sc = (ms % 60).toLong()
                        if (ms < 60) {
                            about[LAST_CONTACTED] = context.getString(R.string.manager_contactmanager_min_sec_cbb62, ms, sc)
                        } else {
                            var h = ms / 60
                            ms = h % 60
                            if (h < 24) {
                                about[LAST_CONTACTED] = context.getString(R.string.manager_contactmanager_h_min_sec_924a2, h, ms, sc)
                            } else {
                                val days = h / 24
                                h = days % 24
                                about[LAST_CONTACTED] =
                                    context.getString(R.string.manager_contactmanager_d_h_min_sec_1f593, days, h, ms, sc)
                            }
                        }
                    }
                }

                return about
            }
        }

        return null
    }

    fun findNumber(name: kotlin.String?): kotlin.String? {
        if (contacts == null) refreshContacts(context)

        for (count in contacts!!.indices) {
            val c = contacts!!.get(count)
            if (c.string.equals(name, ignoreCase = true)) {
                if (c.numbers.size > 0) return c.numbers.get(0)
            }
        }

        return null
    }

    fun delete(phone: kotlin.String?): Boolean {
        return context.getContentResolver().delete(fromPhone(phone)!!, null, null) > 0
    }

    fun fromPhone(phone: kotlin.String?): Uri? {
        var mCursor = context.getContentResolver().query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf<kotlin.String>(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            ),
            ContactsContract.CommonDataKinds.Phone.NUMBER + " = ?", arrayOf<kotlin.String?>(phone),
            null
        )

        if (mCursor == null || mCursor.getCount() == 0) return null
        mCursor.moveToNext()

        val displayNameIndex =
            mCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val name = if (displayNameIndex != -1) mCursor.getString(displayNameIndex) else null
        mCursor.close()

        mCursor = context.getContentResolver().query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf<kotlin.String>(
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME
            ),
            ContactsContract.Contacts.DISPLAY_NAME + " = ?", arrayOf<kotlin.String?>(name),
            null
        )

        if (mCursor == null || mCursor.getCount() == 0) return null
        mCursor.moveToNext()

        val lookupKeyIndex = mCursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)
        val idIndex = mCursor.getColumnIndex(ContactsContract.Contacts._ID)

        val mCurrentLookupKey =
            if (lookupKeyIndex != -1) mCursor.getString(lookupKeyIndex) else null
        val mCurrentId = if (idIndex != -1) mCursor.getLong(idIndex) else -1

        mCursor.close()

        return ContactsContract.Contacts.getLookupUri(mCurrentId, mCurrentLookupKey)
    }

    class Contact(
        @get:JvmName("getContactString")
        var string: kotlin.String,
        var numbers: MutableList<kotlin.String?>,
        defNumber: Int
    ) : Comparable<Contact>, StringableObject {
        @get:JvmName("getContactLowercaseString")
        var lowercaseString: kotlin.String

        var selectedNumber: Int = 0
            set(s) {
                var s = s
                if (s >= numbers.size) s = 0
                field = s
            }

        init {
            this.lowercaseString = string.lowercase(Locale.getDefault())

            this.selectedNumber = defNumber
        }

        override fun compareTo(o: Contact): Int {
            val tf = string.uppercase(Locale.getDefault()).get(0)
            val of = o.string.uppercase(Locale.getDefault()).get(0)

            return tf.code - of.code
        }

        override fun getString(): String = string

        override fun getLowercaseString(): String = lowercaseString
    }

    companion object {
        var ACTION_REFRESH: kotlin.String = BuildConfig.APPLICATION_ID + ".refresh_contacts"

        const val NAME: Int = 0
        const val NUMBERS: Int = 1
        const val TIME_CONTACTED: Int = 2
        const val LAST_CONTACTED: Int = 3
        const val CONTACT_ID: Int = 4
        val SIZE: Int = CONTACT_ID + 1
    }
}
