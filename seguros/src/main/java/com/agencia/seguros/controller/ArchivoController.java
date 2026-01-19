package com.agencia.seguros.controller;

import com.agencia.seguros.model.Factura;
import com.agencia.seguros.repository.FacturaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/archivos")
public class ArchivoController {

    @Value("${app.facturas.ruta-archivos}")
    private String rutaArchivos;   // viene del application.yml

    private final FacturaRepository facturaRepository;

    public ArchivoController(FacturaRepository facturaRepository) {
        this.facturaRepository = facturaRepository;
    }

    @GetMapping("/{nombreArchivo:.+}")
    public ResponseEntity<Resource> verArchivo(@PathVariable String nombreArchivo) {
        try {
            // facturas_pdf/nombreArchivo
            Path carpeta = Paths.get(rutaArchivos).toAbsolutePath().normalize();
            Path ruta = carpeta.resolve(nombreArchivo).normalize();

            if (!Files.exists(ruta)) {
                System.out.println("Archivo no encontrado: " + ruta);
                return ResponseEntity.notFound().build();
            }

            Resource recurso = new UrlResource(ruta.toUri());
            if (!recurso.exists() || !recurso.isReadable()) {
                System.out.println("No se puede leer el archivo: " + ruta);
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + recurso.getFilename() + "\"")
                    .body(recurso);

        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        }
    }
    // Descargar el adjunto extra (PDF) – accesible para admin y PDV
    @GetMapping("/facturas/{id}/adjunto-extra")
    public ResponseEntity<Resource> descargarAdjuntoExtra(@PathVariable Long id) throws Exception {

        var optFactura = facturaRepository.findById(id);
        if (optFactura.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Factura factura = optFactura.get();

        // Si no tiene archivo adicional, 404
        if (factura.getNombreArchivoAdicional() == null) {
            return ResponseEntity.notFound().build();
        }

        Path carpeta = Paths.get(rutaArchivos).toAbsolutePath().normalize();
        Path archivo = carpeta.resolve(factura.getNombreArchivoAdicional());

        Resource recurso = new UrlResource(archivo.toUri());
        if (!recurso.exists()) {
            return ResponseEntity.notFound().build();
        }

        String nombre = factura.getNombreArchivoAdicional();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + nombre + "\"")
                .body(recurso);
    }


}
