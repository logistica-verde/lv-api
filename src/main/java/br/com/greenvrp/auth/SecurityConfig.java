package br.com.greenvrp.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, BootstrapAdminProperties.class})
public class SecurityConfig {

	private static final String ALGORITHM = "HmacSHA256";

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
		http
			.csrf(AbstractHttpConfigurer::disable)
			.cors(Customizer.withDefaults())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(requests -> requests
				.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
				.requestMatchers("/health", "/api/auth/login").permitAll()
				.requestMatchers("/api/users/**", "/api/settings/**").hasRole("ADMIN")
				.requestMatchers(HttpMethod.POST, "/api/vehicles/**").hasRole("ADMIN")
				.requestMatchers(HttpMethod.PATCH, "/api/vehicles/**").hasRole("ADMIN")
				.requestMatchers(HttpMethod.GET, "/api/routes/*/integrity").hasAnyRole("AUDITOR", "ADMIN")
				.requestMatchers(HttpMethod.POST, "/api/routes/calculate", "/api/delivery-points")
					.hasAnyRole("OPERATOR", "ADMIN")
				.requestMatchers("/api/**").authenticated()
				.anyRequest().denyAll())
			.oauth2ResourceServer(resource -> resource
				.jwt(jwt -> jwt.jwtAuthenticationConverter(authenticationConverter()))
				.authenticationEntryPoint((request, response, exception) ->
					writeProblem(objectMapper, response, HttpStatus.UNAUTHORIZED, "Authentication is required."))
				.accessDeniedHandler((request, response, exception) ->
					writeProblem(objectMapper, response, HttpStatus.FORBIDDEN, "Access denied for this profile.")))
			.exceptionHandling(handling -> handling
				.authenticationEntryPoint((request, response, exception) ->
					writeProblem(objectMapper, response, HttpStatus.UNAUTHORIZED, "Authentication is required."))
				.accessDeniedHandler((request, response, exception) ->
					writeProblem(objectMapper, response, HttpStatus.FORBIDDEN, "Access denied for this profile.")));
		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public JwtEncoder jwtEncoder(JwtProperties properties) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(properties)));
	}

	@Bean
	public JwtDecoder jwtDecoder(JwtProperties properties) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey(properties))
			.macAlgorithm(MacAlgorithm.HS256)
			.build();
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), knownProfile()));
		return decoder;
	}

	private OAuth2TokenValidator<Jwt> knownProfile() {
		OAuth2Error error = new OAuth2Error("invalid_token", "The token has no valid profile.", null);
		return jwt -> {
			String role = jwt.getClaimAsString(TokenService.ROLE_CLAIM);
			boolean known = Arrays.stream(Role.values()).anyMatch(candidate -> candidate.name().equals(role));
			return known ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(error);
		};
	}

	private JwtAuthenticationConverter authenticationConverter() {
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(jwt -> {
			String role = jwt.getClaimAsString(TokenService.ROLE_CLAIM);
			List<GrantedAuthority> authorities = role == null
				? List.of()
				: List.of(new SimpleGrantedAuthority("ROLE_" + role));
			return authorities;
		});
		return converter;
	}

	private SecretKey secretKey(JwtProperties properties) {
		return new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), ALGORITHM);
	}

	private void writeProblem(ObjectMapper objectMapper, HttpServletResponse response, HttpStatus status, String detail)
			throws IOException {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		objectMapper.writeValue(response.getOutputStream(), problem);
	}

}
