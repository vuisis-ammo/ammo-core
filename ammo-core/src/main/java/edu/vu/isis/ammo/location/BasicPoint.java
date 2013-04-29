/*Copyright (C) 2010-2012 Institute for Software Integrated Systems (ISIS)
This software was developed by the Institute for Software Integrated
Systems (ISIS) at Vanderbilt University, Tennessee, USA for the 
Transformative Apps program under DARPA, Contract # HR011-10-C-0175.
The United States Government has unlimited rights to this software. 
The US government has the right to use, modify, reproduce, release, 
perform, display, or disclose computer software or computer software 
documentation in whole or in part, in any manner and for any 
purpose whatsoever, and to have or authorize others to do so.
*/
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
