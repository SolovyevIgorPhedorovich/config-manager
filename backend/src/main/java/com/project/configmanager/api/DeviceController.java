package com.project.configmanager.api;

import com.project.configmanager.device.dto.DeviceRequest;
import com.project.configmanager.device.dto.DeviceResponse;
import com.project.configmanager.device.facade.DeviceFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceFacade deviceFacade;
    @GetMapping
    public List<DeviceResponse> getAll() {
        return deviceFacade.getAll();
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
    public void delete(@PathVariable Long id, @RequestBody String actor) {
        deviceFacade.delete(id, actor);
    }

    @GetMapping("/scan")
    public ResponseEntity<String> startScan(
            @RequestParam String ipaddr,
            @RequestParam(defaultValue = "24") int mask,
            @RequestParam(defaultValue = "161") int port,
            @RequestParam(defaultValue = "public") String community,
            @RequestParam(defaultValue = "v2c") String snmpv
    ) {
        return ResponseEntity.ok(
                //netScanService.startScan(ipaddr, mask, port, community, snmpv)
                "scan"
        );
    }

/*    @GetMapping("/scan/status")
    public ResponseEntity<Map<String, Object>> getScanStatus(
            @RequestParam String taskId
    ) {
        return ResponseEntity.ok(netScanService.getResult(taskId));
    }
*/

}