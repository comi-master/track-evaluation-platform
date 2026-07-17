package com.example.trackanalysis.auth.security;

public record AuthenticatedUser(long id, String username, int authVersion) {}
