package cn.bugstack.trigger.auth;

import java.util.Optional;

/**
 * Authentication boundary for HTTP-facing business operations.
 */
public interface AuthenticatedUserProvider {

    Optional<String> getAuthenticatedUserId();

}
