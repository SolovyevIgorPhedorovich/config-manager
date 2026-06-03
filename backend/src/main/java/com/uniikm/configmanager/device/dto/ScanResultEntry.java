package com.uniikm.configmanager.device.dto;

public record ScanResultEntry(
        DeviceResponse device,
        String scanStatus  // "NEW", "EXISTING", "UPDATED"
) {}
