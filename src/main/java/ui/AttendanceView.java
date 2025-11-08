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
import java.util.HashMap;
import java.util.Map;

public class AttendanceView {

    private static final double MAX_LOGO_WIDTH = 180; // cap logo width
    private static final double LEFT_HEADING_WIDTH = 130; // width for heading cells in the details card
    private static final String HEADING_BG_COLOR = "#0D47A1"; // blue used for headings

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

    // Formatters
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

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
    }

    private Label createHeadingLabel(String text) {
        Label l = new Label(text);
        l.setMinWidth(LEFT_HEADING_WIDTH);
        l.setMaxWidth(LEFT_HEADING_WIDTH);
        l.setAlignment(Pos.CENTER_LEFT);
        l.setPadding(new Insets(10, 12, 10, 12));
        l.setStyle(String.format("""
                -fx-background-color: %s;
                -fx-text-fill: white;
                -fx-font-weight: 900;
                -fx-font-size: 15px;
                """, HEADING_BG_COLOR));
        return l;
    }

    private Label createValueLabel(String text) {
        Label v = new Label(text);
        v.setWrapText(true);
        v.setAlignment(Pos.CENTER_LEFT);
        v.setPadding(new Insets(10, 12, 10, 12));
        v.setStyle("""
                -fx-text-fill: #263238;
                -fx-font-size: 16px;
                -fx-font-weight: 700;
                """);
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
            locationValue.setText("(unknown)");
            eventValue.setText("(unknown)");
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
        Platform.runLater(() -> locationValue.setText(text == null || text.isBlank() ? "(unknown)" : text));
    }

    public void setEvent(String text) {
        Platform.runLater(() -> eventValue.setText(text == null || text.isBlank() ? "(unknown)" : text));
    }

    public void loadLocationEventFromFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            setLocation(null);
            setEvent(null);
            return;
        }

        File f = new File(filePath);
        if (!f.exists() || !f.isFile()) {
            setLocation(null);
            setEvent(null);
            return;
        }

        String loc = null;
        String ev = null;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            loc = br.readLine();
            ev = br.readLine();
        } catch (IOException e) {
            loc = null;
            ev = null;
        }
        setLocation(loc);
        setEvent(ev);
    }
}
