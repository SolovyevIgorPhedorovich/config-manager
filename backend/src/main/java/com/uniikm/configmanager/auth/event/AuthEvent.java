// events/AuthEvent.java
package com.uniikm.configmanager.auth.event;

import com.uniikm.configmanager.audit.enums.AuditAction;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class AuthEvent {
    private final AuditAction action;   // LOGIN_SUCCESS, LOGIN_FAILURE, LOGOUT
    private final String username;
    private final String actor;         // обычно тот же username
    private final String reason;        // причина ошибки
    private final Object additionalInfo;
}