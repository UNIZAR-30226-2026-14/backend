package com.rummikub.server.api.controller;

import com.rummikub.server.application.services.AdminDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminController {

    private final AdminDataService adminDataService;

    public AdminController(AdminDataService adminDataService) {
        this.adminDataService = adminDataService;
    }

    @DeleteMapping("/wipe")
    public ResponseEntity<Void> wipeAllData() {
        adminDataService.wipeAllData();
        return ResponseEntity.noContent().build();
    }
}
