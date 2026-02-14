package com.sgpa.dao.impl;

import com.sgpa.dao.CommandeDAO;
import com.sgpa.exception.DAOException;
import com.sgpa.model.Commande;
import com.sgpa.model.LigneCommande;
import com.sgpa.model.enums.StatutCommande;
import com.sgpa.utils.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementation JDBC de l'interface {@link CommandeDAO}.
 *
 * @author SGPA Team
 * @version 1.0
 */
public class CommandeDAOImpl implements CommandeDAO {

    private static final Logger logger = LoggerFactory.getLogger(CommandeDAOImpl.class);

    private static final String SQL_FIND_BY_ID =
            "SELECT * FROM commandes WHERE id_commande = ?";

    private static final String SQL_FIND_ALL =
            "SELECT * FROM commandes ORDER BY date_creation DESC";

    private static final String SQL_INSERT =
            "INSERT INTO commandes (date_creation, date_reception, statut, id_fournisseur, notes) " +
            "VALUES (?, ?, ?, ?, ?)";

    private static final String SQL_UPDATE =
            "UPDATE commandes SET date_reception = ?, statut = ?, notes = ? WHERE id_commande = ?";

    private static final String SQL_DELETE =
            "DELETE FROM commandes WHERE id_commande = ?";

    private static final String SQL_COUNT =
            "SELECT COUNT(*) FROM commandes";

    private static final String SQL_EXISTS =
            "SELECT COUNT(*) FROM commandes WHERE id_commande = ?";

    private static final String SQL_INSERT_LIGNE =
            "INSERT INTO ligne_commandes (id_commande, id_medicament, quantite_commandee, quantite_recue, prix_unitaire) " +
            "VALUES (?, ?, ?, ?, ?)";

    private static final String SQL_FIND_LIGNES =
            "SELECT * FROM ligne_commandes WHERE id_commande = ?";

    private static final String SQL_FIND_BY_STATUT =
            "SELECT * FROM commandes WHERE statut = ? ORDER BY date_creation DESC";

    private static final String SQL_FIND_BY_FOURNISSEUR =
            "SELECT * FROM commandes WHERE id_fournisseur = ? ORDER BY date_creation DESC";

    private static final String SQL_FIND_BY_MEDICAMENT =
            "SELECT DISTINCT c.* FROM commandes c " +
            "INNER JOIN ligne_commandes lc ON c.id_commande = lc.id_commande " +
            "WHERE lc.id_medicament = ? ORDER BY c.date_creation DESC";

    @Override
    public Optional<Commande> findById(Integer id) throws DAOException {
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_ID)) {

            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Commande commande = mapResultSetToCommande(rs);
                    commande.setLignesCommande(findLignesByCommandeId(id));
                    return Optional.of(commande);
                }
            }
            return Optional.empty();

        } catch (SQLException e) {
            throw new DAOException("Erreur lors de la recherche de la commande", e);
        }
    }

    @Override
    public List<Commande> findAll() throws DAOException {
        List<Commande> commandes = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SQL_FIND_ALL)) {

            while (rs.next()) {
                commandes.add(mapResultSetToCommande(rs));
            }
            return commandes;

        } catch (SQLException e) {
            throw new DAOException("Erreur lors de la recherche des commandes", e);
        }
    }

    @Override
    public Commande save(Commande commande) throws DAOException {
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {

            ps.setTimestamp(1, Timestamp.valueOf(commande.getDateCreation()));

            if (commande.getDateReception() != null) {
                ps.setTimestamp(2, Timestamp.valueOf(commande.getDateReception()));
            } else {
                ps.setNull(2, Types.TIMESTAMP);
            }

            ps.setString(3, commande.getStatut().name());
            ps.setInt(4, commande.getIdFournisseur());
            ps.setString(5, commande.getNotes());

            int affectedRows = ps.executeUpdate();
            if (affectedRows == 0) {
                throw new DAOException("La creation de la commande a echoue");
            }

            try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    commande.setIdCommande(generatedKeys.getInt(1));
                }
            }

            return commande;

        } catch (SQLException e) {
            throw new DAOException("Erreur lors de la sauvegarde de la commande", e);
        }
    }

    @Override
    public void update(Commande commande) throws DAOException {
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_UPDATE)) {

            if (commande.getDateReception() != null) {
                ps.setTimestamp(1, Timestamp.valueOf(commande.getDateReception()));
            } else {
                ps.setNull(1, Types.TIMESTAMP);
            }

            ps.setString(2, commande.getStatut().name());
            ps.setString(3, commande.getNotes());
            ps.setInt(4, commande.getIdCommande());

            ps.executeUpdate();

        } catch (SQLException e) {
            throw new DAOException("Erreur lors de la mise a jour de la commande", e);
        }
    }

    @Override
    public void delete(Integer id) throws DAOException {
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_DELETE)) {

            ps.setInt(1, id);
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new DAOException("Erreur lors de la suppression de la commande", e);
        }
    }

    @Override
    public long count() throws DAOException {
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SQL_COUNT)) {

            if (rs.next()) {
                return rs.getLong(1);
            }
            return 0;

        } catch (SQLException e) {
            throw new DAOException("Erreur lors du comptage des commandes", e);
        }
    }

    @Override
    public boolean existsById(Integer id) throws DAOException {
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_EXISTS)) {

            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
            return false;

        } catch (SQLException e) {
            throw new DAOException("Erreur lors de la verification d'existence", e);
        }
    }

    @Override
    public LigneCommande saveLigneCommande(LigneCommande ligne) throws DAOException {
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT_LIGNE, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, ligne.getIdCommande());
            ps.setInt(2, ligne.getIdMedicament());
            ps.setInt(3, ligne.getQuantiteCommandee());
            ps.setInt(4, ligne.getQuantiteRecue());

            if (ligne.getPrixUnitaire() != null) {
                ps.setBigDecimal(5, ligne.getPrixUnitaire());
            } else {
                ps.setNull(5, Types.DECIMAL);
            }

            ps.executeUpdate();

            try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    ligne.setIdLigneCmd(generatedKeys.getInt(1));
                }
            }

            return ligne;

        } catch (SQLException e) {
            throw new DAOException("Erreur lors de la sauvegarde de la ligne de commande", e);
        }
    }

    @Override
    public List<LigneCommande> findLignesByCommandeId(int idCommande) throws DAOException {
        List<LigneCommande> lignes = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_LIGNES)) {

            ps.setInt(1, idCommande);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lignes.add(mapResultSetToLigneCommande(rs));
                }
            }
            return lignes;

        } catch (SQLException e) {
            throw new DAOException("Erreur lors de la recherche des lignes", e);
        }
    }

    @Override
    public List<Commande> findByStatut(StatutCommande statut) throws DAOException {
        List<Commande> commandes = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_STATUT)) {

            ps.setString(1, statut.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    commandes.add(mapResultSetToCommande(rs));
                }
            }
            return commandes;

        } catch (SQLException e) {
            throw new DAOException("Erreur lors de la recherche par statut", e);
        }
    }

    @Override
    public List<Commande> findByFournisseur(int idFournisseur) throws DAOException {
        List<Commande> commandes = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_FOURNISSEUR)) {

            ps.setInt(1, idFournisseur);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    commandes.add(mapResultSetToCommande(rs));
                }
            }
            return commandes;

        } catch (SQLException e) {
            throw new DAOException("Erreur lors de la recherche par fournisseur", e);
        }
    }

    @Override
    public List<Commande> findByMedicament(int idMedicament) throws DAOException {
        List<Commande> commandes = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_MEDICAMENT)) {

            ps.setInt(1, idMedicament);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    commandes.add(mapResultSetToCommande(rs));
                }
            }
            return commandes;

        } catch (SQLException e) {
            throw new DAOException("Erreur lors de la recherche par medicament", e);
        }
    }

    private Commande mapResultSetToCommande(ResultSet rs) throws SQLException {
        Commande commande = new Commande();
        commande.setIdCommande(rs.getInt("id_commande"));

        Timestamp dateCreation = rs.getTimestamp("date_creation");
        if (dateCreation != null) {
            commande.setDateCreation(dateCreation.toLocalDateTime());
        }

        Timestamp dateReception = rs.getTimestamp("date_reception");
        if (dateReception != null) {
            commande.setDateReception(dateReception.toLocalDateTime());
        }

        String statut = rs.getString("statut");
        if (statut != null) {
            commande.setStatut(StatutCommande.valueOf(statut));
        }

        commande.setIdFournisseur(rs.getInt("id_fournisseur"));
        commande.setNotes(rs.getString("notes"));

        return commande;
    }

    private LigneCommande mapResultSetToLigneCommande(ResultSet rs) throws SQLException {
        LigneCommande ligne = new LigneCommande();
        ligne.setIdLigneCmd(rs.getInt("id_ligne_cmd"));
        ligne.setIdCommande(rs.getInt("id_commande"));
        ligne.setIdMedicament(rs.getInt("id_medicament"));
        ligne.setQuantiteCommandee(rs.getInt("quantite_commandee"));
        ligne.setQuantiteRecue(rs.getInt("quantite_recue"));
        ligne.setPrixUnitaire(rs.getBigDecimal("prix_unitaire"));
        return ligne;
    }
}
