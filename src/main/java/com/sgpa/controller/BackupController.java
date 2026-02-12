package com.sgpa.controller;

import com.sgpa.service.BackupService;
import com.sgpa.service.BackupService.BackupFile;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Controleur pour l'ecran de sauvegarde/restauration de la base de donnees.
 *
 * @author SGPA Team
 * @version 1.0
 */
public class BackupController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(BackupController.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    @FXML private Label lblBackupDir;
    @FXML private CheckBox chkCompress;
    @FXML private ProgressIndicator progressIndicator;
    @FXML private Label lblStatus;
    @FXML private Label lblCount;

    @FXML private TableView<BackupFile> tableBackups;
    @FXML private TableColumn<BackupFile, String> colFileName;
    @FXML private TableColumn<BackupFile, String> colDate;
    @FXML private TableColumn<BackupFile, String> colSize;
    @FXML private TableColumn<BackupFile, Void> colActions;

    private final BackupService backupService;
    private final ObservableList<BackupFile> backupData = FXCollections.observableArrayList();

    public BackupController() {
        this.backupService = new BackupService();
    }

    @FXML
    public void initialize() {
        lblBackupDir.setText("Repertoire de sauvegarde: " + backupService.getBackupDir());
        setupTable();
        setupResponsiveTable(tableBackups);
        loadBackups();
    }

    private void setupTable() {
        colFileName.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getFileName()));

        colDate.setCellValueFactory(data ->
                new SimpleStringProperty(
                        data.getValue().getModifiedTime()
                                .atZone(ZoneId.systemDefault())
                                .format(DATE_FORMAT)
                ));

        colSize.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getFormattedSize()));

        // Colonne actions avec boutons
        colActions.setCellFactory(column -> new TableCell<>() {
            private final Button btnRestore = new Button("Restaurer");
            private final Button btnDelete = new Button();
            private final HBox hbox = new HBox(10);

            {
                btnRestore.setStyle("-fx-background-color: #17a2b8; -fx-text-fill: white;");
                btnRestore.setGraphic(new FontIcon("fas-undo"));

                btnDelete.setGraphic(new FontIcon("fas-trash"));
                btnDelete.setStyle("-fx-background-color: #dc3545; -fx-text-fill: white;");

                hbox.setAlignment(Pos.CENTER);
                hbox.getChildren().addAll(btnRestore, btnDelete);

                btnRestore.setOnAction(event -> {
                    BackupFile backup = getTableView().getItems().get(getIndex());
                    handleRestore(backup);
                });

                btnDelete.setOnAction(event -> {
                    BackupFile backup = getTableView().getItems().get(getIndex());
                    handleDelete(backup);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : hbox);
            }
        });

        tableBackups.setItems(backupData);
    }

    private void loadBackups() {
        List<BackupFile> backups = backupService.listBackups();
        backupData.setAll(backups);
        lblCount.setText(backups.size() + " sauvegarde(s)");
    }

    @FXML
    private void handleBackup() {
        setLoading(true, "Sauvegarde en cours...");

        Task<String> backupTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                return backupService.backup(chkCompress.isSelected());
            }
        };

        backupTask.setOnSucceeded(event -> {
            setLoading(false, "Sauvegarde terminee!");
            String filePath = backupTask.getValue();
            loadBackups();

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Sauvegarde reussie");
            alert.setHeaderText("La base de donnees a ete sauvegardee");
            alert.setContentText("Fichier: " + filePath);
            alert.showAndWait();
        });

        backupTask.setOnFailed(event -> {
            setLoading(false, "Erreur!");
            logger.error("Erreur de sauvegarde", backupTask.getException());

            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Erreur");
            alert.setHeaderText("Echec de la sauvegarde");
            alert.setContentText(backupTask.getException().getMessage());
            alert.showAndWait();
        });

        new Thread(backupTask).start();
    }

    private void handleRestore(BackupFile backup) {
        // Confirmation
        Alert confirm = new Alert(Alert.AlertType.WARNING);
        confirm.setTitle("Confirmer la restauration");
        confirm.setHeaderText("Restaurer la base de donnees?");
        confirm.setContentText(
                "ATTENTION: Cette action va ecraser toutes les donnees actuelles!\n\n" +
                        "Fichier: " + backup.getFileName() + "\n" +
                        "Date: " + backup.getModifiedTime().atZone(ZoneId.systemDefault()).format(DATE_FORMAT) + "\n\n" +
                        "Voulez-vous continuer?"
        );

        ButtonType btnConfirm = new ButtonType("Restaurer", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCancel = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);
        confirm.getButtonTypes().setAll(btnConfirm, btnCancel);

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != btnConfirm) {
            return;
        }

        setLoading(true, "Restauration en cours...");

        Task<Void> restoreTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                backupService.restore(backup.getPath());
                return null;
            }
        };

        restoreTask.setOnSucceeded(event -> {
            setLoading(false, "Restauration terminee!");

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Restauration reussie");
            alert.setHeaderText("La base de donnees a ete restauree");
            alert.setContentText("Veuillez redemarrer l'application pour appliquer les changements.");
            alert.showAndWait();
        });

        restoreTask.setOnFailed(event -> {
            setLoading(false, "Erreur!");
            logger.error("Erreur de restauration", restoreTask.getException());

            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Erreur");
            alert.setHeaderText("Echec de la restauration");
            alert.setContentText(restoreTask.getException().getMessage());
            alert.showAndWait();
        });

        new Thread(restoreTask).start();
    }

    private void handleDelete(BackupFile backup) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmer la suppression");
        confirm.setHeaderText("Supprimer cette sauvegarde?");
        confirm.setContentText("Fichier: " + backup.getFileName());

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }

        try {
            backupService.deleteBackup(backup.getPath());
            loadBackups();
            lblStatus.setText("Sauvegarde supprimee");
        } catch (Exception e) {
            logger.error("Erreur suppression backup", e);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Erreur");
            alert.setHeaderText("Impossible de supprimer");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
    }

    @FXML
    private void handleRefresh() {
        loadBackups();
        lblStatus.setText("Liste actualisee");
    }

    private void setLoading(boolean loading, String message) {
        progressIndicator.setVisible(loading);
        lblStatus.setText(message);
    }
}
