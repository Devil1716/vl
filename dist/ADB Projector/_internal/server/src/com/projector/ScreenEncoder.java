package com.projector;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import android.view.Surface;
import android.os.IBinder;

public class ScreenEncoder {

    private final OutputStream outputStream;

    public ScreenEncoder(OutputStream outputStream) {
        this.outputStream = outputStream;
    }

    public void start() {
        try {
            int width = 720;
            int height = 1280;
            int bitRate = 2_000_000;
            int frameRate = 30;

            MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

            MediaCodec codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            Surface surface = codec.createInputSurface();

            // Using reflection to bypass hidden APIs (SurfaceControl)
            Class<?> surfaceControlClass = Class.forName("android.view.SurfaceControl");
            
            // IBinder display = SurfaceControl.createDisplay("Projector", false);
            Method createDisplay = surfaceControlClass.getMethod("createDisplay", String.class, boolean.class);
            IBinder display = (IBinder) createDisplay.invoke(null, "Projector", false);

            // SurfaceControl.openTransaction();
            Method openTransaction = surfaceControlClass.getMethod("openTransaction");
            openTransaction.invoke(null);

            try {
                // SurfaceControl.setDisplaySurface(display, surface);
                Method setDisplaySurface = surfaceControlClass.getMethod("setDisplaySurface", IBinder.class, Surface.class);
                setDisplaySurface.invoke(null, display, surface);

                // SurfaceControl.setDisplayLayerStack(display, 0);
                Method setDisplayLayerStack = surfaceControlClass.getMethod("setDisplayLayerStack", IBinder.class, int.class);
                setDisplayLayerStack.invoke(null, display, 0);

                // SurfaceControl.setDisplayProjection(display, 0, new Rect(0,0,w,h), new Rect(0,0,w,h));
                Class<?> rectClass = Class.forName("android.graphics.Rect");
                Object rect = rectClass.getConstructor(int.class, int.class, int.class, int.class).newInstance(0, 0, width, height);
                Method setDisplayProjection = surfaceControlClass.getMethod("setDisplayProjection", IBinder.class, int.class, rectClass, rectClass);
                setDisplayProjection.invoke(null, display, 0, rect, rect);
            } finally {
                // SurfaceControl.closeTransaction();
                Method closeTransaction = surfaceControlClass.getMethod("closeTransaction");
                closeTransaction.invoke(null);
            }

            codec.start();

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            while (true) {
                int index = codec.dequeueOutputBuffer(bufferInfo, 10000);
                if (index >= 0) {
                    ByteBuffer buffer = codec.getOutputBuffer(index);
                    if (buffer != null && bufferInfo.size > 0) {
                        buffer.position(bufferInfo.offset);
                        buffer.limit(bufferInfo.offset + bufferInfo.size);
                        byte[] chunk = new byte[bufferInfo.size];
                        buffer.get(chunk);
                        outputStream.write(chunk);
                        outputStream.flush();
                    }
                    codec.releaseOutputBuffer(index, false);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
