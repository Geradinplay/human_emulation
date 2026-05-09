package org.example.ui;

import java.io.File;
import java.net.Socket;

public class DockerService {

    // =========================
    // CHECK SERVICE
    // =========================
    public boolean isServiceReady(String host, int port) {
        try (Socket socket = new Socket(host, port)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // =========================
    // START ALL
    // =========================
    public void upCompose(String path) throws Exception {

        String cmd = System.getProperty("os.name")
                .toLowerCase()
                .contains("win")
                ? "cmd /c docker compose up -d"
                : "docker compose up -d";

        Process p = Runtime.getRuntime().exec(
                cmd,
                null,
                new File(path)
        );

        p.waitFor();
    }

    // =========================
    // START QWEN ONLY
    // =========================
    public void upQwen(String path) throws Exception {

        String cmd = System.getProperty("os.name")
                .toLowerCase()
                .contains("win")
                ? "cmd /c docker compose up -d qwen-ai"
                : "docker compose up -d qwen-ai";

        Process p = Runtime.getRuntime().exec(
                cmd,
                null,
                new File(path)
        );

        p.waitFor();
    }

    // =========================
    // START QDRANT ONLY
    // =========================
    public void upQdrant(String path) throws Exception {

        String cmd = System.getProperty("os.name")
                .toLowerCase()
                .contains("win")
                ? "cmd /c docker compose up -d qdrant"
                : "docker compose up -d qdrant";

        Process p = Runtime.getRuntime().exec(
                cmd,
                null,
                new File(path)
        );

        p.waitFor();
    }

    // =========================
    // STOP QWEN
    // =========================
    public void stopQwen(String path) throws Exception {

        String cmd = System.getProperty("os.name")
                .toLowerCase()
                .contains("win")
                ? "cmd /c docker compose stop qwen-ai"
                : "docker compose stop qwen-ai";

        Process p = Runtime.getRuntime().exec(
                cmd,
                null,
                new File(path)
        );

        p.waitFor();
    }

    // =========================
    // STOP QDRANT
    // =========================
    public void stopQdrant(String path) throws Exception {

        String cmd = System.getProperty("os.name")
                .toLowerCase()
                .contains("win")
                ? "cmd /c docker compose stop qdrant"
                : "docker compose stop qdrant";

        Process p = Runtime.getRuntime().exec(
                cmd,
                null,
                new File(path)
        );

        p.waitFor();
    }

    // =========================
    // DOCKER CHECK
    // =========================
    public boolean isDockerAvailable() {
        try {
            Process p = Runtime.getRuntime().exec("docker ps");
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}