package com.ciphersafe;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * The Password class represents an entity in the Room database, storing information related to user accounts and their associated credentials.
 * This class defines the structure of the table in the database, where each instance represents a row in the "Password" table.
 */
@Entity
public class Password {

    /**
     * The primary key for the Password entity, which is autogenerated by the Room database.
     * It uniquely identifies each password record.
     */
    @PrimaryKey(autoGenerate = true)
    public int id;

    /**
     * The name of the account (e.g., the website or service name) associated with the password.
     */
    public String accountName;

    /**
     * The username or email associated with the account for which the password is stored.
     */
    public String username;

    /**
     * The encrypted password for the account.
     */
    public String password;

    /**
     * Optional field for storing additional notes or information related to the account.
     */
    public String notes;
}
