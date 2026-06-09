package com.uniikm.configmanager.configuration;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ldap.core.support.LdapContextSource;

@Configuration
@Data
public class AdConfiguration {

    @Value("${ad.enabled:false}")
    private boolean adEnabled;

    @Value("${ad.url:ldap://dc.company.local:389}")
    private String ldapUrl;

    @Value("${ad.base:DC=company,DC=local}")
    private String baseDn;

    @Value("${ad.userDn:}") 
    private String userDn;

    @Value("${ad.password:}")
    private String password;

    @Bean
    public LdapContextSource ldapContextSource() {
        LdapContextSource contextSource = new LdapContextSource();

        contextSource.setUrl(ldapUrl);
        contextSource.setBase(baseDn);

        if (!userDn.isEmpty()) {
            contextSource.setUserDn(userDn);
        }
        if (!password.isEmpty()) {
            contextSource.setPassword(password);
        }

        // Таймауты, чтобы проверка домена и логин не висели на недоступном DC
        java.util.Map<String, Object> env = new java.util.HashMap<>();
        env.put("com.sun.jndi.ldap.connect.timeout", "3000"); // мс на TCP-коннект
        env.put("com.sun.jndi.ldap.read.timeout", "5000");    // мс на ответ
        contextSource.setBaseEnvironmentProperties(env);

        // ignore (а не follow): AD при поиске от корня домена возвращает
        // referral'ы на DNS-имя домена (test.ru), которое может не резолвиться
        // с хоста приложения и роняет поиск UnknownHostException.
        contextSource.setReferral("ignore");
        return contextSource;
    }
}