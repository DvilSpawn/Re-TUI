package ohi.andre.consolelauncher.managers.modules.home

import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.modules.ModuleManager
import ohi.andre.consolelauncher.managers.modules.ReminderManager

internal class ReminderModule : HomeModule {
    override val id = ModuleManager.REMINDER
    override val usesThemeTypeface = true
    override val refreshOn = setOf(HomeModuleEvent.CLOCK)

    override fun show(host: HomeModuleHost) {
        val context = host.context
        host.renderText(id, context.getString(R.string.surface_open_reminder_e9efa, ReminderManager.formatPreview(context)))
    }
}
