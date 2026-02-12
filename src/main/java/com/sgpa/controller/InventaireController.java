package com.sgpa.controller;

import com.sgpa.exception.ServiceException;
import com.sgpa.model.ComptageInventaire;
import com.sgpa.model.Lot;
import com.sgpa.model.SessionInventaire;
import com.sgpa.model.enums.MotifEcart;
import com.sgpa.model.enums.StatutInventaire;
import com.sgpa.service.InventaireService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controleur pour l'ecran de gestion des inventaires.
 *
 * @author SGPA Team
 * @version 1.0
 */
public class InventaireController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(InventaireController.class);
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Session
    @FXML private VBox paneSession;
    @FXML private Label lblSessionStatut;
    @FXML private VBox paneNoSession;
    @FXML private TextField txtNotes;
    @FXML private VBox paneSessionEnCours;
    @FXML private Label lblSessionId;
    @FXML private Label lblSessionDebut;
    @FXML private Label lblNbComptages;
    @FXML private Label lblNbEcarts;

    // Lots
    @FXML private TextField txtRecherche;
    @FXML private TableView<LotRow> tableLots;
    @FXML private TableColumn<LotRow, String> colMedicament;
    @FXML private TableColumn<LotRow, String> colLot;
    @FXML private TableColumn<LotRow, String> colPeremption;
    @FXML private TableColumn<LotRow, String> colStockTheorique;
    @FXML private TableColumn<LotRow, String> colStockPhysique;
    @FXML private TableColumn<LotRow, String> colEcart;
    @FXML private TableColumn<LotRow, String> colStatut;

    // Comptage
    @FXML private VBox paneComptage;
    @FXML private Label lblMedicament;
    @FXML private Label lblNumeroLot;
    @FXML private Label lblPeremption;
    @FXML private Label lblQteTheorique;
    @FXML private Spinner<Integer> spinnerQtePhysique;
    @FXML private Label lblEcartCalcule;
    @FXML private VBox paneMotif;
    @FXML private ComboBox<MotifEcart> comboMotif;
    @FXML private TextArea txtCommentaire;

    // Historique
    @FXML private TableView<SessionRow> tableHistorique;
    @FXML private TableColumn<SessionRow, String> colHistId;
    @FXML private TableColumn<SessionRow, String> colHistDebut;
    @FXML private TableColumn<SessionRow, String> colHistFin;
    @FXML private TableColumn<SessionRow, String> colHistStatut;
    @FXML private TableColumn<SessionRow, String> colHistComptages;
    @FXML private TableColumn<SessionRow, String> colHistEcarts;
    @FXML private TableColumn<SessionRow, String> colHistUtilisateur;

    private final InventaireService inventaireService;
    private final ObservableList<LotRow> lotsData = FXCollections.observableArrayList();
    private final ObservableList<SessionRow> sessionsData = FXCollections.observableArrayList();
    private FilteredList<LotRow> filteredLots;

    private SessionInventaire sessionEnCours;
    private LotRow lotSelectionne;
    private Map<Integer, ComptageInventaire> comptagesMap = new HashMap<>();

    public InventaireController() {
        this.inventaireService = new InventaireService();
    }

    @FXML
    public void initialize() {
        setupMotifs();
        setupLotsTable();
        setupHistoriqueTable();
        setupSpinner();
        setupSearch();
        setupResponsiveTable(tableLots);
        setupResponsiveTable(tableHistorique);
        loadData();
    }

    private void setupMotifs() {
        comboMotif.setItems(FXCollections.observableArrayList(MotifEcart.values()));
        comboMotif.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(MotifEcart motif) {
                return motif != null ? motif.getLibelle() : "";
            }

            @Override
            public MotifEcart fromString(String string) {
                return MotifEcart.fromString(string);
            }
        });
    }

    private void setupLotsTable() {
        colMedicament.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().medicament));
        colLot.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().numeroLot));
        colPeremption.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().datePeremption));
        colStockTheorique.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().stockTheorique)));
        colStockPhysique.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().stockPhysique != null ? String.valueOf(data.getValue().stockPhysique) : "-"));
        colEcart.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().ecart != null ? String.valueOf(data.getValue().ecart) : "-"));
        colStatut.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().statut));

        // Colorer les ecarts
        colEcart.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || "-".equals(item)) {
                    setText(item);
                    setStyle("");
                } else {
                    setText(item);
                    int ecart = Integer.parseInt(item);
                    if (ecart < 0) {
                        setStyle("-fx-text-fill: #dc3545; -fx-font-weight: bold;");
                    } else if (ecart > 0) {
                        setStyle("-fx-text-fill: #28a745; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #6c757d;");
                    }
                }
            }
        });

        filteredLots = new FilteredList<>(lotsData, p -> true);
        tableLots.setItems(filteredLots);

        // Selection pour comptage
        tableLots.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && sessionEnCours != null) {
                selectLotForComptage(newVal);
            }
        });
    }

    private void setupHistoriqueTable() {
        colHistId.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().id)));
        colHistDebut.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().dateDebut));
        colHistFin.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().dateFin));
        colHistStatut.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().statut));
        colHistComptages.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().nbComptages)));
        colHistEcarts.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().nbEcarts)));
        colHistUtilisateur.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().utilisateur));

        tableHistorique.setItems(sessionsData);
    }

    private void setupSpinner() {
        SpinnerValueFactory<Integer> valueFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 99999, 0);
        spinnerQtePhysique.setValueFactory(valueFactory);
        spinnerQtePhysique.setEditable(true);

        // Calculer l'ecart en temps reel
        spinnerQtePhysique.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (lotSelectionne != null && newVal != null) {
                int ecart = newVal - lotSelectionne.stockTheorique;
                lblEcartCalcule.setText(String.valueOf(ecart));

                // Afficher/masquer le motif si ecart
                boolean hasEcart = ecart != 0;
                paneMotif.setVisible(hasEcart);
                paneMotif.setManaged(hasEcart);

                // Colorer l'ecart
                if (ecart < 0) {
                    lblEcartCalcule.setStyle("-fx-text-fill: #dc3545;");
                } else if (ecart > 0) {
                    lblEcartCalcule.setStyle("-fx-text-fill: #28a745;");
                } else {
                    lblEcartCalcule.setStyle("-fx-text-fill: #6c757d;");
                }
            }
        });
    }

    private void setupSearch() {
        txtRecherche.textProperty().addListener((obs, oldVal, newVal) -> {
            filteredLots.setPredicate(lot -> {
                if (newVal == null || newVal.isEmpty()) {
                    return true;
                }
                String lowerCaseFilter = newVal.toLowerCase();
                return lot.medicament.toLowerCase().contains(lowerCaseFilter) ||
                        lot.numeroLot.toLowerCase().contains(lowerCaseFilter);
            });
        });
    }

    private void loadData() {
        loadSessionEnCours();
        loadLots();
        loadHistorique();
    }

    private void loadSessionEnCours() {
        Task<Optional<SessionInventaire>> task = new Task<>() {
            @Override
            protected Optional<SessionInventaire> call() throws Exception {
                return inventaireService.getSessionEnCours();
            }
        };

        task.setOnSucceeded(e -> {
            Optional<SessionInventaire> session = task.getValue();
            if (session.isPresent()) {
                sessionEnCours = session.get();
                afficherSessionEnCours();
                loadComptagesSession();
            } else {
                sessionEnCours = null;
                afficherNoSession();
            }
        });

        task.setOnFailed(e -> {
            logger.error("Erreur chargement session", task.getException());
            afficherNoSession();
        });

        new Thread(task).start();
    }

    private void afficherSessionEnCours() {
        paneNoSession.setVisible(false);
        paneNoSession.setManaged(false);
        paneSessionEnCours.setVisible(true);
        paneSessionEnCours.setManaged(true);
        paneComptage.setVisible(false);
        paneComptage.setManaged(false);

        lblSessionStatut.setText("EN COURS");
        lblSessionStatut.setStyle("-fx-background-color: #28a745; -fx-text-fill: white; -fx-padding: 3 8; -fx-background-radius: 3;");

        lblSessionId.setText(String.valueOf(sessionEnCours.getIdSession()));
        lblSessionDebut.setText(sessionEnCours.getDateDebut() != null ?
                sessionEnCours.getDateDebut().format(DATE_TIME_FORMAT) : "-");

        updateComptagesStats();
    }

    private void afficherNoSession() {
        paneNoSession.setVisible(true);
        paneNoSession.setManaged(true);
        paneSessionEnCours.setVisible(false);
        paneSessionEnCours.setManaged(false);
        paneComptage.setVisible(false);
        paneComptage.setManaged(false);

        lblSessionStatut.setText("AUCUNE SESSION");
        lblSessionStatut.setStyle("-fx-background-color: #6c757d; -fx-text-fill: white; -fx-padding: 3 8; -fx-background-radius: 3;");

        comptagesMap.clear();
    }

    private void loadComptagesSession() {
        if (sessionEnCours == null) return;

        Task<List<ComptageInventaire>> task = new Task<>() {
            @Override
            protected List<ComptageInventaire> call() throws Exception {
                return inventaireService.getComptagesBySession(sessionEnCours.getIdSession());
            }
        };

        task.setOnSucceeded(e -> {
            comptagesMap.clear();
            for (ComptageInventaire c : task.getValue()) {
                comptagesMap.put(c.getIdLot(), c);
            }
            updateLotsWithComptages();
            updateComptagesStats();
        });

        new Thread(task).start();
    }

    private void updateLotsWithComptages() {
        for (LotRow lot : lotsData) {
            ComptageInventaire comptage = comptagesMap.get(lot.idLot);
            if (comptage != null) {
                lot.stockPhysique = comptage.getQuantitePhysique();
                lot.ecart = comptage.getEcart();
                lot.statut = "Compte";
            } else {
                lot.stockPhysique = null;
                lot.ecart = null;
                lot.statut = "A compter";
            }
        }
        tableLots.refresh();
    }

    private void updateComptagesStats() {
        if (sessionEnCours == null) return;

        int nbComptages = comptagesMap.size();
        int nbEcarts = (int) comptagesMap.values().stream().filter(c -> c.getEcart() != 0).count();

        lblNbComptages.setText(String.valueOf(nbComptages));
        lblNbEcarts.setText(String.valueOf(nbEcarts));

        if (nbEcarts > 0) {
            lblNbEcarts.setStyle("-fx-text-fill: #dc3545; -fx-font-weight: bold;");
        } else {
            lblNbEcarts.setStyle("");
        }
    }

    private void loadLots() {
        Task<List<Lot>> task = new Task<>() {
            @Override
            protected List<Lot> call() throws Exception {
                return inventaireService.getAllLotsForComptage();
            }
        };

        task.setOnSucceeded(e -> {
            lotsData.clear();
            for (Lot lot : task.getValue()) {
                lotsData.add(new LotRow(lot));
            }
            if (sessionEnCours != null) {
                updateLotsWithComptages();
            }
        });

        task.setOnFailed(e -> logger.error("Erreur chargement lots", task.getException()));

        new Thread(task).start();
    }

    private void loadHistorique() {
        Task<List<SessionInventaire>> task = new Task<>() {
            @Override
            protected List<SessionInventaire> call() throws Exception {
                return inventaireService.getAllSessions();
            }
        };

        task.setOnSucceeded(e -> {
            sessionsData.clear();
            for (SessionInventaire s : task.getValue()) {
                sessionsData.add(new SessionRow(s));
            }
        });

        task.setOnFailed(e -> logger.error("Erreur chargement historique", task.getException()));

        new Thread(task).start();
    }

    private void selectLotForComptage(LotRow lot) {
        lotSelectionne = lot;

        lblMedicament.setText(lot.medicament);
        lblNumeroLot.setText(lot.numeroLot);
        lblPeremption.setText(lot.datePeremption);
        lblQteTheorique.setText(String.valueOf(lot.stockTheorique));

        // Initialiser avec la valeur existante ou theorique
        ComptageInventaire comptageExistant = comptagesMap.get(lot.idLot);
        int valeurInitiale = comptageExistant != null ? comptageExistant.getQuantitePhysique() : lot.stockTheorique;
        spinnerQtePhysique.getValueFactory().setValue(valeurInitiale);

        if (comptageExistant != null && comptageExistant.getMotifEcart() != null) {
            comboMotif.setValue(comptageExistant.getMotifEcart());
        } else {
            comboMotif.setValue(null);
        }

        if (comptageExistant != null && comptageExistant.getCommentaire() != null) {
            txtCommentaire.setText(comptageExistant.getCommentaire());
        } else {
            txtCommentaire.clear();
        }

        paneComptage.setVisible(true);
        paneComptage.setManaged(true);
    }

    @FXML
    private void handleDemarrerSession() {
        String notes = txtNotes.getText();

        Task<SessionInventaire> task = new Task<>() {
            @Override
            protected SessionInventaire call() throws Exception {
                return inventaireService.creerSession(currentUser.getIdUtilisateur(), notes);
            }
        };

        task.setOnSucceeded(e -> {
            sessionEnCours = task.getValue();
            txtNotes.clear();
            afficherSessionEnCours();
            loadHistorique();
            showInfo("Session d'inventaire demarree.");
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            showError(ex instanceof ServiceException ? ex.getMessage() : "Erreur lors du demarrage de la session.");
            logger.error("Erreur demarrage session", ex);
        });

        new Thread(task).start();
    }

    @FXML
    private void handleAnnulerSession() {
        if (sessionEnCours == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmation");
        confirm.setHeaderText("Annuler la session d'inventaire ?");
        confirm.setContentText("Les comptages effectues seront perdus.");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                inventaireService.annulerSession(sessionEnCours.getIdSession());
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            sessionEnCours = null;
            afficherNoSession();
            loadHistorique();
            showInfo("Session annulee.");
        });

        task.setOnFailed(e -> {
            showError("Erreur lors de l'annulation de la session.");
            logger.error("Erreur annulation session", task.getException());
        });

        new Thread(task).start();
    }

    @FXML
    private void handleTerminerSession() {
        if (sessionEnCours == null) return;

        // Verifier que tous les ecarts ont un motif
        boolean motifsManquants = comptagesMap.values().stream()
                .anyMatch(c -> c.getEcart() != 0 && c.getMotifEcart() == null);

        if (motifsManquants) {
            showError("Veuillez renseigner un motif pour tous les ecarts avant de terminer.");
            return;
        }

        int nbEcarts = (int) comptagesMap.values().stream().filter(c -> c.getEcart() != 0).count();

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmation");
        confirm.setHeaderText("Terminer et regulariser l'inventaire ?");
        confirm.setContentText(String.format("%d comptage(s) effectue(s), %d ecart(s) a regulariser.\n" +
                "Les stocks seront mis a jour.", comptagesMap.size(), nbEcarts));

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        final int sessionId = sessionEnCours.getIdSession();

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                // Appliquer les regularisations
                inventaireService.appliquerRegularisations(sessionId, currentUser.getIdUtilisateur());
                // Terminer la session
                inventaireService.terminerSession(sessionId);
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            sessionEnCours = null;
            afficherNoSession();
            loadLots();
            loadHistorique();
            showInfo(String.format("Inventaire termine. %d regularisation(s) appliquee(s).", nbEcarts));
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            showError(ex instanceof ServiceException ? ex.getMessage() : "Erreur lors de la terminaison.");
            logger.error("Erreur terminaison session", ex);
        });

        new Thread(task).start();
    }

    @FXML
    private void handleEnregistrerComptage() {
        if (sessionEnCours == null || lotSelectionne == null) return;

        int qtePhysique = spinnerQtePhysique.getValue();
        int ecart = qtePhysique - lotSelectionne.stockTheorique;

        // Verifier le motif si ecart
        MotifEcart motif = comboMotif.getValue();
        if (ecart != 0 && motif == null) {
            showError("Veuillez selectionner un motif pour l'ecart.");
            return;
        }

        String commentaire = txtCommentaire.getText();

        Task<ComptageInventaire> task = new Task<>() {
            @Override
            protected ComptageInventaire call() throws Exception {
                return inventaireService.enregistrerComptage(
                        sessionEnCours.getIdSession(),
                        lotSelectionne.idLot,
                        qtePhysique,
                        motif,
                        commentaire,
                        currentUser.getIdUtilisateur()
                );
            }
        };

        task.setOnSucceeded(e -> {
            ComptageInventaire comptage = task.getValue();
            comptagesMap.put(comptage.getIdLot(), comptage);
            updateLotsWithComptages();
            updateComptagesStats();

            // Passer au lot suivant
            int currentIndex = tableLots.getSelectionModel().getSelectedIndex();
            if (currentIndex < tableLots.getItems().size() - 1) {
                tableLots.getSelectionModel().select(currentIndex + 1);
            } else {
                paneComptage.setVisible(false);
                paneComptage.setManaged(false);
            }
        });

        task.setOnFailed(e -> {
            showError("Erreur lors de l'enregistrement du comptage.");
            logger.error("Erreur enregistrement comptage", task.getException());
        });

        new Thread(task).start();
    }

    @FXML
    private void handleAnnulerComptage() {
        paneComptage.setVisible(false);
        paneComptage.setManaged(false);
        lotSelectionne = null;
        tableLots.getSelectionModel().clearSelection();
    }

    @FXML
    private void handleRefresh() {
        loadData();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Erreur");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Classe interne pour l'affichage des lots.
     */
    public static class LotRow {
        public final int idLot;
        public final String medicament;
        public final String numeroLot;
        public final String datePeremption;
        public final int stockTheorique;
        public Integer stockPhysique;
        public Integer ecart;
        public String statut;

        public LotRow(Lot lot) {
            this.idLot = lot.getIdLot();
            this.medicament = lot.getMedicament() != null ? lot.getMedicament().getNomCommercial() : "Lot #" + idLot;
            this.numeroLot = lot.getNumeroLot() != null ? lot.getNumeroLot() : "-";
            this.datePeremption = lot.getDatePeremption() != null ?
                    lot.getDatePeremption().format(DATE_FORMAT) : "-";
            this.stockTheorique = lot.getQuantiteStock();
            this.stockPhysique = null;
            this.ecart = null;
            this.statut = "A compter";
        }
    }

    /**
     * Classe interne pour l'affichage des sessions.
     */
    public static class SessionRow {
        public final int id;
        public final String dateDebut;
        public final String dateFin;
        public final String statut;
        public final int nbComptages;
        public final int nbEcarts;
        public final String utilisateur;

        public SessionRow(SessionInventaire session) {
            this.id = session.getIdSession() != null ? session.getIdSession() : 0;
            this.dateDebut = session.getDateDebut() != null ?
                    session.getDateDebut().format(DATE_TIME_FORMAT) : "-";
            this.dateFin = session.getDateFin() != null ?
                    session.getDateFin().format(DATE_TIME_FORMAT) : "-";
            this.statut = session.getStatut() != null ? session.getStatut().getLibelle() : "-";
            this.nbComptages = session.getNombreComptages();
            this.nbEcarts = session.getNombreEcarts();
            this.utilisateur = session.getNomUtilisateur();
        }
    }
}
