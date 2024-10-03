package com.ciphersafe;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * PasswordDao is a Data Access Object (DAO) interface that provides methods for interacting with the "Password" table in the Room database.
 * It defines operations such as querying, inserting, updating, and deleting password records.
 */
@Dao
public interface PasswordDao {

    /**
     * Retrieves all password records from the "Password" table.
     *
     * @return A list of all {@link Password} objects stored in the database.
     */
    @Query("SELECT * FROM password")
    List<Password> getAll();

    /**
     * Inserts a new password record into the "Password" table.
     *
     * @param password The {@link Password} object to be inserted into the database.
     */
    @Insert
    void insert(Password password);

    /**
     * Updates an existing password record in the "Password" table.
     *
     * @param password The {@link Password} object containing updated data.
     */
    @Update
    void update(Password password);

    /**
     * Deletes a password record from the "Password" table.
     *
     * @param password The {@link Password} object to be deleted from the database.
     */
    @Delete
    void delete(Password password);

    /**
     * Finds and retrieves a single password record based on the provided account name.
     * This query limits the result to the first matching record found.
     *
     * @param accountName The name of the account for which the password is being retrieved.
     * @return A {@link Password} object representing the first matching account, or null if not found.
     */
    @Query("SELECT * FROM password WHERE accountName = :accountName LIMIT 1")
    Password findByAccountName(String accountName);
}
