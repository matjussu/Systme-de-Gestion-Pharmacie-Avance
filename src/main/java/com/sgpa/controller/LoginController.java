package com.sgpa.controller;

import com.sgpa.exception.ServiceException;
import com.sgpa.model.Utilisateur;
import com.sgpa.service.AuthenticationService;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;


/**
 * Controleur pour l'ecran de connexion.
 * Gere l'authentification des utilisateurs via BCrypt.
 *
 * @author SGPA Team
 * @version 1.0
 */
public class LoginController {

    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label errorLabel;

    @FXML
    private Button loginButton;

    @FXML
    private ProgressIndicator loadingIndicator;

    @FXML
    private VBox loginFormContainer;

    @FXML
    private BorderPane loginRoot;

    @FXML
    private VBox loginHeader;

    @FXML
    private VBox loginForm;

    @FXML
    private HBox versionInfo;

    private final AuthenticationService authService;

    public LoginController() {
        this.authService = new AuthenticationService();
    }

    @FXML
    public void initialize() {
        // Animation d'entree echelonnee
        Platform.runLater(() -> {
            // Animation echelonnee des elements
            if (loginFormContainer != null) {
                loginFormContainer.setOpacity(1);

                // Cacher les elements individuellement
                if (loginHeader != null) {
                    loginHeader.setOpacity(0);
                    loginHeader.setScaleX(0.85);
                    loginHeader.setScaleY(0.85);
                }
                if (loginForm != null) {
                    loginForm.setOpacity(0);
                    loginForm.setTranslateY(40);
                }
                if (versionInfo != null) {
                    versionInfo.setOpacity(0);
                }

                // Sequence d'animation
                Timeline sequence = new Timeline();

                // 1. Logo + titre: scale + fade (300ms)
                if (loginHeader != null) {
                    sequence.getKeyFrames().addAll(
                            new KeyFrame(Duration.millis(100),
                                    new KeyValue(loginHeader.opacityProperty(), 0),
                                    new KeyValue(loginHeader.scaleXProperty(), 0.85),
                                    new KeyValue(loginHeader.scaleYProperty(), 0.85)),
                            new KeyFrame(Duration.millis(500),
                                    new KeyValue(loginHeader.opacityProperty(), 1, Interpolator.EASE_OUT),
                                    new KeyValue(loginHeader.scaleXProperty(), 1, Interpolator.EASE_OUT),
                                    new KeyValue(loginHeader.scaleYProperty(), 1, Interpolator.EASE_OUT))
                    );
                }

                // 2. Formulaire: slide up + fade (400ms delay)
                if (loginForm != null) {
                    sequence.getKeyFrames().addAll(
                            new KeyFrame(Duration.millis(350),
                                    new KeyValue(loginForm.opacityProperty(), 0),
                                    new KeyValue(loginForm.translateYProperty(), 40)),
                            new KeyFrame(Duration.millis(800),
                                    new KeyValue(loginForm.opacityProperty(), 1, Interpolator.EASE_OUT),
                                    new KeyValue(loginForm.translateYProperty(), 0, Interpolator.EASE_OUT))
                    );
                }

                // 3. Version info: fade (600ms delay)
                if (versionInfo != null) {
                    sequence.getKeyFrames().addAll(
                            new KeyFrame(Duration.millis(700),
                                    new KeyValue(versionInfo.opacityProperty(), 0)),
                            new KeyFrame(Duration.millis(1000),
                                    new KeyValue(versionInfo.opacityProperty(), 1, Interpolator.EASE_OUT))
                    );
                }

                sequence.play();
            }
            usernameField.requestFocus();
        });

        // Focus style sur les conteneurs input
        setupFocusStyle(usernameField);
        setupFocusStyle(passwordField);
    }

    private void setupFocusStyle(TextField field) {
        field.focusedProperty().addListener((obs, oldVal, newVal) -> {
            HBox container = (HBox) field.getParent();
            if (newVal) {
                container.getStyleClass().add("input-container-focused");
            } else {
                container.getStyleClass().remove("input-container-focused");
            }
        });
    }

    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        // Validation
        if (username.isEmpty() || password.isEmpty()) {
            showError("Veuillez remplir tous les champs");
            return;
        }

        // Desactiver le formulaire pendant l'authentification
        setFormDisabled(true);
        hideError();

        // Tache asynchrone pour ne pas bloquer l'UI
        Task<Utilisateur> loginTask = new Task<>() {
            @Override
            protected Utilisateur call() throws Exception {
                return authService.authenticate(username, password);
            }
        };

        loginTask.setOnSucceeded(event -> {
            Utilisateur user = loginTask.getValue();
            logger.info("Connexion reussie: {}", user.getNomComplet());
            openDashboard(user);
        });

        loginTask.setOnFailed(event -> {
            Throwable exception = loginTask.getException();
            logger.warn("Echec de connexion: {}", exception.getMessage());

            if (exception instanceof ServiceException) {
                showError(exception.getMessage());
            } else {
                showError("Erreur de connexion au serveur");
            }
            setFormDisabled(false);
            passwordField.clear();
            passwordField.requestFocus();
        });

        new Thread(loginTask).start();
    }

    private void openDashboard(Utilisateur user) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/dashboard.fxml"));
            Parent root = loader.load();

            // Passer l'utilisateur au dashboard
            DashboardController dashboardController = loader.getController();
            dashboardController.setCurrentUser(user);
            dashboardController.setAuthService(authService);

            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());

            Stage stage = (Stage) loginButton.getScene().getWindow();
            stage.setTitle("ApotiCare - " + user.getNomComplet());
            stage.setMinWidth(800);
            stage.setMinHeight(600);
            stage.setScene(scene);
            stage.setMaximized(true);
            javafx.geometry.Rectangle2D screenBounds = javafx.stage.Screen.getPrimary().getVisualBounds();
            stage.setWidth(screenBounds.getWidth());
            stage.setHeight(screenBounds.getHeight());
            stage.setX(screenBounds.getMinX());
            stage.setY(screenBounds.getMinY());

        } catch (IOException e) {
            logger.error("Erreur lors du chargement du dashboard", e);
            showError("Erreur lors du chargement de l'application");
            setFormDisabled(false);
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    private void setFormDisabled(boolean disabled) {
        usernameField.setDisable(disabled);
        passwordField.setDisable(disabled);
        loginButton.setDisable(disabled);
        loadingIndicator.setVisible(disabled);
        loadingIndicator.setManaged(disabled);
    }
}
