package com.project.configmanager.integration;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;

public class SNMPAdapter implements ProtocolAdapter {
    
    private String host;
    private int port;
    private String community;
    private String version;
    private Snmp snmp;
    private TransportMapping<?> transport;
    private CommunityTarget target;
    private boolean connected = false;

    public SNMPAdapter() {
        this.version = "2c";
    }

    public SNMPAdapter(String host, int port, String community) {
        this.host = host;
        this.port = port;
        this.community = community;
        this.version = "2c";
    }

    @Override
    public CompletableFuture<Map<String, Object>> executeCommand(String command) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> result = new HashMap<>();
            
            if (!isConnected()) {
                result.put("error", "Not connected to device");
                result.put("success", false);
                return result;
            }
            
            try {
                // Для SNMP "команда" - это OID
                PDU pdu = new PDU();
                pdu.add(new VariableBinding(new OID(command)));
                pdu.setType(PDU.GET);
                
                ResponseEvent event = snmp.send(pdu, target);
                
                if (event != null && event.getResponse() != null) {
                    PDU response = event.getResponse();
                    if (response.getErrorStatus() == PDU.noError) {
                        for (VariableBinding vb : response.getVariableBindings()) {
                            result.put(vb.getOid().toString(), vb.getVariable().toString());
                        }
                        result.put("success", true);
                    } else {
                        result.put("error", "SNMP Error: " + response.getErrorStatusText());
                        result.put("success", false);
                    }
                } else {
                    result.put("error", "No response from device");
                    result.put("success", false);
                }
            } catch (Exception e) {
                result.put("error", e.getMessage());
                result.put("success", false);
            }
            
            return result;
        });
    }

    @Override
    public CompletableFuture<Map<String, Object>> connect(Map<String, String> credentials) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> result = new HashMap<>();
            
            try {
                this.host = credentials.getOrDefault("host", host);
                this.port = Integer.parseInt(credentials.getOrDefault("port", String.valueOf(port != 0 ? port : 161)));
                this.community = credentials.getOrDefault("community", community);
                this.version = credentials.getOrDefault("version", version);
                
                String addressStr = "udp:" + host + "/" + port;
                Address address = GenericAddress.parse(addressStr);
                
                transport = new DefaultUdpTransportMapping();
                snmp = new Snmp(transport);
                transport.listen();
                
                target = new CommunityTarget();
                target.setCommunity(new OctetString(community));
                target.setAddress(address);
                target.setRetries(2);
                target.setTimeout(3000);
                target.setVersion(version.equals("3") ? SnmpConstants.version3 : 
                                 version.equals("1") ? SnmpConstants.version1 : SnmpConstants.version2c);
                
                connected = true;
                result.put("success", true);
                result.put("message", "SNMP connection established to " + host + ":" + port);
                
            } catch (Exception e) {
                connected = false;
                result.put("success", false);
                result.put("error", e.getMessage());
            }
            
            return result;
        });
    }

    @Override
    public CompletableFuture<Void> disconnect() {
        return CompletableFuture.runAsync(() -> {
            try {
                if (snmp != null) {
                    snmp.close();
                }
                if (transport != null) {
                    transport.close();
                }
                connected = false;
            } catch (IOException e) {
                throw new RuntimeException("Error disconnecting SNMP", e);
            }
        });
    }

    @Override
    public boolean isConnected() {
        return connected && snmp != null;
    }
    
    @Override
    public String getProtocolName() {
        return "SNMP";
    }
}