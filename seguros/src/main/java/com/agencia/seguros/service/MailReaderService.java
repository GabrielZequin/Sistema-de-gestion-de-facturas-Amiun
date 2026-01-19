package com.agencia.seguros.service;

import com.agencia.seguros.config.CorreoImapProperties;
import com.agencia.seguros.model.Aseguradora;
import com.agencia.seguros.model.EstadoFactura;
import com.agencia.seguros.model.Factura;
import com.agencia.seguros.repository.AseguradoraRepository;
import com.agencia.seguros.repository.FacturaRepository;
import jakarta.mail.*;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.search.FlagTerm;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.text.Normalizer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MailReaderService {

    private final CorreoImapProperties props;
    private final FacturaRepository facturaRepository;
    private final AseguradoraRepository aseguradoraRepository;
    private final HistorialFacturaService historialFacturaService;

    @Value("${app.facturas.ruta-archivos}")
    private String rutaArchivos;

    // remitente real (mail)
    @Value("${app.correo.remitente-esperado:aperez@amiun.com.ar}")
    private String remitenteEsperado;

    // asunto esperado (opcional)
    @Value("${app.correo.asunto-esperado:Has recibido un nuevo comprobante}")
    private String asuntoEsperado;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    public MailReaderService(CorreoImapProperties props,
                             FacturaRepository facturaRepository,
                             AseguradoraRepository aseguradoraRepository,
                             HistorialFacturaService historialFacturaService) {
        this.props = props;
        this.facturaRepository = facturaRepository;
        this.aseguradoraRepository = aseguradoraRepository;
        this.historialFacturaService = historialFacturaService;
    }

    @Scheduled(fixedDelay = 60_000)
    public void leerNuevosCorreos() {

        // En dev (enabled=false) no hace nada
        if (!mailEnabled) {
            return;
        }

        System.out.println("Buscando correos nuevos (no leídos)...");

        Properties properties = new Properties();
        properties.put("mail.store.protocol", "imaps");

        try {
            Session session = Session.getInstance(properties);
            Store store = session.getStore("imaps");
            store.connect(props.getHost(), props.getUsername(), props.getPassword());

            Folder folder = store.getFolder(props.getFolder());
            folder.open(Folder.READ_WRITE);

            Message[] messages = folder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
            System.out.println("Correos NO leídos encontrados: " + messages.length);

            for (Message message : messages) {
                try {
                    procesarMensaje(message);
                    message.setFlag(Flags.Flag.SEEN, true);
                } catch (Exception ex) {
                    System.err.println("Error procesando mail: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }

            folder.close(false);
            store.close();

        } catch (Exception e) {
            System.err.println("Error leyendo correos: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void procesarMensaje(Message message) throws Exception {
        String asunto = safe(message.getSubject());
        String remitenteRaw = extraerFromRaw(message);
        String remitenteEmail = extraerEmail(remitenteRaw).toLowerCase(Locale.ROOT);

        // 1) Message-ID
        String messageId = extraerMessageId(message);
        if (messageId != null && !messageId.isBlank()) {
            if (facturaRepository.existsByMessageId(messageId)) {
                System.out.println("Saltando mail (ya procesado). Message-ID: " + messageId);
                return;
            }
        }

        boolean remitenteOk = remitenteEmail.contains(remitenteEsperado.toLowerCase(Locale.ROOT));
        boolean asuntoOk    = asunto.toLowerCase(Locale.ROOT)
                .contains(asuntoEsperado.toLowerCase(Locale.ROOT));

        if (!remitenteOk && !asuntoOk) {
            System.out.println("Saltando mail: no coincide remitente ni asunto.");
            System.out.println("De: " + remitenteRaw);
            System.out.println("Asunto: " + asunto);
            return;
        }

        // log opcional
        if (!remitenteOk && asuntoOk) {
            System.out.println("Mail aceptado por asunto, aunque remitente no esperado.");
        }

        if (remitenteOk && !asuntoOk) {
            System.out.println("Mail aceptado por remitente, aunque asunto distinto.");
        }


        System.out.println("--------");
        System.out.println("Asunto: " + asunto);
        System.out.println("De: " + remitenteRaw);
        System.out.println("Message-ID: " + messageId);

        Object content = message.getContent();
        if (!(content instanceof Multipart multipart)) {
            System.out.println("Mail sin multipart, no hay adjuntos PDF.");
            return;
        }

        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart bodyPart = multipart.getBodyPart(i);

            String fileName = bodyPart.getFileName();
            String disposition = bodyPart.getDisposition();

            boolean esAdjunto = Part.ATTACHMENT.equalsIgnoreCase(disposition)
                    || (fileName != null && !fileName.isBlank());

            if (esAdjunto && fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                if (bodyPart instanceof MimeBodyPart mimeBodyPart) {
                    guardarAdjuntoPdf(mimeBodyPart, fileName, asunto, remitenteRaw, messageId);
                } else {
                    System.out.println("Adjunto PDF no es MimeBodyPart (se ignora). File: " + fileName);
                }
            }
        }
    }

    private void guardarAdjuntoPdf(MimeBodyPart bodyPart,
                                   String fileName,
                                   String asunto,
                                   String remitente,
                                   String messageId) throws Exception {

        Path carpeta = Path.of(rutaArchivos);
        Files.createDirectories(carpeta);

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String nombreGuardado = timestamp + "_" + fileName;
        Path destino = carpeta.resolve(nombreGuardado);

        try (InputStream is = bodyPart.getInputStream()) {
            Files.copy(is, destino, StandardCopyOption.REPLACE_EXISTING);
        }

        System.out.println("PDF guardado: " + destino.toAbsolutePath());

        Factura factura = new Factura();
        factura.setAsunto(asunto);
        factura.setRemitente(remitente);
        factura.setNombreArchivo(nombreGuardado);
        factura.setFechaRecepcion(LocalDateTime.now());

        // guardar messageId
        if (messageId != null && !messageId.isBlank()) {
            factura.setMessageId(messageId);
        }

        LocalDate fechaFactura = extraerFechaFacturaDesdePdf(destino);
        factura.setFechaFactura(fechaFactura);

        Map<String, String> datos = extraerDatosDesdePdf(destino);
        factura.setNumeroFactura(datos.get("numeroFactura"));
        factura.setNumeroSiniestro(datos.get("numeroSiniestro"));
        factura.setNumeroOrden(datos.get("numeroOrden"));

        factura.setEstado(EstadoFactura.NUEVA);

        Aseguradora aseguradora = detectarAseguradoraDesdePdf(destino);
        if (aseguradora == null) {
            aseguradora = detectarAseguradora(asunto, remitente);
        }

        if (aseguradora != null) {
            factura.setAseguradora(aseguradora);
        } else {
            factura.setEstado(EstadoFactura.PENDIENTE_ASIGNACION);
        }

        facturaRepository.save(factura);

        historialFacturaService.registrarAccionSimple(factura,
                "CREACION",
                "Factura creada desde correo IMAP.",
                "sistema");
    }

    // ===== helpers mail =====

    private String extraerMessageId(Message message) {
        try {
            String[] headers = message.getHeader("Message-ID");
            if (headers != null && headers.length > 0) return headers[0];
        } catch (Exception ignored) {
        }
        return null;
    }

    private String extraerFromRaw(Message message) throws MessagingException {
        Address[] from = message.getFrom();
        return (from != null && from.length > 0) ? from[0].toString() : "(desconocido)";
    }

    private String extraerEmail(String fromRaw) {
        Matcher m = Pattern.compile("<([^>]+)>").matcher(fromRaw);
        if (m.find()) return m.group(1);
        return fromRaw;
    }

    private String safe(String s) {
        return (s == null) ? "" : s;
    }

    // ===== extracción PDF =====

    private LocalDate extraerFechaFacturaDesdePdf(Path pdfPath) {
        try (PDDocument doc = PDDocument.load(pdfPath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String texto = stripper.getText(doc);
            if (texto == null) return null;

            Pattern pattern = Pattern.compile("\\b(\\d{2})/(\\d{2})/(\\d{4})\\b");
            Matcher matcher = pattern.matcher(texto);

            if (matcher.find()) {
                int dia = Integer.parseInt(matcher.group(1));
                int mes = Integer.parseInt(matcher.group(2));
                int anio = Integer.parseInt(matcher.group(3));
                return LocalDate.of(anio, mes, dia);
            }
        } catch (Exception e) {
            System.out.println("No se pudo extraer fecha del PDF: " + e.getMessage());
        }
        return null;
    }
    // ===== extracción de datos del PDF =====

    private Map<String, String> extraerDatosDesdePdf(Path pdfPath) {
        Map<String, String> datos = new HashMap<>();

        try (PDDocument doc = PDDocument.load(pdfPath.toFile())) {

            Map<String, String> datos1 = extraerConModo(doc, true);
            // Si faltan campos importantes, probamos modo alternativo
            if (!datos1.containsKey("numeroFactura") || !datos1.containsKey("numeroOrden") || !datos1.containsKey("numeroSiniestro")) {
                Map<String, String> datos2 = extraerConModo(doc, false);
                // quedate con el que tenga más datos
                datos = (datos2.size() > datos1.size()) ? datos2 : datos1;
            } else {
                datos = datos1;
            }

        } catch (Exception e) {
            System.out.println("Error leyendo PDF: " + e.getMessage());
        }

        return datos;
    }

    private Map<String, String> extraerConModo(PDDocument doc, boolean sortByPosition) throws Exception {
        Map<String, String> datos = new HashMap<>();

        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(sortByPosition);

        String texto = stripper.getText(doc);
        if (texto == null) return datos;

        texto = Normalizer.normalize(texto, Normalizer.Form.NFKC);
        texto = texto.replace('\u00A0', ' ');
        texto = texto.replace('\u2212', '-'); // minus sign
        texto = texto.replace('\u2010', '-');
        texto = texto.replace('\u2011', '-');
        texto = texto.replaceAll("[–—]", "-");
        texto = texto.replaceAll("[\\t\\f\\r]", " ");
        texto = texto.replaceAll(" +", " ");

        // FACTURA
        Pattern pFactura = Pattern.compile(
                "(?is)\\bN\\s*(?:[º°oO]|ro\\.?|o)?\\s*[:]?\\s*(\\d{4})\\s*[-−–—]?\\s*(\\d{8})\\b"
        );
        Matcher mFactura = pFactura.matcher(texto);
        if (mFactura.find()) {
            datos.put("numeroFactura", mFactura.group(1) + "-" + mFactura.group(2));
        }

        // SINIESTRO
        Pattern pSiniestro = Pattern.compile(
                "(?is)\\b(?:n[uú]mero|n(?:ro)?\\.?|n[º°o]|no)\\s*(?:de\\s*)?siniestro\\b\\s*[:#-]?\\s*([0-9\\-]{6,20})\\b"
        );
        Matcher mSiniestro = pSiniestro.matcher(texto);
        if (mSiniestro.find()) {
            datos.put("numeroSiniestro", mSiniestro.group(1));
        }

        // ORDEN (clave: soporta "Orden de Reparación N° 00344693")
        Pattern pOrden = Pattern.compile(
                "(?is)\\b(?:s\\/)?orden(?:\\s+de\\s+reparaci[oó]n)?\\b" +
                        "(?:\\s*(?:n(?:ro)?\\.?|n[º°o]|no)\\s*[:#-]?)?\\s*" +
                        "([0-9]{4,12})\\b"
        );
        Matcher mOrden = pOrden.matcher(texto);
        if (mOrden.find()) {
            datos.put("numeroOrden", mOrden.group(1));
        }

        return datos;
    }

    // DETECCIÓN DE ASEGURADORA

    private static final Map<String, List<String>> ASEGURADORA_ALIASES = new LinkedHashMap<>() {{
        put("Allianz", List.of("allianz"));
        put("Berkley", List.of("berkley"));
        put("Chubb", List.of("chubb"));
        put("Cooperación", List.of("cooperacion", "cooperacion seguros"));
        put("El Norte", List.of("el norte"));
        put("Federación Patronal", List.of("federacion patronal", "federacion", "federación"));
        put("HDI", List.of("hdi"));
        put("Instituto Autárquico", List.of("instituto autarquico", "instituto autarquico provincial", "instituto seguro"));
        put("La Caja", List.of(
                "la caja",
                "lacaja",
                "caja de seguros",
                "caja de seguros sa",
                "caja de seguros s a",
                "caja de seguros s.a"
        ));
        put("La Perseverancia", List.of("la perseverancia", "perseverancia"));
        put("La Segunda", List.of("la segunda"));
        put("Mapfre", List.of("mapfre"));
        put("Meridional", List.of("meridional"));
        put("Mercantil Andina", List.of("mercantil andina"));
        put("Nación", List.of("nacion", "nación"));
        put("Nativa", List.of("nativa"));
        put("Orbis", List.of("orbis"));
        put("Parana", List.of("parana", "paraná"));
        put("Providencia", List.of("providencia"));
        put("Provincia Seguros", List.of("provincia seguros"));
        put("Rio Uruguay", List.of("rio uruguay", "río uruguay"));
        put("Rivadavia", List.of("rivadavia"));
        put("San Cristobal", List.of("san cristobal", "san cristóbal"));
        put("Sancor Seguros", List.of("sancor", "sancor seguros"));
        put("Segurcoop", List.of("segurcoop"));
        put("Seguro Metal", List.of("seguro metal"));
        put("Sura", List.of("sura"));
        put("Swiss Medical", List.of("swiss medical", "swissmedical"));
        put("Triunfo", List.of("triunfo"));
        put("Victoria", List.of("victoria"));
        put("Zurich / ex Qbe", List.of("zurich", "qbe"));
        put("Boston", List.of("boston"));
    }};


    public Aseguradora detectarAseguradora(String asunto, String remitente) {
        String texto = safe(asunto) + " " + safe(remitente);
        String textoNorm = normalizarTextoDeteccion(texto);

        String nombreDetectado = detectarNombreAseguradoraPorAliases(textoNorm);
        if (nombreDetectado == null) return null;

        return aseguradoraRepository.findByNombreIgnoreCase(nombreDetectado).orElse(null);
    }

    private Aseguradora detectarAseguradoraDesdePdf(Path pdfPath) {
        try (PDDocument doc = PDDocument.load(pdfPath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String texto = stripper.getText(doc);
            if (texto == null || texto.isBlank()) return null;

            String textoNorm = normalizarTextoDeteccion(texto);

            String nombreDetectado = detectarNombreAseguradoraPorAliases(textoNorm);
            if (nombreDetectado == null) return null;

            System.out.println("Aseguradora detectada en PDF: " + nombreDetectado);
            return aseguradoraRepository.findByNombreIgnoreCase(nombreDetectado).orElse(null);

        } catch (Exception e) {
            System.out.println("No se pudo leer el PDF para detectar aseguradora: " + e.getMessage());
            return null;
        }
    }

    private String detectarNombreAseguradoraPorAliases(String textoNorm) {
        for (Map.Entry<String, List<String>> entry : ASEGURADORA_ALIASES.entrySet()) {
            String aseguradoraCanonica = entry.getKey();

            for (String alias : entry.getValue()) {
                String aliasNorm = normalizarTextoDeteccion(alias);
                if (contieneFrase(textoNorm, aliasNorm)) {
                    return aseguradoraCanonica;
                }
            }
        }
        return null;
    }

    private static String normalizarTextoDeteccion(String input) {
        if (input == null) return "";
        String lower = input.toLowerCase(Locale.ROOT);

        String sinTildes = Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");

        String limpio = sinTildes.replaceAll("[^a-z0-9]+", " ");
        return limpio.trim().replaceAll("\\s{2,}", " ");
    }

    private static boolean contieneFrase(String textoNorm, String fraseNorm) {
        if (fraseNorm == null || fraseNorm.isBlank()) return false;
        String t = " " + textoNorm + " ";
        String f = " " + fraseNorm + " ";
        return t.contains(f);
    }
}
