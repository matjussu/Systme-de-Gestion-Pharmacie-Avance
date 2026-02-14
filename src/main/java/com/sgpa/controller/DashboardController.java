package com.sgpa.controller;

import com.sgpa.dao.MedicamentDAO;
import com.sgpa.dao.VenteDAO;
import com.sgpa.dao.impl.MedicamentDAOImpl;
import com.sgpa.dao.impl.VenteDAOImpl;
import com.sgpa.dto.AlertePeremption;
import com.sgpa.dto.AlerteStock;
import com.sgpa.model.Utilisateur;
import com.sgpa.model.Vente;
import com.sgpa.service.AlerteService;
import com.sgpa.service.AuthenticationService;
import com.sgpa.utils.AnimationUtils;
import com.sgpa.utils.DialogHelper;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.animation.Interpolator;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.Node;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Controleur principal du tableau de bord.
 * Gere la navigation et l'affichage des statistiques.
 *
 * @author SGPA Team
 * @version 1.0
 */
public class DashboardController {

    private static final Logger logger = LoggerFactory.getLogger(DashboardController.class);

    // Sauvegarde du contenu initial du dashboard pour restauration
    private Node dashboardContent;

    // Cache des vues FXML deja chargees
    private final Map<String, Parent> viewCache = new HashMap<>();
    private final Map<String, Object> controllerCache = new HashMap<>();

    @FXML private Label lblUserName;
    @FXML private Label lblUserRole;
    @FXML private Label lblPageTitle;
    @FXML private Label lblPageSubtitle;
    @FXML private Label lblDate;
    @FXML private Label lblTotalMedicaments;
    @FXML private Label lblVentesJour;
    @FXML private Label lblAlertesStock;
    @FXML private Label lblAlertesPeremption;

    @FXML private StackPane contentArea;
    @FXML private HBox statsRow;
    @FXML private TableView<AlerteDTO> tableAlertes;
    @FXML private TableColumn<AlerteDTO, String> colAlertType;
    @FXML private TableColumn<AlerteDTO, String> colAlertMedicament;
    @FXML private TableColumn<AlerteDTO, String> colAlertDetail;
    @FXML private TableColumn<AlerteDTO, String> colAlertUrgence;

    @FXML private Button btnDashboard;
    @FXML private Button btnVentes;
    @FXML private Button btnHistorique;
    @FXML private Button btnRetours;
    @FXML private Button btnProduitsStock;
    @FXML private Button btnInventaire;
    @FXML private Button btnCommandes;
    @FXML private Button btnAlertes;
    @FXML private Button btnPredictions;
    @FXML private Button btnStatistiques;
    @FXML private Button btnUtilisateurs;
    @FXML private Button btnAudit;
    @FXML private Button btnBackup;
    @FXML private Button btnSettings;

    private Utilisateur currentUser;
    private AuthenticationService authService;
    private final AlerteService alerteService;
    private final MedicamentDAO medicamentDAO;
    private final VenteDAO venteDAO;

    public DashboardController() {
        this.alerteService = new AlerteService();
        this.medicamentDAO = new MedicamentDAOImpl();
        this.venteDAO = new VenteDAOImpl();
    }

    @FXML
    public void initialize() {
        setupDateLabel();
        setupAlertsTable();
        // Sauvegarder le contenu initial du dashboard
        Platform.runLater(() -> {
            if (!contentArea.getChildren().isEmpty()) {
                dashboardContent = contentArea.getChildren().get(0);
            }
            // Appliquer hover scale et click handlers sur les stat cards
            if (statsRow != null) {
                List<Runnable> cardActions = List.of(
                    this::showProduitsStock,  // Card 0: Medicaments
                    this::showHistorique,     // Card 1: Ventes aujourd'hui
                    this::showAlertes,        // Card 2: Alertes Stock
                    this::showAlertes         // Card 3: Peremptions proches
                );
                var children = statsRow.getChildren();
                for (int i = 0; i < children.size(); i++) {
                    Node card = children.get(i);
                    card.setCursor(javafx.scene.Cursor.HAND);
                    int index = i;
                    card.setOnMouseClicked(e -> cardActions.get(index).run());
                }
            }
        });
    }

    public void setCurrentUser(Utilisateur user) {
        this.currentUser = user;
        updateUserInfo();
        loadDashboardData();
    }

    public void setAuthService(AuthenticationService authService) {
        this.authService = authService;
    }

    private void setupDateLabel() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE dd MMMM yyyy", Locale.FRENCH);
        String date = LocalDate.now().format(formatter);
        // Capitaliser la premiere lettre
        lblDate.setText(date.substring(0, 1).toUpperCase() + date.substring(1));
    }

    private void setupAlertsTable() {
        colAlertType.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().type));
        colAlertMedicament.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().medicament));
        colAlertDetail.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().detail));
        colAlertUrgence.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().urgence));

        // Colorer les lignes selon l'urgence
        colAlertUrgence.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    switch (item) {
                        case "CRITIQUE" -> setStyle("-fx-text-fill: #dc3545; -fx-font-weight: bold;");
                        case "URGENT" -> setStyle("-fx-text-fill: #fd7e14; -fx-font-weight: bold;");
                        default -> setStyle("-fx-text-fill: #ffc107;");
                    }
                }
            }
        });

        // Colonne Action avec bouton Commander
        TableColumn<AlerteDTO, Void> colAction = new TableColumn<>("Action");
        colAction.setCellFactory(column -> new TableCell<>() {
            private final Button btnCommander = new Button("Commander");
            {
                btnCommander.getStyleClass().addAll("action-button-small");
                btnCommander.setStyle("-fx-background-color: #166534; -fx-text-fill: white; -fx-cursor: hand; -fx-padding: 4 10; -fx-background-radius: 6; -fx-font-size: 11px;");
                btnCommander.setOnAction(e -> {
                    AlerteDTO alerte = getTableView().getItems().get(getIndex());
                    navigateToCommandeWithMedicament(alerte.medicament);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btnCommander);
            }
        });
        tableAlertes.getColumns().add(colAction);

        // Remplir toute la largeur
        tableAlertes.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
    }

    public void navigateToCommandeWithMedicament(String medicamentName) {
        setActiveButton(btnCommandes);
        lblPageTitle.setText("Commandes Fournisseurs");
        lblPageSubtitle.setText("Gestion des approvisionnements");
        Object controller = loadViewAndGetController("/fxml/commandes.fxml");
        if (controller instanceof CommandeController commandeCtrl) {
            commandeCtrl.prefillMedicament(medicamentName);
        }
    }

    private void updateUserInfo() {
        if (currentUser != null) {
            lblUserName.setText(currentUser.getNomComplet());
            lblUserRole.setText(currentUser.getRole().getLibelle());
        }
    }

    private void loadDashboardData() {
        Task<Void> loadTask = new Task<>() {
            private long totalMedicaments;
            private long ventesJour;
            private int alertesStock;
            private int alertesPeremption;
            private ObservableList<AlerteDTO> alertes;

            @Override
            protected Void call() throws Exception {
                // Charger les statistiques
                totalMedicaments = medicamentDAO.count();

                List<Vente> ventesAujourdhui = venteDAO.findByDate(LocalDate.now());
                ventesJour = ventesAujourdhui.size();

                List<AlerteStock> stockAlertes = alerteService.getAlertesStockBas();
                alertesStock = stockAlertes.size();

                List<AlertePeremption> peremptionAlertes = alerteService.getAlertesPeremption();
                alertesPeremption = peremptionAlertes.size();

                // Preparer les donnees pour la table
                alertes = FXCollections.observableArrayList();

                for (AlerteStock a : stockAlertes) {
                    alertes.add(new AlerteDTO(
                            "STOCK BAS",
                            a.getNomMedicament(),
                            "Stock: " + a.getStockActuel() + " / Seuil: " + a.getSeuilMin(),
                            "URGENT"
                    ));
                }

                for (AlertePeremption a : peremptionAlertes) {
                    alertes.add(new AlerteDTO(
                            "PEREMPTION",
                            a.getNomMedicament(),
                            "Lot " + a.getNumeroLot() + " expire dans " + a.getJoursRestants() + " jours",
                            a.getNiveauUrgence()
                    ));
                }

                return null;
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    // Compteurs animes
                    AnimationUtils.animateCounter(lblTotalMedicaments, 0, (int) totalMedicaments, Duration.millis(800));
                    AnimationUtils.animateCounter(lblVentesJour, 0, (int) ventesJour, Duration.millis(800));
                    AnimationUtils.animateCounter(lblAlertesStock, 0, alertesStock, Duration.millis(800));
                    AnimationUtils.animateCounter(lblAlertesPeremption, 0, alertesPeremption, Duration.millis(800));
                    // Trier par urgence: CRITIQUE > URGENT > reste
                    alertes.sort((a1, a2) -> {
                        int p1 = urgencePriority(a1.urgence);
                        int p2 = urgencePriority(a2.urgence);
                        return Integer.compare(p1, p2);
                    });
                    tableAlertes.setItems(alertes);
                });
            }

            @Override
            protected void failed() {
                logger.error("Erreur lors du chargement des donnees", getException());
            }
        };

        BaseController.getExecutor().submit(loadTask);
    }

    @FXML
    private void refreshData() {
        loadDashboardData();
    }

    @FXML
    private void showDashboard() {
        setActiveButton(btnDashboard);
        lblPageTitle.setText("Tableau de Bord");
        lblPageSubtitle.setText("Vue d'ensemble de votre pharmacie");
        // Restaurer le contenu du dashboard (reset opacity apres fade-out de loadView)
        if (dashboardContent != null) {
            dashboardContent.setOpacity(1.0);
            dashboardContent.setTranslateX(0);
            contentArea.getChildren().setAll(dashboardContent);
            animateViewIn(dashboardContent);
        }
        loadDashboardData();
    }

    @FXML
    private void showVentes() {
        setActiveButton(btnVentes);
        lblPageTitle.setText("Nouvelle Vente");
        lblPageSubtitle.setText("Enregistrer une vente avec algorithme FEFO");
        loadView("/fxml/vente.fxml");
    }

    @FXML
    private void showHistorique() {
        setActiveButton(btnHistorique);
        lblPageTitle.setText("Historique des Ventes");
        lblPageSubtitle.setText("Consulter les ventes passees");
        loadView("/fxml/historique.fxml");
    }

    @FXML
    private void showRetours() {
        setActiveButton(btnRetours);
        lblPageTitle.setText("Retours Produits");
        lblPageSubtitle.setText("Gestion des retours et reintegration stock");
        loadView("/fxml/retours.fxml");
    }

    @FXML
    private void showProduitsStock() {
        setActiveButton(btnProduitsStock);
        lblPageTitle.setText("Produits & Stock");
        lblPageSubtitle.setText("Catalogue des medicaments et gestion des lots");
        loadView("/fxml/produits_stock.fxml");
    }

    @FXML
    private void showInventaire() {
        setActiveButton(btnInventaire);
        lblPageTitle.setText("Inventaire");
        lblPageSubtitle.setText("Comptage physique et regularisation des ecarts");
        loadView("/fxml/inventaire.fxml");
    }

    @FXML
    private void showCommandes() {
        setActiveButton(btnCommandes);
        lblPageTitle.setText("Commandes Fournisseurs");
        lblPageSubtitle.setText("Gestion des approvisionnements");
        loadView("/fxml/commandes.fxml");
    }

    @FXML
    private void showAlertes() {
        setActiveButton(btnAlertes);
        lblPageTitle.setText("Alertes");
        lblPageSubtitle.setText("Stock bas et peremptions proches");
        loadView("/fxml/alertes.fxml");
    }

    @FXML
    private void showPredictions() {
        setActiveButton(btnPredictions);
        lblPageTitle.setText("Predictions");
        lblPageSubtitle.setText("Previsions de reapprovisionnement");
        loadView("/fxml/predictions.fxml");
    }

    @FXML
    private void showStatistiques() {
        setActiveButton(btnStatistiques);
        lblPageTitle.setText("Statistiques");
        lblPageSubtitle.setText("Graphiques et analyses des ventes");
        loadView("/fxml/statistiques.fxml");
    }

    @FXML
    private void showUtilisateurs() {
        // Verifier que l'utilisateur a les droits d'administration
        if (currentUser == null || !currentUser.isAdmin()) {
            DialogHelper.showWarning(contentArea, "Acces refuse",
                    "Seuls les pharmaciens peuvent acceder a la gestion des utilisateurs.");
            return;
        }
        setActiveButton(btnUtilisateurs);
        lblPageTitle.setText("Gestion des Utilisateurs");
        lblPageSubtitle.setText("Administration des comptes utilisateurs");
        loadView("/fxml/utilisateurs.fxml");
    }

    @FXML
    private void showAudit() {
        // Verifier que l'utilisateur a les droits d'administration
        if (currentUser == null || !currentUser.isAdmin()) {
            DialogHelper.showWarning(contentArea, "Acces refuse",
                    "Seuls les pharmaciens peuvent acceder au journal d'audit.");
            return;
        }
        setActiveButton(btnAudit);
        lblPageTitle.setText("Journal d'Audit");
        lblPageSubtitle.setText("Historique des actions du systeme");
        loadView("/fxml/audit.fxml");
    }

    @FXML
    private void showBackup() {
        // Verifier que l'utilisateur a les droits d'administration
        if (currentUser == null || !currentUser.isAdmin()) {
            DialogHelper.showWarning(contentArea, "Acces refuse",
                    "Seuls les pharmaciens peuvent acceder aux sauvegardes.");
            return;
        }
        setActiveButton(btnBackup);
        lblPageTitle.setText("Sauvegardes");
        lblPageSubtitle.setText("Sauvegarde et restauration de la base de donnees");
        loadView("/fxml/backup.fxml");
    }

    @FXML
    private void showSettings() {
        // Verifier que l'utilisateur a les droits d'administration
        if (currentUser == null || !currentUser.isAdmin()) {
            DialogHelper.showWarning(contentArea, "Acces refuse",
                    "Seuls les pharmaciens peuvent acceder aux parametres.");
            return;
        }
        setActiveButton(btnSettings);
        lblPageTitle.setText("Parametres");
        lblPageSubtitle.setText("Configuration de l'application");
        loadView("/fxml/settings.fxml");
    }

    private void loadView(String fxmlPath) {
        try {
            Parent view;
            Object controller;

            if (viewCache.containsKey(fxmlPath)) {
                view = viewCache.get(fxmlPath);
                controller = controllerCache.get(fxmlPath);
                if (controller instanceof BaseController baseCtrl) {
                    baseCtrl.onViewDisplayed();
                }
            } else {
                FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
                view = loader.load();
                controller = loader.getController();

                if (controller instanceof BaseController baseCtrl) {
                    baseCtrl.setCurrentUser(currentUser);
                    baseCtrl.setDashboardController(this);
                }

                if (view instanceof Region region) {
                    region.setMaxWidth(Double.MAX_VALUE);
                    region.setMaxHeight(Double.MAX_VALUE);
                }

                viewCache.put(fxmlPath, view);
                controllerCache.put(fxmlPath, controller);
            }

            showView(view);

        } catch (IOException e) {
            logger.error("Erreur lors du chargement de la vue: {}", fxmlPath, e);
            DialogHelper.showError(contentArea, "Erreur", "Impossible de charger la vue");
        }
    }

    private void showView(Node view) {
        view.setOpacity(1);
        view.setTranslateX(0);
        contentArea.getChildren().setAll(view);
        animateViewIn(view);
    }

    private Object loadViewAndGetController(String fxmlPath) {
        try {
            Parent view;
            Object controller;

            if (viewCache.containsKey(fxmlPath)) {
                view = viewCache.get(fxmlPath);
                controller = controllerCache.get(fxmlPath);
                if (controller instanceof BaseController baseCtrl) {
                    baseCtrl.onViewDisplayed();
                }
            } else {
                FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
                view = loader.load();
                controller = loader.getController();

                if (controller instanceof BaseController baseCtrl) {
                    baseCtrl.setCurrentUser(currentUser);
                    baseCtrl.setDashboardController(this);
                }

                if (view instanceof Region region) {
                    region.setMaxWidth(Double.MAX_VALUE);
                    region.setMaxHeight(Double.MAX_VALUE);
                }

                viewCache.put(fxmlPath, view);
                controllerCache.put(fxmlPath, controller);
            }

            showView(view);
            return controller;
        } catch (IOException e) {
            logger.error("Erreur lors du chargement de la vue: {}", fxmlPath, e);
            DialogHelper.showError(contentArea, "Erreur", "Impossible de charger la vue");
            return null;
        }
    }

    /**
     * Retourne l'utilisateur actuellement connecte.
     */
    public Utilisateur getCurrentUser() {
        return currentUser;
    }

    private void animateViewIn(Node view) {
        view.setOpacity(0);
        view.setTranslateX(15);
        FadeTransition fade = new FadeTransition(Duration.millis(100), view);
        fade.setFromValue(0);
        fade.setToValue(1);
        TranslateTransition slide = new TranslateTransition(Duration.millis(100), view);
        slide.setFromX(15);
        slide.setToX(0);
        slide.setInterpolator(Interpolator.EASE_OUT);
        fade.play();
        slide.play();
    }

    private void setActiveButton(Button activeBtn) {
        // Retirer la classe active de tous les boutons
        btnDashboard.getStyleClass().remove("active");
        btnVentes.getStyleClass().remove("active");
        btnHistorique.getStyleClass().remove("active");
        btnProduitsStock.getStyleClass().remove("active");
        btnCommandes.getStyleClass().remove("active");
        btnAlertes.getStyleClass().remove("active");
        if (btnRetours != null) {
            btnRetours.getStyleClass().remove("active");
        }
        if (btnInventaire != null) {
            btnInventaire.getStyleClass().remove("active");
        }
        if (btnPredictions != null) {
            btnPredictions.getStyleClass().remove("active");
        }
        if (btnStatistiques != null) {
            btnStatistiques.getStyleClass().remove("active");
        }
        if (btnUtilisateurs != null) {
            btnUtilisateurs.getStyleClass().remove("active");
        }
        if (btnAudit != null) {
            btnAudit.getStyleClass().remove("active");
        }
        if (btnBackup != null) {
            btnBackup.getStyleClass().remove("active");
        }
        if (btnSettings != null) {
            btnSettings.getStyleClass().remove("active");
        }

        // Ajouter la classe active au bouton selectionne
        if (!activeBtn.getStyleClass().contains("active")) {
            activeBtn.getStyleClass().add("active");
        }
    }

    @FXML
    private void handleLogout() {
        DialogHelper.showConfirmation(contentArea,
                "Deconnexion",
                "Voulez-vous vraiment vous deconnecter ?",
                () -> {
                    if (authService != null) {
                        authService.logout();
                    }
                    returnToLogin();
                },
                null
        );
    }

    private void returnToLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Parent root = loader.load();

            Scene scene = new Scene(root, 520, 680);
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());

            Rectangle2D sb = Screen.getPrimary().getVisualBounds();
            Stage stage = (Stage) contentArea.getScene().getWindow();
            stage.setMaximized(false);
            stage.setScene(scene);
            stage.setTitle("ApotiCare - Connexion");
            stage.setMinWidth(480);
            stage.setMinHeight(600);
            stage.setWidth(520);
            stage.setHeight(680);
            stage.setX((sb.getWidth() - 520) / 2);
            stage.setY((sb.getHeight() - 680) / 2);

        } catch (IOException e) {
            logger.error("Erreur lors du retour a l'ecran de connexion", e);
        }
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        switch (type) {
            case ERROR:
                DialogHelper.showError(contentArea, title, message);
                break;
            case WARNING:
                DialogHelper.showWarning(contentArea, title, message);
                break;
            default:
                DialogHelper.showInfo(contentArea, title, message);
                break;
        }
    }

    private static int urgencePriority(String urgence) {
        return switch (urgence) {
            case "CRITIQUE" -> 0;
            case "URGENT" -> 1;
            case "PERIME" -> 2;
            default -> 3;
        };
    }

    /**
     * DTO interne pour l'affichage des alertes dans la table.
     */
    public static class AlerteDTO {
        public final String type;
        public final String medicament;
        public final String detail;
        public final String urgence;

        public AlerteDTO(String type, String medicament, String detail, String urgence) {
            this.type = type;
            this.medicament = medicament;
            this.detail = detail;
            this.urgence = urgence;
        }
    }
}
