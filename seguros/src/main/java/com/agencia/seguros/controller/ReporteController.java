package com.agencia.seguros.controller;

import com.agencia.seguros.repository.FacturaRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Controller
@PreAuthorize("hasRole('ADMIN')") // después se puede abrir a PDV
public class ReporteController {

    private final FacturaRepository facturaRepository;

    public ReporteController(FacturaRepository facturaRepository) {
        this.facturaRepository = facturaRepository;
    }

    @GetMapping("/reportes")
    public String reportes(
            @RequestParam(defaultValue = "DIA") String agrupacion,
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta,
            Model model
    ) {

        LocalDate hoy = LocalDate.now();
        LocalDate d = (desde == null || desde.isBlank()) ? hoy.minusDays(30) : LocalDate.parse(desde);
        LocalDate h = (hasta == null || hasta.isBlank()) ? hoy : LocalDate.parse(hasta);

        List<Object[]> rows = switch (agrupacion.toUpperCase()) {
            case "SEMANA" -> facturaRepository.contarPorSemana(d, h);
            case "MES" -> facturaRepository.contarPorMes(d, h);
            default -> facturaRepository.contarPorDia(d, h);
        };

        List<String> labels = new ArrayList<>();
        List<Long> values = new ArrayList<>();

        for (Object[] r : rows) {
            labels.add(String.valueOf(r[0]));
            values.add(((Number) r[1]).longValue());
        }

        model.addAttribute("agrupacion", agrupacion.toUpperCase());
        model.addAttribute("desde", d.toString());
        model.addAttribute("hasta", h.toString());
        model.addAttribute("labels", labels);
        model.addAttribute("values", values);

        return "reportes";
    }
}
