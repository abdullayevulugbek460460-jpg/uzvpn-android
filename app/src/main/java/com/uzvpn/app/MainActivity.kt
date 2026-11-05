package com.uzvpn.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.VpnService
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.BufferedReader
import java.io.StringReader
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var backend: GoBackend
    private lateinit var configField: EditText
    private lateinit var status: TextView
    private lateinit var action: Button
    private var active = false
    private var pendingConfig: Config? = null

    private val tunnel = object : Tunnel {
        override fun getName() = "UZVPN"
        override fun onStateChange(newState: Tunnel.State) {
            runOnUiThread { active = newState == Tunnel.State.UP; render() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        backend = GoBackend(applicationContext)
        createUi()
    }

    private fun createUi() {
        val bg = Color.rgb(9, 15, 29)
        val card = Color.rgb(20, 31, 50)
        val white = Color.rgb(240, 245, 250)
        val grey = Color.rgb(150, 165, 185)
        val green = Color.rgb(40, 220, 160)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 30, 24, 24)
            setBackgroundColor(bg)
        }
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        body.addView(TextView(this).apply {
            text = "UZ VPN"; textSize = 32f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(white); gravity = Gravity.CENTER
        }, lp())
        body.addView(TextView(this).apply {
            text = "XAVFSIZ • SODDA • REKLAMASIZ"; textSize = 12f
            setTextColor(grey); gravity = Gravity.CENTER; setPadding(0, 8, 0, 30)
        }, lp())
        val statusCard = LinearLayout(this).apply {
            gravity = Gravity.CENTER; setPadding(16, 25, 16, 25); setBackgroundColor(card)
        }
        status = TextView(this).apply {
            text = "● Ulanmagan"; textSize = 20f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(grey); gravity = Gravity.CENTER
        }
        statusCard.addView(status, lp())
        body.addView(statusCard, lp().apply { bottomMargin = 18 })
        action = Button(this).apply {
            text = "VPN'ni ulash"; isAllCaps = false; setTextColor(bg)
            backgroundTintList = android.content.res.ColorStateList.valueOf(green)
            setOnClickListener { connectOrDisconnect() }
        }
        body.addView(action, lp().apply { bottomMargin = 25 })
        body.addView(TextView(this).apply {
            text = "WireGuard konfiguratsiyasi"; textSize = 18f
            typeface = Typeface.DEFAULT_BOLD; setTextColor(white); setPadding(0, 0, 0, 8)
        }, lp())
        body.addView(TextView(this).apply {
            text = "Serveringiz bergan .conf fayl matnini joylang. Haqiqiy VPN ulanishi uchun ishlaydigan WireGuard serveri kerak."
            textSize = 13f; setTextColor(grey); setPadding(0, 0, 0, 12)
        }, lp())
        configField = EditText(this).apply {
            hint = "[Interface] ... [Peer] ..."; setHintTextColor(grey); setTextColor(white)
            textSize = 13f; minLines = 9; gravity = Gravity.TOP or Gravity.START
            setPadding(14, 14, 14, 14); setBackgroundColor(card)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        body.addView(configField, lp().apply { bottomMargin = 16 })
        body.addView(TextView(this).apply {
            text = "Maxfiylik: konfiguratsiya ilova tomonidan doimiy saqlanmaydi. PrivateKey va server ma'lumotlarini begonalarga yubormang. VPN server operatori trafik metama'lumotlarini ko'rishi mumkin."
            textSize = 12f; setTextColor(grey)
        }, lp())
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun connectOrDisconnect() {
        if (active) {
            action.isEnabled = false
            executor.execute {
                try { backend.setState(tunnel, Tunnel.State.DOWN, null) }
                catch (e: Exception) { showError("Uzishda xatolik: ${e.message}") }
                finally { runOnUiThread { active = false; render() } }
            }
            return
        }
        val raw = configField.text.toString().trim()
        if (raw.isEmpty()) { toast("Avval WireGuard konfiguratsiyasini kiriting."); return }
        val parsed = try { Config.parse(BufferedReader(StringReader(raw))) }
        catch (e: Exception) { toast("Konfiguratsiya formati xato: ${e.message}"); return }
        pendingConfig = parsed
        AlertDialog.Builder(this)
            .setTitle("VPN ruxsati")
            .setMessage("Android VPN ruxsatini so'raydi. Faqat o'zingiz ishonadigan WireGuard serveridan foydalaning.")
            .setNegativeButton("Bekor qilish", null)
            .setPositiveButton("Davom etish") { _, _ ->
                val intent = VpnService.prepare(this)
                if (intent != null) startActivityForResult(intent, REQUEST_VPN) else startTunnel()
            }.show()
    }

    @Deprecated("Required for compatibility with Android VPN permission flow")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_VPN) {
            if (resultCode == RESULT_OK) startTunnel() else toast("VPN ruxsati berilmadi.")
        }
    }

    private fun startTunnel() {
        val config = pendingConfig ?: return
        status.text = "◌ Ulanmoqda..."
        action.isEnabled = false
        executor.execute {
            try {
                backend.setState(tunnel, Tunnel.State.UP, config)
                runOnUiThread { active = true; render() }
            } catch (e: Exception) {
                showError("Ulanish amalga oshmadi: ${e.message}")
                runOnUiThread { status.text = "● Ulanmadi"; action.isEnabled = true }
            }
        }
    }

    private fun showError(message: String) = runOnUiThread { toast(message) }
    private fun toast(message: String) = runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    private fun render() {
        status.text = if (active) "● Himoyalangan ulanish faol" else "● Ulanmagan"
        status.setTextColor(if (active) Color.rgb(40, 220, 160) else Color.rgb(150, 165, 185))
        action.text = if (active) "VPN'ni uzish" else "VPN'ni ulash"
        action.isEnabled = true
    }
    private fun lp() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    override fun onDestroy() { executor.shutdown(); super.onDestroy() }
    companion object { private const val REQUEST_VPN = 4101 }
}
