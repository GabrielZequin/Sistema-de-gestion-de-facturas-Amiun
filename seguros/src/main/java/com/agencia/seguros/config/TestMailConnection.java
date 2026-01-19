package com.agencia.seguros.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class TestMailConnection {

    @Autowired
    private JavaMailSender mailSender;

    //@EventListener(ApplicationReadyEvent.class)
    public void enviarMailDePrueba() {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo("gabrielzequininfo@gmail.com"); // mail real
            message.setSubject("Prueba Spring Boot - Seguros");
            message.setText("Si ves este mail, la configuración de correo funciona ✅");

            mailSender.send(message);
            System.out.println("✅ Mail de prueba enviado correctamente");

        } catch (Exception e) {
            System.err.println("❌ Error enviando mail:");
            e.printStackTrace();
        }
    }
}


