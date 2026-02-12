package com.sgpa.controller;

import com.sgpa.exception.ServiceException;
import com.sgpa.model.LigneVente;
import com.sgpa.model.Lot;
import com.sgpa.model.Retour;
import com.sgpa.model.Vente;
import com.sgpa.service.RetourService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controleur pour l'ecran de gestion des retours.
 * Permet d'enregistrer les retours et de consulter l'historique.
 *
 * @author SGPA Team
 * @version 1.0
 */
public class RetourController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(RetourController.class);
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // Section nouveau retour
    @FXML private TextField txtNumeroVente;
    @FXML private Label lblInfoVente;
    @FXML private VBox paneVenteDetails;
    @FXML private TableView<LigneVenteRow> tableLignesVente;
    @FXML private TableColumn<LigneVenteRow, String> colMedicament;
    @FXML private TableColumn<LigneVenteRow, String> colLot;
    @FXML private TableColumn<LigneVenteRow, String> colQuantiteVendue;
    @FXML private TableColumn<LigneVenteRow, String> colQuantiteRetournee;
    @FXML private TableColumn<LigneVenteRow, String> colQuantiteRestante;

    // Formulaire de retour
    @FXML private VBox paneFormulaire;
    @FXML private Label lblArticleSelectionne;
    @FXML private Spinner<Integer> spinnerQuantite;
    @FXML private ComboBox<String> comboMotif;
    @FXML private TextField txtCommentaire;
    @FXML private CheckBox chkReintegrer;
    @FXML private Label lblReintegrerInfo;

    // Historique
    @FXML private DatePicker dateDebut;
    @FXML private DatePicker dateFin;
    @FXML private ComboBox<String> comboFiltreReintegre;
    @FXML private Label lblCount;
    @FXML private TableView<RetourRow> tableRetours;
    @FXML private TableColumn<RetourRow, String> colRetourId;
    @FXML private TableColumn<RetourRow, String> colRetourDate;
    @FXML private TableColumn<RetourRow, String> colRetourVente;
    @FXML private TableColumn<RetourRow, String> colRetourMedicament;
    @FXML private TableColumn<RetourRow, String> colRetourLot;
    @FXML private TableColumn<RetourRow, String> colRetourQuantite;
    @FXML private TableColumn<RetourRow, String> colRetourMotif;
    @FXML private TableColumn<RetourRow, String> colRetourReintegre;
    @FXML private TableColumn<RetourRow, String> colRetourUtilisateur;

    private final RetourService retourService;
    private final ObservableList<LigneVenteRow> lignesVenteData = FXCollections.observableArrayList();
    private final ObservableList<RetourRow> retoursData = FXCollections.observableArrayList();

    private Vente venteSelectionnee;
    private LigneVenteRow ligneSelectionnee;
    private Map<Integer, Integer> quantitesDejaRetournees = new HashMap<>();

    public RetourController() {
        this.retourService = new RetourService();
    }

    @FXML
    public void initialize() {
        setupMotifs();
        setupFiltres();
        setupLignesVenteTable();
        setupRetoursTable();
        setupSpinner();
        setupResponsiveTable(tableLignesVente);
        setupResponsiveTable(tableRetours);
        loadRetours();
    }

    private void setupMotifs() {
        comboMotif.setItems(FXCollections.observableArrayList(
                "Produit defectueux",
                "Erreur de prescription",
                "Allergie/Effet indesirable",
                "Changement de traitement",
                "Date de peremption proche",
                "Emballage endommage",
                "Quantite excessive",
                "Autre"
        ));
    }

    private void setupFiltres() {
        dateDebut.setValue(LocalDate.now().minusDays(30));
        dateFin.setValue(LocalDate.now());

        comboFiltreReintegre.setItems(FXCollections.observableArrayList(
                "Tous les retours",
                "Reintegres au stock",
                "Non reintegres"
        ));
        comboFiltreReintegre.setValue("Tous les retours");
        comboFiltreReintegre.setOnAction(e -> loadRetours());
    }

    private void setupLignesVenteTable() {
        colMedicament.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().medicament));
        colLot.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().numeroLot));
        colQuantiteVendue.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().quantiteVendue)));
        colQuantiteRetournee.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().quantiteRetournee)));
        colQuantiteRestante.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().quantiteRestante)));

        tableLignesVente.setItems(lignesVenteData);

        // Selection d'une ligne pour retour
        tableLignesVente.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.quantiteRestante > 0) {
                selectLigneForReturn(newVal);
            }
        });
    }

    private void setupRetoursTable() {
        colRetourId.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().id)));
        colRetourDate.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().dateHeure));
        colRetourVente.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().idVente)));
        colRetourMedicament.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().medicament));
        colRetourLot.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().numeroLot));
        colRetourQuantite.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().quantite)));
        colRetourMotif.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().motif));
        colRetourReintegre.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().reintegre ? "Oui" : "Non"));
        colRetourUtilisateur.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().utilisateur));

        tableRetours.setItems(retoursData);
    }

    private void setupSpinner() {
        SpinnerValueFactory<Integer> valueFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 999, 1);
        spinnerQuantite.setValueFactory(valueFactory);
        spinnerQuantite.setEditable(true);
    }

    @FXML
    private void handleRechercherVente() {
        String numeroVenteStr = txtNumeroVente.getText().trim();
        if (numeroVenteStr.isEmpty()) {
            showError("Veuillez entrer un numero de vente.");
            return;
        }

        int idVente;
        try {
            idVente = Integer.parseInt(numeroVenteStr);
        } catch (NumberFormatException e) {
            showError("Le numero de vente doit etre un nombre.");
            return;
        }

        Task<Vente> searchTask = new Task<>() {
            @Override
            protected Vente call() throws Exception {
                return retourService.rechercherVente(idVente);
            }
        };

        searchTask.setOnSucceeded(event -> {
            venteSelectionnee = searchTask.getValue();
            afficherDetailsVente();
        });

        searchTask.setOnFailed(event -> {
            Throwable ex = searchTask.getException();
            if (ex instanceof ServiceException) {
                showError(ex.getMessage());
            } else {
                showError("Erreur lors de la recherche de la vente.");
                logger.error("Erreur recherche vente", ex);
            }
            resetFormulaire();
        });

        new Thread(searchTask).start();
    }

    private void afficherDetailsVente() {
        if (venteSelectionnee == null) return;

        // Charger les quantites deja retournees pour cette vente
        loadQuantitesRetournees();

        lblInfoVente.setText(String.format("Vente du %s - Montant: %.2f EUR",
                venteSelectionnee.getDateVente() != null ?
                        venteSelectionnee.getDateVente().format(DATE_TIME_FORMAT) : "-",
                venteSelectionnee.getMontantTotal()));

        lignesVenteData.clear();
        for (LigneVente ligne : venteSelectionnee.getLignesVente()) {
            int dejaRetourne = quantitesDejaRetournees.getOrDefault(ligne.getIdLot(), 0);
            lignesVenteData.add(new LigneVenteRow(ligne, dejaRetourne));
        }

        paneVenteDetails.setVisible(true);
        paneVenteDetails.setManaged(true);
        paneFormulaire.setVisible(false);
        paneFormulaire.setManaged(false);
    }

    private void loadQuantitesRetournees() {
        quantitesDejaRetournees.clear();
        try {
            List<Retour> retours = retourService.getRetoursByVente(venteSelectionnee.getIdVente());
            for (Retour r : retours) {
                quantitesDejaRetournees.merge(r.getIdLot(), r.getQuantite(), Integer::sum);
            }
        } catch (ServiceException e) {
            logger.error("Erreur chargement retours existants", e);
        }
    }

    private void selectLigneForReturn(LigneVenteRow ligne) {
        ligneSelectionnee = ligne;
        lblArticleSelectionne.setText(ligne.medicament + " (Lot: " + ligne.numeroLot + ")");

        // Configurer le spinner avec la quantite max
        SpinnerValueFactory<Integer> valueFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, ligne.quantiteRestante, 1);
        spinnerQuantite.setValueFactory(valueFactory);

        // Verifier si le lot est perime
        if (ligne.lot != null && ligne.lot.isPerime()) {
            chkReintegrer.setSelected(false);
            chkReintegrer.setDisable(true);
            lblReintegrerInfo.setText("(Lot perime - reintegration impossible)");
        } else {
            chkReintegrer.setSelected(true);
            chkReintegrer.setDisable(false);
            lblReintegrerInfo.setText("");
        }

        paneFormulaire.setVisible(true);
        paneFormulaire.setManaged(true);
    }

    @FXML
    private void handleEnregistrerRetour() {
        if (ligneSelectionnee == null || venteSelectionnee == null) {
            showError("Veuillez selectionner un article a retourner.");
            return;
        }

        String motif = comboMotif.getValue();
        if (motif == null || motif.isEmpty()) {
            showError("Veuillez selectionner un motif de retour.");
            return;
        }

        int quantite = spinnerQuantite.getValue();
        if (quantite > ligneSelectionnee.quantiteRestante) {
            showError("La quantite ne peut pas depasser " + ligneSelectionnee.quantiteRestante);
            return;
        }

        boolean reintegrer = chkReintegrer.isSelected();
        String commentaire = txtCommentaire.getText();

        Task<Retour> saveTask = new Task<>() {
            @Override
            protected Retour call() throws Exception {
                return retourService.enregistrerRetour(
                        venteSelectionnee.getIdVente(),
                        ligneSelectionnee.idLot,
                        quantite,
                        motif,
                        reintegrer,
                        commentaire,
                        currentUser.getIdUtilisateur()
                );
            }
        };

        saveTask.setOnSucceeded(event -> {
            Retour retour = saveTask.getValue();
            showSuccess(String.format("Retour #%d enregistre avec succes.%s",
                    retour.getIdRetour(),
                    retour.isReintegre() ? " Stock reintegre." : ""));

            // Rafraichir les donnees
            resetFormulaire();
            loadRetours();
            // Recharger les details de la vente
            handleRechercherVente();
        });

        saveTask.setOnFailed(event -> {
            Throwable ex = saveTask.getException();
            if (ex instanceof ServiceException) {
                showError(ex.getMessage());
            } else {
                showError("Erreur lors de l'enregistrement du retour.");
                logger.error("Erreur enregistrement retour", ex);
            }
        });

        new Thread(saveTask).start();
    }

    @FXML
    private void handleAnnuler() {
        resetFormulaire();
    }

    private void resetFormulaire() {
        paneFormulaire.setVisible(false);
        paneFormulaire.setManaged(false);
        ligneSelectionnee = null;
        comboMotif.setValue(null);
        txtCommentaire.clear();
        chkReintegrer.setSelected(true);
        chkReintegrer.setDisable(false);
        lblReintegrerInfo.setText("");
        tableLignesVente.getSelectionModel().clearSelection();
    }

    @FXML
    private void handleFiltrer() {
        loadRetours();
    }

    @FXML
    private void handleReinitialiser() {
        dateDebut.setValue(LocalDate.now().minusDays(30));
        dateFin.setValue(LocalDate.now());
        comboFiltreReintegre.setValue("Tous les retours");
        venteSelectionnee = null;
        paneVenteDetails.setVisible(false);
        paneVenteDetails.setManaged(false);
        txtNumeroVente.clear();
        lblInfoVente.setText("");
        lignesVenteData.clear();
        resetFormulaire();
        loadRetours();
    }

    private void loadRetours() {
        LocalDate debut = dateDebut.getValue();
        LocalDate fin = dateFin.getValue();

        if (debut == null) debut = LocalDate.now().minusDays(30);
        if (fin == null) fin = LocalDate.now();

        final LocalDate finalDebut = debut;
        final LocalDate finalFin = fin;
        final String filtre = comboFiltreReintegre.getValue();

        Task<List<Retour>> loadTask = new Task<>() {
            @Override
            protected List<Retour> call() throws Exception {
                List<Retour> retours = retourService.getRetoursByPeriode(finalDebut, finalFin);

                // Filtrer selon le statut de reintegration
                if ("Reintegres au stock".equals(filtre)) {
                    retours = retours.stream().filter(Retour::isReintegre).toList();
                } else if ("Non reintegres".equals(filtre)) {
                    retours = retours.stream().filter(r -> !r.isReintegre()).toList();
                }

                return retours;
            }
        };

        loadTask.setOnSucceeded(event -> {
            List<Retour> retours = loadTask.getValue();
            retoursData.clear();
            for (Retour r : retours) {
                retoursData.add(new RetourRow(r));
            }
            lblCount.setText(retours.size() + " retour(s)");
        });

        loadTask.setOnFailed(event -> {
            logger.error("Erreur lors du chargement des retours", loadTask.getException());
        });

        new Thread(loadTask).start();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Erreur");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Succes");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Classe interne pour l'affichage des lignes de vente dans le formulaire de retour.
     */
    public static class LigneVenteRow {
        public final int idLot;
        public final String medicament;
        public final String numeroLot;
        public final int quantiteVendue;
        public final int quantiteRetournee;
        public final int quantiteRestante;
        public final Lot lot;

        public LigneVenteRow(LigneVente ligne, int dejaRetourne) {
            this.idLot = ligne.getIdLot();
            this.lot = ligne.getLot();

            if (ligne.getLot() != null && ligne.getLot().getMedicament() != null) {
                this.medicament = ligne.getLot().getMedicament().getNomCommercial();
            } else {
                this.medicament = "Lot #" + ligne.getIdLot();
            }
            this.numeroLot = ligne.getLot() != null ? ligne.getLot().getNumeroLot() : "-";
            this.quantiteVendue = ligne.getQuantite();
            this.quantiteRetournee = dejaRetourne;
            this.quantiteRestante = Math.max(0, quantiteVendue - dejaRetourne);
        }
    }

    /**
     * Classe interne pour l'affichage des retours dans l'historique.
     */
    public static class RetourRow {
        public final int id;
        public final String dateHeure;
        public final int idVente;
        public final String medicament;
        public final String numeroLot;
        public final int quantite;
        public final String motif;
        public final boolean reintegre;
        public final String utilisateur;

        public RetourRow(Retour retour) {
            this.id = retour.getIdRetour() != null ? retour.getIdRetour() : 0;
            this.dateHeure = retour.getDateRetour() != null ?
                    retour.getDateRetour().format(DATE_TIME_FORMAT) : "-";
            this.idVente = retour.getIdVente() != null ? retour.getIdVente() : 0;
            this.medicament = retour.getNomMedicament();
            this.numeroLot = retour.getNumeroLot();
            this.quantite = retour.getQuantite();
            this.motif = retour.getMotif();
            this.reintegre = retour.isReintegre();
            this.utilisateur = retour.getNomUtilisateur();
        }
    }
}
