package net.dacworld.android.holyplacesofthelord.ui.profile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import net.dacworld.android.holyplacesofthelord.R
import net.dacworld.android.holyplacesofthelord.databinding.ItemProfileIconBinding
import net.dacworld.android.holyplacesofthelord.model.Profile

class ProfileIconAdapter(
    private val onIconSelected: (String) -> Unit
) : RecyclerView.Adapter<ProfileIconAdapter.IconViewHolder>() {

    private val icons: List<String> = Profile.AVAILABLE_ICONS
    private var selectedIconName: String = icons.first()

    fun setSelectedIcon(iconName: String) {
        val old = icons.indexOf(selectedIconName)
        selectedIconName = if (icons.contains(iconName)) iconName else icons.first()
        val new = icons.indexOf(selectedIconName)
        if (old >= 0) notifyItemChanged(old)
        if (new >= 0 && new != old) notifyItemChanged(new)
    }

    fun getSelectedIcon(): String = selectedIconName

    override fun getItemCount(): Int = icons.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IconViewHolder {
        val binding = ItemProfileIconBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return IconViewHolder(binding)
    }

    override fun onBindViewHolder(holder: IconViewHolder, position: Int) {
        holder.bind(icons[position], icons[position] == selectedIconName)
    }

    inner class IconViewHolder(private val binding: ItemProfileIconBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(iconName: String, isSelected: Boolean) {
            val resId = ProfileIcons.drawableResId(iconName)
            binding.iconImage.setImageResource(resId)
            binding.iconImage.isSelected = isSelected
            val tintColor = ContextCompat.getColor(binding.root.context, R.color.BaptismBlue)
            binding.iconImage.setColorFilter(tintColor)
            binding.root.setOnClickListener {
                setSelectedIcon(iconName)
                onIconSelected(iconName)
            }
        }
    }
}
