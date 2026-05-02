package org.example.voice;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AvatarVoice {
    private final HttpClient client = HttpClient.newHttpClient();

    public void speak(String text) throws Exception {
        // Подготавливаем JSON (можно использовать Jackson/Gson или просто строку)
        String json = String.format("{\"text\": \"%s\", \"temperature\": 0.3}", text);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:8080/say"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        // Получаем байты аудио
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() == 200) {
            // Сохраняем или сразу играем
            Path path = Paths.get("voice_output.wav");
            java.nio.file.Files.write(path, response.body());
            System.out.println("Озвучено!");
        }
    }
}