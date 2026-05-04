package com.project.configmanager.service;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.project.configmanager.SettingManagerApplication;
import com.project.configmanager.controller.DeviceController;
import com.project.configmanager.model.Device;

@Service
public class NetworkScannerService {


    @Autowired
    private DeviceService deviceService;

    private Device device;

    private static final String baseOID = "1.3.6.1.2.1.1.1.0";
    private List<Device> deviceList;

    private byte[] calculateBroadcastAddres(byte[] ip, int mask){
        byte[] result = new byte[4];
        int fullMask = 0xFFFFFFFF << (32 - mask);
        int hostMask = ~fullMask;
        for (int i = 0; i < 4; i++) {
            result[i] = (byte) ((ip[i] & 0xFF) | (hostMask >> (24 - i * 8)));
        }
        return result;
    }

    // Увеличивает IP-адрес на 1
    private void incrementIp(byte[] ipBytes) {
        for (int i = 3; i >= 0; i--) {
            if (ipBytes[i] == (byte) 0xFF) {
                ipBytes[i] = 0; // Переполнение, переходим к следующему октету
            } else {
                ipBytes[i]++;
                break;
            }
        }
    }

    // Сравнивает два IP-адреса (массивы байт)
    private int compareIpBytes(byte[] ip1, byte[] ip2) {
        for (int i = 0; i < 4; i++) {
            int b1 = ip1[i] & 0xFF;
            int b2 = ip2[i] & 0xFF;
            if (b1 != b2) {
                return Integer.compare(b1, b2);
            }
        }
        return 0;
    }

    public List<Device> scan(String ipaddr, int mask, int port, String community, String snmpv) {
        deviceList = new ArrayList<>();
        try {
            TransportMapping<UdpAddress> transportMapping = new DefaultUdpTransportMapping();
            transportMapping.listen();

            Snmp snmp = new Snmp(transportMapping);
            
            byte[] ip = ipToByteFormat(ipaddr);
            byte[] broadcastAddress = calculateBroadcastAddres(ip, mask); // Конечный адресс

            while (compareIpBytes(ip, broadcastAddress) != 0) {
                String ip_str = ipToStringFormat(ip);
                Address targetAddress = GenericAddress.parse(String.format("udp:%s/%d",ip_str, port));
                CommunityTarget<Address> target = new CommunityTarget<>();
                target.setCommunity(new OctetString(community));
                target.setAddress(targetAddress);
                target.setRetries(2);
                target.setTimeout(1000);
                switch(snmpv){
                    case "v1":
                        target.setVersion(SnmpConstants.version1);
                        break;
                    case "v2c":
                        target.setVersion(SnmpConstants.version2c);
                        break;
                    case "v3":
                        target.setVersion(SnmpConstants.version3);
                        break;
                }

                PDU pdu = new PDU();
                pdu.add(new VariableBinding(new OID(baseOID)));
                pdu.setType(PDU.GET);

                ResponseEvent<Address> event = snmp.send(pdu, target);

                // Обработка ответа
                if (event != null && event.getResponse() != null) {
                    PDU responsePDU = event.getResponse();
                    if (responsePDU.getErrorStatus() == PDU.noError) {
                        for (VariableBinding vb : responsePDU.getVariableBindings()) {
                            System.out.printf("%s -> %s = %s%n", ip, vb.getOid(), vb.getVariable().toString());
                            device = new Device();
                            device.setOsVersion(vb.getVariable().toString().split(",")[1].trim());
                            device.setIp(ip_str);
                            device.setGroupName(vb.getVariable().toString().split(",")[0]);
                            getLoaction(snmp, target); //nd.setLocation(ip);
                            getMACAddre(snmp, target); //md.setMACAddres();
                            getName(snmp, target); //nd.setName(ip);
                            deviceList.add(device);
                        }
                    } else {
                        System.out.printf("%s -> Error: %s", ip, responsePDU.getErrorStatusText());
                    }
                } else {
                    System.out.printf("%s -> No response or timeout.", ip);
                }

                incrementIp(ip);
            }

            snmp.close();

            if (!deviceList.isEmpty()){
                deviceService.addAll(deviceList);
            }
            return deviceList;

        } catch (Exception e)
        {
            e.printStackTrace();
            return null;
        }
    }

    private void getMACAddre(Snmp snmp, CommunityTarget<Address> target) throws IOException { 
        String OID = "1.3.6.1.2.1.2.2.1.6.50";
        PDU pdu = new PDU();
        pdu.add(new VariableBinding(new OID(OID)));
        pdu.setType(PDU.GET);

        ResponseEvent<Address> event = snmp.send(pdu, target);
        if (event != null && event.getResponse() != null) {
            PDU responsePDU = event.getResponse();
            if (responsePDU.getErrorStatus() == PDU.noError) {
                for (VariableBinding vb : responsePDU.getVariableBindings()) {
//                    device.setMACAddres(vb.getVariable().toString());
                }
            } else {
                System.out.printf("Error: %s", responsePDU.getErrorStatusText());
            }
        } else {
            System.out.printf("No response or timeout.");
        }
    }

    private void getLoaction(Snmp snmp, CommunityTarget<Address> target) throws IOException{
        String OID = "1.3.6.1.2.1.1.6.0";
        PDU pdu = new PDU();
        pdu.add(new VariableBinding(new OID(OID)));
        pdu.setType(PDU.GET);

        ResponseEvent<Address> event = snmp.send(pdu, target);
        if (event != null && event.getResponse() != null) {
            PDU responsePDU = event.getResponse();
            if (responsePDU.getErrorStatus() == PDU.noError) {
                for (VariableBinding vb : responsePDU.getVariableBindings()) {
//                    device.setLocation(vb.getVariable().toString());
                }
            } else {
                System.out.printf("Error: %s", responsePDU.getErrorStatusText());
            }
        } else {
            System.out.printf("No response or timeout.");
        }
    }

    private void getName(Snmp snmp, CommunityTarget<Address> target) throws IOException{
        String OID = "1.3.6.1.2.1.1.5.0";
        PDU pdu = new PDU();
        pdu.add(new VariableBinding(new OID(OID)));
        pdu.setType(PDU.GET);

        ResponseEvent<Address> event = snmp.send(pdu, target);
        if (event != null && event.getResponse() != null) {
            PDU responsePDU = event.getResponse();
            if (responsePDU.getErrorStatus() == PDU.noError) {
                for (VariableBinding vb : responsePDU.getVariableBindings()) {
                    device.setHostname(vb.getVariable().toString());
                }
            } else {
                System.out.printf("Error: %s", responsePDU.getErrorStatusText());
            }
        } else {
            System.out.printf("No response or timeout.");
        }
    }

    private byte[] ipToByteFormat(String ip_addr) throws UnknownHostException {
        InetAddress ip = InetAddress.getByName(ip_addr);
        return ip.getAddress();
    }

    private String ipToStringFormat(byte[] ip) {
        return (ip[0] & 0xFF) + "." +
               (ip[1] & 0xFF) + "." +
               (ip[2] & 0xFF) + "." +
               (ip[3] & 0xFF);
    }
}
