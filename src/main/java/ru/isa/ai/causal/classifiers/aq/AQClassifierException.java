package ru.isa.ai.causal.classifiers.aq;

/**
 * Created by GraffT on 01.05.2014.
 */
public class AQClassifierException extends Exception {
    public AQClassifierException() {
        super();
    }

    public AQClassifierException(String message) {
        super(message);
    }

    public AQClassifierException(String message, Throwable cause) {
        super(message, cause);
    }

    public AQClassifierException(Throwable cause) {
        super(cause);
    }
}
