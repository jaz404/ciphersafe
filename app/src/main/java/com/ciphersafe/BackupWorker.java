package com.ciphersafe;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.room.Room;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.Permission;
import com.google.api.services.drive.DriveScopes;

import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.crypt.EncryptionMode;
import org.apache.poi.poifs.crypt.Encryptor;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
/**
 * BackupWorker is a custom worker that handles the backup of user data (passwords) to Google Drive.
 * It uses Room to access local data and Google Drive API for cloud storage.
 */
public class BackupWorker extends Worker {
    private AppDatabase db;
    private String excelPassword;
    private SharedPreferences sharedPreferences;

    /**
     * Constructor for BackupWorker.
     *
     * @param context The context of the application.
     * @param params  The parameters passed to the worker.
     */
    public BackupWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        setupDatabase(context);
        sharedPreferences = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE);
        // Retrieve the password from the input data
        excelPassword = params.getInputData().getString("EXCEL_PASSWORD");
        saveDebugLogToFile("File: BackupWorker Function: Worker Constructor"+" "+"Message: Worker has retrieved the password for the excel file");
    }
    /**
     * Initializes the Room database for accessing local data.
     *
     * @param context The application context.
     */
    private void setupDatabase(Context context) {
        try {
            db = Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, "passwords-db")
                    .allowMainThreadQueries()
                    .build();
            saveDebugLogToFile("File: BackupWorker Function: setupDatabase"+" "+"Database has been setup");

        } catch (Exception e) {
            saveDebugLogToFile("File: BackupWorker Function: setupDatabase"+" Message: An error occurred while initializing the database: " + e.getMessage());
        }
    }
    /**
     * The main function that performs the backup operation.
     * It handles creating or updating backup files on Google Drive.
     *
     * @return Result of the work, either success or failure.
     */
    @NonNull
    @Override
    public Result doWork() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(getApplicationContext());
        if (account == null) {
            Log.e("BackupWorker", "Google account not signed in. Backup aborted.");

            saveDebugLogToFile("File: BackupWorker Function: dowork"+" Message: Backup aborted Google account not signed in.");
            return Result.failure();
        }
        try {
            String fileId = sharedPreferences.getString("GOOGLE_DRIVE_FILE_ID", null);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            if (fileId == null) {
                // First-time backup: Create a new file
                createExcelFile(excelPassword, baos);
                ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
                fileId = uploadFileToGoogleDrive("password_backup.xlsx", bais, account);
                sharedPreferences.edit().putString("GOOGLE_DRIVE_FILE_ID", fileId).apply();
                //saveDebugLogToFile("New backup file created with ID: " + fileId);

                saveDebugLogToFile("File: BackupWorker Function: dowork"+" Message: FileID null so New backup file created");

            } else {
                // Subsequent backups: Update the existing file
                createExcelFile(excelPassword, baos);
                ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
                updateFileOnGoogleDrive(fileId, bais, account);
               // saveDebugLogToFile("Backup file updated with ID: " + fileId);
                saveDebugLogToFile("File: BackupWorker Function: dowork"+" Message: FileID not null so updated existing backup file");
            }

            saveDebugLogToFile("File: BackupWorker Function: dowork"+" Message: backup was successful");
            return Result.success();
        } catch (Exception e) {
            Log.e("BackupWorker", "Backup failed", e);
            saveDebugLogToFile("Backup failed: " + e.getMessage());
            return Result.failure();
        }
    }
    /**
     * Saves a debug log message to a file in the cache directory.
     *
     * @param logMessage The message to be logged.
     */
    private void saveDebugLogToFile(String logMessage) {
        String fileName = "BackupWorkerLog.txt"; // Single file to append logs

        // Get device information
        String deviceInfo = getDeviceInfo();

        // Get the cache directory
        java.io.File cacheDir = getApplicationContext().getCacheDir();
        java.io.File logFile = new java.io.File(cacheDir, fileName);

        // Log to ensure the cache directory exists
        if (!cacheDir.exists()) {
            Log.e("BackupWorker", "Cache directory does not exist. Trying to create it...");
            if (!cacheDir.mkdirs()) {
                Log.e("BackupWorker", "Failed to create cache directory.");
                return; // Abort if the directory couldn't be created
            }
        }

        Log.d("BackupWorker", "Cache directory path: " + cacheDir.getAbsolutePath());

        try (FileWriter fw = new FileWriter(logFile, true)) { // 'true' for append mode
            String timestampedMessage = "[" + getCurrentTimestamp() + "] " + deviceInfo + logMessage + "\n";
            fw.write(timestampedMessage);
            fw.flush();
            Log.d("BackupWorker", "Debug log saved/updated in cache directory: " + logFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e("BackupWorker", "Failed to save/update debug log in cache directory: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Helper method to get the current timestamp
    /**
     * Helper method to get the current timestamp.
     *
     * @return A string representing the current timestamp.
     */
    private String getCurrentTimestamp() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new java.util.Date());
    }

    // Helper method to get device information
    /**
     * Helper method to get device information such as Android version, phone model, and Wi-Fi status.
     *
     * @return A string containing the device information.
     */
    private String getDeviceInfo() {
        StringBuilder deviceInfo = new StringBuilder();
        deviceInfo.append("Device Info: \n");
        deviceInfo.append("Android Version: ").append(android.os.Build.VERSION.RELEASE).append("\n");
        deviceInfo.append("Phone Model: ").append(android.os.Build.MODEL).append("\n");
        deviceInfo.append("Manufacturer: ").append(android.os.Build.MANUFACTURER).append("\n");

        // Wi-Fi status
        android.net.wifi.WifiManager wifiManager = (android.net.wifi.WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            if (wifiManager.isWifiEnabled()) {
                deviceInfo.append("Wi-Fi Status: Enabled\n");
                android.net.wifi.WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                deviceInfo.append("Connected to: ").append(wifiInfo.getSSID()).append("\n");
                deviceInfo.append("Signal Strength: ").append(wifiInfo.getRssi()).append(" dBm\n");
            } else {
                deviceInfo.append("Wi-Fi Status: Disabled\n");
            }
        } else {
            deviceInfo.append("Wi-Fi Info: Unavailable\n");
        }

        return deviceInfo.toString();
    }
    /**
     * Creates an encrypted Excel file containing the user's passwords.
     *
     * @param password The password used for encrypting the file.
     * @param baos     The output stream to which the Excel file will be written.
     * @throws IOException If an error occurs while creating or encrypting the file.
     */
    private void createExcelFile(String password, ByteArrayOutputStream baos) throws IOException {
        saveDebugLogToFile("File: BackupWorker Function: createExcelFile"+" Message: Worker is creating an excel file");

//        saveDebugLogToFile("Creating excel file"+" "+password);

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Passwords");

        // Create header row
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Account Name");
        headerRow.createCell(1).setCellValue("Username");
        headerRow.createCell(2).setCellValue("Password");
        headerRow.createCell(3).setCellValue("Notes");

        // Populate sheet with data
        List<Password> passwords = db.passwordDao().getAll();
        int rowIndex = 1;
        for (Password passwordObj : passwords) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(passwordObj.accountName);
            row.createCell(1).setCellValue(passwordObj.username);
            String decryptedPassword = null;
            try {
                decryptedPassword = EncryptionUtils.decrypt(passwordObj.password);
                saveDebugLogToFile("File: BackupWorker Function: createExcelFile"+" Message: Decryption Successful");

            } catch (Exception e) {
                Log.e("PasswordManager", "Decryption failed", e);
                saveDebugLogToFile("File: BackupWorker Function: createExcelFile"+" Message: Decryption Failed");

            }
            row.createCell(2).setCellValue(decryptedPassword != null ? decryptedPassword : "[Decryption failed]");
            row.createCell(3).setCellValue(passwordObj.notes);
        }

        // Apply password protection
        POIFSFileSystem fs = new POIFSFileSystem();
        EncryptionInfo info = new EncryptionInfo(EncryptionMode.standard);
        Encryptor encryptor = info.getEncryptor();
        encryptor.confirmPassword(password);

        try (ByteArrayOutputStream tempOut = new ByteArrayOutputStream()) {
            workbook.write(tempOut);
            workbook.close();

            try (OutputStream encryptedOutputStream = encryptor.getDataStream(fs)) {
                tempOut.writeTo(encryptedOutputStream);

            }
        } catch (GeneralSecurityException e) {
            saveDebugLogToFile("File: BackupWorker Function: createExcelFile"+" Message: Failed to encrypt the Excel file "+e);
            throw new IOException("Failed to encrypt the Excel file", e);

        }
        // Write the encrypted POIFSFileSystem to the final output stream
        fs.writeFilesystem(baos);
        saveDebugLogToFile("File: BackupWorker Function: createExcelFile"+" Message: Successfully encrypted the Excel file");
    }
    /**
     * Reads all bytes from a ByteArrayInputStream and returns them as a byte array.
     * This method reads the input stream into a buffer and returns the complete byte array.
     *
     * @param bais The ByteArrayInputStream to read from.
     * @return A byte array containing all the bytes read from the input stream.
     * @throws IOException If an error occurs while reading the input stream.
     */
    private byte[] readAllBytesCompat(ByteArrayInputStream bais) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[16384]; // 16KB buffer size

        while ((nRead = bais.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }

        buffer.flush();
        return buffer.toByteArray();
    }
    /**
     * Uploads a file to Google Drive. If successful, the method returns the file ID of the uploaded file.
     *
     * @param fileName The name of the file to be uploaded.
     * @param bais     The ByteArrayInputStream containing the file data to be uploaded.
     * @param account  The GoogleSignInAccount representing the signed-in Google account.
     * @return The file ID of the uploaded file on Google Drive, or null if the upload fails.
     * @throws IOException If an error occurs during file upload or Google Drive API interaction.
     */
    private String uploadFileToGoogleDrive(String fileName, ByteArrayInputStream bais, GoogleSignInAccount account) throws IOException {
        // Log that the worker has started
        saveDebugLogToFile("File: BackupWorker Function: uploadFileToGoogleDrive Message: Worker has started googleDriveUpload");

        String fileId = null; // Variable to store file ID if the upload succeeds

        try {
            // Read file bytes from input stream
            byte[] fileBytes = readAllBytesCompat(bais);

            // Set up Google Drive credentials
            GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                    getApplicationContext(), Collections.singleton(DriveScopes.DRIVE_FILE));
            credential.setSelectedAccount(account.getAccount());

            // Create Google Drive service
            Drive googleDriveService = new Drive.Builder(
                    AndroidHttp.newCompatibleTransport(),
                    new GsonFactory(),
                    credential)
                    .setApplicationName("PasswordManager")
                    .build();

            // Check if "cipherSafe" directory exists or create it
            String cipherSafeFolderId = getOrCreateCipherSafeFolder(googleDriveService);

            // Set file metadata
            File fileMetadata = new File();
            fileMetadata.setName(fileName);
            fileMetadata.setParents(Collections.singletonList(cipherSafeFolderId));

            // Create file content
            ByteArrayContent mediaContent = new ByteArrayContent(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", fileBytes);

            // Upload file to Google Drive
            File file = googleDriveService.files().create(fileMetadata, mediaContent)
                    .setFields("id")
                    .execute();

            // Set file permissions
            Permission permission = new Permission()
                    .setType("user")
                    .setRole("writer")
                    .setEmailAddress(account.getEmail());
            googleDriveService.permissions().create(file.getId(), permission).execute();

            Log.d("BackupWorker", "File uploaded to Google Drive: " + file.getId());
            fileId = file.getId(); // Store file ID
            saveDebugLogToFile("File: BackupWorker Function: uploadFileToGoogleDrive Message: File Uploaded Successfully to Google Drive");


        }catch (GoogleJsonResponseException e) {
            Log.e("BackupWorker", "Google Drive API error: " + e.getDetails(), e);
            saveDebugLogToFile("File: BackupWorker Function: uploadFileToGoogleDrive Message: GoogleJsonResponseException occurred during file upload");

        }catch (IOException e) {
            Log.e("BackupWorker", "IO Exception occurred during file upload: " + e.getMessage(), e);
            saveDebugLogToFile("File: BackupWorker Function: uploadFileToGoogleDrive Message: IOException occurred during file upload");
            throw e; // Re-throw the IOException to propagate it up

        } catch (Exception e) {
            Log.e("BackupWorker", "Unexpected error occurred: " + e.getMessage(), e);
            saveDebugLogToFile("File: BackupWorker Function: uploadFileToGoogleDrive Message: Unexpected error occurred during file upload");
        }
        return fileId; // Return the file ID or null if upload failed
    }
    /**
     * Updates an existing file on Google Drive with new data. If successful, the file is updated with the new content.
     *
     * @param fileId  The ID of the file on Google Drive to be updated.
     * @param bais    The ByteArrayInputStream containing the updated file data.
     * @param account The GoogleSignInAccount representing the signed-in Google account.
     * @throws IOException If an error occurs during file update or Google Drive API interaction.
     */
    private void updateFileOnGoogleDrive(String fileId, ByteArrayInputStream bais, GoogleSignInAccount account) throws IOException {
        // Log that the worker has started
        saveDebugLogToFile("File: BackupWorker Function: updateFileOnGoogleDrive Message: Worker has started google drive file updation");

        try {
            // Read file bytes from input stream
            byte[] fileBytes = readAllBytesCompat(bais);

            // Set up Google Drive credentials
            GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                    getApplicationContext(), Collections.singleton(DriveScopes.DRIVE_FILE));
            credential.setSelectedAccount(account.getAccount());

            // Create Google Drive service
            Drive googleDriveService = new Drive.Builder(
                    AndroidHttp.newCompatibleTransport(),
                    new GsonFactory(),
                    credential)
                    .setApplicationName("PasswordManager")
                    .build();

            // Check if "cipherSafe" directory exists
            String cipherSafeFolderId = getOrCreateCipherSafeFolder(googleDriveService);

            // Update the file content
            ByteArrayContent mediaContent = new ByteArrayContent("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", fileBytes);

            // Update the file on Google Drive
            File file = googleDriveService.files().update(fileId, null, mediaContent)
                    .execute();

            // If the file isn't already in the "cipherSafe" folder, move it there
            if (!file.getParents().contains(cipherSafeFolderId)) {
                googleDriveService.files().update(fileId, null)
                        .setAddParents(cipherSafeFolderId)
                        .setRemoveParents(file.getParents().get(0)) // assuming there's only one parent
                        .setFields("id, parents")
                        .execute();
            }

            Log.d("BackupWorker", "File updated and moved to cipherSafe folder on Google Drive: " + file.getId());
            //saveDebugLogToFile("File updated on Google Drive with ID: " + fileId);
            saveDebugLogToFile("File: BackupWorker Function: updateFileOnGoogleDrive Message: Worker has successfully updated the file");

        } catch (GoogleJsonResponseException e) {
            Log.e("BackupWorker", "Google Drive API error during update: " + e.getDetails(), e);
            saveDebugLogToFile("File: BackupWorker Function: updateFileOnGoogleDrive Message: GoogleJsonResponseException occurred during file update");

        } catch (IOException e) {
            Log.e("BackupWorker", "IO Exception occurred during file update: " + e.getMessage(), e);
            saveDebugLogToFile("File: BackupWorker Function: updateFileOnGoogleDrive Message: IOException occurred during file update");
            throw e; // Re-throw the IOException to propagate it up

        } catch (Exception e) {
            Log.e("BackupWorker", "Unexpected error occurred during file update: " + e.getMessage(), e);
            saveDebugLogToFile("File: BackupWorker Function: updateFileOnGoogleDrive Message: Unexpected error occurred during file update");
        }
    }
    /**
     * Retrieves the "cipherSafe" folder ID on Google Drive, creating the folder if it does not exist.
     *
     * @param googleDriveService The Google Drive service instance used to interact with Google Drive.
     * @return The ID of the "cipherSafe" folder on Google Drive, or null if the folder could not be created or found.
     * @throws IOException If an error occurs while checking or creating the folder on Google Drive.
     */
    private String getOrCreateCipherSafeFolder(Drive googleDriveService) throws IOException {
        saveDebugLogToFile("File: BackupWorker Function: getOrCreateCipherSafeFolder Message: -");

        String folderId = null; // Variable to store the folder ID
        try {
            // Check if the "cipherSafe" folder already exists
            FileList result = googleDriveService.files().list()
                    .setQ("mimeType='application/vnd.google-apps.folder' and name='cipherSafe' and trashed=false")
                    .setSpaces("drive")
                    .setFields("files(id, name)")
                    .execute();

            if (result.getFiles().isEmpty()) {
                // Folder does not exist, create it
                File fileMetadata = new File();
                fileMetadata.setName("cipherSafe");
                fileMetadata.setMimeType("application/vnd.google-apps.folder");

                File folder = googleDriveService.files().create(fileMetadata)
                        .setFields("id")
                        .execute();

                Log.d("BackupWorker", "'cipherSafe' folder created with ID: " + folder.getId());
                folderId = folder.getId(); // Store the folder ID
                saveDebugLogToFile("File: BackupWorker Function: getOrCreateCipherSafeFolder Message: Folder did not exist so created");


            } else {
                // Folder exists, return its ID
                Log.d("BackupWorker", "'cipherSafe' folder already exists with ID: " + result.getFiles().get(0).getId());
                folderId = result.getFiles().get(0).getId(); // Store the folder ID
                saveDebugLogToFile("File: BackupWorker Function: getOrCreateCipherSafeFolder Message: folder already existed so returned the ID");

            }

        } catch (GoogleJsonResponseException e) {
            Log.e("BackupWorker", "Google Drive API error when checking or creating cipherSafe folder: " + e.getDetails(), e);
            saveDebugLogToFile("File: BackupWorker Function: getOrCreateCipherSafeFolder Message: GoogleJsonResponseException occurred");

        } catch (IOException e) {
            Log.e("BackupWorker", "IO Exception occurred when checking or creating cipherSafe folder: " + e.getMessage(), e);
            saveDebugLogToFile("File: BackupWorker Function: getOrCreateCipherSafeFolder Message: IOException occurred");
            throw e; // Re-throw the IOException to propagate it up

        } catch (Exception e) {
            Log.e("BackupWorker", "Unexpected error occurred when checking or creating cipherSafe folder: " + e.getMessage(), e);
            saveDebugLogToFile("File: BackupWorker Function: getOrCreateCipherSafeFolder Message: Unexpected error occurred");
        }

        return folderId; // Return the folder ID or null if failed
    }


}
