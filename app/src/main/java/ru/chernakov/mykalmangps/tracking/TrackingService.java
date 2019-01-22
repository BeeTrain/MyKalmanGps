package ru.chernakov.mykalmangps.tracking;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.GnssStatus;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;

import com.squareup.otto.Bus;

import ru.chernakov.mykalmangps.App;
import ru.chernakov.mykalmangps.R;
import ru.chernakov.mykalmangps.utils.EventBus;
import ru.chernakov.mykalmangps.utils.NotificationHelper;

@SuppressWarnings("ResourceType")
public final class TrackingService extends Service implements LocationListener {
	/**
	 * Фильтр действия "GNSS трекинг завершен"
	 */
	public static final String TRACKING_STOP_ACTION = "ru.ifrigate.flugersale.gps.trader.TRACKING_STOP_ACTION";

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
	 * [GNSS] Максимальная погрешность определения координат местоположения с помощью GNSS, м
	 */
	public static final int GNSS_MAX_ACCURACY = 150;

	/**
	 * [NWK] Интервал между попытками определения координат, мс
	 */
	public static final int NWK_MIN_TIME = 5000;

	/**
	 * [NWK] Максимальная погрешность определения координат местоположения с помощью вышки мобильной связи, м
	 * Данный параметр используется для того, чтобы в случае, когда недоступен GNSS приёмник и данные определяются
	 * с помощью вышки мобильной связи, максимально отфильтровать координаты местоположения, определённые с большой
	 * погрешностью
	 */
	public static final int NWK_MAX_ACCURACY = 2 * GNSS_MAX_ACCURACY;

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
	 * Текущий статус
	 */
	public static boolean sIsActive;

	/**
	 * Провайдер определения местоположения через спутниковые системы
	 */
	private LocationManager mLocatorGnss;

	/**
	 * Провайдер определения местоположения через сеть
	 */
	private LocationManager mLocatorNwk;

	/**
	 * Обработчик определения коодинат
	 */
	private TrackingHandler mServiceHandler;

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

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onCreate() {
		HandlerThread thread = new HandlerThread("ServiceStartArguments", Process.THREAD_PRIORITY_BACKGROUND);
		thread.start();

		sBus = EventBus.getInstance();
		sBus.register(this);

		mServiceHandler = new TrackingHandler(thread.getLooper());

		if (mLocatorGnss == null) {
			mLocatorGnss = (LocationManager) App.getContext().getSystemService(Context.LOCATION_SERVICE);
		}

		if (mLocatorNwk == null) {
			mLocatorNwk = (LocationManager) App.getContext().getSystemService(Context.LOCATION_SERVICE);
		}

		App.setTrackingService(this);
		sIsActive = true;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// Создание оповещения о запуске службы
		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
		NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "location");
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

	@Override
	public void onDestroy() {
		stopTracking(new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER});
		mServiceHandler.getLooper().quit();

		super.onDestroy();
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
			} else if (provider.equals(LocationManager.NETWORK_PROVIDER)
					&& mLocatorNwk.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
				mLocatorNwk.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, NWK_MIN_TIME, 0, this);
			}
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
			} else if (provider.equals(LocationManager.NETWORK_PROVIDER)
					&& mLocatorNwk.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
				mLocatorNwk.removeUpdates(this);
			}
		}
	}

	//
	// Callback'и, используемые для определения местоположения
	//
	@Override
	public void onLocationChanged(Location location) {
		if (!TextUtils.isEmpty(location.getProvider())) {
			if (location.getProvider().equals(LocationManager.GPS_PROVIDER)) {
				// Сохраняем местоположение, если оно определено с помощью GNSS и при определении
				// зайдествовано минимально необходимое количество спутников
				TrackPoint point = new TrackPoint(location);

				handleLocation(point);
			} else if (location.getProvider().equals(LocationManager.NETWORK_PROVIDER)
					&& (location.getAccuracy() / 0.68) - NWK_MAX_ACCURACY <= 0.0001) {
				// Местоположение, определённое с помощью вышек беспроводной связи проверяется только
				// на допустимую погрешность
				TrackPoint point = new TrackPoint(location);

				handleLocation(point);
			}
		}
	}

	@Override
	public void onStatusChanged(String s, int i, Bundle bundle) {
	}

	@Override
	public void onProviderEnabled(String s) {
		startTracking(new String[]{s});
	}

	@Override
	public void onProviderDisabled(String s) {
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

		@Override
		public void handleMessage(Message msg) {
			if (msg != null && msg.obj != null) {
				// Получение определённого местоположения от обработчика
				TrackPoint point = (TrackPoint) msg.obj;

				sBus.post(point);
			}
		}
	}
}
