package com.rummikub.server.api.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/*
 * Fallback SPA: rutas de React Router vía GET sin extensión de fichero.
 * Las rutas /api/** las atienden los {@code @RestController}.
 */
@Controller
public class SpaController {

    @GetMapping(value = {"/", "/{path:[^\\.]*}", "/{path:[^\\.]*}/**"})
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
