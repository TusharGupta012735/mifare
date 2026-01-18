package cloudSync;

import java.util.Map;
import java.util.Set;
import java.util.ArrayList;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;

import helper.CheckInternet;
import repository.CloudSyncDeniedRepository;

public class CloudSyncDenied {

    public static void startBackgroundSync() {

        System.out.println("[DENIED-SYNC] startBackgroundSync() CALLED");

        Thread syncThread = new Thread(() -> {

            System.out.println("[DENIED-SYNC] Thread STARTED");

            // PROD
            // final String ENDPOINT =
            // "https://smartserv.in/bsd-dashboard/api/attendance/denied/admin/upload-batch";

            // LOCAL
            final String ENDPOINT = "http://localhost:9090/api/attendance/denied/admin/upload-batch";

            while (true) {
                try {
                    System.out.println("[DENIED-SYNC] Checking internet connectivity...");

                    if (!CheckInternet.isInternetAvailable()) {
                        Thread.sleep(2000);
                        continue;
                    }

                    java.net.URI uri = java.net.URI.create(ENDPOINT);
                    java.net.URL url = uri.toURL();

                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();

                    List<Map<String, Object>> payload = CloudSyncDeniedRepository.fetchPendingDeniedUploads();

                    System.out.println("[DENIED-SYNC] payload = " + payload);

                    if (payload.isEmpty()) {
                        Thread.sleep(60000);
                        continue;
                    }

                    /* ---------------------- API CALL ---------------------- */

                    ObjectMapper mapper = new ObjectMapper();
                    String jsonPayload = mapper.writeValueAsString(payload);

                    conn.setRequestMethod("POST");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setRequestProperty("Accept", "application/json");
                    conn.setRequestProperty("Content-Length",
                            String.valueOf(jsonPayload.getBytes(StandardCharsets.UTF_8).length));

                    try (java.io.OutputStream os = conn.getOutputStream()) {
                        os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
                    }

                    int responseCode = conn.getResponseCode();

                    InputStream is;
                    if (responseCode >= 200 && responseCode < 300) {
                        is = conn.getInputStream();
                        System.out.println("[DENIED-SYNC] Upload successful");
                    } else {
                        is = conn.getErrorStream();
                        System.err.println("[DENIED-SYNC] Server error: " + responseCode);
                    }

                    String responseBody = "";

                    if (is != null) {
                        responseBody = new String(
                                is.readAllBytes(),
                                StandardCharsets.UTF_8);
                    }

                    System.out.println("[DENIED-SYNC] response body = " + responseBody);

                    List<String> failedCardUids = new ArrayList<>();
                    if (responseBody != null && !responseBody.trim().isEmpty()) {
                        try {
                            failedCardUids = mapper.readValue(
                                    responseBody,
                                    new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {
                                    });
                        } catch (Exception parseEx) {
                            System.err.println("[DENIED-SYNC] Failed to parse response: " + parseEx.getMessage());
                            failedCardUids = new ArrayList<>();
                        }
                    }

                    /* ----------------------------------------------------- */

                    if (responseCode >= 200 && responseCode < 300) {
                        Set<String> failedSet = new HashSet<>(failedCardUids);
                        CloudSyncDeniedRepository.markUploadedExceptFailed(payload, failedSet);
                    } else {
                        System.err
                                .println("[DENIED-SYNC] Not marking uploaded because server returned " + responseCode);
                    }

                } catch (Exception ex) {
                    ex.printStackTrace();
                }

                try {
                    Thread.sleep(60_000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

        }, "background-denied-sync-thread");

        syncThread.setDaemon(true);
        syncThread.start();
    }
}
