package ui;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Pair;
import nfc.SmartMifareReader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * AttendancePage: reads NFC card and displays UID, Name, Time, Date, Location
 * in a table sized to occupy available space.
 */
public class AttendancePage {

    /**
     * Create the attendance UI. Caller can set it into the scene via
     * setContent(AttendancePage.create()).
     */
    public static Parent create() {
        // Root container that can grow to fill the parent
        VBox root = new VBox(12);
        root.setPadding(new Insets(16));
        root.setStyle("-fx-background-color: #F5F5F5;");
        // allow root to expand in both directions when placed inside a resizable parent
        root.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        Label status = new Label("📡 Waiting for card...");
        status.setStyle("""
                    -fx-font-size: 24px;
                    -fx-font-weight: bold;
                    -fx-text-fill: #1565C0;
                    -fx-background-color: #E3F2FD;
                    -fx-padding: 20 30;
                    -fx-background-radius: 10;
                    -fx-border-color: #1565C0;
                    -fx-border-radius: 10;
                    -fx-border-width: 2;
                """);

        root.getChildren().add(status);

        // Start background thread to read NFC card and update UI
        new Thread(() -> {
            // blocking read (10s timeout)
            SmartMifareReader.ReadResult rr = SmartMifareReader.readUIDWithData(10_000);
            String uid = (rr == null) ? "" : rr.uid;

            LocalDate nowDate = LocalDate.now();
            LocalTime nowTime = LocalTime.now();
            DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss");

            String dateStr = dateFmt.format(nowDate);
            String timeStr = timeFmt.format(nowTime);

            // read location.txt (best-effort) from current working directory
            String location = "Room x";
            try {
                Path p = Paths.get("location.txt");
                if (Files.exists(p)) {
                    String raw = Files.readString(p).trim();
                    if (!raw.isEmpty())
                        location = raw;
                }
            } catch (Exception ex) {
                location = "(error reading location)";
            }

            String name = extractName(rr);

            final String finalUid = (uid == null || uid.isEmpty()) ? "(not read)" : uid;
            final String finalName = (name == null || name.isEmpty()) ? "(unknown)" : name;
            final String finalDate = dateStr;
            final String finalTime = timeStr;
            final String finalLocation = location;

            Platform.runLater(() -> {
                if (rr == null || rr.uid == null || rr.uid.isEmpty()) {
                    Label err = new Label("❌ No card read (timed out or error).");
                    err.setStyle("""
                                -fx-font-size: 20px;
                                -fx-font-weight: bold;
                                -fx-text-fill: #C62828;
                                -fx-background-color: #FFEBEE;
                                -fx-padding: 16 20;
                                -fx-background-radius: 8;
                                -fx-border-color: #C62828;
                                -fx-border-radius: 8;
                                -fx-border-width: 2;
                            """);
                    root.getChildren().setAll(err);
                    return;
                }

                // Build table
                GridPane table = new GridPane();
                table.setVgap(12);
                table.setHgap(18);
                table.setPadding(new Insets(16));
                table.setStyle("""
                            -fx-background-color: white;
                            -fx-background-radius: 10;
                            -fx-border-radius: 10;
                            -fx-border-color: #E0E0E0;
                            -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 12, 0, 0, 2);
                        """);
                // allow table to grow horizontally
                table.setMaxWidth(Double.MAX_VALUE);

                // Column constraints: percent-based so columns scale with available width
                ColumnConstraints leftCol = new ColumnConstraints();
                leftCol.setPercentWidth(35);
                leftCol.setHalignment(HPos.LEFT);
                ColumnConstraints rightCol = new ColumnConstraints();
                rightCol.setPercentWidth(65);
                rightCol.setHalignment(HPos.LEFT);
                table.getColumnConstraints().addAll(leftCol, rightCol);

                List<Label> headingLabels = new ArrayList<>();
                List<Label> valueLabels = new ArrayList<>();

                BiConsumer<Integer, Pair<String, String>> addKV = (rowIdx, kv) -> {
                    Label h = new Label(kv.getKey());
                    h.setPadding(new Insets(8, 10, 8, 10));
                    h.setStyle("""
                                -fx-font-weight: bold;
                                -fx-text-fill: white;
                                -fx-background-radius: 6;
                                -fx-background-color: linear-gradient(to right, #1976D2, #42A5F5);
                            """);
                    h.setWrapText(true);
                    h.setMaxWidth(Double.MAX_VALUE);

                    Label v = new Label(kv.getValue());
                    v.setPadding(new Insets(8));
                    v.setStyle("-fx-text-fill: #212121;");
                    v.setWrapText(true);
                    v.setMaxWidth(Double.MAX_VALUE);

                    GridPane.setConstraints(h, 0, rowIdx);
                    GridPane.setConstraints(v, 1, rowIdx);
                    table.getChildren().addAll(h, v);

                    headingLabels.add(h);
                    valueLabels.add(v);
                };

                int r = 0;
                addKV.accept(r++, new Pair<>("UID", finalUid));
                addKV.accept(r++, new Pair<>("Name", finalName));
                addKV.accept(r++, new Pair<>("Time", finalTime));
                addKV.accept(r++, new Pair<>("Date", finalDate));
                addKV.accept(r++, new Pair<>("Location", finalLocation));

                // Wrap table into ScrollPane so it can expand and scroll when necessary
                ScrollPane sp = new ScrollPane(table);
                sp.setFitToWidth(true);
                sp.setFitToHeight(true);
                sp.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
                sp.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

                // Header label
                Label header = new Label("Attendance Entry");
                header.setStyle("""
                            -fx-font-weight: bold;
                            -fx-text-fill: #1565C0;
                        """);

                // Container that holds header and scroll pane — set to grow
                VBox container = new VBox(12);
                container.setPadding(new Insets(12));
                container.setStyle("-fx-background-color: #F5F5F5;");
                container.getChildren().addAll(header, sp);
                container.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

                // Allow the ScrollPane to take all remaining vertical space
                VBox.setVgrow(sp, Priority.ALWAYS);

                // Dynamic font sizing
                DoubleBinding fontSizeBinding = Bindings.createDoubleBinding(() -> {
                    double w = Math.max(320, container.getWidth());
                    double fs = Math.max(12, Math.min(22, w / 40.0));
                    return fs;
                }, container.widthProperty());

                double initFs = fontSizeBinding.get();
                header.setStyle(header.getStyle() + " -fx-font-size: " + Math.max(16, initFs + 2) + "px;");

                for (Label h : headingLabels) {
                    h.setStyle(h.getStyle() + " -fx-font-size: " + Math.max(12, initFs) + "px;");
                }
                for (Label v2 : valueLabels) {
                    v2.setStyle(v2.getStyle() + " -fx-font-size: " + Math.max(12, initFs - 1) + "px;");
                }

                fontSizeBinding.addListener((obs, oldV, newV) -> {
                    double fs = newV.doubleValue();
                    header.setStyle("""
                                -fx-font-weight: bold;
                                -fx-text-fill: #1565C0;
                            """ + " -fx-font-size: " + Math.max(16, fs + 2) + "px;");

                    for (Label h2 : headingLabels) {
                        h2.setStyle("""
                                    -fx-font-weight: bold;
                                    -fx-text-fill: white;
                                    -fx-background-radius: 6;
                                    -fx-background-color: linear-gradient(to right, #1976D2, #42A5F5);
                                """ + " -fx-font-size: " + Math.max(12, fs) + "px;");
                    }
                    for (Label v3 : valueLabels) {
                        v3.setStyle("""
                                    -fx-text-fill: #212121;
                                """ + " -fx-font-size: " + Math.max(12, fs - 1) + "px;");
                    }
                });

                // Replace root content with the new, full-size container
                root.getChildren().setAll(container);
            });
        }, "attendance-thread").start();

        return root;
    }

    // Try to extract name from rr.data (CSV first, then key:value/key=value)
    private static String extractName(SmartMifareReader.ReadResult rr) {
        if (rr == null || rr.data == null || rr.data.trim().isEmpty())
            return "";
        String data = rr.data.trim();

        // CSV first (first token)
        String[] csv = data.split(",", -1);
        if (csv.length > 0 && !csv[0].trim().isEmpty())
            return csv[0].trim();

        // key:value or key=value pairs
        String[] items = data.split("[|;,]");
        for (String it : items) {
            String s = it.trim();
            if (s.contains(":")) {
                String[] kv = s.split(":", 2);
                if (kv[0].toLowerCase().contains("name"))
                    return kv[1].trim();
            } else if (s.contains("=")) {
                String[] kv = s.split("=", 2);
                if (kv[0].toLowerCase().contains("name"))
                    return kv[1].trim();
            }
        }
        return "";
    }
}
