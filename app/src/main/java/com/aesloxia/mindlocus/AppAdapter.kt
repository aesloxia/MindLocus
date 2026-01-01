package com.aesloxia.mindlocus

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aesloxia.mindlocus.databinding.ItemAppBinding

class AppAdapter : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    private var apps = listOf<AppInfo>()

    fun setApps(newApps: List<AppInfo>) {
        apps = newApps
        notifyDataSetChanged()
    }

    fun getSelectedPackages(): Set<String> = apps.filter { it.isSelected }.map { it.packageName }.toSet()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) = holder.bind(apps[position])

    override fun getItemCount(): Int = apps.size

    inner class AppViewHolder(private val binding: ItemAppBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(app: AppInfo) {
            binding.apply {
                appName.text = app.name
                packageName.text = app.packageName
                appIcon.setImageDrawable(app.icon)
                
                appCheckbox.setOnCheckedChangeListener(null)
                appCheckbox.isChecked = app.isSelected
                
                root.setOnClickListener {
                    app.isSelected = !app.isSelected
                    appCheckbox.isChecked = app.isSelected
                }
                
                appCheckbox.setOnCheckedChangeListener { _, isChecked ->
                    app.isSelected = isChecked
                }
            }
        }
    }
}
