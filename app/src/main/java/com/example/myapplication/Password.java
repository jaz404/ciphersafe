package com.example.myapplication;

// Password.java
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class Password {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String accountName;
    public String username;
    public String password;
    public String notes;
}
