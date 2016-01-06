
package main.com.tencent.mm.androlib;


/**
 * @author shwenzhang
 */
public class AndrolibException extends Exception {
    public AndrolibException() {
    }

    public AndrolibException(String message) {
        super(message);
    }

    public AndrolibException(String message, Throwable cause) {
        super(message, cause);
    }

    public AndrolibException(Throwable cause) {
        super(cause);
    }
}
