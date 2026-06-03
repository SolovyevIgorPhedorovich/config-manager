// facade/AuthFacade.java
package com.uniikm.configmanager.auth.facade;

import com.uniikm.configmanager.auth.dto.LoginRequest;
import com.uniikm.configmanager.auth.dto.LoginResponse;

public interface AuthFacade {
    LoginResponse login(LoginRequest request);
    String getCurrentUser();
}