package io.github.khajamohammeddev.configadmin.client;

/** A call to a service failed; the message is written to be shown in the UI as-is. */
public class ServiceCallException extends RuntimeException {

    public ServiceCallException(String message) {
        super(message);
    }

    public ServiceCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
