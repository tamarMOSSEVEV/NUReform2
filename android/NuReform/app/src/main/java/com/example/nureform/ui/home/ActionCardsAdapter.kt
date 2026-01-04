package com.example.nureform.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.nureform.databinding.ItemActionCardBinding

class ActionCardsAdapter(
    private val onCardClick: (ActionCardType) -> Unit
) : ListAdapter<ActionCard, ActionCardsAdapter.ActionCardViewHolder>(ActionCardDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActionCardViewHolder {
        val binding = ItemActionCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ActionCardViewHolder(binding, onCardClick)
    }

    override fun onBindViewHolder(holder: ActionCardViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ActionCardViewHolder(
        private val binding: ItemActionCardBinding,
        private val onCardClick: (ActionCardType) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(card: ActionCard) {
            binding.tvTitle.text = card.title
            binding.ivIcon.setImageResource(card.icon)
            binding.ivIcon.setColorFilter(
                ContextCompat.getColor(binding.root.context, card.tintColor)
            )

            binding.root.setOnClickListener {
                onCardClick(card.action)
            }
        }
    }

    private class ActionCardDiffCallback : DiffUtil.ItemCallback<ActionCard>() {
        override fun areItemsTheSame(oldItem: ActionCard, newItem: ActionCard): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ActionCard, newItem: ActionCard): Boolean {
            return oldItem == newItem
        }
    }
}

