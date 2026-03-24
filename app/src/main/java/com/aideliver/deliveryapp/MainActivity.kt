package com.aideliver.deliveryapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
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

    private fun preprocessBitmap(uri: Uri): Bitmap {
        val original = BitmapFactory.decodeStream(contentResolver.openInputStream(uri))

        // 1단계: 해상도 축소 (노이즈 감소 + OCR 최적 크기)
        val maxWidth = 1200
        val working = if (original.width > maxWidth) {
            val ratio = maxWidth.toFloat() / original.width
            Bitmap.createScaledBitmap(original, maxWidth, (original.height * ratio).toInt(), true)
        } else original

        // 2단계: 그레이스케일 변환
        val gray = Bitmap.createBitmap(working.width, working.height, Bitmap.Config.ARGB_8888)
        Canvas(gray).drawBitmap(working, 0f, 0f, Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().also { it.setSaturation(0f) })
        })

        // 3단계: 대비 강화 + 이진화
        val binary = Bitmap.createBitmap(gray.width, gray.height, Bitmap.Config.ARGB_8888)
        Canvas(binary).drawBitmap(gray, 0f, 0f, Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                3f, 0f, 0f, 0f, -100f,
                0f, 3f, 0f, 0f, -100f,
                0f, 0f, 3f, 0f, -100f,
                0f, 0f, 0f, 1f,    0f
            )))
        })

        // 4단계: 침식(erosion) 2회 — 글자 획을 2픽셀 얇게
        // 두꺼운 획이 0→( 또는 0→G 로 오인식되는 현상 개선
        val w = binary.width
        val h = binary.height
        var pixels = IntArray(w * h)
        binary.getPixels(pixels, 0, w, 0, 0, w, h)
        repeat(2) {
            val eroded = pixels.copyOf()
            for (y in 1 until h - 1) {
                for (x in 1 until w - 1) {
                    val i = y * w + x
                    if (Color.red(pixels[i]) < 128) {
                        if (Color.red(pixels[i - 1]) > 200 ||
                            Color.red(pixels[i + 1]) > 200 ||
                            Color.red(pixels[i - w]) > 200 ||
                            Color.red(pixels[i + w]) > 200) {
                            eroded[i] = Color.WHITE
                        }
                    }
                }
            }
            pixels = eroded
        }
        val result = Bitmap.createBitmap(w, h, binary.config)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    private fun recognizeText(uri: Uri) {
        val bitmap = preprocessBitmap(uri)
        val image = InputImage.fromBitmap(bitmap, 0)
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
        // OCR 오인식 보정: 숫자 0으로 혼동되는 문자들을 0으로 치환
        val corrected = text
            .replace('O', '0').replace('o', '0')  // 영문 O
            .replace('Q', '0')                    // 영문 Q
            .replace('ㅇ', '0').replace('ㅎ', '0') // 한글 ㅇ, ㅎ
        // 숫자·하이픈·점 사이의 공백/줄바꿈 제거 (OCR 분리 출력 전체 대응)
        val normalized = corrected.replace(Regex("""(?<=[\d\-.])\s+(?=[\d\-.])"""), "")

        // 0으로 오인식되는 문자 집합 (앞자리 보정용)
        val zeroLikes = setOf('(', '[', '{', 'C', 'G', 'O', 'o', 'Q', 'ㅇ', 'ㅎ', '6')
        // 5로 오인식되는 문자 집합
        val fiveLikes = setOf('5', '6', 'S', 's')

        // 첫 글자에 1도 허용 (0을 1로 오인식하는 경우 대응), 자릿수로 검증
        val regex = Regex("""[01][\d\-\.]{7,13}\d""")
        return regex.findAll(normalized)
            .map { match ->
                val start = match.range.first
                val prefix = normalized.substring(maxOf(0, start - 2), start)
                Pair(prefix, match.value)
            }
            .filter { (_, raw) ->
                raw.replace(Regex("""[^\d]"""), "").length in 9..12
            }
            .map { (prefix, raw) ->
                val fixed = if (raw[0] == '1') "0" + raw.substring(1) else raw
                val phone = fixed.replace(".", "-")
                                 .replace(Regex("""-{2,}"""), "-")
                                 .trim('-')
                // 앞 2글자가 '05'의 오인식 패턴이면 앞에 05 보완
                // 예: (6→05, (5→05, G5→05, 65→05
                val prepend05 = prefix.length == 2 &&
                                prefix[0] in zeroLikes &&
                                prefix[1] in fiveLikes
                if (prepend05) "05$phone" else phone
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

    private fun getCleanNumber(): String? {
        val text = tvPhoneNumber.text.toString().trim()
        if (text.isEmpty() || text == "-") return null
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
