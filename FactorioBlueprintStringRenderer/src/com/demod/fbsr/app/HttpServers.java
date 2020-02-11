package com.demod.fbsr.app;

import org.rapidoid.setup.Setup;

import java.util.concurrent.ConcurrentHashMap;

public class HttpServers {
    private static ConcurrentHashMap<String, Setup> servers = new ConcurrentHashMap<>();

    public static Setup create(String address, int port) {
        String key = address + ":" + port;
        return servers.computeIfAbsent(key, (k) -> {
            String[] split = k.split(":");
            return Setup.create("server").address(split[0]).port(Integer.parseInt(split[1]));
        });
    }
}
