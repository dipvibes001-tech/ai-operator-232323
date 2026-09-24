package com.aioperator.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.aioperator.model.ScreenElement
import com.aioperator.model.ScreenState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AIOperatorAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceActive.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        _isServiceActive.value = false
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        _isServiceActive.value = false
    }

    fun captureCurrentScreen(): ScreenState {
        val rootNode = rootInActiveWindow ?: return ScreenState(null, emptyList())
        val elements = mutableListOf<ScreenElement>()
        val packageName = rootNode.packageName?.toString()

        fun traverse(node: AccessibilityNodeInfo?) {
            if (node == null) return

            val text = node.text?.toString() ?: node.contentDescription?.toString()
            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            if (!text.isNullOrBlank() && node.isVisibleToUser) {
                elements.add(
                    ScreenElement(
                        text = text.trim(),
                        clickable = node.isClickable,
                        editable = node.isEditable,
                        resourceId = node.viewIdResourceName,
                        bounds = "${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}"
                    )
                )
            }

            for (i in 0 until node.childCount) {
                traverse(node.getChild(i))
            }
        }

        traverse(rootNode)
        return ScreenState(packageName, elements)
    }

    fun tapOnText(targetText: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val nodes = rootNode.findAccessibilityNodeInfosByText(targetText)

        for (node in nodes) {
            var clickableNode: AccessibilityNodeInfo? = node
            while (clickableNode != null && !clickableNode.isClickable) {
                clickableNode = clickableNode.parent
            }

            if (clickableNode != null && clickableNode.isClickable) {
                return clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        }
        return false
    }

    fun typeText(text: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val arguments = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    fun scroll(direction: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val action = if (direction.equals("down", ignoreCase = true)) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        return rootNode.performAction(action)
    }

    fun pressBackAction(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    fun pressHomeAction(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    companion object {
        var instance: AIOperatorAccessibilityService? = null
            private set

        private val _isServiceActive = MutableStateFlow(false)
        val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()
    }
}