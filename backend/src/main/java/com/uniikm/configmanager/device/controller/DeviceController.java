package com.uniikm.configmanager.device.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.uniikm.configmanager.device.dto.BulkDeleteRequest;
import com.uniikm.configmanager.device.dto.BulkDeleteResponse;
import com.uniikm.configmanager.device.dto.DeviceRequest;
import com.uniikm.configmanager.device.dto.DeviceResponse;
import com.uniikm.configmanager.device.facade.DeviceFacade;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceFacade deviceFacade;
    
    @GetMapping
    public List<DeviceResponse> getAll() {
        return deviceFacade.getAll();
    }

    /** Фактическая доступность устройств в сети (ping/TCP): id устройства → online. */
    @GetMapping("/status")
    public Map<Long, Boolean> status() {
        return deviceFacade.getReachability();
    }

    @PostMapping
    public ResponseEntity<DeviceResponse> create(@RequestBody DeviceRequest request) {
        return ResponseEntity.ok(deviceFacade.create(request));
    }

    @GetMapping("/{id}")
    public DeviceResponse getById(@PathVariable Long id) {
        return deviceFacade.getById(id);
    }

    @PutMapping("/{id}")
    public DeviceResponse update(@PathVariable Long id,
                                 @RequestBody DeviceRequest request) {
        return deviceFacade.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id
    ) {
        deviceFacade.delete(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/bulk")
    public ResponseEntity<BulkDeleteResponse> bulkDelete(
            @RequestBody BulkDeleteRequest request
    ) {
        BulkDeleteResponse response = deviceFacade.bulkDelete(request.ids());
        return ResponseEntity.ok(response);
    }
}