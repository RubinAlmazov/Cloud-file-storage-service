package org.me.cloudfilestorage.security.entities;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.validator.constraints.UniqueElements;

@Entity
@Table(name = "users")
@Data
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private  int id;

    @Column(name = "username", nullable = false, unique = true)
    private  String username;

    @Column(name = "password")
    private  String password;
}
