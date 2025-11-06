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

    // Simple attendance view controls we update from the poller
    private Label attendanceHeadline; // big prompt ("Tap your card", "Card read", etc)
    private Label attendanceUid; // shows UID
    private VBox attendanceView; // root node for attendance page

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
        setContent(createAttendanceView());
        onAttendanceTab.set(true);
        startAttendancePoller();

        // --- Add Components to Layout ---
        setTop(navBar);
        setCenter(contentArea);

        // --- Actions ---
        attendanceBtn.setOnAction(e -> {
            onAttendanceTab.set(true);
            setContent(createAttendanceView()); // rebuild attendance view
            startAttendancePoller();
        });

        entryFormBtn.setOnAction(e -> {
            leaveAttendance();
            Parent form = EntryForm.create((formData, done) -> {
                new Thread(() -> {
                    long dbId = -1;
                    try {
                        String textToWrite = formData.get("__CSV__");
                        if (textToWrite == null) {
                            textToWrite = formData.values().stream()
                                    .map(v -> v == null ? "" : v.trim())
                                    .collect(Collectors.joining(","));
                        }

                        EntryForm.setNfcBusy(true);
                        String cardUid = null;
                        try {
                            SmartMifareWriter.WriteResult result = SmartMifareWriter.writeText(textToWrite);
                            if (result != null)
                                cardUid = result.uid;
                        } catch (Exception nfcEx) {
                            final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(
                                    1);
                            final boolean[] proceed = new boolean[1];
                            Platform.runLater(() -> {
                                Alert warn = new Alert(Alert.AlertType.CONFIRMATION,
                                        "Writing to NFC card failed: " + nfcEx.getMessage() + "\n\n" +
                                                "Continue and save to DB without assigning a card?",
                                        ButtonType.YES, ButtonType.NO);
                                warn.setHeaderText(null);
                                proceed[0] = warn.showAndWait().filter(b -> b == ButtonType.YES).isPresent();
                                latch.countDown();
                            });
                            try {
                                latch.await();
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                            }
                            if (!proceed[0])
                                return;
                        } finally {
                            EntryForm.setNfcBusy(false);
                        }

                        try {
                            dbId = AccessDb.insertAttendee(formData, cardUid);
                        } catch (Exception dbEx) {
                            final String msg = "DB insert failed: " + dbEx.getMessage();
                            Platform.runLater(() -> {
                                Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
                                alert.setHeaderText(null);
                                alert.showAndWait();
                            });
                            return;
                        }

                        final long idFinal = dbId;
                        Platform.runLater(() -> {
                            Alert ok = new Alert(Alert.AlertType.INFORMATION,
                                    "Saved successfully to database. (id=" + idFinal + ")",
                                    ButtonType.OK);
                            ok.setHeaderText(null);
                            ok.showAndWait();
                        });

                    } catch (Exception ex2) {
                        final String err = ex2.getMessage() == null ? ex2.toString() : ex2.getMessage();
                        Platform.runLater(() -> {
                            Alert a = new Alert(Alert.AlertType.ERROR, "Operation failed: " + err, ButtonType.OK);
                            a.setHeaderText(null);
                            a.showAndWait();
                        });
                    } finally {
                        if (done != null)
                            done.run();
                    }
                }, "writer-db-thread").start();
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

            Parent batch = EntryForm.createBatch((formData, done) -> {
                new Thread(() -> {
                    try {
                        String textToWrite = formData.get("__CSV__");
                        if (textToWrite == null) {
                            textToWrite = formData.values().stream()
                                    .map(v -> v == null ? "" : v.trim())
                                    .collect(Collectors.joining(","));
                        }

                        String cardUid = null;
                        EntryForm.setNfcBusy(true);
                        try {
                            SmartMifareWriter.WriteResult wr = SmartMifareWriter.writeText(textToWrite);
                            if (wr != null)
                                cardUid = wr.uid;
                        } catch (Exception nfcEx) {
                            System.err.println("[WARN] NFC write failed: " + nfcEx.getMessage());
                        } finally {
                            EntryForm.setNfcBusy(false);
                        }

                        try {
                            AccessDb.insertAttendee(formData, cardUid);
                            int doneCount = processed.incrementAndGet();
                            Platform.runLater(() -> {
                                banner.setText("Processed " + doneCount + " / " + total);
                                Alert ok = new Alert(Alert.AlertType.INFORMATION,
                                        "Saved: " + formData.getOrDefault("FullName", "(no name)"),
                                        ButtonType.OK);
                                ok.setHeaderText(null);
                                ok.showAndWait();
                            });
                        } catch (Exception dbEx) {
                            Platform.runLater(() -> {
                                Alert alert = new Alert(Alert.AlertType.ERROR,
                                        "DB insert failed: " + dbEx.getMessage(), ButtonType.OK);
                                alert.setHeaderText(null);
                                alert.showAndWait();
                            });
                        }
                    } finally {
                        if (done != null)
                            done.run();
                    }
                }, "batch-filter-thread").start();
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

    // -------- Attendance UI (inline, replaces AttendancePage.create()) --------
    private Parent createAttendanceView() {
        attendanceHeadline = new Label("Tap your card");
        attendanceHeadline.setStyle("""
                        -fx-font-size: 28px;
                        -fx-font-weight: 900;
                        -fx-text-fill: #0D47A1;
                """);

        attendanceUid = new Label("");
        attendanceUid.setStyle("""
                        -fx-font-size: 20px;
                        -fx-font-weight: 700;
                        -fx-text-fill: #2E7D32;
                """);

        Label sub = new Label("Hold for a moment, then remove the card to continue.");
        sub.setStyle("-fx-font-size: 13px; -fx-text-fill: #455A64;");

        VBox box = new VBox(12, attendanceHeadline, attendanceUid, sub);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(30));
        box.setStyle("""
                        -fx-background-color: white;
                        -fx-background-radius: 12;
                        -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 16, 0, 0, 4);
                """);

        StackPane wrapper = new StackPane(box);
        wrapper.setPadding(new Insets(10));
        wrapper.setStyle("-fx-background-color: #ECEFF1;");

        attendanceView = new VBox(wrapper);
        attendanceView.setFillWidth(true);

        // Reset initial prompt
        setAttendancePrompt();

        return attendanceView;
    }

    private void setAttendancePrompt() {
        Platform.runLater(() -> {
            if (attendanceHeadline != null) {
                attendanceHeadline.setText("Tap your card");
                attendanceHeadline.setTextFill(Color.web("#0D47A1"));
            }
            if (attendanceUid != null)
                attendanceUid.setText("");
        });
    }

    private void showUid(String uid) {
        Platform.runLater(() -> {
            if (attendanceHeadline != null) {
                attendanceHeadline.setText("Card detected");
                attendanceHeadline.setTextFill(Color.web("#2E7D32"));
            }
            if (attendanceUid != null)
                attendanceUid.setText("UID: " + uid);
        });
    }

    private void showRemovePrompt() {
        Platform.runLater(() -> {
            if (attendanceHeadline != null) {
                attendanceHeadline.setText("Remove card…");
                attendanceHeadline.setTextFill(Color.web("#E65100"));
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
                    // Show UID
                    showUid(rr.uid);

                    // After: showUid(rr.uid);
                    showRemovePrompt();

                    // Block here until the reader confirms the card is absent.
                    // This avoids debounce glitches and polling races completely.
                    boolean absentConfirmed = SmartMifareReader.waitForCardAbsent(0); // 0 = wait forever

                    // If the poller is still active and we're on the Attendance tab, reset the UI
                    if (attendancePollerRunning.get() && onAttendanceTab.get() && absentConfirmed) {
                        setAttendancePrompt();
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
