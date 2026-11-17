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
    private final LocationListener listener = location -> {
        fix = location;
        refresh();
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

        TextView title = new TextView(this);
        title.setText("Mät in brunn");
        title.setTextSize(28);
        form.addView(title);
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
        save = button(form, "Spara punkt", this::savePoint);
        saved = new TextView(this);
        form.addView(saved);
        export = button(form, "Exportera GeoJSON", this::chooseExport);
        refresh();
        refreshCount();
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
        save.setEnabled(fresh && features != null);
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
    }

    private void savePoint() {
        refresh();
        if (!save.isEnabled()) return;
        String id = identifier.getText().toString().trim();
        if (id.isEmpty()) {
            identifier.setError("Ange brunns-ID");
            return;
        }
        try {
            JSONObject properties = new JSONObject()
                    .put("brunn_id", id)
                    .put("skick", condition.getSelectedItem().toString())
                    .put("anteckning", note.getText().toString().trim())
                    .put("kalla", "Telefonens GPS")
                    .put("matt_utc", Instant.ofEpochMilli(fix.getTime()).toString())
                    .put("preliminar", !meetsTarget(fix))
                    .put("plan_osakerhet_m", fix.hasAccuracy() ? (Object) fix.getAccuracy() : JSONObject.NULL)
                    .put("hojd_osakerhet_m", fix.hasAltitude() && fix.hasVerticalAccuracy()
                            ? (Object) fix.getVerticalAccuracyMeters() : JSONObject.NULL)
                    .put("hojdreferens", fix.hasAltitude() ? "WGS 84-ellipsoid" : JSONObject.NULL);
            JSONArray coordinates = new JSONArray().put(fix.getLongitude()).put(fix.getLatitude());
            if (fix.hasAltitude()) coordinates.put(fix.getAltitude());
            JSONObject geometry = new JSONObject().put("type", "Point").put("coordinates", coordinates);
            JSONObject feature = new JSONObject().put("type", "Feature")
                    .put("id", UUID.randomUUID().toString()).put("properties", properties).put("geometry", geometry);
            JSONArray next = new JSONArray(features.toString()).put(feature);
            if (!getPreferences(MODE_PRIVATE).edit().putString("features", next.toString()).commit()) {
                message("Punkten kunde inte sparas.");
                return;
            }
            features = next;
            identifier.setText("");
            note.setText("");
            condition.setSelection(0);
            refreshCount();
            message("Punkten sparades.");
        } catch (JSONException error) {
            message("Punkten kunde inte sparas.");
        }
    }

    private void refreshCount() {
        saved.setText(features == null ? "Sparade punkter kunde inte läsas." : "Sparade punkter: " + features.length());
        export.setEnabled(features != null && features.length() > 0);
    }

    private void chooseExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("application/geo+json")
                .putExtra(Intent.EXTRA_TITLE, "plopmap-brunnar.geojson");
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
