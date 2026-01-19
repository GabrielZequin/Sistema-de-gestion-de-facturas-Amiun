package com.agencia.seguros.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "facturas")
public class Factura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "aseguradora_id")
    private Aseguradora aseguradora;

    @Enumerated(EnumType.STRING)
    private EstadoFactura estado;

    @Column(name = "observaciones_admin")
    private String observacionesAdmin;

    private String asunto;
    private String remitente;

    private String nombreArchivo;
    private String nombreArchivoAdicional;

    private String rutaArchivo;

    @Column(name = "fecha_recepcion")
    private LocalDateTime fechaRecepcion;

    @Column(name = "fecha_factura")
    private LocalDate fechaFactura;

    @Column(name = "fecha_envio")
    private LocalDateTime fechaEnvio;

    @Column(name = "numero_factura")
    private String numeroFactura;

    @Column(name = "numero_siniestro")
    private String numeroSiniestro;

    @Column(name = "numero_orden")
    private String numeroOrden;

    // ✅ NUEVO: identificador único del mail (Message-ID)
    @Column(name = "message_id", unique = true, length = 255)
    private String messageId;

    public Factura() {}

    // ===== Getters y setters =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Aseguradora getAseguradora() { return aseguradora; }
    public void setAseguradora(Aseguradora aseguradora) { this.aseguradora = aseguradora; }

    public EstadoFactura getEstado() { return estado; }
    public void setEstado(EstadoFactura estado) { this.estado = estado; }

    public String getObservacionesAdmin() { return observacionesAdmin; }
    public void setObservacionesAdmin(String observacionesAdmin) { this.observacionesAdmin = observacionesAdmin; }

    public String getAsunto() { return asunto; }
    public void setAsunto(String asunto) { this.asunto = asunto; }

    public String getRemitente() { return remitente; }
    public void setRemitente(String remitente) { this.remitente = remitente; }

    public String getNombreArchivo() { return nombreArchivo; }
    public void setNombreArchivo(String nombreArchivo) { this.nombreArchivo = nombreArchivo; }

    public String getNombreArchivoAdicional() { return nombreArchivoAdicional; }
    public void setNombreArchivoAdicional(String nombreArchivoAdicional) { this.nombreArchivoAdicional = nombreArchivoAdicional; }

    public String getRutaArchivo() { return rutaArchivo; }
    public void setRutaArchivo(String rutaArchivo) { this.rutaArchivo = rutaArchivo; }

    public LocalDateTime getFechaRecepcion() { return fechaRecepcion; }
    public void setFechaRecepcion(LocalDateTime fechaRecepcion) { this.fechaRecepcion = fechaRecepcion; }

    public LocalDate getFechaFactura() { return fechaFactura; }
    public void setFechaFactura(LocalDate fechaFactura) { this.fechaFactura = fechaFactura; }

    public LocalDateTime getFechaEnvio() { return fechaEnvio; }
    public void setFechaEnvio(LocalDateTime fechaEnvio) { this.fechaEnvio = fechaEnvio; }

    public String getNumeroFactura() { return numeroFactura; }
    public void setNumeroFactura(String numeroFactura) { this.numeroFactura = numeroFactura; }

    public String getNumeroSiniestro() { return numeroSiniestro; }
    public void setNumeroSiniestro(String numeroSiniestro) { this.numeroSiniestro = numeroSiniestro; }

    public String getNumeroOrden() { return numeroOrden; }
    public void setNumeroOrden(String numeroOrden) { this.numeroOrden = numeroOrden; }

    // NUEVO
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    @Transient
    public boolean isVencida() {
        if (fechaFactura == null) return false;
        if (fechaEnvio != null) return false;
        return fechaFactura.isBefore(LocalDate.now().minusDays(15));
    }
}
