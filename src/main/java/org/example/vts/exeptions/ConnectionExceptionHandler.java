package org.example.vts.exeptions;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.UnknownHostException;

public class ConnectionExceptionHandler {

    public static void handle(Exception e) {
        if (e instanceof UnknownHostException) {
            System.out.println("[ERROR] Unknown host: " + e.getMessage());
        } else if (e instanceof ConnectException) {
            System.out.println("[ERROR] Cannot connect to server: " + e.getMessage());
        } else if (e instanceof SocketException) {
            System.out.println("[ERROR] Socket error: " + e.getMessage());
        } else {
            System.out.println("[ERROR] Unexpected connection error: " + e.getMessage());
        }
    }
}