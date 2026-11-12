package ohi.andre.consolelauncher.managers.modules.home

import android.content.Context
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.modules.ModuleManager
import ohi.andre.consolelauncher.managers.time.ClockManager
import ohi.andre.consolelauncher.managers.time.PomodoroManager

internal class TimerModule : HomeModule {
    override val id = ModuleManager.TIMER
    override val usesThemeTypeface = true
    override val refreshOn = setOf(HomeModuleEvent.CLOCK)

    override fun show(host: HomeModuleHost) = host.renderText(id, buildText(host.context))

    private fun buildText(context: Context): String {
        val out = StringBuilder()
        val clockManager = ClockManager.getInstance(context.getApplicationContext())
        out.append(clockManager.timerStatus).append('\n')
        out.append(clockManager.stopwatchStatus).append('\n')

        val pomodoro = PomodoroManager.getInstance(context.getApplicationContext())
        if (pomodoro.isRunning) {
            out.append(context.getString(R.string.surface_pomodoro_0fb35))
                .append(pomodoro.currentType.name.lowercase())
                .append(" ")
                .append(ClockManager.formatDuration(pomodoro.remainingMillis))
                .append('\n')
        } else {
            out.append(context.getString(R.string.surface_pomodoro_idle_46ab6))
        }
        out.append(context.getString(R.string.surface_commands_timer_stopwatch_pomodoro_83ad1))
        return out.toString()
    }
}
