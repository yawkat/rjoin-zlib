package at.yawk.rjoin.zlib;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

/**
 * @author yawkat
 */
@Slf4j
class NZlib implements ZlibProvider {
    static boolean AVAILABLE = false;

    static {
        if (!Boolean.getBoolean("at.yawk.rjoin.nzlib.disable")) {
            try {
                Path tempFile = Files.createTempFile("nzlib", ".so");
                InputStream in = NZlib.class.getResourceAsStream("/nzlib.so");
                if (in != null) {
                    try {
                        Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
                    } finally {
                        in.close();
                    }
                    try {
                        System.load(tempFile.toAbsolutePath().toString());
                        AVAILABLE = true;
                    } finally {
                        Files.deleteIfExists(tempFile);
                    }
                } else {
                    log.debug("Failed to load native because /nzlib.so does not exist");
                }
            } catch (IOException e) {
                log.debug("Failed to load native", e);
            }
        }
    }

    private static void checkDirect(ByteBuffer buffer, String name) {
        if (!buffer.isDirect()) {
            throw new IllegalArgumentException("'" + name + "' must be direct");
        }
    }

    @Override
    public ZInflater createInflater() {
        class ZInflaterImpl extends AbstractStream implements ZInflater {
            {
                log.trace("Inflater.open");
                stream = open(true);
            }

            @Override
            public void reset() {
                super.reset();
                NZlib.reset(true, stream);
            }

            @Override
            public void close() {
                NZlib.close(true, stream);
            }

            @Override
            public void inflate(ByteBuffer out) throws ZlibException {
                checkDirect(out, "out");
                work(true, out, false);
            }
        }
        return new ZInflaterImpl();
    }

    @Override
    public ZDeflater createDeflater() {
        class ZDeflaterImpl extends AbstractStream implements ZDeflater {
            private boolean finish = false;

            {
                log.trace("Deflater.open");
                stream = open(false);
            }

            @Override
            public boolean needsInput() {
                return !finish && super.needsInput();
            }

            @Override
            public void deflate(ByteBuffer out) throws ZlibException {
                checkDirect(out, "out");
                work(false, out, finish);
            }

            @Override
            public void finish() {
                finish = true;
            }

            @Override
            public void reset() {
                super.reset();
                NZlib.reset(false, stream);
                finish = false;
            }

            @Override
            public void close() {
                NZlib.close(false, stream);
            }
        }
        return new ZDeflaterImpl();
    }

    private static abstract class AbstractStream implements ZStream {
        protected long stream;
        @Nullable private ByteBuffer input = null;
        private boolean finished = false;

        @Override
        public boolean needsInput() {
            return !finished && (input == null || !input.hasRemaining());
        }

        @Override
        public void setInput(@Nonnull ByteBuffer input) {
            assert needsInput();
            checkDirect(input, "input");
            this.input = input;
        }

        @Override
        public boolean finished() {
            return finished;
        }

        @Override
        public void reset() {
            this.input = null;
            this.finished = false;
        }

        @Override
        protected void finalize() throws Throwable {
            super.finalize();
            close();
        }

        protected void work(boolean inflate, ByteBuffer out, boolean finish) throws ZlibException {
            finished = NZlib.work(inflate, stream, input, out, finish);
            if (input != null && !input.hasRemaining()) { input = null; } // don't need that anymore
        }
    }

    /**
     * Open a new zlib stream.
     *
     * @return The address of the stream.
     */
    private static native long open(boolean inflate);

    /**
     * Push more data to the stream.
     *
     * @param stream The stream to operate on
     * @param in     The input data
     * @param out    The output data
     * @param finish Whether this is the last chunk of data
     * @return <code>true</code> if the stream is done and internal buffers are clear, <code>false</code> otherwise
     */
    private static native boolean work(boolean inflate, long stream, ByteBuffer in, ByteBuffer out, boolean finish)
            throws ZlibException;

    /**
     * Attempt to reset the given stream.
     *
     * @param stream The stream to reset
     * @return <code>true</code> if the stream was closed successfully, <code>false</code> if closing failed and we need
     * to discard it.
     */
    private static native void reset(boolean inflate, long stream);

    private static native void close(boolean inflate, long stream);
}
