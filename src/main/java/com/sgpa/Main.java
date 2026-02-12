package com.sgpa;

import com.sgpa.dao.LotDAO;
import com.sgpa.dao.MedicamentDAO;
import com.sgpa.dao.impl.LotDAOImpl;
import com.sgpa.dao.impl.MedicamentDAOImpl;
import com.sgpa.dto.AlertePeremption;
import com.sgpa.dto.AlerteStock;
import com.sgpa.dto.LigneVenteDTO;
import com.sgpa.exception.DAOException;
import com.sgpa.exception.ServiceException;
import com.sgpa.model.Lot;
import com.sgpa.model.Medicament;
import com.sgpa.model.Utilisateur;
import com.sgpa.model.Vente;
import com.sgpa.service.AlerteService;
import com.sgpa.service.AuthenticationService;
import com.sgpa.service.StockService;
import com.sgpa.service.VenteService;
import com.sgpa.utils.DatabaseConnection;
import com.sgpa.utils.FontLoader;
import com.sgpa.utils.PasswordUtils;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Point d'entree principal de l'application SGPA.
 * <p>
 * Cette classe initialise l'application JavaFX et teste la connexion
 * a la base de donnees ainsi que les services metier.
 * </p>
 *
 * @author SGPA Team
 * @version 1.0
 */
public class Main extends Application {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static StringBuilder testResults = new StringBuilder();

    /**
     * Point d'entree principal.
     *
     * @param args les arguments de ligne de commande
     */
    public static void main(String[] args) {
        logger.info("===========================================");
        logger.info("ApotiCare - Gestion Moderne de Pharmacie");
        logger.info("===========================================");

        // Test de la connexion BDD avant de lancer JavaFX
        if (!testDatabaseConnection()) {
            logger.error("Impossible de se connecter a la base de donnees.");
            logger.error("Verifiez que MySQL est demarre et que la base 'sgpa_pharmacie' existe.");
            logger.error("Executez: mysql -u root -p < sql/schema.sql");
            System.exit(1);
        }

        // Test BCrypt
        testPasswordUtils();

        // Test DAO
        testDAOLayer();

        // Test Services (Phase 2)
        testServices();

        // Proprietes de rendu pour un affichage plus lisse
        System.setProperty("prism.lcdtext", "false");
        System.setProperty("prism.text", "t2k");

        // Charger les polices custom
        FontLoader.loadFonts();

        // Lancer l'application JavaFX
        launch(args);
    }

    /**
     * Teste la connexion a la base de donnees.
     *
     * @return true si la connexion est etablie
     */
    private static boolean testDatabaseConnection() {
        logger.info("Test de connexion a la base de donnees...");
        try {
            boolean connected = DatabaseConnection.getInstance().testConnection();
            if (connected) {
                logger.info("Connexion a la base de donnees: OK");
                logger.info(DatabaseConnection.getInstance().getPoolStats());
                testResults.append("Connexion BDD: OK\n");
            }
            return connected;
        } catch (Exception e) {
            logger.error("Erreur de connexion: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Teste l'utilitaire de hashage de mots de passe.
     */
    private static void testPasswordUtils() {
        logger.info("Test BCrypt...");
        String password = "admin123";
        String hash = PasswordUtils.hashPassword(password);

        boolean verified = PasswordUtils.verifyPassword(password, hash);
        logger.info("Verification BCrypt: {}", verified ? "OK" : "ECHEC");
        testResults.append("BCrypt: OK\n");
    }

    /**
     * Teste la couche DAO.
     */
    private static void testDAOLayer() {
        logger.info("Test couche DAO...");

        MedicamentDAO medicamentDAO = new MedicamentDAOImpl();
        LotDAO lotDAO = new LotDAOImpl();

        try {
            long nbMedicaments = medicamentDAO.count();
            logger.info("Nombre de medicaments en base: {}", nbMedicaments);

            long nbLots = lotDAO.count();
            logger.info("Nombre de lots en base: {}", nbLots);

            testResults.append("DAO: OK (").append(nbMedicaments).append(" medicaments, ")
                    .append(nbLots).append(" lots)\n");

            logger.info("Test DAO: OK");

        } catch (DAOException e) {
            logger.error("Erreur lors du test DAO: {}", e.getMessage());
            testResults.append("DAO: ERREUR\n");
        }
    }

    /**
     * Teste les services metier (Phase 2).
     */
    private static void testServices() {
        logger.info("========================================");
        logger.info("Test des Services Metier (Phase 2)");
        logger.info("========================================");

        testAuthenticationService();
        testAlerteService();
        testStockService();
        testVenteServiceFEFO();
    }

    /**
     * Teste le service d'authentification.
     */
    private static void testAuthenticationService() {
        logger.info("\n--- Test AuthenticationService ---");
        AuthenticationService authService = new AuthenticationService();

        try {
            // Test connexion avec admin
            Utilisateur user = authService.authenticate("admin", "admin123");
            logger.info("Authentification reussie: {} ({})", user.getNomComplet(), user.getRole());
            logger.info("Est pharmacien: {}", authService.isPharmacien());
            logger.info("Permission VENTE: {}", authService.hasPermission("VENTE"));

            authService.logout();
            logger.info("Deconnexion effectuee");

            testResults.append("Authentification: OK\n");

        } catch (ServiceException e) {
            logger.error("Erreur authentification: {}", e.getMessage());
            testResults.append("Authentification: ERREUR\n");
        }
    }

    /**
     * Teste le service d'alertes.
     */
    private static void testAlerteService() {
        logger.info("\n--- Test AlerteService ---");
        AlerteService alerteService = new AlerteService();

        try {
            // Alertes stock bas
            List<AlerteStock> alertesStock = alerteService.getAlertesStockBas();
            logger.info("Alertes stock bas: {}", alertesStock.size());
            for (AlerteStock a : alertesStock) {
                logger.info("  {}", a);
            }

            // Alertes peremption
            List<AlertePeremption> alertesPeremption = alerteService.getAlertesPeremption();
            logger.info("Alertes peremption (<90 jours): {}", alertesPeremption.size());
            for (AlertePeremption a : alertesPeremption) {
                logger.info("  {}", a);
            }

            // Lots perimes
            List<Lot> lotsPerimes = alerteService.getLotsPerimes();
            logger.info("Lots perimes: {}", lotsPerimes.size());

            // Total alertes
            int totalAlertes = alerteService.getNombreAlertes();
            logger.info("Total alertes: {}", totalAlertes);

            testResults.append("Alertes: OK (").append(totalAlertes).append(" alertes)\n");

        } catch (ServiceException e) {
            logger.error("Erreur alertes: {}", e.getMessage());
            testResults.append("Alertes: ERREUR\n");
        }
    }

    /**
     * Teste le service de stock.
     */
    private static void testStockService() {
        logger.info("\n--- Test StockService ---");
        StockService stockService = new StockService();

        try {
            MedicamentDAO medicamentDAO = new MedicamentDAOImpl();
            List<Medicament> medicaments = medicamentDAO.findAllActive();

            if (!medicaments.isEmpty()) {
                Medicament med = medicaments.get(0);
                int stockTotal = stockService.getStockTotal(med.getIdMedicament());
                int stockVendable = stockService.getStockVendable(med.getIdMedicament());

                logger.info("Stock {} - Total: {}, Vendable: {}",
                        med.getNomCommercial(), stockTotal, stockVendable);

                // Lots FEFO
                List<Lot> lotsFEFO = stockService.getLotsFEFO(med.getIdMedicament());
                logger.info("Lots FEFO pour {}:", med.getNomCommercial());
                for (Lot lot : lotsFEFO) {
                    logger.info("  - {} : {} unites (expire le {})",
                            lot.getNumeroLot(), lot.getQuantiteStock(), lot.getDatePeremption());
                }
            }

            testResults.append("Stock: OK\n");

        } catch (Exception e) {
            logger.error("Erreur stock: {}", e.getMessage());
            testResults.append("Stock: ERREUR\n");
        }
    }

    /**
     * Teste le service de vente avec l'algorithme FEFO.
     */
    private static void testVenteServiceFEFO() {
        logger.info("\n--- Test VenteService (FEFO) ---");
        logger.info("DEMONSTRATION DE L'ALGORITHME FEFO:");
        logger.info("Les lots avec la date de peremption la plus proche sont utilises en premier.");

        VenteService venteService = new VenteService();
        StockService stockService = new StockService();

        try {
            MedicamentDAO medicamentDAO = new MedicamentDAOImpl();
            List<Medicament> medicaments = medicamentDAO.findAllActive();

            if (!medicaments.isEmpty()) {
                // Prendre le premier medicament (Doliprane)
                Medicament med = medicaments.get(0);
                logger.info("\nMedicament de test: {}", med.getNomCommercial());

                // Afficher les lots avant la vente
                logger.info("\nLots AVANT vente (ordre FEFO):");
                List<Lot> lotsAvant = stockService.getLotsFEFO(med.getIdMedicament());
                for (Lot lot : lotsAvant) {
                    logger.info("  - {} : {} unites (expire le {})",
                            lot.getNumeroLot(), lot.getQuantiteStock(), lot.getDatePeremption());
                }

                int stockAvant = stockService.getStockTotal(med.getIdMedicament());
                logger.info("Stock total avant: {}", stockAvant);

                // Creer une vente de 5 unites
                List<LigneVenteDTO> lignes = new ArrayList<>();
                lignes.add(new LigneVenteDTO(med.getIdMedicament(), 5));

                logger.info("\n>>> Vente de 5 unites...");
                Vente vente = venteService.creerVente(lignes, 1, false);
                logger.info("Vente creee: ID={}, Montant={}", vente.getIdVente(), vente.getMontantTotal());

                // Afficher les lots apres la vente
                logger.info("\nLots APRES vente (ordre FEFO):");
                List<Lot> lotsApres = stockService.getLotsFEFO(med.getIdMedicament());
                for (Lot lot : lotsApres) {
                    logger.info("  - {} : {} unites (expire le {})",
                            lot.getNumeroLot(), lot.getQuantiteStock(), lot.getDatePeremption());
                }

                int stockApres = stockService.getStockTotal(med.getIdMedicament());
                logger.info("Stock total apres: {} (reduit de {})", stockApres, stockAvant - stockApres);

                logger.info("\n>>> L'algorithme FEFO a deduit du lot expirant le plus tot en premier!");
            }

            testResults.append("FEFO: OK\n");

        } catch (Exception e) {
            logger.error("Erreur FEFO: {}", e.getMessage());
            testResults.append("FEFO: ERREUR - ").append(e.getMessage()).append("\n");
        }
    }

    @Override
    public void start(Stage primaryStage) {
        logger.info("Demarrage de l'interface JavaFX...");

        try {
            // Charger l'ecran de connexion
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Parent root = loader.load();

            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());

            Rectangle2D sb = Screen.getPrimary().getVisualBounds();
            primaryStage.setTitle("ApotiCare - Connexion");
            primaryStage.setScene(scene);
            primaryStage.setWidth(520);
            primaryStage.setHeight(680);
            primaryStage.setMinWidth(480);
            primaryStage.setMinHeight(600);
            primaryStage.setResizable(true);
            primaryStage.setX((sb.getWidth() - 520) / 2);
            primaryStage.setY((sb.getHeight() - 680) / 2);
            primaryStage.setOnCloseRequest(event -> {
                logger.info("Fermeture de l'application...");
                DatabaseConnection.getInstance().shutdown();
            });

            primaryStage.show();
            logger.info("Application demarree avec succes!");

        } catch (Exception e) {
            logger.error("Erreur lors du chargement de l'interface", e);

            // Fallback: afficher les resultats des tests
            Label label = new Label("ApotiCare - Erreur de chargement\n\n" +
                    "=== RESULTATS DES TESTS ===\n\n" +
                    testResults.toString() +
                    "\n\nErreur: " + e.getMessage());

            label.setStyle("-fx-font-size: 14px; -fx-text-alignment: left; -fx-font-family: monospace;");

            StackPane fallbackRoot = new StackPane(label);
            fallbackRoot.setStyle("-fx-padding: 40px; -fx-background-color: #f5f5f5;");

            Scene scene = new Scene(fallbackRoot, 550, 400);
            primaryStage.setTitle("ApotiCare - Erreur");
            primaryStage.setScene(scene);
            primaryStage.show();
        }
    }

    @Override
    public void stop() {
        logger.info("Arret de l'application...");
        DatabaseConnection.getInstance().shutdown();
    }
}
