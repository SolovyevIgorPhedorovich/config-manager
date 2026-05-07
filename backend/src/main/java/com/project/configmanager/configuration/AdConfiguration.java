package com.project.configmanager.configuration;

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

        contextSource.setReferral("follow");
        return contextSource;
    }
}