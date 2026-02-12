package com.sgpa.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Utilitaire pour l'export de donnees au format CSV.
 * <p>
 * Gere la generation de fichiers CSV avec encodage UTF-8 et BOM
 * pour une compatibilite optimale avec Excel.
 * </p>
 *
 * @author SGPA Team
 * @version 1.0
 */
public class CSVExporter {

    private static final Logger logger = LoggerFactory.getLogger(CSVExporter.class);

    /** Separateur CSV (point-virgule pour compatibilite Excel FR) */
    private static final String SEPARATOR = ";";

    /** Retour a la ligne */
    private static final String NEW_LINE = "\r\n";

    /** BOM UTF-8 pour Excel */
    private static final String UTF8_BOM = "\uFEFF";

    /** Repertoire par defaut pour les exports */
    private static final String DEFAULT_OUTPUT_DIR = System.getProperty("user.home") + "/ApotiCare_Exports";

    /** Formatteur de date/heure pour les noms de fichiers */
    private static final DateTimeFormatter FILE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /** Formatteur de date pour l'affichage */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Formatteur de date/heure pour l'affichage */
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    /**
     * Cree le repertoire de sortie s'il n'existe pas.
     *
     * @return le chemin du repertoire
     */
    public static String getOutputDir() {
        File dir = new File(DEFAULT_OUTPUT_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return DEFAULT_OUTPUT_DIR;
    }

    /**
     * Genere un chemin de fichier avec horodatage.
     *
     * @param prefix le prefixe du fichier
     * @return le chemin complet du fichier
     */
    public static String generateFilePath(String prefix) {
        String timestamp = LocalDateTime.now().format(FILE_DATE_FORMATTER);
        return getOutputDir() + "/" + prefix + "_" + timestamp + ".csv";
    }

    /**
     * Exporte des donnees au format CSV.
     *
     * @param filePath le chemin du fichier
     * @param headers  les en-tetes de colonnes
     * @param rows     les lignes de donnees (chaque ligne est un tableau de valeurs)
     * @return le chemin du fichier genere
     * @throws IOException si une erreur d'ecriture survient
     */
    public static String export(String filePath, String[] headers, List<Object[]> rows) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            // BOM UTF-8 pour Excel
            writer.write(UTF8_BOM);

            // En-tetes
            writer.write(String.join(SEPARATOR, headers));
            writer.write(NEW_LINE);

            // Donnees
            for (Object[] row : rows) {
                StringBuilder line = new StringBuilder();
                for (int i = 0; i < row.length; i++) {
                    if (i > 0) {
                        line.append(SEPARATOR);
                    }
                    line.append(formatValue(row[i]));
                }
                writer.write(line.toString());
                writer.write(NEW_LINE);
            }

            logger.info("Export CSV genere: {}", filePath);
            return filePath;
        }
    }

    /**
     * Formate une valeur pour le CSV.
     *
     * @param value la valeur a formater
     * @return la valeur formatee
     */
    private static String formatValue(Object value) {
        if (value == null) {
            return "";
        }

        String str;
        if (value instanceof LocalDateTime) {
            str = ((LocalDateTime) value).format(DATETIME_FORMATTER);
        } else if (value instanceof LocalDate) {
            str = ((LocalDate) value).format(DATE_FORMATTER);
        } else if (value instanceof BigDecimal) {
            // Remplacer le point par une virgule pour Excel FR
            str = value.toString().replace(".", ",");
        } else if (value instanceof Double || value instanceof Float) {
            str = String.format("%.2f", value).replace(".", ",");
        } else {
            str = value.toString();
        }

        // Echapper les guillemets et encadrer si necessaire
        if (str.contains(SEPARATOR) || str.contains("\"") || str.contains("\n")) {
            str = "\"" + str.replace("\"", "\"\"") + "\"";
        }

        return str;
    }

    /**
     * Formate une date pour l'affichage.
     *
     * @param date la date
     * @return la date formatee
     */
    public static String formatDate(LocalDate date) {
        return date != null ? date.format(DATE_FORMATTER) : "";
    }

    /**
     * Formate une date/heure pour l'affichage.
     *
     * @param dateTime la date/heure
     * @return la date/heure formatee
     */
    public static String formatDateTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DATETIME_FORMATTER) : "";
    }

    /**
     * Formate un montant pour l'affichage.
     *
     * @param montant le montant
     * @return le montant formate
     */
    public static String formatMontant(BigDecimal montant) {
        return montant != null ? String.format("%.2f", montant).replace(".", ",") : "0,00";
    }
}
