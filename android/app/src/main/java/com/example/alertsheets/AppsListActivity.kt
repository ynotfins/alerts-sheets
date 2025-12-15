package com.example.alertsheets

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppsListActivity : AppCompatActivity() {

    private lateinit var adapter: AppsAdapter
    private var installedApps = mutableListOf<ApplicationInfo>()
    private var selectedApps = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apps_list) // Reuse or create new

        selectedApps = PrefsManager.getTargetApps(this).toMutableSet()

        val recycler = findViewById<RecyclerView>(R.id.recycler_all_apps)
        recycler.layoutManager = LinearLayoutManager(this)

        val progressBar = findViewById<ProgressBar>(R.id.progress_loading)

        adapter =
                AppsAdapter(installedApps, selectedApps) { pkg, isSelected ->
                    if (isSelected) selectedApps.add(pkg) else selectedApps.remove(pkg)
                    PrefsManager.saveTargetApps(this, selectedApps)
                }
        recycler.adapter = adapter

        // Load
        CoroutineScope(Dispatchers.IO).launch {
            val pm = packageManager
            val apps =
                    pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
                            // Filter removed to show ALL apps (System + User)
                            .sortedBy { it.loadLabel(pm).toString() }

            withContext(Dispatchers.Main) {
                installedApps.clear()
                installedApps.addAll(apps)
                adapter.notifyDataSetChanged()
                progressBar.visibility = View.GONE
            }
        }
    }
}
