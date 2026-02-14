package com.sgpa.controller;

import com.sgpa.dao.impl.CommandeDAOImpl;
import com.sgpa.dao.impl.FournisseurDAOImpl;
import com.sgpa.dao.impl.LotDAOImpl;
import com.sgpa.dao.impl.MedicamentDAOImpl;
import com.sgpa.model.*;
import com.sgpa.model.enums.StatutCommande;
import com.sgpa.service.RapportService;
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

import java.awt.Desktop;
import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Controleur pour l'ecran de gestion des commandes fournisseurs.
 * Permet de creer, visualiser et recevoir des commandes.
 *
 * @author SGPA Team
 * @version 1.0
 */
public class CommandeController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(CommandeController.class);
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // Filtres
    @FXML private ComboBox<StatutCommande> comboStatutFilter;

    // Stats
    @FXML private Label lblTotal;
    @FXML private Label lblEnAttente;
    @FXML private Label lblRecues;

    // Table commandes
    @FXML private TableView<Commande> tableCommandes;
    @FXML private TableColumn<Commande, String> colId;
    @FXML private TableColumn<Commande, String> colDate;
    @FXML private TableColumn<Commande, String> colFournisseur;
    @FXML private TableColumn<Commande, String> colNbArticles;
    @FXML private TableColumn<Commande, String> colStatut;

    // Detail
    @FXML private Label lblDetailTitle;
    @FXML private Button btnReceive;
    @FXML private Button btnCancel;
    @FXML private TableView<LigneCommande> tableLignes;
    @FXML private TableColumn<LigneCommande, String> colLigneMed;
    @FXML private TableColumn<LigneCommande, String> colLigneQte;
    @FXML private TableColumn<LigneCommande, String> colLigneRecue;
    @FXML private TableColumn<LigneCommande, String> colLignePrix;
    @FXML private TableColumn<LigneCommande, String> colLigneTotal;
    @FXML private TextArea txtNotes;

    // Dialog nouvelle commande
    @FXML private VBox newOrderDialog;
    @FXML private ComboBox<Fournisseur> comboFournisseur;
    @FXML private ComboBox<Medicament> comboMedicament;
    @FXML private Spinner<Integer> spinnerQteCmd;
    @FXML private TextField txtPrixCmd;
    @FXML private TableView<LigneCommande> tableNewLines;
    @FXML private TableColumn<LigneCommande, String> colNewMed;
    @FXML private TableColumn<LigneCommande, String> colNewQte;
    @FXML private TableColumn<LigneCommande, String> colNewPrix;
    @FXML private TableColumn<LigneCommande, String> colNewAction;
    @FXML private TextArea txtNewNotes;

    private final CommandeDAOImpl commandeDAO;
    private final FournisseurDAOImpl fournisseurDAO;
    private final MedicamentDAOImpl medicamentDAO;
    private final LotDAOImpl lotDAO;
    private final RapportService rapportService;

    private final ObservableList<Commande> commandeData = FXCollections.observableArrayList();
    private final ObservableList<LigneCommande> ligneData = FXCollections.observableArrayList();
    private final ObservableList<LigneCommande> newLineData = FXCollections.observableArrayList();

    private Commande selectedCommande;

    public CommandeController() {
        this.commandeDAO = new CommandeDAOImpl();
        this.fournisseurDAO = new FournisseurDAOImpl();
        this.medicamentDAO = new MedicamentDAOImpl();
        this.lotDAO = new LotDAOImpl();
        this.rapportService = new RapportService();
    }

    @FXML
    public void initialize() {
        setupFilters();
        setupCommandeTable();
        setupLigneTable();
        setupNewLineTable();
        setupCombos();
        setupResponsiveTable(tableCommandes);
        loadData();

        tableCommandes.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> handleCommandeSelection(newVal));
    }

    private void setupFilters() {
        ObservableList<StatutCommande> statuts = FXCollections.observableArrayList();
        statuts.add(null); // "Tous"
        statuts.addAll(StatutCommande.values());
        comboStatutFilter.setItems(statuts);

        comboStatutFilter.setConverter(new StringConverter<>() {
            @Override
            public String toString(StatutCommande s) {
                return s != null ? s.getLibelle() : "Tous les statuts";
            }
            @Override
            public StatutCommande fromString(String s) { return null; }
        });

        comboStatutFilter.setOnAction(e -> loadData());
    }

    private void setupCommandeTable() {
        colId.setCellValueFactory(data -> new SimpleStringProperty(
                "CMD-" + String.format("%04d", data.getValue().getIdCommande())));
        colDate.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getDateCreation().format(DATETIME_FORMAT)));
        colFournisseur.setCellValueFactory(data -> {
            Fournisseur f = data.getValue().getFournisseur();
            return new SimpleStringProperty(f != null ? f.getNom() : "N/A");
        });
        colNbArticles.setCellValueFactory(data -> new SimpleStringProperty(
                String.valueOf(data.getValue().getNombreArticlesCommandes())));

        colStatut.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getStatut().getLibelle()));

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
                        case "En attente" -> setStyle("-fx-text-fill: #fd7e14; -fx-font-weight: bold;");
                        case "Recue" -> setStyle("-fx-text-fill: #28a745; -fx-font-weight: bold;");
                        case "Annulee" -> setStyle("-fx-text-fill: #dc3545;");
                        default -> setStyle("");
                    }
                }
            }
        });

        tableCommandes.setItems(commandeData);
    }

    private void setupLigneTable() {
        colLigneMed.setCellValueFactory(data -> {
            Medicament m = data.getValue().getMedicament();
            return new SimpleStringProperty(m != null ? m.getNomCommercial() : "N/A");
        });
        colLigneQte.setCellValueFactory(data -> new SimpleStringProperty(
                String.valueOf(data.getValue().getQuantiteCommandee())));
        colLigneRecue.setCellValueFactory(data -> new SimpleStringProperty(
                String.valueOf(data.getValue().getQuantiteRecue())));
        colLignePrix.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getPrixUnitaire() != null ?
                        data.getValue().getPrixUnitaire().toString() + " EUR" : "N/A"));
        colLigneTotal.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getMontantLigne().toString() + " EUR"));

        tableLignes.setItems(ligneData);
    }

    private void setupNewLineTable() {
        colNewMed.setCellValueFactory(data -> {
            Medicament m = data.getValue().getMedicament();
            return new SimpleStringProperty(m != null ? m.getNomCommercial() : "N/A");
        });
        colNewQte.setCellValueFactory(data -> new SimpleStringProperty(
                String.valueOf(data.getValue().getQuantiteCommandee())));
        colNewPrix.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getPrixUnitaire() != null ?
                        data.getValue().getPrixUnitaire().toString() : "0"));

        colNewAction.setCellFactory(column -> new TableCell<>() {
            private final Button btnRemove = new Button("X");
            {
                btnRemove.setStyle("-fx-background-color: #dc3545; -fx-text-fill: white; -fx-font-size: 10;");
                btnRemove.setOnAction(e -> {
                    LigneCommande ligne = getTableView().getItems().get(getIndex());
                    newLineData.remove(ligne);
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btnRemove);
            }
        });

        tableNewLines.setItems(newLineData);
    }

    private void setupCombos() {
        comboFournisseur.setConverter(new StringConverter<>() {
            @Override
            public String toString(Fournisseur f) {
                return f != null ? f.getNom() : "";
            }
            @Override
            public Fournisseur fromString(String s) { return null; }
        });

        comboMedicament.setConverter(new StringConverter<>() {
            @Override
            public String toString(Medicament m) {
                return m != null ? m.getNomCommercial() : "";
            }
            @Override
            public Medicament fromString(String s) { return null; }
        });
    }

    private void loadData() {
        StatutCommande filter = comboStatutFilter.getValue();

        Task<List<Commande>> task = new Task<>() {
            @Override
            protected List<Commande> call() throws Exception {
                if (filter != null) {
                    return commandeDAO.findByStatut(filter);
                }
                return commandeDAO.findAll();
            }

            @Override
            protected void succeeded() {
                commandeData.setAll(getValue());
                updateStats();
            }

            @Override
            protected void failed() {
                logger.error("Erreur chargement commandes", getException());
            }
        };
        runAsync(task);
    }

    private void updateStats() {
        int total = commandeData.size();
        int enAttente = 0;
        int recues = 0;

        for (Commande c : commandeData) {
            if (c.getStatut() == StatutCommande.EN_ATTENTE) enAttente++;
            else if (c.getStatut() == StatutCommande.RECUE) recues++;
        }

        lblTotal.setText(total + " commandes");
        lblEnAttente.setText(enAttente + " en attente");
        lblRecues.setText(recues + " recues");
    }

    private void handleCommandeSelection(Commande commande) {
        selectedCommande = commande;
        if (commande == null) {
            lblDetailTitle.setText("Detail de la commande");
            btnReceive.setDisable(true);
            btnCancel.setDisable(true);
            ligneData.clear();
            txtNotes.clear();
            return;
        }

        lblDetailTitle.setText("Commande CMD-" + String.format("%04d", commande.getIdCommande()));
        btnReceive.setDisable(!commande.isModifiable());
        btnCancel.setDisable(!commande.isModifiable());
        txtNotes.setText(commande.getNotes() != null ? commande.getNotes() : "");

        // Charger les lignes
        Task<List<LigneCommande>> task = new Task<>() {
            @Override
            protected List<LigneCommande> call() throws Exception {
                return commandeDAO.findLignesByCommandeId(commande.getIdCommande());
            }

            @Override
            protected void succeeded() {
                ligneData.setAll(getValue());
            }

            @Override
            protected void failed() {
                logger.error("Erreur chargement lignes", getException());
            }
        };
        runAsync(task);
    }

    @FXML
    private void handleRefresh() {
        loadData();
    }

    @FXML
    private void handleNewOrder() {
        // Charger les combos
        Task<Void> task = new Task<>() {
            private List<Fournisseur> fournisseurs;
            private List<Medicament> medicaments;

            @Override
            protected Void call() throws Exception {
                fournisseurs = fournisseurDAO.findAllActive();
                medicaments = medicamentDAO.findAllActive();
                return null;
            }

            @Override
            protected void succeeded() {
                comboFournisseur.setItems(FXCollections.observableArrayList(fournisseurs));
                comboMedicament.setItems(FXCollections.observableArrayList(medicaments));

                comboFournisseur.setValue(null);
                newLineData.clear();
                txtNewNotes.clear();
                spinnerQteCmd.getValueFactory().setValue(10);
                txtPrixCmd.clear();

                newOrderDialog.setVisible(true);
                newOrderDialog.setManaged(true);
            }
        };
        runAsync(task);
    }

    @FXML
    private void handleAddLine() {
        Medicament med = comboMedicament.getValue();
        if (med == null) {
            showAlert(Alert.AlertType.WARNING, "Attention", "Selectionnez un medicament.");
            return;
        }

        BigDecimal prix;
        try {
            prix = new BigDecimal(txtPrixCmd.getText().trim().replace(",", "."));
        } catch (Exception e) {
            showAlert(Alert.AlertType.WARNING, "Attention", "Prix invalide.");
            return;
        }

        LigneCommande ligne = new LigneCommande();
        ligne.setIdMedicament(med.getIdMedicament());
        ligne.setMedicament(med);
        ligne.setQuantiteCommandee(spinnerQteCmd.getValue());
        ligne.setPrixUnitaire(prix);

        newLineData.add(ligne);

        // Reset
        comboMedicament.setValue(null);
        spinnerQteCmd.getValueFactory().setValue(10);
        txtPrixCmd.clear();
    }

    @FXML
    private void handleCancelNewOrder() {
        newOrderDialog.setVisible(false);
        newOrderDialog.setManaged(false);
    }

    @FXML
    private void handleConfirmNewOrder() {
        if (comboFournisseur.getValue() == null) {
            showAlert(Alert.AlertType.WARNING, "Attention", "Selectionnez un fournisseur.");
            return;
        }
        if (newLineData.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Attention", "Ajoutez au moins un medicament.");
            return;
        }

        Commande commande = new Commande(comboFournisseur.getValue().getIdFournisseur());
        commande.setNotes(txtNewNotes.getText().trim());
        commande.setLignesCommande(new ArrayList<>(newLineData));

        Task<Commande> task = new Task<>() {
            @Override
            protected Commande call() throws Exception {
                return commandeDAO.save(commande);
            }

            @Override
            protected void succeeded() {
                handleCancelNewOrder();
                loadData();
                showAlert(Alert.AlertType.INFORMATION, "Succes", "Commande creee avec succes.");
            }

            @Override
            protected void failed() {
                logger.error("Erreur creation commande", getException());
                showAlert(Alert.AlertType.ERROR, "Erreur", "Impossible de creer la commande.");
            }
        };
        runAsync(task);
    }

    @FXML
    private void handleReceiveOrder() {
        if (selectedCommande == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Recevoir la commande");
        confirm.setHeaderText("Confirmer la reception?");
        confirm.setContentText("Cette action va:\n" +
                "- Marquer la commande comme recue\n" +
                "- Creer les lots correspondants dans le stock\n\n" +
                "Voulez-vous continuer?");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                Task<Void> task = new Task<>() {
                    @Override
                    protected Void call() throws Exception {
                        // Marquer comme recue
                        selectedCommande.marquerRecue();
                        commandeDAO.update(selectedCommande);

                        // Creer les lots pour chaque ligne
                        for (LigneCommande ligne : ligneData) {
                            Lot lot = new Lot();
                            lot.setIdMedicament(ligne.getIdMedicament());
                            lot.setIdFournisseur(selectedCommande.getIdFournisseur());
                            lot.setNumeroLot("CMD" + selectedCommande.getIdCommande() + "-" + ligne.getIdMedicament());
                            lot.setDatePeremption(LocalDate.now().plusYears(2)); // 2 ans par defaut
                            lot.setQuantiteStock(ligne.getQuantiteCommandee());
                            lot.setPrixAchat(ligne.getPrixUnitaire());
                            lotDAO.save(lot);

                            // Mettre a jour quantite recue
                            ligne.setQuantiteRecue(ligne.getQuantiteCommandee());
                        }
                        return null;
                    }

                    @Override
                    protected void succeeded() {
                        loadData();
                        showAlert(Alert.AlertType.INFORMATION, "Succes",
                                "Commande recue et stock mis a jour.");
                    }

                    @Override
                    protected void failed() {
                        logger.error("Erreur reception commande", getException());
                        showAlert(Alert.AlertType.ERROR, "Erreur",
                                "Impossible de recevoir la commande: " + getException().getMessage());
                    }
                };
                runAsync(task);
            }
        });
    }

    @FXML
    private void handleCancelOrder() {
        if (selectedCommande == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Annuler la commande");
        confirm.setHeaderText("Confirmer l'annulation?");
        confirm.setContentText("Voulez-vous vraiment annuler cette commande?");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                Task<Void> task = new Task<>() {
                    @Override
                    protected Void call() throws Exception {
                        selectedCommande.annuler();
                        commandeDAO.update(selectedCommande);
                        return null;
                    }

                    @Override
                    protected void succeeded() {
                        loadData();
                        showAlert(Alert.AlertType.INFORMATION, "Succes", "Commande annulee.");
                    }

                    @Override
                    protected void failed() {
                        logger.error("Erreur annulation commande", getException());
                        showAlert(Alert.AlertType.ERROR, "Erreur", "Impossible d'annuler la commande.");
                    }
                };
                runAsync(task);
            }
        });
    }

    /**
     * Pre-remplit le formulaire de nouvelle commande avec un medicament specifique.
     * Appele depuis le dashboard lors du clic sur "Commander" dans les alertes.
     */
    public void prefillMedicament(String medicamentName) {
        // Ouvrir le dialog de nouvelle commande et pre-selectionner le medicament
        Task<Void> task = new Task<>() {
            private List<Fournisseur> fournisseurs;
            private List<Medicament> medicaments;

            @Override
            protected Void call() throws Exception {
                fournisseurs = fournisseurDAO.findAllActive();
                medicaments = medicamentDAO.findAllActive();
                return null;
            }

            @Override
            protected void succeeded() {
                comboFournisseur.setItems(FXCollections.observableArrayList(fournisseurs));
                comboMedicament.setItems(FXCollections.observableArrayList(medicaments));

                comboFournisseur.setValue(null);
                newLineData.clear();
                txtNewNotes.clear();
                spinnerQteCmd.getValueFactory().setValue(10);
                txtPrixCmd.clear();

                // Pre-selectionner le medicament correspondant
                for (Medicament med : medicaments) {
                    if (med.getNomCommercial().equalsIgnoreCase(medicamentName)) {
                        comboMedicament.setValue(med);
                        break;
                    }
                }

                newOrderDialog.setVisible(true);
                newOrderDialog.setManaged(true);
            }
        };
        runAsync(task);
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Exporte le bon de commande selectionne en PDF.
     */
    @FXML
    private void handleExportPDF() {
        if (selectedCommande == null) {
            showAlert(Alert.AlertType.WARNING, "Attention",
                    "Selectionnez une commande pour l'exporter.");
            return;
        }

        // Preparer la commande avec les lignes
        Commande commandeComplete = selectedCommande;
        commandeComplete.setLignesCommande(new ArrayList<>(ligneData));

        Task<String> exportTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                return rapportService.genererBonCommande(commandeComplete);
            }
        };

        exportTask.setOnSucceeded(event -> {
            String filePath = exportTask.getValue();
            logger.info("Bon de commande genere: {}", filePath);

            try {
                File pdfFile = new File(filePath);
                if (Desktop.isDesktopSupported() && pdfFile.exists()) {
                    Desktop.getDesktop().open(pdfFile);
                }
                showAlert(Alert.AlertType.INFORMATION, "Export reussi",
                        "Bon de commande exporte:\n" + filePath);
            } catch (Exception e) {
                logger.warn("Impossible d'ouvrir le PDF", e);
                showAlert(Alert.AlertType.INFORMATION, "Export reussi",
                        "Bon de commande exporte:\n" + filePath);
            }
        });

        exportTask.setOnFailed(event -> {
            logger.error("Erreur lors de l'export du bon de commande", exportTask.getException());
            showAlert(Alert.AlertType.ERROR, "Erreur d'export",
                    "Impossible de generer le bon de commande PDF.");
        });

        runAsync(exportTask);
    }
}
