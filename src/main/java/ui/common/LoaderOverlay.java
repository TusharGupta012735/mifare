package ui.common;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public final class LoaderOverlay {

    private final StackPane container;
    private final StackPane overlay;

    private LoaderOverlay(StackPane container, StackPane overlay) {
        this.container = container;
        this.overlay = overlay;
    }

    public static LoaderOverlay wrap(Node content) {

        // overlay layer
        StackPane overlay = new StackPane();
        overlay.setVisible(false);
        overlay.setPickOnBounds(true);
        overlay.setStyle(
                "-fx-background-color: rgba(0,0,0,0.08);" +
                        "-fx-background-radius: 10;");

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(72, 72);

        VBox box = new VBox(spinner);
        box.setAlignment(Pos.CENTER);

        overlay.getChildren().add(box);

        // container that HOLDS BOTH content + overlay
        StackPane container = new StackPane(content, overlay);
        container.setPadding(new Insets(10));

        return new LoaderOverlay(container, overlay);
    }

    /** Node you should return to the scene */
    public StackPane getRoot() {
        return container;
    }

    public void show() {
        overlay.setVisible(true);
    }

    public void hide() {
        overlay.setVisible(false);
    }
}
