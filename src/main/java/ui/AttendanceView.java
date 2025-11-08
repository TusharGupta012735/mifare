package ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import nfc.SmartMifareReader;

public class AttendanceView {

    private static final double MAX_LOGO_WIDTH = 200; // cap logo width
    private static final double LEFT_HEADING_WIDTH = 130; // width for heading cells in the details card
    private static final String HEADING_BG_COLOR = "#1c56aeff"; // blue used for headings

    private volatile String locationText = "(unknown)";
    private volatile String eventText = "(unknown)";

    // Scaling limits for dynamic typography
    private static final double BASE_HEADING_FONT = 15.0;
    private static final double BASE_VALUE_FONT = 16.0;
    private static final double MIN_HEADING_FONT = 12.0;
    private static final double MAX_HEADING_FONT = 24.0;
    private static final double MIN_VALUE_FONT = 12.0;
    private static final double MAX_VALUE_FONT = 22.0;

    // Top card controls
    private final Label headline;
    private final Label uidLabel;
    private final VBox leftTextBox;
    private final VBox root;
    private final StackPane logoContainer;
    private final ImageView logoView;

    // Bottom details card controls
    private final GridPane detailsGrid;
    private final Label fullNameValue;
    private final Label bsguidValue;
    private final Label dateValue;
    private final Label timeValue;
    private final Label locationValue;
    private final Label eventValue;

    // Keep references for dynamic font updates
    private final List<Label> headingLabels = new ArrayList<>();
    private final List<Label> valueLabels = new ArrayList<>();

    // Formatters
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // ---------------- ReadResult handling ----------------
    public void acceptReadResult(SmartMifareReader.ReadResult rr) {
        if (rr == null) {
            Platform.runLater(this::clearDetails);
            return;
        }

        // 1) Update UID display immediately
        final String uidText = (rr.uid == null || rr.uid.isBlank()) ? "(no uid)" : rr.uid;
        Platform.runLater(() -> {
            getUidLabel().setText("UID: " + uidText);
        });

        // 2) Try to extract a payload string from common ReadResult fields
        String csv = null;

        // direct known field: many ReadResult implementations expose 'data' or 'text'
        try {
            java.lang.reflect.Field f = rr.getClass().getField("data");
            Object val = f.get(rr);
            if (val != null)
                csv = String.valueOf(val);
        } catch (Throwable ignored) {
        }

        if ((csv == null || csv.isBlank())) {
            try {
                java.lang.reflect.Field f = rr.getClass().getField("text");
                Object val = f.get(rr);
                if (val != null)
                    csv = String.valueOf(val);
            } catch (Throwable ignored) {
            }
        }

        // try getter methods
        if ((csv == null || csv.isBlank())) {
            try {
                java.lang.reflect.Method m = rr.getClass().getMethod("getData");
                Object val = m.invoke(rr);
                if (val != null)
                    csv = String.valueOf(val);
            } catch (Throwable ignored) {
            }
        }
        if ((csv == null || csv.isBlank())) {
            try {
                java.lang.reflect.Method m = rr.getClass().getMethod("getText");
                Object val = m.invoke(rr);
                if (val != null)
                    csv = String.valueOf(val);
            } catch (Throwable ignored) {
            }
        }

        // last resort: toString()
        if ((csv == null || csv.isBlank())) {
            try {
                csv = rr.toString();
            } catch (Throwable ignored) {
            }
        }

        // 3) If we got something that looks useful, parse it into fields
        if (csv != null && !csv.isBlank()) {
            // updateFromCardCsv already sets FullName, BSGUID, and date/time (it sets now)
            updateFromCardCsv(csv);
        } else {
            // still update date/time even if we didn't get card payload
            String nowDate = java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String nowTime = java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            Platform.runLater(() -> {
                setDate(nowDate);
                setTime(nowTime);
            });
        }
    }

    // ---------------- Constructor / UI ----------------
    public AttendanceView() {
        // --- TOP: left text (headline, uid) --- (increased fonts)
        headline = new Label("Tap your card");
        headline.setStyle("""
                -fx-font-size: 32px;
                -fx-font-weight: 900;
                -fx-text-fill: #0D47A1;
                """);

        uidLabel = new Label("");
        uidLabel.setStyle("""
                -fx-font-size: 22px;
                -fx-font-weight: 700;
                -fx-text-fill: #2E7D32;
                """);

        Label sub = new Label("Hold for a moment, then remove the card to continue.");
        sub.setStyle("-fx-font-size: 14px; -fx-text-fill: #455A64;");

        leftTextBox = new VBox(8, headline, uidLabel, sub);
        leftTextBox.setAlignment(Pos.CENTER_LEFT);
        leftTextBox.setPadding(new Insets(6, 12, 6, 12));
        leftTextBox.setMinWidth(220);
        leftTextBox.setMaxWidth(500);

        // --- TOP: right logo (responsive) ---
        logoView = new ImageView();
        logoView.setPreserveRatio(true);
        logoView.setSmooth(true);
        logoView.setCache(true);

        logoContainer = new StackPane(logoView);
        logoContainer.setAlignment(Pos.CENTER_RIGHT);
        logoContainer.setPadding(new Insets(8));
        HBox.setHgrow(logoContainer, Priority.ALWAYS);
        logoContainer.setMaxWidth(Double.MAX_VALUE);

        // Responsive scaling with upper limit
        logoContainer.widthProperty().addListener((obs, oldW, newW) -> {
            double availableWidth = Math.min(
                    newW.doubleValue() - logoContainer.getPadding().getLeft() - logoContainer.getPadding().getRight(),
                    MAX_LOGO_WIDTH);
            if (availableWidth < 0)
                availableWidth = 0;
            logoView.setFitWidth(availableWidth);
        });

        // Top row combine
        HBox topRow = new HBox(16, leftTextBox, logoContainer);
        topRow.setAlignment(Pos.CENTER_LEFT);
        topRow.setPadding(new Insets(16));
        topRow.setStyle("""
                -fx-background-color: white;
                -fx-background-radius: 12;
                """);

        StackPane topCard = new StackPane(topRow);
        topCard.setPadding(new Insets(10));
        topCard.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 16, 0, 0, 4);");

        // --- BOTTOM: details grid (two column, heading left with blue background)
        detailsGrid = new GridPane();
        detailsGrid.setHgap(0);
        detailsGrid.setVgap(8);
        detailsGrid.setPadding(new Insets(12));
        ColumnConstraints colLeft = new ColumnConstraints(LEFT_HEADING_WIDTH);
        ColumnConstraints colRight = new ColumnConstraints();
        colRight.setHgrow(Priority.ALWAYS);
        detailsGrid.getColumnConstraints().addAll(colLeft, colRight);

        // helper to create heading cells and value cells (increased font sizes)
        Label headingFullName = createHeadingLabel("Full Name");
        fullNameValue = createValueLabel("(empty)");

        Label headingBsguid = createHeadingLabel("BSGUID");
        bsguidValue = createValueLabel("(empty)");

        Label headingDate = createHeadingLabel("Date");
        dateValue = createValueLabel("(--/--/----)");

        Label headingTime = createHeadingLabel("Time");
        timeValue = createValueLabel("(--:--)");

        Label headingLocation = createHeadingLabel("Location");
        locationValue = createValueLabel("(unknown)");

        Label headingEvent = createHeadingLabel("Event");
        eventValue = createValueLabel("(unknown)");

        // add rows
        addDetailRow(0, headingFullName, fullNameValue);
        addDetailRow(1, headingBsguid, bsguidValue);
        addDetailRow(2, headingDate, dateValue);
        addDetailRow(3, headingTime, timeValue);
        addDetailRow(4, headingLocation, locationValue);
        addDetailRow(5, headingEvent, eventValue);

        // Style the details card container similar to top card (white rectangular card
        // + shadow)
        StackPane detailsCardWrap = new StackPane(detailsGrid);
        detailsCardWrap.setPadding(new Insets(10));
        detailsCardWrap.setStyle("""
                -fx-background-color: white;
                -fx-background-radius: 12;
                -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 16, 0, 0, 4);
                """);

        // Put both cards in a VBox (top card then details card)
        root = new VBox(12, topCard, detailsCardWrap);
        root.setFillWidth(true);
        root.setPadding(new Insets(10));

        // Hook dynamic font scaling to width
        root.widthProperty().addListener((obs, oldW, newW) -> adjustFontSizes(newW.doubleValue()));
        // initial sizing
        adjustFontSizes(800); // reasonable default
    }

    private Label createHeadingLabel(String text) {
        Label l = new Label(text);
        l.setMinWidth(LEFT_HEADING_WIDTH);
        l.setMaxWidth(LEFT_HEADING_WIDTH);
        l.setAlignment(Pos.CENTER_LEFT);
        l.setPadding(new Insets(10, 12, 10, 12));
        // default font size via style; will be overridden by adjustFontSizes
        l.setStyle(String.format("""
                -fx-background-color: %s;
                -fx-text-fill: white;
                -fx-font-weight: 900;
                -fx-font-size: %.1fpx;
                """, HEADING_BG_COLOR, BASE_HEADING_FONT));
        headingLabels.add(l);
        return l;
    }

    private Label createValueLabel(String text) {
        Label v = new Label(text);
        v.setWrapText(true);
        v.setAlignment(Pos.CENTER_LEFT);
        v.setPadding(new Insets(10, 12, 10, 12));
        // unbold values (removed font-weight: 700) and default font-size via
        // BASE_VALUE_FONT
        v.setStyle(String.format("""
                -fx-text-fill: #263238;
                -fx-font-size: %.1fpx;
                """, BASE_VALUE_FONT));
        valueLabels.add(v);
        return v;
    }

    private void addDetailRow(int rowIndex, Label heading, Label value) {
        detailsGrid.add(heading, 0, rowIndex);
        detailsGrid.add(value, 1, rowIndex);
    }

    // ---------------- public API (safe to call from any thread) ----------------

    public Parent getView() {
        return root;
    }

    public Label getHeadline() {
        return headline;
    }

    public Label getUidLabel() {
        return uidLabel;
    }

    public void setLogo(Image img) {
        Platform.runLater(() -> {
            logoView.setImage(img);
            logoContainer.applyCss();
            logoContainer.layout();
        });
    }

    public void setLogo(String resourceOrFilePath) {
        if (resourceOrFilePath == null || resourceOrFilePath.isBlank()) {
            setLogo((Image) null);
            return;
        }

        Image img;
        try {
            java.net.URL url = getClass().getResource(resourceOrFilePath);
            if (url != null) {
                img = new Image(url.toExternalForm(), true);
            } else {
                img = new Image("file:" + resourceOrFilePath, true);
            }
        } catch (Exception e) {
            img = null;
        }
        setLogo(img);
    }

    public void updateFromCardCsv(String csv) {
        if (csv == null) {
            Platform.runLater(this::clearDetails);
            return;
        }

        // Normalize
        String normalized = csv.trim();

        String parsedFullName = null;
        String parsedBsguid = null;

        // Try key=value parsing if looks like that
        if (normalized.contains("=")) {
            // Split on commas or newlines
            String[] tokens = normalized.split("[,\\r\\n]+");
            Map<String, String> map = new HashMap<>();
            for (String t : tokens) {
                String[] kv = t.split("=", 2);
                if (kv.length == 2) {
                    String k = kv[0].trim();
                    String v = kv[1].trim();
                    map.put(k.toLowerCase(), v);
                }
            }
            if (map.containsKey("fullname"))
                parsedFullName = map.get("fullname");
            if (map.containsKey("bsguid"))
                parsedBsguid = map.get("bsguid");
            // also try common alternatives
            if (parsedFullName == null && map.containsKey("name"))
                parsedFullName = map.get("name");
            if (parsedBsguid == null && map.containsKey("id"))
                parsedBsguid = map.get("id");

            // Also support location/event embedded in card CSV (optional)
            if (map.containsKey("location")) {
                setLocation(map.get("location"));
            }
            if (map.containsKey("event")) {
                setEvent(map.get("event"));
            }
        }

        // Fallback: simple CSV positional parsing
        if (parsedFullName == null || parsedBsguid == null) {
            String[] parts = normalized.split(",");
            if (parts.length >= 1 && parsedFullName == null) {
                parsedFullName = parts[0].trim();
            }
            if (parts.length >= 2 && parsedBsguid == null) {
                parsedBsguid = parts[1].trim();
            }
            // if card CSV includes location/event in positions 3/4 we won't assume here
        }

        // Sanitize BSGUID (remove all whitespace)
        if (parsedBsguid != null) {
            parsedBsguid = parsedBsguid.replaceAll("\\s+", "");
        }

        final String finalFullName = parsedFullName == null || parsedFullName.isBlank() ? "(empty)"
                : parsedFullName;
        final String finalBsguid = parsedBsguid == null || parsedBsguid.isBlank() ? "(empty)" : parsedBsguid;

        // Set date/time to now
        final String nowDate = LocalDate.now().format(DATE_FMT);
        final String nowTime = LocalTime.now().format(TIME_FMT);

        // Update UI
        Platform.runLater(() -> {
            fullNameValue.setText(finalFullName);
            bsguidValue.setText(finalBsguid);
            dateValue.setText(nowDate);
            timeValue.setText(nowTime);
        });
    }

    public void updateFromCardMap(Map<String, String> cardFields) {
        if (cardFields == null) {
            Platform.runLater(this::clearDetails);
            return;
        }
        String fn = null;
        String id = null;
        for (Map.Entry<String, String> e : cardFields.entrySet()) {
            String k = e.getKey() == null ? "" : e.getKey().trim().toLowerCase();
            String v = e.getValue();
            if ("fullname".equals(k) || "name".equals(k))
                fn = v;
            if ("bsguid".equals(k) || "id".equals(k) || "uid".equals(k))
                id = v;
            if ("location".equals(k))
                setLocation(v);
            if ("event".equals(k))
                setEvent(v);
        }
        if (id != null)
            id = id.replaceAll("\\s+", "");
        final String finalFullName = fn == null || fn.isBlank() ? "(empty)" : fn;
        final String finalBsguid = id == null || id.isBlank() ? "(empty)" : id;
        final String nowDate = LocalDate.now().format(DATE_FMT);
        final String nowTime = LocalTime.now().format(TIME_FMT);
        Platform.runLater(() -> {
            fullNameValue.setText(finalFullName);
            bsguidValue.setText(finalBsguid);
            dateValue.setText(nowDate);
            timeValue.setText(nowTime);
        });
    }

    public void clearDetails() {
        Platform.runLater(() -> {
            fullNameValue.setText("(empty)");
            bsguidValue.setText("(empty)");
            dateValue.setText("(--/--/----)");
            timeValue.setText("(--:--)");
            // keep location/event intact if desired; earlier you asked to clear all —
            // adjust as needed
            locationValue.setText(locationValue.getText() == null ? "(unknown)" : locationValue.getText());
            eventValue.setText(eventValue.getText() == null ? "(unknown)" : eventValue.getText());
        });
    }

    public void setFullName(String text) {
        Platform.runLater(() -> fullNameValue.setText(text == null || text.isBlank() ? "(empty)" : text));
    }

    public void setBSGUID(String text) {
        String clean = text == null ? null : text.replaceAll("\\s+", "");
        Platform.runLater(() -> bsguidValue.setText(clean == null || clean.isBlank() ? "(empty)" : clean));
    }

    public void setDate(String text) {
        Platform.runLater(() -> dateValue.setText(text == null || text.isBlank() ? "(--/--/----)" : text));
    }

    public void setTime(String text) {
        Platform.runLater(() -> timeValue.setText(text == null || text.isBlank() ? "(--:--)" : text));
    }

    public void setLocation(String text) {
        locationText = (text == null || text.isBlank()) ? "(unknown)" : text;
        Platform.runLater(() -> locationValue.setText(locationText));
    }

    public void setEvent(String text) {
        eventText = (text == null || text.isBlank()) ? "(unknown)" : text;
        Platform.runLater(() -> eventValue.setText(eventText));
    }
    
    public String getLocationText() {
        return locationText;
    }
    public String getEventText() {
        return eventText;
    }

    public void loadLocationEventFromFile(String path, boolean classpath) {
        if (path == null || path.isBlank()) {
            setLocation(null);
            setEvent(null);
            return;
        }

        String loc = null;
        String ev = null;

        if (classpath) {
            try (java.io.InputStream in = getClass().getResourceAsStream(path)) {
                if (in == null) {
                    // resource not found
                    System.err.println("AttendanceView: resource not found: " + path);
                } else {
                    try (java.io.BufferedReader br = new java.io.BufferedReader(
                            new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))) {
                        // read first two non-empty lines
                        String line;
                        while ((line = br.readLine()) != null && (loc == null || ev == null)) {
                            if (line == null)
                                break;
                            line = line.trim();
                            if (line.isEmpty())
                                continue;
                            if (loc == null)
                                loc = line;
                            else if (ev == null)
                                ev = line;
                        }
                    }
                }
            } catch (Exception ex) {
                System.err.println("AttendanceView: failed to read classpath resource " + path + " : " + ex);
            }
        } else {
            // load from normal filesystem path
            java.io.File f = new java.io.File(path);
            if (!f.exists() || !f.isFile()) {
                System.err.println("AttendanceView: file not found: " + path);
            } else {
                try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(
                        new java.io.FileInputStream(f), java.nio.charset.StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null && (loc == null || ev == null)) {
                        if (line == null)
                            break;
                        line = line.trim();
                        if (line.isEmpty())
                            continue;
                        if (loc == null)
                            loc = line;
                        else if (ev == null)
                            ev = line;
                    }
                } catch (Exception ex) {
                    System.err.println("AttendanceView: failed to read file " + path + " : " + ex);
                }
            }
        }

        // apply results to UI (use default placeholders if missing)
        final String finalLoc = (loc == null || loc.isBlank()) ? "(unknown)" : loc;
        final String finalEv = (ev == null || ev.isBlank()) ? "(unknown)" : ev;

        Platform.runLater(() -> {
            setLocation(finalLoc);
            setEvent(finalEv);
        });
    }

    // ---------------- Dynamic font sizing ----------------
    private void adjustFontSizes(double availableWidth) {
        // Decide a scale base on a baseline width (600 is chosen as a nice breakpoint)
        double baseWidth = 600.0;
        double scale = Math.max(0.6, Math.min(1.6, availableWidth / baseWidth));

        double headingSize = clamp(BASE_HEADING_FONT * scale, MIN_HEADING_FONT, MAX_HEADING_FONT);
        double valueSize = clamp(BASE_VALUE_FONT * scale, MIN_VALUE_FONT, MAX_VALUE_FONT);

        final String headingStyleTemplate = String.format(
                "-fx-background-color: %s; -fx-text-fill: white; -fx-font-weight: 900; -fx-font-size: %.1fpx;",
                HEADING_BG_COLOR, headingSize);
        final String valueStyleTemplate = String.format("-fx-text-fill: #263238; -fx-font-size: %.1fpx;", valueSize);

        // apply styles on FX thread
        Platform.runLater(() -> {
            for (Label h : headingLabels) {
                // preserve padding/alignment etc. by only setting style; this replaces previous
                // inline style
                h.setStyle(headingStyleTemplate);
            }
            for (Label v : valueLabels) {
                v.setStyle(valueStyleTemplate);
            }
        });
    }

    private static double clamp(double v, double min, double max) {
        if (v < min)
            return min;
        if (v > max)
            return max;
        return v;
    }
}
