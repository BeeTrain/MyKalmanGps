package ru.chernakov.mykalmangps.utils;

import com.squareup.otto.Bus;
import com.squareup.otto.ThreadEnforcer;

public final class EventBus extends Bus {
	/**
	 * Внутренний класс, "Initialization on Demand Holder"
	 */
	private static class EventBusHolder {
		private final static Bus instance = new Bus(ThreadEnforcer.ANY);
	}

	/**
	 * @return экземпляр синглтона
	 */
	public static Bus getInstance() {
		return EventBusHolder.instance;
	}

	/**
	 * Рассылает событие из незарегистрированного источника
	 *
	 * @param context контекст события
	 * @param event   событие
	 */
	public static void postUnregistered(Object context, Object event) {
		Bus bus = getInstance();
		bus.register(context);
		bus.post(event);
		bus.unregister(context);
	}
}
