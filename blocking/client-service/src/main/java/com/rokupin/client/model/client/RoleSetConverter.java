package com.rokupin.client.model.client;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@Converter
public class RoleSetConverter implements AttributeConverter<Set<ClientRole>, String> {
    @Override
    public String convertToDatabaseColumn(Set<ClientRole> roles) {
        if (roles == null || roles.isEmpty()) {
            return null;
        }
        String roles_str = roles.stream()
                .map(Enum::name)
                .collect(Collectors.joining(","));
        return roles_str;
    }

    @Override
    public Set<ClientRole> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return Collections.emptySet();
        }
        return Arrays.stream(dbData.split(","))
                .map(ClientRole::valueOf)
                .collect(Collectors.toSet());
    }
}
