package com.aimx.hack

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

@SuppressLint("ViewConstructor")
class FloatingMenu(
    private val context: Context,
    private val windowManager: WindowManager,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun onToggleLines(enabled: Boolean)
        fun onToggleShotState(enabled: Boolean)
        fun onToggleAutoAim(enabled: Boolean)
    }

    private var menuExpanded = false
    private val density = context.resources.displayMetrics.density

    private val iconView = createIconView()
    private val menuLayout = createMenuLayout()

    val iconParams = WindowManager.LayoutParams(
        (48 * density).toInt(),
        (48 * density).toInt(),
        OverlayUtils.overlayWindowType,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = (10 * density).toInt()
        y = (200 * density).toInt()
    }

    fun attach() {
        try { windowManager.addView(iconView, iconParams) } catch (_: Exception) {}
    }

    fun detach() {
        try { windowManager.removeView(iconView) } catch (_: Exception) {}
        try { windowManager.removeView(menuLayout) } catch (_: Exception) {}
    }

    private fun createIconView(): View {
        val size = (48 * density).toInt()
        return ImageView(context).apply {
            layoutParams = ViewGroup.LayoutParams(size, size)
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(180, 0, 0, 0))
                setStroke(2, Color.argb(200, 0, 230, 118))
            }
            background = bg
            setImageResource(android.R.drawable.ic_menu_compass)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setOnClickListener { toggleMenu() }
            setOnTouchListener(createDragListener())
        }
    }

    private fun createMenuLayout(): LinearLayout {
        val menuWidth = (180 * density).toInt()
        val menuHeight = (150 * density).toInt()

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = WindowManager.LayoutParams(
                menuWidth, menuHeight,
                OverlayUtils.overlayWindowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = (60 * density).toInt()
                y = (200 * density).toInt()
            }
            val bg = GradientDrawable().apply {
                cornerRadius = 8 * density
                setColor(Color.argb(220, 20, 20, 30))
                setStroke(1, Color.argb(150, 0, 230, 118))
            }
            background = bg
            setPadding(12, 12, 12, 12)

            addView(createTitle("AimX"))
            addSwitch("Trajetórias", true) { callbacks.onToggleLines(it) }
            addSwitch("Shot State", true) { callbacks.onToggleShotState(it) }
        }
    }

    private fun createTitle(text: String): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(Color.rgb(0, 230, 118))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 6)
        }
    }

    private fun LinearLayout.addSwitch(label: String, initial: Boolean, onChange: (Boolean) -> Unit) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (32 * density).toInt()
            )
        }
        val tv = TextView(context).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val sw = Switch(context).apply {
            isChecked = initial
            setOnCheckedChangeListener { _, checked -> onChange(checked) }
        }
        row.addView(tv)
        row.addView(sw)
        addView(row)
    }

    private fun toggleMenu() {
        menuExpanded = !menuExpanded
        if (menuExpanded) {
            iconView.visibility = View.GONE
            try { windowManager.addView(menuLayout, menuLayout.layoutParams) } catch (_: Exception) {}
        } else {
            try { windowManager.removeView(menuLayout) } catch (_: Exception) {}
            iconView.visibility = View.VISIBLE
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createDragListener(): View.OnTouchListener {
        return object : View.OnTouchListener {
            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f
            var isDragging = false
            val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = iconParams.x
                        initialY = iconParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - initialTouchX
                        val dy = event.rawY - initialTouchY
                        if (!isDragging && (dx * dx + dy * dy) > touchSlop * touchSlop) {
                            isDragging = true
                        }
                        if (isDragging) {
                            iconParams.x = initialX + dx.toInt()
                            iconParams.y = initialY + dy.toInt()
                            try { windowManager.updateViewLayout(v, iconParams) } catch (_: Exception) {}
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isDragging) v.performClick()
                        return true
                    }
                }
                return false
            }
        }
    }
}
