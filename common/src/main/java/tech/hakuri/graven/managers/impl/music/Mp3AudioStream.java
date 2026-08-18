package tech.hakuri.graven.managers.impl.music;

import net.minecraft.client.sounds.AudioStream;
import org.jcodec.codecs.mpa.Mp3Decoder;
import org.jcodec.common.AudioCodecMeta;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.model.AudioBuffer;
import org.jcodec.common.model.Packet;
import org.jcodec.containers.mp3.MPEGAudioDemuxer;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;

/** 将 JCodec 的 MP3 帧解码成 OpenAL 所需的 16-bit little-endian PCM 流。 */
public final class Mp3AudioStream implements AudioStream {
    private static final int DECODE_BUFFER_SIZE = 32768;

    private final MPEGAudioDemuxer demuxer;
    private final Mp3Decoder decoder = new Mp3Decoder();
    private final AudioFormat format;
    private Packet nextPacket;
    private ByteBuffer pending;
    private boolean closed;

    public Mp3AudioStream(Path path) throws IOException {
        this(path, 0L);
    }

    public Mp3AudioStream(Path path, long startMs) throws IOException {
        this.demuxer = new MPEGAudioDemuxer(NIOUtils.readableChannel(path.toFile()));
        this.nextPacket = demuxer.nextFrame();
        if (nextPacket == null) {
            demuxer.close();
            throw new IOException("Empty MP3 stream: " + path);
        }
        AudioCodecMeta meta = decoder.getCodecMeta(nextPacket.getData().duplicate());
        org.jcodec.common.AudioFormat decodedFormat = meta.getFormat();
        this.format = new AudioFormat(decodedFormat.getSampleRate(), decodedFormat.getSampleSizeInBits(),
                decodedFormat.getChannels(), decodedFormat.isSigned(), decodedFormat.isBigEndian());
        if (startMs > 0L) {
            double target = startMs / 1000.0;
            while (nextPacket != null && nextPacket.getPtsD() + nextPacket.getDurationD() < target) {
                nextPacket = demuxer.nextFrame();
            }
        }
    }

    @Override
    public AudioFormat getFormat() {
        return format;
    }

    @Override
    public synchronized ByteBuffer read(int expectedSize) throws IOException {
        if (closed) return null;
        ByteBuffer output = BufferUtils.createByteBuffer(Math.max(1, expectedSize));
        while (output.hasRemaining()) {
            if (pending == null || !pending.hasRemaining()) {
                pending = decodeNextFrame();
                if (pending == null) break;
            }
            int count = Math.min(output.remaining(), pending.remaining());
            int oldLimit = pending.limit();
            pending.limit(pending.position() + count);
            output.put(pending);
            pending.limit(oldLimit);
        }
        if (output.position() == 0) return null;
        output.flip();
        return output;
    }

    private ByteBuffer decodeNextFrame() throws IOException {
        Packet packet = nextPacket;
        if (packet == null) return null;
        nextPacket = demuxer.nextFrame();
        ByteBuffer pcm = BufferUtils.createByteBuffer(DECODE_BUFFER_SIZE);
        AudioBuffer decoded = decoder.decodeFrame(packet.getData().duplicate(), pcm);
        return decoded.getData();
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) return;
        closed = true;
        pending = null;
        demuxer.close();
    }
}
