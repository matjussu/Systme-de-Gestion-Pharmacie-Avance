package com.sgpa.controller;

import com.sgpa.dao.LotDAO;
import com.sgpa.dao.MedicamentDAO;
import com.sgpa.dao.VenteDAO;
import com.sgpa.dao.impl.LotDAOImpl;
import com.sgpa.dao.impl.MedicamentDAOImpl;
import com.sgpa.dao.impl.VenteDAOImpl;
import com.sgpa.dto.AlertePeremption;
import com.sgpa.dto.AlerteStock;
import com.sgpa.model.LigneVente;
import com.sgpa.model.Lot;
import com.sgpa.model.Medicament;
import com.sgpa.model.Vente;
import com.sgpa.service.AlerteService;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controleur pour l'ecran des statistiques et graphiques.
 *
 * @author SGPA Team
 * @version 1.0
 */
public class StatistiquesController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(StatistiquesController.class);
    private static final DecimalFormat PRICE_FORMAT = new DecimalFormat("#,##0.00");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM");

    // Filtres
    @FXML private ComboBox<String> comboPeriode;
    @FXML private DatePicker dateDebut;
    @FXML private DatePicker dateFin;
    @FXML private ProgressIndicator progressIndicator;

    // Resume
    @FXML private Label lblChiffreAffaires;
    @FXML private Label lblNombreVentes;
    @FXML private Label lblPanierMoyen;
    @FXML private Label lblArticlesVendus;

    // Graphiques
    @FXML private LineChart<String, Number> chartVentes;
    @FXML private BarChart<String, Number> chartTopMedicaments;
    @FXML private PieChart chartAlertes;
    @FXML private BarChart<String, Number> chartStock;

    private final VenteDAO venteDAO;
    private final MedicamentDAO medicamentDAO;
    private final LotDAO lotDAO;
    private final AlerteService alerteService;

    public StatistiquesController() {
        this.venteDAO = new VenteDAOImpl();
        this.medicamentDAO = new MedicamentDAOImpl();
        this.lotDAO = new LotDAOImpl();
        this.alerteService = new AlerteService();
    }

    @FXML
    public void initialize() {
        setupFilters();
        loadData();
    }

    private void setupFilters() {
        comboPeriode.setItems(FXCollections.observableArrayList(
                "Aujourd'hui",
                "Cette semaine",
                "Ce mois",
                "Les 30 derniers jours",
                "Les 90 derniers jours",
                "Personnalise"
        ));
        comboPeriode.setValue("Les 30 derniers jours");
        comboPeriode.setOnAction(e -> onPeriodeChanged());

        // Dates par defaut
        dateFin.setValue(LocalDate.now());
        dateDebut.setValue(LocalDate.now().minusDays(30));

        // Desactiver les date pickers sauf si "Personnalise"
        updateDatePickersState();
    }

    private void onPeriodeChanged() {
        String periode = comboPeriode.getValue();
        LocalDate now = LocalDate.now();

        switch (periode) {
            case "Aujourd'hui" -> {
                dateDebut.setValue(now);
                dateFin.setValue(now);
            }
            case "Cette semaine" -> {
                dateDebut.setValue(now.minusDays(now.getDayOfWeek().getValue() - 1));
                dateFin.setValue(now);
            }
            case "Ce mois" -> {
                dateDebut.setValue(now.withDayOfMonth(1));
                dateFin.setValue(now);
            }
            case "Les 30 derniers jours" -> {
                dateDebut.setValue(now.minusDays(30));
                dateFin.setValue(now);
            }
            case "Les 90 derniers jours" -> {
                dateDebut.setValue(now.minusDays(90));
                dateFin.setValue(now);
            }
        }

        updateDatePickersState();

        if (!"Personnalise".equals(periode)) {
            loadData();
        }
    }

    private void updateDatePickersState() {
        boolean custom = "Personnalise".equals(comboPeriode.getValue());
        dateDebut.setDisable(!custom);
        dateFin.setDisable(!custom);
    }

    @FXML
    private void handleRefresh() {
        loadData();
    }

    private void loadData() {
        progressIndicator.setVisible(true);

        LocalDate debut = dateDebut.getValue();
        LocalDate fin = dateFin.getValue();

        if (debut == null) debut = LocalDate.now().minusDays(30);
        if (fin == null) fin = LocalDate.now();

        final LocalDate finalDebut = debut;
        final LocalDate finalFin = fin;

        Task<StatistiquesData> loadTask = new Task<>() {
            @Override
            protected StatistiquesData call() throws Exception {
                StatistiquesData data = new StatistiquesData();

                // Charger les ventes
                List<Vente> ventes = venteDAO.findByDateRange(finalDebut, finalFin);
                data.ventes = ventes;

                // Calculer les metriques
                data.chiffreAffaires = BigDecimal.ZERO;
                data.articlesVendus = 0;
                Map<String, Integer> ventesParMedicament = new HashMap<>();

                for (Vente v : ventes) {
                    if (v.getMontantTotal() != null) {
                        data.chiffreAffaires = data.chiffreAffaires.add(v.getMontantTotal());
                    }

                    // Charger les lignes de vente pour les statistiques par medicament
                    List<LigneVente> lignes = venteDAO.findLignesByVenteId(v.getIdVente());
                    for (LigneVente ligne : lignes) {
                        data.articlesVendus += ligne.getQuantite();

                        // Recuperer le nom du medicament via le lot
                        if (ligne.getLot() != null && ligne.getLot().getMedicament() != null) {
                            String nomMed = ligne.getLot().getMedicament().getNomCommercial();
                            ventesParMedicament.merge(nomMed, ligne.getQuantite(), Integer::sum);
                        }
                    }
                }

                // Top 10 medicaments
                data.topMedicaments = ventesParMedicament.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .limit(10)
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue,
                                (e1, e2) -> e1,
                                LinkedHashMap::new
                        ));

                // Ventes par jour
                data.ventesParJour = new LinkedHashMap<>();
                LocalDate current = finalDebut;
                while (!current.isAfter(finalFin)) {
                    final LocalDate day = current;
                    BigDecimal totalJour = ventes.stream()
                            .filter(v -> v.getDateVente() != null &&
                                    v.getDateVente().toLocalDate().equals(day))
                            .map(v -> v.getMontantTotal() != null ? v.getMontantTotal() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    data.ventesParJour.put(day.format(DATE_FORMAT), totalJour);
                    current = current.plusDays(1);
                }

                // Alertes
                data.alertesStock = alerteService.getAlertesStockBas();
                data.alertesPeremption = alerteService.getAlertesPeremption();
                data.lotsPerimes = alerteService.getLotsPerimes();

                // Stock par medicament (top 15)
                List<Medicament> medicaments = medicamentDAO.findAll();
                data.stockParMedicament = new LinkedHashMap<>();
                data.seuilParMedicament = new LinkedHashMap<>();

                for (Medicament med : medicaments) {
                    int stock = lotDAO.getTotalStockByMedicament(med.getIdMedicament());
                    data.stockParMedicament.put(med.getNomCommercial(), stock);
                    data.seuilParMedicament.put(med.getNomCommercial(), med.getSeuilMin());
                }

                // Garder seulement les 15 premiers par stock
                data.stockParMedicament = data.stockParMedicament.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .limit(15)
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue,
                                (e1, e2) -> e1,
                                LinkedHashMap::new
                        ));

                return data;
            }
        };

        loadTask.setOnSucceeded(event -> {
            progressIndicator.setVisible(false);
            StatistiquesData data = loadTask.getValue();
            updateUI(data);
        });

        loadTask.setOnFailed(event -> {
            progressIndicator.setVisible(false);
            logger.error("Erreur lors du chargement des statistiques", loadTask.getException());
        });

        runAsync(loadTask);
    }

    private void updateUI(StatistiquesData data) {
        // Resume
        lblChiffreAffaires.setText(PRICE_FORMAT.format(data.chiffreAffaires) + " EUR");
        lblNombreVentes.setText(String.valueOf(data.ventes.size()));
        lblArticlesVendus.setText(String.valueOf(data.articlesVendus));

        if (!data.ventes.isEmpty()) {
            BigDecimal panierMoyen = data.chiffreAffaires.divide(
                    BigDecimal.valueOf(data.ventes.size()), 2, RoundingMode.HALF_UP);
            lblPanierMoyen.setText(PRICE_FORMAT.format(panierMoyen) + " EUR");
        } else {
            lblPanierMoyen.setText("0.00 EUR");
        }

        // Graphique ventes par jour
        updateChartVentes(data.ventesParJour);

        // Graphique top medicaments
        updateChartTopMedicaments(data.topMedicaments);

        // Graphique alertes
        updateChartAlertes(data);

        // Graphique stock
        updateChartStock(data.stockParMedicament, data.seuilParMedicament);
    }

    private void updateChartVentes(Map<String, BigDecimal> ventesParJour) {
        chartVentes.getData().clear();

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Ventes");

        for (Map.Entry<String, BigDecimal> entry : ventesParJour.entrySet()) {
            series.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
        }

        chartVentes.getData().add(series);
    }

    private void updateChartTopMedicaments(Map<String, Integer> topMedicaments) {
        chartTopMedicaments.getData().clear();

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Quantite vendue");

        for (Map.Entry<String, Integer> entry : topMedicaments.entrySet()) {
            // Tronquer le nom si trop long
            String nom = entry.getKey();
            if (nom.length() > 15) {
                nom = nom.substring(0, 12) + "...";
            }
            series.getData().add(new XYChart.Data<>(nom, entry.getValue()));
        }

        chartTopMedicaments.getData().add(series);
    }

    private void updateChartAlertes(StatistiquesData data) {
        chartAlertes.getData().clear();

        int stockBas = data.alertesStock.size();
        int peremptionProche = data.alertesPeremption.size();
        int perimes = data.lotsPerimes.size();

        if (stockBas > 0 || peremptionProche > 0 || perimes > 0) {
            if (stockBas > 0) {
                chartAlertes.getData().add(new PieChart.Data("Stock bas (" + stockBas + ")", stockBas));
            }
            if (peremptionProche > 0) {
                chartAlertes.getData().add(new PieChart.Data("Peremption proche (" + peremptionProche + ")", peremptionProche));
            }
            if (perimes > 0) {
                chartAlertes.getData().add(new PieChart.Data("Perimes (" + perimes + ")", perimes));
            }
        } else {
            // Aucune alerte
            chartAlertes.getData().add(new PieChart.Data("Aucune alerte", 1));
        }
    }

    private void updateChartStock(Map<String, Integer> stock, Map<String, Integer> seuils) {
        chartStock.getData().clear();

        XYChart.Series<String, Number> seriesStock = new XYChart.Series<>();
        seriesStock.setName("Stock actuel");

        XYChart.Series<String, Number> seriesSeuil = new XYChart.Series<>();
        seriesSeuil.setName("Seuil minimum");

        for (Map.Entry<String, Integer> entry : stock.entrySet()) {
            String nom = entry.getKey();
            if (nom.length() > 12) {
                nom = nom.substring(0, 9) + "...";
            }
            seriesStock.getData().add(new XYChart.Data<>(nom, entry.getValue()));

            Integer seuil = seuils.get(entry.getKey());
            if (seuil != null) {
                seriesSeuil.getData().add(new XYChart.Data<>(nom, seuil));
            }
        }

        chartStock.getData().addAll(seriesStock, seriesSeuil);
    }

    /**
     * Classe interne pour stocker les donnees de statistiques.
     */
    private static class StatistiquesData {
        List<Vente> ventes = new ArrayList<>();
        BigDecimal chiffreAffaires = BigDecimal.ZERO;
        int articlesVendus = 0;
        Map<String, BigDecimal> ventesParJour = new LinkedHashMap<>();
        Map<String, Integer> topMedicaments = new LinkedHashMap<>();
        Map<String, Integer> stockParMedicament = new LinkedHashMap<>();
        Map<String, Integer> seuilParMedicament = new LinkedHashMap<>();
        List<AlerteStock> alertesStock = new ArrayList<>();
        List<AlertePeremption> alertesPeremption = new ArrayList<>();
        List<Lot> lotsPerimes = new ArrayList<>();
    }
}
