package com.sgpa.utils;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.io.font.constants.StandardFonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utilitaire de generation de documents PDF avec iText 7.
 * <p>
 * Cette classe fournit des methodes helper pour creer des documents PDF
 * formates avec en-tetes, tableaux, et pied de page.
 * </p>
 *
 * @author SGPA Team
 * @version 1.0
 */
public class PDFGenerator {

    private static final Logger logger = LoggerFactory.getLogger(PDFGenerator.class);

    /** Couleur primaire (bleu SGPA) */
    public static final DeviceRgb COLOR_PRIMARY = new DeviceRgb(37, 99, 235);

    /** Couleur secondaire (gris fonce) */
    public static final DeviceRgb COLOR_SECONDARY = new DeviceRgb(51, 65, 85);

    /** Couleur d'en-tete de tableau */
    public static final DeviceRgb COLOR_TABLE_HEADER = new DeviceRgb(241, 245, 249);

    /** Couleur de ligne alternee */
    public static final DeviceRgb COLOR_TABLE_ALTERNATE = new DeviceRgb(248, 250, 252);

    /** Format de date pour affichage */
    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Format de date/heure pour affichage */
    public static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** Repertoire par defaut pour les PDFs generes */
    private static final String DEFAULT_OUTPUT_DIR = System.getProperty("user.home") + File.separator + "ApotiCare_Rapports";

    private PdfFont fontRegular;
    private PdfFont fontBold;

    /**
     * Constructeur par defaut.
     */
    public PDFGenerator() {
        try {
            this.fontRegular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            this.fontBold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        } catch (IOException e) {
            logger.error("Erreur lors du chargement des polices", e);
        }
    }

    /**
     * Cree un nouveau document PDF.
     *
     * @param filePath le chemin complet du fichier PDF
     * @param pageSize la taille de page (A4, A5, etc.)
     * @return le document cree
     * @throws IOException si une erreur survient lors de la creation
     */
    public Document createDocument(String filePath, PageSize pageSize) throws IOException {
        ensureDirectoryExists(filePath);
        PdfWriter writer = new PdfWriter(filePath);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf, pageSize);
        document.setMargins(36, 36, 36, 36);
        logger.debug("Document PDF cree: {}", filePath);
        return document;
    }

    /**
     * Cree un document PDF au format A4.
     *
     * @param filePath le chemin complet du fichier PDF
     * @return le document cree
     * @throws IOException si une erreur survient
     */
    public Document createA4Document(String filePath) throws IOException {
        return createDocument(filePath, PageSize.A4);
    }

    /**
     * Cree un document PDF au format ticket (80mm de large).
     *
     * @param filePath le chemin complet du fichier PDF
     * @return le document cree
     * @throws IOException si une erreur survient
     */
    public Document createTicketDocument(String filePath) throws IOException {
        // Format ticket thermique: 80mm de large, hauteur variable
        PageSize ticketSize = new PageSize(226.77f, 600f); // 80mm = 226.77 points
        Document document = createDocument(filePath, ticketSize);
        document.setMargins(10, 10, 10, 10);
        return document;
    }

    /**
     * Ajoute un en-tete de pharmacie au document.
     *
     * @param document le document PDF
     * @param titre    le titre du document
     */
    public void addHeader(Document document, String titre) {
        addHeader(document, titre, "ApotiCare - Gestion Moderne de Pharmacie", null, null, null);
    }

    /**
     * Ajoute un en-tete de pharmacie complet au document.
     *
     * @param document    le document PDF
     * @param titre       le titre du document
     * @param nomPharmacie le nom de la pharmacie
     * @param adresse     l'adresse (peut etre null)
     * @param telephone   le telephone (peut etre null)
     * @param siret       le numero SIRET (peut etre null)
     */
    public void addHeader(Document document, String titre, String nomPharmacie,
                          String adresse, String telephone, String siret) {
        // Nom de la pharmacie
        Paragraph header = new Paragraph(nomPharmacie)
                .setFont(fontBold)
                .setFontSize(16)
                .setFontColor(COLOR_PRIMARY)
                .setTextAlignment(TextAlignment.CENTER);
        document.add(header);

        // Informations de contact
        if (adresse != null && !adresse.isEmpty()) {
            document.add(new Paragraph(adresse)
                    .setFont(fontRegular)
                    .setFontSize(9)
                    .setTextAlignment(TextAlignment.CENTER));
        }
        if (telephone != null && !telephone.isEmpty()) {
            document.add(new Paragraph("Tel: " + telephone)
                    .setFont(fontRegular)
                    .setFontSize(9)
                    .setTextAlignment(TextAlignment.CENTER));
        }
        if (siret != null && !siret.isEmpty()) {
            document.add(new Paragraph("SIRET: " + siret)
                    .setFont(fontRegular)
                    .setFontSize(9)
                    .setTextAlignment(TextAlignment.CENTER));
        }

        // Ligne de separation
        document.add(new Paragraph("")
                .setBorderBottom(new SolidBorder(COLOR_PRIMARY, 1))
                .setMarginBottom(10));

        // Titre du document
        Paragraph titleParagraph = new Paragraph(titre)
                .setFont(fontBold)
                .setFontSize(14)
                .setFontColor(COLOR_SECONDARY)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(10)
                .setMarginBottom(10);
        document.add(titleParagraph);
    }

    /**
     * Ajoute un en-tete simplifie pour ticket.
     *
     * @param document     le document PDF
     * @param nomPharmacie le nom de la pharmacie
     */
    public void addTicketHeader(Document document, String nomPharmacie) {
        Paragraph header = new Paragraph(nomPharmacie)
                .setFont(fontBold)
                .setFontSize(12)
                .setTextAlignment(TextAlignment.CENTER);
        document.add(header);

        document.add(new Paragraph("--------------------------------")
                .setFont(fontRegular)
                .setFontSize(8)
                .setTextAlignment(TextAlignment.CENTER));
    }

    /**
     * Cree un tableau avec les colonnes specifiees.
     *
     * @param columnWidths les largeurs relatives des colonnes
     * @param headers      les en-tetes des colonnes
     * @return le tableau cree
     */
    public Table createTable(float[] columnWidths, String... headers) {
        Table table = new Table(UnitValue.createPercentArray(columnWidths));
        table.setWidth(UnitValue.createPercentValue(100));

        // Ajouter les en-tetes
        for (String header : headers) {
            Cell cell = new Cell()
                    .add(new Paragraph(header).setFont(fontBold).setFontSize(10))
                    .setBackgroundColor(COLOR_TABLE_HEADER)
                    .setPadding(5)
                    .setBorder(new SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f));
            table.addHeaderCell(cell);
        }

        return table;
    }

    /**
     * Ajoute une cellule de donnees au tableau.
     *
     * @param table     le tableau
     * @param content   le contenu de la cellule
     * @param alternate true pour utiliser la couleur alternee
     */
    public void addCell(Table table, String content, boolean alternate) {
        Cell cell = new Cell()
                .add(new Paragraph(content != null ? content : "").setFont(fontRegular).setFontSize(9))
                .setPadding(4)
                .setBorder(new SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f));
        if (alternate) {
            cell.setBackgroundColor(COLOR_TABLE_ALTERNATE);
        }
        table.addCell(cell);
    }

    /**
     * Ajoute une cellule de donnees alignee a droite (pour les montants).
     *
     * @param table     le tableau
     * @param content   le contenu de la cellule
     * @param alternate true pour utiliser la couleur alternee
     */
    public void addCellRight(Table table, String content, boolean alternate) {
        Cell cell = new Cell()
                .add(new Paragraph(content != null ? content : "")
                        .setFont(fontRegular)
                        .setFontSize(9)
                        .setTextAlignment(TextAlignment.RIGHT))
                .setPadding(4)
                .setBorder(new SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f));
        if (alternate) {
            cell.setBackgroundColor(COLOR_TABLE_ALTERNATE);
        }
        table.addCell(cell);
    }

    /**
     * Ajoute une ligne de total au tableau.
     *
     * @param table       le tableau
     * @param label       le libelle du total
     * @param value       la valeur du total
     * @param colspan     le nombre de colonnes pour le label
     */
    public void addTotalRow(Table table, String label, String value, int colspan) {
        Cell labelCell = new Cell(1, colspan)
                .add(new Paragraph(label)
                        .setFont(fontBold)
                        .setFontSize(10)
                        .setTextAlignment(TextAlignment.RIGHT))
                .setPadding(5)
                .setBorder(Border.NO_BORDER)
                .setBorderTop(new SolidBorder(COLOR_PRIMARY, 1));
        table.addCell(labelCell);

        Cell valueCell = new Cell()
                .add(new Paragraph(value)
                        .setFont(fontBold)
                        .setFontSize(10)
                        .setTextAlignment(TextAlignment.RIGHT))
                .setPadding(5)
                .setBorder(Border.NO_BORDER)
                .setBorderTop(new SolidBorder(COLOR_PRIMARY, 1));
        table.addCell(valueCell);
    }

    /**
     * Ajoute un paragraphe de texte.
     *
     * @param document le document PDF
     * @param text     le texte a ajouter
     */
    public void addParagraph(Document document, String text) {
        document.add(new Paragraph(text)
                .setFont(fontRegular)
                .setFontSize(10)
                .setMarginBottom(5));
    }

    /**
     * Ajoute un paragraphe en gras.
     *
     * @param document le document PDF
     * @param text     le texte a ajouter
     */
    public void addBoldParagraph(Document document, String text) {
        document.add(new Paragraph(text)
                .setFont(fontBold)
                .setFontSize(10)
                .setMarginBottom(5));
    }

    /**
     * Ajoute une ligne d'information (label: valeur).
     *
     * @param document le document PDF
     * @param label    le libelle
     * @param value    la valeur
     */
    public void addInfoLine(Document document, String label, String value) {
        Paragraph p = new Paragraph()
                .add(new com.itextpdf.layout.element.Text(label + ": ").setFont(fontBold).setFontSize(10))
                .add(new com.itextpdf.layout.element.Text(value != null ? value : "-").setFont(fontRegular).setFontSize(10))
                .setMarginBottom(3);
        document.add(p);
    }

    /**
     * Ajoute un pied de page avec date et numero de page.
     *
     * @param document le document PDF
     */
    public void addFooter(Document document) {
        String dateGeneration = LocalDateTime.now().format(DATETIME_FORMATTER);
        document.add(new Paragraph("")
                .setBorderTop(new SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f))
                .setMarginTop(20));
        document.add(new Paragraph("Document genere le " + dateGeneration + " - ApotiCare v1.0")
                .setFont(fontRegular)
                .setFontSize(8)
                .setFontColor(ColorConstants.GRAY)
                .setTextAlignment(TextAlignment.CENTER));
    }

    /**
     * Ajoute un pied de page pour ticket.
     *
     * @param document le document PDF
     */
    public void addTicketFooter(Document document) {
        document.add(new Paragraph("--------------------------------")
                .setFont(fontRegular)
                .setFontSize(8)
                .setTextAlignment(TextAlignment.CENTER));
        document.add(new Paragraph("Merci de votre visite !")
                .setFont(fontBold)
                .setFontSize(9)
                .setTextAlignment(TextAlignment.CENTER));
        document.add(new Paragraph("Conservez ce ticket comme justificatif")
                .setFont(fontRegular)
                .setFontSize(7)
                .setTextAlignment(TextAlignment.CENTER));
    }

    /**
     * Formate un montant en euros.
     *
     * @param montant le montant a formater
     * @return le montant formate (ex: "12,50 EUR")
     */
    public static String formatMontant(BigDecimal montant) {
        if (montant == null) return "0,00 EUR";
        return String.format("%,.2f EUR", montant).replace(',', ' ').replace('.', ',').replace(' ', ' ');
    }

    /**
     * Formate une date.
     *
     * @param date la date a formater
     * @return la date formatee (ex: "15/01/2024")
     */
    public static String formatDate(LocalDate date) {
        if (date == null) return "-";
        return date.format(DATE_FORMATTER);
    }

    /**
     * Formate une date/heure.
     *
     * @param dateTime la date/heure a formater
     * @return la date/heure formatee (ex: "15/01/2024 14:30")
     */
    public static String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) return "-";
        return dateTime.format(DATETIME_FORMATTER);
    }

    /**
     * Genere un nom de fichier unique pour un type de rapport.
     *
     * @param typeRapport le type de rapport (ex: "ticket", "rapport_ventes")
     * @param suffix      un suffixe optionnel (ex: numero de vente)
     * @return le chemin complet du fichier
     */
    public static String generateFilePath(String typeRapport, String suffix) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = typeRapport + "_" + timestamp;
        if (suffix != null && !suffix.isEmpty()) {
            fileName += "_" + suffix;
        }
        fileName += ".pdf";
        return DEFAULT_OUTPUT_DIR + File.separator + fileName;
    }

    /**
     * Retourne le repertoire par defaut des rapports.
     *
     * @return le chemin du repertoire
     */
    public static String getDefaultOutputDir() {
        return DEFAULT_OUTPUT_DIR;
    }

    /**
     * S'assure que le repertoire parent existe.
     *
     * @param filePath le chemin du fichier
     */
    private void ensureDirectoryExists(String filePath) {
        File file = new File(filePath);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
            logger.debug("Repertoire cree: {}", parent.getAbsolutePath());
        }
    }

    /**
     * Retourne la police normale.
     *
     * @return la police normale
     */
    public PdfFont getFontRegular() {
        return fontRegular;
    }

    /**
     * Retourne la police en gras.
     *
     * @return la police en gras
     */
    public PdfFont getFontBold() {
        return fontBold;
    }
}
