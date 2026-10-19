package ohi.andre.consolelauncher.presentation

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.tuils.Tuils

/** The finite native module body schema. No Android view properties or action payloads are copied. */
internal object ModuleBodyPresentation {
    enum class GroupKind { COLUMN,ROW,WEIGHTED_COLUMN,CALENDAR,CALENDAR_ROW }
    enum class TextKind { XML_BODY,PARAGRAPH,CALENDAR_TEXT,CALENDAR_CELL,ACTION,NOTIFICATION_ROW,NOTIFICATION_DETAIL }
    private data class GroupSpec(val kind:GroupKind)
    private data class TextSpec(val kind:TextKind,val compact:Boolean=false)
    private data class SpaceSpec(val heightDp:Int)
    sealed interface Block {
        data class Group(val kind:GroupKind,val children:List<Block>):Block
        data class Text(val kind:TextKind,val compact:Boolean,val value:CharSequence,val style:ModuleContentPresentation.Binding):Block
        data class Space(val heightDp:Int):Block
    }

    fun group(context:Context,kind:GroupKind=GroupKind.COLUMN):LinearLayout = LinearLayout(context).apply {
        setTag(R.id.home_module_body_schema,GroupSpec(kind))
        orientation=if(kind==GroupKind.ROW || kind==GroupKind.CALENDAR_ROW) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        layoutParams=if(kind==GroupKind.WEIGHTED_COLUMN) LinearLayout.LayoutParams(0,-2,1f).apply {
            val margin=Tuils.dpToPx(context,3);setMargins(margin,margin,margin,margin)
        } else ViewGroup.LayoutParams(-1,-2)
        if(kind==GroupKind.CALENDAR) { val inset=Tuils.dpToPx(context,2);setPadding(inset,0,inset,0) }
        if(kind==GroupKind.CALENDAR_ROW) isBaselineAligned=false
    }
    fun paragraph(context:Context)=text(context,TextKind.PARAGRAPH)
    fun calendarText(context:Context)=text(context,TextKind.CALENDAR_TEXT)
    fun action(context:Context)=text(context,TextKind.ACTION)
    fun notificationText(context:Context,detail:Boolean,compact:Boolean)=text(context,
        if(detail) TextKind.NOTIFICATION_DETAIL else TextKind.NOTIFICATION_ROW,compact)
    fun calendarCell(view:TextView) {
        view.setTag(R.id.home_module_body_schema,TextSpec(TextKind.CALENDAR_CELL))
        view.gravity=Gravity.CENTER;view.minHeight=0;view.setPadding(0,0,0,0)
        view.layoutParams=LinearLayout.LayoutParams(0,-2,1f)
    }
    fun registerBody(view:TextView) {
        if(view.getTag(R.id.home_module_body_schema)==null) view.setTag(R.id.home_module_body_schema,
            TextSpec(if(view.id==R.id.module_text_body) TextKind.XML_BODY else TextKind.PARAGRAPH))
    }
    fun space(context:Context,heightDp:Int)=View(context).apply {
        setTag(R.id.home_module_body_schema,SpaceSpec(heightDp))
        layoutParams=LinearLayout.LayoutParams(-1,Tuils.dpToPx(context,heightDp))
    }
    private fun text(context:Context,kind:TextKind,compact:Boolean=false)=TextView(context).apply {
        setTag(R.id.home_module_body_schema,TextSpec(kind,compact))
        when(kind) {
            TextKind.XML_BODY -> error("XML body must come from the native module panel")
            TextKind.PARAGRAPH -> { includeFontPadding=true;setLineSpacing(Tuils.dpToPx(context,2).toFloat(),1f);layoutParams=LinearLayout.LayoutParams(-1,-2) }
            TextKind.CALENDAR_TEXT,TextKind.CALENDAR_CELL -> {
                setSingleLine(true);includeFontPadding=false;gravity=Gravity.START or Gravity.CENTER_VERTICAL;setPadding(0,0,0,0)
                if(kind==TextKind.CALENDAR_CELL) calendarCell(this)
            }
            TextKind.ACTION -> {
                setSingleLine(false);gravity=Gravity.CENTER;minHeight=Tuils.dpToPx(context,36)
                val h=Tuils.dpToPx(context,10);val v=Tuils.dpToPx(context,7);setPadding(h,v,h,v)
                layoutParams=LinearLayout.LayoutParams(0,-2,1f).apply {val m=Tuils.dpToPx(context,4);setMargins(m,m,m,m)}
            }
            TextKind.NOTIFICATION_ROW,TextKind.NOTIFICATION_DETAIL -> {
                val detail=kind==TextKind.NOTIFICATION_DETAIL
                layoutParams=LinearLayout.LayoutParams(-1,-2).apply {bottomMargin=if(detail || compact) 0 else Tuils.dpToPx(context,6)}
                setSingleLine(!detail);ellipsize=if(detail) null else TextUtils.TruncateAt.END
                gravity=if(detail) Gravity.TOP else Gravity.CENTER_VERTICAL
                if(detail) minLines=if(compact) 1 else 3
                val horizontal=Tuils.dpToPx(context,if(detail) 6 else 10)
                val vertical=Tuils.dpToPx(context,if(compact) 5 else if(detail) 10 else 8)
                setPadding(horizontal,vertical,horizontal,vertical)
            }
        }
    }

    fun capture(view:View):Block = when(val spec=view.getTag(R.id.home_module_body_schema)) {
        is GroupSpec -> {
            val parent=view as ViewGroup
            Block.Group(spec.kind,(0 until parent.childCount).map { capture(parent.getChildAt(it)) })
        }
        is TextSpec -> {
            val text=view as TextView
            val style=text.getTag(R.id.home_module_content_binding) as? ModuleContentPresentation.Binding
                ?:error("Native module text has no presentation style")
            Block.Text(spec.kind,spec.compact,ReadOnlyStyledText.freeze(text.text,text.paint),style)
        }
        is SpaceSpec -> Block.Space(spec.heightDp)
        else -> error("Native module body is not registered: ${view.javaClass.simpleName}")
    }

    fun bind(block:Block,parent:ViewGroup,appearance:HomeAppearance,saved:HomeAppearance,existingXmlBody:TextView?=null):View {
        val view=when(block) {
            is Block.Group -> group(parent.context,block.kind).also { group ->block.children.forEach { group.addView(bind(it,group,appearance,saved)) } }
            is Block.Space -> space(parent.context,block.heightDp)
            is Block.Text -> {
                val target=if(block.kind==TextKind.XML_BODY) checkNotNull(existingXmlBody) else text(parent.context,block.kind,block.compact)
                target.text=block.value
                ModuleContentPresentation.bind(target,block.style,appearance)
                ModuleContentPresentation.styleDraft(target,appearance,saved)
                target
            }
        }
        view.isClickable=false;view.isLongClickable=false;view.isFocusable=false;view.isFocusableInTouchMode=false
        return view
    }
}
