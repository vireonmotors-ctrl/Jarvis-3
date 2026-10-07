package com.jarvis.app;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

public class JarvisService extends AccessibilityService {

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        int m = MainActivity.mode;
        if (m == 0) return;
        if (System.currentTimeMillis() > MainActivity.until) {
            MainActivity.mode = 0;
            return;
        }
        CharSequence pk = e.getPackageName();
        if (pk == null || !pk.toString().startsWith("com.whatsapp")) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        AccessibilityNodeInfo n = null;
        if (m == 1) {
            List<AccessibilityNodeInfo> l = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send");
            if (l != null && !l.isEmpty()) n = l.get(0);
            if (n == null) n = find(root, new String[]{"enviar", "send"}, true);
        } else if (m == 2) {
            n = find(root, new String[]{"ligação de voz", "ligacao de voz", "chamada de voz", "voice call", "chamada de áudio"}, false);
        } else if (m == 3) {
            n = find(root, new String[]{"ligar", "call", "chamar"}, true);
        }

        if (n != null && click(n)) {
            if (m == 2) {
                MainActivity.mode = 3;
                MainActivity.until = System.currentTimeMillis() + 10000;
            } else {
                MainActivity.mode = 0;
            }
        }
    }

    private AccessibilityNodeInfo find(AccessibilityNodeInfo n, String[] keys, boolean exact) {
        if (n == null) return null;
        String d = lower(n.getContentDescription());
        String t = lower(n.getText());
        for (String k : keys) {
            if (exact ? (d.equals(k) || t.equals(k)) : (d.contains(k) || t.contains(k))) return n;
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo r = find(n.getChild(i), keys, exact);
            if (r != null) return r;
        }
        return null;
    }

    private boolean click(AccessibilityNodeInfo n) {
        AccessibilityNodeInfo x = n;
        while (x != null && !x.isClickable()) x = x.getParent();
        return x != null && x.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    private String lower(CharSequence c) {
        return c == null ? "" : c.toString().toLowerCase();
    }

    @Override
    public void onInterrupt() {}
}