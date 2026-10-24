package com.autoalert.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.view.View
import android.widget.*
import kotlin.math.roundToInt

class PlusActivity : Activity() {

    private val NOIR = Color.rgb(35, 38, 45)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(247, 248, 250))
            setPadding(dp(20), dp(28), dp(20), dp(20))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        }

        // COMMENT ÇA MARCHE
        root.addView(sectionTitle(getString(R.string.how_title)))
        val howCard = card()
        howCard.addView(TextView(this).apply {
            text = Html.fromHtml(
                "<b>${getString(R.string.how_1_title)}</b> ${getString(R.string.how_1_body)}" +
                    "<br><br>" +
                    "<b>${getString(R.string.how_2_title)}</b> ${getString(R.string.how_2_body)}" +
                    "<br><br>" +
                    "<b>${getString(R.string.how_3_title)}</b> ${getString(R.string.how_3_body)}",
                Html.FROM_HTML_MODE_LEGACY
            )
            textSize = 15f
            setTextColor(NOIR)
            setLineSpacing(0f, 1.15f)
        })
        root.addView(howCard)

        // Espacement identique entre tous les boutons
        val gap = dp(16)
        fun buttonParams() = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(56)
        ).apply { topMargin = gap }

        // DÉBLOQUER LA SENSIBILITÉ (téléphone de la voiture seulement)
        val isCar = getSharedPreferences("p", MODE_PRIVATE).getString("role", "car") == "car"
        if (isCar) {
            root.addView(
                accentButton(Icons.withIcon(this, getString(R.string.btn_unlock), Icons.Kind.LOCK_OPEN, Color.WHITE, 18)) {
                    startActivity(Intent(this@PlusActivity, UnlockActivity::class.java))
                },
                buttonParams()
            )
        }

        // PARTAGER
        root.addView(grayButton(Icons.withIcon(this, getString(R.string.btn_share), Icons.Kind.SHARE, NOIR, 18)) { share() }, buttonParams())

        // CONTACT (Messenger)
        root.addView(grayButton(Icons.withIcon(this, getString(R.string.btn_contact), Icons.Kind.CHAT, NOIR, 18)) { openMessenger() }, buttonParams())

        // ESPACE LIBRE : pousse le bouton Retour en bas de l'écran
        root.addView(
            View(this),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        // RETOUR
        root.addView(
            backButton(getString(R.string.btn_back)) { finish() },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
            ).apply { topMargin = dp(24) }
        )

        setContentView(scroll)
    }

    // ACTIONS
    private fun share() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                getString(R.string.share_text) + "\n\n" +
                    "https://play.google.com/store/apps/details?id=$packageName"
            )
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_chooser)))
    }

    private fun openMessenger() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(License.MESSENGER)))
        } catch (_: Exception) {
        }
    }

    // OUTILS D'INTERFACE
    private fun grayButton(label: CharSequence, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 16f
            setTextColor(NOIR)
            background = roundedBackground(
                Color.rgb(235, 236, 239),
                Color.rgb(220, 223, 229),
                12
            )
            stateListAnimator = null
            setOnClickListener { onClick() }
        }
    }

    private fun accentButton(label: CharSequence, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = roundedBackground(
                Color.rgb(25, 145, 80),
                Color.TRANSPARENT,
                12
            )
            stateListAnimator = null
            setOnClickListener { onClick() }
        }
    }

    private fun backButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 16f
            setTextColor(NOIR)
            background = roundedBackground(Color.WHITE, NOIR, 12)
            stateListAnimator = null
            setOnClickListener { onClick() }
        }
    }

    private fun card(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = roundedBackground(Color.WHITE, Color.rgb(232, 234, 238), 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(6), 0, dp(8)) }
        }
    }

    private fun sectionTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.rgb(90, 95, 105))
            setPadding(dp(4), dp(18), dp(4), dp(5))
        }
    }

    private fun roundedBackground(color: Int, strokeColor: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (strokeColor != Color.TRANSPARENT) setStroke(dp(1), strokeColor)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()
}
