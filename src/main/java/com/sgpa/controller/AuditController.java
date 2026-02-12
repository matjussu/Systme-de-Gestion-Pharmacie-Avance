package com.sgpa.controller;

import com.sgpa.model.AuditLog;
import com.sgpa.model.Utilisateur;
import com.sgpa.model.enums.TypeAction;
import com.sgpa.service.AuditService;
import com.sgpa.service.ExportService;
import com.sgpa.service.UtilisateurService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Controleur pour l'ecran du journal d'audit.
 * Permet de consulter et rechercher les actions enregistrees.
 *
 * @author SGPA Team
 * @version 1.0
 */
public class AuditController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(AuditController.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final int PAGE_SIZE = 50;

    // Filtres
    @FXML private DatePicker dateDebut;
    @FXML private DatePicker dateFin;
    @FXML private ComboBox<TypeAction> comboTypeAction;
    @FXML private ComboBox<String> comboEntite;
    @FXML private ComboBox<Utilisateur> comboUtilisateur;

    // Table
    @FXML private Label lblCount;
    @FXML private TableView<AuditLog> tableAudit;
    @FXML private TableColumn<AuditLog, String> colDate;
    @FXML private TableColumn<AuditLog, String> colUtilisateur;
    @FXML private TableColumn<AuditLog, String> colTypeAction;
    @FXML private TableColumn<AuditLog, String> colEntite;
    @FXML private TableColumn<AuditLog, String> colDescription;

    // Pagination
    @FXML private Button btnPrevious;
    @FXML private Button btnNext;
    @FXML private Label lblPage;

    private final AuditService auditService;
    private final UtilisateurService utilisateurService;
    private final ExportService exportService;
    private final ObservableList<AuditLog> auditData = FXCollections.observableArrayList();

    private int currentPage = 0;
    private long totalEntries = 0;

    public AuditController() {
        this.auditService = new AuditService();
        this.utilisateurService = new UtilisateurService();
        this.exportService = new ExportService();
    }

    @FXML
    public void initialize() {
        setupTable();
        setupFilters();
        setupResponsiveTable(tableAudit);
        loadData();
    }

    private void setupFilters() {
        // Types d'action
        ObservableList<TypeAction> types = FXCollections.observableArrayList();
        types.add(null); // Pour "Tous"
        types.addAll(TypeAction.values());
        comboTypeAction.setItems(types);
        comboTypeAction.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(TypeAction type) {
                return type != null ? type.getLibelle() : "Tous";
            }

            @Override
            public TypeAction fromString(String string) {
                return null;
            }
        });

        // Entites
        comboEntite.setItems(FXCollections.observableArrayList(
                null, "UTILISATEUR", "MEDICAMENT", "LOT", "VENTE", "COMMANDE", "FOURNISSEUR"
        ));
        comboEntite.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(String entite) {
                return entite != null ? entite : "Toutes";
            }

            @Override
            public String fromString(String string) {
                return string;
            }
        });

        // Utilisateurs
        loadUtilisateurs();

        // Dates par defaut: 30 derniers jours
        dateFin.setValue(LocalDate.now());
        dateDebut.setValue(LocalDate.now().minusDays(30));
    }

    private void loadUtilisateurs() {
        Task<List<Utilisateur>> task = new Task<>() {
            @Override
            protected List<Utilisateur> call() throws Exception {
                return utilisateurService.getAllUtilisateurs();
            }

            @Override
            protected void succeeded() {
                ObservableList<Utilisateur> users = FXCollections.observableArrayList();
                users.add(null); // Pour "Tous"
                users.addAll(getValue());
                comboUtilisateur.setItems(users);
                comboUtilisateur.setConverter(new javafx.util.StringConverter<>() {
                    @Override
                    public String toString(Utilisateur user) {
                        return user != null ? user.getNomComplet() : "Tous";
                    }

                    @Override
                    public Utilisateur fromString(String string) {
                        return null;
                    }
                });
            }
        };
        new Thread(task).start();
    }

    private void setupTable() {
        colDate.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getDateAction() != null
                        ? data.getValue().getDateAction().format(DATE_TIME_FORMATTER)
                        : ""));

        colUtilisateur.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getNomUtilisateur() != null
                        ? data.getValue().getNomUtilisateur()
                        : "Systeme"));

        colTypeAction.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getTypeAction() != null
                        ? data.getValue().getTypeAction().getLibelle()
                        : ""));

        colEntite.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getEntite() != null
                        ? data.getValue().getEntite()
                        : ""));

        colDescription.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getDescription() != null
                        ? data.getValue().getDescription()
                        : ""));

        // Colorisation par type d'action
        colTypeAction.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    switch (item) {
                        case "Connexion", "Deconnexion" -> setStyle("-fx-text-fill: #17a2b8;");
                        case "Creation" -> setStyle("-fx-text-fill: #28a745; -fx-font-weight: bold;");
                        case "Modification" -> setStyle("-fx-text-fill: #fd7e14;");
                        case "Suppression" -> setStyle("-fx-text-fill: #dc3545; -fx-font-weight: bold;");
                        case "Vente" -> setStyle("-fx-text-fill: #6f42c1; -fx-font-weight: bold;");
                        case "Commande", "Reception" -> setStyle("-fx-text-fill: #20c997;");
                        default -> setStyle("");
                    }
                }
            }
        });

        tableAudit.setItems(auditData);
    }

    private void loadData() {
        Task<Void> task = new Task<>() {
            private List<AuditLog> logs;
            private long count;

            @Override
            protected Void call() throws Exception {
                // Recuperer les filtres
                LocalDate debut = dateDebut.getValue();
                LocalDate fin = dateFin.getValue();
                TypeAction type = comboTypeAction.getValue();
                String entite = comboEntite.getValue();
                Utilisateur user = comboUtilisateur.getValue();
                Integer userId = user != null ? user.getIdUtilisateur() : null;

                logs = auditService.search(debut, fin, type, entite, userId, PAGE_SIZE * (currentPage + 1));
                count = auditService.countLogs();

                // Garder seulement la page courante
                int start = currentPage * PAGE_SIZE;
                int end = Math.min(start + PAGE_SIZE, logs.size());
                if (start < logs.size()) {
                    logs = logs.subList(start, end);
                }

                return null;
            }

            @Override
            protected void succeeded() {
                auditData.setAll(logs);
                totalEntries = count;
                updatePagination();
                lblCount.setText(count + " entrees au total");
            }

            @Override
            protected void failed() {
                logger.error("Erreur chargement audit", getException());
                showAlert(Alert.AlertType.ERROR, "Erreur",
                        "Impossible de charger le journal: " + getException().getMessage());
            }
        };
        new Thread(task).start();
    }

    private void updatePagination() {
        int totalPages = (int) Math.ceil((double) totalEntries / PAGE_SIZE);
        lblPage.setText("Page " + (currentPage + 1) + " / " + Math.max(1, totalPages));
        btnPrevious.setDisable(currentPage == 0);
        btnNext.setDisable((currentPage + 1) * PAGE_SIZE >= totalEntries);
    }

    @FXML
    private void handleSearch() {
        currentPage = 0;
        loadData();
    }

    @FXML
    private void handleReset() {
        dateDebut.setValue(LocalDate.now().minusDays(30));
        dateFin.setValue(LocalDate.now());
        comboTypeAction.setValue(null);
        comboEntite.setValue(null);
        comboUtilisateur.setValue(null);
        currentPage = 0;
        loadData();
    }

    @FXML
    private void handleRefresh() {
        loadData();
    }

    @FXML
    private void handlePrevious() {
        if (currentPage > 0) {
            currentPage--;
            loadData();
        }
    }

    @FXML
    private void handleNext() {
        if ((currentPage + 1) * PAGE_SIZE < totalEntries) {
            currentPage++;
            loadData();
        }
    }

    @FXML
    private void handleExport() {
        // Recuperer les filtres actuels pour exporter les donnees filtrees
        LocalDate debut = dateDebut.getValue();
        LocalDate fin = dateFin.getValue();
        TypeAction type = comboTypeAction.getValue();
        String entite = comboEntite.getValue();
        Utilisateur user = comboUtilisateur.getValue();
        Integer userId = user != null ? user.getIdUtilisateur() : null;

        Task<String> exportTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                // Recuperer toutes les entrees filtrees (sans pagination)
                List<AuditLog> logs = auditService.search(debut, fin, type, entite, userId, Integer.MAX_VALUE);
                return exportService.exportAudit(logs);
            }
        };

        exportTask.setOnSucceeded(event -> {
            String filePath = exportTask.getValue();
            showAlert(Alert.AlertType.INFORMATION, "Export reussi",
                    "Le journal d'audit a ete exporte vers:\n" + filePath);
        });

        exportTask.setOnFailed(event -> {
            logger.error("Erreur lors de l'export CSV", exportTask.getException());
            showAlert(Alert.AlertType.ERROR, "Erreur",
                    "Une erreur est survenue lors de l'export CSV.");
        });

        new Thread(exportTask).start();
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
