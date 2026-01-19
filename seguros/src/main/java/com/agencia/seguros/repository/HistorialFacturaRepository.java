package com.agencia.seguros.repository;

import com.agencia.seguros.model.HistorialFactura;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistorialFacturaRepository extends JpaRepository<HistorialFactura, Long> {

    List<HistorialFactura> findByFacturaIdOrderByFechaMovimientoDesc(Long facturaId);
}
