package se.plopmap;

import android.location.Location;
import android.os.Bundle;
import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

final class NmeaReceiver implements AutoCloseable {
    static final String PROVIDER = "NMEA TCP";
    private final Socket socket = new Socket();

    NmeaReceiver(String host, int port, Consumer<Location> location, Consumer<String> error) {
        Thread worker = new Thread(() -> {
            try {
                socket.connect(new InetSocketAddress(host, port), 5000);
                socket.setSoTimeout(10000);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(),
                        StandardCharsets.US_ASCII))) {
                    String line;
                    double plan = Double.NaN, vertical = Double.NaN;
                    String gstTime = "", lastGga = "";
                    Location lastFix = null;
                    while ((line = reader.readLine()) != null) {
                        String[] fields = sentence(line);
                        if (fields.length == 0) continue;
                        if (fields[0].endsWith("GST") && fields.length >= 9) {
                            gstTime = fields[1];
                            plan = Math.hypot(number(fields[6]), number(fields[7]));
                            vertical = number(fields[8]);
                            if (lastFix != null && gstTime.equals(lastGga)) {
                                Location updated = new Location(lastFix);
                                applyUncertainty(updated, plan, vertical);
                                location.accept(updated);
                            }
                            continue;
                        }
                        Fix fix = parse(fields);
                        if (fix == null) {
                            if (fields[0].endsWith("GGA")) {
                                lastFix = null;
                                location.accept(null);
                            }
                            continue;
                        }
                        Location point = new Location(PROVIDER);
                        point.setLatitude(fix.latitude());
                        point.setLongitude(fix.longitude());
                        if (Double.isFinite(fix.ellipsoidHeight())) point.setAltitude(fix.ellipsoidHeight());
                        if (gstTime.equals(fields[1])) {
                            applyUncertainty(point, plan, vertical);
                        }
                        point.setTime(System.currentTimeMillis());
                        point.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
                        Bundle metadata = new Bundle();
                        metadata.putInt("quality", fix.quality());
                        if (Double.isFinite(fix.mslHeight())) metadata.putDouble("mslHeight", fix.mslHeight());
                        point.setExtras(metadata);
                        lastFix = point;
                        lastGga = fields[1];
                        location.accept(point);
                    }
                    if (!socket.isClosed()) error.accept("NMEA-strömmen stängdes.");
                }
            } catch (java.io.IOException failure) {
                if (!socket.isClosed()) error.accept("Kunde inte läsa NMEA. Kontrollera adress, port och mottagare.");
            } finally {
                close();
            }
        }, "plopmap-nmea");
        worker.start();
    }

    private static void applyUncertainty(Location point, double plan, double vertical) {
        if (Double.isFinite(plan) && plan > 0 && plan <= Float.MAX_VALUE) point.setAccuracy((float) plan);
        if (Double.isFinite(vertical) && vertical > 0 && vertical <= Float.MAX_VALUE) {
            point.setVerticalAccuracyMeters((float) vertical);
        }
    }

    static String[] sentence(String line) {
        int end = line.indexOf('*');
        if (!line.startsWith("$") || end < 1 || line.length() != end + 3 || end > 128) return new String[0];
        int checksum = 0;
        for (int i = 1; i < end; i++) checksum ^= line.charAt(i);
        try {
            if (checksum == Integer.parseInt(line.substring(end + 1), 16)) return line.substring(1, end).split(",", -1);
        } catch (NumberFormatException ignored) {}
        return new String[0];
    }

    record Fix(double latitude, double longitude, int quality, double ellipsoidHeight, double mslHeight) {}

    static Fix parse(String[] fields) {
        if (fields.length < 13 || !fields[0].endsWith("GGA")) return null;
        double quality = number(fields[6]);
        if (!(quality >= 1 && quality <= 5 && quality == (int) quality)) return null;
        double latitude = degrees(fields[2], fields[3], "N", "S", 90);
        double longitude = degrees(fields[4], fields[5], "E", "W", 180);
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)) return null;
        double msl = fields[10].equals("M") ? number(fields[9]) : Double.NaN;
        double separation = fields[12].equals("M") ? number(fields[11]) : Double.NaN;
        return new Fix(latitude, longitude, (int) quality, msl + separation, msl);
    }

    private static double number(String value) {
        try { return Double.parseDouble(value); }
        catch (NumberFormatException error) { return Double.NaN; }
    }

    private static double degrees(String value, String direction, String positive, String negative, int limit) {
        double nmea = number(value);
        if (!(nmea >= 0) || !(positive.equals(direction) || negative.equals(direction))) return Double.NaN;
        double whole = Math.floor(nmea / 100);
        double minutes = nmea - whole * 100;
        double degrees = whole + minutes / 60;
        return minutes < 60 && degrees <= limit ? (negative.equals(direction) ? -degrees : degrees) : Double.NaN;
    }

    @Override
    public void close() {
        try { socket.close(); }
        catch (java.io.IOException ignored) {}
    }
}
