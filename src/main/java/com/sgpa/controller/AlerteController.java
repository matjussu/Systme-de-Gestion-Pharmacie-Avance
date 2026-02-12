package com.sgpa.controller;

import com.sgpa.dto.AlertePeremption;
import com.sgpa.dto.AlerteStock;
import com.sgpa.model.Lot;
import com.sgpa.service.AlerteService;
import com.sgpa.service.RapportService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Controleur pour l'ecran des alertes.
 * Gere les onglets stock bas, peremption proche et lots perimes.
 *
 * @author SGPA Team
 * @version 1.0
 */
public class AlerteController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(AlerteController.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML private TabPane tabPane;

    // Stock bas
    @FXML private Label lblStockBasCount;
    @FXML private TableView<AlerteStock> tableStockBas;
    @FXML private TableColumn<AlerteStock, String> colStockMedicament;
    @FXML private TableColumn<AlerteStock, String> colStockActuel;
    @FXML private TableColumn<AlerteStock, String> colSeuilMin;
    @FXML private TableColumn<AlerteStock, String> colStockDeficit;

    // Peremption proche
    @FXML private Label lblPeremptionCount;
    @FXML private TableView<AlertePeremption> tablePeremption;
    @FXML private TableColumn<AlertePeremption, String> colPeremptionMedicament;
    @FXML private TableColumn<AlertePeremption, String> colPeremptionLot;
    @FXML private TableColumn<AlertePeremption, String> colPeremptionDate;
    @FXML private TableColumn<AlertePeremption, String> colPeremptionJours;
    @FXML private TableColumn<AlertePeremption, String> colPeremptionQuantite;
    @FXML private TableColumn<AlertePeremption, String> colPeremptionUrgence;

    // Lots perimes
    @FXML private Label lblPerimesCount;
    @FXML private TableView<Lot> tablePerimes;
    @FXML private TableColumn<Lot, String> colPerimesMedicament;
    @FXML private TableColumn<Lot, String> colPerimesLot;
    @FXML private TableColumn<Lot, String> colPerimesDate;
    @FXML private TableColumn<Lot, String> colPerimesQuantite;

    private final AlerteService alerteService;
    private final RapportService rapportService;
    private final ObservableList<AlerteStock> stockBasData = FXCollections.observableArrayList();
    private final ObservableList<AlertePeremption> peremptionData = FXCollections.observableArrayList();
    private final ObservableList<Lot> perimesData = FXCollections.observableArrayList();

    public AlerteController() {
        this.alerteService = new AlerteService();
        this.rapportService = new RapportService();
    }

    @FXML
    public void initialize() {
        setupStockBasTable();
        setupPeremptionTable();
        setupPerimesTable();
        setupResponsiveTable(tableStockBas);
        setupResponsiveTable(tablePeremption);
        setupResponsiveTable(tablePerimes);
        loadAllAlertes();
    }

    private void setupStockBasTable() {
        colStockMedicament.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getNomMedicament()));
        colStockActuel.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getStockActuel())));
        colSeuilMin.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getSeuilMin())));
        colStockDeficit.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getDeficit())));

        // Colorer le deficit en rouge
        colStockDeficit.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText("-" + item);
                    setStyle("-fx-text-fill: #dc3545; -fx-font-weight: bold;");
                }
            }
        });

        tableStockBas.setItems(stockBasData);
    }

    private void setupPeremptionTable() {
        colPeremptionMedicament.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getNomMedicament()));
        colPeremptionLot.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getNumeroLot()));
        colPeremptionDate.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getDatePeremption().format(DATE_FORMAT)));
        colPeremptionJours.setCellValueFactory(data -> new SimpleStringProperty(
                String.valueOf(data.getValue().getJoursRestants())));
        colPeremptionQuantite.setCellValueFactory(data -> new SimpleStringProperty(
                String.valueOf(data.getValue().getQuantiteStock())));
        colPeremptionUrgence.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getNiveauUrgence()));

        // Colorer l'urgence
        colPeremptionUrgence.setCellFactory(column -> new TableCell<>() {
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

        tablePeremption.setItems(peremptionData);
    }

    private void setupPerimesTable() {
        colPerimesMedicament.setCellValueFactory(data -> {
            Lot lot = data.getValue();
            String nom = lot.getMedicament() != null ? lot.getMedicament().getNomCommercial() : "Medicament #" + lot.getIdMedicament();
            return new SimpleStringProperty(nom);
        });
        colPerimesLot.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getNumeroLot()));
        colPerimesDate.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getDatePeremption().format(DATE_FORMAT)));
        colPerimesQuantite.setCellValueFactory(data -> new SimpleStringProperty(
                String.valueOf(data.getValue().getQuantiteStock())));

        tablePerimes.setItems(perimesData);
    }

    private void loadAllAlertes() {
        Task<Void> loadTask = new Task<>() {
            private List<AlerteStock> stockBas;
            private List<AlertePeremption> peremption;
            private List<Lot> perimes;

            @Override
            protected Void call() throws Exception {
                stockBas = alerteService.getAlertesStockBas();
                peremption = alerteService.getAlertesPeremption();
                perimes = alerteService.getLotsPerimes();
                return null;
            }

            @Override
            protected void succeeded() {
                stockBasData.setAll(stockBas);
                peremptionData.setAll(peremption);
                perimesData.setAll(perimes);

                lblStockBasCount.setText(stockBas.size() + " alerte(s)");
                lblPeremptionCount.setText(peremption.size() + " alerte(s)");
                lblPerimesCount.setText(perimes.size() + " lot(s) perime(s)");
            }

            @Override
            protected void failed() {
                logger.error("Erreur lors du chargement des alertes", getException());
            }
        };

        new Thread(loadTask).start();
    }

    @FXML
    private void handleRefresh() {
        loadAllAlertes();
    }

    /**
     * Exporte toutes les alertes en PDF.
     */
    @FXML
    private void handleExportAll() {
        exportPDF(() -> rapportService.genererRapportAlertesComplet(), "Rapport complet des alertes");
    }

    /**
     * Exporte les alertes de stock bas en PDF.
     */
    @FXML
    private void handleExportStockBas() {
        exportPDF(() -> rapportService.genererRapportAlertesStock(), "Alertes stock bas");
    }

    /**
     * Exporte les alertes de peremption en PDF.
     */
    @FXML
    private void handleExportPeremption() {
        exportPDF(() -> rapportService.genererRapportAlertesPeremption(), "Alertes peremption");
    }

    /**
     * Exporte les lots perimes en PDF.
     */
    @FXML
    private void handleExportPerimes() {
        exportPDF(() -> rapportService.genererRapportLotsPerimes(), "Lots perimes");
    }

    /**
     * Methode generique pour exporter un rapport PDF.
     */
    private void exportPDF(PDFExporter exporter, String titre) {
        Task<String> exportTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                return exporter.export();
            }
        };

        exportTask.setOnSucceeded(event -> {
            String filePath = exportTask.getValue();
            logger.info("Rapport genere: {}", filePath);

            try {
                File pdfFile = new File(filePath);
                if (Desktop.isDesktopSupported() && pdfFile.exists()) {
                    Desktop.getDesktop().open(pdfFile);
                }
                showAlert(Alert.AlertType.INFORMATION, "Export reussi",
                        titre + " exporte avec succes:\n" + filePath);
            } catch (Exception e) {
                logger.warn("Impossible d'ouvrir le PDF", e);
                showAlert(Alert.AlertType.INFORMATION, "Export reussi",
                        titre + " exporte:\n" + filePath);
            }
        });

        exportTask.setOnFailed(event -> {
            logger.error("Erreur lors de l'export", exportTask.getException());
            showAlert(Alert.AlertType.ERROR, "Erreur d'export",
                    "Impossible de generer le rapport PDF.");
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

    @FunctionalInterface
    private interface PDFExporter {
        String export() throws Exception;
    }
}
