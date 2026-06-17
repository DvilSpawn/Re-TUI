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
import androidx.core.graphics.ColorUtils
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.managers.AppsManager
import ohi.andre.consolelauncher.managers.AppsManager.LaunchInfo
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.dashedBorders
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.moduleCornerRadius
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.terminalBorderColor
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.terminalHeaderTabBackground
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.terminalWindowBackground
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.tuils.TerminalBorderRuntime
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
        val drawerColor = XMLPrefsManager.getColor(Theme.apps_drawer_text_color)
        val borderColor = terminalBorderColor()
        val backgroundColor = terminalWindowBackground()
        val headerBackgroundColor = terminalHeaderTabBackground()

        drawerRoot.setBackgroundColor(Color.TRANSPARENT)
        header.setTextColor(drawerColor)
        footer.setTextColor(drawerColor)
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
            dashedBorders()
        )
        header.background = TerminalBorderRuntime.tabDrawable(context, headerBackgroundColor)
        footer.background = TerminalBorderRuntime.tabDrawable(context, headerBackgroundColor)
        TerminalBorderRuntime.bind(drawerPanel, header, footer)

        if (adapter == null) {
            adapter = AppsDrawerAdapter(context, drawerColor, backgroundColor)
            appsList.adapter = adapter
            appsList.onItemClickListener =
                AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
                    val entry = entries[position]
                    if (entry is AppEntry) {
                        appsManager.launch(context, entry.app)
                        hide()
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
                    updateSelectedAlphaFromPosition(firstVisibleItem)
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
        addGroupTab("ALL", null, drawerColor, borderColor, backgroundColor, true)

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

        val selected = (isAll && selectedGroup == null) || (groupName != null && groupName == selectedGroup)
        val selectedColor = getSelectionColor(drawerColor, backgroundColor)
        var foregroundColor = drawerColor
        var tabBackgroundColor = backgroundColor
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
            borderColor,
            1.5f,
            2,
            dashedBorders()
        )
        tab.setTextColor(if (selected) backgroundColor else foregroundColor)
        tab.alpha = 1f
        tab.setOnClickListener {
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
        val visibleApps = getAppsForDrawer(appsManager)
        entries.clear()
        alphaPositions.clear()
        selectedAlpha = null

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
        header?.text = "Applications/ [${visibleApps.size}] <$scope>"
        footer?.text = "groups ${appsManager.groups.size} | tabs ${alphaPositions.size}"
        appsList?.setSelection(0)
        updateSelectedAlphaFromPosition(0)
    }

    private fun getAppsForDrawer(appsManager: AppsManager): MutableList<LaunchInfo> {
        val apps: MutableList<LaunchInfo> = ArrayList()
        val shownApps: List<LaunchInfo> = appsManager.shownApps() ?: emptyList()

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
        tab.setTextColor(if (selected) backgroundColor else drawerColor)
        val selectedColor = getSelectionColor(drawerColor, backgroundColor)

        tab.background = TerminalBorderRuntime.panelDrawable(
            context,
            if (selected) selectedColor else backgroundColor,
            borderColor,
            1.2f,
            2,
            dashedBorders()
        )
    }

    private fun getSelectionColor(drawerColor: Int, backgroundColor: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(drawerColor, hsv)
        hsv[1] = max(0f, hsv[1] * 0.55f)
        hsv[2] = min(1f, 0.88f + (0.12f * hsv[2]))
        val lightBase = Color.HSVToColor(hsv)
        return ColorUtils.blendARGB(lightBase, backgroundColor, 0.18f)
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

    private fun updateSelectedAlpha(letter: String?) {
        if (letter == null || letter == selectedAlpha) {
            return
        }

        selectedAlpha = letter
        val drawerColor = XMLPrefsManager.getColor(Theme.apps_drawer_text_color)
        val borderColor = terminalBorderColor()
        val backgroundColor = terminalWindowBackground()
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

        override fun getViewTypeCount(): Int = 2

        override fun getItemViewType(position: Int): Int = entries[position].viewType

        override fun isEnabled(position: Int): Boolean = getItemViewType(position) == 1

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val entry = entries[position]
            val textView = if (convertView is TextView) convertView else TextView(context)

            if (entry is SectionEntry) {
                textView.setPadding(0, Tuils.dpToPx(context, 8), 0, Tuils.dpToPx(context, 6))
                textView.setTextColor(color)
                textView.textSize = 12f
                textView.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
                textView.setBackgroundColor(Color.TRANSPARENT)
                textView.text = "[${entry.title}]"
                return textView
            }

            val app: LaunchInfo = (entry as AppEntry).app
            textView.setPadding(
                Tuils.dpToPx(context, 6),
                Tuils.dpToPx(context, 12),
                Tuils.dpToPx(context, 6),
                Tuils.dpToPx(context, 12)
            )
            textView.setTextColor(color)
            textView.textSize = 16f
            textView.typeface = Tuils.getTypeface(context)
            textView.setBackgroundColor(Color.TRANSPARENT)
            textView.text = app.publicLabel
            return textView
        }
    }
}
