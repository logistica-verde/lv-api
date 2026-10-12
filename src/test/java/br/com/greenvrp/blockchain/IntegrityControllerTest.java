package br.com.greenvrp.blockchain;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.greenvrp.api.ResourceNotFoundException;
import br.com.greenvrp.auth.SecurityConfig;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(IntegrityController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "ADMIN")
class IntegrityControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private IntegrityVerifier integrityVerifier;

	@Test
	void returnsTheIntegrityReport() throws Exception {
		given(integrityVerifier.verify(7L)).willReturn(new IntegrityReport(
			7L, IntegrityStatus.VALID, "aa", "aa", "0xabc", 42L, Instant.parse("2026-10-05T12:00:00Z")));

		mockMvc.perform(get("/api/routes/7/integrity"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.operationId").value(7))
			.andExpect(jsonPath("$.status").value("VALID"))
			.andExpect(jsonPath("$.txHash").value("0xabc"))
			.andExpect(jsonPath("$.blockNumber").value(42));
	}

	@Test
	void mapsUnknownOperationToNotFound() throws Exception {
		given(integrityVerifier.verify(99L)).willThrow(new ResourceNotFoundException("Route operation 99 not found"));

		mockMvc.perform(get("/api/routes/99/integrity")).andExpect(status().isNotFound());
	}

	@Test
	void mapsLedgerFailureToServiceUnavailable() throws Exception {
		given(integrityVerifier.verify(7L)).willThrow(new LedgerException("Besu is down"));

		mockMvc.perform(get("/api/routes/7/integrity"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.detail").value("Besu is down"));
	}

}
