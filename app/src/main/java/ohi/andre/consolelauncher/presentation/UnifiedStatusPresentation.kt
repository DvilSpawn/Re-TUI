package ohi.andre.consolelauncher.presentation

import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.FrameTarget
import ohi.andre.consolelauncher.tuils.LauncherFontScale
import ohi.andre.consolelauncher.tuils.TerminalBorderRuntime
import ohi.andre.consolelauncher.tuils.Tuils

/** Styles the actual compact status XML; data and controller actions stay outside presentation. */
internal object UnifiedStatusPresentation {
    private val textRoles=linkedMapOf(
        R.id.unified_status_date to Theme.time_text_color,
        R.id.unified_status_time to Theme.time_text_color,
        R.id.unified_status_battery to Theme.battery_text_high,
        R.id.unified_status_weather to Theme.weather_text_color,
        R.id.unified_status_weather_location to Theme.weather_text_color,
        R.id.unified_status_weather_details to Theme.weather_text_color,
        R.id.unified_status_storage_internal to Theme.storage_text_color,
        R.id.unified_status_storage_external to Theme.storage_text_color,
        R.id.unified_status_network to Theme.network_info_text_color,
        R.id.unified_status_ram to Theme.ram_text_color,
        R.id.unified_status_unlock to Theme.unlock_counter_text_color
    )
    private val imageRoles=mapOf(
        R.id.unified_status_weather_icon to Theme.weather_text_color,
        R.id.unified_status_storage_internal_icon to Theme.storage_text_color,
        R.id.unified_status_storage_external_icon to Theme.storage_text_color,
        R.id.unified_status_ram_icon to Theme.ram_text_color
    )
    data class Content(val texts:Map<Int,CharSequence>,val visibility:Map<Int,Int>,val batteryColor:Int?,val networkIcon:Int?)

    fun capture(root:View):Content {
        val texts=textRoles.keys.mapNotNull { id -> root.findViewById<TextView>(id)?.let {
            id to ReadOnlyStyledText.freeze(it.text,it.paint)
        } }.toMap()
        val visibility=(textRoles.keys+imageRoles.keys).mapNotNull { id -> root.findViewById<View>(id)?.let { id to it.visibility } }.toMap()
        return Content(texts,visibility,root.findViewById<TextView>(R.id.unified_status_battery)?.currentTextColor,
            root.findViewById<View>(R.id.unified_status_network)?.tag as? Int)
    }

    fun bindContent(root:View,content:Content,appearance:HomeAppearance,saved:HomeAppearance?=null) {
        content.texts.forEach { (id,text)->root.findViewById<TextView>(id)?.text=text }
        content.visibility.forEach { (id,visible)->root.findViewById<View>(id)?.visibility=visible }
        content.batteryColor?.let { source ->
            val role=if(saved==null) null else listOf(Theme.battery_text_high,Theme.battery_text_medium,Theme.battery_text_low)
                .firstOrNull { saved.color(it)==source }
            root.findViewById<TextView>(R.id.unified_status_battery)?.setTextColor(if(role==null) source else appearance.color(role))
        }
        content.networkIcon?.let { icon ->root.findViewById<TextView>(R.id.unified_status_network)?.let {
            setMetricIcon(it,icon,appearance.color(Theme.network_info_text_color))
        } }
        if(saved!=null) {
            StatusPresentation.draftBattery(root.context,appearance,saved)?.let { root.findViewById<TextView>(R.id.unified_status_battery)?.text=it }
            val visibilityKeys=mapOf(
                Ui.show_time to listOf(R.id.unified_status_date,R.id.unified_status_time),
                Ui.show_battery to listOf(R.id.unified_status_battery),
                Ui.show_weather to listOf(R.id.unified_status_weather,R.id.unified_status_weather_icon,R.id.unified_status_weather_location,R.id.unified_status_weather_details),
                Ui.show_storage_info to listOf(R.id.unified_status_storage_internal,R.id.unified_status_storage_internal_icon,R.id.unified_status_storage_external,R.id.unified_status_storage_external_icon),
                Ui.show_network_info to listOf(R.id.unified_status_network),
                Ui.show_ram to listOf(R.id.unified_status_ram,R.id.unified_status_ram_icon),
                Ui.show_unlock_counter to listOf(R.id.unified_status_unlock)
            )
            visibilityKeys.forEach { (key,ids)->if(appearance.changed(key,saved)) ids.forEach { id ->
                val textId=when(id) {
                    R.id.unified_status_weather_icon -> R.id.unified_status_weather
                    R.id.unified_status_storage_internal_icon -> R.id.unified_status_storage_internal
                    R.id.unified_status_storage_external_icon -> R.id.unified_status_storage_external
                    R.id.unified_status_ram_icon -> R.id.unified_status_ram
                    else -> id
                }
                root.findViewById<View>(id)?.visibility=if(appearance.bool(key) && content.texts[textId]?.isNotEmpty()==true) View.VISIBLE else View.GONE
            } }
        }
    }

    fun positionModulesToggle(root:View) {
        val container=root.findViewById<View>(R.id.unified_status_container)?:return
        root.findViewById<View>(R.id.unified_modules_toggle)?.let { it.translationY=container.paddingBottom+it.height/2f }
    }

    fun style(root:View,appearance:HomeAppearance) {
        val container=root.findViewById<View>(R.id.unified_status_container)?:return
        val padding=appearance.raw(Ui.status_lines_margins).orEmpty().split(',').map { it.trim().toIntOrNull()?:0 }
        val horizontal=(padding.getOrElse(0){0}).coerceAtLeast(Tuils.dpToPx(root.context,8))
        val vertical=(padding.getOrElse(1){0}).coerceAtLeast(Tuils.dpToPx(root.context,4))
        container.setPadding(horizontal,vertical,(padding.getOrElse(2){0}).coerceAtLeast(Tuils.dpToPx(root.context,8)),(padding.getOrElse(3){0}).coerceAtLeast(vertical))
        val font=appearance.font(root.context)
        textRoles.forEach { (id,role)->root.findViewById<TextView>(id)?.let { it.typeface=font;it.setTextColor(appearance.color(role)) } }
        val time=appearance.number(Ui.time_size,12f).coerceIn(18f,28f)
        val compact=(time*.65f).coerceAtLeast(11f)
        root.findViewById<TextView>(R.id.unified_status_date)?.let { size(it,compact,appearance) }
        root.findViewById<TextView>(R.id.unified_status_time)?.let { size(it,time,appearance) }
        root.findViewById<TextView>(R.id.unified_status_battery)?.let { size(it,appearance.number(Ui.battery_size,12f).coerceIn(11f,16f),appearance) }
        imageRoles.forEach { (id,role)->root.findViewById<ImageView>(id)?.imageTintList=ColorStateList.valueOf(appearance.color(role)) }
        textRoles.filterKeys { it !in setOf(R.id.unified_status_date,R.id.unified_status_time,R.id.unified_status_battery) }.forEach { (id,role)->
            root.findViewById<TextView>(id)?.let { view ->
                size(view,compact,appearance)
                view.compoundDrawablePadding=Tuils.dpToPx(root.context,3)
                view.includeFontPadding=false
                view.height=Tuils.dpToPx(root.context,16)
                view.gravity=view.gravity or Gravity.CENTER_VERTICAL
                val icon=when(id) { R.id.unified_status_network -> R.drawable.ic_status_wifi_40;R.id.unified_status_unlock -> R.drawable.ic_status_lock_open_40;else -> 0 }
                if(icon!=0) setMetricIcon(view,icon,appearance.color(role))
            }
        }
        root.findViewById<ImageView>(R.id.unified_modules_toggle)?.let { toggle ->
            toggle.imageTintList=ColorStateList.valueOf(appearance.color(Theme.icon_button_color))
            toggle.background=TerminalBorderRuntime.panelDrawablePx(root.context,
                appearance.color(Theme.icon_button_background_color),appearance.color(Theme.icon_button_border_color),1.25f,
                Tuils.dpToPx(root.context,appearance.radius(Ui.module_corner_radius)).toFloat(),appearance.bool(Ui.enable_dashed_border),
                false,true,true,FrameTarget.ICON_BUTTON,appearance.borderStyle())
            toggle.visibility=if(appearance.bool(Behavior.show_module_dock)) View.VISIBLE else View.GONE
        }
    }

    private fun size(view:TextView,base:Float,appearance:HomeAppearance) {
        val scaled=LauncherFontScale.scaledSp(base,appearance.number(Ui.font_size_offset).toInt())
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP,scaled)
        view.setTag(R.id.retui_font_base_size_sp,base)
        view.setTag(R.id.retui_font_scaled_size_sp,scaled)
    }

    fun setMetricIcon(view:TextView,iconRes:Int,color:Int) {
        val icon=ContextCompat.getDrawable(view.context,iconRes)?.mutate()
        val size=Tuils.dpToPx(view.context,16)
        icon?.setBounds(0,0,size,size)
        icon?.let { DrawableCompat.setTint(it,color) }
        view.setCompoundDrawables(icon,null,null,null)
    }
}
