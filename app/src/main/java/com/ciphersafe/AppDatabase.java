package com.ciphersafe;

// AppDatabase.java
import androidx.room.Database;
import androidx.room.RoomDatabase;

/**
 * AppDatabase is the main database class that defines the database configuration and serves as the app's main access point to the persisted data.
 */
@Database(entities = {Password.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {

    /**
     * Returns the Data Access Object (DAO) for the Password entity.
     *
     * @return An instance of {@link PasswordDao}.
     */
    public abstract PasswordDao passwordDao();
}
