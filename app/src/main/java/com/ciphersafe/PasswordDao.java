package com.ciphersafe;

// PasswordDao.java
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface PasswordDao {
    @Query("SELECT * FROM password")
    List<Password> getAll();

    @Insert
    void insert(Password password);

    @Update
    void update(Password password);

    @Delete
    void delete(Password password);
    @Query("SELECT * FROM password WHERE accountName = :accountName LIMIT 1")
    Password findByAccountName(String accountName);
}
