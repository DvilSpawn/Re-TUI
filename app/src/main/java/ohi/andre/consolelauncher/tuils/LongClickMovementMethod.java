package ohi.andre.consolelauncher.tuils;

import android.text.Layout;
import android.text.Spannable;
import android.text.method.LinkMovementMethod;
import android.text.method.MovementMethod;
import android.view.MotionEvent;
import android.widget.TextView;

public class LongClickMovementMethod extends LinkMovementMethod {

    private int longClickDuration, lastLine = -1;

    private abstract class WasActivatedRunnable implements Runnable {

        public boolean wasActivated = false;

        @Override
        public void run() {
            wasActivated = true;
        }
    };

    private WasActivatedRunnable runnable;

    @Override
    public boolean onTouchEvent(final TextView widget, Spannable buffer, MotionEvent event) {
        int action = event.getAction();

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_CANCEL) {
            int x = (int) event.getX();
            int y = (int) event.getY();

            x -= widget.getTotalPaddingLeft();
            y -= widget.getTotalPaddingTop();

            x += widget.getScrollX();
            y += widget.getScrollY();

            Layout layout = widget.getLayout();
            if (layout == null) {
                return super.onTouchEvent(widget, buffer, event);
            }
            final int line = layout.getLineForVertical(y);
            int off = layout.getOffsetForHorizontal(line, x);

            final LongClickableSpan[] link = buffer.getSpans(off, off, LongClickableSpan.class);
            if (link.length == 0 && runnable == null) {
                return super.onTouchEvent(widget, buffer, event);
            }

            if (action == MotionEvent.ACTION_UP) {
                if(runnable != null) {
                    if(!runnable.wasActivated) {
                        widget.removeCallbacks(runnable);
                        if(link.length > 0) link[0].onClick(widget);
                    }

                    runnable = null;
                }

            } else if (action == MotionEvent.ACTION_DOWN) {

                if(link.length > 0) {
                    final LongClickableSpan span = link[0];
                    runnable = new WasActivatedRunnable() {

                        @Override
                        public void run() {
                            super.run();
                            span.onLongClick(widget);
                        }
                    };
                    widget.postDelayed(runnable, longClickDuration);
                }

            } else {
                if(line != lastLine && runnable != null) {
                    widget.removeCallbacks(runnable);
                    runnable = null;
                }
            }

            lastLine = line;

            return true;
        }

        return super.onTouchEvent(widget, buffer, event);
    }

    private static LongClickMovementMethod sInstance;
    public static MovementMethod getInstance(int longClickDuration) {
        if (sInstance == null) {
            sInstance = new LongClickMovementMethod();
            sInstance.longClickDuration = longClickDuration;
        }

        return sInstance;
    }

    public static MovementMethod getInstance() {
        return getInstance(-1);
    }
}
