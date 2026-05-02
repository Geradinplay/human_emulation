package org.example.vts.log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class VTSLogger {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void info(String message) {
        log("INFO", message);
    }

    public static void error(String message, Exception e) {
        log("ERROR", message + (e != null ? " | Exception: " + e.getMessage() : ""));
    }

    public static void logResponse(String type, String message) {
        System.out.println(String.format("[%s] [VTS-RESPONSE] %s", dtf.format(LocalDateTime.now()), type));
        try {
            JsonObject json = gson.fromJson(message, JsonObject.class);
            System.out.println(gson.toJson(json));
        } catch (Exception e) {
            System.out.println("Raw: " + message);
        }
    }

    private static void log(String level, String message) {
        System.out.println(String.format("[%s] [%s] %s", dtf.format(LocalDateTime.now()), level, message));
    }
}