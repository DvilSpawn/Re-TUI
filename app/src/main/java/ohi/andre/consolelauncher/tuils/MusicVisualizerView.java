package ohi.andre.consolelauncher.tuils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.Random;

public class MusicVisualizerView extends View {

    private static final int BAR_COUNT = 36;
    private static final float MIN_BAR = 0.08f;
    private static final float MAX_BAR = 0.92f;
    private static final long FRAME_DELAY_MS = 66L;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] heights = new float[BAR_COUNT];
    private final float[] targets = new float[BAR_COUNT];
    private final Random random = new Random(1337L);

    private boolean playing;
    private long lastFrameTime;
    private int barColor = Color.parseColor("#66FF3B30");

    public MusicVisualizerView(Context context) {
        super(context);
        init();
    }

    public MusicVisualizerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MusicVisualizerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(barColor);
        for (int i = 0; i < BAR_COUNT; i++) {
            heights[i] = 0.18f + (random.nextFloat() * 0.2f);
            targets[i] = heights[i];
        }
        setAlpha(0.72f);
    }

    public void setPlaying(boolean playing) {
        if (this.playing == playing) {
            return;
        }

        this.playing = playing;
        if (playing) {
            lastFrameTime = 0L;
            postInvalidateOnAnimation();
        } else {
            for (int i = 0; i < BAR_COUNT; i++) {
                targets[i] = MIN_BAR;
            }
            invalidate();
        }
    }

    public void setBarColor(int color) {
        barColor = Color.argb(140, Color.red(color), Color.green(color), Color.blue(color));
        paint.setColor(barColor);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        long now = System.currentTimeMillis();
        if (lastFrameTime == 0L) {
            lastFrameTime = now;
        }

        if (playing && now - lastFrameTime >= FRAME_DELAY_MS) {
            lastFrameTime = now;
            updateTargets();
        }

        float spacing = width / (float) BAR_COUNT;
        float barWidth = Math.max(4f, spacing * 0.72f);

        for (int i = 0; i < BAR_COUNT; i++) {
            heights[i] += (targets[i] - heights[i]) * (playing ? 0.22f : 0.1f);
            float left = i * spacing;
            float right = left + barWidth;
            float top = height * (1f - clamp(heights[i]));
            canvas.drawRect(left, top, right, height, paint);
        }

        if (playing) {
            postInvalidateOnAnimation();
        } else if (!isCollapsed()) {
            postInvalidateOnAnimation();
        }
    }

    private void updateTargets() {
        for (int i = 0; i < BAR_COUNT; i++) {
            float base = 0.18f + (0.64f * random.nextFloat());
            if (i % 7 == 0 || i % 11 == 0) {
                base += 0.15f * random.nextFloat();
            }
            targets[i] = clamp(base);
        }
    }

    private boolean isCollapsed() {
        for (float height : heights) {
            if (height > MIN_BAR + 0.02f) {
                return false;
            }
        }
        return true;
    }

    private float clamp(float value) {
        return Math.max(MIN_BAR, Math.min(MAX_BAR, value));
    }
}
