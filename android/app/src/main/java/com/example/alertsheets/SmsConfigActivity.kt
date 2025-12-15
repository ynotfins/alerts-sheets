package com.example.alertsheets

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class SmsConfigActivity : AppCompatActivity() {

    private lateinit var adapter: SmsTargetAdapter
    private var targets: MutableList<SmsTarget> = mutableListOf()
    
    // For contact picker dialog
    private var currentNumberInput: EditText? = null
    private var currentNameInput: EditText? = null

    private val contactPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                processContactUri(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sms_config)

        targets = PrefsManager.getSmsConfigList(this).toMutableList()

        val recycler = findViewById<RecyclerView>(R.id.recycler_sms)
        recycler.layoutManager = LinearLayoutManager(this)
        
        adapter = SmsTargetAdapter(targets, 
            onEdit = { target -> showAddEditDialog(target) },
            onToggle = { target, isEnabled ->
                target.isEnabled = isEnabled
                save()
            }
        )
        recycler.adapter = adapter

        findViewById<FloatingActionButton>(R.id.fab_add).setOnClickListener {
            showAddEditDialog(null)
        }
    }

    private fun save() {
        PrefsManager.saveSmsConfigList(this, targets)
        adapter.updateData(targets)
    }
    
    private fun showAddEditDialog(target: SmsTarget?) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_sms, null)
        
        val inputNumber = view.findViewById<EditText>(R.id.input_number)
        val inputName = view.findViewById<EditText>(R.id.input_name)
        val inputFilter = view.findViewById<EditText>(R.id.input_filter)
        val checkCase = view.findViewById<CheckBox>(R.id.check_case)
        val btnPick = view.findViewById<ImageButton>(R.id.btn_pick_contact)
        
        // Populate if edit
        if (target != null) {
            inputNumber.setText(target.phoneNumber)
            inputName.setText(target.name)
            inputFilter.setText(target.filterText)
            checkCase.isChecked = target.isCaseSensitive
        }
        
        // Store refs for picker callback (simple way)
        currentNumberInput = inputNumber
        currentNameInput = inputName
        
        btnPick.setOnClickListener {
            // Check Perms
            if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                launchContactPicker()
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS), 101)
            }
        }
        
        val title = if (target == null) "Add SMS Target" else "Edit SMS Target"
        
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val number = inputNumber.text.toString().trim()
                if (number.isEmpty()) {
                    Toast.makeText(this, "Phone Number is required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                // Normalization (strip spaces, dashes, parens)
                // Just keep it relatively raw but maybe strip formatting chars for storage?
                // Actually keep it as user typed visuals, but norm in logic?
                // Let's just save as is for now, Receiver will norm.
                
                val name = inputName.text.toString().trim()
                val filter = inputFilter.text.toString() // allow empty
                val case = checkCase.isChecked
                
                if (target == null) {
                    // New
                    val newTarget = SmsTarget(
                        name = if(name.isEmpty()) "Unknown" else name,
                        phoneNumber = number,
                        filterText = filter,
                        isCaseSensitive = case
                    )
                    targets.add(newTarget)
                } else {
                    // Update
                    target.phoneNumber = number
                    target.name = name
                    target.filterText = filter
                    target.isCaseSensitive = case
                }
                save()
            }
            .setNegativeButton("Cancel", null)
            
        if (target != null) {
            dialog.setNeutralButton("Delete") { _, _ ->
                targets.remove(target)
                save()
            }
        }
            
        dialog.show()
    }
    
    private fun launchContactPicker() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        contactPickerLauncher.launch(intent)
    }
    
    private fun processContactUri(uri: Uri) {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        )
        
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(uri, projection, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val numIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                
                val number = if (numIndex >= 0) cursor.getString(numIndex) else ""
                val name = if (nameIndex >= 0) cursor.getString(nameIndex) else ""
                
                currentNumberInput?.setText(number)
                if (currentNameInput?.text.isNullOrEmpty()) {
                    currentNameInput?.setText(name)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to load contact", Toast.LENGTH_SHORT).show()
        } finally {
            cursor?.close()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
             launchContactPicker()
        }
    }
}
