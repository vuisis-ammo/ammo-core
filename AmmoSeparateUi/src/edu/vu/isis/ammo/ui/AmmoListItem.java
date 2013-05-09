package edu.vu.isis.ammo.ui;

public class AmmoListItem {
	private String name;
	private String formal;
	private String status_one;
	private String status_two;
	private String send_stats;
	private String receive_stats;
	private int colorOne;
	private int colorTwo;
	
	public AmmoListItem(String a, String b, String c, String d, String e, String f){
		name = a;
		formal = b;
		status_one = c;
		status_two = d;
		send_stats = e;
		receive_stats = f;
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
	
	public String getSendStats(){
		return send_stats;
	}
	
	public String getReceiveStats(){
		return receive_stats;
	}
	
	public boolean update(String status_oneNew, String status_twoNew, String send_new, String receive_new){
		status_one = status_oneNew;
		status_two = status_twoNew;
		send_stats = send_new;
		receive_stats = receive_new;
		return true;
	}
	
	public boolean update(String nameNew, String formalNew, String status_oneNew, String status_twoNew, String send_new, String receive_new){
		name = nameNew;
		formal = formalNew;
		status_one = status_oneNew;
		status_two = status_twoNew;
		send_stats = send_new;
		receive_stats = receive_new;
		return true;
	}

	public int getColorOne() {
		return colorOne;
	}

	public void setColorOne(int colorOne) {
		this.colorOne = colorOne;
	}

	public int getColorTwo() {
		return colorTwo;
	}

	public void setColorTwo(int colorTwo) {
		this.colorTwo = colorTwo;
	}
}
