package ru.chernakov.mykalmangps;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import ru.chernakov.mykalmangps.kalman.KalmanTrackingService;
import ru.chernakov.mykalmangps.tracking.TrackPoint;
import ru.chernakov.mykalmangps.tracking.TrackingService;

import static ru.chernakov.mykalmangps.utils.NotificationHelper.NOTIFICATION_CHANNEL_DEFAULT;
import static ru.chernakov.mykalmangps.utils.NotificationHelper.NOTIFICATION_ID_DEFAULT;

public class App extends Application {
	/**
	 * Идентификатор оповещения о событиях сервиса определения местоположений
	 */
	public static final int TRACKING_NOTIFY_ID = 1000;

	private static App instance;

	private static List<TrackPoint> sTrack;

	private static TrackingService sTrackingService;

	private static KalmanTrackingService sKalmanTrackingService;
	/**
	 * Массив разрешений, которые необходимо проверять перед запуском приложения
	 */
	public static List<String> appUncheckedPermissions;

	@Override
	public void onCreate() {
		instance = this;
		sTrack = new ArrayList<>();

		createNotificationChannel();
		startKalmanTrackingService(this);
//		startTrackingService(this);

		super.onCreate();
	}

	public static App getContext() {
		return instance;
	}

	public static void startTrackingService(Context c) {
		if (!TrackingService.sIsActive) {
			// Запуск определения GPS координат
			Intent start = new Intent(c, TrackingService.class);
			c.startService(start);
			Toast.makeText(instance, "Трекер включен", Toast.LENGTH_SHORT).show();
		}
	}

	public void stopTrackingService() {
		if (TrackingService.sIsActive) {
			stopService(new Intent(this, TrackingService.class));
			sTrackingService.stopSelf();
			TrackingService.sIsActive = false;

			Toast.makeText(instance, "Трекер выключен", Toast.LENGTH_SHORT).show();
		}
	}

	public static void startKalmanTrackingService(Context c) {
		if (!KalmanTrackingService.sIsActive) {
			// Запуск определения GPS координат
			Intent start = new Intent(c, KalmanTrackingService.class);
			c.startService(start);
			Toast.makeText(instance, "Трекер Калмана включен", Toast.LENGTH_SHORT).show();
		}
	}

	public void stopKalmanTrackingService() {
		if (KalmanTrackingService.sIsActive) {
			stopService(new Intent(this, KalmanTrackingService.class));
			sKalmanTrackingService.stopSelf();
			KalmanTrackingService.sIsActive = false;
			Toast.makeText(instance, "Трекер Калмана выключен", Toast.LENGTH_SHORT).show();
		}
	}

	/**
	 * Создает канал оповещений (только для API >= 26)
	 */
	private void createNotificationChannel() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			int importance = NotificationManager.IMPORTANCE_DEFAULT;
			NotificationChannel channel = new NotificationChannel(NOTIFICATION_ID_DEFAULT, NOTIFICATION_CHANNEL_DEFAULT, importance);
			channel.setDescription(NOTIFICATION_ID_DEFAULT);
			NotificationManager notificationManager = getSystemService(NotificationManager.class);
			notificationManager.createNotificationChannel(channel);
		}
	}

	public static List<TrackPoint> getTrack() {
		return sTrack;
	}

	public static KalmanTrackingService getTrackingService() {
		return sKalmanTrackingService;
	}

	public static void setKalmanTrackingService(KalmanTrackingService sTrackingService) {
		App.sKalmanTrackingService = sTrackingService;
	}

	public static TrackingService getsTrackingService() {
		return sTrackingService;
	}

	public static void setTrackingService(TrackingService sTrackingService) {
		App.sTrackingService = sTrackingService;
	}
}
