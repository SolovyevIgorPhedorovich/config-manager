package com.uniikm.configmanager.integration.client;

import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
public class SnmpClient {

    private Snmp snmp;
    private CommunityTarget target;
    private TransportMapping<UdpAddress> transport;

    public void connect(String host, int port, String community) {
        try {
            String address = "udp:" + host + "/" + port;
            Address targetAddress = GenericAddress.parse(address);

            transport = new DefaultUdpTransportMapping();
            snmp = new Snmp(transport);
            transport.listen();

            target = new CommunityTarget();
            target.setCommunity(new OctetString(community));
            target.setAddress(targetAddress);
            target.setRetries(2);
            target.setTimeout(3000);
            target.setVersion(SnmpConstants.version2c);

        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize SNMP connection", e);
        }
    }

    public Map<String, String> get(String oid) {
        Map<String, String> result = new HashMap<>();

        if (snmp == null || target == null) {
            throw new IllegalStateException("SNMP client is not connected");
        }

        try {
            PDU pdu = new PDU();
            pdu.add(new VariableBinding(new OID(oid)));
            pdu.setType(PDU.GET);

            ResponseEvent event = snmp.send(pdu, target);

            if (event != null && event.getResponse() != null) {
                PDU response = event.getResponse();

                if (response.getErrorStatus() == PDU.noError) {
                    for (VariableBinding vb : response.getVariableBindings()) {
                        result.put(vb.getOid().toString(), vb.getVariable().toString());
                    }
                } else {
                    result.put("error", response.getErrorStatusText());
                }
            } else {
                result.put("error", "No response from SNMP agent");
            }

        } catch (Exception e) {
            result.put("error", e.getMessage());
        }

        return result;
    }

    public Map<String, String> set(String oid, String type, String value) {
        Map<String, String> result = new HashMap<>();

        if (snmp == null || target == null) {
            throw new IllegalStateException("SNMP client is not connected");
        }

        try {
            Variable variable = createVariable(type, value);
            PDU pdu = new PDU();
            pdu.add(new VariableBinding(new OID(oid), variable));
            pdu.setType(PDU.SET);

            ResponseEvent event = snmp.send(pdu, target);

            if (event != null && event.getResponse() != null) {
                PDU response = event.getResponse();
                if (response.getErrorStatus() == PDU.noError) {
                    result.put(oid, value);
                } else {
                    result.put("error", response.getErrorStatusText());
                }
            } else {
                result.put("error", "No response from SNMP agent");
            }

        } catch (Exception e) {
            result.put("error", e.getMessage());
        }

        return result;
    }

    private Variable createVariable(String type, String value) {
        return switch (type) {
            case "i" -> new Integer32(Integer.parseInt(value));
            case "t" -> new TimeTicks(Long.parseLong(value));
            case "o" -> new OID(value);
            case "u" -> new UnsignedInteger32(Long.parseLong(value));
            default  -> new OctetString(value);
        };
    }

    public void close() {
        try {
            if (snmp != null) {
                snmp.close();
            }
            if (transport != null) {
                transport.close();
            }
        } catch (IOException e) {
            throw new RuntimeException("Error closing SNMP client", e);
        }
    }
}