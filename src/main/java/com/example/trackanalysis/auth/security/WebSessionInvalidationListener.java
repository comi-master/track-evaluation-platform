package com.example.trackanalysis.auth.security;

import com.example.trackanalysis.user.application.UserSecurityChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class WebSessionInvalidationListener {
  private static final Logger log = LoggerFactory.getLogger(WebSessionInvalidationListener.class);
  private final ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> sessions;

  public WebSessionInvalidationListener(
      ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> sessions) {
    this.sessions = sessions;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void invalidate(UserSecurityChangedEvent event) {
    try {
      var repository = sessions.getIfAvailable();
      if (repository == null) {
        return;
      }
      var sessionIds = repository.findByPrincipalName(event.username()).keySet();
      sessionIds.forEach(repository::deleteById);
      if (!sessionIds.isEmpty()) {
        log.info("Invalidated {} web session(s) after a user security change", sessionIds.size());
      }
    } catch (RuntimeException exception) {
      log.error(
          "Web session invalidation failed after a committed user security change", exception);
    }
  }
}
