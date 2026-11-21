package app.hushmessenger.extension;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.Window;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Videos in chat heads (#104). Messenger's video player either draws a video on a SurfaceView, a layer of its own
 * that the system places under the window, or into a TextureView like any other view. 587 takes the SurfaceView on
 * Android 10 and newer unless the screen asks for a TextureView. A chat head is a window over other apps, and on some
 * phones that separate layer shows black there and stays on screen after the chat head closes. Messenger hands its
 * choice and the player's context to textureView() before it builds the player, and a player in a chat head gets the
 * TextureView. Public, since the call comes from Messenger's own package.
 */
public final class ChatHeadVideo {
    static final String KEY = "chat_head_video";

    /**
     * Phone makers whose window compositor leaves the video layer behind. A box stayed on screen where a video had
     * been, both in a chat head and on the way back out of a chat, so the chat head alone wasn't the whole of it
     * (#104, a Huawei Mate XT on Android 12). Nothing in Android reports the fault and the leftover costs more than
     * the layer saves, so every video on these phones draws into a TextureView.
     */
    static final Set<String> LAYER_LEAVES_A_BOX =
        Collections.unmodifiableSet(new HashSet<>(Arrays.asList("huawei", "honor")));

    /** The chat screen Messenger runs inside an Android bubble. Matching it by name needs no help from the phone. */
    static final String BUBBLE_ACTIVITY =
        "com.facebook.messaging.msys.thread.bubbles.activity.StaxThreadViewBubblesActivity";

    private ChatHeadVideo() {}

    /**
     * True when the player should draw into a TextureView: Messenger asked for one, the video is in a chat head, or
     * this phone's compositor leaves the separate layer behind.
     */
    public static boolean textureView(Context context, boolean messengerAsked) {
        if (messengerAsked) return true;
        try {
            return context != null && Settings.running() && (layerLeavesABox() || inChatHead(context));
        } catch (Throwable error) {
            Settings.hookFailedPrivately(KEY, "Couldn't tell where a video plays", error);
            return false;
        }
    }

    /** Whether this phone is one of [LAYER_LEAVES_A_BOX]. */
    static boolean layerLeavesABox() {
        String maker = Build.MANUFACTURER;
        return maker != null && LAYER_LEAVES_A_BOX.contains(maker.trim().toLowerCase(Locale.US));
    }

    /**
     * Chat heads draw from Messenger's chat head service, so their context leads to no activity. On Android 11 and
     * newer they can also be bubbles, where the chat runs in an activity Android launched from the bubble.
     */
    static boolean inChatHead(Context context) {
        Activity activity = ChatJump.activity(context);
        if (activity == null) return true;
        // Some phones answer isLaunchedFromBubble() false for a chat that is in a bubble, which left the chat on the
        // normal screen's layout and the gallery button opening nothing. The bubble's own screen is named, so ask that.
        if (BUBBLE_ACTIVITY.equals(activity.getClass().getName())) return true;
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && activity.isLaunchedFromBubble();
    }

    /**
     * Whether [activity] is a chat Android shows in a bubble. Besides the bubble's own screen name and
     * isLaunchedFromBubble(), which some phones get wrong, a screen in multi-window mode or in a window narrower than
     * the phone counts too. Anything counted wrongly just keeps Messenger's own look, so effects that blur or tint a
     * chat leave it alone. Never throws.
     */
    static boolean inBubbleActivity(Activity activity) {
        try {
            if (activity == null) return false;
            if (BUBBLE_ACTIVITY.equals(activity.getClass().getName())) return true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && activity.isLaunchedFromBubble()) return true;
            if (activity.isInMultiWindowMode()) return true;
            Window window = activity.getWindow();
            View root = window == null ? null : window.peekDecorView();
            int screen = activity.getResources().getDisplayMetrics().widthPixels;
            return root != null && root.getWidth() > 0 && root.getWidth() < screen * 0.98f;
        } catch (Throwable error) {
            return false;
        }
    }
}
