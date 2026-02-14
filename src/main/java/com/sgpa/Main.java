package com.sgpa;

import com.sgpa.utils.DatabaseConnection;
import com.sgpa.utils.FontLoader;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Point d'entree principal de l'application SGPA.
 */
public class Main extends Application {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        logger.info("===========================================");
        logger.info("ApotiCare - Gestion Moderne de Pharmacie");
        logger.info("===========================================");

        // Proprietes de rendu pour un affichage plus lisse
        System.setProperty("prism.lcdtext", "false");
        System.setProperty("prism.text", "t2k");

        // Charger les polices custom
        FontLoader.loadFonts();

        // Lancer l'application JavaFX
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        logger.info("Demarrage de l'interface JavaFX...");

        // Tester la connexion BDD
        boolean dbConnected = false;
        try {
            dbConnected = DatabaseConnection.getInstance().testConnection();
            if (dbConnected) {
                logger.info("Connexion a la base de donnees: OK");
            }
        } catch (Exception e) {
            logger.error("Erreur de connexion a la base de donnees: {}", e.getMessage());
        }

        if (!dbConnected) {
            logger.error("Impossible de se connecter a la base de donnees.");
            showDatabaseError(primaryStage);
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Parent root = loader.load();

            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());

            Rectangle2D sb = Screen.getPrimary().getVisualBounds();
            primaryStage.setTitle("ApotiCare - Connexion");
            primaryStage.setScene(scene);
            primaryStage.setWidth(520);
            primaryStage.setHeight(680);
            primaryStage.setMinWidth(480);
            primaryStage.setMinHeight(600);
            primaryStage.setResizable(true);
            primaryStage.setX((sb.getWidth() - 520) / 2);
            primaryStage.setY((sb.getHeight() - 680) / 2);
            primaryStage.setOnCloseRequest(event -> {
                logger.info("Fermeture de l'application...");
                DatabaseConnection.getInstance().shutdown();
            });

            primaryStage.show();
            logger.info("Application demarree avec succes!");

        } catch (Exception e) {
            logger.error("Erreur lors du chargement de l'interface", e);

            Label label = new Label("ApotiCare - Erreur de chargement\n\n" +
                    "Erreur: " + e.getMessage());
            label.setStyle("-fx-font-size: 14px; -fx-text-alignment: left; -fx-font-family: monospace;");

            StackPane fallbackRoot = new StackPane(label);
            fallbackRoot.setStyle("-fx-padding: 40px; -fx-background-color: #f5f5f5;");

            Scene scene = new Scene(fallbackRoot, 550, 400);
            primaryStage.setTitle("ApotiCare - Erreur");
            primaryStage.setScene(scene);
            primaryStage.show();
        }
    }

    private void showDatabaseError(Stage primaryStage) {
        Label label = new Label("ApotiCare - Erreur de connexion\n\n" +
                "Impossible de se connecter a la base de donnees.\n\n" +
                "Verifiez que:\n" +
                "  - MySQL est demarre\n" +
                "  - La base 'sgpa_pharmacie' existe\n" +
                "  - Le fichier database.properties est configure\n\n" +
                "Commande: mysql -u root -p < sql/schema.sql");
        label.setStyle("-fx-font-size: 14px; -fx-text-alignment: left; -fx-font-family: monospace;");

        StackPane fallbackRoot = new StackPane(label);
        fallbackRoot.setStyle("-fx-padding: 40px; -fx-background-color: #f5f5f5;");

        Scene scene = new Scene(fallbackRoot, 550, 400);
        primaryStage.setTitle("ApotiCare - Erreur BDD");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    @Override
    public void stop() {
        logger.info("Arret de l'application...");
        DatabaseConnection.getInstance().shutdown();
    }
}
