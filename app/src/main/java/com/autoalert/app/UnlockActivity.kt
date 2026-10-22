
package com.autoalert.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import kotlin.math.roundToInt

class UnlockActivity : Activity() {

    private val NOIR = Color.rgb(35, 38, 45)

    private lateinit var root: LinearLayout
    private lateinit var banner: TextView
    private lateinit var statusText: TextView
    private lateinit var codeInput: EditText

    private val handler = Handler(Looper.getMainLooper())
    private val hideBanner = Runnable { banner.visibility = View.GONE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(247, 248, 250))
            setPadding(dp(20), dp(28), dp(20), dp(20))
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        }

        // BANDEAU DE MESSAGE
        banner = TextView(this).apply {
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(16), dp(18), dp(16))
            elevation = dp(8).toFloat()
            visibility = View.GONE
        }

        // TITRE ET STATUT
        root.addView(sectionTitle(getString(R.string.unlock_title)))

        val statusCard = card()

        statusCard.addView(TextView(this).apply {
            text = getString(R.string.unlock_intro)
            textSize = 15f
            setTextColor(NOIR)
        })

        statusText = TextView(this).apply {
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(10), 0, 0)
        }

        statusCard.addView(statusText)
        root.addView(statusCard)

        // CODE APPAREIL
        root.addView(sectionTitle(getString(R.string.unlock_device_label)))

        val deviceCard = card().apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        deviceCard.addView(
            TextView(this).apply {
                text = License.deviceCodeDisplay(this@UnlockActivity)
                textSize = 24f
                setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                setTextColor(NOIR)
                setTextIsSelectable(true)
            },
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        deviceCard.addView(
            grayButton(getString(R.string.btn_copy)) {
                copyCode()
            }.apply {
                textSize = 14f
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(44)
            )
        )

        root.addView(deviceCard)

        // OBTENIR UN CODE VIA MESSENGER
        root.addView(
            grayButton(getString(R.string.btn_get_code)) {
                openChat()
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
            ).apply {
                topMargin = dp(8)
            }
        )

        // SAISIE DU CODE D'ACTIVATION
        root.addView(
            sectionTitle(getString(R.string.unlock_enter_label))
        )

        val inputCard = card()

        codeInput = EditText(this).apply {
            hint = getString(R.string.unlock_code_hint)
            textSize = 18f

            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS

            setSingleLine(true)
            filters = arrayOf(InputFilter.LengthFilter(12))
            setPadding(dp(14), dp(12), dp(14), dp(12))

            background = roundedBackground(
                Color.WHITE,
                Color.rgb(220, 223, 229),
                12
            )
        }

        inputCard.addView(codeInput)

        inputCard.addView(
            grayButton(getString(R.string.btn_validate)) {
                validate()
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
            ).apply {
                topMargin = dp(12)
            }
        )

        root.addView(inputCard)

        // RETOUR
        root.addView(
            backButton(getString(R.string.btn_back)) {
                finish()
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
            ).apply {
                topMargin = dp(28)
            }
        )

        refreshStatus()

        // ÉCRAN ET BANDEAU
        val frame = FrameLayout(this)

        frame.addView(
            scroll,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        frame.addView(
            banner,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            ).apply {
                setMargins(dp(16), dp(34), dp(16), 0)
            }
        )

        setContentView(frame)
    }

    override fun onDestroy() {
        handler.removeCallbacks(hideBanner)
        super.onDestroy()
    }

    // VALIDER LE CODE
    private fun validate() {
        hideKeyboard()

        when (License.activate(this, codeInput.text.toString())) {
            0 -> {
                codeInput.setText("")
                refreshStatus()
                showBanner(
                    getString(R.string.msg_code_ok),
                    true
                )
            }

            2 -> showBanner(
                getString(R.string.msg_code_expired),
                false
            )

            else -> showBanner(
                getString(R.string.msg_code_invalid),
                false
            )
        }
    }

    // COPIER LE CODE APPAREIL SUR DEMANDE
    private fun copyCode() {
        val cm = getSystemService(
            Context.CLIPBOARD_SERVICE
        ) as ClipboardManager

        cm.setPrimaryClip(
            ClipData.newPlainText(
                "device",
                License.deviceCodeDisplay(this)
            )
        )

        showBanner(
            getString(R.string.msg_copied),
            true
        )
    }

    // OUVRIR DIRECTEMENT LA PAGE MESSENGER
    private fun openChat() {
        try {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(License.MESSENGER)
                )
            )
        } catch (_: Exception) {
            showBanner(
                getString(R.string.msg_code_invalid),
                false
            )
        }
    }

    // ACTUALISER LE STATUT DU DÉBLOCAGE
    private fun refreshStatus() {
        val until = License.unlockedUntil(this)

        if (until != null) {
            statusText.text = getString(
                R.string.unlock_status_on,
                License.formatDate(until)
            )
            statusText.setTextColor(Color.rgb(25, 145, 80))
        } else {
            statusText.text =
                getString(R.string.unlock_status_off)

            statusText.setTextColor(Color.rgb(200, 50, 60))
        }
    }

    // BANDEAU
    private fun showBanner(
        message: String,
        success: Boolean
    ) {
        banner.text = message

        banner.background = roundedBackground(
            if (success) {
                Color.rgb(25, 145, 80)
            } else {
                Color.rgb(200, 50, 60)
            },
            Color.TRANSPARENT,
            14
        )

        banner.visibility = View.VISIBLE

        handler.removeCallbacks(hideBanner)
        handler.postDelayed(hideBanner, 3000)
    }

    // MASQUER LE CLAVIER
    private fun hideKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE)
            as InputMethodManager)
            .hideSoftInputFromWindow(codeInput.windowToken, 0)

        codeInput.clearFocus()
        root.isFocusableInTouchMode = true
        root.requestFocus()
    }

    // BOUTON GRIS
    private fun grayButton(
        label: String,
        onClick: () -> Unit
    ): Button {
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

    // BOUTON RETOUR
    private fun backButton(
        label: String,
        onClick: () -> Unit
    ): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 16f
            setTextColor(NOIR)

            background = roundedBackground(
                Color.WHITE,
                NOIR,
                12
            )

            stateListAnimator = null
            setOnClickListener { onClick() }
        }
    }

    // CARTE
    private fun card(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(18),
                dp(16),
                dp(18),
                dp(16)
            )

            background = roundedBackground(
                Color.WHITE,
                Color.rgb(232, 234, 238),
                16
            )

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(6), 0, dp(8))
            }
        }
    }

    // TITRE DE SECTION
    private fun sectionTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.rgb(90, 95, 105))
            setPadding(
                dp(4),
                dp(18),
                dp(4),
                dp(5)
            )
        }
    }

    // FOND ARRONDI
    private fun roundedBackground(
        color: Int,
        strokeColor: Int,
        radius: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()

            if (strokeColor != Color.TRANSPARENT) {
                setStroke(dp(1), strokeColor)
            }
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density)
            .roundToInt()
}
