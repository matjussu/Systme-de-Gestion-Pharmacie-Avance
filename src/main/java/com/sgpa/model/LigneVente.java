package com.sgpa.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Entite representant une ligne de vente.
 * <p>
 * Correspond a la table {@code ligne_ventes} de la base de donnees.
 * Chaque ligne est liee a un lot specifique pour assurer la tracabilite.
 * </p>
 *
 * @author SGPA Team
 * @version 1.0
 * @see Vente
 * @see Lot
 */
public class LigneVente {

    /** Identifiant unique de la ligne */
    private Integer idLigne;

    /** Reference a la vente parente */
    private Integer idVente;

    /** Reference au lot specifique (tracabilite) */
    private Integer idLot;

    /** Quantite vendue */
    private int quantite;

    /** Prix unitaire applique (peut differer du prix catalogue) */
    private BigDecimal prixUnitaireApplique;

    /** Reference vers le lot (pour jointures) */
    private Lot lot;

    /**
     * Constructeur par defaut.
     */
    public LigneVente() {
    }

    /**
     * Constructeur complet.
     *
     * @param idLigne             l'identifiant unique
     * @param idVente             l'ID de la vente
     * @param idLot               l'ID du lot
     * @param quantite            la quantite vendue
     * @param prixUnitaireApplique le prix unitaire applique
     */
    public LigneVente(Integer idLigne, Integer idVente, Integer idLot,
                      int quantite, BigDecimal prixUnitaireApplique) {
        this.idLigne = idLigne;
        this.idVente = idVente;
        this.idLot = idLot;
        this.quantite = quantite;
        this.prixUnitaireApplique = prixUnitaireApplique;
    }

    /**
     * Constructeur pour nouvelle ligne de vente.
     *
     * @param idLot               l'ID du lot
     * @param quantite            la quantite vendue
     * @param prixUnitaireApplique le prix unitaire applique
     */
    public LigneVente(Integer idLot, int quantite, BigDecimal prixUnitaireApplique) {
        this.idLot = idLot;
        this.quantite = quantite;
        this.prixUnitaireApplique = prixUnitaireApplique;
    }

    // Getters et Setters

    public Integer getIdLigne() {
        return idLigne;
    }

    public void setIdLigne(Integer idLigne) {
        this.idLigne = idLigne;
    }

    public Integer getIdVente() {
        return idVente;
    }

    public void setIdVente(Integer idVente) {
        this.idVente = idVente;
    }

    public Integer getIdLot() {
        return idLot;
    }

    public void setIdLot(Integer idLot) {
        this.idLot = idLot;
    }

    public int getQuantite() {
        return quantite;
    }

    public void setQuantite(int quantite) {
        this.quantite = quantite;
    }

    public BigDecimal getPrixUnitaireApplique() {
        return prixUnitaireApplique;
    }

    public void setPrixUnitaireApplique(BigDecimal prixUnitaireApplique) {
        this.prixUnitaireApplique = prixUnitaireApplique;
    }

    public Lot getLot() {
        return lot;
    }

    public void setLot(Lot lot) {
        this.lot = lot;
    }

    // Methodes metier

    /**
     * Calcule le montant de la ligne (quantite x prix unitaire).
     *
     * @return le montant de la ligne
     */
    public BigDecimal getMontantLigne() {
        if (prixUnitaireApplique == null) {
            return BigDecimal.ZERO;
        }
        return prixUnitaireApplique.multiply(BigDecimal.valueOf(quantite));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LigneVente that = (LigneVente) o;
        return Objects.equals(idLigne, that.idLigne);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idLigne);
    }

    @Override
    public String toString() {
        return "LigneVente{" +
               "idLigne=" + idLigne +
               ", idVente=" + idVente +
               ", idLot=" + idLot +
               ", quantite=" + quantite +
               ", prixUnitaireApplique=" + prixUnitaireApplique +
               ", montantLigne=" + getMontantLigne() +
               '}';
    }
}
