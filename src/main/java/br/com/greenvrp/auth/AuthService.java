package br.com.greenvrp.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

	private final AppUserRepository users;
	private final PasswordEncoder passwordEncoder;
	private final TokenService tokenService;
	private final String unknownUserHash;

	public AuthService(AppUserRepository users, PasswordEncoder passwordEncoder, TokenService tokenService) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.tokenService = tokenService;
		this.unknownUserHash = passwordEncoder.encode("unknown-user");
	}

	public LoginResponse login(LoginRequest request) {
		AppUser user = users.findByEmail(AppUser.normalize(request.email())).orElse(null);
		String hash = user != null ? user.getSenhaHash() : unknownUserHash;
		boolean matches = passwordEncoder.matches(request.senha(), hash);
		if (user == null || !matches) {
			throw new InvalidCredentialsException();
		}
		if (!user.isAtivo()) {
			throw new InactiveUserException();
		}
		return new LoginResponse(tokenService.issue(user), UserSummary.from(user));
	}

}
