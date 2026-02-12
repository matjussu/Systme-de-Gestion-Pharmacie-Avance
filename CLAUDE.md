# SGPA - Systeme de Gestion Pharmacie Avance

## Vue d'ensemble

Application JavaFX de gestion de pharmacie avec gestion avancee des lots et algorithme FEFO (First Expired, First Out).

## Commandes de Build

```bash
# Compilation
mvn clean compile

# Execution
mvn javafx:run

# Package JAR executable
mvn clean package

# Execution du JAR
java -jar target/sgpa-pharmacie-1.0.0.jar
```

## Architecture

```
MVC Strict avec Pattern DAO
src/main/java/com/sgpa/
  model/         # POJO (entites)
  model/enums/   # Enumerations (Role, StatutCommande)
  dao/           # Interfaces DAO
  dao/impl/      # Implementations JDBC
  service/       # Logique metier (FEFO, alertes)
  controller/    # Controleurs JavaFX
  view/          # Fichiers FXML (dans resources)
  exception/     # Exceptions personnalisees
  utils/         # Utilitaires (DatabaseConnection, PasswordUtils)
```

## Conventions de Code

- **Types financiers** : Toujours utiliser `BigDecimal` (jamais double/float)
- **Dates** : Utiliser `LocalDate` et `LocalDateTime`
- **SQL** : PreparedStatement obligatoire (securite injection SQL)
- **Transactions** : Commit/rollback explicites pour les operations multi-tables
- **Logging** : SLF4J avec Logback
- **Controllers** : Tous les controleurs de vues etendent `BaseController`

## Flux de donnees

```
Controller -> Service -> DAO -> Base de donnees
     ^          |
     |__________|
        (DTOs)
```

Le Controller ne doit JAMAIS appeler le DAO directement.

## Base de Donnees

### Configuration
Fichier `src/main/resources/database.properties`:
```properties
db.url=jdbc:mysql://localhost:3306/sgpa_pharmacie
db.username=root
db.password=votre_mot_de_passe
db.pool.size=10
```

### Initialisation
```bash
mysql -u root -p < sql/schema.sql
```

### Tables principales
- `utilisateurs` : Gestion des comptes (PHARMACIEN, PREPARATEUR)
- `medicaments` : Fiche produit (prix, seuil min)
- `lots` : Stock physique avec date de peremption
- `ventes` / `ligne_ventes` : Historique des ventes avec tracabilite par lot
- `commandes` / `ligne_commandes` : Commandes fournisseurs
- `fournisseurs` : Catalogue fournisseurs

## Algorithme FEFO

Lors d'une vente, le systeme deduit automatiquement les quantites des lots dont la date de peremption est la plus proche:

```java
// Pseudo-code
List<Lot> lots = lotDAO.findByMedicamentIdSortedByExpiration(medId);
for (Lot lot : lots) {
    if (quantiteRestante <= 0) break;
    int aDeduire = Math.min(lot.getQuantiteStock(), quantiteRestante);
    lot.setQuantiteStock(lot.getQuantiteStock() - aDeduire);
    quantiteRestante -= aDeduire;
}
```

## Alertes

1. **Stock bas** : `SUM(lots.quantite) < medicament.seuil_min`
2. **Peremption proche** : `date_peremption < NOW() + 3 MOIS`
3. **Lots perimes** : `date_peremption < NOW()`

## Securite

- **Mots de passe** : Hashes avec BCrypt (salage automatique)
- **Roles** :
  - PHARMACIEN : Acces complet
  - PREPARATEUR : Ventes uniquement
- **SQL** : PreparedStatement pour prevenir les injections

## Dependances Principales

| Dependance | Version | Usage |
|------------|---------|-------|
| JavaFX | 21.0.2 | Interface utilisateur |
| MySQL Connector | 8.0.33 | Connexion BDD |
| HikariCP | 5.1.0 | Pool de connexions |
| jBCrypt | 0.4 | Hashage mots de passe |
| iText | 7.2.5 | Generation PDF |
| ControlsFX | 11.2.0 | Composants UI avances |
| Ikonli | 12.3.1 | Icones FontAwesome |

## Structure des Phases

### Phase 1 - Infrastructure (TERMINEE)
- Schema BDD MySQL
- POJO (Medicament, Lot, Vente, LigneVente, Utilisateur, Fournisseur, Commande)
- Interfaces et implementations DAO
- Pool de connexions HikariCP

### Phase 2 - Logique Metier (TERMINEE)
- Service FEFO (VenteService)
- Service Alertes (AlerteService)
- DTOs (AlerteStock, AlertePeremption)
- Gestion des transactions

### Phase 3 - Interface Utilisateur (TERMINEE)
- **login.fxml** + LoginController : Authentification BCrypt
- **dashboard.fxml** + DashboardController : Navigation et statistiques
- **vente.fxml** + VenteController : Point de vente avec panier FEFO
- **historique.fxml** + HistoriqueController : Historique des ventes avec detail
- **alertes.fxml** + AlerteController : 3 onglets (stock bas, peremption proche, perimes)
- **stock.fxml** + StockController : Gestion des lots par medicament
- **medicaments.fxml** + MedicamentController : CRUD catalogue medicaments
- **commandes.fxml** + CommandeController : Commandes fournisseurs (creation, reception)
- **BaseController.java** : Classe de base pour tous les controleurs de vues
- **style.css** : Styles complets pour toutes les vues (~1000 lignes)

### Phase 4 - Fonctionnalites Avancees (TERMINEE)

#### 4.1 Generation de rapports PDF (TERMINEE)
- [x] Ticket de caisse apres vente (PDFGenerator + RapportService.genererTicketCaisse)
- [x] Rapport journalier des ventes (RapportService.genererRapportVentes)
- [x] Liste des alertes stock/peremption (RapportService.genererRapportAlertes*)
- [x] Bon de commande fournisseur (RapportService.genererBonCommande)

**Fichiers crees:**
- `src/main/java/com/sgpa/utils/PDFGenerator.java` - Utilitaire generation PDF avec iText 7
- `src/main/java/com/sgpa/service/RapportService.java` - Service de generation des rapports

**Integration dans les controleurs:**
- VenteController: Proposition impression ticket apres vente
- AlerteController: Export PDF des alertes (stock bas, peremption, perimes, complet)
- CommandeController: Export bon de commande PDF

**Repertoire des rapports:** `~/SGPA_Rapports/`

#### 4.2 Ameliorations UI (TERMINEE)
- [x] Export CSV des donnees (CSVExporter + ExportService)
- [x] Graphiques statistiques (statistiques.fxml + StatistiquesController)

**Fichiers crees:**
- `src/main/java/com/sgpa/utils/CSVExporter.java` - Utilitaire export CSV
- `src/main/java/com/sgpa/service/ExportService.java` - Service d'export (ventes, stock, medicaments, audit)
- `src/main/resources/fxml/statistiques.fxml` - Interface graphiques statistiques
- `src/main/java/com/sgpa/controller/StatistiquesController.java` - Controleur statistiques

**Integration dans les controleurs:**
- HistoriqueController: Export CSV des ventes
- StockController: Export CSV du stock
- AuditController: Export CSV du journal d'audit

#### 4.3 Fonctionnalites metier (EN COURS)

**4.3.1 Gestion des retours produits (TERMINEE)**
- [x] Enregistrement des retours avec motif
- [x] Reintegration au stock (si lot non perime)
- [x] Tracabilite complete (vente, lot, utilisateur)
- [x] Historique des retours avec filtres

**Fichiers crees:**
- `sql/retours_table.sql` - Script de creation de la table retours
- `src/main/java/com/sgpa/model/Retour.java` - Entite retour
- `src/main/java/com/sgpa/dao/RetourDAO.java` - Interface DAO
- `src/main/java/com/sgpa/dao/impl/RetourDAOImpl.java` - Implementation DAO
- `src/main/java/com/sgpa/service/RetourService.java` - Service metier
- `src/main/resources/fxml/retours.fxml` - Interface de gestion
- `src/main/java/com/sgpa/controller/RetourController.java` - Controleur

**Fonctionnalites:**
- Recherche de vente par numero
- Affichage des articles vendus avec quantites retournables
- Selection du motif de retour (liste predeterminee)
- Option de reintegration au stock (desactivee si lot perime)
- Historique avec filtres par date et statut de reintegration

**4.3.2 Inventaire avec ecarts (TERMINEE)**
- [x] Sessions d'inventaire (creation, terminaison, annulation)
- [x] Comptage physique par lot
- [x] Calcul automatique des ecarts
- [x] Regularisation du stock avec motifs

**Fichiers crees:**
- `sql/inventaire_tables.sql` - Script creation tables (3 tables)
- `src/main/java/com/sgpa/model/enums/StatutInventaire.java` - Enum statuts session
- `src/main/java/com/sgpa/model/enums/MotifEcart.java` - Enum motifs d'ecart
- `src/main/java/com/sgpa/model/SessionInventaire.java` - Entite session
- `src/main/java/com/sgpa/model/ComptageInventaire.java` - Entite comptage
- `src/main/java/com/sgpa/model/Regularisation.java` - Entite regularisation
- `src/main/java/com/sgpa/dao/SessionInventaireDAO.java` + impl - DAO session
- `src/main/java/com/sgpa/dao/ComptageInventaireDAO.java` + impl - DAO comptage
- `src/main/java/com/sgpa/dao/RegularisationDAO.java` + impl - DAO regularisation
- `src/main/java/com/sgpa/service/InventaireService.java` - Service metier
- `src/main/resources/fxml/inventaire.fxml` - Interface utilisateur
- `src/main/java/com/sgpa/controller/InventaireController.java` - Controleur

**Fonctionnalites:**
- Creation de session d'inventaire (une seule a la fois)
- Liste des lots avec recherche
- Saisie de quantite physique avec calcul d'ecart en temps reel
- Motif obligatoire si ecart (PERTE, CASSE, VOL, PEREMPTION, AJUSTEMENT, AUTRE)
- Regularisation automatique du stock lors de la terminaison
- Historique des sessions avec statistiques

**4.3.3 Predictions de reapprovisionnement (TERMINEE)**
- [x] Analyse de l'historique des ventes pour calculer la consommation moyenne
- [x] Prediction de la date de rupture de stock
- [x] Suggestion de quantites a commander
- [x] Niveaux d'urgence (RUPTURE, CRITIQUE, URGENT, ATTENTION, OK)
- [x] Graphique d'evolution prevue du stock
- [x] Export PDF des predictions

**Fichiers crees:**
- `src/main/java/com/sgpa/dto/PredictionReapprovisionnement.java` - DTO prediction
- `src/main/java/com/sgpa/dao/ConsommationDAO.java` - Interface DAO statistiques
- `src/main/java/com/sgpa/dao/impl/ConsommationDAOImpl.java` - Implementation DAO
- `src/main/java/com/sgpa/service/PredictionService.java` - Service metier predictions
- `src/main/resources/fxml/predictions.fxml` - Interface utilisateur
- `src/main/java/com/sgpa/controller/PredictionController.java` - Controleur

**Parametres configurables (config.properties):**
- `prediction.jours.analyse` : Periode d'analyse (defaut: 90 jours)
- `prediction.delai.livraison.defaut` : Delai livraison (defaut: 3 jours)
- `prediction.marge.securite.jours` : Marge securite (defaut: 7 jours)
- `prediction.stock.cible.jours` : Stock cible (defaut: 30 jours)
- `prediction.seuil.critique.jours` : Seuil critique (defaut: 7 jours)
- `prediction.seuil.urgent.jours` : Seuil urgent (defaut: 14 jours)

#### 4.4 Securite et administration (TERMINEE)
- [x] Gestion des utilisateurs (CRUD)
- [x] Journal d'audit des actions
- [x] Sauvegarde/restauration BDD
- [x] Configuration parametrable

**4.4.1 Gestion des utilisateurs (TERMINEE)**
- `src/main/java/com/sgpa/service/UtilisateurService.java` - Service CRUD utilisateurs
- `src/main/resources/fxml/utilisateurs.fxml` - Interface de gestion
- `src/main/java/com/sgpa/controller/UtilisateurController.java` - Controleur
- Accessible uniquement aux Pharmaciens (verification des droits)
- Fonctionnalites: creation, modification, suppression, activation/desactivation, changement mot de passe

**4.4.2 Journal d'audit (TERMINEE)**
- `src/main/java/com/sgpa/model/AuditLog.java` - Entite audit
- `src/main/java/com/sgpa/model/enums/TypeAction.java` - Types d'actions (CONNEXION, DECONNEXION, CREATION, MODIFICATION, SUPPRESSION, VENTE, COMMANDE, RECEPTION)
- `src/main/java/com/sgpa/dao/AuditLogDAO.java` + impl - DAO avec recherche multicriteres
- `src/main/java/com/sgpa/service/AuditService.java` - Service d'audit
- `src/main/resources/fxml/audit.fxml` - Interface de consultation avec filtres
- `src/main/java/com/sgpa/controller/AuditController.java` - Controleur avec pagination
- `sql/audit_table.sql` - Script de creation de la table audit_log
- Integration dans AuthenticationService (connexion/deconnexion)

**4.4.3 Sauvegarde/Restauration BDD (TERMINEE)**
- `src/main/java/com/sgpa/service/BackupService.java` - Service de sauvegarde (mysqldump/mysql)
- `src/main/resources/fxml/backup.fxml` - Interface de gestion des sauvegardes
- `src/main/java/com/sgpa/controller/BackupController.java` - Controleur avec liste/restauration/suppression
- Compression gzip optionnelle
- Repertoire des sauvegardes: `~/SGPA_Backups/`

**4.4.4 Configuration parametrable (TERMINEE)**
- `src/main/resources/config.properties` - Fichier de configuration par defaut
- `src/main/java/com/sgpa/service/ConfigService.java` - Service de configuration
- `src/main/resources/fxml/settings.fxml` - Interface de configuration
- `src/main/java/com/sgpa/controller/SettingsController.java` - Controleur
- Parametres: nom pharmacie, alertes, repertoires, options UI

## Structure des Fichiers FXML

```
src/main/resources/
  fxml/
    login.fxml         # Ecran de connexion
    dashboard.fxml     # Layout principal avec sidebar
    vente.fxml         # Point de vente
    historique.fxml    # Historique des ventes
    retours.fxml       # Gestion des retours produits
    alertes.fxml       # Alertes stock et peremption
    stock.fxml         # Gestion des lots
    inventaire.fxml    # Inventaire physique avec ecarts
    medicaments.fxml   # Catalogue medicaments
    commandes.fxml     # Commandes fournisseurs
    predictions.fxml   # Predictions de reapprovisionnement
    statistiques.fxml  # Graphiques et statistiques
    utilisateurs.fxml  # Gestion des utilisateurs (admin)
    audit.fxml         # Journal d'audit (admin)
    backup.fxml        # Sauvegarde/restauration BDD (admin)
    settings.fxml      # Configuration application (admin)
  css/
    style.css          # Styles globaux
  database.properties  # Configuration BDD
  config.properties    # Configuration application
```

## Structure des Controleurs

```
src/main/java/com/sgpa/controller/
  BaseController.java         # Classe abstraite commune
  LoginController.java        # Authentification
  DashboardController.java    # Navigation principale
  VenteController.java        # Logique vente + panier
  HistoriqueController.java   # Consultation ventes
  RetourController.java       # Gestion des retours
  AlerteController.java       # Affichage alertes
  StockController.java        # Gestion lots
  InventaireController.java   # Inventaire physique
  MedicamentController.java   # CRUD medicaments
  CommandeController.java     # Commandes fournisseurs
  PredictionController.java   # Predictions reapprovisionnement
  StatistiquesController.java # Graphiques statistiques
  UtilisateurController.java  # Gestion utilisateurs (admin)
  AuditController.java        # Journal d'audit (admin)
  BackupController.java       # Sauvegarde BDD (admin)
  SettingsController.java     # Configuration (admin)
```

## Navigation

Le DashboardController gere la navigation via `loadView()`:
- Tableau de bord : Restaure le contenu initial (statistiques)
- Nouvelle Vente : `/fxml/vente.fxml`
- Historique : `/fxml/historique.fxml`
- Retours Produits : `/fxml/retours.fxml`
- Gestion Stock : `/fxml/stock.fxml`
- Inventaire : `/fxml/inventaire.fxml`
- Medicaments : `/fxml/medicaments.fxml`
- Commandes : `/fxml/commandes.fxml`
- Alertes : `/fxml/alertes.fxml`
- Predictions : `/fxml/predictions.fxml`
- Statistiques : `/fxml/statistiques.fxml`
- Utilisateurs : `/fxml/utilisateurs.fxml` (Pharmaciens uniquement)
- Journal Audit : `/fxml/audit.fxml` (Pharmaciens uniquement)
- Sauvegardes : `/fxml/backup.fxml` (Pharmaciens uniquement)
- Parametres : `/fxml/settings.fxml` (Pharmaciens uniquement)
