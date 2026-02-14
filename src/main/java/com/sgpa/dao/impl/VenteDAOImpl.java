package com.sgpa.dao.impl;

import com.sgpa.dao.VenteDAO;
import com.sgpa.exception.DAOException;
import com.sgpa.model.LigneVente;
import com.sgpa.model.Lot;
import com.sgpa.model.Medicament;
import com.sgpa.model.Vente;
import com.sgpa.utils.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementation JDBC de l'interface {@link VenteDAO}.
 *
 * @author SGPA Team
 * @version 1.0
 */
public class VenteDAOImpl implements VenteDAO {

    private static final Logger logger = LoggerFactory.getLogger(VenteDAOImpl.class);

    private static final String SQL_FIND_BY_ID =
            "SELECT * FROM ventes WHERE id_vente = ?";

    private static final String SQL_FIND_ALL =
            "SELECT * FROM ventes ORDER BY date_vente DESC";

    private static final String SQL_INSERT =
            "INSERT INTO ventes (date_vente, montant_total, est_sur_ordonnance, numero_ordonnance, id_utilisateur, notes) " +
            "VALUES (?, ?, ?, ?, ?, ?)";

    private static final String SQL_UPDATE =
            "UPDATE ventes SET montant_total = ?, est_sur_ordonnance = ?, numero_ordonnance = ?, notes = ? " +
            "WHERE id_vente = ?";

    private static final String SQL_DELETE =
            "DELETE FROM ventes WHERE id_vente = ?";

    private static final String SQL_COUNT =
            "SELECT COUNT(*) FROM ventes";

    private static final String SQL_EXISTS =
            "SELECT COUNT(*) FROM ventes WHERE id_vente = ?";

    private static final String SQL_INSERT_LIGNE =
            "INSERT INTO ligne_ventes (id_vente, id_lot, quantite, prix_unitaire_applique) " +
            "VALUES (?, ?, ?, ?)";

    private static final String SQL_FIND_LIGNES_BY_VENTE =
            "SELECT lv.*, l.numero_lot, m.nom_commercial FROM ligne_ventes lv " +
            "LEFT JOIN lots l ON lv.id_lot = l.id_lot " +
            "LEFT JOIN medicaments m ON l.id_medicament = m.id_medicament " +
            "WHERE lv.id_vente = ?";

    private static final String SQL_FIND_BY_DATE =
            "SELECT * FROM ventes WHERE DATE(date_vente) = ? ORDER BY date_vente DESC";

    private static final String SQL_FIND_BY_DATE_RANGE =
            "SELECT * FROM ventes WHERE DATE(date_vente) BETWEEN ? AND ? ORDER BY date_vente DESC";

    private static final String SQL_FIND_BY_UTILISATEUR =
            "SELECT * FROM ventes WHERE id_utilisateur = ? ORDER BY date_vente DESC";

    private static final String SQL_FIND_SUR_ORDONNANCE =
            "SELECT * FROM ventes WHERE est_sur_ordonnance = TRUE ORDER BY date_vente DESC";

    @Override
    public Optional<Vente> findById(Integer id) throws DAOException {
        logger.debug("Recherche vente par ID: {}", id);

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_ID)) {

            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Vente vente = mapResultSetToVente(rs);
                    vente.setLignesVente(findLignesByVenteId(id));
                    return Optional.of(vente);
                }
            }
            return Optional.empty();

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche de la vente ID: {}", id, e);
            throw new DAOException("Erreur lors de la recherche de la vente", e);
        }
    }

    @Override
    public List<Vente> findAll() throws DAOException {
        logger.debug("Recherche de toutes les ventes");
        List<Vente> ventes = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SQL_FIND_ALL)) {

            while (rs.next()) {
                ventes.add(mapResultSetToVente(rs));
            }
            return ventes;

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche de toutes les ventes", e);
            throw new DAOException("Erreur lors de la recherche des ventes", e);
        }
    }

    @Override
    public Vente save(Vente vente) throws DAOException {
        logger.debug("Sauvegarde d'une nouvelle vente");

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {

            ps.setTimestamp(1, Timestamp.valueOf(vente.getDateVente()));
            ps.setBigDecimal(2, vente.getMontantTotal());
            ps.setBoolean(3, vente.isEstSurOrdonnance());
            ps.setString(4, vente.getNumeroOrdonnance());

            if (vente.getIdUtilisateur() != null) {
                ps.setInt(5, vente.getIdUtilisateur());
            } else {
                ps.setNull(5, Types.INTEGER);
            }

            ps.setString(6, vente.getNotes());

            int affectedRows = ps.executeUpdate();
            if (affectedRows == 0) {
                throw new DAOException("La creation de la vente a echoue");
            }

            try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    vente.setIdVente(generatedKeys.getInt(1));
                    logger.info("Vente creee avec ID: {}", vente.getIdVente());
                }
            }

            return vente;

        } catch (SQLException e) {
            logger.error("Erreur lors de la sauvegarde de la vente", e);
            throw new DAOException("Erreur lors de la sauvegarde de la vente", e);
        }
    }

    @Override
    public void update(Vente vente) throws DAOException {
        logger.debug("Mise a jour de la vente ID: {}", vente.getIdVente());

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_UPDATE)) {

            ps.setBigDecimal(1, vente.getMontantTotal());
            ps.setBoolean(2, vente.isEstSurOrdonnance());
            ps.setString(3, vente.getNumeroOrdonnance());
            ps.setString(4, vente.getNotes());
            ps.setInt(5, vente.getIdVente());

            int affectedRows = ps.executeUpdate();
            if (affectedRows == 0) {
                throw new DAOException("Vente non trouvee pour mise a jour");
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la mise a jour de la vente", e);
            throw new DAOException("Erreur lors de la mise a jour de la vente", e);
        }
    }

    @Override
    public void delete(Integer id) throws DAOException {
        logger.debug("Suppression de la vente ID: {}", id);

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_DELETE)) {

            ps.setInt(1, id);
            int affectedRows = ps.executeUpdate();
            if (affectedRows == 0) {
                throw new DAOException("Vente non trouvee pour suppression");
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la suppression de la vente", e);
            throw new DAOException("Erreur lors de la suppression de la vente", e);
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
            throw new DAOException("Erreur lors du comptage des ventes", e);
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
    public LigneVente saveLigneVente(LigneVente ligneVente) throws DAOException {
        logger.debug("Sauvegarde d'une ligne de vente");

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT_LIGNE, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, ligneVente.getIdVente());
            ps.setInt(2, ligneVente.getIdLot());
            ps.setInt(3, ligneVente.getQuantite());
            ps.setBigDecimal(4, ligneVente.getPrixUnitaireApplique());

            int affectedRows = ps.executeUpdate();
            if (affectedRows == 0) {
                throw new DAOException("La creation de la ligne de vente a echoue");
            }

            try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    ligneVente.setIdLigne(generatedKeys.getInt(1));
                }
            }

            return ligneVente;

        } catch (SQLException e) {
            logger.error("Erreur lors de la sauvegarde de la ligne de vente", e);
            throw new DAOException("Erreur lors de la sauvegarde de la ligne de vente", e);
        }
    }

    @Override
    public List<LigneVente> findLignesByVenteId(int idVente) throws DAOException {
        List<LigneVente> lignes = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_LIGNES_BY_VENTE)) {

            ps.setInt(1, idVente);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lignes.add(mapResultSetToLigneVente(rs));
                }
            }
            return lignes;

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche des lignes de vente", e);
            throw new DAOException("Erreur lors de la recherche des lignes de vente", e);
        }
    }

    @Override
    public List<Vente> findByDate(LocalDate date) throws DAOException {
        List<Vente> ventes = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_DATE)) {

            ps.setDate(1, Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ventes.add(mapResultSetToVente(rs));
                }
            }
            return ventes;

        } catch (SQLException e) {
            throw new DAOException("Erreur lors de la recherche par date", e);
        }
    }

    @Override
    public List<Vente> findByDateRange(LocalDate dateDebut, LocalDate dateFin) throws DAOException {
        List<Vente> ventes = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_DATE_RANGE)) {

            ps.setDate(1, Date.valueOf(dateDebut));
            ps.setDate(2, Date.valueOf(dateFin));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ventes.add(mapResultSetToVente(rs));
                }
            }
            return ventes;

        } catch (SQLException e) {
            throw new DAOException("Erreur lors de la recherche par periode", e);
        }
    }

    @Override
    public List<Vente> findByUtilisateur(int idUtilisateur) throws DAOException {
        List<Vente> ventes = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_UTILISATEUR)) {

            ps.setInt(1, idUtilisateur);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ventes.add(mapResultSetToVente(rs));
                }
            }
            return ventes;

        } catch (SQLException e) {
            throw new DAOException("Erreur lors de la recherche par utilisateur", e);
        }
    }

    @Override
    public List<Vente> findVentesSurOrdonnance() throws DAOException {
        List<Vente> ventes = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SQL_FIND_SUR_ORDONNANCE)) {

            while (rs.next()) {
                ventes.add(mapResultSetToVente(rs));
            }
            return ventes;

        } catch (SQLException e) {
            throw new DAOException("Erreur lors de la recherche des ventes sur ordonnance", e);
        }
    }

    private Vente mapResultSetToVente(ResultSet rs) throws SQLException {
        Vente vente = new Vente();
        vente.setIdVente(rs.getInt("id_vente"));

        Timestamp dateVente = rs.getTimestamp("date_vente");
        if (dateVente != null) {
            vente.setDateVente(dateVente.toLocalDateTime());
        }

        vente.setMontantTotal(rs.getBigDecimal("montant_total"));
        vente.setEstSurOrdonnance(rs.getBoolean("est_sur_ordonnance"));
        vente.setNumeroOrdonnance(rs.getString("numero_ordonnance"));

        int idUtilisateur = rs.getInt("id_utilisateur");
        if (!rs.wasNull()) {
            vente.setIdUtilisateur(idUtilisateur);
        }

        vente.setNotes(rs.getString("notes"));

        return vente;
    }

    private LigneVente mapResultSetToLigneVente(ResultSet rs) throws SQLException {
        LigneVente ligne = new LigneVente();
        ligne.setIdLigne(rs.getInt("id_ligne"));
        ligne.setIdVente(rs.getInt("id_vente"));
        ligne.setIdLot(rs.getInt("id_lot"));
        ligne.setQuantite(rs.getInt("quantite"));
        ligne.setPrixUnitaireApplique(rs.getBigDecimal("prix_unitaire_applique"));

        Lot lot = new Lot();
        lot.setNumeroLot(rs.getString("numero_lot"));
        Medicament med = new Medicament();
        med.setNomCommercial(rs.getString("nom_commercial"));
        lot.setMedicament(med);
        ligne.setLot(lot);

        return ligne;
    }
}
