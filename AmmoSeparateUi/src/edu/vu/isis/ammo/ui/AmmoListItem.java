package edu.vu.isis.ammo.ui;

public class AmmoListItem {
	private String name;
	private String formal;
	private String status_one;
	private String status_two;
	
	public AmmoListItem(String a, String b, String c, String d){
		name = a;
		formal = b;
		status_one = c;
		status_two = d;
	}
	
	public String getName(){
		return name;
	}
	
	public String getFormal(){
		return formal;
	}
	
	public String getStatusOne(){
		return status_one;
	}

	public String getStatusTwo(){
		return status_two;
	}
	
	public boolean update(String status_oneNew, String status_twoNew){
		status_one = status_oneNew;
		status_two = status_twoNew;
		return true;
	}
	
	public boolean update(String nameNew, String formalNew, String status_oneNew, String status_twoNew){
		name = nameNew;
		formal = formalNew;
		status_one = status_oneNew;
		status_two = status_twoNew;
		return true;
	}
}
