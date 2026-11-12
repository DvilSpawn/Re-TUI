package ohi.andre.consolelauncher.managers.modules.home

import android.content.Intent
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.managers.lua.LuaWidgetEngine
import ohi.andre.consolelauncher.managers.lua.LuaWidgetEngine.UpdateListener
import ohi.andre.consolelauncher.managers.lua.LuaWidgetManager
import ohi.andre.consolelauncher.managers.modules.ModuleManager
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.notificationWidgetBorderColor
import ohi.andre.consolelauncher.managers.ui.LuaLayoutRenderer
import ohi.andre.consolelauncher.managers.ui.LuaSurfaceAction
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.frame.FrameTarget
import ohi.andre.consolelauncher.tuils.frame.TuiWidgetDecorator.decorateWidget

/** User and bundled Lua modules: their engines, ticks, actions and panels. New scripts need no wiring here. */
internal class LuaModules(
    private val host: HomeModuleHost,
    actionLuaApp: (String?) -> Unit,
) {
    private val context get() = host.context
    private val luaWidgetEngines = HashMap<String?, LuaWidgetEngine?>()
    private val luaWidgetTickRunnable = Runnable { tickActiveLuaWidget() }

    val layout = LuaLayoutRenderer(host, actionLuaApp, ::actionLuaWidget)

    fun cancelTick() {
        host.handler?.removeCallbacks(luaWidgetTickRunnable)
    }

    fun disposeEngine(id: String?) {
        luaWidgetEngines.remove(id)?.dispose()
    }

    fun disposeEngines() {
        luaWidgetEngines.values.filterNotNull().forEach(LuaWidgetEngine::dispose)
        luaWidgetEngines.clear()
    }

    fun handleCommand(command: String?, module: String?, intent: Intent) {
        when (command) {
            "lua_click" -> clickLuaWidget(module, intent.getIntExtra(UIManager.EXTRA_WIDGET_ACTION_INDEX, 0))
            "lua_action" -> actionLuaWidget(module, intent.getStringExtra(UIManager.EXTRA_WIDGET_ACTION_VALUE))
            "lua_dialog" -> dialogLuaWidget(module, intent.getIntExtra(UIManager.EXTRA_WIDGET_ACTION_INDEX, 0))
            "lua_expand" -> setLuaWidgetExpanded(module, true)
            "lua_collapse" -> setLuaWidgetExpanded(module, false)
            "lua_toggle" -> toggleLuaWidgetExpanded(module)
            "lua_reload_widget" -> reloadLuaWidget(module)
        }
    }

    fun ensureSystemLuaModules() {
        try {
            LuaWidgetManager.ensureSystemTimerWidget()
            val timerSource =
                LuaWidgetManager.SOURCE_PREFIX + LuaWidgetManager.SYSTEM_TIMER_WIDGET_ID
            val currentSource = ModuleManager.getModuleSource(context, ModuleManager.TIMER)
            if (TextUtils.isEmpty(currentSource) || TextUtils.equals(currentSource, timerSource)) {
                ModuleManager.setScriptModule(context, ModuleManager.TIMER, timerSource)
            }
        } catch (e: Exception) {
            Tuils.log(e)
        }
    }

    fun showLuaModuleIfSource(id: String?): Boolean {
        val source = ModuleManager.getModuleSource(context, id)
        if (!ModuleManager.isLuaSource(source)) {
            return false
        }
        val result = renderLuaWidgetModule(id, false, false)
        val widgetId = ModuleManager.luaWidgetId(source)
        if (result != null) {
            showLuaWidgetModule(id, widgetId, result)
        } else {
            val text = ModuleManager.getScriptText(context, id)
            host.renderText(id, if (TextUtils.isEmpty(text)) context.getString(R.string.surface_no_module_output_yet_ba2f9) else text)
        }
        return true
    }

    private fun repaintActiveLuaWidgetModule(
        module: String?,
        widgetId: String?,
        result: LuaWidgetEngine.RenderResult
    ) {
        if (host.moduleContainer == null) {
            return
        }
        host.moduleContainer!!.removeAllViews()
        showLuaWidgetModule(module, widgetId, result)
        host.refreshSuggestions()
    }

    /** Uses the module renderer while making every generated action inert. */
    fun buildLuaPreview(module: String, result: LuaWidgetEngine.RenderResult): View {
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Tuils.dpToPx(context, 12), Tuils.dpToPx(context, 12), Tuils.dpToPx(context, 12), Tuils.dpToPx(context, 12))
            ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.stylePanel(context, this)
        }
        layout.addLuaText(panel, result.title?.takeIf { it.isNotBlank() } ?: LuaWidgetManager.getName(module), module)
        if (TextUtils.isEmpty(result.layoutJson) || !layout.renderLuaLayout(panel, module, result.layoutJson)) {
            layout.addLuaBodyText(panel, result.body?.takeIf { it.isNotEmpty() } ?: "No module output", module)
        }
        addLuaResultActions(panel, module, module, result)
        for (suggestion in result.suggestions) layout.addLuaText(panel, suggestion?.label, module)
        fun makeInert(view: View) {
            view.setOnClickListener(null)
            view.isClickable = false
            view.isLongClickable = false
            if (view is ViewGroup) for (index in 0 until view.childCount) makeInert(view.getChildAt(index))
        }
        makeInert(panel)
        return panel
    }

    private fun showLuaWidgetModule(
        module: String?,
        widgetId: String?,
        result: LuaWidgetEngine.RenderResult
    ) {
        val moduleView = ohi.andre.consolelauncher.presentation.ModulePanelPresentation.inflate(context,host.moduleContainer,ohi.andre.consolelauncher.presentation.ModulePanelPresentation.Kind.TEXT)
        host.moduleContainer!!.addView(moduleView)

        val label = moduleView.findViewById<TextView?>(R.id.module_text_label)
        val close = moduleView.findViewById<TextView?>(R.id.module_text_close)
        val scroll = moduleView.findViewById<ScrollView?>(R.id.module_text_border)
        val body = moduleView.findViewById<TextView?>(R.id.module_text_body)

        if (label != null) {
            val title = if (TextUtils.isEmpty(result.title)) ModuleManager.displayTitle(
                context,
                module
            ) else result.title
            label.setText(title)
            label.setOnClickListener(View.OnClickListener {
                renderLuaWidgetModule(module, true, true)
            })
        }
        close?.let(host::bindCloseButton)

        if (scroll != null) {
            scroll.removeAllViews()
            val content = ohi.andre.consolelauncher.presentation.ModuleBodyPresentation.group(context)
            content.setOrientation(LinearLayout.VERTICAL)
            content.setLayoutParams(
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            scroll.addView(content)

            if (!TextUtils.isEmpty(result.error)) {
                layout.addLuaText(content, context.getString(R.string.surface_lua_error_95ede, result.error), module)
                if (!TextUtils.isEmpty(result.errorStage)) {
                    layout.addLuaText(content, context.getString(R.string.surface_stage_6da98, result.errorStage), module)
                }
            } else if (!TextUtils.isEmpty(result.layoutJson)
                && layout.renderLuaLayout(content, module, result.layoutJson)
            ) {
                // The declarative layout rendered itself.
            } else {
                layout.addLuaBodyText(
                    content,
                    if (TextUtils.isEmpty(result.body)) context.getString(R.string.surface_no_widget_output_yet_c383d) else result.body,
                    module
                )
            }
            addLuaResultActions(content, module, widgetId, result)
        } else if (body != null) {
            body.setText(if (TextUtils.isEmpty(result.body)) context.getString(R.string.surface_no_widget_output_yet_c383d) else result.body)
            body.setTextColor(host.bodyTextColor())
            body.setTextSize(host.bodyTextSize().toFloat())
            host.applyBodyTypeface(body, module)
        }

        decorateWidget(
            moduleView,
            R.id.module_text_border,
            R.id.module_text_label,
            R.id.module_text_close,
            notificationWidgetBorderColor(),
            host.accentTextColor(),
            FrameTarget.MODULES
        )
        host.styleCloseButton(close, FrameTarget.MODULES)
    }

    private fun addLuaResultActions(
        parent: LinearLayout,
        module: String?,
        widgetId: String?,
        result: LuaWidgetEngine.RenderResult
    ) {
        val actions = ArrayList<LuaSurfaceAction>()
        var index = 1
        for (button in result.buttons) {
            val actionIndex = index
            if (!TextUtils.isEmpty(button)) {
                actions.add(
                    LuaSurfaceAction(button!!) {
                        clickLuaWidget(module, actionIndex)
                    }
                )
            }
            index += 1
        }
        for (action in result.valueActions) {
            if (action == null || TextUtils.isEmpty(action.label)) continue
            actions.add(
                LuaSurfaceAction(action.label!!) {
                    actionLuaWidget(module, action.value)
                }
            )
        }
        if (result.dialogOpen) {
            var dialogIndex = 1
            for (item in result.dialogItems) {
                val choiceIndex = dialogIndex
                if (!TextUtils.isEmpty(item)) {
                    val label = if (choiceIndex == result.dialogSelected) "* " + item else item
                    actions.add(
                        LuaSurfaceAction(label!!) {
                            dialogLuaWidget(module, choiceIndex)
                        }
                    )
                }
                dialogIndex += 1
            }
            actions.add(LuaSurfaceAction("cancel") { dialogLuaWidget(module, -1) })
        }
        for (action in result.commands) {
            if (action == null || TextUtils.isEmpty(action.label) || TextUtils.isEmpty(action.command)) {
                continue
            }
            actions.add(
                LuaSurfaceAction(action.label!!) {
                    host.executeCommand(action.command)
                }
            )
        }
        if (result.expandable) {
            val expanded = result.expanded
            actions.add(
                LuaSurfaceAction(if (expanded) "collapse" else "expand") {
                    if (expanded) setLuaWidgetExpanded(module, false)
                    else setLuaWidgetExpanded(module, true)
                }
            )
        }
        layout.addLuaButtonGrid(parent, actions)
    }

    private fun scheduleLuaWidgetTickIfNeeded(
        module: String?,
        result: LuaWidgetEngine.RenderResult?
    ) {
        if (host.handler == null) {
            return
        }
        host.handler!!.removeCallbacks(luaWidgetTickRunnable)
        val id = ModuleManager.normalize(module)
        if ((id != host.activeModuleId) || result == null || result.tickIntervalMs <= 0L) {
            return
        }
        val source = ModuleManager.getModuleSource(context, id)
        if (!ModuleManager.isLuaSource(source)) {
            return
        }
        host.handler!!.postDelayed(luaWidgetTickRunnable, result.tickIntervalMs)
    }

    private fun tickActiveLuaWidget() {
        val id = ModuleManager.normalize(host.activeModuleId)
        if (TextUtils.isEmpty(id)) {
            return
        }
        val source = ModuleManager.getModuleSource(context, id)
        val widgetId = ModuleManager.luaWidgetId(source)
        if (TextUtils.isEmpty(widgetId) || !LuaWidgetManager.exists(widgetId)) {
            return
        }
        if (applyLuaWidgetUnavailablePayload(
                id,
                widgetId,
                true,
                id == host.activeModuleId,
                id == host.activeModuleId
            )
        ) {
            return
        }

        val result = getLuaWidgetEngine(widgetId).tick()
        val active = id == host.activeModuleId
        applyLuaWidgetResult(id, widgetId, result, active, active, false)
    }

    fun renderLuaWidgetModule(
        module: String?,
        repaint: Boolean,
        announce: Boolean
    ): LuaWidgetEngine.RenderResult? {
        val id = ModuleManager.normalize(module)
        val source = ModuleManager.getModuleSource(context, id)
        val widgetId = ModuleManager.luaWidgetId(source)
        if (TextUtils.isEmpty(widgetId) || !LuaWidgetManager.exists(widgetId)) {
            ModuleManager.setScriptText(
                context, id, (context.getString(R.string.surface_title_body_lua_module_source_not_found_1535c, ModuleManager.displayName(context, id), widgetId))
            )
            return null
        } else if (!applyLuaWidgetUnavailablePayload(id, widgetId, true, false, false)) {
            val engine = getLuaWidgetEngine(widgetId)
            val result = engine.render(announce)
            applyLuaWidgetResult(id, widgetId, result, true, false, false)
            if (repaint && id == host.activeModuleId) {
                repaintActiveLuaWidgetModule(id, widgetId, result)
            }
            host.refreshDockSelection()
            if (announce) {
                Tuils.sendOutput(context, context.getString(R.string.surface_lua_module_refreshed_0d7bd, id))
            }
            return result
        }

        if (repaint && id == host.activeModuleId) {
            host.repaintTextModule(id)
        }
        host.refreshDockSelection()
        if (announce) {
            Tuils.sendOutput(context, context.getString(R.string.surface_lua_module_refreshed_0d7bd, id))
        }
        return null
    }

    private fun clickLuaWidget(module: String?, index: Int) {
        if (index <= 0) {
            return
        }
        runLuaWidgetOperation(
            module,
            LuaWidgetOperation { engine: LuaWidgetEngine? -> engine!!.click(index) })
    }

    private fun actionLuaWidget(module: String?, value: String?) {
        runLuaWidgetOperation(
            module,
            LuaWidgetOperation { engine: LuaWidgetEngine? -> engine!!.action(value) })
    }

    private fun dialogLuaWidget(module: String?, index: Int) {
        runLuaWidgetOperation(
            module,
            LuaWidgetOperation { engine: LuaWidgetEngine? -> engine!!.dialog(index) })
    }

    private fun setLuaWidgetExpanded(module: String?, expanded: Boolean) {
        runLuaWidgetOperation(
            module,
            LuaWidgetOperation { engine: LuaWidgetEngine? -> engine!!.setExpanded(expanded) })
    }

    private fun toggleLuaWidgetExpanded(module: String?) {
        runLuaWidgetOperation(
            module,
            LuaWidgetOperation { obj: LuaWidgetEngine? -> obj!!.toggleExpanded() })
    }

    private fun reloadLuaWidget(widgetId: String?) {
        val id = LuaWidgetManager.normalizeId(widgetId)
        if (TextUtils.isEmpty(id)) {
            return
        }
        luaWidgetEngines.remove(id)?.dispose()
        val modules = modulesForLuaWidget(id)
        if (modules.isEmpty()) {
            return
        }
        for (module in modules) {
            renderLuaWidgetModule(module, module == host.activeModuleId, false)
        }
        host.refreshDockSelection()
        host.refreshSuggestions()
    }

    private fun runLuaWidgetOperation(module: String?, operation: LuaWidgetOperation) {
        val id = ModuleManager.normalize(module)
        if (TextUtils.isEmpty(id)) {
            return
        }
        val source = ModuleManager.getModuleSource(context, id)
        val widgetId = ModuleManager.luaWidgetId(source)
        if (TextUtils.isEmpty(widgetId) || !LuaWidgetManager.exists(widgetId)) {
            Tuils.sendOutput(context, context.getString(R.string.surface_lua_module_source_not_found_ff7f6, id))
            return
        }
        if (applyLuaWidgetUnavailablePayload(id, widgetId, false, true, true)) {
            return
        }

        val result = operation.run(getLuaWidgetEngine(widgetId))
        applyLuaWidgetResult(id, widgetId, result, true, true, true)
    }

    private fun applyLuaWidgetUnavailablePayload(
        module: String,
        widgetId: String?,
        stopTicks: Boolean,
        repaint: Boolean,
        updateDock: Boolean
    ): Boolean {
        if (LuaWidgetManager.isEnabled(widgetId) && LuaWidgetManager.isTrusted(widgetId)) {
            return false
        }

        if (stopTicks) {
            host.handler!!.removeCallbacks(luaWidgetTickRunnable)
        }
        val payload = if (LuaWidgetManager.isEnabled(widgetId))
            LuaWidgetManager.consentPayload(widgetId)
        else
            LuaWidgetManager.disabledPayload(widgetId)
        ModuleManager.setScriptText(context, module, payload)
        if (repaint && module == host.activeModuleId) {
            host.repaintTextModule(module)
        }
        if (updateDock) {
            host.refreshDockSelection()
        }
        return true
    }

    private fun applyLuaWidgetResult(
        module: String,
        widgetId: String?,
        result: LuaWidgetEngine.RenderResult,
        scheduleTicks: Boolean,
        repaint: Boolean,
        updateDock: Boolean
    ) {
        ModuleManager.setScriptText(
            context,
            module,
            LuaWidgetManager.modulePayload(widgetId, result)
        )
        if (scheduleTicks) {
            scheduleLuaWidgetTickIfNeeded(module, result)
        }
        if (repaint && module == host.activeModuleId) {
            repaintActiveLuaWidgetModule(module, widgetId, result)
        }
        if (updateDock) {
            host.refreshDockSelection()
        }
    }

    private fun interface LuaWidgetOperation {
        fun run(engine: LuaWidgetEngine?): LuaWidgetEngine.RenderResult
    }

    private fun getLuaWidgetEngine(widgetId: String?): LuaWidgetEngine {
        val id = LuaWidgetManager.normalizeId(widgetId)
        val version = LuaWidgetManager.version(id)
        var engine = luaWidgetEngines.get(id)
        if (engine == null || engine.version() != version) {
            engine?.dispose()
            engine = LuaWidgetEngine(
                context,
                id,
                LuaWidgetManager.readScript(id),
                version,
                UpdateListener { updatedWidgetId: String?, result: LuaWidgetEngine.RenderResult ->
                    val modules = modulesForLuaWidget(updatedWidgetId)
                    if (modules.isEmpty()) {
                        return@UpdateListener
                    }
                    for (module in modules) {
                        if (!LuaWidgetManager.isEnabled(updatedWidgetId)) {
                            ModuleManager.setScriptText(
                                context,
                                module,
                                LuaWidgetManager.disabledPayload(updatedWidgetId)
                            )
                            host.handler!!.removeCallbacks(luaWidgetTickRunnable)
                        } else if (!LuaWidgetManager.isTrusted(updatedWidgetId)) {
                            ModuleManager.setScriptText(
                                context,
                                module,
                                LuaWidgetManager.consentPayload(updatedWidgetId)
                            )
                            host.handler!!.removeCallbacks(luaWidgetTickRunnable)
                        } else {
                            ModuleManager.setScriptText(
                                context,
                                module,
                                LuaWidgetManager.modulePayload(updatedWidgetId, result)
                            )
                        }
                        if (module == host.activeModuleId) {
                            if (LuaWidgetManager.isEnabled(updatedWidgetId)
                                && LuaWidgetManager.isTrusted(updatedWidgetId)
                            ) {
                                repaintActiveLuaWidgetModule(module, updatedWidgetId, result)
                            } else {
                                host.repaintTextModule(module)
                            }
                            scheduleLuaWidgetTickIfNeeded(module, result)
                        }
                    }
                    host.refreshDockSelection()
                })
            luaWidgetEngines.put(id, engine)
        }
        return engine
    }

    private fun modulesForLuaWidget(widgetId: String?): MutableList<String> {
        val modules = ArrayList<String>()
        val normalizedWidget = LuaWidgetManager.normalizeId(widgetId)
        for (module in ModuleManager.listAll(context)) {
            val source = ModuleManager.getModuleSource(context, module)
            if (ModuleManager.isLuaSource(source)
                && TextUtils.equals(normalizedWidget, ModuleManager.luaWidgetId(source))
            ) {
                modules.add(ModuleManager.normalize(module))
            }
        }
        return modules
    }
}
