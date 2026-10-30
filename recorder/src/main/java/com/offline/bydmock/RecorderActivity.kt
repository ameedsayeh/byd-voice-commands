package com.offline.bydmock

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.offline.bydmock.databinding.ActivityRecorderBinding

/** Live view of recorded BYD calls. Polls the log so new calls appear without interaction. */
class RecorderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecorderBinding
    private val main = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            render()
            main.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CallLog.init(applicationContext)
        binding = ActivityRecorderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnClear.setOnClickListener { CallLog.clear(); render() }
    }

    override fun onResume() {
        super.onResume()
        main.post(refresh)
    }

    override fun onPause() {
        super.onPause()
        main.removeCallbacks(refresh)
    }

    private fun render() {
        val calls = CallLog.snapshot()
        binding.count.text = "${calls.size} calls"
        binding.log.text = if (calls.isEmpty()) "(waiting for BYD API calls…)" else calls.joinToString("\n")
    }
}
