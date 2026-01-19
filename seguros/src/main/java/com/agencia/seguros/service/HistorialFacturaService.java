package com.agencia.seguros.service;

import com.agencia.seguros.model.EstadoFactura;
import com.agencia.seguros.model.Factura;
import com.agencia.seguros.model.HistorialFactura;
import com.agencia.seguros.repository.HistorialFacturaRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class HistorialFacturaService {

    private final HistorialFacturaRepository historialFacturaRepository;

    public HistorialFacturaService(HistorialFacturaRepository historialFacturaRepository) {
        this.historialFacturaRepository = historialFacturaRepository;
    }

    /**
     * Registra un cambio de estado de la factura (ej: PENDIENTE -> PROCESADA)
     */
    public void registrarCambioEstado(Factura factura,
                                      EstadoFactura estadoAnterior,
                                      EstadoFactura estadoNuevo,
                                      String usuario) {

        HistorialFactura historial = new HistorialFactura();
        historial.setFactura(factura);
        historial.setFechaMovimiento(LocalDateTime.now());
        historial.setTipo("CAMBIO_ESTADO");

        String detalle = String.format("Cambio de estado de %s a %s",
                estadoAnterior != null ? estadoAnterior.name() : "SIN_ESTADO",
                estadoNuevo != null ? estadoNuevo.name() : "SIN_ESTADO");

        historial.setDetalle(detalle);
        historial.setEstadoAnterior(estadoAnterior);
        historial.setEstadoNuevo(estadoNuevo);
        historial.setUsuario(usuario);

        historialFacturaRepository.save(historial);
    }

    /**
     * Registra una acción genérica sobre la factura
     * (ej: REENVIO, CAMBIO_ASEGURADORA, etc.)
     */
    public void registrarAccionSimple(Factura factura,
                                      String tipo,
                                      String detalle,
                                      String usuario) {

        HistorialFactura historial = new HistorialFactura();
        historial.setFactura(factura);
        historial.setFechaMovimiento(LocalDateTime.now());
        historial.setTipo(tipo);
        historial.setDetalle(detalle);
        historial.setUsuario(usuario);

        historialFacturaRepository.save(historial);
    }

    /**
     * Devuelve el historial completo de una factura ordenado por fecha descendente
     */
    public List<HistorialFactura> listarPorFactura(Long facturaId) {
        return historialFacturaRepository.findByFacturaIdOrderByFechaMovimientoDesc(facturaId);
    }
}
