package com.example.bookreview.dto.domain;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.Builder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "users")
@Builder
public record User(
        @Id
        String id,

        @NotBlank
        @Indexed(unique = true)
        String username,

        @NotBlank
        String passwordHash,

        @Indexed(unique = true, sparse = true)
        String email,

        Set<String> roles,

        boolean enabled,

        @CreatedDate
        Instant createdAt
) {

    public User {
        roles = roles == null ? new HashSet<>() : roles;
        createdAt = createdAt == null ? Instant.now() : createdAt;
        // enabled는 primitive → 기본 false지만, Builder에서 true 주면 OK
    }
}
