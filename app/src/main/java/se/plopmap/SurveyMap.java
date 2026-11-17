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
    private double minX, maxX, minY, maxY, scale, horizontalFactor;

    SurveyMap(Context context) {
        super(context);
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

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(0xffe9f1ec);
        paint.setColor(0xff30473b);
        paint.setTextSize(14 * getResources().getDisplayMetrics().scaledDensity);
        float margin = 16 * getResources().getDisplayMetrics().density;
        canvas.drawText(shapes.isEmpty() ? "Sparade objekt visas här" : "Objektöversikt · norr upp",
                margin, margin * 1.3f, paint);
        if (shapes.isEmpty() || getWidth() <= 2 * margin || getHeight() <= 4 * margin) return;
        minX = minY = Double.POSITIVE_INFINITY;
        maxX = maxY = Double.NEGATIVE_INFINITY;
        for (Shape shape : shapes) for (var point : shape.points()) {
            minX = Math.min(minX, point.x());
            maxX = Math.max(maxX, point.x());
            minY = Math.min(minY, point.y());
            maxY = Math.max(maxY, point.y());
        }
        horizontalFactor = Math.max(0.001, Math.cos(Math.toRadians((minY + maxY) / 2)));
        scale = Math.min((getWidth() - 2 * margin) / Math.max(0.00003, (maxX - minX) * horizontalFactor),
                (getHeight() - 4 * margin) / Math.max(0.00003, maxY - minY));
        for (Shape shape : shapes) {
            paint.setColor(shape.color());
            Path path = new Path();
            boolean first = true;
            for (var point : shape.points()) {
                float x = (float) (getWidth() / 2.0 + (point.x() - (minX + maxX) / 2) * horizontalFactor * scale);
                float y = (float) (getHeight() / 2.0 + margin / 2 - (point.y() - (minY + maxY) / 2) * scale);
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
    }
}
