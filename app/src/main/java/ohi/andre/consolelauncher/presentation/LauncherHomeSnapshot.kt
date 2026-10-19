package ohi.andre.consolelauncher.presentation

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.modules.ModuleDockButtonFactory
import ohi.andre.consolelauncher.managers.xml.options.*
import ohi.andre.consolelauncher.managers.xml.options.Toolbar as ToolbarOptions
import ohi.andre.consolelauncher.tuils.OutlineEditText
import ohi.andre.consolelauncher.tuils.OutlineTextView
import ohi.andre.consolelauncher.tuils.TerminalTrayToggleView
import ohi.andre.consolelauncher.tuils.ThemeColorSpan

/** Frozen launcher content and viewport, rendered by Home's own components. No controllers or views retained. */
internal class LauncherHomeSnapshot private constructor(
    val width:Int,val height:Int,val saved:HomeAppearance,
    private val statuses:StatusPresentation.Content?,private val unified:UnifiedStatusPresentation.Content?,
    private val tray:TerminalTrayPresentation.RuntimeState?,private val insets:Insets,
    private val configuration:Configuration,private val input:CharSequence,private val hint:CharSequence?,
    private val prefix:CharSequence,private val output:CharSequence,private val outputHint:CharSequence?,
    private val clock:CharSequence,private val header:TerminalTrayToggleView.Binding?,
    private val dock:List<DockEntry>,private val toolbar:ToolbarContent,
    private val suggestions:SuggestionsPresentationSnapshot,private val module:ModulePresentationSnapshot?,
    private val sourceScroll:Map<Int,Pair<Int,Int>>,private val canApplyTheme:Boolean,
    private val foldWidth:Int,private val modulePageLandscape:Boolean,private val notesScroll:Pair<Int,Int>?
) {
    data class Insets(val left:Int=0,val top:Int=0,val right:Int=0,val bottom:Int=0)
    private data class DockEntry(val label:CharSequence,val close:Boolean,val selected:Boolean)

    fun create(context:Context):ViewGroup=createDraft(context,saved)

    fun createDraft(context:Context,appearance:HomeAppearance,showIdleModule:Boolean=false):ViewGroup {
        val sceneContext=ContextThemeWrapper(context.createConfigurationContext(configuration),context.theme)
        return LauncherHomePresentation.inflate(sceneContext).also { root ->
            val pager=root.findViewById<ViewGroup>(R.id.view_pager)
            pager.addView(HomeLayoutPresentation.inflateModulesPage(sceneContext,pager,modulePageLandscape),FrameLayout.LayoutParams(-1,-1))
            bindDraft(root,appearance,showIdleModule)
        }
    }

    /** The same scene survives edits and expansion; section binding uses absolute appearance values. */
    fun bindDraft(root:ViewGroup,appearance:HomeAppearance,showIdleModule:Boolean=false) {
        val shownModule=module ?: if(showIdleModule) ModulePresentationSnapshot.idleMusic(root.context) else null
        val initialized=root.getTag(R.id.home_preview_appearance)!=null
        val scroll=if(initialized) scrollPositions(root) else sourceScroll
        val notesOffset=if(initialized) StatusPresentation.notesView(root,root.getTag(R.id.home_preview_appearance) as HomeAppearance,saved)?.let { it.scrollX to it.scrollY } else notesScroll
        HomeLayoutPresentation.restoreClassicModules(root)
        HomeChromePresentation.styleRoot(root,appearance,canApplyTheme)
        for(id in intArrayOf(R.id.terminal_output_border,R.id.input_group,R.id.tools_view,R.id.suggestions_container,R.id.module_suggestions_container))
            root.findViewById<View>(id)?.let { HomeChromePresentation.styleSurface(it,appearance) }
        fun terminal(id:Int,text:CharSequence,hint:CharSequence?,input:Boolean) {
            root.findViewById<TextView>(id)?.let { view ->
                view.text=ThemeColorSpan.refresh(text,appearance::color)
                view.hint=hint
                HomeChromePresentation.styleTerminalText(view,appearance,input)
            }
        }
        terminal(R.id.input_view,input,hint,true)
        terminal(R.id.prefix_view,if(appearance.changed(Ui.input_prefix,saved)) appearance.raw(Ui.input_prefix).orEmpty().let { if(it.endsWith(" ")) it else "$it " } else prefix,null,true)
        terminal(R.id.terminal_view,output,outputHint,false)
        root.findViewById<TextView>(R.id.search_mode_clock_tab)?.text=clock
        root.findViewById<TextView>(R.id.terminal_tray_toggle)?.let { toggle ->
            toggle.setTag(R.id.home_output_header_binding,header?:TerminalTrayToggleView.Binding(false,false))
            TerminalTrayToggleView.styleDraft(toggle,root,appearance,saved)
        }
        statuses?.let { StatusPresentation.renderRows(root,it,appearance,saved) }
        unified?.let { UnifiedStatusPresentation.style(root,appearance);UnifiedStatusPresentation.bindContent(root,it,appearance,saved) }
        root.findViewById<LinearLayout>(R.id.module_dock)?.let { host ->
            host.removeAllViews()
            dock.forEach { entry -> host.addView(ModuleDockButtonFactory.createPresentation(root.context,entry.label,entry.close,
                appearance.number(Ui.module_dock_spacing_dp).toInt(),entry.selected,appearance)) }
        }
        suggestions.bind(root,appearance)
        root.findViewById<ViewGroup>(R.id.home_modules_container)?.let { host ->
            host.removeAllViews()
            shownModule?.bind(host,appearance)
        }
        HomeLayoutPresentation.bindScene(root,appearance,insets,tray)
        toolbar.bind(root,appearance)
        root.findViewById<View>(R.id.landscape_fold_gutter)?.let { fold ->
            fold.layoutParams=fold.layoutParams.apply { width=foldWidth }
            fold.visibility=if(foldWidth>0) View.VISIBLE else View.GONE
        }
        makePassive(root,appearance)
        TerminalTrayPresentation.prepare(root,appearance)
        layout(root)
        shownModule?.afterLayout(root)
        HomeLayoutPresentation.finishDuoLayout(root,appearance,insets,tray)
        layout(root)
        tray?.let { TerminalTrayPresentation.apply(root,it,appearance,height) }
        layout(root)
        // Android normally runs this during traversal. Detached scaled scenes have no ViewRoot traversal.
        fun preDraw(view:View) {
            if(view is TextView) view.onPreDraw()
            if(view is ViewGroup) for(i in 0 until view.childCount) preDraw(view.getChildAt(i))
        }
        shownModule?.afterLayout(root)
        UnifiedStatusPresentation.positionModulesToggle(root)
        preDraw(root)
        suggestions.restoreScroll(root)
        scroll.forEach { (id,offset) -> root.findViewById<View>(id)?.scrollTo(offset.first,offset.second) }
        notesOffset?.let { StatusPresentation.notesView(root,appearance,saved)?.scrollTo(it.first,it.second) }
        root.setTag(R.id.home_preview_appearance,appearance)
    }

    private fun layout(root:View) {
        root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY))
        root.layout(0,0,width,height)
    }

    private fun makePassive(view:View,appearance:HomeAppearance) {
        if(view is EditText) {
            view.isCursorVisible=false
            view.showSoftInputOnFocus=false
            view.setTextIsSelectable(false)
            if(view is OutlineEditText) view.setIdleCursorVisible(false)
        } else if(view is TextView) view.setTextIsSelectable(false)
        view.isClickable=false;view.isLongClickable=false;view.isFocusable=false;view.isFocusableInTouchMode=false
        if(view is OutlineTextView) view.redrawPassesOverride=appearance.number(Ui.text_redraw_times,1f).toInt().coerceIn(1,8)
        if(view is ViewGroup) for(i in 0 until view.childCount) makePassive(view.getChildAt(i),appearance)
    }

    companion object {
        private val scrollIds=intArrayOf(R.id.input_view,R.id.terminal_output_border,R.id.terminal_view,R.id.suggestions_container,R.id.module_dock_scroll,
            R.id.home_modules_root,R.id.search_mode_results_scroll,R.id.unified_module_scroll,R.id.module_suggestions_container,
            R.id.module_text_border,R.id.notification_scroll)
        private fun scrollPositions(root:View)=scrollIds.asIterable().mapNotNull { id -> root.findViewById<View>(id)?.let { id to (it.scrollX to it.scrollY) } }.toMap()
        fun capture(root:ViewGroup,statuses:StatusPresentation.Content?=null,unified:UnifiedStatusPresentation.Content?=null,
                    tray:TerminalTrayPresentation.RuntimeState?=null,insets:Insets=Insets(root.paddingLeft,0,root.paddingRight,root.paddingBottom),
                    configuration:Configuration=root.resources.configuration):LauncherHomeSnapshot? {
            if(root.width<=0 || root.height<=0) return null
            fun text(id:Int)=root.findViewById<TextView>(id)?.let { ReadOnlyStyledText.freeze(it.text?:"",it.paint) }?:""
            fun hint(id:Int)=root.findViewById<TextView>(id)?.let { view -> view.hint?.let { ReadOnlyStyledText.freeze(it,view.paint) } }
            val host=root.findViewById<ViewGroup>(R.id.module_dock)
            val entries=if(host==null) emptyList() else (0 until host.childCount).mapNotNull { (host.getChildAt(it) as? TextView)?.let { button ->
                val label=ReadOnlyStyledText.freeze(button.text.toString().removePrefix("✓ "))
                DockEntry(label,label.toString()=="X",button.getTag(R.id.home_dock_binding) as? Boolean?:button.isSelected)
            } }
            return LauncherHomeSnapshot(root.width,root.height,HomeAppearance.saved(),statuses,unified,tray,insets,Configuration(configuration),
                text(R.id.input_view),hint(R.id.input_view),text(R.id.prefix_view),text(R.id.terminal_view),hint(R.id.terminal_view),text(R.id.search_mode_clock_tab),
                root.findViewById<View>(R.id.terminal_tray_toggle)?.getTag(R.id.home_output_header_binding) as? TerminalTrayToggleView.Binding,
                entries,ToolbarContent.capture(root),SuggestionsPresentationSnapshot.capture(root),ModulePresentationSnapshot.capture(root),
                scrollPositions(root),root.getTag(R.id.home_root_binding) as? Boolean?:true,
                root.findViewById<View>(R.id.landscape_fold_gutter)?.takeIf { it.visibility==View.VISIBLE }?.width?:0,root.findViewById<View>(R.id.home_modules_content_scroll)!=null,
                StatusPresentation.notesView(root,HomeAppearance.saved())?.let { it.scrollX to it.scrollY })
        }
    }

    /** Only toolbar icons are retained, as immutable drawable constant states; never their action targets. */
    private class ToolbarContent(private val icons:List<Icon>,private val visible:Boolean,private val sourceSearch:Boolean) {
        private data class Icon(val id:Int,val image:Drawable.ConstantState?,val visible:Int)
        fun bind(root:ViewGroup,appearance:HomeAppearance) {
            val host=root.findViewById<LinearLayout>(R.id.tools_view)?:return
            // Default controls come from the actual XML. Added shortcut icons have no callbacks or command payload.
            while(host.childCount>7) host.removeViewAt(host.childCount-1)
            icons.forEach { icon ->
                val view=if(icon.id!=View.NO_ID) root.findViewById<ImageView>(icon.id) else ImageButton(root.context).also { button ->
                    val pad=root.resources.getDimensionPixelSize(R.dimen.tools_padding)
                    button.setPadding(pad,pad,pad,pad);button.scaleType=ImageView.ScaleType.FIT_CENTER
                    host.addView(button,LinearLayout.LayoutParams(0,-1,1f))
                }
                if(view!=null) {
                    view.setImageDrawable(icon.image?.newDrawable(root.resources)?.mutate())
                    view.visibility=icon.visible
                    HomeChromePresentation.styleToolbarIcon(view,appearance)
                    HomeChromePresentation.styleToolbarButton(view,appearance)
                }
            }
            host.weightSum=(0 until host.childCount).count { host.getChildAt(it).visibility!=View.GONE }.toFloat()
            host.visibility=if((visible || sourceSearch) && appearance.bool(ToolbarOptions.show_toolbar) && !HomeLayoutPresentation.isSearch(appearance)) View.VISIBLE else View.GONE
            root.findViewById<ImageView>(R.id.submit_tv)?.let { submit ->
                submit.visibility=if(appearance.bool(Ui.show_enter_button)) View.VISIBLE else View.GONE
                HomeChromePresentation.styleToolbarIcon(submit,appearance)
            }
        }
        companion object {
            fun capture(root:ViewGroup):ToolbarContent {
                val host=root.findViewById<ViewGroup>(R.id.tools_view)
                return ToolbarContent(if(host==null) emptyList() else (0 until host.childCount).mapNotNull { index ->
                    (host.getChildAt(index) as? ImageView)?.let { Icon(it.id,it.drawable?.constantState,it.visibility) }
                },host?.visibility==View.VISIBLE,root.findViewById<View>(R.id.search_mode_results_scroll)?.visibility==View.VISIBLE)
            }
        }
    }
}

/** The actual launcher layout with a passive single page and frozen clock in the preview. */
internal object LauncherHomePresentation {
    fun inflateInto(context:Context,parent:ViewGroup) { LayoutInflater.from(context).inflate(R.layout.base_view,parent,true) }
    fun inflate(context:Context,parent:ViewGroup?=null):ViewGroup {
        val root=FrameLayout(context).apply { id=R.id.mainview }
        val inflater=LayoutInflater.from(context).cloneInContext(context)
        inflater.factory2=object:LayoutInflater.Factory2 {
            override fun onCreateView(parent:View?,name:String,context:Context,attrs:android.util.AttributeSet):View?=when(name) {
                "androidx.viewpager2.widget.ViewPager2" -> FrameLayout(context,attrs)
                "TextClock" -> TextView(context,attrs)
                "ohi.andre.consolelauncher.tuils.OutlineEditText" -> OutlineTextView(context,attrs,androidx.appcompat.R.attr.editTextStyle).apply {
                    ellipsize=null;setHorizontallyScrolling(true)
                }
                else -> null
            }
            override fun onCreateView(name:String,context:Context,attrs:android.util.AttributeSet)=onCreateView(null,name,context,attrs)
        }
        inflater.inflate(R.layout.launcher_home_scene,root,true)
        parent?.addView(root)
        return root
    }
}
