package at.yawk.rjoin.zlib;

import lombok.extern.slf4j.Slf4j;

/**
 * @author yawkat
 */
@Slf4j
public class Zlib {
    private static final ZlibProvider PROVIDER;

    static {
        if (supportsNative()) {
            log.info("Using native zlib provider");
            PROVIDER = new NZlib();
        } else {
            log.warn("Could not load native zlib provider, falling back on j.u.zip provider");
            PROVIDER = new FallbackZlibProvider();
        }
    }

    private Zlib() {}

    public static ZlibProvider getProvider() {
        return PROVIDER;
    }

    public static boolean supportsNative() {
        return NZlib.AVAILABLE;
    }
}
