package com.rummikub.server.api.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/*
 * Fallback SPA: rutas de React Router vía GET sin extensión de fichero.
 * Las rutas /api/** las atienden los {@code @RestController}.
 */
@Controller
public class SpaController {

    // Solo rutas de React Router: sin extensión y que no sean /api/** ni /assets/**
    @GetMapping(value = {"/", "/{path:(?!api$|assets$)[^\\.]*}", "/{path:(?!api$|assets$)[^\\.]*}/**"})
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
