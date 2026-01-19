package com.agencia.seguros.repository.spec;

import com.agencia.seguros.model.EstadoFactura;
import com.agencia.seguros.model.Factura;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

public class FacturaSpecifications {

    public static Specification<Factura> estadoIgual(String estado) {
        if (estado == null || estado.isBlank()) return null;
        return (root, query, cb) ->
                cb.equal(cb.upper(root.get("estado").as(String.class)), estado.toUpperCase());
        // Alternativa más estricta si estado es Enum:
        // return (root, query, cb) -> cb.equal(root.get("estado"), EstadoFactura.valueOf(estado));
    }

    public static Specification<Factura> aseguradoraIdIgual(Long aseguradoraId) {
        if (aseguradoraId == null) return null;
        return (root, query, cb) -> cb.equal(root.get("aseguradora").get("id"), aseguradoraId);
    }

    public static Specification<Factura> numeroFacturaLike(String numeroFactura) {
        if (numeroFactura == null || numeroFactura.isBlank()) return null;
        return (root, query, cb) ->
                cb.like(root.get("numeroFactura"), "%" + numeroFactura + "%");
    }

    public static Specification<Factura> numeroSiniestroLike(String numeroSiniestro) {
        if (numeroSiniestro == null || numeroSiniestro.isBlank()) return null;
        return (root, query, cb) ->
                cb.like(root.get("numeroSiniestro"), "%" + numeroSiniestro + "%");
    }

    public static Specification<Factura> numeroOrdenLike(String numeroOrden) {
        if (numeroOrden == null || numeroOrden.isBlank()) return null;
        return (root, query, cb) ->
                cb.like(root.get("numeroOrden"), "%" + numeroOrden + "%");
    }

    /**
     * Vencidas: fechaFactura != null AND fechaEnvio == null AND fechaFactura < hoy-14
     * (replica tu lógica del panel)
     */
    public static Specification<Factura> vencidas(LocalDate hoy) {
        if (hoy == null) hoy = LocalDate.now();
        LocalDate limite = hoy.minusDays(14);

        return (root, query, cb) -> cb.and(
                cb.isNotNull(root.get("fechaFactura")),
                cb.isNull(root.get("fechaEnvio")),
                cb.lessThan(root.get("fechaFactura"), limite)
        );
    }

    /**
     * Filtro por sucursal basado en prefijo del número de factura.
     * Si podés guardar sucursal como campo en Factura, sería MUCHO mejor.
     */
    public static Specification<Factura> numeroFacturaPrefijo(String... prefijos) {
        if (prefijos == null || prefijos.length == 0) return null;

        return (root, query, cb) -> {
            var numero = root.get("numeroFactura").as(String.class);

            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();

            for (String p : prefijos) {
                if (p != null && !p.isBlank()) {
                    predicates.add(cb.like(numero, p + "%"));
                }
            }

            return cb.or(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }
}
