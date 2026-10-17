package com.quqi.autohand.ui

import android.content.Context
import android.util.TypedValue
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.quqi.autohand.R

/** 隐私政策 / 权限说明 / 使用声明这类长文本弹框。 */
object PolicyDialogs {

    fun showPrivacy(c: Context) = show(c, R.string.privacy_title, R.string.privacy_text)

    fun showPermissions(c: Context) = show(c, R.string.permissions_title, R.string.permissions_text)

    fun showDisclaimer(c: Context) = show(c, R.string.disclaimer_title, R.string.disclaimer_text)

    private fun show(c: Context, titleRes: Int, textRes: Int) {
        val text = TextView(c).apply {
            setText(textRes)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setLineSpacing(dp(c, 4), 1f)
            setPadding(dp(c, 24).toInt(), dp(c, 8).toInt(), dp(c, 24).toInt(), 0)
            setTextIsSelectable(true)
        }
        AlertDialog.Builder(c)
            .setTitle(titleRes)
            .setView(ScrollView(c).apply { addView(text) })
            .setPositiveButton(R.string.btn_ok, null)
            .show()
    }

    private fun dp(c: Context, v: Int) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), c.resources.displayMetrics)
}
