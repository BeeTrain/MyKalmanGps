package ru.chernakov.mykalmangps.utils;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.DrawableRes;
import android.support.v4.app.NotificationCompat;

/**
 * Вспомогательный класс для работы с уведомлениями
 *
 * @author Lord (Kuleshov M.V.)
 */
public final class NotificationHelper {
	/**
	 * Стандартный канал оповещений
	 */
	public static final String NOTIFICATION_CHANNEL_DEFAULT = "notification_channel_default";

	/**
	 * Стандартный идентификатор оповещения
	 */
	public static final String NOTIFICATION_ID_DEFAULT = "0001";

	/**
	 * Идентификатор уведомления о новом сообщении
	 */
	public static final int MESSAGE_NOTIFY_ID = 1;

	/**
	 * Единственный экземпляр класса
	 */
	private static NotificationHelper instance;

	/**
	 * Контекст приложения
	 */
	private static Context context;

	/**
	 * Системная утилита, управляющая уведомлениями
	 */
	private NotificationManager mManager;

	/**
	 * Приватный конструктор для Singleton
	 *
	 * @param context контекст приложения
	 */
	private NotificationHelper(Context context) {
		NotificationHelper.context = context;
		mManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
	}

	/**
	 * Получение ссылки на синглтон
	 */
	public static NotificationHelper getInstance(Context context) {
		if (instance == null) {
			instance = new NotificationHelper(context);
		} else {
			NotificationHelper.context = context;
		}

		return instance;
	}

	/**
	 * Добавляет уведомление в статус бар
	 *
	 * @param action       Intent для запуска при нажатии на уведомление
	 * @param message      текст уведомления
	 * @param iconResource идентификатор графического ресурса для использования в качестве иконки
	 * @param notifyUser   флаг "Оповещать пользователя" (использовать звук/вибро/диодный индикатор)
	 * @param ongoing      флаг "Постоянная операция"
	 */
	public void showNotification(Intent action, String appName, String message, @DrawableRes int iconResource,
	                             boolean notifyUser, boolean ongoing) {
		showNotification(MESSAGE_NOTIFY_ID, action, appName, message, iconResource, notifyUser, ongoing);
	}

	/**
	 * Добавляет уведомление в статус бар
	 *
	 * @param id           идентификатор уведомления
	 * @param action       Intent для запуска при нажатии на уведомление
	 * @param message      текст уведомления
	 * @param iconResource идентификатор графического ресурса для использования в качестве иконки
	 * @param notifyUser   флаг "Оповещать пользователя" (использовать звук/вибро/диодный индикатор)
	 * @param ongoing      флаг "Постоянная операция"
	 */
	public void showNotification(int id, Intent action, String appName, String message, @DrawableRes int iconResource,
	                             boolean notifyUser, boolean ongoing) {
		NotificationCompat.Builder nb = new NotificationCompat.Builder(context, NOTIFICATION_ID_DEFAULT)
				// для версии Android > 3.0 необходимо использовать
				// NotificationCompat.Builder nb = new
				// NotificationBuilder(context)
				.setSmallIcon(iconResource)
				.setAutoCancel(!ongoing)        // уведомление закроется по клику на него (если оно не постоянное)
				.setTicker(message)             // текст, который отобразится вверху статус-бара при создании уведомления
				.setContentText(message)        // основной текст уведомления
				.setContentIntent(PendingIntent.getActivity(context, 0, action, PendingIntent.FLAG_CANCEL_CURRENT))
				.setWhen(System.currentTimeMillis())    // время вывода уведомления
				.setContentTitle(appName)               // заголовок уведомления
				.setOngoing(ongoing)
				.setChannelId(NOTIFICATION_ID_DEFAULT);

		if (notifyUser) {
			// звук, вибро и диодный индикатор - по умолчанию
			nb = nb.setDefaults(Notification.DEFAULT_ALL);
		}

		mManager.notify(id, nb.build());
	}

	/**
	 * Убирает оповещение с идентификатором, равным MESSAGE_NOTIFY_ID
	 */
	public void cancelNotification() {
		cancelNotification(MESSAGE_NOTIFY_ID);
	}

	/**
	 * Убирает оповещение
	 *
	 * @param id идентификатор уведомления
	 */
	public void cancelNotification(int id) {
		mManager.cancel(id);
	}
}
