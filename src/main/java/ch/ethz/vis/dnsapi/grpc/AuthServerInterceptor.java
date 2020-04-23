package ch.ethz.vis.dnsapi.grpc;

import io.grpc.*;
import org.jose4j.jwk.HttpsJwks;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.keys.resolvers.HttpsJwksVerificationKeyResolver;

import java.util.List;

/**
 * Use a {@link ServerInterceptor} to capture metadata, retrieve a token, validate it using jwt / and keycloak. Also
 * information about the user is read from the jwt and stored in the context.
 * <p>
 * NOTE: ServerInterceptors in GRPC need to be THREAD SAFE. (e.g. don't use JSONParser as an instance method, since it is
 * not thread safe).
 */
public class AuthServerInterceptor implements ServerInterceptor {

    private final JwtConsumer jwtConsumer;

    private final String clientId;

    private static final String ROLE_USAGE = "usage";

    public AuthServerInterceptor(String clientId, String issuer, String jwksUrl) {
        this.clientId = clientId;

        HttpsJwks httpsJwks = new HttpsJwks(jwksUrl);

        HttpsJwksVerificationKeyResolver httpsJwksKeyResolver = new HttpsJwksVerificationKeyResolver(httpsJwks);

        this.jwtConsumer = new JwtConsumerBuilder()
                .setRequireExpirationTime()
                .setAllowedClockSkewInSeconds(30)
                .setRequireSubject()
                .setExpectedIssuer(issuer)
                .setExpectedAudience(this.clientId)
                .setVerificationKeyResolver(httpsJwksKeyResolver)
                .build();

    }

    /**
     * The interceptCall method is invoked before a request is forwarded to the PeopleAPIImpl.
     */
    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> serverCall, Metadata metadata,
                                                                 ServerCallHandler<ReqT, RespT> serverCallHandler) {

        Metadata.Key<String> AUTHORIZATION_KEY = Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
        String tokenString = metadata.get(AUTHORIZATION_KEY);

        if (tokenString == null || tokenString.isEmpty()) {
            serverCall.close(Status.UNAUTHENTICATED, metadata);
            return new ServerCall.Listener<>(){};
        }

        if (!tokenString.matches("Bearer .*")) {
            serverCall.close(Status.UNAUTHENTICATED, metadata);
            return new ServerCall.Listener<>(){};
        }

        String jwtToken = tokenString.split(" ", 2)[1];

        if (jwtToken == null || jwtToken.isEmpty()) {
            serverCall.close(Status.UNAUTHENTICATED, metadata);
            return new ServerCall.Listener<>(){};
        }

        try {
            JwtClaims jwtClaims = jwtConsumer.processToClaims(jwtToken);
            if (isAuthorized(jwtClaims)) {
                return Contexts.interceptCall(Context.current(), serverCall, metadata, serverCallHandler);
            } else {
                serverCall.close(Status.PERMISSION_DENIED, metadata);
                return new ServerCall.Listener<>(){};
            }
        } catch (InvalidJwtException e) {
            serverCall.close(Status.UNAUTHENTICATED, metadata);
            return new ServerCall.Listener<>() {};
        }
    }

    private boolean isAuthorized(JwtClaims jwtClaims) {
        List<Object> roleObjects = jwtClaims.flattenClaims().get(String.format("resource_access.%s.roles", clientId));

        if (roleObjects != null) {
            return roleObjects
                    .stream()
                    .map(Object::toString)
                    .anyMatch(s -> s.equals(ROLE_USAGE));
        }

        return false;
    }
}