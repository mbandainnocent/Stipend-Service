package com.stipend.service.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.stipend.service.Controller.FinanceStipendController;
import com.stipend.service.DTO.MonthlyStipendPageDTO;
import com.stipend.service.service.FinanceStipendReadService;
import com.stipend.service.service.FinanceStipendWorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = FinanceStipendController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "app.jwt.secret=" + SecurityConfigTest.SECRET,
        "app.security.finance-roles=ADMIN,FINANCE",
        "app.security.cors.allowed-origins=https://finance.example.org, http://localhost:3000"
})
class SecurityConfigTest {

    // Same default as the attendance service: 58 bytes, so jjwt signs with HS384.
    static final String SECRET = "change-this-to-a-long-secure-secret-key-at-least-32-bytes";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FinanceStipendReadService readService;

    @MockBean
    private FinanceStipendWorkflowService workflowService;

    @Test
    void requestWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/stipends")).andExpect(status().isUnauthorized());
    }

    @Test
    void financeRolesCanReadStipends() throws Exception {
        when(readService.searchStipends(any(), any(), any(), any(), any(), any()))
                .thenReturn(new MonthlyStipendPageDTO(List.of(), 0, 20, 0, 0));

        for (String role : List.of("ROLE_ADMIN", "ROLE_FINANCE")) {
            mockMvc.perform(get("/api/v1/stipends").header("Authorization", bearer(token(role))))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void studentAndTrainerAreForbidden() throws Exception {
        for (String role : List.of("ROLE_STUDENT", "ROLE_TRAINER")) {
            mockMvc.perform(get("/api/v1/stipends").header("Authorization", bearer(token(role))))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() throws Exception {
        String forged = sign(claims("ROLE_ADMIN", Instant.now().plusSeconds(600)),
                "a-completely-different-secret-that-is-also-58-bytes-long!!");
        mockMvc.perform(get("/api/v1/stipends").header("Authorization", bearer(forged)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        String expired = sign(claims("ROLE_ADMIN", Instant.now().minusSeconds(3600)), SECRET);
        mockMvc.perform(get("/api/v1/stipends").header("Authorization", bearer(expired)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void approvalRecordsTokenSubjectAsApprover() throws Exception {
        UUID stipendId = UUID.randomUUID();
        mockMvc.perform(patch("/api/v1/stipends/{id}/approve", stipendId)
                        .header("Authorization", bearer(token("ROLE_FINANCE"))))
                .andExpect(status().isOk());

        verify(workflowService).approve(eq(stipendId), eq("finance@igire.test"), any());
    }

    @Test
    void algorithmMatchesJjwtKeyLengthRule() {
        assertThat(SecurityConfig.macAlgorithmFor(32)).isEqualTo(MacAlgorithm.HS256);
        assertThat(SecurityConfig.macAlgorithmFor(58)).isEqualTo(MacAlgorithm.HS384);
        assertThat(SecurityConfig.macAlgorithmFor(64)).isEqualTo(MacAlgorithm.HS512);
    }

    private static String token(String role) throws Exception {
        return sign(claims(role, Instant.now().plusSeconds(600)), SECRET);
    }

    /** Shape of the attendance service's access token (JwtService.generateAccessToken). */
    private static JWTClaimsSet claims(String role, Instant expiresAt) {
        return new JWTClaimsSet.Builder()
                .subject("finance@igire.test")
                .claim("roles", List.of(role))
                .claim("role", role.substring("ROLE_".length()))
                .claim("userId", UUID.randomUUID().toString())
                .issueTime(new Date())
                .expirationTime(Date.from(expiresAt))
                .build();
    }

    private static String sign(JWTClaimsSet claims, String secret) throws Exception {
        byte[] key = secret.getBytes(StandardCharsets.UTF_8);
        JWSAlgorithm algorithm = JWSAlgorithm.parse(SecurityConfig.macAlgorithmFor(key.length).getName());
        SignedJWT jwt = new SignedJWT(new JWSHeader(algorithm), claims);
        jwt.sign(new MACSigner(key));
        return jwt.serialize();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    void bulkCohortActionsRequireFinanceRole() throws Exception {
        UUID cohortId = UUID.randomUUID();
        for (String path : List.of("/api/v1/cohorts/{id}/stipends/approve", "/api/v1/cohorts/{id}/stipends/mark-paid")) {
            mockMvc.perform(patch(path, cohortId).param("year", "2026").param("month", "10"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(patch(path, cohortId).param("year", "2026").param("month", "10")
                            .header("Authorization", bearer(token("ROLE_STUDENT"))))
                    .andExpect(status().isForbidden());
        }

        mockMvc.perform(patch("/api/v1/cohorts/{id}/stipends/approve", cohortId)
                        .param("year", "2026").param("month", "10")
                        .header("Authorization", bearer(token("ROLE_FINANCE"))))
                .andExpect(status().isOk());
        verify(workflowService).approveCohortPeriod(eq(cohortId), eq(2026), eq(10), eq("finance@igire.test"), any());
    }

    @Test
    void preflightFromAllowedOriginSucceedsWithoutToken() throws Exception {
        mockMvc.perform(options("/api/v1/stipends")
                        .header("Origin", "https://finance.example.org")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://finance.example.org"));
    }

    @Test
    void preflightFromUnknownOriginIsRejected() throws Exception {
        mockMvc.perform(options("/api/v1/stipends")
                        .header("Origin", "https://evil.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
