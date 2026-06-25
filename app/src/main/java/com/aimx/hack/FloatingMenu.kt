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
import android.widget.ScrollView
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

    private var menuVisible = false

    val layoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        50, 400,
        OverlayUtils.overlayWindowType,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    )

    private val iconView = createIconView()
    private val menuView = createMenuView()

    init {
        layoutParams.gravity = Gravity.TOP or Gravity.START
    }

    fun attach() {
        windowManager.addView(iconView, layoutParams)
    }

    fun detach() {
        try { windowManager.removeView(iconView) } catch (_: Exception) {}
        try { windowManager.removeView(menuView) } catch (_: Exception) {}
    }

    private fun createIconView(): View {
        val size = (48 * context.resources.displayMetrics.density).toInt()
        return ImageView(context).apply {
            layoutParams = ViewGroup.LayoutParams(size, size)
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(200, 0, 0, 0))
                setStroke(2, Color.argb(200, 0, 230, 118))
            }
            background = bg
            setImageResource(android.R.drawable.ic_menu_compass)
            setOnClickListener { toggleMenu() }
            setOnTouchListener(createDragListener())
        }
    }

    private fun createMenuView(): LinearLayout {
        val density = context.resources.displayMetrics.density
        val menuWidth = (220 * density).toInt()
        val menuHeight = (300 * density).toInt()

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
                y = 400
            }
            val bg = GradientDrawable().apply {
                cornerRadius = 12 * density
                setColor(Color.argb(230, 20, 20, 30))
                setStroke(2, Color.argb(200, 0, 230, 118))
            }
            background = bg
            setPadding(16, 16, 16, 16)

            addView(createTitle("AimX Hack"))
            addView(createSeparator())

            val scrollView = ScrollView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
                )
            }
            val content = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }

            addSwitch("Trajetórias", true) { callbacks.onToggleLines(it) }
            addSwitch("Shot State", true) { callbacks.onToggleShotState(it) }
            addSwitch("Auto-Aim", false) { callbacks.onToggleAutoAim(it) }

            scrollView.addView(content)

            val contentLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            addView(scrollView)

            addView(createSeparator())
            addView(createButton("Fechar") { toggleMenu() })
        }
    }

    private fun LinearLayout.addSwitch(label: String, initial: Boolean, onChange: (Boolean) -> Unit) {
        val density = context.resources.displayMetrics.density
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (40 * density).toInt()
            )
        }
        val tv = TextView(context).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 14f
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

    private fun createTitle(text: String): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(Color.rgb(0, 230, 118))
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8)
        }
    }

    private fun createSeparator(): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 2
            ).apply { topMargin = 8; bottomMargin = 8 }
            setBackgroundColor(Color.argb(100, 255, 255, 255))
        }
    }

    private fun createButton(text: String, onClick: () -> Unit): TextView {
        val density = context.resources.displayMetrics.density
        return TextView(context).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(16, (8 * density).toInt(), 16, (8 * density).toInt())
            val bg = GradientDrawable().apply {
                cornerRadius = 8 * density
                setColor(Color.argb(150, 255, 52, 52))
            }
            background = bg
            setOnClickListener { onClick() }
        }
    }

    private fun toggleMenu() {
        menuVisible = !menuVisible
        if (menuVisible) {
            iconView.visibility = View.GONE
            try { windowManager.addView(menuView, menuView.layoutParams) } catch (_: Exception) {}
        } else {
            try { windowManager.removeView(menuView) } catch (_: Exception) {}
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
                        initialX = layoutParams.x
                        initialY = layoutParams.y
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
                            layoutParams.x = initialX + dx.toInt()
                            layoutParams.y = initialY + dy.toInt()
                            windowManager.updateViewLayout(v, layoutParams)
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
