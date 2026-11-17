package se.plopmap;

enum ExportCrs {
    WGS84, SWEREF99_TM;

    private static final String WGS84_WKT = "GEOGCS[\"GCS_WGS_1984\",DATUM[\"D_WGS_1984\","
            + "SPHEROID[\"WGS_1984\",6378137,298.257223563]],PRIMEM[\"Greenwich\",0],"
            + "UNIT[\"Degree\",0.0174532925199433]]";
    private static final String SWEREF_WKT = "PROJCS[\"SWEREF99_TM\",GEOGCS[\"GCS_SWEREF99\","
            + "DATUM[\"D_SWEREF99\",SPHEROID[\"GRS_1980\",6378137,298.257222101]],"
            + "PRIMEM[\"Greenwich\",0],UNIT[\"Degree\",0.0174532925199433]],"
            + "PROJECTION[\"Transverse_Mercator\"],PARAMETER[\"False_Easting\",500000],"
            + "PARAMETER[\"False_Northing\",0],PARAMETER[\"Central_Meridian\",15],"
            + "PARAMETER[\"Scale_Factor\",0.9996],PARAMETER[\"Latitude_Of_Origin\",0],UNIT[\"Meter\",1]]";

    String wkt() {
        return this == WGS84 ? WGS84_WKT : SWEREF_WKT;
    }

    PolygonRing.Vertex project(double longitude, double latitude) {
        if (this == WGS84) return new PolygonRing.Vertex(longitude, latitude);
        // Area of use bounding box for EPSG:3006. Export of GPS data uses a
        // zero-shift WGS 84 approximation, with no realization or epoch metadata.
        if (!(longitude >= 10.03 && longitude <= 24.17 && latitude >= 54.96 && latitude <= 69.07)) {
            throw new IllegalArgumentException("Projektet innehåller koordinater utanför SWEREF 99 TM:s område.");
        }
        double flattening = 1 / 298.257222101;
        double e2 = flattening * (2 - flattening);
        double n = flattening / (2 - flattening);
        double n2 = n * n, n3 = n2 * n, n4 = n2 * n2;
        double radius = 6378137 / (1 + n) * (1 + n2 / 4 + n4 / 64);
        double phi = Math.toRadians(latitude);
        double lambda = Math.toRadians(longitude - 15);
        double sin = Math.sin(phi);
        double e = Math.sqrt(e2);
        double conformal = Math.atan(Math.sinh(atanh(sin) - e * atanh(e * sin)));
        double xi = Math.atan2(Math.tan(conformal), Math.cos(lambda));
        double eta = atanh(Math.cos(conformal) * Math.sin(lambda));
        double north = xi, east = eta;
        double[] coefficients = {
                n / 2 - 2 * n2 / 3 + 5 * n3 / 16 + 41 * n4 / 180,
                13 * n2 / 48 - 3 * n3 / 5 + 557 * n4 / 1440,
                61 * n3 / 240 - 103 * n4 / 140,
                49561 * n4 / 161280
        };
        for (int i = 0; i < coefficients.length; i++) {
            int factor = 2 * (i + 1);
            north += coefficients[i] * Math.sin(factor * xi) * Math.cosh(factor * eta);
            east += coefficients[i] * Math.cos(factor * xi) * Math.sinh(factor * eta);
        }
        return new PolygonRing.Vertex(500_000 + 0.9996 * radius * east, 0.9996 * radius * north);
    }

    private static double atanh(double value) {
        return 0.5 * (Math.log1p(value) - Math.log1p(-value));
    }
}
