package com.agencia.seguros.service;

import com.agencia.seguros.model.Factura;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Profile("prod")
@Service
public class EmailEnvioFacturaService {

    private final JavaMailSender mailSender;

    @Value("${app.facturas.ruta-archivos}")
    private String rutaArchivos;

    @Value("${app.correo.modo-pruebas:false}")
    private boolean modoPruebas;

    @Value("${app.correo.destino-pruebas:}")
    private String destinoPruebas;

    public EmailEnvioFacturaService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void enviarFacturaAseguradora(Factura factura) throws Exception {

        String emailDestino;

        // Si está activado el modo pruebas → ignorar el mail real
        if (modoPruebas && destinoPruebas != null && !destinoPruebas.isBlank()) {
            emailDestino = destinoPruebas;
            System.out.println("MODO PRUEBAS ACTIVADO → enviando siempre a: " + emailDestino);
        } else {
            if (factura.getAseguradora() == null || factura.getAseguradora().getEmail() == null) {
                throw new IllegalStateException("Factura sin aseguradora o sin email de aseguradora.");
            }
            emailDestino = factura.getAseguradora().getEmail();
        }

        MimeMessage mensaje = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mensaje, true, "UTF-8");

        helper.setTo(emailDestino);
        helper.setSubject("Factura " + factura.getId() + " - " + factura.getAseguradora().getNombre());
        helper.setText("Se envían adjuntas la factura y la documentación complementaria.", false);

        Path carpeta = Paths.get(rutaArchivos).toAbsolutePath().normalize();

        // PDF original
        if (factura.getNombreArchivo() != null) {
            Path pdfOriginal = carpeta.resolve(factura.getNombreArchivo());
            if (Files.exists(pdfOriginal)) {
                helper.addAttachment(factura.getNombreArchivo(), pdfOriginal.toFile());
            }
        }

        // PDF adicional
        if (factura.getNombreArchivoAdicional() != null) {
            Path pdfExtra = carpeta.resolve(factura.getNombreArchivoAdicional());
            if (Files.exists(pdfExtra)) {
                helper.addAttachment(factura.getNombreArchivoAdicional(), pdfExtra.toFile());
            }
        }

        mailSender.send(mensaje);
    }
}


