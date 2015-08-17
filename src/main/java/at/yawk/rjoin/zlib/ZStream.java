package at.yawk.rjoin.zlib;

import java.io.Closeable;
import java.nio.ByteBuffer;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * @author yawkat
 */
@NotThreadSafe
public interface ZStream extends Closeable {
    boolean needsInput();

    void setInput(ByteBuffer input);

    boolean finished();

    /**
     * Reset this stream.
     */
    void reset();

    @Override
    void close();
}
