package com.agencia.seguros.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

//Para que si alguien entra a http://localhost:8080/ no vea 404
@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "redirect:/facturas";
    }
}