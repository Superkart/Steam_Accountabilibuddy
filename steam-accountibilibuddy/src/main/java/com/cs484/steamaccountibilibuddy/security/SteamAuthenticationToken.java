package com.cs484.steamaccountibilibuddy.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

public class SteamAuthenticationToken extends AbstractAuthenticationToken {
    private final String steamId;

    public SteamAuthenticationToken(String steamId, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.steamId = steamId;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return steamId;
    }

    public String getSteamId() {
        return steamId;
    }
}
