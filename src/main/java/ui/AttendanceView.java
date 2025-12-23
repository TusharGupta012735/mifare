package ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import nfc.SmartMifareReader;
import service.AttendanceService.AttendanceEvent;

import controller.AttendanceController;

public class AttendanceView {

    private static AttendanceEvent LAST_EVENT = null;
    private static String LAST_LOCATION = null;

    private static final double MAX_LOGO_WIDTH = 200;
    private static final double LEFT_HEADING_WIDTH = 130;
    private static final String HEADING_BG_COLOR = "#1c56aeff";

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
    private final ComboBox<String> locationCombo;
    private ComboBox<AttendanceEvent> eventCombo;

    // Keep references for dynamic font updates
    private final List<Label> headingLabels = new ArrayList<>();
    private final List<Label> valueLabels = new ArrayList<>();

    // Formatters
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Replace existing acceptReadResult(...) with this improved version
    public void acceptReadResult(SmartMifareReader.ReadResult rr) {
        if (rr == null) {
            Platform.runLater(this::clearDetails);
            return;
        }

        // 1) Update UID display immediately
        final String uidText = (rr.uid == null || rr.uid.isBlank()) ? "(no uid)" : rr.uid;
        Platform.runLater(() -> getUidLabel().setText("UID: " + uidText));

        // 2) Try to extract a payload string from common ReadResult fields (robust)
        String csv = null;

        // reflection: field 'data' or 'text'
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

        // reflection: getter getData() / getText()
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

        // If still blank, inspect toString() (often contains "data=..." or "text=...")
        if ((csv == null || csv.isBlank())) {
            try {
                String s = rr.toString();
                if (s != null && !s.isBlank()) {
                    // Try to extract data=... or text=... inside the toString output
                    // Accept quoted or unquoted values; stop at comma or closing brace.
                    java.util.regex.Pattern p = java.util.regex.Pattern
                            .compile("(?:\\bdata\\b|\\btext\\b)\\s*=\\s*(\"([^\"]*)\"|([^,}\\]]+))",
                                    java.util.regex.Pattern.CASE_INSENSITIVE);
                    java.util.regex.Matcher m = p.matcher(s);
                    if (m.find()) {
                        String quoted = m.group(2);
                        String unquoted = m.group(3);
                        csv = (quoted != null) ? quoted : (unquoted != null ? unquoted.trim() : null);
                    } else {
                        // If no explicit data= found, but the toString looks like 'ReadResult{...}'
                        // try to extract the first quoted block or the first key=value payload inside
                        // braces.
                        // First try anything inside braces
                        int open = s.indexOf('{');
                        int close = s.lastIndexOf('}');
                        if (open >= 0 && close > open) {
                            String inner = s.substring(open + 1, close);
                            // If inner contains an equals sign, treat it as key=value pairs and let
                            // updateFromCardCsv parse
                            if (inner.contains("=")) {
                                csv = inner.trim();
                            } else {
                                // otherwise try to find a quoted substring
                                java.util.regex.Matcher q = java.util.regex.Pattern.compile("\"([^\"]+)\"")
                                        .matcher(inner);
                                if (q.find()) {
                                    csv = q.group(1);
                                } else {
                                    // as a last resort, take the whole inner content if short
                                    if (inner.length() < 512)
                                        csv = inner.trim();
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        // 3) If we got something that looks useful, parse it into fields; otherwise
        // just update time/date
        if (csv != null && !csv.isBlank()) {
            // guard against accidental "__CSV__" like keys being used as the name
            if ("__CSV__".equalsIgnoreCase(csv.trim())) {
                // ignore as payload
                csv = null;
            }
        }

        if (csv != null && !csv.isBlank()) {
            updateFromCardCsv(csv);
        } else {
            // still update date/time even if we didn't get card payload
            final String nowDate = java.time.LocalDate.now().format(DATE_FMT);
            final String nowTime = java.time.LocalTime.now().format(TIME_FMT);
            Platform.runLater(() -> {
                setDate(nowDate);
                setTime(nowTime);
            });
        }
    }

    // ---------------- Constructor / UI ----------------
    public AttendanceView() {
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

        Label headingLocation = createHeadingLabel("Location");
        locationCombo = new ComboBox<>();
        locationCombo.setPromptText("Select room");
        locationCombo.setPrefWidth(220); // default preferred width
        locationCombo.setMinWidth(140); // don't shrink too small
        locationCombo.setMaxWidth(320);
        // initial style same as value labels — will be updated by adjustFontSizes
        locationCombo.setStyle(String.format("-fx-font-size: %.1fpx;", BASE_VALUE_FONT));
        locationCombo.setDisable(true);

        eventCombo = new ComboBox<>();
        eventCombo.setPromptText("Select Event");
        eventCombo.setPrefWidth(240);

        Label headingFullName = createHeadingLabel("Full Name");
        fullNameValue = createValueLabel("(empty)");

        Label headingBsguid = createHeadingLabel("BSGUID");
        bsguidValue = createValueLabel("(empty)");

        Label headingDate = createHeadingLabel("Date");
        dateValue = createValueLabel("(--/--/----)");

        Label headingTime = createHeadingLabel("Time");
        timeValue = createValueLabel("(--:--)");

        Label headingEvent = createHeadingLabel("Event");

        addDetailRowNode(0, headingEvent, eventCombo);
        addDetailRowNode(1, headingLocation, locationCombo);
        addDetailRow(2, headingFullName, fullNameValue);
        addDetailRow(3, headingBsguid, bsguidValue);
        addDetailRow(4, headingDate, dateValue);
        addDetailRow(5, headingTime, timeValue);

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

        // When user picks a location from combo, update the stored locationText
        locationCombo.setOnAction(evt -> {
            String sel = locationCombo.getValue();
            if (sel == null || sel.isBlank()) {
                locationText = "(unknown)";
                LAST_LOCATION = null;
            } else {
                locationText = sel.trim();
                LAST_LOCATION = locationText;
            }
        });

        eventCombo.valueProperty().addListener((obs, oldEv, newEv) -> {

            LAST_EVENT = newEv; // ✅ remember event
            eventText = (newEv == null) ? "(unknown)" : newEv.name;

            locationCombo.getItems().clear();
            locationCombo.setValue(null);
            locationCombo.setDisable(true);

            if (newEv == null || newEv.locations.isEmpty()) {
                return;
            }

            locationCombo.getItems().addAll(newEv.locations);
            locationCombo.setDisable(false);

            if (LAST_LOCATION != null && newEv.locations.contains(LAST_LOCATION)) {
                locationCombo.setValue(LAST_LOCATION);
                locationText = LAST_LOCATION;
            }
        });

    }

    public void loadEventsAndBindLocations() {

        new Thread(() -> {

            AttendanceController controller = new AttendanceController();
            List<AttendanceEvent> events = controller.getAllEvents();

            Platform.runLater(() -> {

                eventCombo.getItems().clear();
                eventCombo.getItems().addAll(events);

                locationCombo.getItems().clear();
                locationCombo.setDisable(true);

                // ✅ restore previously selected event
                if (LAST_EVENT != null) {
                    for (AttendanceEvent ev : events) {
                        if (ev.id == LAST_EVENT.id) {
                            eventCombo.setValue(ev);
                            break;
                        }
                    }
                }
            });

        }, "attendance-load-events").start();
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
        // unbold values and default font-size via BASE_VALUE_FONT
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

    private void addDetailRowNode(int rowIndex, Label heading, javafx.scene.Node node) {
        detailsGrid.add(heading, 0, rowIndex);
        detailsGrid.add(node, 1, rowIndex);
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

    // Replace existing updateFromCardCsv(...) with this improved version
    public void updateFromCardCsv(String csv) {
        if (csv == null) {
            Platform.runLater(this::clearDetails);
            return;
        }

        // Trim and normalize whitespace/newlines
        String normalized = csv.trim();

        // If the input looks like a wrapper "ReadResult{...}" extract inner part
        if (normalized.startsWith("ReadResult") && normalized.contains("{") && normalized.contains("}")) {
            int open = normalized.indexOf('{');
            int close = normalized.lastIndexOf('}');
            if (open >= 0 && close > open) {
                normalized = normalized.substring(open + 1, close).trim();
            }
        }

        // If the string looks like JSON (starts with {) try simple JSON-ish parse (not
        // full JSON lib)
        if (normalized.startsWith("{") && normalized.endsWith("}")) {
            // remove braces
            String inner = normalized.substring(1, normalized.length() - 1);
            Map<String, String> map = new LinkedHashMap<>();
            for (String token : inner.split("[,\\n\\r]+")) {
                if (!token.contains("=") && token.contains(":")) {
                    // allow "key":"value" style
                    String[] kv = token.split(":", 2);
                    if (kv.length == 2) {
                        String k = kv[0].replaceAll("[\"{}\\[\\]]", "").trim();
                        String v = kv[1].replaceAll("[\"{}\\[\\]]", "").trim();
                        map.put(k.toLowerCase(), v);
                    }
                } else if (token.contains("=")) {
                    String[] kv = token.split("=", 2);
                    String k = kv[0].replaceAll("[\"{}\\[\\]]", "").trim();
                    String v = kv[1].replaceAll("[\"{}\\[\\]]", "").trim();
                    map.put(k.toLowerCase(), v);
                }
            }
            // Hand off to updateFromCardMap which sets appropriate UI
            if (!map.isEmpty()) {
                updateFromCardMap(map);
                return;
            }
        }

        // If it contains key=value pairs separated by commas/newlines, parse into map
        if (normalized.contains("=")) {
            String[] tokens = normalized.split("[,\\r\\n]+");
            Map<String, String> map = new HashMap<>();
            for (String t : tokens) {
                String[] kv = t.split("=", 2);
                if (kv.length == 2) {
                    String k = kv[0].trim();
                    String v = kv[1].trim();
                    // strip surrounding quotes if present
                    if (v.length() >= 2 && ((v.startsWith("\"") && v.endsWith("\""))
                            || (v.startsWith("'") && v.endsWith("'")))) {
                        v = v.substring(1, v.length() - 1);
                    }
                    map.put(k.toLowerCase(), v);
                }
            }
            if (!map.isEmpty()) {
                updateFromCardMap(map);
                return;
            }
        }

        // If it looks like quoted string "first,last,..." extract inside quotes
        java.util.regex.Matcher quoted = java.util.regex.Pattern.compile("^\\s*\"([^\"]+)\"\\s*$")
                .matcher(normalized);
        if (quoted.find()) {
            normalized = quoted.group(1);
        }

        // Now fallback to simple positional CSV parsing (first -> fullname, second ->
        // bsguid)
        String[] parts = normalized.split("[,\\r\\n]+");
        String parsedFullName = null;
        String parsedBsguid = null;

        if (parts.length >= 1) {
            parsedFullName = parts[0].trim();
        }
        if (parts.length >= 2) {
            parsedBsguid = parts[1].trim();
        }

        // sanitize
        if (parsedBsguid != null) {
            parsedBsguid = parsedBsguid.replaceAll("\\s+", "");
        }

        final String finalFullName = (parsedFullName == null || parsedFullName.isBlank()) ? "(empty)"
                : parsedFullName;
        final String finalBsguid = (parsedBsguid == null || parsedBsguid.isBlank()) ? "(empty)" : parsedBsguid;
        final String nowDate = LocalDate.now().format(DATE_FMT);
        final String nowTime = LocalTime.now().format(TIME_FMT);

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

            if ("fullname".equals(k) || "name".equals(k)) {
                fn = v;
            }

            if ("bsguid".equals(k) || "id".equals(k) || "uid".equals(k)) {
                id = v;
            }
        }

        if (id != null) {
            id = id.replaceAll("\\s+", "");
        }

        final String finalFullName = (fn == null || fn.isBlank()) ? "(empty)" : fn;
        final String finalBsguid = (id == null || id.isBlank()) ? "(empty)" : id;
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
        // Do not clear location selection (persist it)
        Platform.runLater(() -> {
            fullNameValue.setText("(empty)");
            bsguidValue.setText("(empty)");
            dateValue.setText("(--/--/----)");
            timeValue.setText("(--:--)");
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

    /**
     * Set the location selection. If the combo has items and the value matches one,
     * that item will be selected. If it doesn't match, it will be appended to the
     * list and selected.
     */
    public void setLocation(String text) {
        final String resolved = (text == null || text.isBlank()) ? "(unknown)" : text.trim();
        locationText = resolved;
        Platform.runLater(() -> {
            // try to match ignoring whitespace
            if (resolved.equals("(unknown)")) {
                // don't select anything
                return;
            }
            Optional<String> match = locationCombo.getItems().stream()
                    .filter(item -> item != null && item.trim().equalsIgnoreCase(resolved.trim())).findFirst();
            if (match.isPresent()) {
                locationCombo.setValue(match.get());
            } else {
                // add new item and select it
                locationCombo.getItems().add(resolved);
                locationCombo.setValue(resolved);
            }
        });
    }

    public String getLocationText() {
        // prefer Combo selection on FX thread; use volatile fallback otherwise
        try {
            if (Platform.isFxApplicationThread()) {
                String v = locationCombo.getValue();
                return (v == null || v.isBlank()) ? locationText : v;
            } else {
                return locationText;
            }
        } catch (Throwable t) {
            return locationText;
        }
    }

    public String getEventText() {
        AttendanceEvent ev = eventCombo.getValue();
        return ev == null ? "(unknown)" : ev.name;
    }

    // ---------------- Dynamic font sizing ----------------
    private void adjustFontSizes(double availableWidth) {
        // Decide a scale based on a baseline width (600 is chosen as a nice breakpoint)
        double baseWidth = 600.0;
        double scale = Math.max(0.6, Math.min(1.6, availableWidth / baseWidth));

        double headingSize = clamp(BASE_HEADING_FONT * scale, MIN_HEADING_FONT, MAX_HEADING_FONT);
        double valueSize = clamp(BASE_VALUE_FONT * scale, MIN_VALUE_FONT, MAX_VALUE_FONT);

        final String headingStyleTemplate = String.format(
                "-fx-background-color: %s; -fx-text-fill: white; -fx-font-weight: 900; -fx-font-size: %.1fpx;",
                HEADING_BG_COLOR, headingSize);
        final String valueStyleTemplate = String.format("-fx-text-fill: #263238; -fx-font-size: %.1fpx;", valueSize);
        final String comboStyle = String.format("-fx-font-size: %.1fpx;", valueSize);

        // apply styles on FX thread
        Platform.runLater(() -> {
            for (Label h : headingLabels) {
                h.setStyle(headingStyleTemplate);
            }
            for (Label v : valueLabels) {
                v.setStyle(valueStyleTemplate);
            }
            // style combo too
            locationCombo.setStyle(comboStyle);
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
