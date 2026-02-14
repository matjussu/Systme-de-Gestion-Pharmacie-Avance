package com.sgpa.controller;

import com.sgpa.dao.VenteDAO;
import com.sgpa.dao.impl.VenteDAOImpl;
import com.sgpa.exception.ServiceException;
import com.sgpa.model.LigneVente;
import com.sgpa.model.Vente;
import com.sgpa.service.ExportService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javafx.util.StringConverter;

/**
 * Controleur pour l'ecran d'historique des ventes.
 * Affiche la liste des ventes avec filtres et detail.
 *
 * @author SGPA Team
 * @version 1.0
 */
public class HistoriqueController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(HistoriqueController.class);
    private static final DecimalFormat PRICE_FORMAT = new DecimalFormat("#,##0.00");

    @FXML private DatePicker dateDebut;
    @FXML private DatePicker dateFin;
    @FXML private Label lblTotal;
    @FXML private Label lblCount;

    @FXML private TableView<VenteRow> tableVentes;
    @FXML private TableColumn<VenteRow, String> colId;
    @FXML private TableColumn<VenteRow, String> colDate;
    @FXML private TableColumn<VenteRow, String> colVendeur;
    @FXML private TableColumn<VenteRow, String> colNbArticles;
    @FXML private TableColumn<VenteRow, String> colMontant;
    @FXML private TableColumn<VenteRow, String> colOrdonnance;

    @FXML private VBox detailPane;
    @FXML private Label lblDetailTitle;
    @FXML private TableView<LigneVenteRow> tableDetail;
    @FXML private TableColumn<LigneVenteRow, String> colDetailMedicament;
    @FXML private TableColumn<LigneVenteRow, String> colDetailLot;
    @FXML private TableColumn<LigneVenteRow, String> colDetailQuantite;
    @FXML private TableColumn<LigneVenteRow, String> colDetailPrixUnit;
    @FXML private TableColumn<LigneVenteRow, String> colDetailTotal;

    private final VenteDAO venteDAO;
    private final ExportService exportService;
    private final ObservableList<VenteRow> ventesData = FXCollections.observableArrayList();
    private final ObservableList<LigneVenteRow> detailData = FXCollections.observableArrayList();

    public HistoriqueController() {
        this.venteDAO = new VenteDAOImpl();
        this.exportService = new ExportService();
    }

    @FXML
    public void initialize() {
        setupDatePickers();
        setupVentesTable();
        setupDetailTable();
        setupResponsiveTable(tableVentes);
        setupResponsiveTable(tableDetail);
        loadVentes();
    }

    private void setupDatePickers() {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        StringConverter<LocalDate> converter = new StringConverter<>() {
            @Override
            public String toString(LocalDate date) {
                return date != null ? dateFormatter.format(date) : "";
            }

            @Override
            public LocalDate fromString(String string) {
                if (string != null && !string.isEmpty()) {
                    return LocalDate.parse(string, dateFormatter);
                }
                return null;
            }
        };
        dateDebut.setConverter(converter);
        dateFin.setConverter(converter);
        dateDebut.setValue(LocalDate.now());
        dateFin.setValue(LocalDate.now());
    }

    private void setupVentesTable() {
        colId.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().id)));
        colDate.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().dateHeure));
        colVendeur.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().vendeur));
        colNbArticles.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().nbArticles)));
        colMontant.setCellValueFactory(data -> new SimpleStringProperty(PRICE_FORMAT.format(data.getValue().montant) + " EUR"));
        colOrdonnance.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().ordonnance ? "Oui" : "Non"));

        tableVentes.setItems(ventesData);

        // Double-clic pour voir le detail
        tableVentes.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                VenteRow selected = tableVentes.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    showDetail(selected);
                }
            }
        });
    }

    private void setupDetailTable() {
        colDetailMedicament.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().medicament));
        colDetailLot.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().numeroLot));
        colDetailQuantite.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().quantite)));
        colDetailPrixUnit.setCellValueFactory(data -> new SimpleStringProperty(PRICE_FORMAT.format(data.getValue().prixUnitaire) + " EUR"));
        colDetailTotal.setCellValueFactory(data -> new SimpleStringProperty(PRICE_FORMAT.format(data.getValue().total) + " EUR"));

        tableDetail.setItems(detailData);
    }

    private void loadVentes() {
        LocalDate debut = dateDebut.getValue();
        LocalDate fin = dateFin.getValue();

        if (debut == null) debut = LocalDate.now();
        if (fin == null) fin = LocalDate.now();

        final LocalDate finalDebut = debut;
        final LocalDate finalFin = fin;

        Task<List<Vente>> loadTask = new Task<>() {
            @Override
            protected List<Vente> call() throws Exception {
                return venteDAO.findByDateRange(finalDebut, finalFin);
            }
        };

        loadTask.setOnSucceeded(event -> {
            List<Vente> ventes = loadTask.getValue();
            ventesData.clear();

            BigDecimal total = BigDecimal.ZERO;
            for (Vente v : ventes) {
                ventesData.add(new VenteRow(v));
                total = total.add(v.getMontantTotal() != null ? v.getMontantTotal() : BigDecimal.ZERO);
            }

            lblCount.setText(ventes.size() + " vente(s)");
            lblTotal.setText("Total: " + PRICE_FORMAT.format(total) + " EUR");
        });

        loadTask.setOnFailed(event -> {
            logger.error("Erreur lors du chargement des ventes", loadTask.getException());
        });

        runAsync(loadTask);
    }

    @FXML
    private void handleFilter() {
        loadVentes();
    }

    @FXML
    private void handleToday() {
        dateDebut.setValue(LocalDate.now());
        dateFin.setValue(LocalDate.now());
        loadVentes();
    }

    @FXML
    private void handleThisWeek() {
        LocalDate today = LocalDate.now();
        dateDebut.setValue(today.minusDays(today.getDayOfWeek().getValue() - 1));
        dateFin.setValue(today);
        loadVentes();
    }

    private void showDetail(VenteRow venteRow) {
        lblDetailTitle.setText("Detail de la vente N°" + venteRow.id);
        detailData.clear();

        Task<List<LigneVente>> loadTask = new Task<>() {
            @Override
            protected List<LigneVente> call() throws Exception {
                return venteDAO.findLignesByVenteId(venteRow.id);
            }
        };

        loadTask.setOnSucceeded(event -> {
            List<LigneVente> lignes = loadTask.getValue();
            for (LigneVente ligne : lignes) {
                detailData.add(new LigneVenteRow(ligne));
            }
            detailPane.setVisible(true);
            detailPane.setManaged(true);
        });

        loadTask.setOnFailed(event -> {
            logger.error("Erreur lors du chargement du detail", loadTask.getException());
        });

        runAsync(loadTask);
    }

    @FXML
    private void handleCloseDetail() {
        detailPane.setVisible(false);
        detailPane.setManaged(false);
    }

    @FXML
    private void handleExportCSV() {
        LocalDate debut = dateDebut.getValue();
        LocalDate fin = dateFin.getValue();

        if (debut == null) debut = LocalDate.now();
        if (fin == null) fin = LocalDate.now();

        final LocalDate finalDebut = debut;
        final LocalDate finalFin = fin;

        Task<String> exportTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                return exportService.exportVentes(finalDebut, finalFin);
            }
        };

        exportTask.setOnSucceeded(event -> {
            String filePath = exportTask.getValue();
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Export reussi");
            alert.setHeaderText("Fichier CSV genere");
            alert.setContentText("Le fichier a ete exporte vers:\n" + filePath);
            alert.showAndWait();
        });

        exportTask.setOnFailed(event -> {
            logger.error("Erreur lors de l'export CSV", exportTask.getException());
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Erreur");
            alert.setHeaderText("Echec de l'export");
            alert.setContentText("Une erreur est survenue lors de l'export CSV.");
            alert.showAndWait();
        });

        runAsync(exportTask);
    }

    /**
     * Classe interne pour l'affichage des ventes.
     */
    public static class VenteRow {
        public final int id;
        public final String dateHeure;
        public final String vendeur;
        public final int nbArticles;
        public final BigDecimal montant;
        public final boolean ordonnance;

        public VenteRow(Vente vente) {
            this.id = vente.getIdVente() != null ? vente.getIdVente() : 0;
            this.dateHeure = vente.getDateVente() != null ?
                    vente.getDateVente().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : "-";
            this.vendeur = vente.getUtilisateur() != null ?
                    vente.getUtilisateur().getNomComplet() : "Utilisateur #" + vente.getIdUtilisateur();
            this.nbArticles = vente.getNombreArticles();
            this.montant = vente.getMontantTotal() != null ? vente.getMontantTotal() : BigDecimal.ZERO;
            this.ordonnance = vente.getNumeroOrdonnance() != null && !vente.getNumeroOrdonnance().isEmpty();
        }
    }

    /**
     * Classe interne pour l'affichage des lignes de vente.
     */
    public static class LigneVenteRow {
        public final String medicament;
        public final String numeroLot;
        public final int quantite;
        public final BigDecimal prixUnitaire;
        public final BigDecimal total;

        public LigneVenteRow(LigneVente ligne) {
            // Recuperer le medicament via le lot
            if (ligne.getLot() != null && ligne.getLot().getMedicament() != null) {
                this.medicament = ligne.getLot().getMedicament().getNomCommercial();
            } else {
                this.medicament = "Lot #" + ligne.getIdLot();
            }
            this.numeroLot = ligne.getLot() != null ? ligne.getLot().getNumeroLot() : "-";
            this.quantite = ligne.getQuantite();
            this.prixUnitaire = ligne.getPrixUnitaireApplique() != null ?
                    ligne.getPrixUnitaireApplique() : BigDecimal.ZERO;
            this.total = prixUnitaire.multiply(BigDecimal.valueOf(quantite));
        }
    }
}
