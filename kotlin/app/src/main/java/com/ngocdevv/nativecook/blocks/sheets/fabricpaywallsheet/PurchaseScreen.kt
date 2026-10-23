package com.ngocdevv.nativecook.blocks.sheets.fabricpaywallsheet

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout

/**
 * The original app's content view and the sheet logic of its MainActivity, moved here unchanged:
 * the white root with the Purchase button, and presenting, sizing and dismissing the sheet. What
 * the Activity did with the window, the back button, the lifecycle and saved state is in
 * FabricPaywallSheetDemo.
 */
internal class PurchaseScreen(context: Context, initialPlan: Plan) : FrameLayout(context) {
    private val root: FrameLayout = this
    private val purchase: GlossyButton
    private var sheet: PurchaseSheet? = null
    private var backdrop: View? = null
    var selected = initialPlan
        private set

    /** True from the moment the sheet is presented until its dismissal has finished. */
    val isSheetShown: Boolean get() = sheet != null

    /** Called when [isSheetShown] changes. */
    var onSheetShownChanged: ((Boolean) -> Unit)? = null

    init {
        setBackgroundColor(Color.WHITE)
        purchase = GlossyButton(context, "Purchase").apply { setOnClickListener { present() } }
        root.addView(purchase, LayoutParams(LayoutParams.WRAP_CONTENT, context.buttonHeight(), Gravity.CENTER))
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateSheetSize() }
    }

    fun present() {
        if (sheet != null) return
        purchase.visibility = View.INVISIBLE
        purchase.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        backdrop = View(context).apply {
            setBackgroundColor(Color.BLACK)
            alpha = .5f
            contentDescription = "Dismiss purchase options"
            setOnClickListener { dismiss() }
        }.also { root.addView(it, LayoutParams(-1, -1)) }
        val panel = PurchaseSheet(context, selected, { selected = it }, { dismiss() })
        sheet = panel
        root.addView(panel, LayoutParams(minOf(root.width, context.dp(560)), (root.height * .895f).toInt(), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        updateSheetSize()
        panel.post { if (sheet === panel) panel.present() }
        onSheetShownChanged?.invoke(true)
    }

    private fun updateSheetSize() {
        val panel = sheet ?: return
        val width = minOf(root.width, context.dp(560))
        val height = (root.height * .895f).toInt()
        if (panel.layoutParams.width != width || panel.layoutParams.height != height)
            panel.layoutParams = (panel.layoutParams as LayoutParams).apply {
                this.width = width; this.height = height
            }
        panel.setScreenSize(root.width, root.height)
    }

    fun dismiss() {
        val panel = sheet ?: return
        panel.dismiss {
            if (sheet === panel) {
                root.removeView(panel)
                backdrop?.let { root.removeView(it) }
                backdrop = null; sheet = null
                purchase.visibility = View.VISIBLE
                purchase.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                purchase.requestFocus()
                onSheetShownChanged?.invoke(false)
            }
        }
    }

    fun pauseFabric() { sheet?.pauseFabric() }
    fun resumeFabric() { sheet?.resumeFabric() }
}
