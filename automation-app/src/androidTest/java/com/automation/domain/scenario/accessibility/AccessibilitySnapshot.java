package com.automation.domain.scenario.accessibility;

import android.app.UiAutomation;
import android.graphics.Rect;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.automation.domain.scenario.SelectorCondition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 用于缓存当前窗口的可访问节点树，避免重复获取。
 */
public final class AccessibilitySnapshot {

    private static final String TAG = "AccessibilitySnapshot";
    private static final AccessibilitySnapshot EMPTY = new AccessibilitySnapshot(Collections.emptyList(), null);

    private final List<Node> nodes;
    private final Node root;

    private AccessibilitySnapshot(List<Node> nodes, Node root) {
        this.nodes = Collections.unmodifiableList(nodes);
        this.root = root;
    }

    public static AccessibilitySnapshot empty() {
        return EMPTY;
    }

    public static AccessibilitySnapshot capture(@Nullable UiAutomation automation) {
        if (automation == null) {
            return EMPTY;
        }

        AccessibilityNodeInfo root;
        try {
            root = automation.getRootInActiveWindow();
        } catch (RuntimeException e) {
            Log.e(TAG, "获取根节点失败", e);
            return EMPTY;
        }

        if (root == null) {
            return EMPTY;
        }

        List<Node> flat = new ArrayList<>(128);
        Node treeRoot = buildTree(root, flat);
        return new AccessibilitySnapshot(flat, treeRoot);
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    public boolean exists(@NonNull SelectorCondition condition) {
        for (Node node : nodes) {
            if (condition.matchesNode(node)) {
                return true;
            }
        }
        return false;
    }

    public List<Node> nodes() {
        return nodes;
    }

    @Nullable
    public Node root() {
        return root;
    }

    public List<Node> childrenOf(@Nullable Node node) {
        if (node == null) {
            return List.of();
        }
        return node.children();
    }

    private static Node buildTree(AccessibilityNodeInfo info, List<Node> flat) {
        Node node = Node.from(info);
        flat.add(node);
        int childCount = info.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = info.getChild(i);
            if (child == null) {
                continue;
            }
            Node childNode = buildTree(child, flat);
            node.addChild(childNode);
        }
        info.recycle();
        node.freezeChildren();
        return node;
    }

    public static final class Node {
        private final String text;
        private final String contentDescription;
        private final String resourceId;
        private final String className;
        private final String packageName;
        private final boolean clickable;
        private final boolean enabled;
        private final boolean selected;
        private final boolean checkable;
        private final boolean checked;
        private final boolean focusable;
        private final boolean focused;
        private final boolean scrollable;
        private final boolean longClickable;
        private final Rect boundsInScreen;
        private List<Node> children = new ArrayList<>();

        private Node(String text,
                     String contentDescription,
                     String resourceId,
                     String className,
                     String packageName,
                     boolean clickable,
                     boolean enabled,
                     boolean selected,
                     boolean checkable,
                     boolean checked,
                     boolean focusable,
                     boolean focused,
                     boolean scrollable,
                     boolean longClickable,
                     Rect bounds) {
            this.text = text;
            this.contentDescription = contentDescription;
            this.resourceId = resourceId;
            this.className = className;
            this.packageName = packageName;
            this.clickable = clickable;
            this.enabled = enabled;
            this.selected = selected;
            this.checkable = checkable;
            this.checked = checked;
            this.focusable = focusable;
            this.focused = focused;
            this.scrollable = scrollable;
            this.longClickable = longClickable;
            this.boundsInScreen = bounds != null ? new Rect(bounds) : new Rect();
        }

        private static Node from(AccessibilityNodeInfo info) {
            Rect rect = new Rect();
            info.getBoundsInScreen(rect);
            return new Node(
                    safeToString(info.getText()),
                    safeToString(info.getContentDescription()),
                    info.getViewIdResourceName(),
                    safeToString(info.getClassName()),
                    safeToString(info.getPackageName()),
                    info.isClickable(),
                    info.isEnabled(),
                    info.isSelected(),
                    info.isCheckable(),
                    info.isChecked(),
                    info.isFocusable(),
                    info.isFocused(),
                    info.isScrollable(),
                    info.isLongClickable(),
                    rect
            );
        }

        private void addChild(Node child) {
            if (child == null) {
                return;
            }
            children.add(child);
        }

        private void freezeChildren() {
            if (!(children instanceof ArrayList)) {
                return;
            }
            children = Collections.unmodifiableList(children);
        }

        @Nullable
        public String text() {
            return text;
        }

        @Nullable
        public String contentDescription() {
            return contentDescription;
        }

        @Nullable
        public String resourceId() {
            return resourceId;
        }

        @Nullable
        public String className() {
            return className;
        }

        @Nullable
        public String packageName() {
            return packageName;
        }

        public boolean clickable() {
            return clickable;
        }

        public boolean enabled() {
            return enabled;
        }

        public boolean selected() {
            return selected;
        }

        public boolean checkable() {
            return checkable;
        }

        public boolean checked() {
            return checked;
        }

        public boolean focusable() {
            return focusable;
        }

        public boolean focused() {
            return focused;
        }

        public boolean scrollable() {
            return scrollable;
        }

        public boolean longClickable() {
            return longClickable;
        }

        public Rect bounds() {
            return new Rect(boundsInScreen);
        }

        public List<Node> children() {
            return children;
        }

        private static String safeToString(CharSequence cs) {
            return cs == null ? null : cs.toString();
        }
    }
}
