package io.github.ffeltrinelli.pool.android

import android.content.res.AssetManager
import android.media.*
import io.github.ffeltrinelli.pool.replay.*
import java.io.*
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Every codec, EGL allocation and temporary media file belongs to this worker invocation. */
internal class ReplayVideoEncoder(private val assets: AssetManager, private val cancelled: AtomicBoolean) {
    private fun checkActive() { if (cancelled.get()) throw InterruptedException("Export cancelled") }

    fun encode(clip: ReplayClip, frameCount: Int, queue: ArrayBlockingQueue<ByteArray>, destination: File) {
        val video = File(destination.parentFile, "${destination.name}.video")
        val audio = File(destination.parentFile, "${destination.name}.audio")
        try {
            encodeVideo(frameCount, queue, video)
            encodeAudio(clip, frameCount, audio)
            mux(video, audio, destination)
            appendRepositoryLink(destination)
        } finally { video.delete(); audio.delete() }
    }

    private fun encodeVideo(frameCount: Int, queue: ArrayBlockingQueue<ByteArray>, file: File) {
        val codec = MediaCodec.createEncoderByType("video/avc")
        try {
            val format = MediaFormat.createVideoFormat("video/avc", 720, 1280).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = codec.createInputSurface()
            try {
                codec.start()
                TrackWriter(codec, file, ::checkActive).use { writer ->
                    EncoderSurface(surface).use { egl ->
                        repeat(frameCount) { index ->
                            var frame: ByteArray? = null
                            while (frame == null) {
                                checkActive()
                                frame = queue.poll(100, TimeUnit.MILLISECONDS)
                                writer.drain(false)
                            }
                            writer.drain(false)
                            egl.draw(frame, index * 1_000_000_000L / 30)
                            writer.drain(false)
                        }
                        codec.signalEndOfInputStream()
                        writer.drain(true)
                    }
                }
            } finally { surface.release() }
        } finally { codec.release() }
    }

    private fun encodeAudio(clip: ReplayClip, frameCount: Int, file: File) {
        checkActive()
        val pcm = ReplayPcm(clip) { path -> assets.open(path).use { it.readBytes() } }
        val codec = MediaCodec.createEncoderByType("audio/mp4a-latm")
        try {
            val format = MediaFormat.createAudioFormat("audio/mp4a-latm", ReplayPcm.SAMPLE_RATE, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            TrackWriter(codec, file, ::checkActive).use { writer ->
                val total = (frameCount.toLong() * ReplayPcm.SAMPLE_RATE / 30).toInt()
                var sample = 0
                while (sample < total) {
                    checkActive()
                    val index = codec.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val buffer = checkNotNull(codec.getInputBuffer(index)); buffer.clear()
                        val count = minOf(buffer.remaining() / 2, total - sample)
                        pcm.write(buffer, sample, count)
                        codec.queueInputBuffer(index, 0, count * 2, sample * 1_000_000L / ReplayPcm.SAMPLE_RATE, 0)
                        sample += count
                    }
                    writer.drain(false)
                }
                var ended = false
                while (!ended) {
                    checkActive()
                    val index = codec.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        codec.queueInputBuffer(index, 0, 0, sample * 1_000_000L / ReplayPcm.SAMPLE_RATE, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        ended = true
                    }
                    writer.drain(false)
                }
                writer.drain(true)
            }
        } finally { codec.release() }
    }

    private fun mux(video: File, audio: File, destination: File) {
        val muxer = MediaMuxer(destination.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val extractors = listOf(MediaExtractor(), MediaExtractor())
        var started = false
        try {
            val tracks = listOf(video, audio).mapIndexed { index, file ->
                val extractor = extractors[index]
                extractor.setDataSource(file.path); extractor.selectTrack(0)
                muxer.addTrack(extractor.getTrackFormat(0))
            }
            muxer.start(); started = true
            val buffer = ByteBuffer.allocateDirect(4 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            extractors.forEachIndexed { index, extractor ->
                while (true) {
                    checkActive(); buffer.clear()
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    val flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                    info.set(0, size, extractor.sampleTime, flags)
                    muxer.writeSampleData(tracks[index], buffer, info)
                    extractor.advance()
                }
            }
        } finally {
            extractors.forEach { it.release() }
            try { if (started) muxer.stop() } finally { muxer.release() }
        }
    }

    /** Standard iTunes comment metadata; appending udta leaves existing chunk offsets intact. */
    private fun appendRepositoryLink(file: File) {
        fun box(type: ByteArray, data: ByteArray): ByteArray = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { it.writeInt(8 + data.size); it.write(type); it.write(data) }
        }.toByteArray()
        fun box(type: String, data: ByteArray) = box(type.toByteArray(Charsets.ISO_8859_1), data)
        val value = ByteArrayOutputStream().also { bytes -> DataOutputStream(bytes).use {
            it.writeInt(1); it.writeInt(0); it.write(AndroidReplaySharing.REPOSITORY_URL.toByteArray(Charsets.UTF_8))
        } }.toByteArray()
        val comment = box(byteArrayOf(0xa9.toByte(), 0x63, 0x6d, 0x74), box("data", value))
        val handler = ByteBuffer.allocate(25).putInt(0).putInt(0).put("mdir".toByteArray()).put(ByteArray(13)).array()
        val metadata = box("udta", box("meta", ByteArray(4) + box("hdlr", handler) + box("ilst", comment)))
        // Relocate moov without moving media data; existing chunk offsets stay valid.
        RandomAccessFile(file, "rw").use { out ->
            var position = 0L
            while (position + 8 <= out.length()) {
                out.seek(position)
                val shortSize = out.readInt().toLong() and 0xffffffffL
                val type = out.readInt()
                val extended = shortSize == 1L
                val size = if (extended) out.readLong() else shortSize
                require(size >= (if (extended) 16 else 8) && position + size <= out.length())
                if (type == 0x6d6f6f76) {
                    require(!extended && size < 16 * 1024 * 1024) { "Unexpected MP4 movie metadata" }
                    val original = ByteArray(size.toInt() - 8)
                    out.readFully(original)
                    out.seek(out.length())
                    out.writeInt((size + metadata.size).toInt()); out.writeInt(type)
                    out.write(original); out.write(metadata)
                    out.seek(position + 4); out.writeInt(0x66726565)
                    return
                }
                position += size
            }
            error("MP4 metadata could not be written")
        }
    }
}

private class TrackWriter(private val codec: MediaCodec, file: File, private val checkActive: () -> Unit) : AutoCloseable {
    private val muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private val info = MediaCodec.BufferInfo()
    private var track = -1
    private var ended = false
    fun drain(untilEnd: Boolean) {
        var empty = 0
        while (!ended) {
            checkActive()
            val index = codec.dequeueOutputBuffer(info, if (untilEnd) 10_000 else 0)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!untilEnd) return
                    check(++empty < 1000) { "Video encoder timed out" }
                }
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    check(track == -1); track = muxer.addTrack(codec.outputFormat); muxer.start()
                }
                index >= 0 -> {
                    empty = 0
                    try {
                        if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            check(track >= 0)
                            val buffer = checkNotNull(codec.getOutputBuffer(index))
                            buffer.position(info.offset); buffer.limit(info.offset + info.size)
                            muxer.writeSampleData(track, buffer, info)
                        }
                        ended = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    } finally { codec.releaseOutputBuffer(index, false) }
                }
            }
        }
    }
    override fun close() {
        try { if (track >= 0) muxer.stop() } finally { muxer.release() }
    }
}
