/* Copyright (c) 2010-2015 Vanderbilt University
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package edu.vu.isis.ammo.location;

/**
 * The Geoid provides methods for converting between different geoid (earth)
 * point location/orientation systems. Most are coordinate representations like
 * (longitude,latitude).
 * <p>
 * Methods are also provided for computing the distances between points.
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

	/**
	 * The position and orientation on the geoid.
	 * Internally Geoid always uses radians, but all the interfaces use degrees by default.
	 */
	private BasicPoint _ctr = null;
	private double _rad = 0.0;

	// *****End Data Members*****

	/**
	 * Specify a point on the geoid in longitude, latitude, heading in the angular unit specified.
	 * 
	 * @param lon
	 * @param lat
	 * @param rad
	 * @param unit
	 */
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

	/**
	 * A factory method for converting a point location from one angular unit to another.
	 * 
	 * @param geo the point to be converted
	 * @param unit the target units
	 * @return the new point
	 */
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
	 * @param lf
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

		final double dL = lf - ls;
		final double sin_dL = Math.sin(dL);
		final double cos_dL = Math.cos(dL);

		final double cos_Pf = Math.cos(pf);
		final double sin_Pf = Math.sin(pf);

		final double cos_Ps = Math.cos(ps);
		final double sin_Ps = Math.sin(ps);

		final double addend = cos_Ps * sin_Pf - sin_Ps * cos_Pf * cos_dL;
		final double num = cos_Pf * sin_dL * cos_Pf * sin_dL + addend * addend;

		final double den = sin_Ps * sin_Pf + cos_Ps * cos_Pf * cos_dL;

		return radius * Math.atan2(Math.sqrt(num), den) / unit;
	}

	/**
	 * Distance along the great circle from the current location to the
	 * specified location. The result is specified in the provided angular units.
	 * 
	 * @param lon
	 * @param lat
	 * @param unit
	 * @return
	 */
	public double distance(double lon, double lat, double unit) {
		if (unit < 0) {
			unit = DEGREES;
		}
		return vincenty(_ctr.getX(), _ctr.getY(), lon, lat, unit, _rad);
	}

	/**
	 * http://en.wikipedia.org/wiki/Orthographic_projection_(cartography)
	 * http://www.progonos.com/furuti/MapProj/Dither/ProjAz/projAz.html
	 * <p>
	 * Long/Lat are expressed in radians. Distances are relative to the radius.
	 * <p>
	 * Given a plane tangent at the objects center project onto that plane from
	 * the spheroid.
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

		final double dL = lon - _ctr.getX();
		final double cos_dL = Math.cos(dL);
		final double sin_dL = Math.sin(dL);

		final double cos_lat = Math.cos(lat);
		final double cos_lat_0 = Math.cos(_ctr.getY());
		final double sin_lat = Math.sin(lat);
		final double sin_lat_0 = Math.sin(_ctr.getY());

		return new BasicPoint(_rad * cos_lat * sin_dL, _rad
				* (cos_lat_0 * sin_lat - sin_lat_0 * cos_lat_0 * cos_dL));
	}

	/**
	 * Normalize the angular measure to be always within the bounds [0 360).
	 * 
	 * @return never negative rather than returning [-180:0] or [0:+180],
	 *         [180:360] or [0:+180]
	 */
	public static double azimuth(double current, double last) {
		final double azimuth = current - last;
		if (azimuth < 0.0)
			return 360.0 + (azimuth % 360);
		if (azimuth > 360)
			return azimuth % 360.0;
		return azimuth;
	}

	/**
	 * Heading in degrees from true north. The result has clockwise as the
	 * positive direction.
	 * 
	 * @param lon
	 * @param lat
	 * @param unit
	 * @return
	 */
	public double heading(double lon, double lat, double unit) {
		if (unit < 0) {
			unit = DEGREES;
		}
		final BasicPoint excursion = azimuth_ortho_fwd(lon, lat, unit);
		return (NORTH - Math.atan2(excursion.getY(), excursion.getX())) * unit;
	}

	/**
	 * This method is the inverse to azimuth_ortho_fwd.
	 * 
	 * @param x
	 *            the x coordinate on the tangent plane
	 * @param y
	 *            the y coordinate on the tangent plane
	 */
	public BasicPoint azimuth_ortho_inv(double x, double y, double unit) {
		final double rho = Math.sqrt(x * x + y * y);
		final double cee = Math.asin(rho / _rad);

		final double cos_cee = Math.cos(cee);
		final double sin_cee = Math.sin(cee);
		final double cos_lat_0 = Math.cos(_ctr.getY());
		final double sin_lat_0 = Math.sin(_ctr.getY());

		final double lon = _ctr.getX()
				+ Math.atan2(x * sin_cee, rho * cos_lat_0 * cos_cee - y
						* sin_lat_0 * sin_cee);
		final double lat = Math.asin(cos_cee * sin_lat_0 + y * sin_cee * cos_lat_0
				/ rho);
		return new BasicPoint(unit * lon, unit * lat);
	}
}
