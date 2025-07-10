package com.mvxgreen.ytdloader;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public final class ConcatRunner {
    static final String TAG = ConcatRunner.class.getCanonicalName();

    // ... (your existing TAG_KEYS and other variables)

    public static void mergeAV(String filepath, String vFilepath, String aFilepath) {
        String msg = "MERGING:\nfilepath=" + filepath
                + "\nvFilepath=" + vFilepath
                + "\naFilepath=" + aFilepath;
        Log.i(TAG, msg);

        try {
            mergeFiles(vFilepath, aFilepath, filepath);
        } catch (IOException e) {
            Log.e(TAG, "Error merging files: " + e.getMessage());
        }
        /*
        finally {
            deleteTempFiles(vFilepath, aFilepath);
        }
        */
    }

    /**
     * Merges a video file and an audio file into a single output MP4 file.
     *
     * @param videoFilePath Path to the input video file (e.g., .mp4).
     * @param audioFilePath Path to the input audio file (e.g., .m4a).
     * @param outputFilePath Path for the merged output MP4 file.
     * @throws IOException If an error occurs during the merging process.
     */
    public static void mergeFiles(String videoFilePath, String audioFilePath, String outputFilePath) throws IOException {
        MediaMuxer muxer = null;
        MediaExtractor videoExtractor = null;
        MediaExtractor audioExtractor = null;

        try {
            // Setup output file
            File outputFile = new File(outputFilePath);
            if (outputFile.exists()) {
                outputFile.delete();
            }

            muxer = new MediaMuxer(outputFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            // --- Video Track ---
            videoExtractor = new MediaExtractor();
            try {
                File v = new File(videoFilePath);
                FileInputStream vfis = new FileInputStream(v);
                videoExtractor.setDataSource(vfis.getFD());
            } catch (Exception e) {
                Log.e(TAG, "Error opening audio file: " + e.getMessage());
                e.printStackTrace();

                videoExtractor.setDataSource(videoFilePath);
            }
            int videoTrackIndex = -1;
            MediaFormat videoFormat = null;
            for (int i = 0; i < videoExtractor.getTrackCount(); i++) {
                MediaFormat format = videoExtractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("video/")) {
                    videoExtractor.selectTrack(i);
                    videoFormat = format;
                    videoTrackIndex = muxer.addTrack(videoFormat);
                    break;
                }
            }
            if (videoTrackIndex == -1) {
                throw new IOException("No video track found in " + videoFilePath);
            }

            // --- Audio Track ---
            audioExtractor = new MediaExtractor();
            try {
                File a = new File(audioFilePath);
                FileInputStream afis = new FileInputStream(a);
                audioExtractor.setDataSource(afis.getFD());
            } catch (Exception e) {
                Log.e(TAG, "Error opening audio file: " + e.getMessage());
                e.printStackTrace();

                audioExtractor.setDataSource(audioFilePath);
            }

            int audioTrackIndex = -1;
            MediaFormat audioFormat = null;
            for (int i = 0; i < audioExtractor.getTrackCount(); i++) {
                MediaFormat format = audioExtractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    audioExtractor.selectTrack(i);
                    audioFormat = format;
                    audioTrackIndex = muxer.addTrack(audioFormat);
                    break;
                }
            }
            if (audioTrackIndex == -1) {
                throw new IOException("No audio track found in " + audioFilePath);
            }

            muxer.start();

            // --- Copy Video Samples ---
            ByteBuffer videoBuffer = ByteBuffer.allocate(1024 * 1024); // 1MB buffer
            MediaCodec.BufferInfo videoBufferInfo = new MediaCodec.BufferInfo();
            videoExtractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);

            while (true) {
                int sampleSize = videoExtractor.readSampleData(videoBuffer, 0);
                if (sampleSize < 0) {
                    break;
                }
                videoBufferInfo.offset = 0;
                videoBufferInfo.size = sampleSize;
                videoBufferInfo.presentationTimeUs = videoExtractor.getSampleTime();
                videoBufferInfo.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME; // Use sample flags directly

                muxer.writeSampleData(videoTrackIndex, videoBuffer, videoBufferInfo);
                videoExtractor.advance();
            }
            Log.d(TAG, "Finished writing video track.");

            // --- Copy Audio Samples ---
            ByteBuffer audioBuffer = ByteBuffer.allocate(1024 * 1024); // 1MB buffer
            MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();
            audioExtractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);

            while (true) {
                int sampleSize = audioExtractor.readSampleData(audioBuffer, 0);
                if (sampleSize < 0) {
                    break;
                }
                audioBufferInfo.offset = 0;
                audioBufferInfo.size = sampleSize;
                audioBufferInfo.presentationTimeUs = audioExtractor.getSampleTime();
                audioBufferInfo.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME; // Use sample flags directly

                muxer.writeSampleData(audioTrackIndex, audioBuffer, audioBufferInfo);
                audioExtractor.advance();
            }
            Log.d(TAG, "Finished writing audio track.");


        } finally {
            if (muxer != null) {
                try {
                    muxer.stop();
                } catch (IllegalStateException e) {
                    Log.e(TAG, "IllegalStateException when stopping muxer: " + e.getMessage());
                }
                muxer.release();
                Log.d(TAG, "Muxer released.");
            }
            if (videoExtractor != null) {
                videoExtractor.release();
                Log.d(TAG, "Video extractor released.");
            }
            if (audioExtractor != null) {
                audioExtractor.release();
                Log.d(TAG, "Audio extractor released.");
            }
        }
    }


    /**
     * Delete chunk files
     */
    public static boolean deleteTempFiles(String vfp, String afp) {
        Log.i(TAG, "deleteTempFiles: " + vfp + ", " + afp);
        boolean audioDeleted = false;
        File audioFile = new File(afp);
        if (audioFile.exists()) {
            audioDeleted = audioFile.delete();
            Log.d(TAG, "Audio file " + afp + " deleted: " + audioDeleted);
        } else {
            Log.d(TAG, "Audio file " + afp + " not found.");
        }

        boolean videoDeleted = false;
        File videoFile = new File(vfp);
        if (videoFile.exists()) {
            videoDeleted = videoFile.delete();
            Log.d(TAG, "Video file " + vfp + " deleted: " + videoDeleted);
        } else {
            Log.d(TAG, "Video file " + vfp + " not found.");
        }
        return audioDeleted && videoDeleted; // Or handle individual success/failure as needed
    }

    // ... (rest of your ConcatRunner class)
}
