package com.agencia.seguros.controller;

import com.agencia.seguros.model.Sucursal;
import com.agencia.seguros.model.Usuario;
import com.agencia.seguros.repository.UsuarioRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Controller
@RequestMapping("/usuarios")
@PreAuthorize("hasRole('ADMIN')")
public class UsuarioController {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioController(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("usuarios", usuarioRepository.findAll());
        return "usuarios";
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        Usuario u = new Usuario();
        u.setRol("PDV");
        u.setSucursal(Sucursal.SANTA_FE);

        model.addAttribute("usuario", u);
        model.addAttribute("sucursales", Sucursal.values());
        model.addAttribute("modoEdicion", false);
        return "usuario-form";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, Model model, RedirectAttributes ra) {
        Optional<Usuario> opt = usuarioRepository.findById(id);
        if (opt.isEmpty()) {
            ra.addFlashAttribute("error", "Usuario no encontrado.");
            return "redirect:/usuarios";
        }

        model.addAttribute("usuario", opt.get());
        model.addAttribute("sucursales", Sucursal.values());
        model.addAttribute("modoEdicion", true);
        return "usuario-form";
    }

    @PostMapping("/guardar")
    public String guardar(@ModelAttribute Usuario usuario,
                          @RequestParam(required = false) String passwordPlano,
                          RedirectAttributes ra) {

        // Normalizar
        if (usuario.getUsername() != null) {
            usuario.setUsername(usuario.getUsername().trim());
        }

        // Validaciones mínimas
        if (usuario.getUsername() == null || usuario.getUsername().isBlank()) {
            ra.addFlashAttribute("error", "El username es obligatorio.");
            return "redirect:/usuarios";
        }
        if (usuario.getRol() == null || usuario.getRol().isBlank()) {
            ra.addFlashAttribute("error", "El rol es obligatorio.");
            return "redirect:/usuarios";
        }

        boolean esNuevo = (usuario.getId() == null);

        // Username único
        var existente = usuarioRepository.findByUsername(usuario.getUsername());
        if (existente.isPresent() && (esNuevo || !existente.get().getId().equals(usuario.getId()))) {
            ra.addFlashAttribute("error", "Ya existe un usuario con ese username.");
            return "redirect:/usuarios";
        }

        if (esNuevo) {
            if (passwordPlano == null || passwordPlano.isBlank()) {
                ra.addFlashAttribute("error", "La contraseña es obligatoria al crear un usuario.");
                return "redirect:/usuarios/nuevo";
            }
            usuario.setPassword(passwordEncoder.encode(passwordPlano));
        } else {
            // edición: si no mandan passwordPlano, mantenemos la que ya tenía
            Usuario actual = usuarioRepository.findById(usuario.getId()).orElse(null);
            if (actual == null) {
                ra.addFlashAttribute("error", "Usuario no encontrado.");
                return "redirect:/usuarios";
            }

            if (passwordPlano != null && !passwordPlano.isBlank()) {
                usuario.setPassword(passwordEncoder.encode(passwordPlano));
            } else {
                usuario.setPassword(actual.getPassword());
            }
        }

        // regla: ADMIN puede tener sucursal null
        if ("ADMIN".equalsIgnoreCase(usuario.getRol())) {
            usuario.setSucursal(null);
        }

        usuarioRepository.save(usuario);
        ra.addFlashAttribute("mensaje", "Usuario guardado correctamente.");
        return "redirect:/usuarios";
    }

    @PostMapping("/{id}/reset-password")
    public String resetPassword(@PathVariable Long id, RedirectAttributes ra) {
        Usuario u = usuarioRepository.findById(id).orElse(null);
        if (u == null) {
            ra.addFlashAttribute("error", "Usuario no encontrado.");
            return "redirect:/usuarios";
        }

        u.setPassword(passwordEncoder.encode("1234"));
        usuarioRepository.save(u);

        ra.addFlashAttribute("mensaje", "Contraseña reseteada a 1234 para " + u.getUsername());
        return "redirect:/usuarios";
    }

    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable Long id, RedirectAttributes ra) {
        if (!usuarioRepository.existsById(id)) {
            ra.addFlashAttribute("error", "Usuario no encontrado.");
            return "redirect:/usuarios";
        }
        usuarioRepository.deleteById(id);
        ra.addFlashAttribute("mensaje", "Usuario eliminado.");
        return "redirect:/usuarios";
    }
}
