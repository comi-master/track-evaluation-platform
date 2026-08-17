package com.example.trackanalysis.auth.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.trackanalysis.user.application.UserSecurityChangedEvent;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

class WebSessionInvalidationListenerTest {

  @Test
  void deletesEverySessionIndexedByUsername() {
    @SuppressWarnings("unchecked")
    FindByIndexNameSessionRepository<Session> sessions =
        mock(FindByIndexNameSessionRepository.class);
    Session first = mock(Session.class);
    Session second = mock(Session.class);
    Session third = mock(Session.class);
    when(sessions.findByPrincipalName("researcher"))
        .thenReturn(Map.of("session-1", first, "session-2", second, "session-3", third));

    @SuppressWarnings("unchecked")
    ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> provider =
        mock(ObjectProvider.class);
    doReturn(sessions).when(provider).getIfAvailable();

    new WebSessionInvalidationListener(provider)
        .invalidate(new UserSecurityChangedEvent("researcher"));

    verify(sessions).deleteById("session-1");
    verify(sessions).deleteById("session-2");
    verify(sessions).deleteById("session-3");
    verify(sessions, never()).findByPrincipalName("other-user");
  }

  @Test
  void committedDatabaseChangeIsNotReportedAsFailedWhenRedisCleanupFails() {
    @SuppressWarnings("unchecked")
    FindByIndexNameSessionRepository<Session> sessions =
        mock(FindByIndexNameSessionRepository.class);
    when(sessions.findByPrincipalName("researcher"))
        .thenThrow(new IllegalStateException("redis unavailable"));

    @SuppressWarnings("unchecked")
    ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> provider =
        mock(ObjectProvider.class);
    doReturn(sessions).when(provider).getIfAvailable();

    assertThatCode(
            () ->
                new WebSessionInvalidationListener(provider)
                    .invalidate(new UserSecurityChangedEvent("researcher")))
        .doesNotThrowAnyException();
  }
}
