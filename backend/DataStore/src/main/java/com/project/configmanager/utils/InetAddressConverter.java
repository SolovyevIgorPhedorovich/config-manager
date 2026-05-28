package com.project.configmanager.utils;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Converter(autoApply = true)
public class InetAddressConverter implements AttributeConverter<InetAddress, String> {

    @Override
    public String convertToDatabaseColumn(InetAddress attribute) {
        return attribute == null ? null : attribute.getHostAddress();
    }

    @Override
    public InetAddress convertToEntityAttribute(String dbData) {
        try {
            String cleanIp = dbData.split("/")[0];
            return InetAddress.getByName(cleanIp);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid inet value from DB: " + dbData, e);
        }
    }
}