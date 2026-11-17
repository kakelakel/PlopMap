package se.plopmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class GrassOutline {
    record Pixel(int x, int y) {}

    // Returns a convex approximation of the connected patch matching the clicked seed.
    static List<Pixel> suggest(int[] rgb, int width, int height, int x, int y) {
        int seed = rgb[y * width + x];
        if (!green(seed)) return java.util.Collections.emptyList();
        int[] queue = new int[rgb.length];
        boolean[] seen = new boolean[rgb.length];
        int start = y * width + x;
        seen[start] = true;
        queue[0] = start;
        int count = 1;
        int[] left = new int[height], right = new int[height];
        java.util.Arrays.fill(left, width);
        java.util.Arrays.fill(right, -1);
        for (int i = 0; i < count; i++) {
            int at = queue[i], px = at % width, py = at / width;
            left[py] = Math.min(left[py], px);
            right[py] = Math.max(right[py], px);
            if (px > 0) count = visit(at - 1, seed, rgb, seen, queue, count);
            if (px + 1 < width) count = visit(at + 1, seed, rgb, seen, queue, count);
            if (py > 0) count = visit(at - width, seed, rgb, seen, queue, count);
            if (py + 1 < height) count = visit(at + width, seed, rgb, seen, queue, count);
        }
        if (count < 16) return java.util.Collections.emptyList();
        List<Pixel> ends = new ArrayList<>();
        for (int row = 0; row < height; row++) if (right[row] >= 0) {
            ends.add(new Pixel(left[row], row));
            if (left[row] != right[row]) ends.add(new Pixel(right[row], row));
        }
        ends.sort(Comparator.comparingInt(Pixel::x).thenComparingInt(Pixel::y));
        List<Pixel> hull = new ArrayList<>();
        for (Pixel pixel : ends) append(hull, pixel, 0);
        int split = hull.size() - 1;
        for (int i = ends.size() - 2; i >= 0; i--) append(hull, ends.get(i), split);
        hull.remove(hull.size() - 1);
        return hull.size() >= 3 ? hull : java.util.Collections.emptyList();
    }

    private static void append(List<Pixel> hull, Pixel pixel, int floor) {
        while (hull.size() >= floor + 2) {
            Pixel a = hull.get(hull.size() - 2), b = hull.get(hull.size() - 1);
            int cross = (b.x - a.x) * (pixel.y - a.y) - (b.y - a.y) * (pixel.x - a.x);
            if (cross > 0) break;
            hull.remove(hull.size() - 1);
        }
        hull.add(pixel);
    }

    private static int visit(int at, int seed, int[] rgb, boolean[] seen, int[] queue, int count) {
        if (seen[at]) return count;
        seen[at] = true;
        int candidate = rgb[at];
        if (green(candidate) && Math.abs(channel(candidate, 16) - channel(seed, 16))
                + Math.abs(channel(candidate, 8) - channel(seed, 8))
                + Math.abs(channel(candidate, 0) - channel(seed, 0)) <= 105) queue[count++] = at;
        return count;
    }

    private static boolean green(int color) {
        return (color >>> 24) > 127 && channel(color, 8) >= channel(color, 16) + 4
                && channel(color, 8) >= channel(color, 0) + 8;
    }

    private static int channel(int color, int shift) {
        return (color >>> shift) & 255;
    }
}
