package se.plopmap;

import java.util.HashSet;
import java.util.List;

final class PolygonRing {
    record Vertex(double x, double y) {}

    static void validate(List<Vertex> vertices) {
        int count = vertices.size();
        if (count < 3 || new HashSet<>(vertices).size() != count) {
            throw new IllegalArgumentException("Ytan behöver minst tre olika hörn utan dubbletter.");
        }
        for (int i = 0; i < count; i++) {
            Vertex a = vertices.get(i);
            Vertex b = vertices.get((i + 1) % count);
            for (int j = i + 2; j < count; j++) {
                if (i == 0 && j == count - 1) continue;
                Vertex c = vertices.get(j);
                Vertex d = vertices.get((j + 1) % count);
                if (intersect(a, b, c, d)) {
                    throw new IllegalArgumentException("Ytans kanter korsar eller vidrör varandra.");
                }
            }
        }
        if (signedArea(vertices) == 0) {
            throw new IllegalArgumentException("Hörnen behöver omsluta en yta.");
        }
    }

    static double signedArea(List<Vertex> vertices) {
        Vertex origin = vertices.get(0);
        double twiceArea = 0;
        for (int i = 1; i < vertices.size() - 1; i++) {
            twiceArea += cross(origin, vertices.get(i), vertices.get(i + 1));
        }
        return twiceArea / 2;
    }

    private static double cross(Vertex a, Vertex b, Vertex c) {
        return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
    }

    private static boolean intersect(Vertex a, Vertex b, Vertex c, Vertex d) {
        if (!overlap(a.x, b.x, c.x, d.x) || !overlap(a.y, b.y, c.y, d.y)) return false;
        return cross(a, b, c) * cross(a, b, d) <= 0 && cross(c, d, a) * cross(c, d, b) <= 0;
    }

    private static boolean overlap(double a, double b, double c, double d) {
        return Math.max(Math.min(a, b), Math.min(c, d)) <= Math.min(Math.max(a, b), Math.max(c, d));
    }
}
