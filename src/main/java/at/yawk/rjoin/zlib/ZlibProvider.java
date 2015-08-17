package at.yawk.rjoin.zlib;

/**
 * @author yawkat
 */
public interface ZlibProvider {
    ZInflater createInflater();

    ZDeflater createDeflater();
}
