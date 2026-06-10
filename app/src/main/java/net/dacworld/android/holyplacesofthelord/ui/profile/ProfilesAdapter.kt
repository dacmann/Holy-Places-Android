package net.dacworld.android.holyplacesofthelord.ui.profile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import net.dacworld.android.holyplacesofthelord.databinding.ItemProfileBinding
import net.dacworld.android.holyplacesofthelord.model.Profile

class ProfilesAdapter(
    private val onProfileClick: (Profile) -> Unit
) : ListAdapter<ProfilesAdapter.ProfileItem, ProfilesAdapter.ProfileViewHolder>(DIFF_CALLBACK) {

    data class ProfileItem(
        val profile: Profile,
        val visitCount: Int,
        val isActive: Boolean
    )

    fun submitProfileItems(items: List<ProfileItem>) {
        submitList(items)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileViewHolder {
        val binding = ItemProfileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ProfileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ProfileViewHolder(private val binding: ItemProfileBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ProfileItem) {
            val profile = item.profile
            binding.profileName.text = profile.name

            val visitWord = if (item.visitCount == 1) "visit" else "visits"
            val defaultLabel = if (profile.isDefault) " · Default" else ""
            binding.profileSubtitle.text = "${item.visitCount} $visitWord$defaultLabel"

            val iconRes = ProfileIcons.drawableResId(profile.iconName)
            binding.profileIcon.setImageResource(iconRes)

            binding.profileActiveCheck.visibility =
                if (item.isActive) View.VISIBLE else View.GONE

            binding.root.setOnClickListener { onProfileClick(profile) }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ProfileItem>() {
            override fun areItemsTheSame(old: ProfileItem, new: ProfileItem) =
                old.profile.profileId == new.profile.profileId

            override fun areContentsTheSame(old: ProfileItem, new: ProfileItem) =
                old == new
        }
    }
}
