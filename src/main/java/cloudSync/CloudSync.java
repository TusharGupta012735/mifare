package cloudSync;

import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;

import helper.CheckInternet;
import repository.CloudSyncRepository;

public class CloudSync {

    public static void startBackgroundSync() {

        Thread syncThread = new Thread(() -> {

            // final String ENDPOINT =
            // "https://smartserv.in/bsd-dashboard/api/attendance/admin/upload-batch";
            final String ENDPOINT = "http://localhost:9090/api/attendance/admin/upload-batch";

            while (true) {
                try {
                    if (!CheckInternet.isInternetAvailable()) {
                        Thread.sleep(2000);
                        continue;
                    }
                    java.net.URI uri = java.net.URI.create(ENDPOINT);
                    java.net.URL url = uri.toURL();

                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();

                    List<Map<String, Object>> payload = CloudSyncRepository.fetchPendingTransUploads();
                    System.out.println(payload);

                    if (payload.isEmpty()) {
                        Thread.sleep(20000);
                        continue;
                    }

                    // ----------------------api call--------------------------------

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
                        byte[] bytes = jsonPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        os.write(bytes);

                    }

                    int responseCode = conn.getResponseCode();

                    InputStream is;
                    if (responseCode >= 200 && responseCode < 300) {
                        is = conn.getInputStream();
                        System.out.println("[SYNC] Upload successful");
                    } else {
                        is = conn.getErrorStream();
                        System.err.println("[SYNC] Server error: " + responseCode);
                    }

                    String responseBody = "";

                    if (is != null) {
                        responseBody = new String(
                                is.readAllBytes(),
                                java.nio.charset.StandardCharsets.UTF_8);
                    }
                    System.out.println("[SYNC] response body = " + responseBody);

                    List<String> failedCardUids = mapper.readValue(responseBody,
                            new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {
                            });

                    // ---------------------------------------------------------

                    Set<String> failedSet = new HashSet<>(failedCardUids);
                    CloudSyncRepository.markUploadedExceptFailed(payload, failedSet);

                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                try {
                    Thread.sleep(50_000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

        }, "background-sync-thread");
        syncThread.setDaemon(true);
        syncThread.start();
    }
}
