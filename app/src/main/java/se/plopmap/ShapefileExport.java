package se.plopmap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class ShapefileExport {
    private record Field(String name, char type, int width, int decimals) {}
    private record Row(List<PolygonRing.Vertex> points, String[] values) {}

    private static final Field[] FIELDS = {
            new Field("uuid", 'C', 36, 0), new Field("objekt_id", 'C', 254, 0),
            new Field("typ", 'C', 24, 0), new Field("skick", 'C', 40, 0),
            new Field("anteckn", 'C', 254, 0), new Field("kalla", 'C', 40, 0),
            new Field("prelim", 'L', 1, 0), new Field("horn_nr", 'N', 9, 0),
            new Field("hojd_m", 'N', 16, 4), new Field("plan_m", 'N', 16, 4),
            new Field("hojdosak_m", 'N', 16, 4), new Field("hojdref", 'C', 32, 0),
            new Field("matt_utc", 'C', 32, 0), new Field("fixstatus", 'C', 24, 0)
    };

    static void write(JSONArray features, OutputStream output, ExportCrs crs) throws JSONException, IOException {
        List<Row> points = new ArrayList<>();
        List<Row> lines = new ArrayList<>();
        List<Row> polygons = new ArrayList<>();
        List<Row> vertices = new ArrayList<>();
        for (int i = 0; i < features.length(); i++) {
            JSONObject feature = features.getJSONObject(i);
            JSONObject geometry = feature.getJSONObject("geometry");
            JSONObject properties = feature.getJSONObject("properties");
            JSONArray coordinates = geometry.getJSONArray("coordinates");
            String type = geometry.getString("type");
            if ("Point".equals(type)) {
                String[] fields = values(feature, properties, properties, "");
                if (fields[8].isEmpty() && coordinates.length() > 2) fields[8] = number(coordinates, 2);
                points.add(new Row(java.util.Collections.singletonList(vertex(coordinates, crs)), fields));
                continue;
            }
            boolean polygon = "Polygon".equals(type);
            if (!polygon && !"LineString".equals(type)) throw new JSONException("Unsupported geometry: " + type);
            JSONArray path = polygon ? coordinates.getJSONArray(0) : coordinates;
            List<PolygonRing.Vertex> geometryPoints = new ArrayList<>();
            JSONArray measurements = properties.getJSONArray("hornmatningar");
            for (int j = 0; j < path.length(); j++) {
                var point = vertex(path.getJSONArray(j), crs);
                geometryPoints.add(point);
                if (j < measurements.length()) {
                    vertices.add(new Row(java.util.Collections.singletonList(point),
                            values(feature, properties, measurements.getJSONObject(j), Integer.toString(j + 1))));
                }
            }
            if (polygon && PolygonRing.signedArea(geometryPoints) > 0) {
                java.util.Collections.reverse(geometryPoints);
            }
            (polygon ? polygons : lines).add(new Row(geometryPoints, values(feature, properties, properties, "")));
        }

        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            layer(zip, "punkter", 1, points, crs);
            layer(zip, "linjer", 3, lines, crs);
            layer(zip, "ytor", 5, polygons, crs);
            layer(zip, "brytpunkter", 1, vertices, crs);
            entry(zip, "objektattribut.geojson",
                    new JSONObject().put("type", "FeatureCollection").put("features", features).toString());
        }
    }

    private static PolygonRing.Vertex vertex(JSONArray point, ExportCrs crs) throws JSONException {
        return crs.project(point.getDouble(0), point.getDouble(1));
    }

    private static String[] values(JSONObject feature, JSONObject properties, JSONObject measurement, String index) {
        String id = properties.optString("brunn_id",
                properties.optString("yta_id", properties.optString("linje_id")));
        return new String[]{feature.optString("id"), id,
                properties.optString("objekttyp", properties.has("brunn_id") ? "Brunn" : "Gräsyta"),
                properties.optString("skick"), properties.optString("anteckning"), measurement.optString("kalla", properties.optString("kalla")),
                measurement.has("preliminar") ? (measurement.optBoolean("preliminar") ? "T" : "F") : "?",
                index, number(measurement.optDouble("hojd_m")), number(measurement.optDouble("plan_osakerhet_m")),
                number(measurement.optDouble("hojd_osakerhet_m")), measurement.optString("hojdreferens", ""),
                measurement.optString("matt_utc", ""), measurement.optString("fixstatus", "")};
    }

    private static String number(JSONArray value, int index) {
        return number(value.optDouble(index));
    }

    private static String number(double value) {
        return Double.isFinite(value) ? String.format(java.util.Locale.ROOT, "%.4f", value) : "";
    }

    private static void layer(ZipOutputStream zip, String name, int type, List<Row> rows, ExportCrs crs) throws IOException {
        if (rows.isEmpty()) return;
        Bounds bounds = new Bounds();
        int length = 100;
        for (Row row : rows) {
            length += 8 + size(type, row);
            for (var point : row.points()) bounds.add(point);
        }
        zip.putNextEntry(new ZipEntry(name + ".shp"));
        zip.write(header(length, type, bounds));
        int record = 1;
        for (Row row : rows) {
            int size = size(type, row);
            ByteBuffer buffer = ByteBuffer.allocate(size + 8);
            buffer.putInt(record++).putInt(size / 2).order(ByteOrder.LITTLE_ENDIAN).putInt(type);
            if (type != 1) {
                Bounds area = new Bounds();
                for (var point : row.points()) area.add(point);
                area.write(buffer);
                buffer.putInt(1).putInt(row.points().size()).putInt(0);
            }
            for (var point : row.points()) buffer.putDouble(point.x()).putDouble(point.y());
            zip.write(buffer.array());
        }
        zip.closeEntry();

        zip.putNextEntry(new ZipEntry(name + ".shx"));
        zip.write(header(100 + 8 * rows.size(), type, bounds));
        int offset = 50;
        for (Row row : rows) {
            int words = size(type, row) / 2;
            zip.write(ByteBuffer.allocate(8).putInt(offset).putInt(words).array());
            offset += 4 + words;
        }
        zip.closeEntry();

        writeTable(zip, name, rows);
        entry(zip, name + ".prj", crs.wkt());
        entry(zip, name + ".cpg", "UTF-8");
    }

    private static int size(int type, Row row) {
        return type == 1 ? 20 : 48 + 16 * row.points().size();
    }

    private static byte[] header(int length, int type, Bounds bounds) {
        ByteBuffer header = ByteBuffer.allocate(100).putInt(9994);
        header.position(24);
        header.putInt(length / 2).order(ByteOrder.LITTLE_ENDIAN).putInt(1000).putInt(type);
        bounds.write(header);
        return header.array();
    }

    private static void writeTable(ZipOutputStream zip, String name, List<Row> rows) throws IOException {
        int headerSize = 33 + 32 * FIELDS.length;
        int rowSize = 1;
        for (Field field : FIELDS) rowSize += field.width();
        LocalDate day = LocalDate.now();
        ByteBuffer header = ByteBuffer.allocate(headerSize).order(ByteOrder.LITTLE_ENDIAN);
        header.put((byte) 3).put((byte) (day.getYear() - 1900)).put((byte) day.getMonthValue())
                .put((byte) day.getDayOfMonth()).putInt(rows.size()).putShort((short) headerSize).putShort((short) rowSize);
        for (int i = 0; i < FIELDS.length; i++) {
            Field field = FIELDS[i];
            int offset = 32 + i * 32;
            header.position(offset);
            header.put(field.name().getBytes(StandardCharsets.US_ASCII));
            header.position(offset + 11);
            header.put((byte) field.type()).putInt(0).put((byte) field.width()).put((byte) field.decimals());
        }
        header.put(headerSize - 1, (byte) 13);
        zip.putNextEntry(new ZipEntry(name + ".dbf"));
        zip.write(header.array());
        for (Row row : rows) {
            byte[] record = new byte[rowSize];
            java.util.Arrays.fill(record, (byte) ' ');
            int offset = 1;
            for (int i = 0; i < FIELDS.length; i++) {
                Field field = FIELDS[i];
                byte[] bytes = row.values()[i].getBytes(StandardCharsets.UTF_8);
                int count = Math.min(bytes.length, field.width());
                while (count < bytes.length && count > 0 && (bytes[count] & 0xc0) == 0x80) count--;
                if (field.type() != 'N' || bytes.length <= field.width()) {
                    int start = field.type() == 'N' ? offset + field.width() - count : offset;
                    System.arraycopy(bytes, 0, record, start, count);
                }
                offset += field.width();
            }
            zip.write(record);
        }
        zip.write(26);
        zip.closeEntry();
    }

    private static void entry(ZipOutputStream zip, String name, String text) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static final class Bounds {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;

        void add(PolygonRing.Vertex point) {
            minX = Math.min(minX, point.x());
            minY = Math.min(minY, point.y());
            maxX = Math.max(maxX, point.x());
            maxY = Math.max(maxY, point.y());
        }

        void write(ByteBuffer buffer) {
            buffer.putDouble(minX).putDouble(minY).putDouble(maxX).putDouble(maxY);
        }
    }
}
