package org.example.vts.context;

import org.example.vts.client.VTubeStudioClient;

public class VTSContext {
    private static VTubeStudioClient activeClient;

    public static void setClient(VTubeStudioClient client) {
        activeClient = client;
    }

    public static VTubeStudioClient getClient() {
        return activeClient;
    }
}