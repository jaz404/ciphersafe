package com.ciphersafe;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.room.Room;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
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
import java.io.IOException;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

public class BackupWorker extends Worker {

    private AppDatabase db;
    private String excelPassword;
    private SharedPreferences sharedPreferences;

    public BackupWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        setupDatabase(context);
        sharedPreferences = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE);

        // Retrieve the password from the input data
        excelPassword = params.getInputData().getString("EXCEL_PASSWORD");
    }

    private void setupDatabase(Context context) {
        db = Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, "passwords-db")
                .allowMainThreadQueries()
                .build();
    }

    @NonNull
    @Override
//    public Result doWork() {
//        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(getApplicationContext());
//        if (account == null) {
//            Log.e("BackupWorker", "Google account not signed in. Backup aborted.");
//            return Result.failure(); // Or Result.retry() based on your use case.
//        }
//        try {
//            String fileId = sharedPreferences.getString("GOOGLE_DRIVE_FILE_ID", null);
//            ByteArrayOutputStream baos = new ByteArrayOutputStream();
//
//            if (fileId == null) {
//                // First-time backup: Create a new file
//                createExcelFile(excelPassword, baos);
//                ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
//                fileId = uploadFileToGoogleDrive("password_backup.xlsx", bais, account);
//                sharedPreferences.edit().putString("GOOGLE_DRIVE_FILE_ID", fileId).apply();
//            } else {
//                // Subsequent backups: Update the existing file
//                createExcelFile(excelPassword, baos);
//                ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
//                updateFileOnGoogleDrive(fileId, bais, account);
//            }
//
//            return Result.success();
//        } catch (Exception e) {
//            e.printStackTrace();
//            return Result.failure();
//        }
//    }
    public Result doWork() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(getApplicationContext());
        if (account == null) {
            Log.e("BackupWorker", "Google account not signed in. Backup aborted.");
            saveDebugLogToFile("Backup aborted: Google account not signed in.");
            return Result.failure(); // Or Result.retry() based on your use case.
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
                saveDebugLogToFile("New backup file created with ID: " + fileId);
            } else {
                // Subsequent backups: Update the existing file
                createExcelFile(excelPassword, baos);
                ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
                updateFileOnGoogleDrive(fileId, bais, account);
                saveDebugLogToFile("Backup file updated with ID: " + fileId);
            }

            saveDebugLogToFile("Backup completed successfully.");
            return Result.success();
        } catch (Exception e) {
            Log.e("BackupWorker", "Backup failed", e);
            saveDebugLogToFile("Backup failed: " + e.getMessage());
            return Result.failure();
        }
    }
    private void saveDebugLogToFile(String logMessage) {
        String fileName = "BackupWorkerLog.txt"; // Single file to append logs

        ContentResolver resolver = getApplicationContext().getContentResolver();
        Uri externalContentUri = MediaStore.Files.getContentUri("external");

        // Query the Downloads folder to check if the file already exists
        String selection = MediaStore.MediaColumns.DISPLAY_NAME + "=?";
        String[] selectionArgs = new String[]{fileName};

        Uri fileUri = null;

        try (Cursor cursor = resolver.query(externalContentUri, new String[]{MediaStore.MediaColumns._ID}, selection, selectionArgs, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                // File exists, retrieve its URI
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID));
                fileUri = Uri.withAppendedPath(externalContentUri, String.valueOf(id));
            }
        }

        if (fileUri == null) {
            // File doesn't exist, create a new one
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/CipherSafe");

            fileUri = resolver.insert(externalContentUri, values);
        }

        try (OutputStream os = resolver.openOutputStream(fileUri, "wa")) { // "wa" is for write-append mode
            if (os != null) {
                os.write((logMessage + "\n").getBytes()); // Append log message with newline
                os.flush();
                Log.d("BackupWorker", "Debug log saved/updated in Downloads folder");
            }
        } catch (Exception e) {
            Log.e("BackupWorker", "Failed to save/update debug log", e);
        }
    }



    private void createExcelFile(String password, ByteArrayOutputStream baos) throws IOException {

        saveDebugLogToFile("Creating excel file"+" "+password);

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
            } catch (Exception e) {
                Log.e("PasswordManager", "Decryption failed", e);
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
            throw new IOException("Failed to encrypt the Excel file", e);
        }

        // Write the encrypted POIFSFileSystem to the final output stream
        fs.writeFilesystem(baos);

        Log.d("PasswordManager", "Excel file created successfully in memory with password protection");
    }

private String uploadFileToGoogleDrive(String fileName, ByteArrayInputStream bais, GoogleSignInAccount account) throws IOException {
    saveDebugLogToFile("Starting upload of file: " + fileName);
    byte[] fileBytes = bais.readAllBytes();

    GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
            getApplicationContext(), Collections.singleton(DriveScopes.DRIVE_FILE));
    credential.setSelectedAccount(account.getAccount());

    Drive googleDriveService = new Drive.Builder(
            AndroidHttp.newCompatibleTransport(),
            new GsonFactory(),
            credential)
            .setApplicationName("PasswordManager")
            .build();

    // Check if "cipherSafe" directory exists
    String cipherSafeFolderId = getOrCreateCipherSafeFolder(googleDriveService);

    // Create file metadata with the folder ID
    File fileMetadata = new File();
    fileMetadata.setName(fileName);
    fileMetadata.setParents(Collections.singletonList(cipherSafeFolderId));

    ByteArrayContent mediaContent = new ByteArrayContent("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", fileBytes);

    File file = googleDriveService.files().create(fileMetadata, mediaContent)
            .setFields("id")
            .execute();

    // Set permissions to make the file private or restricted
    Permission permission = new Permission()
            .setType("user")
            .setRole("writer")
            .setEmailAddress(account.getEmail());

    googleDriveService.permissions().create(file.getId(), permission).execute();

    Log.d("BackupWorker", "File uploaded to Google Drive: " + file.getId());

    return file.getId();

}

    private void updateFileOnGoogleDrive(String fileId, ByteArrayInputStream bais, GoogleSignInAccount account) throws IOException {
        saveDebugLogToFile("Starting update of file with ID: " + fileId);
        byte[] fileBytes = bais.readAllBytes();

        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Collections.singleton(DriveScopes.DRIVE_FILE));
        credential.setSelectedAccount(account.getAccount());

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
        saveDebugLogToFile("File updated on Google Drive with ID: " + fileId);

    }

    private String getOrCreateCipherSafeFolder(Drive googleDriveService) throws IOException {
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
            return folder.getId();
        } else {
            // Folder exists, return its ID
            Log.d("BackupWorker", "'cipherSafe' folder already exists with ID: " + result.getFiles().get(0).getId());
            return result.getFiles().get(0).getId();
        }
    }

}
