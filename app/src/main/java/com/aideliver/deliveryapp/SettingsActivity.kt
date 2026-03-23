package com.aideliver.deliveryapp

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "문자 내용 설정"

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val etTemplate = findViewById<EditText>(R.id.etSmsTemplate)

        etTemplate.setText(
            prefs.getString("sms_template", "주문이 접수되었습니다. 곧 배달해 드리겠습니다!")
        )

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

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
