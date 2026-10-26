package com.hatkid.mkxpz;

import android.util.Log;
import android.util.SparseIntArray;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Iterator;

/**
 * Translates physical controller input (built-in handheld controls, USB or
 * Bluetooth pads) into the keyboard keys the game already understands.
 *
 * Going through the keyboard rather than SDL's GameController API means every
 * game script path works, including the few that only check raw keys
 * (Input.triggerex?(:RETURN) etc.), and the mapping is the same on every device.
 *
 * Defaults can be overridden with a "controller.json" file in the game folder:
 *
 *   {
 *     "swapConfirmCancel": false,
 *     "stickDeadzone": 0.5,
 *     "buttons": { "BUTTON_A": "ENTER", "BUTTON_R2": "F" }
 *   }
 *
 * Button names are Android KeyEvent names without the "KEYCODE_" prefix.
 */
public class ControllerMapper
{
    public interface KeySink
    {
        void keyDown(int keycode);
        void keyUp(int keycode);
    }

    private static final String TAG = "mkxp-z[Controller]";

    // Physical button -> keyboard key
    private final SparseIntArray mButtons = new SparseIntArray();

    // Stick -> D-pad with hysteresis so a resting stick never jitters
    private float mPressThreshold = 0.5f;
    private float mReleaseThreshold = 0.35f;

    // Direction state per source (stick and hat are tracked separately and
    // OR-ed together, so releasing one never cancels the other)
    private static final int UP = 0, DOWN = 1, LEFT = 2, RIGHT = 3;
    private static final int[] DIR_KEYS = {
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT
    };
    private final boolean[] mStickDir = new boolean[4];
    private final boolean[] mHatDir = new boolean[4];
    private final boolean[] mButtonDir = new boolean[4];
    private final boolean[] mSentDir = new boolean[4];

    private final KeySink mSink;

    public ControllerMapper(KeySink sink)
    {
        mSink = sink;
        loadDefaults();
    }

    private void loadDefaults()
    {
        mButtons.clear();

        // RGSS meaning in parentheses; see Data/Scripts/001_Technical/004_Input.rb
        mButtons.put(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_ENTER);        // C: confirm / interact
        mButtons.put(KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_X);            // B: back / open menu
        mButtons.put(KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_SHIFT_LEFT);   // A: run, sort, mega
        mButtons.put(KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_D);            // Z: registered item, shift
        mButtons.put(KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_Q);           // L: page up / jump
        mButtons.put(KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_W);           // R: page down / jump
        mButtons.put(KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_A);           // X
        mButtons.put(KeyEvent.KEYCODE_BUTTON_R2, KeyEvent.KEYCODE_S);           // Y
        mButtons.put(KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_X);        // B: pause menu on the overworld
        mButtons.put(KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_D);       // Z
        mButtons.put(KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_SHIFT_LEFT);
    }

    /** Reads <gameDir>/controller.json if present. Bad entries are logged and skipped. */
    public void loadConfig(String gameDir)
    {
        File file = new File(gameDir, "controller.json");
        if (!file.isFile())
            return;

        try {
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(text);

            if (json.optBoolean("swapConfirmCancel", false)) {
                int a = mButtons.get(KeyEvent.KEYCODE_BUTTON_A);
                mButtons.put(KeyEvent.KEYCODE_BUTTON_A, mButtons.get(KeyEvent.KEYCODE_BUTTON_B));
                mButtons.put(KeyEvent.KEYCODE_BUTTON_B, a);
                int x = mButtons.get(KeyEvent.KEYCODE_BUTTON_X);
                mButtons.put(KeyEvent.KEYCODE_BUTTON_X, mButtons.get(KeyEvent.KEYCODE_BUTTON_Y));
                mButtons.put(KeyEvent.KEYCODE_BUTTON_Y, x);
            }

            if (json.has("stickDeadzone")) {
                mPressThreshold = (float) json.getDouble("stickDeadzone");
                mReleaseThreshold = mPressThreshold * 0.7f;
            }

            JSONObject buttons = json.optJSONObject("buttons");
            if (buttons != null) {
                Iterator<String> it = buttons.keys();
                while (it.hasNext()) {
                    String from = it.next();
                    String to = buttons.optString(from, "");
                    int fromCode = KeyEvent.keyCodeFromString("KEYCODE_" + from);
                    if (fromCode == KeyEvent.KEYCODE_UNKNOWN) {
                        Log.w(TAG, "Unknown controller button: " + from);
                        continue;
                    }
                    if (to.isEmpty() || to.equals("NONE")) {
                        mButtons.delete(fromCode);
                        continue;
                    }
                    int toCode = KeyEvent.keyCodeFromString("KEYCODE_" + to);
                    if (toCode == KeyEvent.KEYCODE_UNKNOWN) {
                        Log.w(TAG, "Unknown keyboard key: " + to);
                        continue;
                    }
                    mButtons.put(fromCode, toCode);
                }
            }

            Log.i(TAG, "Loaded " + file);
        } catch (Exception e) {
            Log.e(TAG, "Failed to read " + file + ", using defaults: " + e);
            loadDefaults();
        }
    }

    private static boolean isController(InputDevice device)
    {
        if (device == null)
            return false;
        int sources = device.getSources();
        return (sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
            || (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
            || (sources & InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD;
    }

    /** @return true if the event was consumed */
    public boolean onKeyEvent(KeyEvent evt)
    {
        if (!isController(evt.getDevice()))
            return false;

        int code = evt.getKeyCode();

        // Leave system keys to Android
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_HOME
            || code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN
            || code == KeyEvent.KEYCODE_VOLUME_MUTE)
            return false;

        int action = evt.getAction();
        if (action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP)
            return true;

        // The game does its own key repeat
        if (action == KeyEvent.ACTION_DOWN && evt.getRepeatCount() > 0)
            return true;

        boolean down = action == KeyEvent.ACTION_DOWN;

        int dir = directionOf(code);
        if (dir >= 0) {
            mButtonDir[dir] = down;
            flushDirections();
            return true;
        }

        int mapped = mButtons.get(code, KeyEvent.KEYCODE_UNKNOWN);
        if (mapped == KeyEvent.KEYCODE_UNKNOWN)
            return true;

        if (down)
            mSink.keyDown(mapped);
        else
            mSink.keyUp(mapped);
        return true;
    }

    /** Handles analog sticks and hat-switch D-pads. @return true if consumed */
    public boolean onMotionEvent(MotionEvent evt)
    {
        if (!isController(evt.getDevice()) || evt.getAction() != MotionEvent.ACTION_MOVE)
            return false;

        float hx = evt.getAxisValue(MotionEvent.AXIS_HAT_X);
        float hy = evt.getAxisValue(MotionEvent.AXIS_HAT_Y);
        mHatDir[LEFT] = hx < -0.5f;
        mHatDir[RIGHT] = hx > 0.5f;
        mHatDir[UP] = hy < -0.5f;
        mHatDir[DOWN] = hy > 0.5f;

        float sx = evt.getAxisValue(MotionEvent.AXIS_X);
        float sy = evt.getAxisValue(MotionEvent.AXIS_Y);
        mStickDir[LEFT] = axisState(mStickDir[LEFT], -sx);
        mStickDir[RIGHT] = axisState(mStickDir[RIGHT], sx);
        mStickDir[UP] = axisState(mStickDir[UP], -sy);
        mStickDir[DOWN] = axisState(mStickDir[DOWN], sy);

        // Overworld movement is 4-way: keep only the dominant stick axis
        if ((mStickDir[LEFT] || mStickDir[RIGHT]) && (mStickDir[UP] || mStickDir[DOWN])) {
            if (Math.abs(sx) >= Math.abs(sy))
                mStickDir[UP] = mStickDir[DOWN] = false;
            else
                mStickDir[LEFT] = mStickDir[RIGHT] = false;
        }

        flushDirections();
        return true;
    }

    private boolean axisState(boolean wasDown, float value)
    {
        return wasDown ? value > mReleaseThreshold : value > mPressThreshold;
    }

    private static int directionOf(int keycode)
    {
        switch (keycode) {
            case KeyEvent.KEYCODE_DPAD_UP: return UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: return DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT: return LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return RIGHT;
            default: return -1;
        }
    }

    private void flushDirections()
    {
        for (int i = 0; i < 4; i++) {
            boolean want = mStickDir[i] || mHatDir[i] || mButtonDir[i];
            if (want == mSentDir[i])
                continue;
            mSentDir[i] = want;
            if (want)
                mSink.keyDown(DIR_KEYS[i]);
            else
                mSink.keyUp(DIR_KEYS[i]);
        }
    }

    /** Releases everything, e.g. when the app loses focus mid-press. */
    public void releaseAll()
    {
        for (int i = 0; i < 4; i++) {
            mStickDir[i] = mHatDir[i] = mButtonDir[i] = false;
        }
        flushDirections();
        for (int i = 0; i < mButtons.size(); i++)
            mSink.keyUp(mButtons.valueAt(i));
    }
}
