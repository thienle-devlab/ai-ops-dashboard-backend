package com.lethien.aiopsdashboard.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class User {
    private String email;
    private String passwordHash;
    private String role;
    private Boolean isActive;
}
