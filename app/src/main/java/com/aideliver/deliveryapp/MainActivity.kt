package com.aideliver.deliveryapp

import android.Manifest
import android.content.ClipboardManager
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
import com.google.mlkit.nl.entityextraction.Entity
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractionParams
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var ivReceipt: ImageView
    private lateinit var tvPhoneNumber: EditText
    private lateinit var tvAddress: EditText
    private lateinit var tvStatus: TextView

    private var selectedPhoneNumber: String? = null
    private lateinit var photoUri: Uri
    private var waitingForClipboard = false

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
        tvAddress = findViewById(R.id.tvAddress)
        tvStatus = findViewById(R.id.tvStatus)

        requestPermissions()

        findViewById<Button>(R.id.btnCamera).setOnClickListener { openCamera() }
        findViewById<Button>(R.id.btnCall).setOnClickListener { makeCall() }
        findViewById<Button>(R.id.btnSms).setOnClickListener { sendSms() }
        findViewById<Button>(R.id.btnNavi).setOnClickListener { openNavi() }
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

    override fun onResume() {
        super.onResume()
        if (waitingForClipboard) {
            waitingForClipboard = false
            readFromClipboard()
        }
    }

    private fun openCamera() {
        val lensIntent = Intent().setClassName(
            "com.google.android.googlequicksearchbox",
            "com.google.android.apps.search.lens.LensActivity"
        )
        try {
            waitingForClipboard = true
            startActivity(lensIntent)
        } catch (e: Exception) {
            waitingForClipboard = false
            Toast.makeText(this, "구글 렌즈를 찾을 수 없어 기본 카메라를 사용합니다.", Toast.LENGTH_SHORT).show()
            openBuiltinCamera()
        }
    }

    private fun openBuiltinCamera() {
        val photoFile = File.createTempFile(
            "receipt_", ".jpg",
            getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        )
        photoUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", photoFile)
        takePictureLauncher.launch(photoUri)
    }

    private fun readFromClipboard() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
        if (text.isNullOrBlank()) {
            tvStatus.text = "클립보드에 텍스트가 없습니다"
            return
        }
        tvStatus.text = "번호/주소 분석 중..."
        val lines = text.lines().filter { it.isNotBlank() }
        extractWithEntityExtraction(text, lines) { phones, address ->
            when {
                phones.isEmpty() -> {
                    selectedPhoneNumber = null
                    tvPhoneNumber.setText("")
                    tvStatus.text = "전화번호를 찾을 수 없습니다"
                }
                phones.size == 1 -> {
                    selectedPhoneNumber = phones[0]
                    tvPhoneNumber.setText(phones[0])
                    tvStatus.text = "전화번호 인식 완료"
                }
                else -> {
                    tvStatus.text = "전화번호 ${phones.size}개 발견 - 선택해 주세요"
                    showPhoneNumberSelector(phones)
                }
            }
            tvAddress.setText(address ?: "")
        }
    }

    private fun recognizeText(uri: Uri) {
        val image = InputImage.fromFilePath(this, uri)
        val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val lines = visionText.textBlocks
                    .flatMap { it.lines }
                    .map { it.text }
                val fullText = visionText.text

                tvStatus.text = "번호/주소 분석 중..."
                extractWithEntityExtraction(fullText, lines) { phones, address ->
                    when {
                        phones.isEmpty() -> {
                            selectedPhoneNumber = null
                            tvPhoneNumber.setText("")
                            tvStatus.text = "전화번호를 찾을 수 없습니다"
                        }
                        phones.size == 1 -> {
                            selectedPhoneNumber = phones[0]
                            tvPhoneNumber.setText(phones[0])
                            tvStatus.text = "전화번호 인식 완료"
                        }
                        else -> {
                            tvStatus.text = "전화번호 ${phones.size}개 발견 - 선택해 주세요"
                            showPhoneNumberSelector(phones)
                        }
                    }
                    tvAddress.setText(address ?: "")
                }
            }
            .addOnFailureListener { e ->
                tvStatus.text = "인식 실패"
                Toast.makeText(this, "인식 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun extractWithEntityExtraction(
        fullText: String,
        lines: List<String>,
        onComplete: (phones: List<String>, address: String?) -> Unit
    ) {
        val extractor = EntityExtraction.getClient(
            EntityExtractorOptions.Builder(EntityExtractorOptions.KOREAN).build()
        )

        extractor.downloadModelIfNeeded()
            .addOnSuccessListener {
                val params = EntityExtractionParams.Builder(fullText)
                    .setEntityTypesFilter(setOf(Entity.TYPE_PHONE, Entity.TYPE_ADDRESS))
                    .build()

                extractor.annotate(params)
                    .addOnSuccessListener { annotations ->
                        val phones = annotations
                            .filter { ann -> ann.entities.any { it.type == Entity.TYPE_PHONE } }
                            .map { ann ->
                                val digits = ann.annotatedText.replace(Regex("""[^\d]"""), "")
                                normalizePhone(digits)
                            }
                            .filter { it.isNotEmpty() }
                            .distinct()

                        val address = annotations
                            .firstOrNull { ann -> ann.entities.any { it.type == Entity.TYPE_ADDRESS } }
                            ?.annotatedText

                        // Entity Extraction 결과 없으면 정규식으로 폴백
                        val finalPhones = if (phones.isNotEmpty()) phones else extractPhoneNumbers(lines)
                        val finalAddress = if (!address.isNullOrEmpty()) address else extractAddress(lines)

                        onComplete(finalPhones, finalAddress)
                    }
                    .addOnFailureListener {
                        onComplete(extractPhoneNumbers(lines), extractAddress(lines))
                    }
            }
            .addOnFailureListener {
                // 모델 다운로드 실패 → 정규식으로 폴백
                onComplete(extractPhoneNumbers(lines), extractAddress(lines))
            }
    }

    private fun normalizePhone(digits: String): String {
        if (digits.length !in 9..12) return ""
        val d = if (digits.startsWith("1")) "0$digits" else digits
        return when (d.length) {
            11 -> "${d.substring(0,3)}-${d.substring(3,7)}-${d.substring(7)}"
            10 -> "${d.substring(0,3)}-${d.substring(3,6)}-${d.substring(6)}"
            9  -> "${d.substring(0,2)}-${d.substring(2,5)}-${d.substring(5)}"
            else -> d
        }
    }

    private fun extractAddress(lines: List<String>): String? {
        // 도로명(로/길) + 건물번호가 있는 줄에서, 도로명 글자부터 끝까지만 반환
        val roadRegex = Regex("""[가-힣]+(대로|로|길)\s*\d+(-\d+)?.*""")

        for (line in lines) {
            val match = roadRegex.find(line) ?: continue
            return match.value.trim()
        }

        // 인접 두 줄 합쳐서도 탐지 (도로명이 줄 경계에서 분리된 경우)
        for (i in 0 until lines.size - 1) {
            val combined = lines[i] + " " + lines[i + 1]
            val match = roadRegex.find(combined) ?: continue
            return match.value.trim()
        }

        return null
    }

    private fun openNavi() {
        val address = tvAddress.text.toString().trim()
        if (address.isEmpty()) {
            Toast.makeText(this, "주소를 입력하거나 주문전표를 촬영해 주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val encodedAddress = Uri.encode(address)

        // 카카오맵 앱으로 주소 검색 실행
        val kakaoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("kakaomap://search?q=$encodedAddress"))
        if (packageManager.resolveActivity(kakaoIntent, 0) != null) {
            startActivity(kakaoIntent)
            return
        }

        // 카카오맵 미설치 시 기본 지도 앱 사용
        val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encodedAddress"))
        if (packageManager.resolveActivity(geoIntent, 0) != null) {
            startActivity(geoIntent)
        } else {
            Toast.makeText(this, "지도 앱이 설치되어 있지 않습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun correctOcr(text: String): String {
        return text
            .replace('O', '0').replace('o', '0')
            .replace('Q', '0').replace('G', '0')
            .replace('(', '0').replace('[', '0').replace('{', '0')
            .replace('ㅇ', '0').replace('ㅎ', '0')
    }

    private fun extractPhoneNumbers(lines: List<String>): List<String> {
        val zeroLikes = setOf('(', '[', '{', 'C', 'G', 'O', 'o', 'Q', 'ㅇ', 'ㅎ', '6')
        val fiveLikes  = setOf('5', '6', 'S', 's')
        val regex = Regex("""[01][\d\-\.]{7,13}\d""")

        fun findInText(text: String): List<String> {
            val corrected = correctOcr(text)
            val normalized = corrected.replace(Regex("""(?<=[\d\-.])\s+(?=[\d\-.])"""), "")
            return regex.findAll(normalized)
                .map { match ->
                    val start = match.range.first
                    val prefix = normalized.substring(maxOf(0, start - 2), start)
                    Pair(prefix, match.value)
                }
                .filter { (_, raw) -> raw.replace(Regex("""[^\d]"""), "").length in 9..12 }
                .map { (prefix, raw) ->
                    val fixed = if (raw[0] == '1') "0" + raw.substring(1) else raw
                    val phone = fixed.replace(".", "-")
                                     .replace(Regex("""-{2,}"""), "-")
                                     .trim('-')
                    val prepend05 = prefix.length == 2 &&
                                    prefix[0] in zeroLikes &&
                                    prefix[1] in fiveLikes
                    if (prepend05) "05$phone" else phone
                }
                .toList()
        }

        val results = mutableListOf<String>()

        // 1. 각 줄 단독 검색
        lines.forEach { results += findInText(it) }

        // 2. 인접한 두 줄 합쳐서 검색 (번호가 줄 경계에서 분리된 경우 대응)
        lines.zipWithNext { a, b -> results += findInText(a + b) }

        return results.distinct()
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

    private fun getCleanNumber(): String? {
        val text = tvPhoneNumber.text.toString().trim()
        if (text.isEmpty()) return null
        return text
    }

    private fun makeCall() {
        val number = getCleanNumber() ?: run {
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
        val number = getCleanNumber() ?: run {
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
        val template = prefs.getString("sms_template", "주문이 접수되었습니다. 곧 배달해 드리겠습니다!")
            ?: "주문이 접수되었습니다. 곧 배달해 드리겠습니다!"
        val linkUrl = prefs.getString("link_url", "") ?: ""
        val message = if (linkUrl.isNotEmpty()) "$template\n$linkUrl" else template

        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            val parts = smsManager.divideMessage(message)
            if (parts.size == 1) {
                smsManager.sendTextMessage(number, null, message, null, null)
            } else {
                smsManager.sendMultipartTextMessage(number, null, parts, null, null)
            }
            Toast.makeText(this, "문자가 전송되었습니다.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "전송 실패: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
