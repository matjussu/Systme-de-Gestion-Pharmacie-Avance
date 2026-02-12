package com.sgpa.dto;

import java.math.BigDecimal;

/**
 * DTO pour la creation d'une ligne de vente.
 * Utilise pour passer les informations d'une ligne de vente au service.
 *
 * @author SGPA Team
 * @version 1.0
 */
public class LigneVenteDTO {

    private int idMedicament;
    private int quantite;
    private BigDecimal prixUnitaire;

    public LigneVenteDTO() {
    }

    public LigneVenteDTO(int idMedicament, int quantite) {
        this.idMedicament = idMedicament;
        this.quantite = quantite;
    }

    public LigneVenteDTO(int idMedicament, int quantite, BigDecimal prixUnitaire) {
        this.idMedicament = idMedicament;
        this.quantite = quantite;
        this.prixUnitaire = prixUnitaire;
    }

    public int getIdMedicament() {
        return idMedicament;
    }

    public void setIdMedicament(int idMedicament) {
        this.idMedicament = idMedicament;
    }

    public int getQuantite() {
        return quantite;
    }

    public void setQuantite(int quantite) {
        this.quantite = quantite;
    }

    public BigDecimal getPrixUnitaire() {
        return prixUnitaire;
    }

    public void setPrixUnitaire(BigDecimal prixUnitaire) {
        this.prixUnitaire = prixUnitaire;
    }

    /**
     * Calcule le montant de la ligne.
     */
    public BigDecimal getMontantNet() {
        if (prixUnitaire == null) return BigDecimal.ZERO;
        return prixUnitaire.multiply(BigDecimal.valueOf(quantite));
    }

    @Override
    public String toString() {
        return "LigneVenteDTO{" +
                "idMedicament=" + idMedicament +
                ", quantite=" + quantite +
                ", prixUnitaire=" + prixUnitaire +
                '}';
    }
}
