package ohi.andre.consolelauncher.presentation

import android.content.Context
import android.content.res.Configuration
import android.util.DisplayMetrics
import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.settings.LauncherMode
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.managers.xml.options.Toolbar
import ohi.andre.consolelauncher.managers.xml.options.Suggestions
import ohi.andre.consolelauncher.managers.xml.options.SurfaceBorder
import ohi.andre.consolelauncher.managers.xml.options.SurfaceBorderOption
import androidx.viewpager2.widget.ViewPager2
import ohi.andre.consolelauncher.tuils.FrameTarget
import ohi.andre.consolelauncher.tuils.TerminalBorderRuntime

/** View layout only; Home keeps ownership of navigation, providers and command execution. */
internal object HomeLayoutPresentation {
    fun isSearch(appearance: HomeAppearance)=LauncherMode.resolve(appearance.raw(Behavior.launcher_mode),
        appearance.bool(Behavior.search_only_mode))==LauncherMode.SEARCH

    fun inflateModulesPage(context:Context,parent:ViewGroup?,landscapeVariant:Boolean?=null):View {
        val pageContext=if(landscapeVariant==null) context else android.view.ContextThemeWrapper(
            context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                orientation=if(landscapeVariant) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
            }),context.theme)
        return LayoutInflater.from(pageContext).inflate(R.layout.home_modules_page,parent,false)
    }

    /** Only changes parenting; live Home retains module lifecycles and visibility timers. */
    fun attachUnified(root:ViewGroup):Boolean {
        val header=root.findViewById<View>(R.id.header_container)?:return false
        val dock=root.findViewById<View>(R.id.module_dock_scroll)?:return false
        val modules=root.findViewById<View>(R.id.home_modules_container)?:return false
        val statusHost=root.findViewById<ViewGroup>(R.id.unified_status_host)?:return false
        val dockHost=root.findViewById<ViewGroup>(R.id.unified_dock_host)?:return false
        val moduleHost=root.findViewById<ViewGroup>(R.id.unified_module_host)?:return false
        move(header,statusHost,FrameLayout.LayoutParams(-1,-2),replace=true)
        if(dock.parent!==dockHost) dock.setPadding(dock.paddingLeft,dock.paddingBottom,dock.paddingRight,dock.paddingBottom)
        move(dock,dockHost,FrameLayout.LayoutParams(-1,-2),replace=true)
        move(modules,moduleHost,FrameLayout.LayoutParams(-1,-2),replace=true)
        root.findViewById<View>(R.id.unified_bottom_console)?.visibility=View.VISIBLE
        root.findViewById<View>(R.id.main_container)?.visibility=View.GONE
        return true
    }

    fun attachPortrait(root:ViewGroup,mainParams:FrameLayout.LayoutParams?=null,trayParams:FrameLayout.LayoutParams?=null,
                       mainView:View?=null,trayView:View?=null) {
        val main=mainView?:root.findViewById<View>(R.id.main_container)?:return
        val tray=trayView?:root.findViewById<View>(R.id.terminal_tray_container)?:return
        detach(main);detach(tray)
        root.findViewById<View>(R.id.landscape_split_container)?.visibility=View.GONE
        root.addView(main,0,mainParams?:FrameLayout.LayoutParams(-1,-1))
        root.addView(tray,1.coerceAtMost(root.childCount),trayParams?:FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM))
    }

    fun attachLandscape(root:ViewGroup,mainView:View?=null,trayView:View?=null) {
        val main=mainView?:root.findViewById<View>(R.id.main_container)?:return
        val tray=trayView?:root.findViewById<View>(R.id.terminal_tray_container)?:return
        val split=root.findViewById<View>(R.id.landscape_split_container)?:return
        val left=root.findViewById<ViewGroup>(R.id.landscape_left_pane)?:return
        val right=root.findViewById<ViewGroup>(R.id.landscape_right_pane)?:return
        move(main,left,FrameLayout.LayoutParams(-1,-1))
        move(tray,right,FrameLayout.LayoutParams(-1,-1))
        split.visibility=View.VISIBLE
        if(split.parent!==root) { detach(split);root.addView(split,0) }
    }

    /** Shared Duo parenting. Controllers supply detached live views; previews find their own scene views. */
    fun attachDuo(root:ViewGroup,side:String,appearance:HomeAppearance,imeBottom:Int,
                  mainView:View?=null,trayView:View?=null,suggestionsView:View?=null) {
        val main=mainView?:root.findViewById<View>(R.id.main_container)?:return
        val tray=trayView?:root.findViewById<View>(R.id.terminal_tray_container)?:return
        val left=root.findViewById<ViewGroup>(R.id.landscape_left_pane)?:return
        val right=root.findViewById<ViewGroup>(R.id.landscape_right_pane)?:return
        val swapped=appearance.bool(Behavior.duo_swap_top_panes)
        val target=if(side=="left") left else right
        val modules=if((side=="left")!=swapped) left else right
        move(main,modules,FrameLayout.LayoutParams(-1,-1))
        move(tray,target,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM))
        if(appearance.bool(Ui.split_duo_launcher) || swapped) root.findViewById<View>(R.id.header_container)?.let {
            move(it,if(swapped) target else duoEmptyPane(root,side)!!,FrameLayout.LayoutParams(-1,-2,Gravity.TOP or Gravity.START))
        }
        (suggestionsView?:root.findViewById<View>(R.id.module_suggestions_container))?.let { strip ->
            val margin=ohi.andre.consolelauncher.tuils.Tuils.dpToPx(root.context,18)
            move(strip,modules,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM).apply {
                setMargins(margin,0,margin,margin+ohi.andre.consolelauncher.tuils.Tuils.dpToPx(root.context,56)+imeBottom)
            })
        }
        root.findViewById<View>(R.id.landscape_split_container)?.let { split ->
            split.visibility=View.VISIBLE
            if(split.parent!==root) { detach(split);root.addView(split,0) }
        }
    }

    fun duoEmptyPane(root:View,side:String):ViewGroup? = root.findViewById(
        if(side=="right") R.id.landscape_left_pane else R.id.landscape_right_pane)

    /** Header height is known after the first layout; apply the same section margins on both surfaces. */
    fun finishDuoLayout(root:ViewGroup,appearance:HomeAppearance,insets:LauncherHomeSnapshot.Insets,
                        state:TerminalTrayPresentation.RuntimeState?) {
        if(state?.duo!=true || isSearch(appearance) || appearance.bool(SurfaceBorderOption.unified_status_border) ||
            root.findViewById<View>(R.id.landscape_split_container)?.visibility!=View.VISIBLE) return
        fun margins(key:Ui)=appearance.raw(key).orEmpty().replace(Regex("[\\[\\]\\s]"),"").split(',').let { parts ->
            IntArray(4) { parts.getOrNull(it)?.toIntOrNull()?:0 }
        }
        val top=margins(Ui.display_margin_top_section)
        val bottom=margins(if(state.keyboardMarginsOverride && appearance.raw(Ui.keyboard_visible_margin_override)!=Ui.keyboard_visible_margin_override.defaultValue())
            Ui.keyboard_visible_margin_override else Ui.display_margin_bottom_section)
        val metrics=root.resources.displayMetrics
        val swapped=appearance.bool(Behavior.duo_swap_top_panes)
        val split=appearance.bool(Ui.split_duo_launcher)||swapped
        applySectionMargins(root.findViewById(R.id.main_container),top,metrics,
            if(swapped) ohi.andre.consolelauncher.tuils.Tuils.dpToPx(root.context,84)+state.imeBottom else 0,insets.top)
        if(split) applySectionMargins(root.findViewById(R.id.header_container),top,metrics,0,insets.top)
        val trayTop=if(swapped) insets.top+ohi.andre.consolelauncher.tuils.Tuils.mmToPx(metrics,top[1])+
            (root.findViewById<View>(R.id.header_container)?.height?:0) else insets.top
        applySectionMargins(root.findViewById(R.id.terminal_tray_container),bottom,metrics,state.imeBottom,trayTop)
    }

    private fun restoreDuoSuggestions(root:ViewGroup,appearance:HomeAppearance) {
        root.findViewById<View>(R.id.home_duo_controls)?.let(::detach)
        val input=root.findViewById<ViewGroup>(R.id.persistent_input_container)?:return
        val strip=root.findViewById<View>(R.id.module_suggestions_container)?:return
        if(strip.parent!==input) {
            detach(strip)
            val before=root.findViewById<View>(R.id.input_group)
            input.addView(strip,if(before?.parent===input) input.indexOfChild(before) else 0,LinearLayout.LayoutParams(-1,-2))
            HomeChromePresentation.styleSurface(strip,appearance)
        }
    }

    fun restoreClassicModules(root:ViewGroup) {
        val main=root.findViewById<ViewGroup>(R.id.main_container)?:return
        root.findViewById<View>(R.id.header_container)?.let { header ->
            if(header.parent!==main) { detach(header);main.addView(header,0,LinearLayout.LayoutParams(-1,-2)) }
        }
        val existingDock=root.findViewById<View>(R.id.module_dock_scroll)
        val existingModules=root.findViewById<View>(R.id.home_modules_container)
        var page=root.findViewById<ViewGroup>(R.id.home_modules_root)
        if(page==null) {
            val pager=root.findViewById<ViewGroup>(R.id.view_pager)?:return
            // Frozen pagers are plain containers. Live Home always uses its existing pager adapter.
            if(pager is ViewPager2) return
            page=inflateModulesPage(root.context,pager) as ViewGroup
            if(existingDock!=null) page.findViewById<View>(R.id.module_dock_scroll)?.let(::detach)
            if(existingModules!=null) page.findViewById<View>(R.id.home_modules_container)?.let(::detach)
            pager.addView(page,FrameLayout.LayoutParams(-1,-1))
        }
        val host=if(page is LinearLayout) page else page.getChildAt(0) as? ViewGroup?:return
        existingDock?.let { dock ->
            if(dock.parent!==host) { detach(dock);host.addView(dock,0,LinearLayout.LayoutParams(-1,-2));dock.setPadding(dock.paddingLeft,0,dock.paddingRight,dock.paddingBottom) }
        }
        existingModules?.let { modules ->
            move(modules,host,LinearLayout.LayoutParams(-1,-2))
        }
    }

    /** Absolute layout from explicit values: repeated draft binds never accumulate margins or moves. */
    fun bindScene(root:ViewGroup,appearance:HomeAppearance,insets:LauncherHomeSnapshot.Insets,runtime:TerminalTrayPresentation.RuntimeState?) {
        val main=root.findViewById<View>(R.id.main_container)?:return
        val tray=root.findViewById<View>(R.id.terminal_tray_container)?:return
        restoreDuoSuggestions(root,appearance)
        attachPortrait(root,mainView=main,trayView=tray)
        restoreClassicModules(root)
        val search=isSearch(appearance)
        val unified=appearance.bool(SurfaceBorderOption.unified_status_border)
        val landscape=root.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE && appearance.number(Behavior.orientation).toInt()!=1
        main.visibility=View.VISIBLE
        root.findViewById<View>(R.id.search_mode_results_scroll)?.visibility=View.GONE
        root.findViewById<View>(R.id.search_mode_clock_tab)?.visibility=View.GONE
        root.findViewById<View>(R.id.terminal_container)?.visibility=View.VISIBLE
        root.findViewById<View>(R.id.unified_bottom_console)?.visibility=View.GONE
        root.findViewById<View>(R.id.unified_status_container)?.visibility=if(unified) View.VISIBLE else View.GONE
        val dockVisible=if(appearance.bool(Behavior.show_module_dock)) View.VISIBLE else View.GONE
        root.findViewById<View>(R.id.module_dock_scroll)?.visibility=dockVisible
        root.findViewById<View>(R.id.tools_view)?.visibility=if(appearance.bool(Toolbar.show_toolbar)) View.VISIBLE else View.GONE
        suggestionsVisibility(root,appearance)
        if(search) configureSearch(root,appearance)
        else if(unified) {
            for(id in intArrayOf(R.id.tv0,R.id.tv1,R.id.tv2,R.id.tv3,R.id.tv4,R.id.tv5,R.id.tv6,R.id.tv7,R.id.tv8,R.id.tv9)) root.findViewById<View>(id)?.visibility=View.GONE
            attachUnified(root)
            root.findViewById<View>(R.id.unified_dock_host)?.visibility=dockVisible
            root.findViewById<View>(R.id.unified_modules_toggle)?.visibility=dockVisible
            root.findViewById<View>(R.id.unified_module_scroll)?.visibility=if((root.findViewById<ViewGroup>(R.id.home_modules_container)?.childCount?:0)>0) View.VISIBLE else View.GONE
        } else if(landscape && runtime?.duo==true) {
            attachDuo(root,runtime.duoSide,appearance,runtime.imeBottom)
            duoEmptyPane(root,runtime.duoSide)?.let { DuoControlsPresentation.attach(it,runtime.duoSide,appearance,runtime.imeBottom) }
        } else if(landscape) attachLandscape(root)
        styleStatusGroup(root,appearance)
        root.setPadding(insets.left,0,insets.right,insets.bottom)
        fun margins(key:Ui)=appearance.raw(key).orEmpty().replace(Regex("[\\[\\]\\s]"),"").split(',').let { values -> IntArray(4) { values.getOrNull(it)?.toIntOrNull()?:0 } }
        val landscapeMargins=landscape && runtime?.duo!=true && appearance.raw(Ui.display_margin_landscape_mm)!=Ui.display_margin_landscape_mm.defaultValue()
        val top=margins(if(landscapeMargins) Ui.display_margin_landscape_mm else Ui.display_margin_top_section)
        val bottom=margins(if(runtime?.keyboardMarginsOverride==true && appearance.raw(Ui.keyboard_visible_margin_override)!=Ui.keyboard_visible_margin_override.defaultValue()) Ui.keyboard_visible_margin_override
            else if(landscapeMargins) Ui.display_margin_landscape_mm else Ui.display_margin_bottom_section)
        applySectionMargins(main,top,root.resources.displayMetrics,0,insets.top)
        val edge=searchEdgeInset(root.context,search)
        applySectionMargins(tray,bottom,root.resources.displayMetrics,(runtime?.imeBottom?:0)+edge,if(landscape) insets.top else 0,edge)
    }

    fun suggestionsVisibility(root:View,appearance:HomeAppearance) {
        val visibility=if(appearance.bool(Suggestions.show_suggestions)) View.VISIBLE else View.GONE
        root.findViewById<View>(R.id.suggestions_container)?.visibility=visibility
        root.findViewById<View>(R.id.suggestions_group)?.visibility=visibility
    }

    fun styleStatusGroup(root:View,appearance:HomeAppearance) {
        val header=root.findViewById<ViewGroup>(R.id.header_container)?:return
        val parent=(header.findViewById<View>(R.id.unified_status_container)?.parent as? ViewGroup)
            ?:header.getChildAt(0) as? ViewGroup?:return
        val unified=appearance.bool(SurfaceBorderOption.unified_status_border)
        val inset=if(unified) ohi.andre.consolelauncher.tuils.Tuils.dpToPx(root.context,4) else 0
        parent.setPadding(0,inset,0,inset)
        (parent.layoutParams as? ViewGroup.MarginLayoutParams)?.let { it.setMargins(0,0,0,0);parent.layoutParams=it }
        parent.background=if(!unified) null else TerminalBorderRuntime.panelDrawablePx(root.context,
            appearance.color(Theme.unified_status_background_color),appearance.color(Theme.terminal_border_color),1.5f,
            ohi.andre.consolelauncher.tuils.Tuils.dpToPx(root.context,appearance.radius(Ui.module_corner_radius)).toFloat(),
            appearance.bool(Ui.enable_dashed_border),false,true,true,FrameTarget.STATUS_GROUP,appearance.borderStyle())
    }

    private fun detach(view:View) { (view.parent as? ViewGroup)?.removeView(view) }
    private fun move(view:View,parent:ViewGroup,params:ViewGroup.LayoutParams,replace:Boolean=false) {
        if(view.parent===parent) return
        detach(view)
        if(replace) parent.removeAllViews()
        parent.addView(view,params)
    }
    fun searchEdgeInset(context:Context,search:Boolean)=if(search) ohi.andre.consolelauncher.tuils.Tuils.dpToPx(context,6) else 0

    fun applySectionMargins(view:View?,marginMm:IntArray,metrics:DisplayMetrics?,extraBottom:Int,extraTop:Int=0,extraHorizontal:Int=0) {
        view?:return
        val left=ohi.andre.consolelauncher.tuils.Tuils.mmToPx(metrics,marginMm[0])+extraHorizontal
        val top=extraTop+ohi.andre.consolelauncher.tuils.Tuils.mmToPx(metrics,marginMm[1])
        val right=ohi.andre.consolelauncher.tuils.Tuils.mmToPx(metrics,marginMm[2])+extraHorizontal
        val bottom=ohi.andre.consolelauncher.tuils.Tuils.mmToPx(metrics,marginMm[3])+extraBottom
        val params=view.layoutParams
        if(params is ViewGroup.MarginLayoutParams) {
            if(params.leftMargin==left && params.topMargin==top && params.rightMargin==right && params.bottomMargin==bottom) return
            params.setMargins(left,top,right,bottom);view.layoutParams=params
        } else if(view.paddingLeft!=left || view.paddingTop!=top || view.paddingRight!=right || view.paddingBottom!=bottom)
            view.setPadding(left,top,right,bottom)
    }

    fun configureSearch(root: ViewGroup,appearance: HomeAppearance) {
        val tray=root.findViewById<View>(R.id.terminal_tray_container) ?: return
        // Home restores portrait parenting before this step; a detached landscape scene needs it too.
        if(tray.parent!==root) {
            (tray.parent as? ViewGroup)?.removeView(tray)
            root.addView(tray,FrameLayout.LayoutParams(-1,-1,Gravity.BOTTOM))
        }
        for(id in intArrayOf(R.id.main_container,R.id.unified_bottom_console,R.id.landscape_split_container,
            R.id.terminal_container,R.id.module_suggestions_container,R.id.tools_view,R.id.suggestions_container)) {
            root.findViewById<View>(id)?.visibility=View.GONE
        }
        root.findViewById<View>(R.id.search_mode_results_scroll)?.visibility=View.VISIBLE
        root.findViewById<View>(R.id.input_group)?.let { input ->
            val radius=ohi.andre.consolelauncher.tuils.Tuils.dpToPx(root.context,appearance.radius(Ui.dashed_border_corner_radius)).toFloat()
            val border=SurfaceBorder.resolveColor(appearance.raw(SurfaceBorder.INPUT.color),appearance.color(Theme.terminal_border_color))
            val dashed=appearance.bool(Ui.enable_dashed_border)
            input.background=TerminalBorderRuntime.panelDrawablePx(root.context,appearance.color(Theme.input_background_color),
                border,1.5f,radius,dashed,false,borderEnabled=false,frameEnabled=false,target=FrameTarget.INPUT,style=appearance.borderStyle())
            input.foreground=TerminalBorderRuntime.panelDrawablePx(root.context,android.graphics.Color.TRANSPARENT,
                border,1.5f,radius,dashed,false,
                borderEnabled=appearance.bool(Behavior.enable_cyberdeck_mode) || (dashed && appearance.bool(SurfaceBorder.INPUT.enabled)),
                target=FrameTarget.INPUT,style=appearance.borderStyle())
        }
        styleSearchClock(root,appearance)
        (tray.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            params.width=ViewGroup.LayoutParams.MATCH_PARENT
            params.height=ViewGroup.LayoutParams.MATCH_PARENT
            params.gravity=Gravity.BOTTOM
            tray.layoutParams=params
        }
        root.findViewById<TextView>(R.id.input_view)?.setHint(R.string.search_mode_hint)
    }

    fun styleSearchClock(root: View,appearance: HomeAppearance) {
        val clock=root.findViewById<TextView>(R.id.search_mode_clock_tab) ?: return
        val input=root.findViewById<View>(R.id.input_group)
        clock.visibility=View.VISIBLE
        clock.setTypeface(appearance.font(root.context),Typeface.BOLD)
        ohi.andre.consolelauncher.tuils.LauncherFontScale.setBaseSize(clock,12f,appearance.number(Ui.font_size_offset).toInt())
        clock.setTextColor(appearance.color(Theme.time_text_color))
        clock.background=TerminalBorderRuntime.tabDrawable(root.context,appearance.color(Theme.terminal_header_background_color),
            appearance.color(Theme.terminal_header_border_color),target=FrameTarget.INPUT,
            style=appearance.borderStyle(),draftDashed=appearance.bool(Ui.enable_dashed_border))
        val margins=appearance.raw(Ui.input_area_margins).orEmpty().replace(Regex("[\\[\\]\\s]"),"").split(',')
        (clock.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            params.bottomMargin=-(margins.getOrNull(1)?.toIntOrNull() ?: 0)
            clock.layoutParams=params
        }
        TerminalBorderRuntime.bindPresentation(input,clock)
    }
}
