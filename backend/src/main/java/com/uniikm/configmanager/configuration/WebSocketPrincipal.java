package com.uniikm.configmanager.configuration;

import java.security.Principal;
import java.util.List;

public class WebSocketPrincipal implements Principal{

    private final String name;
    private final List<String> roles;

    public WebSocketPrincipal(String name, List<String> roles) {
        this.name = name;
        this.roles = roles;
    }

    @Override
    public String getName() {
        return name;
    }

    public List<String> getRoles() {
        return roles;
    }
}