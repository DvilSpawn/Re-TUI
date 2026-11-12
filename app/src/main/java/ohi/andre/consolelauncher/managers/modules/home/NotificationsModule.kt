package ohi.andre.consolelauncher.managers.modules.home

import android.annotation.SuppressLint
import android.app.PendingIntent.CanceledException
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.modules.ModuleManager
import ohi.andre.consolelauncher.managers.modules.ModulePromptManager
import ohi.andre.consolelauncher.managers.notifications.NotificationGroups
import ohi.andre.consolelauncher.managers.notifications.NotificationManager
import ohi.andre.consolelauncher.managers.notifications.NotificationService
import ohi.andre.consolelauncher.managers.notifications.reply.ReplyManager
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.moduleBodyTextSize
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.moduleHeaderTextSize
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.moduleNameTextColor
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.notificationWidgetBorderColor
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.notificationWidgetTextColor
import ohi.andre.consolelauncher.managers.settings.TextShadows
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.tuils.frame.FrameTarget
import ohi.andre.consolelauncher.tuils.frame.TuiWidgetDecorator.decorateWidget
import ohi.andre.consolelauncher.tuils.Tuils
import kotlin.math.min

/** The notifications module: its panel, rows, paging and reply state. */
internal class NotificationsModule(
    private val host: HomeModuleHost,
    private val onNotificationsChanged: (List<NotificationService.Notification>) -> Unit,
) : HomeModule {
    private val context get() = host.context
    private val rootView get() = host.root!!

    override val id = ModuleManager.NOTIFICATIONS
    override val pushesStatusRowsUp = true
    override val broadcasts = setOf(NotificationService.ACTION_NOTIFICATION_FEED)

    val currentOverlayNotifications = ArrayList<NotificationService.Notification>()
    private var currentNotificationIndex = 0
    /** The app group whose notifications the list shows, or null for the top-level list. */
    private var openNotificationGroup: String? = null
    private var notificationReplyFocusKey: String? = null
    private var notificationCompactForKeyboard = false

    // Opening the notifications module always starts at the list.
    override fun onOpening() {
        ModuleManager.notificationExpanded = false
        openNotificationGroup = null
    }

    override fun onBroadcast(intent: Intent) {
        updateNotificationWidget(intent.getParcelableArrayListExtra(NotificationService.EXTRA_NOTIFICATION_LIST))
    }

    override fun show(host: HomeModuleHost) {
        ensureNotificationServiceForModule()
        val notificationWidget = ohi.andre.consolelauncher.presentation.ModulePanelPresentation.inflate(context,host.moduleContainer,ohi.andre.consolelauncher.presentation.ModulePanelPresentation.Kind.NOTIFICATIONS)
        host.moduleContainer!!.addView(notificationWidget)
        notificationWidget.setVisibility(View.VISIBLE)
        notificationWidget.setClickable(true)
        notificationWidget.setFocusable(true)
        val notificationBorder =
            notificationWidget.findViewById<View?>(R.id.notification_module_border)
        val notificationLabel =
            notificationWidget.findViewById<View?>(R.id.notification_module_label)
        if (notificationLabel != null) {
            notificationLabel.setOnClickListener(View.OnClickListener { v: View? -> openNotificationShade() })
        }
        val prev = notificationWidget.findViewById<TextView?>(R.id.notification_module_prev)
        if (prev != null) {
            prev.setOnClickListener(View.OnClickListener { v: View? -> previousNotificationPage() })
            prev.setTextColor(moduleNameTextColor())
            prev.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
        }
        val next = notificationWidget.findViewById<TextView?>(R.id.notification_module_next)
        if (next != null) {
            next.setOnClickListener(View.OnClickListener { v: View? -> nextNotificationPage() })
            next.setTextColor(moduleNameTextColor())
            next.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
        }
        val clear = notificationWidget.findViewById<TextView?>(R.id.notification_module_clear)
        if (clear != null) {
            clear.setOnClickListener(View.OnClickListener { v: View? -> dismissCurrentNotification() })
            clear.setTextColor(moduleNameTextColor())
            clear.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
        }
        val settings = notificationWidget.findViewById<ImageButton?>(R.id.notification_module_settings)
        if (settings != null) {
            settings.setOnClickListener(View.OnClickListener { v: View? -> showNotificationSettingsPopup(settings) })
            styleNotificationSettingsButton(settings)
        }
        notificationWidget.findViewById<TextView?>(R.id.notification_module_close)?.let(host::bindCloseButton)
        styleNotificationWidget(notificationWidget)
        LocalBroadcastManager.getInstance(context.getApplicationContext()).sendBroadcast(
            Intent(NotificationService.ACTION_REQUEST_NOTIFICATION_FEED)
        )
    }

    private fun ensureNotificationServiceForModule() {
        if (!Tuils.hasNotificationAccess(context)) {
            return
        }
        try {
            NotificationService.requestListenerRebind(context)
        } catch (e: Exception) {
            Tuils.log(e)
        }
    }

    private fun styleNotificationWidget(notificationWidget: View) {
        decorateWidget(
            notificationWidget,
            R.id.notification_module_border,
            R.id.notification_module_label,
            R.id.notification_module_close,
            notificationWidgetBorderColor(),
            notificationWidgetTextColor(),
            FrameTarget.NOTIFICATIONS
        )
        host.styleCloseButton(notificationWidget.findViewById<TextView?>(R.id.notification_module_close), FrameTarget.NOTIFICATIONS)
        styleNotificationSettingsButton(notificationWidget.findViewById<ImageButton?>(R.id.notification_module_settings))
        styleNotificationPagerButton(notificationWidget.findViewById<View?>(R.id.notification_module_prev))
        styleNotificationPagerButton(notificationWidget.findViewById<View?>(R.id.notification_module_next))
        styleNotificationPagerButton(notificationWidget.findViewById<View?>(R.id.notification_module_clear))
        applyNotificationWidgetSize(notificationWidget)
        renderNotificationRows(notificationWidget)
    }

    private fun updateNotificationWidget(notifications: List<NotificationService.Notification?>?) {
        var previousFocusKey = notificationReplyFocusKey
        if (ModulePromptManager.isNotificationReplyActive(context) && TextUtils.isEmpty(
                previousFocusKey
            )
        ) {
            val selected = currentNotification()
            previousFocusKey = notificationKey(selected)
        }

        // New notifications must not swap the one being read for another.
        val expandedKey = if (ModuleManager.notificationExpanded) notificationKey(currentNotification()) else null
        currentOverlayNotifications.clear()
        if (notifications != null) {
            currentOverlayNotifications.addAll(notifications.filterNotNull())
        }
        onNotificationsChanged(currentOverlayNotifications)
        findNotificationIndexByKey(expandedKey).takeIf { it >= 0 }?.let { currentNotificationIndex = it }
        preserveNotificationReplyFocus(previousFocusKey)
        clampNotificationIndex()

        val notificationWidget = rootView.findViewById<View?>(R.id.notification_module)
        if (notificationWidget != null) {
            val visible = ModuleManager.NOTIFICATIONS == host.activeModuleId
            notificationWidget.setVisibility(if (visible) View.VISIBLE else View.GONE)
            if (visible) {
                styleNotificationWidget(notificationWidget)
            }
        }
        host.refreshContextVisibility()
    }

    private fun renderNotificationRows(notificationWidget: View) {
        val rows = notificationWidget.findViewById<LinearLayout?>(R.id.notification_rows)
        val scrollView = notificationWidget.findViewById<ScrollView?>(R.id.notification_scroll)
        if (rows == null) {
            return
        }

        rows.removeAllViews()
        val widgetTextColor = notificationWidgetTextColor()
        val widgetBorderColor = notificationWidgetBorderColor()

        val maxRows = if (notificationCompactForKeyboard) min(
            1,
            currentOverlayNotifications.size
        ) else currentOverlayNotifications.size
        if (maxRows == 0) {
            val row = buildNotificationRow(context.getString(R.string.surface_no_notifications_273d1), widgetTextColor, widgetBorderColor)
            rows.addView(row)
            updateNotificationPagerButtons(notificationWidget)
            constrainNotificationContentScroll(scrollView)
            if (scrollView != null) {
                scrollView.post(Runnable { scrollView.scrollTo(0, 0) })
            }
            return
        }
        val listing = ModuleManager.NOTIFICATIONS == host.activeModuleId && !ModuleManager.notificationExpanded
        if (ModuleManager.NOTIFICATIONS == host.activeModuleId && ModuleManager.notificationExpanded) {
            clampNotificationIndex()
            val notification = currentOverlayNotifications.get(currentNotificationIndex)
            val row = buildNotificationDetailRow(notification, widgetTextColor, widgetBorderColor)
            wireNotificationOpen(row, notification)
            rows.addView(row)
        } else {
            val group = if (listing) openNotificationGroup else null
            val entries = listedNotificationEntries()
            if (group != null && !notificationCompactForKeyboard) {
                // A row back to the full list, alongside the Back key.
                val back = buildNotificationRow(
                    context.getString(R.string.surface_notification_group_back, entries.first().head.appName ?: entries.first().head.pkg),
                    widgetTextColor,
                    widgetBorderColor
                )
                back.isClickable = true
                back.setOnClickListener { collapseNotification() }
                rows.addView(back)
            }
            for (entry in entries.take(if (notificationCompactForKeyboard) 1 else entries.size)) {
                val notification = entry.head
                val row = buildNotificationRow(
                    when {
                        entry.isGroup -> buildNotificationGroupLine(entry)
                        // The back row already names the app.
                        group != null -> notification.preview.takeUnless { it.isNullOrEmpty() } ?: buildNotificationLine(notification)
                        else -> buildNotificationLine(notification)
                    },
                    widgetTextColor,
                    widgetBorderColor
                )
                if (listing) {
                    // Tapping a group lists its notifications; tapping one expands it, where reply and actions live.
                    row.isClickable = true
                    row.isFocusable = true
                    row.setOnClickListener {
                        if (entry.isGroup) listNotificationGroup(notification.group)
                        else expandNotification(currentOverlayNotifications.indexOf(notification))
                    }
                } else {
                    wireNotificationOpen(row, notification)
                }
                rows.addView(row)
            }
        }
        updateNotificationPagerButtons(notificationWidget)
        constrainNotificationContentScroll(scrollView)

        if (scrollView != null) {
            scrollView.post(Runnable { scrollView.scrollTo(0, 0) }) // fullScroll would take focus from the input
        }
    }

    private fun wireNotificationOpen(
        row: TextView?,
        notification: NotificationService.Notification?
    ) {
        if (row == null || notification == null || notification.pendingIntent == null) {
            return
        }
        row.setClickable(true)
        row.setFocusable(true)
        row.setOnClickListener(View.OnClickListener { v: View? ->
            try {
                Tuils.sendPendingIntent(context, notification.pendingIntent)
            } catch (e: CanceledException) {
                Tuils.sendOutput(ThemeColorResolver.color(Theme.error_text_color), context, e.toString())
            }
        })
    }

    private fun buildNotificationRow(
        text: CharSequence?,
        widgetTextColor: Int,
        widgetBorderColor: Int
    ): TextView {
        val row = ohi.andre.consolelauncher.presentation.ModuleBodyPresentation.notificationText(context,false,notificationCompactForKeyboard)
        TextShadows.apply(row, Theme.module_text_shadow_color)
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.bottomMargin = if (notificationCompactForKeyboard) 0 else Tuils.dpToPx(context, 6)
        row.setLayoutParams(lp)
        row.setTypeface(Tuils.getTypeface(context))
        row.setTextSize(moduleBodyTextSize().toFloat())
        row.setSingleLine(true)
        row.setEllipsize(TextUtils.TruncateAt.END)
        row.setGravity(Gravity.CENTER_VERTICAL)
        val verticalPadding = Tuils.dpToPx(context, if (notificationCompactForKeyboard) 5 else 8)
        row.setPadding(
            Tuils.dpToPx(context, 10),
            verticalPadding,
            Tuils.dpToPx(context, 10),
            verticalPadding
        )
        row.setTextColor(widgetTextColor)
        row.setText(text)
        ohi.andre.consolelauncher.presentation.ModuleContentPresentation.notificationRow(row,
            host.appearance(),widgetTextColor,widgetBorderColor,true)
        return row
    }

    private fun buildNotificationDetailRow(
        notification: NotificationService.Notification,
        widgetTextColor: Int,
        widgetBorderColor: Int
    ): TextView {
        val row = ohi.andre.consolelauncher.presentation.ModuleBodyPresentation.notificationText(context,true,notificationCompactForKeyboard)
        TextShadows.apply(row, Theme.module_text_shadow_color)
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        row.setLayoutParams(lp)
        row.setTypeface(Tuils.getTypeface(context))
        row.setTextSize(moduleBodyTextSize().toFloat())
        row.setTextColor(widgetTextColor)
        row.setText(buildNotificationDetail(notification))
        row.setSingleLine(false)
        row.setEllipsize(null)
        row.setGravity(Gravity.TOP)
        row.setMinLines(if (notificationCompactForKeyboard) 1 else 3)
        row.setPadding(
            Tuils.dpToPx(context, 6),
            Tuils.dpToPx(context, if (notificationCompactForKeyboard) 5 else 10),
            Tuils.dpToPx(context, 6),
            Tuils.dpToPx(context, if (notificationCompactForKeyboard) 5 else 10)
        )
        ohi.andre.consolelauncher.presentation.ModuleContentPresentation.notificationRow(row,
            host.appearance(),widgetTextColor,widgetBorderColor,false)
        return row
    }

    private fun buildNotificationDetail(notification: NotificationService.Notification): CharSequence {
        var appName = notification.appName
        if (TextUtils.isEmpty(appName)) {
            appName = notification.pkg
        }
        val title = cleanNotificationValue(notification.title)
        val body = cleanNotificationValue(notification.body)
        var fallback = cleanNotificationValue(notification.preview)
        if (TextUtils.isEmpty(fallback)) {
            fallback = cleanNotificationValue(notification.text)
        }

        val pages = pageableNotifications()
        val out = StringBuilder()
        out.append(pages.indexOf(notification) + 1)
            .append(" / ")
            .append(pages.size)
            .append("    ")
            .append(if (appName != null) appName else context.getString(R.string.surface_notification_c18f8))
        if (!TextUtils.isEmpty(title)) {
            out.append(Tuils.NEWLINE).append(Tuils.NEWLINE).append(title)
        }
        if (!TextUtils.isEmpty(body)) {
            out.append(Tuils.NEWLINE).append(body)
        } else if (!TextUtils.isEmpty(fallback)) {
            out.append(Tuils.NEWLINE).append(fallback)
        } else {
            out.append(Tuils.NEWLINE).append(Tuils.NEWLINE).append(context.getString(R.string.surface_no_readable_content_5d972))
        }
        if (this.isCurrentNotificationReplyable) {
            out.append(Tuils.NEWLINE).append(context.getString(R.string.surface_reply_available_0b0fd))
        }
        return out.toString()
    }

    private fun cleanNotificationValue(value: String?): String {
        if (value == null) {
            return Tuils.EMPTYSTRING
        }
        var clean = value.trim { it <= ' ' }
        if (clean.length == 0 || "null".equals(clean, ignoreCase = true)) {
            return Tuils.EMPTYSTRING
        }
        if (clean.contains("%pkg") || clean.contains("%t") || clean.contains("--- null")) {
            return Tuils.EMPTYSTRING
        }
        clean = clean.replace("(?i)\\bnull\\b".toRegex(), "").replace("\\s+---\\s*$".toRegex(), "")
            .trim { it <= ' ' }
        return clean
    }

    /** A notification with no title or text keeps only its unfilled format string, which is not for reading. */
    private fun readableNotificationText(notification: NotificationService.Notification): String =
        cleanNotificationValue(notification.text).ifEmpty { context.getString(R.string.surface_no_readable_content_5d972) }

    private fun buildNotificationLine(notification: NotificationService.Notification): CharSequence {
        var appName = notification.appName
        if (TextUtils.isEmpty(appName)) {
            appName = notification.pkg
        }
        var preview = notification.preview
        if (TextUtils.isEmpty(preview)) {
            preview = readableNotificationText(notification)
        }
        return (if (appName != null) appName else context.getString(R.string.surface_notification_c18f8)) + "  " + (if (preview != null) preview else Tuils.EMPTYSTRING)
    }

    private fun buildNotificationGroupLine(entry: NotificationGroups.Entry<NotificationService.Notification>): CharSequence {
        val head = entry.head
        val appName = head.appName.takeUnless { it.isNullOrEmpty() } ?: head.pkg
        // A summary's title is usually the app name again, so its text alone reads better.
        val preview = (if (head.groupSummary) head.body else head.preview).takeUnless { it.isNullOrEmpty() }
            ?: head.preview.takeUnless { it.isNullOrEmpty() }
            ?: entry.children.firstNotNullOfOrNull { child -> child.preview.takeUnless { it.isNullOrEmpty() } }
            ?: readableNotificationText(head)
        return context.getString(R.string.surface_notification_group_row, appName, entry.children.size, preview)
    }

    private fun notificationEntries() =
        NotificationGroups.entries(currentOverlayNotifications, { it.group }, { it.groupSummary })

    /** The rows the list shows: the open group's notifications, or the top level. */
    private fun listedNotificationEntries(): List<NotificationGroups.Entry<NotificationService.Notification>> {
        val group = openNotificationGroup ?: return notificationEntries()
        val children = NotificationGroups.children(currentOverlayNotifications, group, { it.group }, { it.groupSummary })
        if (children.isEmpty()) {
            // The group dropped below two notifications, so the top level shows what is left.
            openNotificationGroup = null
            return notificationEntries()
        }
        return children.map { NotificationGroups.Entry(it, emptyList()) }
    }

    /** What Prev and Next step through from an expanded notification. */
    private fun pageableNotifications() = NotificationGroups.pageable(listedNotificationEntries())

    fun setNotificationWidgetCompact(compact: Boolean) {
        if (notificationCompactForKeyboard == compact) {
            return
        }

        notificationCompactForKeyboard = compact
        val notificationWidget = rootView.findViewById<View?>(R.id.notification_module)
        if (notificationWidget != null && notificationWidget.getVisibility() == View.VISIBLE) {
            applyNotificationWidgetSize(notificationWidget)
            renderNotificationRows(notificationWidget)
        }
    }

    private fun applyNotificationWidgetSize(notificationWidget: View) {
        ohi.andre.consolelauncher.presentation.ModulePanelPresentation.notificationSize(notificationWidget,notificationCompactForKeyboard)
    }

    private fun calculateNotificationContentMaxHeight(): Int {
        return ohi.andre.consolelauncher.presentation.ModulePanelPresentation.notificationMaxHeight(context,
            rootView.height,notificationCompactForKeyboard)
    }

    private fun constrainNotificationContentScroll(scrollView: ScrollView?) {
        if(scrollView==null) return
        ohi.andre.consolelauncher.presentation.ModulePanelPresentation.limitNotifications(scrollView,calculateNotificationContentMaxHeight())
    }

    private fun styleNotificationPagerButton(button: View?) {
        if (button !is TextView) return
        ohi.andre.consolelauncher.presentation.ModulePanelPresentation.notificationPager(button,
            host.appearance())
    }

    private fun styleNotificationSettingsButton(button: ImageButton?) {
        if (button == null) return
        ohi.andre.consolelauncher.presentation.ModulePanelPresentation.notificationSettings(button,
            host.appearance())
    }

    private fun clampNotificationIndex() {
        if (currentOverlayNotifications.isEmpty()) {
            currentNotificationIndex = 0
            return
        }
        if (currentNotificationIndex < 0) {
            currentNotificationIndex = 0
        } else if (currentNotificationIndex >= currentOverlayNotifications.size) {
            currentNotificationIndex = currentOverlayNotifications.size - 1
        }
    }

    private fun preserveNotificationReplyFocus(focusKey: String?) {
        if (!ModulePromptManager.isNotificationReplyActive(context)) {
            notificationReplyFocusKey = null
            return
        }

        if (!TextUtils.isEmpty(focusKey)) {
            val index = findNotificationIndexByKey(focusKey)
            if (index >= 0) {
                currentNotificationIndex = index
                notificationReplyFocusKey = focusKey
                return
            }
        }

        val pkg = ModulePromptManager.getNotificationReplyPackage(context)
        val packageIndex = findNotificationIndexByPackage(pkg)
        if (packageIndex >= 0) {
            currentNotificationIndex = packageIndex
            notificationReplyFocusKey =
                notificationKey(currentOverlayNotifications.get(packageIndex))
        }
    }

    private fun findNotificationIndexByKey(key: String?): Int {
        if (TextUtils.isEmpty(key)) return -1
        for (i in currentOverlayNotifications.indices) {
            if (TextUtils.equals(key, notificationKey(currentOverlayNotifications.get(i)))) {
                return i
            }
        }
        return -1
    }

    private fun findNotificationIndexByPackage(pkg: String?): Int {
        if (TextUtils.isEmpty(pkg)) return -1
        for (i in currentOverlayNotifications.indices) {
            if (TextUtils.equals(pkg, currentOverlayNotifications.get(i).pkg)) {
                return i
            }
        }
        return -1
    }

    private fun notificationKey(notification: NotificationService.Notification?): String? {
        if (notification == null) return null
        if (!TextUtils.isEmpty(notification.key)) return notification.key
        return (safeNotificationPart(notification.pkg)
                + "|"
                + safeNotificationPart(notification.title)
                + "|"
                + safeNotificationPart(notification.body)
                + "|"
                + safeNotificationPart(notification.preview))
    }

    private fun safeNotificationPart(value: String?): String {
        return if (value == null) "" else value.trim { it <= ' ' }
    }

    private fun updateNotificationPagerButtons(notificationWidget: View?) {
        if (notificationWidget == null) return
        val replyActive = ModulePromptManager.isNotificationReplyActive(context)
        val expanded = ModuleManager.NOTIFICATIONS == host.activeModuleId && ModuleManager.notificationExpanded
        val enabled = expanded && pageableNotifications().size > 1 && !replyActive
        val clearEnabled = expanded && !replyActive && !TextUtils.isEmpty(currentNotification()?.key)
        val prev = notificationWidget.findViewById<TextView?>(R.id.notification_module_prev)
        val next = notificationWidget.findViewById<TextView?>(R.id.notification_module_next)
        val clear = notificationWidget.findViewById<TextView?>(R.id.notification_module_clear)
        if (prev != null) {
            prev.setVisibility(if (expanded) View.VISIBLE else View.GONE)
            prev.setEnabled(enabled)
            prev.setAlpha(if (enabled) 1f else 0.35f)
        }
        if (next != null) {
            next.setVisibility(if (expanded) View.VISIBLE else View.GONE)
            next.setEnabled(enabled)
            next.setAlpha(if (enabled) 1f else 0.35f)
        }
        if (clear != null) {
            clear.setVisibility(if (expanded) View.VISIBLE else View.GONE)
            clear.setEnabled(clearEnabled)
            clear.setAlpha(if (clearEnabled) 1f else 0.35f)
        }
    }

    fun expandNotification(index: Int) {
        if (index !in currentOverlayNotifications.indices) return
        currentNotificationIndex = index
        ModuleManager.notificationExpanded = true
        refreshNotificationModuleView()
    }

    private fun listNotificationGroup(group: String?) {
        if (group == null) return
        openNotificationGroup = group
        refreshNotificationModuleView()
    }

    /**
     * Back from an expanded notification returns to the list it came from, and back from a group's
     * list returns to the top level; false when there is nothing to collapse.
     */
    fun collapseNotification(): Boolean {
        if (ModuleManager.NOTIFICATIONS != host.activeModuleId) return false
        if (ModuleManager.notificationExpanded) {
            if (ModulePromptManager.isNotificationReplyActive(context)) return false
            ModuleManager.notificationExpanded = false
        } else if (openNotificationGroup != null) {
            openNotificationGroup = null
        } else {
            return false
        }
        refreshNotificationModuleView()
        return true
    }

    private fun requireExpandedNotification(): Boolean {
        if (ModuleManager.notificationExpanded) return true
        Tuils.sendOutput(context, context.getString(R.string.surface_open_a_notification_first))
        return false
    }

    fun nextNotificationPage() = stepNotificationPage(1)

    fun previousNotificationPage() = stepNotificationPage(-1)

    private fun stepNotificationPage(delta: Int) {
        if (!requireExpandedNotification()) return
        if (ModulePromptManager.isNotificationReplyActive(context)) return
        val pages = pageableNotifications()
        if (pages.isEmpty()) return
        val at = pages.indexOf(currentNotification()).coerceAtLeast(0)
        currentNotificationIndex = currentOverlayNotifications.indexOf(pages[(at + delta + pages.size) % pages.size])
        refreshNotificationModuleView()
    }

    fun startCurrentNotificationReply() {
        if (!requireExpandedNotification()) return
        val notification = currentNotification()
        if (notification == null) {
            Tuils.sendOutput(context, context.getString(R.string.surface_no_notification_selected_1f0ee))
            return
        }
        if (!this.isCurrentNotificationReplyable) {
            Tuils.sendOutput(
                context,
                context.getString(R.string.surface_selected_notification_is_not_replyable_bind_the_app_with_r_0cf3e)
            )
            return
        }
        notificationReplyFocusKey = notificationKey(notification)
        ModulePromptManager.startNotificationReply(
            context,
            notification.pkg ?: return,
            notification.appName,
            notification.key
        )
        host.refreshSuggestions()
    }

    fun dismissCurrentNotification() {
        if (ModulePromptManager.isNotificationReplyActive(context)) return
        if (!requireExpandedNotification()) return
        val notification = currentNotification()
        if (notification == null) {
            Tuils.sendOutput(context, context.getString(R.string.surface_no_notification_selected_1f0ee))
            return
        }
        val key = notification.key
        if (TextUtils.isEmpty(key)) {
            Tuils.sendOutput(context, context.getString(R.string.surface_selected_notification_cannot_be_cleared_20da0))
            return
        }

        NotificationService.requestDismiss(context, key)
        // Move on to the next notification in the same list, or back to the list after its last one.
        val pages = pageableNotifications()
        val at = pages.indexOf(notification)
        val following = pages.getOrNull(at + 1) ?: pages.getOrNull(at - 1)
        currentOverlayNotifications.remove(notification)
        if (following != null) currentNotificationIndex = currentOverlayNotifications.indexOf(following)
        clampNotificationIndex()
        if (following == null) ModuleManager.notificationExpanded = false
        refreshNotificationModuleView()
    }

    private fun showNotificationSettingsPopup(anchor: View) {
        val notification = currentNotification()
        if (notification == null) {
            Tuils.sendOutput(context, context.getString(R.string.surface_no_notification_selected_1f0ee))
            return
        }

        val menu = PopupMenu(anchor.context, anchor)
        menu.menu.add(R.string.exclude_notification)
        menu.setOnMenuItemClickListener {
                excludeNotificationFromPanel(notification)
            true
        }
        menu.show()
    }

    private fun excludeNotificationFromPanel(notification: NotificationService.Notification) {
        if (TextUtils.isEmpty(notification.text)) {
            Tuils.sendOutput(context, context.getString(R.string.surface_selected_notification_cannot_be_excluded_5df6b))
            return
        }

        NotificationManager.addLiteralFilter(notification.text)
        NotificationService.requestReload(context)
        currentOverlayNotifications.remove(notification)
        clampNotificationIndex()
        refreshNotificationModuleView()
        Tuils.sendOutput(context, context.getString(R.string.surface_notification_excluded_from_launcher_panel_18a97))
    }

    private fun currentNotification(): NotificationService.Notification? {
        if (currentOverlayNotifications.isEmpty()) return null
        clampNotificationIndex()
        return currentOverlayNotifications.get(currentNotificationIndex)
    }

    private fun refreshNotificationModuleView() {
        if (ModuleManager.NOTIFICATIONS == host.activeModuleId) {
            host.presentModule(id)
        }
    }

    fun openNotificationShade(): Boolean {
        try {
            @SuppressLint("WrongConstant") val sbservice = context.getSystemService("statusbar")
            val statusbarManager = Class.forName("android.app.StatusBarManager")
            val expand = statusbarManager.getMethod("expandNotificationsPanel")
            expand.invoke(sbservice)
            return true
        } catch (e: Exception) {
            Tuils.sendOutput(ThemeColorResolver.color(Theme.error_text_color), context, e.toString())
            return false
        }
    }

    private val isCurrentNotificationReplyable: Boolean
        get() {
            val notification = currentNotification()
            val replyManager = ReplyManager.getInstance()
            return notification != null && replyManager != null &&
                (replyManager.canReplyToNotification(notification.key) || replyManager.canReplyTo(notification.pkg))
        }
}
