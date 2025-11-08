package ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class AttendanceView {

    private final Label headline;
    private final Label uidLabel;
    private final VBox root;

    public AttendanceView() {
        headline = new Label("Tap your card");
        headline.setStyle("""
                        -fx-font-size: 28px;
                        -fx-font-weight: 900;
                        -fx-text-fill: #0D47A1;
                """);

        uidLabel = new Label("");
        uidLabel.setStyle("""
                        -fx-font-size: 20px;
                        -fx-font-weight: 700;
                        -fx-text-fill: #2E7D32;
                """);

        Label sub = new Label("Hold for a moment, then remove the card to continue.");
        sub.setStyle("-fx-font-size: 13px; -fx-text-fill: #455A64;");

        VBox box = new VBox(12, headline, uidLabel, sub);
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

        root = new VBox(wrapper);
        root.setFillWidth(true);
    }

    public Parent getView() {
        return root;
    }

    public Label getHeadline() {
        return headline;
    }

    public Label getUidLabel() {
        return uidLabel;
    }
}
