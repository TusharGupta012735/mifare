package util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Simple CSV reader that supports quoted fields (") and commas inside quotes.
 * First non-empty row is treated as header (column names).
 */
public class CsvReader {

    public static List<Map<String, String>> readCsvAsMaps(Path csvFile) throws IOException {
        List<String> lines = Files.readAllLines(csvFile, StandardCharsets.UTF_8);
        List<String> nonEmpty = new ArrayList<>();
        for (String L : lines) {
            if (L != null && !L.trim().isEmpty())
                nonEmpty.add(L);
        }
        if (nonEmpty.isEmpty())
            return Collections.emptyList();

        List<String> headers = parseCsvLine(nonEmpty.get(0));
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < nonEmpty.size(); i++) {
            List<String> fields = parseCsvLine(nonEmpty.get(i));
            Map<String, String> map = new LinkedHashMap<>();
            for (int c = 0; c < headers.size(); c++) {
                String key = headers.get(c).trim();
                String val = c < fields.size() ? fields.get(c) : "";
                map.put(key, val == null ? "" : val.trim());
            }
            // optional: skip rows that are all empty
            boolean allBlank = true;
            for (String v : map.values())
                if (v != null && !v.trim().isEmpty()) {
                    allBlank = false;
                    break;
                }
            if (!allBlank)
                rows.add(map);
        }
        return rows;
    }

    private static List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        if (line == null)
            return out;
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuote && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    // escaped quote -> add one quote and skip next
                    cur.append('"');
                    i++;
                } else {
                    inQuote = !inQuote;
                }
            } else if (ch == ',' && !inQuote) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        out.add(cur.toString());
        return out;
    }
}
