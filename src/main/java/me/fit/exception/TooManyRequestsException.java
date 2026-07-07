package me.fit.exception;

import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.core.Response;

// HTTP 429 sa Retry-After zaglavljem; koristi se kod ogranicenja broja prijava
public class TooManyRequestsException extends ClientErrorException {

    private static final int TOO_MANY_REQUESTS = 429;

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        super(message, Response.status(TOO_MANY_REQUESTS)
                .header("Retry-After", retryAfterSeconds)
                .build());
    }
}
