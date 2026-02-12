package com.sgpa.controller;

import com.sgpa.dao.impl.FournisseurDAOImpl;
import com.sgpa.dao.impl.LotDAOImpl;
import com.sgpa.dao.impl.MedicamentDAOImpl;
import com.sgpa.model.Fournisseur;
import com.sgpa.model.Lot;
import com.sgpa.model.Medicament;
import com.sgpa.service.ExportService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Controleur pour l'ecran de gestion du stock.
 * Affiche les medicaments et leurs lots associes.
 *
 * @author SGPA Team
 * @version 1.0
 */
public class StockController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(StockController.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Composants recherche
    @FXML private TextField searchField;

    // Labels statistiques
    @FXML private Label lblTotalMedicaments;
    @FXML private Label lblStockBas;
    @FXML private Label lblPeremptionProche;

    // Table medicaments
    @FXML private TableView<Medicament> tableMedicaments;
    @FXML private TableColumn<Medicament, String> colNom;
    @FXML private TableColumn<Medicament, String> colPrincipe;
    @FXML private TableColumn<Medicament, String> colStock;
    @FXML private TableColumn<Medicament, String> colSeuil;
    @FXML private TableColumn<Medicament, String> colStatut;

    // Table lots
    @FXML private Label lblSelectedMedicament;
    @FXML private Button btnAddLotToSelected;
    @FXML private TableView<Lot> tableLots;
    @FXML private TableColumn<Lot, String> colLotNumero;
    @FXML private TableColumn<Lot, String> colLotPeremption;
    @FXML private TableColumn<Lot, String> colLotJours;
    @FXML private TableColumn<Lot, String> colLotQuantite;
    @FXML private TableColumn<Lot, String> colLotFournisseur;
    @FXML private TableColumn<Lot, String> colLotAction;

    // Dialog ajout lot
    @FXML private VBox addLotDialog;
    @FXML private ComboBox<Medicament> comboMedicament;
    @FXML private TextField txtNumeroLot;
    @FXML private DatePicker dpPeremption;
    @FXML private Spinner<Integer> spinnerQte;
    @FXML private ComboBox<Fournisseur> comboFournisseur;
    @FXML private TextField txtPrixAchat;

    private final MedicamentDAOImpl medicamentDAO;
    private final LotDAOImpl lotDAO;
    private final FournisseurDAOImpl fournisseurDAO;
    private final ExportService exportService;

    private final ObservableList<Medicament> medicamentData = FXCollections.observableArrayList();
    private final ObservableList<Lot> lotData = FXCollections.observableArrayList();

    private Medicament selectedMedicament;

    public StockController() {
        this.medicamentDAO = new MedicamentDAOImpl();
        this.lotDAO = new LotDAOImpl();
        this.fournisseurDAO = new FournisseurDAOImpl();
        this.exportService = new ExportService();
    }

    @FXML
    public void initialize() {
        setupMedicamentTable();
        setupLotTable();
        setupDialog();
        setupResponsiveTable(tableMedicaments);
        setupResponsiveTable(tableLots);
        loadData();

        // Selection listener
        tableMedicaments.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> handleMedicamentSelection(newVal));
    }

    private void setupMedicamentTable() {
        colNom.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getNomCommercial()));
        colPrincipe.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getPrincipeActif()));
        colSeuil.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getSeuilMin())));

        // Stock total (necessite requete)
        colStock.setCellValueFactory(data -> {
            try {
                int stock = lotDAO.getTotalStockByMedicament(data.getValue().getIdMedicament());
                return new SimpleStringProperty(String.valueOf(stock));
            } catch (Exception e) {
                return new SimpleStringProperty("?");
            }
        });

        // Statut avec couleur
        colStatut.setCellValueFactory(data -> {
            try {
                int stock = lotDAO.getTotalStockByMedicament(data.getValue().getIdMedicament());
                int seuil = data.getValue().getSeuilMin();
                if (stock == 0) return new SimpleStringProperty("RUPTURE");
                if (stock < seuil) return new SimpleStringProperty("BAS");
                return new SimpleStringProperty("OK");
            } catch (Exception e) {
                return new SimpleStringProperty("?");
            }
        });

        colStatut.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    switch (item) {
                        case "RUPTURE" -> setStyle("-fx-text-fill: #dc3545; -fx-font-weight: bold;");
                        case "BAS" -> setStyle("-fx-text-fill: #fd7e14; -fx-font-weight: bold;");
                        case "OK" -> setStyle("-fx-text-fill: #28a745;");
                        default -> setStyle("");
                    }
                }
            }
        });

        tableMedicaments.setItems(medicamentData);
    }

    private void setupLotTable() {
        colLotNumero.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getNumeroLot()));
        colLotPeremption.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getDatePeremption().format(DATE_FORMAT)));
        colLotJours.setCellValueFactory(data -> new SimpleStringProperty(
                String.valueOf(data.getValue().getJoursAvantPeremption())));
        colLotQuantite.setCellValueFactory(data -> new SimpleStringProperty(
                String.valueOf(data.getValue().getQuantiteStock())));
        colLotFournisseur.setCellValueFactory(data -> {
            Fournisseur f = data.getValue().getFournisseur();
            return new SimpleStringProperty(f != null ? f.getNom() : "N/A");
        });

        // Colorisation selon peremption
        colLotPeremption.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    Lot lot = getTableView().getItems().get(getIndex());
                    if (lot.isPerime()) {
                        setStyle("-fx-text-fill: #dc3545; -fx-font-weight: bold;");
                    } else if (lot.isPeremptionProche()) {
                        setStyle("-fx-text-fill: #fd7e14; -fx-font-weight: bold;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });

        // Bouton supprimer lot perime
        colLotAction.setCellFactory(column -> new TableCell<>() {
            private final Button btnDelete = new Button("X");
            {
                btnDelete.setStyle("-fx-background-color: #dc3545; -fx-text-fill: white; -fx-font-size: 10;");
                btnDelete.setOnAction(e -> {
                    Lot lot = getTableView().getItems().get(getIndex());
                    handleDeleteLot(lot);
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    Lot lot = getTableView().getItems().get(getIndex());
                    // Afficher bouton supprimer seulement pour lots perimes ou vides
                    if (lot.isPerime() || lot.getQuantiteStock() == 0) {
                        setGraphic(btnDelete);
                    } else {
                        setGraphic(null);
                    }
                }
            }
        });

        tableLots.setItems(lotData);
    }

    private void setupDialog() {
        // Converter pour ComboBox medicament
        comboMedicament.setConverter(new StringConverter<>() {
            @Override
            public String toString(Medicament m) {
                return m != null ? m.getNomCommercial() : "";
            }
            @Override
            public Medicament fromString(String s) { return null; }
        });

        // Converter pour ComboBox fournisseur
        comboFournisseur.setConverter(new StringConverter<>() {
            @Override
            public String toString(Fournisseur f) {
                return f != null ? f.getNom() : "";
            }
            @Override
            public Fournisseur fromString(String s) { return null; }
        });

        dpPeremption.setValue(LocalDate.now().plusMonths(12));
    }

    private void loadData() {
        Task<List<Medicament>> task = new Task<>() {
            @Override
            protected List<Medicament> call() throws Exception {
                return medicamentDAO.findAllActive();
            }

            @Override
            protected void succeeded() {
                medicamentData.setAll(getValue());
                updateStats();
            }

            @Override
            protected void failed() {
                logger.error("Erreur chargement medicaments", getException());
            }
        };
        new Thread(task).start();
    }

    private void updateStats() {
        int total = medicamentData.size();
        int stockBas = 0;
        int peremptionProche = 0;

        for (Medicament m : medicamentData) {
            try {
                int stock = lotDAO.getTotalStockByMedicament(m.getIdMedicament());
                if (stock < m.getSeuilMin()) stockBas++;
            } catch (Exception e) {
                logger.error("Erreur calcul stock", e);
            }
        }

        try {
            peremptionProche = lotDAO.findExpiringBefore(LocalDate.now().plusMonths(3)).size();
        } catch (Exception e) {
            logger.error("Erreur calcul peremption", e);
        }

        lblTotalMedicaments.setText(total + " medicaments");
        lblStockBas.setText(stockBas + " en stock bas");
        lblPeremptionProche.setText(peremptionProche + " peremption proche");
    }

    private void handleMedicamentSelection(Medicament medicament) {
        selectedMedicament = medicament;
        if (medicament == null) {
            lblSelectedMedicament.setText("Lots du medicament");
            btnAddLotToSelected.setDisable(true);
            lotData.clear();
            return;
        }

        lblSelectedMedicament.setText("Lots: " + medicament.getNomCommercial());
        btnAddLotToSelected.setDisable(false);

        Task<List<Lot>> task = new Task<>() {
            @Override
            protected List<Lot> call() throws Exception {
                return lotDAO.findByMedicamentIdSortedByExpiration(medicament.getIdMedicament());
            }

            @Override
            protected void succeeded() {
                lotData.setAll(getValue());
            }

            @Override
            protected void failed() {
                logger.error("Erreur chargement lots", getException());
            }
        };
        new Thread(task).start();
    }

    @FXML
    private void handleSearch() {
        String search = searchField.getText().trim().toLowerCase();
        if (search.isEmpty()) {
            loadData();
            return;
        }

        Task<List<Medicament>> task = new Task<>() {
            @Override
            protected List<Medicament> call() throws Exception {
                return medicamentDAO.findByNom(search);
            }

            @Override
            protected void succeeded() {
                medicamentData.setAll(getValue());
            }
        };
        new Thread(task).start();
    }

    @FXML
    private void handleRefresh() {
        loadData();
        if (selectedMedicament != null) {
            handleMedicamentSelection(selectedMedicament);
        }
    }

    @FXML
    private void handleAddLot() {
        showAddLotDialog(null);
    }

    @FXML
    private void handleAddLotToSelected() {
        showAddLotDialog(selectedMedicament);
    }

    private void showAddLotDialog(Medicament preselected) {
        // Charger les donnees pour les combos
        Task<Void> task = new Task<>() {
            private List<Medicament> medicaments;
            private List<Fournisseur> fournisseurs;

            @Override
            protected Void call() throws Exception {
                medicaments = medicamentDAO.findAllActive();
                fournisseurs = fournisseurDAO.findAllActive();
                return null;
            }

            @Override
            protected void succeeded() {
                comboMedicament.setItems(FXCollections.observableArrayList(medicaments));
                comboFournisseur.setItems(FXCollections.observableArrayList(fournisseurs));

                if (preselected != null) {
                    comboMedicament.setValue(preselected);
                }

                // Reset fields
                txtNumeroLot.clear();
                dpPeremption.setValue(LocalDate.now().plusMonths(12));
                spinnerQte.getValueFactory().setValue(1);
                txtPrixAchat.clear();

                addLotDialog.setVisible(true);
                addLotDialog.setManaged(true);
            }
        };
        new Thread(task).start();
    }

    @FXML
    private void handleCancelAddLot() {
        addLotDialog.setVisible(false);
        addLotDialog.setManaged(false);
    }

    @FXML
    private void handleConfirmAddLot() {
        // Validation
        if (comboMedicament.getValue() == null) {
            showAlert(Alert.AlertType.WARNING, "Attention", "Veuillez selectionner un medicament.");
            return;
        }
        if (txtNumeroLot.getText().trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Attention", "Veuillez saisir un numero de lot.");
            return;
        }
        if (dpPeremption.getValue() == null) {
            showAlert(Alert.AlertType.WARNING, "Attention", "Veuillez selectionner une date de peremption.");
            return;
        }
        if (comboFournisseur.getValue() == null) {
            showAlert(Alert.AlertType.WARNING, "Attention", "Veuillez selectionner un fournisseur.");
            return;
        }

        BigDecimal prixAchat;
        try {
            prixAchat = new BigDecimal(txtPrixAchat.getText().replace(",", "."));
        } catch (Exception e) {
            showAlert(Alert.AlertType.WARNING, "Attention", "Prix d'achat invalide.");
            return;
        }

        Lot lot = new Lot();
        lot.setIdMedicament(comboMedicament.getValue().getIdMedicament());
        lot.setIdFournisseur(comboFournisseur.getValue().getIdFournisseur());
        lot.setNumeroLot(txtNumeroLot.getText().trim());
        lot.setDatePeremption(dpPeremption.getValue());
        lot.setQuantiteStock(spinnerQte.getValue());
        lot.setPrixAchat(prixAchat);

        Task<Lot> task = new Task<>() {
            @Override
            protected Lot call() throws Exception {
                return lotDAO.save(lot);
            }

            @Override
            protected void succeeded() {
                handleCancelAddLot();
                handleRefresh();
                showAlert(Alert.AlertType.INFORMATION, "Succes", "Lot ajoute avec succes.");
            }

            @Override
            protected void failed() {
                logger.error("Erreur ajout lot", getException());
                showAlert(Alert.AlertType.ERROR, "Erreur", "Impossible d'ajouter le lot: " + getException().getMessage());
            }
        };
        new Thread(task).start();
    }

    private void handleDeleteLot(Lot lot) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmation");
        confirm.setHeaderText("Supprimer le lot?");
        confirm.setContentText("Voulez-vous vraiment supprimer le lot " + lot.getNumeroLot() + "?");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                Task<Void> task = new Task<>() {
                    @Override
                    protected Void call() throws Exception {
                        lotDAO.delete(lot.getIdLot());
                        return null;
                    }

                    @Override
                    protected void succeeded() {
                        handleMedicamentSelection(selectedMedicament);
                        updateStats();
                    }

                    @Override
                    protected void failed() {
                        logger.error("Erreur suppression lot", getException());
                        showAlert(Alert.AlertType.ERROR, "Erreur", "Impossible de supprimer le lot.");
                    }
                };
                new Thread(task).start();
            }
        });
    }

    @FXML
    private void handleExportCSV() {
        Task<String> exportTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                return exportService.exportStock();
            }
        };

        exportTask.setOnSucceeded(event -> {
            String filePath = exportTask.getValue();
            showAlert(Alert.AlertType.INFORMATION, "Export reussi",
                    "Le fichier CSV a ete genere:\n" + filePath);
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
