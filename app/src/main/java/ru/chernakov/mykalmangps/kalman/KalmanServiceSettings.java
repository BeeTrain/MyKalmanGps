package ru.chernakov.mykalmangps.kalman;

import android.hardware.Sensor;

public class KalmanServiceSettings {

	private double accelerationDeviation;
	private int gpsMinDistance;
	private int gpsMinTime;
	private int geoHashPrecision;
	private int geoHashMinPointCount;
	private int sensorFrequencyHz;
	private boolean filterMockGpsCoordinates;

	private double mVelFactor;
	private double mPosFactor;

	public KalmanServiceSettings(double accelerationDeviation, int gpsMinDistance, int gpsMinTime, int geoHashPrecision,
	                             int geoHashMinPointCount, int sensorFrequencyHz, boolean filterMockGpsCoordinates,
	                             double velFactor, double posFactor) {
		this.accelerationDeviation = accelerationDeviation;
		this.gpsMinDistance = gpsMinDistance;
		this.gpsMinTime = gpsMinTime;
		this.geoHashPrecision = geoHashPrecision;
		this.geoHashMinPointCount = geoHashMinPointCount;
		this.sensorFrequencyHz = sensorFrequencyHz;
		this.filterMockGpsCoordinates = filterMockGpsCoordinates;
		this.mVelFactor = velFactor;
		this.mPosFactor = posFactor;
	}

	private static int[] sensorTypes = {
			Sensor.TYPE_LINEAR_ACCELERATION,
			Sensor.TYPE_ROTATION_VECTOR,
	};

	public static int[] getSensorTypes() {
		return sensorTypes;
	}

	public double getAccelerationDeviation() {
		return accelerationDeviation;
	}

	public void setAccelerationDeviation(double accelerationDeviation) {
		this.accelerationDeviation = accelerationDeviation;
	}

	public int getGpsMinDistance() {
		return gpsMinDistance;
	}

	public void setGpsMinDistance(int gpsMinDistance) {
		this.gpsMinDistance = gpsMinDistance;
	}

	public int getGpsMinTime() {
		return gpsMinTime;
	}

	public void setGpsMinTime(int gpsMinTime) {
		this.gpsMinTime = gpsMinTime;
	}

	public int getGeoHashPrecision() {
		return geoHashPrecision;
	}

	public void setGeoHashPrecision(int geoHashPrecision) {
		this.geoHashPrecision = geoHashPrecision;
	}

	public int getGeoHashMinPointCount() {
		return geoHashMinPointCount;
	}

	public void setGeoHashMinPointCount(int geoHashMinPointCount) {
		this.geoHashMinPointCount = geoHashMinPointCount;
	}

	public int getSensorFrequencyHz() {
		return sensorFrequencyHz;
	}

	public void setSensorFrequencyHz(int sensorFrequencyHz) {
		this.sensorFrequencyHz = sensorFrequencyHz;
	}

	public boolean isFilterMockGpsCoordinates() {
		return filterMockGpsCoordinates;
	}

	public void setFilterMockGpsCoordinates(boolean filterMockGpsCoordinates) {
		this.filterMockGpsCoordinates = filterMockGpsCoordinates;
	}

	public double getmVelFactor() {
		return mVelFactor;
	}

	public void setmVelFactor(double mVelFactor) {
		this.mVelFactor = mVelFactor;
	}

	public double getmPosFactor() {
		return mPosFactor;
	}

	public void setmPosFactor(double mPosFactor) {
		this.mPosFactor = mPosFactor;
	}

	public static void setSensorTypes(int[] sensorTypes) {
		KalmanServiceSettings.sensorTypes = sensorTypes;
	}
}
