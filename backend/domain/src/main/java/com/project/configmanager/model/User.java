package com.project.configmanager.model;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import jakarta.persistence.*;

@Entity
@Table(name = "users")
@Data
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    private String email;

    private Boolean enabled = true;
    private Boolean accountNonExpired = true;
    private Boolean credentialsNonExpired = true;
    private Boolean accountNonLocked = true;

}
