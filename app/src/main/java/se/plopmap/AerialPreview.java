package se.plopmap;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.List;

final class AerialPreview extends View {
    private final Bitmap bitmap;
    private final int[] rgb;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF bounds = new RectF();
    private List<GrassOutline.Pixel> outline = java.util.Collections.emptyList();
    private final Runnable onChange;
    private int moving = -1;

    AerialPreview(Context context, Bitmap bitmap, Runnable onChange) {
        super(context);
        this.bitmap = bitmap;
        this.onChange = onChange;
        rgb = new int[bitmap.getWidth() * bitmap.getHeight()];
        bitmap.getPixels(rgb, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        setContentDescription("Flygbild. Tryck på gräsytan för att skapa ett polygonförslag.");
    }

    List<GrassOutline.Pixel> outline() {
        return outline;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float scale = Math.min((float) getWidth() / bitmap.getWidth(), (float) getHeight() / bitmap.getHeight());
        float width = bitmap.getWidth() * scale, height = bitmap.getHeight() * scale;
        bounds.set((getWidth() - width) / 2, (getHeight() - height) / 2,
                (getWidth() + width) / 2, (getHeight() + height) / 2);
        canvas.drawBitmap(bitmap, null, bounds, paint);
        if (outline.isEmpty()) return;
        Path path = new Path();
        for (int i = 0; i < outline.size(); i++) {
            GrassOutline.Pixel point = outline.get(i);
            float x = bounds.left + (point.x() + 0.5f) * scale;
            float y = bounds.top + (point.y() + 0.5f) * scale;
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        path.close();
        paint.setColor(0x6054ef91);
        canvas.drawPath(path, paint);
        paint.setColor(0xff54ef91);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
        for (var point : outline) {
            canvas.drawCircle(bounds.left + (point.x() + 0.5f) * scale,
                    bounds.top + (point.y() + 0.5f) * scale, 5, paint);
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN && bounds.contains(event.getX(), event.getY())) {
            moving = -1;
            float limit = 16 * getResources().getDisplayMetrics().density;
            for (int i = 0; i < outline.size(); i++) {
                var point = outline.get(i);
                float x = bounds.left + (point.x() + 0.5f) * bounds.width() / bitmap.getWidth();
                float y = bounds.top + (point.y() + 0.5f) * bounds.height() / bitmap.getHeight();
                float distance = (float) Math.hypot(x - event.getX(), y - event.getY());
                if (distance < limit) { moving = i; limit = distance; }
            }
        }
        boolean released = event.getAction() == MotionEvent.ACTION_UP;
        if ((released || (moving >= 0 && event.getAction() == MotionEvent.ACTION_MOVE))
                && bounds.contains(event.getX(), event.getY())) {
            int x = Math.min(bitmap.getWidth() - 1, (int) ((event.getX() - bounds.left) / bounds.width() * bitmap.getWidth()));
            int y = Math.min(bitmap.getHeight() - 1, (int) ((event.getY() - bounds.top) / bounds.height() * bitmap.getHeight()));
            if (moving >= 0) outline.set(moving, new GrassOutline.Pixel(x, y));
            else outline = GrassOutline.suggest(rgb, bitmap.getWidth(), bitmap.getHeight(), x, y);
            if (released) {
                moving = -1;
                performClick();
                onChange.run();
            }
            invalidate();
        }
        return true;
    }
}
