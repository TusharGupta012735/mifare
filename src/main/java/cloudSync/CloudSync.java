package cloudSync;

public class CloudSync {
    private static boolean isInternetAvailable() {
        try {
            java.net.InetAddress addr = java.net.InetAddress.getByName("8.8.8.8");
            return addr.isReachable(2000);
        } catch (Exception e) {
            return false;
        }
    }

    public static void startBackgroundSync() {

        Thread syncThread = new Thread(() -> {

            final String ENDPOINT = "http://localhost:5000/sync";

            while (true) {
                try {
                    if (!isInternetAvailable()) {
                        Thread.sleep(5000);
                        continue;
                    }
                    java.net.URI uri = java.net.URI.create(ENDPOINT);
                    java.net.URL url = uri.toURL();

                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();

                    conn.setRequestMethod("POST");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/json");

                    String payload = """
                            {
                              "device": "attendance-terminal-1",
                              "timestamp": "%s"
                            }
                            """.formatted(java.time.Instant.now());

                    try (java.io.OutputStream os = conn.getOutputStream()) {
                        os.write(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }

                    int responseCode = conn.getResponseCode();

                    if (responseCode >= 200 && responseCode < 300) {
                        System.out.println("[SYNC] Upload successful");
                    } else {
                        System.err.println("[SYNC] Server error: " + responseCode);
                    }

                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                try {
                    Thread.sleep(10_000);
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
