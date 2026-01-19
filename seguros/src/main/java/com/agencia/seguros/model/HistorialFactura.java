package com.agencia.seguros.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class HistorialFactura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Factura factura;

    private LocalDateTime fechaMovimiento;

    private String tipo;    // CAMBIO_ESTADO, REENVIO, CAMBIO_ASEGURADORA, etc.

    @Column(length = 500)
    private String detalle;

    @Enumerated(EnumType.STRING)
    private EstadoFactura estadoAnterior;

    @Enumerated(EnumType.STRING)
    private EstadoFactura estadoNuevo;

    private String usuario;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Factura getFactura() {
        return factura;
    }

    public void setFactura(Factura factura) {
        this.factura = factura;
    }

    public LocalDateTime getFechaMovimiento() {
        return fechaMovimiento;
    }

    public void setFechaMovimiento(LocalDateTime fechaMovimiento) {
        this.fechaMovimiento = fechaMovimiento;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getDetalle() {
        return detalle;
    }

    public void setDetalle(String detalle) {
        this.detalle = detalle;
    }

    public EstadoFactura getEstadoAnterior() {
        return estadoAnterior;
    }

    public void setEstadoAnterior(EstadoFactura estadoAnterior) {
        this.estadoAnterior = estadoAnterior;
    }

    public EstadoFactura getEstadoNuevo() {
        return estadoNuevo;
    }

    public void setEstadoNuevo(EstadoFactura estadoNuevo) {
        this.estadoNuevo = estadoNuevo;
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }
}
