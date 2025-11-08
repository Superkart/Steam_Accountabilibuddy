package com.cs484.steamaccountibilibuddy.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Adapted DTO for Steam OpenID callback parameters.
 * Fields map to openid.* parameters Steam sends.
 */
@Data
@NoArgsConstructor
public class SteamOpenidLoginDTO {
    private String ns;                 // openid.ns
    private String mode;               // openid.mode
    private String opEndpoint;         // openid.op_endpoint
    private String claimedId;          // openid.claimed_id
    private String identity;           // openid.identity
    private String returnTo;           // openid.return_to
    private String responseNonce;      // openid.response_nonce
    private String assocHandle;        // openid.assoc_handle
    private String signed;             // openid.signed
    private String sig;                // openid.sig
}
