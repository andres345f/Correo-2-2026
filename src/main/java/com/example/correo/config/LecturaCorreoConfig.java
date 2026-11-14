package com.example.correo.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Activa la revision periodica del buzon, salvo con app.mail.lectura-activa=false (tests). */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.mail.lectura-activa", matchIfMissing = true)
public class LecturaCorreoConfig {
}
