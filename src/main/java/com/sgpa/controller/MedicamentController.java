package com.sgpa.controller;

import com.sgpa.dao.impl.MedicamentDAOImpl;
import com.sgpa.model.Medicament;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;

/**
 * Controleur pour l'ecran de gestion des medicaments.
 * Permet CRUD complet sur le catalogue des medicaments.
 *
 * @author SGPA Team
 * @version 1.0
 */
public class MedicamentController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(MedicamentController.class);

    // Recherche
    @FXML private TextField searchField;

    // Table
    @FXML private Label lblCount;
    @FXML private TableView<Medicament> tableMedicaments;
    @FXML private TableColumn<Medicament, String> colNom;
    @FXML private TableColumn<Medicament, String> colPrincipe;
    @FXML private TableColumn<Medicament, String> colForme;
    @FXML private TableColumn<Medicament, String> colDosage;
    @FXML private TableColumn<Medicament, String> colPrix;
    @FXML private TableColumn<Medicament, String> colOrdonnance;
    @FXML private TableColumn<Medicament, String> colActif;

    // Formulaire
    @FXML private VBox formPanel;
    @FXML private Label lblFormTitle;
    @FXML private TextField txtNomCommercial;
    @FXML private TextField txtPrincipeActif;
    @FXML private ComboBox<String> comboForme;
    @FXML private TextField txtDosage;
    @FXML private TextField txtPrix;
    @FXML private Spinner<Integer> spinnerSeuil;
    @FXML private TextArea txtDescription;
    @FXML private CheckBox chkOrdonnance;
    @FXML private CheckBox chkActif;
    @FXML private Button btnDelete;
    @FXML private Button btnSave;

    private final MedicamentDAOImpl medicamentDAO;
    private final ObservableList<Medicament> medicamentData = FXCollections.observableArrayList();

    private Medicament selectedMedicament;
    private boolean isEditMode = false;

    public MedicamentController() {
        this.medicamentDAO = new MedicamentDAOImpl();
    }

    @FXML
    public void initialize() {
        setupTable();
        setupFormesGaleniques();
        setupResponsiveTable(tableMedicaments);
        loadData();

        // Selection listener
        tableMedicaments.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> handleSelection(newVal));

        // Par defaut, formulaire pour nouveau
        resetForm();
    }

    private void setupFormesGaleniques() {
        comboForme.setItems(FXCollections.observableArrayList(
                "Comprime", "Gelule", "Sirop", "Solution Injectable",
                "Pommade", "Creme", "Suppositoire", "Collyre", "Spray"
        ));
    }

    private void setupTable() {
        colNom.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getNomCommercial()));
        colPrincipe.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getPrincipeActif()));
        colForme.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getFormeGalenique() != null ? data.getValue().getFormeGalenique() : ""));
        colDosage.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getDosage() != null ? data.getValue().getDosage() : ""));
        colPrix.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getPrixPublic() != null ? data.getValue().getPrixPublic().toString() : "0.00"));
        colOrdonnance.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().isNecessiteOrdonnance() ? "Oui" : "Non"));
        colActif.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().isActif() ? "Oui" : "Non"));

        // Colorisation ordonnance
        colOrdonnance.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if ("Oui".equals(item)) {
                        setStyle("-fx-text-fill: #fd7e14; -fx-font-weight: bold;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });

        // Colorisation actif
        colActif.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if ("Non".equals(item)) {
                        setStyle("-fx-text-fill: #dc3545;");
                    } else {
                        setStyle("-fx-text-fill: #28a745;");
                    }
                }
            }
        });

        tableMedicaments.setItems(medicamentData);
    }

    private void loadData() {
        Task<List<Medicament>> task = new Task<>() {
            @Override
            protected List<Medicament> call() throws Exception {
                return medicamentDAO.findAll();
            }

            @Override
            protected void succeeded() {
                medicamentData.setAll(getValue());
                lblCount.setText(getValue().size() + " medicaments");
            }

            @Override
            protected void failed() {
                logger.error("Erreur chargement medicaments", getException());
            }
        };
        new Thread(task).start();
    }

    private void handleSelection(Medicament medicament) {
        selectedMedicament = medicament;
        if (medicament == null) {
            resetForm();
            return;
        }

        isEditMode = true;
        lblFormTitle.setText("Modifier: " + medicament.getNomCommercial());
        btnDelete.setVisible(true);
        btnDelete.setManaged(true);

        // Remplir le formulaire
        txtNomCommercial.setText(medicament.getNomCommercial());
        txtPrincipeActif.setText(medicament.getPrincipeActif());
        comboForme.setValue(medicament.getFormeGalenique());
        txtDosage.setText(medicament.getDosage());
        txtPrix.setText(medicament.getPrixPublic() != null ? medicament.getPrixPublic().toString() : "");
        spinnerSeuil.getValueFactory().setValue(medicament.getSeuilMin());
        txtDescription.setText(medicament.getDescription());
        chkOrdonnance.setSelected(medicament.isNecessiteOrdonnance());
        chkActif.setSelected(medicament.isActif());
    }

    private void resetForm() {
        isEditMode = false;
        selectedMedicament = null;
        lblFormTitle.setText("Nouveau Medicament");
        btnDelete.setVisible(false);
        btnDelete.setManaged(false);

        txtNomCommercial.clear();
        txtPrincipeActif.clear();
        comboForme.setValue(null);
        txtDosage.clear();
        txtPrix.clear();
        spinnerSeuil.getValueFactory().setValue(10);
        txtDescription.clear();
        chkOrdonnance.setSelected(false);
        chkActif.setSelected(true);

        tableMedicaments.getSelectionModel().clearSelection();
    }

    @FXML
    private void handleSearch() {
        String search = searchField.getText().trim().toLowerCase();
        if (search.isEmpty()) {
            loadData();
            return;
        }

        Task<List<Medicament>> task = new Task<>() {
            @Override
            protected List<Medicament> call() throws Exception {
                return medicamentDAO.findByNom(search);
            }

            @Override
            protected void succeeded() {
                medicamentData.setAll(getValue());
                lblCount.setText(getValue().size() + " resultat(s)");
            }
        };
        new Thread(task).start();
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
    private void handleSave() {
        // Validation
        if (txtNomCommercial.getText().trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Validation", "Le nom commercial est obligatoire.");
            return;
        }
        if (txtPrincipeActif.getText().trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Validation", "Le principe actif est obligatoire.");
            return;
        }

        BigDecimal prix;
        try {
            prix = new BigDecimal(txtPrix.getText().trim().replace(",", "."));
            if (prix.compareTo(BigDecimal.ZERO) < 0) {
                throw new NumberFormatException("Prix negatif");
            }
        } catch (Exception e) {
            showAlert(Alert.AlertType.WARNING, "Validation", "Le prix doit etre un nombre positif.");
            return;
        }

        Medicament medicament = isEditMode ? selectedMedicament : new Medicament();
        medicament.setNomCommercial(txtNomCommercial.getText().trim());
        medicament.setPrincipeActif(txtPrincipeActif.getText().trim());
        medicament.setFormeGalenique(comboForme.getValue());
        medicament.setDosage(txtDosage.getText().trim());
        medicament.setPrixPublic(prix);
        medicament.setSeuilMin(spinnerSeuil.getValue());
        medicament.setDescription(txtDescription.getText().trim());
        medicament.setNecessiteOrdonnance(chkOrdonnance.isSelected());
        medicament.setActif(chkActif.isSelected());

        Task<Medicament> task = new Task<>() {
            @Override
            protected Medicament call() throws Exception {
                if (isEditMode) {
                    medicamentDAO.update(medicament);
                    return medicament;
                } else {
                    return medicamentDAO.save(medicament);
                }
            }

            @Override
            protected void succeeded() {
                String message = isEditMode ? "Medicament modifie avec succes." : "Medicament cree avec succes.";
                showAlert(Alert.AlertType.INFORMATION, "Succes", message);
                loadData();
                resetForm();
            }

            @Override
            protected void failed() {
                logger.error("Erreur sauvegarde medicament", getException());
                showAlert(Alert.AlertType.ERROR, "Erreur", "Impossible de sauvegarder: " + getException().getMessage());
            }
        };
        new Thread(task).start();
    }

    @FXML
    private void handleDelete() {
        if (selectedMedicament == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmation");
        confirm.setHeaderText("Supprimer le medicament?");
        confirm.setContentText("Voulez-vous vraiment supprimer \"" + selectedMedicament.getNomCommercial() + "\"?\n\n" +
                "Attention: cette action est irreversible et supprimera egalement tous les lots associes.");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                Task<Void> task = new Task<>() {
                    @Override
                    protected Void call() throws Exception {
                        medicamentDAO.delete(selectedMedicament.getIdMedicament());
                        return null;
                    }

                    @Override
                    protected void succeeded() {
                        showAlert(Alert.AlertType.INFORMATION, "Succes", "Medicament supprime.");
                        loadData();
                        resetForm();
                    }

                    @Override
                    protected void failed() {
                        logger.error("Erreur suppression medicament", getException());
                        showAlert(Alert.AlertType.ERROR, "Erreur",
                                "Impossible de supprimer ce medicament. Il est peut-etre utilise dans des ventes.");
                    }
                };
                new Thread(task).start();
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
