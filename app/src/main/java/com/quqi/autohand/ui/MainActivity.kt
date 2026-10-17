package com.quqi.autohand.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.quqi.autohand.R
import com.quqi.autohand.UmengInit
import com.quqi.autohand.databinding.ActivityMainBinding
import com.quqi.autohand.model.Script
import com.quqi.autohand.service.AutoHandService
import com.quqi.autohand.util.Prefs

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: ScriptAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = ScriptAdapter(emptyList(), null, ::select, ::rename, ::delete)
        binding.scriptList.layoutManager = LinearLayoutManager(this)
        binding.scriptList.adapter = adapter

        binding.btnAccessibility.setOnClickListener { openAccessibilitySettings() }
        binding.btnNewScript.setOnClickListener { createBlank() }
        binding.btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        if (!Prefs.privacyConsented(this)) showConsent()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    /** 首启隐私告知：同意后才初始化友盟 SDK，拒绝则完全不上报。 */
    private fun showConsent() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.consent_title)
            .setMessage(R.string.consent_msg)
            .setPositiveButton(R.string.consent_agree) { _, _ ->
                Prefs.setPrivacyConsented(this, true)
                UmengInit.start(this)
            }
            .setNegativeButton(R.string.consent_deny) { _, _ -> }
            .setNeutralButton(R.string.consent_view, null)
            .setCancelable(false)
            .create()
        dialog.show()
        // 中性按钮默认会关掉弹框，这里改成只打开政策全文、保留选择
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            PolicyDialogs.showPrivacy(this)
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val enabled = AutoHandService.isServiceEnabled(this)
        binding.statusText.text = getString(
            if (enabled) com.quqi.autohand.R.string.status_service_on else com.quqi.autohand.R.string.status_service_off
        )
        binding.statusHint.text = getString(
            if (enabled) com.quqi.autohand.R.string.status_hint_on else com.quqi.autohand.R.string.btn_open_accessibility_hint
        )
        binding.btnAccessibility.isVisible = !enabled
        binding.switchBall.isEnabled = enabled
        // 先摘掉监听再回填状态，避免刷新时把自己写的值又写回去
        binding.switchBall.setOnCheckedChangeListener(null)
        binding.switchBall.isChecked = enabled && Prefs.ballEnabled(this)
        binding.switchBall.setOnCheckedChangeListener { _, checked ->
            Prefs.setBallEnabled(this, checked)
            AutoHandService.instance?.refreshBallVisibility()
        }
        binding.switchKeepScreenOn.setOnCheckedChangeListener(null)
        binding.switchKeepScreenOn.isChecked = Prefs.keepScreenOn(this)
        binding.switchKeepScreenOn.setOnCheckedChangeListener { _, checked ->
            Prefs.setKeepScreenOn(this, checked)
        }
        Prefs.resolveSelected(this)
        val scripts = Prefs.readScripts(this)
        binding.emptyText.isVisible = scripts.isEmpty()
        adapter.submit(scripts, Prefs.selectedId(this))
    }

    private fun select(script: Script) {
        Prefs.setSelected(this, script.id)
        refresh()
    }

    private fun rename(script: Script) {
        val input = EditText(this).apply {
            setText(script.name)
            setSelection(script.name.length)
        }
        val container = FrameLayout(this).apply {
            setPadding(40, 20, 40, 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle("重命名")
            .setView(container)
            .setPositiveButton("保存") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    script.name = name
                    val list = Prefs.readScripts(this)
                    list.firstOrNull { it.id == script.id }?.name = name
                    Prefs.writeScripts(this, list)
                    refresh()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun delete(script: Script) {
        AlertDialog.Builder(this)
            .setTitle("删除「${script.name}」？")
            .setPositiveButton("删除") { _, _ ->
                val list = Prefs.readScripts(this).filterNot { it.id == script.id }
                Prefs.writeScripts(this, list)
                Prefs.resolveSelected(this)
                refresh()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun createBlank() {
        val script = Script(name = Prefs.nextScriptName(this))
        val list = Prefs.readScripts(this).toMutableList()
        list.add(script)
        Prefs.writeScripts(this, list)
        Prefs.setSelected(this, script.id)
        refresh()
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            // 部分定制 ROM 不认这个 action，退到应用详情页
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            )
        }
    }
}
