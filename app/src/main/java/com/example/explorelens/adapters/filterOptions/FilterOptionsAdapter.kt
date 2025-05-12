package com.example.explorelens.adapters.filterOptions

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.explorelens.R
import com.example.explorelens.data.model.FilterOption

class FilterOptionAdapter(
    private val options: List<FilterOption>,
    private val onOptionChecked: (FilterOption, Boolean) -> Unit
) : RecyclerView.Adapter<FilterOptionAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val optionTextView: TextView = itemView.findViewById(R.id.optionTextView)
        val optionCheckBox: CheckBox = itemView.findViewById(R.id.optionCheckBox)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_filter_option, parent, false)
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val currentOption = options[position]
        holder.optionTextView.text = currentOption.name
        holder.optionCheckBox.isChecked = currentOption.isChecked

        holder.optionCheckBox.setOnCheckedChangeListener { _, isChecked ->
            onOptionChecked(currentOption, isChecked)
        }
    }

    override fun getItemCount() = options.size

    fun getSelectedFilters(): List<String> {
        return options.filter { it.isChecked }.map { it.name }
    }
}