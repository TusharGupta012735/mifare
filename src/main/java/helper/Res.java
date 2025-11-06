package helper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class Res {
    private Res() {
    }

    public static String readText(String pathInJar) {
        try (InputStream in = Res.class.getResourceAsStream(pathInJar)) {
            if (in == null)
                return "";
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    if (sb.length() > 0)
                        sb.append('\n');
                    sb.append(line);
                }
                return sb.toString().trim();
            }
        } catch (Exception e) {
            return "";
        }
    }
}
