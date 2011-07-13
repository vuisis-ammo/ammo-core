package edu.vu.isis.ammo.location;

/**
 * Wrapper for an x,y pair.
 * @author demetri
 *
 */
public class BasicPoint {
    private double x;
    private double y;


    public BasicPoint(double xVal, double yVal) {
        x = xVal;
        y = yVal;
    }

    // *****Generated getters and setters
    public void setX(double x) {
        this.x = x;
    }
    public double getX() {
        return x;
    }

    public void setY(double y) {
        this.y = y;
    }
    public double getY() {
        return y;
    }
    // *****End generated getters and setters


}
