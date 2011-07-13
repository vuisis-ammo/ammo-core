package edu.vu.isis.ammo.location;

/**
 * @author Demetri Miller
 *
 */
public class Geoid {

    // ****Begin Data Members****
    // Constants
    public static final double EQUATORIAL_RADIUS_KM = 6378.135;
    public static final double EQUATORIAL_RADIUS_MI = 3958.76;

    public static final double RADIANS = 1.0;
    public static final double DEGREES = 180.0 / Math.PI;

    private static final double NORTH = Math.PI * 0.5;

    // Vars
    private BasicPoint _ctr = null;
    private double _rad = 0.0;

    // *****End Data Members*****

    // Internally Geoid always uses radians, but all the interfaces use
    // degrees by default.
    public Geoid(double lon, double lat, double rad, double unit) {
        // Default values if params
        if (unit < 0) {
            unit = DEGREES;
        }
        if (rad < 0) {
            rad = EQUATORIAL_RADIUS_KM;
        }

        _rad = rad;
        _ctr = new BasicPoint(lon / unit, lat / unit);
    }

    public static BasicPoint convert(BasicPoint geo, double unit) {
        return new BasicPoint(geo.getX() * unit, geo.getY() * unit);
    }

    /**
     * The Vincenty formula : http://www.ngs.noaa.gov/PUBS_LIB/inverse.pdf see
     * http://en.wikipedia.org/wiki/Great_circle_distance Is used to compute the
     * shortest (great circle) distance between two points. The input parameters
     * and output are in the units specified.
     *
     * @param ps
     *            (phi_s) latitude of start point
     * @param pf
     *            (phi_f) latitude of finish point
     * @param ls
     *            (lambda_s) longitude of start point
     * @param ls
     *            (lambda_f) longitude of finish point
     * @param unit
     *            the units of angular measure (radians by default)
     * @param radius
     *            the radius of the spheroid (equatorial radius by default)
     * @return the distance in the units of 'unit'
     */
    public static double vincenty(double ls, double ps, double lf, double pf,
                                  double unit, double radius) {
        if (unit < 0) {
            unit = DEGREES;
        }
        if (radius < 0) {
            radius = EQUATORIAL_RADIUS_KM;
        }

        ps *= unit;
        pf *= unit;
        ls *= unit;
        lf *= unit;

        double dL = lf - ls;
        double sin_dL = Math.sin(dL);
        double cos_dL = Math.cos(dL);

        double cos_Pf = Math.cos(pf);
        double sin_Pf = Math.sin(pf);

        double cos_Ps = Math.cos(ps);
        double sin_Ps = Math.sin(ps);

        double addend = cos_Ps * sin_Pf - sin_Ps * cos_Pf * cos_dL;
        double num = cos_Pf * sin_dL * cos_Pf * sin_dL + addend * addend;

        double den = sin_Ps * sin_Pf + cos_Ps * cos_Pf * cos_dL;

        return radius * Math.atan2(Math.sqrt(num), den) / unit;
    }

    /**
     * distance along the great circle to the next point
     */
    public double distance(double lon, double lat, double unit) {
        if (unit < 0) {
            unit = DEGREES;
        }
        return vincenty(_ctr.getX(), _ctr.getY(), lon, lat, unit, _rad);
    }

    /**
     * http://en.wikipedia.org/wiki/Orthographic_projection_(cartography)
     * http://www.progonos.com/furuti/MapProj/Dither/ProjAz/projAz.html Long/Lat
     * are expressed in radians. Distances are relative to the radius.
     *
     * Given a plane tangent at the objects center project onto that plane from
     * the geoid.
     *
     * @param lon
     *            longitude
     * @param lat
     *            latitude
     * @return orthographic projection onto the tangent plane.
     */
    public BasicPoint azimuth_ortho_fwd(double lon, double lat, double unit) {
        lon /= unit;
        lat /= unit;

        double dL = lon - _ctr.getX();
        double cos_dL = Math.cos(dL);
        double sin_dL = Math.sin(dL);

        double cos_lat = Math.cos(lat);
        double cos_lat_0 = Math.cos(_ctr.getY());
        double sin_lat = Math.sin(lat);
        double sin_lat_0 = Math.sin(_ctr.getY());

        return new BasicPoint(_rad * cos_lat * sin_dL, _rad
                              * (cos_lat_0 * sin_lat - sin_lat_0 * cos_lat_0 * cos_dL));
    }

    /**
     * @return never negative rather than returning [-180:0] or [0:+180],
     *         [180:360] or [0:+180]
     */
    public static double azimuth(double current, double last) {
        double azimuth = current - last;
        if (azimuth < 0.0)
            return 360.0 + (azimuth % 360);
        if (azimuth > 360)
            return azimuth % 360.0;
        return azimuth;
    }

    /**
     * Heading in degrees from true north positive clockwise
     */
    public double heading(double lon, double lat, double unit) {
        if (unit < 0) {
            unit = DEGREES;
        }
        BasicPoint excursion = azimuth_ortho_fwd(lon, lat, unit);
        return (NORTH - Math.atan2(excursion.getY(), excursion.getX())) * unit;
    }

    /**
    * @param radx the x coordinate on the tangent plane
    * @param rady the y coordinate on the tangent plane
    */
    public BasicPoint azimuth_ortho_inv(double x, double y, double unit) {
        double rho = Math.sqrt( x*x + y*y);
        double cee = Math.asin(rho/_rad);

        double cos_cee = Math.cos(cee);
        double sin_cee = Math.sin(cee);
        double cos_lat_0 = Math.cos(_ctr.getY());
        double sin_lat_0 = Math.sin(_ctr.getY());

        double lon = _ctr.getX()
                     + Math.atan2( x*sin_cee, rho*cos_lat_0*cos_cee - y*sin_lat_0*sin_cee );
        double lat = Math.asin( cos_cee*sin_lat_0 + y*sin_cee*cos_lat_0/rho );
        return new BasicPoint(unit*lon, unit*lat);
    }
}
