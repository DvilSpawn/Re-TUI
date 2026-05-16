package ohi.andre.consolelauncher.tuils;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.HorizontalScrollView;

public class StableHorizontalScrollView extends HorizontalScrollView {

    private int stableScrollX = 0;
    private int touchDownScrollX = -1;
    private boolean touchDownActive = false;
    private boolean restoringLayout = false;

    public StableHorizontalScrollView(Context context) {
        super(context);
    }

    public StableHorizontalScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public StableHorizontalScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        if (!restoringLayout && touchDownActive && touchDownScrollX > 0 && l == 0) {
            post(() -> scrollTo(Math.min(touchDownScrollX, maxScrollX()), getScrollY()));
            return;
        }
        if (!restoringLayout) {
            stableScrollX = l;
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int target = stableScrollX;
        restoringLayout = true;
        super.onLayout(changed, l, t, r, b);
        if (target > 0) {
            scrollTo(Math.min(target, maxScrollX()), getScrollY());
        }
        restoringLayout = false;
        stableScrollX = getScrollX();
    }

    @Override
    public boolean requestChildRectangleOnScreen(View child, android.graphics.Rect rectangle, boolean immediate) {
        return false;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        updateTouchState(ev);
        updateParentIntercept(ev);
        return super.dispatchTouchEvent(ev);
    }

    public void preserveScrollX(int scrollX) {
        final int target = Math.max(0, scrollX);
        applyPreservedScrollX(target);
        post(() -> applyPreservedScrollX(target));
        postDelayed(() -> applyPreservedScrollX(target), 80);
    }

    public int getPreservedScrollX() {
        if (touchDownActive && touchDownScrollX >= 0) {
            return touchDownScrollX;
        }
        return stableScrollX;
    }

    private void updateTouchState(MotionEvent ev) {
        if (ev == null) {
            return;
        }
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            touchDownScrollX = getScrollX();
            touchDownActive = true;
            stableScrollX = touchDownScrollX;
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            post(() -> {
                touchDownActive = false;
                touchDownScrollX = -1;
                stableScrollX = getScrollX();
            });
        }
    }

    private void updateParentIntercept(MotionEvent ev) {
        ViewParent parent = getParent();
        if (parent == null || ev == null) {
            return;
        }
        int action = ev.getActionMasked();
        parent.requestDisallowInterceptTouchEvent(action != MotionEvent.ACTION_UP
                && action != MotionEvent.ACTION_CANCEL);
    }

    private int maxScrollX() {
        if (getChildCount() == 0) {
            return 0;
        }
        int visibleWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        return Math.max(0, getChildAt(0).getWidth() - visibleWidth);
    }

    private void applyPreservedScrollX(int target) {
        stableScrollX = Math.min(Math.max(0, target), maxScrollX());
        scrollTo(stableScrollX, getScrollY());
    }
}
