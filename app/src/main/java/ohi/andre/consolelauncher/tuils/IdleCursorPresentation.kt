package ohi.andre.consolelauncher.tuils

import android.graphics.Canvas
import android.graphics.Paint
import android.widget.TextView
import kotlin.math.max
import kotlin.math.min

/** One cursor drawing path for Home and its inert preview, with no blink loop in the preview. */
internal object IdleCursorPresentation {
    fun draw(canvas: Canvas, view: TextView, color: Int, paint: Paint) {
        val text=view.text
        var x=view.compoundPaddingLeft-view.scrollX.toFloat()
        if(!text.isNullOrEmpty()) x+=view.paint.measureText(text,0,text.length)
        val density=view.resources.displayMetrics.density
        val lineHeight=max(view.lineHeight,(18f*density).toInt())
        val centerY=view.height/2
        val top=max(view.paddingTop,centerY-lineHeight/2)
        val bottom=min(view.height-view.paddingBottom,centerY+lineHeight/2)
        paint.color=color;paint.style=Paint.Style.FILL
        canvas.drawRect(x,top.toFloat(),x+max(3f*density,3f),bottom.toFloat(),paint)
    }
}
