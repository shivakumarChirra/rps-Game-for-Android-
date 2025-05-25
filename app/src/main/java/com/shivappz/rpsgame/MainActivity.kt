package com.shivappz.rpsgame

import android.media.MediaPlayer
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var player1Image: ImageView
    private lateinit var player2Image: ImageView
    private lateinit var player1Overlay: ImageView
    private lateinit var player2Overlay: ImageView
    private lateinit var meScoreText: TextView
    private lateinit var youScoreText: TextView
    private lateinit var adView: AdView

    private var meScore = 0
    private var youScore = 0

    private val cards = arrayOf("rock", "paper", "scissor")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Mobile Ads SDK
        MobileAds.initialize(this) {}

        // Bind views
        player1Image = findViewById(R.id.player1Image)
        player2Image = findViewById(R.id.player2Image)
        player1Overlay = findViewById(R.id.player1Overlay)
        player2Overlay = findViewById(R.id.player2Overlay)
        meScoreText = findViewById(R.id.meScore)
        youScoreText = findViewById(R.id.youScore)
        adView = findViewById(R.id.adView)  // Initialize banner ad view

        // Load an ad into the AdView
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)

        val playButton: Button = findViewById(R.id.playButton)
        playButton.setOnClickListener {
            playGame()
        }

        val resetButton: Button = findViewById(R.id.resetButton)
        resetButton.setOnClickListener {
            meScore = 0
            youScore = 0
            meScoreText.text = meScore.toString()
            youScoreText.text = youScore.toString()

            player1Overlay.visibility = ImageView.GONE
            player2Overlay.visibility = ImageView.GONE

            player1Image.setImageResource(R.drawable.rock)
            player2Image.setImageResource(R.drawable.paper)
        }
    }

    private fun playGame() {
        val player1Choice = Random.nextInt(0, 3)
        val player2Choice = Random.nextInt(0, 3)

        val res1 = resources.getIdentifier(cards[player1Choice], "drawable", packageName)
        val res2 = resources.getIdentifier(cards[player2Choice], "drawable", packageName)

        player1Image.setImageResource(res1)
        player2Image.setImageResource(res2)

        player1Overlay.visibility = ImageView.GONE
        player2Overlay.visibility = ImageView.GONE

        if ((player1Choice == 0 && player2Choice == 2) ||
            (player1Choice == 1 && player2Choice == 0) ||
            (player1Choice == 2 && player2Choice == 1)
        ) {
            meScore++
            meScoreText.text = meScore.toString()
            player1Overlay.visibility = ImageView.VISIBLE
            playSound(R.raw.me_win)
        } else if ((player2Choice == 0 && player1Choice == 2) ||
            (player2Choice == 1 && player1Choice == 0) ||
            (player2Choice == 2 && player1Choice == 1)
        ) {
            youScore++
            youScoreText.text = youScore.toString()
            player2Overlay.visibility = ImageView.VISIBLE
            playSound(R.raw.you_win)
        } else {
            playSound(R.raw.draw)
        }
    }

    private fun playSound(soundRes: Int) {
        val mediaPlayer = MediaPlayer.create(this, soundRes)
        mediaPlayer?.start()
        mediaPlayer?.setOnCompletionListener {
            it.release()
        }
    }
}
