package edu.polyu.comp5511.hotelselection.service;

public class DistanceProviderException extends RuntimeException {
    public DistanceProviderException(String message) {
        super(message);
    }

    public DistanceProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
