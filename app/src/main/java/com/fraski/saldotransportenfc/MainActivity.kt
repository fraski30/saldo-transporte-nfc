package com.fraski.saldotransportenfc

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.os.*
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.*
import com.fraski.saldotransportenfc.databinding.ActivityMainBinding
import com.fraski.saldotransportenfc.databinding.ItemHistoryBinding
import com.fraski.saldotransportenfc.db.*
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private lateinit var db: AppDatabase
    private val historyAdapter = HistoryAdapter()
    private lateinit var settings: SettingsManager
    private var currentCardUid: String? = null

    // CONFIGURATION
    private val TARGET_SECTOR = 9
    private val HEX_KEY_A = "99100225D83B"

    override fun onCreate(savedInstanceState: Bundle?) {
        settings = SettingsManager(this)
        settings.applyTheme(settings.themeMode)
        
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "mifare_db")
            .fallbackToDestructiveMigration()
            .build()

        setupToolbar()
        setupDrawer()
        setupRecyclerView()
        setupButtons()
        setupSettingsUI()
        
        if (settings.isBiometricEnabled) {
            checkBiometrics()
        }

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_MUTABLE
        )
    }

    private fun checkBiometrics() {
        val biometricManager = BiometricManager.from(this)
        if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS) {
            val executor = ContextCompat.getMainExecutor(this)
            val biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    finish() 
                }
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    // Success, allow access
                }
            })

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Seguridad")
                .setSubtitle("Identifíquese para usar la aplicación")
                .setNegativeButtonText("Cancelar")
                .build()

            biometricPrompt.authenticate(promptInfo)
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Consulta de saldo"
    }

    private fun setupDrawer() {
        val toggle = ActionBarDrawerToggle(this, binding.drawerLayout, binding.toolbar, 0, 0)
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        binding.navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_scan -> showView(binding.viewScan, "Consulta de saldo")
                R.id.nav_history -> {
                    showView(binding.viewHistory, "Historial")
                    loadHistory()
                }
                R.id.nav_charts -> {
                    showView(binding.viewCharts, "Gráfica de Gastos")
                    loadCharts()
                }
                R.id.nav_settings -> showView(binding.viewSettings, "Configuración")
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    private fun showView(view: View, title: String) {
        binding.viewScan.visibility = View.GONE
        binding.viewHistory.visibility = View.GONE
        binding.viewCharts.visibility = View.GONE
        binding.viewSettings.visibility = View.GONE
        view.visibility = View.VISIBLE
        supportActionBar?.title = title
    }

    private fun setupRecyclerView() {
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = historyAdapter
    }

    private fun setupButtons() {
        binding.btnDeleteAll.setOnClickListener {
            AlertDialog.Builder(this).setTitle("Borrar Historial").setMessage("¿Borrar todo?").setPositiveButton("Sí") { _, _ -> lifecycleScope.launch(Dispatchers.IO) { db.historyDao().deleteAll(); loadHistory() } }.setNegativeButton("No", null).show()
        }
        binding.btnDeleteSelected.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) { db.historyDao().deleteRecords(historyAdapter.getSelectedItems()); loadHistory() }
        }
        binding.btnEditName.setOnClickListener { showNicknameDialog() }
    }

    private fun setupSettingsUI() {
        binding.rgTheme.setOnCheckedChangeListener(null)
        when (settings.themeMode) {
            SettingsManager.THEME_LIGHT -> binding.rbLight.isChecked = true
            SettingsManager.THEME_DARK -> binding.rbDark.isChecked = true
            else -> binding.rbAuto.isChecked = true
        }
        binding.rgTheme.setOnCheckedChangeListener { _, id ->
            val newMode = when (id) {
                R.id.rbLight -> SettingsManager.THEME_LIGHT
                R.id.rbDark -> SettingsManager.THEME_DARK
                else -> SettingsManager.THEME_AUTO
            }
            if (newMode != settings.themeMode) {
                settings.themeMode = newMode
            }
        }
        binding.switchVibration.isChecked = settings.isVibrationEnabled
        binding.switchVibration.setOnCheckedChangeListener { _, isChecked -> settings.isVibrationEnabled = isChecked }
        binding.switchBiometrics.isChecked = settings.isBiometricEnabled
        binding.switchBiometrics.setOnCheckedChangeListener { _, isChecked -> settings.isBiometricEnabled = isChecked }
    }

    override fun onResume() {
        super.onResume()
        if (nfcAdapter == null) {
            binding.txtStatus.text = "NFC NO SOPORTADO"
        } else if (!nfcAdapter!!.isEnabled) {
            showNfcDisabledDialog()
        }
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
    }

    private fun showNfcDisabledDialog() {
        AlertDialog.Builder(this)
            .setTitle("NFC Desactivado")
            .setMessage("Para consultar el saldo es necesario activar el NFC.")
            .setPositiveButton("Activar") { _, _ -> startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (NfcAdapter.ACTION_TECH_DISCOVERED == intent.action || NfcAdapter.ACTION_TAG_DISCOVERED == intent.action) {
            val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            tag?.let { processTag(it) }
        }
    }

    private fun processTag(tag: Tag) {
        val mifare = MifareClassic.get(tag) ?: return
        val uid = bytesToHex(tag.id)
        currentCardUid = uid

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                mifare.connect()
                val key = hexStringToByteArray(HEX_KEY_A)
                if (mifare.authenticateSectorWithKeyA(TARGET_SECTOR, key)) {
                    val d1 = mifare.readBlock(mifare.sectorToBlock(TARGET_SECTOR) + 1)
                    val d2 = mifare.readBlock(mifare.sectorToBlock(TARGET_SECTOR) + 2)

                    if (d1.contentEquals(d2)) {
                        val rawValue = ByteBuffer.wrap(d1).order(ByteOrder.LITTLE_ENDIAN).int
                        val balance = (rawValue / 200.0 * 100.0).toInt() // Fórmula: (Valor / 200) * 100 = céntimos
                        val alias = db.historyDao().getNickname(uid) ?: "Tarjeta $uid"
                        
                        withContext(Dispatchers.Main) {
                            binding.txtBalance.text = String.format("%.2f €", balance / 100.0)
                            binding.txtStatus.text = "LECTURA CORRECTA"
                            binding.txtCardName.text = alias
                            binding.btnEditName.visibility = View.VISIBLE
                            if (settings.isVibrationEnabled) vibrate()
                            if (binding.viewHistory.visibility == View.VISIBLE || binding.viewSettings.visibility == View.VISIBLE) showView(binding.viewScan, "Consulta de saldo")
                        }
                        saveToHistory(uid, balance)
                    } else { updateStatusMain("ERROR DE INTEGRIDAD") }
                } else { updateStatusMain("ERROR DE AUTENTICACIÓN") }
            } catch (e: Exception) { updateStatusMain("ERROR: ${e.message}") } finally { try { mifare.close() } catch (e: Exception) {} }
        }
    }

    private fun vibrate() {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        v.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun showNicknameDialog() {
        val input = EditText(this).apply { setText(binding.txtCardName.text) }
        AlertDialog.Builder(this).setTitle("Apodo de Tarjeta").setView(input).setPositiveButton("Guardar") { _, _ ->
            val newName = input.text.toString()
            if (currentCardUid != null) {
                lifecycleScope.launch(Dispatchers.IO) {
                    db.historyDao().saveAlias(CardAlias(currentCardUid!!, newName))
                    withContext(Dispatchers.Main) { binding.txtCardName.text = newName }
                }
            }
        }.setNegativeButton("Cancelar", null).show()
    }

    private fun loadHistory() {
        lifecycleScope.launch(Dispatchers.IO) {
            val history = db.historyDao().getAll()
            withContext(Dispatchers.Main) {
                historyAdapter.submitList(history)
            }
        }
    }

    private fun loadCharts() {
        lifecycleScope.launch(Dispatchers.IO) {
            val allRecords = db.historyDao().getAllRaw()
            // Simple logic: Group by week (Simplified: group by day for visualization)
            val entries = allRecords.takeLast(7).mapIndexed { index, record -> BarEntry(index.toFloat(), record.balance / 100f) }
            withContext(Dispatchers.Main) {
                val dataSet = BarDataSet(entries, "Saldo por lectura")
                dataSet.color = ContextCompat.getColor(applicationContext, R.color.text_primary)
                binding.barChart.data = BarData(dataSet)
                // Fix for dark mode chart visibility
                binding.barChart.xAxis.textColor = ContextCompat.getColor(applicationContext, R.color.text_secondary)
                binding.barChart.axisLeft.textColor = ContextCompat.getColor(applicationContext, R.color.text_secondary)
                binding.barChart.legend.textColor = ContextCompat.getColor(applicationContext, R.color.text_secondary)
                binding.barChart.description.isEnabled = false
                binding.barChart.invalidate()
            }
        }
    }

    private suspend fun saveToHistory(uid: String, balance: Int) {
        val dateString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        if (db.historyDao().countSpecific(uid, balance, dateString) == 0) {
            db.historyDao().insert(HistoryRecord(uid = uid, balance = balance, date = dateString, timestamp = System.currentTimeMillis()))
        }
    }

    private suspend fun updateStatusMain(status: String) = withContext(Dispatchers.Main) { binding.txtStatus.text = status; if (status.contains("ERROR")) binding.txtBalance.text = "---" }
    private fun hexStringToByteArray(s: String) : ByteArray = ByteArray(s.length / 2) { ((Character.digit(s[it * 2], 16) shl 4) + Character.digit(s[it * 2 + 1], 16)).toByte() }
    private fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02X".format(it) }

    inner class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {
        private var items = emptyList<HistoryRecord>()
        private val selectedSet = mutableSetOf<Long>()
        fun submitList(newItems: List<HistoryRecord>) { items = newItems; selectedSet.clear(); notifyDataSetChanged() }
        fun getSelectedItems() = items.filter { selectedSet.contains(it.id) }
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = ViewHolder(ItemHistoryBinding.inflate(LayoutInflater.from(p.context), p, false))
        override fun onBindViewHolder(h: ViewHolder, pos: Int) {
            val item = items[pos]
            lifecycleScope.launch(Dispatchers.IO) {
                val alias = db.historyDao().getNickname(item.uid)
                withContext(Dispatchers.Main) { h.binding.itemUid.text = alias ?: "ID: ${item.uid}" }
            }
            h.binding.itemDate.text = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(item.timestamp))
            h.binding.itemBalance.text = String.format("%.2f €", item.balance / 100.0)
            h.binding.itemCheckbox.setOnCheckedChangeListener(null)
            h.binding.itemCheckbox.isChecked = selectedSet.contains(item.id)
            h.binding.itemCheckbox.setOnCheckedChangeListener { _, c -> if (c) selectedSet.add(item.id) else selectedSet.remove(item.id); binding.btnDeleteSelected.visibility = if (selectedSet.isNotEmpty()) View.VISIBLE else View.GONE }
        }
        override fun getItemCount() = items.size
        inner class ViewHolder(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
