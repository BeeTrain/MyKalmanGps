package ru.chernakov.mykalmangps.ui;

import android.Manifest;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.widget.ImageButton;
import android.widget.TextView;

import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.mylocation.DirectedLocationOverlay;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;
import ru.chernakov.mykalmangps.App;
import ru.chernakov.mykalmangps.R;
import ru.chernakov.mykalmangps.kalman.KalmanTrackingService;
import ru.chernakov.mykalmangps.tracking.TrackPoint;
import ru.chernakov.mykalmangps.tracking.TrackingService;
import ru.chernakov.mykalmangps.utils.EventBus;

public class MainActivity extends AppCompatActivity {
	/**
	 * Базовое увеличение, устанавливаемое при загрузке карты
	 */
	protected static final int BASE_MAP_ZOOM = 14;

	/**
	 * Компонент разметки для отображения карты
	 */
	@BindView(R.id.map)
	protected MapView mMapView;

	/**
	 * Счетчик обработанных точек
	 */
	@BindView(R.id.tv_point_counter)
	TextView mPointCounter;

	/**
	 * Кнопка переключения активности сервиса gps
	 */
	@BindView(R.id.ib_gps)
	ImageButton mGpsButton;

	@BindView(R.id.tv_service)
	TextView mActiveService;

	/**
	 * Инфраструктурный объект Butterknife
	 */
	protected Unbinder mUnBinder;

	/**
	 * Текущее местоположение пользователя
	 */
	protected DirectedLocationOverlay mLocationOverlay;

	/**
	 * Массив разрешений, которые необходимо проверять перед запуском приложения
	 */
	public static List<String> appUncheckedPermissions;

	/**
	 * Текущее местоположение пользователя
	 */
	protected TrackPoint mCurrentUserPosition;
	/**
	 * Объект для связи активити и фрагмента
	 */
	protected static Bus sBus;

	MenuItem mKalmanMenu;

	MenuItem mLocationMenu;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		initRequiredPermissions();
		ActivityCompat.requestPermissions(
				this, appUncheckedPermissions.toArray(new String[appUncheckedPermissions.size()]), 0
		);

		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		sBus = EventBus.getInstance();
		sBus.register(this);


		mUnBinder = ButterKnife.bind(this);

		// Устанавливаем масштаб по умолчанию
		mMapView.getController().setZoom(BASE_MAP_ZOOM);
		mMapView.setMinZoomLevel(BASE_MAP_ZOOM - 11);
		mMapView.setMaxZoomLevel(BASE_MAP_ZOOM + 4);
		// Разрешаем встроенные кнопки изменения масштаба
		mMapView.setBuiltInZoomControls(true);
		// Включаем использование сети Интернет, если она доступна
		mMapView.setUseDataConnection(true);
		// Включаем поддержку режима мульти-тач для управления картовй
		mMapView.setMultiTouchControls(true);
		mLocationOverlay = new DirectedLocationOverlay(App.getContext());

		if (TrackingService.sIsActive && !KalmanTrackingService.sIsActive) {
			mActiveService.setText("Обычный сервис gps");
		} else if (!TrackingService.sIsActive && KalmanTrackingService.sIsActive) {
			mActiveService.setText("Сервис gps Калмана");
		}
	}

	@Subscribe
	public void onPointCaptured(final TrackPoint point) {
		if (point != null) {
			App.getTrack().add(point);
			mPointCounter.setText(String.valueOf(App.getTrack().size()));
			mCurrentUserPosition = point;
			if (App.getTrack().size() > 1) {
				mMapView.getOverlayManager().add(getTrack(App.getTrack()));
			}
			runOnUiThread(new Runnable() {
				@Override
				public void run() {

					setCenterAndScale(point.getLatitude(), point.getLongitude());
					mLocationOverlay.setLocation(new GeoPoint(point.getLatitude(), point.getLongitude()));
					mLocationOverlay.setBearing(point.getBearing());
					mLocationOverlay.setAccuracy((int) point.getAccuracy());
					mLocationOverlay.setShowAccuracy(true);
					getMapView().getOverlays().remove(mLocationOverlay);
					getMapView().getOverlays().add(mLocationOverlay);
					getMapView().invalidate();
				}
			});
		}
	}

	@OnClick(R.id.ib_gps)
	public void toggleTrackService() {

		if (TrackingService.sIsActive && !KalmanTrackingService.sIsActive) {
			App.getContext().stopTrackingService();
			mGpsButton.setBackground(getDrawable(R.drawable.shape_round_accent));
			mGpsButton.setImageResource(R.drawable.ic_add_location_white_24dp);
			App.startKalmanTrackingService(App.getContext());
			mActiveService.setText("Сервис gps Калмана");
		} else if (!TrackingService.sIsActive && KalmanTrackingService.sIsActive) {
			App.getContext().stopKalmanTrackingService();
			mGpsButton.setBackground(getDrawable(R.drawable.shape_round));
			mGpsButton.setImageResource(R.drawable.ic_location_on_white_24dp);
			App.startTrackingService(App.getContext());
			mActiveService.setText("Обычный сервис gps");
		}
	}

	/**
	 * Возвращает компонент разметки карты
	 *
	 * @return компонент разметки для отображения карты или null, если данный компонент не проинициализирован
	 */
	public MapView getMapView() {
		if (mMapView != null) {
			return mMapView;
		}

		return null;
	}

	/**
	 * Устанаваливает центр и масштаб карты между координатами загруженной торговой точки и местоположением пользователя
	 *
	 * @param latitudeTradePoint  координата широты торговой точки
	 * @param longitudeTradePoint координата долготы торговой точки
	 */
	protected void setCenterAndScale(double latitudeTradePoint, double longitudeTradePoint) {
		if (getMapView() != null) {
			// Проверка на получение координат пользователя через GPS
			if (mCurrentUserPosition != null) {
				// Вычисляем точку для фокусировки камеры на карте
				double X = Math.abs((latitudeTradePoint - mCurrentUserPosition.getLatitude()) / 2);
				if (latitudeTradePoint > mCurrentUserPosition.getLatitude()) {
					X = Math.abs(X + mCurrentUserPosition.getLatitude());
				} else {
					X = Math.abs(X + latitudeTradePoint);
				}

				double Y = Math.abs((longitudeTradePoint - mCurrentUserPosition.getLongitude()) / 2);
				if (longitudeTradePoint > mCurrentUserPosition.getLongitude()) {
					Y = Math.abs(Y + mCurrentUserPosition.getLongitude());
				} else {
					Y = Math.abs(Y + longitudeTradePoint);
				}

				getMapView().getController().animateTo(new GeoPoint(X, Y));

				// Вычисляем масштаб для карты
				GeoPoint currentUserPosition = new GeoPoint(mCurrentUserPosition.getLatitude(), mCurrentUserPosition.getLongitude());
				GeoPoint currentTradePoint = new GeoPoint(latitudeTradePoint, longitudeTradePoint);
				int distanceBetweenTwoPoints = Math.round(currentUserPosition.distanceTo(currentTradePoint) / 1000);

				// Массив интервалов (в км.), номера элементов которого соответствуют масштабу карты
				int[] mapIntervals = App.getContext().getResources().getIntArray(R.array.map_scale_intervals);

				if (distanceBetweenTwoPoints <= mapIntervals[0]) {
					getMapView().getController().setZoom(getMapView().getMaxZoomLevel());
				} else if (distanceBetweenTwoPoints >= mapIntervals[mapIntervals.length - 1]) {
					getMapView().getController().setZoom(0);
				} else {
					for (int i = 0; i != mapIntervals.length - 1; i++) {
						if (distanceBetweenTwoPoints >= mapIntervals[i] && distanceBetweenTwoPoints < mapIntervals[i + 1]) {
							// Массив масштаба и зум подобраны эксперементальным путём
							getMapView().getController().setZoom(getMapView().getMaxZoomLevel() - i - 3);
							break;
						}
					}
				}
			} else {
				// Если нет координат местоположения пользователя, то центрируем карту на ТТ
				getMapView().getController().setZoom(16);
				getMapView().getController().animateTo(new GeoPoint(latitudeTradePoint, longitudeTradePoint));
			}
		}
	}

	private Polyline getTrack(List<TrackPoint> trackPoints) {
		List<GeoPoint> geoPoints = new ArrayList<>();
		for (TrackPoint point : trackPoints) {
			geoPoints.add(new GeoPoint(point));
		}
		Polyline line = new Polyline();
		line.setPoints(geoPoints);
		line.setColor(Color.RED);
		return line;
	}

	/**
	 * Инициализирует список разрешений, которые необходимо проверять при запуске приложения.
	 */
	private void initRequiredPermissions() {
		appUncheckedPermissions = new ArrayList<>();
		appUncheckedPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
		appUncheckedPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
		appUncheckedPermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
	}
}
