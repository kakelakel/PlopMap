package se.plopmap;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class GeoPackageExport {
    private static final int SRS = 4326;

    static void write(JSONArray features, File cache, OutputStream output) throws IOException, JSONException {
        File file = File.createTempFile("plopmap-", ".gpkg", cache);
        try {
            try (SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(file, null)) {
                db.execSQL("PRAGMA application_id = 1196444487");
                db.execSQL("PRAGMA user_version = 10300");
                db.beginTransaction();
                try {
                    initialize(db);
                    for (int i = 0; i < features.length(); i++) insertFeature(db, features.getJSONObject(i));
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }
            try (FileInputStream input = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int bytes;
                while ((bytes = input.read(buffer)) != -1) output.write(buffer, 0, bytes);
            }
        } finally {
            SQLiteDatabase.deleteDatabase(file);
        }
    }

    private static void initialize(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE gpkg_spatial_ref_sys (srs_name TEXT NOT NULL, srs_id INTEGER PRIMARY KEY, "
                + "organization TEXT NOT NULL, organization_coordsys_id INTEGER NOT NULL, definition TEXT NOT NULL, description TEXT)");
        db.execSQL("CREATE TABLE gpkg_contents (table_name TEXT PRIMARY KEY, data_type TEXT NOT NULL, identifier TEXT UNIQUE, "
                + "description TEXT DEFAULT '', last_change DATETIME NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')), "
                + "min_x DOUBLE, min_y DOUBLE, max_x DOUBLE, max_y DOUBLE, srs_id INTEGER REFERENCES gpkg_spatial_ref_sys(srs_id))");
        db.execSQL("CREATE TABLE gpkg_geometry_columns (table_name TEXT NOT NULL REFERENCES gpkg_contents(table_name), "
                + "column_name TEXT NOT NULL, geometry_type_name TEXT NOT NULL, srs_id INTEGER NOT NULL REFERENCES "
                + "gpkg_spatial_ref_sys(srs_id), z TINYINT NOT NULL, m TINYINT NOT NULL, PRIMARY KEY(table_name,column_name))");
        db.execSQL("INSERT INTO gpkg_spatial_ref_sys VALUES ('Undefined Cartesian',-1,'NONE',-1,'undefined',''),"
                + "('Undefined geographic',0,'NONE',0,'undefined','')");
        db.execSQL("INSERT INTO gpkg_spatial_ref_sys VALUES ('WGS 84',4326,'EPSG',4326,?,'')",
                new Object[]{ExportCrs.WGS84.wkt()});
        layer(db, "punkter", "POINT");
        layer(db, "linjer", "LINESTRING");
        layer(db, "ytor", "POLYGON");
        layer(db, "brytpunkter", "POINT");
    }

    private static void layer(SQLiteDatabase db, String name, String type) {
        db.execSQL("CREATE TABLE " + name + " (fid INTEGER PRIMARY KEY AUTOINCREMENT, geom " + type
                + " NOT NULL, uuid TEXT, objekt_id TEXT, typ TEXT, projekt TEXT, skick TEXT, anteckning TEXT, "
                + "kalla TEXT, fixstatus TEXT, preliminar BOOLEAN, horn_nr INTEGER, hojd_m REAL, "
                + "plan_m REAL, hojdosak_m REAL, hojdref TEXT, matt_utc TEXT, attribut TEXT)");
        db.execSQL("INSERT INTO gpkg_contents (table_name,data_type,identifier,srs_id) VALUES (?,'features',?,4326)",
                new Object[]{name, name});
        db.execSQL("INSERT INTO gpkg_geometry_columns VALUES (?,'geom',?,4326,0,0)", new Object[]{name, type});
    }

    private static void insertFeature(SQLiteDatabase db, JSONObject feature) throws JSONException {
        JSONObject geometry = feature.getJSONObject("geometry");
        JSONObject properties = feature.getJSONObject("properties");
        JSONArray coordinates = geometry.getJSONArray("coordinates");
        String type = geometry.getString("type");
        switch (type) {
            case "Point":
                ContentValues point = row(feature, properties, properties);
                if (!point.containsKey("hojd_m") && coordinates.length() > 2) point.put("hojd_m", coordinates.getDouble(2));
                point.put("geom", geometry(1, new JSONArray().put(coordinates)));
                db.insertOrThrow("punkter", null, point);
                return;
            case "LineString":
            case "Polygon":
                break;
            default:
                throw new JSONException("Unknown geometry: " + type);
        }
        boolean polygon = "Polygon".equals(type);
        JSONArray path = polygon ? coordinates.getJSONArray(0) : coordinates;
        ContentValues object = row(feature, properties, properties);
        object.put("geom", geometry(polygon ? 3 : 2, path));
        db.insertOrThrow(polygon ? "ytor" : "linjer", null, object);
        JSONArray measurements = properties.optJSONArray("hornmatningar");
        if (measurements == null) return;
        for (int i = 0; i < measurements.length(); i++) {
            ContentValues vertex = row(feature, properties, measurements.getJSONObject(i));
            vertex.put("geom", geometry(1, new JSONArray().put(path.getJSONArray(i))));
            vertex.put("horn_nr", i + 1);
            db.insertOrThrow("brytpunkter", null, vertex);
        }
    }

    private static ContentValues row(JSONObject feature, JSONObject properties, JSONObject measurement) {
        ContentValues row = new ContentValues();
        row.put("uuid", feature.optString("id"));
        row.put("objekt_id", properties.optString("brunn_id", properties.optString("yta_id", properties.optString("linje_id"))));
        row.put("typ", properties.optString("objekttyp", properties.has("brunn_id") ? "Brunn" : "Gräsyta"));
        row.put("projekt", properties.optString("projekt", "Standard"));
        for (String key : new String[]{"skick", "anteckning"}) row.put(key, properties.optString(key));
        row.put("kalla", measurement.optString("kalla", properties.optString("kalla")));
        row.put("fixstatus", measurement.optString("fixstatus", ""));
        if (measurement.has("preliminar")) row.put("preliminar", measurement.optBoolean("preliminar"));
        putNumber(row, "hojd_m", measurement.optDouble("hojd_m"));
        putNumber(row, "plan_m", measurement.optDouble("plan_osakerhet_m"));
        putNumber(row, "hojdosak_m", measurement.optDouble("hojd_osakerhet_m"));
        if (!measurement.isNull("hojdreferens")) row.put("hojdref", measurement.optString("hojdreferens"));
        if (!measurement.isNull("matt_utc")) row.put("matt_utc", measurement.optString("matt_utc"));
        JSONObject attributes = properties.optJSONObject("attribut");
        if (attributes != null) row.put("attribut", attributes.toString());
        return row;
    }

    private static void putNumber(ContentValues row, String column, double value) {
        if (Double.isFinite(value)) row.put(column, value);
    }

    static byte[] geometry(int type, JSONArray path) throws JSONException {
        int count = path.length();
        ByteBuffer bytes = ByteBuffer.allocate(13 + (type == 1 ? 0 : type == 2 ? 4 : 8) + 16 * count)
                .order(ByteOrder.LITTLE_ENDIAN);
        bytes.put((byte) 'G').put((byte) 'P').put((byte) 0).put((byte) 1).putInt(SRS);
        bytes.put((byte) 1).putInt(type);
        if (type == 3) bytes.putInt(1);
        if (type != 1) bytes.putInt(count);
        for (int i = 0; i < count; i++) {
            JSONArray point = path.getJSONArray(i);
            bytes.putDouble(point.getDouble(0)).putDouble(point.getDouble(1));
        }
        return bytes.array();
    }
}
