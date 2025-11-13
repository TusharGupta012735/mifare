package ui;

import db.AccessDb;
import javafx.scene.control.Control;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ExcelImportDialog {

    private ExcelImportDialog() {
    }

    /**
     * Opens a modal dialog to import a sheet. Returns number of rows imported, or
     * -1 if cancelled.
     */
    public static int show(Stage owner) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null)
            stage.initOwner(owner);
        stage.setTitle("Import from Excel");

        Label banner = new Label("Upload an Excel file, pick a sheet, preview, then Import");
        banner.setStyle("-fx-font-size: 18px; -fx-font-weight: 900; -fx-text-fill: #0D47A1;");
        HBox bannerWrap = new HBox(banner);
        bannerWrap.setAlignment(Pos.CENTER);
        bannerWrap.setPadding(new Insets(8, 0, 12, 0));

        TextField fileField = new TextField();
        fileField.setPromptText("Choose .xlsx or .xls file");
        fileField.setEditable(false);
        styleField(fileField);

        Button browse = new Button("Browse…");
        stylePrimary(browse);

        ComboBox<String> sheetBox = new ComboBox<>();
        sheetBox.setPromptText("Choose a sheet");
        sheetBox.setDisable(true);
        sheetBox.setPrefWidth(260);
        styleField(sheetBox);

        Button importBtn = new Button("Import");
        importBtn.setDisable(true);
        stylePrimary(importBtn);

        Button cancelBtn = new Button("Cancel");
        styleSecondary(cancelBtn);

        TableView<Map<String, String>> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Sheet preview will appear here"));
        table.setPrefHeight(420);

        // state
        final List<Map<String, String>>[] currentData = new List[] { List.of() };
        final String[] currentSheetName = new String[1];

        // browse action
        // browse action
        browse.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Excel Files", "*.xlsx", "*.xls"),
                    new FileChooser.ExtensionFilter("All Files", "*.*"));
            File f = fc.showOpenDialog(stage);
            if (f == null)
                return;

            fileField.setText(f.getAbsolutePath());

            // clear any stale preview first
            table.getItems().clear();
            table.getColumns().clear();
            importBtn.setDisable(true);

            loadSheetsIntoCombo(f, sheetBox);
            // loadSheetsIntoCombo will selectFirst() which in turn triggers the
            // selectedItemProperty listener
        });

        // sheet selection -> preview
        sheetBox.getSelectionModel().selectedItemProperty().addListener((obs, oldSheet, newSheet) -> {
            if (newSheet == null || fileField.getText().isBlank())
                return;
            File f = new File(fileField.getText());
            try {
                List<Map<String, String>> rows = readSheet(f, newSheet, 200); // preview first 200
                currentData[0] = rows;
                currentSheetName[0] = newSheet;
                buildTable(table, rows);
                importBtn.setDisable(rows.isEmpty());
            } catch (Exception ex) {
                showAlert(Alert.AlertType.ERROR, "Failed to read sheet: " + ex.getMessage());
                importBtn.setDisable(true);
                table.getItems().clear();
                table.getColumns().clear();
            }
        });

        // import
        importBtn.setOnAction(e -> {
            if (currentData[0] == null || currentData[0].isEmpty() || currentSheetName[0] == null) {
                showAlert(Alert.AlertType.WARNING, "Nothing to import.");
                return;
            }

            // Re-read full sheet (not just 200 rows)
            try {
                List<Map<String, String>> allRows = readSheet(new File(fileField.getText()), currentSheetName[0],
                        Integer.MAX_VALUE);
                int n = AccessDb.bulkImportParticipantsRecord(currentSheetName[0], allRows);
                showAlert(Alert.AlertType.INFORMATION,
                        "Imported " + n + " row(s) as status='F' with excel_category='" + currentSheetName[0] + "'");
                stage.setUserData(Integer.valueOf(n));
                stage.close();
            } catch (Exception ex) {
                showAlert(Alert.AlertType.ERROR, "Import failed: " + ex.getMessage());
            }
        });

        cancelBtn.setOnAction(e -> {
            stage.setUserData(Integer.valueOf(-1));
            stage.close();
        });

        // layout
        GridPane top = new GridPane();
        top.setHgap(10);
        top.setVgap(10);
        top.add(new Label("File:"), 0, 0);
        top.add(fileField, 1, 0);
        top.add(browse, 2, 0);
        top.add(new Label("Sheet:"), 0, 1);
        top.add(sheetBox, 1, 1);

        VBox card = new VBox(12, top, table, new HBox(10, importBtn, cancelBtn));
        ((HBox) card.getChildren().get(card.getChildren().size() - 1)).setAlignment(Pos.CENTER_RIGHT);
        card.setPadding(new Insets(16));
        card.setStyle("""
                    -fx-background-color: white;
                    -fx-background-radius: 12;
                    -fx-border-radius: 12;
                    -fx-border-color: #E0E0E0;
                    -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 12, 0, 0, 3);
                """);

        VBox root = new VBox(0, bannerWrap, card);
        root.setPadding(new Insets(12, 18, 18, 18));

        Scene sc = new Scene(root, 900, 650);
        stage.setScene(sc);
        stage.showAndWait();

        Object ud = stage.getUserData();
        return (ud instanceof Integer i) ? i.intValue() : -1;
    }

    // ---- helpers ----

    private static void buildTable(TableView<Map<String, String>> table, List<Map<String, String>> rows) {
        table.getItems().clear();
        table.getColumns().clear();

        if (rows == null || rows.isEmpty())
            return;

        Map<String, String> first = rows.get(0);

        int colCount = first.size();
        double colWidth = 900.0 / colCount; // estimate usable width

        for (String key : first.keySet()) {

            // --- REMOVE Serial Number Columns ---
            String k = key.toLowerCase();
            if (k.equals("slno") || k.equals("sno") || k.equals("serial") || k.equals("serial no")
                    || k.equals("srno") || k.equals("sr_no") || k.equals("sr no")) {
                continue; // ← Skip column entirely
            }

            TableColumn<Map<String, String>, String> col = new TableColumn<>(key);
            col.setCellValueFactory(cd -> new SimpleStringProperty(
                    Optional.ofNullable(cd.getValue().get(key)).orElse("")));

            // Keep ID / UID tiny
            if (k.equals("id") || k.contains("uid")) {
                col.setMaxWidth(70);
                col.setMinWidth(50);
            } else {
                col.setPrefWidth(colWidth);
            }

            table.getColumns().add(col);
        }

        table.getItems().addAll(rows);
    }

    private static void loadSheetsIntoCombo(File file, ComboBox<String> sheetBox) {
        List<String> names = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file)) {
            try (Workbook wb = WorkbookFactory.create(fis)) {
                int n = wb.getNumberOfSheets();
                for (int i = 0; i < n; i++)
                    names.add(wb.getSheetName(i));
            }
        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR, "Failed to open Excel: " + ex.getMessage());
        }
        sheetBox.getItems().setAll(names);
        sheetBox.setDisable(names.isEmpty());
        if (!names.isEmpty())
            sheetBox.getSelectionModel().selectFirst();
    }

    /**
     * Read a sheet into list of maps using first row as headers. Limit rows if >0.
     */
    private static List<Map<String, String>> readSheet(File file, String sheetName, int limit) throws Exception {
        try (FileInputStream fis = new FileInputStream(file);
                Workbook wb = WorkbookFactory.create(fis)) {

            Sheet sh = wb.getSheet(sheetName);
            if (sh == null)
                throw new IllegalArgumentException("Sheet not found: " + sheetName);

            Iterator<Row> it = sh.iterator();
            if (!it.hasNext())
                return List.of();

            // headers
            Row header = it.next();
            List<String> headers = new ArrayList<>();
            for (Cell c : header)
                headers.add(getCellString(c).trim());

            List<Map<String, String>> out = new ArrayList<>();
            int count = 0;
            while (it.hasNext()) {
                Row r = it.next();
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    String h = headers.get(i);
                    if (h == null || h.isBlank())
                        continue;
                    Cell c = r.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    row.put(h, c == null ? "" : getCellString(c));
                }
                // ignore fully-empty rows
                boolean any = row.values().stream().anyMatch(v -> v != null && !v.trim().isEmpty());
                if (any)
                    out.add(row);
                if (limit != Integer.MAX_VALUE && ++count >= limit)
                    break;
            }
            return out;
        }
    }

    private static String getCellString(Cell cell) {
        if (cell == null)
            return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    java.util.Date d = cell.getDateCellValue();
                    java.time.LocalDate ld = d.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                    yield ld.toString(); // yyyy-MM-dd
                } else {
                    java.math.BigDecimal bd = new java.math.BigDecimal(cell.getNumericCellValue());
                    yield bd.stripTrailingZeros().toPlainString();
                }
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (Exception ex) {
                    try {
                        java.math.BigDecimal bd = new java.math.BigDecimal(cell.getNumericCellValue());
                        yield bd.stripTrailingZeros().toPlainString();
                    } catch (Exception e2) {
                        yield cell.getCellFormula();
                    }
                }
            }
            default -> "";
        };
    }

    private static void styleField(Control c) {
        c.setStyle("""
                    -fx-background-color: white;
                    -fx-border-color: #BDBDBD;
                    -fx-border-radius: 8;
                    -fx-background-radius: 8;
                    -fx-padding: 8 10;
                    -fx-font-size: 14px;
                    -fx-text-fill: #212121;
                """);
        if (c instanceof ComboBox)
            ((ComboBox<?>) c).setVisibleRowCount(12);
    }

    private static void stylePrimary(Button b) {
        b.setStyle("""
                    -fx-background-color:#1976D2;
                    -fx-text-fill:white; -fx-font-weight:bold; -fx-background-radius:8;
                    -fx-padding:8 14;
                """);
        b.setOnMouseEntered(e -> b.setStyle("""
                    -fx-background-color:#2196F3;
                    -fx-text-fill:white; -fx-font-weight:bold; -fx-background-radius:8;
                    -fx-padding:8 14;
                """));
        b.setOnMouseExited(e -> b.setStyle("""
                    -fx-background-color:#1976D2;
                    -fx-text-fill:white; -fx-font-weight:bold; -fx-background-radius:8;
                    -fx-padding:8 14;
                """));
    }

    private static void styleSecondary(Button b) {
        b.setStyle("""
                    -fx-background-color: transparent;
                    -fx-text-fill: #1565C0;
                    -fx-font-size: 13px;
                    -fx-underline: true;
                    -fx-padding: 6 8;
                """);
    }

    private static void showAlert(Alert.AlertType type, String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(type, msg, ButtonType.OK);
            a.setHeaderText(null);
            a.showAndWait();
        });
    }
}
