package com.example.myapplication;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

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
import com.google.api.services.drive.model.Permission;
import com.google.api.services.drive.DriveScopes;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class BackupWorker extends Worker {

    private AppDatabase db;

    public BackupWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        setupDatabase(context);
    }

    private void setupDatabase(Context context) {
        db = Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, "passwords-db")
                .allowMainThreadQueries()
                .build();
    }

    @NonNull
    @Override
    public Result doWork() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(getApplicationContext());
        if (account == null) {
            Log.e("BackupWorker", "Google account not signed in. Backup aborted.");
            return Result.failure(); // Or Result.retry() based on your use case.
        }
        try {
            String password = "test";
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            createExcelFile(password, baos);
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            uploadFileToGoogleDrive("password_backup.xlsx", bais, account);
            return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Result.failure();
        }
    }

    private void showToast(final String message) {
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show()
        );
    }

    private void createExcelFile(String password, ByteArrayOutputStream baos) throws IOException {
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
        }

        workbook.write(baos);
        workbook.close();
        Log.d("PasswordManager", "Excel file created successfully in memory");
        showToast("Exported to Excel successfully");
    }

    private void uploadFileToGoogleDrive(String fileName, ByteArrayInputStream bais, GoogleSignInAccount account) throws IOException {
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

        com.google.api.services.drive.model.File fileMetadata = new com.google.api.services.drive.model.File();
        fileMetadata.setName(fileName);

        ByteArrayContent mediaContent = new ByteArrayContent("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", fileBytes);

        new Thread(() -> {
            try {
                com.google.api.services.drive.model.File file = googleDriveService.files().create(fileMetadata, mediaContent)
                        .setFields("id")
                        .execute();

                // Set permissions to make the file private or restricted
                Permission permission = new Permission()
                        .setType("user")
                        .setRole("writer")
                        .setEmailAddress(account.getEmail());

                googleDriveService.permissions().create(file.getId(), permission).execute();

                Log.d("BackupWorker", "File uploaded to Google Drive: " + file.getId());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }
}
