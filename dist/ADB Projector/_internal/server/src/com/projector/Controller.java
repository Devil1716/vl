package com.projector;

import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

public class Controller {

    private final InputStream inputStream;
    private Object inputManager;
    private Method injectInputEventMethod;
    private Class<?> inputManagerClass;

    public Controller(InputStream inputStream) {
        this.inputStream = inputStream;
        try {
            // hidden Android APIs to inject native events without accessibility
            inputManagerClass = Class.forName("android.hardware.input.InputManager");
            Method getInstanceMethod = inputManagerClass.getMethod("getInstance");
            inputManager = getInstanceMethod.invoke(null);
            
            // INJECT_INPUT_EVENT_MODE_ASYNC = 0
            injectInputEventMethod = inputManagerClass.getMethod("injectInputEvent", android.view.InputEvent.class, int.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void start() {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = reader.readLine()) != null) {
                // Parse the very simple JSON injected by the PC python client
                if (line.contains("\"type\": \"tap\"")) {
                    float x = extractFloat(line, "x");
                    float y = extractFloat(line, "y");
                    injectTap(x, y);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private float extractFloat(String json, String key) {
        // very basic parsing to avoid bundling a JSON library
        String search = "\"" + key + "\": ";
        int start = json.indexOf(search) + search.length();
        int end = json.indexOf(",", start);
        if (end == -1) end = json.indexOf("}", start);
        return Float.parseFloat(json.substring(start, end));
    }

    private void injectEvent(android.view.InputEvent event) throws Exception {
        if (injectInputEventMethod != null && inputManager != null) {
            injectInputEventMethod.invoke(inputManager, event, 0); // 0 = ASYNC
        }
    }

    private void injectTap(float x, float y) throws Exception {
        long now = SystemClock.uptimeMillis();
        MotionEvent downEvent = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
        downEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        injectEvent(downEvent);

        MotionEvent upEvent = MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, x, y, 0);
        upEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        injectEvent(upEvent);
    }
}
