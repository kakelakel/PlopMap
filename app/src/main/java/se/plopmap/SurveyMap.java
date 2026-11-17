package se.plopmap;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.location.Location;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class SurveyMap extends View {
    private record Shape(List<PolygonRing.Vertex> points, boolean closed, int color) {}
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Shape> shapes = new ArrayList<>();
    private JSONArray cachedFeatures;
    private final List<Shape> saved = new ArrayList<>();
    private final BasemapTiles tiles;
    private final android.graphics.RectF tileRect = new android.graphics.RectF();
    private boolean basemapEnabled;
    private double centerX, centerY, scaleX;
    private boolean automatic = true;
    private Location latestFix;
    private final android.view.GestureDetector gestures;
    private final android.view.ScaleGestureDetector pinch;
    private boolean multiTouch;
    private int bottomOverlay;

    SurveyMap(Context context) {
        super(context);
        tiles = new BasemapTiles(context);
        pinch = new android.view.ScaleGestureDetector(context,
                new android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override public boolean onScale(android.view.ScaleGestureDetector detector) {
                        zoomAt(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
                        return true;
                    }
                });
        pinch.setQuickScaleEnabled(false);
        gestures = new android.view.GestureDetector(context, new android.view.GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(android.view.MotionEvent event) { return true; }
            @Override public boolean onScroll(android.view.MotionEvent start, android.view.MotionEvent current,
                                              float dx, float dy) {
                if (multiTouch || scaleX <= 0) return false;
                automatic = false;
                setCenter(centerX + dx / scaleX, centerY + dy / scaleX);
                return true;
            }
            @Override public boolean onDoubleTap(android.view.MotionEvent event) {
                zoomAt(2, event.getX(), event.getY());
                return true;
            }
        });
        setContentDescription("Översikt över sparade objekt, pågående inmätning och aktuell GPS-position");
    }

    void update(JSONArray features, List<Location> draft, Location fix, boolean closeDraft) {
        if (features != cachedFeatures) {
            cachedFeatures = features;
            saved.clear();
            for (int i = 0; features != null && i < features.length(); i++) {
                JSONObject feature = features.optJSONObject(i);
                JSONObject geometry = feature == null ? null : feature.optJSONObject("geometry");
                if (geometry == null) continue;
                JSONArray coordinates = geometry.optJSONArray("coordinates");
                if (coordinates == null) continue;
                String type = geometry.optString("type");
                if ("Point".equals(type)) coordinates = new JSONArray().put(coordinates);
                if ("Polygon".equals(type)) coordinates = coordinates.optJSONArray(0);
                if (coordinates == null) continue;
                List<PolygonRing.Vertex> points = new ArrayList<>();
                for (int j = 0; j < coordinates.length(); j++) {
                    JSONArray point = coordinates.optJSONArray(j);
                    if (point != null && Double.isFinite(point.optDouble(0)) && Double.isFinite(point.optDouble(1))) {
                        points.add(new PolygonRing.Vertex(point.optDouble(0), point.optDouble(1)));
                    }
                }
                if (!points.isEmpty()) saved.add(new Shape(points, "Polygon".equals(type), 0xff266d4b));
            }
        }
        latestFix = fix;
        shapes.clear();
        shapes.addAll(saved);
        List<PolygonRing.Vertex> points = new ArrayList<>();
        for (Location location : draft) points.add(vertex(location));
        if (!points.isEmpty()) shapes.add(new Shape(points, closeDraft && points.size() >= 3, 0xffb45212));
        if (fix != null) shapes.add(new Shape(java.util.Collections.singletonList(vertex(fix)), false, 0xff245db8));
        invalidate();
    }

    private PolygonRing.Vertex vertex(Location location) {
        return new PolygonRing.Vertex(location.getLongitude(), location.getLatitude());
    }

    void setBottomOverlay(int height) {
        if (bottomOverlay == height) return;
        bottomOverlay = height;
        invalidate();
    }

    void setBasemapEnabled(boolean enabled) {
        basemapEnabled = enabled;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) multiTouch = false;
        if (event.getPointerCount() > 1) multiTouch = true;
        pinch.onTouchEvent(event);
        gestures.onTouchEvent(event);
        return true;
    }

    void zoom(double factor) {
        zoomAt(factor, getWidth() / 2f, getHeight() / 2f);
    }

    private void zoomAt(double factor, float x, float y) {
        if (scaleX <= 0) return;
        double before = scaleX;
        scaleX = Math.max(Math.max(getWidth(), getHeight()), Math.min(1_073_741_824, before * factor));
        automatic = false;
        setCenter(centerX + (x - getWidth() / 2.0) * (1 / before - 1 / scaleX),
                centerY + (y - getHeight() / 2.0) * (1 / before - 1 / scaleX));
    }

    private void setCenter(double x, double y) {
        centerX = Math.max(0, Math.min(1, x));
        centerY = Math.max(0, Math.min(1, y));
        invalidate();
    }

    void fit() {
        automatic = true;
        invalidate();
    }

    boolean locate() {
        if (latestFix == null) return false;
        automatic = false;
        scaleX = 256 * (1 << 18);
        setCenter(mercatorX(latestFix.getLongitude()), mercatorY(latestFix.getLatitude()));
        return true;
    }

    void saveCamera(android.os.Bundle state) {
        state.putBoolean("cameraAutomatic", automatic);
        state.putDouble("cameraX", centerX);
        state.putDouble("cameraY", centerY);
        state.putDouble("cameraScale", scaleX);
    }

    void restoreCamera(android.os.Bundle state) {
        automatic = state.getBoolean("cameraAutomatic", true);
        centerX = state.getDouble("cameraX");
        centerY = state.getDouble("cameraY");
        scaleX = state.getDouble("cameraScale");
    }

    private static double mercatorX(double longitude) {
        return (longitude + 180) / 360;
    }

    private static double mercatorY(double latitude) {
        double rad = Math.toRadians(Math.max(-85.05112878, Math.min(85.05112878, latitude)));
        return (1 - Math.log(Math.tan(rad) + 1 / Math.cos(rad)) / Math.PI) / 2;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(0xffe9f1ec);
        float margin = 16 * getResources().getDisplayMetrics().density;
        if (getWidth() <= 2 * margin || getHeight() <= 4 * margin) return;
        if (scaleX <= 0 || automatic) fitCamera(margin);
        if (basemapEnabled) drawTiles(canvas);
        drawShapes(canvas, margin);
        if (basemapEnabled) label(canvas, "© OpenStreetMap contributors", margin, getHeight() - margin / 2);
        drawScale(canvas, margin);
    }

    private void drawScale(Canvas canvas, float margin) {
        double latitude = Math.atan(Math.sinh(Math.PI * (1 - 2 * centerY)));
        double metresPerPixel = 40_075_016.686 * Math.cos(latitude) / scaleX;
        double maximum = margin * 5 * metresPerPixel;
        double step = Math.pow(10, Math.floor(Math.log10(maximum)));
        double metres = (maximum >= 5 * step ? 5 : maximum >= 2 * step ? 2 : 1) * step;
        float length = (float) (metres / metresPerPixel);
        float x = margin;
        float y = getHeight() - bottomOverlay - margin * 1.35f;
        if (y < margin * 3) return;
        String distance = metres >= 1000 ? (int) (metres / 1000) + " km"
                : metres >= 1 ? (int) metres + " m" : Math.round(metres * 100) + " cm";
        paint.setColor(0xfffcfffd);
        paint.setStrokeWidth(margin / 4);
        canvas.drawLine(x, y, x + length, y, paint);
        paint.setColor(0xff13362e);
        paint.setStrokeWidth(margin / 8);
        canvas.drawLine(x, y, x + length, y, paint);
        label(canvas, distance, x, y - margin * 0.3f);
    }

    private void fitCamera(float margin) {
        if (shapes.isEmpty()) {
            centerX = mercatorX(15);
            centerY = mercatorY(62);
            scaleX = 8192;
            return;
        }

        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (Shape shape : shapes) for (var point : shape.points()) {
            double x = mercatorX(point.x()), y = mercatorY(point.y());
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        centerX = (minX + maxX) / 2;
        centerY = (minY + maxY) / 2;
        // Use a local field view for a single fix, then zoom out to fit all survey objects.
        double span = basemapEnabled ? 0.000004 : 0.00000008;
        scaleX = Math.min((getWidth() - 2 * margin) / Math.max(span, maxX - minX),
                (getHeight() - 3 * margin) / Math.max(span, maxY - minY));
    }

    private void drawShapes(Canvas canvas, float margin) {
        for (Shape shape : shapes) {
            paint.setColor(shape.color());
            Path path = new Path();
            boolean first = true;
            for (var point : shape.points()) {
                float x = screenX(mercatorX(point.x()));
                float y = screenY(mercatorY(point.y()));
                if (first) path.moveTo(x, y);
                else path.lineTo(x, y);
                first = false;
                int color = paint.getColor();
                paint.setColor(0xfffcfffd);
                canvas.drawCircle(x, y, margin * 0.37f, paint);
                paint.setColor(color);
                canvas.drawCircle(x, y, margin * 0.25f, paint);
            }
            if (shape.closed()) {
                path.close();
                paint.setAlpha(45);
                canvas.drawPath(path, paint);
                paint.setAlpha(255);
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(margin / 6);
            canvas.drawPath(path, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private float screenX(double value) {
        return (float) (getWidth() / 2.0 + (value - centerX) * scaleX);
    }

    private float screenY(double value) {
        return (float) (getHeight() / 2.0 + (value - centerY) * scaleX);
    }

    private void drawTiles(Canvas canvas) {
        int zoom = Math.max(0, Math.min(19, (int) Math.floor(Math.log(scaleX / 256) / Math.log(2))));
        int count = 1 << zoom;
        int firstX = (int) Math.floor((centerX - getWidth() / (2 * scaleX)) * count);
        int lastX = (int) Math.floor((centerX + getWidth() / (2 * scaleX)) * count);
        int firstY = Math.max(0, (int) Math.floor((centerY - getHeight() / (2 * scaleX)) * count));
        int lastY = Math.min(count - 1, (int) Math.floor((centerY + getHeight() / (2 * scaleX)) * count));
        paint.setFilterBitmap(true);
        for (int row = firstY; row <= lastY; row++) {
            for (int column = firstX; column <= lastX; column++) {
                var tile = tiles.get(zoom, Math.floorMod(column, count), row, this::invalidate);
                if (tile == null) continue;
                tileRect.set(screenX((double) column / count), screenY((double) row / count),
                        screenX((double) (column + 1) / count), screenY((double) (row + 1) / count));
                canvas.drawBitmap(tile, null, tileRect, paint);
            }
        }
    }

    private void label(Canvas canvas, String text, float x, float y) {
        paint.setTextSize(12 * getResources().getDisplayMetrics().scaledDensity);
        paint.setColor(0xeaffffff);
        canvas.drawRect(x - 4, y + paint.ascent() - 2, x + paint.measureText(text) + 4,
                y + paint.descent() + 2, paint);
        paint.setColor(0xff263b32);
        canvas.drawText(text, x, y, paint);
    }
}
