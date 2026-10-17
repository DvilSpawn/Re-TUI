package ohi.andre.consolelauncher.managers.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.managers.AppsManager
import ohi.andre.consolelauncher.managers.AppsManager.LaunchInfo
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.dashedBorders
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.moduleCornerRadius
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.terminalBorderColor
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.terminalHeaderTabBackground
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.terminalWindowBackground
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.tuils.TerminalBorderRuntime
import ohi.andre.consolelauncher.tuils.FrameTarget
import ohi.andre.consolelauncher.tuils.Tuils
import java.util.Collections
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class AppDrawerPaneManager(
    private val context: Context,
    private val rootView: ViewGroup,
    private val mainPackProvider: () -> MainPack?,
    private val closeKeyboard: () -> Unit,
    private val onSurfaceOpen: () -> Unit,
    private val onSurfaceClose: () -> Unit
) {
    private val drawerRoot: View? = rootView.findViewById(R.id.apps_drawer_root)
    private val drawerContainer: View? = rootView.findViewById(R.id.apps_drawer_container)
    private val drawerContainerBaseMargins = IntArray(4)
    private val appsList: ListView? = rootView.findViewById(R.id.apps_list)
    private val groupTabs: LinearLayout? = rootView.findViewById(R.id.apps_group_tabs)
    private val alphaTabs: LinearLayout? = rootView.findViewById(R.id.apps_alpha_tabs)
    private val header: TextView? = rootView.findViewById(R.id.apps_drawer_header)
    private val footer: TextView? = rootView.findViewById(R.id.apps_drawer_footer)
    private var adapter: AppsDrawerAdapter? = null
    private val entries: MutableList<AppDrawerEntry> = ArrayList()
    private val alphaPositions = LinkedHashMap<String?, Int?>()
    private val alphaViews = LinkedHashMap<String?, TextView?>()
    private var selectedGroup: String? = null
    private var selectedPrivate = false
    private var selectedAlpha: String? = null
    private var surfaceOpen = false

    init {
        OverlayLayoutManager.captureBaseMargins(drawerContainer, drawerContainerBaseMargins)
        configureInputAnchor()
        drawerRoot?.setOnClickListener { hide() }
    }

    val isOpen: Boolean
        get() = drawerRoot != null && drawerRoot.visibility == View.VISIBLE

    fun hide() {
        drawerRoot?.visibility = View.GONE
        closeSurface()
    }

    fun show() {
        if (drawerRoot == null || appsList == null || header == null || footer == null) {
            return
        }

        closeKeyboard()
        val mainPack = mainPackProvider() ?: return
        openSurface()
        val appsManager = mainPack.appsManager
        val drawerColor = ThemeColorResolver.color(Theme.app_drawer_panel_text_color)
        val borderColor = ThemeColorResolver.color(Theme.app_drawer_panel_border_color)
        val backgroundColor = ThemeColorResolver.color(Theme.app_drawer_panel_background_color)
        val headerBackgroundColor = ThemeColorResolver.color(Theme.app_drawer_header_background_color)

        drawerRoot.setBackgroundColor(Color.TRANSPARENT)
        header.setTextColor(ThemeColorResolver.color(Theme.app_drawer_header_text_color))
        footer.setTextColor(ThemeColorResolver.color(Theme.app_drawer_header_text_color))
        header.typeface = Tuils.getTypeface(context)
        header.setTypeface(header.typeface, Typeface.BOLD)
        footer.typeface = Tuils.getTypeface(context)

        val drawerPanel = drawerContainer ?: drawerRoot.findViewById(R.id.apps_drawer_container)
        drawerPanel?.background = TerminalBorderRuntime.panelDrawable(
            context,
            backgroundColor,
            borderColor,
            1.5f,
            moduleCornerRadius(),
            dashedBorders(),
            target = FrameTarget.APP_DRAWER
        )
        header.background = TerminalBorderRuntime.tabDrawable(context, headerBackgroundColor, FrameTarget.APP_DRAWER)
        footer.background = TerminalBorderRuntime.tabDrawable(context, headerBackgroundColor, FrameTarget.APP_DRAWER)
        TerminalBorderRuntime.bind(drawerPanel, header, footer)

        if (adapter == null) {
            adapter = AppsDrawerAdapter(context, drawerColor, backgroundColor)
            appsList.adapter = adapter
            appsList.onItemClickListener =
                AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
                    val entry = entries[position]
                    if (entry is AppEntry) {
                        if (appsManager.launch(context, entry.app)) {
                            hide()
                        } else {
                            val message = if (entry.app.isPrivateProfile && appsManager.isPrivateSpaceLocked()) {
                                R.string.output_private_space_not_ready
                            } else {
                                R.string.output_app_launch_failed
                            }
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            show()
                        }
                    } else if (entry is PrivateActionEntry) {
                        when (entry.action) {
                            PrivateAction.LOCK -> appsManager.requestPrivateSpaceQuietMode(true)
                            PrivateAction.HIDE -> appsManager.setPrivateSpaceEntrypointVisible(false)
                        }
                        show()
                    }
                }
            appsList.setOnScrollListener(object : AbsListView.OnScrollListener {
                override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) {
                }

                override fun onScroll(
                    view: AbsListView?,
                    firstVisibleItem: Int,
                    visibleItemCount: Int,
                    totalItemCount: Int
                ) {
                    if (isAtScrollableEnd(firstVisibleItem, visibleItemCount, totalItemCount)) {
                        updateSelectedAlpha(lastAlpha())
                    } else {
                        updateSelectedAlphaFromPosition(firstVisibleItem)
                    }
                }
            })
        } else {
            adapter!!.setColors(drawerColor, backgroundColor)
        }

        buildGroupTabs(appsManager, drawerColor, borderColor, backgroundColor)
        rebuildContents(appsManager, drawerColor, borderColor, backgroundColor)
        drawerRoot.visibility = View.VISIBLE
    }

    fun applyDisplayMargins(left: Int, top: Int, right: Int, bottom: Int) {
        OverlayLayoutManager.applyMarginsWithBase(
            drawerContainer,
            drawerContainerBaseMargins,
            left,
            top,
            right,
            bottom
        )
    }

    private fun configureInputAnchor() {
        val dummyAnchor = rootView.findViewById<View?>(R.id.apps_drawer_dummy_input_anchor) ?: return
        rootView.post {
            val params = dummyAnchor.layoutParams as? RelativeLayout.LayoutParams ?: return@post
            params.height = 0
            params.removeRule(RelativeLayout.ALIGN_PARENT_TOP)
            params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            dummyAnchor.layoutParams = params
        }
    }

    private fun openSurface() {
        if (surfaceOpen) {
            return
        }
        surfaceOpen = true
        onSurfaceOpen()
    }

    private fun closeSurface() {
        if (!surfaceOpen) {
            return
        }
        surfaceOpen = false
        onSurfaceClose()
    }

    private fun buildGroupTabs(
        appsManager: AppsManager,
        drawerColor: Int,
        borderColor: Int,
        backgroundColor: Int
    ) {
        if (groupTabs == null) {
            return
        }

        groupTabs.removeAllViews()
        if (selectedPrivate && !appsManager.isPrivateSpaceTabVisible()) {
            selectedPrivate = false
            selectedGroup = null
        }
        addGroupTab(context.getString(R.string.manager_appdrawerpanemanager_all_6b428), null, drawerColor, borderColor, backgroundColor, true)

        val groups: MutableList<AppsManager.Group> = ArrayList(appsManager.groups)
        Collections.sort(
            groups,
            Comparator { a: AppsManager.Group, b: AppsManager.Group ->
                Tuils.alphabeticCompare(a.name(), b.name())
            }
        )
        for (group in groups) {
            val groupName = group.name()
            val tabLabel = if (groupName.length <= 3) {
                groupName.uppercase(Locale.getDefault())
            } else {
                groupName.substring(0, 3).uppercase(Locale.getDefault())
            }
            addGroupTab(tabLabel, groupName, drawerColor, borderColor, backgroundColor, false)
        }
        if (appsManager.isPrivateSpaceTabVisible()) {
            addPrivateTab(drawerColor, borderColor, backgroundColor)
        }
    }

    private fun addPrivateTab(drawerColor: Int, borderColor: Int, backgroundColor: Int) {
        val tabs = groupTabs ?: return
        val tab = TextView(context)
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            Tuils.dpToPx(context, 34)
        )
        params.bottomMargin = Tuils.dpToPx(context, 4)
        tab.layoutParams = params
        tab.gravity = Gravity.CENTER
        tab.setPadding(Tuils.dpToPx(context, 2), 0, Tuils.dpToPx(context, 2), 0)
        tab.text = context.getString(R.string.manager_appdrawerpanemanager_private_7d5e1)
        tab.maxLines = 1
        tab.ellipsize = TextUtils.TruncateAt.END
        tab.textSize = 9.5f
        tab.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
        tab.minWidth = 0
        tab.minimumWidth = 0
        val selected = selectedPrivate
        tab.background = TerminalBorderRuntime.panelDrawable(
            context,
            if (selected) ThemeColorResolver.color(Theme.app_drawer_group_selected_background_color)
            else ThemeColorResolver.color(Theme.app_drawer_group_background_color),
            if (selected) ThemeColorResolver.color(Theme.app_drawer_group_selected_border_color)
            else ThemeColorResolver.color(Theme.app_drawer_group_border_color),
            1.5f,
            2,
            dashedBorders(),
            target = FrameTarget.APP_DRAWER
        )
        tab.setTextColor(
            if (selected) ThemeColorResolver.color(Theme.app_drawer_group_selected_text_color)
            else drawerColor
        )
        tab.setOnClickListener {
            selectedPrivate = true
            selectedGroup = null
            buildGroupTabs(
                mainPackProvider()?.appsManager ?: return@setOnClickListener,
                drawerColor,
                borderColor,
                backgroundColor
            )
            rebuildContents(
                mainPackProvider()?.appsManager ?: return@setOnClickListener,
                drawerColor,
                borderColor,
                backgroundColor
            )
        }
        tabs.addView(tab)
    }

    private fun addGroupTab(
        label: String?,
        groupName: String?,
        drawerColor: Int,
        borderColor: Int,
        backgroundColor: Int,
        isAll: Boolean
    ) {
        val tabs = groupTabs ?: return
        val tab = TextView(context)
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            Tuils.dpToPx(context, 34)
        )
        params.bottomMargin = Tuils.dpToPx(context, 4)
        tab.layoutParams = params
        tab.gravity = Gravity.CENTER
        tab.setPadding(Tuils.dpToPx(context, 2), 0, Tuils.dpToPx(context, 2), 0)
        tab.text = label
        tab.maxLines = 1
        tab.ellipsize = TextUtils.TruncateAt.END
        tab.textSize = 9.5f
        tab.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
        tab.minWidth = 0
        tab.minimumWidth = 0

        val selected = (isAll && selectedGroup == null && !selectedPrivate) ||
                (groupName != null && groupName == selectedGroup && !selectedPrivate)
        val selectedColor = ThemeColorResolver.color(Theme.app_drawer_group_selected_background_color)
        var foregroundColor = ThemeColorResolver.color(Theme.app_drawer_group_text_color)
        var tabBackgroundColor = ThemeColorResolver.color(Theme.app_drawer_group_background_color)
        if (groupName != null) {
            val group = findGroup(groupName)
            if (group != null) {
                if (group.foreColor != Int.MAX_VALUE) {
                    foregroundColor = group.foreColor
                }
                if (group.bgColor != Int.MAX_VALUE) {
                    tabBackgroundColor = group.bgColor
                }
            }
        }

        tab.background = TerminalBorderRuntime.panelDrawable(
            context,
            if (selected) selectedColor else tabBackgroundColor,
            if (selected) ThemeColorResolver.color(Theme.app_drawer_group_selected_border_color)
            else ThemeColorResolver.color(Theme.app_drawer_group_border_color),
            1.5f,
            2,
            dashedBorders(),
            target = FrameTarget.APP_DRAWER
        )
        tab.setTextColor(
            if (selected) ThemeColorResolver.color(Theme.app_drawer_group_selected_text_color)
            else foregroundColor
        )
        tab.alpha = 1f
        tab.setOnClickListener {
            selectedPrivate = false
            selectedGroup = groupName
            val appsManager = mainPackProvider()?.appsManager ?: return@setOnClickListener
            buildGroupTabs(appsManager, drawerColor, borderColor, backgroundColor)
            rebuildContents(appsManager, drawerColor, borderColor, backgroundColor)
        }

        tabs.addView(tab)
    }

    private fun findGroup(name: String?): AppsManager.Group? {
        val appsManager = mainPackProvider()?.appsManager ?: return null
        for (group in appsManager.groups) {
            if (group.name() == name) {
                return group
            }
        }
        return null
    }

    private fun rebuildContents(
        appsManager: AppsManager,
        drawerColor: Int,
        borderColor: Int,
        backgroundColor: Int
    ) {
        val visibleApps = if (selectedPrivate) appsManager.privateApps() else getAppsForDrawer(appsManager)
        entries.clear()
        alphaPositions.clear()
        selectedAlpha = null

        if (selectedPrivate) {
            entries.add(PrivateActionEntry(PrivateAction.LOCK))
            entries.add(PrivateActionEntry(PrivateAction.HIDE))
        }

        var currentSection: String? = null
        for (app in visibleApps) {
            val section = sectionForApp(app)
            if (section != currentSection) {
                alphaPositions[section] = entries.size
                entries.add(SectionEntry(section))
                currentSection = section
            }
            entries.add(AppEntry(app))
        }

        adapter?.notifyDataSetChanged()
        buildAlphabetTabs(drawerColor, borderColor, backgroundColor)

        val scope = if (selectedGroup == null) "all" else selectedGroup
        header?.text = if (selectedPrivate) {
            context.getString(R.string.manager_appdrawerpanemanager_private_space_9f6b2, visibleApps.size)
        } else {
            context.getString(R.string.manager_appdrawerpanemanager_applications_a46d2, visibleApps.size, scope)
        }
        footer?.text = context.getString(R.string.manager_appdrawerpanemanager_groups_tabs_ef814, appsManager.groups.size, alphaPositions.size)
        appsList?.setSelection(0)
        updateSelectedAlphaFromPosition(0)
    }

    private fun getAppsForDrawer(appsManager: AppsManager): MutableList<LaunchInfo> {
        val apps: MutableList<LaunchInfo> = ArrayList()
        val shownApps: List<LaunchInfo> = appsManager.shownApps()

        if (selectedGroup == null) {
            apps.addAll(shownApps)
        } else {
            val group = findGroup(selectedGroup)
            if (group != null) {
                val members = group.members()
                for (member in members) {
                    if (member is LaunchInfo && shownApps.contains(member)) {
                        apps.add(member)
                    }
                }
            }
        }

        Collections.sort(
            apps,
            Comparator { a: LaunchInfo, b: LaunchInfo ->
                Tuils.alphabeticCompare(
                    a.publicLabel ?: Tuils.EMPTYSTRING,
                    b.publicLabel ?: Tuils.EMPTYSTRING
                )
            }
        )
        return apps
    }

    private fun sectionForApp(app: LaunchInfo?): String {
        val publicLabel = app?.publicLabel
        if (publicLabel.isNullOrEmpty()) {
            return "#"
        }

        val first = publicLabel[0].uppercaseChar()
        if (first < 'A' || first > 'Z') {
            return "#"
        }
        return first.toString()
    }

    private fun buildAlphabetTabs(drawerColor: Int, borderColor: Int, backgroundColor: Int) {
        if (alphaTabs == null) {
            return
        }

        alphaTabs.removeAllViews()
        alphaViews.clear()
        for (entry in alphaPositions.entries) {
            val tab = TextView(context)
            val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            params.bottomMargin = Tuils.dpToPx(context, 3)
            tab.layoutParams = params
            tab.gravity = Gravity.CENTER
            tab.minHeight = 0
            tab.minimumHeight = 0
            tab.setPadding(0, 0, 0, 0)
            tab.text = entry.key
            tab.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
            tab.textSize = 9.5f
            styleAlphaTab(tab, entry.key, drawerColor, borderColor, backgroundColor)
            tab.setOnClickListener {
                appsList?.setSelection(entry.value ?: 0)
                updateSelectedAlpha(entry.key)
            }
            alphaViews[entry.key] = tab
            alphaTabs.addView(tab)
        }
    }

    private fun styleAlphaTab(
        tab: TextView,
        letter: String?,
        drawerColor: Int,
        borderColor: Int,
        backgroundColor: Int
    ) {
        val selected = letter != null && letter == selectedAlpha
        tab.setTextColor(
            ThemeColorResolver.color(
            if (selected) Theme.app_drawer_alphabet_selected_text_color else Theme.app_drawer_alphabet_text_color
            )
        )
        val selectedColor = ThemeColorResolver.color(Theme.app_drawer_alphabet_selected_background_color)

        tab.background = TerminalBorderRuntime.panelDrawable(
            context,
            if (selected) selectedColor else ThemeColorResolver.color(Theme.app_drawer_alphabet_background_color),
            ThemeColorResolver.color(
                if (selected) Theme.app_drawer_alphabet_selected_border_color else Theme.app_drawer_alphabet_border_color
            ),
            1.2f,
            2,
            dashedBorders(),
            target = FrameTarget.APP_DRAWER
        )
    }

    private fun updateSelectedAlphaFromPosition(position: Int) {
        if (position < 0 || position >= entries.size) {
            return
        }

        for (i in position..<entries.size) {
            val entry = entries[i]
            if (entry is SectionEntry) {
                updateSelectedAlpha(entry.title)
                return
            }
        }
    }

    private fun lastAlpha(): String? = alphaPositions.keys.lastOrNull()

    private fun updateSelectedAlpha(letter: String?) {
        if (letter == null || letter == selectedAlpha) {
            return
        }

        selectedAlpha = letter
        val drawerColor = ThemeColorResolver.color(Theme.app_drawer_panel_text_color)
        val borderColor = ThemeColorResolver.color(Theme.app_drawer_panel_border_color)
        val backgroundColor = ThemeColorResolver.color(Theme.app_drawer_panel_background_color)
        for (entry in alphaViews.entries) {
            val tab = entry.value ?: continue
            styleAlphaTab(tab, entry.key, drawerColor, borderColor, backgroundColor)
        }
    }

    private abstract class AppDrawerEntry {
        abstract val viewType: Int
    }

    private class SectionEntry(val title: String?) : AppDrawerEntry() {
        override val viewType: Int = 0
    }

    private class AppEntry(val app: LaunchInfo) : AppDrawerEntry() {
        override val viewType: Int = 1
    }

    private enum class PrivateAction {
        LOCK,
        HIDE
    }

    private class PrivateActionEntry(val action: PrivateAction) : AppDrawerEntry() {
        override val viewType: Int = 2
    }

    private inner class AppsDrawerAdapter(
        private val context: Context,
        private var color: Int,
        private var backgroundColor: Int
    ) : BaseAdapter() {
        fun setColors(color: Int, backgroundColor: Int) {
            this.color = color
            this.backgroundColor = backgroundColor
        }

        override fun getCount(): Int = entries.size

        override fun getItem(position: Int): Any? = entries[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getViewTypeCount(): Int = 3

        override fun getItemViewType(position: Int): Int = entries[position].viewType

        override fun isEnabled(position: Int): Boolean = getItemViewType(position) != 0

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val entry = entries[position]
            val textView = if (convertView is TextView) convertView else TextView(context)

            if (entry is SectionEntry) {
                textView.setPadding(0, Tuils.dpToPx(context, 8), 0, Tuils.dpToPx(context, 6))
                textView.setTextColor(color)
                textView.textSize = 12f
                textView.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
                textView.setBackgroundColor(ThemeColorResolver.color(Theme.app_drawer_row_background_color))
                textView.text = "[${entry.title}]"
                return textView
            }

            if (entry is PrivateActionEntry) {
                textView.setPadding(
                    Tuils.dpToPx(context, 6),
                    Tuils.dpToPx(context, 10),
                    Tuils.dpToPx(context, 6),
                    Tuils.dpToPx(context, 10)
                )
                textView.setTextColor(ThemeColorResolver.color(Theme.app_drawer_group_selected_text_color))
                textView.textSize = 13f
                textView.typeface = Tuils.getTypeface(context)
                textView.setBackgroundColor(ThemeColorResolver.color(Theme.app_drawer_group_selected_background_color))
                textView.text = when (entry.action) {
                    PrivateAction.LOCK -> context.getString(R.string.manager_appdrawerpanemanager_private_lock_6b0f1)
                    PrivateAction.HIDE -> context.getString(R.string.manager_appdrawerpanemanager_private_hide_4d1c2)
                }
                return textView
            }

            val app: LaunchInfo = (entry as AppEntry).app
            textView.setPadding(
                Tuils.dpToPx(context, 6),
                Tuils.dpToPx(context, 12),
                Tuils.dpToPx(context, 6),
                Tuils.dpToPx(context, 12)
            )
            textView.setTextColor(ThemeColorResolver.color(Theme.app_drawer_row_text_color))
            textView.textSize = 16f
            textView.typeface = Tuils.getTypeface(context)
            textView.setBackgroundColor(ThemeColorResolver.color(Theme.app_drawer_row_background_color))
            textView.text = app.publicLabel
            return textView
        }
    }

    companion object {
        internal fun isAtScrollableEnd(
            firstVisibleItem: Int,
            visibleItemCount: Int,
            totalItemCount: Int
        ): Boolean =
            firstVisibleItem > 0 &&
                    visibleItemCount > 0 &&
                    totalItemCount > 0 &&
                    firstVisibleItem + visibleItemCount >= totalItemCount
    }
}
