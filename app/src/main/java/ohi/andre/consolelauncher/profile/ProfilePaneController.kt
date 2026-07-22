package ohi.andre.consolelauncher.profile

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.FrameLayout
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
    private val handler = Handler(Looper.getMainLooper())
    private val qrCache = LruCache<String, Bitmap>(3)
    private val maskedQrCache = LruCache<String, Bitmap>(3)
    private var profile = ProfileStore.load(context)
    private val maskPhoneRunnable = Runnable {
        if (::phone.isInitialized) phone.text = ProfileStore.maskedPhone(profile.phone)
    }

    val visible: Boolean get() = overlay.visibility == View.VISIBLE

    init {
        inflatePane(false)
    }

    private fun inflatePane(showAfterInflate: Boolean) {
        handler.removeCallbacks(maskPhoneRunnable)
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
        val qrAdapter = QrAdapter()
        pager.adapter = qrAdapter
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(page: Int) {
                qrAdapter.maskAll()
                updateControls(page)
            }

            override fun onPageScrollStateChanged(state: Int) {
                if (state == ViewPager2.SCROLL_STATE_DRAGGING) qrAdapter.maskAll()
            }
        })
        close.setOnClickListener { onClose() }
        edit.setOnClickListener { editProfile() }
        addQr.setOnClickListener { addQr() }
        removeQr.setOnClickListener { removeCurrentQr() }
        phone.setOnClickListener { revealPhone() }
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
        (pager.adapter as? QrAdapter)?.maskAll()
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
        photo.clearColorFilter()
        pager.background = TuixtTheme.rect(
            context,
            ColorUtils.setAlphaComponent(TuixtTheme.surfaceColor(), 225),
            TuixtTheme.borderColor(),
            1.25f
        )
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
        photo.setImageBitmap(
            recolorMonochrome(
                BitmapFactory.decodeResource(context.resources, R.drawable.profile_avatar),
                TuixtTheme.borderColor(),
                Color.WHITE
            )
        )
        qrCache.evictAll()
        maskedQrCache.evictAll()
        (pager.adapter as? QrAdapter)?.maskAll()
        pager.adapter?.notifyDataSetChanged()
        val current = pager.currentItem.coerceAtMost((qrItems().size - 1).coerceAtLeast(0))
        if (pager.currentItem != current) pager.setCurrentItem(current, false)
        updateControls(current)
    }

    private fun maskPhone() {
        handler.removeCallbacks(maskPhoneRunnable)
        phone.text = ProfileStore.maskedPhone(profile.phone)
    }

    private fun revealPhone() {
        handler.removeCallbacks(maskPhoneRunnable)
        phone.text = profile.phone.ifBlank { "NOT SET" }
        if (profile.phone.isNotBlank()) handler.postDelayed(maskPhoneRunnable, PHONE_REVEAL_MS)
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
                    else -> ProfileStore.qrValueValidationError(values["value"].orEmpty())
                }
            },
            TuixtDialog.FormAction { values ->
                profile = profile.copy(codes = profile.codes + ProfileQr(values["label"].orEmpty(), values["value"].orEmpty()))
                ProfileStore.save(context, profile)
                pager.adapter?.notifyDataSetChanged()
                pager.setCurrentItem(qrItems().lastIndex, true)
                updateControls(qrItems().lastIndex)
            }
        )
    }

    private fun removeCurrentQr() {
        val item = qrItems().getOrNull(pager.currentItem) ?: return
        if (item.storedIndex < 0) return
        val index = item.storedIndex
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
        val items = qrItems()
        val count = items.size
        position.text = if (count == 0) "00/00" else "%02d/%02d".format(page + 1, count)
        removeQr.visibility = if (items.getOrNull(page)?.storedIndex?.let { it >= 0 } == true) View.VISIBLE else View.INVISIBLE
    }

    private fun qrItems(): List<DisplayQr> = buildList {
        if (profile.phone.isNotBlank()) {
            val displayName = profile.name.ifBlank { "OPERATOR" }
            add(DisplayQr("CONTACT", ProfileStore.contactVCard(displayName, profile.phone), "SCAN TO ADD $displayName", -1))
        }
        profile.codes.forEachIndexed { index, code -> add(DisplayQr(code.label, code.value, code.value, index)) }
    }

    private inner class QrAdapter : RecyclerView.Adapter<QrHolder>() {
        private var revealedPage = RecyclerView.NO_POSITION

        fun maskAll() {
            if (revealedPage == RecyclerView.NO_POSITION) return
            val previous = revealedPage
            revealedPage = RecyclerView.NO_POSITION
            notifyItemChanged(previous)
        }

        override fun getItemCount(): Int = qrItems().size.coerceAtLeast(1)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QrHolder {
            val page = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setPadding(dp(context, 10f), dp(context, 8f), dp(context, 10f), dp(context, 8f))
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
            val reveal = TextView(context).apply {
                gravity = Gravity.CENTER
                text = "[ REVEAL ]"
                textSize = 18f
                setTextColor(TuixtTheme.borderColor())
                setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
                setBackgroundColor(ColorUtils.setAlphaComponent(TuixtTheme.surfaceColor(), 150))
                contentDescription = "Reveal QR code"
                isClickable = true
                isFocusable = true
            }
            val imageHost = FrameLayout(context).apply {
                addView(image, FrameLayout.LayoutParams(-1, -1))
                addView(reveal, FrameLayout.LayoutParams(-1, -1))
            }
            val value = TextView(context).apply {
                gravity = Gravity.CENTER
                textSize = 10f
                setTextColor(TuixtTheme.textColor())
                typeface = Tuils.getTypeface(context)
                maxLines = 2
            }
            page.addView(label, LinearLayout.LayoutParams(-1, -2))
            page.addView(imageHost, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            page.addView(value, LinearLayout.LayoutParams(-1, -2))
            return QrHolder(page, label, image, reveal, value)
        }

        override fun onBindViewHolder(holder: QrHolder, page: Int) {
            val items = qrItems()
            if (items.isEmpty()) {
                holder.label.text = "NO CONNECTION CODES"
                holder.image.setImageDrawable(null)
                holder.reveal.visibility = View.GONE
                holder.value.text = "TAP ADD QR TO GENERATE ONE LOCALLY"
                return
            }
            val code = items[page]
            holder.label.text = code.label.uppercase(Locale.getDefault())
            val revealed = page == revealedPage
            val qr = qrCache.get(code.value) ?: generateQr(
                code.value,
                TuixtTheme.borderColor(),
                TuixtTheme.surfaceColor()
            ).also { qrCache.put(code.value, it) }
            holder.image.visibility = View.VISIBLE
            holder.reveal.visibility = if (revealed) View.GONE else View.VISIBLE
            holder.image.contentDescription = if (revealed) "QR code. Tap to hide." else "Blurred QR preview"
            holder.image.setImageBitmap(if (revealed) qr else {
                maskedQrCache.get(code.value) ?: maskQr(qr).also { maskedQrCache.put(code.value, it) }
            })
            holder.reveal.setOnClickListener {
                toggleReveal(holder)
            }
            holder.image.setOnClickListener {
                if (revealed) toggleReveal(holder)
            }
            holder.value.text = code.caption
        }

        private fun toggleReveal(holder: QrHolder) {
            val selected = holder.bindingAdapterPosition
            if (selected == RecyclerView.NO_POSITION) return
            revealedPage = if (revealedPage == selected) RecyclerView.NO_POSITION else selected
            notifyItemChanged(selected)
        }
    }

    private data class DisplayQr(
        val label: String,
        val value: String,
        val caption: String,
        val storedIndex: Int
    )

    private class QrHolder(
        view: View,
        val label: TextView,
        val image: ImageView,
        val reveal: TextView,
        val value: TextView
    ) : RecyclerView.ViewHolder(view)

    companion object {
        private const val MAX_CODES = 12
        private const val QR_SIZE = 512
        private const val MASKED_QR_SIZE = 12
        private const val PHONE_REVEAL_MS = 30_000L

        private fun generateQr(value: String, sourceWhiteColor: Int, sourceBlackColor: Int): Bitmap {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.MARGIN, 2)
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
            }
            val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE, hints)
            val pixels = IntArray(QR_SIZE * QR_SIZE)
            for (y in 0 until QR_SIZE) for (x in 0 until QR_SIZE) {
                pixels[y * QR_SIZE + x] = if (matrix[x, y]) sourceBlackColor else sourceWhiteColor
            }
            return Bitmap.createBitmap(QR_SIZE, QR_SIZE, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, QR_SIZE, 0, 0, QR_SIZE, QR_SIZE)
            }
        }

        private fun maskQr(source: Bitmap): Bitmap {
            val tiny = Bitmap.createScaledBitmap(source, MASKED_QR_SIZE, MASKED_QR_SIZE, true)
            return Bitmap.createScaledBitmap(tiny, source.width, source.height, true).also { tiny.recycle() }
        }

        private fun recolorMonochrome(source: Bitmap, sourceWhiteColor: Int, sourceBlackColor: Int): Bitmap {
            val width = source.width
            val height = source.height
            val pixels = IntArray(width * height)
            source.getPixels(pixels, 0, width, 0, 0, width, height)
            for (index in pixels.indices) {
                val pixel = pixels[index]
                val mapped = if (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel) >= 384) {
                    sourceWhiteColor
                } else {
                    sourceBlackColor
                }
                pixels[index] = ColorUtils.setAlphaComponent(
                    mapped,
                    Color.alpha(pixel) * Color.alpha(mapped) / 255
                )
            }
            return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }
        }

    }
}
