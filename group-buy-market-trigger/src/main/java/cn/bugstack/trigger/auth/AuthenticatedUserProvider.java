package cn.bugstack.trigger.auth;

import java.util.Optional;

/**
 * Authentication boundary for HTTP-facing business operations. Replace this implementation with a
 * gateway/JWT-backed provider when the application receives a real authentication system.
 */
public interface AuthenticatedUserProvider {

    Optional<String> getAuthenticatedUserId();

}
