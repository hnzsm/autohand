package com.quqi.autohand.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.quqi.autohand.BuildConfig
import com.quqi.autohand.databinding.ActivitySettingsBinding
import com.quqi.autohand.util.UpdateChecker

class SettingsActivity : AppCompatActivity() {

    private companion object {
        const val REPO_PAGE = "https://github.com/hnzsm/autohand"
    }

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.versionText.text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

        binding.btnBack.setOnClickListener { finish() }
        binding.rowPrivacy.setOnClickListener { PolicyDialogs.showPrivacy(this) }
        binding.rowPermissions.setOnClickListener { PolicyDialogs.showPermissions(this) }
        binding.rowCheckUpdate.setOnClickListener { UpdateChecker.check(this) }
        binding.rowDisclaimer.setOnClickListener { PolicyDialogs.showDisclaimer(this) }
        binding.rowRepo.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO_PAGE)))
        }
    }
}
