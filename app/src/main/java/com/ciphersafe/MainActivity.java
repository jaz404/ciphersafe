package com.ciphersafe;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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


import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
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
        saveDebugLogToFile("onCreate: App started.");
        SharedPreferences sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE);
        boolean isPolicyAccepted = sharedPreferences.getBoolean("PolicyAccepted", false);

        boolean isSubscribed = sharedPreferences.getBoolean("isSubscribed", false);

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("IsAuthenticated", false);
        editor.apply();

        setContentView(R.layout.activity_main);
        initializeViews();
        saveDebugLogToFile("onCreate: Views initialized.");
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

        setupBillingClient();
        startBillingConnection();
        querySubscriptionStatus();
        saveDebugLogToFile("onCreate: Billing client setup and subscription status queried.");


        setupDatabase();
        if (!isPolicyAccepted) {
            // If not accepted, show the policy acceptance fragment
            showPolicyAcceptanceFragment();
            saveDebugLogToFile("onCreate: Policy acceptance required.");
        } else {
            // Check the length of the password list
            if (isPasswordListEmpty()) {
                onFirstUse();
                saveDebugLogToFile("onCreate: First-time use setup completed.");
            } else {
                authenticateAppStart();
                saveDebugLogToFile("onCreate: Authentication started.");
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
        saveDebugLogToFile("isPasswordListEmpty: Password list size: " + passwordListSize);
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
        saveDebugLogToFile("onFirstUse: Password list and button listeners setup.");
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
        saveDebugLogToFile("onPolicyAccepted: Policy accepted by the user.");

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
        saveDebugLogToFile("onResume: Cloud icon updated.");
    }
    /**
     * Sets up the Google Sign-In process for enabling Google Drive backup.
     * This method configures the GoogleSignInClient with the necessary
     * scopes and starts the sign-in process.
     */
    private void setupGoogleSignIn() {
        saveDebugLogToFile("setupGoogleSignIn: Setting up Google Sign-In.");

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
            saveDebugLogToFile("setupGoogleSignIn: User is signed in, setting up Google Drive service.");
        } else {
            promptSignIn();
            saveDebugLogToFile("setupGoogleSignIn: No signed-in user, prompting for sign-in.");

        }
    }

    /**
     * Queries the Google Drive account to verify the current sign-in status.
     * If the user is signed in, this method sets up the Drive API client.
     */
    private void checkGoogleDriveSignInStatus() {
        saveDebugLogToFile("checkGoogleDriveSignInStatus: Checking Google Drive sign-in status.");
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        SharedPreferences sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE);

        String storedPassword = sharedPreferences.getString("EXCEL_PASSWORD", null);

        if (account != null) {
            // User is signed in
            cloudImage.setImageResource(R.drawable.cloudenable); // Set image indicating connected
            saveDebugLogToFile("checkGoogleDriveSignInStatus: User is signed in, cloud icon enabled.");

        } else {
            // User is not signed in
            cloudImage.setImageResource(R.drawable.clouddisable); // Set image indicating not connected
            saveDebugLogToFile("checkGoogleDriveSignInStatus: User is not signed in, cloud icon disabled.");
        }
    }
    /**
     * Prompts the user to set a password for securing the backup file.
     * This method displays a dialog with a password input field, validates the input,
     * and stores the password securely in SharedPreferences.
     * Once the password is stored, it starts the backup worker for Google Drive backups.
     */
    private void promptForPasswordAndStore() {
        saveDebugLogToFile("promptForPasswordAndStore: Prompting user for a password.");

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
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            saveDebugLogToFile("promptForPasswordAndStore: User cancelled password prompt.");
            dialog.cancel();
        });

        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(dialogInterface -> {
            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positiveButton.setOnClickListener(view -> {
                String password = passwordEditText.getText().toString().trim();

                // Validate the password input
                if (password.isEmpty()) {
                    passwordLayout.setError("Password cannot be empty");
                    saveDebugLogToFile("promptForPasswordAndStore: User entered empty password.");

                } else {
                    passwordLayout.setError(null); // Clear error

                    // Store the password securely in SharedPreferences
                    SharedPreferences sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE);
                    sharedPreferences.edit().putString("EXCEL_PASSWORD", password).apply();
                    Toast.makeText(this, "Password saved successfully", Toast.LENGTH_SHORT).show();
                    saveDebugLogToFile("promptForPasswordAndStore: Password saved successfully.");

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
        saveDebugLogToFile("promptSignIn: Prompting user to sign in to Google.");
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
            saveDebugLogToFile("onActivityResult: Received result for Google Sign-In.");
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
            saveDebugLogToFile("handleSignInResult: Sign-in successful, setting up Google Drive service.");
            setupGoogleDriveService();
            cloudImage.setImageResource(R.drawable.cloudenable);
            Toast.makeText(this, "Signed in successfully!", Toast.LENGTH_SHORT).show();
        } catch (ApiException e) {
            Log.w("GoogleSignIn", "signInResult:failed code=" + e.getStatusCode());
            saveDebugLogToFile("handleSignInResult: Sign-in failed with error code: " + e.getStatusCode());

        }
    }
    /**
     * Sets up the Google Drive service for handling file operations.
     * This method configures the necessary API clients and permissions to interact with Google Drive.
     */
    private void setupGoogleDriveService() {
        saveDebugLogToFile("setupGoogleDriveService: Setting up Google Drive service.");
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
            saveDebugLogToFile("setupGoogleDriveService: Google Drive service initialized.");

            SharedPreferences sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE);
            String storedPassword = sharedPreferences.getString("EXCEL_PASSWORD", null);

            if (storedPassword == null) {
                // Prompt for the password since it's not stored
                saveDebugLogToFile("setupGoogleDriveService: No stored password, prompting user to set password.");

                promptForPasswordAndStore();
            } else {
                // Start backup with stored password
                saveDebugLogToFile("setupGoogleDriveService: Starting backup worker with stored password.");

                startBackupWorker(storedPassword);
            }
        } else {
            saveDebugLogToFile("setupGoogleDriveService: No Google account found.");
        }
    }
    /**
     * Initializes the views and UI components of the activity.
     * This method sets up references to the ListView, ImageViews, and TextView elements in the layout.
     * It also provides a Toast message to inform the user about accessing the User Manual.
     */
    private void initializeViews() {
        saveDebugLogToFile("initializeViews: Initializing views.");

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
        saveDebugLogToFile("setupDatabase: Setting up the Room database.");

        db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "passwords-db")
                .allowMainThreadQueries()
                .build();
        saveDebugLogToFile("setupDatabase: Database setup complete.");

    }
    /**
     * Initializes the WorkManager to handle periodic backups to Google Drive.
     * This method schedules periodic backup tasks based on user subscription.
     */
    private void startBackupWorker(String password) {
        saveDebugLogToFile("startBackupWorker: Backup worker initiated.");
        Data inputData = new Data.Builder()
                .putString("EXCEL_PASSWORD", password)
                .build();

        PeriodicWorkRequest backupWorkRequest =
                new PeriodicWorkRequest.Builder(BackupWorker.class, 15, TimeUnit.MINUTES)
                        .setInputData(inputData)
                        .build();

        WorkManager.getInstance(this).enqueue(backupWorkRequest);
        saveDebugLogToFile("startBackupWorker: Periodic backup worker enqueued.");

    }
    /**
     * Saves the debug log message to a file named "BackupWorkerLog.txt" in the Downloads/CipherSafe directory.
     * This method checks if the file already exists and appends the log message. If the file doesn't exist,
     * it creates a new one. It handles file operations using the Android `ContentResolver` API.
     *
     * @param logMessage The log message to be saved or appended to the log file.
     */
    private void saveDebugLogToFile(String logMessage) {
        String fileName = "MainActivity.txt"; // Single file to append logs

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

        Log.d("MainActivityLog", "Cache directory path: " + cacheDir.getAbsolutePath());

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
     * Sets up the password list from the Room database and displays it in the ListView.
     * This method retrieves all passwords from the database and prepares the data for the custom adapter.
     * It also sets an OnItemClickListener to show password details when an item is clicked.
     */
    private void setupPasswordList() {
        saveDebugLogToFile("setupPasswordList: Setting up the password list.");
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
                    saveDebugLogToFile("setupPasswordList: Showing details for account: " + selectedAccountData[0]);
                    showPasswordDetails(selectedAccountData[0]);  // Show details using account name
                } else {
                    Log.e("MainActivity", "Selected account data array is empty or has insufficient elements.");
                    saveDebugLogToFile("setupPasswordList: Selected account data array is empty or insufficient.");
                }
            } else {
                Log.e("MainActivity", "Invalid position: " + position + " for adapter of size " + adapter.getCount());
                saveDebugLogToFile("setupPasswordList: Invalid position: " + position + " for adapter of size " + adapter.getCount());
            }
        });
    }
    /**
     * Loads passwords from the Room database and updates the ListView.
     * This method retrieves the passwords from the database and updates the adapter's data.
     * If the adapter is null, it initializes the adapter and sets it to the ListView.
     */
    private void loadPasswords() {
        saveDebugLogToFile("loadPasswords: Loading passwords from the database.");
        List<Password> passwords = db.passwordDao().getAll();

        List<String[]> passwordDataList = new ArrayList<>();
        for (Password password : passwords) {
            passwordDataList.add(new String[]{password.accountName, password.username});
        }

        CustomAdapter adapter = (CustomAdapter) passwordListView.getAdapter();
        if (adapter != null) {
            adapter.updateData(passwordDataList);  // Update the adapter's data and notify it to refresh
            saveDebugLogToFile("loadPasswords: Password list updated.");
        } else {
            // If adapter is null, initialize it and set it to the ListView
            adapter = new CustomAdapter(this, passwordDataList);
            passwordListView.setAdapter(adapter);
            saveDebugLogToFile("loadPasswords: Adapter initialized and set to the ListView.");
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
        saveDebugLogToFile("showPasswordDetails: Retrieving details for account: " + accountName);
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
            saveDebugLogToFile("showPasswordDetails: Failed to decrypt password for account: ");
        }
        passwordEditText.setText(decryptedPassword);
        notesEditText.setText(password.notes);

        // Set the custom view to the dialog
        builder.setView(dialogView);

        // Add Edit button
        builder.setNeutralButton("Edit", (dialog, which) -> {
            saveDebugLogToFile("showPasswordDetails: Editing password");
            showEditPasswordDialog(password);
        });

        // Add Delete button
        builder.setNegativeButton("Delete", (dialog, which) -> {
            db.passwordDao().delete(password);
            loadPasswords();
            Toast.makeText(this, "Password deleted", Toast.LENGTH_SHORT).show();
            saveDebugLogToFile("showPasswordDetails: Deleted password");
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
        saveDebugLogToFile("showEditPasswordDialog: Editing password");
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
            saveDebugLogToFile("showEditPasswordDialog: Failed to decrypt password");
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
                saveDebugLogToFile("showEditPasswordDialog: Failed to encrypt password for account");

                return;
            }
            password.notes = notes;

            // Update the database
            db.passwordDao().update(password);

            // Refresh the list
            loadPasswords();

            Toast.makeText(this, "Password updated", Toast.LENGTH_SHORT).show();
            saveDebugLogToFile("showEditPasswordDialog: Updated password for account");
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        saveDebugLogToFile("showEditPasswordDialog: Edit password cancelled for account");
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
        saveDebugLogToFile("addPassword: Adding new password for account: " + accountName);
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
        saveDebugLogToFile("addPassword: Password successfully added for account");

        // Update the adapter with the new data
        loadPasswords();
    }
    /**
     * Initiates the process of exporting stored passwords to an Excel file.
     * If the Excel password is not set, the user is prompted to set a password before proceeding.
     * Once the password is available, it calls the method to create the Excel file.
     */
    private void exportToExcel() {
        saveDebugLogToFile("exportToExcel: Initiating export to Excel.");
        SharedPreferences sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE);
        String password = sharedPreferences.getString("EXCEL_PASSWORD", null);

        if (password == null) {
            // If the password is not set, prompt the user to set it first
            saveDebugLogToFile("exportToExcel: No password set, prompting user for password.");
            promptForPasswordAndStore();
            return;
        }

        createExcelFile(password);
        saveDebugLogToFile("exportToExcel: Export to Excel initiated with stored password.");
    }
    /**
     * Creates an encrypted Excel file that contains all the stored passwords.
     * The Excel file includes columns for account name, username, password (decrypted), and notes.
     * The file is saved in the Downloads/CipherSafe folder, and encryption is applied using the provided password.
     *
     * @param password The password used to encrypt the Excel file.
     */
    private void createExcelFile(String password) {
        saveDebugLogToFile("createExcelFile: Creating Excel file with encryption.");
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
                saveDebugLogToFile("createExcelFile: Decryption successful for account: ");
            } catch (Exception e) {
                Log.e("PasswordManager", "Decryption failed", e);
                saveDebugLogToFile("createExcelFile: Decryption failed for account");
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
            saveDebugLogToFile("createExcelFile: Exported to Excel successfully as " + fileName);

            // Now upload the file to Google Drive
            // uploadFileToGoogleDrive(uri);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to export", Toast.LENGTH_SHORT).show();
            saveDebugLogToFile("createExcelFile: Failed to export Excel file.");

        }
    }
    /**
     * Displays the Google Drive backup options dialog based on the user's subscription status.
     * If the user is subscribed, the dialog shows options to enable or disable Google Drive backups.
     * If the user is not subscribed, the dialog prompts the user to subscribe to the CipherSafe subscription.
     */
    private void showBackupOptionsDialog() {
        saveDebugLogToFile("showBackupOptionsDialog: Showing backup options dialog.");
        SharedPreferences sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE);
        boolean isSubscribed = sharedPreferences.getBoolean("isSubscribed", false);

        if (isSubscribed) {
            saveDebugLogToFile("showBackupOptionsDialog: User is subscribed, displaying options.");
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
            builder.setTitle("Google Drive Backup");
            sharedPreferences.edit().putBoolean("isSubscribed", true).apply();
            // Determine if backup is enabled
            GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
            boolean isBackupEnabled = (account != null); // Check if Google Drive backup is enabled

            // Inflate a custom view for the dialog message (optional, but can enhance visuals)
            View dialogView = getLayoutInflater().inflate(R.layout.dialog_backup_options, null);
            TextView messageTextView = dialogView.findViewById(R.id.backup_message);

            if (isBackupEnabled) {
                // If backup is enabled, offer the option to disable it
                messageTextView.setText("Google Drive backups are currently enabled. Would you like to disable them?");
                saveDebugLogToFile("showBackupOptionsDialog: Backup is enabled, offering to disable it.");

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
                                saveDebugLogToFile("showBackupOptionsDialog: Backup disabled by user.");
                            });

                    dialog.dismiss();
                });

            } else {
                // If backup is disabled, offer the option to enable it
                messageTextView.setText("Google Drive backups are currently disabled. Would you like to enable them?");
                saveDebugLogToFile("showBackupOptionsDialog: Backup is disabled, offering to enable it.");

                builder.setPositiveButton("Enable", (dialog, which) -> {
                    // Start the Google Drive sign-in process
                    setupGoogleSignIn();
                    saveDebugLogToFile("showBackupOptionsDialog: Enabling backup, initiating Google sign-in.");
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
            saveDebugLogToFile("showBackupOptionsDialog: User is not subscribed, showing subscription dialog.");
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
                saveDebugLogToFile("showBackupOptionsDialog: User opted to subscribe.");
                promptSubscriptionPurchase();
                dialog.dismiss();
            });

            // Set up "Cancel" button
            builder.setNegativeButton("Cancel", (dialog, which) -> {
                // Simply dismiss the dialog
                dialog.dismiss();
                saveDebugLogToFile("showBackupOptionsDialog: User canceled the subscription dialog.");
                saveDebugLogToFile("showBackupOptionsDialog: Subscription dialog displayed.");
            });

            // Set the custom view to the dialog
            builder.setView(dialogView);

            // Show the dialog
            builder.show();
        }
    }

    /**
     * Prompts the user to purchase a CipherSafe subscription using Google Play Billing.
     * This method queries the SKU details for the subscription product and launches the billing flow
     * if the billing client is ready. It handles the SKU query and initiates the purchase process.
     */
    private void promptSubscriptionPurchase() {
        saveDebugLogToFile("promptSubscriptionPurchase: Initiating subscription purchase flow.");
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
                        saveDebugLogToFile("promptSubscriptionPurchase: Subscription price retrieved: " + price);

                        // Launch the billing flow to purchase the subscription
                        BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
                                .setSkuDetails(skuDetails)
                                .build();
                        billingClient.launchBillingFlow(MainActivity.this, billingFlowParams);
                        saveDebugLogToFile("promptSubscriptionPurchase: Billing flow launched.");
                    }
                } else {
                    Log.e("BillingClient", "Failed to query SKU details: " + billingResult.getResponseCode());
                    saveDebugLogToFile("promptSubscriptionPurchase: Failed to query SKU details. Response code: " + billingResult.getResponseCode());
                }
            });
        } else {
            Log.e("BillingClient", "Billing client is not ready to query SKU details.");
            saveDebugLogToFile("promptSubscriptionPurchase: Billing client not ready.");
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
        saveDebugLogToFile("handlePurchase: Handling purchase for subscription.");
        if (purchase.getProducts().contains(SUBSCRIPTION_ID)) {
            SharedPreferences sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE);
            boolean isSubscribed = sharedPreferences.getBoolean("isSubscribed", false);
//            SharedPreferences sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE);
            sharedPreferences.edit().putBoolean("isSubscribed", true).apply();

            // Update cloud icon immediately
            updateCloudIcon();
            saveDebugLogToFile("handlePurchase: Subscription activated, cloud icon updated.");
            // Start Google Drive backup process if subscribed
//            setupGoogleSignIn();
            // Mark the purchase as acknowledged if not already acknowledged.
            if (!purchase.isAcknowledged()) {
                AcknowledgePurchaseParams acknowledgePurchaseParams =
                        AcknowledgePurchaseParams.newBuilder()
                                .setPurchaseToken(purchase.getPurchaseToken())
                                .build();
                billingClient.acknowledgePurchase(acknowledgePurchaseParams, billingResult -> {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        Toast.makeText(this, "Subscription successful", Toast.LENGTH_SHORT).show();
                        saveDebugLogToFile("handlePurchase: Subscription acknowledged successfully.");
                        showSubscriptionSuccessDialog();
                    } else {
                        saveDebugLogToFile("handlePurchase: Subscription acknowledgment failed. Response code: " + billingResult.getResponseCode());
                    }
                });
            }
        } else {
            saveDebugLogToFile("handlePurchase: No valid subscription found in purchase.");
        }
    }
    /**
     * Displays a dialog informing the user that the subscription was successful.
     * This dialog notifies the user that Google Drive backups are now available.
     * After the user dismisses the dialog, the Google Drive sign-in process is initiated.
     */
    private void showSubscriptionSuccessDialog() {
        saveDebugLogToFile("showSubscriptionSuccessDialog: Displaying subscription success dialog.");
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("Subscription Successful");
        builder.setMessage("You are now subscribed to Google Drive Backup. You can start backing up your passwords to Google Drive.");
        builder.setPositiveButton("OK", (dialog, which) -> {
            // Dismiss the dialog
            dialog.dismiss();
            saveDebugLogToFile("showSubscriptionSuccessDialog: User acknowledged subscription success dialog.");
        });
        builder.show();

        setupGoogleSignIn();
        saveDebugLogToFile("showSubscriptionSuccessDialog: Initiated Google Sign-In after subscription success.");
    }

    /**
     * Authenticates the user when the app starts using biometric or device credentials.
     * It checks if the user has been authenticated before; if not, it shows a biometric prompt.
     * If the authentication succeeds, the app is initialized, including setting up the database,
     * password list, and buttons. If authentication fails, the app is closed.
     */
    private void authenticateAppStart() {
        saveDebugLogToFile("authenticateAppStart: Starting authentication.");
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
                    saveDebugLogToFile("authenticateAppStart: Authentication error: " + errString);
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


                        saveDebugLogToFile("authenticateAppStart: Authentication succeeded.");

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
                    saveDebugLogToFile("authenticateAppStart: Authentication failed.");
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
                saveDebugLogToFile("authenticateAppStart: Exception during authentication: " + e.getMessage());
                Toast.makeText(this, "Error during authentication: " + e.getMessage(), Toast.LENGTH_LONG).show();
                finish(); // Close the app if authentication fails
            }
        } else {
            Log.d("MainActivity", "Already authenticated, initializing app");
            saveDebugLogToFile("authenticateAppStart: Already authenticated, initializing app.");
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
        saveDebugLogToFile("setupButtonListeners: Setting up button listeners.");
        addPasswordImage.setOnClickListener(v -> showAddPasswordDialog());

        //exportPasswordsButton.setOnClickListener(v -> authenticateAndExportToExcel());
        downloadImage.setOnClickListener(v -> exportToExcel());

        cloudImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //setupGoogleSignIn();
                showBackupOptionsDialog();
                saveDebugLogToFile("setupButtonListeners: Cloud image clicked, showing backup options.");
            }
        });
        cipherSafeTextView.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, UserManualActivity.class);
            startActivity(intent);
            saveDebugLogToFile("setupButtonListeners: User manual clicked.");
        });
    }
    /**
     * Updates the cloud icon in the UI based on the user's subscription status.
     * If the user is subscribed, the icon indicates that Google Drive backup is enabled.
     * If the user is not subscribed, the icon indicates that Google Drive backup is disabled.
     */
    private void updateCloudIcon() {
        saveDebugLogToFile("updateCloudIcon: Updating cloud icon based on subscription status.");
        SharedPreferences sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE);
        boolean isSubscribed = sharedPreferences.getBoolean("isSubscribed", false);
        if (isSubscribed) {
            cloudImage.setImageResource(R.drawable.cloudenable);  // Set the icon to indicate Google Drive is enabled
            saveDebugLogToFile("updateCloudIcon: Cloud icon updated to indicate subscription is active.");
        } else {
            cloudImage.setImageResource(R.drawable.clouddisable);  // Set the icon to indicate Google Drive is disabled
            saveDebugLogToFile("updateCloudIcon: Cloud icon updated to indicate no active subscription.");
        }
    }
    /**
     * Displays a dialog for adding a new password entry to the database.
     * The dialog includes input fields for account name, username, password, and notes.
     * It validates the input fields before adding the password to the database and dismissing the dialog.
     */
    private void showAddPasswordDialog() {
        saveDebugLogToFile("showAddPasswordDialog: Displaying add password dialog.");
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
                    saveDebugLogToFile("showAddPasswordDialog: Password added for account: ");
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
        saveDebugLogToFile("setupBillingClient: Initializing billing client.");
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
                            saveDebugLogToFile("setupBillingClient: User canceled the purchase.");
                        } else {
                            Log.e("BillingClient", "Purchase failed with code: " + billingResult.getResponseCode());
                            saveDebugLogToFile("setupBillingClient: Purchase failed with code: " + billingResult.getResponseCode());
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
        saveDebugLogToFile("startBillingConnection: Starting billing connection.");
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    // BillingClient is ready
                    Log.d("BillingClient", "Billing Client setup finished. Ready to make purchases.");
                    Toast.makeText(MainActivity.this, "Billing Client is connected!", Toast.LENGTH_SHORT).show();
                    saveDebugLogToFile("startBillingConnection: Billing client setup finished, ready for purchases.");
                } else {
                    Log.e("BillingClient", "Billing setup failed with code: " + billingResult.getResponseCode());
                    saveDebugLogToFile("startBillingConnection: Billing setup failed with code: " + billingResult.getResponseCode());
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                // Handle disconnection
                Log.e("BillingClient", "Billing service disconnected.");
                Toast.makeText(MainActivity.this, "Billing Client disconnected.", Toast.LENGTH_SHORT).show();
                saveDebugLogToFile("startBillingConnection: Billing service disconnected.");
            }
        });
    }
    /**
     * Queries Google Play Billing to check if the user is subscribed
     * to the Google Drive backup feature. It updates the UI and app functionality
     * based on the subscription status.
     */
    private void querySubscriptionStatus() {
        saveDebugLogToFile("querySubscriptionStatus: Querying subscription status.");
        billingClient.queryPurchasesAsync(BillingClient.SkuType.SUBS, (billingResult, purchaseList) -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchaseList != null) {
                saveDebugLogToFile("querySubscriptionStatus: Purchase list retrieved.");
                for (Purchase purchase : purchaseList) {
                    if (purchase.getProducts().contains(SUBSCRIPTION_ID)) {
                        SharedPreferences sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE);
                        sharedPreferences.edit().putBoolean("isSubscribed", true).apply();
                        saveDebugLogToFile("querySubscriptionStatus: User is subscribed.");
                        break;  // If the user is subscribed, we can stop checking further.
                    }
                }
            }
            else {
                saveDebugLogToFile("querySubscriptionStatus: No valid subscription found or query failed.");
            }
            SharedPreferences sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE);
            saveDebugLogToFile("querySubscriptionStatus: Subscription status: " + sharedPreferences.getBoolean("isSubscribed", false));

            Log.d("Billing", "Subscription status: " + sharedPreferences.getBoolean("isSubscribed", false));
        });
    }

}

