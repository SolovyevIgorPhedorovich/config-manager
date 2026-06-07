package com.uniikm.configmanager.device.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.uniikm.configmanager.device.dto.ScanCredentialDto;
import com.uniikm.configmanager.device.dto.ScanCredentialRequest;
import com.uniikm.configmanager.device.service.ScanCredentialService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/scan-credentials")
@RequiredArgsConstructor
public class ScanCredentialController {

    private final ScanCredentialService service;

    @GetMapping
    public List<ScanCredentialDto> getAll() {
        return service.getAll();
    }

    @PostMapping
    public ScanCredentialDto create(@RequestBody ScanCredentialRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public ScanCredentialDto update(@PathVariable Long id, @RequestBody ScanCredentialRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
