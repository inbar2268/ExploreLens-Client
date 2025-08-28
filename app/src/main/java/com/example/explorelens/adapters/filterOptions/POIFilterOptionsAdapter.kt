package com.example.explorelens.adapters.filterOptions

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.explorelens.R
import com.example.explorelens.data.model.siteHistory.FilterOption

class FilterOptionAdapter(
    private val options: MutableList<FilterOption>,
    private val onOptionChecked: (FilterOption, Boolean) -> Unit
) : RecyclerView.Adapter<FilterOptionAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        Log.d("FilterAdapter", "onCreateViewHolder called")
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_filter_option, parent, false)
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val option = options[position]
        holder.bind(option)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val iconImageView: ImageView = itemView.findViewById(R.id.optionIcon)
        val textView: TextView = itemView.findViewById(R.id.optionTextView)
        val checkBox: CheckBox = itemView.findViewById(R.id.optionCheckBox)

        fun bind(option: FilterOption) {
            textView.text = option.name
            option.iconResId?.let { iconImageView.setImageResource(it) }
            checkBox.isChecked = option.isChecked

            // Remove any existing listeners to prevent conflicts
            checkBox.setOnCheckedChangeListener(null)
            itemView.setOnClickListener(null)

            itemView.setOnClickListener {
                toggleOption(option)
            }
            checkBox.setOnClickListener {
                toggleOption(option)
            }
        }

        private fun toggleOption(option: FilterOption) {
            option.isChecked = !option.isChecked
            checkBox.isChecked = option.isChecked
            onOptionChecked(option, option.isChecked)
        }
    }

    override fun getItemCount() = options.size

    fun getCurrentChoices(): List<String> {
        return options.filter { it.isChecked }.map { it.name }
    }
}