package br.com.greenvrp.auth;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserAdminController {

	private final UserAdminService service;

	public UserAdminController(UserAdminService service) {
		this.service = service;
	}

	@GetMapping
	public List<UserResponse> list() {
		return service.list().stream()
			.map(UserResponse::from)
			.toList();
	}

	@PatchMapping("/{id}")
	public UserResponse update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
		return UserResponse.from(service.update(id, request));
	}

}
