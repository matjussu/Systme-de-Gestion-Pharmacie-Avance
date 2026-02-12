package com.sgpa.service;

import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.sgpa.dao.MedicamentDAO;
import com.sgpa.dao.VenteDAO;
import com.sgpa.dao.impl.MedicamentDAOImpl;
import com.sgpa.dao.impl.VenteDAOImpl;
import com.sgpa.dto.AlertePeremption;
import com.sgpa.dto.AlerteStock;
import com.sgpa.dto.PredictionReapprovisionnement;
import com.sgpa.exception.DAOException;
import com.sgpa.exception.ServiceException;
import com.sgpa.model.*;
import com.sgpa.utils.PDFGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service de generation des rapports PDF.
 * <p>
 * Ce service permet de generer differents types de rapports :
 * <ul>
 *   <li>Ticket de caisse</li>
 *   <li>Rapport journalier des ventes</li>
 *   <li>Export des alertes stock/peremption</li>
 *   <li>Bon de commande fournisseur</li>
 * </ul>
 * </p>
 *
 * @author SGPA Team
 * @version 1.0
 */
public class RapportService {

    private static final Logger logger = LoggerFactory.getLogger(RapportService.class);

    private final PDFGenerator pdfGenerator;
    private final VenteDAO venteDAO;
    private final MedicamentDAO medicamentDAO;
    private final AlerteService alerteService;

    // Informations de la pharmacie (a configurer)
    private String nomPharmacie = "ApotiCare";
    private String adressePharmacie = "";
    private String telephonePharmacie = "";
    private String siretPharmacie = "";

    /**
     * Constructeur par defaut.
     */
    public RapportService() {
        this.pdfGenerator = new PDFGenerator();
        this.venteDAO = new VenteDAOImpl();
        this.medicamentDAO = new MedicamentDAOImpl();
        this.alerteService = new AlerteService();
    }

    /**
     * Constructeur avec injection des dependances (pour tests).
     *
     * @param pdfGenerator  le generateur PDF
     * @param venteDAO      le DAO vente
     * @param medicamentDAO le DAO medicament
     * @param alerteService le service d'alertes
     */
    public RapportService(PDFGenerator pdfGenerator, VenteDAO venteDAO,
                          MedicamentDAO medicamentDAO, AlerteService alerteService) {
        this.pdfGenerator = pdfGenerator;
        this.venteDAO = venteDAO;
        this.medicamentDAO = medicamentDAO;
        this.alerteService = alerteService;
    }

    /**
     * Configure les informations de la pharmacie pour les rapports.
     *
     * @param nom       le nom de la pharmacie
     * @param adresse   l'adresse
     * @param telephone le telephone
     * @param siret     le numero SIRET
     */
    public void configurerPharmacie(String nom, String adresse, String telephone, String siret) {
        this.nomPharmacie = nom;
        this.adressePharmacie = adresse;
        this.telephonePharmacie = telephone;
        this.siretPharmacie = siret;
    }

    // =====================================================
    // TICKET DE CAISSE
    // =====================================================

    /**
     * Genere un ticket de caisse pour une vente.
     *
     * @param vente      la vente
     * @param nomVendeur le nom du vendeur
     * @return le chemin du fichier PDF genere
     * @throws ServiceException si une erreur survient
     */
    public String genererTicketCaisse(Vente vente, String nomVendeur) throws ServiceException {
        String filePath = PDFGenerator.generateFilePath("ticket", String.valueOf(vente.getIdVente()));

        try {
            Document document = pdfGenerator.createTicketDocument(filePath);

            // En-tete
            pdfGenerator.addTicketHeader(document, nomPharmacie);

            // Date et numero de vente
            document.add(new Paragraph("Date: " + PDFGenerator.formatDateTime(vente.getDateVente()))
                    .setFontSize(8)
                    .setTextAlignment(TextAlignment.CENTER));
            document.add(new Paragraph("Vente N° " + vente.getIdVente())
                    .setFontSize(8)
                    .setTextAlignment(TextAlignment.CENTER));
            document.add(new Paragraph("Vendeur: " + nomVendeur)
                    .setFontSize(8)
                    .setTextAlignment(TextAlignment.CENTER));

            document.add(new Paragraph("--------------------------------")
                    .setFontSize(8)
                    .setTextAlignment(TextAlignment.CENTER));

            // Lignes de vente
            List<LigneVente> lignes = vente.getLignesVente();
            if (lignes != null) {
                for (LigneVente ligne : lignes) {
                    String nomMed = getNomMedicament(ligne);
                    int qte = ligne.getQuantite();
                    BigDecimal prix = ligne.getPrixUnitaireApplique();
                    BigDecimal total = ligne.getMontantLigne();

                    document.add(new Paragraph(nomMed)
                            .setFontSize(8)
                            .setTextAlignment(TextAlignment.LEFT));
                    document.add(new Paragraph(
                            String.format("  %d x %s = %s",
                                    qte,
                                    formatMontantTicket(prix),
                                    formatMontantTicket(total)))
                            .setFontSize(8)
                            .setTextAlignment(TextAlignment.RIGHT));
                }
            }

            document.add(new Paragraph("--------------------------------")
                    .setFontSize(8)
                    .setTextAlignment(TextAlignment.CENTER));

            // Total
            document.add(new Paragraph("TOTAL: " + formatMontantTicket(vente.getMontantTotal()))
                    .setFont(pdfGenerator.getFontBold())
                    .setFontSize(10)
                    .setTextAlignment(TextAlignment.RIGHT));

            // Informations ordonnance
            if (vente.isEstSurOrdonnance()) {
                document.add(new Paragraph("Sur ordonnance")
                        .setFontSize(8)
                        .setTextAlignment(TextAlignment.CENTER));
                if (vente.getNumeroOrdonnance() != null && !vente.getNumeroOrdonnance().isEmpty()) {
                    document.add(new Paragraph("N° Ord: " + vente.getNumeroOrdonnance())
                            .setFontSize(8)
                            .setTextAlignment(TextAlignment.CENTER));
                }
            }

            // Pied de page
            pdfGenerator.addTicketFooter(document);

            document.close();
            logger.info("Ticket de caisse genere: {}", filePath);
            return filePath;

        } catch (IOException e) {
            logger.error("Erreur lors de la generation du ticket", e);
            throw new ServiceException("Erreur lors de la generation du ticket de caisse", e);
        }
    }

    // =====================================================
    // RAPPORT JOURNALIER DES VENTES
    // =====================================================

    /**
     * Genere un rapport des ventes pour une date donnee.
     *
     * @param date la date du rapport
     * @return le chemin du fichier PDF genere
     * @throws ServiceException si une erreur survient
     */
    public String genererRapportVentesJournalier(LocalDate date) throws ServiceException {
        return genererRapportVentes(date, date);
    }

    /**
     * Genere un rapport des ventes pour une periode.
     *
     * @param dateDebut la date de debut
     * @param dateFin   la date de fin
     * @return le chemin du fichier PDF genere
     * @throws ServiceException si une erreur survient
     */
    public String genererRapportVentes(LocalDate dateDebut, LocalDate dateFin) throws ServiceException {
        String suffix = dateDebut.equals(dateFin)
                ? dateDebut.toString()
                : dateDebut.toString() + "_" + dateFin.toString();
        String filePath = PDFGenerator.generateFilePath("rapport_ventes", suffix);

        try {
            List<Vente> ventes = venteDAO.findByDateRange(dateDebut, dateFin);

            Document document = pdfGenerator.createA4Document(filePath);

            // En-tete
            String titre = dateDebut.equals(dateFin)
                    ? "Rapport des ventes du " + PDFGenerator.formatDate(dateDebut)
                    : "Rapport des ventes du " + PDFGenerator.formatDate(dateDebut)
                      + " au " + PDFGenerator.formatDate(dateFin);
            pdfGenerator.addHeader(document, titre, nomPharmacie, adressePharmacie, telephonePharmacie, siretPharmacie);

            // Resume
            int nombreVentes = ventes.size();
            BigDecimal totalCA = ventes.stream()
                    .map(Vente::getMontantTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            int nombreArticles = ventes.stream()
                    .mapToInt(Vente::getNombreArticles)
                    .sum();
            long ventesOrdonnance = ventes.stream()
                    .filter(Vente::isEstSurOrdonnance)
                    .count();

            pdfGenerator.addBoldParagraph(document, "Resume");
            pdfGenerator.addInfoLine(document, "Nombre de ventes", String.valueOf(nombreVentes));
            pdfGenerator.addInfoLine(document, "Chiffre d'affaires", PDFGenerator.formatMontant(totalCA));
            pdfGenerator.addInfoLine(document, "Articles vendus", String.valueOf(nombreArticles));
            pdfGenerator.addInfoLine(document, "Ventes sur ordonnance", String.valueOf(ventesOrdonnance));

            document.add(new Paragraph(" "));

            // Tableau des ventes
            if (!ventes.isEmpty()) {
                pdfGenerator.addBoldParagraph(document, "Detail des ventes");

                float[] columnWidths = {1, 2, 2, 1, 2};
                Table table = pdfGenerator.createTable(columnWidths,
                        "N°", "Date/Heure", "Vendeur", "Articles", "Montant");

                boolean alternate = false;
                for (Vente vente : ventes) {
                    pdfGenerator.addCell(table, String.valueOf(vente.getIdVente()), alternate);
                    pdfGenerator.addCell(table, PDFGenerator.formatDateTime(vente.getDateVente()), alternate);
                    String vendeur = vente.getUtilisateur() != null
                            ? vente.getUtilisateur().getNomComplet()
                            : "ID:" + vente.getIdUtilisateur();
                    pdfGenerator.addCell(table, vendeur, alternate);
                    pdfGenerator.addCellRight(table, String.valueOf(vente.getNombreArticles()), alternate);
                    pdfGenerator.addCellRight(table, PDFGenerator.formatMontant(vente.getMontantTotal()), alternate);
                    alternate = !alternate;
                }

                pdfGenerator.addTotalRow(table, "TOTAL", PDFGenerator.formatMontant(totalCA), 4);
                document.add(table);
            } else {
                pdfGenerator.addParagraph(document, "Aucune vente pour cette periode.");
            }

            pdfGenerator.addFooter(document);
            document.close();

            logger.info("Rapport des ventes genere: {}", filePath);
            return filePath;

        } catch (DAOException e) {
            logger.error("Erreur DAO lors de la generation du rapport", e);
            throw new ServiceException("Erreur lors de la recuperation des ventes", e);
        } catch (IOException e) {
            logger.error("Erreur IO lors de la generation du rapport", e);
            throw new ServiceException("Erreur lors de la generation du rapport", e);
        }
    }

    // =====================================================
    // EXPORT ALERTES
    // =====================================================

    /**
     * Genere un rapport des alertes de stock bas.
     *
     * @return le chemin du fichier PDF genere
     * @throws ServiceException si une erreur survient
     */
    public String genererRapportAlertesStock() throws ServiceException {
        String filePath = PDFGenerator.generateFilePath("alertes_stock", null);

        try {
            List<AlerteStock> alertes = alerteService.getAlertesStockBas();

            Document document = pdfGenerator.createA4Document(filePath);

            pdfGenerator.addHeader(document, "Alertes Stock Bas", nomPharmacie,
                    adressePharmacie, telephonePharmacie, siretPharmacie);

            pdfGenerator.addInfoLine(document, "Date du rapport", PDFGenerator.formatDateTime(LocalDateTime.now()));
            pdfGenerator.addInfoLine(document, "Nombre d'alertes", String.valueOf(alertes.size()));

            document.add(new Paragraph(" "));

            if (!alertes.isEmpty()) {
                float[] columnWidths = {3, 1, 1, 1, 1};
                Table table = pdfGenerator.createTable(columnWidths,
                        "Medicament", "Stock actuel", "Seuil min", "Deficit", "Criticite");

                boolean alternate = false;
                for (AlerteStock alerte : alertes) {
                    pdfGenerator.addCell(table, alerte.getNomMedicament(), alternate);
                    pdfGenerator.addCellRight(table, String.valueOf(alerte.getStockActuel()), alternate);
                    pdfGenerator.addCellRight(table, String.valueOf(alerte.getSeuilMin()), alternate);
                    pdfGenerator.addCellRight(table, String.valueOf(alerte.getDeficit()), alternate);
                    pdfGenerator.addCellRight(table, alerte.getNiveauCriticite() + "%", alternate);
                    alternate = !alternate;
                }

                document.add(table);
            } else {
                pdfGenerator.addParagraph(document, "Aucune alerte de stock bas.");
            }

            pdfGenerator.addFooter(document);
            document.close();

            logger.info("Rapport alertes stock genere: {}", filePath);
            return filePath;

        } catch (IOException e) {
            logger.error("Erreur lors de la generation du rapport alertes stock", e);
            throw new ServiceException("Erreur lors de la generation du rapport", e);
        }
    }

    /**
     * Genere un rapport des alertes de peremption.
     *
     * @return le chemin du fichier PDF genere
     * @throws ServiceException si une erreur survient
     */
    public String genererRapportAlertesPeremption() throws ServiceException {
        String filePath = PDFGenerator.generateFilePath("alertes_peremption", null);

        try {
            List<AlertePeremption> alertes = alerteService.getAlertesPeremption();

            Document document = pdfGenerator.createA4Document(filePath);

            pdfGenerator.addHeader(document, "Alertes Peremption Proche", nomPharmacie,
                    adressePharmacie, telephonePharmacie, siretPharmacie);

            pdfGenerator.addInfoLine(document, "Date du rapport", PDFGenerator.formatDateTime(LocalDateTime.now()));
            pdfGenerator.addInfoLine(document, "Nombre d'alertes", String.valueOf(alertes.size()));

            document.add(new Paragraph(" "));

            if (!alertes.isEmpty()) {
                float[] columnWidths = {3, 2, 2, 1, 1, 1};
                Table table = pdfGenerator.createTable(columnWidths,
                        "Medicament", "N° Lot", "Date peremption", "Jours", "Quantite", "Urgence");

                boolean alternate = false;
                for (AlertePeremption alerte : alertes) {
                    pdfGenerator.addCell(table, alerte.getNomMedicament(), alternate);
                    pdfGenerator.addCell(table, alerte.getNumeroLot(), alternate);
                    pdfGenerator.addCell(table, PDFGenerator.formatDate(alerte.getDatePeremption()), alternate);
                    pdfGenerator.addCellRight(table, String.valueOf(alerte.getJoursRestants()), alternate);
                    pdfGenerator.addCellRight(table, String.valueOf(alerte.getQuantiteStock()), alternate);
                    pdfGenerator.addCell(table, alerte.getNiveauUrgence(), alternate);
                    alternate = !alternate;
                }

                document.add(table);
            } else {
                pdfGenerator.addParagraph(document, "Aucune alerte de peremption proche.");
            }

            pdfGenerator.addFooter(document);
            document.close();

            logger.info("Rapport alertes peremption genere: {}", filePath);
            return filePath;

        } catch (IOException e) {
            logger.error("Erreur lors de la generation du rapport alertes peremption", e);
            throw new ServiceException("Erreur lors de la generation du rapport", e);
        }
    }

    /**
     * Genere un rapport des lots perimes.
     *
     * @return le chemin du fichier PDF genere
     * @throws ServiceException si une erreur survient
     */
    public String genererRapportLotsPerimes() throws ServiceException {
        String filePath = PDFGenerator.generateFilePath("lots_perimes", null);

        try {
            List<Lot> lotsPerimes = alerteService.getLotsPerimes();

            Document document = pdfGenerator.createA4Document(filePath);

            pdfGenerator.addHeader(document, "Lots Perimes", nomPharmacie,
                    adressePharmacie, telephonePharmacie, siretPharmacie);

            pdfGenerator.addInfoLine(document, "Date du rapport", PDFGenerator.formatDateTime(LocalDateTime.now()));
            pdfGenerator.addInfoLine(document, "Nombre de lots perimes", String.valueOf(lotsPerimes.size()));

            document.add(new Paragraph(" "));

            if (!lotsPerimes.isEmpty()) {
                float[] columnWidths = {3, 2, 2, 1};
                Table table = pdfGenerator.createTable(columnWidths,
                        "Medicament", "N° Lot", "Date peremption", "Quantite");

                boolean alternate = false;
                for (Lot lot : lotsPerimes) {
                    String nomMed = lot.getMedicament() != null
                            ? lot.getMedicament().getNomCommercial()
                            : "ID:" + lot.getIdMedicament();
                    pdfGenerator.addCell(table, nomMed, alternate);
                    pdfGenerator.addCell(table, lot.getNumeroLot(), alternate);
                    pdfGenerator.addCell(table, PDFGenerator.formatDate(lot.getDatePeremption()), alternate);
                    pdfGenerator.addCellRight(table, String.valueOf(lot.getQuantiteStock()), alternate);
                    alternate = !alternate;
                }

                document.add(table);

                document.add(new Paragraph(" "));
                pdfGenerator.addBoldParagraph(document,
                        "ATTENTION: Ces lots doivent etre retires de la vente et detruits conformement a la reglementation.");
            } else {
                pdfGenerator.addParagraph(document, "Aucun lot perime en stock.");
            }

            pdfGenerator.addFooter(document);
            document.close();

            logger.info("Rapport lots perimes genere: {}", filePath);
            return filePath;

        } catch (IOException e) {
            logger.error("Erreur lors de la generation du rapport lots perimes", e);
            throw new ServiceException("Erreur lors de la generation du rapport", e);
        }
    }

    /**
     * Genere un rapport complet de toutes les alertes.
     *
     * @return le chemin du fichier PDF genere
     * @throws ServiceException si une erreur survient
     */
    public String genererRapportAlertesComplet() throws ServiceException {
        String filePath = PDFGenerator.generateFilePath("alertes_complet", null);

        try {
            List<AlerteStock> alertesStock = alerteService.getAlertesStockBas();
            List<AlertePeremption> alertesPeremption = alerteService.getAlertesPeremption();
            List<Lot> lotsPerimes = alerteService.getLotsPerimes();

            Document document = pdfGenerator.createA4Document(filePath);

            pdfGenerator.addHeader(document, "Rapport Complet des Alertes", nomPharmacie,
                    adressePharmacie, telephonePharmacie, siretPharmacie);

            pdfGenerator.addInfoLine(document, "Date du rapport", PDFGenerator.formatDateTime(LocalDateTime.now()));

            // Resume
            document.add(new Paragraph(" "));
            pdfGenerator.addBoldParagraph(document, "Resume");
            pdfGenerator.addInfoLine(document, "Alertes stock bas", String.valueOf(alertesStock.size()));
            pdfGenerator.addInfoLine(document, "Alertes peremption proche", String.valueOf(alertesPeremption.size()));
            pdfGenerator.addInfoLine(document, "Lots perimes", String.valueOf(lotsPerimes.size()));

            // Section Stock Bas
            document.add(new Paragraph(" "));
            pdfGenerator.addBoldParagraph(document, "1. Alertes Stock Bas");
            if (!alertesStock.isEmpty()) {
                float[] columnWidths1 = {3, 1, 1, 1};
                Table table1 = pdfGenerator.createTable(columnWidths1,
                        "Medicament", "Stock", "Seuil", "Deficit");
                boolean alt1 = false;
                for (AlerteStock alerte : alertesStock) {
                    pdfGenerator.addCell(table1, alerte.getNomMedicament(), alt1);
                    pdfGenerator.addCellRight(table1, String.valueOf(alerte.getStockActuel()), alt1);
                    pdfGenerator.addCellRight(table1, String.valueOf(alerte.getSeuilMin()), alt1);
                    pdfGenerator.addCellRight(table1, String.valueOf(alerte.getDeficit()), alt1);
                    alt1 = !alt1;
                }
                document.add(table1);
            } else {
                pdfGenerator.addParagraph(document, "Aucune alerte.");
            }

            // Section Peremption Proche
            document.add(new Paragraph(" "));
            pdfGenerator.addBoldParagraph(document, "2. Alertes Peremption Proche");
            if (!alertesPeremption.isEmpty()) {
                float[] columnWidths2 = {3, 2, 1, 1};
                Table table2 = pdfGenerator.createTable(columnWidths2,
                        "Medicament", "Date peremption", "Jours", "Qte");
                boolean alt2 = false;
                for (AlertePeremption alerte : alertesPeremption) {
                    pdfGenerator.addCell(table2, alerte.getNomMedicament(), alt2);
                    pdfGenerator.addCell(table2, PDFGenerator.formatDate(alerte.getDatePeremption()), alt2);
                    pdfGenerator.addCellRight(table2, String.valueOf(alerte.getJoursRestants()), alt2);
                    pdfGenerator.addCellRight(table2, String.valueOf(alerte.getQuantiteStock()), alt2);
                    alt2 = !alt2;
                }
                document.add(table2);
            } else {
                pdfGenerator.addParagraph(document, "Aucune alerte.");
            }

            // Section Lots Perimes
            document.add(new Paragraph(" "));
            pdfGenerator.addBoldParagraph(document, "3. Lots Perimes (A RETIRER)");
            if (!lotsPerimes.isEmpty()) {
                float[] columnWidths3 = {3, 2, 2, 1};
                Table table3 = pdfGenerator.createTable(columnWidths3,
                        "Medicament", "N° Lot", "Perime le", "Qte");
                boolean alt3 = false;
                for (Lot lot : lotsPerimes) {
                    String nomMed = lot.getMedicament() != null
                            ? lot.getMedicament().getNomCommercial()
                            : "ID:" + lot.getIdMedicament();
                    pdfGenerator.addCell(table3, nomMed, alt3);
                    pdfGenerator.addCell(table3, lot.getNumeroLot(), alt3);
                    pdfGenerator.addCell(table3, PDFGenerator.formatDate(lot.getDatePeremption()), alt3);
                    pdfGenerator.addCellRight(table3, String.valueOf(lot.getQuantiteStock()), alt3);
                    alt3 = !alt3;
                }
                document.add(table3);
            } else {
                pdfGenerator.addParagraph(document, "Aucun lot perime.");
            }

            pdfGenerator.addFooter(document);
            document.close();

            logger.info("Rapport alertes complet genere: {}", filePath);
            return filePath;

        } catch (IOException e) {
            logger.error("Erreur lors de la generation du rapport alertes complet", e);
            throw new ServiceException("Erreur lors de la generation du rapport", e);
        }
    }

    // =====================================================
    // BON DE COMMANDE FOURNISSEUR
    // =====================================================

    /**
     * Genere un bon de commande PDF.
     *
     * @param commande la commande
     * @return le chemin du fichier PDF genere
     * @throws ServiceException si une erreur survient
     */
    public String genererBonCommande(Commande commande) throws ServiceException {
        String filePath = PDFGenerator.generateFilePath("bon_commande", String.valueOf(commande.getIdCommande()));

        try {
            Document document = pdfGenerator.createA4Document(filePath);

            pdfGenerator.addHeader(document, "Bon de Commande N° " + commande.getIdCommande(),
                    nomPharmacie, adressePharmacie, telephonePharmacie, siretPharmacie);

            // Informations de la commande
            pdfGenerator.addInfoLine(document, "Date de creation",
                    PDFGenerator.formatDateTime(commande.getDateCreation()));
            pdfGenerator.addInfoLine(document, "Statut", commande.getStatut().getLibelle());

            // Informations fournisseur
            document.add(new Paragraph(" "));
            pdfGenerator.addBoldParagraph(document, "Fournisseur");
            if (commande.getFournisseur() != null) {
                Fournisseur f = commande.getFournisseur();
                pdfGenerator.addInfoLine(document, "Nom", f.getNom());
                if (f.getAdresse() != null) {
                    pdfGenerator.addInfoLine(document, "Adresse", f.getAdresse());
                }
                if (f.getTelephone() != null) {
                    pdfGenerator.addInfoLine(document, "Telephone", f.getTelephone());
                }
                if (f.getEmail() != null) {
                    pdfGenerator.addInfoLine(document, "Email", f.getEmail());
                }
            } else {
                pdfGenerator.addInfoLine(document, "ID Fournisseur", String.valueOf(commande.getIdFournisseur()));
            }

            // Lignes de commande
            document.add(new Paragraph(" "));
            pdfGenerator.addBoldParagraph(document, "Articles commandes");

            List<LigneCommande> lignes = commande.getLignesCommande();
            if (lignes != null && !lignes.isEmpty()) {
                float[] columnWidths = {4, 1, 2, 2};
                Table table = pdfGenerator.createTable(columnWidths,
                        "Medicament", "Quantite", "Prix unitaire", "Total");

                BigDecimal totalCommande = BigDecimal.ZERO;
                boolean alternate = false;

                for (LigneCommande ligne : lignes) {
                    String nomMed = ligne.getMedicament() != null
                            ? ligne.getMedicament().getNomCommercial()
                            : "ID:" + ligne.getIdMedicament();
                    pdfGenerator.addCell(table, nomMed, alternate);
                    pdfGenerator.addCellRight(table, String.valueOf(ligne.getQuantiteCommandee()), alternate);
                    pdfGenerator.addCellRight(table, PDFGenerator.formatMontant(ligne.getPrixUnitaire()), alternate);
                    pdfGenerator.addCellRight(table, PDFGenerator.formatMontant(ligne.getMontantLigne()), alternate);

                    totalCommande = totalCommande.add(ligne.getMontantLigne());
                    alternate = !alternate;
                }

                pdfGenerator.addTotalRow(table, "TOTAL COMMANDE", PDFGenerator.formatMontant(totalCommande), 3);
                document.add(table);
            } else {
                pdfGenerator.addParagraph(document, "Aucune ligne de commande.");
            }

            // Notes
            if (commande.getNotes() != null && !commande.getNotes().isEmpty()) {
                document.add(new Paragraph(" "));
                pdfGenerator.addBoldParagraph(document, "Notes");
                pdfGenerator.addParagraph(document, commande.getNotes());
            }

            // Signature
            document.add(new Paragraph(" "));
            document.add(new Paragraph(" "));
            document.add(new Paragraph("Signature du responsable: _______________________")
                    .setFontSize(10));

            pdfGenerator.addFooter(document);
            document.close();

            logger.info("Bon de commande genere: {}", filePath);
            return filePath;

        } catch (IOException e) {
            logger.error("Erreur lors de la generation du bon de commande", e);
            throw new ServiceException("Erreur lors de la generation du bon de commande", e);
        }
    }

    // =====================================================
    // RAPPORT PREDICTIONS REAPPROVISIONNEMENT
    // =====================================================

    /**
     * Genere un rapport des predictions de reapprovisionnement.
     *
     * @param predictions la liste des predictions
     * @return le chemin du fichier PDF genere
     * @throws ServiceException si une erreur survient
     */
    public String genererRapportPredictions(List<PredictionReapprovisionnement> predictions)
            throws ServiceException {
        String filePath = PDFGenerator.generateFilePath("predictions_reappro", null);

        try {
            Document document = pdfGenerator.createA4Document(filePath);

            pdfGenerator.addHeader(document, "Predictions de Reapprovisionnement", nomPharmacie,
                    adressePharmacie, telephonePharmacie, siretPharmacie);

            pdfGenerator.addInfoLine(document, "Date du rapport", PDFGenerator.formatDateTime(LocalDateTime.now()));
            pdfGenerator.addInfoLine(document, "Nombre de medicaments", String.valueOf(predictions.size()));

            // Comptage par niveau d'urgence
            int ruptures = 0, critiques = 0, urgents = 0, attention = 0;
            for (PredictionReapprovisionnement p : predictions) {
                switch (p.getNiveauUrgence()) {
                    case PredictionReapprovisionnement.NIVEAU_RUPTURE -> ruptures++;
                    case PredictionReapprovisionnement.NIVEAU_CRITIQUE -> critiques++;
                    case PredictionReapprovisionnement.NIVEAU_URGENT -> urgents++;
                    case PredictionReapprovisionnement.NIVEAU_ATTENTION -> attention++;
                }
            }

            document.add(new Paragraph(" "));
            pdfGenerator.addBoldParagraph(document, "Resume des alertes");
            pdfGenerator.addInfoLine(document, "Ruptures", String.valueOf(ruptures));
            pdfGenerator.addInfoLine(document, "Critiques", String.valueOf(critiques));
            pdfGenerator.addInfoLine(document, "Urgents", String.valueOf(urgents));
            pdfGenerator.addInfoLine(document, "Attention", String.valueOf(attention));

            document.add(new Paragraph(" "));

            if (!predictions.isEmpty()) {
                pdfGenerator.addBoldParagraph(document, "Detail des predictions");

                float[] columnWidths = {3, 1, 1, 1, 2, 1, 1};
                Table table = pdfGenerator.createTable(columnWidths,
                        "Medicament", "Stock", "Conso/J", "Jours", "Date rupture", "Qte sugg.", "Urgence");

                boolean alternate = false;
                for (PredictionReapprovisionnement p : predictions) {
                    pdfGenerator.addCell(table, p.getNomMedicament(), alternate);
                    pdfGenerator.addCellRight(table, String.valueOf(p.getStockVendable()), alternate);
                    pdfGenerator.addCellRight(table, String.format("%.1f", p.getConsommationJournaliere()), alternate);
                    pdfGenerator.addCellRight(table, p.getJoursRestantsFormate(), alternate);
                    String dateRupture = p.getDateRupturePrevue() != null
                            ? PDFGenerator.formatDate(p.getDateRupturePrevue())
                            : "N/A";
                    pdfGenerator.addCell(table, dateRupture, alternate);
                    pdfGenerator.addCellRight(table, String.valueOf(p.getQuantiteSuggeree()), alternate);
                    pdfGenerator.addCell(table, p.getNiveauUrgence(), alternate);
                    alternate = !alternate;
                }

                document.add(table);
            } else {
                pdfGenerator.addParagraph(document, "Aucune prediction disponible.");
            }

            pdfGenerator.addFooter(document);
            document.close();

            logger.info("Rapport predictions genere: {}", filePath);
            return filePath;

        } catch (IOException e) {
            logger.error("Erreur lors de la generation du rapport predictions", e);
            throw new ServiceException("Erreur lors de la generation du rapport", e);
        }
    }

    // =====================================================
    // METHODES UTILITAIRES
    // =====================================================

    /**
     * Formate un montant pour affichage sur ticket.
     */
    private String formatMontantTicket(BigDecimal montant) {
        if (montant == null) return "0.00";
        return String.format("%.2f", montant);
    }

    /**
     * Recupere le nom du medicament pour une ligne de vente.
     */
    private String getNomMedicament(LigneVente ligne) {
        if (ligne.getLot() != null && ligne.getLot().getMedicament() != null) {
            return ligne.getLot().getMedicament().getNomCommercial();
        }
        try {
            if (ligne.getLot() != null) {
                Medicament med = medicamentDAO.findById(ligne.getLot().getIdMedicament()).orElse(null);
                if (med != null) {
                    return med.getNomCommercial();
                }
            }
        } catch (DAOException e) {
            logger.warn("Impossible de recuperer le nom du medicament", e);
        }
        return "Medicament #" + (ligne.getLot() != null ? ligne.getLot().getIdMedicament() : "?");
    }

    /**
     * Retourne le repertoire par defaut des rapports.
     *
     * @return le chemin du repertoire
     */
    public String getRepertoireRapports() {
        return PDFGenerator.getDefaultOutputDir();
    }
}
