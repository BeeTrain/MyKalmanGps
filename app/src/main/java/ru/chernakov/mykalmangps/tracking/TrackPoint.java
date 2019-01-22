package ru.chernakov.mykalmangps.tracking;

import android.location.Location;

public class TrackPoint extends Location {
	//
	// Projection
	//
	public static final String CONTENT_URI = "gps_track";
	public static final String LATITUDE = "latitude";
	public static final String LONGITUDE = "longitude";
	public static final String ACCURACY = "accuracy";
	public static final String ZONE_ID = "zone_id";
	public static final String SAT_OVERALL = "satellites_cnt_overall";
	public static final String SAT_ACTIVE = "satellites_cnt_active";
	public static final String TIME = "time";
	public static final String PROVIDER = "provider";

	public TrackPoint(Location l) {
		super(l);
	}
}
