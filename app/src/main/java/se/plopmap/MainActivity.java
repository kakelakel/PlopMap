package se.plopmap;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public final class MainActivity extends Activity {
    private static final int LOCATION_PERMISSION = 1;
    private static final int EXPORT_FILE = 2;
    private static final float PLAN_TARGET_METRES = 0.20f;
    private static final long MAX_AGE_MS = 5_000;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final LocationListener listener = new LocationListener() {
        @Override public void onLocationChanged(Location location) {
            fix = location;
            refresh();
        }

        @Override public void onProviderDisabled(String provider) {
            fix = null;
            refresh();
        }

        @Override public void onProviderEnabled(String provider) {
            refresh();
        }

        @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    };
    private final Runnable expire = this::refresh;
    private LocationManager locations;
    private Location fix;
    private TextView status;
    private TextView saved;
    private EditText identifier;
    private EditText note;
    private Spinner condition;
    private Button save;
    private Button export;
    private JSONArray features;
    private final java.util.ArrayList<Location> corners = new java.util.ArrayList<>();
    private boolean polygon;
    private TextView title;
    private Button cornerButton;
    private Button undoButton;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        locations = getSystemService(LocationManager.class);
        try {
            features = new JSONArray(getPreferences(MODE_PRIVATE).getString("features", "[]"));
        } catch (JSONException error) {
            features = null;
        }

        ScrollView scroll = new ScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        form.setPadding(padding, padding, padding, padding);
        scroll.addView(form);
        setContentView(scroll);

        title = new TextView(this);
        title.setText("Mät in brunn");
        title.setTextSize(28);
        form.addView(title);
        button(form, "Byt mellan brunn och gräsyta", this::switchGeometry);
        status = new TextView(this);
        form.addView(status);
        button(form, "Aktivera GPS", this::activateGps);
        identifier = new EditText(this);
        identifier.setId(101);
        identifier.setHint("Brunns-ID (obligatoriskt)");
        identifier.setSingleLine(true);
        form.addView(identifier);

        TextView conditionLabel = new TextView(this);
        conditionLabel.setText("Skick");
        form.addView(conditionLabel);
        condition = new Spinner(this);
        condition.setId(102);
        condition.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Ej bedömt", "Bra", "Behöver åtgärd"}));
        form.addView(condition);
        note = new EditText(this);
        note.setId(103);
        note.setHint("Anteckning");
        form.addView(note);
        cornerButton = button(form, "Lägg till GPS-hörn", this::addCorner);
        undoButton = button(form, "Ångra senaste hörn", () -> {
            corners.remove(corners.size() - 1);
            refresh();
        });
        save = button(form, "Spara punkt", this::savePoint);
        if (state != null) {
            polygon = state.getBoolean("polygon");
            java.util.ArrayList<Location> restored = state.getParcelableArrayList("corners");
            if (restored != null) corners.addAll(restored);
        }
        updateForm();
        saved = new TextView(this);
        form.addView(saved);
        export = button(form, "Exportera GeoJSON", this::chooseExport);
        refresh();
        refreshCount();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean("polygon", polygon);
        state.putParcelableArrayList("corners", corners);
    }

    private void switchGeometry() {
        if (polygon && !corners.isEmpty()) {
            new android.app.AlertDialog.Builder(this)
                    .setMessage("Byta mätläge och kassera osparade hörn?")
                    .setNegativeButton("Fortsätt mäta", null)
                    .setPositiveButton("Byt mätläge", (dialog, which) -> {
                        corners.clear();
                        toggleGeometry();
                    }).show();
            return;
        }
        toggleGeometry();
    }

    private void toggleGeometry() {
        polygon = !polygon;
        updateForm();
        refresh();
    }

    private void updateForm() {
        title.setText(polygon ? "Mät in gräsyta" : "Mät in brunn");
        identifier.setHint(polygon ? "Yt-ID (obligatoriskt)" : "Brunns-ID (obligatoriskt)");
        cornerButton.setVisibility(polygon ? android.view.View.VISIBLE : android.view.View.GONE);
        undoButton.setVisibility(polygon ? android.view.View.VISIBLE : android.view.View.GONE);
        save.setText(polygon ? "Spara yta" : "Spara punkt");
    }

    private void addCorner() {
        refresh();
        if (cornerButton.isEnabled()) {
            corners.add(new Location(fix));
            refresh();
        }
    }

    private Button button(LinearLayout form, String text, Runnable action) {
        Button button = new Button(this);
        button.setText(text);
        button.setOnClickListener(view -> action.run());
        form.addView(button);
        return button;
    }

    @Override
    protected void onResume() {
        super.onResume();
        startGps();
    }

    @Override
    protected void onPause() {
        locations.removeUpdates(listener);
        handler.removeCallbacks(expire);
        fix = null;
        super.onPause();
    }

    private boolean precisePermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void activateGps() {
        if (!precisePermission()) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION);
        } else if (!locations.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        } else {
            startGps();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(code, permissions, grants);
        if (code == LOCATION_PERMISSION) startGps();
    }

    private void startGps() {
        if (precisePermission() && locations.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 0, listener);
        }
        refresh();
    }

    private long age(Location point) {
        return SystemClock.elapsedRealtime() - point.getElapsedRealtimeNanos() / 1_000_000;
    }

    private boolean meetsTarget(Location point) {
        return point.hasAccuracy() && point.getAccuracy() <= PLAN_TARGET_METRES;
    }

    private void refresh() {
        handler.removeCallbacks(expire);
        boolean enabled = precisePermission() && locations.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean fresh = enabled && fix != null && age(fix) < MAX_AGE_MS;
        cornerButton.setEnabled(polygon && fresh);
        undoButton.setEnabled(polygon && !corners.isEmpty());
        save.setEnabled(features != null && (polygon ? corners.size() >= 3 : fresh));
        if (!enabled) {
            status.setText("Aktivera GPS och ge appen exakt platsbehörighet.");
        } else if (!fresh) {
            status.setText("Väntar på en aktuell GPS-position …");
        } else {
            String plan = fix.hasAccuracy() ? String.format(Locale.forLanguageTag("sv-SE"),
                    "±%.2f m", fix.getAccuracy()) : "okänd";
            String height = fix.hasAltitude() ? String.format(Locale.forLanguageTag("sv-SE"),
                    "%.2f m över WGS 84-ellipsoiden", fix.getAltitude()) : "saknas";
            if (fix.hasAltitude() && fix.hasVerticalAccuracy()) {
                height += String.format(Locale.forLanguageTag("sv-SE"), " (±%.2f m)", fix.getVerticalAccuracyMeters());
            }
            status.setText("Uppskattad osäkerhet i plan " + plan + "\nHöjd " + height
                    + (meetsTarget(fix) ? "\nInom målet enligt GPS-uppskattningen" : "\nPreliminär · mål 20 cm"));
            handler.postDelayed(expire, Math.max(1, MAX_AGE_MS - age(fix)));
        }
        if (polygon) status.append("\nRegistrerade hörn: " + corners.size());
    }

    private void savePoint() {
        refresh();
        if (!save.isEnabled()) return;
        String id = identifier.getText().toString().trim();
        if (id.isEmpty()) {
            identifier.setError(polygon ? "Ange yt-ID" : "Ange brunns-ID");
            return;
        }
        try {
            JSONObject properties = new JSONObject()
                    .put(polygon ? "yta_id" : "brunn_id", id)
                    .put("objekttyp", polygon ? "Gräsyta" : "Brunn")
                    .put("skick", condition.getSelectedItem().toString())
                    .put("anteckning", note.getText().toString().trim())
                    .put("kalla", "Telefonens GPS");
            JSONObject geometry = polygon ? polygonGeometry(properties) : pointGeometry(properties);
            if (geometry == null) return;
            JSONObject feature = new JSONObject().put("type", "Feature")
                    .put("id", UUID.randomUUID().toString()).put("properties", properties).put("geometry", geometry);
            JSONArray next = new JSONArray(features.toString()).put(feature);
            if (!getPreferences(MODE_PRIVATE).edit().putString("features", next.toString()).commit()) {
                message("Objektet kunde inte sparas.");
                return;
            }
            features = next;
            corners.clear();
            refresh();
            identifier.setText("");
            note.setText("");
            condition.setSelection(0);
            refreshCount();
            message(polygon ? "Ytan sparades." : "Punkten sparades.");
        } catch (JSONException error) {
            message("Objektet kunde inte sparas.");
        }
    }

    private JSONObject pointGeometry(JSONObject properties) throws JSONException {
        JSONObject measurement = measurement(fix);
        java.util.Iterator<String> keys = measurement.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            properties.put(key, measurement.get(key));
        }
        JSONArray coordinates = coordinates(fix);
        if (fix.hasAltitude()) coordinates.put(fix.getAltitude());
        return new JSONObject().put("type", "Point").put("coordinates", coordinates);
    }

    private JSONObject polygonGeometry(JSONObject properties) throws JSONException {
        java.util.ArrayList<PolygonRing.Vertex> vertices = new java.util.ArrayList<>();
        for (Location corner : corners) {
            vertices.add(new PolygonRing.Vertex(corner.getLongitude(), corner.getLatitude()));
        }
        try {
            PolygonRing.validate(vertices);
        } catch (IllegalArgumentException error) {
            message(error.getMessage());
            return null;
        }
        JSONArray ring = new JSONArray();
        JSONArray measurements = new JSONArray();
        boolean preliminary = false;
        boolean reverse = PolygonRing.signedArea(vertices) < 0;
        for (int i = 0; i < corners.size(); i++) {
            Location corner = corners.get(reverse && i > 0 ? corners.size() - i : i);
            ring.put(coordinates(corner));
            measurements.put(measurement(corner));
            preliminary |= !meetsTarget(corner);
        }
        ring.put(coordinates(corners.get(0)));
        properties.put("preliminar", preliminary).put("hornmatningar", measurements);
        return new JSONObject().put("type", "Polygon").put("coordinates", new JSONArray().put(ring));
    }

    private JSONArray coordinates(Location point) throws JSONException {
        return new JSONArray().put(point.getLongitude()).put(point.getLatitude());
    }

    private JSONObject measurement(Location point) throws JSONException {
        return new JSONObject()
                .put("matt_utc", Instant.ofEpochMilli(point.getTime()).toString())
                .put("preliminar", !meetsTarget(point))
                .put("plan_osakerhet_m", point.hasAccuracy() ? (Object) point.getAccuracy() : JSONObject.NULL)
                .put("hojd_m", point.hasAltitude() ? (Object) point.getAltitude() : JSONObject.NULL)
                .put("hojdreferens", point.hasAltitude() ? "WGS 84-ellipsoid" : JSONObject.NULL)
                .put("hojd_osakerhet_m", point.hasAltitude() && point.hasVerticalAccuracy()
                        ? (Object) point.getVerticalAccuracyMeters() : JSONObject.NULL);
    }

    private void refreshCount() {
        saved.setText(features == null ? "Sparade objekt kunde inte läsas." : "Sparade objekt: " + features.length());
        export.setEnabled(features != null && features.length() > 0);
    }

    private void chooseExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("application/geo+json")
                .putExtra(Intent.EXTRA_TITLE, "plopmap-inmatning.geojson");
        startActivityForResult(intent, EXPORT_FILE);
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code != EXPORT_FILE || result != RESULT_OK || data == null || data.getData() == null || features == null) return;
        try (OutputStream output = getContentResolver().openOutputStream(data.getData(), "wt")) {
            if (output == null) throw new IOException("Output unavailable");
            JSONObject collection = new JSONObject().put("type", "FeatureCollection").put("features", features);
            output.write(collection.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException | JSONException error) {
            message("Exporten misslyckades.");
            return;
        }
        message("GeoJSON exporterades.");
    }

    private void message(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }
}
