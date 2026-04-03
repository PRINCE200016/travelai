package com.tripmind.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);
    private final WebClient webClient;

    @Value("${weather.api.key}")
    private String apiKey;

    public WeatherService() {
        this.webClient = WebClient.builder()
                .baseUrl("https://api.openweathermap.org")
                .build();
    }

    public Map<String, Object> getWeather(String city) {
        if ("demo".equals(apiKey)) {
            return getFallbackWeather(city);
        }

        try {
            // Current weather
            String currentResponse = webClient.get()
                    .uri("/data/2.5/weather?q={city},IN&appid={key}&units=metric", city, apiKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // 5-day forecast
            String forecastResponse = webClient.get()
                    .uri("/data/2.5/forecast?q={city},IN&appid={key}&units=metric&cnt=5", city, apiKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseWeatherData(currentResponse, forecastResponse);
        } catch (Exception e) {
            log.error("Weather API error for {}: {}", city, e.getMessage());
            return getFallbackWeather(city);
        }
    }

    private Map<String, Object> parseWeatherData(String currentJson, String forecastJson) {
        Map<String, Object> result = new HashMap<>();

        try {
            JsonObject current = JsonParser.parseString(currentJson).getAsJsonObject();
            double temp = current.getAsJsonObject("main").get("temp").getAsDouble();
            String desc = current.getAsJsonArray("weather").get(0).getAsJsonObject().get("description").getAsString();

            result.put("temp", Math.round(temp));
            result.put("desc", capitalize(desc));

            // Forecast
            List<Map<String, Object>> forecast = new ArrayList<>();
            JsonObject forecastObj = JsonParser.parseString(forecastJson).getAsJsonObject();
            JsonArray list = forecastObj.getAsJsonArray("list");

            String[] days = {"Mon", "Tue", "Wed", "Thu", "Fri"};
            for (int i = 0; i < Math.min(5, list.size()); i++) {
                JsonObject item = list.get(i).getAsJsonObject();
                double forecastTemp = item.getAsJsonObject("main").get("temp").getAsDouble();
                String weatherMain = item.getAsJsonArray("weather").get(0).getAsJsonObject().get("main").getAsString();

                Map<String, Object> dayData = new HashMap<>();
                dayData.put("day", days[i]);
                dayData.put("temp", Math.round(forecastTemp));
                dayData.put("icon", getWeatherEmoji(weatherMain));
                forecast.add(dayData);
            }

            result.put("forecast", forecast);
        } catch (Exception e) {
            log.error("Error parsing weather data: {}", e.getMessage());
            return getFallbackWeather("Unknown");
        }

        return result;
    }

    private Map<String, Object> getFallbackWeather(String city) {
        Map<String, Object> result = new HashMap<>();
        
        // Default weather based on common Indian destinations
        Map<String, int[]> cityTemps = Map.of(
                "manali", new int[]{8, 6, 10, 7, 9},
                "goa", new int[]{30, 31, 29, 30, 28},
                "munnar", new int[]{18, 17, 19, 16, 18},
                "varanasi", new int[]{28, 30, 27, 29, 26},
                "kashmir", new int[]{5, 3, 7, 4, 6},
                "jaipur", new int[]{32, 34, 31, 33, 30}
        );

        String key = city.toLowerCase().split(",")[0].trim();
        int[] temps = cityTemps.getOrDefault(key, new int[]{25, 26, 24, 27, 25});

        result.put("temp", temps[0]);
        result.put("desc", temps[0] <= 10 ? "Cold & Clear" : temps[0] <= 22 ? "Cool & Pleasant" : "Warm & Sunny");

        String[] days = {"Mon", "Tue", "Wed", "Thu", "Fri"};
        String[] icons = temps[0] <= 10 ? new String[]{"❄️", "🌨️", "☀️", "❄️", "🌤️"}
                : temps[0] <= 22 ? new String[]{"🌤️", "🌧️", "☀️", "🌤️", "☁️"}
                : new String[]{"☀️", "☀️", "🌤️", "☀️", "🌤️"};

        List<Map<String, Object>> forecast = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Map<String, Object> day = new HashMap<>();
            day.put("day", days[i]);
            day.put("temp", temps[i]);
            day.put("icon", icons[i]);
            forecast.add(day);
        }
        result.put("forecast", forecast);

        return result;
    }

    private String getWeatherEmoji(String main) {
        return switch (main.toLowerCase()) {
            case "clear" -> "☀️";
            case "clouds" -> "☁️";
            case "rain", "drizzle" -> "🌧️";
            case "snow" -> "❄️";
            case "thunderstorm" -> "⛈️";
            case "mist", "fog", "haze" -> "🌫️";
            default -> "🌤️";
        };
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
