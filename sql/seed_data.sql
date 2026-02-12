-- =============================================================================
-- SGPA / ApotiCare - Donnees de demonstration (seed data)
-- =============================================================================
-- A executer APRES schema.sql :
--   mysql -u sgpa -p sgpa_pharmacie < sql/schema.sql
--   mysql -u sgpa -p sgpa_pharmacie < sql/seed_data.sql
--
-- Ce script est IDEMPOTENT : il peut etre relance sans erreur.
-- =============================================================================

USE sgpa_pharmacie;

-- =============================================================================
-- SECTION 0 : Nettoyage (idempotence)
-- =============================================================================
SET FOREIGN_KEY_CHECKS = 0;

-- Tables transactionnelles uniquement
TRUNCATE TABLE regularisations;
TRUNCATE TABLE comptages_inventaire;
TRUNCATE TABLE sessions_inventaire;
TRUNCATE TABLE retours;
TRUNCATE TABLE audit_log;
TRUNCATE TABLE ligne_commandes;
TRUNCATE TABLE commandes;
TRUNCATE TABLE ligne_ventes;
TRUNCATE TABLE ventes;

-- Supprimer les lots et medicaments ajoutes par ce script (IDs >= 13 / >= 9)
DELETE FROM lots WHERE id_lot >= 13;
DELETE FROM medicaments WHERE id_medicament >= 9;

-- Restaurer le stock original des lots (tel que defini dans schema.sql)
UPDATE lots SET quantite_stock = 50  WHERE id_lot = 1;
UPDATE lots SET quantite_stock = 100 WHERE id_lot = 2;
UPDATE lots SET quantite_stock = 25  WHERE id_lot = 3;
UPDATE lots SET quantite_stock = 40  WHERE id_lot = 4;
UPDATE lots SET quantite_stock = 30  WHERE id_lot = 5;
UPDATE lots SET quantite_stock = 60  WHERE id_lot = 6;
UPDATE lots SET quantite_stock = 15  WHERE id_lot = 7;
UPDATE lots SET quantite_stock = 20  WHERE id_lot = 8;
UPDATE lots SET quantite_stock = 45  WHERE id_lot = 9;
UPDATE lots SET quantite_stock = 70  WHERE id_lot = 10;
UPDATE lots SET quantite_stock = 35  WHERE id_lot = 11;
UPDATE lots SET quantite_stock = 55  WHERE id_lot = 12;

SET FOREIGN_KEY_CHECKS = 1;


-- =============================================================================
-- SECTION 1 : Medicaments et lots supplementaires
-- =============================================================================

-- Nouveaux medicaments pour declencher les alertes de stock bas
INSERT INTO medicaments (id_medicament, nom_commercial, principe_actif, forme_galenique, dosage, prix_public, necessite_ordonnance, seuil_min, description, actif) VALUES
(9,  'Omeprazole Biogaran', 'Omeprazole',  'Gelule',            '20mg',       4.20, FALSE, 10, 'Inhibiteur de la pompe a protons - Traitement des reflux gastro-oesophagiens', TRUE),
(10, 'Cetirizine Mylan',    'Cetirizine',   'Comprime pellicule', '10mg',       3.80, FALSE, 8,  'Antihistaminique H1 - Traitement des allergies', TRUE),
(11, 'Ibuprofene Enfant',   'Ibuprofene',   'Suspension buvable', '100mg/5ml',  2.90, FALSE, 10, 'Anti-inflammatoire non steroidien - Usage pediatrique', TRUE);

-- Lots supplementaires
INSERT INTO lots (id_lot, id_medicament, id_fournisseur, numero_lot, date_peremption, date_fabrication, quantite_stock, prix_achat) VALUES
-- Lots PERIMES (pour alerte "Lots perimes")
(13, 1, 1, 'DOL2023001', DATE_SUB(CURDATE(), INTERVAL 7 DAY),  DATE_SUB(CURDATE(), INTERVAL 18 MONTH), 8, 1.15),
(14, 2, 2, 'ADV2023001', DATE_SUB(CURDATE(), INTERVAL 15 DAY), DATE_SUB(CURDATE(), INTERVAL 20 MONTH), 5, 2.00),
-- Lots avec stock bas (pour alerte "Stock bas")
(15, 9,  1, 'OME2024001', DATE_ADD(CURDATE(), INTERVAL 12 MONTH), DATE_SUB(CURDATE(), INTERVAL 2 MONTH), 3, 2.10),
(16, 10, 3, 'CET2024001', DATE_ADD(CURDATE(), INTERVAL 8 MONTH),  DATE_SUB(CURDATE(), INTERVAL 4 MONTH), 5, 1.90);
-- Ibuprofene Enfant (id 11) n'a aucun lot → stock 0 < seuil_min 10 → alerte stock bas


-- =============================================================================
-- SECTION 2 : Ventes (43 ventes sur 90 jours)
-- =============================================================================
-- Mapping lots → medicaments :
--   Lots 1,2,3  → Med 1 (Doliprane 2.50)    FEFO: lot3(+2m) → lot1(+6m) → lot2(+12m)
--   Lots 4,5    → Med 2 (Advil 4.50)         FEFO: lot5(+4m) → lot4(+8m)
--   Lots 6,7    → Med 3 (Amoxicilline 6.80)  FEFO: lot7(+1m) → lot6(+10m)
--   Lot 8       → Med 4 (Ventoline 3.50)
--   Lot 9       → Med 5 (Levothyrox 2.30)
--   Lot 10      → Med 6 (Spasfon 3.20)
--   Lot 11      → Med 7 (Gaviscon 5.90)
--   Lot 12      → Med 8 (Kardegic 2.80)
-- =============================================================================

INSERT INTO ventes (id_vente, date_vente, montant_total, est_sur_ordonnance, numero_ordonnance, id_utilisateur, notes) VALUES
-- === Jours -90 a -61 (12 ventes) ===
(1,  CONCAT(DATE_SUB(CURDATE(), INTERVAL 89 DAY), ' 09:15:00'), 10.70, FALSE, NULL,             1, NULL),
(2,  CONCAT(DATE_SUB(CURDATE(), INTERVAL 87 DAY), ' 10:30:00'),  9.10, TRUE,  'ORD-2025-001',   2, NULL),
(3,  CONCAT(DATE_SUB(CURDATE(), INTERVAL 84 DAY), ' 14:00:00'),  9.50, FALSE, NULL,             1, NULL),
(4,  CONCAT(DATE_SUB(CURDATE(), INTERVAL 81 DAY), ' 11:45:00'), 13.60, TRUE,  'ORD-2025-002',   2, 'Patient regulier'),
(5,  CONCAT(DATE_SUB(CURDATE(), INTERVAL 78 DAY), ' 09:30:00'),  7.50, FALSE, NULL,             1, NULL),
(6,  CONCAT(DATE_SUB(CURDATE(), INTERVAL 75 DAY), ' 15:00:00'),  9.00, FALSE, NULL,             2, NULL),
(7,  CONCAT(DATE_SUB(CURDATE(), INTERVAL 73 DAY), ' 10:15:00'), 12.40, TRUE,  'ORD-2025-003',   1, NULL),
(8,  CONCAT(DATE_SUB(CURDATE(), INTERVAL 71 DAY), ' 16:30:00'),  5.00, FALSE, NULL,             2, NULL),
(9,  CONCAT(DATE_SUB(CURDATE(), INTERVAL 68 DAY), ' 09:00:00'), 11.80, FALSE, NULL,             1, NULL),
(10, CONCAT(DATE_SUB(CURDATE(), INTERVAL 65 DAY), ' 11:00:00'),  6.80, TRUE,  'ORD-2025-004',   2, NULL),
(11, CONCAT(DATE_SUB(CURDATE(), INTERVAL 63 DAY), ' 14:30:00'),  7.00, FALSE, NULL,             1, NULL),
(12, CONCAT(DATE_SUB(CURDATE(), INTERVAL 61 DAY), ' 10:00:00'), 14.10, FALSE, NULL,             2, 'Achat familial'),
-- === Jours -60 a -31 (12 ventes) ===
(13, CONCAT(DATE_SUB(CURDATE(), INTERVAL 58 DAY), ' 09:45:00'), 10.00, FALSE, NULL,             1, NULL),
(14, CONCAT(DATE_SUB(CURDATE(), INTERVAL 55 DAY), ' 13:00:00'), 13.60, TRUE,  'ORD-2025-005',   2, NULL),
(15, CONCAT(DATE_SUB(CURDATE(), INTERVAL 53 DAY), ' 10:30:00'),  9.20, FALSE, NULL,             1, NULL),
(16, CONCAT(DATE_SUB(CURDATE(), INTERVAL 50 DAY), ' 15:15:00'),  7.40, TRUE,  'ORD-2025-006',   1, NULL),
(17, CONCAT(DATE_SUB(CURDATE(), INTERVAL 47 DAY), ' 11:00:00'), 12.30, FALSE, NULL,             2, NULL),
(18, CONCAT(DATE_SUB(CURDATE(), INTERVAL 46 DAY), ' 09:30:00'),  9.50, FALSE, NULL,             1, NULL),
(19, CONCAT(DATE_SUB(CURDATE(), INTERVAL 43 DAY), ' 10:00:00'), 16.50, FALSE, NULL,             2, NULL),
(20, CONCAT(DATE_SUB(CURDATE(), INTERVAL 40 DAY), ' 14:15:00'),  9.60, TRUE,  'ORD-2025-007',   1, NULL),
(21, CONCAT(DATE_SUB(CURDATE(), INTERVAL 38 DAY), ' 11:30:00'),  7.50, FALSE, NULL,             2, NULL),
(22, CONCAT(DATE_SUB(CURDATE(), INTERVAL 36 DAY), ' 09:00:00'), 20.00, FALSE, NULL,             1, 'Grande commande'),
(23, CONCAT(DATE_SUB(CURDATE(), INTERVAL 33 DAY), ' 15:45:00'),  9.10, TRUE,  'ORD-2025-008',   2, NULL),
(24, CONCAT(DATE_SUB(CURDATE(), INTERVAL 31 DAY), ' 10:00:00'),  5.00, FALSE, NULL,             1, NULL),
-- === Jours -30 a -1 (16 ventes) ===
(25, CONCAT(DATE_SUB(CURDATE(), INTERVAL 28 DAY), ' 09:15:00'), 14.20, FALSE, NULL,             2, NULL),
(26, CONCAT(DATE_SUB(CURDATE(), INTERVAL 26 DAY), ' 11:00:00'),  9.60, TRUE,  'ORD-2025-009',   1, NULL),
(27, CONCAT(DATE_SUB(CURDATE(), INTERVAL 24 DAY), ' 14:30:00'),  7.50, FALSE, NULL,             2, NULL),
(28, CONCAT(DATE_SUB(CURDATE(), INTERVAL 22 DAY), ' 10:00:00'), 17.70, FALSE, NULL,             1, NULL),
(29, CONCAT(DATE_SUB(CURDATE(), INTERVAL 20 DAY), ' 15:00:00'), 13.60, TRUE,  'ORD-2025-010',   2, NULL),
(30, CONCAT(DATE_SUB(CURDATE(), INTERVAL 18 DAY), ' 09:30:00'),  8.70, FALSE, NULL,             1, NULL),
(31, CONCAT(DATE_SUB(CURDATE(), INTERVAL 17 DAY), ' 11:15:00'),  5.60, FALSE, NULL,             2, NULL),
(32, CONCAT(DATE_SUB(CURDATE(), INTERVAL 16 DAY), ' 13:45:00'),  2.50, FALSE, NULL,             1, NULL),
(33, CONCAT(DATE_SUB(CURDATE(), INTERVAL 16 DAY), ' 16:00:00'), 10.30, TRUE,  'ORD-2025-011',   2, NULL),
(34, CONCAT(DATE_SUB(CURDATE(), INTERVAL 13 DAY), ' 09:00:00'), 15.90, FALSE, NULL,             1, NULL),
(35, CONCAT(DATE_SUB(CURDATE(), INTERVAL 10 DAY), ' 10:30:00'),  7.00, TRUE,  'ORD-2025-012',   2, NULL),
(36, CONCAT(DATE_SUB(CURDATE(), INTERVAL 8 DAY),  ' 14:00:00'), 13.40, FALSE, NULL,             1, NULL),
(37, CONCAT(DATE_SUB(CURDATE(), INTERVAL 5 DAY),  ' 11:00:00'), 13.50, FALSE, NULL,             2, NULL),
(38, CONCAT(DATE_SUB(CURDATE(), INTERVAL 3 DAY),  ' 09:45:00'), 18.20, TRUE,  'ORD-2025-013',   1, 'Ordonnance trimestrielle'),
(39, CONCAT(DATE_SUB(CURDATE(), INTERVAL 2 DAY),  ' 15:30:00'),  6.40, FALSE, NULL,             2, NULL),
(40, CONCAT(DATE_SUB(CURDATE(), INTERVAL 1 DAY),  ' 10:00:00'),  7.80, FALSE, NULL,             1, NULL),
-- === Aujourd'hui (3 ventes) ===
(41, CONCAT(CURDATE(), ' 09:15:00'),  7.50, FALSE, NULL,             1, NULL),
(42, CONCAT(CURDATE(), ' 10:30:00'), 13.60, TRUE,  'ORD-2025-014',   2, NULL),
(43, CONCAT(CURDATE(), ' 11:45:00'),  5.00, FALSE, NULL,             1, 'Vente rapide');


-- =============================================================================
-- Lignes de vente (72 lignes)
-- Chaque ligne reference un lot qui contient le bon medicament.
-- montant_total de chaque vente = SUM(quantite * prix_unitaire_applique)
-- =============================================================================

INSERT INTO ligne_ventes (id_vente, id_lot, quantite, prix_unitaire_applique) VALUES
-- Vente 1 (10.70) : Doliprane x3 + Spasfon x1
(1,  3,  3, 2.50),
(1,  10, 1, 3.20),
-- Vente 2 (9.10) : Amoxicilline x1 + Levothyrox x1 [ordonnance]
(2,  7,  1, 6.80),
(2,  9,  1, 2.30),
-- Vente 3 (9.50) : Doliprane x2 + Advil x1
(3,  3,  2, 2.50),
(3,  5,  1, 4.50),
-- Vente 4 (13.60) : Amoxicilline x2 [ordonnance]
(4,  7,  2, 6.80),
-- Vente 5 (7.50) : Doliprane x3
(5,  3,  3, 2.50),
-- Vente 6 (9.00) : Advil x2
(6,  5,  2, 4.50),
-- Vente 7 (12.40) : Amoxicilline x1 + Kardegic x2 [ordonnance]
(7,  7,  1, 6.80),
(7,  12, 2, 2.80),
-- Vente 8 (5.00) : Doliprane x2
(8,  3,  2, 2.50),
-- Vente 9 (11.80) : Gaviscon x2
(9,  11, 2, 5.90),
-- Vente 10 (6.80) : Amoxicilline x1 [ordonnance]
(10, 7,  1, 6.80),
-- Vente 11 (7.00) : Ventoline x2
(11, 8,  2, 3.50),
-- Vente 12 (14.10) : Doliprane x2 + Gaviscon x1 + Spasfon x1
(12, 3,  2, 2.50),
(12, 11, 1, 5.90),
(12, 10, 1, 3.20),
-- Vente 13 (10.00) : Doliprane x2 (lot3) + Doliprane x2 (lot1)
-- Lot 3 atteint sa cible de 14 vendus, bascule vers lot 1
(13, 3,  2, 2.50),
(13, 1,  2, 2.50),
-- Vente 14 (13.60) : Advil x2 + Levothyrox x2 [ordonnance]
(14, 5,  2, 4.50),
(14, 9,  2, 2.30),
-- Vente 15 (9.20) : Spasfon x2 + Kardegic x1
(15, 10, 2, 3.20),
(15, 12, 1, 2.80),
-- Vente 16 (7.40) : Levothyrox x2 + Kardegic x1 [ordonnance]
(16, 9,  2, 2.30),
(16, 12, 1, 2.80),
-- Vente 17 (12.30) : Spasfon x2 + Gaviscon x1
(17, 10, 2, 3.20),
(17, 11, 1, 5.90),
-- Vente 18 (9.50) : Advil x1 + Doliprane x2 (lot1)
(18, 5,  1, 4.50),
(18, 1,  2, 2.50),
-- Vente 19 (16.50) : Doliprane x3 (lot1) + Advil x2
(19, 1,  3, 2.50),
(19, 5,  2, 4.50),
-- Vente 20 (9.60) : Amoxicilline x1 (lot6) + Kardegic x1 [ordonnance]
(20, 6,  1, 6.80),
(20, 12, 1, 2.80),
-- Vente 21 (7.50) : Doliprane x3 (lot1)
(21, 1,  3, 2.50),
-- Vente 22 (20.00) : Spasfon x3 + Gaviscon x1 + Advil x1
(22, 10, 3, 3.20),
(22, 11, 1, 5.90),
(22, 5,  1, 4.50),
-- Vente 23 (9.10) : Amoxicilline x1 (lot7) + Levothyrox x1 [ordonnance]
(23, 7,  1, 6.80),
(23, 9,  1, 2.30),
-- Vente 24 (5.00) : Doliprane x2 (lot1)
(24, 1,  2, 2.50),
-- Vente 25 (14.20) : Doliprane x2 (lot1) + Spasfon x2 + Kardegic x1
(25, 1,  2, 2.50),
(25, 10, 2, 3.20),
(25, 12, 1, 2.80),
-- Vente 26 (9.60) : Amoxicilline x1 (lot6) + Kardegic x1 [ordonnance]
(26, 6,  1, 6.80),
(26, 12, 1, 2.80),
-- Vente 27 (7.50) : Doliprane x3 (lot1)
(27, 1,  3, 2.50),
-- Vente 28 (17.70) : Advil x2 + Gaviscon x1 + Kardegic x1
(28, 5,  2, 4.50),
(28, 11, 1, 5.90),
(28, 12, 1, 2.80),
-- Vente 29 (13.60) : Amoxicilline x2 (lot6) [ordonnance]
(29, 6,  2, 6.80),
-- Vente 30 (8.70) : Spasfon x2 + Levothyrox x1
(30, 10, 2, 3.20),
(30, 9,  1, 2.30),
-- Vente 31 (5.60) : Kardegic x2
(31, 12, 2, 2.80),
-- Vente 32 (2.50) : Doliprane x1 (lot1)
(32, 1,  1, 2.50),
-- Vente 33 (10.30) : Amoxicilline x1 (lot6) + Ventoline x1 [ordonnance]
(33, 6,  1, 6.80),
(33, 8,  1, 3.50),
-- Vente 34 (15.90) : Doliprane x2 (lot1) + Spasfon x2 + Advil x1
(34, 1,  2, 2.50),
(34, 10, 2, 3.20),
(34, 5,  1, 4.50),
-- Vente 35 (7.00) : Ventoline x2 [ordonnance]
(35, 8,  2, 3.50),
-- Vente 36 (13.40) : Doliprane x3 (lot2) + Gaviscon x1
(36, 2,  3, 2.50),
(36, 11, 1, 5.90),
-- Vente 37 (13.50) : Advil x3
(37, 5,  3, 4.50),
-- Vente 38 (18.20) : Amoxicilline x2 (lot6) + Levothyrox x2 [ordonnance]
(38, 6,  2, 6.80),
(38, 9,  2, 2.30),
-- Vente 39 (6.40) : Spasfon x2
(39, 10, 2, 3.20),
-- Vente 40 (7.80) : Doliprane x2 (lot2) + Kardegic x1
(40, 2,  2, 2.50),
(40, 12, 1, 2.80),
-- Vente 41 (7.50) : Doliprane x3 (lot2) [aujourd'hui]
(41, 2,  3, 2.50),
-- Vente 42 (13.60) : Amoxicilline x2 (lot6) [aujourd'hui, ordonnance]
(42, 6,  2, 6.80),
-- Vente 43 (5.00) : Doliprane x2 (lot2) [aujourd'hui]
(43, 2,  2, 2.50);


-- =============================================================================
-- SECTION 3 : Mise a jour du stock (ventes + retours + inventaire)
-- =============================================================================
-- Lot 1  (Doliprane)    : 50  - 20 vendu            - 2 inventaire(casse)  = 28
-- Lot 2  (Doliprane)    : 100 - 10 vendu                                   = 90
-- Lot 3  (Doliprane)    : 25  - 14 vendu  + 1 retour                       = 12
-- Lot 4  (Advil)        : 40  - 0  vendu                                   = 40 (inchange)
-- Lot 5  (Advil)        : 30  - 15 vendu  + 1 retour                       = 16
-- Lot 6  (Amoxicilline) : 60  - 9  vendu             - 1 inventaire(perte) = 50
-- Lot 7  (Amoxicilline) : 15  - 6  vendu                                   = 9
-- Lot 8  (Ventoline)    : 20  - 5  vendu                                   = 15
-- Lot 9  (Levothyrox)   : 45  - 9  vendu                                   = 36
-- Lot 10 (Spasfon)      : 70  - 17 vendu                                   = 53
-- Lot 11 (Gaviscon)     : 35  - 7  vendu             - 1 inventaire(ajust) = 27
-- Lot 12 (Kardegic)     : 55  - 11 vendu                                   = 44

UPDATE lots SET quantite_stock = 28 WHERE id_lot = 1;
UPDATE lots SET quantite_stock = 90 WHERE id_lot = 2;
UPDATE lots SET quantite_stock = 12 WHERE id_lot = 3;
-- Lot 4 inchange (40)
UPDATE lots SET quantite_stock = 16 WHERE id_lot = 5;
UPDATE lots SET quantite_stock = 50 WHERE id_lot = 6;
UPDATE lots SET quantite_stock = 9  WHERE id_lot = 7;
UPDATE lots SET quantite_stock = 15 WHERE id_lot = 8;
UPDATE lots SET quantite_stock = 36 WHERE id_lot = 9;
UPDATE lots SET quantite_stock = 53 WHERE id_lot = 10;
UPDATE lots SET quantite_stock = 27 WHERE id_lot = 11;
UPDATE lots SET quantite_stock = 44 WHERE id_lot = 12;


-- =============================================================================
-- SECTION 4 : Commandes fournisseurs (5 commandes, 11 lignes)
-- =============================================================================

INSERT INTO commandes (id_commande, date_creation, date_reception, statut, id_fournisseur, notes) VALUES
(1, CONCAT(DATE_SUB(CURDATE(), INTERVAL 60 DAY), ' 08:30:00'), CONCAT(DATE_SUB(CURDATE(), INTERVAL 55 DAY), ' 10:00:00'), 'RECUE',      1, 'Reapprovisionnement mensuel Doliprane et Advil'),
(2, CONCAT(DATE_SUB(CURDATE(), INTERVAL 30 DAY), ' 09:00:00'), CONCAT(DATE_SUB(CURDATE(), INTERVAL 25 DAY), ' 14:30:00'), 'RECUE',      2, 'Commande Ventoline et Gaviscon'),
(3, CONCAT(DATE_SUB(CURDATE(), INTERVAL 7 DAY),  ' 10:15:00'), NULL,                                                       'EN_ATTENTE', 3, 'Commande urgente Amoxicilline et Spasfon'),
(4, CONCAT(DATE_SUB(CURDATE(), INTERVAL 2 DAY),  ' 11:00:00'), NULL,                                                       'EN_ATTENTE', 1, 'Reapprovisionnement Doliprane, Kardegic et Levothyrox'),
(5, CONCAT(DATE_SUB(CURDATE(), INTERVAL 45 DAY), ' 09:30:00'), NULL,                                                       'ANNULEE',    2, 'Annulee - doublon avec commande #2');

INSERT INTO ligne_commandes (id_ligne_cmd, id_commande, id_medicament, quantite_commandee, quantite_recue, prix_unitaire) VALUES
-- Commande 1 (RECUE) : Doliprane + Advil
(1,  1, 1, 100, 100, 1.20),
(2,  1, 2,  50,  50, 2.10),
-- Commande 2 (RECUE) : Ventoline + Gaviscon
(3,  2, 4,  30,  30, 1.80),
(4,  2, 7,  40,  40, 3.00),
-- Commande 3 (EN_ATTENTE) : Amoxicilline + Spasfon
(5,  3, 3,  80,   0, 3.50),
(6,  3, 6,  50,   0, 1.60),
-- Commande 4 (EN_ATTENTE) : Doliprane + Kardegic + Levothyrox
(7,  4, 1, 150,   0, 1.25),
(8,  4, 8,  60,   0, 1.40),
(9,  4, 5,  40,   0, 1.10),
-- Commande 5 (ANNULEE) : Ventoline + Gaviscon (doublon)
(10, 5, 4,  30,   0, 1.80),
(11, 5, 7,  40,   0, 3.00);


-- =============================================================================
-- SECTION 5 : Journal d'audit (20 entrees)
-- =============================================================================

INSERT INTO audit_log (date_action, id_utilisateur, nom_utilisateur, type_action, entite, id_entite, description, details_json, adresse_ip) VALUES
-- Connexions / Deconnexions
(CONCAT(DATE_SUB(CURDATE(), INTERVAL 30 DAY), ' 08:00:00'), 1, 'Administrateur',    'CONNEXION',    'UTILISATEUR', 1, 'Connexion reussie',    NULL, '127.0.0.1'),
(CONCAT(DATE_SUB(CURDATE(), INTERVAL 30 DAY), ' 18:00:00'), 1, 'Administrateur',    'DECONNEXION',  'UTILISATEUR', 1, 'Deconnexion',          NULL, '127.0.0.1'),
(CONCAT(DATE_SUB(CURDATE(), INTERVAL 28 DAY), ' 08:30:00'), 2, 'Jean Preparateur',  'CONNEXION',    'UTILISATEUR', 2, 'Connexion reussie',    NULL, '127.0.0.1'),
(CONCAT(DATE_SUB(CURDATE(), INTERVAL 28 DAY), ' 17:30:00'), 2, 'Jean Preparateur',  'DECONNEXION',  'UTILISATEUR', 2, 'Deconnexion',          NULL, '127.0.0.1'),

-- Creations
(CONCAT(DATE_SUB(CURDATE(), INTERVAL 25 DAY), ' 09:00:00'), 1, 'Administrateur', 'CREATION', 'MEDICAMENT', 9,  'Creation medicament Omeprazole Biogaran',       '{"nom":"Omeprazole Biogaran","prix":4.20}', '127.0.0.1'),
(CONCAT(DATE_SUB(CURDATE(), INTERVAL 25 DAY), ' 09:15:00'), 1, 'Administrateur', 'CREATION', 'LOT',        15, 'Ajout lot OME2024001 pour Omeprazole',           '{"numero_lot":"OME2024001","quantite":3}',  '127.0.0.1'),

-- Modifications
(CONCAT(DATE_SUB(CURDATE(), INTERVAL 22 DAY), ' 10:00:00'), 1, 'Administrateur', 'MODIFICATION', 'MEDICAMENT',  1, 'Modification seuil minimum Doliprane : 15 → 20', '{"ancien_seuil":15,"nouveau_seuil":20}', '127.0.0.1'),
(CONCAT(DATE_SUB(CURDATE(), INTERVAL 20 DAY), ' 11:00:00'), 1, 'Administrateur', 'MODIFICATION', 'UTILISATEUR', 2, 'Modification profil Jean Preparateur',            NULL, '127.0.0.1'),

-- Ventes
(CONCAT(DATE_SUB(CURDATE(), INTERVAL 18 DAY), ' 09:35:00'), 2, 'Jean Preparateur',  'VENTE', 'VENTE', 30, 'Vente #30 - Montant : 8.70 EUR (3 articles)',   '{"montant":8.70,"nb_articles":3}',   '127.0.0.1'),
(CONCAT(DATE_SUB(CURDATE(), INTERVAL 16 DAY), ' 13:50:00'), 1, 'Administrateur',    'VENTE', 'VENTE', 32, 'Vente #32 - Montant : 2.50 EUR (1 article)',    '{"montant":2.50,"nb_articles":1}',   '127.0.0.1'),
(CONCAT(DATE_SUB(CURDATE(), INTERVAL 13 DAY), ' 09:05:00'), 1, 'Administrateur',    'VENTE', 'VENTE', 34, 'Vente #34 - Montant : 15.90 EUR (5 articles)',  '{"montant":15.90,"nb_articles":5}',  '127.0.0.1'),

-- Commandes
(CONCAT(DATE_SUB(CURDATE(), INTERVAL 7 DAY), ' 10:20:00'), 1, 'Administrateur', 'COMMANDE',  'COMMANDE', 3, 'Commande #3 creee - Fournisseur : Grossiste Medical',     '{"fournisseur":"Grossiste Medical","nb_lignes":2}',     '127.0.0.1'),
(CONCAT(DATE_SUB(CURDATE(), INTERVAL 2 DAY), ' 11:05:00'), 1, 'Administrateur', 'COMMANDE',  'COMMANDE', 4, 'Commande #4 creee - Fournisseur : Pharma Distribution',   '{"fournisseur":"Pharma Distribution","nb_lignes":3}',   '127.0.0.1'),

-- Receptions
(CONCAT(DATE_SUB(CURDATE(), INTERVAL 55 DAY), ' 10:00:00'), 1, 'Administrateur', 'RECEPTION', 'COMMANDE', 1, 'Reception commande #1 - 150 articles recus', '{"quantite_totale":150}', '127.0.0.1'),
(CONCAT(DATE_SUB(CURDATE(), INTERVAL 25 DAY), ' 14:30:00'), 1, 'Administrateur', 'RECEPTION', 'COMMANDE', 2, 'Reception commande #2 - 70 articles recus',  '{"quantite_totale":70}',  '127.0.0.1'),

-- Suppression
(CONCAT(DATE_SUB(CURDATE(), INTERVAL 15 DAY), ' 09:30:00'), 1, 'Administrateur', 'SUPPRESSION', 'COMMANDE', 5, 'Annulation commande #5 (doublon avec commande #2)', NULL, '127.0.0.1'),

-- Autre
(CONCAT(DATE_SUB(CURDATE(), INTERVAL 10 DAY), ' 22:00:00'), 1, 'Administrateur', 'AUTRE', 'SYSTEME', NULL, 'Sauvegarde automatique de la base de donnees', '{"fichier":"backup_sgpa.sql.gz","taille":"12.4 MB"}', '127.0.0.1'),

-- Connexions recentes
(CONCAT(DATE_SUB(CURDATE(), INTERVAL 3 DAY), ' 08:00:00'), 1, 'Administrateur',   'CONNEXION', 'UTILISATEUR', 1, 'Connexion reussie', NULL, '127.0.0.1'),
(CONCAT(DATE_SUB(CURDATE(), INTERVAL 1 DAY), ' 08:15:00'), 2, 'Jean Preparateur', 'CONNEXION', 'UTILISATEUR', 2, 'Connexion reussie', NULL, '127.0.0.1'),
(CONCAT(CURDATE(), ' 08:00:00'),                            1, 'Administrateur',   'CONNEXION', 'UTILISATEUR', 1, 'Connexion reussie', NULL, '127.0.0.1');


-- =============================================================================
-- SECTION 6 : Retours produits (3 retours)
-- =============================================================================

INSERT INTO retours (id_retour, id_vente, id_lot, id_utilisateur, quantite, motif, date_retour, reintegre, commentaire) VALUES
-- Retour 1 : 1 Doliprane de vente #5 (lot 3) - Reintegre au stock
(1, 5,  3, 1, 1, 'Emballage endommage',         CONCAT(DATE_SUB(CURDATE(), INTERVAL 77 DAY), ' 14:00:00'), TRUE,  'Boite ouverte par erreur en rayon, produit intact - reintegre'),
-- Retour 2 : 1 Amoxicilline de vente #4 (lot 7) - NON reintegre (lot peremption proche)
(2, 4,  7, 2, 1, 'Allergie/Effet indesirable',   CONCAT(DATE_SUB(CURDATE(), INTERVAL 79 DAY), ' 10:30:00'), FALSE, 'Patient allergique a la penicilline - lot en peremption proche, non reintegre'),
-- Retour 3 : 1 Advil de vente #37 (lot 5) - Reintegre
(3, 37, 5, 1, 1, 'Changement de traitement',     CONCAT(DATE_SUB(CURDATE(), INTERVAL 4 DAY),  ' 15:00:00'), TRUE,  'Medecin a modifie le traitement du patient');

-- Note : les stocks de lot 3 (+1) et lot 5 (+1) sont deja pris en compte
-- dans les UPDATE de la section 3.


-- =============================================================================
-- SECTION 7 : Inventaire (1 session terminee, 6 comptages, 3 regularisations)
-- =============================================================================

INSERT INTO sessions_inventaire (id_session, date_debut, date_fin, statut, id_utilisateur, notes) VALUES
(1,
 CONCAT(DATE_SUB(CURDATE(), INTERVAL 14 DAY), ' 08:00:00'),
 CONCAT(DATE_SUB(CURDATE(), INTERVAL 14 DAY), ' 11:30:00'),
 'TERMINEE', 1, 'Inventaire mensuel - controle de 6 lots');

INSERT INTO comptages_inventaire (id_comptage, id_session, id_lot, quantite_theorique, quantite_physique, ecart, motif_ecart, commentaire, date_comptage, id_utilisateur) VALUES
-- Lot 1 (Doliprane) : ecart -2 (CASSE)
(1, 1, 1,  32, 30, -2, 'CASSE',       'Deux boites endommagees trouvees en rayon',
 CONCAT(DATE_SUB(CURDATE(), INTERVAL 14 DAY), ' 08:30:00'), 1),
-- Lot 5 (Advil) : ecart 0
(2, 1, 5,  19, 19,  0, NULL,          NULL,
 CONCAT(DATE_SUB(CURDATE(), INTERVAL 14 DAY), ' 08:45:00'), 1),
-- Lot 6 (Amoxicilline) : ecart -1 (PERTE)
(3, 1, 6,  55, 54, -1, 'PERTE',       'Une boite introuvable apres verification complete',
 CONCAT(DATE_SUB(CURDATE(), INTERVAL 14 DAY), ' 09:00:00'), 1),
-- Lot 10 (Spasfon) : ecart 0
(4, 1, 10, 57, 57,  0, NULL,          NULL,
 CONCAT(DATE_SUB(CURDATE(), INTERVAL 14 DAY), ' 09:15:00'), 1),
-- Lot 11 (Gaviscon) : ecart -1 (AJUSTEMENT)
(5, 1, 11, 30, 29, -1, 'AJUSTEMENT',  'Erreur de comptage precedent probable',
 CONCAT(DATE_SUB(CURDATE(), INTERVAL 14 DAY), ' 09:30:00'), 1),
-- Lot 12 (Kardegic) : ecart 0
(6, 1, 12, 47, 47,  0, NULL,          NULL,
 CONCAT(DATE_SUB(CURDATE(), INTERVAL 14 DAY), ' 09:45:00'), 1);

INSERT INTO regularisations (id_regularisation, id_session, id_lot, quantite_ancienne, quantite_nouvelle, raison, justificatif, date_regularisation, id_utilisateur) VALUES
(1, 1, 1,  32, 30, 'CASSE',       'Boites endommagees lors de la mise en rayon - mises au rebut',
 CONCAT(DATE_SUB(CURDATE(), INTERVAL 14 DAY), ' 11:00:00'), 1),
(2, 1, 6,  55, 54, 'PERTE',       'Boite non retrouvee apres verification complete du stock',
 CONCAT(DATE_SUB(CURDATE(), INTERVAL 14 DAY), ' 11:10:00'), 1),
(3, 1, 11, 30, 29, 'AJUSTEMENT',  'Ajustement suite a ecart de comptage - probable erreur lors du dernier inventaire',
 CONCAT(DATE_SUB(CURDATE(), INTERVAL 14 DAY), ' 11:20:00'), 1);


-- =============================================================================
-- SECTION 8 : Reset des compteurs AUTO_INCREMENT
-- =============================================================================

ALTER TABLE ventes              AUTO_INCREMENT = 44;
ALTER TABLE ligne_ventes        AUTO_INCREMENT = 100;
ALTER TABLE commandes           AUTO_INCREMENT = 6;
ALTER TABLE ligne_commandes     AUTO_INCREMENT = 12;
ALTER TABLE audit_log           AUTO_INCREMENT = 100;
ALTER TABLE retours             AUTO_INCREMENT = 4;
ALTER TABLE sessions_inventaire AUTO_INCREMENT = 2;
ALTER TABLE comptages_inventaire AUTO_INCREMENT = 7;
ALTER TABLE regularisations     AUTO_INCREMENT = 4;
ALTER TABLE medicaments         AUTO_INCREMENT = 12;
ALTER TABLE lots                AUTO_INCREMENT = 17;


-- =============================================================================
-- VERIFICATION (decommenter pour tester)
-- =============================================================================

-- Ventes aujourd'hui (attendu : 3)
-- SELECT COUNT(*) AS ventes_aujourdhui FROM ventes WHERE DATE(date_vente) = CURDATE();

-- Alertes stock bas (attendu : 3 medicaments - Omeprazole, Cetirizine, Ibuprofene Enfant)
-- SELECT * FROM v_stock_bas;

-- Lots perimes avec stock (attendu : 2 - lots 13 et 14)
-- SELECT l.id_lot, l.numero_lot, m.nom_commercial, l.quantite_stock, l.date_peremption,
--        DATEDIFF(CURDATE(), l.date_peremption) AS jours_depuis_peremption
-- FROM lots l JOIN medicaments m ON l.id_medicament = m.id_medicament
-- WHERE l.date_peremption < CURDATE() AND l.quantite_stock > 0;

-- Lots en peremption proche (attendu : 2 - lots 3 et 7)
-- SELECT * FROM v_lots_peremption_proche;

-- Consommation sur 90 jours par medicament
-- SELECT m.nom_commercial, COALESCE(SUM(lv.quantite), 0) AS conso_90j
-- FROM medicaments m
-- LEFT JOIN lots l ON m.id_medicament = l.id_medicament
-- LEFT JOIN ligne_ventes lv ON lv.id_lot = l.id_lot
-- LEFT JOIN ventes v ON lv.id_vente = v.id_vente
--   AND v.date_vente >= DATE_SUB(CURDATE(), INTERVAL 90 DAY)
-- WHERE m.actif = TRUE
-- GROUP BY m.id_medicament ORDER BY conso_90j DESC;

-- Stock total par medicament
-- SELECT m.nom_commercial, m.seuil_min, COALESCE(SUM(l.quantite_stock), 0) AS stock_total
-- FROM medicaments m LEFT JOIN lots l ON m.id_medicament = l.id_medicament
-- GROUP BY m.id_medicament ORDER BY m.nom_commercial;
