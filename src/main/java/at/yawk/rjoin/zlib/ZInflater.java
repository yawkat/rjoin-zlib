package at.yawk.rjoin.zlib;

import java.nio.ByteBuffer;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * @author yawkat
 */
@NotThreadSafe
public interface ZInflater extends ZStream {
    void inflate(ByteBuffer out) throws ZlibException;
}
