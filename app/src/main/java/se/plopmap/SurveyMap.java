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

    SurveyMap(Context context) {
        super(context);
        tiles = new BasemapTiles(context);
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

    void setBasemapEnabled(boolean enabled) {
        basemapEnabled = enabled;
        invalidate();
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
        if (shapes.isEmpty() || getWidth() <= 2 * margin || getHeight() <= 4 * margin) {
            label(canvas, "Väntar på position eller sparade objekt", margin, margin * 1.3f);
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
        if (basemapEnabled) drawTiles(canvas);
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
                canvas.drawCircle(x, y, margin / 4, paint);
            }
            if (shape.closed()) {
                path.close();
                paint.setAlpha(45);
                canvas.drawPath(path, paint);
                paint.setAlpha(255);
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(margin / 6);
            canvas.drawPath(path, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        label(canvas, basemapEnabled ? "Baskarta · norr upp" : "Objektöversikt · norr upp", margin, margin * 1.3f);
        if (basemapEnabled) label(canvas, "© OpenStreetMap contributors", margin, getHeight() - margin / 2);
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
