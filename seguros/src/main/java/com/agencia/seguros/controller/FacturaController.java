package com.agencia.seguros.controller;

import com.agencia.seguros.model.*;
import com.agencia.seguros.repository.AseguradoraRepository;
import com.agencia.seguros.repository.FacturaRepository;
import com.agencia.seguros.repository.spec.FacturaSpecifications;
import com.agencia.seguros.service.EmailEnvioFacturaService;
import com.agencia.seguros.service.HistorialFacturaService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.agencia.seguros.repository.UsuarioRepository;
import org.springframework.data.domain.Pageable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Profile("prod")
@Controller
public class FacturaController {

    private final FacturaRepository facturaRepository;
    private final AseguradoraRepository aseguradoraRepository;
    private final HistorialFacturaService historialFacturaService;
    private final EmailEnvioFacturaService emailEnvioFacturaService;
    private final UsuarioRepository usuarioRepository;


    @Value("${app.facturas.ruta-archivos}")
    private String rutaArchivos;

    public FacturaController(FacturaRepository facturaRepository,
                             AseguradoraRepository aseguradoraRepository,
                             HistorialFacturaService historialFacturaService,
                             EmailEnvioFacturaService emailEnvioFacturaService,
                             UsuarioRepository usuarioRepository) {
        this.facturaRepository = facturaRepository;
        this.aseguradoraRepository = aseguradoraRepository;
        this.historialFacturaService = historialFacturaService;
        this.emailEnvioFacturaService = emailEnvioFacturaService;
        this.usuarioRepository = usuarioRepository;
    }
    private String getUsuarioActual() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null ? auth.getName() : "sistema");
    }


    @GetMapping("/facturas")
    public String listarFacturas(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Long aseguradoraId,
            @RequestParam(required = false) Boolean soloVencidas,
            @RequestParam(required = false) String numeroFactura,
            @RequestParam(required = false) String numeroSiniestro,
            @RequestParam(required = false) String numeroOrden,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {

        var auth = SecurityContextHolder.getContext().getAuthentication();
        boolean esAdmin = auth != null &&
                auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        Specification<Factura> spec = Specification.allOf();

        spec = andIfNotNull(spec, FacturaSpecifications.estadoIgual(estado));
        spec = andIfNotNull(spec, FacturaSpecifications.aseguradoraIdIgual(aseguradoraId));
        spec = andIfNotNull(spec, FacturaSpecifications.numeroFacturaLike(numeroFactura));
        spec = andIfNotNull(spec, FacturaSpecifications.numeroSiniestroLike(numeroSiniestro));
        spec = andIfNotNull(spec, FacturaSpecifications.numeroOrdenLike(numeroOrden));

        // solo vencidas (solo admin)
        boolean filtroSoloVencidas = soloVencidas != null && soloVencidas;
        if (filtroSoloVencidas && esAdmin) {
            spec = spec.and(FacturaSpecifications.vencidas(LocalDate.now()));
        }

        // filtro sucursal (solo PDV)
        if (!esAdmin && auth != null) {
            var optUsuario = usuarioRepository.findByUsername(auth.getName());
            if (optUsuario.isPresent() && optUsuario.get().getSucursal() != null) {
                Sucursal sucursal = optUsuario.get().getSucursal();

                // Mapeo simple por prefijo (esto depende del formato real de numeroFactura)
                spec = spec.and(switch (sucursal) {
                    case SANTA_FE -> FacturaSpecifications.numeroFacturaPrefijo("0104", "104");
                    case RAFAELA -> FacturaSpecifications.numeroFacturaPrefijo("0109", "109");
                    case RECONQUISTA -> FacturaSpecifications.numeroFacturaPrefijo("0105", "105");
                });
            }
        }

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.max(size, 1),
                Sort.by(Sort.Direction.DESC, "fechaFactura").and(Sort.by(Sort.Direction.DESC, "id"))
        );

        Page<Factura> facturasPage = facturaRepository.findAll(spec, pageable);

        model.addAttribute("facturasPage", facturasPage);
        model.addAttribute("facturas", facturasPage.getContent());
        model.addAttribute("page", facturasPage.getNumber());
        model.addAttribute("size", facturasPage.getSize());


        // filtros para repintar
        model.addAttribute("estados", EstadoFactura.values());
        model.addAttribute("aseguradoras", aseguradoraRepository.findAll(Sort.by("nombre")));
        model.addAttribute("estadoSeleccionado", estado);
        model.addAttribute("aseguradoraSeleccionada", aseguradoraId);
        model.addAttribute("hoy", LocalDate.now());
        model.addAttribute("soloVencidas", filtroSoloVencidas);
        model.addAttribute("numeroFactura", numeroFactura);
        model.addAttribute("numeroSiniestro", numeroSiniestro);
        model.addAttribute("numeroOrden", numeroOrden);


        return "facturas";
    }
    @PostMapping("/facturas/{id}/asignar-aseguradora")
    public String asignarAseguradora(@PathVariable Long id,
                                     @RequestParam("aseguradoraId") Long aseguradoraId,
                                     RedirectAttributes ra) {

        Factura factura = facturaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Factura no encontrada"));

        Aseguradora aseguradora = aseguradoraRepository.findById(aseguradoraId)
                .orElseThrow(() -> new RuntimeException("Aseguradora no encontrada"));

        factura.setAseguradora(aseguradora);

        // Si estaba pendiente por no tener aseguradora, la pasamos a NUEVA
        if (factura.getEstado() == EstadoFactura.PENDIENTE_ASIGNACION) {
            factura.setEstado(EstadoFactura.NUEVA);
        }

        facturaRepository.save(factura);

        historialFacturaService.registrarAccionSimple(
                factura,
                "ASIGNACION_ASEGURADORA",
                "Aseguradora asignada manualmente: " + aseguradora.getNombre(),
                "admin"
        );

        ra.addFlashAttribute("ok", "Aseguradora asignada a la factura " + factura.getNumeroFactura());
        return "redirect:/facturas";
    }


    //Accion de "enviar" solo para el admin
    // Acciones por fila: procesar / rechazar / reenviar / enviar
    @PostMapping("/facturas/{id}/accion")
    public String ejecutarAccionFactura(
            @PathVariable Long id,
            @RequestParam("accion") String accion,
            RedirectAttributes redirectAttributes) {

        var optFactura = facturaRepository.findById(id);
        if (optFactura.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Factura no encontrada.");
            return "redirect:/facturas";
        }

        Factura factura = optFactura.get();
        String usuario = getUsuarioActual();

        // Si está enviada o cerrada, ya no se puede hacer nada
        if (esEstadoDefinitivo(factura.getEstado())) {
            redirectAttributes.addFlashAttribute(
                    "error",
                    "La factura ya está cerrada/enviada y no se puede modificar."
            );
            return "redirect:/facturas";
        }

        try {
            switch (accion) {
                case "cerrar_manual" -> {

                    var auth = SecurityContextHolder.getContext().getAuthentication();
                    boolean esAdmin = auth != null &&
                            auth.getAuthorities().stream()
                                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

                    if (!esAdmin) {
                        redirectAttributes.addFlashAttribute("error",
                                "No tiene permisos para cerrar facturas.");
                        return "redirect:/facturas";
                    }

                    var estadoAnterior = factura.getEstado();
                    factura.setEstado(EstadoFactura.CERRADA_MANUALMENTE);
                    // FECHA DE ENVÍO
                    factura.setFechaEnvio(LocalDateTime.now());
                    facturaRepository.save(factura);

                    historialFacturaService.registrarCambioEstado(
                            factura,
                            estadoAnterior,
                            factura.getEstado(),
                            auth.getName()
                    );

                    historialFacturaService.registrarAccionSimple(
                            factura,
                            "CIERRE_MANUAL",
                            "Factura cerrada manualmente sin envío de correo.",
                            auth.getName()
                    );

                    redirectAttributes.addFlashAttribute("mensaje",
                            "Factura cerrada manualmente.");
                }

                case "reenviar" -> {
                    historialFacturaService.registrarAccionSimple(
                            factura, "REENVIO", "Reenvío manual desde panel.", usuario);
                    redirectAttributes.addFlashAttribute("mensaje",
                            "Acción de reenvío registrada (luego la ligamos a un mail real).");
                }

                case "enviar" -> {
                    // chequeo de rol ADMIN
                    var authentication = SecurityContextHolder.getContext().getAuthentication();
                    boolean esAdmin = authentication != null &&
                            authentication.getAuthorities().stream()
                                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

                    if (!esAdmin) {
                        redirectAttributes.addFlashAttribute("error",
                                "No tiene permisos para enviar facturas.");
                        return "redirect:/facturas";
                    }

                    if (factura.getNombreArchivoAdicional() == null) {
                        redirectAttributes.addFlashAttribute("error",
                                "La factura no tiene adjunto extra. No se puede enviar.");
                        return "redirect:/facturas";
                    }

                    // Envío de correo
                    emailEnvioFacturaService.enviarFacturaAseguradora(factura);
                    // FECHA DE ENVÍO
                    factura.setFechaEnvio(LocalDateTime.now());

                    var estadoAnterior = factura.getEstado();
                    factura.setEstado(EstadoFactura.ENVIADA_A_SEGURO);
                    facturaRepository.save(factura);

                    historialFacturaService.registrarCambioEstado(
                            factura, estadoAnterior, factura.getEstado(), usuario
                    );

                    String nombreAseguradora = factura.getAseguradora() != null
                            ? factura.getAseguradora().getNombre()
                            : "(sin aseguradora)";

                    historialFacturaService.registrarAccionSimple(
                            factura,
                            "ENVIO_A_ASEGURADORA",
                            "Factura enviada a la aseguradora " + nombreAseguradora,
                            usuario
                    );

                    redirectAttributes.addFlashAttribute("mensaje",
                            "Factura enviada a la aseguradora correctamente.");
                }

                default -> redirectAttributes.addFlashAttribute("error", "Acción no reconocida.");
            }

        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error",
                    "Error al ejecutar la acción: " + e.getMessage());
        }

        return "redirect:/facturas";
    }

    //Ver historial de una factura
    @GetMapping("/facturas/{id}/historial")
    public String verHistorial(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<Factura> optFactura = facturaRepository.findById(id);
        if (optFactura.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Factura no encontrada.");
            return "redirect:/facturas";
        }

        model.addAttribute("factura", optFactura.get());
        model.addAttribute("historial", historialFacturaService.listarPorFactura(id));

        return "facturas-historial"; // plantilla aparte, por ejemplo
    }
    //Adjuntar segundo archivo
    @PostMapping("/facturas/{id}/adjunto-extra")
    public String subirAdjuntoExtra(@PathVariable Long id,
                                    @RequestParam("archivoExtra") MultipartFile archivoExtra,
                                    RedirectAttributes redirectAttributes) {

        String usuario = getUsuarioActual();

        try {
            var optFactura = facturaRepository.findById(id);
            if (optFactura.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Factura no encontrada.");
                return "redirect:/facturas";
            }

            var factura = optFactura.get();

            // No permitir adjuntar si ya está cerrada/enviada
            if (esEstadoDefinitivo(factura.getEstado())) {
                redirectAttributes.addFlashAttribute(
                        "error",
                        "La factura ya está cerrada/enviada. No se pueden adjuntar nuevos archivos."
                );
                return "redirect:/facturas";
            }

            if (archivoExtra.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Debe seleccionar un archivo PDF.");
                return "redirect:/facturas";
            }

            Path carpeta = Paths.get(rutaArchivos).toAbsolutePath().normalize();
            Files.createDirectories(carpeta);

            String nombreLimpio = archivoExtra.getOriginalFilename();
            if (nombreLimpio == null || nombreLimpio.isBlank()) {
                nombreLimpio = "adjunto.pdf";
            }

            String nombreGuardado = "extra_" + System.currentTimeMillis() + "_" + nombreLimpio;
            Path destino = carpeta.resolve(nombreGuardado);
            Files.copy(archivoExtra.getInputStream(), destino);

            factura.setNombreArchivoAdicional(nombreGuardado);
            factura.setEstado(EstadoFactura.LISTA_PARA_ENVIAR);
            facturaRepository.save(factura);

            historialFacturaService.registrarAccionSimple(
                    factura,
                    "ADJUNTO_EXTRA",
                    "Se cargó un PDF adicional para la factura.",
                    usuario
            );

            redirectAttributes.addFlashAttribute("mensaje",
                    "Adjunto cargado correctamente. La factura está lista para enviar.");

        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error",
                    "Error al guardar el archivo: " + e.getMessage());
        }

        return "redirect:/facturas";
    }

    @GetMapping("/facturas/{id}/adjunto-extra")
    public ResponseEntity<Resource> descargarAdjuntoExtra(@PathVariable Long id) throws Exception {

        Factura factura = facturaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Factura no encontrada"));

        if (factura.getNombreArchivoAdicional() == null) {
            return ResponseEntity.notFound().build();
        }

        Path carpeta = Paths.get(rutaArchivos).toAbsolutePath().normalize();
        Path archivo = carpeta.resolve(factura.getNombreArchivoAdicional());

        if (!Files.exists(archivo)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new UrlResource(archivo.toUri());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + archivo.getFileName().toString() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(resource);
    }
    @PostMapping("/facturas/{id}/observaciones")
    @PreAuthorize("hasRole('ADMIN')")
    public String guardarObservaciones(
            @PathVariable Long id,
            @RequestParam String observaciones) {

        Factura factura = facturaRepository.findById(id)
                .orElseThrow();

        factura.setObservacionesAdmin(observaciones);
        facturaRepository.save(factura);

        return "redirect:/facturas";
    }
    
    private boolean esEstadoDefinitivo(EstadoFactura estado) {
        return estado == EstadoFactura.ENVIADA_A_SEGURO
                || estado == EstadoFactura.CERRADA_MANUALMENTE;
    }
    private boolean perteneceASucursal(String numeroFactura, Sucursal sucursal) {
        if (numeroFactura == null || sucursal == null) {
            return false;
        }

        // Dejar solo dígitos: "0104-00062226" -> "010400062226"
        String soloDigitos = numeroFactura.replaceAll("\\D", "");
        if (soloDigitos.length() < 3) {
            return false;
        }

        // Opciones de prefijo por sucursal
        return switch (sucursal) {
            case SANTA_FE -> soloDigitos.startsWith("104")  || soloDigitos.startsWith("0104");
            case RAFAELA  -> soloDigitos.startsWith("109")  || soloDigitos.startsWith("0109");
            case RECONQUISTA -> soloDigitos.startsWith("105") || soloDigitos.startsWith("0105");
        };
    }
    private Specification<Factura> andIfNotNull(
            Specification<Factura> base,
            Specification<Factura> extra) {

        return extra == null ? base : base.and(extra);
    }

}
