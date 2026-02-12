package com.sgpa.service;

import com.sgpa.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Service de sauvegarde et restauration de la base de donnees.
 * <p>
 * Permet d'effectuer des sauvegardes MySQL via mysqldump
 * et de restaurer depuis un fichier de sauvegarde.
 * </p>
 *
 * @author SGPA Team
 * @version 1.0
 */
public class BackupService {

    private static final Logger logger = LoggerFactory.getLogger(BackupService.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private static final String BACKUP_DIR = System.getProperty("user.home") + "/ApotiCare_Backups";
    private static final String BACKUP_PREFIX = "sgpa_backup_";

    private String dbHost = "localhost";
    private String dbPort = "3306";
    private String dbName = "sgpa_pharmacie";
    private String dbUser;
    private String dbPassword;

    public BackupService() {
        loadDatabaseConfig();
    }

    /**
     * Charge la configuration de la base de donnees.
     */
    private void loadDatabaseConfig() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("database.properties")) {
            if (input == null) {
                logger.error("Fichier database.properties introuvable");
                return;
            }

            Properties props = new Properties();
            props.load(input);

            String url = props.getProperty("db.url", "");
            // Extraire host, port et database depuis l'URL
            // Format: jdbc:mysql://localhost:3306/sgpa_pharmacie?...
            if (url.contains("://")) {
                String urlPart = url.substring(url.indexOf("://") + 3);
                if (urlPart.contains("/")) {
                    String hostPort = urlPart.substring(0, urlPart.indexOf("/"));
                    if (hostPort.contains(":")) {
                        dbHost = hostPort.substring(0, hostPort.indexOf(":"));
                        dbPort = hostPort.substring(hostPort.indexOf(":") + 1);
                    } else {
                        dbHost = hostPort;
                    }
                    String dbPart = urlPart.substring(urlPart.indexOf("/") + 1);
                    if (dbPart.contains("?")) {
                        dbName = dbPart.substring(0, dbPart.indexOf("?"));
                    } else {
                        dbName = dbPart;
                    }
                }
            }

            dbUser = props.getProperty("db.username");
            dbPassword = props.getProperty("db.password");

        } catch (IOException e) {
            logger.error("Erreur lors du chargement de la configuration BDD", e);
        }
    }

    /**
     * Effectue une sauvegarde de la base de donnees.
     *
     * @param compress true pour compresser le fichier (gzip)
     * @return le chemin du fichier de sauvegarde genere
     * @throws ServiceException si une erreur survient
     */
    public String backup(boolean compress) throws ServiceException {
        // Creer le repertoire de backup s'il n'existe pas
        Path backupPath = Paths.get(BACKUP_DIR);
        try {
            Files.createDirectories(backupPath);
        } catch (IOException e) {
            throw new ServiceException("Impossible de creer le repertoire de backup: " + BACKUP_DIR, e);
        }

        // Generer le nom du fichier
        String timestamp = LocalDateTime.now().format(DATE_FORMAT);
        String fileName = BACKUP_PREFIX + timestamp + ".sql";
        if (compress) {
            fileName += ".gz";
        }
        Path outputFile = backupPath.resolve(fileName);

        // Verifier que mysqldump est disponible
        if (!isMysqldumpAvailable()) {
            throw new ServiceException("mysqldump n'est pas disponible. Assurez-vous que MySQL est installe et dans le PATH.");
        }

        // Construire la commande mysqldump
        List<String> command = new ArrayList<>();
        command.add("mysqldump");
        command.add("--host=" + dbHost);
        command.add("--port=" + dbPort);
        command.add("--user=" + dbUser);
        command.add("--password=" + dbPassword);
        command.add("--single-transaction");
        command.add("--routines");
        command.add("--triggers");
        command.add("--add-drop-table");
        command.add(dbName);

        logger.info("Demarrage de la sauvegarde vers: {}", outputFile);

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(false);
            Process process = pb.start();

            // Lire la sortie et ecrire dans le fichier
            if (compress) {
                try (InputStream is = process.getInputStream();
                     GZIPOutputStream gzos = new GZIPOutputStream(new FileOutputStream(outputFile.toFile()))) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        gzos.write(buffer, 0, bytesRead);
                    }
                }
            } else {
                try (InputStream is = process.getInputStream();
                     FileOutputStream fos = new FileOutputStream(outputFile.toFile())) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                    }
                }
            }

            // Lire les erreurs
            StringBuilder errors = new StringBuilder();
            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = errorReader.readLine()) != null) {
                    // Ignorer les avertissements sur le mot de passe
                    if (!line.contains("password on the command line")) {
                        errors.append(line).append("\n");
                    }
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new ServiceException("Erreur mysqldump (code " + exitCode + "): " + errors);
            }

            logger.info("Sauvegarde terminee: {}", outputFile);
            return outputFile.toString();

        } catch (IOException | InterruptedException e) {
            throw new ServiceException("Erreur lors de la sauvegarde", e);
        }
    }

    /**
     * Restaure la base de donnees depuis un fichier de sauvegarde.
     *
     * @param backupFile le chemin du fichier de sauvegarde
     * @throws ServiceException si une erreur survient
     */
    public void restore(String backupFile) throws ServiceException {
        Path filePath = Paths.get(backupFile);
        if (!Files.exists(filePath)) {
            throw new ServiceException("Fichier de sauvegarde introuvable: " + backupFile);
        }

        // Verifier que mysql est disponible
        if (!isMysqlAvailable()) {
            throw new ServiceException("mysql n'est pas disponible. Assurez-vous que MySQL est installe et dans le PATH.");
        }

        // Construire la commande mysql
        List<String> command = new ArrayList<>();
        command.add("mysql");
        command.add("--host=" + dbHost);
        command.add("--port=" + dbPort);
        command.add("--user=" + dbUser);
        command.add("--password=" + dbPassword);
        command.add(dbName);

        logger.info("Demarrage de la restauration depuis: {}", backupFile);

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(false);
            Process process = pb.start();

            // Ecrire le contenu du fichier vers l'entree standard de mysql
            try (OutputStream os = process.getOutputStream()) {
                if (backupFile.endsWith(".gz")) {
                    try (GZIPInputStream gzis = new GZIPInputStream(new FileInputStream(filePath.toFile()))) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = gzis.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                    }
                } else {
                    try (FileInputStream fis = new FileInputStream(filePath.toFile())) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = fis.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                    }
                }
            }

            // Lire les erreurs
            StringBuilder errors = new StringBuilder();
            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = errorReader.readLine()) != null) {
                    if (!line.contains("password on the command line")) {
                        errors.append(line).append("\n");
                    }
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new ServiceException("Erreur mysql (code " + exitCode + "): " + errors);
            }

            logger.info("Restauration terminee depuis: {}", backupFile);

        } catch (IOException | InterruptedException e) {
            throw new ServiceException("Erreur lors de la restauration", e);
        }
    }

    /**
     * Liste les fichiers de sauvegarde disponibles.
     *
     * @return la liste des fichiers de sauvegarde
     */
    public List<BackupFile> listBackups() {
        Path backupPath = Paths.get(BACKUP_DIR);
        if (!Files.exists(backupPath)) {
            return new ArrayList<>();
        }

        try (Stream<Path> files = Files.list(backupPath)) {
            return files
                    .filter(p -> p.getFileName().toString().startsWith(BACKUP_PREFIX))
                    .filter(p -> p.getFileName().toString().endsWith(".sql")
                              || p.getFileName().toString().endsWith(".sql.gz"))
                    .map(p -> {
                        try {
                            return new BackupFile(
                                    p.toString(),
                                    p.getFileName().toString(),
                                    Files.size(p),
                                    Files.getLastModifiedTime(p).toInstant()
                            );
                        } catch (IOException e) {
                            return null;
                        }
                    })
                    .filter(bf -> bf != null)
                    .sorted((a, b) -> b.getModifiedTime().compareTo(a.getModifiedTime()))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            logger.error("Erreur lors de la liste des backups", e);
            return new ArrayList<>();
        }
    }

    /**
     * Supprime un fichier de sauvegarde.
     *
     * @param backupFile le chemin du fichier a supprimer
     * @throws ServiceException si une erreur survient
     */
    public void deleteBackup(String backupFile) throws ServiceException {
        Path filePath = Paths.get(backupFile);
        if (!Files.exists(filePath)) {
            throw new ServiceException("Fichier introuvable: " + backupFile);
        }

        // Securite: verifier que le fichier est dans le repertoire de backup
        if (!filePath.getParent().toString().equals(BACKUP_DIR)) {
            throw new ServiceException("Operation non autorisee");
        }

        try {
            Files.delete(filePath);
            logger.info("Backup supprime: {}", backupFile);
        } catch (IOException e) {
            throw new ServiceException("Impossible de supprimer le fichier", e);
        }
    }

    /**
     * Retourne le repertoire de sauvegarde.
     *
     * @return le chemin du repertoire
     */
    public String getBackupDir() {
        return BACKUP_DIR;
    }

    /**
     * Verifie si mysqldump est disponible.
     */
    private boolean isMysqldumpAvailable() {
        try {
            Process process = new ProcessBuilder("mysqldump", "--version").start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Verifie si mysql est disponible.
     */
    private boolean isMysqlAvailable() {
        try {
            Process process = new ProcessBuilder("mysql", "--version").start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Classe representant un fichier de sauvegarde.
     */
    public static class BackupFile {
        private final String path;
        private final String fileName;
        private final long size;
        private final java.time.Instant modifiedTime;

        public BackupFile(String path, String fileName, long size, java.time.Instant modifiedTime) {
            this.path = path;
            this.fileName = fileName;
            this.size = size;
            this.modifiedTime = modifiedTime;
        }

        public String getPath() {
            return path;
        }

        public String getFileName() {
            return fileName;
        }

        public long getSize() {
            return size;
        }

        public java.time.Instant getModifiedTime() {
            return modifiedTime;
        }

        public String getFormattedSize() {
            if (size < 1024) return size + " B";
            if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        }
    }
}
