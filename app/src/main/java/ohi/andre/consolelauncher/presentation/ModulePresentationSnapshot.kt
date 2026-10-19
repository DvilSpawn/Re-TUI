package ohi.andre.consolelauncher.presentation

import android.appwidget.AppWidgetHostView
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.tuils.MusicVisualizerView
import ohi.andre.consolelauncher.tuils.TuiWidgetDecorator

/** Frozen native module content. No source view, provider, engine, media action or callback is retained. */
internal class ModulePresentationSnapshot private constructor(private val kind:ModulePanelPresentation.Kind?,private val title:CharSequence,
    private val chrome:TuiWidgetDecorator.Binding?,private val closeStyle:ModuleContentPresentation.Binding?,
    private val content:Content,private val saved:HomeAppearance) {
    private sealed interface Content {
        data class Music(val title:CharSequence,val singer:CharSequence,val appPrefix:CharSequence,val appLabel:CharSequence,
                         val appVisible:Boolean,val previous:CharSequence,val playPause:CharSequence,val next:CharSequence,
                         val frame:MusicVisualizerView.Frame):Content
        data class Text(val body:ModuleBodyPresentation.Block,val scroll:Int,val policy:ModulePanelPresentation.ScrollPolicy?):Content
        data class Notifications(val rows:List<ModuleBodyPresentation.Block>,val compact:Boolean,val viewportHeight:Int,
                                 val previous:Pager,val clear:Pager,val next:Pager,val scroll:Int):Content
        data class Provider(val bitmap:Bitmap,val width:Int,val height:Int):Content
    }
    private data class Pager(val label:CharSequence,val enabled:Boolean,val alpha:Float)

    fun bind(parent:ViewGroup,appearance:HomeAppearance) {
        val payload=content
        if(payload is Content.Provider) {
            parent.addView(ImageView(parent.context).apply {
                setImageBitmap(payload.bitmap);scaleType=ImageView.ScaleType.FIT_XY
                layoutParams=LinearLayout.LayoutParams(payload.width,payload.height)
                importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
            });return
        }
        val type=checkNotNull(kind)
        val root=ModulePanelPresentation.inflate(parent.context,parent,type)
        parent.addView(root);root.visibility=View.VISIBLE
        root.findViewById<TextView>(type.label).text=title
        ModulePanelPresentation.chrome(root,type,appearance,chrome)
        closeStyle?.let { ModuleContentPresentation.bind(root.findViewById(type.close),it,appearance) }
        when(payload) {
            is Content.Music -> {
                root.findViewById<TextView>(R.id.music_song_title).text=payload.title
                root.findViewById<TextView>(R.id.music_singer).text=payload.singer
                root.findViewById<TextView>(R.id.music_app_prefix).text=payload.appPrefix
                root.findViewById<TextView>(R.id.music_app_label).text=payload.appLabel
                root.findViewById<View>(R.id.music_app_row).visibility=if(payload.appVisible) View.VISIBLE else View.GONE
                root.findViewById<TextView>(R.id.music_prev).text=payload.previous
                root.findViewById<TextView>(R.id.music_play_pause).text=payload.playPause
                root.findViewById<TextView>(R.id.music_next).text=payload.next
                ModulePanelPresentation.musicContent(root,appearance,false)
                root.findViewById<MusicVisualizerView>(R.id.music_visualizer).setFrozenFrame(payload.frame,
                    appearance.color(Theme.module_panel_text_color).takeIf { appearance.changed(Theme.module_panel_text_color,saved) })
            }
            is Content.Text -> {
                val scroll=root.findViewById<ScrollView>(R.id.module_text_border)
                val xmlBody=scroll.findViewById<TextView>(R.id.module_text_body)
                scroll.removeAllViews()
                scroll.addView(ModuleBodyPresentation.bind(payload.body,scroll,appearance,saved,xmlBody))
                scroll.isFillViewport=false
                scroll.overScrollMode=View.OVER_SCROLL_IF_CONTENT_SCROLLS
            }
            is Content.Notifications -> {
                val rows=root.findViewById<ViewGroup>(R.id.notification_rows)
                payload.rows.forEach { rows.addView(ModuleBodyPresentation.bind(it,rows,appearance,saved)) }
                ModulePanelPresentation.notificationSize(root,payload.compact)
                val scroll=root.findViewById<ScrollView>(R.id.notification_scroll)
                scroll.isFillViewport=false
                fun pager(id:Int,value:Pager) {
                    root.findViewById<TextView>(id).apply {
                        text=value.label;isEnabled=value.enabled;alpha=value.alpha
                        ModulePanelPresentation.notificationPager(this,appearance)
                    }
                }
                pager(R.id.notification_module_prev,payload.previous);pager(R.id.notification_module_clear,payload.clear);pager(R.id.notification_module_next,payload.next)
                ModulePanelPresentation.notificationSettings(root.findViewById<ImageButton>(R.id.notification_module_settings),appearance)
            }
            is Content.Provider -> error("Provider already bound")
        }
        fun passive(view:View) {
            view.setOnClickListener(null);view.isClickable=false;view.isLongClickable=false;view.isFocusable=false;view.isFocusableInTouchMode=false
            if(view is ViewGroup) for(index in 0 until view.childCount) passive(view.getChildAt(index))
        }
        passive(root)
    }

    fun afterLayout(homeRoot:ViewGroup) {
        when(val value=content) {
            is Content.Music -> homeRoot.findViewById<View>(R.id.music_module)?.let(ModulePanelPresentation::applyVisualizerSize)
            is Content.Text -> homeRoot.findViewById<ScrollView>(R.id.module_text_border)?.let { scroll ->
                value.policy?.let { ModulePanelPresentation.applyTextScroll(scroll,it) }
                scroll.scrollTo(0,value.scroll)
            }
            is Content.Notifications -> homeRoot.findViewById<ScrollView>(R.id.notification_scroll)?.let { scroll ->
                ModulePanelPresentation.applyNotificationLimit(scroll,ModulePanelPresentation.notificationMaxHeight(homeRoot.context,value.viewportHeight,value.compact))
                scroll.scrollTo(0,value.scroll)
            }
            is Content.Provider -> Unit
        }
    }

    companion object {
        /** Use the real module's idle layout without starting a media controller. */
        fun idleMusic(context:android.content.Context):ModulePresentationSnapshot {
            val container=android.widget.FrameLayout(context).apply { id=R.id.home_modules_container }
            val music=ModulePanelPresentation.inflate(context,container,ModulePanelPresentation.Kind.MUSIC)
            music.visibility=View.VISIBLE
            container.addView(music)
            return checkNotNull(capture(container))
        }

        fun capture(homeRoot:ViewGroup):ModulePresentationSnapshot? {
            val container=homeRoot.findViewById<ViewGroup>(R.id.home_modules_container)?:return null
            val root=(0 until container.childCount).map {container.getChildAt(it)}.firstOrNull {it.visibility!=View.GONE}?:return null
            val saved=HomeAppearance.saved()
            if(root is AppWidgetHostView) {
                check(root.width>0 && root.height>0) {"Provider content has not been laid out"}
                val bitmap=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888).also { root.draw(Canvas(it)) }
                return ModulePresentationSnapshot(null,"",null,null,Content.Provider(bitmap,root.width,root.height),saved)
            }
            val kind=when(root.id) {
                R.id.music_module -> ModulePanelPresentation.Kind.MUSIC
                R.id.module_text_panel -> ModulePanelPresentation.Kind.TEXT
                R.id.notification_module -> ModulePanelPresentation.Kind.NOTIFICATIONS
                else -> error("Unsupported native module: ${root.resources.getResourceEntryName(root.id)}")
            }
            fun text(id:Int):CharSequence = root.findViewById<TextView>(id).let { ReadOnlyStyledText.freeze(it.text,it.paint) }
            val payload=when(kind) {
                ModulePanelPresentation.Kind.MUSIC -> Content.Music(text(R.id.music_song_title),text(R.id.music_singer),text(R.id.music_app_prefix),text(R.id.music_app_label),
                    root.findViewById<View>(R.id.music_app_row).visibility==View.VISIBLE,text(R.id.music_prev),text(R.id.music_play_pause),text(R.id.music_next),
                    root.findViewById<MusicVisualizerView>(R.id.music_visualizer).captureFrame())
                ModulePanelPresentation.Kind.TEXT -> {
                    val scroll=root.findViewById<ScrollView>(R.id.module_text_border)
                    Content.Text(ModuleBodyPresentation.capture(checkNotNull(scroll.getChildAt(0))),scroll.scrollY,
                        scroll.getTag(R.id.home_module_scroll_policy) as? ModulePanelPresentation.ScrollPolicy)
                }
                ModulePanelPresentation.Kind.NOTIFICATIONS -> {
                    val rows=root.findViewById<ViewGroup>(R.id.notification_rows)
                    fun pager(id:Int)=root.findViewById<TextView>(id).let { Pager(text(id),it.isEnabled,it.alpha) }
                    Content.Notifications((0 until rows.childCount).map {ModuleBodyPresentation.capture(rows.getChildAt(it))},
                        (root.getTag(R.id.home_module_scroll_policy) as? ModulePanelPresentation.NotificationSize)?.compact?:false,homeRoot.height,
                        pager(R.id.notification_module_prev),pager(R.id.notification_module_clear),pager(R.id.notification_module_next),
                        root.findViewById<ScrollView>(R.id.notification_scroll).scrollY)
                }
            }
            return ModulePresentationSnapshot(kind,text(kind.label),root.getTag(R.id.home_widget_binding) as? TuiWidgetDecorator.Binding,
                root.findViewById<TextView>(kind.close).getTag(R.id.home_module_content_binding) as? ModuleContentPresentation.Binding,payload,saved)
        }
    }
}
