package com.tripmind.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Hosting platforms (Render, Supabase, Railway, Heroku) hand out a single
 * {@code DATABASE_URL=postgres://user:pass@host:port/db?sslmode=require}. Spring needs a JDBC URL plus separate
 * credentials, so this converts it at startup. An explicit {@code DB_URL} always wins.
 */
public class DatabaseUrlEnvironment implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        String explicit = env.getProperty("DB_URL");
        String url = env.getProperty("DATABASE_URL");
        if ((explicit != null && !explicit.isBlank()) || url == null || url.isBlank()) return;
        Map<String, Object> props = fromDatabaseUrl(url);
        if (!props.isEmpty()) env.getPropertySources().addFirst(new MapPropertySource("databaseUrl", props));
    }

    static Map<String, Object> fromDatabaseUrl(String raw) {
        Map<String, Object> out = new HashMap<>();
        if (!raw.startsWith("postgres://") && !raw.startsWith("postgresql://")) return out;
        URI uri = URI.create(raw.replaceFirst("^postgres(ql)?://", "http://"));
        String query = uri.getRawQuery();
        int port = uri.getPort() > 0 ? uri.getPort() : 5432;
        String jdbc = "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getRawPath();
        if (query != null && !query.isBlank()) jdbc += "?" + query;
        else if (!isLocal(uri.getHost())) jdbc += "?sslmode=require";

        out.put("spring.datasource.url", jdbc);
        out.put("spring.datasource.driverClassName", "org.postgresql.Driver");
        out.put("spring.jpa.properties.hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        String userInfo = uri.getRawUserInfo();
        if (userInfo != null) {
            int i = userInfo.indexOf(':');
            out.put("spring.datasource.username", decode(i >= 0 ? userInfo.substring(0, i) : userInfo));
            if (i >= 0) out.put("spring.datasource.password", decode(userInfo.substring(i + 1)));
        }
        return out;
    }

    private static boolean isLocal(String host) {
        return host == null || host.equals("localhost") || host.equals("127.0.0.1") || !host.contains(".");
    }

    private static String decode(String s) { return URLDecoder.decode(s, StandardCharsets.UTF_8); }
}
