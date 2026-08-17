package com.example.trackanalysis.web.application;

import com.example.trackanalysis.auth.application.WebIdentityService.WebIdentity;

public record BusinessScope(long actorId, String username, boolean administrator) {
  public static BusinessScope from(WebIdentity identity) {
    return new BusinessScope(
        identity.id(), identity.username(), identity.roles().contains("ADMIN"));
  }
}
