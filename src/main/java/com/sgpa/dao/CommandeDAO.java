package com.sgpa.dao;

import com.sgpa.exception.DAOException;
import com.sgpa.model.Commande;
import com.sgpa.model.LigneCommande;
import com.sgpa.model.enums.StatutCommande;

import java.util.List;

/**
 * Interface DAO pour les operations sur les commandes fournisseurs.
 *
 * @author SGPA Team
 * @version 1.0
 */
public interface CommandeDAO extends GenericDAO<Commande, Integer> {

    /**
     * Sauvegarde une ligne de commande.
     *
     * @param ligneCommande la ligne a sauvegarder
     * @return la ligne sauvegardee avec son ID
     * @throws DAOException si une erreur survient
     */
    LigneCommande saveLigneCommande(LigneCommande ligneCommande) throws DAOException;

    /**
     * Recupere les lignes d'une commande.
     *
     * @param idCommande l'ID de la commande
     * @return la liste des lignes
     * @throws DAOException si une erreur survient
     */
    List<LigneCommande> findLignesByCommandeId(int idCommande) throws DAOException;

    /**
     * Recherche les commandes par statut.
     *
     * @param statut le statut recherche
     * @return la liste des commandes
     * @throws DAOException si une erreur survient
     */
    List<Commande> findByStatut(StatutCommande statut) throws DAOException;

    /**
     * Recherche les commandes d'un fournisseur.
     *
     * @param idFournisseur l'ID du fournisseur
     * @return la liste des commandes
     * @throws DAOException si une erreur survient
     */
    List<Commande> findByFournisseur(int idFournisseur) throws DAOException;

    /**
     * Recherche les commandes contenant un medicament donne.
     *
     * @param idMedicament l'ID du medicament
     * @return la liste des commandes
     * @throws DAOException si une erreur survient
     */
    List<Commande> findByMedicament(int idMedicament) throws DAOException;
}
