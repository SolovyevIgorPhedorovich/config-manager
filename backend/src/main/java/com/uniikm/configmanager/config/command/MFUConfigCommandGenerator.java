package com.uniikm.configmanager.config.command;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class MFUConfigCommandGenerator implements ConfigCommandGenerator {

    // MIB-II (RFC 1213) numeric OIDs
    private static final String OID_SYS_NAME     = "1.3.6.1.2.1.1.5.0";
    private static final String OID_SYS_LOCATION = "1.3.6.1.2.1.1.6.0";
    private static final String OID_SYS_CONTACT  = "1.3.6.1.2.1.1.4.0";

    @Override
    public String generateCommand(JsonNode config) {
        boolean persist = !config.has("saveToMemory") || config.get("saveToMemory").asBoolean();
        String brand = str(config, "brand");

        StringBuilder sb = new StringBuilder();
        appendMibII(sb, config);
        appendVendorSpecific(sb, config, brand);
        appendSaveToNvram(sb, brand, persist);
        return sb.toString();
    }

    private void appendMibII(StringBuilder sb, JsonNode c) {
        String sysName = str(c, "deviceName");
        if (!sysName.isEmpty()) {
            sb.append("SET ").append(OID_SYS_NAME).append(" s ").append(sysName).append("\n");
        }
        String location = str(c, "location");
        if (!location.isEmpty()) {
            sb.append("SET ").append(OID_SYS_LOCATION).append(" s ").append(location).append("\n");
        }
        String contact = str(c, "contact");
        if (!contact.isEmpty()) {
            sb.append("SET ").append(OID_SYS_CONTACT).append(" s ").append(contact).append("\n");
        }
    }

    private void appendVendorSpecific(StringBuilder sb, JsonNode c, String brand) {
        switch (brand.toLowerCase()) {
            case "kyocera" -> appendKyocera(sb, c);
            case "canon"   -> appendCanon(sb, c);
            case "ricoh"   -> appendRicoh(sb, c);
            case "hp"      -> appendHp(sb, c);
            case "xerox"   -> appendXerox(sb, c);
            default        -> { /* only MIB-II OIDs */ }
        }
    }

    private void appendKyocera(StringBuilder sb, JsonNode c) {
        String K = "1.3.6.1.4.1.1347";
        if (c.has("tonerSave")) {
            sb.append("SET ").append(K).append(".43.5.1.1.30.1 i ").append(bool(c, "tonerSave") ? "1" : "0").append("\n");
        }
        String resolution = str(c, "resolution");
        if (!resolution.isEmpty()) {
            int dpi = resolution.replace("dpi", "").trim().equals("1200") ? 1200
                    : resolution.replace("dpi", "").trim().equals("300") ? 300 : 600;
            sb.append("SET ").append(K).append(".43.10.1.1.7.1 i ").append(dpi).append("\n");
        }
        String powerSave = str(c, "powerSaveMinutes");
        if (!powerSave.isEmpty()) {
            sb.append("SET ").append(K).append(".43.5.1.1.9.1 i ").append(Integer.parseInt(powerSave) * 60).append("\n");
        }
        String duplex = str(c, "duplex");
        if (!duplex.isEmpty()) {
            int duplexVal = "long-edge".equals(duplex) ? 2 : "short-edge".equals(duplex) ? 3 : 1;
            sb.append("SET ").append(K).append(".43.5.1.1.28.1 i ").append(duplexVal).append("\n");
        }
        String copies = str(c, "copies");
        if (!copies.isEmpty()) {
            sb.append("SET ").append(K).append(".43.5.1.1.5.1 i ").append(copies).append("\n");
        }
    }

    private void appendCanon(StringBuilder sb, JsonNode c) {
        String C = "1.3.6.1.4.1.1602";
        String powerSave = str(c, "powerSaveMinutes");
        if (!powerSave.isEmpty()) {
            sb.append("SET ").append(C).append(".1.11.1.7.0 i ").append(powerSave).append("\n");
        }
        if (bool(c, "tonerSave")) {
            sb.append("SET ").append(C).append(".1.11.1.10.0 i 1\n");
        }
    }

    private void appendRicoh(StringBuilder sb, JsonNode c) {
        String R = "1.3.6.1.4.1.367";
        String powerSave = str(c, "powerSaveMinutes");
        if (!powerSave.isEmpty()) {
            sb.append("SET ").append(R).append(".3.2.2.2.15.0 i ").append(powerSave).append("\n");
        }
    }

    private void appendHp(StringBuilder sb, JsonNode c) {
        String H = "1.3.6.1.4.1.11";
        String powerSave = str(c, "powerSaveMinutes");
        if (!powerSave.isEmpty()) {
            sb.append("SET ").append(H).append(".2.3.9.4.2.1.1.5.26.0 i ").append(Integer.parseInt(powerSave) * 60).append("\n");
        }
        if (bool(c, "tonerSave")) {
            sb.append("SET ").append(H).append(".2.3.9.4.2.1.1.5.1.0 i 4\n");
        }
    }

    private void appendXerox(StringBuilder sb, JsonNode c) {
        String X = "1.3.6.1.4.1.253";
        String powerSave = str(c, "powerSaveMinutes");
        if (!powerSave.isEmpty()) {
            sb.append("SET ").append(X).append(".8.53.17.2.1.5.1 i ").append(powerSave).append("\n");
        }
    }

    private void appendSaveToNvram(StringBuilder sb, String brand, boolean persist) {
        if (!persist) return;
        switch (brand.toLowerCase()) {
            case "kyocera" -> sb.append("SET 1.3.6.1.4.1.1347.43.5.1.1.2.1 i 3\n");
            case "ricoh"   -> sb.append("SET 1.3.6.1.4.1.367.3.2.2.2.1.0 i 1\n");
            default        -> { /* HP and others persist automatically or require web UI */ }
        }
    }

    private String str(JsonNode node, String key) {
        return node.has(key) ? node.get(key).asText("").trim() : "";
    }

    private boolean bool(JsonNode node, String key) {
        return node.has(key) && node.get(key).asBoolean();
    }
}
