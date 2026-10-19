package ohi.andre.consolelauncher.presentation

import android.content.Context
import android.graphics.PorterDuff
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ScrollView
import android.widget.LinearLayout
import android.widget.ImageButton
import android.widget.TextView
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.tuils.*

/** Native module XML and chrome shared by the live controller and passive content binder. */
internal object ModulePanelPresentation {
    enum class Kind(val layout:Int,val border:Int,val label:Int,val close:Int,val target:FrameTarget) {
        MUSIC(R.layout.music_module,R.id.music_module_border,R.id.music_module_label,R.id.music_module_close,FrameTarget.MUSIC),
        TEXT(R.layout.module_text_panel,R.id.module_text_border,R.id.module_text_label,R.id.module_text_close,FrameTarget.MODULES),
        NOTIFICATIONS(R.layout.notification_module,R.id.notification_module_border,R.id.notification_module_label,R.id.notification_module_close,FrameTarget.NOTIFICATIONS)
    }
    fun inflate(context:Context,parent:ViewGroup?,kind:Kind):View = LayoutInflater.from(context).inflate(kind.layout,parent,false)
    fun chrome(root:View,kind:Kind,appearance:HomeAppearance,binding:TuiWidgetDecorator.Binding?=null) {
        val actual=binding?:TuiWidgetDecorator.Binding(kind.border,kind.label,kind.close,kind.target,
            appearance.color(Theme.module_panel_border_color),appearance.color(Theme.module_panel_text_color),true,true)
        TuiWidgetDecorator.decorateDraft(root,actual,appearance)
        root.findViewById<TextView>(kind.close)?.let { ModuleContentPresentation.close(it,appearance,kind.target) }
    }
    fun musicContent(root:View,appearance:HomeAppearance,layoutVisualizer:Boolean=true) {
        for(id in intArrayOf(R.id.music_song_title,R.id.music_singer,R.id.music_app_prefix,R.id.music_app_label)) root.findViewById<TextView>(id)?.let {
            ModuleContentPresentation.musicText(it,appearance)
            it.includeFontPadding=true
            if(id==R.id.music_app_prefix || id==R.id.music_app_label) it.setSingleLine(true)
            if(id==R.id.music_app_label) it.ellipsize=TextUtils.TruncateAt.END
        }
        for(id in intArrayOf(R.id.music_prev,R.id.music_next,R.id.music_play_pause)) root.findViewById<TextView>(id)?.let {
            ModuleContentPresentation.action(it,appearance,FrameTarget.MUSIC,true)
            it.includeFontPadding=true;it.setSingleLine(true);it.ellipsize=TextUtils.TruncateAt.END
        }
        if(layoutVisualizer) sizeVisualizer(root)
    }
    fun applyVisualizerSize(root:View) {
        val visualizer=root.findViewById<View>(R.id.music_visualizer)?:return
        val border=root.findViewById<View>(R.id.music_module_border)?:return
        val height=border.height-border.paddingTop-border.paddingBottom
        if(height>0 && visualizer.layoutParams.height!=height) visualizer.layoutParams=visualizer.layoutParams.apply {this.height=height}
    }
    fun sizeVisualizer(root:View) {
        val visualizer=root.findViewById<View>(R.id.music_visualizer)?:return
        val border=root.findViewById<View>(R.id.music_module_border)?:return
        if(border.isLaidOut && !border.isLayoutRequested) applyVisualizerSize(root)
        else border.addOnLayoutChangeListener(object:View.OnLayoutChangeListener {
            override fun onLayoutChange(v:View,l:Int,t:Int,r:Int,b:Int,ol:Int,ot:Int,or:Int,ob:Int) {
                if(b-t>0) { v.removeOnLayoutChangeListener(this);applyVisualizerSize(root) }
            }
        })
    }
    data class ScrollPolicy(val lines:Int,val paragraphs:Boolean)
    data class NotificationSize(val compact:Boolean)
    fun limitTextScroll(scroll:ScrollView,policy:ScrollPolicy) {
        scroll.setTag(R.id.home_module_scroll_policy,policy)
        scroll.isFillViewport=false;scroll.overScrollMode=View.OVER_SCROLL_IF_CONTENT_SCROLLS
        onceMeasured(scroll) { applyTextScroll(scroll,policy) }
    }
    fun applyTextScroll(scroll:ScrollView,policy:ScrollPolicy) {
        val child=scroll.getChildAt(0)?:return
        val texts=if(policy.paragraphs && child is ViewGroup) (0 until child.childCount).mapNotNull { child.getChildAt(it) as? TextView }
            else listOfNotNull(child as? TextView)
        val line=texts.maxOfOrNull { it.lineHeight }?:0
        val lineHeight=if(policy.paragraphs) maxOf(Tuils.dpToPx(scroll.context,18),line) else line
        val padding=scroll.paddingTop+scroll.paddingBottom
        val maxHeight=lineHeight*policy.lines+padding
        val content=child.height+padding
        if(content>0 && maxHeight>0) {
            val target=maxOf(scroll.minimumHeight,minOf(content,maxHeight))
            if(scroll.layoutParams.height!=target) scroll.layoutParams=scroll.layoutParams.apply { height=target }
            scroll.isVerticalScrollBarEnabled=content>target
        }
    }
    fun notificationSize(root:View,compact:Boolean) {
        root.setTag(R.id.home_module_scroll_policy,NotificationSize(compact))
        root.findViewById<View>(R.id.notification_module_border)?.let { border ->
            if(border.layoutParams.height!=-2) border.layoutParams=border.layoutParams.apply {height=-2}
            border.minimumHeight=Tuils.dpToPx(root.context,if(compact)104 else 112)
        }
        root.findViewById<ScrollView>(R.id.notification_scroll)?.let { scroll ->
            if(scroll.layoutParams.height!=-2) scroll.layoutParams=scroll.layoutParams.apply {height=-2}
            scroll.isFillViewport=false
        }
        root.setPadding(root.paddingLeft,root.paddingTop,root.paddingRight,Tuils.dpToPx(root.context,if(compact)6 else 12))
    }
    fun notificationMaxHeight(context:Context,rootHeight:Int,compact:Boolean):Int {
        val fallback=Tuils.dpToPx(context,if(compact)92 else 180)
        if(rootHeight<=0) return fallback
        val floor=Tuils.dpToPx(context,if(compact)58 else 96)
        return minOf(fallback,maxOf(floor,Math.round(rootHeight * if(compact) 0.16f else 0.28f)))
    }
    fun limitNotifications(scroll:ScrollView,maximum:Int) = onceMeasured(scroll) { applyNotificationLimit(scroll,maximum) }
    fun applyNotificationLimit(scroll:ScrollView,maximum:Int) {
        val child=scroll.getChildAt(0)?:return
        val content=child.height+scroll.paddingTop+scroll.paddingBottom
        if(content>0) {
            val target=if(content>maximum) maximum else ViewGroup.LayoutParams.WRAP_CONTENT
            if(scroll.layoutParams.height!=target) scroll.layoutParams=scroll.layoutParams.apply {height=target}
            scroll.isFillViewport=target!=ViewGroup.LayoutParams.WRAP_CONTENT
            scroll.isVerticalScrollBarEnabled=content>maximum
        }
    }
    private fun onceMeasured(view:View,apply:()->Unit) {
        view.viewTreeObserver.addOnPreDrawListener(object:ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw():Boolean {
                if(view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnPreDrawListener(this)
                apply();return true
            }
        })
    }
    fun notificationPager(button:TextView,appearance:HomeAppearance) {
        ModuleContentPresentation.notificationRow(button,appearance,appearance.color(Theme.module_panel_text_color),appearance.color(Theme.module_panel_border_color),true)
        button.setTypeface(appearance.font(button.context),android.graphics.Typeface.BOLD)
    }
    fun notificationSettings(button:ImageButton,appearance:HomeAppearance) {
        button.setColorFilter(appearance.color(Theme.module_panel_text_color),PorterDuff.Mode.SRC_IN)
        button.background=TerminalBorderRuntime.tabDrawable(button.context,appearance.color(Theme.module_header_control_background_color),
            appearance.color(Theme.terminal_header_border_color),target=FrameTarget.NOTIFICATIONS,style=appearance.borderStyle(),
            draftDashed=appearance.bool(ohi.andre.consolelauncher.managers.xml.options.Ui.enable_dashed_border))
    }
}
