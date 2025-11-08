package ui;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import db.AccessDb;
import nfc.SmartMifareReader;
import nfc.SmartMifareWriter;
import javafx.application.Platform;
import javafx.scene.Parent;

import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.util.Duration;
import javafx.scene.Node;
import javafx.scene.paint.Color;

public class Dashboard extends BorderPane {

    // >>> Attendance poller state (runs ONLY on Attendance tab)
    private volatile Thread attendancePollerThread = null;
    private final AtomicBoolean attendancePollerRunning = new AtomicBoolean(false);
    private final AtomicBoolean onAttendanceTab = new AtomicBoolean(false);

    // Reuse these whenever the Attendance tab is (re)loaded
    private static final String LOC_EVENT_PATH = "src/main/resources/location.txt";
    private static final boolean LOC_EVENT_CLASSPATH = false;
    private static final String LOGO_PATH = "src/main/resources/logo-removebg-preview.png";

    private AttendanceView attendanceView;

    // Make inputs/headings larger & cleaner without touching EntryForm code
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
        // --- Buttons ---
        Button attendanceBtn = new Button("Attendance");
        Button entryFormBtn = new Button("Entry Form");
        Button batchBtn = new Button("Batch (Filter)");
        Button reportBtn = new Button("Report");
        Button importBtn = new Button("Import Excel"); // NEW

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

        for (Button btn : new Button[] { attendanceBtn, entryFormBtn, batchBtn, reportBtn, importBtn }) {
            btn.setStyle(btnStyle);
            btn.setOnMouseEntered(e -> btn.setStyle(hoverStyle));
            btn.setOnMouseExited(e -> btn.setStyle(btnStyle));
        }

        // --- Navbar Layout (added Import Excel at the end) ---
        HBox navBar = new HBox(20, attendanceBtn, entryFormBtn, batchBtn, reportBtn, importBtn);
        navBar.setPadding(new Insets(15, 20, 15, 20));
        navBar.setStyle(
                "-fx-background-color: linear-gradient(to bottom, #1565c0, #0d47a1); -fx-alignment: center; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 8, 0, 0, 2);");

        for (Button btn : new Button[] { attendanceBtn, entryFormBtn, batchBtn, reportBtn, importBtn }) {
            HBox.setHgrow(btn, Priority.ALWAYS);
            btn.setMaxWidth(Double.MAX_VALUE);
        }

        // --- Default Content: ATTENDANCE ---
        contentArea.setPadding(new Insets(20));
        attendanceView = new AttendanceView();
        setContent(attendanceView.getView());
        attendanceView.setLogo(LOGO_PATH);
        attendanceView.loadLocationEventFromFile(LOC_EVENT_PATH, LOC_EVENT_CLASSPATH);

        onAttendanceTab.set(true);
        startAttendancePoller();

        // --- Add Components to Layout ---
        setTop(navBar);
        setCenter(contentArea);

        // --- Actions ---
        attendanceBtn.setOnAction(e -> {
            onAttendanceTab.set(true);
            attendanceView = new AttendanceView(); // create new instance
            setContent(attendanceView.getView());
            attendanceView.loadLocationEventFromFile(LOC_EVENT_PATH, LOC_EVENT_CLASSPATH);
            startAttendancePoller();
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

        // Report placeholder
        reportBtn.setOnAction(e -> {
            leaveAttendance();
            setContent("📊 Report Page");
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
    }

    // --- Helper Method for Page Switching with Animation ---
    private void setContent(Node node) {
        // stop any NFC poller from previous view (EntryForm-owned pollers)
        if (!contentArea.getChildren().isEmpty()) {
            Node prev = contentArea.getChildren().get(0);
            EntryForm.stopNfcPolling(prev);
        }

        contentArea.getChildren().setAll(node);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(400), node);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();
    }

    private void setContent(String text) {
        Text newText = new Text(text);
        newText.setStyle("-fx-font-size: 20px; -fx-fill: #212121; -fx-font-weight: 600;");
        setContent(newText);
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

    // >>> Attendance poller control

    /**
     * Start (or restart) the attendance poller. Safe to call repeatedly.
     * Waits indefinitely for a card; when present, shows UID, then waits until card
     * is removed and returns to "Tap your card".
     */
    // Add near the top of Dashboard (class fields) or as local finals inside
    // startAttendancePoller():
    private static final int PROBE_TIMEOUT_MS = 200; // each probe wait
    private static final int ABSENT_CONFIRM_MS = 1000; // must see NULL for this long to consider removed

    private void startAttendancePoller() {

        if (attendancePollerRunning.get())
            return;

        attendancePollerRunning.set(true);
        attendancePollerThread = new Thread(() -> {
            Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

            while (attendancePollerRunning.get() && onAttendanceTab.get()) {
                // 1) Wait indefinitely for a card
                SmartMifareReader.ReadResult rr = null;
                try {
                    rr = SmartMifareReader.readUIDWithData(0); // infinite wait
                } catch (Throwable t) {
                    // swallow & keep looping
                }

                if (!attendancePollerRunning.get() || !onAttendanceTab.get())
                    break;

                if (rr != null && rr.uid != null && !rr.uid.isBlank()) {
                    // Show UID immediately in UI
                    showUid(rr.uid);

                    // Let the view parse payload / show name/bsguid etc.
                    if (attendanceView != null) {
                        try {
                            attendanceView.acceptReadResult(rr);
                        } catch (Throwable t) {
                            t.printStackTrace();
                        }
                    }

                    // --- BLOCKING DB write: insert into trans BEFORE accepting next card ---
                    int inserted = 0;
                    try {
                        String loc = (attendanceView != null ? attendanceView.getLocationText() : "(unknown)");
                        String ev = (attendanceView != null ? attendanceView.getEventText() : "(unknown)");
                        try {
                            inserted = db.AccessDb.insertTrans(rr.uid, loc, ev); // returns 1 on success
                        } catch (Throwable dbEx) {
                            dbEx.printStackTrace();
                            inserted = 0;
                        }
                    } finally {
                        // update headline with short message (no popup)
                        if (inserted > 0) {
                            Platform.runLater(() -> {
                                if (attendanceView != null) {
                                    attendanceView.getHeadline().setText("Attendance updated");
                                    attendanceView.getHeadline().setStyle("""
                                                -fx-font-size: 28px;
                                                -fx-font-weight: 900;
                                                -fx-text-fill: #2E7D32;
                                            """);
                                }
                            });
                        } else {
                            Platform.runLater(() -> {
                                if (attendanceView != null) {
                                    attendanceView.getHeadline().setText("Card not registered");
                                    attendanceView.getHeadline().setStyle("""
                                                -fx-font-size: 28px;
                                                -fx-font-weight: 900;
                                                -fx-text-fill: #D32F2F;
                                            """);
                                }
                            });
                        }
                    }

                    // Prompt removal and block until absent (prevents processing next card)
                    showRemovePrompt();
                    boolean absentConfirmed = SmartMifareReader.waitForCardAbsent(0); // 0 = wait forever

                    // Reset UI for next card while preserving location/event if your view holds
                    // them
                    if (attendancePollerRunning.get() && onAttendanceTab.get() && absentConfirmed) {
                        setAttendancePrompt();
                        if (attendanceView != null) {
                            // clear transient fields (name/uid/date/time) but keep location/event
                            try {
                                attendanceView.clearDetails();
                            } catch (Throwable ignored) {
                            }
                        }
                    }

                } else {
                    // timed out or error (shouldn't happen with 0), brief rest to avoid tight loop
                    try {
                        Thread.sleep(150);
                    } catch (InterruptedException ex) {
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

    /**
     * Called whenever we navigate away from the Attendance tab.
     * Ensures the poller is fully stopped and state cleared (so other tabs aren't
     * affected).
     */
    private void leaveAttendance() {
        onAttendanceTab.set(false);
        stopAttendancePoller();
    }

    private void stopAttendancePoller() {
        if (!attendancePollerRunning.get())
            return;

        attendancePollerRunning.set(false);
        Thread t = attendancePollerThread;
        attendancePollerThread = null;
        if (t != null) {
            t.interrupt();
        }
    }
}
