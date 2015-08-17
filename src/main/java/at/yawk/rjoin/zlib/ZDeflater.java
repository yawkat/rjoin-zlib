package at.yawk.rjoin.zlib;

import java.nio.ByteBuffer;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * @author yawkat
 */
@NotThreadSafe
public interface ZDeflater extends ZStream {
    /**
     * Deflate more data into the output buffer.
     */
    void deflate(ByteBuffer out) throws ZlibException;

    /**
     * Mark the current input as the last input.
     */
    void finish();
}
