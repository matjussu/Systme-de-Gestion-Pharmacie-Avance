package com.sgpa.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Service de gestion de la configuration de l'application.
 * <p>
 * Permet de lire et modifier les parametres de configuration
 * stockes dans un fichier properties.
 * </p>
 *
 * @author SGPA Team
 * @version 1.0
 */
public class ConfigService {

    private static final Logger logger = LoggerFactory.getLogger(ConfigService.class);

    // Chemin du fichier de configuration utilisateur
    private static final String USER_CONFIG_DIR = System.getProperty("user.home") + "/.sgpa";
    private static final String USER_CONFIG_FILE = USER_CONFIG_DIR + "/config.properties";

    // Proprietes par defaut
    private static final Properties DEFAULT_PROPS = new Properties();

    static {
        // Valeurs par defaut
        DEFAULT_PROPS.setProperty("pharmacie.nom", "ApotiCare");
        DEFAULT_PROPS.setProperty("pharmacie.adresse", "");
        DEFAULT_PROPS.setProperty("pharmacie.telephone", "");
        DEFAULT_PROPS.setProperty("alerte.peremption.jours", "90");
        DEFAULT_PROPS.setProperty("alerte.stock.seuil.defaut", "10");
        DEFAULT_PROPS.setProperty("rapports.repertoire", System.getProperty("user.home") + "/ApotiCare_Rapports");
        DEFAULT_PROPS.setProperty("backup.repertoire", System.getProperty("user.home") + "/ApotiCare_Backups");
        DEFAULT_PROPS.setProperty("backup.compression", "true");
        DEFAULT_PROPS.setProperty("ui.pagination.taille", "50");
        DEFAULT_PROPS.setProperty("ui.theme", "default");
        DEFAULT_PROPS.setProperty("format.monnaie", "EUR");
        DEFAULT_PROPS.setProperty("format.decimales", "2");
        // Predictions de reapprovisionnement
        DEFAULT_PROPS.setProperty("prediction.jours.analyse", "90");
        DEFAULT_PROPS.setProperty("prediction.delai.livraison.defaut", "3");
        DEFAULT_PROPS.setProperty("prediction.marge.securite.jours", "7");
        DEFAULT_PROPS.setProperty("prediction.stock.cible.jours", "30");
        DEFAULT_PROPS.setProperty("prediction.seuil.critique.jours", "7");
        DEFAULT_PROPS.setProperty("prediction.seuil.urgent.jours", "14");
    }

    private Properties config;

    public ConfigService() {
        loadConfig();
    }

    /**
     * Charge la configuration.
     * <p>
     * Charge d'abord les valeurs par defaut, puis les surcharge
     * avec les valeurs du fichier utilisateur si disponible.
     * </p>
     */
    private void loadConfig() {
        config = new Properties(DEFAULT_PROPS);

        // Charger la config par defaut embarquee
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (is != null) {
                Properties embedded = new Properties();
                embedded.load(is);
                // Remplacer les variables ${user.home}
                for (String key : embedded.stringPropertyNames()) {
                    String value = embedded.getProperty(key);
                    value = value.replace("${user.home}", System.getProperty("user.home"));
                    config.setProperty(key, value);
                }
            }
        } catch (IOException e) {
            logger.warn("Impossible de charger la config embarquee", e);
        }

        // Charger la config utilisateur (prioritaire)
        Path userConfigPath = Paths.get(USER_CONFIG_FILE);
        if (Files.exists(userConfigPath)) {
            try (InputStream is = Files.newInputStream(userConfigPath)) {
                Properties userConfig = new Properties();
                userConfig.load(is);
                for (String key : userConfig.stringPropertyNames()) {
                    config.setProperty(key, userConfig.getProperty(key));
                }
                logger.info("Configuration utilisateur chargee depuis {}", USER_CONFIG_FILE);
            } catch (IOException e) {
                logger.warn("Impossible de charger la config utilisateur", e);
            }
        }
    }

    /**
     * Sauvegarde la configuration dans le fichier utilisateur.
     *
     * @throws IOException si une erreur d'ecriture survient
     */
    public void saveConfig() throws IOException {
        // Creer le repertoire si necessaire
        Path configDir = Paths.get(USER_CONFIG_DIR);
        if (!Files.exists(configDir)) {
            Files.createDirectories(configDir);
        }

        // Sauvegarder uniquement les proprietes modifiees (differentes des defauts)
        Properties toSave = new Properties();
        for (String key : config.stringPropertyNames()) {
            String value = config.getProperty(key);
            String defaultValue = DEFAULT_PROPS.getProperty(key);
            // Sauvegarder si different de la valeur par defaut ou si c'est une nouvelle cle
            if (defaultValue == null || !value.equals(defaultValue)) {
                toSave.setProperty(key, value);
            }
        }

        try (OutputStream os = Files.newOutputStream(Paths.get(USER_CONFIG_FILE))) {
            toSave.store(os, "ApotiCare - Configuration Utilisateur");
            logger.info("Configuration sauvegardee dans {}", USER_CONFIG_FILE);
        }
    }

    // ==================== Getters ====================

    public String getPharmacieNom() {
        return config.getProperty("pharmacie.nom");
    }

    public String getPharmacieAdresse() {
        return config.getProperty("pharmacie.adresse");
    }

    public String getPharmacieTelephone() {
        return config.getProperty("pharmacie.telephone");
    }

    public int getAlertePeremptionJours() {
        try {
            return Integer.parseInt(config.getProperty("alerte.peremption.jours", "90"));
        } catch (NumberFormatException e) {
            return 90;
        }
    }

    public int getAlerteStockSeuilDefaut() {
        try {
            return Integer.parseInt(config.getProperty("alerte.stock.seuil.defaut", "10"));
        } catch (NumberFormatException e) {
            return 10;
        }
    }

    public String getRapportsRepertoire() {
        return config.getProperty("rapports.repertoire");
    }

    public String getBackupRepertoire() {
        return config.getProperty("backup.repertoire");
    }

    public boolean isBackupCompression() {
        return Boolean.parseBoolean(config.getProperty("backup.compression", "true"));
    }

    public int getUiPaginationTaille() {
        try {
            return Integer.parseInt(config.getProperty("ui.pagination.taille", "50"));
        } catch (NumberFormatException e) {
            return 50;
        }
    }

    public String getUiTheme() {
        return config.getProperty("ui.theme", "default");
    }

    public String getFormatMonnaie() {
        return config.getProperty("format.monnaie", "EUR");
    }

    public int getFormatDecimales() {
        try {
            return Integer.parseInt(config.getProperty("format.decimales", "2"));
        } catch (NumberFormatException e) {
            return 2;
        }
    }

    // ==================== Setters ====================

    public void setPharmacieNom(String nom) {
        config.setProperty("pharmacie.nom", nom != null ? nom : "");
    }

    public void setPharmacieAdresse(String adresse) {
        config.setProperty("pharmacie.adresse", adresse != null ? adresse : "");
    }

    public void setPharmacieTelephone(String telephone) {
        config.setProperty("pharmacie.telephone", telephone != null ? telephone : "");
    }

    public void setAlertePeremptionJours(int jours) {
        config.setProperty("alerte.peremption.jours", String.valueOf(jours));
    }

    public void setAlerteStockSeuilDefaut(int seuil) {
        config.setProperty("alerte.stock.seuil.defaut", String.valueOf(seuil));
    }

    public void setRapportsRepertoire(String repertoire) {
        config.setProperty("rapports.repertoire", repertoire != null ? repertoire : "");
    }

    public void setBackupRepertoire(String repertoire) {
        config.setProperty("backup.repertoire", repertoire != null ? repertoire : "");
    }

    public void setBackupCompression(boolean compression) {
        config.setProperty("backup.compression", String.valueOf(compression));
    }

    public void setUiPaginationTaille(int taille) {
        config.setProperty("ui.pagination.taille", String.valueOf(taille));
    }

    public void setUiTheme(String theme) {
        config.setProperty("ui.theme", theme != null ? theme : "default");
    }

    /**
     * Recharge la configuration depuis les fichiers.
     */
    public void reloadConfig() {
        loadConfig();
    }

    /**
     * Reinitialise la configuration aux valeurs par defaut.
     *
     * @throws IOException si une erreur survient lors de la suppression du fichier
     */
    public void resetToDefaults() throws IOException {
        Path userConfigPath = Paths.get(USER_CONFIG_FILE);
        if (Files.exists(userConfigPath)) {
            Files.delete(userConfigPath);
        }
        loadConfig();
    }

    // ==================== Getters Predictions ====================

    /**
     * Retourne le nombre de jours d'historique pour l'analyse des predictions.
     */
    public int getPredictionJoursAnalyse() {
        try {
            return Integer.parseInt(config.getProperty("prediction.jours.analyse", "90"));
        } catch (NumberFormatException e) {
            return 90;
        }
    }

    /**
     * Retourne le delai de livraison par defaut (en jours).
     */
    public int getPredictionDelaiLivraisonDefaut() {
        try {
            return Integer.parseInt(config.getProperty("prediction.delai.livraison.defaut", "3"));
        } catch (NumberFormatException e) {
            return 3;
        }
    }

    /**
     * Retourne la marge de securite (en jours) pour les predictions.
     */
    public int getPredictionMargeSecuriteJours() {
        try {
            return Integer.parseInt(config.getProperty("prediction.marge.securite.jours", "7"));
        } catch (NumberFormatException e) {
            return 7;
        }
    }

    /**
     * Retourne le nombre de jours de stock cible.
     */
    public int getPredictionStockCibleJours() {
        try {
            return Integer.parseInt(config.getProperty("prediction.stock.cible.jours", "30"));
        } catch (NumberFormatException e) {
            return 30;
        }
    }

    /**
     * Retourne le seuil critique en jours avant rupture.
     */
    public int getPredictionSeuilCritiqueJours() {
        try {
            return Integer.parseInt(config.getProperty("prediction.seuil.critique.jours", "7"));
        } catch (NumberFormatException e) {
            return 7;
        }
    }

    /**
     * Retourne le seuil urgent en jours avant rupture.
     */
    public int getPredictionSeuilUrgentJours() {
        try {
            return Integer.parseInt(config.getProperty("prediction.seuil.urgent.jours", "14"));
        } catch (NumberFormatException e) {
            return 14;
        }
    }

    // ==================== Setters Predictions ====================

    public void setPredictionJoursAnalyse(int jours) {
        config.setProperty("prediction.jours.analyse", String.valueOf(jours));
    }

    public void setPredictionDelaiLivraisonDefaut(int jours) {
        config.setProperty("prediction.delai.livraison.defaut", String.valueOf(jours));
    }

    public void setPredictionMargeSecuriteJours(int jours) {
        config.setProperty("prediction.marge.securite.jours", String.valueOf(jours));
    }

    public void setPredictionStockCibleJours(int jours) {
        config.setProperty("prediction.stock.cible.jours", String.valueOf(jours));
    }

    public void setPredictionSeuilCritiqueJours(int jours) {
        config.setProperty("prediction.seuil.critique.jours", String.valueOf(jours));
    }

    public void setPredictionSeuilUrgentJours(int jours) {
        config.setProperty("prediction.seuil.urgent.jours", String.valueOf(jours));
    }
}
