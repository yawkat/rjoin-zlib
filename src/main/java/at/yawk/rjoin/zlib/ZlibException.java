package at.yawk.rjoin.zlib;

import java.io.IOException;

/**
 * @author yawkat
 */
public class ZlibException extends IOException {
    @SuppressWarnings("unused") // used by JNI
    public ZlibException(String message) {
        super(message);
    }

    public ZlibException(Throwable cause) {
        super(cause);
    }
}
