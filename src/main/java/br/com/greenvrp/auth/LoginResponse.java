package br.com.greenvrp.auth;

public record LoginResponse(String token, UserSummary usuario) {
}
