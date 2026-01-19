package com.agencia.seguros.repository;

import com.agencia.seguros.model.Factura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface FacturaRepository extends JpaRepository<Factura, Long>, JpaSpecificationExecutor<Factura> {

    @Query(value = """
    SELECT DATE(f.fecha_factura) AS periodo, COUNT(*) AS total
    FROM facturas f
    WHERE f.fecha_factura IS NOT NULL
      AND f.fecha_factura BETWEEN :desde AND :hasta
    GROUP BY DATE(f.fecha_factura)
    ORDER BY DATE(f.fecha_factura)
""", nativeQuery = true)
    List<Object[]> contarPorDia(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

    @Query(value = """
    SELECT
      CONCAT(YEAR(f.fecha_factura), '-', LPAD(WEEK(f.fecha_factura, 1), 2, '0')) AS periodo,
      COUNT(*) AS total
    FROM facturas f
    WHERE f.fecha_factura IS NOT NULL
      AND f.fecha_factura BETWEEN :desde AND :hasta
    GROUP BY periodo
    ORDER BY periodo
""", nativeQuery = true)
    List<Object[]> contarPorSemana(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

    @Query(value = """
    SELECT
      DATE_FORMAT(f.fecha_factura, '%Y-%m') AS periodo,
      COUNT(*) AS total
    FROM facturas f
    WHERE f.fecha_factura IS NOT NULL
      AND f.fecha_factura BETWEEN :desde AND :hasta
    GROUP BY periodo
    ORDER BY periodo
""", nativeQuery = true)
    List<Object[]> contarPorMes(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

    Optional<Factura> findByMessageId(String messageId);
    boolean existsByMessageId(String messageId);
}
