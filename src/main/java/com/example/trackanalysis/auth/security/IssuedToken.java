package com.example.trackanalysis.auth.security;

public record IssuedToken(String value, long expiresInSeconds) {}
