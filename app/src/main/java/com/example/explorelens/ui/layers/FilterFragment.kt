package com.example.explorelens.ui.layers

import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.explorelens.R
import com.example.explorelens.adapters.filterOptions.FilterOptionAdapter
import com.example.explorelens.data.model.FilterOption

class FilterFragment : DialogFragment() {

    private lateinit var filterOptionsRecyclerView: RecyclerView
    private lateinit var applyButton: Button
    private lateinit var clearAllButton: Button
    private lateinit var closeButton: ImageView
    private lateinit var adapter: FilterOptionAdapter
    private val allFilterOptions = mutableListOf(
        FilterOption("Restaurant"),
        FilterOption("Cafe"),
        FilterOption("Bar"),
        FilterOption("Bakery"),
        FilterOption("Hotel"),
        FilterOption("Pharmacy"),
        FilterOption("Gym"),
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.dialog_filter_options, container, false)
        filterOptionsRecyclerView = view.findViewById(R.id.filterOptionsRecyclerViewSlideRight)
        applyButton = view.findViewById(R.id.applyButtonSlideRight)
        clearAllButton = view.findViewById(R.id.clearAllButton)
        closeButton = view.findViewById(R.id.closeButtonSlideRight)
        return view
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setGravity(Gravity.END)
            attributes.windowAnimations = R.style.DialogAnimationSlideRight
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = FilterOptionAdapter(allFilterOptions) { option, isChecked ->
            option.isChecked = isChecked
        }
        filterOptionsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        filterOptionsRecyclerView.adapter = adapter

        closeButton.setOnClickListener {
            dismiss()
        }

        applyButton.setOnClickListener {
            val selectedFilters = adapter.getSelectedFilters()
            Log.d("FilterSideSheet", "Selected Filters: $selectedFilters")
            Toast.makeText(requireContext(), "Filters Applied: $selectedFilters", Toast.LENGTH_SHORT).show()
            dismiss()
            // You can pass the selected filters back to the ArActivity here using an interface or ViewModel
        }

        clearAllButton.setOnClickListener {
            allFilterOptions.forEach { it.isChecked = false }
            adapter.notifyDataSetChanged()
        }
    }
}