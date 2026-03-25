package com.aideliver.deliveryapp

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private val LINK_PASSWORD = "6182"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "문자 내용 설정"

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val etTemplate = findViewById<EditText>(R.id.etSmsTemplate)
        val tvLinkUrl = findViewById<TextView>(R.id.tvLinkUrl)
        val btnEditLink = findViewById<Button>(R.id.btnEditLink)

        etTemplate.setText(
            prefs.getString("sms_template", "주문이 접수되었습니다. 곧 배달해 드리겠습니다!")
        )
        tvLinkUrl.text = prefs.getString("link_url", "")

        btnEditLink.setOnClickListener {
            showPasswordDialog(prefs, tvLinkUrl)
        }

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val message = etTemplate.text.toString().trim()
            if (message.isEmpty()) {
                Toast.makeText(this, "문자 내용을 입력해 주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.edit().putString("sms_template", message).apply()
            Toast.makeText(this, "저장되었습니다.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun showPasswordDialog(prefs: android.content.SharedPreferences, tvLinkUrl: TextView) {
        val etPassword = EditText(this).apply {
            hint = "비밀번호 입력"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setPadding(40, 20, 40, 20)
        }

        AlertDialog.Builder(this)
            .setTitle("비밀번호 확인")
            .setView(etPassword)
            .setPositiveButton("확인") { _, _ ->
                if (etPassword.text.toString() == LINK_PASSWORD) {
                    showLinkEditDialog(prefs, tvLinkUrl)
                } else {
                    Toast.makeText(this, "비밀번호가 틀렸습니다.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showLinkEditDialog(prefs: android.content.SharedPreferences, tvLinkUrl: TextView) {
        val etLink = EditText(this).apply {
            hint = "링크 주소 입력 (예: https://...)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_URI
            setText(prefs.getString("link_url", ""))
            setPadding(40, 20, 40, 20)
        }

        AlertDialog.Builder(this)
            .setTitle("링크 주소 수정")
            .setView(etLink)
            .setPositiveButton("저장") { _, _ ->
                val link = etLink.text.toString().trim()
                prefs.edit().putString("link_url", link).apply()
                tvLinkUrl.text = link
                Toast.makeText(this, "링크 주소가 저장되었습니다.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
