package org.example.vts.auth;

public interface VTSAuthListener {
    void onAuthSuccess();
    void onAuthFail(String reason);
}