package com.lbt.telegram_learning_bot.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import static com.lbt.telegram_learning_bot.util.Constants.*;

@Entity
@Table(name = "user_session")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserSession {

    @Id
    @Column(name = "user_id")
    private Long userId; // Telegram ID

    @Column(nullable = false, length = 50)
    private String state;

    @Column(columnDefinition = "jsonb") // В PostgreSQL это будет тип jsonb
    private String context; // Можно хранить JSON как строку и парсить при необходимости

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}