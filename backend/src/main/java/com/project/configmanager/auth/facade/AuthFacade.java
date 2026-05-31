// facade/AuthFacade.java
package com.project.configmanager.auth.facade;

import com.project.configmanager.auth.dto.LoginRequest;
import com.project.configmanager.auth.dto.LoginResponse;

public interface AuthFacade {
    LoginResponse login(LoginRequest request);
    String getCurrentUser();
}