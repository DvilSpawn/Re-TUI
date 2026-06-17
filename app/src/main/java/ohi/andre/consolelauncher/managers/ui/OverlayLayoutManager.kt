package ohi.andre.consolelauncher.managers.ui

import android.view.View
import android.view.ViewGroup.MarginLayoutParams

object OverlayLayoutManager {
    fun captureBaseMargins(view: View?, out: IntArray?) {
        if (view == null || out == null || out.size < 4) {
            return
        }

        val params = view.layoutParams
        if (params is MarginLayoutParams) {
            out[0] = params.leftMargin
            out[1] = params.topMargin
            out[2] = params.rightMargin
            out[3] = params.bottomMargin
        }
    }

    fun applyMarginsWithBase(
        view: View?,
        baseMargins: IntArray?,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        if (view == null || baseMargins == null || baseMargins.size < 4) {
            return
        }

        val params = view.layoutParams
        if (params !is MarginLayoutParams) {
            return
        }

        val newLeft = baseMargins[0] + left
        val newTop = baseMargins[1] + top
        val newRight = baseMargins[2] + right
        val newBottom = baseMargins[3] + bottom
        if (params.leftMargin == newLeft
            && params.topMargin == newTop
            && params.rightMargin == newRight
            && params.bottomMargin == newBottom
        ) {
            return
        }

        params.setMargins(newLeft, newTop, newRight, newBottom)
        view.layoutParams = params
    }

    fun applyPaddingWithBase(
        view: View?,
        baseLeft: Int,
        baseTop: Int,
        baseRight: Int,
        baseBottom: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        if (view == null) {
            return
        }

        val newLeft = baseLeft + left
        val newTop = baseTop + top
        val newRight = baseRight + right
        val newBottom = baseBottom + bottom
        if (view.paddingLeft == newLeft
            && view.paddingTop == newTop
            && view.paddingRight == newRight
            && view.paddingBottom == newBottom
        ) {
            return
        }

        view.setPadding(newLeft, newTop, newRight, newBottom)
    }
}
