package com.example.myapplication;

// AppDatabase.java
import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {Password.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
    public abstract PasswordDao passwordDao();
}
