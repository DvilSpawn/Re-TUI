package ohi.andre.consolelauncher.tuils

import android.app.PendingIntent
import android.app.PendingIntent.CanceledException
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.location.Address
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import ohi.andre.consolelauncher.BuildConfig
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.tuils.Tuils.find
import org.w3c.dom.Node
import org.xml.sax.SAXParseException
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintStream
import java.io.PrintWriter
import java.io.Reader
import java.io.StringWriter
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Arrays
import java.util.Date
import java.util.Enumeration
import java.util.Locale
import java.util.Scanner
import java.util.regex.Matcher
import java.util.regex.Pattern
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerException
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.ActivityManager.MemoryInfo
import android.app.ActivityOptions
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.location.Geocoder
import android.os.Environment
import android.os.Parcelable
import android.os.StatFs
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.telephony.TelephonyManager
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.util.DisplayMetrics
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.io.RandomAccessFile
import java.lang.reflect.Field
import java.net.HttpURLConnection
import java.net.URL
import java.nio.channels.Channels
import java.util.ArrayList
import java.util.NoSuchElementException
import javax.xml.transform.Transformer
import dalvik.system.DexFile
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.managers.TerminalManager
import ohi.andre.consolelauncher.managers.music.MusicManager2
import ohi.andre.consolelauncher.managers.music.Song
import ohi.andre.consolelauncher.managers.notifications.NotificationService
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.interfaces.OnBatteryUpdate
import ohi.andre.consolelauncher.tuils.stuff.FakeLauncherActivity

object Tuils {
    private const val TAG = "TUI"

    const val SPACE: String = " "
    const val DOUBLE_SPACE: String = "  "
    const val NEWLINE: String = "\n"
    const val TRIBLE_SPACE: String = "   "
    const val DOT: String = "."
    const val EMPTYSTRING: String = ""
    const val MINUS: String = "-"

    var patternNewline: Pattern = Pattern.compile("%n", Pattern.CASE_INSENSITIVE or Pattern.LITERAL)

    private var globalTypeface: Typeface? = null
    var fontPath: String? = null

    var calculusPattern: Pattern = Pattern.compile("([\\+\\-\\*\\/\\^])(\\d+\\.?\\d*)")
    fun textCalculus(input: Double, text: String): Double {
        var input = input
        val m = calculusPattern.matcher(text)
        while (m.find()) {
            val operator = m.group(1).get(0)
            val value = m.group(2).toDouble()

            when (operator) {
                '+' -> input += value
                '-' -> input -= value
                '*' -> input *= value
                '/' -> input = input / value
                '^' -> input = input.pow(value)
            }

            log("now im", input)
        }

        return input
    }

    fun getTypeface(context: Context): Typeface? {
        if (globalTypeface == null) {
            var prefsLoaded = false
            var systemFont = true
            var configuredFont: String? = null

            try {
                XMLPrefsManager.loadCommons(context)
                LauncherSettings.refreshFromLoadedPrefs()
                prefsLoaded = true
                systemFont = AppearanceSettings.useSystemFont()
                configuredFont = AppearanceSettings.fontFile()
            } catch (e: Exception) {
                Log.e("TUI-FONT", "Unable to load font prefs, trying filesystem fallback", e)
            }

            val tui = folder
            var font = resolveConfiguredFontFile(tui, configuredFont)
            if (font == null) {
                font = resolveLegacyFontFile(tui)
                if (prefsLoaded && !systemFont && font != null && (configuredFont == null || configuredFont.trim { it <= ' ' }.length == 0)) {
                    try {
                        LauncherSettings.setUi(Ui.font_file, font.getName())
                    } catch (ignored: Exception) {
                    }
                }
            }

            if (prefsLoaded && systemFont) {
                globalTypeface = Typeface.DEFAULT
                fontPath = null
                Log.e("TUI-FONT", "Using system font")
            } else if (font != null && font.exists() && font.length() > 0) {
                try {
                    fontPath = font.getAbsolutePath()
                    Log.e("TUI-FONT", "Attempting to create Typeface from: " + fontPath)
                    globalTypeface = Typeface.createFromFile(font)
                    Log.e("TUI-FONT", "Loaded custom font: " + fontPath)
                } catch (e: Exception) {
                    Log.e("TUI-FONT", "Failed to load font from " + font.getAbsolutePath(), e)
                    log(e)
                    toFile(e)
                    globalTypeface = null
                }
            }

            if (globalTypeface == null) {
                try {
                    globalTypeface =
                        Typeface.createFromAsset(context.getAssets(), "lucida_console.ttf")
                    fontPath = "asset://lucida_console.ttf"
                    Log.e("TUI-FONT", "Falling back to bundled font")
                } catch (e: Exception) {
                    globalTypeface = Typeface.DEFAULT
                    fontPath = null
                    Log.e("TUI-FONT", "Falling back to system default font")
                }
            }
        }
        return globalTypeface
    }

    private fun resolveConfiguredFontFile(tui: File?, configuredFont: String?): File? {
        var configuredFont = configuredFont
        if (tui == null || configuredFont == null) {
            return null
        }

        configuredFont = configuredFont.trim { it <= ' ' }
        if (configuredFont.length == 0) {
            return null
        }

        val direct = File(tui, configuredFont)
        if (direct.exists() && direct.isFile()) {
            return direct
        }

        val fontsDir = File(tui, "fonts")
        val inFontsDir = File(fontsDir, configuredFont)
        if (inFontsDir.exists() && inFontsDir.isFile()) {
            return inFontsDir
        }

        return null
    }

    private fun resolveLegacyFontFile(tui: File?): File? {
        if (tui == null) {
            return null
        }

        val files = tui.listFiles()
        if (files == null) {
            return null
        }

        for (f in files) {
            if (f.isDirectory()) continue
            val name = f.getName().lowercase(Locale.getDefault())
            if (name.endsWith(".ttf") || name.endsWith(".otf")) {
                return f
            }
        }

        return null
    }

    fun cancelFont() {
        globalTypeface = null
        fontPath = null
    }

    fun locationName(context: Context, lat: Double, lng: Double): String? {
        val geocoder: Geocoder = Geocoder(context, Locale.getDefault())
        var addresses: MutableList<Address?>? = null
        try {
            addresses = geocoder.getFromLocation(lat, lng, 1)
            return addresses!!.get(0)!!.getAddressLine(2)
        } catch (e: Exception) {
            return null
        }
    }

    fun notificationServiceIsRunning(context: Context): Boolean {
        val collectorComponent: ComponentName =
            ComponentName(context, NotificationService::class.java)
        val manager: ActivityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        var collectorRunning = false
        val runningServices: MutableList<ActivityManager.RunningServiceInfo>? =
            manager.getRunningServices(Int.Companion.MAX_VALUE)
        if (runningServices == null) {
            return false
        }

        for (service in runningServices) {
            if (service.service == collectorComponent) {
                if (service.pid == Process.myPid()) {
                    collectorRunning = true
                }
            }
        }

        return collectorRunning
    }

    fun arrayContains(array: IntArray?, value: Int): Boolean {
        if (array == null) return false

        for (i in array) {
            if (i == value) {
                return true
            }
        }
        return false
    }

    @Throws(IOException::class)
    fun readerToString(initialReader: Reader): String {
        val arr = CharArray(8 * 1024)
        val buffer = StringBuilder()
        var numCharsRead: Int
        while ((initialReader.read(arr, 0, arr.size).also { numCharsRead = it }) != -1) {
            buffer.append(arr, 0, numCharsRead)
        }
        initialReader.close()
        return buffer.toString()
    }

    private var batteryUpdate: OnBatteryUpdate? = null
    private var batteryReceiver: BroadcastReceiver? = null

    fun registerBatteryReceiver(context: Context, listener: OnBatteryUpdate?) {
        try {
            batteryReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent) {
                    val update = batteryUpdate ?: return

                    when (intent.getAction()) {
                        Intent.ACTION_BATTERY_CHANGED -> {
                            val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
                            update.update(level.toFloat())
                        }

                        Intent.ACTION_POWER_CONNECTED -> update.onCharging()
                        Intent.ACTION_POWER_DISCONNECTED -> update.onNotCharging()
                    }
                }
            }

            val iFilter: IntentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            iFilter.addAction(Intent.ACTION_POWER_CONNECTED)
            iFilter.addAction(Intent.ACTION_POWER_DISCONNECTED)

            ContextCompat.registerReceiver(
                context,
                batteryReceiver,
                iFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )

            batteryUpdate = listener
        } catch (e: Exception) {
            toFile(e)
        }
    }

    fun unregisterBatteryReceiver(context: Context) {
        if (batteryReceiver != null) {
            try {
                context.unregisterReceiver(batteryReceiver)
            } catch (e: Exception) {
            }
        }
    }

    fun containsExtension(array: Array<out String?>, value: String?): Boolean {
        var value = value ?: return false
        try {
            value = value.lowercase(Locale.getDefault()).trim { it <= ' ' }
            for (s in array) {
                if (s != null && value.endsWith(s)) {
                    return true
                }
            }
            return false
        } catch (e: Exception) {
            return false
        }
    }

    fun getSongsInFolder(folder: File): MutableList<Song?> {
        val songs: MutableList<Song?> = ArrayList<Song?>()

        val files = folder.listFiles()
        if (files == null || files.size == 0) {
            return songs
        }

        for (file in files) {
            if (file.isDirectory()) {
                val s: MutableList<Song?> = getSongsInFolder(file)
                if (s != null) {
                    songs.addAll(s)
                }
            } else if (containsExtension(MusicManager2.MUSIC_EXTENSIONS, file.getName())) {
                songs.add(Song(file))
            }
        }

        return songs
    }

    fun convertStreamToString(`is`: InputStream?): String? {
        if (`is` == null) return EMPTYSTRING

        val s = Scanner(`is`).useDelimiter("\\A")
        return if (s.hasNext()) s.next() else EMPTYSTRING
    }

    @Throws(Exception::class)
    fun copy(from: File?, to: File?) {
        download(FileInputStream(from), to)
    }

    @Throws(Exception::class)
    fun download(`in`: InputStream, file: File?): Long {
        val out: OutputStream = FileOutputStream(file, false)

        val data: ByteArray? = ByteArray(1024)

        var bytes: Long = 0

        var count: Int
        while ((`in`.read(data).also { count = it }) != -1) {
            out.write(data, 0, count)
            bytes += count.toLong()
        }

        out.flush()
        out.close()
        `in`.close()

        return bytes
    }

    @Throws(Exception::class)
    fun write(file: File?, separator: String, vararg ss: String?) {
        val headerStream = FileOutputStream(file, false)

        for (c in 0..<ss.size - 1) {
            headerStream.write(ss[c]!!.toByteArray())
            headerStream.write(separator.toByteArray())
        }
        headerStream.write(ss[ss.size - 1]!!.toByteArray())

        headerStream.flush()
        headerStream.close()
    }

    fun dpToPx(context: Context, valueInDp: Float): Float {
        return UIUtils.dpToPx(context, valueInDp)
    }

    fun hasNotificationAccess(context: Context): Boolean {
        val pkgName: String? = BuildConfig.APPLICATION_ID
        val flat = Settings.Secure.getString(
            context.getContentResolver(),
            "enabled_notification_listeners"
        )
        if (!TextUtils.isEmpty(flat)) {
            val names: Array<String?> =
                flat!!.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (i in names.indices) {
                val cn: ComponentName? = ComponentName.unflattenFromString(names[i] ?: continue)
                if (cn != null) {
                    if (TextUtils.equals(pkgName, cn.getPackageName())) {
                        return true
                    }
                }
            }
        }
        return false
    }

    fun resetPreferredLauncherAndOpenChooser(context: Context) {
        val packageManager: PackageManager = context.getPackageManager()
        val componentName: ComponentName = ComponentName(context, FakeLauncherActivity::class.java)
        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )

        val selector: Intent = Intent(Intent.ACTION_MAIN)
        selector.addCategory(Intent.CATEGORY_HOME)
        selector.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(selector)

        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
            PackageManager.DONT_KILL_APP
        )
    }

    fun openSettingsPage(c: Context, packageName: String?) {
        val intent: Intent = Intent()
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", packageName, null)
        intent.setData(uri)
        c.startActivity(intent)
    }

    fun requestAdmin(component: ComponentName?, explanation: String?): Intent {
        val intent: Intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, explanation)
        return intent
    }

    fun webPage(url: String?): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse(url))
    }

    fun getAvailableInternalMemorySize(unit: Int): Double {
        return getAvailableSpace(Environment.getDataDirectory(), unit)
    }

    fun getTotalInternalMemorySize(unit: Int): Double {
        return getTotaleSpace(Environment.getDataDirectory(), unit)
    }

    fun getAvailableExternalMemorySize(unit: Int): Double {
        try {
            return Tuils.getAvailableSpace(
                XMLPrefsManager.get(
                    File::class.java,
                    Behavior.external_storage_path
                ), unit
            )
        } catch (e: Exception) {
            return -1.0
        }
    }

    fun getTotalExternalMemorySize(unit: Int): Double {
        try {
            return Tuils.getTotaleSpace(
                XMLPrefsManager.get(
                    File::class.java,
                    Behavior.external_storage_path
                ), unit
            )
        } catch (e: Exception) {
            return -1.0
        }
    }

    fun getAvailableSpace(dir: File?, unit: Int): Double {
        if (dir == null) return -1.0

        val statFs: StatFs = StatFs(dir.getAbsolutePath())
        val blocks = statFs.getAvailableBlocks().toLong()
        return formatSize(blocks * statFs.getBlockSize(), unit)
    }

    fun getTotaleSpace(dir: File?, unit: Int): Double {
        if (dir == null) return -1.0

        val statFs: StatFs = StatFs(dir.getAbsolutePath())
        val blocks = statFs.getBlockCount().toLong()
        return formatSize(blocks * statFs.getBlockSize(), unit)
    }

    fun percentage(part: Double, total: Double): Double {
        return round(part * 100 / total, 2)
    }

    fun formatSize(bytes: Long, unit: Int): Double {
        val convert = 1048576.0
        val smallConvert = 1024.0

        val result: Double

        when (unit) {
            TERA -> result = (bytes / convert) / convert
            GIGA -> result = (bytes / convert) / smallConvert
            MEGA -> result = bytes / convert
            KILO -> result = bytes / smallConvert
            BYTE -> result = bytes.toDouble()
            else -> return -1.0
        }

        return round(result, 2)
    }

    fun isMyLauncherDefault(packageManager: PackageManager): Boolean {
        val filter: IntentFilter = IntentFilter(Intent.ACTION_MAIN)
        filter.addCategory(Intent.CATEGORY_HOME)

        val filters: MutableList<IntentFilter?> = ArrayList<IntentFilter?>()
        filters.add(filter)

        val myPackageName: String = BuildConfig.APPLICATION_ID
        val activities: MutableList<ComponentName> = ArrayList<ComponentName>()

        // You can use name of your package here as third argument
        packageManager.getPreferredActivities(filters, activities, null)

        for (activity in activities) {
            if (myPackageName == activity.getPackageName()) {
                return true
            }
        }
        return false
    }


    fun span(text: CharSequence?, color: Int): SpannableString? {
        return UIUtils.span(text, color)
    }

    fun span(context: Context, size: Int, text: CharSequence?): SpannableString {
        return UIUtils.span(context, size, text)
    }

    fun span(context: Context, text: CharSequence?, color: Int, size: Int): SpannableString {
        return UIUtils.span(context, text, color, size)
    }

    fun span(bgColor: Int, foreColor: Int, text: CharSequence?): SpannableString {
        return UIUtils.span(bgColor, foreColor, text)
    }

    fun span(
        context: Context?,
        bgColor: Int,
        foreColor: Int,
        text: CharSequence?,
        size: Int
    ): SpannableString {
        return UIUtils.span(context, bgColor, foreColor, text, size)
    }

    fun span(bgColor: Int, text: SpannableString, section: String, fromIndex: Int): Int {
        return UIUtils.span(bgColor, text, section, fromIndex)
    }

    fun convertSpToPixels(sp: Float, context: Context): Int {
        return UIUtils.convertSpToPixels(sp, context)
    }

    fun inputStreamToString(`is`: InputStream?): String? {
        val s = Scanner(`is`).useDelimiter("\\A")
        return if (s.hasNext()) s.next() else EMPTYSTRING
    }

    fun deleteContentOnly(dir: File) {
        val files = dir.listFiles()
        if (files == null) return

        for (f in files) {
            if (f.isDirectory()) delete(f)
            f.delete()
        }
    }

    fun delete(dir: File) {
        val files = dir.listFiles()
        if (files == null) {
            dir.delete()
            return
        }

        for (f in files) {
            if (f.isDirectory()) delete(f)
            f.delete()
        }
        dir.delete()
    }

    fun insertOld(oldFile: File?): Boolean {
        if (oldFile == null || !oldFile.exists() || oldFile.isDirectory()) return false

        val oldFolder = File(folder, "old")
        if (!oldFolder.exists()) oldFolder.mkdirs()

        val dest = File(oldFolder, oldFile.getName())
        if (dest.exists()) dest.delete()

        val success = oldFile.renameTo(dest)
        if (!success) {
            // Fallback for Android 11+ where renameTo often fails due to Scoped Storage
            try {
                copy(oldFile, dest)
                return oldFile.delete()
            } catch (e: Exception) {
                Tuils.toFile("insertOld fallback failed: " + e.message)
                return false
            }
        }
        return success
    }

    fun getOld(name: String): File? {
        val old = File(folder, "old")
        val file = File(old, name)

        if (file.exists()) return file
        return null
    }

    fun deepView(v: View) {
        log(v.toString())

        if (v !is ViewGroup) return
        val g: ViewGroup = v as ViewGroup

        log(g.getChildCount())
        for (c in 0..<g.getChildCount()) deepView(g.getChildAt(c))

        log("end of parents of: " + v.toString())
    }

    private val deepClickListener = View.OnClickListener { v: View? -> log(v.toString()) }

    fun deepClickView(v: View) {
        v.setOnClickListener(deepClickListener)

        if (v !is ViewGroup) return
        val g: ViewGroup = v as ViewGroup

        for (c in 0..<g.getChildCount()) deepClickView(g.getChildAt(c))
    }

    @Throws(NoSuchElementException::class)
    fun scaleImage(view: ImageView, newX: Int, newY: Int) {
        // Get bitmap from the the ImageView.
        var bitmap: Bitmap? = null

        try {
            val drawing = view.getDrawable()
            bitmap = (drawing as BitmapDrawable).getBitmap()
        } catch (e: NullPointerException) {
            throw NoSuchElementException("No drawable on given view")
        }

        // Get current dimensions AND the desired bounding box
        var width = 0

        try {
            width = bitmap!!.getWidth()
        } catch (e: NullPointerException) {
            throw NoSuchElementException("Can't find bitmap on given view/drawable")
        }

        var height = bitmap.getHeight()
        val xBounding = dpToPx(view.getContext(), newX)
        val yBounding = dpToPx(view.getContext(), newY)

        // Determine how much to scale: the dimension requiring less scaling is
        // closer to the its side. This way the image always stays inside your
        // bounding box AND either x/y axis touches it.
        val xScale = (xBounding.toFloat()) / width
        val yScale = (yBounding.toFloat()) / height
        val scale = if (xScale <= yScale) xScale else yScale

        // Create a matrix for the scaling and add the scaling data
        val matrix = Matrix()
        matrix.postScale(scale, scale)

        // Create a new bitmap and convert it to a format understood by the ImageView
        val scaledBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
        width = scaledBitmap.getWidth() // re-use
        height = scaledBitmap.getHeight() // re-use
        val result: BitmapDrawable = BitmapDrawable(scaledBitmap)

        // Apply the scaled bitmap
        view.setImageDrawable(result)

        // Now change ImageView's dimensions to match the scaled image
        val params: LinearLayout.LayoutParams = view.getLayoutParams() as LinearLayout.LayoutParams
        params.width = width
        params.height = height
        view.setLayoutParams(params)
    }

    fun dpToPx(context: Context, dp: Int): Int {
        val density = context.getApplicationContext().getResources().getDisplayMetrics().density
        return Math.round(dp.toFloat() * density)
    }

    fun sendOutput(context: Context, res: Int) {
        sendOutput(Int.Companion.MAX_VALUE, context, res)
    }

    fun sendOutput(color: Int, context: Context, res: Int) {
        sendOutput(color, context, context.getString(res))
    }

    fun sendOutput(context: Context, res: Int, type: Int) {
        sendOutput(Int.Companion.MAX_VALUE, context, res, type)
    }

    fun sendOutput(color: Int, context: Context, res: Int, type: Int) {
        sendOutput(color, context, context.getString(res), type)
    }

    fun sendOutput(context: Context, s: CharSequence?) {
        sendOutput(Int.Companion.MAX_VALUE, context, s)
    }

    fun sendOutput(context: Context, s: CharSequence?, type: Int) {
        sendOutput(Int.Companion.MAX_VALUE, context, s, type)
    }

    @JvmOverloads
    fun sendOutput(
        color: Int,
        context: Context,
        s: CharSequence?,
        type: Int = TerminalManager.CATEGORY_OUTPUT
    ) {
        val intent: Intent = Intent(PrivateIOReceiver.ACTION_OUTPUT)
        intent.putExtra(PrivateIOReceiver.TEXT, s)
        intent.putExtra(PrivateIOReceiver.COLOR, color)
        intent.putExtra(PrivateIOReceiver.TYPE, type)
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }

    fun sendOutput(mainPack: MainPack, s: CharSequence?, type: Int) {
        sendOutput(mainPack.commandColor, mainPack.context, s, type)
    }

    fun sendOutput(context: Context, s: CharSequence?, type: Int, action: Any?) {
        sendOutput(Int.Companion.MAX_VALUE, context, s, type, action)
    }

    fun sendOutput(color: Int, context: Context, s: CharSequence?, type: Int, action: Any?) {
        val intent: Intent = Intent(PrivateIOReceiver.ACTION_OUTPUT)
        intent.putExtra(PrivateIOReceiver.TEXT, s)
        intent.putExtra(PrivateIOReceiver.COLOR, color)
        intent.putExtra(PrivateIOReceiver.TYPE, type)

        if (action is String) intent.putExtra(PrivateIOReceiver.ACTION, action)
        else if (action is Parcelable) intent.putExtra(
            PrivateIOReceiver.ACTION,
            action as Parcelable
        )

        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }

    fun sendOutput(context: Context, s: CharSequence?, type: Int, action: Any?, longAction: Any?) {
        sendOutput(Int.Companion.MAX_VALUE, context, s, type, action, longAction)
    }

    fun sendOutput(
        color: Int,
        context: Context,
        s: CharSequence?,
        type: Int,
        action: Any?,
        longAction: Any?
    ) {
        val intent: Intent = Intent(PrivateIOReceiver.ACTION_OUTPUT)
        intent.putExtra(PrivateIOReceiver.TEXT, s)
        intent.putExtra(PrivateIOReceiver.COLOR, color)
        intent.putExtra(PrivateIOReceiver.TYPE, type)

        if (action is String) intent.putExtra(PrivateIOReceiver.ACTION, action)
        else if (action is Parcelable) intent.putExtra(
            PrivateIOReceiver.ACTION,
            action as Parcelable
        )

        if (longAction is String) intent.putExtra(PrivateIOReceiver.LONG_ACTION, longAction)
        else if (longAction is Parcelable) intent.putExtra(
            PrivateIOReceiver.LONG_ACTION,
            longAction as Parcelable
        )

        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }

    fun sendInput(context: Context, text: String?) {
        val intent: Intent = Intent(PrivateIOReceiver.ACTION_INPUT)
        intent.putExtra(PrivateIOReceiver.TEXT, text)
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }

    const val TERA: Int = 0
    const val GIGA: Int = 1
    const val MEGA: Int = 2
    const val KILO: Int = 3
    const val BYTE: Int = 4

    private val total: Long = -1

    fun freeRam(mgr: ActivityManager, info: ActivityManager.MemoryInfo): Double {
        mgr.getMemoryInfo(info)
        return info.availMem.toDouble()
    }

    fun totalRam(): Long {
        if (total > 0) return total

        val reader: BufferedReader?
        try {
            reader = BufferedReader(InputStreamReader(FileInputStream("/proc/meminfo")))

            var line: String?
            while ((reader.readLine().also { line = it }) != null) {
                if (line!!.startsWith("MemTotal")) {
                    line = line.replace("\\D+".toRegex(), EMPTYSTRING)
                    return line.toLong()
                }
            }
        } catch (e: Exception) {
        }
        return 0
    }

    fun round(value: Double, places: Int): Double {
        var places = places
        if (places < 0) places = 0

        try {
            var bd = BigDecimal(value)
            bd = bd.setScale(places, RoundingMode.HALF_UP)
            return bd.toDouble()
        } catch (e: Exception) {
            return value
        }
    }

    @Throws(IOException::class)
    fun getClassesInPackage(packageName: String, c: Context): MutableList<String> {
        val classes: MutableList<String> = ArrayList<String>()
        val packageCodePath = c.getPackageCodePath()
        val df: DexFile = DexFile(packageCodePath)
        val iter: Enumeration<String> = df.entries()
        while (iter.hasMoreElements()) {
            val className = iter.nextElement()
            if (className.contains(packageName) && !className.contains("$")) {
                classes.add(className.substring(className.lastIndexOf(".") + 1, className.length))
            }
        }

        return classes
    }

    fun scale(from: IntArray, to: IntArray, n: Int): Int {
        return (to[1] - to[0]) * (n - from[0]) / (from[1] - from[0]) + to[0]
    }

    fun toString(enums: Array<Enum<*>?>): Array<String?> {
        val arr = arrayOfNulls<String>(enums.size)
        for (count in enums.indices) arr[count] = enums[count]!!.name
        return arr
    }

    private fun getNicePath(filePath: String?): String {
        if (filePath == null) return "null"

        val home: String =
            XMLPrefsManager.get(File::class.java, Behavior.home_path)?.getAbsolutePath() ?: EMPTYSTRING

        if (filePath == home) {
            return "~"
        } else if (filePath.startsWith(home)) {
            return "~" + filePath.replace(home, EMPTYSTRING)
        } else {
            return filePath
        }
    }

    fun find(o: Any?, array: Array<out Any?>?): Int {
        if (array == null) {
            return -1
        }
        return find(o, Arrays.asList<Any?>(*array))
    }

    fun find(o: Any?, list: List<*>?): Int {
        if (list == null) {
            return -1
        }
        for (count in list.indices) {
            val x: Any? = list.get(count)
            if (x == null) continue

            if (o === x) return count

            if (o is XMLPrefsSave) {
                try {
                    if ((o as XMLPrefsSave).label() == x as String) return count
                } catch (e: Exception) {
                }
            }

            if (o is String && x is XMLPrefsSave) {
                try {
                    if ((x as XMLPrefsSave).label() == o) return count
                } catch (e: Exception) {
                }
            }

            try {
                if (o == x || x == o) return count
            } catch (e: Exception) {
                continue
            }
        }
        return -1
    }

    var pd: Pattern = Pattern.compile("%d", Pattern.CASE_INSENSITIVE or Pattern.LITERAL)
    var pu: Pattern = Pattern.compile("%u", Pattern.CASE_INSENSITIVE or Pattern.LITERAL)
    var pp: Pattern = Pattern.compile("%p", Pattern.CASE_INSENSITIVE or Pattern.LITERAL)
    fun getHint(currentPath: String?): String? {
        if (!XMLPrefsManager.getBoolean(Ui.show_session_info)) return null

        var format: String = XMLPrefsManager.get(Behavior.session_info_format)
        if (format.length == 0) return null

        var deviceName: String = XMLPrefsManager.get(Ui.deviceName)
        if (deviceName == null || deviceName.length == 0) {
            deviceName = Build.DEVICE
        }

        var username: String? = XMLPrefsManager.get(Ui.username)
        if (username == null) username = EMPTYSTRING

        format = pd.matcher(format).replaceAll(Matcher.quoteReplacement(deviceName))
        format = pu.matcher(format).replaceAll(Matcher.quoteReplacement(username))
        format = pp.matcher(format).replaceAll(Matcher.quoteReplacement(getNicePath(currentPath)))

        return format
    }

    fun findPrefix(list: MutableList<String?>, prefix: String): Int {
        for (count in list.indices) if (list.get(count)!!.startsWith(prefix)) return count
        return -1
    }

    fun mmToPx(metrics: DisplayMetrics?, mm: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, mm.toFloat(), metrics).toInt()
    }

    fun insertHeaders(s: MutableList<String?>, newLine: Boolean) {
        var current = 0.toChar()
        for (count in s.indices) {
            val st = s.get(count)!!.trim { it <= ' ' }.uppercase(Locale.getDefault())
            if (st.length < 0) continue

            val c = st.get(0)
            if (current != c) {
                s.add(
                    count,
                    (if (newLine) NEWLINE else EMPTYSTRING) + c + (if (newLine) NEWLINE else EMPTYSTRING)
                )
                current = c
            }
        }
    }

    fun addPrefix(list: MutableList<String?>, prefix: String) {
        for (count in list.indices) {
            list.set(count, prefix + list.get(count))
        }
    }

    fun addSeparator(list: MutableList<String?>, separator: String) {
        for (count in list.indices) list.set(count, list.get(count) + separator)
    }

    fun formatMillis(millis: Int): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = (millis / (1000 * 60 * 60)) % 24

        if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            return String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    fun toPlanString(strings: Array<out String?>?): String {
        if (strings != null) {
            return Tuils.toPlanString(strings, NEWLINE)
        }
        return EMPTYSTRING
    }

    fun toPlanString(separator: String, strings: List<*>?): String {
        if (strings == null) {
            return EMPTYSTRING
        }

        var output = EMPTYSTRING
        for (count in strings.indices) {
            output = output + strings.get(count).toString()
            if (count < strings.size - 1) output = output + separator
        }
        return output
    }

    fun nodeToString(node: Node?): String? {
        try {
            val transfac = TransformerFactory.newInstance()
            val trans = transfac.newTransformer()
            trans.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
            trans.setOutputProperty(OutputKeys.INDENT, "yes")
            val sw = StringWriter()
            val result = StreamResult(sw)
            val source = DOMSource(node)
            trans.transform(source, result)

            return sw.toString()
        } catch (e: TransformerException) {
            e.printStackTrace()
            return null
        }
    }

    fun log(o: Any?) {
        Log.e(TAG, o.toString())
    }

    fun log(o: Any?, o2: Any?) {
        Log.e(TAG, o.toString() + " -- " + o2.toString())
    }

    fun log(o: Any, to: PrintStream) {
        if (o is Throwable) {
            o.printStackTrace(to)
        } else {
            val text: String?
            if (o is Array<*>) text = o.contentToString()
            else text = o.toString()

            try {
                to.write(text.toByteArray())
            } catch (e: IOException) {
                Log.e(TAG, e.message, e)
            }
        }
    }

    fun log(o: Any?, o2: Any?, to: OutputStream) {
        try {
            if (o is Array<*> && o2 is Array<*>) {
                to.write((o.contentToString() + " -- " + o2.contentToString()).toByteArray())
            } else {
                to.write((o.toString() + " -- " + o2.toString()).toByteArray())
            }
        } catch (e: Exception) {
            Log.e(TAG, e.message, e)
        }
    }

    fun hasInternetAccess(): Boolean {
        return NetUtils.hasInternetAccess()
    }

    fun <T> getDefaultValue(clazz: Class<T>?): T? {
        return java.lang.reflect.Array.get(java.lang.reflect.Array.newInstance(clazz, 1), 0) as T?
    }

    fun toFile(s: String?) {
        if (s == null) return
        try {
            val f = File(folder, "crash.txt")
            val pw = PrintWriter(FileOutputStream(f, true))
            pw.println(Date().toString())
            pw.println(s)
            pw.println()
            pw.flush()
            pw.close()
        } catch (e1: Exception) {
        }
    }

    fun toFile(o: Any?) {
        if (o == null) return
        try {
            val f = File(folder, "crash.txt")
            val pw = PrintWriter(FileOutputStream(f, true))
            pw.println(Date().toString())
            if (o is Throwable) {
                o.printStackTrace(pw)
            } else {
                pw.println(o.toString())
            }
            pw.println()
            pw.flush()
            pw.close()
        } catch (e: Exception) {
        }
    }

    fun toPlanString(strings: List<String?>?, separator: String): String {
        return ohi.andre.consolelauncher.tuils.TextUtils.toPlanString(strings, separator)
    }

    fun filesToPlanString(files: MutableList<File?>?, separator: String?): String? {
        if (files == null || files.size == 0) {
            return null
        }

        val builder = StringBuilder()
        val limit = files.size - 1
        for (count in files.indices) {
            builder.append(files.get(count)!!.getName())
            if (count < limit) {
                builder.append(separator)
            }
        }
        return builder.toString()
    }

    fun toPlanString(strings: List<String?>?): String {
        return toPlanString(strings, NEWLINE)
    }

    fun toPlanString(objs: kotlin.Array<out Any?>?, separator: String): String {
        return ohi.andre.consolelauncher.tuils.TextUtils.toPlanString(objs, separator)
    }

    var unnecessarySpaces: Pattern = Pattern.compile("\\s{2,}")
    fun removeUnncesarySpaces(string: String?): String? {
        return ohi.andre.consolelauncher.tuils.TextUtils.removeUnncesarySpaces(string)
    }

    fun splitArgs(input: String?): MutableList<String?> {
        val args: MutableList<String?> = ArrayList<String?>()
        if (input == null) return args

        val currentArg = StringBuilder()
        var inDoubleQuote = false
        var inSingleQuote = false
        var escaped = false

        for (i in 0..<input.length) {
            val c = input.get(i)
            if (escaped) {
                currentArg.append(c)
                escaped = false
            } else if (c == '\\') {
                escaped = true
            } else if (c == '\"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote
            } else if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote
            } else if (Character.isWhitespace(c) && !inDoubleQuote && !inSingleQuote) {
                if (currentArg.length > 0) {
                    args.add(currentArg.toString())
                    currentArg.setLength(0)
                }
            } else {
                currentArg.append(c)
            }
        }
        if (currentArg.length > 0) {
            args.add(currentArg.toString())
        }
        return args
    }

    fun getStackTrace(throwable: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw, true)
        throwable.printStackTrace(pw)
        return sw.getBuffer().toString()
    }

    fun isAlpha(s: String?): Boolean {
        if (s == null) {
            return false
        }
        val chars = s.toCharArray()

        for (c in chars) if (!Character.isLetter(c)) return false

        return true
    }

    fun isPhoneNumber(s: String?): Boolean {
        if (s == null) {
            return false
        }
        val chars = s.toCharArray()

        for (c in chars) {
            if (Character.isLetter(c)) {
                return false
            }
        }

        return true
    }

    //    return 0 if only digit
    fun firstNonDigit(s: String?): Char {
        if (s == null) {
            return 0.toChar()
        }

        val chars = s.toCharArray()

        for (c in chars) {
            if (!Character.isDigit(c)) {
                return c
            }
        }

        return 0.toChar()
    }

    fun isNumber(s: String?): Boolean {
        if (s == null || s.length == 0) {
            return false
        }

        val chars = s.toCharArray()

        for (c in chars) {
            if (!Character.isDigit(c)) {
                return false
            }
        }

        return true
    }

    fun openFile(c: Context, f: File): Intent {
        val intent: Intent = Intent(Intent.ACTION_VIEW)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val u = buildFile(c, f)
        val mimetype = MimeTypes.getMimeType(f.getAbsolutePath(), f.isDirectory())

        intent.setDataAndType(u, mimetype)

        val flags: Int =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION

        intent.addFlags(flags)

        return intent
    }

    fun shareFile(c: Context, f: File): Intent {
        val intent: Intent = Intent(Intent.ACTION_SEND)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val u = buildFile(c, f)

        val mimetype = MimeTypes.getMimeType(f.getAbsolutePath(), f.isDirectory())

        intent.setDataAndType(u, mimetype)

        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.putExtra(Intent.EXTRA_STREAM, u)

        return intent
    }

    private fun buildFile(context: Context, file: File): Uri? {
        return FileProvider.getUriForFile(context, GenericFileProvider.PROVIDER_NAME, file)
    }

    fun eval(str: String): Double {
        return object : Any() {
            var pos: Int = -1
            var ch: Int = 0

            fun nextChar() {
                ch = if (++pos < str.length) str.get(pos).code else -1
            }

            fun eat(charToEat: Int): Boolean {
                while (ch == ' '.code) nextChar()
                if (ch == charToEat) {
                    nextChar()
                    return true
                }
                return false
            }

            fun parse(): Double {
                nextChar()
                val x = parseExpression()
                if (pos < str.length) throw RuntimeException("Unexpected: " + ch.toChar())
                return x
            }

            fun parseExpression(): Double {
                var x = parseTerm()
                while (true) {
                    if (eat('+'.code)) x += parseTerm()
                    else if (eat('-'.code)) x -= parseTerm()
                    else return x
                }
            }

            fun parseTerm(): Double {
                var x = parseFactor()
                while (true) {
                    if (eat('*'.code)) x *= parseFactor()
                    else if (eat('/'.code)) x /= parseFactor()
                    else return x
                }
            }

            fun parseFactor(): Double {
                if (eat('+'.code)) return parseFactor()
                if (eat('-'.code)) return -parseFactor()

                var x: Double
                val startPos = this.pos
                if (eat('('.code)) {
                    x = parseExpression()
                    eat(')'.code)
                } else if ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) {
                    while ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) nextChar()
                    x = str.substring(startPos, this.pos).toDouble()
                } else if (ch >= 'a'.code && ch <= 'z'.code) {
                    while (ch >= 'a'.code && ch <= 'z'.code) nextChar()
                    val func = str.substring(startPos, this.pos)
                    x = parseFactor()
                    if (func == "sqrt") x = sqrt(x)
                    else if (func == "sin") x = sin(Math.toRadians(x))
                    else if (func == "cos") x = cos(Math.toRadians(x))
                    else if (func == "tan") x = tan(Math.toRadians(x))
                    else throw RuntimeException("Unknown function: " + func)
                } else {
                    throw RuntimeException("Unexpected: " + ch.toChar())
                }

                if (eat('^'.code)) x = x.pow(parseFactor())

                return x
            }
        }.parse()
    }

    fun getTextFromClipboard(context: Context): String? {
        try {
            val manager =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val item: ClipData.Item = manager.getPrimaryClip()!!.getItemAt(0)
            return item.getText().toString()
        } catch (e: Exception) {
            return null
        }
    }

    fun dpToPx(resources: Resources, dp: Int): Int {
        val displayMetrics: DisplayMetrics = resources.getDisplayMetrics()
        return Math.round(dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT))
    }

    private const val FILEUPDATE_DELAY = 100
    private const val FORK_FOLDER_NAME = "Re-T-UI"
    private var folder: File? = null

    @JvmStatic
    fun getFolder(): File {
        return folder ?: File("")
    }

    fun init(context: Context) {
        Log.e("TUI-INIT", "Starting Tuils.init()")
        try {
            val sharedRoot: File? = Environment.getExternalStorageDirectory()
            var newFolder: File?
            if (isPlayStoreBuild) {
                var appExternalRoot = context.getExternalFilesDir(null)
                if (appExternalRoot == null) {
                    appExternalRoot = context.getFilesDir()
                }
                // Play Store flavor does not request shared-storage permissions.
                // Keep the folder name Linux/path friendly, but store it in app-owned
                // external storage so normal File APIs can read/write reliably.
                newFolder = File(appExternalRoot, FORK_FOLDER_NAME)
                Log.e("TUI-INIT", "Play Store app-owned folder: " + newFolder.getAbsolutePath())
            } else {
                // F-Droid/debug channel can own the shared user-facing root.
                newFolder = File(sharedRoot, FORK_FOLDER_NAME)
            }

            Log.e("TUI-INIT", "Target folder: " + newFolder.getAbsolutePath())

            if (!newFolder.exists()) {
                val created = newFolder.mkdirs()
                Log.e("TUI-INIT", "Root folder creation attempt: " + created)


                // Fallback to private storage if shared root is not accessible
                if (!created) {
                    var appExternalRoot = context.getExternalFilesDir(null)
                    if (appExternalRoot == null) {
                        appExternalRoot = context.getFilesDir()
                    }
                    newFolder = File(appExternalRoot, FORK_FOLDER_NAME)
                    newFolder.mkdirs()
                    Log.e("TUI-INIT", "Fallback to private storage: " + newFolder.getAbsolutePath())
                }
            }

            val legacyForkFolder = File(sharedRoot, "Re:T-UI")
            val legacyOriginalFolder = File(sharedRoot, "T-UI")

            if (newFolder.exists() && isDirectoryEffectivelyEmpty(newFolder)) {
                if (legacyForkFolder.exists() && legacyForkFolder.isDirectory()) {
                    Log.e("TUI-INIT", "Legacy Re:T-UI folder found, migration skipped for now...")
                    // copyDirectory(legacyForkFolder, newFolder);
                } else if (legacyOriginalFolder.exists() && legacyOriginalFolder.isDirectory()) {
                    Log.e("TUI-INIT", "Old T-UI folder found, migration skipped for now...")
                    // copyDirectory(legacyOriginalFolder, newFolder);
                }
            }

            if (newFolder.exists()) {
                folder = newFolder
                val subfolders = arrayOf<String?>("fonts", "rss", "old")
                for (sub in subfolders) {
                    val f = File(newFolder, sub)
                    if (!f.exists()) {
                        f.mkdirs()
                    }
                }
                Log.e("TUI-INIT", "Subfolders checked/created")
            } else {
                Log.e("TUI-INIT", "Folder still does not exist after mkdirs()")
            }
        } catch (e: Exception) {
            Log.e("TUI-INIT", "Crash in Tuils.init", e)
            toFile(e)
        }
    }

    private val isPlayStoreBuild: Boolean
        get() = "playstore".equals(BuildConfig.FLAVOR, ignoreCase = true)

    private fun isDirectoryEffectivelyEmpty(dir: File): Boolean {
        val files = dir.listFiles()
        return files == null || files.size == 0
    }

    @Throws(IOException::class)
    fun copyDirectory(source: File, target: File) {
        if (source.isDirectory()) {
            if (!target.exists() && !target.mkdirs()) {
                throw IOException("Cannot create dir " + target.getAbsolutePath())
            }

            val children = source.list()
            if (children != null) {
                for (child in children) {
                    copyDirectory(File(source, child), File(target, child))
                }
            }
        } else {
            val parent = target.getParentFile()
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw IOException("Cannot create dir " + parent.getAbsolutePath())
            }

            val `in`: InputStream = FileInputStream(source)
            val out: OutputStream = FileOutputStream(target)

            val buf = ByteArray(1024)
            var len: Int
            while ((`in`.read(buf).also { len = it }) > 0) {
                out.write(buf, 0, len)
            }
            `in`.close()
            out.close()
        }
    }

    fun alphabeticCompare(s1: String?, s2: String?): Int {
        val cmd1 = removeSpaces(s1 ?: EMPTYSTRING).lowercase(Locale.getDefault())
        val cmd2 = removeSpaces(s2 ?: EMPTYSTRING).lowercase(Locale.getDefault())

        var count = 0
        while (count < cmd1.length && count < cmd2.length) {
            val c1 = cmd1.get(count)
            val c2 = cmd2.get(count)

            if (c1 < c2) {
                return -1
            } else if (c1 > c2) {
                return 1
            }
            count++
        }

        if (cmd1.length > cmd2.length) {
            return 1
        } else if (cmd1.length < cmd2.length) {
            return -1
        }
        return 0
    }

    private const val SPACE_REGEXP = "\\s"
    fun removeSpaces(string: String): String {
        return string.replace(SPACE_REGEXP.toRegex(), EMPTYSTRING)
    }

    fun getNetworkType(context: Context): String {
        return NetUtils.getNetworkType(context)
    }

    @SuppressLint("SoonBlockedPrivateApi")
    fun setCursorDrawableColor(editText: EditText, color: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            editText.setTextCursorDrawable(editText.getTextCursorDrawable())
            val cursorDrawable = editText.getTextCursorDrawable()
            if (cursorDrawable != null) {
                cursorDrawable.setColorFilter(color, PorterDuff.Mode.SRC_IN)
            }
        }
    }

    fun pendingIntentFlags(flags: Int): Int {
        return flags or PendingIntent.FLAG_IMMUTABLE
    }

    @Throws(CanceledException::class)
    fun sendPendingIntent(context: Context?, pendingIntent: PendingIntent?) {
        if (pendingIntent == null) {
            return
        }

        if (context == null) {
            pendingIntent.send()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val options: ActivityOptions = ActivityOptions.makeBasic()
            options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            pendingIntent.send(context, 0, null, null, null, null, options.toBundle())
            return
        }

        pendingIntent.send(context, 0, null)
    }

    fun nOfBytes(file: File?): Int {
        var count = 0
        try {
            val `in` = FileInputStream(file)

            while (`in`.read() != -1) count++

            return count
        } catch (e: IOException) {
            log(e)
            return count
        }
    }

    fun sendXMLParseError(context: Context, PATH: String?, e: SAXParseException) {
        sendOutput(
            Color.RED,
            context,
            context.getString(R.string.output_xmlproblem1) + SPACE + PATH + context.getString(R.string.output_xmlproblem2) + NEWLINE + context.getString(
                R.string.output_errorlabel
            ) +
                    "File: " + e.getSystemId() + NEWLINE +
                    "Message" + e.message + NEWLINE +
                    "Line" + e.getLineNumber() + NEWLINE +
                    "Column" + e.getColumnNumber()
        )
    }

    fun sendXMLParseError(context: Context, PATH: String?) {
        sendOutput(
            Color.RED,
            context,
            context.getString(R.string.output_xmlproblem1) + SPACE + PATH + context.getString(R.string.output_xmlproblem2)
        )
    }

    //    static final int WEATHER_TIMEOUT = 6000;
    //    public static boolean location(Context context, final ArgsRunnable whenFound, final Runnable notFound, final Handler handler) {
    //        final LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    //        if(locationManager == null) return false;
    //
    //        final LocationListener locationListener = new LocationListener() {
    //            @Override
    //            public void onLocationChanged(Location location) {
    //                whenFound.run(location.getLatitude(), location.getLongitude());
    //            }
    //
    //            @Override
    //            public void onStatusChanged(String provider, int status, Bundle extras) {
    //            }
    //
    //            @Override
    //            public void onProviderEnabled(String provider) {
    //            }
    //
    //            @Override
    //            public void onProviderDisabled(String provider) {
    //            }
    //        };
    //
    //        boolean gpsProvider = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    //        boolean networkProvider = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    //        boolean passiveProvider = locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER);
    //
    //        if (!gpsStatus && !networkStatus) return false;
    //
    //        try {
    //            locationManager.requestSingleUpdate(gpsStatus ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER, locationListener, Looper.getMainLooper());
    //        } catch (SecurityException e) {
    //            Tuils.log(e);
    //            Tuils.toFile(e);
    //            return false;
    //        }
    //
    //        Location location;
    //        try {
    //            Location[] ls = {
    //                    locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER),
    //                    locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER),
    //                    locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)};
    //
    //            location = ls[0];
    //            for(int c = 1; c < ls.length; c++) {
    //                if(location == null) location = ls[c];
    //                else if(ls[c] != null && location.getTime() < ls[c].getTime()) location = ls[c];
    //            }
    //        } catch (SecurityException e) {
    //            Tuils.toFile(e);
    //            return false;
    //        }
    //
    //        if(handler != null) {
    //            handler.postDelayed(notFound, WEATHER_TIMEOUT);
    //            handler.postDelayed(new Runnable() {
    //                @Override
    //                public void run() {
    //                    if(locationManager != null) locationManager.removeUpdates(locationListener);
    //                }
    //            }, WEATHER_TIMEOUT);
    //        }
    //
    //        return true;
    //    }
    //    public static Location getLocation(Context context) {
    //        final LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    //        if(locationManager == null) return null;
    //
    //        Location location;
    //        try {
    //            Location[] ls = {
    //                    locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER),
    //                    locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER),
    //                    locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)};
    //
    //            location = ls[0];
    //            for(int c = 1; c < ls.length; c++) {
    //                if(location == null) location = ls[c];
    //                else if(ls[c] != null && location.getTime() < ls[c].getTime()) location = ls[c];
    //            }
    //
    //            return location;
    //        } catch (SecurityException e) {
    //            Tuils.toFile(e);
    //            return null;
    //        }
    //    }
    abstract class ArgsRunnable : Runnable {
        private var args: kotlin.Array<out Any?> = emptyArray()

        fun setArgs(vararg args: Any?) {
            this.args = args
        }

        fun run(vararg args: Any?) {
            setArgs(*args)
            run()
        }

        fun <T> get(c: Class<T>?, index: Int): T? {
            if (index < args.size) return args[index] as T?
            return null
        }
    }
}
