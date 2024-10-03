package com.ciphersafe;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;

import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.room.Room;

import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.crypt.EncryptionMode;
import org.apache.poi.poifs.crypt.Encryptor;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Task;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;

import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;


import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

import androidx.work.Data;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;
import com.android.billingclient.api.*;
/**
 * MainActivity class for managing CipherSafe password manager's primary functions.
 * Handles the user interface, Google Drive backup functionality, and subscription management.
 */
public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_SIGN_IN = 1001;
    private ListView passwordListView;
    private AppDatabase db;
    private BillingClient billingClient;
    ImageView cloudImage, downloadImage, addPasswordImage;
    private static final String SUBSCRIPTION_ID = "google_drive_backup_subscription";
    private boolean isSubscribed = false;
    private GoogleSignInClient googleSignInClient;
    private Drive googleDriveService;
    TextView cipherSafeTextView;
    /**
     * Called when the activity is first created. Sets up views, initializes the database,
     * starts the billing connection, and checks the subscription status.
     *
     * @param savedInstanceState If the activity is being re-initialized after being previously shut down,
     *                           this Bundle contains the data it most recently supplied.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE);
        boolean isPolicyAccepted = sharedPreferences.getBoolean("PolicyAccepted", false);

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("IsAuthenticated", false);
        editor.apply();

        setContentView(R.layout.activity_main);
        initializeViews();
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

        setupBillingClient();
        startBillingConnection();
        querySubscriptionStatus();

        setupDatabase();
        if (!isPolicyAccepted) {
            // If not accepted, show the policy acceptance fragment
            showPolicyAcceptanceFragment();
        } else {
            // Check the length of the password list
            if (isPasswordListEmpty()) {
                onFirstUse();
            } else {
                authenticateAppStart();
            }
        }
    }
    /**
     * Checks if the password list is empty. Retrieves all stored passwords from the database
     * and logs the current size of the password list.
     *
     * @return true if the password list is empty, false otherwise.
     */
    private boolean isPasswordListEmpty() {
        setupDatabase();

        List<Password> passwords = db.passwordDao().getAll();
        int passwordListSize = passwords.size();

        Log.d("MainActivity", "Password list size: " + passwordListSize);
        Log.d("MainActivity", "" + passwords.isEmpty());

        return passwords.isEmpty();
    }
    /**
     * Sets up the application for first-time use. Initializes the password list,
     * sets up button listeners, and checks the Google Drive sign-in status.
     */
    private void onFirstUse() {
        // Perform the first-use setup tasks
        setupPasswordList();
        setupButtonListeners();
        passwordListView.setVisibility(View.VISIBLE);
        checkGoogleDriveSignInStatus();
    }
    /**
     * Displays the policy acceptance fragment. This fragment asks the user to accept
     * the application's privacy policy before continuing.
     */
    private void showPolicyAcceptanceFragment() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.fragment_container, new PolicyAcceptanceFragment());
        fragmentTransaction.commit();
    }
    /**
     * Handles the event when the user accepts the privacy policy. Updates the shared preferences
     * to store the policy acceptance status.
     */
    public void onPolicyAccepted() {
        SharedPreferences sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("PolicyAccepted", true);
        editor.apply();
    }
    /**
     * Called when the activity is about to become visible and interactive.
     * It refreshes the current state, ensuring the app checks the subscription
     * and backup status after a resume from the background.
     */
    @Override
    protected void onResume() {
        super.onResume();
        // Optional: Move the authentication logic here if it's more reliable
        //passwordListView.setVisibility(View.GONE);
        SharedPreferences sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE);
        updateCloudIcon();
    }
    /**
     * Sets up the Google Sign-In process for enabling Google Drive backup.
     * This method configures the GoogleSignInClient with the necessary
     * scopes and starts the sign-in process.
     */
    private void setupGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestScopes(new Scope(DriveScopes.DRIVE_FILE))
                .requestEmail()
                .build();
        SharedPreferences sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE);

        String storedPassword = sharedPreferences.getString("EXCEL_PASSWORD", null);

        googleSignInClient = GoogleSignIn.getClient(this, gso);
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null) {
            setupGoogleDriveService();

        } else {
            promptSignIn();
        }
    }

    /**
     * Queries the Google Drive account to verify the current sign-in status.
     * If the user is signed in, this method sets up the Drive API client.
     */
    private void checkGoogleDriveSignInStatus() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        SharedPreferences sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE);

        String storedPassword = sharedPreferences.getString("EXCEL_PASSWORD", null);

        if (account != null) {
            // User is signed in
            cloudImage.setImageResource(R.drawable.cloudenable); // Set image indicating connected
        } else {
            // User is not signed in
            cloudImage.setImageResource(R.drawable.clouddisable); // Set image indicating not connected
        }
    }
    /**
     * Prompts the user to set a password for securing the backup file.
     * This method displays a dialog with a password input field, validates the input,
     * and stores the password securely in SharedPreferences.
     * Once the password is stored, it starts the backup worker for Google Drive backups.
     */
    private void promptForPasswordAndStore() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("Set Password for the File");

        // Inflate a custom view that includes a TextInputLayout for better design
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_set_password, null);
        builder.setView(dialogView);

        // Initialize TextInputLayout and EditText for password input
        TextInputLayout passwordLayout = dialogView.findViewById(R.id.password_layout);
        TextInputEditText passwordEditText = dialogView.findViewById(R.id.input_password);

        // Set up the dialog buttons
        builder.setPositiveButton("OK", null); // Set to null for manual validation handling
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(dialogInterface -> {
            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positiveButton.setOnClickListener(view -> {
                String password = passwordEditText.getText().toString().trim();

                // Validate the password input
                if (password.isEmpty()) {
                    passwordLayout.setError("Password cannot be empty");
                } else {
                    passwordLayout.setError(null); // Clear error

                    // Store the password securely in SharedPreferences
                    SharedPreferences sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE);
                    sharedPreferences.edit().putString("EXCEL_PASSWORD", password).apply();
                    Toast.makeText(this, "Password saved successfully", Toast.LENGTH_SHORT).show();

                    // Start the backup worker now that the password is stored
                    startBackupWorker(password);
                    dialog.dismiss();
                }
            });
        });

        dialog.show();
    }
    /**
     * Initiates the Google Sign-In process to enable Google Drive backup functionality.
     * This method launches the Google sign-in intent and waits for a result.
     * The result is handled in onActivityResult().
     */
    private void promptSignIn() {
        Intent signInIntent = googleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, REQUEST_SIGN_IN);
    }
    /**
     * Handles the result of the Google Sign-In process.
     * This method is triggered after the user attempts to sign in to Google Drive.
     * It processes the result of the sign-in attempt.
     *
     * @param requestCode The integer request code originally supplied to startActivityForResult().
     * @param resultCode The integer result code returned by the child activity through its setResult().
     * @param data An Intent, which can return result data to the caller.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            handleSignInResult(task);
        }
    }
    /**
     * Processes the result of the Google Sign-In flow.
     * If the sign-in was successful, it enables Google Drive backup.
     *
     * @param completedTask The completed task containing the result of the sign-in.
     */
    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            setupGoogleDriveService();
            cloudImage.setImageResource(R.drawable.cloudenable);
            Toast.makeText(this, "Signed in successfully!", Toast.LENGTH_SHORT).show();
        } catch (ApiException e) {
            Log.w("GoogleSignIn", "signInResult:failed code=" + e.getStatusCode());
        }
    }
    /**
     * Sets up the Google Drive service for handling file operations.
     * This method configures the necessary API clients and permissions to interact with Google Drive.
     */
    private void setupGoogleDriveService() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null) {
            GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                    this, Collections.singleton(DriveScopes.DRIVE_FILE));
            credential.setSelectedAccount(account.getAccount());
            googleDriveService = new Drive.Builder(
                    AndroidHttp.newCompatibleTransport(),
                    GsonFactory.getDefaultInstance(), // Changed from JacksonFactory to GsonFactory
                    credential)
                    .setApplicationName("CipherSafe")
                    .build();

            SharedPreferences sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE);
            String storedPassword = sharedPreferences.getString("EXCEL_PASSWORD", null);

            if (storedPassword == null) {
                // Prompt for the password since it's not stored
                promptForPasswordAndStore();
            } else {
                // Start backup with stored password
                startBackupWorker(storedPassword);
            }
        }
    }
    /**
     * Initializes the views and UI components of the activity.
     * This method sets up references to the ListView, ImageViews, and TextView elements in the layout.
     * It also provides a Toast message to inform the user about accessing the User Manual.
     */
    private void initializeViews() {
        passwordListView = findViewById(R.id.password_list);
        cloudImage = findViewById(R.id.cloud_image);
        downloadImage = findViewById(R.id.downloadasexcel);
        addPasswordImage = findViewById(R.id.addpassword);
        cipherSafeTextView = findViewById(R.id.app_title);
        Toast.makeText(this, "Click on CipherSafe to open the User Manual", Toast.LENGTH_SHORT).show();

    }
    /**
     * Sets up the local database using Room for storing and retrieving passwords.
     * This method initializes the Room database and allows querying on the main thread.
     * The database is named "passwords-db".
     */
    private void setupDatabase() {
        db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "passwords-db")
                .allowMainThreadQueries()
                .build();
    }
    /**
     * Initializes the WorkManager to handle periodic backups to Google Drive.
     * This method schedules periodic backup tasks based on user subscription.
     */
    private void startBackupWorker(String password) {
        saveDebugLogToFile("backup worker initiated");

        Data inputData = new Data.Builder()
                .putString("EXCEL_PASSWORD", password)
                .build();

        PeriodicWorkRequest backupWorkRequest =
                new PeriodicWorkRequest.Builder(BackupWorker.class, 15, TimeUnit.MINUTES)
                        .setInputData(inputData)
                        .build();

        WorkManager.getInstance(this).enqueue(backupWorkRequest);
    }
    /**
     * Saves the debug log message to a file named "BackupWorkerLog.txt" in the Downloads/CipherSafe directory.
     * This method checks if the file already exists and appends the log message. If the file doesn't exist,
     * it creates a new one. It handles file operations using the Android `ContentResolver` API.
     *
     * @param logMessage The log message to be saved or appended to the log file.
     */
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
    /**
     * Sets up the password list from the Room database and displays it in the ListView.
     * This method retrieves all passwords from the database and prepares the data for the custom adapter.
     * It also sets an OnItemClickListener to show password details when an item is clicked.
     */
    private void setupPasswordList() {
        List<Password> passwords = db.passwordDao().getAll();
        List<String[]> passwordDataList = new ArrayList<>();
        for (Password password : passwords) {
            passwordDataList.add(new String[]{password.accountName, password.username});
        }
        // Initialize the adapter with the data
        CustomAdapter adapter = new CustomAdapter(this, passwordDataList);
        passwordListView.setAdapter(adapter);

        passwordListView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < adapter.getCount()) {  // Use adapter.getCount() instead of passwordDataList.size()
                String[] selectedAccountData = (String[]) adapter.getItem(position);
                if (selectedAccountData.length > 0) {
                    showPasswordDetails(selectedAccountData[0]);  // Show details using account name
                } else {
                    Log.e("MainActivity", "Selected account data array is empty or has insufficient elements.");
                }
            } else {
                Log.e("MainActivity", "Invalid position: " + position + " for adapter of size " + adapter.getCount());
            }
        });
    }
    /**
     * Loads passwords from the Room database and updates the ListView.
     * This method retrieves the passwords from the database and updates the adapter's data.
     * If the adapter is null, it initializes the adapter and sets it to the ListView.
     */
    private void loadPasswords() {
        List<Password> passwords = db.passwordDao().getAll();

        List<String[]> passwordDataList = new ArrayList<>();
        for (Password password : passwords) {
            passwordDataList.add(new String[]{password.accountName, password.username});
        }

        CustomAdapter adapter = (CustomAdapter) passwordListView.getAdapter();
        if (adapter != null) {
            adapter.updateData(passwordDataList);  // Update the adapter's data and notify it to refresh
        } else {
            // If adapter is null, initialize it and set it to the ListView
            adapter = new CustomAdapter(this, passwordDataList);
            passwordListView.setAdapter(adapter);
        }
    }
    /**
     * Displays the password details for the specified account.
     * This method retrieves the password information from the database,
     * decrypts the password, and shows it in a dialog. The dialog allows the user
     * to edit or delete the password or simply view the details.
     *
     * @param accountName The name of the account for which the password details are being displayed.
     */
    public void showPasswordDetails(String accountName) {
        Password password = db.passwordDao().findByAccountName(accountName);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(accountName);

        // Inflate the custom dialog layout
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_password_details, null);
        //TextInputEditText accountNameEditText = dialogView.findViewById(R.id.dialog_account_name);
        TextInputEditText usernameEditText = dialogView.findViewById(R.id.dialog_username);
        TextInputEditText passwordEditText = dialogView.findViewById(R.id.dialog_password);
        TextInputEditText notesEditText = dialogView.findViewById(R.id.dialog_notes);

        // Set the values from the database
        // accountNameEditText.setText(password.accountName);
        usernameEditText.setText(password.username);

        String decryptedPassword;
        try {
            decryptedPassword = EncryptionUtils.decrypt(password.password);
        } catch (Exception e) {
            decryptedPassword = "[Decryption failed]";
            Log.e("PasswordManager", "Decryption failed", e);
        }
        passwordEditText.setText(decryptedPassword);
        notesEditText.setText(password.notes);

        // Set the custom view to the dialog
        builder.setView(dialogView);

        // Add Edit button
        builder.setNeutralButton("Edit", (dialog, which) -> {
            showEditPasswordDialog(password);
        });

        // Add Delete button
        builder.setNegativeButton("Delete", (dialog, which) -> {
            db.passwordDao().delete(password);
            loadPasswords();
            Toast.makeText(this, "Password deleted", Toast.LENGTH_SHORT).show();
        });

        // Add OK button
        builder.setPositiveButton("OK", (dialog, which) -> dialog.dismiss());

        builder.show();
    }
    /**
     * Displays a dialog to edit the details of an existing password entry.
     * The dialog is pre-filled with the existing password information, allowing the user
     * to modify the account name, username, password, and notes. The password is encrypted before saving.
     *
     * @param password The Password object containing the existing details of the password to be edited.
     */
    private void showEditPasswordDialog(Password password) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("Edit Password");

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_password, null);
        builder.setView(dialogView);

        EditText accountNameEditText = dialogView.findViewById(R.id.dialog_account_name);
        EditText usernameEditText = dialogView.findViewById(R.id.dialog_username);
        EditText passwordEditText = dialogView.findViewById(R.id.dialog_password);
        EditText notesEditText = dialogView.findViewById(R.id.dialog_notes);

        // Pre-fill the fields with existing data
        accountNameEditText.setText(password.accountName);
        usernameEditText.setText(password.username);

        String decryptedPassword;
        try {
            decryptedPassword = EncryptionUtils.decrypt(password.password);
        } catch (Exception e) {
            decryptedPassword = "[Decryption failed]";
            Log.e("PasswordManager", "Decryption failed", e);
        }
        passwordEditText.setText(decryptedPassword);
        notesEditText.setText(password.notes);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String accountName = accountNameEditText.getText().toString();
            String username = usernameEditText.getText().toString();
            String passwordStr = passwordEditText.getText().toString();
            String notes = notesEditText.getText().toString();

            // Update the password entity
            password.accountName = accountName;
            password.username = username;
            try {
                password.password = EncryptionUtils.encrypt(passwordStr);
            } catch (Exception e) {
                Log.e("PasswordManager", "Encryption failed", e);
                Toast.makeText(this, "Failed to encrypt password", Toast.LENGTH_SHORT).show();
                return;
            }
            password.notes = notes;

            // Update the database
            db.passwordDao().update(password);

            // Refresh the list
            loadPasswords();

            Toast.makeText(this, "Password updated", Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        builder.show();
    }
    /**
     * Adds a new password entry to the database after validating the input.
     * This method encrypts the password before storing it and handles errors in encryption.
     * After inserting the password into the database, it updates the password list in the UI.
     *
     * @param accountName The name of the account associated with the password.
     * @param username The username associated with the account.
     * @param password The password to be encrypted and stored.
     * @param notes Additional notes associated with the password.
     */
    private void addPassword(String accountName, String username, String password, String notes) {
        if (accountName == null || accountName.isEmpty()) {
            Toast.makeText(this, "Please enter the account name.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (username == null || username.isEmpty()) {
            Toast.makeText(this, "Please enter the username.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (password == null || password.isEmpty()) {
            Toast.makeText(this, "Please enter the password.", Toast.LENGTH_SHORT).show();
            return;
        }

        String encryptedPassword = null;
        try {
            encryptedPassword = EncryptionUtils.encrypt(password);
        } catch (Exception e) {
            Log.e("PasswordManager", "Encryption failed", e);
        }
        if (encryptedPassword == null) {
            Toast.makeText(this, "Failed to encrypt password", Toast.LENGTH_SHORT).show();
            return;
        }

        Password passwordEntity = new Password();
        passwordEntity.accountName = accountName;
        passwordEntity.username = username;
        passwordEntity.password = encryptedPassword;
        passwordEntity.notes = notes;

        db.passwordDao().insert(passwordEntity);

        // Update the adapter with the new data
        loadPasswords();
    }
    /**
     * Initiates the process of exporting stored passwords to an Excel file.
     * If the Excel password is not set, the user is prompted to set a password before proceeding.
     * Once the password is available, it calls the method to create the Excel file.
     */
    private void exportToExcel() {
        SharedPreferences sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE);
        String password = sharedPreferences.getString("EXCEL_PASSWORD", null);

        if (password == null) {
            // If the password is not set, prompt the user to set it first
            promptForPasswordAndStore();
            return;
        }

        createExcelFile(password);
    }
    /**
     * Creates an encrypted Excel file that contains all the stored passwords.
     * The Excel file includes columns for account name, username, password (decrypted), and notes.
     * The file is saved in the Downloads/CipherSafe folder, and encryption is applied using the provided password.
     *
     * @param password The password used to encrypt the Excel file.
     */
    private void createExcelFile(String password) {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Passwords");

        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Account Name");
        headerRow.createCell(1).setCellValue("Username");
        headerRow.createCell(2).setCellValue("Password");
        headerRow.createCell(3).setCellValue("Notes");

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

        // Generate a unique filename using timestamp
        String timestamp = String.valueOf(System.currentTimeMillis());
        String fileName = "Passwords_" + timestamp + ".xlsx";

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/CipherSafe");

        ContentResolver resolver = getContentResolver();
        Uri externalContentUri = MediaStore.Files.getContentUri("external");
        Uri uri = resolver.insert(externalContentUri, values);

        try (POIFSFileSystem fs = new POIFSFileSystem();
             OutputStream os = resolver.openOutputStream(uri)) {

            EncryptionInfo info = new EncryptionInfo(EncryptionMode.standard);
            Encryptor encryptor = info.getEncryptor();
            encryptor.confirmPassword(password);

            try (OutputStream encOS = encryptor.getDataStream(fs)) {
                workbook.write(encOS);
            }

            fs.writeFilesystem(os);
            Toast.makeText(this, "Exported to Excel successfully as " + fileName, Toast.LENGTH_SHORT).show();

            // Now upload the file to Google Drive
            // uploadFileToGoogleDrive(uri);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to export", Toast.LENGTH_SHORT).show();
        }
    }
    /**
     * Displays the Google Drive backup options dialog based on the user's subscription status.
     * If the user is subscribed, the dialog shows options to enable or disable Google Drive backups.
     * If the user is not subscribed, the dialog prompts the user to subscribe to the CipherSafe subscription.
     */
    private void showBackupOptionsDialog() {

        if (isSubscribed) {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
            builder.setTitle("Google Drive Backup");

            // Determine if backup is enabled
            GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
            boolean isBackupEnabled = (account != null); // Check if Google Drive backup is enabled

            // Inflate a custom view for the dialog message (optional, but can enhance visuals)
            View dialogView = getLayoutInflater().inflate(R.layout.dialog_backup_options, null);
            TextView messageTextView = dialogView.findViewById(R.id.backup_message);

            if (isBackupEnabled) {
                // If backup is enabled, offer the option to disable it
                messageTextView.setText("Google Drive backups are currently enabled. Would you like to disable them?");

                builder.setPositiveButton("Disable", (dialog, which) -> {
                    // Ensure googleSignInClient is initialized before attempting to sign out
                    if (googleSignInClient == null) {
                        googleSignInClient = GoogleSignIn.getClient(this, GoogleSignInOptions.DEFAULT_SIGN_IN);
                    }

                    googleSignInClient.signOut()
                            .addOnCompleteListener(this, task -> {
                                // Handle sign-out
                                cloudImage.setImageResource(R.drawable.clouddisable);
                                Toast.makeText(MainActivity.this, "Google Drive backups disabled", Toast.LENGTH_SHORT).show();
                            });

                    dialog.dismiss();
                });

            } else {
                // If backup is disabled, offer the option to enable it
                messageTextView.setText("Google Drive backups are currently disabled. Would you like to enable them?");

                builder.setPositiveButton("Enable", (dialog, which) -> {
                    // Start the Google Drive sign-in process
                    setupGoogleSignIn();
                    dialog.dismiss();
                });
            }

            // Cancel button for both cases
            builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

            // Set the custom view for the dialog
            builder.setView(dialogView);

            // Show the dialog
            AlertDialog dialog = builder.create();
            dialog.show();
        } else {
            // If not subscribed, show the CipherSafe subscription dialog
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
            builder.setTitle("CipherSafe Subscription Required");

            // Inflate a custom view for the dialog message
            View dialogView = getLayoutInflater().inflate(R.layout.dialog_backup_options, null);
            TextView messageTextView = dialogView.findViewById(R.id.backup_message);

            // Set the message for the user
            messageTextView.setText("To enable Google Drive backup functionality, you need to subscribe to CipherSafe.");

            // Set up "Get it" button
            builder.setPositiveButton("Get it", (dialog, which) -> {
                // Handle subscription flow (e.g., redirect to subscription activity or Google Play)
                promptSubscriptionPurchase();
                dialog.dismiss();
            });

            // Set up "Cancel" button
            builder.setNegativeButton("Cancel", (dialog, which) -> {
                // Simply dismiss the dialog
                dialog.dismiss();
            });

            // Set the custom view to the dialog
            builder.setView(dialogView);

            // Show the dialog
            builder.show();
        }
    }

//    private void showBackupOptionsDialog() {
//        if (isSubscribed) {
////            showSubscriptionSuccessDialog();
//            // if subscribed > proceed with google backup
//            setupGoogleSignIn();
//        } else {
//            promptSubscriptionPurchase();
//        }
//    }
    /**
     * Prompts the user to purchase a CipherSafe subscription using Google Play Billing.
     * This method queries the SKU details for the subscription product and launches the billing flow
     * if the billing client is ready. It handles the SKU query and initiates the purchase process.
     */
    private void promptSubscriptionPurchase() {
        if (billingClient != null && billingClient.isReady()) {
            // Define the list of SKUs to query
            List<String> skuList = new ArrayList<>();
            skuList.add(SUBSCRIPTION_ID);  // Your product ID for the subscription

            // Set up the SKU details query
            SkuDetailsParams params = SkuDetailsParams.newBuilder()
                    .setSkusList(skuList)
                    .setType(BillingClient.SkuType.SUBS)
                    .build();

            // Query for SKU details
            billingClient.querySkuDetailsAsync(params, (billingResult, skuDetailsList) -> {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && skuDetailsList != null) {
                    for (SkuDetails skuDetails : skuDetailsList) {
                        // Get the SKU details for the subscription
                        String price = skuDetails.getPrice();
                        Log.d("BillingClient", "Price for subscription: " + price);

                        // Launch the billing flow to purchase the subscription
                        BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
                                .setSkuDetails(skuDetails)
                                .build();
                        billingClient.launchBillingFlow(MainActivity.this, billingFlowParams);
                    }
                } else {
                    Log.e("BillingClient", "Failed to query SKU details: " + billingResult.getResponseCode());
                }
            });
        } else {
            Log.e("BillingClient", "Billing client is not ready to query SKU details.");
        }
    }
    /**
     * Handles the Google Play subscription purchase for the Google Drive backup.
     * If the purchase is for the subscription ID, it marks the user as subscribed,
     * updates the UI to reflect the cloud backup status, and starts the Google Drive backup process.
     * If the purchase is not acknowledged, it sends an acknowledgment request to Google Play.
     *
     * @param purchase The purchase object that contains details of the user's subscription.
     */
    private void handlePurchase(Purchase purchase) {
        if (purchase.getProducts().contains(SUBSCRIPTION_ID)) {
            isSubscribed = true;
            // Update cloud icon immediately
            updateCloudIcon();
            Log.d("SUB","SHOUDL change cloud and prompt setupgoogle");
            // Start Google Drive backup process if subscribed
            setupGoogleSignIn();
            // Mark the purchase as acknowledged if not already acknowledged.
            if (!purchase.isAcknowledged()) {
                AcknowledgePurchaseParams acknowledgePurchaseParams =
                        AcknowledgePurchaseParams.newBuilder()
                                .setPurchaseToken(purchase.getPurchaseToken())
                                .build();
                billingClient.acknowledgePurchase(acknowledgePurchaseParams, billingResult -> {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        Toast.makeText(this, "Subscription successful", Toast.LENGTH_SHORT).show();
                        showSubscriptionSuccessDialog();
                    }
                });
            }
        }
    }
    /**
     * Displays a dialog informing the user that the subscription was successful.
     * This dialog notifies the user that Google Drive backups are now available.
     * After the user dismisses the dialog, the Google Drive sign-in process is initiated.
     */
    private void showSubscriptionSuccessDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("Subscription Successful");
        builder.setMessage("You are now subscribed to Google Drive Backup. You can start backing up your passwords to Google Drive.");
        builder.setPositiveButton("OK", (dialog, which) -> {
            // Dismiss the dialog
            dialog.dismiss();
        });
        builder.show();

        setupGoogleSignIn();
    }

    /**
     * Authenticates the user when the app starts using biometric or device credentials.
     * It checks if the user has been authenticated before; if not, it shows a biometric prompt.
     * If the authentication succeeds, the app is initialized, including setting up the database,
     * password list, and buttons. If authentication fails, the app is closed.
     */
    private void authenticateAppStart() {
        //Log.d("MainActivity", "Auth should be done");

        SharedPreferences sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE);
        boolean isAuthenticated = sharedPreferences.getBoolean("IsAuthenticated", false);

        if (!isAuthenticated) {
            BiometricManager biometricManager = BiometricManager.from(this);

            // Check biometric capability and log the result
            int canAuthenticateResult = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK);
            Log.d("MainActivity", "Can Authenticate Result: " + canAuthenticateResult);

            if (canAuthenticateResult != BiometricManager.BIOMETRIC_SUCCESS) {
                Log.w("FaceAuth", "Face authentication is not available on this device");
                Toast.makeText(this, "Face authentication is not available. Falling back to PIN/pattern.", Toast.LENGTH_LONG).show();
            }

            BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Biometric Authentication")
                    .setSubtitle("Authenticate to access the app")
                    .setAllowedAuthenticators(
                            BiometricManager.Authenticators.BIOMETRIC_STRONG |  // Strong biometrics like fingerprint or face
                                    BiometricManager.Authenticators.BIOMETRIC_WEAK |    // Weaker biometrics
                                    BiometricManager.Authenticators.DEVICE_CREDENTIAL   // PIN, pattern, or password
                    )
                    .build();

            Executor executor = ContextCompat.getMainExecutor(this);
            BiometricPrompt biometricPrompt = new BiometricPrompt(MainActivity.this, executor, new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationError(int errorCode, CharSequence errString) {
                    super.onAuthenticationError(errorCode, errString);
                    Log.e("FaceAuth", "Authentication error: " + errorCode + " " + errString);
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Authentication error: " + errString, Toast.LENGTH_SHORT).show();
                        if (errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS) {
                            Toast.makeText(MainActivity.this, "No face data enrolled. Please set up face recognition in your device settings.", Toast.LENGTH_LONG).show();
                        }
                        finish(); // Close the app if authentication fails
                    });
                }

                @Override
                public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                    super.onAuthenticationSucceeded(result);
                    runOnUiThread(() -> {
                        if (result.getAuthenticationType() == BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC) {
                            Toast.makeText(MainActivity.this, "Face authentication succeeded", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this, "Device credential authentication succeeded", Toast.LENGTH_SHORT).show();
                        }
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putBoolean("IsAuthenticated", true);
                        editor.apply();



                        setupDatabase();
                        setupPasswordList();
                        setupButtonListeners();
                        passwordListView.setVisibility(View.VISIBLE);
                        checkGoogleDriveSignInStatus();

                    });
                }

                @Override
                public void onAuthenticationFailed() {
                    super.onAuthenticationFailed();
                    Log.e("FaceAuth", "Authentication failed");
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Face authentication failed. Please try again or use your PIN/pattern.", Toast.LENGTH_SHORT).show();
                    });
                }
            });

            try {
                Log.d("MainActivity", "Starting biometric prompt");
                biometricPrompt.authenticate(promptInfo);
            } catch (Exception e) {
                Log.e("FaceAuth", "Exception during authentication", e);
                Toast.makeText(this, "Error during authentication: " + e.getMessage(), Toast.LENGTH_LONG).show();
                finish(); // Close the app if authentication fails
            }
        } else {
            Log.d("MainActivity", "Already authenticated, initializing app");
            setupDatabase();
            setupPasswordList();
            setupButtonListeners();
            passwordListView.setVisibility(View.VISIBLE);
            checkGoogleDriveSignInStatus();
        }
    }
    /**
     * Sets up click listeners for various UI buttons in the activity.
     * Includes listeners for adding a new password, exporting passwords to Excel,
     * showing backup options for Google Drive, and opening the user manual.
     */
    private void setupButtonListeners() {
        addPasswordImage.setOnClickListener(v -> showAddPasswordDialog());

        //exportPasswordsButton.setOnClickListener(v -> authenticateAndExportToExcel());
        downloadImage.setOnClickListener(v -> exportToExcel());

        cloudImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //setupGoogleSignIn();
                showBackupOptionsDialog();
            }
        });
        cipherSafeTextView.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, UserManualActivity.class);
            startActivity(intent);
        });
    }
    /**
     * Updates the cloud icon in the UI based on the user's subscription status.
     * If the user is subscribed, the icon indicates that Google Drive backup is enabled.
     * If the user is not subscribed, the icon indicates that Google Drive backup is disabled.
     */
    private void updateCloudIcon() {
        if (isSubscribed) {
            cloudImage.setImageResource(R.drawable.cloudenable);  // Set the icon to indicate Google Drive is enabled
        } else {
            cloudImage.setImageResource(R.drawable.clouddisable);  // Set the icon to indicate Google Drive is disabled
        }
    }
    /**
     * Displays a dialog for adding a new password entry to the database.
     * The dialog includes input fields for account name, username, password, and notes.
     * It validates the input fields before adding the password to the database and dismissing the dialog.
     */
    private void showAddPasswordDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("Add Password");

        // Inflate the dialog layout with Material TextInputLayouts
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_password, null);
        builder.setView(dialogView);

        // Initialize the TextInputLayouts and EditTexts
        TextInputLayout accountNameLayout = dialogView.findViewById(R.id.layout_account_name);
        TextInputLayout usernameLayout = dialogView.findViewById(R.id.layout_username);
        TextInputLayout passwordLayout = dialogView.findViewById(R.id.layout_password);
        TextInputLayout notesLayout = dialogView.findViewById(R.id.layout_notes);

        EditText accountNameEditText = dialogView.findViewById(R.id.dialog_account_name);
        EditText usernameEditText = dialogView.findViewById(R.id.dialog_username);
        EditText passwordEditText = dialogView.findViewById(R.id.dialog_password);
        EditText notesEditText = dialogView.findViewById(R.id.dialog_notes);

        // Automatically focus the first field
        accountNameEditText.requestFocus();

        // Set up the dialog buttons
        builder.setPositiveButton("Add", null);  // Set to null to override later
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(dialogInterface -> {
            Button addButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            addButton.setOnClickListener(view -> {
                // Get the input values
                String accountName = accountNameEditText.getText().toString().trim();
                String username = usernameEditText.getText().toString().trim();
                String password = passwordEditText.getText().toString().trim();
                String notes = notesEditText.getText().toString().trim();

                // Validate inputs and show errors in TextInputLayouts
                if (accountName.isEmpty()) {
                    accountNameLayout.setError("Please enter the account name");
                } else if (username.isEmpty()) {
                    usernameLayout.setError("Please enter the username");
                } else if (password.isEmpty()) {
                    passwordLayout.setError("Please enter the password");
                } else {
                    // Clear any previous errors
                    accountNameLayout.setError(null);
                    usernameLayout.setError(null);
                    passwordLayout.setError(null);

                    // Add the password to the database
                    addPassword(accountName, username, password, notes);
                    dialog.dismiss();
                }
            });
        });

        dialog.show();
    }
    /**
     * Sets up the Google Play BillingClient for in-app purchases and subscriptions.
     * This method initializes the billing client and sets a listener to handle updates
     * on purchases, including successful purchases, user cancellations, or purchase failures.
     */
    private void setupBillingClient() {
        billingClient = BillingClient.newBuilder(this)
                .enablePendingPurchases()
                .setListener(new PurchasesUpdatedListener() {
                    @Override
                    public void onPurchasesUpdated(BillingResult billingResult, @Nullable List<Purchase> purchases) {
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
                            for (Purchase purchase : purchases) {
                                handlePurchase(purchase);
                            }
                        } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
                            Log.d("BillingClient", "User canceled the purchase.");
                        } else {
                            Log.e("BillingClient", "Purchase failed with code: " + billingResult.getResponseCode());
                        }
                    }
                })
                .build();
    }
    /**
     * Starts the connection to the Google Play Billing service.
     * Once the connection is established, the app is ready to handle in-app purchases and subscriptions.
     * If the connection is successful, a log message is generated and a toast message is shown.
     * If the connection fails, it logs an error message and handles disconnection scenarios.
     */
    private void startBillingConnection() {
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    // BillingClient is ready
                    Log.d("BillingClient", "Billing Client setup finished. Ready to make purchases.");
                    Toast.makeText(MainActivity.this, "Billing Client is connected!", Toast.LENGTH_SHORT).show();
                } else {
                    Log.e("BillingClient", "Billing setup failed with code: " + billingResult.getResponseCode());
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                // Handle disconnection
                Log.e("BillingClient", "Billing service disconnected.");
                Toast.makeText(MainActivity.this, "Billing Client disconnected.", Toast.LENGTH_SHORT).show();
            }
        });
    }
    /**
     * Queries Google Play Billing to check if the user is subscribed
     * to the Google Drive backup feature. It updates the UI and app functionality
     * based on the subscription status.
     */
    private void querySubscriptionStatus() {
        billingClient.queryPurchasesAsync(BillingClient.SkuType.SUBS, (billingResult, purchaseList) -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchaseList != null) {
                for (Purchase purchase : purchaseList) {
                    if (purchase.getProducts().contains(SUBSCRIPTION_ID)) {
                        isSubscribed = true;
                        break;  // If the user is subscribed, we can stop checking further.
                    }
                }
            }
            Log.d("Billing", "Subscription status: " + isSubscribed);
        });
    }




}

