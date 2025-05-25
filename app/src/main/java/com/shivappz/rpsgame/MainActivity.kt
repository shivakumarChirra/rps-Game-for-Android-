package com.shivappz.rpsgame

import android.app.AlertDialog
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.android.billingclient.api.*
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlin.random.Random

class MainActivity : AppCompatActivity(), PurchasesUpdatedListener {

    // --- Billing & Ads ---
    private lateinit var billingClient: BillingClient
    private var isAdRemoved = false

    private lateinit var resetButton: Button
    private lateinit var playButton: Button
    private lateinit var bannerAdView: AdView
    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null

    private val interstitialInterval = 5 * 60 * 1000L // 5 minutes in milliseconds
    private val handler = Handler(Looper.getMainLooper())
    private val interstitialRunnable = Runnable { showInterstitialAd() }

    // --- Game UI ---
    private lateinit var player1Image: ImageView
    private lateinit var player2Image: ImageView
    private lateinit var player1Overlay: ImageView
    private lateinit var player2Overlay: ImageView
    private lateinit var meScoreText: TextView
    private lateinit var youScoreText: TextView

    private var meScore = 0
    private var youScore = 0

    private val cards = arrayOf("rock", "paper", "scissor")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // --- Bind Views ---
        resetButton = findViewById(R.id.resetButton)
        playButton = findViewById(R.id.playButton)
        bannerAdView = findViewById(R.id.bannerAdView)

        player1Image = findViewById(R.id.player1Image)
        player2Image = findViewById(R.id.player2Image)
        player1Overlay = findViewById(R.id.player1Overlay)
        player2Overlay = findViewById(R.id.player2Overlay)
        meScoreText = findViewById(R.id.meScore)
        youScoreText = findViewById(R.id.youScore)

        // --- Initialize Ads ---
        MobileAds.initialize(this) {}

        setupBillingClient()
        loadBannerAd()
        loadInterstitialAd()
        loadRewardedAd()

        // Start interstitial timer
        handler.postDelayed(interstitialRunnable, interstitialInterval)

        // Show initial random images on start
        showRandomInitialImages()

        // --- Button Listeners ---

        playButton.setOnClickListener {
            playGame()
        }

        resetButton.setOnClickListener {
            if (isAdRemoved) {
                resetGameScores()
            } else {
                showRewardedAdBeforeReset()
            }
        }
    }

    // --- Billing Setup ---
    private fun setupBillingClient() {
        billingClient = BillingClient.newBuilder(this)
            .enablePendingPurchases()
            .setListener(this)
            .build()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    checkPurchasedItems()
                }
            }

            override fun onBillingServiceDisconnected() {
                // Implement retry logic if needed
            }
        })
    }

    private fun checkPurchasedItems() {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { billingResult, purchasesList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                purchasesList?.forEach { purchase ->
                    if (purchase.products.contains("remove_ads")) {
                        isAdRemoved = true
                        removeAds()
                    }
                }
            }
        }
    }

    private fun promptPurchaseDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Remove Ads")
        builder.setMessage("Purchase ₹9 to permanently remove ads?")
        builder.setPositiveButton("Buy") { dialog, _ ->
            dialog.dismiss()
            startPurchase()
        }
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
            // Player cancelled purchase, reset scores anyway
            resetGameScores()
        }
        builder.setCancelable(false)
        builder.show()
    }

    fun startPurchase() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("remove_ads")  // Your in-app product id here
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                val productDetails = productDetailsList[0]

                val billingFlowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(
                        listOf(
                            BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(productDetails)
                                .build()
                        )
                    )
                    .build()

                billingClient.launchBillingFlow(this, billingFlowParams)
            } else {
                Toast.makeText(this, "Unable to start purchase", Toast.LENGTH_SHORT).show()
                // fallback reset scores anyway
                resetGameScores()
            }
        }
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Toast.makeText(this, "Purchase canceled", Toast.LENGTH_SHORT).show()
            resetGameScores()
        } else {
            Toast.makeText(this, "Error during purchase: ${billingResult.debugMessage}", Toast.LENGTH_SHORT).show()
            resetGameScores()
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        isAdRemoved = true
                        removeAds()
                        Toast.makeText(this, "Ads Removed Successfully!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun removeAds() {
        bannerAdView.apply {
            destroy()
            visibility = AdView.GONE
        }
        interstitialAd = null
        rewardedAd = null
        handler.removeCallbacks(interstitialRunnable)
    }

    // --- Ads Loading and Showing ---
    private fun loadBannerAd() {
        if (isAdRemoved) return
        val adRequest = AdRequest.Builder().build()
        bannerAdView.loadAd(adRequest)
    }

    private fun loadInterstitialAd() {
        if (isAdRemoved) return
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(this, "ca-app-pub-2148527592793948/9097085762", adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    interstitialAd = null
                }
            })
    }

    private fun showInterstitialAd() {
        if (isAdRemoved) return
        interstitialAd?.let {
            it.show(this)
        }
        loadInterstitialAd() // Load next ad
        handler.postDelayed(interstitialRunnable, interstitialInterval)
    }

    private fun loadRewardedAd() {
        if (isAdRemoved) return
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(this, "ca-app-pub-2148527592793948/2678408267", adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    rewardedAd = null
                }
            })
    }

    private fun showRewardedAdBeforeReset() {
        if (rewardedAd == null) {
            Toast.makeText(this, "Rewarded Ad not available. Resetting game now.", Toast.LENGTH_SHORT).show()
            resetGameScores()
            loadRewardedAd()
            return
        }

        rewardedAd?.show(this) { rewardItem: RewardItem ->
            // Rewarded ad watched completely - show purchase popup now
            promptPurchaseDialog()
            loadRewardedAd() // Load next rewarded ad
        }
    }

    // --- Game Logic ---

    private fun showRandomInitialImages() {
        val choice1 = Random.nextInt(0, 3)
        val choice2 = Random.nextInt(0, 3)
        val res1 = resources.getIdentifier(cards[choice1], "drawable", packageName)
        val res2 = resources.getIdentifier(cards[choice2], "drawable", packageName)
        player1Image.setImageResource(res1)
        player2Image.setImageResource(res2)
        player1Overlay.visibility = ImageView.GONE
        player2Overlay.visibility = ImageView.GONE

        // Reset scores on app start
        meScore = 0
        youScore = 0
        meScoreText.text = meScore.toString()
        youScoreText.text = youScore.toString()
    }

    private fun playGame() {
        val player1Choice = Random.nextInt(0, 3)
        val player2Choice = Random.nextInt(0, 3)

        val player1Res = resources.getIdentifier(cards[player1Choice], "drawable", packageName)
        val player2Res = resources.getIdentifier(cards[player2Choice], "drawable", packageName)

        player1Image.setImageResource(player1Res)
        player2Image.setImageResource(player2Res)

        player1Overlay.visibility = ImageView.GONE
        player2Overlay.visibility = ImageView.GONE

        // Determine winner
        val result = determineWinner(player1Choice, player2Choice)

        when (result) {
            0 -> { // Tie
                playSound(R.raw.draw)
            }
            1 -> { // Player 1 wins
                player1Overlay.setImageResource(R.drawable.win_overlay)
                player1Overlay.visibility = ImageView.VISIBLE
                meScore++
                meScoreText.text = meScore.toString()
                playSound(R.raw.me_win)
            }
            2 -> { // Player 2 wins
                player2Overlay.setImageResource(R.drawable.win_overlay)
                player2Overlay.visibility = ImageView.VISIBLE
                youScore++
                youScoreText.text = youScore.toString()
                playSound(R.raw.you_win)
            }
        }
    }

    private fun determineWinner(p1: Int, p2: Int): Int {
        if (p1 == p2) return 0 // tie
        // rock=0, paper=1, scissor=2
        // rock beats scissor, paper beats rock, scissor beats paper
        return when (p1) {
            0 -> if (p2 == 2) 1 else 2
            1 -> if (p2 == 0) 1 else 2
            2 -> if (p2 == 1) 1 else 2
            else -> 0
        }
    }

    private fun playSound(soundResId: Int) {
        val mediaPlayer = MediaPlayer.create(this, soundResId)
        mediaPlayer.start()
        mediaPlayer.setOnCompletionListener { mp ->
            mp.release()
        }
    }

    private fun resetGameScores() {
        meScore = 0
        youScore = 0
        meScoreText.text = meScore.toString()
        youScoreText.text = youScore.toString()

        player1Overlay.visibility = ImageView.GONE
        player2Overlay.visibility = ImageView.GONE

        showRandomInitialImages()

        Toast.makeText(this, "Game reset!", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        bannerAdView.destroy()
        handler.removeCallbacks(interstitialRunnable)
    }
}
