package com.sgpa.controller;

import com.sgpa.model.Utilisateur;
import com.sgpa.model.enums.Role;
import com.sgpa.service.UtilisateurService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Controleur pour l'ecran de gestion des utilisateurs.
 * Permet CRUD complet sur les utilisateurs du systeme.
 *
 * @author SGPA Team
 * @version 1.0
 */
public class UtilisateurController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(UtilisateurController.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // Recherche et filtres
    @FXML private TextField searchField;
    @FXML private ComboBox<String> comboFilterRole;

    // Table
    @FXML private Label lblCount;
    @FXML private TableView<Utilisateur> tableUtilisateurs;
    @FXML private TableColumn<Utilisateur, String> colNomUtilisateur;
    @FXML private TableColumn<Utilisateur, String> colNomComplet;
    @FXML private TableColumn<Utilisateur, String> colRole;
    @FXML private TableColumn<Utilisateur, String> colActif;
    @FXML private TableColumn<Utilisateur, String> colDateCreation;
    @FXML private TableColumn<Utilisateur, String> colDerniereConnexion;

    // Formulaire
    @FXML private VBox formPanel;
    @FXML private Label lblFormTitle;
    @FXML private TextField txtNomUtilisateur;
    @FXML private TextField txtNomComplet;
    @FXML private ComboBox<Role> comboRole;
    @FXML private VBox passwordBox;
    @FXML private Label lblPassword;
    @FXML private PasswordField txtMotDePasse;
    @FXML private Label lblPasswordHint;
    @FXML private VBox confirmPasswordBox;
    @FXML private PasswordField txtConfirmMotDePasse;
    @FXML private CheckBox chkActif;

    // Section changement mot de passe (mode edition)
    @FXML private VBox changePasswordSection;
    @FXML private CheckBox chkChangePassword;
    @FXML private VBox newPasswordBox;
    @FXML private PasswordField txtNewPassword;
    @FXML private PasswordField txtConfirmNewPassword;

    // Boutons
    @FXML private Button btnDelete;
    @FXML private Button btnToggleActive;
    @FXML private Button btnSave;

    private final UtilisateurService utilisateurService;
    private final ObservableList<Utilisateur> utilisateurData = FXCollections.observableArrayList();
    private List<Utilisateur> allUtilisateurs;

    private Utilisateur selectedUtilisateur;
    private boolean isEditMode = false;

    public UtilisateurController() {
        this.utilisateurService = new UtilisateurService();
    }

    @FXML
    public void initialize() {
        setupTable();
        setupComboBoxes();
        setupResponsiveTable(tableUtilisateurs);
        loadData();

        // Selection listener
        tableUtilisateurs.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> handleSelection(newVal));

        // Par defaut, formulaire pour nouveau
        resetForm();
    }

    private void setupComboBoxes() {
        // ComboBox des roles pour le formulaire
        comboRole.setItems(FXCollections.observableArrayList(Role.values()));
        comboRole.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Role role) {
                return role != null ? role.getLibelle() : "";
            }

            @Override
            public Role fromString(String string) {
                return Role.valueOf(string);
            }
        });

        // ComboBox filtre par role
        comboFilterRole.setItems(FXCollections.observableArrayList(
                "Tous", "Pharmacien", "Preparateur"
        ));
        comboFilterRole.setValue("Tous");
    }

    private void setupTable() {
        colNomUtilisateur.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getNomUtilisateur()));
        colNomComplet.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getNomComplet()));
        colRole.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getRole() != null
                        ? data.getValue().getRole().getLibelle()
                        : ""));
        colActif.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().isActif() ? "Actif" : "Inactif"));
        colDateCreation.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getDateCreation() != null
                        ? data.getValue().getDateCreation().format(DATE_FORMATTER)
                        : ""));
        colDerniereConnexion.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getDerniereConnexion() != null
                        ? data.getValue().getDerniereConnexion().format(DATE_FORMATTER)
                        : "Jamais"));

        // Colorisation du role
        colRole.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if ("Pharmacien".equals(item)) {
                        setStyle("-fx-text-fill: #6f42c1; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #17a2b8;");
                    }
                }
            }
        });

        // Colorisation du statut
        colActif.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if ("Actif".equals(item)) {
                        setStyle("-fx-text-fill: #28a745; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #dc3545;");
                    }
                }
            }
        });

        tableUtilisateurs.setItems(utilisateurData);
    }

    private void loadData() {
        Task<List<Utilisateur>> task = new Task<>() {
            @Override
            protected List<Utilisateur> call() throws Exception {
                return utilisateurService.getAllUtilisateurs();
            }

            @Override
            protected void succeeded() {
                allUtilisateurs = getValue();
                applyFilters();
            }

            @Override
            protected void failed() {
                logger.error("Erreur chargement utilisateurs", getException());
                showAlert(Alert.AlertType.ERROR, "Erreur",
                        "Impossible de charger les utilisateurs: " + getException().getMessage());
            }
        };
        runAsync(task);
    }

    private void applyFilters() {
        if (allUtilisateurs == null) return;

        String search = searchField.getText().trim().toLowerCase();
        String roleFilter = comboFilterRole.getValue();

        List<Utilisateur> filtered = allUtilisateurs.stream()
                .filter(u -> {
                    // Filtre recherche
                    if (!search.isEmpty()) {
                        return u.getNomUtilisateur().toLowerCase().contains(search) ||
                               u.getNomComplet().toLowerCase().contains(search);
                    }
                    return true;
                })
                .filter(u -> {
                    // Filtre role
                    if (roleFilter == null || "Tous".equals(roleFilter)) return true;
                    return u.getRole() != null && u.getRole().getLibelle().equals(roleFilter);
                })
                .collect(Collectors.toList());

        utilisateurData.setAll(filtered);
        lblCount.setText(filtered.size() + " utilisateur(s)");
    }

    private void handleSelection(Utilisateur utilisateur) {
        selectedUtilisateur = utilisateur;
        if (utilisateur == null) {
            resetForm();
            return;
        }

        isEditMode = true;
        lblFormTitle.setText("Modifier: " + utilisateur.getNomComplet());

        // Afficher boutons d'action
        btnDelete.setVisible(true);
        btnDelete.setManaged(true);
        btnToggleActive.setVisible(true);
        btnToggleActive.setManaged(true);
        btnToggleActive.setText(utilisateur.isActif() ? "Desactiver" : "Activer");

        // Masquer les champs de mot de passe (creation) et afficher section changement
        passwordBox.setVisible(false);
        passwordBox.setManaged(false);
        confirmPasswordBox.setVisible(false);
        confirmPasswordBox.setManaged(false);
        changePasswordSection.setVisible(true);
        changePasswordSection.setManaged(true);
        chkChangePassword.setSelected(false);
        newPasswordBox.setVisible(false);
        newPasswordBox.setManaged(false);

        // Remplir le formulaire
        txtNomUtilisateur.setText(utilisateur.getNomUtilisateur());
        txtNomComplet.setText(utilisateur.getNomComplet());
        comboRole.setValue(utilisateur.getRole());
        chkActif.setSelected(utilisateur.isActif());
    }

    private void resetForm() {
        isEditMode = false;
        selectedUtilisateur = null;
        lblFormTitle.setText("Nouvel Utilisateur");

        // Masquer boutons d'action
        btnDelete.setVisible(false);
        btnDelete.setManaged(false);
        btnToggleActive.setVisible(false);
        btnToggleActive.setManaged(false);

        // Afficher les champs de mot de passe (creation)
        passwordBox.setVisible(true);
        passwordBox.setManaged(true);
        confirmPasswordBox.setVisible(true);
        confirmPasswordBox.setManaged(true);
        changePasswordSection.setVisible(false);
        changePasswordSection.setManaged(false);

        // Vider le formulaire
        txtNomUtilisateur.clear();
        txtNomComplet.clear();
        comboRole.setValue(null);
        txtMotDePasse.clear();
        txtConfirmMotDePasse.clear();
        txtNewPassword.clear();
        txtConfirmNewPassword.clear();
        chkActif.setSelected(true);
        chkChangePassword.setSelected(false);

        tableUtilisateurs.getSelectionModel().clearSelection();
    }

    @FXML
    private void handleSearch() {
        applyFilters();
    }

    @FXML
    private void handleFilterRole() {
        applyFilters();
    }

    @FXML
    private void handleRefresh() {
        loadData();
        resetForm();
    }

    @FXML
    private void handleNew() {
        resetForm();
    }

    @FXML
    private void handleCancel() {
        resetForm();
    }

    @FXML
    private void handleToggleChangePassword() {
        boolean show = chkChangePassword.isSelected();
        newPasswordBox.setVisible(show);
        newPasswordBox.setManaged(show);
        if (!show) {
            txtNewPassword.clear();
            txtConfirmNewPassword.clear();
        }
    }

    @FXML
    private void handleSave() {
        // Validation commune
        if (txtNomUtilisateur.getText().trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Validation", "Le nom d'utilisateur est obligatoire.");
            return;
        }
        if (txtNomComplet.getText().trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Validation", "Le nom complet est obligatoire.");
            return;
        }
        if (comboRole.getValue() == null) {
            showAlert(Alert.AlertType.WARNING, "Validation", "Veuillez selectionner un role.");
            return;
        }

        if (isEditMode) {
            saveExistingUser();
        } else {
            saveNewUser();
        }
    }

    private void saveNewUser() {
        // Validation mot de passe
        String password = txtMotDePasse.getText();
        String confirmPassword = txtConfirmMotDePasse.getText();

        if (password.length() < 4) {
            showAlert(Alert.AlertType.WARNING, "Validation",
                    "Le mot de passe doit contenir au moins 4 caracteres.");
            return;
        }
        if (!password.equals(confirmPassword)) {
            showAlert(Alert.AlertType.WARNING, "Validation",
                    "Les mots de passe ne correspondent pas.");
            return;
        }

        Task<Utilisateur> task = new Task<>() {
            @Override
            protected Utilisateur call() throws Exception {
                Utilisateur user = utilisateurService.creerUtilisateur(
                        txtNomUtilisateur.getText().trim(),
                        password,
                        comboRole.getValue(),
                        txtNomComplet.getText().trim()
                );
                if (!chkActif.isSelected()) {
                    utilisateurService.setActif(user.getIdUtilisateur(), false);
                }
                return user;
            }

            @Override
            protected void succeeded() {
                showAlert(Alert.AlertType.INFORMATION, "Succes", "Utilisateur cree avec succes.");
                loadData();
                resetForm();
            }

            @Override
            protected void failed() {
                logger.error("Erreur creation utilisateur", getException());
                showAlert(Alert.AlertType.ERROR, "Erreur", getException().getMessage());
            }
        };
        runAsync(task);
    }

    private void saveExistingUser() {
        // Verification changement de mot de passe
        if (chkChangePassword.isSelected()) {
            String newPassword = txtNewPassword.getText();
            String confirmNewPassword = txtConfirmNewPassword.getText();

            if (newPassword.length() < 4) {
                showAlert(Alert.AlertType.WARNING, "Validation",
                        "Le nouveau mot de passe doit contenir au moins 4 caracteres.");
                return;
            }
            if (!newPassword.equals(confirmNewPassword)) {
                showAlert(Alert.AlertType.WARNING, "Validation",
                        "Les nouveaux mots de passe ne correspondent pas.");
                return;
            }
        }

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                selectedUtilisateur.setNomUtilisateur(txtNomUtilisateur.getText().trim());
                selectedUtilisateur.setNomComplet(txtNomComplet.getText().trim());
                selectedUtilisateur.setRole(comboRole.getValue());
                selectedUtilisateur.setActif(chkActif.isSelected());

                utilisateurService.mettreAJourUtilisateur(selectedUtilisateur);

                // Changement de mot de passe si demande
                if (chkChangePassword.isSelected()) {
                    utilisateurService.changerMotDePasse(
                            selectedUtilisateur.getIdUtilisateur(),
                            txtNewPassword.getText()
                    );
                }
                return null;
            }

            @Override
            protected void succeeded() {
                showAlert(Alert.AlertType.INFORMATION, "Succes", "Utilisateur modifie avec succes.");
                loadData();
                resetForm();
            }

            @Override
            protected void failed() {
                logger.error("Erreur modification utilisateur", getException());
                showAlert(Alert.AlertType.ERROR, "Erreur", getException().getMessage());
            }
        };
        runAsync(task);
    }

    @FXML
    private void handleToggleActive() {
        if (selectedUtilisateur == null) return;

        boolean newStatus = !selectedUtilisateur.isActif();
        String action = newStatus ? "activer" : "desactiver";

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmation");
        confirm.setHeaderText(action.substring(0, 1).toUpperCase() + action.substring(1) + " l'utilisateur?");
        confirm.setContentText("Voulez-vous vraiment " + action + " \"" +
                selectedUtilisateur.getNomComplet() + "\"?");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                Task<Void> task = new Task<>() {
                    @Override
                    protected Void call() throws Exception {
                        utilisateurService.setActif(selectedUtilisateur.getIdUtilisateur(), newStatus);
                        return null;
                    }

                    @Override
                    protected void succeeded() {
                        showAlert(Alert.AlertType.INFORMATION, "Succes",
                                "Utilisateur " + (newStatus ? "active" : "desactive") + ".");
                        loadData();
                        resetForm();
                    }

                    @Override
                    protected void failed() {
                        logger.error("Erreur changement statut", getException());
                        showAlert(Alert.AlertType.ERROR, "Erreur", getException().getMessage());
                    }
                };
                runAsync(task);
            }
        });
    }

    @FXML
    private void handleDelete() {
        if (selectedUtilisateur == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmation");
        confirm.setHeaderText("Supprimer l'utilisateur?");
        confirm.setContentText("Voulez-vous vraiment supprimer \"" +
                selectedUtilisateur.getNomComplet() + "\"?\n\n" +
                "Attention: cette action est irreversible.\n" +
                "Si l'utilisateur a effectue des ventes, la suppression sera impossible.");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                Task<Void> task = new Task<>() {
                    @Override
                    protected Void call() throws Exception {
                        utilisateurService.supprimerUtilisateur(selectedUtilisateur.getIdUtilisateur());
                        return null;
                    }

                    @Override
                    protected void succeeded() {
                        showAlert(Alert.AlertType.INFORMATION, "Succes", "Utilisateur supprime.");
                        loadData();
                        resetForm();
                    }

                    @Override
                    protected void failed() {
                        logger.error("Erreur suppression utilisateur", getException());
                        showAlert(Alert.AlertType.ERROR, "Erreur",
                                "Impossible de supprimer cet utilisateur.\n" +
                                "Il est peut-etre reference dans des ventes.");
                    }
                };
                runAsync(task);
            }
        });
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
