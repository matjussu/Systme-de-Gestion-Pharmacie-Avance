package com.sgpa.controller;

import com.sgpa.dao.impl.CommandeDAOImpl;
import com.sgpa.dao.impl.FournisseurDAOImpl;
import com.sgpa.dao.impl.LotDAOImpl;
import com.sgpa.dao.impl.MedicamentDAOImpl;
import com.sgpa.dto.PredictionReapprovisionnement;
import com.sgpa.model.Commande;
import com.sgpa.model.Fournisseur;
import com.sgpa.model.Lot;
import com.sgpa.model.Medicament;
import com.sgpa.service.AuditService;
import com.sgpa.service.ExportService;
import com.sgpa.service.PredictionService;
import com.sgpa.service.StockService;
import com.sgpa.utils.DialogHelper;
import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controleur pour la page fusionnee Produits & Stock.
 * Combine la gestion du catalogue medicaments, des lots et l'historique commandes.
 */
public class ProduitsStockController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(ProduitsStockController.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final List<String> ALL_FORMES_GALENIQUES = List.of(
            "Aerosol", "Bain de bouche", "Capsule molle", "Collyre",
            "Comprime", "Comprime effervescent", "Comprime orodispersible", "Comprime pellicule",
            "Creme", "Emulsion", "Gel", "Gelule",
            "Gouttes buvables", "Granules", "Lotion",
            "Ovule", "Patch transdermique", "Pommade",
            "Poudre pour solution injectable", "Poudre pour suspension buvable",
            "Sirop", "Solution buvable", "Solution injectable",
            "Spray nasal", "Suppositoire", "Suspension buvable"
    );

    // Root pane for DialogHelper
    @FXML private StackPane rootPane;

    // Recherche
    @FXML private TextField searchField;

    // Stats badges (filtres cliquables)
    @FXML private HBox badgeTotal;
    @FXML private HBox badgeStockBas;
    @FXML private HBox badgePeremption;
    @FXML private HBox badgeValorisation;
    @FXML private Label lblTotalMedicaments;
    @FXML private Label lblStockBas;
    @FXML private Label lblPeremptionProche;
    @FXML private Label lblValeurStock;
    @FXML private Label lblCount;

    // Table medicaments
    @FXML private TableView<Medicament> tableMedicaments;
    @FXML private TableColumn<Medicament, String> colNom;
    @FXML private TableColumn<Medicament, String> colOrdonnance;
    @FXML private TableColumn<Medicament, String> colPrincipe;
    @FXML private TableColumn<Medicament, String> colForme;
    @FXML private TableColumn<Medicament, String> colDosage;
    @FXML private TableColumn<Medicament, String> colPrix;
    @FXML private TableColumn<Medicament, Number> colStock;
    @FXML private TableColumn<Medicament, Number> colSeuil;
    @FXML private TableColumn<Medicament, String> colStatut;
    @FXML private TableColumn<Medicament, String> colRupture;

    // Detail panel
    @FXML private VBox detailPanel;
    @FXML private Label lblDetailTitle;
    @FXML private TabPane detailTabPane;
    @FXML private Tab tabFiche;
    @FXML private Tab tabLots;
    @FXML private Tab tabCommandes;

    // Fiche (onglet 1)
    @FXML private TextField txtNomCommercial;
    @FXML private TextField txtPrincipeActif;
    @FXML private ComboBox<String> comboForme;
    @FXML private TextField txtDosage;
    @FXML private TextField txtPrix;
    @FXML private Spinner<Integer> spinnerSeuil;
    @FXML private TextArea txtDescription;
    @FXML private CheckBox chkOrdonnance;
    @FXML private CheckBox chkActif;
    @FXML private Button btnDelete;

    // Lots (onglet 2)
    @FXML private Label lblLotsTitle;
    @FXML private TableView<Lot> tableLots;
    @FXML private TableColumn<Lot, String> colLotNumero;
    @FXML private TableColumn<Lot, String> colLotPeremption;
    @FXML private TableColumn<Lot, String> colLotJours;
    @FXML private TableColumn<Lot, String> colLotQuantite;
    @FXML private TableColumn<Lot, String> colLotFournisseur;
    @FXML private TableColumn<Lot, String> colLotPrixAchat;
    @FXML private TableColumn<Lot, String> colLotDateReception;
    @FXML private TableColumn<Lot, String> colLotAction;

    // Commandes (onglet 3)
    @FXML private Button btnCommanderCeProduit;
    @FXML private TableView<Commande> tableCommandes;
    @FXML private TableColumn<Commande, String> colCmdId;
    @FXML private TableColumn<Commande, String> colCmdDate;
    @FXML private TableColumn<Commande, String> colCmdStatut;
    @FXML private TableColumn<Commande, String> colCmdFournisseur;

    // Dialog ajout lot
    @FXML private VBox addLotDialog;
    @FXML private ComboBox<Medicament> comboMedicament;
    @FXML private TextField txtNumeroLot;
    @FXML private DatePicker dpPeremption;
    @FXML private Spinner<Integer> spinnerQte;
    @FXML private ComboBox<Fournisseur> comboFournisseur;
    @FXML private TextField txtPrixAchat;

    // Services (couche service au lieu de DAO direct)
    private final StockService stockService;
    private final AuditService auditService;
    private final PredictionService predictionService;
    private final ExportService exportService;

    // DAOs pour les operations sans service existant
    private final MedicamentDAOImpl medicamentDAO;
    private final LotDAOImpl lotDAO;
    private final FournisseurDAOImpl fournisseurDAO;
    private final CommandeDAOImpl commandeDAO;

    private final ObservableList<Medicament> medicamentData = FXCollections.observableArrayList();
    private final ObservableList<Lot> lotData = FXCollections.observableArrayList();
    private final ObservableList<Commande> commandeData = FXCollections.observableArrayList();

    // Cache stock vendable pour eviter requetes DB repetees
    private Map<Integer, Integer> stockCache = new HashMap<>();
    // Cache medicaments avec peremption proche
    private Set<Integer> peremptionProcheIds = new HashSet<>();
    // Cache predictions
    private Map<Integer, PredictionReapprovisionnement> predictionsCache = new HashMap<>();
    // Tous les medicaments charges (avant filtrage)
    private List<Medicament> allMedicaments = new ArrayList<>();

    private Medicament selectedMedicament;
    private boolean isEditMode = false;
    private boolean isNewMode = false;
    private String activeFilter = null; // null, "STOCK_BAS", "PEREMPTION"

    // Debounce pour la recherche
    private final PauseTransition searchDebounce = new PauseTransition(Duration.millis(300));

    // Flag modifications non sauvegardees
    private boolean formDirty = false;

    // Listes completes pour les ComboBox searchables du dialog
    private List<Medicament> allActiveMedicaments = new ArrayList<>();
    private List<Fournisseur> allActiveFournisseurs = new ArrayList<>();

    // Cache fournisseurs pour les commandes
    private Map<Integer, String> fournisseurNameCache = new HashMap<>();

    public ProduitsStockController() {
        this.stockService = new StockService();
        this.auditService = new AuditService();
        this.predictionService = new PredictionService();
        this.exportService = new ExportService();
        this.medicamentDAO = new MedicamentDAOImpl();
        this.lotDAO = new LotDAOImpl();
        this.fournisseurDAO = new FournisseurDAOImpl();
        this.commandeDAO = new CommandeDAOImpl();
    }

    @FXML
    public void initialize() {
        setupMedicamentTable();
        setupLotTable();
        setupCommandeTable();
        setupFormesGaleniques();
        setupDialog();
        setupBadgeFilters();
        setupSearchDebounce();
        setupFormDirtyListeners();
        setupResponsiveTable(tableMedicaments);
        setupResponsiveTable(tableLots);
        setupResponsiveTable(tableCommandes);
        loadData();

        tableMedicaments.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> handleMedicamentSelection(newVal));
    }

    @Override
    public void onViewDisplayed() {
        handleRefresh();
    }

    // === Debounce recherche ===

    private void setupSearchDebounce() {
        searchDebounce.setOnFinished(e -> executeSearch());
    }

    // === Form dirty listeners ===

    private void setupFormDirtyListeners() {
        txtNomCommercial.textProperty().addListener((obs, o, n) -> formDirty = true);
        txtPrincipeActif.textProperty().addListener((obs, o, n) -> formDirty = true);
        comboForme.valueProperty().addListener((obs, o, n) -> formDirty = true);
        txtDosage.textProperty().addListener((obs, o, n) -> formDirty = true);
        txtPrix.textProperty().addListener((obs, o, n) -> formDirty = true);
        spinnerSeuil.valueProperty().addListener((obs, o, n) -> formDirty = true);
        txtDescription.textProperty().addListener((obs, o, n) -> formDirty = true);
        chkOrdonnance.selectedProperty().addListener((obs, o, n) -> formDirty = true);
        chkActif.selectedProperty().addListener((obs, o, n) -> formDirty = true);
    }

    // === Forme galenique avec autocompletion ===

    private void setupFormesGaleniques() {
        comboForme.setItems(FXCollections.observableArrayList(ALL_FORMES_GALENIQUES));
        setupSearchableComboBox(comboForme, ALL_FORMES_GALENIQUES);
    }

    private void setupSearchableComboBox(ComboBox<String> combo, List<String> allItems) {
        combo.getEditor().textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.isEmpty()) {
                combo.setItems(FXCollections.observableArrayList(allItems));
                return;
            }
            String lower = newVal.toLowerCase();
            List<String> filtered = allItems.stream()
                    .filter(item -> item.toLowerCase().startsWith(lower))
                    .collect(Collectors.toList());
            combo.setItems(FXCollections.observableArrayList(filtered));
            if (!filtered.isEmpty() && !combo.isShowing()) {
                combo.show();
            }
        });
    }

    // === Badge filters ===

    private void setupBadgeFilters() {
        badgeTotal.setOnMouseClicked(e -> toggleFilter(null));
        badgeStockBas.setOnMouseClicked(e -> toggleFilter("STOCK_BAS"));
        badgePeremption.setOnMouseClicked(e -> toggleFilter("PEREMPTION"));
    }

    private void toggleFilter(String filter) {
        if (filter == null || Objects.equals(activeFilter, filter)) {
            activeFilter = null;
        } else {
            activeFilter = filter;
        }
        applyFilter();
        updateBadgeStyles();
    }

    private void applyFilter() {
        if (activeFilter == null) {
            medicamentData.setAll(allMedicaments);
        } else if ("STOCK_BAS".equals(activeFilter)) {
            List<Medicament> filtered = allMedicaments.stream()
                    .filter(m -> stockCache.getOrDefault(m.getIdMedicament(), 0) < m.getSeuilMin())
                    .collect(Collectors.toList());
            medicamentData.setAll(filtered);
        } else if ("PEREMPTION".equals(activeFilter)) {
            List<Medicament> filtered = allMedicaments.stream()
                    .filter(m -> peremptionProcheIds.contains(m.getIdMedicament()))
                    .collect(Collectors.toList());
            medicamentData.setAll(filtered);
        }
        lblCount.setText(medicamentData.size() + " medicament(s)");
    }

    private void updateBadgeStyles() {
        badgeTotal.getStyleClass().remove("badge-disabled");
        badgeStockBas.getStyleClass().remove("badge-disabled");
        badgePeremption.getStyleClass().remove("badge-disabled");
        badgeTotal.getStyleClass().remove("badge-active");
        badgeStockBas.getStyleClass().remove("badge-active");
        badgePeremption.getStyleClass().remove("badge-active");

        if (activeFilter == null) {
            return;
        }

        if ("STOCK_BAS".equals(activeFilter)) {
            badgeStockBas.getStyleClass().add("badge-active");
            badgeTotal.getStyleClass().add("badge-disabled");
            badgePeremption.getStyleClass().add("badge-disabled");
        } else if ("PEREMPTION".equals(activeFilter)) {
            badgePeremption.getStyleClass().add("badge-active");
            badgeTotal.getStyleClass().add("badge-disabled");
            badgeStockBas.getStyleClass().add("badge-disabled");
        }
    }

    // === Table setup ===

    private void setupMedicamentTable() {
        colNom.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getNomCommercial()));

        colOrdonnance.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().isNecessiteOrdonnance() ? "\uD83D\uDD12" : ""));
        colOrdonnance.setStyle("-fx-alignment: CENTER;");

        colPrincipe.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getPrincipeActif()));
        colForme.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getFormeGalenique() != null ? data.getValue().getFormeGalenique() : ""));

        colDosage.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getDosage() != null ? data.getValue().getDosage() : ""));

        colPrix.setCellValueFactory(data -> {
            BigDecimal prix = data.getValue().getPrixPublic();
            return new SimpleStringProperty(prix != null ? prix.setScale(2, RoundingMode.HALF_UP) + " \u20AC" : "");
        });

        colStock.setCellValueFactory(data -> {
            int stock = stockCache.getOrDefault(data.getValue().getIdMedicament(), 0);
            return new SimpleIntegerProperty(stock);
        });

        colSeuil.setCellValueFactory(data ->
                new SimpleIntegerProperty(data.getValue().getSeuilMin()));

        colStatut.setCellValueFactory(data -> {
            int stock = stockCache.getOrDefault(data.getValue().getIdMedicament(), 0);
            int seuil = data.getValue().getSeuilMin();
            if (stock == 0) return new SimpleStringProperty("RUPTURE");
            if (stock < seuil) return new SimpleStringProperty("BAS");
            return new SimpleStringProperty("OK");
        });

        colStatut.setComparator((a, b) -> {
            Map<String, Integer> order = Map.of("OK", 0, "BAS", 1, "RUPTURE", 2);
            return Integer.compare(order.getOrDefault(a, 0), order.getOrDefault(b, 0));
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

        // Colonne Rupture (predictions)
        colRupture.setCellValueFactory(data -> {
            PredictionReapprovisionnement pred = predictionsCache.get(data.getValue().getIdMedicament());
            if (pred == null) return new SimpleStringProperty("-");
            int jours = pred.getJoursAvantRupture();
            if (jours == Integer.MAX_VALUE) return new SimpleStringProperty("\u221E");
            if (jours <= 0) return new SimpleStringProperty("RUPTURE");
            return new SimpleStringProperty(jours + "j");
        });

        colRupture.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if ("RUPTURE".equals(item)) {
                        setStyle("-fx-text-fill: #dc3545; -fx-font-weight: bold;");
                    } else if (!"-".equals(item) && !"\u221E".equals(item)) {
                        try {
                            int jours = Integer.parseInt(item.replace("j", ""));
                            if (jours < 7) {
                                setStyle("-fx-text-fill: #dc3545; -fx-font-weight: bold;");
                            } else if (jours < 14) {
                                setStyle("-fx-text-fill: #fd7e14; -fx-font-weight: bold;");
                            } else {
                                setStyle("-fx-text-fill: #28a745;");
                            }
                        } catch (NumberFormatException e) {
                            setStyle("");
                        }
                    } else {
                        setStyle("-fx-text-fill: #94a3b8;");
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

        colLotPrixAchat.setCellValueFactory(data -> {
            BigDecimal prix = data.getValue().getPrixAchat();
            return new SimpleStringProperty(prix != null ? prix.setScale(2, RoundingMode.HALF_UP) + " \u20AC" : "N/A");
        });

        colLotDateReception.setCellValueFactory(data -> {
            LocalDateTime dr = data.getValue().getDateReception();
            return new SimpleStringProperty(dr != null ? dr.format(DATE_FORMAT) : "N/A");
        });

        colLotFournisseur.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(item);
                    setTooltip(new Tooltip(item));
                }
            }
        });

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

        colLotAction.setCellFactory(column -> new TableCell<>() {
            private final Button btnDel = new Button("X");
            {
                btnDel.setStyle("-fx-background-color: #dc3545; -fx-text-fill: white; -fx-font-size: 10;");
                btnDel.setOnAction(e -> {
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
                    if (lot.isPerime() || lot.getQuantiteStock() == 0) {
                        setGraphic(btnDel);
                    } else {
                        setGraphic(null);
                    }
                }
            }
        });

        tableLots.setItems(lotData);
    }

    private void setupCommandeTable() {
        colCmdId.setCellValueFactory(data -> new SimpleStringProperty(
                String.valueOf(data.getValue().getIdCommande())));
        colCmdDate.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getDateCreation() != null
                        ? data.getValue().getDateCreation().format(DATETIME_FORMAT)
                        : ""));
        colCmdStatut.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getStatut() != null ? data.getValue().getStatut().getLibelle() : ""));
        colCmdFournisseur.setCellValueFactory(data -> {
            Fournisseur f = data.getValue().getFournisseur();
            if (f != null) return new SimpleStringProperty(f.getNom());
            String cached = fournisseurNameCache.get(data.getValue().getIdFournisseur());
            return new SimpleStringProperty(cached != null ? cached : "N/A");
        });

        colCmdFournisseur.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(item);
                    setTooltip(new Tooltip(item));
                }
            }
        });

        colCmdStatut.setCellFactory(column -> new TableCell<>() {
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
                        case "Recue" -> setStyle("-fx-text-fill: #28a745;");
                        case "Annulee" -> setStyle("-fx-text-fill: #dc3545;");
                        default -> setStyle("");
                    }
                }
            }
        });

        tableCommandes.setItems(commandeData);
    }

    private void setupDialog() {
        comboMedicament.setConverter(new StringConverter<>() {
            @Override
            public String toString(Medicament m) {
                return m != null ? m.getNomCommercial() : "";
            }
            @Override
            public Medicament fromString(String s) {
                if (s == null || s.isEmpty()) return null;
                return allActiveMedicaments.stream()
                        .filter(m -> m.getNomCommercial().equalsIgnoreCase(s))
                        .findFirst().orElse(null);
            }
        });

        comboMedicament.getEditor().textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.isEmpty()) {
                comboMedicament.setItems(FXCollections.observableArrayList(allActiveMedicaments));
                return;
            }
            String lower = newVal.toLowerCase();
            List<Medicament> filtered = allActiveMedicaments.stream()
                    .filter(m -> m.getNomCommercial().toLowerCase().contains(lower))
                    .collect(Collectors.toList());
            comboMedicament.setItems(FXCollections.observableArrayList(filtered));
            if (!filtered.isEmpty() && !comboMedicament.isShowing()) {
                comboMedicament.show();
            }
        });

        comboFournisseur.setConverter(new StringConverter<>() {
            @Override
            public String toString(Fournisseur f) {
                return f != null ? f.getNom() : "";
            }
            @Override
            public Fournisseur fromString(String s) {
                if (s == null || s.isEmpty()) return null;
                return allActiveFournisseurs.stream()
                        .filter(f -> f.getNom().equalsIgnoreCase(s))
                        .findFirst().orElse(null);
            }
        });

        comboFournisseur.getEditor().textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.isEmpty()) {
                comboFournisseur.setItems(FXCollections.observableArrayList(allActiveFournisseurs));
                return;
            }
            String lower = newVal.toLowerCase();
            List<Fournisseur> filtered = allActiveFournisseurs.stream()
                    .filter(f -> f.getNom().toLowerCase().contains(lower))
                    .collect(Collectors.toList());
            comboFournisseur.setItems(FXCollections.observableArrayList(filtered));
            if (!filtered.isEmpty() && !comboFournisseur.isShowing()) {
                comboFournisseur.show();
            }
        });

        dpPeremption.setValue(LocalDate.now().plusMonths(12));
    }

    // === Data loading ===

    private void loadData() {
        Task<Void> task = new Task<>() {
            private List<Medicament> medicaments;
            private Map<Integer, Integer> stocks;
            private Set<Integer> peremptionIds;
            private Map<Integer, PredictionReapprovisionnement> predictions;
            private BigDecimal valeurTotale;

            @Override
            protected Void call() throws Exception {
                medicaments = medicamentDAO.findAllActive();

                // Stock vendable via StockService
                stocks = new HashMap<>();
                for (Medicament m : medicaments) {
                    try {
                        stocks.put(m.getIdMedicament(),
                                stockService.getStockVendable(m.getIdMedicament()));
                    } catch (Exception e) {
                        stocks.put(m.getIdMedicament(), 0);
                    }
                }

                // Peremption proche
                peremptionIds = new HashSet<>();
                List<Lot> expiringLots = lotDAO.findExpiringBefore(LocalDate.now().plusMonths(3));
                for (Lot lot : expiringLots) {
                    peremptionIds.add(lot.getIdMedicament());
                }

                // Predictions
                predictions = new HashMap<>();
                for (Medicament m : medicaments) {
                    try {
                        PredictionReapprovisionnement pred = predictionService.genererPrediction(m.getIdMedicament());
                        if (pred != null) {
                            predictions.put(m.getIdMedicament(), pred);
                        }
                    } catch (Exception e) {
                        // Prediction non disponible, ignorer
                    }
                }

                // Valorisation totale du stock
                valeurTotale = BigDecimal.ZERO;
                for (Medicament m : medicaments) {
                    try {
                        List<Lot> lots = lotDAO.findByMedicamentIdSortedByExpiration(m.getIdMedicament());
                        for (Lot lot : lots) {
                            if (lot.isVendable() && lot.getPrixAchat() != null) {
                                valeurTotale = valeurTotale.add(
                                        lot.getPrixAchat().multiply(BigDecimal.valueOf(lot.getQuantiteStock())));
                            }
                        }
                    } catch (Exception e) {
                        // ignorer
                    }
                }

                return null;
            }

            @Override
            protected void succeeded() {
                allMedicaments = medicaments;
                stockCache = stocks;
                peremptionProcheIds = peremptionIds;
                predictionsCache = predictions;

                applyFilter();
                updateStats();
                lblValeurStock.setText(valeurTotale.setScale(2, RoundingMode.HALF_UP) + " \u20AC");
            }

            @Override
            protected void failed() {
                logger.error("Erreur chargement medicaments", getException());
            }
        };
        runAsync(task);
    }

    private void updateStats() {
        int total = allMedicaments.size();
        int stockBas = 0;

        for (Medicament m : allMedicaments) {
            int stock = stockCache.getOrDefault(m.getIdMedicament(), 0);
            if (stock < m.getSeuilMin()) stockBas++;
        }

        lblTotalMedicaments.setText(total + " medicaments");
        lblStockBas.setText(stockBas + " en stock bas");
        lblPeremptionProche.setText(peremptionProcheIds.size() + " peremption proche");
    }

    // === Selection / Detail Panel ===

    private void handleMedicamentSelection(Medicament medicament) {
        if (isNewMode) return;

        // Avertissement si modifications non sauvegardees
        if (formDirty && selectedMedicament != null && medicament != null
                && medicament.getIdMedicament() != selectedMedicament.getIdMedicament()) {
            DialogHelper.showConfirmation(rootPane,
                    "Modifications non sauvegardees",
                    "Vous avez des modifications non sauvegardees. Voulez-vous les abandonner ?",
                    () -> {
                        formDirty = false;
                        proceedWithSelection(medicament);
                    },
                    () -> {
                        // Reselectionner l'ancien medicament
                        tableMedicaments.getSelectionModel().select(selectedMedicament);
                    });
            return;
        }

        proceedWithSelection(medicament);
    }

    private void proceedWithSelection(Medicament medicament) {
        selectedMedicament = medicament;
        if (medicament == null) {
            hideDetailPanel();
            return;
        }

        isEditMode = true;
        isNewMode = false;
        formDirty = false;
        showDetailPanel(medicament);
        fillForm(medicament);
        showEditTabs();
        loadLotsForMedicament(medicament);
        loadCommandesForMedicament(medicament);
    }

    private void showDetailPanel(Medicament medicament) {
        lblDetailTitle.setText(medicament.getNomCommercial());
        btnDelete.setText("Desactiver");
        btnDelete.setVisible(true);
        btnDelete.setManaged(true);
        detailPanel.setVisible(true);
        detailPanel.setManaged(true);
    }

    private void hideDetailPanel() {
        detailPanel.setVisible(false);
        detailPanel.setManaged(false);
        lotData.clear();
        commandeData.clear();
    }

    private void showEditTabs() {
        if (!detailTabPane.getTabs().contains(tabLots)) {
            detailTabPane.getTabs().add(tabLots);
        }
        if (!detailTabPane.getTabs().contains(tabCommandes)) {
            detailTabPane.getTabs().add(tabCommandes);
        }
    }

    private void hideEditTabs() {
        detailTabPane.getTabs().remove(tabLots);
        detailTabPane.getTabs().remove(tabCommandes);
    }

    @FXML
    private void handleCloseDetail() {
        isNewMode = false;
        isEditMode = false;
        formDirty = false;
        selectedMedicament = null;
        hideDetailPanel();
        tableMedicaments.getSelectionModel().clearSelection();
    }

    private void fillForm(Medicament medicament) {
        txtNomCommercial.setText(medicament.getNomCommercial());
        txtPrincipeActif.setText(medicament.getPrincipeActif());
        comboForme.setValue(medicament.getFormeGalenique());
        txtDosage.setText(medicament.getDosage());
        txtPrix.setText(medicament.getPrixPublic() != null ? medicament.getPrixPublic().toString() : "");
        spinnerSeuil.getValueFactory().setValue(medicament.getSeuilMin());
        txtDescription.setText(medicament.getDescription());
        chkOrdonnance.setSelected(medicament.isNecessiteOrdonnance());
        chkActif.setSelected(medicament.isActif());
        // Reset dirty flag after filling
        formDirty = false;
    }

    private void resetForm() {
        txtNomCommercial.clear();
        txtPrincipeActif.clear();
        comboForme.setValue(null);
        comboForme.getEditor().clear();
        txtDosage.clear();
        txtPrix.clear();
        spinnerSeuil.getValueFactory().setValue(10);
        txtDescription.clear();
        chkOrdonnance.setSelected(false);
        chkActif.setSelected(true);
        formDirty = false;
    }

    // === Lots ===

    private void loadLotsForMedicament(Medicament medicament) {
        Task<List<Lot>> task = new Task<>() {
            @Override
            protected List<Lot> call() throws Exception {
                return lotDAO.findByMedicamentIdSortedByExpiration(medicament.getIdMedicament());
            }

            @Override
            protected void succeeded() {
                lotData.setAll(getValue());
                lblLotsTitle.setText("Lots (" + getValue().size() + ")");
            }

            @Override
            protected void failed() {
                logger.error("Erreur chargement lots", getException());
            }
        };
        runAsync(task);
    }

    // === Commandes ===

    private void loadCommandesForMedicament(Medicament medicament) {
        Task<List<Commande>> task = new Task<>() {
            private Map<Integer, String> fournisseurNames;

            @Override
            protected List<Commande> call() throws Exception {
                List<Commande> commandes = commandeDAO.findByMedicament(medicament.getIdMedicament());
                // Pre-charger les noms de fournisseurs
                fournisseurNames = new HashMap<>();
                Set<Integer> idsToLoad = new HashSet<>();
                for (Commande c : commandes) {
                    if (c.getFournisseur() == null && c.getIdFournisseur() > 0) {
                        idsToLoad.add(c.getIdFournisseur());
                    }
                }
                for (int id : idsToLoad) {
                    try {
                        fournisseurDAO.findById(id).ifPresent(f -> fournisseurNames.put(id, f.getNom()));
                    } catch (Exception e) {
                        fournisseurNames.put(id, "N/A");
                    }
                }
                return commandes;
            }

            @Override
            protected void succeeded() {
                fournisseurNameCache.putAll(fournisseurNames);
                commandeData.setAll(getValue());
            }

            @Override
            protected void failed() {
                logger.error("Erreur chargement commandes", getException());
            }
        };
        runAsync(task);
    }

    @FXML
    private void handleCommanderCeProduit() {
        if (selectedMedicament == null) return;
        if (dashboardController != null) {
            dashboardController.navigateToCommandeWithMedicament(selectedMedicament.getNomCommercial());
        }
    }

    // === Actions barre d'outils ===

    @FXML
    private void handleSearch() {
        searchDebounce.playFromStart();
    }

    private void executeSearch() {
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
                allMedicaments = getValue();
                stockCache.clear();
                for (Medicament m : allMedicaments) {
                    try {
                        stockCache.put(m.getIdMedicament(),
                                stockService.getStockVendable(m.getIdMedicament()));
                    } catch (Exception e) {
                        stockCache.put(m.getIdMedicament(), 0);
                    }
                }
                activeFilter = null;
                updateBadgeStyles();
                applyFilter();
                updateStats();
            }
        };
        runAsync(task);
    }

    @FXML
    private void handleRefresh() {
        loadData();
        if (selectedMedicament != null && !isNewMode) {
            loadLotsForMedicament(selectedMedicament);
            loadCommandesForMedicament(selectedMedicament);
        }
    }

    @FXML
    private void handleNewMedicament() {
        isNewMode = true;
        isEditMode = false;
        selectedMedicament = null;
        tableMedicaments.getSelectionModel().clearSelection();

        resetForm();
        lblDetailTitle.setText("Nouveau Medicament");
        btnDelete.setVisible(false);
        btnDelete.setManaged(false);
        detailPanel.setVisible(true);
        detailPanel.setManaged(true);
        hideEditTabs();
        detailTabPane.getSelectionModel().select(tabFiche);
        lotData.clear();
        commandeData.clear();
    }

    @FXML
    private void handleCancelForm() {
        if (isNewMode) {
            handleCloseDetail();
        } else if (selectedMedicament != null) {
            fillForm(selectedMedicament);
        }
    }

    @FXML
    private void handleSaveMedicament() {
        boolean valid = true;

        if (txtNomCommercial.getText().trim().isEmpty()) {
            txtNomCommercial.setStyle("-fx-border-color: #dc3545;");
            valid = false;
        } else {
            txtNomCommercial.setStyle("");
        }

        if (txtPrincipeActif.getText().trim().isEmpty()) {
            txtPrincipeActif.setStyle("-fx-border-color: #dc3545;");
            valid = false;
        } else {
            txtPrincipeActif.setStyle("");
        }

        BigDecimal prix = null;
        try {
            prix = new BigDecimal(txtPrix.getText().trim().replace(",", "."));
            if (prix.compareTo(BigDecimal.ZERO) < 0) {
                throw new NumberFormatException("Prix negatif");
            }
            txtPrix.setStyle("");
        } catch (Exception e) {
            txtPrix.setStyle("-fx-border-color: #dc3545;");
            valid = false;
        }

        if (!valid) {
            DialogHelper.showWarning(rootPane, "Validation", "Veuillez corriger les champs en rouge.");
            return;
        }

        Medicament medicament = (isEditMode && selectedMedicament != null) ? selectedMedicament : new Medicament();
        medicament.setNomCommercial(txtNomCommercial.getText().trim());
        medicament.setPrincipeActif(txtPrincipeActif.getText().trim());
        medicament.setFormeGalenique(comboForme.getValue());
        medicament.setDosage(txtDosage.getText().trim());
        medicament.setPrixPublic(prix);
        medicament.setSeuilMin(spinnerSeuil.getValue());
        medicament.setDescription(txtDescription.getText().trim());
        medicament.setNecessiteOrdonnance(chkOrdonnance.isSelected());
        medicament.setActif(chkActif.isSelected());

        boolean creating = isNewMode;

        Task<Medicament> task = new Task<>() {
            @Override
            protected Medicament call() throws Exception {
                if (creating) {
                    return medicamentDAO.save(medicament);
                } else {
                    medicamentDAO.update(medicament);
                    return medicament;
                }
            }

            @Override
            protected void succeeded() {
                Medicament saved = getValue();
                formDirty = false;
                if (creating) {
                    auditService.logCreation("Medicament", saved.getIdMedicament(), saved.getNomCommercial());
                    DialogHelper.showInfo(rootPane, "Succes", "Medicament cree avec succes.");
                } else {
                    auditService.logModification("Medicament", saved.getIdMedicament(), saved.getNomCommercial());
                    DialogHelper.showInfo(rootPane, "Succes", "Medicament modifie avec succes.");
                }
                isNewMode = false;
                loadData();
                if (creating) {
                    handleCloseDetail();
                }
            }

            @Override
            protected void failed() {
                logger.error("Erreur sauvegarde medicament", getException());
                DialogHelper.showError(rootPane, "Erreur", "Impossible de sauvegarder: " + getException().getMessage());
            }
        };
        runAsync(task);
    }

    @FXML
    private void handleDeleteMedicament() {
        if (selectedMedicament == null) return;

        DialogHelper.showConfirmation(rootPane,
                "Desactiver le medicament ?",
                "Voulez-vous vraiment desactiver \"" + selectedMedicament.getNomCommercial() + "\" ?\n\n" +
                        "Le medicament ne sera plus visible dans le catalogue mais les donnees seront conservees.",
                () -> {
                    selectedMedicament.setActif(false);
                    Task<Void> task = new Task<>() {
                        @Override
                        protected Void call() throws Exception {
                            medicamentDAO.update(selectedMedicament);
                            return null;
                        }

                        @Override
                        protected void succeeded() {
                            auditService.logSuppression("Medicament", selectedMedicament.getIdMedicament(),
                                    selectedMedicament.getNomCommercial());
                            DialogHelper.showInfo(rootPane, "Succes", "Medicament desactive.");
                            handleCloseDetail();
                            loadData();
                        }

                        @Override
                        protected void failed() {
                            logger.error("Erreur desactivation medicament", getException());
                            selectedMedicament.setActif(true); // rollback local
                            DialogHelper.showError(rootPane, "Erreur",
                                    "Impossible de desactiver ce medicament.");
                        }
                    };
                    runAsync(task);
                },
                () -> { /* annule */ });
    }

    // === Lots dialog ===

    @FXML
    private void handleAddLot() {
        showAddLotDialog(null);
    }

    @FXML
    private void handleAddLotToSelected() {
        showAddLotDialog(selectedMedicament);
    }

    private void showAddLotDialog(Medicament preselected) {
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
                allActiveMedicaments = medicaments;
                allActiveFournisseurs = fournisseurs;
                comboMedicament.setItems(FXCollections.observableArrayList(medicaments));
                comboFournisseur.setItems(FXCollections.observableArrayList(fournisseurs));

                if (preselected != null) {
                    comboMedicament.setValue(preselected);
                } else {
                    comboMedicament.setValue(null);
                    comboMedicament.getEditor().clear();
                }

                txtNumeroLot.clear();
                dpPeremption.setValue(LocalDate.now().plusMonths(12));
                spinnerQte.getValueFactory().setValue(1);
                txtPrixAchat.clear();
                comboFournisseur.setValue(null);
                comboFournisseur.getEditor().clear();

                txtNumeroLot.setStyle("");
                txtPrixAchat.setStyle("");

                addLotDialog.setVisible(true);
                addLotDialog.setManaged(true);
            }
        };
        runAsync(task);
    }

    @FXML
    private void handleCancelAddLot() {
        addLotDialog.setVisible(false);
        addLotDialog.setManaged(false);
    }

    @FXML
    private void handleConfirmAddLot() {
        boolean valid = true;

        Medicament selectedMed = comboMedicament.getValue();
        if (selectedMed == null) {
            String text = comboMedicament.getEditor().getText();
            if (text != null && !text.isEmpty()) {
                selectedMed = allActiveMedicaments.stream()
                        .filter(m -> m.getNomCommercial().equalsIgnoreCase(text))
                        .findFirst().orElse(null);
            }
        }
        if (selectedMed == null) {
            DialogHelper.showWarning(rootPane, "Attention", "Veuillez selectionner un medicament valide.");
            return;
        }

        if (txtNumeroLot.getText().trim().isEmpty()) {
            txtNumeroLot.setStyle("-fx-border-color: #dc3545;");
            valid = false;
        } else {
            txtNumeroLot.setStyle("");
        }

        if (dpPeremption.getValue() == null) {
            valid = false;
        } else if (dpPeremption.getValue().isBefore(LocalDate.now())) {
            DialogHelper.showConfirmation(rootPane,
                    "Date passee",
                    "La date de peremption est deja passee. Voulez-vous continuer ?",
                    () -> proceedAddLot(comboMedicament.getValue()),
                    () -> { });
            return;
        }

        Fournisseur selectedFourn = comboFournisseur.getValue();
        if (selectedFourn == null) {
            String text = comboFournisseur.getEditor().getText();
            if (text != null && !text.isEmpty()) {
                selectedFourn = allActiveFournisseurs.stream()
                        .filter(f -> f.getNom().equalsIgnoreCase(text))
                        .findFirst().orElse(null);
            }
        }
        if (selectedFourn == null) {
            DialogHelper.showWarning(rootPane, "Attention", "Veuillez selectionner un fournisseur valide.");
            return;
        }

        BigDecimal prixAchat;
        try {
            prixAchat = new BigDecimal(txtPrixAchat.getText().replace(",", "."));
            txtPrixAchat.setStyle("");
        } catch (Exception e) {
            txtPrixAchat.setStyle("-fx-border-color: #dc3545;");
            valid = false;
            prixAchat = BigDecimal.ZERO;
        }

        if (!valid) {
            DialogHelper.showWarning(rootPane, "Validation", "Veuillez corriger les champs en rouge.");
            return;
        }

        proceedAddLot(selectedMed);
    }

    private void proceedAddLot(Medicament selectedMed) {
        if (selectedMed == null) return;

        Fournisseur selectedFourn = comboFournisseur.getValue();
        if (selectedFourn == null) {
            String text = comboFournisseur.getEditor().getText();
            if (text != null && !text.isEmpty()) {
                selectedFourn = allActiveFournisseurs.stream()
                        .filter(f -> f.getNom().equalsIgnoreCase(text))
                        .findFirst().orElse(null);
            }
        }
        if (selectedFourn == null) return;

        BigDecimal prixAchat;
        try {
            prixAchat = new BigDecimal(txtPrixAchat.getText().replace(",", "."));
        } catch (Exception e) {
            return;
        }

        Lot lot = new Lot();
        lot.setIdMedicament(selectedMed.getIdMedicament());
        lot.setIdFournisseur(selectedFourn.getIdFournisseur());
        lot.setNumeroLot(txtNumeroLot.getText().trim());
        lot.setDatePeremption(dpPeremption.getValue());
        lot.setQuantiteStock(spinnerQte.getValue());
        lot.setPrixAchat(prixAchat);

        final String medNom = selectedMed.getNomCommercial();

        Task<Lot> saveTask = new Task<>() {
            @Override
            protected Lot call() throws Exception {
                return lotDAO.save(lot);
            }

            @Override
            protected void succeeded() {
                Lot saved = getValue();
                auditService.logCreation("Lot", saved.getIdLot(),
                        "Lot " + saved.getNumeroLot() + " pour medicament " + medNom);
                handleCancelAddLot();
                handleRefresh();
                DialogHelper.showInfo(rootPane, "Succes", "Lot ajoute avec succes.");
            }

            @Override
            protected void failed() {
                logger.error("Erreur ajout lot", getException());
                DialogHelper.showError(rootPane, "Erreur", "Impossible d'ajouter le lot: " + getException().getMessage());
            }
        };
        runAsync(saveTask);
    }

    private void handleDeleteLot(Lot lot) {
        DialogHelper.showConfirmation(rootPane,
                "Supprimer le lot ?",
                "Voulez-vous vraiment supprimer le lot " + lot.getNumeroLot() + " ?",
                () -> {
                    Task<Void> task = new Task<>() {
                        @Override
                        protected Void call() throws Exception {
                            lotDAO.delete(lot.getIdLot());
                            return null;
                        }

                        @Override
                        protected void succeeded() {
                            auditService.logSuppression("Lot", lot.getIdLot(), "Lot " + lot.getNumeroLot());
                            if (selectedMedicament != null) {
                                loadLotsForMedicament(selectedMedicament);
                            }
                            loadData();
                        }

                        @Override
                        protected void failed() {
                            logger.error("Erreur suppression lot", getException());
                            DialogHelper.showError(rootPane, "Erreur", "Impossible de supprimer le lot.");
                        }
                    };
                    runAsync(task);
                },
                () -> { /* annule */ });
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
            DialogHelper.showInfo(rootPane, "Export reussi",
                    "Le fichier CSV a ete genere:\n" + filePath);
        });

        exportTask.setOnFailed(event -> {
            logger.error("Erreur lors de l'export CSV", exportTask.getException());
            DialogHelper.showError(rootPane, "Erreur",
                    "Une erreur est survenue lors de l'export CSV.");
        });

        runAsync(exportTask);
    }
}
