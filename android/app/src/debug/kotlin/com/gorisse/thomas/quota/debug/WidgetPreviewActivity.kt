package com.gorisse.thomas.quota.debug

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.gorisse.thomas.quota.widget.QuotaWidgetReceiver

/**
 * Debug-only harness that binds the app's own widget provider into an [AppWidgetHost] and
 * shows it at the two sizes that ship, 2×1 and 4×2, stacked. This renders the real
 * RemoteViews Glance produces — it is not a mock — so a screenshot of this screen is
 * evidence about the widget itself.
 *
 * Binding needs a permission a normal app cannot hold, so grant it first:
 *
 *     adb shell appwidget grantbind --package com.gorisse.thomas.quota --user 0
 */
class WidgetPreviewActivity : ComponentActivity() {

    private val host by lazy { AppWidgetHost(this, HOST_ID) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = AppWidgetManager.getInstance(this)
        val provider = ComponentName(this, QuotaWidgetReceiver::class.java)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(16), dp(24), dp(16), dp(24))
        }

        listOf("2 x 1" to (160 to 80), "4 x 2" to (330 to 180)).forEach { (name, size) ->
            val (widthDp, heightDp) = size
            root.addView(
                TextView(this).apply {
                    text = name
                    setTextColor(Color.parseColor("#9AA4B2"))
                    setPadding(0, dp(16), 0, dp(8))
                },
            )
            val id = host.allocateAppWidgetId()
            val bound = manager.bindAppWidgetIdIfAllowed(id, provider)
            if (!bound) {
                root.addView(
                    TextView(this).apply {
                        text = "bind refused — run `adb shell appwidget grantbind " +
                            "--package $packageName --user 0`"
                        setTextColor(Color.RED)
                    },
                )
                return@forEach
            }
            manager.updateAppWidgetOptions(
                id,
                Bundle().apply {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
                },
            )
            val view = host.createView(this, id, manager.getAppWidgetInfo(id))
            view.setAppWidget(id, manager.getAppWidgetInfo(id))
            root.addView(view, ViewGroup.LayoutParams(dp(widthDp), dp(heightDp)))
        }

        setContentView(root)
    }

    override fun onStart() {
        super.onStart()
        host.startListening()
    }

    override fun onStop() {
        super.onStop()
        runCatching { host.stopListening() }
    }

    private fun dp(value: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        resources.displayMetrics,
    ).toInt()

    private companion object {
        const val HOST_ID = 4242
    }
}
