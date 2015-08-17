package at.yawk.rjoin.zlib;

import java.nio.ByteBuffer;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author yawkat
 */
@Slf4j
public class ZlibTest {
    private static final int BLOCK_SIZE = 1024;
    private static final int INPUT_SIZE = BLOCK_SIZE * 2;
    private static final int OUTPUT_SIZE = INPUT_SIZE + 1024;

    @Test
    public void testNative() throws ZlibException {
        ZlibProvider provider = new NZlib();
        testProvider(provider, 0);
        testProvider(provider, 1);
        testProvider(provider, 2);
        testProvider(provider, 3);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testNativeDirectOnlyDeflaterInput() {
        new NZlib().createDeflater().setInput(ByteBuffer.allocate(0));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testNativeDirectOnlyDeflaterProcess() throws ZlibException {
        new NZlib().createDeflater().setInput(ByteBuffer.allocateDirect(0));
        new NZlib().createDeflater().deflate(ByteBuffer.allocate(0));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testNativeDirectOnlyInflaterInput() {
        new NZlib().createInflater().setInput(ByteBuffer.allocate(0));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testNativeDirectOnlyInflaterProcess() throws ZlibException {
        new NZlib().createInflater().setInput(ByteBuffer.allocateDirect(0));
        new NZlib().createInflater().inflate(ByteBuffer.allocate(0));
    }

    @Test
    public void testFallback() throws ZlibException {
        ZlibProvider provider = new FallbackZlibProvider();
        testProvider(provider, 0);
        testProvider(provider, 1);
        testProvider(provider, 2);
        testProvider(provider, 3);
    }

    private void testProvider(ZlibProvider provider, int seed) throws ZlibException {
        log.info("Testing zlib provider {} with seed {}", provider, seed);

        ByteBuffer data = ByteBuffer.allocate(INPUT_SIZE);
        if (seed != 0) {
            new Random(seed).nextBytes(data.array());
        }

        ZInflater inflater = provider.createInflater();
        ZDeflater deflater = provider.createDeflater();

        for (int i = 0; i < 3; i++) {
            // deflate
            ByteBuffer deflated = ByteBuffer.allocate(OUTPUT_SIZE);
            deflate(deflater, data, deflated);
            data.flip();
            deflated.flip();

            log.debug("Deflated {} to {}", data, deflated);

            // inflate
            ByteBuffer result = ByteBuffer.allocate(OUTPUT_SIZE);
            inflate(inflater, deflated, result);
            deflated.flip();
            result.flip();

            log.debug("Inflated {} to {}", deflated, result);

            Assert.assertEquals(result, data);

            inflater.reset();
            deflater.reset();
        }

        inflater.close();
        deflater.close();
    }

    private void deflate(ZDeflater deflater, ByteBuffer source, ByteBuffer target) throws ZlibException {
        operate(deflater, deflater::deflate, deflater::finish, source, target);
    }

    private void inflate(ZInflater inflater, ByteBuffer source, ByteBuffer target) throws ZlibException {
        operate(inflater, inflater::inflate, () -> {}, source, target);
    }

    /**
     * Do an operation on the source data into the target data in blocks.
     */
    private void operate(ZStream stream,
                         BufferOperation work,
                         Runnable finish,
                         ByteBuffer source,
                         ByteBuffer target)
            throws ZlibException {
        ByteBuffer inputBuf = ByteBuffer.allocateDirect(BLOCK_SIZE);
        inputBuf.limit(0);
        ByteBuffer outputBuf = ByteBuffer.allocateDirect(BLOCK_SIZE);

        while (!stream.finished()) {
            log.trace("Operation pass");

            if (stream.needsInput()) {
                Assert.assertTrue(source.hasRemaining());

                // copy some data from data to inputBuf
                inputBuf.clear();
                log.trace("Copying input source:{} -> in:{}", source, inputBuf);
                int oldLimit = source.limit();
                source.limit(Math.min(oldLimit, source.position() + inputBuf.remaining()));
                inputBuf.put(source);
                source.limit(oldLimit);
                inputBuf.flip();

                stream.setInput(inputBuf);
            }
            if (!source.hasRemaining()) {
                finish.run();
            }

            // deflate inputBuf -> outputBuf
            work.operate(outputBuf);
            log.trace("Operation done in:{} -> out:{}", inputBuf, outputBuf);

            // move data from outputBuf
            outputBuf.flip();
            if (outputBuf.hasRemaining()) {
                log.trace("Copying output out:{} -> target:{}", outputBuf, target);
                target.put(outputBuf); // we assume all data was moved (postion = limit = capacity)
            }

            outputBuf.clear();
        }
    }

    private interface BufferOperation {
        void operate(ByteBuffer in) throws ZlibException;
    }
}
