package com.demod.fbsr.app;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.json.JSONObject;

import com.demod.factorio.Config;
import com.demod.fbsr.app.PluginFinder.Plugin;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.google.common.util.concurrent.ServiceManager.Listener;

public class StartAllServices {

	private static void addServiceIfEnabled(List<Service> services, String configKey,
			Supplier<? extends Service> factory) {
		JSONObject configJson = Config.get();
		if (configJson.has(configKey) && configJson.getJSONObject(configKey).optBoolean("enabled", true)) {
			services.add(factory.get());
		}
	}

	public static void main(String[] args) {
		List<Service> services = new ArrayList<>();
		addServiceIfEnabled(services, "discord", BlueprintBotDiscordService::new);
		addServiceIfEnabled(services, "reddit", BlueprintBotRedditService::new);
		addServiceIfEnabled(services, "irc", BlueprintBotIRCService::new);
		addServiceIfEnabled(services, "webapi", WebAPIService::new);
		addServiceIfEnabled(services, "watchdog", WatchdogService::new);
		addServiceIfEnabled(services, "logging", LoggingService::new);

		ServiceManager manager = new ServiceManager(services);
		manager.addListener(new Listener() {
			@Override
			public void failure(Service service) {
				System.out.println("SERVICE FAILURE: " + service.getClass().getSimpleName());
				service.failureCause().printStackTrace();
			}

			@Override
			public void healthy() {
				System.out.println("ALL SERVICES ARE HEALTHY!");
			}

			@Override
			public void stopped() {
				System.out.println("ALL SERVICES HAVE STOPPED!");
			}
		});

		manager.startAsync().awaitHealthy();
		Runtime.getRuntime().addShutdownHook(new Thread(() -> manager.stopAsync().awaitStopped()));

		PluginFinder.loadPlugins().forEach(Plugin::run);
	}

}
