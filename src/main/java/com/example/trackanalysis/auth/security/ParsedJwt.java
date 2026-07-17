package com.example.trackanalysis.auth.security;

public record ParsedJwt(long userId, String username, int authVersion, String jwtId) {}
