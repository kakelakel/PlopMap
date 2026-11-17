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
    private static final String DEFAULT_PROJECT = "Standard";
    private JSONArray allFeatures;
    private JSONArray features;
    private String project;
    private Button projectButton;
    private final java.util.ArrayList<Location> corners = new java.util.ArrayList<>();
    private static final String[] TYPES = {"Brunn", "Gräsyta", "Linje"};
    private static final String[] ID_FIELDS = {"brunn_id", "yta_id", "linje_id"};
    private int mode;
    private boolean polygon() { return mode == 1; }
    private boolean multipart() { return mode != 0; }
    private SurveyMap map;
    private int exportFormat;
    private TextView title;
    private Button cornerButton;
    private Button undoButton;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        locations = getSystemService(LocationManager.class);
        try {
            allFeatures = new JSONArray(getPreferences(MODE_PRIVATE).getString("features", "[]"));
        } catch (JSONException error) {
            allFeatures = null;
        }

        project = getPreferences(MODE_PRIVATE).getString("project", DEFAULT_PROJECT);
        selectProjectFeatures();

        ScrollView scroll = new ScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        form.setPadding(padding, padding, padding, padding);
        scroll.addView(form);
        setContentView(scroll);

        projectButton = button(form, "Projekt · " + project, this::chooseProject);
        title = new TextView(this);
        title.setText("Mät in brunn");
        title.setTextSize(28);
        form.addView(title);
        button(form, "Välj objekttyp", this::switchGeometry);
        map = new SurveyMap(this);
        form.addView(map, new LinearLayout.LayoutParams(-1, padding * 8));
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
            mode = state.getInt("mode", state.getBoolean("polygon") ? 1 : 0);
            exportFormat = state.getInt("exportFormat", state.getBoolean("shapefile") ? 1 : 0);
            java.util.ArrayList<Location> restored = state.getParcelableArrayList("corners");
            if (restored != null) corners.addAll(restored);
        }
        updateForm();
        saved = new TextView(this);
        form.addView(saved);
        export = button(form, "Exportera", this::chooseExport);
        refresh();
        refreshCount();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("mode", mode);
        state.putInt("exportFormat", exportFormat);
        state.putParcelableArrayList("corners", corners);
    }

    private void selectProjectFeatures() {
        if (allFeatures == null) {
            features = null;
            return;
        }
        features = new JSONArray();
        for (int i = 0; i < allFeatures.length(); i++) {
            JSONObject item = allFeatures.optJSONObject(i);
            JSONObject properties = item == null ? null : item.optJSONObject("properties");
            if (properties != null && project.equals(properties.optString("projekt", DEFAULT_PROJECT))) {
                features.put(item);
            }
        }
    }

    private void chooseProject() {
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        names.add(DEFAULT_PROJECT);
        names.addAll(getPreferences(MODE_PRIVATE).getStringSet("projects", java.util.Collections.emptySet()));
        names.add(project);
        if (allFeatures != null) for (int i = 0; i < allFeatures.length(); i++) {
            JSONObject item = allFeatures.optJSONObject(i);
            JSONObject properties = item == null ? null : item.optJSONObject("properties");
            if (properties != null) names.add(properties.optString("projekt", DEFAULT_PROJECT));
        }
        java.util.List<String> options = new java.util.ArrayList<>(names);
        options.add("Nytt projekt …");
        new android.app.AlertDialog.Builder(this).setTitle("Projekt")
                .setItems(options.toArray(new String[0]), (dialog, index) -> {
                    if (index < names.size()) {
                        requestProject(options.get(index));
                    } else {
                        EditText name = new EditText(this);
                        name.setSingleLine(true);
                        name.setHint("Projektnamn");
                        new android.app.AlertDialog.Builder(this).setTitle("Nytt projekt")
                                .setView(name).setNegativeButton("Avbryt", null)
                                .setPositiveButton("Öppna", (prompt, which) -> {
                                    String value = name.getText().toString().trim();
                                    if (value.isEmpty()) message("Ange ett projektnamn.");
                                    else requestProject(value);
                                }).show();
                    }
                }).show();
    }

    private void requestProject(String name) {
        if (project.equals(name)) return;
        if (corners.isEmpty() && identifier.length() == 0 && note.length() == 0
                && condition.getSelectedItemPosition() == 0) {
            openProject(name);
            return;
        }
        new android.app.AlertDialog.Builder(this).setMessage("Kassera osparad inmätning och byta projekt?")
                .setNegativeButton("Fortsätt mäta", null)
                .setPositiveButton("Byt projekt", (dialog, which) -> openProject(name)).show();
    }

    private void openProject(String name) {
        java.util.Set<String> names = new java.util.HashSet<>(
                getPreferences(MODE_PRIVATE).getStringSet("projects", java.util.Collections.emptySet()));
        names.add(name);
        if (!getPreferences(MODE_PRIVATE).edit().putString("project", name).putStringSet("projects", names).commit()) {
            message("Projektet kunde inte öppnas.");
            return;
        }
        project = name;
        projectButton.setText("Projekt · " + project);
        corners.clear();
        identifier.setText("");
        note.setText("");
        condition.setSelection(0);
        selectProjectFeatures();
        refresh();
        refreshCount();
    }

    private void switchGeometry() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Objekttyp")
                .setSingleChoiceItems(TYPES, mode, (dialog, selected) -> {
                    dialog.dismiss();
                    if (selected == mode) return;
                    if (corners.isEmpty()) {
                        changeMode(selected);
                    } else {
                        new android.app.AlertDialog.Builder(this)
                                .setMessage("Kassera osparade hörn och byta objekttyp?")
                                .setNegativeButton("Fortsätt mäta", null)
                                .setPositiveButton("Byt", (confirm, which) -> changeMode(selected))
                                .show();
                    }
                }).show();
    }

    private void changeMode(int selected) {
        corners.clear();
        mode = selected;
        updateForm();
        refresh();
    }

    private void updateForm() {
        title.setText(TYPES[mode]);
        identifier.setHint(TYPES[mode] + " · ID (obligatoriskt)");
        cornerButton.setVisibility(multipart() ? android.view.View.VISIBLE : android.view.View.GONE);
        cornerButton.setText(polygon() ? "Lägg till GPS-hörn" : "Lägg till brytpunkt");
        undoButton.setVisibility(multipart() ? android.view.View.VISIBLE : android.view.View.GONE);
        save.setText(mode == 0 ? "Spara punkt" : polygon() ? "Spara yta" : "Spara linje");
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
        cornerButton.setEnabled(multipart() && fresh);
        undoButton.setEnabled(multipart() && !corners.isEmpty());
        save.setEnabled(features != null && (multipart() ? corners.size() >= (polygon() ? 3 : 2) : fresh));
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
        if (multipart()) status.append("\nRegistrerade brytpunkter: " + corners.size());
        map.update(features, corners, fresh ? fix : null, polygon());
    }

    private void savePoint() {
        refresh();
        if (!save.isEnabled()) return;
        String id = identifier.getText().toString().trim();
        if (id.isEmpty()) {
            identifier.setError("Ange objektets ID");
            return;
        }
        try {
            JSONObject properties = new JSONObject()
                    .put(ID_FIELDS[mode], id)
                    .put("objekttyp", TYPES[mode])
                    .put("projekt", project)
                    .put("skick", condition.getSelectedItem().toString())
                    .put("anteckning", note.getText().toString().trim())
                    .put("kalla", "Telefonens GPS");
            JSONObject geometry = multipart() ? pathGeometry(properties) : pointGeometry(properties);
            if (geometry == null) return;
            JSONObject feature = new JSONObject().put("type", "Feature")
                    .put("id", UUID.randomUUID().toString()).put("properties", properties).put("geometry", geometry);
            JSONArray next = new JSONArray(allFeatures.toString()).put(feature);
            if (!getPreferences(MODE_PRIVATE).edit().putString("features", next.toString()).commit()) {
                message("Objektet kunde inte sparas.");
                return;
            }
            allFeatures = next;
            selectProjectFeatures();
            corners.clear();
            refresh();
            identifier.setText("");
            note.setText("");
            condition.setSelection(0);
            refreshCount();
            message("Objektet sparades.");
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

    private JSONObject pathGeometry(JSONObject properties) throws JSONException {
        java.util.ArrayList<PolygonRing.Vertex> vertices = new java.util.ArrayList<>();
        for (Location corner : corners) {
            vertices.add(new PolygonRing.Vertex(corner.getLongitude(), corner.getLatitude()));
        }
        try {
            if (polygon()) PolygonRing.validate(vertices);
            else for (int i = 1; i < vertices.size(); i++) {
                if (vertices.get(i).equals(vertices.get(i - 1))) {
                    throw new IllegalArgumentException("Två brytpunkter i följd har samma position.");
                }
            }
        } catch (IllegalArgumentException error) {
            message(error.getMessage());
            return null;
        }
        JSONArray ring = new JSONArray();
        JSONArray measurements = new JSONArray();
        boolean preliminary = false;
        boolean reverse = polygon() && PolygonRing.signedArea(vertices) < 0;
        for (int i = 0; i < corners.size(); i++) {
            Location corner = corners.get(reverse && i > 0 ? corners.size() - i : i);
            ring.put(coordinates(corner));
            measurements.put(measurement(corner));
            preliminary |= !meetsTarget(corner);
        }
        if (polygon()) ring.put(coordinates(corners.get(0)));
        properties.put("preliminar", preliminary).put("hornmatningar", measurements);
        return new JSONObject().put("type", polygon() ? "Polygon" : "LineString")
                .put("coordinates", polygon() ? new JSONArray().put(ring) : ring);
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
        new android.app.AlertDialog.Builder(this).setTitle("Exportformat")
                .setItems(new String[]{"GeoJSON · WGS 84", "Shapefile · WGS 84", "Shapefile · SWEREF 99 TM"},
                        (dialog, which) -> createExport(which))
                .show();
    }

    private void createExport(int format) {
        exportFormat = format;
        boolean zip = format != 0;
        if (format == 2) try {
            for (int i = 0; i < features.length(); i++) {
                JSONObject geometry = features.getJSONObject(i).getJSONObject("geometry");
                JSONArray coordinates = geometry.getJSONArray("coordinates");
                switch (geometry.getString("type")) {
                    case "Point": coordinates = new JSONArray().put(coordinates); break;
                    case "Polygon": coordinates = coordinates.getJSONArray(0); break;
                    default: break;
                }
                for (int j = 0; j < coordinates.length(); j++) {
                    JSONArray point = coordinates.getJSONArray(j);
                    ExportCrs.SWEREF99_TM.project(point.getDouble(0), point.getDouble(1));
                }
            }
        } catch (IllegalArgumentException error) {
            message(error.getMessage());
            return;
        } catch (JSONException error) {
            message("Projektets koordinater kunde inte läsas.");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType(zip ? "application/zip" : "application/geo+json")
                .putExtra(Intent.EXTRA_TITLE, zip ? (format == 2 ? "plopmap-sweref99tm.zip" : "plopmap-wgs84.zip") : "plopmap-inmatning.geojson");
        startActivityForResult(intent, EXPORT_FILE);
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code != EXPORT_FILE || result != RESULT_OK || data == null || data.getData() == null || features == null) return;
        try (OutputStream output = getContentResolver().openOutputStream(data.getData(), "wt")) {
            if (output == null) throw new IOException("Output unavailable");
            if (exportFormat != 0) ShapefileExport.write(features, output,
                    exportFormat == 2 ? ExportCrs.SWEREF99_TM : ExportCrs.WGS84);
            else {
                JSONObject collection = new JSONObject().put("type", "FeatureCollection").put("features", features);
                output.write(collection.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException | JSONException | IllegalArgumentException error) {
            message("Exporten misslyckades.");
            return;
        }
        message(exportFormat != 0 ? "Shapefile exporterades." : "GeoJSON exporterades.");
    }

    private void message(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }
}
