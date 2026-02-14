package com.sgpa.controller;

import com.sgpa.dao.MedicamentDAO;
import com.sgpa.dao.impl.MedicamentDAOImpl;
import com.sgpa.dto.LigneVenteDTO;
import com.sgpa.exception.DAOException;
import com.sgpa.exception.ServiceException;
import com.sgpa.model.Medicament;
import com.sgpa.model.Utilisateur;
import com.sgpa.model.Vente;
import com.sgpa.service.RapportService;
import com.sgpa.service.StockService;
import com.sgpa.service.VenteService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.animation.PauseTransition;
import javafx.util.Duration;

import java.io.File;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Controleur pour l'ecran de vente.
 * Implemente l'interface de vente avec algorithme FEFO.
 *
 * @author SGPA Team
 * @version 1.0
 */
public class VenteController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(VenteController.class);
    private static final DecimalFormat PRICE_FORMAT = new DecimalFormat("#,##0.00");

    @FXML private TextField searchField;
    @FXML private TableView<MedicamentStock> tableMedicaments;
    @FXML private TableColumn<MedicamentStock, String> colNom;
    @FXML private TableColumn<MedicamentStock, String> colPrincipe;
    @FXML private TableColumn<MedicamentStock, String> colPrix;
    @FXML private TableColumn<MedicamentStock, String> colStock;

    @FXML private Spinner<Integer> spinnerQuantite;
    @FXML private Button btnAjouter;

    @FXML private TableView<LignePanier> tablePanier;
    @FXML private TableColumn<LignePanier, String> colPanierNom;
    @FXML private TableColumn<LignePanier, String> colPanierQte;
    @FXML private TableColumn<LignePanier, String> colPanierPrix;
    @FXML private TableColumn<LignePanier, String> colPanierTotal;
    @FXML private TableColumn<LignePanier, Void> colPanierAction;

    @FXML private Label lblNbArticles;
    @FXML private Label lblTotal;
    @FXML private CheckBox chkOrdonnance;
    @FXML private HBox ordonnanceBox;
    @FXML private TextField txtNumeroOrdonnance;
    @FXML private Button btnValider;
    @FXML private Button btnViderPanier;

    private final MedicamentDAO medicamentDAO;
    private final StockService stockService;
    private final VenteService venteService;
    private final RapportService rapportService;

    private final ObservableList<MedicamentStock> medicamentsData = FXCollections.observableArrayList();
    private final ObservableList<LignePanier> panierData = FXCollections.observableArrayList();
    private final PauseTransition searchDebounce = new PauseTransition(Duration.millis(250));

    public VenteController() {
        this.medicamentDAO = new MedicamentDAOImpl();
        this.stockService = new StockService();
        this.venteService = new VenteService();
        this.rapportService = new RapportService();
    }

    @FXML
    public void initialize() {
        setupMedicamentsTable();
        setupPanierTable();
        setupOrdonnanceCheckbox();
        setupResponsiveTable(tableMedicaments);
        setupResponsiveTable(tablePanier);
        loadAllMedicaments();
    }

    private void setupMedicamentsTable() {
        colNom.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().nom));
        colPrincipe.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().principeActif));
        colPrix.setCellValueFactory(data -> new SimpleStringProperty(PRICE_FORMAT.format(data.getValue().prix) + " EUR"));
        colStock.setCellValueFactory(data -> {
            int stock = data.getValue().stock;
            return new SimpleStringProperty(String.valueOf(stock));
        });

        // Colorer le stock selon le niveau
        colStock.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    int stock = Integer.parseInt(item);
                    if (stock == 0) {
                        setStyle("-fx-text-fill: #dc3545; -fx-font-weight: bold; -fx-alignment: center;");
                    } else if (stock < 10) {
                        setStyle("-fx-text-fill: #fd7e14; -fx-font-weight: bold; -fx-alignment: center;");
                    } else {
                        setStyle("-fx-text-fill: #28a745; -fx-alignment: center;");
                    }
                }
            }
        });

        tableMedicaments.setItems(medicamentsData);

        // Activer le bouton ajouter quand une ligne est selectionnee
        tableMedicaments.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            boolean hasSelection = newVal != null && newVal.stock > 0;
            btnAjouter.setDisable(!hasSelection);
            if (newVal != null) {
                spinnerQuantite.getValueFactory().setValue(1);
                ((SpinnerValueFactory.IntegerSpinnerValueFactory) spinnerQuantite.getValueFactory())
                        .setMax(newVal.stock);
            }
        });

        // Simple clic pour ajouter au panier (incremente si deja present)
        tableMedicaments.setOnMouseClicked(event -> {
            if (event.getClickCount() == 1) {
                MedicamentStock selected = tableMedicaments.getSelectionModel().getSelectedItem();
                if (selected != null && selected.stock > 0) {
                    handleAddToCart();
                }
            }
        });
    }

    private void setupPanierTable() {
        colPanierNom.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().nom));
        colPanierQte.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().quantite)));
        colPanierPrix.setCellValueFactory(data -> new SimpleStringProperty(PRICE_FORMAT.format(data.getValue().prixUnitaire)));
        colPanierTotal.setCellValueFactory(data -> new SimpleStringProperty(PRICE_FORMAT.format(data.getValue().getTotal())));

        // Bouton supprimer
        colPanierAction.setCellFactory(column -> new TableCell<>() {
            private final Button btnDelete = new Button();

            {
                FontIcon icon = new FontIcon("fas-times");
                icon.setIconSize(12);
                btnDelete.setGraphic(icon);
                btnDelete.getStyleClass().add("delete-button");
                btnDelete.setOnAction(event -> {
                    LignePanier ligne = getTableView().getItems().get(getIndex());
                    panierData.remove(ligne);
                    updateTotals();
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btnDelete);
            }
        });

        tablePanier.setItems(panierData);
    }

    private void setupOrdonnanceCheckbox() {
        chkOrdonnance.selectedProperty().addListener((obs, oldVal, newVal) -> {
            ordonnanceBox.setVisible(newVal);
            ordonnanceBox.setManaged(newVal);
            if (!newVal) {
                txtNumeroOrdonnance.clear();
            }
        });
    }

    private void loadAllMedicaments() {
        Task<List<MedicamentStock>> loadTask = new Task<>() {
            @Override
            protected List<MedicamentStock> call() throws Exception {
                List<Medicament> medicaments = medicamentDAO.findAllActive();
                List<MedicamentStock> result = new ArrayList<>();

                for (Medicament med : medicaments) {
                    int stock = stockService.getStockVendable(med.getIdMedicament());
                    result.add(new MedicamentStock(med, stock));
                }
                return result;
            }
        };

        loadTask.setOnSucceeded(event -> {
            medicamentsData.setAll(loadTask.getValue());
        });

        loadTask.setOnFailed(event -> {
            logger.error("Erreur lors du chargement des medicaments", loadTask.getException());
        });

        runAsync(loadTask);
    }

    @FXML
    private void handleSearch() {
        searchDebounce.setOnFinished(e -> executeSearch());
        searchDebounce.playFromStart();
    }

    private void executeSearch() {
        String search = searchField.getText().trim().toLowerCase();
        if (search.isEmpty()) {
            loadAllMedicaments();
            return;
        }

        Task<List<MedicamentStock>> searchTask = new Task<>() {
            @Override
            protected List<MedicamentStock> call() throws Exception {
                List<Medicament> medicaments = medicamentDAO.findByNom(search);
                List<MedicamentStock> result = new ArrayList<>();

                for (Medicament med : medicaments) {
                    int stock = stockService.getStockVendable(med.getIdMedicament());
                    result.add(new MedicamentStock(med, stock));
                }
                return result;
            }
        };

        searchTask.setOnSucceeded(event -> {
            medicamentsData.setAll(searchTask.getValue());
        });

        runAsync(searchTask);
    }

    @FXML
    private void handleAddToCart() {
        MedicamentStock selected = tableMedicaments.getSelectionModel().getSelectedItem();
        if (selected == null || selected.stock == 0) return;

        int quantite = spinnerQuantite.getValue();
        if (quantite > selected.stock) {
            showAlert(Alert.AlertType.WARNING, "Stock insuffisant",
                    "Stock disponible: " + selected.stock + " unites");
            return;
        }

        // Verifier si le medicament est deja dans le panier
        for (LignePanier ligne : panierData) {
            if (ligne.idMedicament == selected.id) {
                int newQte = ligne.quantite + quantite;
                if (newQte > selected.stock) {
                    showAlert(Alert.AlertType.WARNING, "Stock insuffisant",
                            "Vous avez deja " + ligne.quantite + " dans le panier. Stock disponible: " + selected.stock);
                    return;
                }
                ligne.quantite = newQte;
                tablePanier.refresh();
                updateTotals();
                return;
            }
        }

        // Ajouter nouvelle ligne
        panierData.add(new LignePanier(selected.id, selected.nom, quantite, selected.prix));
        updateTotals();
    }

    @FXML
    private void handleClearCart() {
        if (panierData.isEmpty()) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Vider le panier");
        confirm.setHeaderText("Confirmer");
        confirm.setContentText("Voulez-vous vraiment vider le panier ?");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                panierData.clear();
                updateTotals();
            }
        });
    }

    @FXML
    private void handleValidateSale() {
        if (panierData.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Panier vide", "Ajoutez des medicaments au panier");
            return;
        }

        // Preparer les lignes de vente
        List<LigneVenteDTO> lignes = new ArrayList<>();
        for (LignePanier ligne : panierData) {
            lignes.add(new LigneVenteDTO(ligne.idMedicament, ligne.quantite));
        }

        // Desactiver le bouton pendant le traitement
        btnValider.setDisable(true);

        Task<Vente> venteTask = new Task<>() {
            @Override
            protected Vente call() throws Exception {
                return venteService.creerVente(
                        lignes,
                        currentUser != null ? currentUser.getIdUtilisateur() : 1,
                        chkOrdonnance.isSelected()
                );
            }
        };

        venteTask.setOnSucceeded(event -> {
            Vente vente = venteTask.getValue();
            logger.info("Vente creee avec succes: ID={}, Montant={}",
                    vente.getIdVente(), vente.getMontantTotal());

            // Proposer l'impression du ticket
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Vente enregistree");
            confirm.setHeaderText("Vente N°" + vente.getIdVente() + " enregistree avec succes");
            confirm.setContentText("Montant total: " + PRICE_FORMAT.format(vente.getMontantTotal()) + " EUR\n\n" +
                    "Voulez-vous imprimer le ticket de caisse ?");

            ButtonType btnImprimer = new ButtonType("Imprimer ticket");
            ButtonType btnNon = new ButtonType("Non merci");
            confirm.getButtonTypes().setAll(btnImprimer, btnNon);

            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isPresent() && result.get() == btnImprimer) {
                imprimerTicket(vente);
            }

            // Reinitialiser
            panierData.clear();
            updateTotals();
            loadAllMedicaments(); // Rafraichir les stocks
            btnValider.setDisable(false);
        });

        venteTask.setOnFailed(event -> {
            Throwable ex = venteTask.getException();
            logger.error("Erreur lors de la vente", ex);

            String message = "Erreur lors de l'enregistrement de la vente";
            if (ex instanceof ServiceException) {
                message = ex.getMessage();
            }

            showAlert(Alert.AlertType.ERROR, "Erreur", message);
            btnValider.setDisable(false);
        });

        runAsync(venteTask);
    }

    private void updateTotals() {
        int nbArticles = panierData.stream().mapToInt(l -> l.quantite).sum();
        BigDecimal total = panierData.stream()
                .map(LignePanier::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        lblNbArticles.setText(String.valueOf(nbArticles));
        lblTotal.setText(PRICE_FORMAT.format(total) + " EUR");

        btnValider.setDisable(panierData.isEmpty());
        btnViderPanier.setDisable(panierData.isEmpty());
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Imprime le ticket de caisse pour une vente.
     *
     * @param vente la vente a imprimer
     */
    private void imprimerTicket(Vente vente) {
        Task<String> printTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                String nomVendeur = currentUser != null
                        ? currentUser.getNomComplet()
                        : "Vendeur";
                return rapportService.genererTicketCaisse(vente, nomVendeur);
            }
        };

        printTask.setOnSucceeded(event -> {
            String filePath = printTask.getValue();
            logger.info("Ticket genere: {}", filePath);

            // Ouvrir le PDF avec l'application par defaut
            try {
                File pdfFile = new File(filePath);
                if (pdfFile.exists()) {
                    String os = System.getProperty("os.name").toLowerCase();
                    ProcessBuilder pb;
                    if (os.contains("win")) {
                        pb = new ProcessBuilder("cmd", "/c", "start", "", filePath);
                    } else if (os.contains("mac")) {
                        pb = new ProcessBuilder("open", filePath);
                    } else {
                        pb = new ProcessBuilder("xdg-open", filePath);
                    }
                    pb.start();
                }
                showAlert(Alert.AlertType.INFORMATION, "Ticket genere",
                        "Le ticket a ete genere:\n" + filePath);
            } catch (Throwable e) {
                logger.warn("Impossible d'ouvrir le PDF automatiquement", e);
                showAlert(Alert.AlertType.INFORMATION, "Ticket genere",
                        "Le ticket a ete genere:\n" + filePath + "\n\n" +
                                "(Ouverture automatique non disponible)");
            }
        });

        printTask.setOnFailed(event -> {
            logger.error("Erreur lors de la generation du ticket", printTask.getException());
            showAlert(Alert.AlertType.ERROR, "Erreur",
                    "Impossible de generer le ticket de caisse.");
        });

        runAsync(printTask);
    }

    /**
     * Classe interne pour l'affichage des medicaments avec stock.
     */
    public static class MedicamentStock {
        public final int id;
        public final String nom;
        public final String principeActif;
        public final BigDecimal prix;
        public final int stock;

        public MedicamentStock(Medicament med, int stock) {
            this.id = med.getIdMedicament();
            this.nom = med.getNomCommercial();
            this.principeActif = med.getPrincipeActif();
            this.prix = med.getPrixPublic();
            this.stock = stock;
        }
    }

    /**
     * Classe interne pour les lignes du panier.
     */
    public static class LignePanier {
        public final int idMedicament;
        public final String nom;
        public int quantite;
        public final BigDecimal prixUnitaire;

        public LignePanier(int idMedicament, String nom, int quantite, BigDecimal prixUnitaire) {
            this.idMedicament = idMedicament;
            this.nom = nom;
            this.quantite = quantite;
            this.prixUnitaire = prixUnitaire;
        }

        public BigDecimal getTotal() {
            return prixUnitaire.multiply(BigDecimal.valueOf(quantite));
        }
    }
}
