package com.example.gr2

import android.content.Intent
import android.media.MediaPlayer
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import com.google.android.material.button.MaterialButton

class StartActivity : AppCompatActivity() {
    private lateinit var StartBtn: MaterialButton
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start)
        StartBtn = findViewById(R.id.btn_start)

        // Khởi tạo MediaPlayer với đường dẫn đến file MP3
        mediaPlayer = MediaPlayer.create(this, R.raw.start)
        mediaPlayer?.start()


        // Phát nhạc khi ứng dụng khởi chạy
        StartBtn.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Giải phóng nguồn tài nguyên khi ứng dụng kết thúc
        mediaPlayer?.release()
    }
}