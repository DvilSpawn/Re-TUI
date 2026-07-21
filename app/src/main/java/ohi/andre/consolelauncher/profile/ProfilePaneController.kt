package ohi.andre.consolelauncher.profile

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.util.LruCache
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.util.EnumMap
import java.util.Locale
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.dp
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleButton
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleHeader
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.stylePanel
import ohi.andre.consolelauncher.tuils.Tuils

class ProfilePaneController(
    private val context: Context,
    root: ViewGroup,
    private val onClose: () -> Unit
) {
    private val host = root.findViewById<ViewGroup>(R.id.profile_surface_host)
    private lateinit var overlay: View
    private lateinit var window: View
    private lateinit var header: TextView
    private lateinit var close: TextView
    private lateinit var photo: ImageView
    private lateinit var name: TextView
    private lateinit var phone: TextView
    private lateinit var edit: TextView
    private lateinit var addQr: TextView
    private lateinit var removeQr: TextView
    private lateinit var position: TextView
    private lateinit var pager: ViewPager2
    private val qrCache = LruCache<String, Bitmap>(3)
    private var profile = ProfileStore.load(context)

    val visible: Boolean get() = overlay.visibility == View.VISIBLE

    init {
        inflatePane(false)
    }

    private fun inflatePane(showAfterInflate: Boolean) {
        host.removeAllViews()
        LayoutInflater.from(context).inflate(R.layout.profile_surface, host, true)
        overlay = host.findViewById(R.id.profile_overlay)
        window = host.findViewById(R.id.profile_window_border)
        header = host.findViewById(R.id.profile_window_label)
        close = host.findViewById(R.id.profile_close)
        photo = host.findViewById(R.id.profile_photo)
        name = host.findViewById(R.id.profile_name)
        phone = host.findViewById(R.id.profile_phone)
        edit = host.findViewById(R.id.profile_edit)
        addQr = host.findViewById(R.id.profile_add_qr)
        removeQr = host.findViewById(R.id.profile_remove_qr)
        position = host.findViewById(R.id.profile_qr_position)
        pager = host.findViewById(R.id.profile_qr_pager)
        pager.adapter = QrAdapter()
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(page: Int) = updateControls(page)
        })
        close.setOnClickListener { onClose() }
        edit.setOnClickListener { editProfile() }
        addQr.setOnClickListener { addQr() }
        removeQr.setOnClickListener { removeCurrentQr() }
        phone.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> phone.text = profile.phone.ifBlank { "NOT SET" }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> maskPhone()
            }
            true
        }
        style()
        if (showAfterInflate) {
            render()
            overlay.visibility = View.VISIBLE
            overlay.bringToFront()
            overlay.post { alignHeaderTabs() }
        }
    }

    fun onConfigurationChanged(newConfig: Configuration) {
        val wasVisible = visible
        inflatePane(wasVisible)
    }

    fun show() {
        profile = ProfileStore.load(context)
        style()
        render()
        overlay.visibility = View.VISIBLE
        overlay.bringToFront()
        overlay.post { alignHeaderTabs() }
    }

    fun hide() {
        maskPhone()
        overlay.visibility = View.GONE
    }

    private fun style() {
        stylePanel(context, window)
        styleHeader(context, header)
        styleButton(context, close, false)
        styleButton(context, edit, false)
        styleButton(context, addQr, false)
        styleButton(context, removeQr, false)
        styleTextTree(window)
        name.setTextColor(TuixtTheme.accentColor())
        name.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
        phone.setTextColor(TuixtTheme.accentColor())
        phone.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
        photo.setColorFilter(TuixtTheme.accentColor(), PorterDuff.Mode.SRC_IN)
    }

    private fun alignHeaderTabs() {
        val paneTop = window.top - dp(context, 10f)
        positionHeader(header, Gravity.TOP or Gravity.START, paneTop, dp(context, 38f))
        positionHeader(close, Gravity.TOP or Gravity.END, paneTop, 0)
    }

    private fun positionHeader(view: TextView, gravity: Int, top: Int, start: Int) {
        val params = view.layoutParams as? android.widget.FrameLayout.LayoutParams ?: return
        params.gravity = gravity
        params.topMargin = top.coerceAtLeast(0)
        if (gravity and Gravity.START == Gravity.START) params.marginStart = start
        view.layoutParams = params
    }

    private fun styleTextTree(view: View) {
        if (view is TextView) {
            view.setTextColor(TuixtTheme.textColor())
            view.typeface = Tuils.getTypeface(context)
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) styleTextTree(view.getChildAt(index))
        }
    }

    private fun render() {
        name.text = profile.name.ifBlank { "OPERATOR" }.uppercase(Locale.getDefault())
        maskPhone()
        photo.setImageResource(R.drawable.profile_avatar)
        qrCache.evictAll()
        pager.adapter?.notifyDataSetChanged()
        val current = pager.currentItem.coerceAtMost((profile.codes.size - 1).coerceAtLeast(0))
        if (pager.currentItem != current) pager.setCurrentItem(current, false)
        updateControls(current)
    }

    private fun maskPhone() {
        phone.text = ProfileStore.maskedPhone(profile.phone)
    }

    private fun editProfile() {
        TuixtDialog.showValidatedForm(
            context,
            "EDIT PROFILE",
            listOf(
                TuixtDialog.FormField("name", "Name", "Operator name", value = profile.name),
                TuixtDialog.FormField("phone", "Phone", "Hidden by default", value = profile.phone)
            ),
            "SAVE",
            "CANCEL",
            TuixtDialog.FormValidator { values ->
                when {
                    values["name"].orEmpty().isBlank() -> "Name is required."
                    values["name"].orEmpty().length > 48 -> "Name must be 48 characters or fewer."
                    values["phone"].orEmpty().length > 32 -> "Phone must be 32 characters or fewer."
                    else -> null
                }
            },
            TuixtDialog.FormAction { values ->
                profile = profile.copy(name = values["name"].orEmpty(), phone = values["phone"].orEmpty())
                saveAndRender()
            }
        )
    }

    private fun addQr() {
        if (profile.codes.size >= MAX_CODES) return
        TuixtDialog.showValidatedForm(
            context,
            "ADD CONNECTION CODE",
            listOf(
                TuixtDialog.FormField("label", "Label", "GitHub, Wi-Fi, Contact"),
                TuixtDialog.FormField("value", "QR content", "URL or text")
            ),
            "ADD",
            "CANCEL",
            TuixtDialog.FormValidator { values ->
                when {
                    values["label"].orEmpty().isBlank() -> "Label is required."
                    values["label"].orEmpty().length > 40 -> "Label must be 40 characters or fewer."
                    values["value"].orEmpty().isBlank() -> "QR content is required."
                    values["value"].orEmpty().length > 2048 -> "QR content must be 2048 characters or fewer."
                    else -> null
                }
            },
            TuixtDialog.FormAction { values ->
                profile = profile.copy(codes = profile.codes + ProfileQr(values["label"].orEmpty(), values["value"].orEmpty()))
                ProfileStore.save(context, profile)
                pager.adapter?.notifyDataSetChanged()
                pager.setCurrentItem(profile.codes.lastIndex, true)
                updateControls(profile.codes.lastIndex)
            }
        )
    }

    private fun removeCurrentQr() {
        if (profile.codes.isEmpty()) return
        val index = pager.currentItem.coerceIn(profile.codes.indices)
        TuixtDialog.showConfirm(
            context,
            "REMOVE ${profile.codes[index].label}",
            "Delete this connection code from the local profile?",
            "REMOVE",
            "CANCEL",
            TuixtDialog.ConfirmAction {
                profile = profile.copy(codes = profile.codes.filterIndexed { itemIndex, _ -> itemIndex != index })
                saveAndRender()
            }
        )
    }

    private fun saveAndRender() {
        ProfileStore.save(context, profile)
        render()
    }

    private fun updateControls(page: Int) {
        val count = profile.codes.size
        position.text = if (count == 0) "00/00" else "%02d/%02d".format(page + 1, count)
        removeQr.visibility = if (count == 0) View.INVISIBLE else View.VISIBLE
    }

    private inner class QrAdapter : RecyclerView.Adapter<QrHolder>() {
        override fun getItemCount(): Int = profile.codes.size.coerceAtLeast(1)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QrHolder {
            val page = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setPadding(dp(context, 10f), dp(context, 8f), dp(context, 10f), dp(context, 8f))
                background = TuixtTheme.rect(context, ColorUtils.setAlphaComponent(TuixtTheme.surfaceColor(), 225), TuixtTheme.borderColor(), 1.25f)
            }
            val label = TextView(context).apply {
                gravity = Gravity.CENTER
                textSize = 15f
                setTextColor(TuixtTheme.accentColor())
                setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
            }
            val image = ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = "Connection QR code"
            }
            val value = TextView(context).apply {
                gravity = Gravity.CENTER
                textSize = 10f
                setTextColor(TuixtTheme.textColor())
                typeface = Tuils.getTypeface(context)
                maxLines = 2
            }
            page.addView(label, LinearLayout.LayoutParams(-1, -2))
            page.addView(image, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            page.addView(value, LinearLayout.LayoutParams(-1, -2))
            return QrHolder(page, label, image, value)
        }

        override fun onBindViewHolder(holder: QrHolder, page: Int) {
            if (profile.codes.isEmpty()) {
                holder.label.text = "NO CONNECTION CODES"
                holder.image.setImageDrawable(null)
                holder.value.text = "TAP ADD QR TO GENERATE ONE LOCALLY"
                return
            }
            val code = profile.codes[page]
            holder.label.text = code.label.uppercase(Locale.getDefault())
            holder.image.setImageBitmap(qrCache.get(code.value) ?: generateQr(code.value).also { qrCache.put(code.value, it) })
            holder.value.text = code.value
        }
    }

    private class QrHolder(
        view: View,
        val label: TextView,
        val image: ImageView,
        val value: TextView
    ) : RecyclerView.ViewHolder(view)

    companion object {
        private const val MAX_CODES = 12
        private const val QR_SIZE = 512

        private fun generateQr(value: String): Bitmap {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.MARGIN, 2)
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
            }
            val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE, hints)
            val pixels = IntArray(QR_SIZE * QR_SIZE)
            for (y in 0 until QR_SIZE) for (x in 0 until QR_SIZE) {
                pixels[y * QR_SIZE + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
            return Bitmap.createBitmap(QR_SIZE, QR_SIZE, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, QR_SIZE, 0, 0, QR_SIZE, QR_SIZE)
            }
        }

    }
}
