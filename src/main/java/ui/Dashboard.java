package ui;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import cloudSync.CloudSync;
import controller.EventFormController;
import nfc.SmartMifareReader;
import ui.pages.EventFormPage;
import util.DebugLog;
import javafx.application.Platform;
import javafx.scene.Parent;

import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import javafx.scene.Node;

public class Dashboard extends BorderPane {

    public void initialize() {
        CloudSync.startBackgroundSync();
    }

    private final ScrollPane scrollPane = new ScrollPane();

    // >>> Attendance poller state (runs ONLY on Attendance tab)
    private volatile Thread attendancePollerThread = null;
    private final AtomicBoolean attendancePollerRunning = new AtomicBoolean(false);
    private final AtomicBoolean onAttendanceTab = new AtomicBoolean(false);

    private static final String LOGO_PATH = "/logo-removebg-preview.png";

    private AttendanceView attendanceView;

    // Make inputsw/headings larger & cleaner without touching EntryForm code
    private void prettifyForm(Parent root) {
        // TextFields
        for (Node n : root.lookupAll(".text-field")) {
            n.setStyle("""
                        -fx-font-size: 15px;
                        -fx-padding: 10 12;
                        -fx-background-color: white;
                        -fx-background-radius: 8;
                        -fx-border-color: #BDBDBD;
                        -fx-border-radius: 8;
                    """);
            if (n instanceof Region r) {
                r.setMinHeight(36);
            }
        }

        // ComboBoxes
        for (Node n : root.lookupAll(".combo-box")) {
            n.setStyle("""
                        -fx-font-size: 15px;
                        -fx-padding: 8 10;
                        -fx-background-color: white;
                        -fx-background-radius: 8;
                        -fx-border-color: #BDBDBD;
                        -fx-border-radius: 8;
                    """);
            if (n instanceof ComboBox<?> cb) {
                cb.setVisibleRowCount(12);
            }
            if (n instanceof Region r) {
                r.setMinHeight(36);
            }
        }

        // DatePickers
        for (Node n : root.lookupAll(".date-picker")) {
            n.setStyle("""
                        -fx-font-size: 15px;
                        -fx-padding: 8 10;
                        -fx-background-color: white;
                        -fx-background-radius: 8;
                        -fx-border-color: #BDBDBD;
                        -fx-border-radius: 8;
                    """);
            if (n instanceof Region r) {
                r.setMinHeight(36);
            }
        }

        // TextAreas
        for (Node n : root.lookupAll(".text-area")) {
            n.setStyle("""
                        -fx-font-size: 15px;
                        -fx-padding: 10 12;
                        -fx-background-color: white;
                        -fx-background-radius: 8;
                        -fx-border-color: #BDBDBD;
                        -fx-border-radius: 8;
                    """);
        }

        // Labels (only inside form – skip the big status banner)
        for (Node n : root.lookupAll(".label")) {
            if (n.getStyleClass().contains("status-banner") || "statusBanner".equals(n.getId())) {
                continue; // don't override the banner styling
            }
            n.setStyle("-fx-font-size: 14px; -fx-text-fill: #263238;");
        }

        // Buttons (only inside the batch form container)
        for (Node n : root.lookupAll(".button")) {
            n.setStyle("""
                        -fx-background-color: #1976D2;
                        -fx-text-fill: white;
                        -fx-font-weight: bold;
                        -fx-font-size: 14px;
                        -fx-background-radius: 8;
                        -fx-padding: 8 14;
                        -fx-cursor: hand;
                    """);
            n.setOnMouseEntered(e -> n.setStyle("""
                        -fx-background-color: #2196F3;
                        -fx-text-fill: white;
                        -fx-font-weight: bold;
                        -fx-font-size: 14px;
                        -fx-background-radius: 8;
                        -fx-padding: 8 14;
                        -fx-cursor: hand;
                    """));
            n.setOnMouseExited(e -> n.setStyle("""
                        -fx-background-color: #1976D2;
                        -fx-text-fill: white;
                        -fx-font-weight: bold;
                        -fx-font-size: 14px;
                        -fx-background-radius: 8;
                        -fx-padding: 8 14;
                        -fx-cursor: hand;
                    """));
        }

        if (root instanceof Region r) {
            r.setStyle("-fx-background-color: transparent;");
        }
    }

    private final StackPane contentArea = new StackPane();

    public Dashboard() {

        try {
            DebugLog.d("Active DB path: %s", db.AccessDb.getActiveDbPath());
        } catch (SQLException e) {
            e.printStackTrace();
        }
        // --- Buttons ---
        Button attendanceBtn = new Button("Attendance");
        Button entryFormBtn = new Button("Entry Form");
        Button batchBtn = new Button("Batch (Filter)");
        Button reportBtn = new Button("Report");
        Button importBtn = new Button("Import Excel");
        Button exportParticipantsBtn = new Button("Export Data");
        Button addEventBtn = new Button("Add Event");

        // --- Common Button Style ---
        String btnStyle = """
                    -fx-background-color: #1976d2;
                    -fx-text-fill: white;
                    -fx-font-weight: bold;
                    -fx-font-size: 15px;
                    -fx-background-radius: 8;
                    -fx-padding: 12 20;
                    -fx-cursor: hand;
                    -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 4, 0, 0, 1);
                """;

        String hoverStyle = """
                    -fx-background-color: #2196f3;
                    -fx-text-fill: white;
                    -fx-font-weight: bold;
                    -fx-font-size: 15px;
                    -fx-background-radius: 8;
                    -fx-padding: 12 20;
                    -fx-cursor: hand;
                    -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 6, 0, 0, 2);
                """;

        for (Button btn : new Button[] { attendanceBtn, entryFormBtn, batchBtn, reportBtn, importBtn,
                exportParticipantsBtn, addEventBtn }) {
            btn.setStyle(btnStyle);
            btn.setOnMouseEntered(e -> btn.setStyle(hoverStyle));
            btn.setOnMouseExited(e -> btn.setStyle(btnStyle));
        }

        // --- Navbar Layout (added Import Excel at the end) ---
        HBox navBar = new HBox(20, attendanceBtn, entryFormBtn, batchBtn, reportBtn, importBtn, exportParticipantsBtn,
                addEventBtn);
        navBar.setPadding(new Insets(15, 20, 15, 20));
        navBar.setStyle(
                "-fx-background-color: linear-gradient(to bottom, #1565c0, #0d47a1); -fx-alignment: center; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 8, 0, 0, 2);");

        for (Button btn : new Button[] { attendanceBtn, entryFormBtn, batchBtn, reportBtn, importBtn,
                exportParticipantsBtn, addEventBtn }) {
            HBox.setHgrow(btn, Priority.ALWAYS);
            btn.setMaxWidth(Double.MAX_VALUE);
        }

        // --- Default Content: ATTENDANCE ---
        contentArea.setPadding(new Insets(20));
        attendanceView = new AttendanceView();
        setContent(attendanceView.getView());
        attendanceView.setLogo(LOGO_PATH);
        attendanceView.loadEventsAndBindLocations();

        onAttendanceTab.set(true);
        startAttendancePoller();

        // --- Add Components to Layout ---
        setTop(navBar);

        // Wrap the content area in the scroll pane so all pages become scrollable when
        // needed
        scrollPane.setContent(contentArea);
        scrollPane.setFitToWidth(true); // pages will stretch to viewport width (nice wrapping)
        scrollPane.setFitToHeight(false); // allow vertical scrolling
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setPannable(true); // enable touch / drag panning
        scrollPane.setPadding(new Insets(6));

        // Keep the contentArea width in sync with the viewport so children wrap
        // correctly.
        // When the viewport changes size update contentArea's maxWidth so layout uses
        // it.
        scrollPane.viewportBoundsProperty().addListener((obs, oldB, newB) -> {
            if (newB != null) {
                double vw = Math.max(0, newB.getWidth() - 2); // small guard padding
                // allow contentArea to grow up to viewport width
                contentArea.setMaxWidth(vw);
                // also prefer to fill horizontally
                contentArea.setPrefWidth(vw);
            }
        });

        setCenter(scrollPane);

        // --- Actions ---
        attendanceBtn.setOnAction(e -> {

            // 1) request we are no longer on attendance tab (helps poller exit quickly)
            onAttendanceTab.set(false);

            // 2) stop existing poller (if any) and wait a short time for it to die
            try {
                DebugLog.d("Stopping existing attendance poller (if running)...");
                stopAttendancePoller(); // should request stop and attempt to cancel blocking read

                Thread t = attendancePollerThread;
                if (t != null && t.isAlive()) {
                    DebugLog.d("Waiting for poller thread to terminate (join up to 2000ms)...");
                    try {
                        t.join(2000); // wait up to 2s for previous poller to exit
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        DebugLog.d("Interrupted while waiting for poller thread to join");
                    }
                    DebugLog.d("Poller thread alive after join=%b", t.isAlive());
                }
            } catch (Throwable stopEx) {
                DebugLog.ex(stopEx, "Error while stopping previous attendance poller");
            }

            // 3) mark we are on attendance tab and create fresh AttendanceView
            onAttendanceTab.set(true);
            try {
                DebugLog.d("Creating new AttendanceView instance");
                attendanceView = new AttendanceView(); // create new instance
                attendanceView.setLogo(LOGO_PATH);
                setContent(attendanceView.getView());

                attendanceView.loadEventsAndBindLocations();

                DebugLog.d("Starting attendance poller");
                startAttendancePoller();
                DebugLog.d("Attendance tab ready (poller started)");
            } catch (Throwable initEx) {
                DebugLog.ex(initEx, "Failed to initialize Attendance tab");
                // best-effort fallback: still try to start poller if partial init succeeded
                try {
                    if (!attendancePollerRunning.get()) {
                        startAttendancePoller();
                    }
                } catch (Throwable t2) {
                    DebugLog.ex(t2, "Failed to start poller after initialization failure");
                }
            }
        });

        // --- Entry Form ---
        entryFormBtn.setOnAction(e -> {
            leaveAttendance(); // stop attendance poller

            Parent form = EntryForm.create((formData, done) -> {
                // Do NFC + DB work off the UI thread
                Thread t = new Thread(() -> {
                    String uid = null;
                    try {
                        // Build CSV if not present
                        String csv = formData.get("__CSV__");
                        if (csv == null) {
                            csv = formData.values().stream()
                                    .map(v -> v == null ? "" : v.trim())
                                    .collect(java.util.stream.Collectors.joining(","));
                        }

                        // Make sure only one NFC op runs at a time
                        EntryForm.setNfcBusy(true);

                        // 1) Wait for card (blocks until present)
                        nfc.SmartMifareReader.ReadResult present;
                        try {
                            present = nfc.SmartMifareReader.readUIDWithData(0); // 0 = infinite wait
                        } catch (Throwable ex) {
                            present = null;
                        }
                        if (present == null || present.uid == null || present.uid.isBlank()) {
                            Platform.runLater(() -> {
                                Alert a = new Alert(Alert.AlertType.ERROR,
                                        "No card detected. Please present a card and try again.",
                                        ButtonType.OK);
                                a.setHeaderText(null);
                                a.showAndWait();
                            });
                            return;
                        }
                        uid = present.uid;

                        // 2) Write to card
                        nfc.SmartMifareWriter.WriteResult wr;
                        try {
                            wr = nfc.SmartMifareWriter.writeText(csv);
                        } catch (Throwable ex) {
                            wr = null;
                        }
                        if (wr == null) {
                            Platform.runLater(() -> {
                                Alert a = new Alert(Alert.AlertType.ERROR,
                                        "Writing to the card failed.",
                                        ButtonType.OK);
                                a.setHeaderText(null);
                                a.showAndWait();
                            });
                            return;
                        }

                        // 3) Quick verify while card still present (UID match is enough)
                        nfc.SmartMifareReader.ReadResult verify = null;
                        try {
                            verify = nfc.SmartMifareReader.readUIDWithData(800);
                        } catch (Throwable ignore) {
                        }
                        if (verify == null || verify.uid == null || !uid.equalsIgnoreCase(verify.uid)) {
                            Platform.runLater(() -> {
                                Alert a = new Alert(Alert.AlertType.ERROR,
                                        "Could not verify the card after writing.",
                                        ButtonType.OK);
                                a.setHeaderText(null);
                                a.showAndWait();
                            });
                            return;
                        }

                        // 4) DB insert (your AccessDb is already guarded to only mark T when CardUID is
                        // non-empty)
                        try {
                            db.AccessDb.insertAttendee(formData, uid);
                        } catch (Exception dbEx) {
                            final String msg = dbEx.getMessage() == null ? dbEx.toString() : dbEx.getMessage();
                            Platform.runLater(() -> {
                                Alert a = new Alert(Alert.AlertType.ERROR,
                                        "DB insert failed: " + msg,
                                        ButtonType.OK);
                                a.setHeaderText(null);
                                a.showAndWait();
                            });
                            return;
                        }

                        // Success toast
                        final String uidFinal = uid;
                        final String name = formData.getOrDefault("FullName", "(no name)");
                        Platform.runLater(() -> {
                            Alert ok = new Alert(Alert.AlertType.INFORMATION,
                                    "Saved: " + name + " — UID " + uidFinal,
                                    ButtonType.OK);
                            ok.setHeaderText(null);
                            ok.showAndWait();
                        });

                    } finally {
                        EntryForm.setNfcBusy(false);
                        if (done != null)
                            done.run();
                    }
                }, "entryform-write-thread");

                t.setDaemon(true);
                t.start();
            });

            setContent(form);
        });

        // Batch (Filter)
        batchBtn.setOnAction(e -> {
            leaveAttendance();

            List<Map<String, String>> rows = BatchFilterDialog.showAndFetch(
                    this.getScene() == null ? null : this.getScene().getWindow());
            if (rows == null || rows.isEmpty())
                return;

            // Top banner (centered)
            int total = rows.size();
            Label banner = new Label("Ready for records — " + total + " selected");
            banner.setStyle("""
                        -fx-font-size: 22px;
                        -fx-font-weight: 900;
                        -fx-text-fill: #0D47A1;
                    """);
            HBox bannerWrap = new HBox(banner);
            bannerWrap.setAlignment(Pos.CENTER);
            bannerWrap.setPadding(new Insets(8, 0, 12, 0));

            java.util.concurrent.atomic.AtomicInteger processed = new java.util.concurrent.atomic.AtomicInteger(0);

            Parent batch = EntryForm.createBatch((formData, finish) -> {
                Thread t = new Thread(() -> {
                    String uid = null;
                    try {
                        // Build CSV if missing
                        String textToWrite = formData.get("__CSV__");
                        if (textToWrite == null) {
                            textToWrite = formData.values().stream()
                                    .map(v -> v == null ? "" : v.trim())
                                    .collect(java.util.stream.Collectors.joining(","));
                        }

                        // STRICT, SEQUENTIAL, FAIL-FAST
                        EntryForm.setNfcBusy(true);

                        // 1) Wait for card & read UID (block until present)
                        nfc.SmartMifareReader.ReadResult present = null;
                        try {
                            present = nfc.SmartMifareReader.readUIDWithData(0); // infinite wait
                        } catch (Throwable ignored) {
                        }
                        if (present == null || present.uid == null || present.uid.isBlank()) {
                            Platform.runLater(() -> {
                                Alert alert = new Alert(Alert.AlertType.ERROR,
                                        "No card detected. Please present a card and try again.",
                                        ButtonType.OK);
                                alert.setHeaderText(null);
                                alert.showAndWait();
                            });
                            finish.accept(false); // stay on same record
                            return;
                        }
                        uid = present.uid;

                        // 2) WRITE to card
                        nfc.SmartMifareWriter.WriteResult wr;
                        try {
                            wr = nfc.SmartMifareWriter.writeText(textToWrite);
                        } catch (Throwable ex) {
                            wr = null;
                        }
                        if (wr == null) {
                            Platform.runLater(() -> {
                                Alert alert = new Alert(Alert.AlertType.ERROR,
                                        "Writing to the card failed. Nothing was saved to the database.",
                                        ButtonType.OK);
                                alert.setHeaderText(null);
                                alert.showAndWait();
                            });
                            finish.accept(false);
                            return;
                        }

                        // 3) QUICK VERIFY (UID should still match while card is present)
                        nfc.SmartMifareReader.ReadResult verify = null;
                        try {
                            verify = nfc.SmartMifareReader.readUIDWithData(800);
                        } catch (Throwable ignored) {
                        }
                        if (verify == null || verify.uid == null || verify.uid.isBlank()
                                || !uid.equalsIgnoreCase(verify.uid)) {
                            Platform.runLater(() -> {
                                Alert alert = new Alert(Alert.AlertType.ERROR,
                                        "Could not verify the card after writing. Nothing was saved to the database.",
                                        ButtonType.OK);
                                alert.setHeaderText(null);
                                alert.showAndWait();
                            });
                            finish.accept(false);
                            return;
                        }

                        // 4) DB INSERT — only after successful read + write + verify
                        try {
                            db.AccessDb.insertAttendee(formData, uid);
                        } catch (Exception dbEx) {
                            final String msg = dbEx.getMessage() == null ? dbEx.toString() : dbEx.getMessage();
                            Platform.runLater(() -> {
                                Alert alert = new Alert(Alert.AlertType.ERROR,
                                        "DB insert failed: " + msg,
                                        ButtonType.OK);
                                alert.setHeaderText(null);
                                alert.showAndWait();
                            });
                            finish.accept(false);
                            return;
                        }

                        // SUCCESS UI + counter only after DB success
                        int doneCount = processed.incrementAndGet();
                        final String name = formData.getOrDefault("FullName", "(no name)");
                        final String uidFinal = uid;
                        Platform.runLater(() -> {
                            banner.setText("Processed " + doneCount + " / " + total);
                            Alert ok = new Alert(Alert.AlertType.INFORMATION,
                                    "Saved: " + name + " — UID " + uidFinal,
                                    ButtonType.OK);
                            ok.setHeaderText(null);
                            ok.showAndWait();
                        });

                        finish.accept(true); // advance to next record

                    } finally {
                        EntryForm.setNfcBusy(false);
                    }
                }, "batch-write-thread");
                t.setDaemon(true);
                t.start();
            }, rows);

            // Make the existing form look cleaner/bigger WITHOUT changing its structure
            prettifyForm(batch);

            VBox page = new VBox(0, bannerWrap, batch);
            page.setPadding(new Insets(12, 20, 12, 20));
            setContent(page);
        });

        reportBtn.setOnAction(e -> {
            leaveAttendance();

            // Build the UI: selectors on top, TableView for results below
            Label title = new Label("📋 Trans / Attendance Records");
            title.setStyle("-fx-font-size:20px; -fx-font-weight:700; -fx-text-fill:#0D47A1;");
            HBox titleWrap = new HBox(title);
            titleWrap.setAlignment(Pos.CENTER_LEFT);
            titleWrap.setPadding(new Insets(6, 0, 12, 0));

            ComboBox<String> stateCb = new ComboBox<>();
            stateCb.setPromptText("State (optional)");
            ComboBox<String> categoryCb = new ComboBox<>();
            categoryCb.setPromptText("Category (optional)");

            // Try to populate from AccessDb (off the FX thread)
            new Thread(() -> {
                try {
                    java.util.List<String> states = db.AccessDb.fetchDistinctStates();
                    java.util.List<String> cats = db.AccessDb.fetchDistinctExcelCategories();
                    Platform.runLater(() -> {
                        stateCb.getItems().clear();
                        stateCb.getItems().add(""); // allow empty selection
                        stateCb.getItems().addAll(states);
                        categoryCb.getItems().clear();
                        categoryCb.getItems().add("");
                        categoryCb.getItems().addAll(cats);
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                    Platform.runLater(() -> {
                        stateCb.getItems().clear();
                        categoryCb.getItems().clear();
                    });
                }
            }, "load-states-cats").start();

            Button loadBtn = new Button("Load");
            Button exportBtn = new Button("Export CSV");
            exportBtn.setDisable(true); // only enabled when table has rows

            // --- DATE PICKERS: From / To (inclusive) ---
            DatePicker fromDate = new DatePicker();
            fromDate.setPromptText("From (date)");
            DatePicker toDate = new DatePicker();
            toDate.setPromptText("To (date)");

            // You can default fromDate if you want, e.g. last 7 days:
            // fromDate.setValue(java.time.LocalDate.now().minusDays(7));

            HBox controls = new HBox(10,
                    new Label("State:"), stateCb,
                    new Label("Category:"), categoryCb,
                    new Label("From:"), fromDate,
                    new Label("To:"), toDate,
                    loadBtn, exportBtn);
            controls.setAlignment(Pos.CENTER_LEFT);
            controls.setPadding(new Insets(6, 0, 12, 0));

            // Small inline status / message label (for errors / confirmations)
            Label inlineMsg = new Label();
            inlineMsg.setStyle("-fx-text-fill:#2E7D32; -fx-font-weight:600;");

            // TableView<Map<String,String>>
            TableView<Map<String, String>> table = new TableView<>();
            table.setPlaceholder(new Label("No records loaded."));

            // Include rank and bsguid and other useful columns
            java.util.List<String> colKeys = Arrays.asList(
                    "date_time", "fullname", "bsguid", "rank", "location", "event", "bsgState", "excel_category");

            java.util.Map<String, String> colTitles = new HashMap<>();
            colTitles.put("date_time", "Date / Time");
            colTitles.put("fullname", "Full Name");
            colTitles.put("bsguid", "BSGUID");
            colTitles.put("rank", "Rank/Section");
            colTitles.put("location", "Location");
            colTitles.put("event", "Event");
            colTitles.put("bsgState", "State");
            colTitles.put("excel_category", "Category");

            for (String key : colKeys) {
                TableColumn<Map<String, String>, String> tc = new TableColumn<>(colTitles.getOrDefault(key, key));
                tc.setCellValueFactory(cell -> {
                    Map<String, String> row = cell.getValue();
                    String v = row == null ? "" : (row.get(key) == null ? "" : row.get(key));
                    return new javafx.beans.property.ReadOnlyStringWrapper(v);
                });
                // make useful columns wider
                if ("fullname".equals(key))
                    tc.setPrefWidth(220);
                else if ("location".equals(key) || "event".equals(key))
                    tc.setPrefWidth(160);
                else if ("date_time".equals(key))
                    tc.setPrefWidth(160);
                else
                    tc.setPrefWidth(120);
                table.getColumns().add(tc);
            }

            VBox page = new VBox(8, titleWrap, controls, inlineMsg, table);
            page.setPadding(new Insets(12));
            VBox.setVgrow(table, Priority.ALWAYS);

            setContent(page);

            // Helper to convert currently displayed table rows to CSV string
            java.util.function.Supplier<String> buildCsvFromTable = () -> {
                StringBuilder sb = new StringBuilder();
                // header
                sb.append(String.join(",", colKeys.stream().map(k -> "\"" + colTitles.getOrDefault(k, k) + "\"")
                        .toArray(String[]::new))).append("\n");
                for (Map<String, String> row : table.getItems()) {
                    List<String> cells = new ArrayList<>();
                    for (String k : colKeys) {
                        String v = row.getOrDefault(k, "");
                        // escape double quotes by doubling
                        v = v.replace("\"", "\"\"");
                        cells.add("\"" + v + "\"");
                    }
                    sb.append(String.join(",", cells)).append("\n");
                }
                return sb.toString();
            };

            // Export CSV action (open FileChooser)
            exportBtn.setOnAction(x -> {
                try {
                    if (table.getItems().isEmpty()) {
                        inlineMsg.setText("No rows to export.");
                        return;
                    }
                    javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
                    fc.setTitle("Save trans as CSV");
                    fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("CSV Files", "*.csv"));
                    fc.setInitialFileName("trans_export.csv");
                    java.io.File chosen = fc
                            .showSaveDialog(this.getScene() == null ? null : this.getScene().getWindow());
                    if (chosen == null) {
                        return; // user cancelled
                    }
                    String csv = buildCsvFromTable.get();
                    try (java.io.OutputStreamWriter w = new java.io.OutputStreamWriter(
                            new java.io.FileOutputStream(chosen), java.nio.charset.StandardCharsets.UTF_8)) {
                        w.write(csv);
                    }
                    inlineMsg.setStyle("-fx-text-fill:#2E7D32; -fx-font-weight:600;");
                    inlineMsg.setText("Exported " + table.getItems().size() + " row(s) to " + chosen.getName());
                } catch (Exception ex) {
                    ex.printStackTrace();
                    inlineMsg.setStyle("-fx-text-fill:#C62828; -fx-font-weight:600;");
                    inlineMsg.setText("Export failed: " + ex.getMessage());
                }
            });

            // Load action: query DB (off UI thread) and populate table
            loadBtn.setOnAction(ev -> {
                loadBtn.setDisable(true);
                exportBtn.setDisable(true);
                inlineMsg.setText("");
                table.getItems().clear();

                final String chosenState = (stateCb.getValue() == null || stateCb.getValue().isBlank()) ? null
                        : stateCb.getValue().trim();
                final String chosenCat = (categoryCb.getValue() == null || categoryCb.getValue().isBlank()) ? null
                        : categoryCb.getValue().trim();

                Thread q = new Thread(() -> {
                    java.util.List<Map<String, String>> rows = new ArrayList<>();
                    // Query trans joined with ParticipantsRecord to get state/category/rank
                    String sql = """
                            SELECT t.[date_time], t.[fullname], t.[bsguid], t.[location], t.[event],
                                   p.[bsgState], p.[excel_category], p.[rank_or_section]
                            FROM [trans] t
                            LEFT JOIN [ParticipantsRecord] p ON p.[BSGUID] = t.[bsguid]
                            WHERE 1=1
                            """;
                    java.util.List<Object> params = new ArrayList<>();
                    if (chosenState != null) {
                        sql += " AND UCASE(p.[bsgState]) LIKE UCASE(?)";
                        params.add("%" + chosenState + "%");
                    }
                    if (chosenCat != null) {
                        sql += " AND UCASE(p.[excel_category]) LIKE UCASE(?)";
                        params.add("%" + chosenCat + "%");
                    }

                    // --- Date range filtering (inclusive) ---
                    // trans.date_time is stored as "yyyy-MM-dd HH:mm:ss" (text); lexicographic
                    // comparison works.
                    java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter
                            .ofPattern("yyyy-MM-dd HH:mm:ss");

                    java.time.LocalDate from = fromDate.getValue();
                    java.time.LocalDate to = toDate.getValue();

                    // optional validation: if both present and from > to, swap or return empty
                    if (from != null && to != null && from.isAfter(to)) {
                        // swap so query still returns expected results, or you could show inline error.
                        java.time.LocalDate tmp = from;
                        from = to;
                        to = tmp;
                    }

                    if (from != null) {
                        String fromTs = from.atStartOfDay().format(dtf); // "YYYY-MM-DD 00:00:00"
                        sql += " AND t.[date_time] >= ?";
                        params.add(fromTs);
                    }
                    if (to != null) {
                        String toTs = to.atTime(23, 59, 59).format(dtf); // "YYYY-MM-DD 23:59:59"
                        sql += " AND t.[date_time] <= ?";
                        params.add(toTs);
                    }

                    sql += " ORDER BY t.[date_time] DESC";

                    try (java.sql.Connection c = db.AccessDb.getConnection();
                            java.sql.PreparedStatement ps = c.prepareStatement(sql)) {

                        for (int i = 0; i < params.size(); i++) {
                            ps.setString(i + 1, params.get(i).toString());
                        }

                        try (java.sql.ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                Map<String, String> r = new LinkedHashMap<>();
                                r.put("date_time", Optional.ofNullable(rs.getString("date_time")).orElse(""));
                                r.put("fullname", Optional.ofNullable(rs.getString("fullname")).orElse(""));
                                r.put("bsguid", Optional.ofNullable(rs.getString("bsguid")).orElse(""));
                                r.put("rank", Optional.ofNullable(rs.getString("rank_or_section")).orElse(""));
                                r.put("location", Optional.ofNullable(rs.getString("location")).orElse(""));
                                r.put("event", Optional.ofNullable(rs.getString("event")).orElse(""));
                                r.put("bsgState", Optional.ofNullable(rs.getString("bsgState")).orElse(""));
                                r.put("excel_category", Optional.ofNullable(rs.getString("excel_category")).orElse(""));
                                rows.add(r);
                            }
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        Platform.runLater(() -> {
                            inlineMsg.setStyle("-fx-text-fill:#C62828; -fx-font-weight:600;");
                            inlineMsg.setText("Failed to load records: " + ex.getMessage());
                            loadBtn.setDisable(false);
                            exportBtn.setDisable(true);
                        });
                        return;
                    }

                    final java.util.List<Map<String, String>> finalRows = rows;
                    Platform.runLater(() -> {
                        table.getItems().setAll(finalRows);
                        loadBtn.setDisable(false);
                        exportBtn.setDisable(finalRows.isEmpty());
                        inlineMsg.setStyle("-fx-text-fill:#2E7D32; -fx-font-weight:600;");
                        inlineMsg.setText("Loaded " + finalRows.size() + " row(s).");
                    });
                }, "load-trans-thread");
                q.setDaemon(true);
                q.start();
            });
        });

        // NEW: Import Excel wiring
        importBtn.setOnAction(e -> {
            leaveAttendance();

            int imported = ExcelImportDialog.show(
                    this.getScene() == null ? null : (javafx.stage.Stage) this.getScene().getWindow());
            if (imported >= 0) {
                Alert a = new Alert(Alert.AlertType.INFORMATION,
                        imported + " row(s) imported with status='F'. You can now use Batch (Filter).",
                        ButtonType.OK);
                a.setHeaderText(null);
                a.showAndWait();
            }
        });

        // full-page Export preview (replace existing
        exportParticipantsBtn.setOnAction(ev -> {
            leaveAttendance();

            // Title
            Label title = new Label("📤 Preview & Export Participants");
            title.setStyle("-fx-font-size:20px; -fx-font-weight:700; -fx-text-fill:#0D47A1;");
            HBox titleWrap = new HBox(title);
            titleWrap.setAlignment(Pos.CENTER_LEFT);
            titleWrap.setPadding(new Insets(6, 0, 12, 0));

            // Filters
            ComboBox<String> stateCb = new ComboBox<>();
            stateCb.setPromptText("State (optional)");
            stateCb.setMinWidth(220);

            ComboBox<String> categoryCb = new ComboBox<>();
            categoryCb.setPromptText("Category (optional)");
            categoryCb.setMinWidth(220);

            Button loadBtn = new Button("Load");
            Button exportBtn = new Button("Export CSV");
            exportBtn.setDisable(true);

            Label statusLbl = new Label();
            statusLbl.setStyle("-fx-text-fill:#666;");

            HBox filters = new HBox(10,
                    new Label("State:"), stateCb,
                    new Label("Category:"), categoryCb,
                    loadBtn, exportBtn);
            filters.setAlignment(Pos.CENTER_LEFT);
            filters.setPadding(new Insets(6, 0, 12, 0));

            // Preview Table
            TableView<Map<String, String>> table = new TableView<>();
            table.setPlaceholder(new Label("No participants loaded."));
            VBox.setVgrow(table, Priority.ALWAYS);

            // Columns to show by default
            List<String> colKeys = Arrays.asList("FullName", "BSGUID", "bsgState", "excel_category", "rank_or_section",
                    "Email", "phoneNumber", "status", "CardUID");
            Map<String, String> colTitles = new HashMap<>();
            colTitles.put("FullName", "Full Name");
            colTitles.put("BSGUID", "BSGUID");
            colTitles.put("bsgState", "State");
            colTitles.put("excel_category", "Category");
            colTitles.put("rank_or_section", "Rank/Section");
            colTitles.put("Email", "Email");
            colTitles.put("phoneNumber", "Phone");
            colTitles.put("status", "Status");
            colTitles.put("CardUID", "CardUID");

            for (String key : colKeys) {
                TableColumn<Map<String, String>, String> tc = new TableColumn<>(colTitles.getOrDefault(key, key));
                tc.setCellValueFactory(cell -> {
                    Map<String, String> row = cell.getValue();
                    String v = row == null ? "" : row.getOrDefault(key, "");
                    return new javafx.beans.property.ReadOnlyStringWrapper(v);
                });
                tc.setPrefWidth(140);
                table.getColumns().add(tc);
            }

            // Layout
            VBox page = new VBox(8, titleWrap, filters, statusLbl, table);
            page.setPadding(new Insets(12));
            setContent(page);

            // Populate filter lists off FX thread
            new Thread(() -> {
                try {
                    java.util.List<String> states = db.AccessDb.fetchDistinctStates();
                    java.util.List<String> cats = db.AccessDb.fetchDistinctExcelCategories();
                    Platform.runLater(() -> {
                        stateCb.getItems().clear();
                        stateCb.getItems().add(""); // allow empty
                        stateCb.getItems().addAll(states);
                        categoryCb.getItems().clear();
                        categoryCb.getItems().add("");
                        categoryCb.getItems().addAll(cats);
                        statusLbl.setStyle("-fx-text-fill:#666;");
                        statusLbl.setText("Choose filters (leave empty for all). Click Load to preview participants.");
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                    Platform.runLater(() -> {
                        stateCb.getItems().clear();
                        categoryCb.getItems().clear();
                        statusLbl.setStyle("-fx-text-fill:#C62828;");
                        statusLbl.setText("Failed to load filter lists: "
                                + (ex.getMessage() != null ? ex.getMessage() : ex.toString()));
                    });
                }
            }, "load-filters-export-page").start();

            // Load handler
            loadBtn.setOnAction(ae -> {
                final String chosenState = (stateCb.getValue() == null || stateCb.getValue().isBlank()) ? null
                        : stateCb.getValue().trim();
                final String chosenCat = (categoryCb.getValue() == null || categoryCb.getValue().isBlank()) ? null
                        : categoryCb.getValue().trim();

                loadBtn.setDisable(true);
                exportBtn.setDisable(true);
                statusLbl.setStyle("-fx-text-fill:#666;");
                statusLbl.setText("Loading participants...");

                new Thread(() -> {
                    java.util.List<Map<String, String>> participants;
                    try {
                        participants = db.AccessDb.fetchParticipantsByStateAndCategory(chosenState, chosenCat, false);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        final String em = ex.getMessage() != null ? ex.getMessage() : ex.toString();
                        Platform.runLater(() -> {
                            statusLbl.setStyle("-fx-text-fill:#C62828;");
                            statusLbl.setText("Failed to fetch participants: " + em);
                            loadBtn.setDisable(false);
                        });
                        return;
                    }

                    if (participants == null || participants.isEmpty()) {
                        Platform.runLater(() -> {
                            table.getItems().clear();
                            statusLbl.setStyle("-fx-text-fill:#666;");
                            statusLbl.setText("No participants found for selected filters.");
                            loadBtn.setDisable(false);
                            exportBtn.setDisable(true);
                        });
                        return;
                    }

                    final java.util.List<Map<String, String>> finalParticipants = participants;
                    Platform.runLater(() -> {
                        table.getItems().setAll(finalParticipants);
                        statusLbl.setStyle("-fx-text-fill:#2E7D32;");
                        statusLbl.setText("Loaded " + finalParticipants.size()
                                + " row(s). Preview below and click Export CSV to save.");
                        loadBtn.setDisable(false);
                        exportBtn.setDisable(finalParticipants.isEmpty());
                    });
                }, "fetch-participants-export-page").start();
            });

            // Export handler (writes the currently displayed rows)
            exportBtn.setOnAction(ae -> {
                if (table.getItems().isEmpty()) {
                    statusLbl.setStyle("-fx-text-fill:#C62828;");
                    statusLbl.setText("No rows to export.");
                    return;
                }

                javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
                fc.setTitle("Save Participants as CSV");
                fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("CSV Files", "*.csv"));
                fc.setInitialFileName("participants_export.csv");
                java.io.File chosen = fc.showSaveDialog(this.getScene() == null ? null : this.getScene().getWindow());
                if (chosen == null) {
                    return; // cancelled
                }

                // snapshot rows to write
                List<Map<String, String>> toExport = new ArrayList<>(table.getItems());

                // perform write in background
                new Thread(() -> {
                    try {
                        // Ensure parent dir exists
                        java.io.File parent = chosen.getParentFile();
                        if (parent != null && !parent.exists()) {
                            boolean ok = parent.mkdirs();
                            if (!ok && !parent.exists()) {
                                throw new IOException("Failed to create parent directory: " + parent.getAbsolutePath());
                            }
                        }

                        // Build header (use displayed columns first, then any extra keys)
                        List<String> headerOrder = new ArrayList<>(colKeys);
                        if (!toExport.isEmpty()) {
                            Map<String, String> first = toExport.get(0);
                            for (String k : first.keySet()) {
                                if (!headerOrder.contains(k))
                                    headerOrder.add(k);
                            }
                        }

                        java.nio.file.Path outPath = chosen.toPath();
                        try (java.io.BufferedWriter w = java.nio.file.Files.newBufferedWriter(outPath,
                                java.nio.charset.StandardCharsets.UTF_8)) {
                            // header
                            for (int i = 0; i < headerOrder.size(); i++) {
                                if (i > 0)
                                    w.write(',');
                                w.write('"');
                                w.write(headerOrder.get(i));
                                w.write('"');
                            }
                            w.write('\n');

                            for (Map<String, String> row : toExport) {
                                for (int i = 0; i < headerOrder.size(); i++) {
                                    if (i > 0)
                                        w.write(',');
                                    String v = row.getOrDefault(headerOrder.get(i), "");
                                    v = v.replace("\"", "\"\"");
                                    w.write('"');
                                    w.write(v);
                                    w.write('"');
                                }
                                w.write('\n');
                            }
                        }

                        Platform.runLater(() -> {
                            statusLbl.setStyle("-fx-text-fill:#2E7D32;");
                            statusLbl.setText("Exported " + toExport.size() + " row(s) to " + chosen.getName());
                            Alert a = new Alert(Alert.AlertType.INFORMATION,
                                    "Exported " + toExport.size() + " row(s) to " + chosen.getName(), ButtonType.OK);
                            a.setHeaderText(null);
                            a.showAndWait();
                        });
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        final String em = ex.getMessage() != null ? ex.getMessage() : ex.toString();
                        Platform.runLater(() -> {
                            statusLbl.setStyle("-fx-text-fill:#C62828;");
                            statusLbl.setText("Failed to write CSV: " + em);
                            Alert a = new Alert(Alert.AlertType.ERROR, "Failed to write CSV: " + em, ButtonType.OK);
                            a.setHeaderText(null);
                            a.showAndWait();
                        });
                    }
                }, "export-write-thread").start();
            });
        });

        addEventBtn.setOnAction(e -> {
            EventFormController controller = new EventFormController();

            Parent page = EventFormPage.create(controller::handleCreate);
            setContent(page);
        });
    }

    // --- Helper Method for Page Switching with Animation ---
    private void setContent(Node node) {
        // stop any NFC poller from previous view (EntryForm-owned pollers)
        if (!contentArea.getChildren().isEmpty()) {
            Node prev = contentArea.getChildren().get(0);
            EntryForm.stopNfcPolling(prev);
        }

        // Ensure pages expand horizontally to use the available viewport width
        if (node instanceof Region r) {
            r.setMaxWidth(Double.MAX_VALUE);
            // allow the region to grow horizontally when scrollPane's viewport expands
            HBox.setHgrow(r, Priority.ALWAYS);
            VBox.setVgrow(r, Priority.ALWAYS);
            r.setPrefWidth(contentArea.getMaxWidth() > 0 ? contentArea.getMaxWidth() : Region.USE_COMPUTED_SIZE);
        }

        contentArea.getChildren().setAll(node);

        // make sure the node width tracks the contentArea / viewport width
        // (use runLater so layout bounds are valid)
        Platform.runLater(() -> {
            if (node instanceof Region r && scrollPane.getViewportBounds() != null) {
                double vw = Math.max(0, scrollPane.getViewportBounds().getWidth() - 2);
                r.setPrefWidth(vw);
            }
        });

        FadeTransition fadeIn = new FadeTransition(Duration.millis(400), node);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();
    }

    private void showUid(String uid) {
        Platform.runLater(() -> {
            if (attendanceView != null) {
                attendanceView.getHeadline().setText("Card detected");
                attendanceView.getHeadline().setStyle("""
                        -fx-font-size: 28px;
                        -fx-font-weight: 900;
                                    -fx-text-fill: #2E7D32;
                            """);
                attendanceView.getUidLabel().setText("UID: " + uid);
            }
        });
    }

    private void showRemovePrompt() {
        Platform.runLater(() -> {
            if (attendanceView != null) {
                attendanceView.getHeadline().setText("Remove card…");
                attendanceView.getHeadline().setStyle("""
                            -fx-font-size: 28px;
                            -fx-font-weight: 900;
                            -fx-text-fill: #E65100;
                        """);
            }
        });
    }

    private void setAttendancePrompt() {
        Platform.runLater(() -> {
            if (attendanceView != null) {
                attendanceView.getHeadline().setText("Tap your card");
                attendanceView.getHeadline().setStyle("""
                                -fx-font-size: 28px;
                                -fx-font-weight: 900;
                                -fx-text-fill: #0D47A1;
                        """);
                attendanceView.getUidLabel().setText("");
            }
        });
    }

    // >>> Attendance poller control

    /**
     * Start (or restart) the attendance poller. Safe to call repeatedly.
     * Waits indefinitely for a card; when present, shows UID, then waits until card
     * is removed and returns to "Tap your card".
     */
    // Add near the top of Dashboard (class fields) or as local finals inside
    // startAttendancePoller():
    private static final int PROBE_TIMEOUT_MS = 200; // each probe wait

    private void startAttendancePoller() {

        if (attendancePollerRunning.get())
            return;

        attendancePollerRunning.set(true);
        attendancePollerThread = new Thread(() -> {
            Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

            while (attendancePollerRunning.get() && onAttendanceTab.get()) {
                // WAIT FOR CARD (short-timeout polling so we can stop cooperatively)
                SmartMifareReader.ReadResult rr = null;
                try {
                    // use a short timeout so the thread checks the running flag often
                    rr = SmartMifareReader.readUIDWithData(PROBE_TIMEOUT_MS);
                } catch (Throwable t) {
                    // swallow and continue if still running
                    // if the error is fatal you may want to log it
                    t.printStackTrace();
                }

                if (!attendancePollerRunning.get() || !onAttendanceTab.get()) {
                    break;
                }

                if (rr != null && rr.uid != null && !rr.uid.isBlank()) {
                    // Process card present exactly like before
                    showUid(rr.uid);

                    if (attendanceView != null) {
                        try {
                            attendanceView.acceptReadResult(rr);
                        } catch (Throwable t) {
                            t.printStackTrace();
                        }
                    }
                    // Prompt removal and block until absent (prevents processing next card)
                    showRemovePrompt();
                    boolean absentConfirmed = SmartMifareReader.waitForCardAbsent(0); // 0 = wait forever

                    if (attendancePollerRunning.get() && onAttendanceTab.get() && absentConfirmed) {
                        setAttendancePrompt();
                        if (attendanceView != null) {
                            try {
                                attendanceView.clearDetails();
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                } else {
                    // no card seen in this short probe -> continue loop (no tight spin)
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ie) {
                        // don't propagate interrupt; just honour shutdown flag
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            attendancePollerRunning.set(false);
        }, "attendance-poller");
        attendancePollerThread.setDaemon(true);
        attendancePollerThread.start();
    }

    private void leaveAttendance() {
        onAttendanceTab.set(false);
        stopAttendancePoller();
    }

    private void stopAttendancePoller() {
        if (!attendancePollerRunning.get())
            return;

        // tell the poller to stop
        attendancePollerRunning.set(false);

        // If your SmartMifareReader provides a cancel/unblock method, call it so
        // readUIDWithData(0)
        // will return quickly. We try reflectively to avoid compile-time dependency.
        try {
            java.lang.reflect.Method cancel = null;
            try {
                cancel = SmartMifareReader.class.getMethod("cancelWait");
            } catch (NoSuchMethodException ignored) {
                // try alternative method name used in some libs
                try {
                    cancel = SmartMifareReader.class.getMethod("close");
                } catch (NoSuchMethodException ignored2) {
                }
            }
            if (cancel != null) {
                try {
                    cancel.invoke(null); // assume static cancel; if instance method exists adjust accordingly
                } catch (Throwable ignore) {
                }
            }
        } catch (Throwable ignore) {
        }

        // Do NOT interrupt the thread — let it finish its I/O and exit. Interrupting
        // can close
        // file channels used by Jackcess and lead to ClosedByInterruptException.
    }
}
