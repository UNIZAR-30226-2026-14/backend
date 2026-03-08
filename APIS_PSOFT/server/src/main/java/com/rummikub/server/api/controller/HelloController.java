package com.rummikub.server.api.controller;


import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController                 //para peticiones http
public class HelloController {
    @GetMapping("/ping")        //si llega una peticion get a /ping
    public String ping() {
        return "pong";          //devolvemos pong
    }
}
