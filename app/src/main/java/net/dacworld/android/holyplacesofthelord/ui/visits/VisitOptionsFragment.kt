package net.dacworld.android.holyplacesofthelord.ui.visits

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import net.dacworld.android.holyplacesofthelord.R
import net.dacworld.android.holyplacesofthelord.databinding.FragmentVisitOptionsBinding
import net.dacworld.android.holyplacesofthelord.databinding.ItemFilterOptionBinding // Import for item binding
import net.dacworld.android.holyplacesofthelord.ui.SharedVisitsViewModel
import net.dacworld.android.holyplacesofthelord.ui.VisitPlaceTypeFilter
import net.dacworld.android.holyplacesofthelord.util.ColorUtils // Your ColorUtils import

class VisitOptionsFragment : Fragment() {

    private var _binding: FragmentVisitOptionsBinding? = null
    private val binding get() = _binding!!

    private val sharedVisitsViewModel: SharedVisitsViewModel by activityViewModels()
    private lateinit var filterOptionsAdapter: FilterOptionsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVisitOptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.visitOptionsToolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }
        // You can customize the toolbar further if needed (e.g., set up a navigation icon if not done by theme)
        // (activity as? AppCompatActivity)?.setSupportActionBar(binding.visitOptionsToolbar)
        // (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        // (activity as? AppCompatActivity)?.supportActionBar?.setDisplayShowHomeEnabled(true)
    }


    private fun setupRecyclerView() {
        filterOptionsAdapter = FilterOptionsAdapter { selectedFilter ->
            sharedVisitsViewModel.setPlaceTypeFilter(selectedFilter)
            findNavController().popBackStack() // Navigate back after selection
        }

        binding.filterOptionsRecyclerView.apply {
            adapter = filterOptionsAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        // Submit the list of all possible filter types
        filterOptionsAdapter.submitList(VisitPlaceTypeFilter.values().toList())
    }

    private fun observeViewModel() {
        // Observe the current filter to update the adapter's selected state
        sharedVisitsViewModel.selectedPlaceTypeFilter.observe(viewLifecycleOwner) { currentFilter ->
            filterOptionsAdapter.setCurrentFilter(currentFilter)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.filterOptionsRecyclerView.adapter = null // Important to prevent memory leaks
        _binding = null
    }
}

// --- RecyclerView Adapter for Filter Options ---

class FilterOptionsAdapter(
    private val onFilterSelected: (VisitPlaceTypeFilter) -> Unit
) : ListAdapter<VisitPlaceTypeFilter, FilterOptionsAdapter.FilterOptionViewHolder>(FilterOptionDiffCallback()) {

    private var currentFilter: VisitPlaceTypeFilter? = null

    fun setCurrentFilter(filter: VisitPlaceTypeFilter) {
        val oldFilter = currentFilter
        currentFilter = filter
        // Notify changes for the old and new selected items to update RadioButton state
        oldFilter?.let { notifyItemChanged(getItemPosition(it)) }
        currentFilter?.let { notifyItemChanged(getItemPosition(it)) }
    }

    private fun getItemPosition(filter: VisitPlaceTypeFilter): Int {
        return currentList.indexOf(filter)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilterOptionViewHolder {
        val binding = ItemFilterOptionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FilterOptionViewHolder(binding, onFilterSelected)
    }

    override fun onBindViewHolder(holder: FilterOptionViewHolder, position: Int) {
        val filterOption = getItem(position)
        holder.bind(filterOption, filterOption == currentFilter)
    }

    inner class FilterOptionViewHolder(
        private val binding: ItemFilterOptionBinding,
        private val onFilterSelected: (VisitPlaceTypeFilter) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(filterOption: VisitPlaceTypeFilter, isSelected: Boolean) {
            binding.filterOptionName.text = itemView.context.getString(filterOption.displayNameResource)
            binding.filterOptionRadioButton.isChecked = isSelected

            val context = itemView.context
            // Use the typeCode from VisitPlaceTypeFilter directly with your ColorUtils method
            val color = ColorUtils.getTextColorForTempleType(context, filterOption.typeCode)
            binding.filterOptionName.setTextColor(color)

            itemView.setOnClickListener {
                onFilterSelected(filterOption)
            }
        }
    }
}

class FilterOptionDiffCallback : DiffUtil.ItemCallback<VisitPlaceTypeFilter>() {
    override fun areItemsTheSame(oldItem: VisitPlaceTypeFilter, newItem: VisitPlaceTypeFilter): Boolean {
        return oldItem == newItem // Enums are compared by identity
    }

    override fun areContentsTheSame(oldItem: VisitPlaceTypeFilter, newItem: VisitPlaceTypeFilter): Boolean {
        return oldItem == newItem // Enums are value types, content is the same if items are the same
    }
}
