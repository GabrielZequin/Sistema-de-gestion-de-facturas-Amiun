package com.agencia.seguros.controller;

import com.agencia.seguros.model.Usuario;
import com.agencia.seguros.repository.UsuarioRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CuentaController {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public CuentaController(UsuarioRepository usuarioRepository,
                            PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/mi-cuenta")
    public String miCuenta() {
        return "mi-cuenta";
    }

    @PostMapping("/mi-cuenta/cambiar-password")
    public String cambiarPassword(@RequestParam String passwordActual,
                                  @RequestParam String passwordNueva,
                                  @RequestParam String passwordConfirmar,
                                  RedirectAttributes ra) {

        String username = SecurityContextHolder.getContext()
                .getAuthentication().getName();

        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElse(null);

        if (usuario == null) {
            ra.addFlashAttribute("error", "Usuario no encontrado.");
            return "redirect:/mi-cuenta";
        }

        if (!passwordEncoder.matches(passwordActual, usuario.getPassword())) {
            ra.addFlashAttribute("error", "La contraseña actual es incorrecta.");
            return "redirect:/mi-cuenta";
        }

        if (passwordNueva == null || passwordNueva.length() < 4) {
            ra.addFlashAttribute("error", "La nueva contraseña debe tener al menos 4 caracteres.");
            return "redirect:/mi-cuenta";
        }

        if (!passwordNueva.equals(passwordConfirmar)) {
            ra.addFlashAttribute("error", "La nueva contraseña no coincide.");
            return "redirect:/mi-cuenta";
        }

        usuario.setPassword(passwordEncoder.encode(passwordNueva));
        usuarioRepository.save(usuario);

        ra.addFlashAttribute("mensaje", "Contraseña actualizada correctamente.");
        return "redirect:/mi-cuenta";
    }
}
