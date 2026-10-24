package ohi.andre.consolelauncher.presentation

import android.content.res.Configuration
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.SurfaceBorderOption
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.TerminalTrayToggleView
import ohi.andre.consolelauncher.tuils.Tuils
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Shared geometry only. No focus, persistence, command, animation or controller callbacks. */
internal object TerminalTrayPresentation {
    data class RuntimeState(val keyboardVisible:Boolean,val imeBottom:Int,val systemBottom:Int,val imeVisible:Boolean,
                            val expanded:Boolean,val landscape:Boolean,val duo:Boolean,val hasActiveModule:Boolean,
                            val sourceSearch:Boolean=false,val classicHint:String?=null,val keyboardMarginsOverride:Boolean=false,val duoSide:String="off")

    fun contentHeight(text:TextView?,chrome:Int,minimum:Int,maximum:Int,lineMinimum:Int):Int {
        if(text==null || text.text.isNullOrEmpty()) return minimum
        return max(minimum,min(maximum,max(1,text.lineCount)*max(text.lineHeight,lineMinimum)+chrome))
    }

    fun expandedLimit(rootHeight:Int,collapsed:Int,keyboard:Boolean,fallback:Int,maxHeight:Int):Int {
        val adaptive=if(rootHeight<=0) max(collapsed,fallback) else max(collapsed,Math.round(rootHeight*if(keyboard) .34f else .48f))
        return if(maxHeight<=0) adaptive else max(collapsed,min(adaptive,maxHeight))
    }

    fun constrain(requestedHeight:Int,rootHeight:Int,imeBottom:Int,systemBottom:Int,persistentHeight:Int,imeVisible:Boolean):Int {
        val requested=max(0,requestedHeight)
        if(!imeVisible || rootHeight<=0) return requested
        val available=max(0,rootHeight-max(0,imeBottom)-max(0,systemBottom))
        return min(requested,max(0,available-max(0,persistentHeight)))
    }

    fun unifiedModuleHeight(contentHeight:Int,availableHeight:Int,maxHeight:Int):Int =
        min(max(0,contentHeight),max(0,min(maxHeight,(max(0,availableHeight)*.34f).roundToInt())))

    fun headerOnly(headerBottom:Int,fallback:Int)=if(headerBottom>0) headerBottom else fallback

    /** Output room between the top section's content (header, dock, open module) and the bottom chrome;
     *  -1 when the sections are not stacked in portrait or margins place the output above the top section. */
    fun roomBelowTopContent(root:View,rootHeight:Int,bottomChrome:Int):Int {
        val main=root.findViewById<View>(R.id.main_container)?:return -1
        val header=root.findViewById<View>(R.id.header_container)?:return -1
        val tray=root.findViewById<View>(R.id.terminal_tray_container)?:return -1
        if(rootHeight<=0 || header.parent!==main || tray.parent!==main.parent) return -1
        val mainTop=(main.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin?:0
        val trayBottom=rootHeight-root.paddingBottom-((tray.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin?:0)
        if(trayBottom<=mainTop) return -1
        val page=root.findViewById<ViewGroup>(R.id.home_modules_root)
        // The page fills its viewport, so measure the dock and open module rather than the page itself.
        val content=page?.getChildAt(0) as? ViewGroup
        val used=content?.let { c -> (0 until c.childCount).map(c::getChildAt).filter { it.visibility!=View.GONE }.sumOf { it.measuredHeight } }?:0
        val top=mainTop+header.measuredHeight+(page?.paddingTop?:0)+used
        // The output header sits 10dp above the pane; keep a small gap below the dock or module.
        return max(0,trayBottom-bottomChrome-top-Tuils.dpToPx(root.context,16))
    }

    /** Fit the output in [room]: native and auto fill it up to [fillHeight]; other modes only shrink. */
    fun fitRoom(requested:Int,fillHeight:Int,room:Int,header:Int,fill:Boolean):Int {
        if(room<0) return requested
        return max(header,min(room,if(fill) fillHeight else requested))
    }

    fun chromeHeight(root:View):Int {
        val fallback=Tuils.dpToPx(root.context,38)
        val output=root.findViewById<View>(R.id.terminal_output_border)?:return fallback
        val container=root.findViewById<View>(R.id.terminal_container)
        val margins=output.layoutParams as? ViewGroup.MarginLayoutParams
        return max(fallback,(container?.paddingTop?:0)+(container?.paddingBottom?:0)+(margins?.topMargin?:0)+
            (margins?.bottomMargin?:0)+output.paddingTop+output.paddingBottom)
    }

    fun applyEdgeInsets(root:View,landscape:Boolean,appearance:HomeAppearance) {
        root.findViewById<View>(R.id.terminal_container)?.let { container ->
            val top=if(landscape) 0 else Tuils.dpToPx(root.context,8)
            if(container.paddingTop!=top) container.setPadding(container.paddingLeft,top,container.paddingRight,container.paddingBottom)
        }
        root.findViewById<View>(R.id.terminal_tray_toggle)?.let { header ->
            (header.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                val top=if(landscape) 0 else -Tuils.dpToPx(root.context,10)
                if(params.topMargin!=top) { params.topMargin=top;header.layoutParams=params }
            }
        }
        applyContentInsets(root,appearance)
    }

    fun prepare(root:View,appearance:HomeAppearance) = applyEdgeInsets(root,
        root.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE && appearance.number(Behavior.orientation).toInt()!=1,appearance)

    fun applyContentInsets(root:View,appearance:HomeAppearance) {
        val border=root.findViewById<View>(R.id.terminal_output_border)?:return
        val margins=appearance.raw(Ui.output_field_margins).orEmpty().replace(Regex("[\\[\\]\\s]"),"").split(',').map { it.trim().toIntOrNull()?:0 }
        val horizontal=max(0,margins.getOrElse(2){0})+Tuils.dpToPx(root.context,8)
        val vertical=max(0,margins.getOrElse(3){0})
        val top=vertical+topContentInset(root,appearance.raw(Behavior.output_header_mode)?.trim()?.equals("none",true)==true)
        val bottom=vertical+Tuils.dpToPx(root.context,6)
        if(border.paddingLeft!=horizontal || border.paddingRight!=horizontal || border.paddingTop!=top || border.paddingBottom!=bottom)
            border.setPadding(horizontal,top,horizontal,bottom)
    }

    private fun topContentInset(root:View,none:Boolean):Int {
        val base=Tuils.dpToPx(root.context,12)
        if(none) return base
        val header=root.findViewById<TextView>(R.id.terminal_tray_toggle)?:return base
        val height=when { header.height>0 -> header.height;header.measuredHeight>0 -> header.measuredHeight;else -> (header.textSize+header.paddingTop+header.paddingBottom).roundToInt() }
        val margin=(header.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin?:0
        return max(base,max(0,height+margin+Tuils.dpToPx(root.context,6)))
    }

    /** Run after measuring at the frozen Home viewport; then measure/layout once more. */
    fun apply(root:ViewGroup,state:RuntimeState,appearance:HomeAppearance,viewportHeight:Int) {
        if(HomeLayoutPresentation.isSearch(appearance)) return
        if(state.sourceSearch) root.findViewById<TextView>(R.id.input_view)?.hint=state.classicHint
        val container=root.findViewById<View>(R.id.terminal_container)?:return
        val text=root.findViewById<TextView>(R.id.terminal_view)
        val border=root.findViewById<View>(R.id.terminal_output_border)
        val toggle=root.findViewById<TextView>(R.id.terminal_tray_toggle)
        val tray=root.findViewById<View>(R.id.terminal_tray_container)
        val none=appearance.raw(Behavior.output_header_mode)?.trim()?.equals("none",true)==true
        val arrows=appearance.raw(Behavior.output_header_mode)?.trim()?.equals("arrows",true)==true
        val mode=trayMode(appearance,none)
        val autoHide=!none && appearance.bool(Behavior.auto_hide_output)
        val toggled=mode=="toggled"
        val native=mode=="native"
        val auto=mode=="auto"
        val expanded=if(auto) !state.hasActiveModule else state.expanded
        val landscape=!appearance.bool(SurfaceBorderOption.unified_status_border) &&
            root.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE && appearance.number(Behavior.orientation).toInt()!=1
        val fullLandscape=landscape && ((!toggled && !autoHide && !state.duo) || ((toggled || autoHide) && expanded))
        border?.visibility=if((autoHide || toggled) && !expanded) View.INVISIBLE else View.VISIBLE
        if(landscape) (tray?.layoutParams as? FrameLayout.LayoutParams)?.let {
            it.width=ViewGroup.LayoutParams.MATCH_PARENT
            it.height=if(fullLandscape) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
            it.gravity=Gravity.BOTTOM
            tray.layoutParams=it
        }
        applyContentInsets(root,appearance)
        if(appearance.bool(SurfaceBorderOption.unified_status_border)) {
            val scroll=root.findViewById<View>(R.id.unified_module_scroll)
            val content=root.findViewById<View>(R.id.home_modules_container)
            if(scroll?.visibility==View.VISIBLE && content!=null) {
                val cap=unifiedModuleHeight(content.measuredHeight,viewportHeight-state.imeBottom-state.systemBottom,Tuils.dpToPx(root.context,240))
                scroll.layoutParams=scroll.layoutParams.apply { height=cap }
                root.findViewById<View>(R.id.unified_bottom_console)?.let { console ->
                    console.measure(View.MeasureSpec.makeMeasureSpec(console.measuredWidth,View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
                }
            }
        }
        val params=container.layoutParams
        if(fullLandscape) {
            if(params is LinearLayout.LayoutParams) { params.height=0;params.weight=1f }
            else params?.height=ViewGroup.LayoutParams.MATCH_PARENT
        } else {
            if(params is LinearLayout.LayoutParams) params.weight=0f
            val chrome=chromeHeight(root)
            val lineMinimum=Tuils.dpToPx(root.context,18)
            val minimum=Tuils.dpToPx(root.context,66)
            val collapsed=contentHeight(text,chrome,minimum,Tuils.dpToPx(root.context,if(state.keyboardVisible) 96 else 132),lineMinimum)
            val maxDp=appearance.number(Ui.output_tray_max_height).toInt().coerceIn(0,1200)
            val limit=expandedLimit(viewportHeight,collapsed,state.keyboardVisible,Tuils.dpToPx(root.context,if(state.keyboardVisible) 220 else 320),Tuils.dpToPx(root.context,maxDp))
            val expandedHeight=contentHeight(text,chrome,minimum,limit,lineMinimum)
            val header=headerOnly(toggle?.bottom?:0,Tuils.dpToPx(root.context,32))
            val requested=when {
                autoHide -> if(expanded) expandedHeight else header
                native -> if(!state.hasActiveModule) expandedHeight else collapsed
                auto -> if(expanded) expandedHeight else collapsed
                else -> if(expanded) expandedHeight else header
            }
            val inputHeight=root.findViewById<View>(R.id.persistent_input_container)?.measuredHeight?:0
            val console=root.findViewById<View>(R.id.unified_bottom_console)
            val consoleHeight=if(appearance.bool(SurfaceBorderOption.unified_status_border) && console?.visibility==View.VISIBLE) console.measuredHeight else 0
            val room=roomBelowTopContent(root,viewportHeight,inputHeight+consoleHeight)
            val cap=if(maxDp>0) min(room,Tuils.dpToPx(root.context,maxDp)) else room
            val fitted=fitRoom(requested,cap,room,header,(native || auto) && !autoHide)
            params?.height=constrain(fitted,viewportHeight,state.imeBottom,state.systemBottom,inputHeight+consoleHeight,state.imeVisible || state.imeBottom>0)
        }
        if(params!=null) container.layoutParams=params
        TerminalTrayToggleView.updateText(root.context,toggle,none,landscape,expanded,arrows,native,auto,autoHide,appearance.color(ohi.andre.consolelauncher.managers.xml.options.Theme.tab_text_color))
    }

    private fun trayMode(appearance:HomeAppearance,none:Boolean):String {
        val raw=appearance.raw(Behavior.output_tray_mode)?.trim()?.lowercase(java.util.Locale.US)
        if(raw=="toggled" && none) return "native"
        if(raw in listOf("native","auto","toggled")) return raw!!
        return if(!none && appearance.bool(Behavior.toggle_output_state)) "toggled" else "native"
    }
}
