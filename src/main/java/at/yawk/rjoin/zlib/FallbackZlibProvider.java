package at.yawk.rjoin.zlib;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import javax.xml.bind.DatatypeConverter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author yawkat
 */
@Slf4j
class FallbackZlibProvider implements ZlibProvider {
    private static final int BUFFER_SIZE = 1024;

    @Override
    public ZInflater createInflater() {
        class ZInflaterImpl extends AbstractStream implements ZInflater {
            private final Inflater inflater = new Inflater();

            @Override
            protected void setInput(byte[] in, int offset, int length) {
                inflater.setInput(in);
            }

            @Override
            protected int process(byte[] out, int offset, int length) throws DataFormatException {
                return inflater.inflate(out, offset, length);
            }

            @Override
            protected boolean needsInput0() {
                return inflater.needsInput();
            }

            @Override
            protected boolean finished0() {
                return inflater.finished();
            }

            @Override
            public void reset() {
                super.reset();
                inflater.reset();
            }

            @Override
            public void close() {
                inflater.end();
            }

            @Override
            public void inflate(ByteBuffer out) throws ZlibException {
                process(out);
            }
        }
        return new ZInflaterImpl();
    }

    @Override
    public ZDeflater createDeflater() {
        class ZDeflaterImpl extends AbstractStream implements ZDeflater {
            private final Deflater deflater = new Deflater();

            private boolean finishRequested = false;
            private boolean finish = false;

            @Override
            protected void setInput(byte[] in, int offset, int length) {
                deflater.setInput(in, offset, length);
                checkFinish();
            }

            private void checkFinish() {
                if (finishRequested && !hasLocalBuffer()) {
                    log.trace("Deflater.finish - impl");
                    deflater.finish();
                    finish = true;
                }
            }

            @Override
            protected int process(byte[] out, int offset, int length) throws DataFormatException {
                return deflater.deflate(out, offset, length);
            }

            @Override
            protected boolean needsInput0() {
                return !finish && deflater.needsInput();
            }

            @Override
            protected boolean finished0() {
                return deflater.finished();
            }

            @Override
            public void reset() {
                super.reset();
                deflater.reset();
                finishRequested = finish = false;
            }

            @Override
            public void close() {
                deflater.end();
            }

            @Override
            public void deflate(ByteBuffer out) throws ZlibException {
                process(out);
            }

            @Override
            public void finish() {
                finishRequested = true;
                checkFinish();
            }
        }
        return new ZDeflaterImpl();
    }

    private static abstract class AbstractStream implements ZStream {
        private ByteBuffer input = null;
        private final byte[] inBuf = new byte[BUFFER_SIZE];

        private final byte[] outBuf = new byte[BUFFER_SIZE];
        private int outStart = 0;
        private int outEnd = 0;

        protected abstract void setInput(byte[] in, int offset, int length);

        protected abstract int process(byte[] out, int offset, int length) throws DataFormatException;

        /**
         * needsInput that does not respect the local buffer but only the wrapped object (if this is true we may still
         * have local buffered data)
         */
        protected abstract boolean needsInput0();

        protected abstract boolean finished0();

        /**
         * needsInput that respects the local buffer
         */
        @Override
        public boolean needsInput() {
            return !hasLocalBuffer() && needsInput0();
        }

        protected boolean hasLocalBuffer() {
            return input != null && input.hasRemaining();
        }

        @Override
        public boolean finished() {
            return outStart == outEnd && finished0();
        }

        @Override
        public void setInput(ByteBuffer input) {
            assert needsInput();
            this.input = input;
        }

        public void process(ByteBuffer out) throws ZlibException {
            assert !needsInput();
            assert !finished();

            try {
                // reset the buffer if it's empty
                if (outStart == outEnd) {
                    outStart = outEnd = 0;
                }

                // if we have spare space in the buffer, process some data into it
                if (outEnd != outBuf.length) {
                    if (needsInput0()) {
                        // copy data into the input buffer
                        int n = Math.min(inBuf.length, input.remaining());
                        input.get(inBuf, 0, n);
                        if (!input.hasRemaining()) { input = null; } // don't need that anymore
                        setInput(inBuf, 0, n);
                        if (log.isTraceEnabled()) {
                            log.trace("< " + DatatypeConverter.printHexBinary(Arrays.copyOfRange(inBuf, 0, n)));
                        }
                    }

                    outEnd += process(outBuf, outEnd, outBuf.length - outEnd);
                }
                // copy some data into the output
                int n = Math.min(out.remaining(), outEnd - outStart);
                if (n != 0) {
                    out.put(outBuf, outStart, n);
                    if (log.isTraceEnabled()) {
                        log.trace("> " + DatatypeConverter.printHexBinary(Arrays.copyOfRange(
                                outBuf, outStart, outStart + n)));
                    }
                    outStart += n;
                }
            } catch (DataFormatException e) {
                throw new ZlibException(e);
            }
        }

        @Override
        public void reset() {
            input = null;
            outStart = outEnd = 0;
        }
    }
}
