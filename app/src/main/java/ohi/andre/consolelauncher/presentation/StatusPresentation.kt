package ohi.andre.consolelauncher.presentation

import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.method.ScrollingMovementMethod
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.settings.StatusRowResolver
import ohi.andre.consolelauncher.managers.settings.TextShadows
import android.content.Context
import java.util.regex.Matcher
import java.util.regex.Pattern
import ohi.andre.consolelauncher.managers.NotesManager
import ohi.andre.consolelauncher.managers.status.AsciiAnimationManager
import ohi.andre.consolelauncher.managers.status.NetworkManager
import ohi.andre.consolelauncher.managers.status.RamManager
import ohi.andre.consolelauncher.managers.status.StatusUpdateListener
import ohi.andre.consolelauncher.managers.status.StorageManager
import ohi.andre.consolelauncher.managers.status.UnlockManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.*
import ohi.andre.consolelauncher.tuils.*

/** The same status row styling and formatted content binding for Home and detached previews. */
internal object StatusPresentation {
    data class Descriptor(val visible:Ui,val index:Ui,val color:Theme,val size:Ui,val background:Theme,val shadow:Theme,val alignment:Ui)
    val descriptors=listOf(
        Descriptor(Ui.show_ram,Ui.ram_index,Theme.ram_text_color,Ui.ram_size,Theme.ram_status_background_color,Theme.ram_status_text_shadow_color,Ui.ram_status_alignment),
        Descriptor(Ui.show_device_name,Ui.device_index,Theme.device_text_color,Ui.device_size,Theme.device_status_background_color,Theme.device_status_text_shadow_color,Ui.device_status_alignment),
        Descriptor(Ui.show_time,Ui.time_index,Theme.time_text_color,Ui.time_size,Theme.time_status_background_color,Theme.time_status_text_shadow_color,Ui.time_status_alignment),
        Descriptor(Ui.show_battery,Ui.battery_index,Theme.battery_text_high,Ui.battery_size,Theme.battery_status_background_color,Theme.battery_status_text_shadow_color,Ui.battery_status_alignment),
        Descriptor(Ui.show_storage_info,Ui.storage_index,Theme.storage_text_color,Ui.storage_size,Theme.storage_status_background_color,Theme.storage_status_text_shadow_color,Ui.storage_status_alignment),
        Descriptor(Ui.show_network_info,Ui.network_index,Theme.network_info_text_color,Ui.network_size,Theme.network_status_background_color,Theme.network_status_text_shadow_color,Ui.network_status_alignment),
        Descriptor(Ui.show_notes,Ui.notes_index,Theme.notes_text_color,Ui.notes_size,Theme.notes_status_background_color,Theme.notes_status_text_shadow_color,Ui.notes_status_alignment),
        Descriptor(Ui.show_weather,Ui.weather_index,Theme.weather_text_color,Ui.weather_size,Theme.weather_status_background_color,Theme.weather_status_text_shadow_color,Ui.weather_status_alignment),
        Descriptor(Ui.show_unlock_counter,Ui.unlock_index,Theme.unlock_counter_text_color,Ui.unlock_size,Theme.unlock_status_background_color,Theme.unlock_status_text_shadow_color,Ui.unlock_status_alignment),
        Descriptor(Ui.show_ascii,Ui.ascii_index,Theme.ascii_text_color,Ui.ascii_size,Theme.ascii_status_background_color,Theme.ascii_status_text_shadow_color,Ui.ascii_status_alignment)
    )
    private val rowIds=intArrayOf(R.id.tv0,R.id.tv1,R.id.tv2,R.id.tv3,R.id.tv4,R.id.tv5,R.id.tv6,R.id.tv7,R.id.tv8,R.id.tv9)
    class Content internal constructor(internal val texts:List<CharSequence?>,internal val asciiColor:Int?,internal val asciiColors:IntArray?)
    fun capture(texts:Array<CharSequence?>):Content {
        val frame=texts.getOrNull(9) as? AsciiAnimationManager.AsciiFrameText
        return Content(texts.map { text -> text?.let { ReadOnlyStyledText.freeze(it) } },frame?.color(),frame?.colors()?.copyOf())
    }

    fun rows(appearance:HomeAppearance,saved:HomeAppearance?=null):List<List<Int>> {
        val original=StatusRowResolver.settings.associateWith { (saved?:appearance).raw(it) }
        val edits=if(saved==null) emptyMap() else StatusRowResolver.settings.filter { appearance.changed(it,saved) }.associateWith { appearance.raw(it) }
        val indices=if(edits.isEmpty()) StatusRowResolver.normalize(original).values else StatusRowResolver.reorder(original,edits).values
        return StatusRowResolver.groupVisible(descriptors.mapIndexedNotNull { i,d ->
            if(appearance.bool(d.visible)) (indices[d.index]?.toFloatOrNull()?:appearance.number(d.index)) to i else null
        })
    }

    fun bindStyle(view:TextView,primaryOrdinal:Int,appearance:HomeAppearance,unified:Boolean=false) {
        val d=descriptors[primaryOrdinal]
        val surface=SurfaceBorder.entries[primaryOrdinal]
        view.typeface=if(view is AsciiArtTextView) Typeface.MONOSPACE else appearance.font(view.context)
        view.gravity=when { appearance.number(d.alignment)>0 -> Gravity.END;appearance.number(d.alignment)<0 -> Gravity.START;else -> Gravity.CENTER_HORIZONTAL }
        view.setTextColor(appearance.color(d.color))
        HomeAppearance.surface(view.context,view,appearance.color(d.background),
            SurfaceBorder.resolveColor(appearance.raw(surface.color),appearance.color(Theme.terminal_border_color)),
            if(unified) 0f else Tuils.dpToPx(view.context,appearance.radius(Ui.module_corner_radius)).toFloat(),
            appearance.bool(Ui.enable_dashed_border),false,surface,!unified,!unified,appearance)
        val padding=appearance.raw(Ui.status_lines_margins).orEmpty().split(',').map { it.trim().toIntOrNull()?:0 }
        if(padding.size==4) view.setPadding(padding[0],padding[1],padding[2],padding[3])
        val shadow=appearance.color(d.shadow)
        if(Color.alpha(shadow)==0) { view.setShadowLayer(0f,0f,0f,Color.TRANSPARENT);view.tag=null }
        else {
            val params=TextShadows.parameters(appearance.raw(Ui.shadow_params))
            view.setShadowLayer(params[2],params[0],params[1],shadow)
            view.tag=OutlineTextView.SHADOW_TAG
        }
    }

    fun bindLiveContent(view:TextView,members:List<Int>,texts:Array<CharSequence?>,appearance:HomeAppearance) {
        val frame=texts.getOrNull(9) as? AsciiAnimationManager.AsciiFrameText
        bindContent(view,members,Content(texts.asList(),frame?.color(),frame?.colors()),appearance)
    }

    fun notesView(root:View,appearance:HomeAppearance,saved:HomeAppearance?=null):TextView? {
        val row=rows(appearance,saved).indexOfFirst { it.contains(6) }
        return rowIds.getOrNull(row)?.let { root.findViewById(it) }
    }

    fun bindContent(view:TextView,members:List<Int>,content:Content,appearance:HomeAppearance,saved:HomeAppearance?=null) {
        if(members.contains(6)) appearance.number(Ui.notes_max_lines).toInt().takeIf { it>0 }?.let { view.maxLines=it }

        if(members.size==1 && members[0]==9 && view is AsciiArtTextView) {
            val changed=saved!=null && appearance.changed(Theme.ascii_text_color,saved)
            view.setAsciiFrame(content.texts.getOrNull(9)?.toString(),
                if(changed) appearance.color(Theme.ascii_text_color) else content.asciiColor?:appearance.color(Theme.ascii_text_color),
                appearance.number(Ui.ascii_max_lines).toInt(),appearance.number(Ui.ascii_pane_height_rows).toInt(),
                if(changed) null else content.asciiColors?.copyOf())
            val landscape=view.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE && appearance.number(Behavior.orientation).toInt()!=1
            view.visibility=if(content.texts.getOrNull(9).isNullOrEmpty() || (landscape && !appearance.bool(Ui.show_ascii_landscape))) View.GONE else View.VISIBLE
            return
        }
        val parts=members.mapNotNull { ordinal ->
            if(saved!=null && ordinal==2 && listOf(Behavior.status_time_format,Behavior.time_format_separator,
                    Ui.time_size,Ui.font_size_offset,Theme.time_text_color).any { appearance.changed(it,saved) }) {
                ohi.andre.consolelauncher.managers.TimeManager.previewStatus(view.context,
                    appearance.raw(Behavior.status_time_format).orEmpty(),
                    appearance.raw(Behavior.time_format_separator).orEmpty(),
                    appearance.number(Ui.time_size,12f).toInt(),appearance.color(Theme.time_text_color))
            } else {
                val formatted=if(saved==null) null else draftFormat(view.context,ordinal,appearance,saved)
                (formatted ?: content.texts.getOrNull(ordinal))?.let { text ->
                    if(saved==null) text else draftText(text,ordinal,view,appearance,saved)
                }
            }
        }
        view.text=TextUtils.concat(*parts.toTypedArray())
        view.visibility=if(view.text.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    fun deviceText(read:(XMLPrefsSave)->String):String {
        val name=read(Ui.deviceName).ifEmpty { android.os.Build.DEVICE }
        var text=Pattern.compile("%u",Pattern.CASE_INSENSITIVE or Pattern.LITERAL).matcher(read(Behavior.device_format)).replaceAll(Matcher.quoteReplacement(read(Ui.username)))
        text=Pattern.compile("%d",Pattern.CASE_INSENSITIVE or Pattern.LITERAL).matcher(text).replaceAll(Matcher.quoteReplacement(name))
        return Tuils.patternNewline.matcher(text).replaceAll(Matcher.quoteReplacement(Tuils.NEWLINE))
    }

    private val formatKeys:Map<Int,List<XMLPrefsSave>> = mapOf(
        0 to listOf(Behavior.ram_format),
        1 to listOf(Behavior.device_format,Ui.username,Ui.deviceName),
        4 to listOf(Behavior.storage_format),
        5 to listOf(Behavior.network_info_format,Behavior.max_optional_depth,Behavior.optional_values_separator),
        6 to listOf(Ui.notes_header,Ui.notes_footer,Ui.notes_divider,Behavior.optional_values_separator),
        8 to listOf(Behavior.unlock_counter_format,Behavior.not_available_text,Behavior.unlock_time_divider,Behavior.unlock_time_order))

    // Re-runs the row's own formatter on the draft in saved colours/sizes; draftText then applies draft styling.
    // shortcut: unlock times (%t) show not-available text because preview copies never observe unlocks.
    private val batteryKeys=listOf<XMLPrefsSave>(Behavior.battery_progress_bar,Behavior.battery_progress_bar_length,Behavior.battery_progress_bar_symbol,
        Behavior.battery_format,Behavior.battery_medium,Behavior.battery_low,Behavior.optional_values_separator,Ui.enable_battery_status)

    /** Battery text rendered from the draft at the current charge; null when no battery setting is drafted. */
    fun draftBattery(context:Context,appearance:HomeAppearance,saved:HomeAppearance):CharSequence? {
        if(batteryKeys.none { appearance.changed(it,saved) }) return null
        val sticky=context.registerReceiver(null,android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val level=sticky.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL,0)*100f/sticky.getIntExtra(android.os.BatteryManager.EXTRA_SCALE,100).coerceAtLeast(1)
        val charging=sticky.getIntExtra(android.os.BatteryManager.EXTRA_STATUS,-1) in
            listOf(android.os.BatteryManager.BATTERY_STATUS_CHARGING,android.os.BatteryManager.BATTERY_STATUS_FULL)
        return BatteryPresentation.render(context,level,charging,appearance.number(Ui.battery_size,12f).toInt(),
            appearance.number(Behavior.battery_medium).toInt(),appearance.number(Behavior.battery_low).toInt(),appearance)
    }

    private fun draftFormat(context:Context,ordinal:Int,appearance:HomeAppearance,saved:HomeAppearance):CharSequence? {
        if(ordinal==3) return draftBattery(context,appearance,saved)
        if(formatKeys[ordinal]?.any { appearance.changed(it,saved) }!=true) return null
        val read:(XMLPrefsSave)->String={ appearance.raw(it).orEmpty() }
        val size=saved.number(descriptors[ordinal].size,12f).toInt()
        var out:CharSequence?=null
        val listener=StatusUpdateListener { _,text -> out=text }
        runCatching { when(ordinal) {
            0->RamManager(context,0,size,listener,read).refreshNow()
            1->out=Tuils.span(context,deviceText(read),saved.color(Theme.device_text_color),size)
            4->StorageManager(context,0,size,listener,read).refreshNow()
            5->NetworkManager(context,0,size,listener,read).refreshNow()
            6->out=NotesManager(context,null,read,readOnly=true).getNotes()
            8->UnlockManager(context,size,listener,read).refreshNow()
        } }.onFailure { Tuils.log(it) }
        return out
    }

    private fun draftText(source:CharSequence,ordinal:Int,view:TextView,appearance:HomeAppearance,saved:HomeAppearance):CharSequence {
        val d=descriptors[ordinal]
        val roles=when(ordinal) {
            3->listOf(Theme.battery_text_high,Theme.battery_text_medium,Theme.battery_text_low)
            6->listOf(Theme.notes_text_color,Theme.locked_notes_text_color)
            else->listOf(d.color)
        }
        if(!roles.any { appearance.changed(it,saved) } && !appearance.changed(d.size,saved)) return source
        val text=SpannableString(source)
        text.getSpans(0,text.length,ForegroundColorSpan::class.java).forEach { span ->
            val role=roles.firstOrNull { span.foregroundColor==saved.color(it) && appearance.changed(it,saved) }
            if(role!=null) {
                val start=text.getSpanStart(span);val end=text.getSpanEnd(span);val flags=text.getSpanFlags(span)
                text.removeSpan(span);text.setSpan(ForegroundColorSpan(appearance.color(role)),start,end,flags)
            }
        }
        if(appearance.changed(d.size,saved)) {
            val spans=text.getSpans(0,text.length,AbsoluteSizeSpan::class.java)
            // Status formatters emit absolute pixel spans; preserve their formatting instead of inventing text.
            val pixels=Tuils.convertSpToPixels(appearance.number(d.size,12f).coerceAtLeast(1f),view.context)
            spans.forEach { span ->
                val start=text.getSpanStart(span);val end=text.getSpanEnd(span);val flags=text.getSpanFlags(span)
                text.removeSpan(span);text.setSpan(AbsoluteSizeSpan(pixels),start,end,flags)
            }
            if(spans.isEmpty()) text.setSpan(AbsoluteSizeSpan(pixels),0,text.length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return text
    }

    /** Rebuilds only presentation rows, using the same XML and binder, with no status managers or actions. */
    fun renderRows(root:View,content:Content,appearance:HomeAppearance,saved:HomeAppearance) {
        if(appearance.bool(SurfaceBorderOption.unified_status_border)) return
        val header=root.findViewById<ViewGroup>(R.id.header_container)?:return
        val parent=(root.findViewById<View>(R.id.unified_status_container)?.parent as? ViewGroup)
            ?: (header.getChildAt(0) as? ViewGroup)?:return
        val oldRows=(0 until parent.childCount).map { parent.getChildAt(it) }.filter { it.id in rowIds }
        val insertion=oldRows.minOfOrNull { parent.indexOfChild(it) }?:parent.childCount
        oldRows.forEach(parent::removeView)
        val template=LayoutInflater.from(root.context).inflate(R.layout.about_device_view,null,false) as ViewGroup
        rows(appearance,saved).forEachIndexed { row,members ->
            val original=template.findViewById<TextView>(rowIds[row])
            val view=if(members==listOf(9)) AsciiArtTextView(root.context) else original.also { (it.parent as? ViewGroup)?.removeView(it) }
            view.id=rowIds[row]
            view.layoutParams=LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT)
            view.isClickable=false;view.isFocusable=false;view.isFocusableInTouchMode=false
            view.isVerticalScrollBarEnabled=members.contains(6)
            if(members.contains(6)) view.movementMethod=ScrollingMovementMethod()
            if(view !is AsciiArtTextView) ohi.andre.consolelauncher.tuils.LauncherFontScale.setBaseSize(view,
                ohi.andre.consolelauncher.tuils.LauncherFontScale.pixelsToSp(original),appearance.number(Ui.font_size_offset).toInt())
            bindStyle(view,members[0],appearance)
            bindContent(view,members,content,appearance,saved)
            view.isClickable=false;view.isLongClickable=false;view.isFocusable=false;view.isFocusableInTouchMode=false
            parent.addView(view,(insertion+row).coerceAtMost(parent.childCount))
        }
        root.findViewById<View>(R.id.unified_status_container)?.visibility=View.GONE
    }
}
