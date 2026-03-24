package com.aideliver.deliveryapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.telephony.SmsManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var ivReceipt: ImageView
    private lateinit var tvPhoneNumber: EditText
    private lateinit var tvStatus: TextView

    private var selectedPhoneNumber: String? = null
    private lateinit var photoUri: Uri

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            ivReceipt.setImageURI(null)
            ivReceipt.setImageURI(photoUri)
            tvStatus.text = "전화번호 인식 중..."
            recognizeText(photoUri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ivReceipt = findViewById(R.id.ivReceipt)
        tvPhoneNumber = findViewById(R.id.tvPhoneNumber)
        tvStatus = findViewById(R.id.tvStatus)

        requestPermissions()

        findViewById<Button>(R.id.btnCamera).setOnClickListener { openCamera() }
        findViewById<Button>(R.id.btnCall).setOnClickListener { makeCall() }
        findViewById<Button>(R.id.btnSms).setOnClickListener { sendSms() }
        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun requestPermissions() {
        val permissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS
        ).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 100)
        }
    }

    private fun openCamera() {
        val photoFile = File.createTempFile(
            "receipt_", ".jpg",
            getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        )
        photoUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", photoFile)
        takePictureLauncher.launch(photoUri)
    }

    private fun recognizeText(uri: Uri) {
        val image = InputImage.fromFilePath(this, uri)
        val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val phoneNumbers = extractPhoneNumbers(visionText.text)
                when {
                    phoneNumbers.isEmpty() -> {
                        selectedPhoneNumber = null
                        tvPhoneNumber.setText("-")
                        tvStatus.text = "전화번호를 찾을 수 없습니다"
                    }
                    phoneNumbers.size == 1 -> {
                        selectedPhoneNumber = phoneNumbers[0]
                        tvPhoneNumber.setText(phoneNumbers[0])
                        tvStatus.text = "전화번호 인식 완료"
                    }
                    else -> {
                        tvStatus.text = "전화번호 ${phoneNumbers.size}개 발견 - 선택해 주세요"
                        showPhoneNumberSelector(phoneNumbers)
                    }
                }
            }
            .addOnFailureListener { e ->
                tvStatus.text = "인식 실패"
                Toast.makeText(this, "인식 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun extractPhoneNumbers(text: String): List<String> {
        // OCR 오인식 보정: 영문자 O → 숫자 0 (열영수증에서 혼동 빈번)
        val corrected = text.replace('O', '0').replace('o', '0')
        // 숫자·하이픈·점 사이의 공백/줄바꿈 제거 (OCR 분리 출력 전체 대응)
        val normalized = corrected.replace(Regex("""(?<=[\d\-.])\s+(?=[\d\-.])"""), "")

        // 0으로 시작하고 숫자·하이픈·점으로 이루어진 블록 추출 후 자릿수로 검증
        val regex = Regex("""0[\d\-\.]{7,13}\d""")
        return regex.findAll(normalized)
            .map { it.value }
            .filter { raw ->
                // 실제 숫자만 9~12자리인 것만 허용
                raw.replace(Regex("""[^\d]"""), "").length in 9..12
            }
            .map { raw ->
                raw.replace(".", "-")
                   .replace(Regex("""-{2,}"""), "-")
                   .trim('-')
            }
            .distinct()
            .toList()
    }

    private fun showPhoneNumberSelector(phoneNumbers: List<String>) {
        AlertDialog.Builder(this)
            .setTitle("전화번호를 선택하세요")
            .setItems(phoneNumbers.toTypedArray()) { _, which ->
                selectedPhoneNumber = phoneNumbers[which]
                tvPhoneNumber.setText(phoneNumbers[which])
                tvStatus.text = "전화번호 선택 완료"
            }
            .setCancelable(true)
            .show()
    }

    private fun makeCall() {
        val number = tvPhoneNumber.text.toString().trim().takeIf { it.isNotEmpty() && it != "-" } ?: run {
            Toast.makeText(this, "전화번호를 입력하거나 주문전표를 촬영해 주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "통화 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")))
    }

    private fun sendSms() {
        val number = tvPhoneNumber.text.toString().trim().takeIf { it.isNotEmpty() && it != "-" } ?: run {
            Toast.makeText(this, "전화번호를 입력하거나 주문전표를 촬영해 주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "문자 전송 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val message = prefs.getString("sms_template", "주문이 접수되었습니다. 곧 배달해 드리겠습니다!")
            ?: "주문이 접수되었습니다. 곧 배달해 드리겠습니다!"

        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            smsManager.sendTextMessage(number, null, message, null, null)
            Toast.makeText(this, "문자가 전송되었습니다.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "전송 실패: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
