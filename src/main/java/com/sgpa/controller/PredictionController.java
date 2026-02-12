package com.sgpa.controller;

import com.sgpa.dto.PredictionReapprovisionnement;
import com.sgpa.exception.ServiceException;
import com.sgpa.service.ConfigService;
import com.sgpa.service.PredictionService;
import com.sgpa.service.RapportService;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Controleur pour la vue des predictions de reapprovisionnement.
 *
 * @author SGPA Team
 * @version 1.0
 */
public class PredictionController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(PredictionController.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML private ComboBox<String> comboPeriode;
    @FXML private TextField txtRecherche;
    @FXML private Button btnCreerCommande;
    @FXML private Button btnCommanderDetail;

    // Labels statistiques
    @FXML private Label lblTotal;
    @FXML private Label lblRuptures;
    @FXML private Label lblCritiques;
    @FXML private Label lblUrgents;

    // Table des predictions
    @FXML private TableView<PredictionReapprovisionnement> tablePredictions;
    @FXML private TableColumn<PredictionReapprovisionnement, String> colMedicament;
    @FXML private TableColumn<PredictionReapprovisionnement, Number> colStockActuel;
    @FXML private TableColumn<PredictionReapprovisionnement, String> colConsoJour;
    @FXML private TableColumn<PredictionReapprovisionnement, String> colJoursRestants;
    @FXML private TableColumn<PredictionReapprovisionnement, String> colDateRupture;
    @FXML private TableColumn<PredictionReapprovisionnement, Number> colQuantiteSuggeree;
    @FXML private TableColumn<PredictionReapprovisionnement, String> colUrgence;

    // Detail
    @FXML private Label lblDetailTitre;
    @FXML private Label lblDetailStock;
    @FXML private Label lblDetailConsoMois;
    @FXML private Label lblDetailSeuil;
    @FXML private Label lblDetailDelai;

    // Graphique
    @FXML private LineChart<String, Number> chartPrevision;
    @FXML private CategoryAxis xAxis;
    @FXML private NumberAxis yAxis;

    private final PredictionService predictionService;
    private final ConfigService configService;
    private final RapportService rapportService;

    private ObservableList<PredictionReapprovisionnement> predictions;
    private FilteredList<PredictionReapprovisionnement> filteredPredictions;

    public PredictionController() {
        this.predictionService = new PredictionService();
        this.configService = new ConfigService();
        this.rapportService = new RapportService();
    }

    @FXML
    public void initialize() {
        setupComboBox();
        setupTable();
        setupSearch();
        setupSelection();
        setupResponsiveTable(tablePredictions);
    }

    @Override
    protected void onUserSet() {
        loadPredictions();
    }

    private void setupComboBox() {
        comboPeriode.getItems().addAll("30 jours", "60 jours", "90 jours", "180 jours");
        comboPeriode.setValue("90 jours");
        comboPeriode.setOnAction(e -> loadPredictions());
    }

    private void setupTable() {
        // Configuration des colonnes
        colMedicament.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getNomMedicament()));

        colStockActuel.setCellValueFactory(data ->
                new SimpleIntegerProperty(data.getValue().getStockVendable()));

        colConsoJour.setCellValueFactory(data ->
                new SimpleStringProperty(String.format("%.1f", data.getValue().getConsommationJournaliere())));

        colJoursRestants.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getJoursRestantsFormate()));

        colDateRupture.setCellValueFactory(data -> {
            LocalDate date = data.getValue().getDateRupturePrevue();
            return new SimpleStringProperty(date != null ? date.format(DATE_FORMATTER) : "N/A");
        });

        colQuantiteSuggeree.setCellValueFactory(data ->
                new SimpleIntegerProperty(data.getValue().getQuantiteSuggeree()));

        colUrgence.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getNiveauUrgence()));

        // Colorer les cellules d'urgence
        colUrgence.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    switch (item) {
                        case PredictionReapprovisionnement.NIVEAU_RUPTURE ->
                                setStyle("-fx-text-fill: #dc3545; -fx-font-weight: bold; -fx-background-color: #f8d7da;");
                        case PredictionReapprovisionnement.NIVEAU_CRITIQUE ->
                                setStyle("-fx-text-fill: #dc3545; -fx-font-weight: bold;");
                        case PredictionReapprovisionnement.NIVEAU_URGENT ->
                                setStyle("-fx-text-fill: #fd7e14; -fx-font-weight: bold;");
                        case PredictionReapprovisionnement.NIVEAU_ATTENTION ->
                                setStyle("-fx-text-fill: #ffc107;");
                        default ->
                                setStyle("-fx-text-fill: #28a745;");
                    }
                }
            }
        });

        // Colorer les lignes selon le niveau d'urgence
        tablePredictions.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(PredictionReapprovisionnement item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setStyle("");
                } else if (PredictionReapprovisionnement.NIVEAU_RUPTURE.equals(item.getNiveauUrgence())) {
                    setStyle("-fx-background-color: #f8d7da;");
                } else if (PredictionReapprovisionnement.NIVEAU_CRITIQUE.equals(item.getNiveauUrgence())) {
                    setStyle("-fx-background-color: #ffe0e0;");
                } else {
                    setStyle("");
                }
            }
        });
    }

    private void setupSearch() {
        txtRecherche.textProperty().addListener((obs, oldVal, newVal) -> {
            if (filteredPredictions != null) {
                filteredPredictions.setPredicate(p -> {
                    if (newVal == null || newVal.isEmpty()) {
                        return true;
                    }
                    return p.getNomMedicament().toLowerCase().contains(newVal.toLowerCase());
                });
            }
        });
    }

    private void setupSelection() {
        tablePredictions.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> {
                    if (newVal != null) {
                        showDetail(newVal);
                        btnCreerCommande.setDisable(false);
                        btnCommanderDetail.setDisable(false);
                    } else {
                        clearDetail();
                        btnCreerCommande.setDisable(true);
                        btnCommanderDetail.setDisable(true);
                    }
                });
    }

    private int getSelectedPeriod() {
        String selected = comboPeriode.getValue();
        if (selected == null) return 90;
        return switch (selected) {
            case "30 jours" -> 30;
            case "60 jours" -> 60;
            case "180 jours" -> 180;
            default -> 90;
        };
    }

    private void loadPredictions() {
        int nbJours = getSelectedPeriod();

        Task<List<PredictionReapprovisionnement>> loadTask = new Task<>() {
            @Override
            protected List<PredictionReapprovisionnement> call() throws Exception {
                return predictionService.genererPredictions(nbJours);
            }

            @Override
            protected void succeeded() {
                List<PredictionReapprovisionnement> result = getValue();
                predictions = FXCollections.observableArrayList(result);
                filteredPredictions = new FilteredList<>(predictions, p -> true);
                tablePredictions.setItems(filteredPredictions);

                updateStats(result);
                logger.info("{} predictions chargees", result.size());
            }

            @Override
            protected void failed() {
                logger.error("Erreur lors du chargement des predictions", getException());
                showAlert(Alert.AlertType.ERROR, "Erreur",
                        "Impossible de charger les predictions: " + getException().getMessage());
            }
        };

        new Thread(loadTask).start();
    }

    private void updateStats(List<PredictionReapprovisionnement> list) {
        int total = list.size();
        int ruptures = 0, critiques = 0, urgents = 0;

        for (PredictionReapprovisionnement p : list) {
            switch (p.getNiveauUrgence()) {
                case PredictionReapprovisionnement.NIVEAU_RUPTURE -> ruptures++;
                case PredictionReapprovisionnement.NIVEAU_CRITIQUE -> critiques++;
                case PredictionReapprovisionnement.NIVEAU_URGENT -> urgents++;
            }
        }

        lblTotal.setText(total + " medicament(s)");
        lblRuptures.setText(ruptures + " rupture(s)");
        lblCritiques.setText(critiques + " critique(s)");
        lblUrgents.setText(urgents + " urgent(s)");
    }

    private void showDetail(PredictionReapprovisionnement prediction) {
        lblDetailTitre.setText(prediction.getNomMedicament());
        lblDetailStock.setText(String.valueOf(prediction.getStockVendable()));
        lblDetailConsoMois.setText(String.format("%.0f unites", prediction.getConsommationMensuelle()));
        lblDetailSeuil.setText(String.valueOf(prediction.getSeuilMin()));
        lblDetailDelai.setText(configService.getPredictionDelaiLivraisonDefaut() + " jours");

        // Mettre a jour le graphique
        updateChart(prediction);
    }

    private void clearDetail() {
        lblDetailTitre.setText("Detail du medicament");
        lblDetailStock.setText("-");
        lblDetailConsoMois.setText("-");
        lblDetailSeuil.setText("-");
        lblDetailDelai.setText("-");
        chartPrevision.getData().clear();
    }

    private void updateChart(PredictionReapprovisionnement prediction) {
        chartPrevision.getData().clear();

        XYChart.Series<String, Number> stockSeries = new XYChart.Series<>();
        stockSeries.setName("Stock prevu");

        XYChart.Series<String, Number> seuilSeries = new XYChart.Series<>();
        seuilSeries.setName("Seuil minimum");

        int stock = prediction.getStockVendable();
        double consoJour = prediction.getConsommationJournaliere();
        int seuil = prediction.getSeuilMin();

        // Generer les points pour les 60 prochains jours
        LocalDate today = LocalDate.now();
        for (int i = 0; i <= 60; i += 5) {
            String dateLabel = today.plusDays(i).format(DateTimeFormatter.ofPattern("dd/MM"));
            int stockPrevu = Math.max(0, (int) (stock - (consoJour * i)));
            stockSeries.getData().add(new XYChart.Data<>(dateLabel, stockPrevu));
            seuilSeries.getData().add(new XYChart.Data<>(dateLabel, seuil));
        }

        chartPrevision.getData().addAll(stockSeries, seuilSeries);
    }

    @FXML
    private void handleRefresh() {
        loadPredictions();
    }

    @FXML
    private void handleCreerCommande() {
        List<PredictionReapprovisionnement> selected =
                tablePredictions.getSelectionModel().getSelectedItems();

        if (selected == null || selected.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Selection requise",
                    "Veuillez selectionner au moins un medicament.");
            return;
        }

        // Construire un message recapitulatif
        StringBuilder message = new StringBuilder("Medicaments a commander:\n\n");
        for (PredictionReapprovisionnement p : selected) {
            message.append("- ").append(p.getNomMedicament())
                   .append(": ").append(p.getQuantiteSuggeree()).append(" unites\n");
        }
        message.append("\nAccedez au module Commandes pour creer la commande.");

        showAlert(Alert.AlertType.INFORMATION, "Commande suggeree", message.toString());

        // Naviguer vers la page des commandes
        if (dashboardController != null) {
            // TODO: Passer les donnees pre-remplies au CommandeController
        }
    }

    @FXML
    private void handleCommanderMedicament() {
        PredictionReapprovisionnement selected = tablePredictions.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Selection requise",
                    "Veuillez selectionner un medicament.");
            return;
        }

        String message = String.format(
                "Medicament: %s\nQuantite suggeree: %d unites\n\nAccedez au module Commandes pour creer la commande.",
                selected.getNomMedicament(), selected.getQuantiteSuggeree());

        showAlert(Alert.AlertType.INFORMATION, "Commande suggeree", message);
    }

    @FXML
    private void handleExportPDF() {
        if (predictions == null || predictions.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Aucune donnee",
                    "Aucune prediction a exporter.");
            return;
        }

        Task<String> exportTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                return rapportService.genererRapportPredictions(predictions);
            }

            @Override
            protected void succeeded() {
                String filePath = getValue();
                showAlert(Alert.AlertType.INFORMATION, "Export reussi",
                        "Le rapport a ete genere:\n" + filePath);
            }

            @Override
            protected void failed() {
                logger.error("Erreur lors de l'export PDF", getException());
                showAlert(Alert.AlertType.ERROR, "Erreur d'export",
                        "Impossible de generer le rapport: " + getException().getMessage());
            }
        };

        new Thread(exportTask).start();
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}
