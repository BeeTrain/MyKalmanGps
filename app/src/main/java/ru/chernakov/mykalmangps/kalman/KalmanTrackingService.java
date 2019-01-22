package ru.chernakov.mykalmangps.kalman;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GnssStatus;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;
import android.widget.Toast;

import com.squareup.otto.Bus;

import java.util.Queue;
import java.util.concurrent.PriorityBlockingQueue;

import mad.location.manager.lib.Commons.Coordinates;
import mad.location.manager.lib.Commons.GeoPoint;
import mad.location.manager.lib.Commons.SensorGpsDataItem;
import mad.location.manager.lib.Commons.Utils;
import mad.location.manager.lib.Filters.GPSAccKalmanFilter;
import mad.location.manager.lib.Services.ServicesHelper;
import ru.chernakov.mykalmangps.App;
import ru.chernakov.mykalmangps.R;
import ru.chernakov.mykalmangps.tracking.TrackPoint;
import ru.chernakov.mykalmangps.utils.EventBus;
import ru.chernakov.mykalmangps.utils.NotificationHelper;

@SuppressWarnings("ResourceType")
public class KalmanTrackingService extends Service
		implements mad.location.manager.lib.Interfaces.LocationServiceInterface, SensorEventListener, LocationListener {
	public static final String TAG = KalmanTrackingService.class.getSimpleName();

	//
	// Состояния условий приёма GNSS
	//

	/**
	 * Состояние, характеризующее нормальные условия приёма
	 */
	private static final int GNSS_CONDITION_FINE = 1;

	/**
	 * Состояние, характеризующее условия приёма низкого качества
	 */
	private static final int GNSS_CONDITION_LOW = 2;

	/**
	 * Состояние, характеризующее плохие условия приёма
	 */
	private static final int GNSS_CONDITION_INVALID = 3;

	/**
	 * [GNSS] Интервал между попытками определения координат, мс
	 */
	public static final int GNSS_MIN_TIME = 2000;

	/**
	 * Количество спутников, при котором определённое местоположение считается определённым при хороших условиях
	 */
	public static final int PREFERRED_SATELLITES_COUNT = 7;

	/**
	 * Минимальное количество спутников, требуемое для того, чтобы определённое местоположение считалось определённым
	 * в пределах допустимых условий
	 */
	public static final int MIN_SATELLITES_COUNT = 5;

	/**
	 * Провайдер определения местоположения через спутниковые системы
	 */
	private LocationManager mLocatorGnss;

	/**
	 * Обработчик определения коодинат
	 */
	private TrackingHandler mServiceHandler;

	/**
	 * Фильтр точек
	 */
	private GPSAccKalmanFilter mKalmanFilter;

	/**
	 * Количество активных спутников
	 */
	private int mActiveSatellitesCount;

	/**
	 * Состояние условий приёма сигнала от спутников
	 */
	private int mGnssConditionsState;

	/**
	 * Шина обмена сообщениями
	 */
	private static Bus sBus;

	/**
	 * Текущий статус
	 */
	public static boolean sIsActive;

	// KalmanFilter
	private double mMagneticDeclination = 0.0;

	/**
	 * Настройки сервиса
	 */
	private KalmanServiceSettings mKalmanServiceSettings;

	/**
	 * Очередь для обработки данных сенсоров
	 */
	private Queue<SensorGpsDataItem> mSensorDataQueue = new PriorityBlockingQueue<>();

	// Матрицы обработки точек в пространстве
	private float[] mRotationMatrix = new float[16];
	private float[] mRotationMatrixInv = new float[16];
	private float[] mAbsAcceleration = new float[4];
	private float[] mLinearAcceleration = new float[4];

	public KalmanTrackingService() {
		ServicesHelper.addLocationServiceInterface(this);
	}

	public class LocalBinder extends Binder {
		public KalmanTrackingService getService() {
			return KalmanTrackingService.this;
		}
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return new LocalBinder();
	}

	@Override
	public void onCreate() {
		HandlerThread thread = new HandlerThread("ServiceStartArguments", Process.THREAD_PRIORITY_BACKGROUND);
		thread.start();

		sBus = EventBus.getInstance();
		sBus.register(this);

		mKalmanServiceSettings = new KalmanServiceSettings(Utils.ACCELEROMETER_DEFAULT_DEVIATION,
				Utils.GPS_MIN_DISTANCE, Utils.GPS_MIN_TIME,
				Utils.GEOHASH_DEFAULT_PREC, Utils.GEOHASH_DEFAULT_MIN_POINT_COUNT,
				Utils.SENSOR_DEFAULT_FREQ_HZ,
				true, Utils.DEFAULT_VEL_FACTOR, Utils.DEFAULT_POS_FACTOR);

		mServiceHandler = new TrackingHandler(thread.getLooper());

		if (mLocatorGnss == null) {
			mLocatorGnss = (LocationManager) App.getContext().getSystemService(Context.LOCATION_SERVICE);
		}
		App.setKalmanTrackingService(this);
		sIsActive = true;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// Создание оповещения о запуске службы
		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
		NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "kalman");
		NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle();
		bigTextStyle.setBigContentTitle(App.getContext().getString(R.string.app_name));
		bigTextStyle.bigText("Сервис записи трека запущен");
		builder.setStyle(bigTextStyle);
		builder.setWhen(System.currentTimeMillis());
		Bitmap largeIconBitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
		builder.setLargeIcon(largeIconBitmap);
		builder.setPriority(Notification.PRIORITY_MAX);
		builder.setFullScreenIntent(pendingIntent, false);

		Notification notification = builder.build();
		// Запуск службы в Foreground
		startForeground(1, notification);

		startTracking(new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER});

		// Флаг, означающий, что в случае, если сервис будет уничтожен, он не будет перезапущен
		// до тех пор, пока не будет вызван метод Context.startService()
		return START_NOT_STICKY;
	}

	/**
	 * Подписывается на получение координат.
	 *
	 * @param providers список провайдеров, на которые необходимо подписаться
	 */
	public void startTracking(String[] providers) {
		for (String provider : providers) {
			if (provider.equals(LocationManager.GPS_PROVIDER)) {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
					mLocatorGnss.registerGnssStatusCallback(
							new GnssStatus.Callback() {
								@Override
								public void onStarted() {
									super.onStarted();
								}

								@Override
								public void onStopped() {
									super.onStopped();
								}

								@Override
								public void onFirstFix(int ttffMillis) {
									super.onFirstFix(ttffMillis);
								}

								@TargetApi(Build.VERSION_CODES.N)
								@Override
								public void onSatelliteStatusChanged(GnssStatus status) {
									super.onSatelliteStatusChanged(status);
									int satellitesCount = status.getSatelliteCount();

									if (satellitesCount > 0) {
										mActiveSatellitesCount = 0;

										for (int i = 0; i < satellitesCount; i++) {
											if (status.usedInFix(i)) {
												mActiveSatellitesCount++;
											}
										}

										// Проверка и сохранение условий определения
										// местоположения с помощью GNSS
										checkGnssConditions();
									}
								}
							}
					);
				} else {
					//noinspection deprecation
					mLocatorGnss.addGpsStatusListener(new GpsStatus.Listener() {
						@SuppressWarnings("deprecation")
						@Override
						public void onGpsStatusChanged(int event) {
							if (event == GpsStatus.GPS_EVENT_SATELLITE_STATUS) {
								GpsStatus status = mLocatorGnss.getGpsStatus(null);
								Iterable<GpsSatellite> satellites = status.getSatellites();
								mActiveSatellitesCount = 0;

								if (satellites != null) {
									for (GpsSatellite satellite : satellites) {
										if (satellite.usedInFix()) {
											mActiveSatellitesCount++;
										}
									}
								}

								// Проверка и сохранение условий определения
								// местоположения с помощью GNSS
								checkGnssConditions();
							}
						}
					});
				}
				mLocatorGnss.requestLocationUpdates(LocationManager.GPS_PROVIDER, GNSS_MIN_TIME, 0, this);
			}
		}
	}

	/**
	 * Проверяет условия приёма GNSS. На основании результатов проверки
	 * выводит оповещение пользователю.
	 */
	private void checkGnssConditions() {
		// Проверка и сохранение условий определения местоположения с помощью GNSS
		String msg;
		boolean conditionsChanged = false;
		int drawableRes;
		if (mActiveSatellitesCount >= PREFERRED_SATELLITES_COUNT) {
			drawableRes = R.drawable.ic_stat_gps_conditions_nice;
			msg = App.getContext().getString(R.string.gps_quality_fine);
			if (mGnssConditionsState != GNSS_CONDITION_FINE) {
				conditionsChanged = true;
			}

			mGnssConditionsState = GNSS_CONDITION_FINE;
		} else if (mActiveSatellitesCount >= MIN_SATELLITES_COUNT) {
			drawableRes = R.drawable.ic_stat_gps_conditions_bad;
			msg = App.getContext().getString(R.string.gps_quality_low);
			if (mGnssConditionsState != GNSS_CONDITION_LOW) {
				conditionsChanged = true;
			}

			mGnssConditionsState = GNSS_CONDITION_LOW;
		} else {
			drawableRes = R.drawable.ic_stat_gps_conditions_invalid;
			msg = App.getContext().getString(R.string.gps_quality_invalid);
			if (mGnssConditionsState != GNSS_CONDITION_INVALID) {
				conditionsChanged = true;
			}

			mGnssConditionsState = GNSS_CONDITION_INVALID;
		}

		// Выводим оповещение об изменении условий приёма и сохраняем данные в лог для отправки на сервер
		if (conditionsChanged) {
			NotificationHelper
					.getInstance(App.getContext())
					.showNotification(
							App.TRACKING_NOTIFY_ID,
							new Intent(),
							App.getContext().getString(R.string.app_name),
							msg, drawableRes, false, false
					);
		}
	}

	/**
	 * Отписывается от получения координат.
	 *
	 * @param providers список провайдеров, от которых необходимо отписаться
	 */
	public void stopTracking(@NonNull String[] providers) {
		sIsActive = false;

		for (String provider : providers) {
			if (provider.equals(LocationManager.GPS_PROVIDER)) {
				mLocatorGnss.removeUpdates(this);
			}
		}
	}

	@Override
	public void onLocationChanged(Location location) {
		if (!TextUtils.isEmpty(location.getProvider())) {
			if (location.getProvider().equals(LocationManager.GPS_PROVIDER)) {
				double x, y, xVel, yVel, posDev, course, speed;
				long timeStamp;
				speed = location.getSpeed();
				course = location.getBearing();
				x = location.getLongitude();
				y = location.getLatitude();
				xVel = speed * Math.cos(course);
				yVel = speed * Math.sin(course);
				posDev = location.getAccuracy();
				timeStamp = Utils.nano2milli(location.getElapsedRealtimeNanos());
				double velErr = location.getAccuracy() * 0.1;

				GeomagneticField geomagneticField = new GeomagneticField(
						(float) location.getLatitude(),
						(float) location.getLongitude(),
						(float) location.getAltitude(),
						timeStamp);
				mMagneticDeclination = geomagneticField.getDeclination();

				if (mKalmanFilter == null) {
					mKalmanFilter = new GPSAccKalmanFilter(
							false,
							Coordinates.longitudeToMeters(x),
							Coordinates.latitudeToMeters(y),
							xVel,
							yVel,
							mKalmanServiceSettings.getAccelerationDeviation(),
							posDev,
							timeStamp,
							mKalmanServiceSettings.getmVelFactor(),
							mKalmanServiceSettings.getmPosFactor());
				}

				SensorGpsDataItem sdi = new SensorGpsDataItem(
						timeStamp, location.getLatitude(), location.getLongitude(), location.getAltitude(),
						SensorGpsDataItem.NOT_INITIALIZED,
						SensorGpsDataItem.NOT_INITIALIZED,
						SensorGpsDataItem.NOT_INITIALIZED,
						location.getSpeed(),
						location.getBearing(),
						location.getAccuracy(),
						velErr,
						mMagneticDeclination);
				mSensorDataQueue.add(sdi);


				TrackPoint point = new TrackPoint(location);
				handleLocation(point);
			}
		}
	}

	@Override
	public void onProviderEnabled(String s) {
		startTracking(new String[]{s});
	}

	@Override
	public void locationChanged(Location location) {
		onLocationChanged(location);
	}

	@Override
	public void onSensorChanged(SensorEvent sensorEvent) {
		final int east = 0;
		final int north = 1;
		final int up = 2;

		long now = android.os.SystemClock.elapsedRealtimeNanos();
		long nowMs = Utils.nano2milli(now);
		switch (sensorEvent.sensor.getType()) {
			case Sensor.TYPE_LINEAR_ACCELERATION:
				System.arraycopy(sensorEvent.values, 0, mLinearAcceleration, 0, sensorEvent.values.length);
				android.opengl.Matrix.multiplyMV(mAbsAcceleration, 0, mRotationMatrixInv,
						0, mLinearAcceleration, 0);
				if (mKalmanFilter == null) {
					break;
				}

				SensorGpsDataItem sdi = new SensorGpsDataItem(nowMs,
						SensorGpsDataItem.NOT_INITIALIZED,
						SensorGpsDataItem.NOT_INITIALIZED,
						SensorGpsDataItem.NOT_INITIALIZED,
						mAbsAcceleration[north],
						mAbsAcceleration[east],
						mAbsAcceleration[up],
						SensorGpsDataItem.NOT_INITIALIZED,
						SensorGpsDataItem.NOT_INITIALIZED,
						SensorGpsDataItem.NOT_INITIALIZED,
						SensorGpsDataItem.NOT_INITIALIZED,
						mMagneticDeclination);
				mSensorDataQueue.add(sdi);

				break;
			case Sensor.TYPE_ROTATION_VECTOR:
				SensorManager.getRotationMatrixFromVector(mRotationMatrix, sensorEvent.values);
				android.opengl.Matrix.invertM(mRotationMatrixInv, 0, mRotationMatrix, 0);

				break;
		}

	}

	@Override
	public void onProviderDisabled(String s) {

	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int i) {

	}

	@Override
	public void onStatusChanged(String s, int i, Bundle bundle) {

	}

	/**
	 * Передаёт определённое местоположение сервису на дальнейшую обработку
	 * (далее сервис передаёт другим обработчикам).
	 *
	 * @param p определённое местоположение
	 */
	private void handleLocation(TrackPoint p) {
		Message msg = new Message();
		msg.obj = p;
		mServiceHandler.handleMessage(msg);
	}

	/**
	 * Обработчик определённых местоположений.
	 */
	private final class TrackingHandler extends Handler {
		/**
		 * Конструктор.
		 *
		 * @param looper экзепляр класса, реализующий слой для работы с потоками и событиями
		 */
		public TrackingHandler(Looper looper) {
			super(looper);
		}

		private void handlePredict(SensorGpsDataItem sdi) {
			mKalmanFilter.predict(sdi.getTimestamp(), sdi.getAbsEastAcc(), sdi.getAbsNorthAcc());
		}

		private void handleUpdate(SensorGpsDataItem sdi) {
			double xVel = sdi.getSpeed() * Math.cos(sdi.getCourse());
			double yVel = sdi.getSpeed() * Math.sin(sdi.getCourse());

			mKalmanFilter.update(
					sdi.getTimestamp(),
					Coordinates.longitudeToMeters(sdi.getGpsLon()),
					Coordinates.latitudeToMeters(sdi.getGpsLat()),
					xVel,
					yVel,
					sdi.getPosErr(),
					sdi.getVelErr()
			);
		}

		private Location locationAfterUpdateStep(SensorGpsDataItem sdi) {
			double xVel, yVel;
			Location loc = new Location(TAG);
			GeoPoint pp = Coordinates.metersToGeoPoint(mKalmanFilter.getCurrentX(),
					mKalmanFilter.getCurrentY());
			loc.setLatitude(pp.Latitude);
			loc.setLongitude(pp.Longitude);
			loc.setAltitude(sdi.getGpsAlt());
			xVel = mKalmanFilter.getCurrentXVel();
			yVel = mKalmanFilter.getCurrentYVel();
			double speed = Math.sqrt(xVel * xVel + yVel * yVel);
			loc.setBearing((float) sdi.getCourse());
			loc.setSpeed((float) speed);
			loc.setTime(System.currentTimeMillis());
			loc.setElapsedRealtimeNanos(System.nanoTime());
			loc.setAccuracy((float) sdi.getPosErr());

			return loc;
		}

		@Override
		public void handleMessage(Message msg) {
			if (msg != null && msg.obj != null) {
				// Получение определённого местоположения от обработчика
				TrackPoint point = (TrackPoint) msg.obj;
				SensorGpsDataItem sdi;
				double lastTimeStamp = 0.0;
				while ((sdi = mSensorDataQueue.poll()) != null) {
					if (sdi.getTimestamp() < lastTimeStamp) {
						continue;
					}
					lastTimeStamp = sdi.getTimestamp();

					if (sdi.getGpsLat() == SensorGpsDataItem.NOT_INITIALIZED) {
						handlePredict(sdi);
					} else {
						handleUpdate(sdi);
						point = new TrackPoint(locationAfterUpdateStep(sdi));

					}
				}

				Toast.makeText(KalmanTrackingService.this,
						"lat: " + point.getLatitude() + " " + "lon: " + point.getLongitude(), Toast.LENGTH_SHORT).show();
				sBus.post(point);
			}
		}
	}
}

